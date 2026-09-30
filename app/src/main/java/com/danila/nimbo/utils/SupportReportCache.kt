package com.danila.nimbo.utils

import java.io.File

/** Keep recent exports without recursively deleting or following foreign paths. */
internal fun trimSupportReportCache(root: File, keep: Int = 10) {
    require(keep >= 1)
    val base = root.canonicalFile
    val uuid = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    val known = setOf("report.json", "core-config-redacted.json", "subscription-headers-redacted.json")
    base.listFiles()?.filter { it.isDirectory && uuid.matches(it.name) && it.canonicalFile.parentFile == base }
        ?.sortedByDescending { it.lastModified() }?.drop(keep)?.forEach { dir ->
            dir.listFiles()?.filter { it.name in known && it.isFile && it.canonicalFile.parentFile == dir.canonicalFile }
                ?.forEach { it.delete() }
            if (dir.listFiles()?.isEmpty() == true) dir.delete()
        }
}
