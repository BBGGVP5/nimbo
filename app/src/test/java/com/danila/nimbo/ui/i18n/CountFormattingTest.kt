package com.danila.nimbo.ui.i18n

import org.junit.Assert.assertEquals
import org.junit.Test

class CountFormattingTest {

    @Test
    fun `server count uses correct Russian forms`() {
        val expected = mapOf(
            0 to "0 серверов",
            1 to "1 сервер",
            2 to "2 сервера",
            4 to "4 сервера",
            5 to "5 серверов",
            11 to "11 серверов",
            14 to "14 серверов",
            21 to "21 сервер",
            22 to "22 сервера",
            25 to "25 серверов",
            91 to "91 сервер",
            101 to "101 сервер",
            111 to "111 серверов"
        )

        expected.forEach { (count, text) ->
            assertEquals(text, serverCountRu(count))
        }
    }

    @Test
    fun `subscription count uses correct Russian forms`() {
        assertEquals("1 подписка", subscriptionCountRu(1))
        assertEquals("2 подписки", subscriptionCountRu(2))
        assertEquals("5 подписок", subscriptionCountRu(5))
        assertEquals("11 подписок", subscriptionCountRu(11))
        assertEquals("21 подписка", subscriptionCountRu(21))
    }

    @Test
    fun `English counts use singular only for one`() {
        assertEquals("0 servers", serverCountEn(0))
        assertEquals("1 server", serverCountEn(1))
        assertEquals("2 servers", serverCountEn(2))
        assertEquals("1 subscription", subscriptionCountEn(1))
        assertEquals("2 subscriptions", subscriptionCountEn(2))
    }
}
