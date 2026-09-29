package com.danila.nimbo.ui.components

import org.junit.Assert.*
import org.junit.Test

class PingTimeoutInputTest {
    @Test fun `seconds stay seconds including boundaries`() {
        assertEquals(1, parsePingTimeoutSeconds("1"))
        assertEquals(3, parsePingTimeoutSeconds("3"))
        assertEquals(10, parsePingTimeoutSeconds("10"))
    }
    @Test fun `empty invalid and millisecond drafts do not save`() {
        listOf("", " ", "0", "11", "3000", "-1", "+3", "1.5", "3 с", "abc", "999999999999").forEach {
            assertNull(it, parsePingTimeoutSeconds(it))
        }
    }
    @Test fun `pasted surrounding whitespace and leading zero are harmless`() {
        assertEquals(3, parsePingTimeoutSeconds(" 3 "))
        assertEquals(3, parsePingTimeoutSeconds("03"))
    }
}
