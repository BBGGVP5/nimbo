"""Publish stable 1.2.0 once using the established Beta 5 bot/destination.

Default is validation only. Never automatically retry an uncertain delivery.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path

import requests

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "artifacts/telegram/1.2.0"


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


previous = load_module("previous_publisher", ROOT / "scripts/publish-beta5-telegram-rich.py")
RELEASE = previous.RELEASE.replace("1.2.0-beta.5", "1.2.0")
DOWNLOAD = previous.DOWNLOAD.replace("1.2.0-beta.5", "1.2.0")
POSTER = previous.POSTER.replace("1.2.0-beta.5", "1.2.0")
TABLE = re.search(r"<table bordered>.*?</table>", previous.HTML, re.S).group(0).replace("1.2.0-beta.5", "1.2.0")
KEYBOARD = json.loads(json.dumps(previous.KEYBOARD).replace("1.2.0-beta.5", "1.2.0"))

HTML = f"""<img src="{POSTER}"/>
<h1>Nimbo 1.2.0</h1>
<p><i>Стабильный выпуск для Android, iOS, Windows и Linux. Все изменения Beta 1–5 и новые исправления — в одном обновлении.</i></p>
<hr/>
<h2>📶 Nimbo Ping — через каждый сервер</h2>
<ul>
<li>Проверка серверов подписки по отдельности, без переключения текущего подключения. Наш метод стоит первым и выбран по умолчанию, если вы ещё не меняли способ проверки.</li>
<li>Значение с <b>≈</b> — оценка: время HTTP GET ÷ 3,3. Обычный HTTP GET показывает полное время запроса.</li>
<li>Цифры, шкала, шкала с цифрами или точки. На Android таймаут можно ввести в секундах, а результат по-прежнему отображается в миллисекундах.</li>
</ul>
<h2>⚙️ Обновлённые ядра</h2>
<p><b>Xray / LibXray 26.9.9</b> и поддержка <b>AmneziaWG 3.1</b> на Android, iOS и компьютере.</p>
<h2>🏠 Главная и подписки</h2>
<ul>
<li>Убраны пустые блоки мониторинга без подключения; графики новой сессии не продолжают старые значения.</li>
<li>Запасные домены подписок, свои модули маршрутизации и синхронизация между устройствами.</li>
<li>Исправлены длинные подписи, логотипы провайдеров и отображение бессрочных подписок.</li>
</ul>
<h2>⚡ Быстрое управление — из бет в релиз</h2>
<ul>
<li><b>Windows и Linux:</b> подключение и избранные серверы прямо из трея, восстановление после сна и меньше фонового опроса.</li>
<li><b>Android:</b> VPN-плитка, быстрый выбор сервера и раздел «Работа в фоне» с настройками уведомлений и батареи.</li>
<li><b>iOS:</b> действия «Команд», элемент Пункта управления и диагностика готовности VPN-расширения.</li>
</ul>
<p><b>В 1.2.0 остаётся привычный дизайн.</b> Полный редизайн пока отдельно, в превью.</p>
<details><summary>Что важно знать о пинге</summary>
<p>Исправлена потеря результата при завершении проверки и добавлена диагностика. На отдельных LTE-конфигурациях пинг всё ещё может быть недоступен при рабочем VPN.</p>
</details>
<hr/>
<h2>📥 Скачать</h2>
{TABLE}
<p>IPA требует переподписи с правами Network Extension. Для Linux проще использовать установщик x64 со службой TUN.</p>
<footer>Nimbo 1.2.0 · <a href="{RELEASE}">Полный список изменений, все сборки и SHA-256 на GitHub</a></footer>"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--publish", action="store_true")
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    receipt = OUT / "published.json"
    attempt = OUT / "delivery-started.json"
    if args.publish and (receipt.exists() or attempt.exists()):
        raise RuntimeError("Delivery already attempted; inspect receipt before any resend")
    old = json.loads((ROOT / "artifacts/telegram/1.2.0-beta.5/published-short-resend.json").read_text(encoding="utf-8"))
    assert old["chat"]["id"] == previous.CHAT_ID
    assert old["message_thread_id"] == previous.TOPIC_ID == 6135 and old.get("rich_message")
    release = json.loads(subprocess.check_output([
        "gh", "release", "view", "v1.2.0", "-R", "BBGGVP5/nimbo",
        "--json", "isDraft,isPrerelease,assets,url"], encoding="utf-8"))
    assert not release["isDraft"] and not release["isPrerelease"] and release["url"] == RELEASE
    names = {a["name"] for a in release["assets"]}
    links = re.findall(re.escape(DOWNLOAD) + r'/([^"<>\s]+)', HTML)
    assert len(links) == len(set(links)) == 14
    assert all(n in names and n + ".sha256" in names for n in links)
    for row in KEYBOARD["inline_keyboard"]:
        for button in row:
            assert button["url"] == RELEASE or button["url"].removeprefix(DOWNLOAD + "/") in names
    validator = previous.BalancedHTML()
    validator.feed(HTML)
    assert not validator.stack and len(HTML) < 32768 and "beta.5" not in HTML
    response = requests.head(POSTER, timeout=30)
    assert response.status_code == 200 and response.headers.get("Content-Type", "").startswith("image/")
    payload = {"chat_id": previous.CHAT_ID, "message_thread_id": previous.TOPIC_ID,
               "rich_message": {"html": HTML, "skip_entity_detection": True}, "reply_markup": KEYBOARD}
    (OUT / "post.html").write_text(HTML, encoding="utf-8")
    (OUT / "payload.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"validated": True, "html_length": len(HTML), "downloads": len(links), "topic": previous.TOPIC_ID}))
    if not args.publish:
        return
    config = load_module("nimbo_bot_config", previous.CONFIG)
    api = f"https://api.telegram.org/bot{config.TOKEN}"

    def call(method, data):
        try:
            result = requests.post(f"{api}/{method}", json=data, timeout=90).json()
        except Exception:
            raise RuntimeError(f"{method}: uncertain response; do not resend automatically") from None
        if not result.get("ok"):
            raise RuntimeError(f"{method}: request rejected; no automatic retry")
        return result["result"]

    chat = call("getChat", {"chat_id": previous.CHAT_ID})
    assert chat["id"] == previous.CHAT_ID and chat.get("is_forum")
    with attempt.open("x", encoding="utf-8") as f:
        json.dump({"chat_id": previous.CHAT_ID, "topic_id": previous.TOPIC_ID,
                   "started": datetime.now(timezone.utc).isoformat()}, f)
    message = call("sendRichMessage", payload)
    receipt.write_text(json.dumps(message, ensure_ascii=False, indent=2), encoding="utf-8")
    assert message["chat"]["id"] == previous.CHAT_ID and message.get("message_thread_id") == previous.TOPIC_ID
    assert message.get("rich_message", {}).get("blocks"), "Missing rich blocks"
    url = f"https://t.me/c/2561763286/{previous.TOPIC_ID}/{message['message_id']}"
    summary = {"published": True, "message_id": message["message_id"], "url": url, "rich_message": True}
    (OUT / "verified-publication.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary))


if __name__ == "__main__":
    main()
