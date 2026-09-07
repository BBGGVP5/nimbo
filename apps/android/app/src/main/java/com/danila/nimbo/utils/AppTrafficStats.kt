package com.danila.nimbo.utils

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log

/** Расход трафика одним приложением за период. */
data class AppTraffic(
    val uid: Int,
    val packageName: String,
    val label: String,
    val up: Long,
    val down: Long
) {
    val total: Long get() = up + down
}

/**
 * Разбивка трафика по приложениям — то, чего на Android нельзя взять из ядра.
 *
 * Туннель отдаёт только суммарную статистику (см. queryStats у LibXray), поэтому
 * поштучный расход берём у самой системы через [NetworkStatsManager]. Важное
 * ограничение, которое обязательно показывать пользователю: это трафик приложений
 * по сети вообще, а не только через туннель. Придумывать разбивку туннеля
 * «на глазок» нельзя — это были бы выдуманные числа.
 *
 * Для доступа нужен «Доступ к данным использования» — разрешение выдаётся
 * вручную в системных настройках.
 */
object AppTrafficStats {

    private const val TAG = "AppTrafficStats"

    fun hasUsageAccess(context: Context): Boolean = runCatching {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        mode == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Топ приложений по расходу за период. Возвращает пустой список, если
     * разрешения нет или система ничего не отдала — без заглушек.
     */
    fun query(
        context: Context,
        sinceMs: Long,
        untilMs: Long = System.currentTimeMillis(),
        limit: Int = 8
    ): List<AppTraffic> {
        if (!hasUsageAccess(context)) return emptyList()
        val manager = context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
            ?: return emptyList()

        val totals = HashMap<Int, LongArray>()
        for (networkType in NETWORK_TYPES) {
            collectInto(totals, manager, networkType, sinceMs, untilMs)
        }
        if (totals.isEmpty()) return emptyList()

        val packageManager = context.packageManager
        return totals.entries
            .asSequence()
            .filter { (uid, bytes) -> uid >= 0 && bytes[0] + bytes[1] > 0L }
            .mapNotNull { (uid, bytes) ->
                val packageName = runCatching {
                    packageManager.getPackagesForUid(uid)?.firstOrNull()
                }.getOrNull() ?: return@mapNotNull null
                val label = runCatching {
                    val info = packageManager.getApplicationInfo(packageName, 0)
                    packageManager.getApplicationLabel(info).toString()
                }.getOrDefault(packageName)
                AppTraffic(uid = uid, packageName = packageName, label = label, up = bytes[0], down = bytes[1])
            }
            .sortedByDescending { it.total }
            .take(limit)
            .toList()
    }

    private fun collectInto(
        totals: HashMap<Int, LongArray>,
        manager: NetworkStatsManager,
        networkType: Int,
        sinceMs: Long,
        untilMs: Long
    ) {
        runCatching {
            // subscriberId = null: с Android 11 приложению всё равно не отдадут
            // идентификатор сети, а для суммарной разбивки он не нужен.
            manager.querySummary(networkType, null, sinceMs, untilMs).use { stats ->
                val bucket = NetworkStats.Bucket()
                while (stats.hasNextBucket()) {
                    stats.getNextBucket(bucket)
                    val entry = totals.getOrPut(bucket.uid) { longArrayOf(0L, 0L) }
                    entry[0] += bucket.txBytes
                    entry[1] += bucket.rxBytes
                }
            }
        }.onFailure {
            Log.w(TAG, "Could not read per-app stats for type $networkType: ${it.message}")
        }
    }

    private val NETWORK_TYPES: IntArray
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)
        } else {
            @Suppress("DEPRECATION")
            intArrayOf(ConnectivityManager.TYPE_WIFI, ConnectivityManager.TYPE_MOBILE)
        }
}
