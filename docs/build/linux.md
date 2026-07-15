# Сборка Nimbo для Linux

Nimbo выпускает четыре артефакта для каждой нативно собранной архитектуры:

| Формат | Дистрибутивы |
|---|---|
| AppImage | Arch Linux, Fedora, openSUSE и большинство других дистрибутивов |
| DEB | Ubuntu, Debian, Linux Mint, Pop!_OS |
| RPM | Fedora, RHEL/AlmaLinux/Rocky Linux, openSUSE |
| `NimboSetup` | графический собственный установщик Nimbo для Linux x64 или arm64 |

Собирайте пакеты в нативной Linux-среде. Из обычного PowerShell на Windows собрать AppImage/DEB/RPM нельзя: нужны Linux linker, WebKitGTK и системные пакетировщики. Если основной компьютер работает под Windows, установите WSL2 с Ubuntu — команды ниже нужно выполнять внутри WSL, а не в PowerShell.

## Вариант 1: Ubuntu, Debian, Mint или WSL2 Ubuntu

```bash
sudo apt update
sudo apt install -y \
  build-essential git curl file patchelf pkg-config rpm \
  libwebkit2gtk-4.1-dev libssl-dev \
  libayatana-appindicator3-dev librsvg2-dev
```

Установите Rust и Node.js 22+ в этой Linux-среде. Не используйте `node_modules` из Windows: выполните чистую установку зависимостей.

```bash
git clone https://github.com/BBGGVP5/nimbo.git
cd nimbo/apps/installer
npm run build:release:linux
```

Команда собирает AppImage, DEB, RPM и собственный установщик для архитектуры текущей Linux-машины. Результаты находятся в корне репозитория:

```text
target/release/bundle/appimage/*.AppImage
target/release/bundle/deb/*.deb
target/release/bundle/rpm/*.rpm
target/release/bundle/custom/linux/NimboSetup_<версия>_<x64|arm64>
```

Собственный установщик запускается как обычный исполняемый файл и сам создаёт ярлыки и деинсталлятор:

```bash
chmod +x target/release/bundle/custom/linux/NimboSetup_*
./target/release/bundle/custom/linux/NimboSetup_*
```

## Вариант 2: сборка на Windows через WSL2

Один раз в PowerShell с правами администратора:

```powershell
wsl --install -d Ubuntu
```

После перезапуска откройте приложение Ubuntu, задайте пользователя Linux и выполните команды из предыдущего раздела. Лучше хранить checkout в домашней папке WSL (`~/src/nimbo`), а не в `/mnt/c/`: так сборка и работа `node_modules` будут заметно быстрее.

## Архитектуры и GitHub Release

Локальная команда создаёт файлы для архитектуры машины: x64 на x64 Linux и arm64 на arm64 Linux. Теги `v*` и ручной запуск workflow «Публикация релиза» автоматически запускают нативные сборки GitHub Actions для `ubuntu-22.04` (x64) и `ubuntu-22.04-arm` (arm64), после чего все артефакты прикладываются к GitHub Release.

Tauri/WebKitGTK нельзя надёжно кросс-компилировать без полного toolchain и библиотек целевой архитектуры, поэтому для arm64 используйте arm64-машину, arm64 VM или arm64 runner. При первом запуске Nimbo автоматически загрузит совместимый Xray runtime для Linux x64 или arm64 и проверит его SHA-256.

## Проверка пакетов

```bash
# Ubuntu/Debian
sudo apt install ./target/release/bundle/deb/*.deb

# Fedora/openSUSE
sudo dnf install ./target/release/bundle/rpm/*.rpm

# AppImage
chmod +x target/release/bundle/appimage/*.AppImage
./target/release/bundle/appimage/*.AppImage
```

Некоторые дистрибутивы для запуска AppImage требуют установленный FUSE (`libfuse2` или аналог пакета вашего дистрибутива).
