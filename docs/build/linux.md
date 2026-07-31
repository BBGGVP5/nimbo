# Сборка Nimbo для Linux

Nimbo выпускает три формата для популярных Linux-дистрибутивов:

| Формат | Дистрибутивы |
|---|---|
| AppImage | Arch Linux, Fedora, openSUSE и большинство других дистрибутивов |
| DEB | Ubuntu, Debian, Linux Mint, Pop!_OS |
| RPM | Fedora, RHEL/AlmaLinux/Rocky Linux, openSUSE |

Собирайте пакеты в нативной Linux-среде. Из обычного PowerShell на Windows собрать AppImage/DEB/RPM нельзя: нужны Linux linker, WebKitGTK и системные пакетировщики. Если основной компьютер работает под Windows, установите WSL2 с Ubuntu — команды ниже нужно выполнять внутри WSL, а не в PowerShell.

## Вариант 1: Ubuntu, Debian, Mint или WSL2 Ubuntu

```bash
sudo apt update
sudo apt install -y \
  build-essential curl file patchelf pkg-config rpm \
  libwebkit2gtk-4.1-dev libssl-dev \
  libayatana-appindicator3-dev librsvg2-dev
```

Установите Rust и Node.js 22+ в этой Linux-среде. Не используйте `node_modules` из Windows: выполните чистую установку зависимостей.

```bash
git clone https://github.com/BBGGVP5/nimbo.git
cd nimbo/apps/ui
npm ci
npm run build:linux
```

Результат находится в корне репозитория:

```text
target/release/bundle/appimage/*.AppImage
target/release/bundle/deb/*.deb
target/release/bundle/rpm/*.rpm
```

Для собственного Linux-инсталлятора:

```bash
cd ../installer
npm ci
npm run build:custom:linux
```

Исполняемый файл установщика появится в `target/release/bundle/custom/linux/`.

## Вариант 2: сборка на Windows через WSL2

Один раз в PowerShell с правами администратора:

```powershell
wsl --install -d Ubuntu
```

После перезапуска откройте приложение Ubuntu, задайте пользователя Linux и выполните команды из предыдущего раздела. Лучше хранить checkout в домашней папке WSL (`~/src/nimbo`), а не в `/mnt/c/`: так сборка и работа `node_modules` будут заметно быстрее.

## Архитектуры

GitHub Actions собирает x64. Скрипт `apps/installer/build-custom-linux-installer.sh` принимает `--target` для `x86_64-unknown-linux-gnu` и `aarch64-unknown-linux-gnu`, но Tauri/WebKitGTK нельзя надёжно кросс-компилировать без полного toolchain и библиотек целевой архитектуры.

Для arm64 используйте arm64-машину, arm64 VM или arm64 runner и выполните те же команды. При первом запуске Nimbo автоматически загрузит совместимый Xray runtime для Linux x64 или arm64 и проверит его SHA-256.

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
