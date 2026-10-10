#!/usr/bin/env python3
"""Stage verified build assets as a DRAFT release; never publish or rewrite an old release."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import quote

ROOT = Path(__file__).resolve().parents[2]


def gh(*args, missing=False):
    result = subprocess.run(["gh", *args], capture_output=True, text=True, encoding="utf-8")
    if result.returncode:
        if missing and ("HTTP 404" in result.stderr or "release not found" in result.stderr.lower()):
            return None
        raise RuntimeError(result.stderr.strip() or "GitHub command failed")
    return result.stdout


def tag_commit(repository, tag):
    result = gh("api", f"repos/{repository}/git/ref/tags/{quote(tag, safe='')}", missing=True)
    if result is None:
        return None
    obj = json.loads(result)["object"]
    for _ in range(8):
        if obj["type"] == "commit":
            return obj["sha"]
        if obj["type"] != "tag":
            raise RuntimeError("Release tag does not resolve to a commit")
        obj = json.loads(gh("api", f"repos/{repository}/git/tags/{obj['sha']}"))["object"]
    raise RuntimeError("Release tag nesting exceeds the verification limit")


def stage(repository, tag, commit, assets, root=ROOT):
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
        raise ValueError("Invalid repository")
    if not re.fullmatch(r"v\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?", tag) or not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise ValueError("Exact version tag and workflow commit required")
    assets = Path(assets).resolve()
    entries = sorted(assets.rglob("*"))
    if any(file.is_symlink() for file in entries):
        raise ValueError("Release assets must not contain symlinks")
    files = [file for file in entries if file.is_file() and file.suffix != ".sha256"]
    if not files or len({file.name for file in files}) != len(files):
        raise ValueError("Release assets are empty or contain ambiguous basenames")
    version = tag[1:]
    notes = root / "docs/releases" / f"{version}.md"
    existing = gh("release", "view", tag, "-R", repository, "--json", "isDraft,targetCommitish,tagName", missing=True)
    if existing is not None and not json.loads(existing)["isDraft"]:
        raise RuntimeError("Refusing to modify a published release")
    target = tag_commit(repository, tag)
    if target is not None and target != commit:
        raise RuntimeError("Release tag points to another commit; no tag/release is rewritten")
    args = ["--notes-file", str(notes)] if notes.is_file() else ["--generate-notes"]
    if existing is None:
        flags = ["--draft", "--target", commit, "--title", f"Nimbo {version}"]
        if re.search(r"-(alpha|beta|rc)(?:[.-]|$)", version):
            flags.append("--prerelease")
        gh("release", "create", tag, "-R", repository, *flags, *args)
    else:
        # A draft may not have created its tag yet. Set an exact commit for that
        # pending ref; never move any existing (including annotated) tag.
        edit = ["--title", f"Nimbo {version}"]
        if target is None:
            edit += ["--target", commit]
        if notes.is_file():
            edit += args
        gh("release", "edit", tag, "-R", repository, *edit)
    # A pre-existing tag makes --target ineffective. Verify the actual ref,
    # including annotated tags, before any asset upload.
    actual_tag = tag_commit(repository, tag)
    current = json.loads(gh("release", "view", tag, "-R", repository, "--json", "isDraft,targetCommitish,tagName"))
    if actual_tag is not None and actual_tag != commit:
        raise RuntimeError("Release tag does not match the compiled workflow commit")
    if actual_tag is None and (current.get("targetCommitish") != commit or current.get("tagName") != tag):
        raise RuntimeError("Pending draft tag does not target the compiled workflow commit")
    if not current["isDraft"]:
        raise RuntimeError("Release is no longer a draft; stop before upload")
    upload = []
    for file in files:
        digest = hashlib.sha256()
        with file.open("rb") as stream:
            for chunk in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(chunk)
        checksum = file.with_name(file.name + ".sha256")
        checksum.write_text(f"{digest.hexdigest()}  {file.name}\n", encoding="utf-8", newline="\n")
        upload.extend([str(file), str(checksum)])
    # Replacement is limited to a verified draft, never immutable release history.
    gh("release", "upload", tag, "-R", repository, "--clobber", *upload)
    print(f"Staged DRAFT {tag} from {commit}; public publication still requires confirmation")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets", type=Path, default=Path("release-assets"))
    args = parser.parse_args()
    stage(os.environ["GITHUB_REPOSITORY"], os.environ["TAG"], os.environ["GITHUB_SHA"], args.assets)
