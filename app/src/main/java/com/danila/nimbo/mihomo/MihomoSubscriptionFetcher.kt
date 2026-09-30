package com.danila.nimbo.mihomo

import com.danila.nimbo.NebulaGuardApplication
import com.danila.nimbo.network.NimboDns
import com.danila.nimbo.network.SubscriptionManager
import com.danila.nimbo.network.NativeMihomoDocument
import com.danila.nimbo.utils.AppVersionManager
import com.danila.nimbo.utils.PreferencesManager
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit

/** User-requested HTTPS subscription. Never logs a URL: its query may contain a token. */
object MihomoSubscriptionFetcher {
    private val client = OkHttpClient.Builder()
        .dns(NimboDns.privacyAware)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    // Optional template discovery must not stall an otherwise successful server refresh.
    private val discoveryClient = client.newBuilder().callTimeout(8, TimeUnit.SECONDS).build()

    internal fun checkedUrl(value: String): HttpUrl {
        val url = value.toHttpUrlOrNull() ?: throw MihomoException("INVALID_SUBSCRIPTION_URL")
        if (url.scheme != "https" || url.host.isBlank() || url.username.isNotEmpty() ||
            url.password.isNotEmpty() || url.fragment != null) throw MihomoException("INVALID_SUBSCRIPTION_URL")
        return url
    }

    /** Public Remnawave format route; query values (including credentials) stay intact. */
    internal fun templateUrl(sourceUrl: String): String {
        val original = checkedUrl(sourceUrl.trim())
        val builder = original.newBuilder()
        while (builder.build().pathSegments.lastOrNull().isNullOrEmpty() && builder.build().pathSegments.size > 1) {
            builder.removePathSegment(builder.build().pathSegments.lastIndex)
        }
        val segments = builder.build().pathSegments
        if (segments.lastOrNull()?.lowercase() in setOf("json", "v2ray-json", "singbox", "clash", "stash", "mihomo")) {
            builder.removePathSegment(segments.lastIndex)
        }
        return builder.addPathSegment("mihomo").build().toString()
    }

    fun discover(sourceUrl: String, includeOriginal: Boolean = true): MihomoSubscriptionTemplates =
        discover(sourceUrl, includeOriginal, { url ->
            fetch(url, if (includeOriginal) client else discoveryClient, identityHeaders())
        }) { SubscriptionManager.detectNativeMihomoDocuments(it) }

    internal fun discover(
        sourceUrl: String,
        includeOriginal: Boolean,
        fetchBody: (String) -> String,
        inspect: (String) -> List<NativeMihomoDocument>
    ): MihomoSubscriptionTemplates {
        val original = checkedUrl(sourceUrl.trim()).toString()
        val candidates = (if (includeOriginal) listOf(original, templateUrl(original)) else listOf(templateUrl(original))).distinct()
        var failure: MihomoException? = null
        for (candidate in candidates) {
            try {
                val documents = inspect(fetchBody(candidate))
                if (documents.isNotEmpty()) return MihomoSubscriptionTemplates(candidate, documents)
            } catch (error: MihomoException) {
                failure = error
                // An access denial must not be circumvented by probing another route.
                if (error.code == "SUBSCRIPTION_ACCESS_DENIED") throw error
            }
        }
        throw failure ?: MihomoException("SUBSCRIPTION_MIHOMO_UNAVAILABLE")
    }

    fun fetch(sourceUrl: String): String = fetch(sourceUrl, client, identityHeaders())

    private fun identityHeaders(): Map<String, String> {
        val context = NebulaGuardApplication.instance
        val preferences = PreferencesManager(context)
        val hwid = AppVersionManager.getHWID(context)
        return mapOf(
            "User-Agent" to AppVersionManager.getUserAgent(context),
            "x-device-id" to hwid,
            "x-hwid" to hwid,
            "x-device-os" to "Android",
            "x-ver-os" to AppVersionManager.getOSVersion(),
            "x-device-model" to (preferences.customDeviceName?.takeIf(String::isNotBlank) ?: AppVersionManager.getDeviceModel()),
            "x-app-version" to AppVersionManager.getVersionName(context)
        )
    }

    internal fun fetch(sourceUrl: String, transport: OkHttpClient, identityHeaders: Map<String, String>): String {
        var url = checkedUrl(sourceUrl.trim())
        val origin = url
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(
            transport.callTimeoutMillis.takeIf { it > 0 }?.toLong() ?: 25_000L)
        repeat(5) { redirect ->
            val builder = Request.Builder().url(url).header("Accept", "application/yaml, text/yaml, text/plain, */*")
                .header("Cache-Control", "no-cache")
            identityHeaders.forEach { (key, value) ->
                // Device identifiers belong to the subscription service, not a redirected CDN.
                if (!key.startsWith("x-", true) || (url.host == origin.host && url.port == origin.port)) builder.header(key, value)
            }
            val response = try {
                val remaining = deadline - System.nanoTime()
                if (remaining <= 0) throw MihomoException("SUBSCRIPTION_FETCH_FAILED")
                val call = transport.newCall(builder.get().build())
                call.timeout().timeout(remaining, TimeUnit.NANOSECONDS)
                call.execute()
            }
                catch (_: Exception) { throw MihomoException("SUBSCRIPTION_FETCH_FAILED") }
            response.use {
                runCatching { com.danila.nimbo.utils.SupportDiagnosticStore.captureHeaders(
                    NebulaGuardApplication.instance, sourceUrl, it.headers.toMultimap()) }
                if (it.code in 300..399) {
                    if (redirect == 4) throw MihomoException("SUBSCRIPTION_REDIRECT_LIMIT")
                    val location = it.header("Location") ?: throw MihomoException("INVALID_SUBSCRIPTION_URL")
                    url = checkedUrl(url.resolve(location)?.toString() ?: "")
                    return@repeat
                }
                if (it.code == 401 || it.code == 403) throw MihomoException("SUBSCRIPTION_ACCESS_DENIED")
                if (!it.isSuccessful) throw MihomoException("SUBSCRIPTION_HTTP_ERROR")
                val body = it.body
                if (body.contentLength() > MihomoProtocol.MAX_SOURCE_BYTES)
                    throw MihomoException("INVALID_YAML_SIZE_OR_ENCODING")
                return decode(body.byteStream())
            }
        }
        throw MihomoException("SUBSCRIPTION_REDIRECT_LIMIT")
    }

    /** Byte and UTF-8 validation happen before touching persisted profiles. */
    internal fun decode(stream: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (output.size() + count > MihomoProtocol.MAX_SOURCE_BYTES)
                throw MihomoException("INVALID_YAML_SIZE_OR_ENCODING")
            output.write(buffer, 0, count)
        }
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(output.toByteArray())).toString().also(MihomoProtocol::checkSource)
        } catch (_: CharacterCodingException) {
            throw MihomoException("INVALID_YAML_SIZE_OR_ENCODING")
        }
    }
}
