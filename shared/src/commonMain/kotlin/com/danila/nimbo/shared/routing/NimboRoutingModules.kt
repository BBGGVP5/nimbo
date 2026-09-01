package com.danila.nimbo.shared.routing

/**
 * Модули маршрутизации: набор правил, написанный человеком.
 *
 * Формат намеренно взят тот же, что у Shadowrocket и Surge, — строками вида
 * `DOMAIN-SUFFIX,example.com,DIRECT`. Люди переносят готовые наборы из этих
 * приложений, и требовать переписать их в свой синтаксис значит выбросить
 * тысячи уже написанных правил.
 *
 * Разбор живёт в общем модуле: Android и iOS должны понимать один и тот же
 * текст одинаково, иначе один и тот же модуль на двух устройствах вёл бы себя
 * по-разному.
 */

/** Куда отправлять совпавший трафик. */
enum class NimboModulePolicy(val outboundTag: String) {
    DIRECT("direct"),
    PROXY("proxy"),
    REJECT("block")
}

/**
 * Правило после разбора: домены и адреса уже разделены, потому что Xray
 * принимает их разными полями.
 */
data class NimboModuleRule(
    val domains: List<String> = emptyList(),
    val ips: List<String> = emptyList(),
    val policy: NimboModulePolicy
)

/** Один модуль так, как его хранит приложение. */
data class NimboModule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val text: String
)

/** Итог разбора: имя из заголовка, правила и то, что разобрать не удалось. */
data class NimboParsedModule(
    val name: String?,
    val description: String?,
    val rules: List<NimboModuleRule>,
    val skippedLines: Int
)

object NimboModuleParser {

    /**
     * Разбирает текст модуля.
     *
     * Секция `[General]` намеренно пропускается: она описывает поведение DNS
     * и системного стека того приложения, откуда пришёл набор, и переносить
     * её настройки на Xray было бы догадкой. Правила же переносятся точно.
     */
    fun parse(text: String): NimboParsedModule {
        var name: String? = null
        var description: String? = null
        var inRules = false
        var skipped = 0
        val rules = mutableListOf<NimboModuleRule>()

        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line.isEmpty() -> Unit
                line.startsWith("#!name=", ignoreCase = true) ->
                    name = line.substringAfter('=').trim().ifBlank { null }
                line.startsWith("#!desc=", ignoreCase = true) ->
                    description = line.substringAfter('=').trim().ifBlank { null }
                // Комментарии бывают и без «!», их просто пропускаем.
                line.startsWith("#") || line.startsWith("//") || line.startsWith(";") -> Unit
                line.startsWith("[") -> inRules = line.equals("[Rule]", ignoreCase = true)
                inRules -> {
                    val rule = parseRule(line)
                    if (rule == null) skipped++ else rules.add(rule)
                }
                else -> Unit
            }
        }

        return NimboParsedModule(
            name = name,
            description = description,
            rules = rules,
            skippedLines = skipped
        )
    }

    /** Правила всех включённых модулей одним списком, в порядке модулей. */
    fun rulesOf(modules: List<NimboModule>): List<NimboModuleRule> =
        modules.filter { it.enabled }.flatMap { parse(it.text).rules }

    private fun parseRule(line: String): NimboModuleRule? {
        val parts = line.split(',').map { it.trim() }
        if (parts.size < 2) return null
        val kind = parts[0].uppercase()
        // Политика может отсутствовать (`FINAL,DIRECT`) — тогда она во второй
        // позиции; в обычных правилах она третья.
        val policy = policyOf(parts.getOrNull(2) ?: parts.getOrNull(1)) ?: return null
        val value = parts[1]

        return when (kind) {
            // Точное совпадение домена: в Xray это префикс `full:`.
            "DOMAIN" -> NimboModuleRule(domains = listOf("full:$value"), policy = policy)
            "DOMAIN-SUFFIX" -> NimboModuleRule(domains = listOf("domain:$value"), policy = policy)
            "DOMAIN-KEYWORD" -> NimboModuleRule(domains = listOf(value), policy = policy)
            "IP-CIDR", "IP-CIDR6", "IP6-CIDR" ->
                NimboModuleRule(ips = listOf(value.substringBefore(",no-resolve")), policy = policy)
            "GEOIP" -> NimboModuleRule(ips = listOf("geoip:${value.lowercase()}"), policy = policy)
            "GEOSITE", "RULE-SET" ->
                NimboModuleRule(domains = listOf("geosite:${value.lowercase()}"), policy = policy)
            // `FINAL` описывает поведение по умолчанию, а его задаёт профиль
            // маршрутизации: модуль не должен молча переопределять выбор.
            "FINAL" -> null
            else -> null
        }
    }

    private fun policyOf(value: String?): NimboModulePolicy? = when (value?.uppercase()) {
        "DIRECT" -> NimboModulePolicy.DIRECT
        "PROXY" -> NimboModulePolicy.PROXY
        "REJECT", "REJECT-DROP", "REJECT-TINYGIF", "BLOCK" -> NimboModulePolicy.REJECT
        else -> null
    }
}
