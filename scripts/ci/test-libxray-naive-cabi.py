#!/usr/bin/env python3
"""Exercise the real merged Naive C ABI. No remote connection or user secrets."""
import ctypes
import json
from pathlib import Path
import socket
import sys


def main():
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
    print("Native Naive C ABI: HTTPS/QUIC lifecycle, authentication, UDP rejection passed")


if __name__ == "__main__":
    main()
