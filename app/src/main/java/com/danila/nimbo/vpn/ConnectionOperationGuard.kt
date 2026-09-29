package com.danila.nimbo.vpn

import java.util.concurrent.atomic.AtomicLong

/** Shared by UI selections and service work, including queued server switches. */
internal class ConnectionOperationGuard {
    private val generation = AtomicLong()
    val current: Long get() = generation.get()
    fun invalidate(): Long = generation.incrementAndGet()
    fun isCurrent(token: Long): Boolean = token == current
}
