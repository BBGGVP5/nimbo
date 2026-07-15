package com.danila.nimbo.service

import android.annotation.SuppressLint
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.danila.nimbo.R
import com.danila.nimbo.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ProfileStatsQuickSettingsTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var preferencesManager: PreferencesManager

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            while (isActive) {
                updateTileState()
                delay(1500)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        scope.coroutineContext.cancelChildren()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.let {
            it.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            startActivityAndCollapse(it)
        }
    }

    private fun updateTileState() {
        val stats = QuickSettingsStatsProvider.load(preferencesManager)
        qsTile.state = Tile.STATE_ACTIVE
        qsTile.label = stats?.profileName ?: "Подписка"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            qsTile.subtitle = if (stats == null) {
                "Нет активного профиля"
            } else {
                "До конца: ${QuickSettingsStatsProvider.formatDays(stats.daysUntilExpiry)}"
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            qsTile.icon = android.graphics.drawable.Icon.createWithResource(
                this,
                R.drawable.icon_quick_settings
            )
        }

        qsTile.updateTile()
    }
}
