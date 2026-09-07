package com.danila.nimbo.vpn

/**
 * Разбор причины, по которой не удалось подключиться.
 *
 * Раньше пользователь видел одну строку вида «Ошибка подключения: <текст ядра>»,
 * из которой ничего не следовало. Здесь сырое сообщение превращается в пару
 * «настоящая причина + следующий шаг», а техническая строка остаётся в отчёте
 * диагностики, который можно приложить в поддержку.
 */
data class ConnectionFailure(
    /** Настоящая причина, одной строкой. */
    val reason: String,
    /** Что пользователю сделать дальше. */
    val nextStep: String,
    /** Санитизированное сообщение ядра — для отчёта и журнала. */
    val technical: String,
    val atMs: Long = System.currentTimeMillis()
)

object ConnectionFailureClassifier {

    /** Маркер, по которому и журнал, и классификатор узнают сбой TUN. */
    const val TUN_MARKER = "Failed to establish TUN"

    /**
     * @param raw сообщение ядра или исключения
     * @param tunConflictHint подсказка про конфликтующие VPN-приложения, если она есть
     * @param hasNetwork доступна ли сейчас базовая сеть
     */
    fun classify(
        raw: String?,
        tunConflictHint: String? = null,
        hasNetwork: Boolean = true
    ): ConnectionFailure {
        val technical = sanitize(raw)
        val lower = technical.lowercase()

        if (!hasNetwork) {
            return ConnectionFailure(
                reason = "Нет доступа к сети",
                nextStep = "Проверьте Wi-Fi или мобильный интернет — подключение продолжится само, когда сеть вернётся.",
                technical = technical
            )
        }

        return when {
            lower.contains(TUN_MARKER.lowercase()) ||
                lower.contains("configure tun interface") ||
                lower.contains("tun-in") -> tunFailure(technical, tunConflictHint)

            lower.contains("vpn permission") || lower.contains("not prepared") ->
                ConnectionFailure(
                    reason = "Система не выдала разрешение на VPN",
                    nextStep = "Нажмите «Подключить» ещё раз и подтвердите запрос Android на создание VPN-подключения.",
                    technical = technical
                )

            lower.contains("another vpn") || lower.contains("always-on") ->
                ConnectionFailure(
                    reason = "Активен другой VPN",
                    nextStep = "Отключите другое VPN-приложение или снимите «Постоянный VPN» в настройках Android → Сеть → VPN.",
                    technical = technical
                )

            lower.contains("unable to resolve host") ||
                lower.contains("unknownhost") ||
                lower.contains("nodename") ->
                ConnectionFailure(
                    reason = "Адрес сервера не разрешается через DNS",
                    nextStep = "Сервер или его домен могут быть заблокированы. Выберите другой сервер или обновите подписку.",
                    technical = technical
                )

            lower.contains("handshake") || lower.contains("reality") || lower.contains("tls") ->
                ConnectionFailure(
                    reason = "Сервер разорвал TLS-рукопожатие",
                    nextStep = "Обычно это блокировка или устаревшие параметры узла. Обновите подписку и попробуйте другой сервер.",
                    technical = technical
                )

            lower.contains("timeout") || lower.contains("timed out") ->
                ConnectionFailure(
                    reason = "Сервер не ответил вовремя",
                    nextStep = "Проверьте сеть и попробуйте другой сервер — этот может быть перегружен или недоступен.",
                    technical = technical
                )

            lower.contains("connection refused") || lower.contains("econnrefused") ->
                ConnectionFailure(
                    reason = "Сервер отклонил соединение",
                    nextStep = "Порт узла закрыт или сервер выключен. Обновите подписку и выберите другой сервер.",
                    technical = technical
                )

            lower.contains("unauthorized") ||
                lower.contains("invalid user") ||
                lower.contains("authentication") ->
                ConnectionFailure(
                    reason = "Сервер не принял ключ подписки",
                    nextStep = "Обновите подписку: ключ мог смениться или срок доступа закончился.",
                    technical = technical
                )

            lower.contains("address already in use") || lower.contains("bind") ->
                ConnectionFailure(
                    reason = "Локальный порт занят другим приложением",
                    nextStep = "Закройте другие прокси/VPN-клиенты и попробуйте снова.",
                    technical = technical
                )

            lower.contains("core") && lower.contains("start") ->
                ConnectionFailure(
                    reason = "Ядро Xray не запустилось с этой конфигурацией",
                    nextStep = "Обновите подписку — конфигурация узла могла измениться. Если повторяется, приложите логи в поддержку.",
                    technical = technical
                )

            else -> ConnectionFailure(
                reason = "Не удалось подключиться к серверу",
                nextStep = "Попробуйте другой сервер или обновите подписку. Если повторяется — приложите логи в поддержку.",
                technical = technical.ifBlank { "причина не сообщена ядром" }
            )
        }
    }

    private fun tunFailure(technical: String, hint: String?): ConnectionFailure {
        val detail = technical.substringAfter("$TUN_MARKER:", "").trim()
        val reason = if (detail.isNotBlank()) {
            "Не удалось создать VPN-интерфейс: $detail"
        } else {
            "Не удалось создать VPN-интерфейс (TUN)"
        }
        val nextStep = hint?.takeIf { it.isNotBlank() }
            ?: "Закройте другие VPN и сетевые модули, затем подключитесь снова."
        return ConnectionFailure(reason = reason, nextStep = nextStep, technical = technical)
    }

    /** Убирает из сообщения ссылки, адреса и домены — отчёт уходит в поддержку. */
    fun sanitize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw.trim()
            .replace(Regex("""https?://\S+"""), "[url]")
            .replace(Regex("""\b\d{1,3}(?:\.\d{1,3}){3}\b"""), "[ip]")
            .replace(Regex("""(?i)\b(?:[a-z0-9-]+\.)+[a-z]{2,}\b"""), "[host]")
            .replace(Regex("""(?i)\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b"""), "[uuid]")
            .take(300)
    }
}
