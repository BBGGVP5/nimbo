package com.danila.nimbo.shared.diagnostics

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticRedactorTest {
    @Test
    fun removesSecretsBeforeAnEventCanBeStored() {
        val raw = "https://user:pass@example.com/sub/11111111-2222-3333-4444-555555555555?token=secret from 10.20.30.40 Bearer abc.def"
        val safe = DiagnosticRedactor.redact(raw)

        assertFalse("user:pass" in safe)
        assertFalse("11111111" in safe)
        assertFalse("secret" in safe)
        assertFalse("10.20.30.40" in safe)
        assertFalse("abc.def" in safe)
        assertTrue("/sub/***" in safe)
    }
}
