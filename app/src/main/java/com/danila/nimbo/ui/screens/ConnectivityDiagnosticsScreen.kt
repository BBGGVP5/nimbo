@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.danila.nimbo.ui.screens

import com.danila.nimbo.ui.components.*
import androidx.compose.foundation.layout.*

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import java.util.concurrent.atomic.AtomicInteger
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
/** Shown before the first progress callback; runConnectivityDiagnostics reports the real count. */
private const val CONNECTIVITY_TARGET_COUNT = 13

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
        ConnectivityVerdict.NORMAL -> "Интернет работает"
        ConnectivityVerdict.RESTRICTED -> "Есть ограничения"
        ConnectivityVerdict.NO_INTERNET -> "Нет интернета"
    }

    val description: String = when (verdict) {
        ConnectivityVerdict.NORMAL -> "Признаков ограничений не обнаружено."
        ConnectivityVerdict.RESTRICTED -> "Часть контрольных хостов недоступна. Проверьте подключение без VPN или резервный режим."
        ConnectivityVerdict.NO_INTERNET -> "Все контрольные хосты недоступны. Проверьте мобильную сеть, Wi-Fi или VPN."
    }
}

@Composable
fun ConnectivityDiagnosticsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToPingTool: () -> Unit,
    onSelectTool: (DiagnosticsTool) -> Unit = { if (it == DiagnosticsTool.PING) onNavigateToPingTool() }
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val preferencesManager = remember { PreferencesManager(application) }
    val nebulaColors = LocalNebulaColors.current
    val scope = rememberCoroutineScope()

    var isRunning by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(true) }
    var completed by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(CONNECTIVITY_TARGET_COUNT) }
    var history by remember { mutableStateOf(loadConnectivityHistory(preferencesManager)) }
    // The last stored check stays on screen, so the card is never empty after a relaunch.
    var result by remember { mutableStateOf(history.firstOrNull()) }

    fun startCheck() {
        if (isRunning) return
        isRunning = true
        completed = 0
        scope.launch {
            val newResult = runConnectivityDiagnostics { done, count -> completed = done; total = count }
            result = newResult
            history = (listOf(newResult) + history).take(20)
            saveConnectivityHistory(preferencesManager, history)
            showDetails = true
            isRunning = false
        }
    }

    NimboSubPageScaffold(
        title = t("Проверка БС", "Allowlist check"),
        subtitle = null,
        onBack = onNavigateBack
    ) {
        DiagnosticsToolTabs(DiagnosticsTool.CHECK, onSelectTool)
        Spacer(Modifier.height(16.dp))
        StatusCard(result = result, running = isRunning, completed = completed, total = total)
        Spacer(Modifier.height(16.dp))
        DiagnosticsPrimaryButton(
            text = if (isRunning) t("Проверяем… $completed/$total", "Checking… $completed/$total")
                else t("Начать проверку", "Start check"),
            icon = Icons.Default.PlayArrow,
            onClick = ::startCheck,
            busy = isRunning
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DiagnosticsSecondaryButton(t("История", "History"), Icons.Default.History, onNavigateToHistory,
                Modifier.weight(1f), tonal = true)
            DiagnosticsSecondaryButton(t("Поделиться", "Share"), Icons.Default.Share,
                { result?.let { shareConnectivityResult(context, it) } }, Modifier.weight(1f),
                enabled = result != null && !isRunning)
        }
        result?.let { current ->
            Spacer(Modifier.height(22.dp))
            DiagnosticsSectionHeader(
                t("Результаты по узлам", "Results by node"),
                if (showDetails) t("Скрыть", "Hide") else t("Показать все", "Show all")
            ) { showDetails = !showDetails }
            AnimatedVisibility(visible = showDetails) {
                Column {
                    Spacer(Modifier.height(6.dp))
                    ResultDetails(current)
                }
            }
        } ?: run {
            Spacer(Modifier.height(18.dp))
            Text(
                t("Проверка обращается к контрольным сайтам и DNS в текущей сети и показывает, похожа ли она на режим белых списков. Это не проверка всех сервисов.",
                  "The check contacts reference sites and DNS on the current network and shows whether it looks like an allowlist mode. It does not test every service."),
                color = nebulaColors.textTertiary, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatusCard(result: ConnectivityDiagnosticResult?, running: Boolean, completed: Int, total: Int) {
    val nebulaColors = LocalNebulaColors.current
    val tint = when {
        running -> nebulaColors.accent
        result == null -> nebulaColors.textTertiary
        else -> result.verdict.color()
    }
    DiagnosticsCard {
        Row(verticalAlignment = Alignment.Top) {
            DiagnosticsEyebrow(t("Статус сети", "Network status"), Modifier.weight(1f).padding(top = 4.dp))
            DiagnosticsBadge(
                icon = when {
                    running -> Icons.Default.Sync
                    result == null -> Icons.Default.Wifi
                    else -> result.verdict.icon()
                },
                tint = tint
            )
        }
        Spacer(Modifier.height(8.dp))
        when {
            running -> DiagnosticsHeroTitle(t("Проверяем сеть", "Checking network"),
                t("Опрашиваем контрольные узлы: $completed из $total.", "Probing reference nodes: $completed of $total."))
            result == null -> DiagnosticsHeroTitle(t("Не проверено", "Not checked"),
                t("Запустите проверку, чтобы узнать, есть ли ограничения.", "Run a check to see whether the network is restricted."))
            else -> DiagnosticsHeroTitle(result.title, result.description)
        }
        if (running) {
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { if (total == 0) 0f else completed / total.toFloat() },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                color = nebulaColors.accent, trackColor = nebulaColors.softFill, drawStopIndicator = {}
            )
        }
        Spacer(Modifier.height(18.dp))
        Row {
            DiagnosticsHeroMetric(t("Узлы", "Nodes"),
                result?.takeIf { !running }?.let { "${it.availableCount}/${it.checkedCount}" } ?: "—", Modifier.weight(1f))
            DiagnosticsHeroMetric(t("Пинг, мс", "Ping, ms"),
                result?.takeIf { !running }?.averagePingMs?.toString() ?: "—", Modifier.weight(1f))
            DiagnosticsHeroMetric(t("Успех", "Success"),
                result?.takeIf { !running }?.let { "${it.successRate}%" } ?: "—", Modifier.weight(1f))
        }
        result?.takeIf { !running }?.let {
            Spacer(Modifier.height(12.dp))
            Text(t("Проверено ${formatHistoryTime(it.checkedAt)}", "Checked ${formatHistoryTime(it.checkedAt)}"),
                color = nebulaColors.textTertiary, style = MaterialTheme.typography.bodySmall)
        }
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
private fun ResultDetails(result: ConnectivityDiagnosticResult) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        CheckGroup.entries.forEach { group ->
            val groupChecks = result.checks.filter { it.target.group == group }
            if (groupChecks.isNotEmpty()) {
                Column {
                    Row(Modifier.padding(start = 4.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        DiagnosticsEyebrow(group.title, Modifier.weight(1f))
                        DiagnosticsEyebrow("${groupChecks.count { it.isAvailable }}/${groupChecks.size}")
                    }
                    NimboPanel(Modifier.fillMaxWidth()) {
                        Column {
                            groupChecks.forEachIndexed { index, check ->
                                HostResultRow(check)
                                if (index != groupChecks.lastIndex) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = LocalNebulaColors.current.divider
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
    val statusColor = if (check.isAvailable) DiagnosticsOk else DiagnosticsBad
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).background(statusColor, CircleShape))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(check.target.name, style = MaterialTheme.typography.titleSmall, color = nebulaColors.textPrimary,
                fontWeight = FontWeight.SemiBold)
            Text(
                if (check.isAvailable) check.target.host
                else "${check.target.host} · ${check.error ?: t("нет ответа", "no response")}",
                style = MaterialTheme.typography.bodySmall, color = nebulaColors.textTertiary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            if (check.isAvailable) check.pingMs?.let { t("$it мс", "$it ms") } ?: "OK" else t("недоступен", "blocked"),
            color = if (check.isAvailable) nebulaColors.textPrimary else statusColor,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.Monospace),
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun shareConnectivityResult(context: Context, result: ConnectivityDiagnosticResult) {
    val lines = buildList {
        add("Nimbo · проверка сети · ${formatHistoryTime(result.checkedAt)}")
        add("${result.title}. ${result.description}")
        add("Узлы ${result.availableCount}/${result.checkedCount} · успех ${result.successRate}%" +
            (result.averagePingMs?.let { " · пинг $it мс" } ?: ""))
        result.checks.filterNot { it.isAvailable }.map { it.target.name }.takeIf { it.isNotEmpty() }
            ?.let { add("Недоступны: ${it.joinToString()}") }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, lines.joinToString("\n"))
    }
    context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun EmptyHistoryCard() {
    val nebulaColors = LocalNebulaColors.current
    WindowsFlatPanel(shape = RoundedCornerShape(18.dp)) {
        Text(
            text = "История пока пустая. Запустите проверку, и результат появится здесь.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = nebulaColors.textSecondary,
            textAlign = TextAlign.Start
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
    ConnectivityVerdict.NORMAL -> Icons.Default.Wifi
    ConnectivityVerdict.RESTRICTED -> Icons.Default.Warning
    ConnectivityVerdict.NO_INTERNET -> Icons.Default.WifiOff
}

@Composable
private fun ConnectivityVerdict.color(): Color = when (this) {
    ConnectivityVerdict.NORMAL -> DiagnosticsOk
    ConnectivityVerdict.RESTRICTED -> DiagnosticsWarn
    ConnectivityVerdict.NO_INTERNET -> DiagnosticsBad
}

private fun CheckGroup.icon(): ImageVector = when (this) {
    CheckGroup.INTERNATIONAL -> Icons.Default.Public
    CheckGroup.LOCAL -> Icons.Default.CellTower
    CheckGroup.TELEMETRY -> Icons.Default.Speed
    CheckGroup.INFRA -> Icons.Default.Dns
}

private suspend fun runConnectivityDiagnostics(
    onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
): ConnectivityDiagnosticResult = withContext(Dispatchers.IO) {
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

    val done = AtomicInteger(0)
    onProgress(0, targets.size)
    val checks = coroutineScope {
        targets.map { target ->
            async { checkTarget(target).also { onProgress(done.incrementAndGet(), targets.size) } }
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
