#!/usr/bin/env python3
"""Pull ZKTeco attendance records over port 4370 and forward them to ADMS ingestion."""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from zk import ZK


DEFAULT_DEVICE_IPS = (
    "10.10.4.40",
    "10.10.4.41",
    "10.10.4.42",
    "10.10.4.43",
    "10.10.4.46",
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--devices",
        default=os.getenv("ZKTECO_PULL_DEVICE_IPS", ",".join(DEFAULT_DEVICE_IPS)),
        help="Comma-separated device IP addresses.",
    )
    parser.add_argument(
        "--backend-url",
        default=os.getenv("ZKTECO_PULL_BACKEND_URL", "http://localhost:8080"),
        help="Spring backend origin.",
    )
    parser.add_argument(
        "--comm-key",
        default=os.getenv("ZKTECO_ADMS_COMM_KEY", ""),
        help="Optional ADMS communication key.",
    )
    parser.add_argument(
        "--password",
        type=int,
        default=int(os.getenv("ZKTECO_PULL_PASSWORD", "0")),
        help="ZKTeco communication password.",
    )
    parser.add_argument(
        "--interval",
        type=int,
        default=int(os.getenv("ZKTECO_PULL_INTERVAL_SECONDS", "30")),
        help="Polling interval in seconds.",
    )
    parser.add_argument(
        "--device-timeout",
        type=int,
        default=int(os.getenv("ZKTECO_PULL_DEVICE_TIMEOUT_SECONDS", "60")),
        help="SDK connection and attendance-read timeout in seconds.",
    )
    parser.add_argument(
        "--lookback-hours",
        type=int,
        default=int(os.getenv("ZKTECO_PULL_LOOKBACK_HOURS", "24")),
        help="Initial attendance lookback window.",
    )
    parser.add_argument(
        "--batch-size",
        type=int,
        default=int(os.getenv("ZKTECO_PULL_BATCH_SIZE", "100")),
        help="Maximum punches forwarded in one backend request.",
    )
    parser.add_argument(
        "--state-file",
        type=Path,
        default=Path(
            os.getenv(
                "ZKTECO_PULL_STATE_FILE",
                str(Path(__file__).resolve().parents[1] / ".runtime/zkteco-pull-state.json"),
            )
        ),
        help="Local cursor file.",
    )
    parser.add_argument("--once", action="store_true", help="Poll once and exit.")
    return parser.parse_args()


def load_state(path: Path) -> dict[str, str]:
    if not path.exists():
        return {}
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        return value if isinstance(value, dict) else {}
    except (OSError, json.JSONDecodeError):
        return {}


def save_state(path: Path, state: dict[str, str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(state, indent=2, sort_keys=True), encoding="utf-8")
    temporary.replace(path)


def attendance_line(record: object) -> str:
    timestamp = record.timestamp.strftime("%Y-%m-%d %H:%M:%S")
    return "\t".join(
        (
            str(record.user_id),
            timestamp,
            str(record.punch),
            str(record.status),
            "0",
        )
    )


def post_attendance(
    backend_url: str,
    serial_number: str,
    device_ip: str,
    device_name: str,
    comm_key: str,
    records: list[object],
) -> str:
    query = {
        "SN": serial_number,
        "table": "ATTLOG",
        "DeviceName": device_name,
        "DeviceIP": device_ip,
    }
    if comm_key:
        query["pushcommkey"] = comm_key

    body = ("\n".join(attendance_line(record) for record in records) + "\n").encode("utf-8")
    request = Request(
        f"{backend_url.rstrip('/')}/iclock/cdata?{urlencode(query)}",
        data=body,
        headers={"Content-Type": "text/plain; charset=utf-8"},
        method="POST",
    )
    with urlopen(request, timeout=120) as response:
        return response.read().decode("utf-8", errors="replace").strip()


def poll_device(ip: str, args: argparse.Namespace, state: dict[str, str]) -> int:
    connection = None
    try:
        connection = ZK(
            ip,
            port=4370,
            timeout=max(10, args.device_timeout),
            password=args.password,
            force_udp=False,
            ommit_ping=True,
        ).connect()
        serial_number = connection.get_serialnumber().strip()
        device_name = connection.get_device_name().strip() or connection.get_platform().strip()
        records = connection.get_attendance()

        default_cursor = datetime.now() - timedelta(hours=max(1, args.lookback_hours))
        cursor_text = state.get(serial_number)
        cursor = datetime.fromisoformat(cursor_text) if cursor_text else default_cursor
        pending = sorted(
            (record for record in records if record.timestamp >= cursor),
            key=lambda record: (record.timestamp, record.uid),
        )

        if not pending:
            print(f"{ip} {serial_number} {device_name}: no new punches", flush=True)
            return 0

        batch_size = max(1, args.batch_size)
        for start in range(0, len(pending), batch_size):
            batch = pending[start : start + batch_size]
            acknowledgement = post_attendance(
                args.backend_url,
                serial_number,
                ip,
                device_name,
                args.comm_key,
                batch,
            )
            state[serial_number] = batch[-1].timestamp.isoformat()
            save_state(args.state_file, state)
            print(
                f"{ip} {serial_number} {device_name}: forwarded {len(batch)} punch(es), "
                f"backend replied {acknowledgement!r}",
                flush=True,
            )
        return len(pending)
    except Exception as error:
        print(f"{ip}: bridge error: {error}", file=sys.stderr, flush=True)
        return 0
    finally:
        if connection is not None:
            try:
                connection.disconnect()
            except Exception:
                pass


def main() -> int:
    args = parse_args()
    devices = [value.strip() for value in args.devices.split(",") if value.strip()]
    state = load_state(args.state_file)

    while True:
        total = sum(poll_device(ip, args, state) for ip in devices)
        print(f"Polling cycle complete: {total} punch(es) forwarded.", flush=True)
        if args.once:
            return 0
        time.sleep(max(5, args.interval))


if __name__ == "__main__":
    raise SystemExit(main())
