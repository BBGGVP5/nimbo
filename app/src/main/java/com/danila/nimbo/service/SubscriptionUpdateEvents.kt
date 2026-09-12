package com.danila.nimbo.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object SubscriptionUpdateEvents {
    private val mutableUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val updates = mutableUpdates.asSharedFlow()

    fun notifyProfilesChanged() {
        mutableUpdates.tryEmit(Unit)
    }
}
