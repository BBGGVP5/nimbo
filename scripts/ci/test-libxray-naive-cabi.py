#!/usr/bin/env python3
"""Exercise the real merged Naive C ABI. No remote connection or user secrets."""
import ctypes
import json
from pathlib import Path
import socket
import sys
import subprocess
import tempfile
import threading
from libxray_apple_test_host import run_in_app_host_if_needed


def main():
    run_in_app_host_if_needed()
    lib = ctypes.CDLL(str(Path(sys.argv[1]).resolve()))
    lib.CGoFree.argtypes, lib.CGoFree.restype = [ctypes.c_void_p], None
    lib.NimboNaiveStart.argtypes, lib.NimboNaiveStart.restype = [ctypes.c_char_p], ctypes.c_void_p
    lib.NimboNaiveStatus.argtypes, lib.NimboNaiveStatus.restype = [], ctypes.c_void_p
    for name in ("NimboNaiveStop", "NimboNaiveResetConnections"):
        getattr(lib, name).argtypes, getattr(lib, name).restype = [], None

    def response(pointer):
        assert pointer
        try:
            return json.loads(ctypes.string_at(pointer))
        finally:
            lib.CGoFree(pointer)

    def receive(sock, size):
        result = b""
        while len(result) < size:
            part = sock.recv(size - len(result))
            assert part, "Unexpected SOCKS EOF"
            result += part
        return result

    request = dict(link="naive+https://fixture:secret@192.0.2.1:443?peer=proxy.example",
                   listen="127.0.0.1:0", username="local", password="random-test-password-32")
    lib.NimboNaiveStop()
    assert not response(lib.NimboNaiveStatus())["running"]
    for invalid in (None, b"{", b"x" * 32769, b'{"link":"bad-secret"}'):
        result = response(lib.NimboNaiveStart(invalid))
        assert not result["ok"] and "secret" not in json.dumps(result)
    for scheme in ("naive+https", "naive+quic"):
        request["link"] = scheme + "://fixture:secret@192.0.2.1:443?peer=proxy.example"
        wire = json.dumps(request).encode()
        result = response(lib.NimboNaiveStart(wire))
        assert result["ok"] and result["version"] == "150.0.7871.63"
        port = result["port"]
        assert response(lib.NimboNaiveStatus())["running"]
        assert not response(lib.NimboNaiveStart(wire))["ok"]
        try:
            with socket.create_connection(("127.0.0.1", port), 3) as sock:
                sock.sendall(b"\x05\x01\x00")
                assert receive(sock, 2) == b"\x05\xff"
            for password, valid in (("wrong", False), (request["password"], True)):
                with socket.create_connection(("127.0.0.1", port), 3) as sock:
                    sock.sendall(b"\x05\x01\x02")
                    assert receive(sock, 2) == b"\x05\x02"
                    user, pw = request["username"].encode(), password.encode()
                    sock.sendall(bytes([1, len(user)]) + user + bytes([len(pw)]) + pw)
                    assert receive(sock, 2) == bytes([1, 0 if valid else 1])
                    if valid:
                        # UDP ASSOCIATE must fail locally, never silently bypass VPN.
                        sock.sendall(bytes([5, 3, 0, 1, 192, 0, 2, 2, 0, 53]))
                        assert receive(sock, 10)[1] == 7
            lib.NimboNaiveResetConnections()
            assert response(lib.NimboNaiveStatus())["running"]
        finally:
            lib.NimboNaiveStop()
        assert not response(lib.NimboNaiveStatus())["running"]
        try:
            with socket.create_connection(("127.0.0.1", port), 1):
                raise AssertionError("Listener survived stop")
        except OSError:
            pass
        lib.NimboNaiveStop()
    check_dns_route(lib, response, receive)
    print("Native Naive C ABI: HTTPS/QUIC lifecycle, authentication, UDP rejection passed")


def check_dns_route(lib, response, receive):
    """Real Xray UDP-to-TCP DNS through authenticated SOCKS, with no internet.

    The test responder stands in for Naive's private loopback server; the
    encryption engine lifecycle is checked separately above. No OS DNS used.
    """
    root = Path(__file__).resolve().parents[2]
    lib.CGoInvoke.argtypes, lib.CGoInvoke.restype = [ctypes.c_char_p], ctypes.c_void_p

    def invoke(method, payload=None):
        result = response(lib.CGoInvoke(json.dumps(dict(apiVersion=3, method=method, payload=payload or {})).encode()))
        assert result["success"], (method, result)

    with tempfile.TemporaryDirectory(prefix="nimbo-naive-dns-") as directory:
        exe, fixture = Path(directory)/"swift-tests", Path(directory)/"route.json"
        subprocess.run(["swiftc", str(root/"iosApp/Shared/NimboNaiveConfiguration.swift"),
                        str(root/"iosApp/Tests/NaiveConfigurationTests.swift"), "-o", str(exe)], check=True)
        subprocess.run([str(exe), str(fixture)], check=True)
        config = json.loads(fixture.read_text())
    errors, seen = [], []
    # Simple query + NXDOMAIN reply. Only transport/authentication matters here.
    query = bytes.fromhex("123401000001000000000000076578616d706c6503636f6d0000010001")
    answer = query[:2] + bytes.fromhex("8183") + query[4:]
    with socket.socket() as server:
        server.bind(("127.0.0.1", 0)); server.listen(); server.settimeout(10)
        def serve():
            try:
                with server.accept()[0] as sock:
                    sock.settimeout(5)
                    version, length = receive(sock, 2)
                    assert version == 5 and 2 in receive(sock, length)
                    sock.sendall(bytes([5, 2]))
                    version, length = receive(sock, 2)
                    assert version == 1 and receive(sock, length) == b"contract"
                    assert receive(sock, receive(sock, 1)[0]) == b"local-dns-contract"
                    sock.sendall(bytes([1, 0]))
                    assert receive(sock, 4) == bytes([5, 1, 0, 1])
                    assert receive(sock, 6) == bytes([9, 9, 9, 9, 0, 53])
                    sock.sendall(bytes([5, 0, 0, 1, 127, 0, 0, 1, 0, 0]))
                    size = int.from_bytes(receive(sock, 2), "big")
                    assert receive(sock, size) == query
                    seen.append(True)
                    sock.sendall(len(answer).to_bytes(2, "big") + answer)
            except BaseException as error:
                errors.append(error)
        worker = threading.Thread(target=serve, daemon=True); worker.start()
        with socket.socket(type=socket.SOCK_DGRAM) as reservation:
            reservation.bind(("127.0.0.1", 0)); port = reservation.getsockname()[1]
        config["outbounds"][0]["settings"]["servers"][0]["port"] = server.getsockname()[1]
        config["log"] = {"loglevel":"none"}
        config["inbounds"] = [{"tag":"tun-in", "listen":"127.0.0.1", "port":port,
            "protocol":"dokodemo-door", "settings":{"address":"198.18.0.2", "port":53, "network":"udp"}}]
        try:
            invoke("runXray", {"xrayJson":json.dumps(config)})
            with socket.socket(type=socket.SOCK_DGRAM) as sock:
                sock.settimeout(8); sock.sendto(query, ("127.0.0.1", port))
                assert sock.recvfrom(512)[0] == answer
        finally:
            invoke("stopXray")
        worker.join(10)
        assert not worker.is_alive() and not errors and seen, "DNS did not traverse authenticated TCP proxy"
    print("Production Swift DNS route: UDP query -> Xray -> authenticated TCP SOCKS -> reply passed")


if __name__ == "__main__":
    main()
