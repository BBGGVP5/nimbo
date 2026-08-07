package com.danila.nimbo.awg

import com.danila.nimbo.model.Server

/**
 * Собирает строку UAPI-настроек для движка AmneziaWG из модели [Server].
 * Формат соответствует amneziawg-go: каждая строка `key=value`,
 * набор заканчивается пустой строкой.
 *
 * Движок задекларирован как чистый WireGuard/AmneziaWG-клиент: один peer,
 * полная маршрутизация (0.0.0.0/0, ::/0), сокет слушает эфемерный порт,
 * подключённый TUN берётся из fd (каталог сокетов вшит в .so на этапе сборки).
 */
object AwgConfigBuilder {

    private const val DEFAULT_ALLOWED_IPS = "0.0.0.0/0, ::/0"

    fun buildSettings(server: Server): String? {
        val privateKey = server.wgPrivateKey?.trim().orEmpty()
        val peerKey = server.wgPublicKey?.trim().orEmpty()
        if (privateKey.isBlank() || peerKey.isBlank()) return null

        val lines = mutableListOf(
            "private_key=$privateKey",
            "listen_port=0",
            "replace_peers=true",
            "public_key=$peerKey"
        )

        server.wgPresharedKey?.takeIf { it.isNotBlank() }?.let { key ->
            lines += "preshared_key=${key.trim()}"
        }

        val endpointHost = server.host.trim()
        val endpointPort = server.port.coerceAtLeast(1)
        lines += "endpoint=$endpointHost:$endpointPort"

        server.wgKeepAlive?.takeIf { it > 0 }?.let { keepAlive ->
            lines += "persistent_keepalive_interval=$keepAlive"
        }

        lines += "replace_allowed_ips=true"
        val allowedIps = server.wgAllowedIps?.takeIf { it.isNotBlank() } ?: DEFAULT_ALLOWED_IPS
        allowedIps.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { ip ->
            lines += "allowed_ip=$ip"
        }

        server.awgJc?.let { lines += "jc=$it" }
        server.awgJmin?.let { lines += "jmin=$it" }
        server.awgJmax?.let { lines += "jmax=$it" }
        server.awgS1?.let { lines += "s1=$it" }
        server.awgS2?.let { lines += "s2=$it" }
        server.awgS3?.let { lines += "s3=$it" }
        server.awgS4?.let { lines += "s4=$it" }
        server.awgH1?.let { lines += "h1=$it" }
        server.awgH2?.let { lines += "h2=$it" }
        server.awgH3?.let { lines += "h3=$it" }
        server.awgH4?.let { lines += "h4=$it" }
        server.awgI1?.let { lines += "i1=$it" }
        server.awgI2?.let { lines += "i2=$it" }
        server.awgI3?.let { lines += "i3=$it" }
        server.awgI4?.let { lines += "i4=$it" }
        server.awgI5?.let { lines += "i5=$it" }

        return lines.joinToString("\n") + "\n"
    }
}
