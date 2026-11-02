# Обновление ядер 2 ноября 2026

| Компонент | Было | Стало |
|---|---|---|
| Xray desktop | 26.10.21 | 26.10.31 |
| LibXray Android / Apple source | 26.10.17 | 26.10.31 |
| Mihomo | 1.19.32 | 1.19.33 |
| NaiveProxy Android / desktop | 154.0.8037.49-4 | 154.0.8037.49-5 |
| AmneziaWG | 3.1.20260828 | Уже актуален |
| TrustTunnel standalone | 1.1.11 | Уже актуальная стабильная версия |

## Готовые артефакты

- `app/libs/libxray.aar`: единая библиотека Xray/AWG/Mihomo/TrustTunnel, Go 1.27.3, четыре ABI. SHA-256: `8995e2ffbe8fe8153e6741437a2a5ef7de60bba5bcea71638540ac3ed2ed7479`.
- Mihomo в `apps/ui/src-tauri/resources/mihomo`: Windows x64, Linux x64 и ARM64. Собран из исходников с патчами Nimbo; у каждого артефакта есть build manifest и лицензии.
- NaiveProxy обновлён в Android JNI для ARM64/ARMv7 и десктопных resources для Windows/Linux x64.
- Xray подготовлен в `target/xray` для всех шести поддерживаемых платформ; манифест содержит новые хеши архивов и исполняемых файлов.
- Geo-базы закреплены отдельно и сохранены: обновление Xray не меняет данные профиля «Китай».

## Проверки

- Архивы Xray и NaiveProxy сверены с SHA-256 GitHub release assets. Исходники Go проверены по модульным суммам и закреплённым commit.
- Android AAR: сохранены существующие Java-классы и публичные методы, проверены встроенные версии Xray/Mihomo, все четыре ABI, lean build tags и выравнивание 16 KiB. Notices сформированы по 143 фактически скомпилированным модулям.
- Gradle: `:app:testDebugUnitTest :shared:desktopTest` — успешно, 674 Android-теста и 162 shared-теста.
- Mihomo: Go tests и vet при сборке трёх десктопных целей; host-тесты объединённого Android-моста.
- Rust: 4 теста Xray release/runtime; 54 теста Mihomo прошли, 11 помечены ignored.
- Python: Xray staging (4), Naive payload (2), Mihomo pins (3), Linux payload (6), core coherence (4), AAR verifier (10), export (9), gomobile staging (4).
- Apple: 151 контрактный тест iOS, AWG (7), Mihomo source (9), sticky patch (5), TUN ownership (3); реальные `TestNimboSticky` прошли на новых исходниках Xray. Новый Darwin source hash проверен, патчи применены.

## Ограничения и воспроизводимость

- IPA/XCFramework на Windows не собирались. Apple build scripts, workflow, исходники и зависимости обновлены; проверка на macOS и устройствах остаётся необходимой.
- APK и установщики не собирались. Полная проверка custom installer payload остановилась на отсутствующем `target/x86_64-pc-windows-msvc/release/nimbo-ui.exe`; проверки самих ядер прошли.
- Зеркало Xray 26.10.31 не публиковалось. Загрузчики теперь допускают отсутствие `mirror` и используют закреплённый upstream URL с проверкой хеша; порядок fallback для настроенного зеркала сохранён и покрыт тестами.
- Подключение к реальным VPN-серверам не проверялось. Cronet для Apple не заменялся более старым upstream HEAD; NaiveProxy выше относится к Android и desktop.
- Git остаётся в исходном состоянии: без коммитов и remote. Коммитов, push и публикации не выполнялось.
- Резервные копии и логи: `.build/core-refresh-20261102/`. Список изменённых сохранённых файлов — `modified-paths.json`, diff исходников — `source-changes.diff`.
- Успешная Android-сборка: `artifacts/mihomo-android-native-20261102-085450-45395194/result.json`. Первая попытка остановилась из-за неполного go.sum; граф разрешён и повторная сборка проверена. Установлен только прошедший проверки AAR.
