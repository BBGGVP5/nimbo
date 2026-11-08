#!/usr/bin/env python3
"""Verify and unpack the source-pinned dependency export for a local xtool build."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import tarfile


def digest(path: Path) -> str:
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            value.update(chunk)
    return value.hexdigest()


def scoped(root: Path, name: str) -> Path:
    relative = PurePosixPath(name)
    if relative.is_absolute() or ".." in relative.parts or "\\" in name:
        raise ValueError(f"Invalid manifest path: {name}")
    path = root / name
    if not path.resolve().is_relative_to(root.resolve()):
        raise ValueError(f"Manifest path escapes export: {name}")
    return path


def verify(dependencies: Path, source: Path, commit: str) -> dict:
    manifest = json.loads((dependencies / "manifest.json").read_text())
    if manifest.get("kind") != "nimbo-ios-xtool-dependencies" or manifest.get("schema_version") != 1:
        raise ValueError("Unsupported dependency export")
    if manifest.get("git_commit") != commit or manifest.get("git_dirty"):
        raise ValueError("Dependency export differs from the requested clean source commit")
    if manifest.get("slices") != ["iphoneos-arm64"]:
        raise ValueError("Expected device-only dependencies")
    files = manifest["payload_files"]
    if not files:
        raise ValueError("Empty payload manifest")
    for name, expected in files.items():
        path = scoped(dependencies, name)
        if expected["type"] == "symlink":
            if not path.is_symlink() or os.readlink(path) != expected["target"]:
                raise ValueError(f"Changed dependency link: {name}")
        elif expected["type"] == "file":
            if path.is_symlink() or not path.is_file() or digest(path) != expected["sha256"]:
                raise ValueError(f"Missing or altered dependency: {name}")
        else:
            raise ValueError(f"Unknown dependency entry type: {name}")
    actual = {p.relative_to(dependencies).as_posix() for p in dependencies.rglob("*")
              if p.is_file() or p.is_symlink()}
    if actual != set(files) | {"manifest.json"}:
        raise ValueError("Unexpected or missing files in dependency export")
    sources = manifest["source_fingerprints"]
    if not sources:
        raise ValueError("Missing source fingerprints")
    for group in sources.values():
        for name, expected in group["files"].items():
            path = scoped(source, name)
            if not path.is_file() or digest(path) != expected:
                raise ValueError(f"Export built from different source input: {name}")
    return {"commit": commit, "version": manifest["version"], "build": manifest["build_number"],
            "payloadFilesVerified": len(files), "sourceGroupsVerified": len(sources),
            "corePins": manifest["core_pins"]}


def unpack(archive: Path, checksum: Path, output: Path) -> Path:
    expected = checksum.read_text().split()[0].lower()
    if len(expected) != 64 or digest(archive) != expected:
        raise ValueError("Dependency archive SHA-256 mismatch")
    if output.exists() or output.is_symlink():
        raise ValueError(f"Output already exists: {output}")
    with tarfile.open(archive, "r:gz") as stream:
        names = set()
        for member in stream.getmembers():
            name = PurePosixPath(member.name)
            if (not name.parts or name.parts[0] != "dependencies" or name.is_absolute()
                    or ".." in name.parts or member.name in names):
                raise ValueError(f"Invalid dependency archive member: {member.name}")
            names.add(member.name)
        output.mkdir(parents=True)
        stream.extractall(output, filter="data")
    return output / "dependencies"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--archive", required=True, type=Path)
    parser.add_argument("--checksum", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--source-root", required=True, type=Path)
    parser.add_argument("--commit", required=True)
    parser.add_argument("--receipt", type=Path)
    args = parser.parse_args()
    root = unpack(args.archive, args.checksum, args.output)
    result = verify(root, args.source_root, args.commit)
    text = json.dumps(result, ensure_ascii=False, indent=2)
    if args.receipt:
        args.receipt.write_text(text + "\n", encoding="utf-8")
    print(text)


if __name__ == "__main__":
    main()
