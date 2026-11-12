@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.danila.nimbo.ui.screens

import com.danila.nimbo.ui.components.*
import androidx.compose.foundation.layout.*
import com.danila.nimbo.ui.i18n.t

import android.app.Application
import com.danila.nimbo.BuildConfig
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import com.danila.nimbo.network.ActiveProxyPing
import com.danila.nimbo.vpn.HealthProxySessions
import com.danila.nimbo.vpn.VpnManager
import kotlinx.coroutines.Job
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danila.nimbo.network.PingConfig
import com.danila.nimbo.network.PingManager
import com.danila.nimbo.network.PingProtocol
import com.danila.nimbo.ui.components.AnimatedGradientBackground
import com.danila.nimbo.ui.components.ExpressiveCircularLoader
import com.danila.nimbo.ui.components.GlassCard
import com.danila.nimbo.ui.components.GlassHeader
import com.danila.nimbo.ui.components.NebulaInputField
import com.danila.nimbo.ui.components.tick
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.vpn.LocalProxyConfig
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URI
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicLongArray
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.math.cos
import kotlin.math.sin

private data class ParsedPingTarget(val host: String, val port: Int)

private data class PingToolResult(
    val host: String,
    val attempts: List<Int>,
    val protocol: Int = 0
) {
    val success = attempts.filter { it >= 0 }
    val successCount = success.size
    val lossPercent = if (attempts.isEmpty()) 100 else (((attempts.size - successCount) * 100f) / attempts.size).roundToInt()
    val avgMs = success.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val minMs = success.minOrNull()
    val maxMs = success.maxOrNull()
    /** Mean difference between consecutive replies. */
    val jitterMs = success.zipWithNext { a, b -> kotlin.math.abs(a - b) }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
}

private val PING_QUICK_TARGETS = listOf("google.com", "ya.ru", "1.1.1.1", "8.8.8.8", "telegram.org")
private val PING_QUICK_URLS = listOf(
    "https://www.gstatic.com/generate_204",
    "https://cp.cloudflare.com/generate_204",
    "https://www.google.com/generate_204",
    "https://ya.ru",
    "https://telegram.org"
)

private fun quickUrlLabel(url: String): String =
    url.substringAfter("://").removePrefix("www.").substringBefore('/')

private data class SpeedTestResult(
    val pingMs: Int,
    val downloadMbps: Double?,
    val uploadMbps: Double?,
    val server: SpeedEndpoint,
    val error: String? = null
)

private data class SpeedEndpoint(
    val provider: String,
    val host: String,
    val countryCode: String,
    val city: String,
    val downloadUrl: String,
    val uploadUrl: String? = null,
    val bytesLimit: Long = 5_000_000L,
    val isNdt7: Boolean = false,
    val serverName: String = host,
    val networkName: String? = null,
    val asnNumber: String? = null
) {
    val displayProvider: String
        get() = when (provider) {
            "M-Lab" -> "Measurement Lab"
            else -> provider
        }

    val providerLabel: String
        get() = networkName
            ?.trim()
            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: displayProvider

    val isAutoNearest: Boolean
        get() = provider == "Cloudflare" || isNdt7

    val locationLabel: String
        get() {
            val country = countryCode.uppercase().takeIf { it.isNotBlank() && it != "??" && it != "AUTO" }
            val normalizedCity = city
                .trim()
                .takeIf { it.isNotBlank() && !it.equals("auto", ignoreCase = true) }
                ?: prettyNodeName(serverName)
            val location = listOf(normalizedCity, country).filterNotNull().joinToString(", ")
            val nearest = if (isAutoNearest) " · авто-ближайший" else ""
            return location + nearest
        }

    val detailsLabel: String
        get() = buildList {
            add(providerLabel)
            asnNumber
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { add(if (it.startsWith("AS", ignoreCase = true)) it.uppercase() else "AS$it") }
        }.joinToString(" · ")
}

private fun prettyNodeName(raw: String): String {
    val token = raw.substringBefore(".").substringBefore("-lab").trim()
    if (token.isBlank()) return "Ближайший узел"
    return token
        .replace(Regex("(^ndt-|^mlab\\d*-?)", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[-_]+"), " ")
        .trim()
        .replaceFirstChar { it.uppercase() }
        .ifBlank { "Ближайший узел" }
}

private fun recursiveFindString(node: Any?, keyAliases: Set<String>): String? {
    return when (node) {
        is JSONObject -> {
            val keys = node.keys()
            val deferred = mutableListOf<Any?>()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = node.opt(key)
                if (key.lowercase() in keyAliases) {
                    val normalized = value?.toString()?.trim()
                    if (!normalized.isNullOrBlank() && !normalized.equals("null", ignoreCase = true)) {
                        return normalized
                    }
                }
                if (value is JSONObject) deferred += value
            }
            deferred.firstNotNullOfOrNull { recursiveFindString(it, keyAliases) }
        }
        else -> null
    }
}

private val speedEndpoints = listOf(
    SpeedEndpoint(
        provider = "Selectel",
        host = "speedtest.selectel.ru",
        countryCode = "RU",
        city = "Санкт-Петербург",
        downloadUrl = "https://speedtest.selectel.ru/1GB",
        bytesLimit = 120_000_000L
    ),
    SpeedEndpoint(
        provider = "Cloudflare",
        host = "speed.cloudflare.com",
        countryCode = "AUTO",
        city = "Auto",
        downloadUrl = "https://speed.cloudflare.com/__down?bytes=120000000",
        uploadUrl = "https://speed.cloudflare.com/__up",
        bytesLimit = 120_000_000L
    )
)

private val speedHttpClient = OkHttpClient.Builder()
    .connectTimeout(6, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.SECONDS)
    .writeTimeout(0, TimeUnit.SECONDS)
    .pingInterval(5, TimeUnit.SECONDS)
    .build()

@Composable
fun PingToolScreen(mainViewModel: com.danila.nimbo.MainViewModel, onNavigateBack: () -> Unit,
    onSelectTool: (DiagnosticsTool) -> Unit = {}) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val application = context.applicationContext as Application
    val preferencesManager = remember { PreferencesManager(application) }
    val nebulaColors = LocalNebulaColors.current
    val scope = rememberCoroutineScope()

    var targetText by remember { mutableStateOf("ya.ru") }
    var isRunning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PingToolResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val protocolId by preferencesManager.pingProtocolState
    val pingUrl by preferencesManager.pingUrlState
    val timeout by preferencesManager.pingTimeoutState
    val throughProxy by preferencesManager.pingThroughProxyState
    val protocol = PingProtocol.fromId(protocolId)
    val nimbo = protocol == PingProtocol.NIMBO
    val activeRouteOnly = !nimbo && throughProxy
    val usesHealthUrl = nimbo || activeRouteOnly || protocol == PingProtocol.HTTP_GET || protocol == PingProtocol.HTTP_HEAD
    val vpnState = VpnManager.state.value
    val connectedKey = VpnManager.connectedServer.value?.pingMeasurementKey()
    var probeJob by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(protocolId, pingUrl, timeout, throughProxy, targetText, vpnState, connectedKey) {
        probeJob?.cancel()
        result = null
        error = null
        isRunning = false
    }

    fun startPing() {
        if (isRunning) return
        val selectedNode = VpnManager.selectedServer
        val nodeConfig = if (nimbo && selectedNode != null) mainViewModel.nimboConfigForServer(selectedNode) else null
        if (nimbo && nodeConfig == null) {
            error = "Выберите поддерживаемый сервер подписки; маршрут недоступен"
            return
        }
        val session = if (activeRouteOnly) HealthProxySessions.connected() else null
        if (activeRouteOnly && session == null) {
            result = null
            error = "Недоступно: требуется проверенный активный прокси-маршрут"
            return
        }
        val parsed = if (usesHealthUrl) {
            if (!ActiveProxyPing.validUrl(pingUrl)) {
                error = "Введите корректный HTTP/HTTPS URL в настройках пинга"
                return
            }
            parsePingTarget(pingUrl)
        } else parsePingTarget(targetText)
        if (parsed == null) {
            error = "Введите домен или IP-адрес"
            return
        }
        haptic.tick()
        isRunning = true
        error = null
        result = null
        probeJob = scope.launch {
            try {
                val config = PingConfig(protocol, pingUrl, timeout.coerceIn(1, 10) * 1000, throughProxy)
                val attempts = mutableListOf<Int>()
                repeat(4) { index ->
                    attempts += if (nimbo) {
                        com.danila.nimbo.network.NimboNodePing.measure(context, nodeConfig, config.testUrl, config.timeoutMs)
                    } else if (session != null) {
                        ActiveProxyPing.measure(
                            config.testUrl, config.timeoutMs, session,
                            { HealthProxySessions.isConnected(session) },
                            method = if (PingManager.effectiveProtocol(config) in listOf(PingProtocol.NIMBO, PingProtocol.HTTP_GET)) "GET" else "HEAD"
                        )
                    } else PingManager.ping(parsed.host, parsed.port, config)
                    if (index < 3) delay(250)
                }
                if (session == null || HealthProxySessions.isConnected(session)) {
                    result = PingToolResult(
                        if (nimbo) "${selectedNode?.name} · $pingUrl" else if (activeRouteOnly) "Активный маршрут · $pingUrl" else if (usesHealthUrl) pingUrl else parsed.host,
                        attempts, protocol = config.protocol.id
                    )
                } else error = "Маршрут изменился; результат сброшен"
            } finally {
                isRunning = false
            }
        }
    }

    NimboSubPageScaffold(
        title = t("Пинг", "Ping"),
        subtitle = null,
        onBack = onNavigateBack
    ) {
        DiagnosticsToolTabs(DiagnosticsTool.PING, onSelectTool)
        Spacer(Modifier.height(16.dp))
        DiagnosticsEyebrow(
            when {
                nimbo -> t("Через выбранный сервер · GET", "Through the selected server · GET")
                activeRouteOnly -> t("Активный маршрут · HTTP", "Active route · HTTP")
                usesHealthUrl -> t("Диагностика · HTTP", "Diagnostics · HTTP")
                else -> t("Диагностика · TCP", "Diagnostics · TCP")
            },
            Modifier.padding(start = 4.dp)
        )
        Spacer(Modifier.height(8.dp))
        DiagnosticsTargetField(
            value = if (usesHealthUrl) pingUrl else targetText,
            onValueChange = { if (usesHealthUrl) preferencesManager.pingUrl = it else targetText = it },
            placeholder = if (usesHealthUrl) t("URL проверки", "Check URL") else t("Домен или IP-адрес", "Domain or IP address"),
            icon = if (usesHealthUrl) Icons.Default.Link else Icons.Default.Public
        )
        Spacer(Modifier.height(14.dp))
        DiagnosticsEyebrow(t("Быстрый выбор", "Quick pick"), Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(8.dp))
        if (usesHealthUrl) {
            // Health-URL modes ping a URL, so offer known check endpoints instead of hosts.
            DiagnosticsChips(PING_QUICK_URLS, pingUrl.trim(), label = ::quickUrlLabel) {
                preferencesManager.pingUrl = it
            }
        } else {
            DiagnosticsChips(PING_QUICK_TARGETS, targetText.trim()) { targetText = it }
        }
        if (activeRouteOnly && HealthProxySessions.connected() == null) {
            Spacer(Modifier.height(8.dp))
            Text(t("Недоступно: подключите поддерживаемый VPN-маршрут", "Unavailable: connect a supported VPN route"),
                color = nebulaColors.textTertiary, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp))
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = DiagnosticsBad, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp))
        }
        Spacer(Modifier.height(16.dp))
        DiagnosticsPrimaryButton(
            text = if (isRunning) t("Пингуем…", "Pinging…") else t("Запустить пинг", "Run ping"),
            icon = Icons.Default.PlayArrow,
            onClick = ::startPing,
            enabled = !activeRouteOnly || HealthProxySessions.connected() != null,
            busy = isRunning
        )
        Spacer(Modifier.height(16.dp))
        PingResultCard(result, isRunning)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun SpeedTestScreen(onNavigateBack: () -> Unit, onSelectTool: (DiagnosticsTool) -> Unit = {}) {
    val nebulaColors = LocalNebulaColors.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isRunning by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var liveSpeed by remember { mutableFloatStateOf(0f) }
    var downloadSpeed by remember { mutableStateOf<Double?>(null) }
    var uploadSpeed by remember { mutableStateOf<Double?>(null) }
    var pingMs by remember { mutableStateOf<Int?>(null) }
    var result by remember { mutableStateOf<SpeedTestResult?>(null) }
    var statusText by remember { mutableStateOf("Сервер определится автоматически") }
    val downloadMetricTarget = (
        if (isRunning) {
            downloadSpeed
        } else {
            result?.downloadMbps ?: downloadSpeed
        }
    )?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    val uploadMetricTarget = (
        if (isRunning) {
            uploadSpeed
        } else {
            result?.uploadMbps ?: uploadSpeed
        }
    )?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
    val animatedDownloadMetric by animateFloatAsState(
        targetValue = downloadMetricTarget.toFloat(),
        animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessLow),
        label = "downloadMetric"
    )
    val animatedUploadMetric by animateFloatAsState(
        targetValue = uploadMetricTarget.toFloat(),
        animationSpec = spring(dampingRatio = 0.76f, stiffness = Spring.StiffnessLow),
        label = "uploadMetric"
    )

    fun startSpeedTest() {
        if (isRunning) return
        haptic.tick()
        isRunning = true
        progress = 0f
        liveSpeed = 0f
        downloadSpeed = null
        uploadSpeed = null
        pingMs = null
        result = null
        statusText = "Ищу ближайший сервер..."
        scope.launch {
            val server = locateNdt7Endpoint() ?: selectSpeedEndpoint()
            if (server == null) {
                result = null
                statusText = "Не удалось найти доступный сервер теста"
                isRunning = false
                return@launch
            }

            val resolvedServer = if (server.provider == "Cloudflare") detectCloudflareLocation(server) else server
            var displayServer = resolvedServer
            statusText = "Сервер: ${resolvedServer.locationLabel}"
            progress = 0.18f

            var ping = measureEndpointPing(resolvedServer) ?: -1
            if (ping < 0 && resolvedServer.isNdt7) {
                ping = measureNdt7HandshakeLatency(resolvedServer) ?: ping
            }
            pingMs = ping
            progress = 0.30f
            statusText = "Многопоточное скачивание..."
            val download = if (resolvedServer.isNdt7) {
                val ndtDownload = measureNdt7DownloadMbps(
                    endpoint = resolvedServer,
                    onProgress = { speed, done ->
                        liveSpeed = smoothSpeed(liveSpeed, speed)
                        downloadSpeed = smoothSpeed(downloadSpeed?.toFloat() ?: 0f, speed).toDouble()
                        progress = 0.30f + 0.42f * done
                    }
                )
                if (ndtDownload > 0.0) {
                    ndtDownload
                } else {
                    statusText = "NDT7 недоступен, пробую HTTP-тест..."
                    val fallback = selectSpeedEndpoint()
                    if (fallback == null) {
                        0.0
                    } else {
                        displayServer = fallback
                        if (ping < 0) {
                            ping = measureEndpointPing(fallback) ?: ping
                            pingMs = ping
                        }
                        statusText = "Сервер: ${fallback.locationLabel}"
                        measureParallelDownloadMbps(
                            endpoint = fallback,
                            onProgress = { speed, done ->
                                liveSpeed = smoothSpeed(liveSpeed, speed)
                                downloadSpeed = smoothSpeed(downloadSpeed?.toFloat() ?: 0f, speed).toDouble()
                                progress = 0.30f + 0.42f * done
                            }
                        )
                    }
                }
            } else {
                measureParallelDownloadMbps(
                    endpoint = resolvedServer,
                    onProgress = { speed, done ->
                        liveSpeed = smoothSpeed(liveSpeed, speed)
                        downloadSpeed = smoothSpeed(downloadSpeed?.toFloat() ?: 0f, speed).toDouble()
                        progress = 0.30f + 0.42f * done
                    }
                )
            }
            downloadSpeed = download.takeIf { it > 0.0 }
            if (ping < 0 && downloadSpeed != null) {
                ping = measureEndpointPing(displayServer) ?: ping
                if (ping < 0 && displayServer.isNdt7) {
                    ping = measureNdt7HandshakeLatency(displayServer) ?: ping
                }
                pingMs = ping
            }
            val upload = if (resolvedServer.isNdt7) {
                progress = 0.80f
                statusText = "Многопоточная отдача..."
                val ndtUpload = measureNdt7UploadMbps(
                    endpoint = resolvedServer,
                    onProgress = { speed, done ->
                        liveSpeed = smoothSpeed(liveSpeed, speed)
                        uploadSpeed = smoothSpeed(uploadSpeed?.toFloat() ?: 0f, speed).toDouble()
                        progress = 0.80f + 0.20f * done
                    }
                )
                ndtUpload.takeIf { it > 0.0 } ?: speedEndpoints.firstOrNull { it.uploadUrl != null && isEndpointAvailable(it) }?.let { endpoint ->
                    statusText = "Многопоточная отдача через ${endpoint.locationLabel}"
                    measureParallelUploadMbps(
                        endpoint = endpoint,
                        onProgress = { speed, done ->
                            liveSpeed = smoothSpeed(liveSpeed, speed)
                            uploadSpeed = smoothSpeed(uploadSpeed?.toFloat() ?: 0f, speed).toDouble()
                            progress = 0.80f + 0.20f * done
                        }
                    )
                }
            } else {
                val uploadEndpoint = resolvedServer.takeIf { it.uploadUrl != null }
                    ?: speedEndpoints.firstOrNull { it.uploadUrl != null && isEndpointAvailable(it) }

                uploadEndpoint?.let { endpoint ->
                    progress = 0.80f
                    statusText = if (endpoint.host == resolvedServer.host) {
                        "Многопоточная отдача..."
                    } else {
                        "Многопоточная отдача через ${endpoint.locationLabel}"
                    }
                    measureParallelUploadMbps(
                        endpoint = endpoint,
                        onProgress = { speed, done ->
                            liveSpeed = smoothSpeed(liveSpeed, speed)
                            uploadSpeed = smoothSpeed(uploadSpeed?.toFloat() ?: 0f, speed).toDouble()
                            progress = 0.80f + 0.20f * done
                        }
                    )
                }
            }
            uploadSpeed = upload?.takeIf { it > 0.0 }
                result = SpeedTestResult(
                pingMs = ping,
                downloadMbps = downloadSpeed,
                uploadMbps = uploadSpeed,
                server = displayServer
            )
            liveSpeed = maxOf(downloadSpeed ?: 0.0, uploadSpeed ?: 0.0).toFloat()
            progress = 1f
            statusText = "Готово"
            isRunning = false
        }
    }

    val shownPing = (pingMs ?: result?.pingMs)?.takeIf { it >= 0 }
    val gaugeValue = if (isRunning) liveSpeed.toDouble()
        else result?.downloadMbps ?: downloadSpeed ?: result?.uploadMbps ?: uploadSpeed ?: 0.0
    var gaugeMax by remember { mutableStateOf(100.0) }
    LaunchedEffect(gaugeValue) { if (gaugeValue * 1.15 > gaugeMax) gaugeMax = speedGaugeMax(gaugeValue) }
    val phase = when {
        !isRunning && result != null -> t("Готово", "Done")
        !isRunning -> t("Готов к тесту", "Ready")
        progress < 0.30f -> t("Подготовка", "Preparing")
        progress < 0.80f -> t("Загрузка", "Download")
        else -> t("Отдача", "Upload")
    }

    NimboSubPageScaffold(title = t("Скорость", "Speed"), subtitle = null, onBack = onNavigateBack) {
        DiagnosticsToolTabs(DiagnosticsTool.SPEED, onSelectTool)
        Spacer(Modifier.height(16.dp))
        DiagnosticsSpeedGauge(
            value = gaugeValue,
            scaleMax = gaugeMax,
            phase = phase,
            server = result?.server?.host,
            progress = if (isRunning) progress else if (result != null) 1f else 0f
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DiagnosticsSmallMetric(t("Пинг", "Ping"), shownPing?.toString() ?: "—", t("мс", "ms"), Modifier.weight(1f))
            DiagnosticsSmallMetric(t("Загрузка", "Download"),
                if (animatedDownloadMetric > 0f) animatedDownloadMetric.toDouble().formatOne() else "—",
                t("Мбит/с", "Mbps"), Modifier.weight(1f))
            DiagnosticsSmallMetric(t("Отдача", "Upload"),
                if (animatedUploadMetric > 0f) animatedUploadMetric.toDouble().formatOne() else "—",
                t("Мбит/с", "Mbps"), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        NimboPanel(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(nebulaColors.softFill, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Dns, null, Modifier.size(22.dp), tint = nebulaColors.textSecondary)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("Сервер", "Server"), color = nebulaColors.textPrimary, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold)
                    Text(
                        result?.server?.let { "${it.providerLabel} · ${it.locationLabel}" }
                            ?: if (isRunning) statusText else t("Авто (ближайший)", "Auto (nearest)"),
                        color = nebulaColors.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 2
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        DiagnosticsPrimaryButton(
            text = if (isRunning) t("Идёт тест…", "Testing…") else if (result != null) t("Повторить тест", "Run again") else t("Начать тест", "Start test"),
            icon = Icons.Default.PlayArrow,
            onClick = ::startSpeedTest,
            busy = isRunning
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PingResultCard(result: PingToolResult?, running: Boolean) {
    val colors = LocalNebulaColors.current
    val ms = { value: Int? -> value?.let { com.danila.nimbo.network.displayPingLabel(it, result?.protocol ?: 0) } ?: "—" }
    DiagnosticsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DiagnosticsEyebrow(t("Средний отклик", "Average response"), Modifier.weight(1f))
            result?.takeIf { !running }?.let { DiagnosticsEyebrow(it.host.take(28), color = colors.textSecondary) }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                when {
                    running -> "…"
                    result == null -> "—"
                    result.successCount == 0 -> t("нет ответа", "no reply")
                    else -> ms(result.avgMs)
                },
                color = if (!running && result != null && result.successCount == 0) DiagnosticsBad else colors.textPrimary,
                style = if (!running && result != null && result.successCount == 0) MaterialTheme.typography.headlineMedium
                    else MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )
            if (running || result == null || result.successCount > 0) {
                Spacer(Modifier.width(8.dp))
                Text(t("мс", "ms"), color = colors.textSecondary, modifier = Modifier.padding(bottom = 10.dp),
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
            }
        }
        Spacer(Modifier.height(18.dp))
        val shown = result?.takeIf { !running }
        Row {
            DiagnosticsHeroMetric(t("Мин", "Min"), ms(shown?.minMs), Modifier.weight(1f))
            DiagnosticsHeroMetric(t("Макс", "Max"), ms(shown?.maxMs), Modifier.weight(1f))
            DiagnosticsHeroMetric(t("Джиттер", "Jitter"), shown?.jitterMs?.toString() ?: "—", Modifier.weight(1f))
            DiagnosticsHeroMetric(t("Потери", "Loss"), shown?.let { "${it.lossPercent}%" } ?: "—", Modifier.weight(1f),
                valueColor = if ((shown?.lossPercent ?: 0) > 0) DiagnosticsWarn else colors.textPrimary)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            when {
                running -> t("Отправляем 4 запроса…", "Sending 4 probes…")
                shown == null -> t("Введите адрес и запустите пинг.", "Enter an address and run the ping.")
                else -> t("Ответов ${shown.successCount} из ${shown.attempts.size}", "${shown.successCount} of ${shown.attempts.size} replies")
            },
            color = colors.textTertiary, style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun parsePingTarget(raw: String): ParsedPingTarget? {
    val cleaned = raw.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
    if (cleaned.isBlank()) return null
    val host = cleaned.substringBefore(":").trim()
    val port = cleaned.substringAfter(":", "443").toIntOrNull()?.coerceIn(1, 65535) ?: 443
    return host.takeIf { it.isNotBlank() }?.let { ParsedPingTarget(it, port) }
}



private suspend fun locateNdt7Endpoint(): SpeedEndpoint? = withContext(Dispatchers.IO) {
    runCatching {
        val request = Request.Builder()
            .url("https://locate.measurementlab.net/v2/nearest/ndt/ndt7")
            .header("Accept", "application/json")
            .header("User-Agent", "Nimbo/${BuildConfig.VERSION_NAME}/Android")
            .build()
        speedHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            val results = root.optJSONArray("results") ?: return@withContext null
            for (index in 0 until results.length()) {
                val item = results.optJSONObject(index) ?: continue
                val urls = item.optJSONObject("urls") ?: continue
                val downloadUrl = urls.findNdt7Url("download") ?: continue
                val uploadUrl = urls.findNdt7Url("upload") ?: continue
                val host = URI(downloadUrl).host ?: item.optString("machine").takeIf { it.isNotBlank() } ?: continue
                val location = item.optJSONObject("location")
                val city = location?.optString("city")?.takeIf { it.isNotBlank() }
                    ?: prettyNodeName(item.optString("machine"))
                val country = location?.optString("country")?.takeIf { it.isNotBlank() } ?: "??"
                val networkName = recursiveFindString(
                    item,
                    setOf(
                        "asname",
                        "as_name",
                        "asnname",
                        "asn_name",
                        "networkname",
                        "network_name",
                        "organization",
                        "org",
                        "site"
                    )
                )
                val asnNumber = recursiveFindString(
                    item,
                    setOf(
                        "asnumber",
                        "as_number",
                        "asn",
                        "asnnumber",
                        "asn_number"
                    )
                )?.filter { it.isDigit() }
                return@withContext SpeedEndpoint(
                    provider = "Measurement Lab",
                    host = host,
                    countryCode = country,
                    city = city,
                    downloadUrl = downloadUrl,
                    uploadUrl = uploadUrl,
                    bytesLimit = 160_000_000L,
                    isNdt7 = true,
                    serverName = item.optString("machine").takeIf { it.isNotBlank() } ?: host,
                    networkName = networkName,
                    asnNumber = asnNumber
                )
            }
            null
        }
    }.getOrNull()
}

private fun JSONObject.findNdt7Url(direction: String): String? {
    optString("wss:///ndt/v7/$direction")
        .takeIf { it.startsWith("wss://") || it.startsWith("ws://") }
        ?.let { return it }
    val keys = keys()
    while (keys.hasNext()) {
        val key = keys.next()
        val value = optString(key)
        if (key.contains(direction, ignoreCase = true) &&
            (value.startsWith("wss://") || value.startsWith("ws://"))
        ) {
            return value
        }
    }
    return null
}

private suspend fun selectSpeedEndpoint(): SpeedEndpoint? = withContext(Dispatchers.IO) {
    speedEndpoints
        .mapNotNull { endpoint ->
            if (!isEndpointAvailable(endpoint)) {
                null
            } else {
                val medianPing = measureEndpointPing(endpoint)
                if (medianPing == null) null else endpoint to medianPing
            }
        }
        .minByOrNull { (_, ping) -> ping }
        ?.first
}

private fun isEndpointAvailable(endpoint: SpeedEndpoint): Boolean {
    return runCatching {
        val connection = URL(endpoint.downloadUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 3500
        connection.readTimeout = 3500
        connection.requestMethod = "GET"
        connection.setRequestProperty("Range", "bytes=0-1023")
        val code = connection.responseCode
        runCatching { connection.inputStream.close() }
        runCatching { connection.errorStream?.close() }
        connection.disconnect()
        code in 200..399
    }.getOrDefault(false)
}

private fun measureEndpointPing(endpoint: SpeedEndpoint): Int? {
    val tcpPorts = buildList {
        add(portFromUrl(endpoint.downloadUrl))
        endpoint.uploadUrl?.let { add(portFromUrl(it)) }
        add(443)
        add(80)
    }.distinct()

    val tcpSamples = tcpPorts
        .flatMap { port -> List(2) { measureEndpointTcpLatency(endpoint.host, port) } }
        .filterNotNull()
        .sorted()
    if (tcpSamples.isNotEmpty()) return tcpSamples[tcpSamples.size / 2]

    val probeUrls = buildList {
        if (
            endpoint.downloadUrl.startsWith("wss://", ignoreCase = true) ||
            endpoint.downloadUrl.startsWith("ws://", ignoreCase = true)
        ) {
            add("https://${endpoint.host}/")
            add("https://${endpoint.host}/ndt/v7/download")
        } else {
            add(endpoint.downloadUrl)
        }
        endpoint.uploadUrl?.let { upload ->
            if (upload.startsWith("http://", ignoreCase = true) || upload.startsWith("https://", ignoreCase = true)) {
                add(upload)
            }
        }
    }.distinct()

    val httpSamples = probeUrls
        .flatMap { probe -> List(2) { measureEndpointHttpLatency(probe) } }
        .filterNotNull()
        .sorted()
    if (httpSamples.isNotEmpty()) return httpSamples[httpSamples.size / 2]

    return null
}

private fun measureEndpointHttpLatency(probeUrl: String): Int? {
    return runCatching {
        val start = System.nanoTime()
        val connection = URL(probeUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 2600
        connection.readTimeout = 2600
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.setRequestProperty("Pragma", "no-cache")
        connection.setRequestProperty("Range", "bytes=0-1")
        val code = connection.responseCode
        runCatching { connection.inputStream.close() }
        runCatching { connection.errorStream?.close() }
        connection.disconnect()
        if (code in 100..599) {
            ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1).toInt()
        } else {
            null
        }
    }.getOrNull()
}

private fun measureEndpointTcpLatency(host: String, port: Int): Int? {
    return runCatching {
        val start = System.nanoTime()
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 2600)
        }
        ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1).toInt()
    }.getOrNull()
}

private fun portFromUrl(rawUrl: String): Int {
    return runCatching {
        val uri = URI(rawUrl)
        when {
            uri.port > 0 -> uri.port
            rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("ws://", ignoreCase = true) -> 80
            else -> 443
        }
    }.getOrDefault(443)
}

private suspend fun measureDownloadMbps(
    endpoint: SpeedEndpoint,
    onProgress: (speedMbps: Double, doneFraction: Float) -> Unit
): Double = withContext(Dispatchers.IO) {
    runCatching {
        val targetDurationNanos = 6_500_000_000L
        val connection = URL(endpoint.downloadUrl).openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 12000
        val buffer = ByteArray(64 * 1024)
        var bytes = 0L
        val start = System.nanoTime()
        var windowBytes = 0L
        var windowStart = start
        connection.inputStream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                bytes += read
                windowBytes += read
                val now = System.nanoTime()
                if (now - windowStart >= 250_000_000L) {
                    val elapsedFraction = ((now - start).toFloat() / targetDurationNanos).coerceIn(0f, 1f)
                    val bytesFraction = (bytes.toFloat() / endpoint.bytesLimit).coerceIn(0f, 1f)
                    onProgress(mbps(windowBytes, now - windowStart), maxOf(elapsedFraction, bytesFraction * 0.85f))
                    windowBytes = 0L
                    windowStart = now
                }
                if (bytes >= endpoint.bytesLimit || now - start >= targetDurationNanos) break
            }
        }
        connection.disconnect()
        val total = mbps(bytes, System.nanoTime() - start)
        onProgress(total, 1f)
        total
    }.getOrDefault(0.0)
}

private suspend fun measureParallelDownloadMbps(
    endpoint: SpeedEndpoint,
    onProgress: (speedMbps: Double, doneFraction: Float) -> Unit
): Double = withContext(Dispatchers.IO) {
    runCatching {
        val streamCount = 4
        val targetDurationNanos = 10_000_000_000L
        val totals = AtomicLongArray(streamCount)
        val start = System.nanoTime()

        coroutineScope {
            val workers = (0 until streamCount).map { streamIndex ->
                async(Dispatchers.IO) {
                    runCatching {
                        val url = urlWithCacheBuster(endpoint.downloadUrl, streamIndex)
                        val connection = URL(url).openConnection() as HttpURLConnection
                        try {
                            connection.connectTimeout = 5000
                            connection.readTimeout = 15000
                            connection.instanceFollowRedirects = true
                            connection.setRequestProperty("Cache-Control", "no-cache")
                            connection.setRequestProperty("Pragma", "no-cache")
                            val buffer = ByteArray(128 * 1024)
                            val perStreamLimit = (endpoint.bytesLimit / streamCount).coerceAtLeast(24_000_000L)
                            connection.inputStream.use { input ->
                                while (System.nanoTime() - start < targetDurationNanos &&
                                    totals.get(streamIndex) < perStreamLimit
                                ) {
                                    val read = input.read(buffer)
                                    if (read <= 0) break
                                    totals.addAndGet(streamIndex, read.toLong())
                                }
                            }
                        } finally {
                            runCatching { connection.disconnect() }
                        }
                    }
                }
            }

            var lastBytes = 0L
            var lastAt = start
            while (System.nanoTime() - start < targetDurationNanos) {
                delay(250)
                val now = System.nanoTime()
                val totalBytes = totals.sumValues()
                val elapsedFraction = ((now - start).toFloat() / targetDurationNanos).coerceIn(0f, 1f)
                val bytesFraction = (totalBytes.toFloat() / endpoint.bytesLimit).coerceIn(0f, 1f)
                onProgress(
                    mbps(totalBytes - lastBytes, now - lastAt),
                    maxOf(elapsedFraction, bytesFraction * 0.9f)
                )
                lastBytes = totalBytes
                lastAt = now
                if (workers.all { it.isCompleted }) break
            }
            workers.awaitAll()
        }

        val total = mbps(totals.sumValues(), System.nanoTime() - start)
        onProgress(total, 1f)
        total.takeIf { it > 0.0 } ?: measureDownloadMbps(endpoint, onProgress)
    }.getOrDefault(0.0)
}

private suspend fun measureNdt7DownloadMbps(
    endpoint: SpeedEndpoint,
    onProgress: (speedMbps: Double, doneFraction: Float) -> Unit
): Double = withContext(Dispatchers.IO) {
    val targetDurationNanos = 9_000_000_000L
    val completed = CompletableDeferred<Double>()
    val request = Request.Builder()
        .url(endpoint.downloadUrl)
        .header("Sec-WebSocket-Protocol", "net.measurementlab.ndt.v7")
        .header("User-Agent", "Nimbo/${BuildConfig.VERSION_NAME}/Android")
        .build()
    val start = System.nanoTime()
    var totalBytes = 0L
    var windowBytes = 0L
    var windowStart = start
    lateinit var webSocket: WebSocket

    webSocket = speedHttpClient.newWebSocket(
        request,
        object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val now = System.nanoTime()
                totalBytes += bytes.size
                windowBytes += bytes.size
                if (now - windowStart >= 220_000_000L) {
                    onProgress(
                        mbps(windowBytes, now - windowStart),
                        ((now - start).toFloat() / targetDurationNanos).coerceIn(0f, 1f)
                    )
                    windowBytes = 0L
                    windowStart = now
                }
                if (now - start >= targetDurationNanos && !completed.isCompleted) {
                    val total = mbps(totalBytes, now - start)
                    onProgress(total, 1f)
                    completed.complete(total)
                    webSocket.close(1000, "done")
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!completed.isCompleted) {
                    val elapsed = System.nanoTime() - start
                    completed.complete(mbps(totalBytes, elapsed))
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!completed.isCompleted) completed.complete(0.0)
            }
        }
    )

    withTimeoutOrNull(11_000) {
        completed.await()
    } ?: run {
        webSocket.cancel()
        mbps(totalBytes, System.nanoTime() - start)
    }
}

private suspend fun measureNdt7HandshakeLatency(endpoint: SpeedEndpoint): Int? = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(endpoint.downloadUrl)
        .header("Sec-WebSocket-Protocol", "net.measurementlab.ndt.v7")
        .header("User-Agent", "Nimbo/${BuildConfig.VERSION_NAME}/Android")
        .build()
    val start = System.nanoTime()
    val opened = CompletableDeferred<Int?>()
    val webSocket = speedHttpClient.newWebSocket(
        request,
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val latency = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1).toInt()
                if (!opened.isCompleted) opened.complete(latency)
                webSocket.close(1000, "latency-probe")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!opened.isCompleted) opened.complete(null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!opened.isCompleted) opened.complete(null)
            }
        }
    )
    val latency = withTimeoutOrNull(4_000) { opened.await() }
    if (latency == null) webSocket.cancel()
    latency
}

private suspend fun measureNdt7UploadMbps(
    endpoint: SpeedEndpoint,
    onProgress: (speedMbps: Double, doneFraction: Float) -> Unit
): Double = withContext(Dispatchers.IO) {
    val targetDurationNanos = 8_000_000_000L
    val opened = CompletableDeferred<WebSocket?>()
    val closed = CompletableDeferred<Unit>()
    val request = Request.Builder()
        .url(endpoint.uploadUrl ?: return@withContext 0.0)
        .header("Sec-WebSocket-Protocol", "net.measurementlab.ndt.v7")
        .header("User-Agent", "Nimbo/${BuildConfig.VERSION_NAME}/Android")
        .build()
    val webSocket = speedHttpClient.newWebSocket(
        request,
        object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                opened.complete(webSocket)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!closed.isCompleted) closed.complete(Unit)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!opened.isCompleted) opened.complete(null)
                if (!closed.isCompleted) closed.complete(Unit)
            }
        }
    )
    val activeSocket = withTimeoutOrNull(5_000) { opened.await() } ?: run {
        webSocket.cancel()
        return@withContext 0.0
    }

    val payload = ByteArray(64 * 1024).toByteString()
    val start = System.nanoTime()
    var totalBytes = 0L
    var windowBytes = 0L
    var windowStart = start
    while (System.nanoTime() - start < targetDurationNanos && !closed.isCompleted) {
        if (activeSocket.queueSize() > 4_000_000L) {
            delay(2)
            continue
        }
        if (!activeSocket.send(payload)) break
        totalBytes += payload.size
        windowBytes += payload.size
        val now = System.nanoTime()
        if (now - windowStart >= 220_000_000L) {
            onProgress(
                mbps(windowBytes, now - windowStart),
                ((now - start).toFloat() / targetDurationNanos).coerceIn(0f, 1f)
            )
            windowBytes = 0L
            windowStart = now
        }
    }
    activeSocket.close(1000, "done")
    val total = mbps(totalBytes, System.nanoTime() - start)
    onProgress(total, 1f)
    total
}

private suspend fun detectCloudflareLocation(fallback: SpeedEndpoint): SpeedEndpoint = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL("https://speed.cloudflare.com/cdn-cgi/trace").openConnection() as HttpURLConnection
        connection.connectTimeout = 4000
        connection.readTimeout = 4000
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val trace = parseCloudflareTrace(body)
        fallback.copy(
            countryCode = trace["loc"]?.takeIf { it.isNotBlank() } ?: "??",
            city = trace["colo"]?.takeIf { it.isNotBlank() } ?: "Auto"
        )
    }.getOrDefault(fallback.copy(countryCode = "??", city = "Auto"))
}

private fun parseCloudflareTrace(body: String): Map<String, String> {
    return body
        .lineSequence()
        .mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null else line.substring(0, idx) to line.substring(idx + 1)
        }
        .toMap()
}

private suspend fun measureUploadMbps(
    endpoint: SpeedEndpoint,
    onProgress: (speedMbps: Double, doneFraction: Float) -> Unit
): Double = withContext(Dispatchers.IO) {
    runCatching {
        val targetDurationNanos = 5_500_000_000L
        val maxBytes = 48_000_000
        val payload = ByteArray(64 * 1024)
        val connection = URL(endpoint.uploadUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setChunkedStreamingMode(payload.size)
        connection.connectTimeout = 5000
        connection.readTimeout = 12000
        val start = System.nanoTime()
        var totalWritten = 0
        connection.outputStream.use { out: OutputStream ->
            var windowBytes = 0L
            var windowStart = start
            while (totalWritten < maxBytes && System.nanoTime() - start < targetDurationNanos) {
                out.write(payload)
                totalWritten += payload.size
                windowBytes += payload.size
                val now = System.nanoTime()
                if (now - windowStart >= 250_000_000L) {
                    val elapsedFraction = ((now - start).toFloat() / targetDurationNanos).coerceIn(0f, 1f)
                    val bytesFraction = (totalWritten.toFloat() / maxBytes).coerceIn(0f, 1f)
                    onProgress(mbps(windowBytes, now - windowStart), maxOf(elapsedFraction, bytesFraction * 0.85f))
                    windowBytes = 0L
                    windowStart = now
                }
            }
        }
        connection.responseCode
        connection.disconnect()
        val total = mbps(totalWritten.toLong(), System.nanoTime() - start)
        onProgress(total, 1f)
        total
    }.getOrDefault(0.0)
}

private suspend fun measureParallelUploadMbps(
    endpoint: SpeedEndpoint,
    onProgress: (speedMbps: Double, doneFraction: Float) -> Unit
): Double = withContext(Dispatchers.IO) {
    val uploadUrl = endpoint.uploadUrl ?: return@withContext 0.0
    runCatching {
        val streamCount = 3
        val targetDurationNanos = 8_500_000_000L
        val maxBytes = 96_000_000L
        val payload = ByteArray(128 * 1024)
        val totals = AtomicLongArray(streamCount)
        val start = System.nanoTime()

        coroutineScope {
            val workers = (0 until streamCount).map { streamIndex ->
                async(Dispatchers.IO) {
                    runCatching {
                        val connection = URL(urlWithCacheBuster(uploadUrl, streamIndex)).openConnection() as HttpURLConnection
                        try {
                            connection.requestMethod = "POST"
                            connection.doOutput = true
                            connection.setChunkedStreamingMode(payload.size)
                            connection.connectTimeout = 5000
                            connection.readTimeout = 15000
                            connection.setRequestProperty("Cache-Control", "no-cache")
                            connection.setRequestProperty("Content-Type", "application/octet-stream")
                            val perStreamLimit = maxBytes / streamCount
                            connection.outputStream.use { out: OutputStream ->
                                while (System.nanoTime() - start < targetDurationNanos &&
                                    totals.get(streamIndex) < perStreamLimit
                                ) {
                                    out.write(payload)
                                    totals.addAndGet(streamIndex, payload.size.toLong())
                                }
                                out.flush()
                            }
                            runCatching { connection.responseCode }
                        } finally {
                            runCatching { connection.disconnect() }
                        }
                    }
                }
            }

            var lastBytes = 0L
            var lastAt = start
            while (System.nanoTime() - start < targetDurationNanos) {
                delay(250)
                val now = System.nanoTime()
                val totalBytes = totals.sumValues()
                val elapsedFraction = ((now - start).toFloat() / targetDurationNanos).coerceIn(0f, 1f)
                val bytesFraction = (totalBytes.toFloat() / maxBytes.toFloat()).coerceIn(0f, 1f)
                onProgress(
                    mbps(totalBytes - lastBytes, now - lastAt),
                    maxOf(elapsedFraction, bytesFraction * 0.9f)
                )
                lastBytes = totalBytes
                lastAt = now
                if (workers.all { it.isCompleted }) break
            }
            workers.awaitAll()
        }

        val total = mbps(totals.sumValues(), System.nanoTime() - start)
        onProgress(total, 1f)
        total.takeIf { it > 0.0 } ?: measureUploadMbps(endpoint, onProgress)
    }.getOrDefault(0.0)
}

private fun urlWithCacheBuster(rawUrl: String, streamIndex: Int): String {
    val separator = if (rawUrl.contains("?")) "&" else "?"
    return "$rawUrl${separator}ng_stream=$streamIndex&ng_t=${System.nanoTime()}"
}

private fun AtomicLongArray.sumValues(): Long {
    var total = 0L
    for (index in 0 until length()) {
        total += get(index)
    }
    return total
}

private fun mbps(bytes: Long, nanos: Long): Double {
    if (bytes <= 0 || nanos <= 0) return 0.0
    return (bytes * 8.0) / (nanos / 1_000_000_000.0) / 1_000_000.0
}

private fun smoothSpeed(previous: Float, next: Double): Float {
    val sanitized = next.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
    return (previous * 0.62f + sanitized.toFloat() * 0.38f).coerceAtLeast(0f)
}

private fun Double.formatOne(): String = String.format(java.util.Locale.US, "%.1f", this).replace('.', ',')

