package com.danila.nimbo.ui.screens

import android.app.Application
import com.danila.nimbo.BuildConfig
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.components.AnimatedGradientBackground
import com.danila.nimbo.ui.components.ExpressiveCircularLoader
import com.danila.nimbo.ui.components.GlassCard
import com.danila.nimbo.ui.components.GlassHeader
import com.danila.nimbo.ui.components.GlassSection
import com.danila.nimbo.ui.theme.LocalNebulaColors
import com.danila.nimbo.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private const val CONNECTIVITY_HISTORY_KEY = "connectivity_diagnostics_history"

private enum class CheckGroup(val title: String) {
    INTERNATIONAL("Международные сервисы"),
    LOCAL("Локальные сервисы"),
    INFRA("DNS и инфраструктура")
}

private enum class ProbeType {
    HTTPS,
    TCP
}

private data class CheckTarget(
    val name: String,
    val host: String,
    val port: Int = 443,
    val group: CheckGroup,
    val probeType: ProbeType = ProbeType.HTTPS,
    val url: String = "https://$host/",
    val requiredBodyMarkers: List<String> = emptyList()
)

private data class HostCheckResult(
    val target: CheckTarget,
    val isAvailable: Boolean,
    val latencyMs: Long?,
    val pingMs: Long?,
    val error: String?
)

private enum class ConnectivityVerdict {
    NORMAL,
    RESTRICTED,
    NO_INTERNET
}

private data class ConnectivityDiagnosticResult(
    val checkedAt: Long,
    val verdict: ConnectivityVerdict,
    val checks: List<HostCheckResult>
) {
    val checkedCount: Int = checks.size
    val availableCount: Int = checks.count { it.isAvailable }
    val successRate: Int = if (checkedCount == 0) 0 else ((availableCount * 100f) / checkedCount).roundToInt()
    val averagePingMs: Long? = checks.mapNotNull { it.pingMs }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()?.toLong()

    val title: String = when (verdict) {
        ConnectivityVerdict.NORMAL -> "Интернет работает нормально"
        ConnectivityVerdict.RESTRICTED -> "Возможны ограничения связи"
        ConnectivityVerdict.NO_INTERNET -> "Интернет недоступен"
    }

    val description: String = when (verdict) {
        ConnectivityVerdict.NORMAL -> "Признаков блокировок не обнаружено."
        ConnectivityVerdict.RESTRICTED -> "Часть контрольных хостов недоступна. Проверьте подключение без VPN или резервный режим."
        ConnectivityVerdict.NO_INTERNET -> "Все контрольные хосты недоступны. Проверьте мобильную сеть, Wi-Fi или VPN."
    }
}

@Composable
fun ConnectivityDiagnosticsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToPingTool: () -> Unit,
    onNavigateToSpeedTest: () -> Unit,
    onNavigateToTrafficMonitor: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val preferencesManager = remember { PreferencesManager(application) }
    val nebulaColors = LocalNebulaColors.current
    val scope = rememberCoroutineScope()

    var isRunning by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<ConnectivityDiagnosticResult?>(null) }
    var history by remember { mutableStateOf(loadConnectivityHistory(preferencesManager)) }

    fun startCheck() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            val newResult = runConnectivityDiagnostics()
            result = newResult
            history = (listOf(newResult) + history).take(8)
            saveConnectivityHistory(preferencesManager, history)
            showDetails = true
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
                title = "Проверка БС",
                icon = Icons.Default.CellTower,
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
                DiagnosticsHero()

                Button(
                    onClick = ::startCheck,
                    enabled = !isRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = nebulaColors.accent.copy(alpha = 0.82f),
                        disabledContainerColor = nebulaColors.textPrimary.copy(alpha = 0.10f)
                    )
                ) {
                    if (isRunning) {
                        ExpressiveCircularLoader(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = nebulaColors.textPrimary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Проверка...", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Начать проверку", color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
                    }
                }

                TextButton(
                    onClick = onNavigateToHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .border(1.dp, nebulaColors.textTertiary.copy(alpha = 0.40f), RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = nebulaColors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "История",
                        color = nebulaColors.accent,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolNavButton(
                        title = "Пинг домена/IP",
                        icon = Icons.Default.NetworkPing,
                        onClick = onNavigateToPingTool,
                        modifier = Modifier.weight(1f)
                    )
                    ToolNavButton(
                        title = "Тест скорости",
                        icon = Icons.Default.Speed,
                        onClick = onNavigateToSpeedTest,
                        modifier = Modifier.weight(1f)
                    )
                }
                ToolNavButton(
                    title = "Трафик подключений",
                    icon = Icons.Default.Public,
                    onClick = onNavigateToTrafficMonitor,
                    modifier = Modifier.fillMaxWidth()
                )

                result?.let { current ->
                    ResultSummaryCard(current)
                    TextButton(
                        onClick = { showDetails = !showDetails },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .border(1.dp, nebulaColors.textTertiary.copy(alpha = 0.40f), RoundedCornerShape(14.dp)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = if (showDetails) "Скрыть элементы" else "Показать элементы",
                            color = nebulaColors.accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    AnimatedVisibility(visible = showDetails) {
                        ResultDetails(current)
                    }
                } ?: EmptyStateCard()

            }
        }
    }
}

@Composable
private fun ToolNavButton(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nebulaColors = LocalNebulaColors.current
    TextButton(
        onClick = onClick,
        modifier = modifier
            .height(58.dp)
            .border(1.dp, nebulaColors.accent.copy(alpha = 0.32f), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = nebulaColors.accent,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = nebulaColors.accent,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ConnectivityDiagnosticsHistoryScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val preferencesManager = remember { PreferencesManager(application) }
    val history = remember { loadConnectivityHistory(preferencesManager) }
    val nebulaColors = LocalNebulaColors.current

    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedGradientBackground()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            GlassHeader(
                title = "История БС",
                icon = Icons.Default.History,
                iconColor = nebulaColors.accent,
                onBack = onNavigateBack
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 112.dp)
            ) {
                if (history.isEmpty()) {
                    EmptyHistoryCard()
                } else {
                    HistorySection(history = history)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticsHero() {
    val nebulaColors = LocalNebulaColors.current
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(178.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            nebulaColors.accent.copy(alpha = 0.28f),
                            nebulaColors.accent.copy(alpha = 0.10f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CellTower,
                    contentDescription = null,
                    tint = nebulaColors.textPrimary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Проверить соединение",
                    style = MaterialTheme.typography.headlineSmall,
                    color = nebulaColors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Анализ доступности ключевых хостов",
                    style = MaterialTheme.typography.bodyMedium,
                    color = nebulaColors.textSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard() {
    val nebulaColors = LocalNebulaColors.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Нажмите кнопку, чтобы проверить, есть ли ограничения связи. Приложение оценит доступность международных, локальных и DNS-хостов.",
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 28.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = nebulaColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ResultSummaryCard(result: ConnectivityDiagnosticResult) {
    val nebulaColors = LocalNebulaColors.current
    val color = result.verdict.color()
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = result.verdict.icon(),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(34.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleLarge,
                color = color,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = result.description,
                style = MaterialTheme.typography.bodyMedium,
                color = nebulaColors.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Проверено хостов: ${result.checkedCount}\nДоступно хостов: ${result.availableCount}\nУспешность: ${result.successRate}%\nСредний пинг: ${result.averagePingMs?.let { "$it мс" } ?: "-"}",
                style = MaterialTheme.typography.bodyMedium,
                color = nebulaColors.textPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ResultDetails(result: ConnectivityDiagnosticResult) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CheckGroup.entries.forEach { group ->
            val groupChecks = result.checks.filter { it.target.group == group }
            if (groupChecks.isNotEmpty()) {
                GlassSection(title = group.title, icon = group.icon()) {
                    groupChecks.forEachIndexed { index, check ->
                        HostResultRow(check)
                        if (index != groupChecks.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = LocalNebulaColors.current.textTertiary.copy(alpha = 0.1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HostResultRow(check: HostCheckResult) {
    val nebulaColors = LocalNebulaColors.current
    val statusColor = if (check.isAvailable) Color(0xFF33C75A) else Color(0xFFE75555)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (check.isAvailable) Icons.Default.CheckCircle else Icons.Default.Cancel,
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = check.target.name,
                style = MaterialTheme.typography.titleMedium,
                color = nebulaColors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (check.isAvailable) {
                    val probe = if (check.target.probeType == ProbeType.HTTPS) "HTTPS" else "TCP ${check.target.port}"
                    "пинг ${check.pingMs?.let { "$it мс" } ?: "-"} · доступен через $probe"
                } else {
                    "пинг ${check.pingMs?.let { "$it мс" } ?: "-"} · недоступен: ${check.error ?: "нет ответа"}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = statusColor
            )
            Text(
                text = check.target.host,
                style = MaterialTheme.typography.bodySmall,
                color = nebulaColors.textTertiary
            )
        }
    }
}

@Composable
private fun EmptyHistoryCard() {
    val nebulaColors = LocalNebulaColors.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "История пока пустая. Запустите проверку, и результат появится здесь.",
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 28.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = nebulaColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HistorySection(history: List<ConnectivityDiagnosticResult>) {
    val nebulaColors = LocalNebulaColors.current
    GlassSection(title = "История", icon = Icons.Default.History) {
        history.forEachIndexed { index, item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = item.verdict.icon(),
                    contentDescription = null,
                    tint = item.verdict.color(),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatHistoryTime(item.checkedAt),
                        style = MaterialTheme.typography.labelLarge,
                        color = nebulaColors.textSecondary
                    )
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = nebulaColors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Доступно ${item.availableCount}/${item.checkedCount}, пинг ${item.averagePingMs?.let { "$it мс" } ?: "-"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = nebulaColors.textTertiary
                    )
                }
            }
            if (index != history.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = nebulaColors.textTertiary.copy(alpha = 0.1f)
                )
            }
        }
    }
}

private fun ConnectivityVerdict.icon(): ImageVector = when (this) {
    ConnectivityVerdict.NORMAL -> Icons.Default.CheckCircle
    ConnectivityVerdict.RESTRICTED -> Icons.Default.Warning
    ConnectivityVerdict.NO_INTERNET -> Icons.Default.Cancel
}

@Composable
private fun ConnectivityVerdict.color(): Color = when (this) {
    ConnectivityVerdict.NORMAL -> Color(0xFF33C75A)
    ConnectivityVerdict.RESTRICTED -> Color(0xFFF5A524)
    ConnectivityVerdict.NO_INTERNET -> Color(0xFFE75555)
}

private fun CheckGroup.icon(): ImageVector = when (this) {
    CheckGroup.INTERNATIONAL -> Icons.Default.Public
    CheckGroup.LOCAL -> Icons.Default.CellTower
    CheckGroup.INFRA -> Icons.Default.Dns
}

private suspend fun runConnectivityDiagnostics(): ConnectivityDiagnosticResult = withContext(Dispatchers.IO) {
    val targets = listOf(
        CheckTarget("Google", "www.google.com", group = CheckGroup.INTERNATIONAL, url = "https://www.google.com/generate_204"),
        CheckTarget(
            "Cloudflare",
            "speed.cloudflare.com",
            group = CheckGroup.INTERNATIONAL,
            url = "https://speed.cloudflare.com/cdn-cgi/trace",
            requiredBodyMarkers = listOf("colo=", "loc=", "http=http/2")
        ),
        CheckTarget("GitHub", "github.com", group = CheckGroup.INTERNATIONAL, url = "https://github.com/"),
        CheckTarget("Telegram", "telegram.org", group = CheckGroup.INTERNATIONAL, url = "https://telegram.org/"),
        CheckTarget("Yandex", "ya.ru", group = CheckGroup.LOCAL, url = "https://ya.ru/"),
        CheckTarget("VK", "vk.com", group = CheckGroup.LOCAL, url = "https://vk.com/"),
        CheckTarget("DNS Google", "8.8.8.8", port = 53, group = CheckGroup.INFRA, probeType = ProbeType.TCP),
        CheckTarget("DNS Cloudflare", "1.1.1.1", port = 53, group = CheckGroup.INFRA, probeType = ProbeType.TCP)
    )

    val checks = coroutineScope {
        targets.map { target ->
            async { checkTarget(target) }
        }.awaitAll()
    }

    val internationalAvailable = checks.any { it.target.group == CheckGroup.INTERNATIONAL && it.isAvailable }
    val localAvailable = checks.any { it.target.group == CheckGroup.LOCAL && it.isAvailable }
    val anyAvailable = checks.any { it.isAvailable }
    val verdict = when {
        !anyAvailable -> ConnectivityVerdict.NO_INTERNET
        internationalAvailable && localAvailable && checks.count { it.isAvailable } >= 6 -> ConnectivityVerdict.NORMAL
        else -> ConnectivityVerdict.RESTRICTED
    }

    ConnectivityDiagnosticResult(
        checkedAt = System.currentTimeMillis(),
        verdict = verdict,
        checks = checks
    )
}

private fun checkTarget(target: CheckTarget): HostCheckResult {
    return when (target.probeType) {
        ProbeType.HTTPS -> checkHttpsTarget(target)
        ProbeType.TCP -> checkTcpTarget(target)
    }
}

private fun checkHttpsTarget(target: CheckTarget): HostCheckResult {
    val start = System.nanoTime()
    return try {
        val connection = (URL(target.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 3200
            readTimeout = 3200
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "Nimbo/${BuildConfig.VERSION_NAME}/Android")
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val code = connection.responseCode
            val elapsedMs = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
            val body = if (target.requiredBodyMarkers.isNotEmpty() && code in 200..399) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                ""
            }
            val bodyLooksRight = target.requiredBodyMarkers.all { marker -> body.contains(marker) }
            val available = code in 200..399 && (target.requiredBodyMarkers.isEmpty() || bodyLooksRight)
            runCatching { connection.errorStream?.close() }
            if (available) {
                HostCheckResult(target, isAvailable = true, latencyMs = elapsedMs, pingMs = elapsedMs, error = null)
            } else {
                val reason = if (code in 200..399 && target.requiredBodyMarkers.isNotEmpty()) {
                    "не тот ответ"
                } else {
                    "HTTP $code"
                }
                HostCheckResult(target, isAvailable = false, latencyMs = null, pingMs = null, error = reason)
            }
        } finally {
            connection.disconnect()
        }
    } catch (e: Exception) {
        HostCheckResult(target, isAvailable = false, latencyMs = null, pingMs = null, error = e.javaClass.simpleName)
    }
}

private fun checkTcpTarget(target: CheckTarget): HostCheckResult {
    val pingMs = pingTcpOnce(target.host, target.port)
    return if (pingMs != null) {
        HostCheckResult(target, isAvailable = true, latencyMs = pingMs, pingMs = pingMs, error = null)
    } else {
        HostCheckResult(target, isAvailable = false, latencyMs = null, pingMs = null, error = "нет ответа")
    }
}

private fun pingTcpOnce(host: String, port: Int): Long? {
    val start = System.nanoTime()
    return runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 2600)
        }
        ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
    }.getOrNull()
}

private fun loadConnectivityHistory(preferencesManager: PreferencesManager): List<ConnectivityDiagnosticResult> {
    val raw = preferencesManager.getString(CONNECTIVITY_HISTORY_KEY, null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val verdict = runCatching { ConnectivityVerdict.valueOf(item.getString("verdict")) }
                    .getOrDefault(ConnectivityVerdict.RESTRICTED)
                val checked = item.optInt("checked", 0)
                val available = item.optInt("available", 0)
                val average = item.optLong("average", -1L).takeIf { it >= 0L }
                add(
                    ConnectivityDiagnosticResult(
                        checkedAt = item.optLong("checkedAt", 0L),
                        verdict = verdict,
                        checks = buildHistoryChecks(checked, available, average)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())
        .distinctBy { "${it.checkedAt}:${it.verdict}:${it.checkedCount}:${it.availableCount}" }
}

private fun saveConnectivityHistory(
    preferencesManager: PreferencesManager,
    history: List<ConnectivityDiagnosticResult>
) {
    val array = JSONArray()
    history.take(8).forEach { item ->
        array.put(
            JSONObject()
                .put("checkedAt", item.checkedAt)
                .put("verdict", item.verdict.name)
                .put("checked", item.checkedCount)
                .put("available", item.availableCount)
                .put("average", item.averagePingMs ?: -1L)
        )
    }
    preferencesManager.setString(CONNECTIVITY_HISTORY_KEY, array.toString())
}

private fun buildHistoryChecks(
    checked: Int,
    available: Int,
    average: Long?
): List<HostCheckResult> {
    return List(checked.coerceAtLeast(0)) { index ->
        val isAvailable = index < available
        HostCheckResult(
            target = CheckTarget("История", "history.local", group = CheckGroup.INFRA),
            isAvailable = isAvailable,
            latencyMs = if (isAvailable) average else null,
            pingMs = if (isAvailable) average else null,
            error = null
        )
    }
}

private fun formatHistoryTime(timestamp: Long): String {
    if (timestamp <= 0L) return "-"
    return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
}
