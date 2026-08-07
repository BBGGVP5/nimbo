package com.danila.nimbo.network

internal object BundledReleaseNotes {
    fun forVersion(version: String, isEnglish: Boolean): String? {
        if (UpdatePolicy.normalizedVersionTag(version) != "1.0.2") return null
        return if (isEnglish) EN_1_0_2 else RU_1_0_2
    }

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
