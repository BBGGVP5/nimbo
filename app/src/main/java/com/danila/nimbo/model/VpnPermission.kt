package com.danila.nimbo.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService

object VpnPermission {

    fun request(activity: Activity): Intent? {
        return VpnService.prepare(activity)
    }

}
