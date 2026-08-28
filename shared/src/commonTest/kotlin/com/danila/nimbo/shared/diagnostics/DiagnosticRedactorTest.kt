package com.danila.nimbo.shared.diagnostics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticRedactorTest {
    @Test
    fun removesSecretsBeforeAnEventCanBeStored() {
        val raw = "https://user:pass@example.com/sub/a8902d13-8df2-4764-9720-9c8ca12b6a56?token=secret from 10.20.30.40 Bearer abc.def"
        val safe = DiagnosticRedactor.redact(raw)

        assertFalse("user:pass" in safe)
        assertFalse("a8902d13" in safe)
        assertFalse("secret" in safe)
        assertFalse("10.20.30.40" in safe)
        assertFalse("abc.def" in safe)
        assertTrue("/sub/***" in safe)
    }
}
