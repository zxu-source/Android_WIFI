"""Protocol v1 local device simulator. No hardware access. Python 3.10+."""
import argparse
import copy
import json
import math
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

FIXTURES = Path(__file__).resolve().parents[2] / "protocol" / "fixtures"


class Device:
    def __init__(self):
        self.lock = threading.Lock()
        self.description = json.loads((FIXTURES / "device.json").read_text(encoding="utf-8"))
        self.state = json.loads((FIXTURES / "state.json").read_text(encoding="utf-8"))
        boot = str(uuid.uuid4())
        self.description["bootId"] = self.state["bootId"] = boot
        self.state["revision"] = 0

    def write(self, resource_id, property_id, body):
        request_id = body.get("requestId")
        if not isinstance(request_id, str) or not request_id or len(request_id) > 128:
            return 400, "INVALID_REQUEST", "requestId required"
        if body.get("expectedBootId") != self.description["bootId"]:
            return 409, "DEVICE_RESTARTED", "Reload device description"
        resource = next((r for r in self.description["resources"] if r["id"] == resource_id), None)
        if resource is None:
            return 404, "RESOURCE_NOT_FOUND", "Unknown resource"
        prop = next((p for p in resource["properties"] if p["id"] == property_id), None)
        if prop is None:
            return 404, "PROPERTY_NOT_FOUND", "Unknown property"
        if not prop["writable"]:
            return 422, "READ_ONLY", "Property is read-only"
        value = body.get("value")
        if prop["type"] == "boolean":
            valid = type(value) is bool
        else:
            valid = type(value) in (int, float) and math.isfinite(value)
            if valid:
                steps = (value - prop["min"]) / prop["step"]
                valid = prop["min"] <= value <= prop["max"] and abs(steps - round(steps)) <= 1e-6
                if prop["type"] == "integer":
                    valid = valid and value == round(value)
        if not valid:
            return 422, "INVALID_VALUE", "Wrong value type, range or step"
        entry = next(r for r in self.state["resources"] if r["id"] == resource_id)
        if entry["values"][property_id] != value:
            entry["values"][property_id] = value
            self.state["revision"] += 1
        return 200, {
            "protocolVersion": 1, "requestId": request_id,
            "deviceId": self.description["deviceId"], "bootId": self.description["bootId"],
            "resourceId": resource_id, "propertyId": property_id, "value": value,
            "revision": self.state["revision"],
        }


def make_server(host="127.0.0.1", port=8080, delay=0.0, lost_response=False):
    device = Device()

    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"
        def reply(self, status, body):
            payload = json.dumps(body, ensure_ascii=False, allow_nan=False).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(payload)))
            self.send_header("Connection", "close")
            self.close_connection = True
            self.end_headers()
            try:
                self.wfile.write(payload)
            except (BrokenPipeError, ConnectionResetError):
                pass

        def error(self, status, code, message, request_id=None):
            self.reply(status, {"protocolVersion": 1, "requestId": request_id,
                                "error": {"code": code, "message": message}})

        def do_GET(self):
            time.sleep(delay)
            with device.lock:
                if self.path == "/api/v1/device":
                    data = copy.deepcopy(device.description)
                elif self.path == "/api/v1/state":
                    data = copy.deepcopy(device.state)
                else:
                    self.error(404, "RESOURCE_NOT_FOUND", "Unknown endpoint")
                    return
            self.reply(200, data)

        def do_PUT(self):
            if self.headers.get("Content-Type", "").split(";")[0].strip() != "application/json":
                self.error(415, "UNSUPPORTED_MEDIA_TYPE", "JSON required")
                return
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                self.error(400, "INVALID_REQUEST", "Invalid length")
                return
            if not 0 < length <= 1024:
                self.error(413 if length > 1024 else 400, "PAYLOAD_TOO_LARGE" if length > 1024 else "INVALID_REQUEST", "Invalid body length")
                return
            try:
                body = json.loads(self.rfile.read(length), parse_constant=lambda value: (_ for _ in ()).throw(ValueError(value)))
                if not isinstance(body, dict):
                    raise ValueError("Expected object")
            except (ValueError, UnicodeError):
                self.error(400, "INVALID_REQUEST", "Invalid JSON")
                return
            parts = self.path.split("/")
            if len(parts) != 7 or parts[1:4] != ["api", "v1", "resources"] or parts[5] != "properties":
                self.error(404, "RESOURCE_NOT_FOUND", "Unknown endpoint")
                return
            time.sleep(delay)
            with device.lock:
                result = device.write(parts[4], parts[6], body)
            if result[0] == 200:
                if lost_response:
                    time.sleep(6)
                self.reply(200, result[1])
            else:
                request_id = body.get("requestId")
                self.error(*result, request_id if isinstance(request_id, str) else None)

        def do_POST(self):
            self.error(405, "METHOD_NOT_ALLOWED", "Use GET or PUT")

    server = ThreadingHTTPServer((host, port), Handler)
    server.daemon_threads = True
    server.device = device
    return server


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default="127.0.0.1", help="Bind the PC's LAN IP explicitly for phone testing")
    parser.add_argument("--port", type=int, default=8080)
    parser.add_argument("--delay", type=float, default=0.0)
    parser.add_argument("--lost-response", action="store_true", help="Apply writes, then delay confirmation beyond the Android timeout")
    parser.add_argument("--advertise-ip", help="Optional IPv4 address for mDNS; requires zeroconf")
    args = parser.parse_args()
    if args.delay < 0 or not math.isfinite(args.delay):
        parser.error("delay must be finite and >= 0")
    server = make_server(args.host, args.port, args.delay, args.lost_response)
    advertiser = None
    try:
        if args.advertise_ip:
            import socket
            from zeroconf import ServiceInfo, Zeroconf
            advertiser = Zeroconf(interfaces=[args.advertise_ip])
            info = ServiceInfo("_wifi-control._tcp.local.", "WiFi-Control-Simulator._wifi-control._tcp.local.",
                               addresses=[socket.inet_aton(args.advertise_ip)], port=server.server_port,
                               properties={"protocolVersion": "1"}, server="wifi-control-simulator.local.")
            advertiser.register_service(info)
        print(f"SIMULATOR only: http://{args.host}:{server.server_port}/api/v1/device", flush=True)
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        if advertiser:
            advertiser.unregister_all_services()
            advertiser.close()
        server.server_close()


if __name__ == "__main__":
    main()
