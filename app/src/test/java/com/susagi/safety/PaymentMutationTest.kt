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
}
