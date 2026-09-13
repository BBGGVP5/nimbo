"""Stage reviewed CLI core payloads for packaging; never install or run them.

python apps/ui/scripts/stage-xray.py --target x86_64-pc-windows-msvc
Main's custom installer must consume these pins instead of its old constants.
"""
import argparse
import hashlib
import io
import json
from pathlib import Path
import urllib.request
import uuid
import zipfile

MANIFEST = Path(__file__).resolve().parents[1] / "src-tauri/xray-release.json"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def verify(directory, asset):
    return all((directory / name).is_file() and digest((directory / name).read_bytes()) == expected
               for name, expected in asset["files"].items())


def stage(target, output=None, verify_only=False):
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    asset = next((a for a in manifest["assets"] if a["target"] == target), None)
    if asset is None:
        raise ValueError(f"Unsupported Xray target: {target}")
    directory = Path(output) if output else MANIFEST.parents[3] / "target/xray" / target
    if verify(directory, asset):
        return directory
    if verify_only:
        raise ValueError(f"Xray {manifest['version']} payload is missing or mismatched: {directory}")
    url = f"https://github.com/XTLS/Xray-core/releases/download/{manifest['tag']}/{asset['archive']}"
    data = urllib.request.urlopen(url, timeout=90).read()
    if digest(data) != asset["sha256"]:
        raise ValueError("Xray archive SHA-256 mismatch")
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        files = {name: archive.read(name) for name in asset["files"]}
    if any(digest(data) != asset["files"][name] for name, data in files.items()):
        raise ValueError("Xray payload SHA-256 mismatch")
    directory.mkdir(parents=True, exist_ok=True)
    for name, data in files.items():
        temporary = directory / (name + "." + uuid.uuid4().hex + ".partial")
        try:
            temporary.write_bytes(data)
            if asset["os"] == "linux" and name == "xray":
                temporary.chmod(0o755)
            temporary.replace(directory / name)
        finally:
            temporary.unlink(missing_ok=True)
    if not verify(directory, asset):
        raise ValueError("Staged Xray payload verification failed")
    return directory


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", required=True)
    parser.add_argument("--output")
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    print(stage(args.target, args.output, args.verify_only))
