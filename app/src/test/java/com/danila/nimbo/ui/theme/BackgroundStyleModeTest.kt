package com.danila.nimbo.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class BackgroundStyleModeTest {
    @Test
    fun persistedIndexesRemainCompatibleAndNoneIsAppended() {
        assertEquals(BackgroundStyleMode.MORPHISM, backgroundStyleModeForIndex(0))
        assertEquals(BackgroundStyleMode.BLOSSOM, backgroundStyleModeForIndex(14))
        assertEquals(BackgroundStyleMode.NONE, backgroundStyleModeForIndex(15))
    }

    @Test
    fun newMotionStylesAreAppendedAfterNone() {
        assertEquals(BackgroundStyleMode.RAIN, backgroundStyleModeForIndex(16))
        assertEquals(BackgroundStyleMode.ORBIT, backgroundStyleModeForIndex(17))
    }

    @Test
    fun unknownIndexFallsBackToOriginalStyle() {
        assertEquals(BackgroundStyleMode.MORPHISM, backgroundStyleModeForIndex(-1))
        assertEquals(BackgroundStyleMode.MORPHISM, backgroundStyleModeForIndex(999))
    }

    @Test
    fun paletteIndexesMapToTheirOwnPalette() {
        assertEquals(BackgroundPaletteMode.THEME, backgroundPaletteModeForIndex(0))
        assertEquals(BackgroundPaletteMode.CYBER, backgroundPaletteModeForIndex(2))
        assertEquals(BackgroundPaletteMode.FOREST, backgroundPaletteModeForIndex(11))
    }

    @Test
    fun unknownPaletteIndexFallsBackToTheme() {
        assertEquals(BackgroundPaletteMode.THEME, backgroundPaletteModeForIndex(-3))
        assertEquals(BackgroundPaletteMode.THEME, backgroundPaletteModeForIndex(42))
    }

    @Test
    fun upgradingKeepsTheColourThatUsedToBeBakedIntoTheStyle() {
        // Раньше цвет был частью стиля: «Огонь» = стиль 10. После разделения
        // такой пользователь должен по умолчанию получить палитру «Огонь».
        assertEquals(BackgroundPaletteMode.FIRE, backgroundPaletteModeForIndex(legacyBackgroundPaletteForStyle(10)))
        assertEquals(BackgroundPaletteMode.CYBER, backgroundPaletteModeForIndex(legacyBackgroundPaletteForStyle(8)))
        // Стили без собственного цвета остаются на палитре темы.
        assertEquals(BackgroundPaletteMode.THEME, backgroundPaletteModeForIndex(legacyBackgroundPaletteForStyle(0)))
        assertEquals(BackgroundPaletteMode.THEME, backgroundPaletteModeForIndex(legacyBackgroundPaletteForStyle(6)))
    }
}
