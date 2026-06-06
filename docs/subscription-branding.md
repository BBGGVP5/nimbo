# Заголовки подписки Nimbo

Nimbo умеет читать дополнительные HTTP-заголовки из ответа подписки. Через них провайдер может передать название профиля, ссылки поддержки, объявление, лимиты трафика, оформление интерфейса и правила маршрутизации приложений в `direct` или `proxy`.

Заголовки возвращаются вместе с обычным телом подписки:

```http
HTTP/1.1 200 OK
profile-title: My VPN
nimbo-style: glass
process-direct: Steam.exe, EpicGamesLauncher.exe
process-proxy: Telegram.exe, Discord.exe
```

Названия HTTP-заголовков регистронезависимы. Для совместимости лучше писать их в нижнем регистре, как в примерах. Текстовые значения можно передавать обычной строкой, percent-encoding (`%0A` для переноса строки) или с префиксом `base64:`.

## Основные заголовки

| Заголовок | Алиасы | Назначение |
| --- | --- | --- |
| `profile-title` | - | Имя подписки в Nimbo. |
| `subscription-userinfo` | - | Стандартные лимиты: `upload`, `download`, `total`, `expire`. |
| `support-url` | `profile-support-url`, `x-support-url` | Ссылка на поддержку провайдера. |
| `profile-web-page-url` | `profile-page-url`, `website-url`, `web-page-url`, `x-web-page-url` | Сайт или личный кабинет провайдера. |
| `profile-update-interval` | - | Интервал обновления профиля в секундах, отображается в метаданных подписки. |
| `announce` | `profile-announce`, `subscription-announce`, `happ-announce`, `happannounce`, `x-announce`, `x-profile-announce`, `x-subscription-announce`, `x-happ-announce` | Объявление провайдера. Внутри него также можно передавать правила приложений. |

Пример `subscription-userinfo`:

```http
subscription-userinfo: upload=1073741824; download=5368709120; total=107374182400; expire=1893456000
```

## Брендинг

### `nimbo-theme`

Формат:

```http
nimbo-theme: <filter>,<accent>,<orb1>,<orb2>,<blur>
```

Поля:

| Поле | Описание |
| --- | --- |
| `<filter>` | Фильтр цветовой схемы. Можно оставить пустым. |
| `<accent>` | Основной HEX-цвет, например `#7c5dfa` или `7c5dfa`. |
| `<orb1>` | Первый фоновый цвет. |
| `<orb2>` | Второй фоновый цвет. |
| `<blur>` | Сила размытия фона числом. |

Пример:

```http
nimbo-theme: saturate,#7c5dfa,#ff007c,#00f0ff,40
```

Также поддерживается алиас:

```http
x-nimbo-theme: ,7c5dfa,ff007c,00f0ff,40
```

### `nimbo-logo`

Логотип подписки. Поддерживаются:

| Формат | Пример |
| --- | --- |
| HTTP/HTTPS URL | `nimbo-logo: https://example.com/logo.png` |
| Data URI | `nimbo-logo: data:image/png;base64,iVBORw0KGgo...` |
| Raw Base64 PNG | `nimbo-logo: iVBORw0KGgo...` |

Алиас:

```http
x-nimbo-logo: https://example.com/logo.png
```

### `nimbo-style`

Выбор стиля интерфейса.

| Значения | Результат |
| --- | --- |
| `glass`, `nebula`, `nimbo`, `nimboglass` | Стеклянный интерфейс Nimbo. |
| `material`, `materialyou`, `md3`, `mdyou`, `md` | Material You. |

Примеры:

```http
nimbo-style: glass
x-nimbo-style: material
```

## Маршрутизация приложений: direct/proxy

В разговоре это часто называют `proxy-direct`, но в заголовках Nimbo используются отдельные группы: `process-direct`, `process-proxy` и `process-rules`.

Такие правила позволяют провайдеру заранее указать, какие процессы должны идти напрямую (`direct`), а какие через VPN/прокси (`proxy`). Правила сохраняются в метаданных подписки и включены по умолчанию. Пользовательские локальные правила имеют приоритет: пользователь может отключить правило провайдера или заменить режим для того же процесса.

### Заголовки для direct

Все процессы из этих заголовков получают режим `direct`.

```http
process-direct: Steam.exe, EpicGamesLauncher.exe
```

Поддерживаемые имена:

```text
process-direct
process-direct-list
process-bypass
app-direct
apps-direct
nimbo-process-direct
x-nimbo-process-direct
x-process-direct
x-app-direct
```

### Заголовки для proxy

Все процессы из этих заголовков получают режим `proxy`.

```http
process-proxy: Telegram.exe, Discord.exe
```

Поддерживаемые имена:

```text
process-proxy
process-proxy-list
app-proxy
apps-proxy
nimbo-process-proxy
x-nimbo-process-proxy
x-process-proxy
x-app-proxy
```

### Смешанные правила

Если в одном месте нужно передать и `direct`, и `proxy`, используйте `process-rules` или ссылку на файл правил.

Поддерживаемые имена:

```text
process-rules
app-rules
apps-rules
nimbo-process-rules
x-nimbo-process-rules
x-process-rules
x-app-rules
```

Пример через percent-encoded переносы строк:

```http
process-rules: PROCESS-NAME,Steam.exe,DIRECT%0APROCESS-NAME,Telegram.exe,PROXY
```

Для больших списков лучше передавать URL:

```http
process-rules: https://cdn.example.com/nimbo/app-rules.json
```

Nimbo скачает файл и распарсит его как JSON или текст.

### Формат текстового списка

Разделители: запятая, точка с запятой или новая строка.

```text
# direct
process-direct: Steam.exe, EpicGamesLauncher.exe

# proxy
process-proxy: Telegram.exe; Discord.exe

# Mihomo/Clash-style
PROCESS-NAME,Spotify.exe,DIRECT
PROCESS-NAME,Telegram.exe,PROXY
```

Поддерживаемые режимы в тексте:

| Режим | Алиасы |
| --- | --- |
| `direct` | `bypass`, `bypasslan`, `bypass_lan` |
| `proxy` | `vpn`, `proxied` |

### Формат JSON

```json
{
  "processDirect": ["Steam.exe", "EpicGamesLauncher.exe"],
  "processProxy": ["Telegram.exe", "Discord.exe"],
  "rules": [
    {
      "mode": "direct",
      "appNames": ["Spotify.exe"]
    },
    {
      "mode": "proxy",
      "apps": ["C:\\Users\\Danila\\AppData\\Local\\Telegram Desktop\\Telegram.exe"]
    }
  ]
}
```

Поддерживаемые ключи для списков приложений:

```text
appNames
apps
processes
processNames
processDirect
processProxy
directApps
proxyApps
```

Поддерживаемые ключи режима внутри объекта:

```text
mode
action
outbound
outboundTag
policy
```

### Правила внутри announce

В `announce` можно добавить строки с правилами. Это удобно, если панель уже умеет отдавать announcement, но не умеет добавлять отдельные заголовки.

```http
announce: process-direct: Steam.exe, EpicGamesLauncher.exe%0Aprocess-proxy: Telegram.exe, Discord.exe
```

Внутри `announce` поддерживаются формы с двоеточием, знаком равенства и пробелом:

```text
process-direct: Steam.exe
process-proxy=Telegram.exe
app-direct Discord.exe
app-rules https://cdn.example.com/nimbo/app-rules.txt
```

Если в `announce` указать отдельной строкой HTTP/HTTPS URL на `.txt`, `.json`, `.yaml`, `.yml` или путь с `process`/`app`, Nimbo попробует скачать его как список правил. Для явного mixed-режима лучше писать ключ:

```text
process-rules: https://cdn.example.com/nimbo/app-rules.json
```

### Как Nimbo сопоставляет процесс

Можно передавать только имя файла или полный путь:

```text
Telegram.exe
C:\Users\Danila\AppData\Local\Telegram Desktop\Telegram.exe
```

Для сопоставления Nimbo нормализует путь и использует имя исполняемого файла. Поэтому `Telegram.exe` и полный путь к `Telegram.exe` считаются правилом для одного приложения. Не добавляйте один и тот же процесс одновременно в `direct` и `proxy`: при конфликте пользовательское локальное правило сможет переопределить провайдера, но на стороне провайдера лучше держать список однозначным.

## CORS

Если подписка читается из WebView или браузерного окружения, добавьте `Access-Control-Expose-Headers`, иначе клиент может не увидеть кастомные заголовки.

```http
Access-Control-Expose-Headers: profile-title, subscription-userinfo, support-url, profile-update-interval, announce, nimbo-theme, nimbo-logo, nimbo-style, process-direct, process-proxy, process-rules, app-direct, app-proxy, app-rules, x-nimbo-theme, x-nimbo-logo, x-nimbo-style, x-process-direct, x-process-proxy, x-process-rules, x-app-direct, x-app-proxy, x-app-rules
```

## Примеры настройки

### Nginx

```nginx
location /sub {
    proxy_pass https://upstream.example.com/sub;

    add_header profile-title "Nimbo Premium" always;
    add_header support-url "https://t.me/example_support" always;

    add_header nimbo-theme "saturate,#7c5dfa,#ff007c,#00f0ff,40" always;
    add_header nimbo-logo "https://example.com/assets/logo.png" always;
    add_header nimbo-style "glass" always;

    add_header process-direct "Steam.exe, EpicGamesLauncher.exe" always;
    add_header process-proxy "Telegram.exe, Discord.exe" always;
    add_header process-rules "https://cdn.example.com/nimbo/app-rules.json" always;

    add_header Access-Control-Expose-Headers "profile-title, subscription-userinfo, support-url, profile-update-interval, announce, nimbo-theme, nimbo-logo, nimbo-style, process-direct, process-proxy, process-rules, app-direct, app-proxy, app-rules, x-nimbo-theme, x-nimbo-logo, x-nimbo-style, x-process-direct, x-process-proxy, x-process-rules, x-app-direct, x-app-proxy, x-app-rules" always;
}
```

### Cloudflare Workers

```javascript
export default {
  async fetch(request) {
    const upstream = await fetch("https://upstream.example.com/sub", request);
    const headers = new Headers(upstream.headers);

    headers.set("profile-title", "Nimbo Premium");
    headers.set("support-url", "https://t.me/example_support");

    headers.set("nimbo-theme", "saturate,#7c5dfa,#ff007c,#00f0ff,40");
    headers.set("nimbo-logo", "https://example.com/assets/logo.png");
    headers.set("nimbo-style", "glass");

    headers.set("process-direct", "Steam.exe, EpicGamesLauncher.exe");
    headers.set("process-proxy", "Telegram.exe, Discord.exe");
    headers.set("process-rules", JSON.stringify({
      processDirect: ["Spotify.exe"],
      processProxy: ["Discord.exe"],
    }));

    headers.set(
      "Access-Control-Expose-Headers",
      "profile-title, subscription-userinfo, support-url, profile-update-interval, announce, nimbo-theme, nimbo-logo, nimbo-style, process-direct, process-proxy, process-rules, app-direct, app-proxy, app-rules, x-nimbo-theme, x-nimbo-logo, x-nimbo-style, x-process-direct, x-process-proxy, x-process-rules, x-app-direct, x-app-proxy, x-app-rules",
    );

    return new Response(upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers,
    });
  },
};
```

### Node.js / Express

```javascript
app.get("/sub", (req, res) => {
  res.setHeader("profile-title", "Nimbo Premium");
  res.setHeader("support-url", "https://t.me/example_support");

  res.setHeader("nimbo-theme", "saturate,#7c5dfa,#ff007c,#00f0ff,40");
  res.setHeader("nimbo-logo", "https://example.com/assets/logo.png");
  res.setHeader("nimbo-style", "glass");

  res.setHeader("process-direct", "Steam.exe, EpicGamesLauncher.exe");
  res.setHeader("process-proxy", "Telegram.exe, Discord.exe");
  res.setHeader("process-rules", "https://cdn.example.com/nimbo/app-rules.json");

  res.setHeader(
    "Access-Control-Expose-Headers",
    "profile-title, subscription-userinfo, support-url, profile-update-interval, announce, nimbo-theme, nimbo-logo, nimbo-style, process-direct, process-proxy, process-rules, app-direct, app-proxy, app-rules, x-nimbo-theme, x-nimbo-logo, x-nimbo-style, x-process-direct, x-process-proxy, x-process-rules, x-app-direct, x-app-proxy, x-app-rules",
  );

  res.send(getSubscriptionBody());
});
```

### PHP

```php
<?php
header('profile-title: Nimbo Premium');
header('support-url: https://t.me/example_support');

header('nimbo-theme: saturate,#7c5dfa,#ff007c,#00f0ff,40');
header('nimbo-logo: https://example.com/assets/logo.png');
header('nimbo-style: glass');

header('process-direct: Steam.exe, EpicGamesLauncher.exe');
header('process-proxy: Telegram.exe, Discord.exe');
header('process-rules: https://cdn.example.com/nimbo/app-rules.json');

header('Access-Control-Expose-Headers: profile-title, subscription-userinfo, support-url, profile-update-interval, announce, nimbo-theme, nimbo-logo, nimbo-style, process-direct, process-proxy, process-rules, app-direct, app-proxy, app-rules, x-nimbo-theme, x-nimbo-logo, x-nimbo-style, x-process-direct, x-process-proxy, x-process-rules, x-app-direct, x-app-proxy, x-app-rules');

echo getSubscriptionBody();
?>
```
