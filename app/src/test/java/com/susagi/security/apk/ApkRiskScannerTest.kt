package com.susagi.security.apk

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ApkRiskScannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testAnalyzeApkUrl_directApkLink_flagsHighSeverity() = runBlocking {
        val scanner = ApkRiskScanner(null)
        val report = scanner.analyzeApkUrl("https://fake-bank-support.top/sbi_kyc_update.apk")

        assertEquals("HIGH", report.severity)
        assertTrue("Risk score must be >= 75", report.riskScore >= 75)
        assertTrue("Tactics must include unverified_apk_sideload",
            report.detectedTactics.contains("unverified_apk_sideload") || report.detectedTactics.contains("software"))
        assertTrue("Explanation must warn against sideloading",
            report.plainExplanation.contains(".apk") || report.plainExplanation.contains("Android"))
    }

    @Test
    fun testAnalyzeApkUrl_benignUrl_flagsLow() = runBlocking {
        val scanner = ApkRiskScanner(null)
        val report = scanner.analyzeApkUrl("https://google.com/search?q=news")

        assertEquals("LOW", report.severity)
        assertTrue(report.riskScore < 40)
    }

    @Test
    fun testAnalyzeLocalApk_missingFile_returnsUnsupportedGracefully() {
        val scanner = ApkRiskScanner(null)
        val missing = File("non_existent_package.apk")
        val report = scanner.analyzeLocalApk(missing)

        assertFalse(report.isSupported)
        assertEquals("REVIEW", report.severity)
    }

    @Test
    fun testCriticalPermissionsMap_containsCoreSpywarePermissions() {
        assertTrue(ApkRiskScanner.CRITICAL_PERMISSIONS.containsKey("android.permission.BIND_ACCESSIBILITY_SERVICE"))
        assertTrue(ApkRiskScanner.CRITICAL_PERMISSIONS.containsKey("android.permission.READ_SMS"))
        assertTrue(ApkRiskScanner.CRITICAL_PERMISSIONS.containsKey("android.permission.RECEIVE_SMS"))
        assertTrue(ApkRiskScanner.CRITICAL_PERMISSIONS.containsKey("android.permission.SYSTEM_ALERT_WINDOW"))
        assertTrue(ApkRiskScanner.CRITICAL_PERMISSIONS.containsKey("android.permission.REQUEST_INSTALL_PACKAGES"))
    }
}
