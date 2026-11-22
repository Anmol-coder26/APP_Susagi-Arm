package com.guardian.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrScannerTest {

    @Test
    fun testValidUrlRouting() {
        val verdict = QrScannerActivity.evaluateQrContent("https://axisbank.com/support/helpdesk")
        assertTrue("Must be identified as URL", verdict.isUrl)
        assertFalse("Must not be identified as UPI", verdict.isUpi)
        assertEquals("Valid known domain should not trigger high risk", RiskStatus.Low, verdict.status)
        assertTrue(verdict.title.contains("NO KNOWN LOCAL THREAT"))
    }

    @Test
    fun testPunycodeUrlRouting() {
        val verdict = QrScannerActivity.evaluateQrContent("https://xn--sbi-bank-50a.com/login")
        assertTrue("Must be identified as URL", verdict.isUrl)
        assertTrue("Punycode/homoglyph must trigger HIGH or Suspicious", verdict.status == RiskStatus.High || verdict.status == RiskStatus.Suspicious)
    }

    @Test
    fun testUpiPaymentRouting() {
        val upiPayload = "upi://pay?pa=grocer@icici&pn=Supermarket&am=249.50&cu=INR&tn=Groceries"
        val verdict = QrScannerActivity.evaluateQrContent(upiPayload)
        assertTrue("Must be identified as UPI", verdict.isUpi)
        assertFalse("Must not be identified as plain web URL", verdict.isUrl)
        assertEquals("Standard UPI must be marked Suspicious/Unverified, never auto-trusted", RiskStatus.Suspicious, verdict.status)
        assertEquals("Unverified Payee: Supermarket (grocer@icici)", verdict.parsedPayee)
        assertEquals("₹249.50", verdict.parsedAmount)
        assertEquals("Groceries", verdict.parsedNote)
    }

    @Test
    fun testReverseDebitTrapRouting() {
        val trapPayload = "upi://pay?pa=cashback@desk&pn=Cashback%20Desk&am=2000.00&cu=INR&tn=Approve%20to%20receive%20cashback"
        val verdict = QrScannerActivity.evaluateQrContent(trapPayload)
        assertTrue("Must be identified as UPI", verdict.isUpi)
        assertEquals("Reverse debit trap must be HIGH risk", RiskStatus.High, verdict.status)
        assertTrue("Title must indicate fraudulent reverse UPI", verdict.title.contains("FRAUDULENT REVERSE UPI"))
    }

    @Test
    fun testMalformedPaymentUriRouting() {
        val duplicatePayload = "upi://pay?pa=legit@icici&pa=attacker@ybl&am=100.00&cu=INR"
        val verdict = QrScannerActivity.evaluateQrContent(duplicatePayload)
        assertTrue("Must be identified as UPI", verdict.isUpi)
        assertEquals("Duplicate parameter collision must be HIGH risk", RiskStatus.High, verdict.status)
        assertTrue("Title must indicate invalid or tampered UPI", verdict.title.contains("INVALID OR TAMPERED UPI"))
    }

    @Test
    fun testUnsupportedSchemeRouting() {
        val customScheme = "intent://scan/#Intent;scheme=myscheme;package=com.evil;end"
        val verdict = QrScannerActivity.evaluateQrContent(customScheme)
        assertFalse("Must not be identified as standard URL", verdict.isUrl)
        assertFalse("Must not be identified as UPI", verdict.isUpi)
        assertEquals("Custom scheme must trigger caution", RiskStatus.Suspicious, verdict.status)
        assertTrue("Title must indicate unconventional scheme", verdict.title.contains("UNCONVENTIONAL SCHEME"))
    }

    @Test
    fun testPlainTextRouting() {
        val plainText = "Order ID: 948204, Table 12, Guest 2"
        val verdict = QrScannerActivity.evaluateQrContent(plainText)
        assertFalse("Must not be identified as URL", verdict.isUrl)
        assertFalse("Must not be identified as UPI", verdict.isUpi)
        assertEquals("Benign plain text must be LOW risk", RiskStatus.Low, verdict.status)
        assertTrue("Title must indicate scanned text", verdict.title.contains("SCANNED TEXT"))
    }

    @Test
    fun testBlankPayloadRouting() {
        val blankVerdict = QrScannerActivity.evaluateQrContent("   ")
        assertFalse("Blank payload is not a URL", blankVerdict.isUrl)
        assertFalse("Blank payload is not UPI", blankVerdict.isUpi)
        assertEquals("Blank payload is LOW risk", RiskStatus.Low, blankVerdict.status)
        assertTrue("Title must indicate empty QR payload", blankVerdict.title.contains("EMPTY QR PAYLOAD"))
    }

    @Test
    fun testRepeatedScanConsistency() {
        val payload = "https://example.com/menu.pdf"
        val first = QrScannerActivity.evaluateQrContent(payload)
        val second = QrScannerActivity.evaluateQrContent(payload)
        assertEquals(first.status, second.status)
        assertEquals(first.title, second.title)
        assertEquals(first.isUrl, second.isUrl)
    }
}
