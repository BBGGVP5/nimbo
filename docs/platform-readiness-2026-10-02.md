## Current investigation checkpoint — 3 October 2026

Windows installers 37111046901 completed SUCCESS (rollback + embedded-core verification), all three downloaded and SHA256 recorded. Native run [37113077401](https://github.com/BBGGVP5/nimbo/actions/runs/37113077401) at 7c8a04f passed Linux ARM64/amd64 **with syscall tracing attached**. No product fix was made, so this does not resolve the prior untraced ARM64 TCP6 failure. First broker TCP6 remains ~3.6 s. The next gate requires actual per-request TUN RX and REJECT for both families, IPv6 in the Rust session and a separate untraced invocation.

The same run's Windows feature tests (Both/KS crashes and normal TUN cycles) passed, but its emergency-reset utility failed TUN_CLEANUP_FAILED immediately afterward; a subsequent finally reset passed. The workflow is therefore FAILURE. Preserve and investigate this cleanup-timing failure, do not describe that run as a fully successful acceptance.

## Confirmed Windows Both / external session Kill Switch — 3 October 2026

Implementation `923b851`, CI artifact-selection follow-ups `929e1b7` / `81a343a`. **Actual Windows x64 acceptance succeeded three times**, including the exact current Cargo driver at [37111227445, Windows job 111169375227](https://github.com/BBGGVP5/nimbo/actions/runs/37111227445/job/111169375227); [preceding Windows job](https://github.com/BBGGVP5/nimbo/actions/runs/37110959354/job/111168616188) also succeeded. These workflows are NOT overall green: Linux ARM64 failed broker TCP6 traffic after the Rust session in both runs. That failure remains visible and unresolved; Linux amd64 succeeded. [Latest exact-driver run 37111545340](https://github.com/BBGGVP5/nimbo/actions/runs/37111545340) at `81a343a` confirms Windows and Linux amd64 SUCCESS, with Linux ARM64 still FAILURE (broker TCP6, after successful explicit cleanup in this attempt). Do not infer universal platform/provider acceptance.

Windows fixture passed normal Both, forced native-only death and helper-only death/restart: actual TUN TCP4/TCP6, mixed listener plus real per-user GUI proxy snapshot/ownership/restore, UDP relay and DNS hijack, denied physical TCP and existing/new UDP DNS, retained external blocking, authenticated exact-adapter reset, and restored physical traffic. Counters: TCP15, UDP associations6, UDP6, DNS5. Native adapter and WFP journal retired; physical routes/DNS, global firewall profiles and per-user proxy matched baseline. Static WFP protection still does **not** survive BFE restart/reboot.

[Windows x64/x86/ARM64 installer build 37111046901](https://github.com/BBGGVP5/nimbo/actions/runs/37111046901) **completed SUCCESS** with `publish=false`, product implementation `923b851`. Installer rollback and embedded-core verification both passed. Artifact `nimbo-windows-custom` (11270296062) contains all three installers; downloaded locally and SHA256 recorded in `.codex-tmp/windows-installers-923b851-20261003/verification.json` in the primary workspace. x64 installer SHA256: `9558ba59c1712449c09d45322d8521ca8cd7e674caab41d7e87338d6f8b5a73b`. This is an internal CI build, not a public release or hardware acceptance. Mihomo TUN/Both/KS remain x64-only; installers for other architectures do not establish native support. [Project CI](https://github.com/BBGGVP5/nimbo/actions/runs/37110962172) and [Android/shared CI](https://github.com/BBGGVP5/nimbo/actions/runs/37110962173) succeeded at `923b851`.

Local IPC11/Mihomo34/service8 and scoped Clippy/fmt pass; helper cross-target checks for Windows x86 and ARM64 also pass without claiming native TUN support; Linux IPC8/Mihomo34/service10 pass. Existing desktop130/frontend86/build remain successful, with project CI passing this implementation. **27 intentional source/CI/doc files** are mirrored and byte-verified in the primary workspace; unrelated changes and frozen source/notices remain preserved. No developer-host networking, service or ACL mutation. Ordinary nonadmin GUI on physical hardware, roaming/sleep, physical IPv6 bypass, reboot-persistent protection, Windows ARM64/x86 Mihomo and real signed iPhone remain open gates. Host custom writable C:\ ancestry remains safely rejected without modification.

Recovery uses the protected SID-owned journal's native PID/creation time and exact interface GUID/LUID/Wintun device instance. Same-SID reset verifies native death and revalidates the device before checked [SetupAPI DIF_REMOVE](https://learn.microsoft.com/en-us/windows-hardware/drivers/install/dif-remove), then positively verifies interface absence. It does not remove by alias, delete drivers/global routes, or treat restart-required deletion as successful cleanup. Older journals without ownership proof cannot delete retained adapters. [SetupDiCallClassInstaller requirements](https://learn.microsoft.com/en-us/windows/win32/api/setupapi/nf-setupapi-setupdicallclassinstaller).

### Earlier failed recovery checkpoint (historical)

### Follow-up implementation before hosted validation (historical)

Hosted native run 37109598821 at dccf85e passed Linux amd64/arm64, but Windows failed exact reset after injected native death (`TUN_CLEANUP_FAILED`); normal Both fixture traffic and denial had already passed. It is **not** a passing Windows acceptance run. Fixture counters: six TCP, two UDP, two DNS. Project CI 37109602055 and Android/shared CI 37109602189 succeeded.

The follow-up adds protected journal ownership of the native PID/creation time and exact interface GUID/LUID/Wintun device instance, captured before permitting traffic. Explicit same-SID reset checks native death and revalidates the exact device before checked SetupAPI retirement and positive interface absence. It never removes by alias alone, deletes drivers or changes foreign routes. Reboot/restart-required removal remains failure with blocking retained. Older journals without device proof cannot delete retained adapters. Eight local service tests (including foreign/replaced identity and live/reused PID rejection), 34 Mihomo and 11 IPC tests plus scoped Clippy passed without host network mutation; live hosted acceptance is pending.

### Earlier Both / external Kill Switch checkpoint at dccf85e (historical)

Implementation now adds Windows x64 Both through the same authenticated TUN lease and verified mixed listener, with the existing per-user proxy snapshot/journal. External Kill Switch uses a private, SID-owned WFP sublayer; core/loopback/narrow DHCP and exact native TUN LUID permits, physical egress otherwise denied. No global firewall policy/WinHTTP reset. Static WFP filters survive native/helper failure, but not BFE restart or reboot. Abnormal cleanup retains protection; explicit owner-only Reset Kill Switch requires a retired native adapter.

Backward-compatible helper capability fields keep Both/KS unavailable with old helpers. Linux/macOS do not acquire these Windows-only capabilities. Changing active KS/mode requires an explicit disconnect instead of a false live toggle. Unsafe host ancestor ACLs remain unmodified.

Local: Windows IPC11/Mihomo34/service6, Linux IPC8/Mihomo34/service10, desktop130 non-mutating tests and scoped Clippy pass; frontend 86 tests and production build pass. Extended disposable acceptance now includes actual Both listener + real GUI proxy snapshot implementation, interface-bound remote TCP control (no HTTP or credentials), denial during KS, native-only and helper-only crash, existing/new physical UDP DNS denial, retained protection across helper restart, explicit reset and physical restoration. **Extended hosted acceptance and updated installers remain pending.** Implemented at `dccf85e`; source and all 25 intentional files are mirrored/byte-verified in the primary workspace. Installer acceptance at `ee111d5` does not cover later recovery changes. Explicit elevated helper uninstall now removes only journal-owned WFP keys, even after a failed session; it never changes global firewall profiles. Previous successful Windows TUN run does not prove the new Kill Switch.

---

# Состояние платформ Nimbo — 3 октября 2026

Это инвентаризация подтверждённых возможностей, а не заявление «все протоколы работают везде».

## Текущий checkpoint: Windows x64 native Mihomo TUN

**Native acceptance подтверждён:** [37105907646](https://github.com/BBGGVP5/nimbo/actions/runs/37105907646), исходники native `c3b0f93`: Windows x64, Linux x64 и Linux ARM64 — SUCCESS.

- На одноразовой Windows VM настоящий GUI-совместимый Rust Session → SCM broker → исходное native ядро прошёл TCP4/TCP6, UDP, DNS hijack, REJECT без обхода, восстановление выбора, два цикла подключения/отключения, stop и Drop/EOF. Счётчики синтетического сервера: TCP 6, UDP association 2, UDP 2, DNS 2. Физические DNS/маршруты сохранились, адаптер удалён.
- В packet socket hook сохраняется реальный адрес UDP peer вместо локального `:0`. Локальный regression воспроизводил старую ошибку и проходит после pinned source patch. Публичный ABI и immutable установка hook не изменены; неизвестный физический egress по-прежнему отклоняется.
- [Project CI](https://github.com/BBGGVP5/nimbo/actions/runs/37106001988) и [Android/shared contracts](https://github.com/BBGGVP5/nimbo/actions/runs/37106001771) на `0e9eb15` — SUCCESS. Этот дополнительный коммит меняет только source test: точные шесть reviewed файлов вместо прежнего счётчика четырёх.
- [Windows x64/x86/ARM64 installer rebuild](https://github.com/BBGGVP5/nimbo/actions/runs/37105908442) содержит исправленное ядро; сборка ещё идёт, `publish=false`. x86/ARM64 installers не означают поддержку Mihomo TUN на этих архитектурах.
- На ПК разработчика не устанавливалась служба и не менялись сетевые параметры/ACL. Native Windows hard-crash, обычный непривилегированный GUI на реальном ПК, roaming/sleep, внешние Kill Switch/Both и physical hardware/provider matrix всё ещё требуют отдельной проверки.


- Реализованы отдельный LocalSystem broker, защищённая установка исходного ядра, проверка SHA, SCM PID, SID/session и владение туннелем конкретным соединением. Старый pipe по-прежнему не принимает сетевые команды.
- GUI использует полноценный `Session::start_tun` с native контроллером групп/серверов, прежним исходным YAML и подтверждением поколения. Отмена закрывает настоящий pipe; stop/EOF ожидают очистку. При принудительном завершении нет ложного сообщения об успешной очистке.
- Wintun не принимает существующий адаптер как свой. Исправлен откат незавершённого конструктора, включая dynamic WFP/DNS-фильтры. Права, DLL/ядро, каталог и окружение не берутся из клиентского IPC.
- Windows локально: IPC 10 + Mihomo 34 + service 4 + desktop 130 тестов; scoped Clippy и frontend build проходят. Linux IPC/runtime/service 7 + 34 + 10 остаются успешными. Live Windows-проверка вынесена в одноразовую GitHub VM; локально намеренно заблокирована. Её успешный adapter/DNS результат указан выше; обычный пользователь/реальные устройства остаются отдельной проверкой. На текущем ПК read-only проверка обнаружила нестандартный FullControl пользовательского SID на корне C:\; строгая проверка цепочки установки его не допускает. Права не изменялись.
- Windows ARM64/x86 Mihomo, Both и постоянный Kill Switch не объявлены готовыми. Windows hard-crash recovery, roaming/sleep и реальные телефоны остаются отдельными воротами готовности.
- Предыдущий `1efff7c` теперь полностью подтверждён CI: [native](https://github.com/BBGGVP5/nimbo/actions/runs/37099508528), [project](https://github.com/BBGGVP5/nimbo/actions/runs/37099510687), [Android/shared](https://github.com/BBGGVP5/nimbo/actions/runs/37099510679), [IPA](https://github.com/BBGGVP5/nimbo/actions/runs/37099508930), [все desktop-пакеты](https://github.com/BBGGVP5/nimbo/actions/runs/37099510690). Эти артефакты ещё не содержат новой Windows TUN интеграции.

### Предыдущий checkpoint: desktop Linux crash recovery

- Устранена воспроизведённая ошибка native очистки: чужое правило с тем же приоритетом больше не удаляется при обычном отключении Mihomo.
- До первого `RuleAdd` сохраняется защищённый write-ahead journal полного плана. При SIGKILL ядра helper восстанавливает только точные правила этой сессии. После смерти обоих процессов следующий владелец восстанавливается по журналу; чужие маршруты и интерфейсы не удаляются. Сохранённый/некорректный журнал блокирует захват сетевых настроек legacy Xray/AWG после перезапуска helper.
- В реальном kernel namespace прошли TCPv4/v6, UDP, UDP/TCP DNS, переключение групп, обычное закрытие, native/helper/both-process crash, частично установленный план и сохранность чужого правила. Неверный boot/netns/live PID, symlink, публичный/слишком большой журнал и неизвестное помеченное правило отклоняются без сетевых изменений.
- Linux Rust IPC/runtime/service: 7 + 34 + 10 тестов; scoped Clippy без предупреждений. Последние source contracts iOS: 8 merged-source, 11 packet-flow и 18 ping. Это не заменяет проверку на реальном iPhone и не открывает Windows privileged TUN.
- Для предыдущего `2c14eec`: project CI и Android/shared успешны; обе Linux package jobs успешны. Полная IPA [37097102136](https://github.com/BBGGVP5/nimbo/actions/runs/37097102136) и все пять desktop-пакетов [37097104268](https://github.com/BBGGVP5/nimbo/actions/runs/37097104268) успешны. Это проверенный предыдущий checkpoint, до новой правки crash recovery.

### Предыдущий checkpoint: iOS lifecycle и финальные desktop CI-контракты

- В исходниках iOS исправлен возврат физического пути Mihomo: пропадание интерфейса, его возвращение и смена IPv4/IPv6 capabilities сбрасывают старые native сокеты/DNS-пулы. Повторный одинаковый tick ничего не пересоздаёт. Первое наблюдение до запуска не вызывает reset, ограничения Интернета не используются как условие подключения.
- Готовность C bridge теперь сверяет API, request ID, native generation, точный SHA исходника, pinned core version/commit и реальный `ios-packet-flow/tunReady`. Обычные команды и отмена привязаны к захваченному поколению; старый binder не отменяет пробу нового. UTF-8/BOM/CRLF проходят без `String(data:encoding:)`, который мог поглощать BOM при preflight/start.
- Watchdog Xray при неудачном перезапуске снимает умерший сеанс вместо сохранения «подключён». Отложенный wake проверяет lifecycle generation и выбранный двигатель. Fail/stop освобождают retained JSON/FD/assets и observers; healthy Mihomo wake сбрасывает старые pools без переустановки NE routes. Измеренная RSS/энергия этим не заявляется.
- Локально прошли 11 packet-flow source contracts, 6 merged-source, 18 ping, 6 profile, 4 on-demand и 6 Linux payload cases. Linux GUI: **130 passed / 4 opt-in ignored**, включая исправленный admission-тест. Swift runtime policy добавлен в отдельный macOS CI и в три настоящие Swift/C archive link checks; полный новый IPA остаётся отдельной проверкой.
- [Native/helper CI 37095806192](https://github.com/BBGGVP5/nimbo/actions/runs/37095806192), `b80753f`: SUCCESS на Linux x64/ARM64 и Windows x64. На обеих Linux архитектурах прошёл actual helper → Rust Session → TCP/UDP/DNS/IPv6 и teardown в private namespaces. Android/shared CI того же коммита также SUCCESS.
- Общий Linux CI выявил устаревший тест, который всё ещё запрещал уже реализованный TUN. Linux пакеты были собраны/проверены по SHA, но installation smoke остановился на ожидании IPC v2 вместо compiled v3. Оба ожидания исправлены; installation smoke теперь читает точную версию из IPC source. До повторного успешного CI **новая упаковка не считается завершённой**.
- Windows TUN/SID/DNS, Linux System Proxy/Both/KS, native-ядро SIGKILL/power-loss journal, real iPhone sleep/path/pressure и provider/transport matrix остаются открытыми. Эти изменения не делают все платформы полностью готовыми и не влияют на main/ключ подписи.

## Предыдущий checkpoint: GUI → Linux broker → настоящий Mihomo TUN

- Linux x64/ARM64: приложение выбирает native TUN явно, помощник принимает исходный YAML и его SHA256 по авторизованному Unix-соединению. Команды не принимают путь к исполняемому файлу или домашнему каталогу. Ядро устанавливается отдельно, только после системного подтверждения; SHA ядра и полные source/notices закреплены при сборке помощника и GUI.
- Перед остановкой рабочего соединения выполняется чистый native preflight. Готовность подтверждается настоящим интерфейсом, native generation, хешем исходника и защищённым loopback-контроллером; не подменяется флагом или TCP-проверкой. Выбор группы/пинг используют тот же контроллер.
- Потеря GUI-соединения и падение **помощника** закрывают stdin native-процесса и снимают его TUN. Отказ очистки возвращается ошибкой и не разрешает запуск замены. Завершившийся или чужой lease не может погасить новый сеанс. Исправлен зависавший Shutdown, который ранее ждал следующего accept.
- Локально в свежих network+mount namespaces прошли реальные TCP4/TCP6/UDP/DNS, select → REJECT без DIRECT fallback → возврат, ручная остановка, GUI EOF, SIGKILL помощника, отмена во время реальной загрузки provider, отказ неправильному UID/исходнику/ядру/пути и защита нового владельца от старого клиента. Отдельно прошёл настоящий Rust Session::start_tun → трафик → stop. `/run` и `/usr/local/lib` заменены private tmpfs только внутри теста; установка и сеть основного компьютера не менялись.
- Статус помощника не перечитывает всё ядро на каждом polling: кэш разрешён только для root-защищённого inode с неизменными размером/mtime/ctime. Запуск повторно хеширует файл. Синтетическая подмена установленного ядра в private namespace проверяет отказ и инвалидирование кэша.
- Linux ELF передаётся в stored ZIP, чтобы linuxdeploy не переписал закреплённые байты. Provenance находится в отдельном каталоге архитектуры и не перезаписывает Windows manifests. Custom installer и его проверка включают Linux core/archive, frozen source и все notices.
- **Ещё не полный desktop:** Windows Mihomo остаётся System Proxy. Windows TUN/DNS/SID-авторизация помощника, Linux System Proxy, Both, внешний Kill Switch и восстановление после SIGKILL **самого native-ядра**/сбоя питания остаются открытыми. Не путать падение помощника (проверено: native получает EOF) с native SIGKILL (журнал ещё необходим).
- Последний подтверждённый полный iOS IPA: [37036642211](https://github.com/BBGGVP5/nimbo/actions/runs/37036642211), `4bdec2d`, SUCCESS. Подтверждённые desktop packages: [37036636821](https://github.com/BBGGVP5/nimbo/actions/runs/37036636821), тот же коммит, SUCCESS для пяти целей. Native Go CI: [37036616000](https://github.com/BBGGVP5/nimbo/actions/runs/37036616000), SUCCESS для Linux x64/ARM64 и Windows x64. Это предыдущие артефакты, без нового broker-пути.
- **iOS не объявлен полностью проверенным:** merged core/SwiftUI/NetworkExtension успешно собраны, но остаются подписанный туннель на iPhone, фон/смена сети/сон, DNS/IPv6/UDP, memory pressure и provider/transport matrix. Новые сборки запускаются отдельно от публикации; main не слит, PR78 draft.
- Этот slice: 85 frontend теста, TypeScript/Vite, 34 Linux Mihomo +7 IPC +9 helper unit cases, четыре настоящие native proxy/provider/cancel fixtures и isolated Rust TUN fixture. Windows GUI: 130 passed, 4 opt-in tests skipped; IPC/Mihomo: 7+32 passed. Linux GUI/installer cargo check --tests и scoped Clippy -D warnings проходят; они не подменяют проверку пакетов или устройства.

Ниже — исторические checkpoints. Старые строки «не реализовано», «строится» и числа тестов относятся к соответствующему коммиту, не к текущему состоянию.

## Последний checkpoint: настоящее native TUN на desktop

- В Go-адаптер добавлен отдельный привилегированный desktop TUN для Windows/Linux. Он использует настоящий системный стек Mihomo, TCP/UDP, DNS и IPv6, полный native граф правил/providers и переключение группы без пересоздания адаптера. Обычный JSON Invoke не может получить привилегированное владение сетью.
- Linux: проверены реальные TCP IPv4/IPv6, UDP, DNS UDP/TCP, переключение на REJECT без прямого fallback, возврат группы, stale generation и точное восстановление маршрутов/правил в отдельных network/mount namespaces. EOF, SIGTERM и команда stop проходят очистку; сбой после создания TUN также откатывается. Сеть и DNS основного компьютера не менялись.
- Windows: исходники native TUN компилируются; проверка реального Windows-адаптера, DNS и маршрутов ещё необходима. Функциональные Go-тесты не являются проверкой race detector.
- **Полноценный desktop ещё не завершён:** защищённая установка ядра и ресурсов в помощник, авторизованный UID/SID lease, подключение к GUI, аварийный откат при SIGKILL и внешний Kill Switch остаются отдельными обязательными этапами. В приложении доступность TUN/Both не включена преждевременно. Linux System Proxy также остаётся отдельным backend.
- 182 native Go-теста прошли, ошибок нет; одна private Android provider-фикстура пропущена без загрузки пользовательских секретов. Default/with_gvisor, go vet и source-проверки iOS прошли. Native CI дополнительно строит Linux x64/ARM64 и Windows x64 из закреплённых исходников, с source/notice manifests.
- Последняя полная IPA из 98a6a59: [37028406362](https://github.com/BBGGVP5/nimbo/actions/runs/37028406362) — SUCCESS. Windows/Linux packaging из 62c2cec: [37027211807](https://github.com/BBGGVP5/nimbo/actions/runs/37027211807) — SUCCESS. Это предыдущие артефакты; новая сборка нужна для текущих native изменений. Успех сборки не заменяет проверку на телефоне/Windows VM.

Ниже сохранены исторические checkpoints; отметки «ещё строится» в них относятся к моменту записи, а не к текущему статусу.

## Текущий перенос

### iOS
- Нативный экран профиля использует имя и полное описание из тех же метаданных подписки, что главная и окно информации. Выбранный сервер выделен фоном, рамкой и отметкой; ввод ссылки/конфига растёт по содержимому вместо фиксированного пустого редактора.
- Выбор сервера проверяет ядро до сохранения, не запускает незавершённую вложенную задачу и сообщает успех только после подготовки NetworkExtension. Конкурирующие действия заблокированы на время операции. При активном туннеле общий контроллер ожидает фактическую остановку NetworkExtension (до 15 с), сохраняет и подготавливает новый сервер, затем запрашивает запуск. Ручное отключение отзывает намерение переключения; повторные действия заблокированы.
- Это не транзакция между Keychain и NetworkExtension: при ошибке подготовки после сохранения отображается ошибка и актуальный сохранённый выбор, а не ложное сообщение об успехе.
- Удаление явно подтверждается, ожидает очистку системного профиля и только затем удаляет локальный профиль и метаданные. Ошибки больше не игнорируются.
- Настройки on-demand восстанавливаются из системного профиля при потере локальных данных; чтение сохраняет ручную паузу. Сохранение блокирует повторное нажатие и закрытие формы.
- Правила on-demand и переключатель Live Activity/Dynamic Island уже присутствуют. Проверка на устройстве и корректная подпись NetworkExtension всё ещё необходимы.
- **Mihomo packet-flow интегрирован в исходниках**: публичный поток NetworkExtension связан с native Mihomo, DNS, IPv6 и группами без подмены Xray. Apple archive/провайдер прошли линковку; исправленная полная IPA и проверка туннеля на iPhone остаются отдельными этапами. Это не заявление о поддержке всех конфигураций и транспортов.

### Desktop
- Добавлены opt-in правила автоподключения для Wi-Fi, Ethernet и мобильной сети: Windows WLAN/IP Helper и Linux NetworkManager (`nmcli`, без сканирования). Ручное отключение сохраняет паузу между запусками; явное подключение возобновляет правила и запоминает сервер/ядро. Для первого включения нужен установленный сеанс. Правила работают, пока приложение открыто, включая трей; выключены по умолчанию и не переносятся резервной копией/синхронизацией.
- Доверенные SSID сравниваются точно; неизвестное имя и смешанные подключения не разрешают автоматическое отключение. Решения ждут двух наблюдений, повторные попытки ограничены задержкой 15–300 секунд, HTTP/DNS-проверка доступности интернета не нужна. Windows может требовать разрешение местоположения для SSID; при отказе исключение не считается совпавшим.
- Новый монитор читает только малую часть состояния, не копируя все подписки в каждом цикле. SSID текущей сети не публикуется в статусе и не логируется.
- Полный Mihomo YAML теперь можно импортировать HTTP(S)-ссылкой, файлом или текстом. Исходный UTF-8, BOM, окончания строк, группы, providers и правила не преобразуются.
- Загрузка по ссылке однократная, ограничена 4 МиБ и общими 20 секундами. URL не хранится, автообновление подписки этим не реализовано. Редиректы разрешены только в пределах одного origin, максимум пять; при переходе на другой сайт требуется конечная ссылка. HTML и некорректный текст отклоняются. Ошибки не содержат URL, токенов или тела ответа.
- Активный профиль заметно выделяется, breadcrumb называется «Профили Mihomo», поля/кнопки адаптируются к узкой ширине. Состояние опрашивается последовательно, только в видимом окне, без запуска нового live-запроса после закрытия страницы.
- **Mihomo: Windows x64 System Proxy и управляемый native TUN на Windows x64/Linux**, при наличии соответствующего проверенного ядра и системного помощника. Live Windows TUN acceptance пока ожидается; Both, постоянный Kill Switch и Linux System Proxy не объявлены готовыми. Импорт не меняет настройки автоматически.
- AWG требует подготовленного проверенного адаптера. Локальная компиляция с предупреждением об отсутствии AWG binary не является проверкой работающего AWG-туннеля.

## Проверки
- Frontend: 73 тест production-модулей/SSR и polling; TypeScript + Vite build успешны.
- Rust: 270 workspace-тестов и 14 тестов установщика, включая локальные HTTP-фикстуры загрузчика; Clippy с `-D warnings` и проверка форматирования успешны.
- iOS: шесть source/release-наборов прошли локально; на macOS прошли четыре поведенческих Swift-теста порядка admission → persistence → staging и source contracts. Полная Release IPA сборка [36982167821](https://github.com/BBGGVP5/nimbo/actions/runs/36982167821) успешно скомпилировала SwiftUI/NetworkExtension из `9230991`. Артефакт `Nimbo_v1.3.0-beta.1_ios_resignable` опубликован до 9 октября 2026; для установки нужна переподпись. Это не проверка VPN на устройстве.
- Регрессии в основном checkout: 589 Android + 76 Android-host + 118 shared-desktop = 783 теста, без ошибок. GitHub PR-проверки исходников `9230991` прошли для Windows/Linux и Android/shared.
- Desktop UI проверяется в браузере как production-страница, не как рисованный макет. Native-команды в браузерном preview отключены; это не проверка VPN-соединения.

## Остаётся
- Native Mihomo TUN на iOS и desktop; desktop Linux proxy и интеграция Kill Switch.
- Сетевые on-demand-правила Android; проверка переходов сетей, разрешений Windows, разных конфигураций NetworkManager и фоновых ограничений на устройствах. Desktop-правила не работают после полного выхода из приложения.
- Проверки реальных соединений, пинга/автобалансеров, импорта разных провайдеров, переподписанной IPA и длительных сессий.
- Измерение RSS/энергопотребления на устройствах; проценты экономии памяти/батареи без измерений не заявляются. Go memory limit не является жёстким лимитом общей RSS, а standalone upstream Xray и отдельный AWG runtime не считаются оптимизированными этим переносом.
- Исходники этого прохода публикуются в PR #78, не автоматически в `main`.

## Завершённый блок реализации: сетевые правила и упаковка
- Локально: 270 Rust-тестов, 73 frontend-теста, Clippy и TypeScript/Vite build. Production-экран просмотрен на 1280px и 390px, горизонтального переполнения нет. Windows/Linux PR CI и Android/shared проверки `a810cd8` и `5307666` успешны.
- В фирменный Windows x64 установщик добавлены точный Mihomo helper, исходники адаптера и лицензии из проверенного манифеста. Данные кэша и локальная квитанция staging не включаются. Ресурсы проверяются после записи и откатываются вместе с приложением при сбое; 14 локальных тестов установщика и Clippy успешны. В CI добавлена сборка ядра из закреплённых исходников вместо зависимости от файлов с компьютера разработчика.
- Новая полная [IPA-сборка 36987319977](https://github.com/BBGGVP5/nimbo/actions/runs/36987319977) из `a810cd8` успешна. Скачанный архив проверен: SHA-256 `724988d2200ef5dd9f1254fb7356caf9abf61892584c3f932bb49bce18c8a56f`, 65 827 658 байт; приложение, PacketTunnel, LiveActivity и ControlWidget имеют версию 1.3.0 / build 180. Нужна переподпись с NetworkExtension entitlement; артефакт хранится до 9 октября.
- Linux ARM64 packaging успешен: AppImage, DEB и RPM. Скачанный DEB проверен без установки: ARM64 ELF приложения/AWG, исполняемые права и метаданные версии совпадают. Это не проверка соединений и не готовность TUN; ограничение helper указано выше.
- Artifact-only [Windows/Linux packaging 36988824849](https://github.com/BBGGVP5/nimbo/actions/runs/36988824849) из `5307666` завершён успешно. Windows x64/x86/ARM64 установщики скачаны: архитектуры PE, размеры и SHA-256 проверены; 14 тестов установщика прошли в CI. Windows x64 включает проверенные Mihomo binary, исходники и лицензии. Это не добавляет native Mihomo TUN или поддержку других архитектур ядра.

## Linux helper: исправление обычных пакетов
- Упаковщик собирает `nimbo-svc` для выбранного x64/ARM64 target, проверяет ELF и SHA-256, включает raw helper в DEB/RPM и архив helper в AppImage; runtime материализует архив в проверенный кэш после проверки SHA-256 (linuxdeploy не может изменить ELF внутри архива). Release-компиляция не допускает отсутствующий или несовпадающий helper. Фирменный установщик использует ту же подготовку.
- Системная установка по явному действию пользователя сохраняет исполняемый файл в root-owned `/usr/local/lib/nimbo/nimbo-svc`; systemd больше не зависит от временного AppImage mount или пользовательского каталога. GUI остаётся непривилегированным. Статус TUN на Linux проверяет Linux-службу, не Windows DLL и не права администратора GUI.
- Владение TUN привязано к IPC-сессии, успешно запустившей туннель: запрос статуса, ping, закрытие чужого клиента и запоздалое закрытие предыдущего владельца не останавливают текущий туннель. Закрытие владельца сохраняет очистку маршрутов.
- Локально: 76 frontend-тестов и TypeScript/Vite build; пять Python-тестов ELF/TAR/CPIO/ZIP; семь Linux service-тестов, включая настоящие Unix socket pairs; UI-тест отсутствия `TunDown` при чтении статуса. Полные Linux workspace-тесты и Clippy `-D warnings` успешны. Использовались временные файлы/сокеты, без изменения systemd, DNS или маршрутов компьютера пользователя.
- Новый CI отдельно проверяет helper внутри каждого пакета и на **одноразовом GitHub-hosted runner** устанавливает DEB, обновляет службу из временно извлечённого AppImage, удаляет источник, перезапускает службу и проверяет IPC/очистку удаления. Эти проверки не запускают ядро или TUN и не заменяют тест реального VPN-соединения.

## Подтверждённая упаковка Linux helper
- [Сборка 36997163348](https://github.com/BBGGVP5/nimbo/actions/runs/36997163348), `0ff073a`: обе архитектуры успешно собраны, helper/манифест/архив проверены во всех AppImage/DEB/RPM. На обоих одноразовых runners прошли установка DEB, обновление из временного AppImage, удаление источника, перезапуск постоянной службы, IPC и удаление службы. Все шесть скачанных пакетов совпали с SHA-256/размерами отчётов CI.
- Артефакты: [Linux x64](https://github.com/BBGGVP5/nimbo/actions/runs/36997163348/artifacts/11222920573) (также фирменный установщик), [Linux ARM64](https://github.com/BBGGVP5/nimbo/actions/runs/36997163348/artifacts/11222810324). Это подтверждение упаковки и lifecycle helper, **не тест реального VPN**. Эти артефакты ещё не включают следующий UI-проход.

## Серверное меню и отмена пинга
- Android/shared/iOS и desktop: выбранная строка обведена акцентом; удержание открывает действия без выбора/подключения. Видимые серверные точки и кнопки пинга убраны; подписочные действия остаются. Desktop также поддерживает правую кнопку мыши и Shift+F10, Escape/стрелки и возврат фокуса.
- «Пинг сервера» передаёт только ID строки; при замере становится «Остановить пинг». Повторное нажатие отменяет текущую операцию, не запускает дубликат и не стирает последний завершённый замер. Кнопки общего пинга тоже допускают отмену; обновление подписки независимо.
- Android TCP закрывает свой сокет при отмене. Desktop ограничивает параллелизм тремя запросами и ждёт отмены IPC перед новым запуском; не запускает оставшуюся очередь и не принимает поздние ответы. iOS отслеживает UUID операции и ждёт завершения очистки предыдущей диагностической задачи.
- Проверки: 82 frontend-теста, TypeScript/Vite build, browser fixture (реальные right-click/Shift+F10 и per-ID/cancel callback без native IPC); 129 Linux UI Rust-тестов и Clippy. Android Release-компиляция и 591 Android + 76 host + 119 shared desktop тестов прошли (786 без ошибок); новый shared UI-тест проверил меню выбранной строки и ID пинга/отмены без смены выбора. iOS source contracts прошли для ping/profile/Live Activity/on-demand/iPad; полная новая IPA проверяется отдельно.
- Общий Python discover выявил старые неподготовленные Mihomo-storage/branding contracts и отсутствие tree-sitter в локальном runtime; он не выдаётся за зелёный полный iOS suite. Целевые release-наборы запускаются отдельно.

### GitHub проверки этого UI-прохода
- `ff10fc6`: [Windows/Linux project CI](https://github.com/BBGGVP5/nimbo/actions/runs/37000897792) и [Android/shared CI](https://github.com/BBGGVP5/nimbo/actions/runs/37000897902) завершились успешно.
- Предыдущая [полная IPA](https://github.com/BBGGVP5/nimbo/actions/runs/37000894345) завершилась ошибкой Swift type-check; [Windows/Linux установщики ff10fc6](https://github.com/BBGGVP5/nimbo/actions/runs/37001230692) собраны успешно. Следующий проход ниже исправляет Apple-выражение и запускает новые сборки. Релиз не публикуется, `main` не изменён.


## Xray route-fidelity and title-menu pass (2 October)
- Android: already-expanded provider balancer members are no longer regenerated from unrelated profile servers. Explicit injection rules and genuine placeholders still expand; a partially populated template is not flattened to fill another group.
- Android standalone probes preserve reachable backup balancers and their unconditional loopback routes, include backup observer candidates in readiness, retain global transport settings, and authenticate every reachable proxy/fallback route. Missing/conditional/cyclic/direct fallback routes still fail closed.
- Ordinary Android TLS/REALITY nodes may retain a verified terminal ClientHello fragmenter. Redirecting/chained helpers and nested unverified XHTTP dialers remain rejected. This corrects a reproducible configuration rejection, not proof that every remote node responds.
- Android/shared iOS/desktop server menus now anchor at the title. Desktop right-click and keyboard actions were verified inside the production native HTML modal: the portal stays in the dialog, selection count remains zero, per-node cancellation retains 151/87ms fixture results, and menu left equals title left with a 6px gap below the title.
- Android refresh intervals use content-fitting centered 48dp controls; ping-format options put the preview beside the label instead of below a tall empty tile. Touch targets and selection semantics remain intact; Android device/font-scale visual QA remains outstanding.
- iOS: the previous IPA run 37000894345 FAILED because Swift could not type-check the nested server-row expression. The list, row and label are now separate view expressions; the control kind is nonisolated and the displayed application label is NIMBO. New full IPA compilation is required before declaring this repaired.
- Verification before dispatch: 595 Android unit tests +119 shared desktop tests +76 shared host tests passed, along with Release Kotlin compilation; desktop 82 tests and TS/Vite production build passed; iOS source contracts (ping 18, profiles 5, Live Activity 5, on-demand 4) passed. Native Apple compilation/device tests are separate.
- Runtime limits remain explicit: this Xray graph correction is Android-only. The iOS app-process Go diagnostic still rejects ambiguous multi-outbound/chain configurations; desktop diagnostics still reject health-driven balancer strategies. These are not silently measured as TCP or as an arbitrary member. Native Mihomo TUN and the earlier platform/device gaps are not declared complete.
- Prior desktop packaging run 37001230692 completed successfully for Windows x64/x86/ARM64 and Linux x64/ARM64 (ff10fc6, before this pass). Its artifacts are not the newly compact/title-anchored build. New artifact-only packaging and IPA will be dispatched from the corrected revision, without publishing a release or merging main.


### Final local results and dispatched builds (33dea19)
- Primary project: `:app:testDebugUnitTest :shared:desktopTest :shared:testAndroidHostTest :app:assembleDebug :app:assembleRelease` — BUILD SUCCESSFUL, 4m34s; 595+119+76 tests, zero failures/errors. `packageRelease` completed; no release signing keys were changed or read.
- ARM64 debug APK: 143,666,905 bytes, SHA256 `3c23096a04dfe043a6ad4511f7baece9c7cffb2c8ed9f6a61396f25595b53991`, ZIP valid, only arm64-v8a native libraries, apksigner verification passed, application ID `com.danila.nimbo.debug`. This is the installable side-by-side test build.
- ARM64 release APK: 46,399,623 bytes, SHA256 `fa1250bced1502ad70ab4e108f22ebd108334090274176c2b49c75eb32cf20e2`, ZIP valid, only arm64-v8a libraries, application ID `com.danila.nimbo`. **Unsigned**: apksigner verification does not pass; Gradle packaging success is not a signed update over the installed release. It must be signed with the user's existing key through Studio/the established signing process.
- [Latest re-signable IPA build](https://github.com/BBGGVP5/nimbo/actions/runs/37005611847): dispatched from 33dea19, in progress; no successful latest IPA claimed.
- [Latest Windows/Linux artifact-only packaging](https://github.com/BBGGVP5/nimbo/actions/runs/37005616172): dispatched from 33dea19, in progress, publish=false.
- [Latest Windows/Linux source CI](https://github.com/BBGGVP5/nimbo/actions/runs/37005617151) and [latest Android/shared source CI](https://github.com/BBGGVP5/nimbo/actions/runs/37005617185) dispatched on the same revision; statuses must be checked separately from previous passing runs.


## Native ping and live switching — 2 October, second slice

- Apple compiler blocker fixed: the ping notification is now in a shared internal file, with a separate-file Swift access smoke test. Full IPA on `3be12af` [succeeded](https://github.com/BBGGVP5/nimbo/actions/runs/37012002022). This earlier IPA does **not** contain the new changes below. Previous desktop packaging on `33dea19` [succeeded for all five targets](https://github.com/BBGGVP5/nimbo/actions/runs/37005616172).
- iOS ordinary Xray probes retain one selected raw proxy and only its verified terminal freedom fragment helper, including REALITY/transport fields. Default outbound is deny. Multiple proxy/balancer, redirect, chained/nested dialer and interface/file graphs remain rejected. Seven file-list Go tests passed against the verified upstream source and exact production Xray pin, including a real HTTP CONNECT fixture with an unresolvable target hostname. This is not yet full Apple bridge linking/device verification.
- Desktop native Xray `leastPing` uses an isolated observer with at most 16 retained members, the user's current probe URL and a deny fallback. Startup HTTP 503 is retried within the same deadline/cancel owner. A real pinned Xray test chose a healthy second member when the first was unavailable; cancellation with all members down produced no direct control request and cleaned the child/config. `leastLoad`, backup-loopback/ambiguous graphs and iOS health balancers remain unsupported in this slice.
- iOS active selection now has one controller for native and Compose rows: admission → observed stop → persist → awaited staging → start request. Manual stop invalidates the intent, including an awaited on-demand rearm race. Eight standalone Swift behavioral cases are defined for CI; source tests do not prove device NetworkExtension behavior. Mihomo native iOS TUN remains unavailable and incompatible selection fails before teardown.
- Desktop repeated row selections are rejected while one native switch is pending; compatibility is still checked before replacing the current session. Android uses its existing service-owned reconnect path by default when the preference has never been stored; explicit user opt-out remains respected.
- Embedded Mihomo `select` and one-shot `autoSelect` capture established connections through the exact changed group and close them only after a valid changed selection. Same/invalid selections and unrelated groups stay intact. Mobile TCP no longer holds graph.RLock across io.Copy; UDP registers its connection before releasing that lock. Real mobile-handler and desktop loopback relays passed, alongside **148 adapter Go tests** on Windows with the verified patched module. These native changes require rebuilt artifacts: the Android debug APK tested below still uses the existing AAR, not a rebuilt version of this adapter.
- Local validation: **597 Android unit tests**, zero failures, `assembleDebug` succeeded; **83 frontend tests**, TypeScript/Vite build; **134 Rust UI tests** including all ignored real CLI/AWG cases, Clippy `-D warnings`; new manifest-hash-verified Linux CLI CI runner passed both real Xray regressions end-to-end. iOS source contracts: 6 profiles, 18 ping, 4 on-demand, 5 Live Activity; native merged-source 6, sync layout 3, dropdown 4. No host VPN, services, routes, DNS or system proxy changed.
- New-revision IPA and Windows/Linux package builds will be listed separately after dispatch. Main is not merged; PR78 stays draft; public releases are not published. No universal transport parity or measured RSS/battery savings are claimed.


### Mihomo native tunnel follow-up (latest user request)

The next implementation plan is `docs/superpowers/plans/2026-10-02-mihomo-native-tunnel.md`, with iOS and desktop native ownership tracked separately.

- Added an actual portable raw-IP packet device for the public NetworkExtension packet-flow path, not another Go runtime or a fake TUN ready flag. It validates exact IPv4/IPv6 lengths/MTU, copies input/output, permits one stack owner, caps output to 128 packets and drains/wakes readers on stop. At MTU 1500 the raw queued payload is bounded to roughly 192 KiB (this is not total engine RSS).
- Existing real gVisor TCP-DNS and UDP-DNS fixtures now traverse this production device and the native Mihomo handlers, rather than injecting directly into a test-only endpoint. Five packet-device regressions plus both real packet exchanges passed 20 repeated runs. Full native suite with `with_gvisor`: **155 passed**, zero failures; `go vet` passed on the final source, including the concurrent-shutdown test.
- **Not yet an enabled iOS Mihomo VPN**: versioned start/packet ABI, native source-preserving runtime/DNS/IPv6, Swift packet-flow pump, engine-specific status/recovery and device leak acceptance remain to be wired. `StartIOS` and the UI admission remain closed, deliberately. A tested packet device alone must not enable a nonfunctional core picker. Desktop privileged Mihomo TUN and Linux System Proxy also remain unfinished.
- Hot-switch source revision `2f5041a` [Android/shared source CI](https://github.com/BBGGVP5/nimbo/actions/runs/37017146268) and [project source CI](https://github.com/BBGGVP5/nimbo/actions/runs/37017147074) both succeeded. Its [IPA](https://github.com/BBGGVP5/nimbo/actions/runs/37017139855) is still building at this check. [Desktop packaging](https://github.com/BBGGVP5/nimbo/actions/runs/37017144971): Linux x64 and ARM64 succeeded; Windows x64/x86/ARM64 still building. No latest full build success claimed until confirmed.

## Public iOS Mihomo packet-flow integration checkpoint

- Previous fb323b7 IPA run 37019518045 and Windows/Linux artifact run 37019522396 finished SUCCESS. Those artifacts contain packet-device foundations, **not** the newer provider/runtime integration below.
- Added trusted iOS packet start and bounded binary input/output exports to the same merged Go runtime. Raw IP dispatch uses upstream Mihomo sing_tun rules/protocols/DNS/groups, no Xray conversion or private utun FD scan. Native fixtures test protected loopback TCP over IPv4 and IPv6, native UDP DNS, same/changed group selections, stale generation and stop waking a pending output read. Four runtime cases passed ten repetitions; full native suite 159 cases and go vet passed.
- Swift source now connects public readPackets/writePackets, generation guard, bounded 32-packet output batches, physical interface socket scope, engine-specific status/counters/watchdog/stop/path resets. Native full documents import without flattening and expose groups/selection readback in iOS profiles. Original YAML, subscription title and source-specific cached choices are preserved.
- Native source preflight is before preference/route changes. Source DNS must be enabled. Process/UID/package filters, custom host TUN route filters and classical remote rule providers remain explicitly unsupported on iOS rather than ignored. Domain/IP providers and protocol adapters remain native.
- Local source suites do not prove Swift compilation or phone traffic. The Apple merged archive/Swift link/unsigned IPA must pass for this checkpoint; then real iPhone signed-extension tests (memory, background, sleep/wake, blocked networks, DNS/UDP/IPv6) remain required. No protocol-parity/site claim is widened just because source builds.
- Desktop managed native TUN, rebuilt Android merged AAR and the broad protocol acceptance matrix remain open work. No host routes/DNS/system proxy were changed by this session.

### Packet integration follow-up

Native live delay cancellation is tested with a controlled hanging loopback HTTP
server and pre-dispatch cancellation (ten repetitions), followed by the full Go
suite and vet. Desktop controller cancellation ownership passes all 31 portable
Rust tests; four staged-helper integration tests remain ignored in this local run.
The iOS full-profile card avoids cross-file private presentation API, keeps native
automatic groups non-selectable without disabling their context menus, and stores
completed pings under source/node digests. Portable Swift cache round-trip and core admission passed the
early Apple CI gate in run 37027205853; full IPA is still building. New IPA supersedes 37024926758.
Desktop Mihomo managed TUN remains unimplemented and is still refused explicitly.

### Current artifact gate links

Code revision 62c2cec: IPA https://github.com/BBGGVP5/nimbo/actions/runs/37027205853;
Windows/Linux https://github.com/BBGGVP5/nimbo/actions/runs/37027211807.
These are artifact-only builds (publish=false); pending is not successful. Run
37024926758 passed combined C ABI and Apple provider linking, then failed on the
private UI property fixed in 62c2cec. No main merge or public release.

<details><summary>Первый прогон Windows acceptance (исторический)</summary>

### First acceptance findings — 3 October 2026

The first Windows live VM fixture at `1691d89` successfully installed/authenticated
SCM, started the actual adapter and passed TCP4/TCP6, but UDP timed out. Teardown
retired the adapter and preserved physical DNS/routes. A no-TUN loopback regression
then reproduced a concrete cause: upstream's packet socket hook received a wildcard
local bind instead of the actual UDP relay peer and incorrectly bound loopback UDP
to physical egress. The pinned dialer patch now retains the remote peer without
changing the public hook ABI; local regression, native suite/vet and source build
pass. Fresh live Windows acceptance is still required before calling it successful.
The same run's Linux x64 native/broker suite passed; ARM64 failed after the separate
Rust session test on a subsequent TCP request, including a repeated attempt. Added
isolated-only phase/status/kernel diagnostics; do not hide this as a passing build.
Project CI and Android/shared at `1691d89` are confirmed SUCCESS. Windows packages
for that earlier revision were started, but do not contain the packet-peer fix.


</details>
