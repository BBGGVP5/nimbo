#!/usr/bin/env python3
"""Publish a Nimbo release post to the Telegram topic from docs/releases/<version>.md.

    # preview: writes the request JSON, sends nothing
    python tools/release/telegram-post.py 1.3.1 --dry-run
    # send to the log chat first, then to the release topic
    python tools/release/telegram-post.py 1.3.1 --token-file path/to/config.py --chat <log-chat-id> --topic <id>
    python tools/release/telegram-post.py 1.3.1 --token-file path/to/config.py

The bot token comes from NIMBO_TG_BOT_TOKEN or --token-file (a text file with
the token, or a Python config with TOKEN = "..."). It is never printed.
The poster defaults to docs/store/<version>/post-banner.jpg on main, so commit
and push it before sending. Rich messages cannot be edited: to fix a post,
send a new one and delete the old one (--delete <message_id>).
"""
import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
REPO = "BBGGVP5/nimbo"
SITE = "https://nimboapp.pw/"
CHAT = -1002561763286
TOPIC = 6135
EMOJI = {
    "новый дизайн": "🎨", "дизайн": "🎨", "новые возможности": "🆕", "профили mihomo": "🧩",
    "подключение и стабильность": "⚡", "стабильность": "⚡", "размер приложения": "📦",
    "ядра": "🧠", "другие исправления": "🛠", "исправления": "🛠", "проверка сети": "📡",
}
SKIP = {"установка"}
COLLAPSE = {"подключение и стабильность", "другие исправления", "исправления"}


def inline(text):
    """Markdown emphasis, backticks and links are flattened to plain text."""
    text = re.sub(r"\[([^\]]+)\]\([^)]+\)", r"", text)
    return re.sub(r"\*\*|`", "", text)


def paragraph(text):
    return {"type": "paragraph", "text": text}


def heading(text, size=2):
    return {"type": "heading", "text": text, "size": size}


def bullets(items):
    return {"type": "list", "items": [{"label": "•", "blocks": [paragraph(inline(i))]} for i in items]}


def parse_notes(markdown):
    """Returns (intro, [(title, [paragraphs], [bullets])])."""
    intro, sections, current = [], [], None
    for raw in markdown.splitlines():
        line = raw.strip()
        if not line or line.startswith("# ") or line.startswith("|"):
            continue
        if line.startswith("## "):
            current = (line[3:].strip(), [], [])
            sections.append(current)
        elif line.startswith(("- ", "* ")):
            if current:
                current[2].append(line[2:].strip())
        elif current:
            current[1].append(line)
        else:
            intro.append(line)
    return " ".join(intro), sections


def downloads(version):
    beta = "-" in version
    dl = f"https://github.com/{REPO}/releases/download/v{version}/"
    url = lambda text, name: {"type": "url", "text": text, "url": dl + name}
    cell = lambda text, header=False: {"text": text, "valign": "middle",
                                       **({"is_header": True, "align": "center"} if header else {"align": "left"})}
    rows = [
        ("🤖 Android", url("APK · ARM64", f"Nimbo_v{version}_arm64_v8a.apk")),
        ("🤖 Android, универсальный", url("APK · Universal", f"Nimbo_v{version}_universal.apk")),
        ("🍏 iPhone и iPad", url("IPA для переподписи", f"Nimbo_v{version}_ios_resignable.ipa")),
        ("🪟 Windows", url("Установщик · x64", f"NimboSetup_{version}_x64.exe")),
        ("🪟 Windows, ARM64", url("Установщик · ARM64", f"NimboSetup_{version}_arm64.exe")),
        ("🐧 Linux", url("Установщик · x64", f"NimboSetup_{version}_x64")),
        ("🐧 Linux, пакеты", [url("deb", f"Nimbo_{version}_linux_x64.deb"), " · ",
                              url("rpm", f"Nimbo_{version}_linux_x64.rpm"), " · ",
                              url("AppImage", f"Nimbo_{version}_linux_x64.AppImage")]),
    ]
    table = {"type": "table", "is_bordered": True,
             "cells": [[cell("Платформа", True), cell("Файл", True)], *[[cell(a), cell(b)] for a, b in rows]]}
    markup = {"inline_keyboard": [
        [{"text": "📥 Скачать Nimbo", "url": f"https://github.com/{REPO}/releases/tag/v{version}"},
         {"text": "🌐 Сайт", "url": SITE}],
        [{"text": "🤖 Android", "url": dl + f"Nimbo_v{version}_arm64_v8a.apk"},
         {"text": "🪟 Windows", "url": dl + f"NimboSetup_{version}_x64.exe"},
         {"text": "🐧 Linux", "url": dl + f"NimboSetup_{version}_x64"}],
    ]}
    note = ("Бета-версия: приходит тем, кто выбрал канал «Бета». " if beta else
            "Это стабильная версия — обновление приходит всем. ") + \
        "Ставится поверх предыдущей версии, подписки и настройки сохраняются."
    return table, markup, note


def build(version, notes_path, poster):
    intro, sections = parse_notes(notes_path.read_text(encoding="utf-8-sig"))
    blocks = [{"type": "photo", "photo": {"type": "photo", "media": poster}},
              heading(f"Nimbo {version}", 1)]
    if intro:
        blocks.append(paragraph({"type": "italic", "text": re.sub(r"\*\*|`", "", intro)}))
    blocks.append({"type": "divider"})
    hidden = []
    for title, paras, items in sections:
        low = title.lower()
        if low in SKIP:
            continue
        body = [paragraph(inline(p)) for p in paras] + ([bullets(items)] if items else [])
        if low in COLLAPSE:
            hidden += [heading(title, 3), *body]
        else:
            blocks += [heading(f"{EMOJI.get(low, '•')} {title}"), *body]
    if hidden:
        blocks.append({"type": "details", "summary": "🛠 Что ещё поправлено", "blocks": hidden})
    table, markup, note = downloads(version)
    rel = f"https://github.com/{REPO}/releases/tag/v{version}"
    blocks += [{"type": "divider"}, heading("📥 Скачать"), table, paragraph(note),
               {"type": "footer", "text": [f"Nimbo {version} · ", {"type": "url", "text": "nimboapp.pw", "url": SITE},
                                           " · ", {"type": "url", "text": "GitHub Release", "url": rel}]}]
    return blocks, markup


def token(args):
    if os.environ.get("NIMBO_TG_BOT_TOKEN"):
        return os.environ["NIMBO_TG_BOT_TOKEN"].strip()
    if not args.token_file:
        raise SystemExit("Set NIMBO_TG_BOT_TOKEN or pass --token-file")
    text = Path(args.token_file).read_text(encoding="utf-8-sig")
    match = re.search(r"""TOKEN\s*=\s*["']([^"']+)["']""", text)
    return (match.group(1) if match else text).strip()


def call(bot_token, method, payload):
    request = urllib.request.Request(f"https://api.telegram.org/bot{bot_token}/{method}",
                                     data=json.dumps(payload).encode(), headers={"Content-Type": "application/json"})
    # The local dev proxy is often down; Telegram is reached directly.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(request, timeout=60) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        return json.load(error)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("version")
    parser.add_argument("--notes", type=Path, help="default: docs/releases/<version>.md")
    parser.add_argument("--poster", help="image URL; default: post-banner.jpg of this version on main")
    parser.add_argument("--chat", type=int, default=CHAT)
    parser.add_argument("--topic", type=int, default=TOPIC)
    parser.add_argument("--token-file")
    parser.add_argument("--dry-run", action="store_true", help="write the request to telegram-post-<v>.json only")
    parser.add_argument("--delete", type=int, metavar="MESSAGE_ID", help="delete an older post after sending")
    args = parser.parse_args()
    version = args.version.removeprefix("v")
    notes = args.notes or ROOT / "docs/releases" / f"{version}.md"
    poster = args.poster or f"https://raw.githubusercontent.com/{REPO}/main/docs/store/{version}/post-banner.jpg"
    blocks, markup = build(version, notes, poster)
    payload = {"chat_id": args.chat, "message_thread_id": args.topic,
               "rich_message": {"blocks": blocks}, "reply_markup": markup}
    if args.dry_run:
        out = Path(f"telegram-post-{version}.json")
        out.write_text(json.dumps(payload, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"wrote {out} ({len(blocks)} blocks)")
        return
    bot = token(args)
    result = call(bot, "sendRichMessage", payload)
    if not result.get("ok"):
        raise SystemExit(f"Telegram refused the post: {result.get('description')}")
    print(f"sent message {result['result']['message_id']}")
    if args.delete:
        gone = call(bot, "deleteMessage", {"chat_id": args.chat, "message_id": args.delete})
        print("deleted old post" if gone.get("ok") else f"could not delete: {gone.get('description')}")


if __name__ == "__main__":
    sys.exit(main())
