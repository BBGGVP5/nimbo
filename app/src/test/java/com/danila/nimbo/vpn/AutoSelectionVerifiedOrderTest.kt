package com.danila.nimbo.vpn

import com.danila.nimbo.model.Server
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Порядок перебора в автоподборе.
 *
 * Узлы-«обходы» живут на подменном адресе (`google.com:456`): TCP-соединение до
 * него принимает кто угодно на пути, поэтому пинг у мёртвого узла выглядит
 * отлично. Раньше именно по этой цифре строилась очередь, и автоподбор упорно
 * лез в неработающие «Обход 1» и «Обход 3». Теперь решает исход сквозной
 * проверки.
 */
class AutoSelectionVerifiedOrderTest {

    private fun server(name: String, ping: Int?) = Server(
        name = name,
        host = "google.com",
        port = 456,
        uuid = "11111111-1111-4111-8111-111111111111",
        protocol = "vless",
        profileUrl = "https://example.org/sub",
        ping = ping
    )

    private val bypass1 = server("Обход 1", ping = 10)
    private val bypass2 = server("Обход 2", ping = 400)
    private val bypass3 = server("Обход 3", ping = 20)

    private val all = listOf(bypass1, bypass2, bypass3)

    private fun verified(vararg pairs: Pair<Server, Pair<Int, Boolean>>): Map<String, VerifiedLatency> =
        pairs.associate { (server, result) ->
            server.pingMeasurementKey() to VerifiedLatency(
                latencyMs = result.first,
                ok = result.second,
                checkedAtMs = 1_000_000L
            )
        }

    @Test
    fun withoutVerification_fallsBackToPing() {
        val ordered = AutoSelectionPolicy.order(all, restricted = false, verified = emptyMap(), nowMs = 1_000_000L)
        assertEquals(listOf("Обход 1", "Обход 3", "Обход 2"), ordered.map { it.name })
    }

    @Test
    fun verifiedWorkingServerGoesFirstDespiteWorseTcpPing() {
        // Обход 2 по TCP «медленнее» всех, но это единственный реально рабочий.
        val ordered = AutoSelectionPolicy.order(
            all,
            restricted = false,
            verified = verified(bypass2 to (200 to true)),
            nowMs = 1_000_000L
        )
        assertEquals("Обход 2", ordered.first().name)
    }

    @Test
    fun verifiedDeadServersSinkToTheBottom() {
        val ordered = AutoSelectionPolicy.order(
            all,
            restricted = false,
            verified = verified(
                bypass1 to (-1 to false),
                bypass3 to (-1 to false),
                bypass2 to (200 to true)
            ),
            nowMs = 1_000_000L
        )
        assertEquals(listOf("Обход 2", "Обход 1", "Обход 3"), ordered.map { it.name })
    }

    @Test
    fun staleVerificationIsIgnored() {
        val stale = 1_000_000L + VerifiedLatencyStore.FRESH_WINDOW_MS + 1L
        val ordered = AutoSelectionPolicy.order(
            all,
            restricted = false,
            verified = verified(bypass2 to (200 to true)),
            nowMs = stale
        )
        // Протухший результат не считается: возвращаемся к сортировке по пингу.
        assertEquals("Обход 1", ordered.first().name)
    }

    @Test
    fun representativeLatencyIsMedianOfSuccessfulProbes() {
        assertEquals(120, AutoSelectionPolicy.representativeLatency(listOf(100, 120, 900)))
        // Провалившиеся цели не участвуют.
        assertEquals(150, AutoSelectionPolicy.representativeLatency(listOf(-1, 150, -1)))
        assertEquals(-1, AutoSelectionPolicy.representativeLatency(listOf(-1, -1, -1)))
    }
}
