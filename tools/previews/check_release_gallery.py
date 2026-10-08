"""Validate published design asset bytes and truthful renderer provenance (stdlib only)."""
import hashlib
import json
from pathlib import Path
import struct
import re

ROOT = Path(__file__).resolve().parents[2]
manifest = json.loads((ROOT / "docs/previews/1.3.0-beta.1/manifest.json").read_text(encoding="utf-8"))
assert manifest["ui_source_commit"] == "a1a16fa80fe4390274927cb80b6b7e4db8ffc12f"
assert len(manifest["assets"]) == 14
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
for filename in ["desktop-home-device.png", "desktop-mihomo-device.png", "android-home-device.png", "android-settings-device.png", "ios-home-device.png", "ios-settings-device.png"]:
    assert f'src="./1.3.0-beta.1/{filename}"' in notes, f"Separate preview not embedded: {filename}"
    assert any(asset["path"].endswith("/" + filename) for asset in manifest["assets"])
assert "не на iPhone/Simulator" in notes and "не запись Live Activity" in notes
for relative in ["README.md", "docs/releases/1.3.0-beta.1.md", "docs/previews/README.md"]:
    presentation = (ROOT / relative).read_text(encoding="utf-8")
    assert "nimbo-poster-" not in presentation and "/poster/" not in presentation, relative
    assert len(re.findall(r'<img[^>]+-device\.png"', presentation)) == 6, relative
    for attributes in re.findall(r'<img[^>]+-device\.png"([^>]*)>', presentation):
        assert int(re.search(r'width="(\d+)"', attributes).group(1)) <= 300, relative
for relative in ["README.md", "docs/releases/1.3.0-beta.1.md"]:
    assert "src-tauri/icons/icon.png" in (ROOT / relative).read_text(encoding="utf-8")
print("PASS: 14 preview hashes/sizes, six compact linked screens, logo-only GitHub presentation and provenance")
