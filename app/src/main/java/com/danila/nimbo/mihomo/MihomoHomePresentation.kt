package com.danila.nimbo.mihomo

internal data class MihomoHomePresentation(val title: String, val subtitle: String)

/** A profile may route different sites through different groups. An empty
 * manual choice is valid and must not be presented as a missing location. */
internal fun mihomoHomePresentation(
    live: MihomoActiveSelection?, choiceName: String?, choiceGroup: String?,
    connected: Boolean, english: Boolean
): MihomoHomePresentation {
    val title = choiceName?.takeIf(String::isNotBlank)
        ?: live?.group?.let { if (english) "Auto · $it" else "Авто · $it" }
        ?: if (english) "Auto · profile rules" else "Авто · правила профиля"
    val subtitle = when {
        live?.perConnection == true -> if (english) "Server chosen per connection" else "Сервер выбирается для каждого соединения"
        live?.member != null -> if (english) "Now: ${live.member}" else "Сейчас: ${live.member}"
        !choiceName.isNullOrBlank() -> choiceGroup.orEmpty()
        connected -> if (english) "Traffic follows the profile's groups" else "Трафик идёт через группы профиля"
        else -> if (english) "Groups will choose a server when connecting" else "Группы выберут сервер при подключении"
    }
    return MihomoHomePresentation(title, subtitle)
}
