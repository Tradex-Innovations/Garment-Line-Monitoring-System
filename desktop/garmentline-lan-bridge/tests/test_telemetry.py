import importlib.util
import io
import json
import os
from pathlib import Path
import sys
import types
import unittest
from contextlib import redirect_stdout
from unittest.mock import patch

try:
    import requests
except ImportError:
    sys.modules["requests"] = types.ModuleType("requests")
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "workers"))
import telemetry

class TelemetryTest(unittest.TestCase):
    def setUp(self):
        self.env = patch.dict(os.environ, {"BRIDGE_TELEMETRY_ENABLED": "false"}); self.env.start()
        telemetry._context.set(None)
    def tearDown(self):
        self.env.stop(); telemetry._context.set(None)
    def test_device_and_upload_share_correlation_without_payload(self):
        @telemetry.device_read("hikvision")
        def read(device): return [{"employeeName": "PRIVATE", "password": "secret"}]
        @telemetry.upload
        def upload(endpoint, token, rows):
            self.assertTrue(telemetry.correlation_headers()["X-Correlation-Id"])
            return {"accepted": 1}
        output=io.StringIO()
        with redirect_stdout(output):
            upload("unused", "private-token", read("http://192.0.2.1"))
        self.assertNotIn("PRIVATE",output.getvalue()); self.assertNotIn("secret",output.getvalue())
        self.assertNotIn("192.0.2.1",output.getvalue()); self.assertNotIn("private-token",output.getvalue())
        events=[json.loads(line) for line in output.getvalue().splitlines()]
        self.assertEqual(len(events),4); self.assertEqual(len({event["correlationId"] for event in events}),1)
        self.assertEqual(events[-1]["acceptedCount"],1)
    def test_failure_rethrows_without_logging_exception_text(self):
        @telemetry.device_read("zkteco")
        def read(device): raise RuntimeError("secret-password")
        output=io.StringIO()
        with redirect_stdout(output):
            with self.assertRaises(RuntimeError): read("192.0.2.2")
        self.assertNotIn("secret-password",output.getvalue())
        self.assertEqual(json.loads(output.getvalue().splitlines()[-1])["status"],"FAILED")
    def test_heartbeat_is_not_a_business_record(self):
        @telemetry.device_read("hikvision")
        def read(device): return []
        @telemetry.upload
        def upload(endpoint,token,rows):return {"accepted":1}
        output=io.StringIO()
        with redirect_stdout(output):
            read("http://192.0.2.1"); upload("unused","unused",[{"bridgeHeartbeat":True}])
        final=json.loads(output.getvalue().splitlines()[-1])
        self.assertEqual(final["recordCount"],0); self.assertEqual(final["acceptedCount"],0)

if __name__ == "__main__": unittest.main()
