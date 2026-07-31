package com.danila.nimbo.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Server(
    @SerializedName("name") val name: String,
    @SerializedName("host") val host: String,
    @SerializedName("port") val port: Int,
    @SerializedName("uuid") val uuid: String,
    @SerializedName("protocol") val protocol: String,
    @SerializedName("serverDescription") val serverDescription: String? = null,
    @SerializedName("profileUrl") val profileUrl: String? = null,
    
    // Дополнительные параметры для VLESS/VMess/Reality
    @SerializedName("flow") val flow: String? = null,
    @SerializedName("security") val security: String? = null,
    @SerializedName("network") val network: String? = null, // tcp, ws, grpc
    @SerializedName("path") val path: String? = null,
    @SerializedName("hostHeader") val hostHeader: String? = null,
    @SerializedName("serviceName") val serviceName: String? = null,
    @SerializedName("sni") val sni: String? = null,
    @SerializedName("fingerprint") val fingerprint: String? = null,
    @SerializedName("alpn") val alpn: String? = null,
    @SerializedName("allowInsecure") val allowInsecure: Boolean? = null,
    
    // Reality специфичные поля
    @SerializedName("tls") val tls: Boolean? = null,
    @SerializedName("publicKey") val publicKey: String? = null,
    @SerializedName("shortId") val shortId: String? = null,
    @SerializedName("spiderX") val spiderX: String? = null,
    
    // VMess специфичные поля
    @SerializedName("alterId") val alterId: Int? = null,
    
    // Shadowsocks
    @SerializedName("method") val method: String? = null,

    // Hysteria2 / Xray hysteria transport
    @SerializedName("hysteriaObfs") val hysteriaObfs: String? = null,
    @SerializedName("hysteriaObfsPassword") val hysteriaObfsPassword: String? = null,
    @SerializedName("hysteriaPorts") val hysteriaPorts: String? = null,
    @SerializedName("hysteriaHopInterval") val hysteriaHopInterval: String? = null,
    @SerializedName("hysteriaUp") val hysteriaUp: String? = null,
    @SerializedName("hysteriaDown") val hysteriaDown: String? = null,
    @SerializedName("hysteriaCongestion") val hysteriaCongestion: String? = null,

    // Remnawave template selection hints
    @SerializedName("templateUuid") val templateUuid: String? = null,
    @SerializedName("templateName") val templateName: String? = null,
    // Тег балансировщика/виртуального маршрута из Xray JSON Remnawave. Нужен,
    // чтобы несколько стратегий одного конфига (leastPing, leastLoad и т. п.)
    // оставались отдельными пунктами и подключались именно к выбранной стратегии.
    @SerializedName("remoteBalancerTag") val remoteBalancerTag: String? = null,
    @SerializedName("remoteOutboundTag") val remoteOutboundTag: String? = null,
    @SerializedName("pingHost") val pingHost: String? = null,
    @SerializedName("pingPort") val pingPort: Int? = null,

    // Сервер из аварийного пула (заголовок подписки nimbo-fallback). Такие узлы
    // подмешиваются как fallback-группа и используются, когда основные недоступны.
    @SerializedName("isFallback") val isFallback: Boolean = false,

    // Метрики (пинги)
    @SerializedName("ping") val ping: Int? = null,
    @SerializedName("pingTimestamp") val pingTimestamp: Long? = null
) : Serializable {
    private fun normalized(value: String?): String = value?.trim()?.lowercase().orEmpty()

    fun isRemoteTemplateServer(): Boolean {
        return normalized(uuid) == "remote" || normalized(host) == "api"
    }

    /**
     * Stable key for user selection and preset binding.
     * For remote/API template servers includes template hints to avoid selecting wrong template.
     */
    fun selectionKey(): String {
        val profile = normalized(profileUrl)
        val hostPart = normalized(host)
        val uuidPart = normalized(uuid)
        val protocolPart = normalized(protocol)
        val networkPart = normalized(network)

        return if (isRemoteTemplateServer()) {
            val templateUuidPart = normalized(templateUuid)
            val templateNamePart = normalized(templateName)
            val balancerTagPart = normalized(remoteBalancerTag)
            val outboundTagPart = normalized(remoteOutboundTag)
            "remote|$profile|$hostPart|$port|$uuidPart|$templateUuidPart|$templateNamePart|$balancerTagPart|$outboundTagPart"
        } else {
            "regular|$profile|$hostPart|$port|$uuidPart|$protocolPart|$networkPart"
        }
    }

    fun matchesSelection(other: Server): Boolean {
        if (normalized(name) != normalized(other.name)) return false
        if (selectionKey() == other.selectionKey()) return true

        if (isRemoteTemplateServer() && other.isRemoteTemplateServer()) {
            val sameBase = normalized(profileUrl) == normalized(other.profileUrl) &&
                normalized(host) == normalized(other.host) &&
                port == other.port &&
                normalized(uuid) == normalized(other.uuid)
            if (!sameBase) return false

            val thisTemplateUuid = normalized(templateUuid)
            val otherTemplateUuid = normalized(other.templateUuid)
            if (thisTemplateUuid.isNotBlank() && otherTemplateUuid.isNotBlank()) {
                return thisTemplateUuid == otherTemplateUuid
            }

            val thisTemplateName = normalized(templateName)
            val otherTemplateName = normalized(other.templateName)
            if (thisTemplateName.isNotBlank() && otherTemplateName.isNotBlank()) {
                return thisTemplateName == otherTemplateName
            }
        }

        return false
    }

    fun pingKey(): String {
        val stableId = if (uuid.isNotBlank()) uuid else name.lowercase()
        // В одной подписке uuid часто общий для TCP/XHTTP/gRPC вариантов.
        // Добавляем transport-часть, чтобы варианты не склеивались и не "переезжало" описание.
        val transportId = listOf(
            protocol.lowercase(),
            network?.lowercase().orEmpty(),
            security?.lowercase().orEmpty(),
            flow?.lowercase().orEmpty(),
            hostHeader?.lowercase().orEmpty(),
            path.orEmpty(),
            serviceName.orEmpty(),
            sni?.lowercase().orEmpty(),
            fingerprint?.lowercase().orEmpty(),
            publicKey.orEmpty(),
            shortId.orEmpty(),
            hysteriaObfs?.lowercase().orEmpty(),
            hysteriaObfsPassword.orEmpty(),
            hysteriaPorts.orEmpty(),
            hysteriaHopInterval.orEmpty()
        ).joinToString("|")
        val pingTargetPart = if (isRemoteTemplateServer()) {
            "|${normalized(pingHost)}|${pingPort ?: 0}|${normalized(remoteBalancerTag)}|${normalized(remoteOutboundTag)}"
        } else {
            ""
        }
        val profilePart = profileUrl?.hashCode()?.toString(16) ?: "null"
        return "${host}_${port}_${stableId}_${transportId}${pingTargetPart}_$profilePart"
    }

    /**
     * Identity used only for measured latency. Node names and remote-template
     * hints deliberately stay in this key: two nodes may share host:port while
     * selecting different CDN edges, outbounds, or routing strategies.
     */
    fun pingMeasurementKey(): String = listOf(
        pingKey(),
        normalized(name),
        normalized(templateUuid),
        normalized(templateName),
        normalized(remoteBalancerTag),
        normalized(remoteOutboundTag)
    ).joinToString("|")

    fun isPingValid(): Boolean {
        // Пинг хранится как "последний известный" результат и остается видимым
        // до следующей проверки, даже после перезапуска приложения.
        return ping != null && ping >= 0
    }
}
