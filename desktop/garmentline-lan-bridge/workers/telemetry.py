"""Versioned, payload-free telemetry. Delivery never blocks attendance collection."""
from contextvars import ContextVar
from datetime import datetime, timezone
from functools import wraps
import hashlib
import json
import os
from queue import Queue, Full
from threading import Thread, Lock
import time
from urllib.parse import urlsplit, urlunsplit
import uuid
import requests

_context = ContextVar("pipeline_telemetry", default=None)
_queue = Queue(maxsize=500)
_lock = Lock()
_started = False

def device_reference(family, device):
    return "device-" + hashlib.sha256((family + ":" + device.strip().rstrip("/")).encode()).hexdigest()[:16]

def correlation_headers():
    context = _context.get()
    return {"X-Correlation-Id": context["correlationId"]} if context else {}

def _deliver():
    while True:
        endpoint, token, event = _queue.get()
        try:
            # No redirects: never forward the bridge credential to another origin.
            response = requests.post(endpoint, headers={"X-Bridge-Token": token}, json=event,
                                     timeout=2, allow_redirects=False)
            if response.status_code not in range(200, 300):
                raise RuntimeError("Telemetry delivery failed")
        except Exception:
            print('{"schemaVersion":1,"kind":"telemetry-health","status":"delivery-failed"}', flush=True)
        finally:
            _queue.task_done()

def emit(event):
    global _started
    print(json.dumps(event, separators=(",", ":")), flush=True)
    if os.environ.get("BRIDGE_TELEMETRY_ENABLED", "true").lower() != "true":
        return
    base = os.environ.get("ZKTECO_BRIDGE_BACKEND_URL") or os.environ.get("HIKVISION_BRIDGE_BACKEND_URL", "")
    token = os.environ.get("BRIDGE_SHARED_TOKEN", "")
    parsed = urlsplit(base)
    if parsed.scheme not in ("http", "https") or not parsed.netloc or not token:
        return
    endpoint = urlunsplit((parsed.scheme, parsed.netloc, "/api/bridge/telemetry", "", ""))
    with _lock:
        if not _started:
            Thread(target=_deliver, daemon=True, name="safe-telemetry").start()
            _started = True
    try:
        _queue.put_nowait((endpoint, token, event))
    except Full:
        print('{"schemaVersion":1,"kind":"telemetry-health","status":"queue-full"}', flush=True)

def _event(context, stage, status, start, duration=0, records=0, accepted=0, parent=None):
    return {"schemaVersion": 1, "kind": "pipeline-event", "eventId": str(uuid.uuid4()),
            "correlationId": context["correlationId"], "sourceSystem": context["family"],
            "deviceReference": context["reference"], "stageName": stage, "status": status,
            "occurredAt": start, "completedAt": None if status == "RUNNING" else datetime.now(timezone.utc).isoformat(),
            "durationMs": duration, "recordCount": records, "acceptedCount": accepted,
            "parentEventId": parent}

def device_read(family):
    def decorate(function):
        @wraps(function)
        def wrapped(device, *args, **kwargs):
            context = {"correlationId": str(uuid.uuid4()), "family": family, "reference": device_reference(family, device)}
            _context.set(context)  # Shared with this device's subsequent batch upload; next read starts a new run.
            started = datetime.now(timezone.utc).isoformat(); clock = time.monotonic()
            running = _event(context, "device-read", "RUNNING", started); emit(running)
            try:
                result = function(device, *args, **kwargs)
                count = len(result[1] if family == "zkteco" else result)
                emit(_event(context, "device-read", "SUCCEEDED", started, int((time.monotonic()-clock)*1000), count, count, running["eventId"]))
                return result
            except Exception:
                emit(_event(context, "device-read", "FAILED", started, int((time.monotonic()-clock)*1000), parent=running["eventId"]))
                raise
        return wrapped
    return decorate

def upload(function):
    @wraps(function)
    def wrapped(endpoint, token, payload):
        context = _context.get()
        if not context:
            return function(endpoint, token, payload)  # Explicit backfill is outside live polling telemetry.
        count = sum(not row.get("bridgeHeartbeat", False) for row in payload)
        started = datetime.now(timezone.utc).isoformat(); clock = time.monotonic()
        running = _event(context, "bridge-upload", "RUNNING", started, records=count); emit(running)
        try:
            result = function(endpoint, token, payload)
            # An acknowledged upload is not a claim that downstream reconciliation succeeded.
            accepted = min(count, max(0, int(result.get("accepted", 0))))
            status = "SUCCEEDED" if count == accepted else "WARNING"
            emit(_event(context, "bridge-upload", status, started, int((time.monotonic()-clock)*1000), count, accepted, running["eventId"]))
            return result
        except Exception:
            emit(_event(context, "bridge-upload", "FAILED", started, int((time.monotonic()-clock)*1000), count, parent=running["eventId"]))
            raise
    return wrapped
