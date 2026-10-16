import json
import threading
import unittest
import urllib.error
import urllib.request
from server import make_server


class ServerTest(unittest.TestCase):
    def setUp(self):
        self.server = make_server(port=0)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()
        self.base = f"http://127.0.0.1:{self.server.server_port}/api/v1"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def request(self, path, body=None):
        request = urllib.request.Request(self.base + path, data=json.dumps(body).encode() if body is not None else None,
                                         headers={"Content-Type": "application/json"}, method="PUT" if body is not None else "GET")
        try:
            response = urllib.request.urlopen(request, timeout=3)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return response.status, json.load(response)

    def test_roundtrip_idempotency_and_readonly(self):
        _, device = self.request("/device")
        body = {"requestId": "test", "expectedBootId": device["bootId"], "value": True}
        code, first = self.request("/resources/led.main/properties/on", body)
        self.assertEqual(200, code)
        _, second = self.request("/resources/led.main/properties/on", body)
        self.assertEqual(first["revision"], second["revision"])
        _, state = self.request("/state")
        self.assertTrue(state["resources"][0]["values"]["on"])
        code, error = self.request("/resources/sensor.room/properties/temperature", body)
        self.assertEqual((422, "READ_ONLY"), (code, error["error"]["code"]))

    def test_invalid_values_do_not_change_output(self):
        _, device = self.request("/device")
        for value in ["true", 1, None, {}, []]:
            code, error = self.request("/resources/led.main/properties/on", {"requestId": "test", "expectedBootId": device["bootId"], "value": value})
            self.assertEqual((422, "INVALID_VALUE"), (code, error["error"]["code"]))
        _, state = self.request("/state")
        self.assertFalse(state["resources"][0]["values"]["on"])

    def test_restart_guard(self):
        code, body = self.request("/resources/led.main/properties/on", {"requestId": "test", "expectedBootId": "old", "value": True})
        self.assertEqual((409, "DEVICE_RESTARTED"), (code, body["error"]["code"]))

    def test_numeric_constraints(self):
        _, device = self.request("/device")
        for value in [-1, 101, 0.5, True]:
            code, _ = self.request("/resources/pwm.light/properties/level", {"requestId": "test", "expectedBootId": device["bootId"], "value": value})
            self.assertEqual(422, code)


if __name__ == "__main__":
    unittest.main()
