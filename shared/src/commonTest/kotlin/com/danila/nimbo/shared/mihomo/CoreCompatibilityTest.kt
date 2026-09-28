package com.danila.nimbo.shared.mihomo

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CoreCompatibilityTest {
    @Test
    fun nativeFormatsRequireTheirOwnCoreAndNeverClaimRuntimeSupport() {
        val required = mapOf(
            NativeConfigurationFormat.XRAY_JSON to NimboCore.XRAY,
            NativeConfigurationFormat.AMNEZIAWG_INI to NimboCore.AWG,
            NativeConfigurationFormat.MIHOMO_YAML to NimboCore.MIHOMO,
        )
        required.forEach { (format, expected) ->
            assertEquals(expected, CoreCompatibility.requiredCore(format))
            NimboCore.entries.forEach { core ->
                assertEquals(
                    if (core == expected) CoreCompatibilityDecision.REQUIRES_NATIVE_VALIDATION else CoreCompatibilityDecision.INCOMPATIBLE,
                    CoreCompatibility.assess(format, core),
                )
            }
        }
    }

    @Test
    fun completeTextSurvivesSerializationAndRejectedPreflight() {
        val raw = "\uFEFF# comment\r\ndefaults: &defaults {interval: 300}\r\nproxy-groups:\r\n  - {name: Auto, type: url-test, <<: *defaults, include-all: true}\r\nrule-providers: {sites: {type: http, behavior: domain}}\r\ndns: {enable: true}\r\nrules: [MATCH,Auto]\r\nfuture-field: {keep: secret-value}\r\n"
        val document = MihomoDocument(
            originalText = raw,
            graph = MihomoGraph(groups = listOf(MihomoGroup("Auto", "url-test", includeAll = true))),
            unsupportedFeatures = listOf(MihomoUnsupportedFeature("future-field", "Unsupported by the pinned runtime")),
        )
        val restored = Json.decodeFromString<MihomoDocument>(Json.encodeToString(document))
        assertEquals(document, restored)
        for (core in NimboCore.entries) {
            val result = MihomoPreflight.inspect(restored, core)
            assertEquals(MihomoPreflightDecision.REJECTED, result.decision)
            assertSame(restored, result.document)
            assertEquals(raw, result.document.originalText)
            assertTrue(result.issues.any { it.code == MihomoIssueCode.UNSUPPORTED_FEATURE })
        }
        assertFalse(document.toString().contains("secret-value"))
    }

    @Test
    fun wrongCoreIsRejectedWithoutExtractingTheVlessProxy() {
        val document = MihomoDocument(
            "proxies: [{name: node, type: vless}]\nproxy-groups: [{name: all, type: select, proxies: [node]}]",
            MihomoGraph(listOf(MihomoProxy("node", "vless")), listOf(MihomoGroup("all", "select", listOf("node")))),
        )
        for (core in listOf(NimboCore.XRAY, NimboCore.AWG)) {
            val result = MihomoPreflight.inspect(document, core)
            assertEquals(listOf(MihomoIssueCode.INCOMPATIBLE_CORE), result.issues.map { it.code })
            assertSame(document, result.document)
        }
        assertEquals(MihomoPreflightDecision.REQUIRES_NATIVE_VALIDATION, MihomoPreflight.inspect(document, NimboCore.MIHOMO).decision)
    }

    @Test
    fun uninspectedOrUnknownProxyOptionsCannotEstablishSupport() {
        val document = MihomoDocument("unknown-yaml: retained")
        assertEquals(MihomoPreflightDecision.REQUIRES_NATIVE_VALIDATION, MihomoPreflight.inspect(document, NimboCore.MIHOMO).decision)
        val future = document.copy(graph = MihomoGraph(proxies = listOf(MihomoProxy("future", "future-protocol"))))
        assertEquals(MihomoPreflightDecision.REQUIRES_NATIVE_VALIDATION, MihomoPreflight.inspect(future, NimboCore.MIHOMO).decision)
    }

    @Test
    fun graphErrorsRejectTheWholeDocumentAndBlankDocumentsAreRejected() {
        val document = MihomoDocument("original", MihomoGraph(groups = listOf(MihomoGroup("broken", "select", listOf("missing")))))
        val result = MihomoPreflight.inspect(document, NimboCore.MIHOMO)
        assertEquals(MihomoPreflightDecision.REJECTED, result.decision)
        assertSame(document, result.document)
        assertEquals(listOf(MihomoIssueCode.EMPTY_DOCUMENT), MihomoPreflight.inspect(MihomoDocument(" \n"), NimboCore.MIHOMO).issues.map { it.code })
    }
}
