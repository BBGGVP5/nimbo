# Android port UI changelog — 2026-06-04

## Обычный changelog

- Обновлен экран "Внешний вид": добавлены карточки выбора системной, светлой, темной и OLED-темы с мини-превью.
- Настройка вида кнопки подключения перенесена из "Серверы" во "Внешний вид".
- Добавлен выбор фоновых пресетов: Glass, Aurora, Mesh, Stars, Cyberpunk, Deep Space, Fire, Lava, Neon, Nordic, Blossom.
- Улучшено выделение выбранного сервера: теперь используется мягкий акцентный фон и контур без лишней цветной полосы.
- В списках серверов добавлены избранное, отметка избранного рядом с флагом, меню "три точки", проверка пинга, переименование и удаление из списка.
- Настройки подписки стали ближе к Windows-версии: URL с копированием, ссылки провайдера, объявление провайдера, удаление, отмена и сохранение.
- Убрана цветная линия под вкладками настроек.
- Добавлены более плавные фоновые анимации и акцентные переходы для выбранных элементов.
- По changelog Windows дополнительно перенесен вид приватных серверов: если нет описания, в UI показывается техническая строка вроде `VLESS · TLS`, а не домен и порт.
- В мини-интерфейс добавлен экран "Уведомления" с историей событий, относительным временем, очисткой всей истории и удалением отдельных записей.
- Переключатель языка во "Внешнем виде" и на отдельном экране языка переделан в карточки с флагами/системной иконкой.
- Уведомления при выборе/переключении сервера теперь используют отображаемое имя сервера, включая локальное переименование.
- Превью "Стиль интерфейса" заменены на Android-превью: мини-экран телефона с кнопкой подключения, серверным блоком и нижней навигацией для Nimbo Glass и Material You.
- Превью фоновых пресетов заменены на мобильные mini-screen cards с blob-слоями и UI поверх; добавлены все Android-фоны 0..14.
- User-Agent обновлен до формата `Nimbo/<версия>/Android` для подписок, Remnawave API, пинга и сетевых инструментов.
- JSON/BASE64 подписки теперь подтягивают `meta.description`/`serverDescription` в описание сервера.
- Технические имена `server`, `proxy`, `outbound` больше не показываются как нормальные названия серверов, если есть описание или fallback.
- Постоянная кнопка лайка убрана из строки сервера: избранное переключается через меню `...`, а у избранного сервера остается маленькая отметка около логотипа.

## Технический changelog

- `PreferencesManager`:
  - добавлены локальные server UI overrides: `server_name_overrides_v1`;
  - добавлены скрытые серверы: `hidden_server_keys_v1`;
  - добавлены методы `getServerDisplayName`, `setServerDisplayName`, `getHiddenServerKeys`, `hideServer`, `unhideServer`;
  - добавлен метод `removeNotificationFromHistory` для удаления одной записи истории уведомлений;
  - расширен диапазон `backgroundStyle` до `0..14`.
- `NimboMiniApp.kt`:
  - списки серверов используют `serverUiTitle(...)`, фильтруют скрытые серверы и учитывают локальные переименования в поиске/сортировке;
  - `WindowsProfileServerLine` расширен параметрами `displayName`, `isFavorite`, `rowShape`, `onToggleFavorite`, `onRename`, `onHide`;
  - отдельная кнопка пинга в строке заменена на пункт меню "Проверить пинг";
  - избранное хранится через существующие `pinnedServerKeys`;
  - добавлен `NimboRenameServerDialog`;
  - добавлен `ProviderDialogLinkButton`;
  - `WindowsSelectedServerBar` отображает локальное имя сервера и получает accent-aware состояние;
  - `selectServer(...)` показывает toast/top notification с `serverUiTitle(...)`, поэтому локальное переименование видно сразу;
  - `ThemeSettingsSection` получил блоки connection style, background presets и preview cards для theme mode;
  - добавлены `InterfaceStylePreviewCard`, `InterfacePreviewKind` и mobile preview layout для Nimbo Glass / Material You;
  - `BackgroundPresetTile` теперь рисует Android mini-screen preview с палитрами `backgroundPresetColors(...)`;
  - `LanguageRow` переделан в accent-aware карточку с флагом/системной иконкой и выбранным чек-маркером;
  - добавлены `MiniDestination.Notifications`, `NimboNotificationsScreen`, `NimboNotificationHistoryCard` и пункт "Уведомления" в верхней сетке настроек;
  - действие избранного перенесено из постоянной кнопки строки сервера в меню `...`;
  - `serverTitle(...)` фильтрует generic-имена через `SubscriptionManager.isGenericServerName(...)`.
  - `SettingsSectionTabs` больше не рисует цветной scroll indicator.
- `SubscriptionManager` / `LinkParser`:
  - дефолтные попытки загрузки подписки начинаются с `Nimbo/<version>/Android`;
  - добавлено чтение `meta.description`, `server-description` и base64/json `meta` в описания серверов;
  - generic template names не используются как названия XRAY_JSON серверов.
- `AppVersionManager`, `PreferencesManager`, `RemnawaveApiClient`, `PingManager`, diagnostics/network tools`:
  - User-Agent приведен к формату `Nimbo/<version>/Android`.
- `Theme.kt`:
  - `BackgroundStyleMode` расширен значениями `CYBERPUNK`, `DEEP_SPACE`, `FIRE`, `LAVA`, `NEON`, `NORDIC`, `BLOSSOM`;
  - добавлен маппинг `backgroundStyle` 8..14.
- `AnimatedGradientBackground.kt` и `NimboBackdrop`:
  - добавлены палитры и blob-like анимации для новых фоновых пресетов.
- `SubscriptionSettingsDialog`:
  - добавлены URL copy field, provider links, provider announcement и delete action;
  - диалог ограничен по высоте и получил вертикальный скролл.

## Проверка

- Успешно выполнено: `./gradlew.bat :app:assembleDebug`.
- Debug APK собраны в `app/build/outputs/apk/debug/`.
