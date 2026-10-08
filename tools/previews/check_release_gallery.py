"""Validate published design asset bytes and truthful renderer provenance (stdlib only)."""
import hashlib
import json
from pathlib import Path
import struct

ROOT = Path(__file__).resolve().parents[2]
manifest = json.loads((ROOT / "docs/previews/1.3.0-beta.1/manifest.json").read_text(encoding="utf-8"))
assert manifest["ui_source_commit"] == "a1a16fa80fe4390274927cb80b6b7e4db8ffc12f"
assert len(manifest["assets"]) == 10
for asset in manifest["assets"]:
    path = (ROOT / asset["path"]).resolve()
    assert path.is_relative_to(ROOT) and path.suffix == ".png"
    data = path.read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    assert list(struct.unpack(">II", data[16:24])) == asset["pixels"]
    assert hashlib.sha256(data).hexdigest() == asset["sha256"], str(path)
    assert asset["kind"] in {"react-page-fixture", "shared-compose-jvm", "imagegen-screen-edit", "generated-poster", "code-native-device-mockup"}
    assert asset["device_screenshot"] is False
notes = (ROOT / "docs/previews/README.md").read_text(encoding="utf-8")
assert "не на iPhone/Simulator" in notes and "не запись Live Activity" in notes
poster_notes = (ROOT / "docs/poster/README.md").read_text(encoding="utf-8")
assert "image_gen" in poster_notes and "Версия, слово «бета»" in poster_notes
print("PASS: 10 PNG sizes/hashes, confined paths, renderer provenance, mobile preview limitations")
