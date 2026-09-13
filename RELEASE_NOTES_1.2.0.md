<!-- versionCode: 17 -->

<div align="center">
  <img src="https://raw.githubusercontent.com/BBGGVP5/nimbo/v1.2.0/nimbo.png" width="132" alt="Nimbo">
  <h1>Nimbo 1.2.0</h1>
  <p><em>Стабильный выпуск: доработанная главная, AmneziaWG 3.1 и изменения всех пяти бет.</em></p>
</div>

| Платформа | Скачать |
|:--|:--|
| 🤖 **Android** | [![ARM64](https://img.shields.io/badge/ARM64--V8A-APK-00a844?style=for-the-badge&logo=android&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.2.0/Nimbo_v1.2.0_arm64_v8a.apk) [![UNIVERSAL](https://img.shields.io/badge/UNIVERSAL-APK-4b5563?style=for-the-badge&logo=android&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.2.0/Nimbo_v1.2.0_universal.apk) [![ARMV7](https://img.shields.io/badge/ARMEABI--V7A-APK-4b5563?style=for-the-badge&logo=android&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.2.0/Nimbo_v1.2.0_armeabi_v7a.apk) |
| 🍎 **iPhone / iPad** | [![IPA](https://img.shields.io/badge/IPA-ДЛЯ%20ПЕРЕПОДПИСИ-00a844?style=for-the-badge&logo=apple&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.2.0/Nimbo_v1.2.0_ios_resignable.ipa) |
| 🪟 **Windows** | [![WINDOWS X64](https://img.shields.io/badge/УСТАНОВЩИК-X64-00a844?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.2.0/NimboSetup_1.2.0_x64.exe) [![WINDOWS X86](https://img.shields.io/badge/УСТАНОВЩИК-X86-4b5563?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.2.0/NimboSetup_1.2.0_x86.exe) [![WINDOWS ARM64](https://img.shields.io/badge/УСТАНОВЩИК-ARM64-4b5563?style=for-the-badge&logo=windows&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.2.0/NimboSetup_1.2.0_arm64.exe) |
| 🐧 **Linux** | [![LINUX X64](https://img.shields.io/badge/УСТАНОВЩИК-X64-00a844?style=for-the-badge&logo=linux&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.2.0/NimboSetup_1.2.0_x64) [![DEB](https://img.shields.io/badge/DEB-X64-4b5563?style=for-the-badge&logo=debian&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.2.0/Nimbo_1.2.0_linux_x64.deb) [![RPM](https://img.shields.io/badge/RPM-X64-4b5563?style=for-the-badge&logo=fedora&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.2.0/Nimbo_1.2.0_linux_x64.rpm) [![APPIMAGE](https://img.shields.io/badge/APPIMAGE-X64-4b5563?style=for-the-badge&logo=linux&logoColor=white)](https://github.com/BBGGVP5/nimbo/releases/download/v1.2.0/Nimbo_1.2.0_linux_x64.AppImage) |
| 🐧 **Linux ARM64** | [DEB](https://github.com/BBGGVP5/nimbo/releases/download/v1.2.0/Nimbo_1.2.0_linux_arm64.deb) · [RPM](https://github.com/BBGGVP5/nimbo/releases/download/v1.2.0/Nimbo_1.2.0_linux_arm64.rpm) · [AppImage](https://github.com/BBGGVP5/nimbo/releases/download/v1.2.0/Nimbo_1.2.0_linux_arm64.AppImage) |

<div align="center">
  🛡️ Рядом с каждым файлом приложена контрольная сумма <code>.sha256</code>.
</div>

<!-- nimbo:desktop:start -->
# 🖥 Что нового на Windows и Linux

## Главная без лишнего

- Доработаны состояния подключения и мониторинга: графики не занимают место без активной сессии, старые значения не выдаются за текущие.
- На главной доступны выбранный сервер, обновление подписки и сворачиваемый список серверов.
- В боковом меню можно оставить только значки; выбранная ширина сохраняется.

## AmneziaWG 3.1

- Добавлена поддержка конфигураций WireGuard/AmneziaWG и параметров AWG 3.1, включая защиту заголовков и обфускацию пакетов.
- Ядро запускается отдельным процессом, подключение проходит через локальный SOCKS с авторизацией. Конфигурация и ключи не передаются в командной строке.
- Добавлены остановка и переподключение, проверка ядра перед запуском и обходной маршрут до VPN-сервера для TUN.

## Пинг

- **Nimbo Ping**: HTTP GET через проверенный маршрут активного VPN, без подмены результата прямым соединением или копирования на другие серверы.
- TCP, HTTP GET/HEAD и ICMP; цифры, шкала, шкала с цифрами или точки. Настраиваются адрес проверки и таймаут.

## Подписки, маршрутизация и защита

- Карточки подписок объединяют трафик, срок действия, сведения провайдера, обновление и пинг. Серверы подписки можно свернуть.
- Поддерживаются запасные домены подписки: при недоступности основного адреса приложение пробует зеркало.
- Модули маршрутизации позволяют добавлять свои наборы правил и переносить их синхронизацией.
- Добавлены настройки DNS и MTU туннеля, управление Kill Switch и сброс оставшихся правил блокировки.
- На Linux туннель обслуживает отдельная системная служба; улучшены восстановление маршрутов и DNS, проверка запускаемого ядра.

## Быстрое управление и работа в фоне

- Панель в трее показывает выбранный сервер, пинг и избранное; подключиться или переключиться можно без главного окна.
- Восстановление после сна выполняет один нативный механизм. Ручное отключение отменяет следующие попытки.
- Скрытые страницы не выполняют лишний опрос; медленные запросы не накладываются друг на друга.
- Исправлен автозапуск Windows при включённом запуске с правами администратора.

## Оформление

- Стили Signal и Manga, настраиваемая анимация значков, более спокойные стеклянные поверхности.
- Исправлены длинные подписи, оформление окон обновления, логотипы подписок и отображение бессрочных тарифов.
- В описании обновления внутри приложения остаются только изменения для компьютера.
<!-- nimbo:desktop:end -->

<!-- nimbo:android:start -->
# 🤖 Что нового на Android

## Пинг

- **Nimbo Ping** проверяет активный VPN через HTTP GET; результат не присваивается непроверенным серверам.
- Все способы проверки доступны в настройках, включая HTTP GET/HEAD, TCP, ICMP и HTTPS Strict. Добавлена шкала с цифрами; сохранены цифры, шкала и точки.

## Главная и оформление

- Мониторинг памяти показывается только при подключении; графики новой сессии не продолжают историю предыдущей.
- Добавлены Signal и Manga. Стеклянные карточки, Dotted и Material You стали спокойнее, отступы и длинные подписи — аккуратнее.
- Анимацию значков можно отключить; выбор применяется и к настройкам, и к нижней панели.

## Подписки и маршрутизация

- Запасные домены помогают обновлять подписку, когда основной адрес недоступен.
- Добавлены модули с собственными правилами маршрутизации и их синхронизация.
- Синхронизация сохраняет выбранный стиль и поддерживает обмен с iPhone и iPad.

## Быстрые действия и фоновые задачи

- VPN-плитка и экран быстрого управления: выбор сервера, отключение и переход в приложение.
- В разделе «Работа в фоне» собраны уведомления, ограничения батареи и состояния фоновых задач.
- Плитка обновляется по событиям; управление с заблокированного экрана требует разблокировки.

## Ядра

- AmneziaWG **3.1.20260828** для ARM64 и ARMv7.
- LibXray **26.9.9**, NaiveProxy **150.0.7871.63-1**.
- Версия приложения **1.2.0**, код сборки **17**.
<!-- nimbo:android:end -->

<!-- nimbo:ios:start -->
# 🍎 Что нового на iPhone и iPad

## VPN и AmneziaWG

- Добавлен импорт конфигураций AmneziaWG и интеграция ядра **3.1.20260828** с VPN-расширением.
- LibXray обновлён до **26.9.9**; адаптирован запуск через новый API v3.
- AWG и LibXray собраны в одном Go-модуле; добавлена обработка остановки, сна, пробуждения и смены сети.
- Из бет вошли ограничения расхода памяти расширения, последовательные проверки автобалансировщика и исправления конфигурации расширений.

## Управление и диагностика

- **Nimbo Ping**, HTTP GET/HEAD через активный VPN и отдельные TCP/ICMP-замеры. Исправлено присваивание одного HTTP-результата всему списку серверов.
- Добавлены четыре варианта отображения пинга, адреса проверки и сохранение настроек. Неподтверждённый маршрут показывается как недоступный, без прямого обхода VPN.

- Проверка готовности установки показывает состояние расширения, подписи, профиля VPN и сохранённой конфигурации.
- Добавлены действия «Команд» и элемент Пункта управления для настроенного профиля Nimbo.
- Доступны общий и отдельный пинг серверов, таймаут и адрес проверки, профили и модули маршрутизации.

## Интерфейс

- Убраны пустые блоки мониторинга; память не показывается без подключения.
- Настройки собраны по разделам, длинные списки сворачиваются; улучшены подписи и закрытие клавиатуры.
- Signal, Manga, более спокойное стекло, уведомления внутри приложения и синхронизация с другими устройствами.
<!-- nimbo:ios:end -->

---

> [!NOTE]
> **iOS:** IPA нужно переподписать с правами Network Extension. **Linux:** фирменный установщик x64 включает службу TUN; для DEB/RPM/AppImage службу нужно настроить отдельно. **AWG на десктопе:** TUN пока требует IPv4-адрес сервера.

Это стабильный выпуск **1.2.0**, объединяющий изменения Beta 1–5 и новые исправления. Реальная работа VPN, сон и смена сети на физических устройствах в рамках подготовки релиза не проверялись.

<div align="center">
  <sub><a href="https://github.com/BBGGVP5/nimbo">Nimbo на GitHub</a></sub>
</div>
