package com.danila.nimbo.network

internal object BundledReleaseNotes {
    fun forVersion(version: String, isEnglish: Boolean): String? {
        return when (UpdatePolicy.normalizedVersionTag(version)) {
            "1.2.0" -> if (isEnglish) EN_1_2_0 else RU_1_2_0
            "1.2.0-beta.5" -> if (isEnglish) EN_BETA_5 else RU_BETA_5
            "1.0.2" -> if (isEnglish) EN_1_0_2 else RU_1_0_2
            else -> null
        }
    }

    private val RU_1_2_0 = """
        ## Стабильная 1.2.0
        - Изменения всех пяти бет собраны в одном стабильном выпуске.

        ## Главная и оформление
        - Мониторинг памяти доступен только при подключении, без пустых блоков и данных прошлой сессии.
        - Стили Signal и Manga, более спокойное стекло и настраиваемая анимация значков.
        - Улучшены отступы, длинные подписи и оформление списков серверов.

        ## Подписки и маршрутизация
        - Запасные домены подписки помогают обновить профиль при недоступности основного адреса.
        - Модули маршрутизации позволяют переносить свои наборы правил между устройствами.
        - Синхронизация сохраняет выбранный стиль и поддерживает iPhone и iPad.

        ## Пинг
        - Nimbo Ping: отдельный HTTP GET через каждый сервер подписки, без переключения текущего VPN. Первый метод в списке и способ по умолчанию для новых настроек.
        - Полный выбор методов, адрес проверки и таймаут; цифры, шкала, шкала с цифрами или точки.

        ## Быстрое управление
        - VPN-плитка и экран быстрого выбора сервера.
        - Состояние уведомлений, батареи и фоновых задач собрано на отдельном экране.
        - Ядро AmneziaWG 3.1.20260828 для ARM64 и ARMv7.
        - LibXray 26.9.9 с адаптацией к API v3.
    """.trimIndent()

    private val EN_1_2_0 = """
        ## Stable 1.2.0
        - Improvements from all five betas are included in one stable release.

        ## Home and appearance
        - Memory monitoring is only shown while connected, without empty panels or previous-session data.
        - Signal and Manga styles, quieter glass surfaces and configurable icon animation.
        - Improved spacing, long labels and server lists.

        ## Subscriptions and routing
        - Subscription mirrors help refresh profiles when the primary domain is unavailable.
        - Routing modules let you transfer custom rule sets between devices.
        - Synchronization preserves your chosen style and supports iPhone and iPad.

        ## Ping
        - Nimbo Ping: a separate HTTP GET through each subscription server without switching the current VPN. First in the list and the default for new settings.
        - Full method selection, test URL and timeout; numbers, bars, bars with numbers or dots.

        ## Quick controls
        - VPN quick-settings tile and quick server selection.
        - Notification, battery and background task status on a dedicated screen.
        - AmneziaWG 3.1.20260828 for ARM64 and ARMv7.
        - LibXray 26.9.9 with API v3 integration.
    """.trimIndent()

    private val RU_BETA_5 = """
        ## Оформление
        - Более спокойное стекло: меньше бликов и цветных обводок на карточках и кнопках.
        - Нижняя панель сохраняет выразительный стеклянный материал.
        - Более лёгкие заголовки и второстепенные действия; меньше декоративных рамок.
        - У Dotted приглушена сетка внутри карточек, у Manga — компактнее чернильные тени.
        - Выбранные цвета, темы и настройки прозрачности сохраняются.

        ## Версии
        - Android, iOS и приложения для компьютера переведены на 1.2.0 Beta 5.
    """.trimIndent()

    private val EN_BETA_5 = """
        ## Appearance
        - Quieter glass with fewer highlights and colored outlines on cards and controls.
        - The bottom navigation keeps its more expressive floating glass material.
        - Lighter headings and secondary actions, with less decorative chrome.
        - Subtler Dotted card texture and more compact Manga ink shadows.
        - Existing colors, themes and transparency preferences are preserved.

        ## Versions
        - Android, iOS and desktop applications now use 1.2.0 Beta 5.
    """.trimIndent()

    private val RU_1_0_2 = """
        ## Безопасные обновления
        - Добавлены каналы «Стабильный» и «Бета».
        - APK автоматически выбирается под архитектуру устройства.
        - Перед установкой проверяются SHA-256, размер файла и свободное место.
        - Прерванная загрузка продолжается с сохранённого места.
        - Появилась настройка загрузки обновлений только по Wi-Fi.
        - Небольшие исправления могут приходить как дополнительное обновление без смены номера версии.
        - После установки доступна кнопка «Что изменилось».

        ## Подключение и стабильность
        - Уведомление о новой версии теперь может приходить в фоне.
        - Ошибки режима VPN для выбранных приложений показываются внутри Nimbo без падения приложения.
        - Ускорено подключение к серверу и улучшено восстановление соединения.
        - Проверка пинга больше не переносит результат между разными нодами с одинаковым host:port.
        - Улучшен пинг российских серверов и серверов Hysteria.
        - Обновление подписки выполняется без сообщения «Изменений нет».

        ## Проверка сети
        - Добавлена «Проверка БС» для Google, Яндекса, сервисов статистики и DNS.
        - Сохраняется история проверок.
        - Ручная проверка пинга вынесена на отдельную страницу.
        - Экран проверки приведён к общему дизайну Nimbo.
    """.trimIndent()

    private val EN_1_0_2 = """
        ## Safer updates
        - Added Stable and Beta update channels.
        - The APK is selected automatically for the device architecture.
        - SHA-256, file size, and free disk space are checked before installation.
        - Interrupted downloads resume from the saved position.
        - Added a download updates over Wi-Fi only option.
        - If a release file is replaced without changing its version, Nimbo offers the corrected update again.
        - A What changed button is available after installation.

        ## Connection and stability
        - New-version notifications can now arrive in the background.
        - Per-app VPN errors are shown inside Nimbo without crashing the app.
        - Server connection and recovery are faster.
        - Ping results are no longer shared by different nodes with the same host:port.
        - Ping reliability was improved for Russian and Hysteria servers.
        - Subscription refresh no longer shows a No changes message.

        ## Network checks
        - Added an Allowlist check for Google, Yandex, analytics services, and DNS.
        - Check history is saved.
        - Manual ping was moved to a separate page.
        - The check screen now matches the rest of the Nimbo interface.
    """.trimIndent()
}
