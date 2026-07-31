package com.danila.nimbo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinRoutingProfilesTest {
    @Test
    fun `user override keeps a built in id and does not remove the other profiles`() {
        val edited = BuiltinRoutingProfiles.byId(BuiltinRoutingProfiles.RUSSIA_DIRECT)!!
            .copy(name = "Мои правила", directSites = listOf("domain:example.ru"))

        val resolved = BuiltinRoutingProfiles.resolve(mapOf(BuiltinRoutingProfiles.RUSSIA_DIRECT to edited))

        assertEquals(5, resolved.size)
        assertEquals("Мои правила", resolved.first { it.id == BuiltinRoutingProfiles.RUSSIA_DIRECT }.name)
        assertEquals(BuiltinRoutingProfiles.RUSSIA_DIRECT, resolved.first { it.id == BuiltinRoutingProfiles.RUSSIA_DIRECT }.id)
        assertTrue(resolved.all { it.builtin == true })
    }

    @Test
    fun `legacy RosKomVPN spelling resolves to its stable id`() {
        assertEquals(BuiltinRoutingProfiles.ROSCOMVPN, BuiltinRoutingProfiles.idForLegacyName("RosKomVPN"))
    }

    @Test
    fun `built in site lists do not contain GeoIP selectors`() {
        assertTrue(BuiltinRoutingProfiles.defaults().all { profile ->
            profile.directSites.orEmpty().none { it.startsWith("geoip:", ignoreCase = true) }
        })
    }
}
