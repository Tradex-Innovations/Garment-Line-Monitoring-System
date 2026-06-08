#!/usr/bin/env python3
"""Poll Hikvision ISAPI face events from the factory LAN and forward them to Spring."""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

import requests
import urllib3
from requests.auth import HTTPBasicAuth, HTTPDigestAuth


DEFAULT_CAMERAS = (
    {
        "id": "guardroom-101",
        "name": "Guardroom entrance 01",
        "location": "Guardroom entrance",
        "base_url": "http://10.10.4.101",
    },
    {
        "id": "guardroom-102",
        "name": "Guardroom entrance 02",
        "location": "Guardroom entrance",
        "base_url": "http://10.10.4.102",
    },
    {
        "id": "guardroom-103",
        "name": "Guardroom entrance 03",
        "location": "Guardroom entrance",
        "base_url": "http://10.10.4.103",
    },
    {
        "id": "guardroom-104",
        "name": "Guardroom entrance 04",
        "location": "Guardroom entrance",
        "base_url": "http://10.10.4.104",
    },
    {
        "id": "guardroom-105",
        "name": "Guardroom entrance 05",
        "location": "Guardroom entrance",
        "base_url": "http://10.10.4.105",
    },
    {
        "id": "bike-106",
        "name": "Bike parking 01",
        "location": "Bike parking",
        "base_url": "http://10.10.4.106",
    },
    {
        "id": "bike-107",
        "name": "Bike parking 02",
        "location": "Bike parking",
        "base_url": "http://10.10.4.107",
    },
)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--backend-url",
        default=os.getenv("HIKVISION_BRIDGE_BACKEND_URL", "http://localhost:8080"),
        help="Spring backend origin.",
    )
    parser.add_argument(
        "--bridge-token",
        default=os.getenv("HIKVISION_BRIDGE_TOKEN", ""),
        help="Shared token sent as X-Hikvision-Bridge-Token.",
    )
    parser.add_argument(
        "--camera-urls",
        default=os.getenv("HIKVISION_BRIDGE_CAMERA_URLS", os.getenv("HIKVISION_CAMERA_URLS", "")),
        help="Optional comma-separated camera URLs. Defaults to the factory camera set.",
    )
    parser.add_argument(
        "--username",
        default=os.getenv("HIKVISION_USERNAME", "admin"),
        help="Hikvision ISAPI username.",
    )
    parser.add_argument(
        "--password",
        default=os.getenv("HIKVISION_PASSWORD", ""),
        help="Hikvision ISAPI password.",
    )
    parser.add_argument(
        "--interval",
        type=int,
        default=int(os.getenv("HIKVISION_BRIDGE_INTERVAL_SECONDS", "3")),
        help="Polling interval in seconds.",
    )
    parser.add_argument(
        "--lookback-minutes",
        type=int,
        default=int(os.getenv("HIKVISION_LOOKBACK_MINUTES", "60")),
        help="Camera event lookback window.",
    )
    parser.add_argument(
        "--max-results",
        type=int,
        default=int(os.getenv("HIKVISION_BRIDGE_MAX_RESULTS", "30")),
        help="Maximum events requested per camera per poll.",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=int(os.getenv("HIKVISION_BRIDGE_TIMEOUT_SECONDS", "10")),
        help="HTTP timeout in seconds.",
    )
    parser.add_argument(
        "--state-file",
        type=Path,
        default=Path(
            os.getenv(
                "HIKVISION_BRIDGE_STATE_FILE",
                str(Path(__file__).resolve().parents[1] / ".runtime/hikvision-bridge-state.json"),
            )
        ),
        help="Local cursor file.",
    )
    parser.add_argument("--verify-tls", action="store_true", help="Verify camera TLS certificates.")
    parser.add_argument("--once", action="store_true", help="Poll once and exit.")
    return parser.parse_args()


def normalize_base_url(url: str) -> str:
    value = (url or "").strip().rstrip("/")
    if not value.startswith(("http://", "https://")):
        value = f"http://{value}"
    return value


def camera_definitions(raw_urls: str) -> list[dict[str, str]]:
    if not raw_urls.strip():
        return [dict(camera) for camera in DEFAULT_CAMERAS]

    defaults_by_url = {normalize_base_url(camera["base_url"]): camera for camera in DEFAULT_CAMERAS}
    cameras: list[dict[str, str]] = []
    for index, raw_url in enumerate(raw_urls.replace("\n", ",").replace(";", ",").split(","), start=1):
        if not raw_url.strip():
            continue
        base_url = normalize_base_url(raw_url)
        default = defaults_by_url.get(base_url)
        if default:
            cameras.append(dict(default))
        else:
            cameras.append(
                {
                    "id": f"camera-{index}",
                    "name": f"Hikvision camera {index:02d}",
                    "location": "Configured camera",
                    "base_url": base_url,
                }
            )
    return cameras


def load_state(path: Path) -> dict[str, list[str]]:
    if not path.exists():
        return {}
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(value, dict):
            return {}
        return {str(key): list(map(str, values)) for key, values in value.items() if isinstance(values, list)}
    except (OSError, json.JSONDecodeError):
        return {}


def save_state(path: Path, state: dict[str, list[str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    temporary.write_text(json.dumps(state, indent=2, sort_keys=True), encoding="utf-8")
    temporary.replace(path)


def local_timestamp(value: datetime) -> str:
    return value.astimezone().isoformat(timespec="seconds")


def request_isapi(args: argparse.Namespace, method: str, base_url: str, path: str, **kwargs: Any) -> requests.Response:
    url = f"{base_url.rstrip('/')}{path}"
    auth = HTTPDigestAuth(args.username, args.password)
    response = requests.request(
        method,
        url,
        auth=auth,
        timeout=args.timeout,
        verify=args.verify_tls,
        **kwargs,
    )
    if response.status_code == 401:
        response = requests.request(
            method,
            url,
            auth=HTTPBasicAuth(args.username, args.password),
            timeout=args.timeout,
            verify=args.verify_tls,
            **kwargs,
        )
    response.raise_for_status()
    return response


def parse_device_info(text: str) -> dict[str, Any] | None:
    trimmed = (text or "").strip()
    if not trimmed:
        return None

    if trimmed.startswith("{"):
        data = json.loads(trimmed)
        info = data.get("DeviceInfo", data)
        return {
            "deviceName": first_present(info, "deviceName", "deviceDescription"),
            "deviceId": first_present(info, "deviceID", "deviceId"),
            "model": info.get("model"),
            "serialNumber": first_present(info, "serialNumber", "serialNo"),
            "macAddress": first_present(info, "macAddress", "MACAddress"),
            "firmwareVersion": first_present(info, "firmwareVersion", "firmwareReleasedDate"),
        }

    root = ET.fromstring(trimmed)
    return {
        "deviceName": xml_text(root, "deviceName", "deviceDescription"),
        "deviceId": xml_text(root, "deviceID", "deviceId"),
        "model": xml_text(root, "model"),
        "serialNumber": xml_text(root, "serialNumber", "serialNo"),
        "macAddress": xml_text(root, "macAddress", "MACAddress"),
        "firmwareVersion": xml_text(root, "firmwareVersion", "firmwareReleasedDate"),
    }


def first_present(data: dict[str, Any], *keys: str) -> Any:
    for key in keys:
        value = data.get(key)
        if value not in (None, ""):
            return value
    return None


def xml_text(root: ET.Element, *names: str) -> str | None:
    for element in root.iter():
        local_name = element.tag.rsplit("}", 1)[-1]
        if local_name in names and element.text and element.text.strip():
            return element.text.strip()
    return None


def fetch_device_info(args: argparse.Namespace, base_url: str) -> dict[str, Any] | None:
    for path in ("/ISAPI/System/deviceInfo?format=json", "/ISAPI/System/deviceInfo"):
        try:
            return parse_device_info(request_isapi(args, "GET", base_url, path).text)
        except Exception:
            continue
    return None


def fetch_events(args: argparse.Namespace, camera: dict[str, str]) -> list[dict[str, Any]]:
    now = datetime.now().astimezone()
    start = now - timedelta(minutes=max(1, args.lookback_minutes))
    payload = {
        "AcsEventCond": {
            "searchID": f"garmentline-bridge-{camera['id']}",
            "searchResultPosition": 0,
            "maxResults": max(1, args.max_results),
            "major": 0,
            "minor": 0,
            "startTime": local_timestamp(start),
            "endTime": local_timestamp(now),
            "timeReverseOrder": True,
        }
    }
    response = request_isapi(
        args,
        "POST",
        camera["base_url"],
        "/ISAPI/AccessControl/AcsEvent?format=json",
        json=payload,
        headers={"Content-Type": "application/json"},
    )
    data = response.json()
    info_list = data.get("AcsEvent", {}).get("InfoList", [])
    if isinstance(info_list, dict):
        return [info_list]
    return info_list if isinstance(info_list, list) else []


def event_key(camera: dict[str, str], event: dict[str, Any]) -> str:
    return "|".join(
        str(event.get(key, ""))
        for key in ("time", "dateTime", "serialNo", "SerialNo", "employeeNoString", "employeeNo", "major", "minor")
    ) + "|" + camera["id"]


def post_events(
    args: argparse.Namespace,
    camera: dict[str, str],
    device_info: dict[str, Any] | None,
    events: list[dict[str, Any]],
) -> dict[str, Any]:
    payload = {
        "cameraId": camera["id"],
        "cameraName": camera["name"],
        "cameraLocation": camera["location"],
        "cameraBaseUrl": camera["base_url"],
        "polledAt": local_timestamp(datetime.now().astimezone()),
        "deviceInfo": device_info,
        "events": events,
    }
    response = requests.post(
        f"{args.backend_url.rstrip('/')}/api/hikvision/bridge/events",
        json=payload,
        headers={"X-Hikvision-Bridge-Token": args.bridge_token},
        timeout=max(30, args.timeout * 3),
    )
    response.raise_for_status()
    return response.json()


def poll_camera(args: argparse.Namespace, camera: dict[str, str], state: dict[str, list[str]]) -> int:
    try:
        device_info = fetch_device_info(args, camera["base_url"])
        events = fetch_events(args, camera)
        seen = set(state.get(camera["id"], []))
        new_events = [event for event in events if event_key(camera, event) not in seen]
        response = post_events(args, camera, device_info, new_events)

        next_seen = list(dict.fromkeys([event_key(camera, event) for event in events] + state.get(camera["id"], [])))[:500]
        state[camera["id"]] = next_seen
        save_state(args.state_file, state)

        print(
            f"{camera['base_url']} {camera['name']}: "
            f"camera returned {len(events)}, forwarded {len(new_events)}, "
            f"backend accepted {response.get('acceptedEvents', 0)}",
            flush=True,
        )
        return int(response.get("acceptedEvents", 0))
    except Exception as error:
        print(f"{camera['base_url']} {camera['name']}: bridge error: {error}", file=sys.stderr, flush=True)
        return 0


def main() -> int:
    args = parse_args()
    if not args.bridge_token.strip():
        print("HIKVISION_BRIDGE_TOKEN is required.", file=sys.stderr)
        return 2
    if not args.verify_tls:
        urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

    cameras = camera_definitions(args.camera_urls)
    state = load_state(args.state_file)
    while True:
        accepted = sum(poll_camera(args, camera, state) for camera in cameras)
        print(f"Hikvision polling cycle complete: {accepted} new event(s) accepted.", flush=True)
        if args.once:
            return 0
        time.sleep(max(1, args.interval))


if __name__ == "__main__":
    raise SystemExit(main())
