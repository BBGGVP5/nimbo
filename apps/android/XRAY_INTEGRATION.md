# Инструкция по интеграции Sing-box / Xray-Core для NebulaGuard

## Обзор

NebulaGuard использует **sing-box** (рекомендуется) или **xray-core** для работы с протоколами:
- VLESS (включая VLESS Reality)
- VMess
- Trojan
- Shadowsocks
- Hysteria 1/2
- TUIC
- Juicity
- Socks/HTTP

## Шаг 1: Скачивание библиотеки libbox.aar (sing-box)

### Вариант А: Готовая библиотека из NekoBox (рекомендуется)

1. Перейдите на https://github.com/MatsuriDayo/NekoBoxForAndroid/releases
2. Найдите файл `libbox.aar` в assets к релизу
3. Скачайте последнюю версию

### Вариант Б: Самостоятельная компиляция из sing-box

Если вы хотите скомпилировать библиотеку самостоятельно:

```bash
# Клонируйте репозиторий sing-box
git clone https://github.com/SagerNet/sing-box.git
cd sing-box

# Перейдите в директорию Android
cd contrib/android

# Скомпилируйте библиотеку
./gradlew :libbox:assembleRelease
```

Скомпилированная библиотека будет в `libbox/build/outputs/aar/`.

### Вариант В: Использовать xray-core (альтернатива)

Если вы предпочитаете xray-core вместо sing-box:

1. Перейдите на https://github.com/XTLS/libXray
2. Скомпилируйте библиотеку:
   ```bash
   git clone https://github.com/XTLS/libXray.git
   cd libXray
   python3 build/main.py android
   ```

## Шаг 2: Установка библиотеки

1. Создайте директорию `app/libs/` в проекте (если ещё не создана)
2. Поместите скачанный/скомпилированный файл `.aar` в `app/libs/`
3. Для sing-box: переименуйте в `libbox.aar`
4. Для xray-core: переименуйте в `xray-android.aar`

```
NebulaGuard/
├── app/
│   ├── libs/
│   │   └── libbox.aar  <-- Поместите сюда
│   ├── src/
│   └── build.gradle.kts
```

## Шаг 3: Настройка build.gradle.kts

Откройте `app/build.gradle.kts` и раскомментируйте зависимость:

```kotlin
dependencies {
    // ... другие зависимости ...
    
    // Sing-box для работы с VLESS, VMess, Trojan, Shadowsocks
    implementation(files("libs/libbox.aar"))
}
```

## Шаг 4: Настройка XrayManager.kt

Откройте `app/src/main/java/com/danila/nebulaguard/vpn/XrayManager.kt` и раскомментируйте код инициализации:

### Для sing-box (libbox):

```kotlin
private fun startXray(context: Context, configFile: File): Boolean {
    return try {
        val configPath = configFile.absolutePath
        
        Log.d(TAG, "Starting sing-box with config: $configPath")

        // Раскомментируйте для sing-box:
        import io.nekohasekai.libbox.Libbox
        
        val configContent = configFile.readText()
        val service = Libbox.newService(configContent)
        service.start()
        boxInstance = service
        
        return true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to start sing-box", e)
        connectionError = e.message
        false
    }
}
```

### Для xray-core (альтернатива):

```kotlin
private fun startXray(context: Context, configFile: File): Boolean {
    return try {
        val assetPath = File(context.filesDir, "xray/assets").absolutePath
        val configPath = configFile.absolutePath
        
        // Раскомментируйте для xray-core:
        val xray = Xray.new(configPath, assetPath)
        xray.start()
        xrayInstance = xray
        
        return true
    } catch (e: Exception) {
        Log.e(TAG, "Failed to start xray", e)
        false
    }
}
```

## Шаг 5: Синхронизация проекта

1. В Android Studio нажмите **File > Sync Project with Gradle Files**
2. Убедитесь, что нет ошибок компиляции
3. Соберите проект: **Build > Make Project**

## Шаг 6: Тестирование

1. Запустите приложение на устройстве или эмуляторе
2. Добавьте подписку с сервером VLESS/VMess/Trojan
3. Нажмите кнопку подключения
4. Проверьте логи в Logcat (фильтр по тегу `XrayManager`)

## Дополнительные файлы

### GeoIP и GeoSite (опционально)

Для улучшенной маршрутизации скачайте файлы баз данных:

1. Скачайте с https://github.com/Loyalsoldier/v2ray-rules-dat/releases:
   - `geoip.dat`
   - `geosite.dat`

2. Поместите файлы в `app/src/main/assets/` или в runtime директорию:
   - `/data/data/com.danila.nimbo/files/xray/assets/`

## Поддерживаемые форматы ссылок

### VLESS
```
vless://uuid@host:port?encryption=none&security=tls&sni=example.com&fp=chrome&pbk=publicKey&sid=shortId&type=grpc&serviceName=serviceName#ServerName
```

### VMess
```
vmess://base64(json_config)
```

### Trojan
```
trojan://password@host:port?sni=example.com#ServerName
```

### Shadowsocks
```
ss://base64(method:password)@host:port#ServerName
```

### Hysteria 2
```
hysteria2://password@host:port?sni=example.com&insecure=1#ServerName
```

## Решение проблем

### Ошибка: "libbox.aar not found"
- Убедитесь, что файл `libbox.aar` находится в `app/libs/`
- Проверьте, что зависимость раскомментирована в `build.gradle.kts`
- Выполните **Clean Project** и **Rebuild Project**

### Ошибка: "Failed to load native library"
- Убедитесь, что архитектура библиотеки соответствует устройству
- Попробуйте универсальную версию или соберите для всех архитектур

### VPN не подключается
- Проверьте логи в Logcat
- Убедитесь, что сервер доступен
- Проверьте настройки брандмауэра/антивируса

## Ссылки

- **Sing-box (рекомендуется)**: https://github.com/SagerNet/sing-box
- **NekoBox для Android**: https://github.com/MatsuriDayo/NekoBoxForAndroid
- **Xray-core (альтернатива)**: https://github.com/XTLS/Xray-core
- **LibXray**: https://github.com/XTLS/libXray
- **Документация Sing-box**: https://sing-box.sagernet.org/
- **Документация Xray**: https://xtls.github.io/
