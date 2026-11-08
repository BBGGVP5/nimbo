"""Publish one rich article using the existing Nimbo Telegram bot configuration.

Default: validate and save payload only. --publish: send once and save receipt.
Never delete/edit older posts or retry an uncertain delivery automatically.
"""
from __future__ import annotations

import argparse
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
from html.parser import HTMLParser

import requests

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "artifacts" / "telegram" / "1.2.0-beta.5"
CONFIG = Path("C:/Users/Danila/Desktop/backup remnawave/NebulaAI_Security/config.py")
CHAT_ID = -1002561763286
TOPIC_ID = 6135
RELEASE = "https://github.com/BBGGVP5/nimbo/releases/tag/v1.2.0-beta.5"
DOWNLOAD = "https://github.com/BBGGVP5/nimbo/releases/download/v1.2.0-beta.5"
POSTER = "https://raw.githubusercontent.com/BBGGVP5/nimbo/v1.2.0-beta.5/nimbo-poster-rich.png"

HTML = f"""
<img src="{POSTER}"/>
<h1>Nimbo 1.2.0 Beta 5</h1>
<p><i>Управление VPN под рукой, меньше лишней работы в фоне и более спокойный интерфейс.</i></p>
<hr/>

<h2>🖥 Подключение прямо из трея</h2>
<p>На Windows и Linux больше не нужно разворачивать главное окно, чтобы сменить сервер. Панель у значка Nimbo стала полноценным быстрым управлением.</p>
<ul>
  <li>Выбранный сервер, известный пинг и до пяти избранных — в сохранённом порядке.</li>
  <li>Подключение, отключение, настройки и журнал рядом, без путешествий по экранам.</li>
  <li>Если подключение не удалось, ошибка не исчезает мгновенно: её можно прочитать и открыть логи.</li>
  <li>Панель сохраняет выбранный стиль, включая Dotted и Manga.</li>
</ul>

<h2>🌙 Компьютер проснулся — Nimbo восстанавливает соединение</h2>
<p>Сеть после сна появляется не сразу. Вместо конкурирующих таймеров восстановлением теперь управляет один механизм.</p>
<ul>
  <li>До трёх попыток с задержками 2, 5 и 10 секунд — чтобы Wi-Fi или Ethernet успели вернуться.</li>
  <li>Повторные события пробуждения не запускают несколько восстановлений одновременно.</li>
  <li>Если вы сами отключились или выбрали другой сервер, следующие попытки отменяются. Ручное отключение не считается поломкой.</li>
</ul>

<h2>🤖 Работа в фоне — на одном экране</h2>
<p>На Android появился раздел «Настройки → Общие → Работа в фоне». В нём видно, что системе разрешено, что ограничено и запускались ли фоновые задачи.</p>
<ul>
  <li>Разрешение уведомлений, ограничения Android и оптимизация батареи собраны вместе.</li>
  <li>Показаны последние запуски проверки обновлений, подписок и синхронизации — без ссылок и токенов.</li>
  <li>После возвращения из системных настроек состояние обновляется. Nimbo не отключает энергосбережение сам.</li>
  <li>История запуска — не обещание успешного обновления: задача может быть отложена системой.</li>
</ul>

<h2>⚡ VPN-плитка и быстрый выбор сервера</h2>
<ul>
  <li>На Android экран быстрого управления показывает избранные и общий список серверов, позволяет отключиться или открыть Nimbo.</li>
  <li>На Android 13+ можно запросить добавление VPN-плитки. Долгое нажатие открывает выбор сервера в оболочках, поддерживающих этот переход.</li>
  <li>Плитка реагирует на изменения состояния вместо опроса каждые полсекунды.</li>
  <li>Управление с заблокированного экрана сначала запрашивает разблокировку.</li>
</ul>

<h2>🍎 Готовность установки и «Команды» на iPhone</h2>
<p>После обновления доступна проверка готовности: есть ли VPN-расширение, файлы ядра, системный профиль и сохранённая конфигурация. Повторно её можно открыть через диагностику iOS.</p>
<ul>
  <li>Проверка ничего не подключает сама. Подготовка VPN-профиля — отдельная кнопка с системным запросом разрешения.</li>
  <li>Добавлены действия «Команд»: подключить, отключить и получить состояние. Сначала нужно настроить профиль и выбрать сервер в Nimbo.</li>
  <li>В IPA включён элемент Пункта управления. Он и «Команды» обращаются именно к профилю Nimbo, а не к первому VPN в системе.</li>
  <li>Пустую конфигурацию запустить нельзя. Работа быстрых действий зависит от корректной переподписи и системных разрешений.</li>
</ul>

<h2>🎨 Меньше рамок, бликов и лишнего движения</h2>
<p>На Android и в общих экранах iOS оформление стало спокойнее: карточки меньше спорят с содержимым, заголовки легче, второстепенные кнопки сдержаннее.</p>
<ul>
  <li>Material You — без лишних границ, Dotted — с приглушённой сеткой, Manga — с более компактными тенями.</li>
  <li>Стеклянный материал не убран: он сохраняется там, где нужен, в том числе на нижней панели.</li>
  <li>На Android фон строк серверов учитывает прозрачность темы, а отступы внизу разделов настроек приведены к одному виду.</li>
  <li>На iOS улучшены переносы длинных названий и подписей, разделители видны и в светлой теме.</li>
</ul>

<aside>Меньше открывать главное окно, меньше гадать о фоне, больше управлять подключением напрямую.<cite>Nimbo</cite></aside>

<details>
  <summary>🧠 Остальные заметные изменения</summary>
  <h3>Windows и Linux</h3>
  <ul>
    <li>Главная, соединения и журнал приостанавливают периодический опрос скрытого окна и обновляются при возвращении. Медленные запросы не накладываются друг на друга.</li>
    <li>Меньше лишней записи состояния при запуске; флаги стран больше не раздувают таблицу стилей встроенными изображениями.</li>
    <li>Проверка обновлений повторяется, если сеть при запуске ещё не появилась.</li>
    <li>Исправлены прозрачность и рамки стеклянных/Manga-элементов, фон под кнопкой подключения и размещение названия подписки у логотипа.</li>
    <li>Подписка без срока действия показывает ∞ вместо прочерка.</li>
  </ul>
  <h3>Android</h3>
  <ul>
    <li>AmneziaWG для ARM64 и ARMv7 обновлён с 3.0.20260805 до 3.1.20260828.</li>
    <li>Xray остаётся 26.7.28, NaiveProxy — 150.0.7871.63-1. Код сборки Android — 16.</li>
  </ul>
  <h3>Описание обновлений</h3>
  <p>Убраны повторяющиеся заголовки платформ. На компьютере и iOS показываются изменения соответствующей платформы, а не весь общий текст релиза.</p>
</details>

<hr/>
<h2>📥 Скачать</h2>
<table bordered>
  <tr><th>Платформа</th><th>Файлы</th></tr>
  <tr><td>🤖 Android</td><td><a href="{DOWNLOAD}/Nimbo_v1.2.0-beta.5_arm64_v8a.apk">APK · ARM64</a> · <a href="{DOWNLOAD}/Nimbo_v1.2.0-beta.5_armeabi_v7a.apk">ARMv7</a> · <a href="{DOWNLOAD}/Nimbo_v1.2.0-beta.5_universal.apk">Universal</a></td></tr>
  <tr><td>🍎 iPhone и iPad</td><td><a href="{DOWNLOAD}/Nimbo_v1.2.0-beta.5_ios_resignable.ipa">IPA · для переподписи</a></td></tr>
  <tr><td>🪟 Windows</td><td><a href="{DOWNLOAD}/NimboSetup_1.2.0-beta.5_x64.exe">Установщик · x64</a> · <a href="{DOWNLOAD}/NimboSetup_1.2.0-beta.5_x86.exe">x86</a> · <a href="{DOWNLOAD}/NimboSetup_1.2.0-beta.5_arm64.exe">ARM64</a></td></tr>
  <tr><td>🐧 Linux x64</td><td><a href="{DOWNLOAD}/NimboSetup_1.2.0-beta.5_x64">Установщик</a> · <a href="{DOWNLOAD}/Nimbo_1.2.0-beta.5_linux_x64.deb">DEB</a> · <a href="{DOWNLOAD}/Nimbo_1.2.0-beta.5_linux_x64.rpm">RPM</a> · <a href="{DOWNLOAD}/Nimbo_1.2.0-beta.5_linux_x64.AppImage">AppImage</a></td></tr>
  <tr><td>🐧 Linux ARM64</td><td><a href="{DOWNLOAD}/Nimbo_1.2.0-beta.5_linux_arm64.deb">DEB</a> · <a href="{DOWNLOAD}/Nimbo_1.2.0-beta.5_linux_arm64.rpm">RPM</a> · <a href="{DOWNLOAD}/Nimbo_1.2.0-beta.5_linux_arm64.AppImage">AppImage</a></td></tr>
</table>
<p>Предварительная версия — канал «Бета». IPA для iOS требует переподписи.</p>
<footer>Nimbo 1.2.0 Beta 5 · <a href="{RELEASE}">GitHub Release и полный список изменений</a></footer>
""".strip()

KEYBOARD = {"inline_keyboard": [
    [{"text": "📥 Скачать Nimbo", "url": RELEASE}],
    [{"text": "🤖 Android", "url": f"{DOWNLOAD}/Nimbo_v1.2.0-beta.5_arm64_v8a.apk"},
     {"text": "🍎 iPhone", "url": f"{DOWNLOAD}/Nimbo_v1.2.0-beta.5_ios_resignable.ipa"}],
    [{"text": "🪟 Windows", "url": f"{DOWNLOAD}/NimboSetup_1.2.0-beta.5_x64.exe"},
     {"text": "🐧 Linux", "url": f"{DOWNLOAD}/NimboSetup_1.2.0-beta.5_x64"}],
]}


class BalancedHTML(HTMLParser):
    def __init__(self):
        super().__init__()
        self.stack = []

    def handle_starttag(self, tag, attrs):
        if tag not in {"img", "hr", "br"}:
            self.stack.append(tag)

    def handle_endtag(self, tag):
        assert self.stack and self.stack.pop() == tag, f"Unbalanced tag: {tag}"

    def handle_startendtag(self, tag, attrs):
        assert tag in {"img", "hr", "br"}, f"Unexpected self-closing tag: {tag}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--publish", action="store_true")
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    receipt = OUT / "published.json"
    attempt = OUT / "delivery-started.json"
    if args.publish and (receipt.exists() or attempt.exists()):
        raise RuntimeError("Delivery already attempted; inspect receipt before any resend")
    release = json.loads(subprocess.check_output([
        "gh", "release", "view", "v1.2.0-beta.5", "-R", "BBGGVP5/nimbo",
        "--json", "isDraft,isPrerelease,assets",], encoding="utf-8"))
    assert not release["isDraft"] and release["isPrerelease"]
    names = {a["name"] for a in release["assets"]}
    links = re.findall(re.escape(DOWNLOAD) + r'/([^"<>\s]+)', HTML)
    assert len(links) == 14 and all(n in names for n in links)
    for row in KEYBOARD["inline_keyboard"]:
        for button in row:
            assert button["url"] == RELEASE or button["url"].removeprefix(DOWNLOAD + "/") in names
    assert len(HTML) < 32768 and "beta.4" not in HTML
    validator = BalancedHTML()
    validator.feed(HTML)
    assert not validator.stack
    response = requests.head(POSTER, timeout=30)
    assert response.status_code == 200 and response.headers.get("Content-Type", "").startswith("image/")
    payload = {"chat_id": CHAT_ID, "message_thread_id": TOPIC_ID,
               "rich_message": {"html": HTML, "skip_entity_detection": True},
               "reply_markup": KEYBOARD}
    (OUT / "post.html").write_text(HTML, encoding="utf-8")
    (OUT / "payload.json").write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"validated": True, "html_length": len(HTML), "download_links": len(links),
                      "chat_id": CHAT_ID, "topic_id": TOPIC_ID}, ensure_ascii=False))
    if not args.publish:
        return
    spec = importlib.util.spec_from_file_location("nimbo_bot_config", CONFIG)
    config = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(config)
    api = f"https://api.telegram.org/bot{config.TOKEN}"

    def call(method, data):
        try:
            result = requests.post(f"{api}/{method}", json=data, timeout=90).json()
        except Exception:
            raise RuntimeError(f"{method}: uncertain response; do not resend automatically") from None
        if not result.get("ok"):
            raise RuntimeError(f"{method}: {result.get('description', 'request rejected')}")
        return result["result"]

    chat = call("getChat", {"chat_id": CHAT_ID})
    assert chat["id"] == CHAT_ID and chat.get("is_forum")
    attempt.write_text(json.dumps({"chat_id": CHAT_ID, "topic_id": TOPIC_ID}), encoding="utf-8")
    message = call("sendRichMessage", payload)
    receipt.write_text(json.dumps(message, ensure_ascii=False, indent=2), encoding="utf-8")
    assert message["chat"]["id"] == CHAT_ID and message.get("message_thread_id") == TOPIC_ID
    assert message.get("rich_message"), "Telegram did not return rich content"
    url = f"https://t.me/c/2561763286/{TOPIC_ID}/{message['message_id']}"
    print(json.dumps({"published": True, "message_id": message["message_id"], "url": url,
                      "rich_message": True}, ensure_ascii=False))


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"Error: {exc}", file=sys.stderr)
        sys.exit(1)
