"""Fail closed before linking a narrowly scoped pinned Cronet iOS compatibility object."""
import argparse
import hashlib
from pathlib import Path
import subprocess

HASHES = {
    "ios_arm64": "12a56dbb672f14b8cabe0c75be0fa72a1cecb4866658d5d4328700d461d1f133",
    "ios_arm64_simulator": "6c8e3596de7e01de660dbbd23e48e73200585d9e445c5a77c00758019649efe5",
    "ios_amd64_simulator": "2604e0752b1ab9b61401cea53eb56317fcbe8241c865ea9643408c32ba5ae5b2",
}
MISSING = "__ZN4base17MessagePumpKqueue18InitializeFeaturesEv"
IO_CONSTRUCTOR = "__ZN4base19MessagePumpIOSForIOC1Ev"

def verify_symbols(symbols):
    kqueue = [line.split() for line in symbols.splitlines() if "MessagePumpKqueue" in line]
    if kqueue != [["U", MISSING]]:
        raise ValueError("Cronet kqueue implementation changed; remove/review compatibility object")
    if not any(line.split()[-2:] == ["T", IO_CONSTRUCTOR] for line in symbols.splitlines()):
        raise ValueError("Expected real iOS CFRunLoop I/O implementation is absent")

def verify_archive(path, variant, nm):
    if hashlib.sha256(path.read_bytes()).hexdigest() != HASHES[variant]:
        raise ValueError("Cronet archive changed; compatibility patch requires source review")
    symbols = subprocess.run([nm, str(path)], check=True, capture_output=True, text=True).stdout
    verify_symbols(symbols)

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", type=Path)
    parser.add_argument("variant", choices=HASHES)
    parser.add_argument("--nm", default="nm")
    args = parser.parse_args()
    verify_archive(args.archive, args.variant, args.nm)
    print("Pinned Cronet iOS CFRunLoop / unused kqueue feature guard verified")
