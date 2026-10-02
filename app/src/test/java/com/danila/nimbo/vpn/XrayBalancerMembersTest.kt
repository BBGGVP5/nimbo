package com.danila.nimbo.vpn

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class XrayBalancerMembersTest {
    private fun template() = JSONObject("""{"outbounds":[{"tag":"proxy/owned","protocol":"vless","settings":{"vnext":[{"address":"provider.example","port":443}]}}],"routing":{"balancers":[{"tag":"auto","selector":["proxy/"]}]}}""")
    @Test fun `already expanded provider members are not rebuilt`() {
        assertFalse(XrayBalancerMembers.needsInjection(template()))
        val partial = template()
        partial.getJSONObject("routing").getJSONArray("balancers").put(JSONObject("""{"tag":"backup","selector":["backup/"]}"""))
        assertFalse(XrayBalancerMembers.needsInjection(partial)) // Never destroy main members to fill an unrelated group.
    }
    @Test fun `explicit injection rules and placeholder members still expand`() {
        val root = template()
        root.getJSONArray("outbounds").getJSONObject(0).getJSONObject("settings").getJSONArray("vnext").getJSONObject(0).put("address", "API")
        assertTrue(XrayBalancerMembers.needsInjection(root))
        val explicit = template().put("remnawave", JSONObject("""{"injectHosts":[{"tagPrefix":"proxy/"}]}"""))
        assertTrue(XrayBalancerMembers.needsInjection(explicit))
    }
}
