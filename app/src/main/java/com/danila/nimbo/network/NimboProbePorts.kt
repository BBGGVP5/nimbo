package com.danila.nimbo.network

import java.net.InetAddress
import java.net.ServerSocket

internal object NimboProbePorts {
    /** Hold the first reservation while allocating the second; close both before core bind. */
    fun allocate(readiness: Boolean): Pair<Int, Int?> {
        val loopback = InetAddress.getByName("127.0.0.1")
        return ServerSocket(0, 1, loopback).use { target ->
            if (readiness) ServerSocket(0, 1, loopback).use { status -> target.localPort to status.localPort }
            else target.localPort to null
        }
    }
}
