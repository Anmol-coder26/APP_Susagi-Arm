package com.susagi.safety

import kotlinx.coroutines.runBlocking
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentMutationTest {
    @Test(timeout = 15000) fun duplicateAndEncodedFields() = runBlocking {
        val random = Random(1702)
        repeat(64) { trial ->
            val fields = listOf("pa=stall%40bank", "cu=INR", "pn=Shop", "am=42%2E50").shuffled(random)
            val url = "upi://pay?" + fields.joinToString("&")
            assertEquals("seed=1702 trial=$trial", Risk.REVIEW, SafetyBarrier.analyzeUrl(url).risk)
            for (extra in listOf("%70a=other%40bank", "CU=USD", "pn=Different", "am=1")) {
                assertEquals("trial=$trial field=$extra", Action.DO_NOT_OPEN, SafetyBarrier.analyzeUrl("$url&$extra").action)
            }
        }
    }

    @Test fun upiInspectionValidPayload() {
        val uri = "upi://pay?pa=merchant%40okhdfcbank&pn=Merchant%20Store&am=150.00&cu=INR&tn=Order%20123"
        val result = PaymentFields.inspectUri(uri)
        assertEquals(true, result.isValid)
        assertEquals("merchant@okhdfcbank", result.payeeVpa)
        assertEquals("Merchant Store", result.payeeName)
        assertEquals("150.00", result.amount)
        assertEquals("INR", result.currency)
        assertEquals("Order 123", result.note)
        assertEquals(false, result.isAdvanceFeeOrReverseDebit)
        assertEquals("Unverified Payee: Merchant Store (merchant@okhdfcbank)", result.unverifiedDisplayName)
    }

    @Test fun upiInspectionReverseDebitTrap() {
        val uri = "upi://pay?pa=cashback-rewards%40upi&pn=Refund%20Desk&am=4999.00&cu=INR&tn=Claim%20Cashback%20Refund"
        val result = PaymentFields.inspectUri(uri)
        assertEquals(true, result.isValid)
        assertEquals(true, result.isAdvanceFeeOrReverseDebit)
        org.junit.Assert.assertTrue(result.riskReason?.contains("Reverse UPI debit trap") == true)
    }

    @Test fun upiInspectionDuplicateParameterCollision() {
        val uri = "upi://pay?pa=alice%40bank&pa=bob%40bank&am=10.00&cu=INR"
        val result = PaymentFields.inspectUri(uri)
        assertEquals(false, result.isValid)
        assertEquals(listOf("pa"), result.duplicateKeys)
        org.junit.Assert.assertTrue(result.riskReason?.contains("Conflicting or duplicate parameters") == true)
    }

    @Test fun upiInspectionInvalidCurrency() {
        val uri = "upi://pay?pa=alice%40bank&am=10.00&cu=USD"
        val result = PaymentFields.inspectUri(uri)
        assertEquals(false, result.isValid)
        org.junit.Assert.assertTrue(result.riskReason?.contains("Unsupported currency") == true)
    }
}
