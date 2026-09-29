package com.danila.nimbo.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class XrayVersionFormatterTest {

    @Test
    fun format_extractsVersionFromNoisyNativeString() {
        val result = XrayVersionFormatter.format("\u0000Xray-core v26.3.27\u0007 build")

        assertEquals("26.3.27", result)
    }

    @Test
    fun format_returnsDashWhenVersionIsMissing() {
        val result = XrayVersionFormatter.format("core-ready")

        assertEquals("—", result)
    }
}
