package com.danila.nimbo.model

import com.danila.nimbo.ui.components.NotificationType

data class NotificationItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: NotificationType = NotificationType.NORMAL
)
