package com.danila.nimbo.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePostInstallPolicyTest {

    @Test
    fun matchingReplacementIsConfirmed() {
        assertTrue(
            UpdatePostInstallPolicy.isConfirmed(
                expectedVersionName = "v1.0.3",
                expectedVersionCode = 4,
                previousPackageTime = 100L,
                installedVersionName = "1.0.3",
                installedVersionCode = 4L,
                installedPackageTime = 200L
            )
        )
    }

    @Test
    fun unchangedPackageTimeIsNotConfirmed() {
        assertFalse(
            UpdatePostInstallPolicy.isConfirmed(
                expectedVersionName = "1.0.3",
                expectedVersionCode = 4,
                previousPackageTime = 100L,
                installedVersionName = "1.0.3",
                installedVersionCode = 4L,
                installedPackageTime = 100L
            )
        )
    }

    @Test
    fun differentVersionIsNotConfirmed() {
        assertFalse(
            UpdatePostInstallPolicy.isConfirmed(
                expectedVersionName = "1.0.3",
                expectedVersionCode = 4,
                previousPackageTime = 100L,
                installedVersionName = "1.0.2",
                installedVersionCode = 4L,
                installedPackageTime = 200L
            )
        )
    }
}
