package com.danila.nimbo.ui.screens

import com.danila.nimbo.model.Server

/** Provider prose only; never turn a transport URL or host into a subtitle. */
internal fun readableServerDescription(server: Server): String {
    val host = server.host.trim()
    return server.serverDescription
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?.takeIf { desc -> !desc.equals(host, ignoreCase = true) }
        ?.takeIf { desc -> !desc.contains("://") }
        .orEmpty()
}

