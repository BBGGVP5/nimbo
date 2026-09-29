package com.danila.nimbo.mihomo

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class MihomoProxyGraphTest {
    private val source = JsonParser.parseString("""{"declaredGraph":{"groups":[
        {"name":"Sites","type":"select","proxies":["FI","LV"]},
        {"name":"Auto","type":"url-test","proxies":["FI","LV"]},
        {"name":"Internal","type":"select","hidden":true,"proxies":["FI"]}
    ]}}""").asJsonObject
    @Test fun sourceOrderAndHiddenGroups() {
        val groups = mihomoProxyGroups(source, null, mapOf("Sites" to "LV"))
        assertEquals(listOf("Sites", "Auto"), groups.map { it.name })
        assertEquals("LV", groups[0].selected); assertTrue(groups[0].selectable); assertFalse(groups[1].selectable); assertEquals("", groups[1].selected)
    }
    @Test fun liveProviderMembersAndSelectionOverrideOffline() {
        val live = JsonParser.parseString("""{"groups":{"Sites":{"type":"Selector","all":["NL","DE"],"now":"DE"}}}""").asJsonObject
        val group = mihomoProxyGroups(source, live, mapOf("Sites" to "LV"))[0]
        assertEquals(listOf("NL", "DE"), group.members); assertEquals("DE", group.selected)
    }
    @Test fun staleOfflineChoiceFallsBackWithoutInventingMember() {
        assertEquals("FI", mihomoProxyGroups(source, null, mapOf("Sites" to "missing"))[0].selected)
    }

    @Test fun activeAutomaticGroupShowsResolvedLiveMember() {
        val live = JsonParser.parseString("""{"groups":{
            "Auto":{"type":"URLTest","now":"Relay"},
            "Relay":{"type":"Selector","now":"Finland"}
        }}""").asJsonObject
        assertEquals(MihomoActiveSelection("Auto", "Finland", false), mihomoActiveSelection(live, null))
    }

    @Test fun loadBalanceDoesNotPretendToHaveOneServer() {
        val live = JsonParser.parseString("""{"groups":{"Balance":{"type":"LoadBalance","now":"Finland"}}}""").asJsonObject
        assertEquals(MihomoActiveSelection("Balance", null, true), mihomoActiveSelection(live, "Balance"))
    }

    @Test fun cyclicGroupCannotBePresentedAsServer() {
        val live = JsonParser.parseString("""{"groups":{
            "Auto":{"type":"URLTest","now":"Loop"},
            "Loop":{"type":"Selector","now":"Auto"}
        }}""").asJsonObject
        assertNull(mihomoActiveSelection(live, null))
    }
}
