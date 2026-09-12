package com.danila.nimbo.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomIconShapeTest {

    @Test
    fun `existing persisted shape indices stay compatible`() {
        assertEquals(CustomIconShape.SQUIRCLE, CustomIconShape.fromIndex(0))
        assertEquals(CustomIconShape.ROUNDED, CustomIconShape.fromIndex(1))
        assertEquals(CustomIconShape.CIRCLE, CustomIconShape.fromIndex(2))
    }

    @Test
    fun `all android style shapes round trip through their stored index`() {
        assertTrue(CustomIconShape.entries.size >= 6)
        CustomIconShape.entries.forEachIndexed { index, shape ->
            assertEquals(shape, CustomIconShape.fromIndex(index))
        }
    }

    @Test
    fun `invalid stored index falls back to squircle`() {
        assertEquals(CustomIconShape.SQUIRCLE, CustomIconShape.fromIndex(-1))
        assertEquals(CustomIconShape.SQUIRCLE, CustomIconShape.fromIndex(Int.MAX_VALUE))
    }
}
