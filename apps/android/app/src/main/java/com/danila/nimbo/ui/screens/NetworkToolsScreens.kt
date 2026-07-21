package com.danila.nimbo.ui.screens

import android.app.Application
import com.danila.nimbo.BuildConfig
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    val attempts: List<Int>
) {
    val success = attempts.filter { it >= 0 }
    val successCount = success.size
    val lossPercent = if (attempts.isEmpty()) 100 else (((attempts.size - successCount) * 100f) / attempts.size).roundToInt()
    val avgMs = success.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
    val minMs = success.minOrNull()
    val maxMs = success.maxOrNull()
}

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
fun PingToolScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val preferencesManager = remember { PreferencesManager(application) }
    val nebulaColors = LocalNebulaColors.current
    val scope = rememberCoroutineScope()

    var targetText by remember { mutableStateOf("ya.ru") }
    var isRunning by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<PingToolResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun startPing() {
        if (isRunning) return
        val parsed = parsePingTarget(targetText)
        if (parsed == null) {
            error = "Введите домен или IP-адрес"
            return
        }
        isRunning = true
        error = null
        scope.launch {
            val config = PingConfig(
                protocol = preferencesManager.pingProtocol.toPingProtocol(),
                testUrl = "https://${parsed.host}/",
                timeoutMs = preferencesManager.pingTimeout.coerceIn(1, 10) * 1000,
                useProxy = preferencesManager.pingThroughProxy,
                proxyPort = LocalProxyConfig.PORT
            )
            val attempts = mutableListOf<Int>()
            repeat(4) { index ->
                attempts += PingManager.ping(parsed.host, parsed.port, config)
                if (index < 3) delay(250)
            }
            result = PingToolResult(parsed.host, attempts)
            isRunning = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            GlassHeader(
                title = "Пинг",
                icon = Icons.Default.Language,
                iconColor = nebulaColors.accent,
                onBack = onNavigateBack
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                ToolHero(icon = Icons.Default.Language, title = "Проверка пинга", subtitle = "Введите домен или IP-адрес")
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        NebulaInputField(
                            value = targetText,
                            onValueChange = { targetText = it },
                            label = "Домен или IP-адрес",
                            leadingIcon = { Icon(Icons.Default.Dns, null) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        error?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, color = Color(0xFFE75555), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Button(
                    onClick = ::startPing,
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = nebulaColors.accent.copy(alpha = 0.82f))
                ) {
                    if (isRunning) {
                        ExpressiveCircularLoader(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = nebulaColors.textPrimary)
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(if (isRunning) "Пингуем..." else "Запустить пинг", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                }
                PingResultCard(result)
            }
        }
    }
}

@Composable
fun SpeedTestScreen(onNavigateBack: () -> Unit) {
    val nebulaColors = LocalNebulaColors.current
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

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            GlassHeader(
                title = "Тест скорости",
                icon = Icons.Default.Speed,
                iconColor = nebulaColors.accent,
                onBack = onNavigateBack
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SpeedGaugeCard(
                    value = if (isRunning) {
                        liveSpeed.toDouble()
                    } else {
                        result?.downloadMbps ?: downloadSpeed ?: result?.uploadMbps ?: uploadSpeed ?: 0.0
                    },
                    isRunning = isRunning,
                    progress = progress
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SpeedMetricCard("PING", (pingMs ?: result?.pingMs)?.takeIf { it >= 0 }?.toString() ?: "-", "мс", Modifier.weight(1f))
                    SpeedMetricCard(
                        "СКАЧАТЬ",
                        if (animatedDownloadMetric > 0f) animatedDownloadMetric.toDouble().formatOne() else "-",
                        "Мбит/с",
                        Modifier.weight(1f)
                    )
                    SpeedMetricCard(
                        "ОТДАЧА",
                        if (animatedUploadMetric > 0f) animatedUploadMetric.toDouble().formatOne() else "-",
                        "Мбит/с",
                        Modifier.weight(1f)
                    )
                }
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Сервер", color = nebulaColors.textSecondary)
                            Text(
                                result?.server?.providerLabel ?: "Авто",
                                color = nebulaColors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            result?.server?.locationLabel ?: statusText,
                            color = nebulaColors.textPrimary,
                            textAlign = TextAlign.Start
                        )
                        result?.server?.detailsLabel?.let { details ->
                            if (details.isBlank()) return@let
                            Spacer(Modifier.height(4.dp))
                            Text(
                                details,
                                color = nebulaColors.textSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                Button(
                    onClick = ::startSpeedTest,
                    enabled = !isRunning,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = nebulaColors.accent.copy(alpha = 0.82f))
                ) {
                    Text(if (isRunning) "Идёт тест..." else "Начать тест", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ToolHero(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String) {
    val nebulaColors = LocalNebulaColors.current
    GlassCard(modifier = Modifier.fillMaxWidth().height(178.dp)) {
        Box(modifier = Modifier.fillMaxSize().background(nebulaColors.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(icon, null, tint = nebulaColors.textPrimary, modifier = Modifier.size(52.dp))
                Spacer(Modifier.height(14.dp))
                Text(title, color = nebulaColors.textPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(subtitle, color = nebulaColors.textSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun PingResultCard(result: PingToolResult?) {
    val nebulaColors = LocalNebulaColors.current
    GlassCard(modifier = Modifier.fillMaxWidth().height(176.dp)) {
        Box(modifier = Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
            if (result == null) {
                Text("Введите адрес сервера и нажмите кнопку проверки.", color = nebulaColors.textSecondary, textAlign = TextAlign.Center)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(result.host, color = nebulaColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    val avgColor = when {
                        result.avgMs == null || result.successCount == 0 -> Color(0xFFE75555)
                        result.avgMs <= 70 -> nebulaColors.statusConnected
                        result.avgMs <= 120 -> Color(0xFFCDDC39)
                        result.avgMs <= 220 -> Color(0xFFFF9800)
                        else -> Color(0xFFE75555)
                    }
                    Text(result.avgMs?.let { "$it мс" } ?: "Недоступен", color = avgColor, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Успешно: ${result.successCount}/${result.attempts.size}  Потери: ${result.lossPercent}%\nМин: ${result.minMs ?: "-"} мс  Макс: ${result.maxMs ?: "-"} мс",
                        color = nebulaColors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedGaugeCard(value: Double, isRunning: Boolean, progress: Float) {
    val nebulaColors = LocalNebulaColors.current
    val animatedValue by animateFloatAsState(
        targetValue = value.toFloat(),
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow),
        label = "speedValue"
    )
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "speedProgress"
    )
    GlassCard(modifier = Modifier.fillMaxWidth().height(280.dp)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(210.dp)) {
                val stroke = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                val arcSize = Size(size.width, size.height)
                val gaugeFraction = (animatedValue / 200f).coerceIn(0f, 1f)
                val sweep = 250f * if (isRunning) maxOf(gaugeFraction, animatedProgress * 0.2f) else gaugeFraction
                drawArc(nebulaColors.textPrimary.copy(alpha = 0.16f), 145f, 250f, false, style = stroke, size = arcSize)
                drawArc(nebulaColors.textPrimary.copy(alpha = 0.07f), 145f, 250f, false, style = Stroke(width = 28.dp.toPx(), cap = StrokeCap.Round), size = arcSize)
                drawArc(nebulaColors.accent, 145f, sweep, false, style = stroke, size = arcSize)

                val center = Offset(size.width / 2f, size.height / 2f)
                val angleDeg = 145f + 250f * gaugeFraction
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val needleLength = size.width * 0.39f
                val needleEnd = Offset(
                    x = center.x + cos(angleRad).toFloat() * needleLength,
                    y = center.y + sin(angleRad).toFloat() * needleLength
                )
                val dir = Offset(cos(angleRad).toFloat(), sin(angleRad).toFloat())
                val perp = Offset(-dir.y, dir.x)
                val tail = center - dir * 24.dp.toPx()
                val tip = needleEnd
                val left = center + perp * 9.dp.toPx()
                val right = center - perp * 9.dp.toPx()
                val shadowOffset = Offset(2.5.dp.toPx(), 3.dp.toPx())
                val needleShadow = Path().apply {
                    moveTo(tip.x + shadowOffset.x, tip.y + shadowOffset.y)
                    lineTo(left.x + shadowOffset.x, left.y + shadowOffset.y)
                    lineTo(tail.x + shadowOffset.x, tail.y + shadowOffset.y)
                    lineTo(right.x + shadowOffset.x, right.y + shadowOffset.y)
                    close()
                }
                val needle = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(left.x, left.y)
                    lineTo(tail.x, tail.y)
                    lineTo(right.x, right.y)
                    close()
                }
                drawPath(needleShadow, Color.Black.copy(alpha = 0.28f))
                drawPath(needle, nebulaColors.accent.copy(alpha = 0.96f))
                drawLine(
                    color = nebulaColors.textPrimary.copy(alpha = 0.72f),
                    start = center + perp * 3.dp.toPx(),
                    end = tip - dir * 13.dp.toPx(),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(Color.Black.copy(alpha = 0.26f), radius = 15.dp.toPx(), center = center + shadowOffset)
                drawCircle(nebulaColors.textPrimary.copy(alpha = 0.15f), radius = 16.dp.toPx(), center = center)
                drawCircle(nebulaColors.textPrimary.copy(alpha = 0.88f), radius = 10.dp.toPx(), center = center)
                drawCircle(nebulaColors.accent.copy(alpha = 0.9f), radius = 4.dp.toPx(), center = center)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 116.dp)) {
                Text(if (animatedValue > 0f) animatedValue.toDouble().formatOne() else "-", color = nebulaColors.textPrimary, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Мбит/с", color = nebulaColors.textSecondary)
            }
        }
    }
}

@Composable
private fun SpeedMetricCard(title: String, value: String, unit: String, modifier: Modifier) {
    val nebulaColors = LocalNebulaColors.current
    GlassCard(modifier = modifier.height(116.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(title, color = nebulaColors.textSecondary, style = MaterialTheme.typography.labelMedium)
            Text(value, color = nebulaColors.textPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(unit, color = nebulaColors.textTertiary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun parsePingTarget(raw: String): ParsedPingTarget? {
    val cleaned = raw.trim().removePrefix("https://").removePrefix("http://").substringBefore("/")
    if (cleaned.isBlank()) return null
    val host = cleaned.substringBefore(":").trim()
    val port = cleaned.substringAfter(":", "443").toIntOrNull()?.coerceIn(1, 65535) ?: 443
    return host.takeIf { it.isNotBlank() }?.let { ParsedPingTarget(it, port) }
}

private fun Int.toPingProtocol(): PingProtocol = when (this) {
    1 -> PingProtocol.HTTP_GET
    2 -> PingProtocol.HTTP_HEAD
    3 -> PingProtocol.HTTPS_STRICT
    4 -> PingProtocol.ICMP
    else -> PingProtocol.TCP
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

