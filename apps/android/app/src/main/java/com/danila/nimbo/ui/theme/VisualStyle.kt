package com.danila.nimbo.ui.theme

/**
 * Element styles are stored as integers. The numeric values are part of the
 * preferences format and must remain stable between app updates.
 */
enum class ElementStyleMode(val persistedValue: Int) {
    LIQUID_GLASS(0),
    MATERIAL_EXPRESSIVE(1),
    NOTHING_DOTS(2),
    OUTLINED(3),
    SOFT_NEO(4);

    companion object {
        fun fromPersistedValue(value: Int): ElementStyleMode =
            entries.firstOrNull { it.persistedValue == value } ?: LIQUID_GLASS
    }
}
