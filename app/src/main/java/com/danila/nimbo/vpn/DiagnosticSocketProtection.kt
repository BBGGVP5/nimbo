package com.danila.nimbo.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process

/** Protect only the duplicated diagnostic socket; never change the VPN's core or network. */
internal object DiagnosticSocketProtection {
    @Volatile var service: VpnService? = null
    private const val DESCRIPTOR = "com.danila.nimbo.ProtectDiagnosticSocket"

    fun bridge(context: Context): IBinder = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != FIRST_CALL_TRANSACTION || Binder.getCallingUid() != Process.myUid()) return false
            data.enforceInterface(DESCRIPTOR)
            val descriptor = data.readParcelable<ParcelFileDescriptor>(ParcelFileDescriptor::class.java.classLoader) ?: return false
            val protected = descriptor.use {
                val vpn = service
                if (vpn != null) vpn.protect(it.fd)
                else {
                    val cm = context.getSystemService(ConnectivityManager::class.java)
                    VpnManager.state.value == VpnState.DISCONNECTED && cm.allNetworks.none { network ->
                        cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
                    }
                }
            }
            reply?.writeNoException()
            reply?.writeInt(if (protected) 1 else 0)
            return true
        }
    }

    fun protect(bridge: IBinder, fd: Int): Boolean = runCatching {
        ParcelFileDescriptor.fromFd(fd).use { duplicate ->
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeParcelable(duplicate, 0)
                if (!bridge.transact(IBinder.FIRST_CALL_TRANSACTION, data, reply, 0)) return@use false
                reply.readException()
                reply.readInt() == 1
            } finally { data.recycle(); reply.recycle() }
        }
    }.getOrDefault(false)
}
