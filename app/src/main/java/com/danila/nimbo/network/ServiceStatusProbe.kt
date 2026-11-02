package com.danila.nimbo.network

import android.content.Context
import com.danila.nimbo.utils.Logger
import com.danila.nimbo.vpn.HealthProxySession
import com.danila.nimbo.vpn.HealthProxySessions
import com.danila.nimbo.vpn.LocalProxyConfig
import com.danila.nimbo.vpn.VpnManager
import com.danila.nimbo.vpn.VpnState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * «Состояние сервисов»: реальный HTTPS-запрос к каждому сервису через активный
 * туннель (локальный health-прокси Xray, тот же, что у автоподбора).
 *
 * Статусы не выдумываются:
 * - [ServiceAvailability.AVAILABLE] — сервис ответил и явного отказа по региону нет;
 * - [ServiceAvailability.REGION_BLOCKED] — сервис сам сообщил, что регион не поддерживается
 *   (признак из его ответа, см. [ServiceTarget.classify]);
 * - [ServiceAvailability.ERROR] — нет ответа, 5xx или ответ, по которому вывод сделать нельзя.
 *
 * Само приложение исключено из VPN (addDisallowedApplication), поэтому прямой
 * запрос отсюда ушёл бы мимо туннеля — только через прокси активной сессии.
 */
internal enum class ServiceAvailability { AVAILABLE, REGION_BLOCKED, ERROR }

internal data class ServiceCheckResult(
    val availability: ServiceAvailability,
    val latencyMs: Int,
    val exitIp: String?,
    val countryCode: String?,
    val checkedAtMs: Long,
    val detail: String? = null
)

internal data class ExitInfo(val ip: String, val countryCode: String?)

/** Ответ первого хопа + конечный URL после ручного следования редиректам. */
internal class ProbeResponse(
    val code: Int,
    val finalUrl: String,
    val body: String,
    /** Время ответа сервиса по уже открытому соединению; без прогрева — время первого ответа. */
    val latencyMs: Int
)

internal class ServiceTarget(
    val id: String,
    val name: String,
    val url: String,
    val readBody: Boolean = false,
    val followRedirects: Boolean = false,
    val desktopAgent: Boolean = true,
    val headers: Map<String, String> = emptyMap(),
    val enabledByDefault: Boolean = true,
    /** Нельзя сделать вывод — [ServiceAvailability.ERROR] с пояснением в [ServiceVerdict.detail]. */
    val classify: (ProbeResponse) -> ServiceVerdict = ::reachable
)

internal data class ServiceVerdict(val availability: ServiceAvailability, val detail: String? = null)

/** Сервис отвечает; региональной логики у проверки нет. 5xx — сервер не смог ответить. */
internal fun reachable(response: ProbeResponse): ServiceVerdict = when (response.code) {
    in 200..499 -> ServiceVerdict(ServiceAvailability.AVAILABLE)
    else -> ServiceVerdict(ServiceAvailability.ERROR, "HTTP ${response.code}")
}

internal object ServiceStatusCatalog {
    private val geminiFlag = Regex("""45631641(?:\\?"\s*:\s*|,null,)(true|false)""")
    private val geminiRegion = Regex(""",2,1,200,"([A-Z]{3})"""")
    private val biliCode = Regex(""""code"\s*:\s*(-?\d+)""")

    val targets: List<ServiceTarget> = listOf(
        ServiceTarget("google", "Google", "https://www.google.com/generate_204") {
            if (it.code == 204) ServiceVerdict(ServiceAvailability.AVAILABLE)
            else ServiceVerdict(ServiceAvailability.ERROR, "HTTP ${it.code}")
        },
        ServiceTarget("github", "GitHub", "https://github.com/"),
        ServiceTarget("youtube", "YouTube", "https://www.youtube.com/generate_204") {
            if (it.code == 204) ServiceVerdict(ServiceAvailability.AVAILABLE)
            else ServiceVerdict(ServiceAvailability.ERROR, "HTTP ${it.code}")
        },
        // OpenAI сам отвечает unsupported_country(_region_territory) для закрытых стран.
        ServiceTarget(
            "chatgpt", "ChatGPT", "https://api.openai.com/compliance/cookie_requirements", readBody = true,
            headers = mapOf("Authorization" to "Bearer null", "Origin" to "https://platform.openai.com",
                "Referer" to "https://platform.openai.com/")
        ) {
            when {
                it.body.contains("unsupported_country", ignoreCase = true) ->
                    ServiceVerdict(ServiceAvailability.REGION_BLOCKED)
                it.code in 200..499 -> ServiceVerdict(ServiceAvailability.AVAILABLE)
                else -> ServiceVerdict(ServiceAvailability.ERROR, "HTTP ${it.code}")
            }
        },
        // Закрытый регион claude.ai редиректит на anthropic.com/app-unavailable-in-region.
        ServiceTarget("claude", "Claude", "https://claude.ai/", followRedirects = true) {
            when {
                it.finalUrl.contains("app-unavailable-in-region") -> ServiceVerdict(ServiceAvailability.REGION_BLOCKED)
                else -> reachable(it)
            }
        },
        // Страница Gemini содержит флаг доступности 45631641 (true/false) и код страны.
        ServiceTarget("gemini", "Gemini", "https://gemini.google.com/", readBody = true) {
            val region = geminiRegion.find(it.body)?.groupValues?.get(1)
            when (geminiFlag.find(it.body)?.groupValues?.get(1)) {
                "true" -> ServiceVerdict(ServiceAvailability.AVAILABLE)
                "false" -> ServiceVerdict(ServiceAvailability.REGION_BLOCKED, region?.let { r -> "Google: $r" })
                else -> if (it.code !in 200..299) ServiceVerdict(ServiceAvailability.ERROR, "HTTP ${it.code}")
                    else ServiceVerdict(ServiceAvailability.ERROR, "Нет признака доступности на странице")
            }
        },
        ServiceTarget("netflix", "Netflix", "https://www.netflix.com/title/81280792", followRedirects = true),
        // Неподдерживаемый регион уводит на /unavailable (или preview-страницу).
        ServiceTarget("disney", "Disney+", "https://www.disneyplus.com/", followRedirects = true) {
            val path = it.finalUrl.substringAfter("://").substringAfter('/', "")
            when {
                path.contains("unavailable") || it.finalUrl.contains("preview.disneyplus") ->
                    ServiceVerdict(ServiceAvailability.REGION_BLOCKED)
                else -> reachable(it)
            }
        },
        ServiceTarget("prime", "Prime Video", "https://www.primevideo.com/", readBody = true, followRedirects = true) {
            when {
                it.body.contains("isServiceRestricted") -> ServiceVerdict(ServiceAvailability.REGION_BLOCKED)
                it.body.contains("\"currentTerritory\"") -> ServiceVerdict(ServiceAvailability.AVAILABLE)
                it.code !in 200..299 -> ServiceVerdict(ServiceAvailability.ERROR, "HTTP ${it.code}")
                else -> ServiceVerdict(ServiceAvailability.ERROR, "Нет признака региона на странице")
            }
        },
        ServiceTarget("spotify", "Spotify", "https://open.spotify.com/"),
        ServiceTarget("tiktok", "TikTok", "https://www.tiktok.com/"),
        ServiceTarget("discord", "Discord", "https://discord.com/api/v9/experiments"),
        // Тайтл только для материкового Китая: bilibili сам отвечает -10403 «регион недоступен».
        ServiceTarget(
            "bilibili", "bilibili",
            "https://api.bilibili.com/pgc/player/web/playurl?avid=82846771&qn=0&type=&otype=json&ep_id=307247&fourk=1&fnver=0&fnval=16&module=bangumi",
            readBody = true
        ) {
            when (biliCode.find(it.body)?.groupValues?.get(1)) {
                "0" -> ServiceVerdict(ServiceAvailability.AVAILABLE)
                "-10403" -> ServiceVerdict(ServiceAvailability.REGION_BLOCKED)
                null -> ServiceVerdict(ServiceAvailability.ERROR, "HTTP ${it.code}")
                else -> ServiceVerdict(ServiceAvailability.ERROR, "Код ответа ${biliCode.find(it.body)?.groupValues?.get(1)}")
            }
        },
        ServiceTarget("telegram", "Telegram", "https://web.telegram.org/", enabledByDefault = false),
        ServiceTarget("instagram", "Instagram", "https://www.instagram.com/", enabledByDefault = false)
    )

    fun byId(id: String): ServiceTarget? = targets.firstOrNull { it.id == id }
}

internal object ServiceStatusProbe {
    private const val TAG = "ServiceStatus"
    const val TIMEOUT_MS = 8_000
    private const val ATTEMPTS = 2
    private const val MAX_BODY_BYTES = 3 * 1024 * 1024
    private const val MAX_REDIRECTS = 5
    private const val EXIT_CACHE_MS = 60_000L
    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"

    @Volatile private var exitCache: Triple<HealthProxySession, Long, ExitInfo>? = null

    /**
     * Нужен работающий Xray с зеркальным входом. Подтверждённый маршрут health-прокси
     * (как для автоподбора) не требуется: зеркало идёт по правилам tun-in, в том числе
     * через балансировщики, для которых узел заранее не известен.
     */
    fun session(): HealthProxySession? = HealthProxySessions.active?.takeIf(::isCurrent)

    fun isCurrent(session: HealthProxySession): Boolean =
        session.mirrorReady && HealthProxySessions.active === session &&
            VpnManager.state.value == VpnState.CONNECTED &&
            VpnManager.connectedServer.value?.pingMeasurementKey() == session.ownerKey

    suspend fun check(
        target: ServiceTarget,
        session: HealthProxySession,
        exit: kotlinx.coroutines.Deferred<ExitInfo?>
    ): ServiceCheckResult {
        val response = fetch(target, session) { isCurrent(session) }
        val verdict = if (response == null) ServiceVerdict(ServiceAvailability.ERROR, "Нет ответа")
        else runCatching { target.classify(response) }
            .getOrElse { ServiceVerdict(ServiceAvailability.ERROR, "Не удалось разобрать ответ") }
        val info = exit.await()
        return ServiceCheckResult(
            availability = verdict.availability,
            latencyMs = response?.latencyMs ?: -1,
            exitIp = info?.ip.takeIf { verdict.availability != ServiceAvailability.ERROR },
            countryCode = info?.countryCode.takeIf { verdict.availability != ServiceAvailability.ERROR },
            checkedAtMs = System.currentTimeMillis(),
            detail = verdict.detail
        )
    }

    /** IP и страна выхода — как их видит Cloudflare на другом конце туннеля. Один запрос на пакет проверок. */
    suspend fun exitInfo(session: HealthProxySession): ExitInfo? {
        val now = System.currentTimeMillis()
        exitCache?.let { (cachedSession, at, info) ->
            if (cachedSession === session && now - at in 0..EXIT_CACHE_MS) return info
        }
        val info = TRACE_URLS.firstNotNullOfOrNull { url ->
            val response = fetch(ServiceTarget("trace", "trace", url, readBody = true, desktopAgent = false), session) { isCurrent(session) }
                ?.takeIf { it.code == 200 } ?: return@firstNotNullOfOrNull null
            parseTrace(response.body)
        }
        // Кэшируем только успех: неудача не должна залипать на минуту.
        if (info != null) exitCache = Triple(session, now, info)
        return info
    }

    private val TRACE_URLS = listOf("https://www.cloudflare.com/cdn-cgi/trace", "https://1.1.1.1/cdn-cgi/trace")

    internal fun parseTrace(body: String): ExitInfo? {
        val fields = body.lineSequence().mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) null else line.substring(0, index).trim() to line.substring(index + 1).trim()
        }.toMap()
        val ip = fields["ip"]?.takeIf { it.isNotBlank() } ?: return null
        val loc = fields["loc"]?.uppercase()?.takeIf { it.length == 2 && it.all(Char::isLetter) && it != "XX" && it != "T1" }
        return ExitInfo(ip, loc)
    }

    private suspend fun fetch(target: ServiceTarget, session: HealthProxySession, isCurrent: () -> Boolean): ProbeResponse? =
        withContext(Dispatchers.IO) {
            // Автобалансер выбирает узел на каждое новое соединение: зависший узел не должен
            // давать «нет ответа», если соседний рабочий — одна повторная попытка на свежем соединении.
            repeat(ATTEMPTS) { attempt ->
                if (!isCurrent()) return@withContext null
                val result = fetchOnce(target, session, isCurrent)
                result.getOrNull()?.let { return@withContext it }
                Logger.w(TAG, "${target.id}: попытка ${attempt + 1} — ${result.exceptionOrNull()?.let(::describe)}")
            }
            null
        }

    private suspend fun fetchOnce(
        target: ServiceTarget,
        session: HealthProxySession,
        isCurrent: () -> Boolean
    ): Result<ProbeResponse> {
        val authorization = Credentials.basic(session.username, session.password)
        val client = OkHttpClient.Builder()
            .proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(LocalProxyConfig.HOST, LocalProxyConfig.MIRROR_PORT)))
            .proxyAuthenticator { _, response ->
                if (!isCurrent() || response.request.header("Proxy-Authorization") != null) null
                else response.request.newBuilder().header("Proxy-Authorization", authorization).build()
            }
            .callTimeout(TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
        try {
            var url = target.url
            var coldMs = -1
            repeat(MAX_REDIRECTS + 1) {
                if (!isCurrent()) return Result.failure(IOException("маршрут сменился"))
                // Только https: прокси-учётка не должна уйти на origin по http.
                val parsed = url.toHttpUrlOrNull()?.takeIf { it.isHttps }
                    ?: return Result.failure(IOException("не https: $url"))
                val request = buildRequest(target, parsed.toString(), "GET")
                val started = System.nanoTime()
                val response = execute(client, request).getOrElse { return Result.failure(it) }
                response.use {
                    if (coldMs < 0) coldMs = elapsedMs(started)
                    val location = it.header("Location")
                    if (target.followRedirects && it.code in 300..399 && location != null) {
                        val next = parsed.resolve(location)?.toString()
                        // intent:// и прочие схемы — дальше не идём, итог по текущему ответу.
                        if (next != null && next.startsWith("https://", ignoreCase = true)) {
                            url = next
                            return@repeat
                        }
                    }
                    val body = if (target.readBody) readBounded(it) else ""
                    val warmMs = warmLatency(client, target, parsed.toString())
                    return Result.success(ProbeResponse(it.code, parsed.toString(), body, warmMs ?: coldMs))
                }
            }
            return Result.failure(IOException("слишком много редиректов"))
        } finally {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    /**
     * Первый запрос включает CONNECT через туннель, TLS-рукопожатие и выбор узла —
     * цифра выходит «непонятной» и скачет. Повтор HEAD по уже открытому соединению
     * показывает именно задержку ответа сервиса через VPN.
     */
    private suspend fun warmLatency(client: OkHttpClient, target: ServiceTarget, url: String): Int? {
        val started = System.nanoTime()
        val response = execute(client, buildRequest(target, url, "HEAD")).getOrNull() ?: return null
        response.close()
        return elapsedMs(started)
    }

    private fun buildRequest(target: ServiceTarget, url: String, method: String): Request {
        val builder = Request.Builder().url(url).method(method, null)
            .header("User-Agent", if (target.desktopAgent) DESKTOP_UA else "Nimbo Service Check/Android")
            .header("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Cache-Control", "no-cache")
        target.headers.forEach { (name, value) -> builder.header(name, value) }
        return builder.build()
    }

    private fun elapsedMs(startedNanos: Long): Int =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

    private fun describe(error: Throwable): String =
        "${error.javaClass.simpleName}: ${error.message.orEmpty().take(160)}"

    private suspend fun execute(client: OkHttpClient, request: Request): Result<Response> =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    if (continuation.isActive) continuation.resume(Result.success(response)) else response.close()
                }
            })
        }

    private fun readBounded(response: Response): String {
        val body = response.body ?: return ""
        return runCatching {
            body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                while (output.size() < MAX_BODY_BYTES) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, minOf(count, MAX_BODY_BYTES - output.size()))
                }
                output.toString(Charsets.UTF_8.name())
            }
        }.getOrDefault("")
    }
}

/** Результаты живут дольше листа: повторное открытие показывает последнюю проверку. */
internal object ServiceStatusStore {
    private const val PREFS = "service_status"
    private const val KEY_DISABLED = "disabled_ids"
    private val checkSlots = Semaphore(5)
    private const val STALE_AFTER_MS = 5 * 60_000L
    // Проверки живут дольше листа: закрыли лист посреди проверки — строка на главной
    // всё равно получит итог, а не обрывок.
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Default)

    fun launchCheck(targets: List<ServiceTarget>) {
        scope.launch { check(targets) }
    }

    /** Нет результатов текущего маршрута или самый старый из них старше пяти минут. */
    fun needsRefresh(targets: List<ServiceTarget>, nowMs: Long = System.currentTimeMillis()): Boolean {
        val current = _results.value
        return targets.any { target ->
            val result = current[target.id] ?: return@any true
            nowMs - result.checkedAtMs !in 0..STALE_AFTER_MS
        }
    }

    private val _results = MutableStateFlow<Map<String, ServiceCheckResult>>(emptyMap())
    val results: StateFlow<Map<String, ServiceCheckResult>> = _results.asStateFlow()
    private val _running = MutableStateFlow<Set<String>>(emptySet())
    val running: StateFlow<Set<String>> = _running.asStateFlow()

    /** Результат относится к маршруту, через который его получили. */
    private val _owner = MutableStateFlow<String?>(null)
    val owner: StateFlow<String?> = _owner.asStateFlow()

    fun resetIfRouteChanged(ownerKey: String?) {
        if (ownerKey != _owner.value) {
            _owner.value = ownerKey
            _results.value = emptyMap()
        }
    }

    suspend fun check(targets: List<ServiceTarget>) {
        val session = ServiceStatusProbe.session() ?: return
        resetIfRouteChanged(session.ownerKey)
        val fresh = targets.filter { it.id !in _running.value }
        if (fresh.isEmpty()) return
        _running.update { it + fresh.map(ServiceTarget::id) }
        try {
            coroutineScope {
                val exit = async { ServiceStatusProbe.exitInfo(session) }
                fresh.map { target ->
                    async {
                        try {
                            val result = checkSlots.withPermit { ServiceStatusProbe.check(target, session, exit) }
                            // Маршрут сменился за время проверки — результат чужой, не показываем.
                            if (ServiceStatusProbe.isCurrent(session)) _results.update { it + (target.id to result) }
                        } finally {
                            _running.update { it - target.id }
                        }
                    }
                }.awaitAll()
            }
        } finally {
            _running.update { it - fresh.map(ServiceTarget::id).toSet() }
        }
    }

    fun disabledIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getStringSet(KEY_DISABLED, null)
        return stored?.toSet() ?: ServiceStatusCatalog.targets.filterNot { it.enabledByDefault }.map { it.id }.toSet()
    }

    fun setDisabledIds(context: Context, ids: Set<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet(KEY_DISABLED, ids).apply()
    }
}
