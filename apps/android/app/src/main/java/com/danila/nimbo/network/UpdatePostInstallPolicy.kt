package com.danila.nimbo.network

internal object UpdatePostInstallPolicy {
    fun isConfirmed(
        expectedVersionName: String,
        expectedVersionCode: Int,
        previousPackageTime: Long,
        installedVersionName: String,
        installedVersionCode: Long,
        installedPackageTime: Long
    ): Boolean {
        val versionMatches =
            UpdatePolicy.normalizedVersionTag(installedVersionName) ==
                UpdatePolicy.normalizedVersionTag(expectedVersionName)
        val codeMatches = installedVersionCode >= expectedVersionCode
        val packageWasReplaced = installedPackageTime > previousPackageTime
        return versionMatches && codeMatches && packageWasReplaced
    }
}
