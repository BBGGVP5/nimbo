#!/usr/bin/env python3
"""Portable Apple regression runner; never starts a system VPN."""
from pathlib import Path
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix="nimbo-reconnect-") as directory:
    for name, sources in [
        ("start", ["Nimbo/NimboVpnStartAttempt.swift", "Tests/VpnStartAttemptTests.swift"]),
        ("commands", ["Shared/NimboVpnCommandQueue.swift", "Tests/VpnCommandQueueTests.swift"]),
        ("core", ["Shared/NimboAWGConfiguration.swift", "Shared/NimboNaiveConfiguration.swift",
                  "Nimbo/NimboCoreSelection.swift", "Tests/CoreSelectionTests.swift"]),
    ]:
        exe = str(Path(directory) / name)
        subprocess.run(["xcrun", "swiftc", "-parse-as-library", *[str(ROOT / "iosApp" / p) for p in sources], "-o", exe], check=True, timeout=120)
        subprocess.run([exe], check=True, timeout=30)
print("Portable startup cancellation/admission policies passed; no iPhone runtime claimed")
