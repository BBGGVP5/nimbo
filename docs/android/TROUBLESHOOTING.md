# 🔧 Решение проблемы: "Нет интернета после подключения к VPN"

## ✅ Что было исправлено

### 1. Включён `auto_detect_interface`
**Проблема:** В генерируемом конфиге было `auto_detect_interface = false`, из-за чего libbox не мог корректно маршрутизировать трафик.

**Решение:** Изменено на `true` в файле `XrayManager.kt`, функция `generateSingBoxConfig()`.

### 2. Улучшена настройка DNS
**Проблема:** При отсутствии DNS серверов в конфиге от Remnawave, Android не знал куда отправлять DNS запросы.

**Решение:** Добавлены fallback DNS серверы:
- `8.8.8.8` (Google IPv4)
- `1.1.1.1` (Cloudflare IPv4)
- `2001:4860:4860::8888` (Google IPv6)

### 3. Улучшена диагностика TUN интерфейса
**Проблема:** При ошибке `builder.establish() == null` не было понятной диагностики.

**Решение:** Добавлены подробные логи с возможными причинами:
- Проверка разрешений VPN
- Конфликт с другим активным VPN
- Рекомендация перезапустить приложение

---

## 📋 Шаги по устранению проблемы

### Шаг 1: Пересоберите приложение

```bash
# В Android Studio:
1. Build > Clean Project
2. Build > Rebuild Project
3. Run > Run 'app'
```

### Шаг 2: Проверьте логи при подключении

После запуска приложения и попытки подключения:

1. Откройте **Logcat** в Android Studio
2. Установите фильтр по тегу: `XrayManager` или `MyVpnService`
3. Нажмите кнопку подключения в приложении
4. Ищите сообщения:
   - `"Starting VPN connection to..."` — начало подключения
   - `"Config generated successfully..."` — конфиг создан
   - `"TUN opened by libbox, fd=..."` — TUN интерфейс поднят
   - `"Connected successfully through libbox"` — успех

### Шаг 3: Проверьте типичные ошибки

#### ❌ Ошибка: "VpnService.Builder.establish() returned null"

**Причины:**
- Другой VPN уже активен
- Пользователь не подтвердил разрешение на VPN
- Системные ограничения (некоторые прошивки)

**Решение:**
1. Отключите другие VPN приложения
2. Перезапустите NebulaGuard
3. При появлении диалога подтверждения VPN — нажмите "OK"

#### ❌ Ошибка: "Failed to protect socket fd=..."

**Причины:**
- Сокет не был защищён от попадания в VPN туннель
- Возникает сетевая петля

**Решение:**
- Убедитесь, что `auto_detect_interface = true` в конфиге
- Проверьте, что приложение исключено из VPN (`addDisallowedApplication`)

#### ❌ Подключение есть, но интернета нет

**Причины:**
- DNS не работает
- Маршруты не настроены
- Сервер недоступен

**Диагностика:**
1. В Logcat найдите строку `"TUN opened by libbox"`
2. Проверьте, какие DNS серверы были добавлены
3. Попробуйте пропинговать сервер через терминал:
   ```bash
   ping <server_host>
   ```

### Шаг 4: Проверьте конфиг

Приложение сохраняет сгенерированный конфиг в файл:

```
/data/data/com.danila.nimbo/files/debug_config.json
```

Для просмотра:
1. Подключите устройство по ADB
2. Выполните:
   ```bash
   adb shell cat /data/data/com.danila.nimbo/files/debug_config.json
   ```

Проверьте:
- ✅ `route.auto_detect_interface = true`
- ✅ `inbounds[].auto_route = true`
- ✅ `dns.servers` содержит рабочие DNS
- ✅ `outbounds[].server` — правильный адрес сервера

---

## 🔍 Дополнительные проверки

### 1. Проверка подписки Remnawave

Если используется Remnawave:

1. Откройте URL подписки в браузере
2. Проверьте, что ссылка рабочая
3. Попробуйте получить конфиг через API:
   ```bash
   curl -H "User-Agent: SFA" <subscription_url>
   ```

### 2. Проверка сервера

1. Убедитесь, что сервер доступен:
   ```bash
   ping <server_host>
   ```
2. Проверьте порт:
   ```bash
   telnet <server_host> <port>
   ```

### 3. Проверка libbox

Убедитесь, что библиотека загружена:

1. В Logcat найдите: `"Loaded native library: box"`
2. Или: `"libbox initialized, version=..."`

---

## 📱 Инструкция для пользователя

### Если подключение не работает:

1. **Перезапустите приложение**
   - Закройте полностью (свайп из недавних)
   - Откройте снова

2. **Проверьте другой сервер**
   - Выберите сервер из списка
   - Попробуйте подключиться

3. **Очистите кеш приложения**
   - Настройки Android → Приложения → NebulaGuard
   - Хранилище → Очистить кеш

4. **Проверьте подписку**
   - Удалите профиль
   - Добавьте подписку заново

5. **Соберите логи**
   - Откройте Logcat в Android Studio
   - Скопируйте логи с тегами `XrayManager`, `MyVpnService`, `libbox`
   - Отправьте разработчику

---

## 🛠 Технические детали

### Архитектура подключения

```
MainActivity
    ↓
VpnManager
    ↓
MyVpnService (VpnService)
    ↓
XrayManager
    ↓
┌─────────────────────────────────┐
│  libbox (sing-box core)         │
│  ├─ PlatformInterface (Android) │
│  ├─ TunOptions → TUN интерфейс  │
│  ├─ Route → Маршрутизация       │
│  └─ DNS → Разрешение имён       │
└─────────────────────────────────┘
    ↓
TUN интерфейс (tun0)
    ↓
Системная маршрутизация
    ↓
Интернет
```

### Ключевые точки отказа

1. **`ensureLibboxSetup()`** — инициализация libbox
2. **`Libbox.newCommandServer()`** — создание сервера
3. **`newServer.start()`** — запуск
4. **`newServer.startOrReloadService(config)`** — загрузка конфига
5. **`openTun()`** — создание TUN интерфейса
6. **`builder.establish()`** — активация VPN

### Логирование

Все ключевые этапы логируются:

```kotlin
Log.e(TAG, "Connect method called for server: ${server.name}")
Log.e(TAG, "Config generated successfully. Length: ${config.length}")
Log.d(TAG, "Connected successfully through libbox")
```

Используйте Logcat для отслеживания процесса.

---

## 📞 Обратная связь

Если проблема не решена:

1. Соберите логи (Logcat, теги: `XrayManager`, `MyVpnService`, `libbox`)
2. Сохраните конфиг (`debug_config.json`)
3. Опишите:
   - Тип подключения (VLESS/VMess/Trojan)
   - Источник подписки (Remnawave/ручная)
   - Версию Android
   - Модель устройства
