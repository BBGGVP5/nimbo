package com.danila.nimbo.vpn

import com.danila.nimbo.model.Server
import com.danila.nimbo.mihomo.MihomoBridge

/** Engine preference is distinct from Auto server selection and from a transport. */
enum class VpnCoreChoice(val id: String, val title: String) {
    AUTO("auto", "Авто"), XRAY("xray", "Xray"), AWG("awg", "AmneziaWG"), MIHOMO("mihomo", "Mihomo");

    companion object {
        fun fromId(id: String): VpnCoreChoice? = entries.firstOrNull { it.id == id.trim().lowercase() }
    }
}

object VpnCorePolicy {
    enum class Rejection { UNKNOWN_CORE, UNAVAILABLE, INCOMPATIBLE, NO_COMPATIBLE_SERVERS }

    fun isMihomo(server: Server): Boolean = server.protocol.trim().equals("mihomo", true)

    fun rejection(coreId: String, server: Server, mihomoAvailable: Boolean? = null): Rejection? {
        val choice = VpnCoreChoice.fromId(coreId) ?: return Rejection.UNKNOWN_CORE
        // Only the persisted native-profile marker may enter the trusted Android adapter.
        if (isMihomo(server)) {
            if (choice != VpnCoreChoice.AUTO && choice != VpnCoreChoice.MIHOMO) return Rejection.INCOMPATIBLE
            if (server.host != "mihomo.invalid" || server.uuid.isBlank() || server.profileUrl.isNullOrBlank())
                return Rejection.INCOMPATIBLE
            return if (mihomoAvailable ?: MihomoBridge.available()) null else Rejection.UNAVAILABLE
        }
        if (choice == VpnCoreChoice.MIHOMO || server.protocol.trim().lowercase() in
            setOf("clash", "clash-meta", "yaml")) return Rejection.INCOMPATIBLE
        // Native Xray templates must not accidentally enter the WG INI runner.
        if (server.usesAwgEngine() && (server.isRemoteTemplateServer() ||
                server.templateUuid?.startsWith("subscription-json:") == true)) return Rejection.INCOMPATIBLE
        val actual = if (server.usesAwgEngine()) VpnCoreChoice.AWG else VpnCoreChoice.XRAY
        return if (choice == VpnCoreChoice.AUTO || choice == actual) null else Rejection.INCOMPATIBLE
    }

    fun candidates(coreId: String, servers: List<Server>): List<Server> =
        servers.filter { rejection(coreId, it) == null }

    fun message(reason: Rejection, english: Boolean): String = when (reason) {
        Rejection.UNKNOWN_CORE -> if (english) "Unknown VPN core. Choose a core in Settings → VPN core."
            else "Неизвестное ядро VPN. Выберите ядро в Настройки → Ядро VPN."
        Rejection.UNAVAILABLE -> if (english) "The compiled Mihomo Android adapter is unavailable in this build."
            else "Скомпилированный Android-адаптер Mihomo недоступен в этой сборке."
        Rejection.INCOMPATIBLE -> if (english) "This server is incompatible with the selected core. Choose a compatible profile or core."
            else "Сервер несовместим с выбранным ядром. Выберите подходящий профиль или ядро."
        Rejection.NO_COMPATIBLE_SERVERS -> if (english) "No servers compatible with the selected core in this subscription."
            else "В подписке нет серверов, совместимых с выбранным ядром."
    }
}
