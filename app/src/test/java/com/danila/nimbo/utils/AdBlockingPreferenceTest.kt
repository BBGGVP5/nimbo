package com.danila.nimbo.utils

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class AdBlockingPreferenceTest {
    private fun store(values: MutableMap<String, Any>): SharedPreferences {
        val edits = mutableMapOf<String, Any>()
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putBoolean" -> { edits[args!![0] as String] = args[1] as Boolean; editor }
                "apply" -> { values.putAll(edits); edits.clear(); null }
                else -> error("Unexpected editor operation: ${method.name}")
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getBoolean" -> values[args!![0] as String] ?: args[1]
                "edit" -> editor
                else -> error("Unexpected preference operation: ${method.name}")
            }
        } as SharedPreferences
    }

    @Test fun existingInstallMigratesToOffWithoutWritingOrChangingProviderPreferences() {
        val values = mutableMapOf<String, Any>("routing_enabled" to true, "provider_rules" to "original")
        assertFalse(AdBlockingPreference.read(store(values)))
        assertFalse(values.containsKey(AdBlockingPreference.KEY))
        assertEquals("original", values["provider_rules"])
    }

    @Test fun choiceRoundTripsAcrossInstancesAndCanBeDisabledWithoutChangingOtherSettings() {
        val values = mutableMapOf<String, Any>("routing_enabled" to true)
        AdBlockingPreference.write(store(values), true)
        assertTrue(AdBlockingPreference.read(store(values)))
        AdBlockingPreference.write(store(values), false)
        assertFalse(AdBlockingPreference.read(store(values)))
        assertEquals(true, values["routing_enabled"])
    }
}
