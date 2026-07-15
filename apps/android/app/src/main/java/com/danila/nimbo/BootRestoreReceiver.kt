package com.danila.nimbo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.danila.nimbo.utils.PreferencesManager
import com.danila.nimbo.utils.Logger
import com.danila.nimbo.vpn.MyVpnService

internal enum class VpnRestoreTrigger {
    BOOT,
    PACKAGE_REPLACED
}

internal fun shouldRestoreVpnAfterSystemEvent(
    trigger: VpnRestoreTrigger,
    autoConnect: Boolean,
    connectionDesired: Boolean
): Boolean = when (trigger) {
    VpnRestoreTrigger.BOOT -> autoConnect
    VpnRestoreTrigger.PACKAGE_REPLACED -> connectionDesired
}

class BootRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action.orEmpty()
        val trigger = when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> VpnRestoreTrigger.BOOT
            Intent.ACTION_MY_PACKAGE_REPLACED -> VpnRestoreTrigger.PACKAGE_REPLACED
            else -> return
        }

        val prefs = PreferencesManager(context)
        if (!shouldRestoreVpnAfterSystemEvent(trigger, prefs.autoConnect, prefs.vpnConnectionDesired)) {
            Logger.d(TAG, "Skip VPN restore after $action: policy declined")
            return
        }

        val server = prefs.loadLastSelectedServer()
        if (server == null) {
            Logger.d(TAG, "Skip boot restore: no saved server")
            return
        }

        Logger.i(TAG, "Restoring VPN after system event: $action")
        MyVpnService.startConnectService(context, server)
    }

    companion object {
        private const val TAG = "BootRestoreReceiver"
    }
}
