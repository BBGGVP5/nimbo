#!/usr/bin/env python3
"""Install/lifecycle smoke test, ONLY on disposable GitHub-hosted Linux runners."""
import argparse
import json
import os
import re
from pathlib import Path
import socket
import struct
import subprocess
import tempfile
import time

from check_linux_payload import ROOT, MACHINES, extracted_appimage_helper, load_manifest, verify_helper

INSTALLED = Path("/usr/local/lib/nimbo/nimbo-svc")
UNIT = Path("/etc/systemd/system/nimbo-helper.service")
SOCKET = Path("/run/nimbo/helper.sock")
UID_FILE = Path("/etc/nimbo/helper.uid")


def ipc_protocol_version(source=None):
    # This test must agree with the exact source compiled into both clients.
    # Do not accept any positive protocol, which could mask a stale helper.
    if source is None:
        source = (ROOT / "crates/ipc/src/lib.rs").read_text(encoding="utf-8")
    versions = re.findall(r"^pub const PROTOCOL_VERSION: u32 = ([1-9][0-9]*);$", source, re.MULTILINE)
    assert len(versions) == 1, "Missing or ambiguous IPC source version"
    return int(versions[0])


def run(*args):
    subprocess.run([str(arg) for arg in args], check=True, timeout=120)


def receive(stream, size):
    result = b""
    while len(result) < size:
        chunk = stream.recv(size - len(result))
        if not chunk:
            raise RuntimeError("Helper closed its IPC connection")
        result += chunk
    return result


def request(command):
    with socket.socket(socket.AF_UNIX) as stream:
        stream.settimeout(2)
        stream.connect(str(SOCKET))
        payload = json.dumps({"type": command}).encode()
        stream.sendall(struct.pack(">I", len(payload)) + payload)
        length, = struct.unpack(">I", receive(stream, 4))
        assert 0 < length <= 2 * 1024 * 1024, "Invalid response length"
        return json.loads(receive(stream, length))


def check_ready(manifest):
    deadline = time.monotonic() + 10
    while True:
        try:
            pong = request("ping")
            assert pong["type"] == "pong" and pong["protocol"] == ipc_protocol_version()
            assert pong["service_version"] == manifest["version"]
            break
        except (OSError, RuntimeError):
            if time.monotonic() > deadline:
                raise
            time.sleep(0.1)
    verify_helper(INSTALLED.read_bytes(), INSTALLED.stat().st_mode, manifest)
    assert INSTALLED.stat().st_uid == 0, "Installed helper is not root-owned"
    assert f"ExecStart={INSTALLED}\n" in UNIT.read_text(), "Unit depends on a transient/user path"
    assert UID_FILE.read_text().strip() == str(os.getuid()), "Wrong IPC owner"
    status = request("get_status")
    assert status["type"] == "tun_state" and status["up"] is False
    assert request("ping")["type"] == "pong", "Status query interrupted helper"
    run("systemctl", "is-active", "nimbo-helper.service")


def main():
    # Fail closed before any privileged operation. Never run this on a user's host/WSL.
    assert os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted"
    assert os.uname().sysname == "Linux" and os.getuid() != 0
    assert not any(path.exists() for path in (INSTALLED, UNIT, SOCKET, UID_FILE)), "Runner already has Nimbo installed"
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", required=True, choices=MACHINES)
    args = parser.parse_args()
    manifest = load_manifest(args.target)
    bundle = ROOT / "target/release/bundle"
    deb, = (bundle / "deb").glob("*.deb")
    appimage, = (bundle / "appimage").glob("*.AppImage")
    package_installed = False
    try:
        run("sudo", "dpkg", "--install", deb)
        package_installed = True
        run("sudo", "/usr/bin/nimbo-svc", "--install-service", os.getuid())
        check_ready(manifest)
        with tempfile.TemporaryDirectory(prefix="nimbo-transient-helper-") as directory:
            helper = extracted_appimage_helper(appimage, Path(directory) / "payload")
            run("sudo", helper, "--install-service", os.getuid())
            check_ready(manifest)
        # Original extracted/AppImage path has vanished; the protected service still restarts.
        run("sudo", "systemctl", "restart", "nimbo-helper.service")
        check_ready(manifest)
        print("PASS: DEB installation, transient-source upgrade, independent restart and IPC status")
    finally:
        if INSTALLED.exists():
            run("sudo", INSTALLED, "--uninstall-service")
        if package_installed:
            run("sudo", "dpkg", "--remove", "nimbo")
    assert not any(path.exists() for path in (INSTALLED, UNIT, SOCKET, UID_FILE)), "Uninstall left runtime resources"
    print("PASS: uninstall cleanup; no tunnel/core/network configuration was requested")


if __name__ == "__main__":
    main()
