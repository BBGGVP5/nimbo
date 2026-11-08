package com.danila.nimbo.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun NimboRoutingScreen(state: NimboUiState, actions: NimboUiActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(top = LocalNimboContentTop.current, bottom = LocalNimboContentBottom.current).nimboScreenPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NimboSettingsBack("Настройки") { actions.onOpenScreen(NimboScreen.SETTINGS.wireName) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText("Маршрутизация", Modifier.weight(1f).semantics { heading() },
                style = NimboTitleStyle.copy(fontSize = 24.sp, lineHeight = 30.sp))
            NimboSettingsInfo("Маршрутизация", "Изменения применяются при следующем подключении. На iOS доступны правила по доменам и адресам; выбор приложений ограничен системой.")
        }
        AdBlockingSettingsCard(state, actions)
        SettingsSection("Правила") {
            RoutingDestination("Профили", state.routingProfiles.firstOrNull { it.id == state.routingProfileId }
                ?.let { "${it.name} · ${it.ruleCount} правил" } ?: "Не выбран") {
                actions.onOpenScreen(NimboScreen.ROUTING_PROFILES.wireName)
            }
            SettingsDivider()
            RoutingDestination("Модули", "${state.modules.count { it.enabled }} включено из ${state.modules.size}") {
                actions.onOpenScreen(NimboScreen.MODULES.wireName)
            }
        }
        SettingsSection("Соединение") {
            AppearanceToggle("Обход локальных сетей", state.routingBypassLocal,
                info = "Принтеры, NAS и роутер остаются доступны напрямую") {
                actions.onSetRouting("bypassLocal", it.toString())
            }
            SettingsDivider()
            AppearanceToggle("Определение доменов", state.routingSniffing,
                info = "Ядро читает имя сайта из соединения — нужно для правил по доменам") {
                actions.onSetRouting("sniffing", it.toString())
            }
        }
        SettingsSection("DNS в туннеле") {
            NimboSettingsSelector("Набор серверов", NimboDnsPreset.entries.map {
                NimboDropdownOption(it.key, it.title, it.subtitle)
            }, state.routingDns, subtitle = "Кому туннель задаёт вопросы об именах сайтов") {
                actions.onSetRouting("dns", it)
            }
        }
    }
}

@Composable
private fun RoutingDestination(title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick)
        .padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            BasicText(title, style = NimboBodyStyle.copy(color = NimboPalette.Text))
            BasicText(value, style = NimboBodyStyle)
        }
        BasicText("›", Modifier.padding(start = 8.dp), style = NimboBodyStyle)
    }
}

/** Наборы DNS: значения уходят в системные настройки туннеля. */
internal enum class NimboDnsPreset(
    val key: String,
    val title: String,
    val subtitle: String
) {
    CLOUDFLARE("cloudflare", "Cloudflare", "1.1.1.1 — по умолчанию"),
    GOOGLE("google", "Google", "8.8.8.8"),
    ADGUARD("adguard", "AdGuard", "94.140.14.14 — с фильтрацией рекламы"),
    SYSTEM("system", "Системный", "DNS оператора или Wi-Fi сети")
}

