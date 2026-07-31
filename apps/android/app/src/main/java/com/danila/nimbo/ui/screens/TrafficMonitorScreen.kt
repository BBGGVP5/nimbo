package com.danila.nimbo.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.TrafficStats
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danila.nimbo.ui.components.AnimatedGradientBackground
import com.danila.nimbo.ui.components.GlassCard
import com.danila.nimbo.ui.components.GlassHeader
import com.danila.nimbo.ui.theme.LocalNebulaColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class TrafficRow(
    val uid: Int,
    val name: String,
    val packageName: String,
    val packageCount: Int,
    val icon: ImageBitmap?,
    val rxBytes: Long,
    val txBytes: Long,
    val rxRate: Long,
    val txRate: Long
) {
    val totalBytes: Long
        get() = rxBytes + txBytes
}

private data class RawTrafficRow(
    val uid: Int,
    val name: String,
    val packageName: String,
    val packageCount: Int,
    val icon: ImageBitmap?,
    val rxBytes: Long,
    val txBytes: Long
)

private data class AppTrafficMeta(
    val name: String,
    val packageName: String,
    val packageCount: Int,
    val icon: ImageBitmap?
)

private object TrafficMonitorSession {
    val appMetaCache = mutableMapOf<Int, AppTrafficMeta>()
    var baselineRows: Map<Int, Pair<Long, Long>> = emptyMap()
    var previousRows: Map<Int, Pair<Long, Long>> = emptyMap()
    var baselineTotalRx: Long = -1L
    var baselineTotalTx: Long = -1L
    var previousTotalRx: Long = -1L
    var previousTotalTx: Long = -1L
    var rows: List<TrafficRow> = emptyList()
    var totalRx: Long = 0L
    var totalTx: Long = 0L
    var totalRxRate: Long = 0L
    var totalTxRate: Long = 0L
}

@Composable
fun TrafficMonitorScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val nebulaColors = LocalNebulaColors.current
    var rows by remember { mutableStateOf(TrafficMonitorSession.rows) }
    var totalRx by remember { mutableLongStateOf(TrafficMonitorSession.totalRx) }
    var totalTx by remember { mutableLongStateOf(TrafficMonitorSession.totalTx) }
    var totalRxRate by remember { mutableLongStateOf(TrafficMonitorSession.totalRxRate) }
    var totalTxRate by remember { mutableLongStateOf(TrafficMonitorSession.totalTxRate) }

    LaunchedEffect(Unit) {
        if (TrafficMonitorSession.baselineTotalRx < 0L || TrafficMonitorSession.baselineTotalTx < 0L) {
            TrafficMonitorSession.baselineTotalRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
            TrafficMonitorSession.baselineTotalTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
            TrafficMonitorSession.previousTotalRx = TrafficMonitorSession.baselineTotalRx
            TrafficMonitorSession.previousTotalTx = TrafficMonitorSession.baselineTotalTx
        }
        while (true) {
            val rawSnapshot = withContext(Dispatchers.IO) {
                collectUidTrafficSnapshot(packageManager, TrafficMonitorSession.appMetaCache)
            }
            if (TrafficMonitorSession.baselineRows.isEmpty() && rawSnapshot.isNotEmpty()) {
                TrafficMonitorSession.baselineRows = rawSnapshot.associate { it.uid to (it.rxBytes to it.txBytes) }
            }
            val snapshot = rawSnapshot
                .map { raw ->
                    val baseline = TrafficMonitorSession.baselineRows[raw.uid] ?: (raw.rxBytes to raw.txBytes)
                    val previous = TrafficMonitorSession.previousRows[raw.uid]
                    TrafficRow(
                        uid = raw.uid,
                        name = raw.name,
                        packageName = raw.packageName,
                        packageCount = raw.packageCount,
                        icon = raw.icon,
                        rxBytes = (raw.rxBytes - baseline.first).coerceAtLeast(0L),
                        txBytes = (raw.txBytes - baseline.second).coerceAtLeast(0L),
                        rxRate = (raw.rxBytes - (previous?.first ?: raw.rxBytes)).coerceAtLeast(0L),
                        txRate = (raw.txBytes - (previous?.second ?: raw.txBytes)).coerceAtLeast(0L)
                    )
                }
                .filter { it.totalBytes > 0L || it.rxRate > 0L || it.txRate > 0L }
                .sortedWith(compareByDescending<TrafficRow> { it.rxRate + it.txRate }.thenByDescending { it.totalBytes })
                .take(40)
            val nextTotalRx = TrafficStats.getTotalRxBytes().coerceAtLeast(0L)
            val nextTotalTx = TrafficStats.getTotalTxBytes().coerceAtLeast(0L)
            totalRxRate = (nextTotalRx - TrafficMonitorSession.previousTotalRx).coerceAtLeast(0L)
            totalTxRate = (nextTotalTx - TrafficMonitorSession.previousTotalTx).coerceAtLeast(0L)
            totalRx = (nextTotalRx - TrafficMonitorSession.baselineTotalRx).coerceAtLeast(0L)
            totalTx = (nextTotalTx - TrafficMonitorSession.baselineTotalTx).coerceAtLeast(0L)
            rows = snapshot
            TrafficMonitorSession.rows = snapshot
            TrafficMonitorSession.totalRx = totalRx
            TrafficMonitorSession.totalTx = totalTx
            TrafficMonitorSession.totalRxRate = totalRxRate
            TrafficMonitorSession.totalTxRate = totalTxRate
            TrafficMonitorSession.previousRows = rawSnapshot.associate { it.uid to (it.rxBytes to it.txBytes) }
            TrafficMonitorSession.previousTotalRx = nextTotalRx
            TrafficMonitorSession.previousTotalTx = nextTotalTx
            delay(1000)
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
                title = "Трафик",
                icon = Icons.Default.Public,
                iconColor = nebulaColors.accent,
                onBack = onNavigateBack
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TrafficTotalColumn("Скачать", totalRx, totalRxRate, Color(0xFF61D394), Modifier.weight(1f))
                        TrafficTotalColumn("Отдать", totalTx, totalTxRate, Color(0xFF8AA8FF), Modifier.weight(1f))
                    }
                }

                if (rows.isEmpty()) {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Пока нет трафика в текущей сессии монитора. Откройте приложение через VPN, и здесь появятся UID, пакеты и скорость.",
                            modifier = Modifier.padding(18.dp),
                            color = nebulaColors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    rows.forEach { row ->
                        TrafficAppRow(row)
                    }
                }
            }
        }
    }
}

@Composable
private fun TrafficTotalColumn(title: String, total: Long, rate: Long, color: Color, modifier: Modifier = Modifier) {
    val nebulaColors = LocalNebulaColors.current
    Column(modifier = modifier) {
        Text(title, color = nebulaColors.textSecondary, style = MaterialTheme.typography.labelLarge)
        Text(formatTrafficBytes(total), color = nebulaColors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("${formatTrafficBytes(rate)}/с", color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TrafficAppRow(row: TrafficRow) {
    val nebulaColors = LocalNebulaColors.current
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(42.dp),
                contentAlignment = Alignment.Center
            ) {
                if (row.icon != null) {
                    Image(
                        bitmap = row.icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Icon(Icons.Default.Public, contentDescription = null, tint = nebulaColors.accent, modifier = Modifier.size(24.dp))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    row.name,
                    color = nebulaColors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (row.packageCount > 1) "${row.packageName} +${row.packageCount - 1}" else row.packageName,
                    color = nebulaColors.textTertiary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "↓ ${formatTrafficBytes(row.rxBytes)} | ↑ ${formatTrafficBytes(row.txBytes)}",
                    color = nebulaColors.textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "↓ ${formatTrafficBytes(row.rxRate)}/с | ↑ ${formatTrafficBytes(row.txRate)}/с",
                    color = Color(0xFF61D394),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun collectUidTrafficSnapshot(
    packageManager: PackageManager,
    appMetaCache: MutableMap<Int, AppTrafficMeta>
): List<RawTrafficRow> {
    return packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        .asSequence()
        .filter { it.uid > 0 }
        .groupBy { it.uid }
        .mapNotNull { (uid, apps) ->
            val rx = TrafficStats.getUidRxBytes(uid)
            val tx = TrafficStats.getUidTxBytes(uid)
            if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
                null
            } else {
                val meta = appMetaCache.getOrPut(uid) {
                    resolveAppTrafficMeta(apps, packageManager)
                }
                RawTrafficRow(
                    uid = uid,
                    name = meta.name,
                    packageName = meta.packageName,
                    packageCount = meta.packageCount,
                    icon = meta.icon,
                    rxBytes = rx.coerceAtLeast(0L),
                    txBytes = tx.coerceAtLeast(0L)
                )
            }
        }
        .toList()
}

private fun resolveAppTrafficMeta(apps: List<ApplicationInfo>, packageManager: PackageManager): AppTrafficMeta {
    val primary = apps.firstOrNull { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 } ?: apps.first()
    return AppTrafficMeta(
        name = primary.loadLabel(packageManager).toString(),
        packageName = primary.packageName,
        packageCount = apps.size,
        icon = runCatching { primary.loadIcon(packageManager).toImageBitmap(96) }.getOrNull()
    )
}

private fun Drawable.toImageBitmap(sizePx: Int): ImageBitmap {
    if (this is BitmapDrawable && bitmap != null) {
        return bitmap.asImageBitmap()
    }
    val width = intrinsicWidth.takeIf { it > 0 } ?: sizePx
    val height = intrinsicHeight.takeIf { it > 0 } ?: sizePx
    val bitmap = Bitmap.createBitmap(width.coerceAtMost(sizePx), height.coerceAtMost(sizePx), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

private fun formatTrafficBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return if (unitIndex == 0) {
        "${bytes.coerceAtLeast(0L)} ${units[unitIndex]}"
    } else {
        String.format(java.util.Locale.US, "%.1f %s", value, units[unitIndex]).replace('.', ',')
    }
}

