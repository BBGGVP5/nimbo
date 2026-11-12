#!/usr/bin/env python3
"""Compare pinned VPN cores and the Go toolchain with their latest upstream releases.

Prints a Markdown report; exits 0 either way. With --issue-body-file the
report is also written to a file for the weekly workflow, which opens or
refreshes a single "Обновления ядер" issue. Updating a core is still a
reviewed change (AAR rebuild, hashes, notices), so this only reports.
"""
import argparse
import json
import os
import sys
import re
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def fetch(url):
    headers = {"Accept": "application/vnd.github+json", "User-Agent": "nimbo-core-check"}
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token and url.startswith("https://api.github.com/"):
        headers["Authorization"] = f"Bearer {token}"
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({})) if os.environ.get("NIMBO_NO_PROXY") \
        else urllib.request.build_opener()
    with opener.open(urllib.request.Request(url, headers=headers), timeout=30) as response:
        return json.load(response)


def text(rel):
    return (ROOT / rel).read_text(encoding="utf-8-sig")


def norm(version):
    return version.strip().removeprefix("v")


def key(version):
    return [int(part) if part.isdigit() else part for part in re.split(r"[.\-]", norm(version))]


def versionlike(version):
    return bool(re.match(r"v?\d+(\.\d+)+", version.strip()))


def newer(candidate, pinned):
    try:
        return key(candidate) > key(pinned)
    except TypeError:  # 1.3.0-rc.3 vs 1.3.0: compare the numeric part only
        numeric = lambda v: [int(p) for p in re.findall(r"\d+", norm(v).split("-", 1)[0])]
        return numeric(candidate) > numeric(pinned)


def releases(repo):
    rows = fetch(f"https://api.github.com/repos/{repo}/releases?per_page=30")
    rows = [r for r in rows if not r.get("draft")]
    stable = next((r["tag_name"] for r in rows if not r.get("prerelease")), None)
    newest = rows[0]["tag_name"] if rows else None
    return stable, newest


def tags(repo, prefix):
    rows = fetch(f"https://api.github.com/repos/{repo}/tags?per_page=100")
    names = [r["name"] for r in rows if r["name"].startswith(prefix)]
    return max(names, key=key) if names else None


def pins():
    xray = json.loads(text("apps/ui/src-tauri/xray-release.json"))["version"]
    mihomo = re.search(r"github\.com/metacubex/mihomo (v[\d.]+)", text("tools/native/mihomo-core/go.mod")).group(1)
    awg = re.search(r"amneziawg-go/v3 (v[\d.]+)", text("tools/native/awg-core/go.mod")).group(1)
    naive = json.loads(text("app/src/main/assets/third_party/naiveproxy.json"))["version"]
    trust = re.search(r"(?m)^Version: (v[\d.]+)", text("apps/ui/src-tauri/resources/trusttunnel/README.txt")).group(1)
    go = re.search(r"(?m)^go (\d+\.\d+\.\d+)", text("tools/native/mihomo-core/go.mod")).group(1)
    return [
        ("Xray-core", "XTLS/Xray-core", xray, "releases"),
        ("Mihomo", "MetaCubeX/mihomo", mihomo, "releases"),
        ("AmneziaWG (Go)", "amnezia-vpn/amneziawg-go", awg, "tags:v3."),
        ("NaiveProxy", "klzgrad/naiveproxy", naive, "releases"),
        ("TrustTunnel client", "TrustTunnel/TrustTunnelClient", trust, "releases"),
        ("Go toolchain", "golang", go, "go"),
    ]


def latest_go(pinned):
    line = ".".join(pinned.split(".")[:2])
    rows = fetch("https://go.dev/dl/?mode=json&include=all")
    same = [r["version"].removeprefix("go") for r in rows if r.get("stable") and r["version"].startswith(f"go{line}.")]
    newest = next((r["version"].removeprefix("go") for r in rows if r.get("stable")), None)
    return (max(same, key=key) if same else None), newest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--issue-body-file", type=Path)
    args = parser.parse_args()
    lines = ["| Компонент | В Nimbo | Стабильная | Новейшая | |", "|---|---|---|---|---|"]
    outdated = 0
    for name, repo, pinned, mode in pins():
        try:
            if mode == "releases":
                stable, newest = releases(repo)
            elif mode == "go":
                stable, newest = latest_go(pinned)
            else:
                stable = newest = tags(repo, mode.split(":", 1)[1])
        except Exception as error:  # report, never fail the weekly run
            lines.append(f"| {name} | `{pinned}` | — | — | не удалось проверить: {type(error).__name__} |")
            continue
        stable = stable if stable and versionlike(stable) else None
        newest = newest if newest and versionlike(newest) else None
        behind = bool(stable) and newer(stable, pinned)
        preview = bool(newest) and not behind and newer(newest, pinned)
        outdated += behind
        mark = "⬆️ есть новее" if behind else "🧪 есть пре-релиз" if preview else "✅"
        lines.append(f"| {name} | `{pinned}` | `{stable or '—'}` | `{newest or '—'}` | {mark} |")
    head = (f"Устарело компонентов: **{outdated}**." if outdated else "Все ядра актуальны.")
    report = "\n".join([head, "", *lines, "",
                        "Обновление ядра — отдельная проверяемая правка: пересборка libxray.aar и Mihomo, "
                        "хеши, лицензии. Xray выпускает пре-релизы и иногда удаляет их — пиновать только "
                        "версии с архивом и digest на GitHub. gvisor для iOS держать на 20261010."])
    sys.stdout.buffer.write((report + chr(10)).encode("utf-8"))
    if args.issue_body_file:
        args.issue_body_file.write_text(report + "\n", encoding="utf-8")
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as out:
            out.write(f"outdated={outdated}\n")


if __name__ == "__main__":
    main()
