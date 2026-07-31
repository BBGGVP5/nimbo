package com.danila.nimbo.ui.components

enum class HapticStrength(val persistedValue: Int) {
    Light(0),
    Medium(1),
    Strong(2);

    companion object {
        fun fromPersistedValue(value: Int): HapticStrength =
            entries.firstOrNull { it.persistedValue == value } ?: Medium
    }
}

internal data class HapticPulse(
    val durationMs: Long,
    val amplitude: Int
)

internal object HapticPulsePolicy {
    fun tick(strength: HapticStrength): HapticPulse = when (strength) {
        HapticStrength.Light -> HapticPulse(durationMs = 8L, amplitude = 55)
        HapticStrength.Medium -> HapticPulse(durationMs = 12L, amplitude = 110)
        HapticStrength.Strong -> HapticPulse(durationMs = 17L, amplitude = 185)
    }

    fun confirmation(strength: HapticStrength): HapticPulse = when (strength) {
        HapticStrength.Light -> HapticPulse(durationMs = 18L, amplitude = 95)
        HapticStrength.Medium -> HapticPulse(durationMs = 25L, amplitude = 165)
        HapticStrength.Strong -> HapticPulse(durationMs = 34L, amplitude = 245)
    }
}
