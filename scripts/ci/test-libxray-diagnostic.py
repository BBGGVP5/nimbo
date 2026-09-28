#!/usr/bin/env python3
"""Real app-process diagnostic C ABI tests. Only loopback synthetic peers are used."""
import concurrent.futures
import base64
import ctypes
import http.server
import json
from pathlib import Path
import select
import shutil
import socket
import socketserver
import ssl
import subprocess
import sys
import tempfile
import threading
import time
import uuid
from libxray_apple_test_host import run_in_app_host_if_needed


class Proxy(http.server.BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_CONNECT(self):
        self.connection.settimeout(3)
        self.send_response(200, "Connection established")
        self.end_headers()
        self.wfile.flush()
        if self.server.relay:
            host, port = self.path.rsplit(":", 1)
            with socket.create_connection((host, int(port)), timeout=3) as remote:
                while True:
                    ready, _, _ = select.select([self.connection, remote], [], [], 3)
                    if not ready:
                        return
                    for source in ready:
                        data = source.recv(65536)
                        if not data:
                            return
                        (remote if source is self.connection else self.connection).sendall(data)
        else:
            self.handle_one_request()

    def do_GET(self):
        self.probe()

    def do_HEAD(self):
        self.probe()

    def probe(self):
        self.server.events.append((self.command, self.path))
        if self.path == "/hold":
            self.server.entered.set()
            try:
                if self.connection.recv(1) == b"":
                    self.server.closed.set()
            except (ConnectionError, OSError):
                self.server.closed.set()
            return
        if self.path == "/delayed":
            time.sleep(0.12)
        code = 302 if self.path == "/redirect" else 503 if self.path == "/fail" else self.server.status
        self.send_response(code)
        if code == 302:
            self.send_header("Location", self.server.redirect)
        if self.path == "/headers":
            self.send_header("X-Oversized", "x" * 65536)
        self.send_header("Content-Length", "0")
        self.end_headers()


class Socks(socketserver.StreamRequestHandler):
    def handle(self):
        self.request.settimeout(3)
        version, count = self.rfile.read(2)
        methods = self.rfile.read(count)
        assert version == 5 and 2 in methods
        self.wfile.write(b"\x05\x02")
        auth, user_length = self.rfile.read(2)
        username = self.rfile.read(user_length)
        password = self.rfile.read(self.rfile.read(1)[0])
        assert auth == 1 and username == b"fixture" and password == b"fixture-secret-not-for-errors"
        self.wfile.write(b"\x01\x00")
        version, command, _, address_type = self.rfile.read(4)
        assert version == 5 and command == 1
        length = {1: 4, 4: 16}.get(address_type)
        if address_type == 3:
            length = self.rfile.read(1)[0]
        self.rfile.read(length)
        self.rfile.read(2)
        self.wfile.write(b"\x05\x00\x00\x01\x7f\x00\x00\x01\x00\x00")
        request_line = self.rfile.readline(8192).decode().strip()
        while self.rfile.readline(8192).strip():
            pass
        self.server.events.append(request_line)
        self.wfile.write(b"HTTP/1.1 204 No Content\r\nContent-Length: 0\r\nConnection: close\r\n\r\n")


def listener(status=204, relay=False):
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Proxy)
    server.daemon_threads = True
    server.events = []
    server.status = status
    server.relay = relay
    server.redirect = ""
    server.entered = threading.Event()
    server.closed = threading.Event()
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def main():
    run_in_app_host_if_needed()
    library = ctypes.CDLL(str(Path(sys.argv[1]).resolve()))
    library.CGoFree.argtypes = [ctypes.c_void_p]
    library.CGoFree.restype = None
    for name in ("NimboDiagnosticRun", "NimboDiagnosticCancel", "CGoInvoke"):
        method = getattr(library, name)
        method.argtypes = [ctypes.c_char_p]
        method.restype = ctypes.c_void_p

    if "--guard-only" in sys.argv:
        request = {"apiVersion": 1, "requestID": str(uuid.uuid4()), "serverID": "guard",
                   "config": "unsupported-local-fixture", "format": "awg",
                   "url": "https://example.invalid/", "method": "GET", "timeoutMs": 1000}
        pointer = library.NimboDiagnosticRun(json.dumps(request).encode())
        assert pointer
        try:
            response = json.loads(ctypes.string_at(pointer))
        finally:
            library.CGoFree(pointer)
        expected = sys.argv[sys.argv.index("--guard-only") + 1]
        assert response.get("error") == expected, (expected, response)
        print(f"PASS real Apple bundle guard: {expected}")
        return

    def call(name, request):
        pointer = getattr(library, name)(json.dumps(request).encode())
        assert pointer
        try:
            result = json.loads(ctypes.string_at(pointer))
        finally:
            library.CGoFree(pointer)
        if name == "NimboDiagnosticRun":
            assert result["requestID"] == request["requestID"] and result["serverID"] == request["serverID"]
            assert result["latency"] >= 0 if result["ok"] else result["latency"] == -1
            assert "fixture-secret-not-for-errors" not in json.dumps(result)
        return result

    direct, first, second = listener(200), listener(204), listener(201)
    servers = [direct, first, second]
    direct_url = f"http://127.0.0.1:{direct.server_port}"
    for server in servers:
        server.redirect = direct_url + "/redirect-followed"

    def request(proxy=first, path="/ok", **changes):
        config = {"env": {"NIMBO_DIAGNOSTIC_ENV_TEST": "must-not-apply"},
                  "inbounds": [{"protocol": "tun", "port": direct.server_port}],
                  "routing": {"rules": [{"outboundTag": "direct"}]},
                  "outbounds": [{"tag": "direct", "protocol": "freedom"},
                      {"tag": "real-proxy", "protocol": "http", "settings": {"servers": [
                          {"address": "127.0.0.1", "port": proxy.server_port,
                           "users": [{"user": "fixture", "pass": "fixture-secret-not-for-errors"}]}]}}]}
        value = {"apiVersion": 1, "requestID": str(uuid.uuid4()), "serverID": str(proxy.server_port),
                 "config": json.dumps(config), "format": "xray", "url": direct_url + path,
                 "method": "GET", "timeoutMs": 2000}
        value.update(changes)
        return value

    def run(value):
        return call("NimboDiagnosticRun", value)

    def cancel(value):
        return call("NimboDiagnosticCancel", {"apiVersion": 1, "requestID": value["requestID"]})

    def invoke(method, payload=None):
        result = call("CGoInvoke", {"apiVersion": 3, "method": method, "payload": payload or {}})
        assert result["success"], result
        return result["data"]

    try:
        assert not invoke("getXrayState")["running"]
        for peer in (first, second):
            assert run(request(peer))["ok"]
            assert peer.events == [("GET", "/ok")], peer.events
        assert not direct.events, "diagnostic bypassed selected proxy"
        assert not invoke("getXrayState")["running"], "diagnostic touched managed lifecycle"
        libc = ctypes.CDLL(None)
        libc.getenv.argtypes = [ctypes.c_char_p]
        libc.getenv.restype = ctypes.c_char_p
        assert libc.getenv(b"NIMBO_DIAGNOSTIC_ENV_TEST") is None, "root env was applied"
        assert run(request(method="HEAD"))["ok"]
        assert first.events[-1] == ("HEAD", "/ok")
        socks = socketserver.ThreadingTCPServer(("127.0.0.1", 0), Socks)
        socks.daemon_threads, socks.events = True, []
        servers.append(socks)
        threading.Thread(target=socks.serve_forever, daemon=True).start()
        userinfo = base64.b64encode(b"fixture:fixture-secret-not-for-errors").decode()
        link = f"socks://{userinfo}@127.0.0.1:{socks.server_address[1]}#per-server"
        assert run(request(format="share", config=link))["ok"]
        assert socks.events == ["GET /ok HTTP/1.1"], socks.events
        delayed = run(request(path="/delayed"))
        assert delayed["ok"] and delayed["latency"] >= 100, delayed
        for path in ("/redirect", "/fail"):
            assert run(request(path=path))["error"] == "DIAGNOSTIC_HTTP_STATUS"
        assert not direct.events, "redirect or error escaped to direct target"
        assert run(request(path="/headers"))["error"] == "DIAGNOSTIC_NETWORK"
        assert run(request(format="awg", config="[Interface]\nPrivateKey=fixture-secret-not-for-errors"))["error"] == "DIAGNOSTIC_UNSUPPORTED"
        assert run(request(config='{"outbounds":[{"protocol":"freedom"}]}'))["error"] == "DIAGNOSTIC_AMBIGUOUS_ROUTE"
        bound = json.loads(request()["config"])
        bound["outbounds"][1]["sendThrough"] = "127.0.0.1"
        assert run(request(config=json.dumps(bound)))["error"] == "DIAGNOSTIC_UNSUPPORTED_BIND"
        bound["outbounds"][1].pop("sendThrough")
        bound["outbounds"][1]["streamSettings"] = {"sockopt": {"dialerProxy": "direct"}}
        assert run(request(config=json.dumps(bound)))["error"] == "DIAGNOSTIC_UNSAFE_ROUTE"
        # A refused per-server endpoint must not fall back to the target.
        with socket.socket() as vacant:
            vacant.bind(("127.0.0.1", 0))
            refused_port = vacant.getsockname()[1]
        refused = json.loads(request()["config"])
        refused["outbounds"][1]["settings"]["servers"][0]["port"] = refused_port
        assert not run(request(config=json.dumps(refused), timeoutMs=300))["ok"]
        assert not direct.events

        cancelled = request()
        assert cancel(cancelled)["ok"]
        assert run(cancelled)["error"] == "DIAGNOSTIC_CANCELLED"
        with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
            first.entered.clear()
            first.closed.clear()
            holding = request(path="/hold")
            future = pool.submit(run, holding)
            assert first.entered.wait(1), "GET was not issued"
            assert run(request())["error"] == "DIAGNOSTIC_BUSY"
            assert not cancel(request())["cancelled"], "wrong ID cancelled a running request"
            assert not future.done()
            started = time.monotonic()
            assert cancel(holding)["cancelled"]
            result = future.result(timeout=1)
            assert result["error"] == "DIAGNOSTIC_CANCELLED" and time.monotonic() - started < 1
            assert first.closed.wait(0.5), "cancel returned with peer connection still open"
        assert run(request())["ok"], "cleanup did not release serialized slot"
        first.entered.clear()
        first.closed.clear()
        started = time.monotonic()
        result = run(request(path="/hold", timeoutMs=150))
        assert result["error"] == "DIAGNOSTIC_TIMEOUT" and time.monotonic() - started < 1, result
        assert first.closed.wait(0.5)
        assert run(request())["ok"]

        # Existing managed instance is rejected before new core/DNS setup.
        invoke("runXray", {"xrayJson": '{"log":{"loglevel":"none"},"outbounds":[{"protocol":"blackhole"}]}'})
        try:
            assert run(request())["error"] == "DIAGNOSTIC_MANAGED_ACTIVE"
            assert invoke("getXrayState")["running"]
        finally:
            invoke("stopXray")

        check_tls(request, run, servers)
        print("PASS real diagnostic C ABI: distinct forced per-server GET/HEAD and authenticated raw-share SOCKS; no managed lifecycle/env/listeners/direct fallback; status/redirect/header bounds; request IDs; cancel-before-start/in-flight; timeout; serialized cleanup; bind fidelity; target TLS verification; AWG explicit unsupported")
    finally:
        for server in servers:
            server.shutdown()
            server.server_close()


def check_tls(request, run, servers):
    openssl = shutil.which("openssl")
    assert openssl, "openssl is required for the self-signed target TLS rejection fixture"
    with tempfile.TemporaryDirectory(prefix="nimbo-diagnostic-tls-") as directory:
        key, cert = Path(directory) / "key.pem", Path(directory) / "cert.pem"
        subprocess.run([openssl, "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-days", "1",
                        "-subj", "/CN=localhost", "-keyout", str(key), "-out", str(cert)],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        target = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Proxy)
        target.daemon_threads = True
        target.events, target.status = [], 204
        target.relay = False
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.load_cert_chain(cert, key)
        target.socket = context.wrap_socket(target.socket, server_side=True)
        threading.Thread(target=target.serve_forever, daemon=True).start()
        relay = listener(relay=True)
        servers.extend([target, relay])
        result = run(request(relay, url=f"https://127.0.0.1:{target.server_port}/ok"))
        assert result["error"] == "DIAGNOSTIC_NETWORK", "target TLS verification was disabled"
        assert not target.events, "untrusted TLS reached HTTP handler"


if __name__ == "__main__":
    main()
