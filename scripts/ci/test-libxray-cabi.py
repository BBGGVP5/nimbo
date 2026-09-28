#!/usr/bin/env python3
"""Execute Nimbo's Android/Swift contract against a real combined Go shared library.

Usage: python3 scripts/ci/test-libxray-cabi.py /absolute/path/libXray-combined.so
The library must be built from the pinned upstream source plus iosApp/GoBridge.
No remote peer or external network is used. This is not an iOS device test.
"""
import base64
import ctypes
import http.client
import http.server
import json
from pathlib import Path
import re
import socket
import subprocess
import sys
import threading
from libxray_apple_test_host import run_in_app_host_if_needed

ROOT = Path(__file__).resolve().parents[2]


def main():
    run_in_app_host_if_needed()
    swift = (ROOT / "iosApp/PacketTunnel/LibXrayBridge.swift").read_text(encoding="utf-8")
    api = int(re.search(r"private static let apiVersion = (\d+)", swift)[1])
    run = re.search(r'func run\(configurationJSON: String\).*?method: "([^"]+)".*?payload: \["([^"]+)": configurationJSON\]', swift, re.S)
    assert api == 3 and run and run.groups() == ("runXray", "xrayJson"), "Stale Swift request contract"
    assert "defer { CGoFree(responsePointer) }" in swift, "Swift must release C responses"
    android = (ROOT / "app/src/main/java/com/danila/nimbo/vpn/XrayCoreProtocol.kt").read_text(encoding="utf-8")
    android_api = int(re.search(r"const val API_VERSION = (\d+)", android)[1])
    android_run = re.search(r'fun runXrayFromJson\(configJson: String\).*?method = "([^"]+)".*?payload = JSONObject\(\)\.put\("([^"]+)", configJson\)', android, re.S)
    assert android_api == api and android_run and android_run.groups() == run.groups(), "Android/Swift envelopes differ"
    library = ctypes.CDLL(str(Path(sys.argv[1]).resolve()))
    library.CGoInvoke.argtypes = [ctypes.c_char_p]
    library.CGoInvoke.restype = ctypes.c_void_p
    library.CGoFree.argtypes = [ctypes.c_void_p]
    library.CGoFree.restype = None
    library.NimboAWGStart.argtypes = [ctypes.c_char_p]
    library.NimboAWGStart.restype = ctypes.c_void_p
    library.NimboAWGStop.argtypes = []
    library.NimboAWGStop.restype = None
    library.NimboAWGStats.argtypes = []
    library.NimboAWGStats.restype = ctypes.c_void_p

    def response(pointer):
        assert pointer, "NULL C response"
        try:
            return json.loads(ctypes.string_at(pointer).decode())
        finally:
            library.CGoFree(pointer)

    def invoke(method, payload=None, version=api, success=True):
        request = json.dumps({"apiVersion": version, "method": method, "payload": payload or {}}).encode()
        result = response(library.CGoInvoke(request))
        assert result["success"] is success, (method, result)
        return result.get("data")

    version = invoke("xrayVersion")["version"]
    assert version == "26.9.9", version
    invoke("xrayVersion", version=1, success=False)
    invoke("runXrayFromJson", {"configJSON": "{}"}, success=False)
    invoke("runXray", {"configJSON": "{}"}, success=False)
    assert invoke("getXrayState")["running"] is False
    converted = invoke("convertShareLinksToXrayJson", {
        "text": "vless://00000000-0000-4000-8000-000000000001@127.0.0.1:443?encryption=none&security=none&type=tcp#contract"})
    assert isinstance(converted, dict) and converted["outbounds"], converted
    key = base64.b64encode(bytes([1]) * 32).decode()
    public = base64.b64encode(bytes([2]) * 32).decode()
    config = f"[Interface]\nPrivateKey={key}\nAddress=10.23.0.1/32\n[Peer]\nPublicKey={public}\nEndpoint=127.0.0.1:9\nAllowedIPs=10.23.0.2/32\n"
    awg_request = json.dumps({"config": config, "listen": "127.0.0.1:0", "username": "contract",
                              "password": "local-native-contract-test"}).encode()
    assert response(library.NimboAWGStart(b"{}"))["ok"] is False
    awg = response(library.NimboAWGStart(awg_request))
    assert awg["ok"] and awg["port"] > 0 and awg["version"] == "v3.1.20260828", awg
    try:
        assert response(library.NimboAWGStart(awg_request))["ok"] is False
        assert response(library.NimboAWGStats())["running"] is True
        xray = json.dumps({"log": {"loglevel": "none"}, "outbounds": [{"protocol": "socks",
            "settings": {"servers": [{"address": "127.0.0.1", "port": awg["port"],
                "users": [{"user": "contract", "pass": "local-native-contract-test"}]}]}}]})
        invoke(run[1], {run[2]: xray})
        assert invoke("getXrayState")["running"] is True
        invoke(run[1], {run[2]: xray}, success=False)
        invoke("stopXray")
        assert invoke("getXrayState")["running"] is False
        invoke("stopXray")
    finally:
        invoke("stopXray")
        library.NimboAWGStop()
    library.NimboAWGStop()
    assert response(library.NimboAWGStats())["running"] is False
    check_authenticated_route(invoke, run)
    subprocess.run([sys.executable, str(ROOT / "scripts/ci/test-libxray-diagnostic.py"), sys.argv[1]], check=True)
    print(f"PASS real combined C ABI: Xray {version}/API {api}; Android/Swift run/state/stop/conversion envelopes; legacy requests rejected; AWG 3.1 start/stats/duplicate/stop; CGoFree ownership")


def check_authenticated_route(invoke, run):
    class Direct(http.server.BaseHTTPRequestHandler):
        marker = b"DIRECT"

        def do_GET(self):
            self.send_response(200)
            self.send_header("Content-Length", str(len(self.marker)))
            self.end_headers()
            self.wfile.write(self.marker)

        def log_message(self, *args):
            pass

    class Proxy(Direct):
        marker = b"SELECTED_PROXY"

        def do_CONNECT(self):
            # Xray's HTTP outbound tunnels even a plain HTTP target via CONNECT.
            self.send_response(200, "Connection established")
            self.end_headers()
            self.wfile.flush()
            self.handle_one_request()

    direct = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Direct)
    proxy = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Proxy)
    for server in (direct, proxy):
        threading.Thread(target=server.serve_forever, daemon=True).start()
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    target = f"http://127.0.0.1:{direct.server_port}/contract"
    credentials = base64.b64encode(b"contract:local-route-test").decode()
    config = {"log": {"loglevel": "none"}, "inbounds": [{"tag": "nimbo-health-in",
        "listen": "127.0.0.1", "port": port, "protocol": "http", "settings": {
            "accounts": [{"user": "contract", "pass": "local-route-test"}]}}],
        "outbounds": [{"tag": "direct-default", "protocol": "freedom"},
            {"tag": "selected-proxy", "protocol": "http", "settings": {
                "servers": [{"address": "127.0.0.1", "port": proxy.server_port}]}}],
        "routing": {"rules": [{"type": "field", "inboundTag": ["nimbo-health-in"],
            "outboundTag": "selected-proxy"}]}}
    try:
        invoke(run[1], {run[2]: json.dumps(config)})
        for authenticated in (False, True):
            connection = http.client.HTTPConnection("127.0.0.1", port, timeout=5)
            try:
                headers = {"Proxy-Authorization": f"Basic {credentials}"} if authenticated else {}
                connection.request("GET", target, headers=headers)
                result = connection.getresponse()
                body = result.read()
                if authenticated:
                    assert result.status == 200 and body == Proxy.marker, (result.status, body)
                else:
                    assert result.status == 407, result.status
            finally:
                connection.close()
    finally:
        invoke("stopXray")
        for server in (direct, proxy):
            server.shutdown()
            server.server_close()
    print("PASS real Xray HTTP inbound: unauthenticated GET=407; authenticated GET uses named proxy outbound despite direct default")


if __name__ == "__main__":
    main()
