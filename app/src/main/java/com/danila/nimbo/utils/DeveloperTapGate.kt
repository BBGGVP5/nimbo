package com.danila.nimbo.utils

internal class DeveloperTapGate {
    private var first = 0L
    private var taps = 0
    fun tap(nowMs: Long): Boolean {
        if (taps == 0 || nowMs < first || nowMs - first > 2000L) { first = nowMs; taps = 0 }
        taps++
        if (taps < 5) return false
        taps = 0
        return true
    }
}
