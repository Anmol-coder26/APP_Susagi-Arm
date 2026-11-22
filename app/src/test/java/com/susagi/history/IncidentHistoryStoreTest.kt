package com.susagi.history

import com.susagi.safety.Assessment
import com.susagi.safety.Risk
import com.susagi.safety.SafetyBarrier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class IncidentHistoryStoreTest {

    private lateinit var store: IncidentHistoryStore

    @Before
    fun setUp() {
        store = IncidentHistoryStore(null) // in-memory mode for unit tests
        store.clearAll()
    }

    @Test
    fun testRecordAssessment_masksPotentialOtpAndSensitiveDigits() = runBlocking {
        val assessment = SafetyBarrier.analyzeMessage("Send your verification code 492041 immediately.")
        val record = store.recordAssessment(
            channel = "MESSAGE",
            title = "OTP Harvesting Attempt",
            assessment = assessment,
            rawSnippet = "Send your verification code 492041 immediately.",
            omitRawContent = false
        )

        assertEquals("HIGH", record.severity)
        assertTrue("OTP digits must be masked with asterisks", record.sanitizedSnippet.contains("******"))
        assertFalse("Raw 6-digit OTP must not appear in snippet", record.sanitizedSnippet.contains("492041"))
    }

    @Test
    fun testRecordAssessment_respectsOmitRawContentToggle() = runBlocking {
        val assessment = SafetyBarrier.analyzeMessage("Hello mom, send money")
        val record = store.recordAssessment(
            channel = "MESSAGE",
            title = "Family Message",
            assessment = assessment,
            rawSnippet = "Hello mom, send money",
            omitRawContent = true
        )

        assertTrue(record.rawContentOmitted)
        assertTrue(record.sanitizedSnippet.contains("Content omitted by user privacy setting"))
    }

    @Test
    fun testHistoryLifecycle_addDeleteClear() = runBlocking {
        val assessment1 = SafetyBarrier.analyzeUrl("https://ax1sbank.example/login")
        val rec1 = store.recordAssessment("LINK", "Brand Phish", assessment1, "https://ax1sbank.example/login")

        val assessment2 = SafetyBarrier.analyzeMessage("Never share your password")
        val rec2 = store.recordAssessment("MESSAGE", "Benign Control", assessment2, "Never share your password")

        val list1 = store.getAllRecords()
        assertEquals(2, list1.size)
        assertEquals(rec2.id, list1[0].id) // Most recent first

        store.deleteRecord(rec1.id)
        val list2 = store.getAllRecords()
        assertEquals(1, list2.size)
        assertEquals(rec2.id, list2[0].id)

        store.clearAll()
        assertTrue(store.getAllRecords().isEmpty())
    }

    @Test
    fun testExportSanitizedReport_formatsOfficialHelpline() = runBlocking {
        val assessment = SafetyBarrier.analyzeMessage("Police warrant against you. Transfer money immediately to avoid arrest.")
        store.recordAssessment("MESSAGE", "Arrest Extortion", assessment, "Police warrant against you. Transfer money immediately to avoid arrest.")

        val report = store.exportSanitizedReport()
        assertTrue("Report must cite 1930", report.contains("1930"))
        assertTrue("Report must cite cybercrime.gov.in", report.contains("cybercrime.gov.in"))
        assertTrue("Report must include channel", report.contains("Channel: MESSAGE"))
        assertTrue("Report must include threat severity", report.contains("[HIGH]"))
    }
}
