#!/usr/bin/env python3
"""Apply distinct offline entitlements to each bundle in xtool 1.22.2's IPA.

xtool 1.22.2 SignerImpl.swift deliberately filters the entitlement mapping to
the root app (#131). Procursus ldid signs the four executables independently.
The resulting package still requires normal Apple provisioning/re-signing.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path, PurePosixPath
import plistlib
import shutil
import stat
import subprocess
import tempfile
import zipfile

from xtool_verify import macho


def sign(ipa: Path, workspace: Path, output: Path, ldid: str) -> dict:
    if output.exists():
        raise ValueError(f"Output already exists: {output}")
    plan = json.loads((workspace / "xtool-prepare.json").read_text())
    result = {}
    with tempfile.TemporaryDirectory(prefix="nimbo-xtool-sign-") as directory:
        temporary = Path(directory)
        with zipfile.ZipFile(ipa) as archive:
            names = set()
            for item in archive.infolist():
                path = PurePosixPath(item.filename)
                mode = item.external_attr >> 16
                if (path.is_absolute() or ".." in path.parts or item.filename in names
                        or not path.parts or path.parts[0] != "Payload" or stat.S_ISLNK(mode)):
                    raise ValueError(f"Invalid IPA entry: {item.filename}")
                names.add(item.filename)
                archive.extract(item, temporary)
                extracted = temporary / item.filename
                if extracted.is_file():
                    os.chmod(extracted, stat.S_IMODE(mode) or 0o644)
        app = temporary / "Payload/Nimbo.app"
        for signature in list(app.rglob("_CodeSignature")):
            if not signature.resolve().is_relative_to(temporary):
                raise ValueError("Signature path escapes temporary package")
            if signature.is_dir():
                shutil.rmtree(signature)
        # Embedded resource seals from xtool precede the executable edits.
        # Re-signing tools regenerate bundle seals together with provisioning.
        for name, target in sorted(plan["targets"].items(), key=lambda pair: pair[0] == "Nimbo"):
            bundle = app if name == "Nimbo" else app / "PlugIns" / (name + ".appex")
            info = plistlib.loads((bundle / "Info.plist").read_bytes())
            if info["CFBundleIdentifier"] != target["bundle_id"] or info["CFBundleExecutable"] != name:
                raise ValueError(f"Unexpected bundle identity: {name}")
            entitlement_path = target.get("entitlements_path")
            expected = plistlib.loads((workspace / entitlement_path).read_bytes()) if entitlement_path else {}
            entitlement_file = temporary / (name + ".entitlements")
            entitlement_file.write_bytes(plistlib.dumps(expected))
            executable = bundle / name
            os.chmod(executable, 0o755)
            subprocess.run([ldid, "-S" + str(entitlement_file), str(executable)], check=True)
            actual = macho(executable.read_bytes())
            if actual["entitlements"] != expected:
                raise ValueError(f"ldid did not preserve exact entitlements for {name}")
            result[name] = actual
        output.parent.mkdir(parents=True, exist_ok=True)
        with output.open("xb") as stream, zipfile.ZipFile(stream, "w", zipfile.ZIP_DEFLATED) as archive:
            for path in sorted((temporary / "Payload").rglob("*")):
                if path.is_file():
                    archive.write(path, path.relative_to(temporary).as_posix())
    return {"ipa": str(output), "offlineSigner": "Procursus ldid", "bundles": result}


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("ipa", type=Path)
    parser.add_argument("--workspace", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--ldid", default="ldid")
    parser.add_argument("--receipt", type=Path)
    args = parser.parse_args()
    result = sign(args.ipa, args.workspace, args.output, args.ldid)
    text = json.dumps(result, ensure_ascii=False, indent=2)
    if args.receipt:
        args.receipt.write_text(text + "\n")
    print(text)


if __name__ == "__main__":
    main()
