package com.danila.nimbo.utils

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

class SupportReportCacheTest {
    @Test fun onlyOlderOwnedReportsAreTrimmed() {
        val root = Files.createTempDirectory("nimbo-report-test").toFile()
        try {
            val reports = (0..11).map { i ->
                File(root, UUID.randomUUID().toString()).apply {
                    mkdirs()
                    File(this, "report.json").writeText("{}")
                    setLastModified(10_000L + i)
                }
            }
            val unrelated = File(root, "other-files").apply { mkdirs() }
            File(unrelated, "report.json").writeText("do not delete")
            trimSupportReportCache(root)
            assertFalse(reports[0].exists()); assertFalse(reports[1].exists())
            assertTrue(reports.drop(2).all { File(it, "report.json").isFile })
            assertTrue(File(unrelated, "report.json").isFile)
        } finally { root.deleteRecursively() }
    }

    @Test fun unexpectedFilesAreNeverDeleted() {
        val root = Files.createTempDirectory("nimbo-report-test").toFile()
        try {
            val older = File(root, UUID.randomUUID().toString()).apply { mkdirs() }
            val preserve = File(older, "unrelated.txt").apply { writeText("keep") }
            older.setLastModified(1)
            File(root, UUID.randomUUID().toString()).apply { mkdirs(); setLastModified(2) }
            trimSupportReportCache(root, keep = 1)
            assertEquals("keep", preserve.readText())
        } finally { root.deleteRecursively() }
    }
}
