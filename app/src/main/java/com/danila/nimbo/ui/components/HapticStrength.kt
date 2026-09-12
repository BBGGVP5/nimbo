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

enum class HapticStyle(val persistedValue: Int) {
    Soft(0),
    Crisp(1),
    Double(2),
    Wave(3),
    Pulse(4),
    Spring(5);

    companion object {
        fun fromPersistedValue(value: Int): HapticStyle =
            entries.firstOrNull { it.persistedValue == value } ?: Crisp
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

internal data class HapticPattern(
    val timings: LongArray,
    val amplitudes: IntArray
) {
    init {
        require(timings.size == amplitudes.size)
        require(timings.isNotEmpty())
    }
}

internal object HapticPatternPolicy {
    fun tick(strength: HapticStrength, style: HapticStyle): HapticPattern =
        pattern(HapticPulsePolicy.tick(strength), style, confirmation = false)

    fun confirmation(strength: HapticStrength, style: HapticStyle): HapticPattern =
        pattern(HapticPulsePolicy.confirmation(strength), style, confirmation = true)

    private fun pattern(
        pulse: HapticPulse,
        style: HapticStyle,
        confirmation: Boolean
    ): HapticPattern = when (style) {
        HapticStyle.Soft -> HapticPattern(
            timings = longArrayOf(0L, (pulse.durationMs * 1.25f).toLong().coerceAtLeast(8L)),
            amplitudes = intArrayOf(0, (pulse.amplitude * 0.58f).toInt().coerceIn(1, 255))
        )

        HapticStyle.Crisp -> HapticPattern(
            timings = longArrayOf(0L, pulse.durationMs),
            amplitudes = intArrayOf(0, pulse.amplitude.coerceIn(1, 255))
        )

        HapticStyle.Double -> {
            val secondAmplitude = (pulse.amplitude * if (confirmation) 0.72f else 0.48f)
                .toInt()
                .coerceIn(1, 255)
            HapticPattern(
                timings = longArrayOf(
                    0L,
                    pulse.durationMs,
                    if (confirmation) 42L else 30L,
                    (pulse.durationMs * if (confirmation) 0.70f else 0.52f)
                        .toLong()
                        .coerceAtLeast(5L)
                ),
                amplitudes = intArrayOf(0, pulse.amplitude.coerceIn(1, 255), 0, secondAmplitude)
            )
        }

        HapticStyle.Wave -> {
            val firstAmplitude = (pulse.amplitude * 0.34f).toInt().coerceIn(1, 255)
            val secondAmplitude = (pulse.amplitude * 0.62f).toInt().coerceIn(1, 255)
            val finalAmplitude = pulse.amplitude.coerceIn(1, 255)
            HapticPattern(
                timings = longArrayOf(
                    0L,
                    (pulse.durationMs * 0.55f).toLong().coerceAtLeast(5L),
                    if (confirmation) 30L else 22L,
                    (pulse.durationMs * 0.75f).toLong().coerceAtLeast(6L),
                    if (confirmation) 28L else 20L,
                    pulse.durationMs
                ),
                amplitudes = intArrayOf(0, firstAmplitude, 0, secondAmplitude, 0, finalAmplitude)
            )
        }

        HapticStyle.Pulse -> {
            val firstAmplitude = pulse.amplitude.coerceIn(1, 255)
            val secondAmplitude = (pulse.amplitude * if (confirmation) 0.82f else 0.66f)
                .toInt()
                .coerceIn(1, 255)
            HapticPattern(
                timings = longArrayOf(
                    0L,
                    (pulse.durationMs * 0.72f).toLong().coerceAtLeast(6L),
                    if (confirmation) 72L else 54L,
                    pulse.durationMs
                ),
                amplitudes = intArrayOf(0, firstAmplitude, 0, secondAmplitude)
            )
        }

        HapticStyle.Spring -> {
            val secondAmplitude = (pulse.amplitude * 0.62f).toInt().coerceIn(1, 255)
            val thirdAmplitude = (pulse.amplitude * 0.34f).toInt().coerceIn(1, 255)
            HapticPattern(
                timings = longArrayOf(
                    0L,
                    pulse.durationMs,
                    if (confirmation) 25L else 18L,
                    (pulse.durationMs * 0.58f).toLong().coerceAtLeast(5L),
                    if (confirmation) 21L else 15L,
                    (pulse.durationMs * 0.38f).toLong().coerceAtLeast(4L)
                ),
                amplitudes = intArrayOf(
                    0,
                    pulse.amplitude.coerceIn(1, 255),
                    0,
                    secondAmplitude,
                    0,
                    thirdAmplitude
                )
            )
        }
    }
}
