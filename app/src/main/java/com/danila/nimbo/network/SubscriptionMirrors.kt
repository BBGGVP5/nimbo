package com.danila.nimbo.network

import java.net.URI
import java.net.URLDecoder

/**
 * Мультидомен подписки: панель отдаёт список зеркал сабпейджа, и клиент сам
 * переключается на живой домен, когда основной заблокирован или лёг.
 *
 * Транспорт — HTTP-заголовок ответа подписки (в стиле уже существующих
 * `nimbo-logo` / `nimbo-fallback`):
 *
 * ```
 * nimbo-mirrors: sub2.example.com, sub3.example.org:8443, https://backup.example.net
 * ```
 *
 * Разделители — запятая, точка с запятой, пробел или перевод строки. Элементом
 * может быть голый хост, `host:port` или полный URL. Схема, путь и query берутся
 * у исходной ссылки подписки, если зеркало их не задало: панель отдаёт один и тот
 * же сабпейдж на разных доменах, поэтому дублировать путь в заголовке не нужно.
 *
 * Список зеркал сохраняется вместе с подпиской — иначе после блокировки основного
 * домена клиент уже никогда не смог бы прочитать заголовок и остался бы без связи.
 */
object SubscriptionMirrors {

    /** Заголовок, который стоит отдавать панели. Остальные — совместимость. */
    const val PRIMARY_HEADER = "nimbo-mirrors"

    /** Имена заголовков в порядке приоритета; OkHttp сравнивает их без учёта регистра. */
    val HEADER_NAMES: List<String> = listOf(
        PRIMARY_HEADER,
        "x-nimbo-mirrors",
        "subscription-mirrors",
        "x-subscription-mirrors",
        "profile-mirrors",
        "dropweb-mirrors",
    )

    /** Больше восьми доменов перебирать бессмысленно: это минуты ожидания на таймаутах. */
    const val MAX_MIRRORS = 8

    private val SEPARATORS = Regex("""[,;\s]+""")
    private val HOST_PATTERN = Regex("""^[A-Za-z0-9._~-]+(\.[A-Za-z0-9._~-]+)+(:\d{1,5})?$""")

    /**
     * Разбирает значение заголовка в список зеркал. Мусорные элементы отбрасываются
     * молча: заголовок приходит извне, ломать из-за него обновление подписки нельзя.
     */
    fun parse(raw: String?): List<String> {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return emptyList()

        val seen = LinkedHashSet<String>()
        for (chunk in value.split(SEPARATORS)) {
            val entry = chunk.trim().trim('"', '\'').trimEnd('/')
            if (entry.isBlank()) continue
            if (!isPlausibleEntry(entry)) continue
            seen += entry
            if (seen.size >= MAX_MIRRORS) break
        }
        return seen.toList()
    }

    private fun isPlausibleEntry(entry: String): Boolean {
        if (entry.contains("://")) {
            val uri = runCatching { URI(entry) }.getOrNull() ?: return false
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return false
            return !uri.host.isNullOrBlank()
        }
        return HOST_PATTERN.matches(entry)
    }

    /**
     * Подставляет зеркало в ссылку подписки.
     *
     * Если зеркало задано без пути, меняются только схема/хост/порт, а путь и query
     * остаются от исходной ссылки (там лежит токен подписки). Если у зеркала есть
     * собственный путь, оно используется целиком, а query подставляется из исходной
     * ссылки, когда своего нет.
     *
     * @return готовый URL или null, если ссылку разобрать не удалось.
     */
    fun rewrite(primaryUrl: String, mirror: String): String? {
        val primary = runCatching { URI(primaryUrl.trim()) }.getOrNull() ?: return null
        val primaryScheme = primary.scheme?.lowercase()?.takeIf { it.isNotBlank() } ?: "https"

        val normalizedMirror = if (mirror.contains("://")) mirror.trim() else "$primaryScheme://${mirror.trim()}"
        val mirrorUri = runCatching { URI(normalizedMirror) }.getOrNull() ?: return null
        val mirrorHost = mirrorUri.host?.takeIf { it.isNotBlank() } ?: return null

        val scheme = mirrorUri.scheme?.lowercase()?.takeIf { it.isNotBlank() } ?: primaryScheme
        val authority = buildString {
            mirrorUri.userInfo?.takeIf { it.isNotBlank() }?.let { append(it).append('@') }
            append(mirrorHost)
            if (mirrorUri.port > 0) append(':').append(mirrorUri.port)
        }

        val mirrorPath = mirrorUri.rawPath?.trimEnd('/').orEmpty()
        val hasOwnPath = mirrorPath.isNotBlank()

        val path = if (hasOwnPath) mirrorPath else primary.rawPath.orEmpty()
        val query = if (hasOwnPath) mirrorUri.rawQuery ?: primary.rawQuery else primary.rawQuery
        val fragment = primary.rawFragment

        return buildString {
            append(scheme).append("://").append(authority)
            if (path.isNotBlank() && !path.startsWith("/")) append('/')
            append(path)
            query?.takeIf { it.isNotBlank() }?.let { append('?').append(it) }
            fragment?.takeIf { it.isNotBlank() }?.let { append('#').append(it) }
        }
    }

    /**
     * Порядок обхода доменов: сначала зеркало, которое сработало в прошлый раз
     * (иначе клиент каждый раз упирался бы в заблокированный основной домен и ждал
     * таймаут), затем основная ссылка, затем остальные зеркала.
     */
    fun candidates(
        primaryUrl: String,
        mirrors: List<String>,
        preferredUrl: String? = null
    ): List<String> {
        val primary = primaryUrl.trim()
        if (primary.isBlank()) return emptyList()

        val ordered = LinkedHashSet<String>()
        val rewritten = mirrors.mapNotNull { rewrite(primary, it) }.filter { !it.equals(primary, ignoreCase = true) }

        preferredUrl?.trim()
            ?.takeIf { it.isNotBlank() && (it.equals(primary, ignoreCase = true) || it in rewritten) }
            ?.let { ordered += it }

        ordered += primary
        ordered += rewritten
        return ordered.toList()
    }

    /** Хост ссылки — для логов и коротких подписей в интерфейсе. */
    fun hostOf(url: String): String? =
        runCatching { URI(url.trim()).host }.getOrNull()?.takeIf { it.isNotBlank() }

    /** Ссылка подписки без служебных параметров + зеркала, которые в ней лежали. */
    data class LinkWithMirrors(val url: String, val mirrors: List<String>)

    /** Параметры ссылки, которыми можно передать зеркала при импорте. */
    private val URL_PARAM_NAMES = setOf("mirrors", "nimbo-mirrors", "nimbo_mirrors")

    /**
     * Достаёт зеркала прямо из ссылки подписки:
     *
     * ```
     * https://sub.example.com/sub/abc123?mirrors=sub2.example.com,sub3.example.net
     * ```
     *
     * Это единственный способ пережить блокировку основного домена, случившуюся до
     * первой удачной загрузки: заголовок ответа прочитать уже неоткуда, а ссылку
     * пользователь получает из канала/бота и импортирует как обычно — в том числе
     * по QR и deep link.
     *
     * Служебный параметр из ссылки вырезается: он не нужен панели и не должен
     * попадать в сохранённый URL подписки, иначе повторный импорт той же подписки
     * с другим набором зеркал выглядел бы как новая подписка.
     */
    fun extractFromUrl(rawUrl: String): LinkWithMirrors {
        val trimmed = rawUrl.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull()
            ?: return LinkWithMirrors(trimmed, emptyList())
        val query = uri.rawQuery?.takeIf { it.isNotBlank() }
            ?: return LinkWithMirrors(trimmed, emptyList())

        val keptParams = mutableListOf<String>()
        val found = mutableListOf<String>()
        for (part in query.split('&')) {
            if (part.isBlank()) continue
            val key = part.substringBefore('=').lowercase()
            if (key in URL_PARAM_NAMES) {
                val rawValue = part.substringAfter('=', "")
                val decoded = runCatching {
                    URLDecoder.decode(rawValue, Charsets.UTF_8.name())
                }.getOrDefault(rawValue)
                found += parse(decoded)
            } else {
                keptParams += part
            }
        }

        if (found.isEmpty()) return LinkWithMirrors(trimmed, emptyList())

        val cleanUrl = buildString {
            append(uri.scheme ?: "https").append("://")
            uri.rawAuthority?.let { append(it) }
            append(uri.rawPath.orEmpty())
            if (keptParams.isNotEmpty()) append('?').append(keptParams.joinToString("&"))
            uri.rawFragment?.takeIf { it.isNotBlank() }?.let { append('#').append(it) }
        }

        return LinkWithMirrors(cleanUrl, found.distinct().take(MAX_MIRRORS))
    }

    /** Объединяет уже известные зеркала с новыми, сохраняя порядок и лимит. */
    fun merge(known: List<String>, added: List<String>): List<String> {
        val merged = LinkedHashSet<String>()
        for (entry in known + added) {
            if (entry.isBlank()) continue
            if (merged.none { it.equals(entry, ignoreCase = true) }) merged += entry
            if (merged.size >= MAX_MIRRORS) break
        }
        return merged.toList()
    }
}
