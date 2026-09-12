package com.danila.nimbo.ui.screens

import android.app.Application
import com.danila.nimbo.BuildConfig
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.components.ExpressiveCircularLoader
import com.danila.nimbo.ui.i18n.t
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
    TELEMETRY("Сервисы статистики"),
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
    val requiredBodyMarkers: List<String> = emptyList(),
    val acceptClientErrors: Boolean = false
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
    onNavigateToPingTool: () -> Unit
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
            history = (listOf(newResult) + history).take(20)
            saveConnectivityHistory(preferencesManager, history)
            showDetails = true
            isRunning = false
        }
    }

    NimboSubPageScaffold(
        title = t("Проверка БС", "Allowlist check"),
        subtitle = t(
            "Доступность контрольных доменов в текущей сети",
            "Reachability of reference domains on the current network"
        ),
        onBack = onNavigateBack
    ) {
        DiagnosticsHero()
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ToolNavButton(
                title = t("История", "History"),
                icon = Icons.Default.History,
                onClick = onNavigateToHistory,
                modifier = Modifier.weight(1f)
            )
            ToolNavButton(
                title = t("Проверка пинга", "Ping check"),
                icon = Icons.Default.NetworkPing,
                onClick = onNavigateToPingTool,
                modifier = Modifier.weight(1f),
                accent = true
            )
        }

        Spacer(Modifier.height(14.dp))
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
                Text(t("Проверка…", "Checking…"), color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
            } else {
                Text(t("Начать проверку", "Start check"), color = nebulaColors.textPrimary, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(16.dp))
        result?.let { current ->
            ResultSummaryCard(current)
            Spacer(Modifier.height(12.dp))
            ToolNavButton(
                title = if (showDetails) t("Скрыть результаты", "Hide results") else t("Показать результаты", "Show results"),
                icon = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                onClick = { showDetails = !showDetails },
                modifier = Modifier.fillMaxWidth()
            )
            AnimatedVisibility(visible = showDetails) {
                Column {
                    Spacer(Modifier.height(16.dp))
                    ResultDetails(current)
                }
            }
        } ?: EmptyStateCard()

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ToolNavButton(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false
) {
    val colors = LocalNebulaColors.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .height(58.dp)
            .clip(shape)
            .background(if (accent) colors.accent.copy(alpha = 0.14f) else colors.controlFill)
            .border(
                1.dp,
                if (accent) colors.accent.copy(alpha = 0.48f) else colors.panelBorder,
                shape
            )
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (accent) colors.accent else colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            color = if (accent) colors.accent else colors.textPrimary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.ExtraBold,
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
    NimboSubPageScaffold(
        title = t("История проверок", "Check history"),
        subtitle = t("Последние результаты проверки БС", "Recent allowlist check results"),
        onBack = onNavigateBack
    ) {
        if (history.isEmpty()) {
            EmptyHistoryCard()
        } else {
            HistorySection(history = history)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun DiagnosticsHero() {
    val colors = LocalNebulaColors.current
    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(colors.accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.CellTower, null, tint = colors.accent, modifier = Modifier.size(27.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t("Контроль доступности", "Reachability check"),
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = t(
                        "Google, Яндекс, сервисы статистики и DNS",
                        "Google, Yandex, analytics services and DNS"
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyStateCard() {
    val nebulaColors = LocalNebulaColors.current
    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
        Text(
            text = t(
                "Запустите проверку, чтобы увидеть, какие группы сайтов доступны в текущей сети. Результат показывает возможные ограничения, но не гарантирует работу всех сервисов.",
                "Run the check to see which site groups are reachable on the current network. The result indicates possible restrictions but cannot guarantee every service."
            ),
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
    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
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
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CheckGroup.entries.forEach { group ->
            val groupChecks = result.checks.filter { it.target.group == group }
            if (groupChecks.isNotEmpty()) {
                Column {
                    SubPageSectionHeader(text = group.title, icon = group.icon())
                    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
                        Column {
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
    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
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
    Column {
        SubPageSectionHeader(text = t("История", "History"), icon = Icons.Default.History)
        WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
            Column {
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
                            item.checks
                                .filterNot { it.isAvailable }
                                .map { it.target.name }
                                .filterNot { it == "История" }
                                .takeIf { it.isNotEmpty() }
                                ?.let { unavailable ->
                                    Text(
                                        text = "Недоступны: ${unavailable.joinToString()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFE75555)
                                    )
                                }
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
    CheckGroup.TELEMETRY -> Icons.Default.Speed
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
            requiredBodyMarkers = listOf("colo=", "loc=")
        ),
        CheckTarget("GitHub", "github.com", group = CheckGroup.INTERNATIONAL, url = "https://github.com/"),
        CheckTarget("Telegram", "telegram.org", group = CheckGroup.INTERNATIONAL, url = "https://telegram.org/"),
        CheckTarget("Yandex", "ya.ru", group = CheckGroup.LOCAL, url = "https://ya.ru/"),
        CheckTarget("VK", "vk.com", group = CheckGroup.LOCAL, url = "https://vk.com/"),
        CheckTarget(
            "Google Analytics",
            "www.google-analytics.com",
            group = CheckGroup.TELEMETRY,
            url = "https://www.google-analytics.com/g/collect",
            acceptClientErrors = true
        ),
        CheckTarget(
            "Google Tag Manager",
            "www.googletagmanager.com",
            group = CheckGroup.TELEMETRY,
            url = "https://www.googletagmanager.com/gtm.js?id=GTM-NIMBO",
            acceptClientErrors = true
        ),
        CheckTarget(
            "Google Static",
            "www.gstatic.com",
            group = CheckGroup.TELEMETRY,
            url = "https://www.gstatic.com/generate_204"
        ),
        CheckTarget(
            "Яндекс Метрика",
            "mc.yandex.ru",
            group = CheckGroup.TELEMETRY,
            url = "https://mc.yandex.ru/watch/0",
            acceptClientErrors = true
        ),
        CheckTarget(
            "Яндекс Static",
            "yastatic.net",
            group = CheckGroup.TELEMETRY,
            url = "https://yastatic.net/"
        ),
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
    val availableRate = checks.count { it.isAvailable }.toFloat() / checks.size.coerceAtLeast(1)
    val verdict = when {
        !anyAvailable -> ConnectivityVerdict.NO_INTERNET
        internationalAvailable && localAvailable && availableRate >= 0.7f -> ConnectivityVerdict.NORMAL
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
            val acceptedCode = code in 200..399 || (target.acceptClientErrors && code in 400..499)
            val available = acceptedCode && (target.requiredBodyMarkers.isEmpty() || bodyLooksRight)
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
                val savedChecks = item.optJSONArray("checks")
                    ?.let(::parseHistoryChecks)
                    .orEmpty()
                add(
                    ConnectivityDiagnosticResult(
                        checkedAt = item.optLong("checkedAt", 0L),
                        verdict = verdict,
                        checks = savedChecks.ifEmpty {
                            buildHistoryChecks(checked, available, average)
                        }
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
    history.take(20).forEach { item ->
        array.put(
            JSONObject()
                .put("checkedAt", item.checkedAt)
                .put("verdict", item.verdict.name)
                .put("checked", item.checkedCount)
                .put("available", item.availableCount)
                .put("average", item.averagePingMs ?: -1L)
                .put(
                    "checks",
                    JSONArray().apply {
                        item.checks.forEach { check ->
                            put(
                                JSONObject()
                                    .put("name", check.target.name)
                                    .put("host", check.target.host)
                                    .put("port", check.target.port)
                                    .put("group", check.target.group.name)
                                    .put("probeType", check.target.probeType.name)
                                    .put("available", check.isAvailable)
                                    .put("ping", check.pingMs ?: -1L)
                                    .put("error", check.error ?: JSONObject.NULL)
                            )
                        }
                    }
                )
        )
    }
    preferencesManager.setString(CONNECTIVITY_HISTORY_KEY, array.toString())
}

private fun parseHistoryChecks(array: JSONArray): List<HostCheckResult> = buildList {
    for (index in 0 until array.length()) {
        val item = array.optJSONObject(index) ?: continue
        val group = runCatching { CheckGroup.valueOf(item.optString("group")) }
            .getOrDefault(CheckGroup.INFRA)
        val probeType = runCatching { ProbeType.valueOf(item.optString("probeType")) }
            .getOrDefault(ProbeType.HTTPS)
        val ping = item.optLong("ping", -1L).takeIf { it >= 0L }
        val error = item.optString("error")
            .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        add(
            HostCheckResult(
                target = CheckTarget(
                    name = item.optString("name", "Хост"),
                    host = item.optString("host", "-"),
                    port = item.optInt("port", 443).coerceIn(1, 65535),
                    group = group,
                    probeType = probeType
                ),
                isAvailable = item.optBoolean("available", false),
                latencyMs = ping,
                pingMs = ping,
                error = error
            )
        )
    }
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
