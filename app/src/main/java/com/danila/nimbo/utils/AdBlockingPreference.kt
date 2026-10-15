package com.danila.nimbo.utils

import android.content.SharedPreferences

internal object AdBlockingPreference {
    const val KEY = "ad_blocking_enabled"
    fun read(store: SharedPreferences): Boolean = store.getBoolean(KEY, false)
    fun write(store: SharedPreferences, enabled: Boolean) {
        store.edit().putBoolean(KEY, enabled).apply()
    }
}
