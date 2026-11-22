package com.susagi.safety

import java.net.URLEncoder
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AdversarialBarrierTest {
    @Test fun directorateImpersonationNeedsCoercion() = runBlocking {
        for (message in listOf("ED officer here, transfer the funds immediately",
            "प्रवर्तन निदेशालय से बोल रहा हूँ, पैसे भेजो")) {
            assertTrue(SafetyBarrier.analyzeMessage(message).evidence.any { it.id == Signal.ARREST })
        }
        for (message in listOf("Enforcement Directorate published its annual report.",
            "Please pay the special ed tuition fee.", "ED officer spoke at the seminar.")) {
            assertFalse(SafetyBarrier.analyzeMessage(message).evidence.any { it.id == Signal.ARREST })
        }
    }

    @Test fun authorityEvidenceNeedsARelatedDemand() = runBlocking {
        val separate = SafetyBarrier.analyzeMessage("Police station opens at nine. Pay your school fee at the office.")
        assertFalse(separate.evidence.any { it.id == Signal.ARREST })
        val coercion = SafetyBarrier.analyzeMessage("There is a warrant for your arrest. Transfer funds now.")
        assertTrue(coercion.evidence.any { it.id == Signal.ARREST })
        assertEquals(Risk.HIGH, SafetyBarrier.analyzeMessage("You are in custody. Do not tell anyone.").risk)
    }

    @Test(timeout = 30000) fun seededEvidenceMutations() = runBlocking {
        val random = Random(15015)
        val invisible = listOf('\u200b', '\ufeff', '\u2060', '\u00ad')
        repeat(96) {
            val word = "refund".let { token ->
                val pos = random.nextInt(1, token.length)
                token.take(pos) + invisible.random(random) + token.drop(pos)
            }
            val message = "$word paane ke liye QR scan karo"
            val mixed = SafetyBarrier.analyzeMessage("$message https://axisbank.com https://fakeaxisbank.com")
            assertEquals(listOf(Risk.LOW, Risk.HIGH), mixed.destinations.map { it.assessment.risk })
            assertTrue(mixed.evidence.any { it.id == Signal.UPI })
            assertEquals(mixed.evidence.distinct(), mixed.evidence)
            assertTrue(mixed.hindiGuidance().all { it.isNotBlank() })
            assertTrue(mixed.uncertainty.isNotBlank())
            assertEquals(Risk.LOW, SafetyBarrier.analyzeMessage("Never scan a QR to receive a $word").risk)
        }
    }

    @Test(timeout = 30000) fun boundedDestinationMutations() = runBlocking {
        val random = Random(1516)
        repeat(48) {
            val brand = listOf("ax1sbank", "аxisbank", "axisbank").random(random)
            var url = "https://$brand.example/login"
            repeat(random.nextInt(1, 4)) {
                url = "https://relay.example/?next=" + URLEncoder.encode(url, "UTF-8")
            }
            assertEquals(Risk.HIGH, SafetyBarrier.analyzeUrl(url).risk)
            assertEquals(Risk.UNKNOWN, SafetyBarrier.analyzeUrl("https://bad..host/%" + random.nextInt(10)).risk)
        }
        assertEquals(Risk.UNKNOWN, SafetyBarrier.analyzeUrl(" ".repeat(65536) + "https://axisbank.com").risk)
        assertEquals(Risk.UNKNOWN, SafetyBarrier.analyzeMessage("a".repeat(65536)).risk)
    }

    @Test fun payoutConditionAndContrastingDemands() = runBlocking {
        val request = "To release your balance send INR 350"
        assertEquals(Risk.HIGH, SafetyBarrier.analyzeMessage(request).risk)
        assertEquals(Risk.LOW, SafetyBarrier.analyzeMessage("Never send INR 350 to release your balance").risk)
        assertEquals(Risk.LOW, SafetyBarrier.analyzeMessage("Your balance is INR 350 and the payout was released").risk)
        assertEquals(Risk.HIGH, SafetyBarrier.analyzeMessage("Do not pay the caller, instead $request").risk)
    }

    @Test fun externallyLabeledUrlHoldout() = runBlocking {
        val rows = requireNotNull(javaClass.getResourceAsStream("/safety/phiusiil-preview.tsv"))
            .bufferedReader().use { it.readLines() }.filterNot { it.startsWith("#") }.drop(1)
        val counts = IntArray(4) // TP, FP, TN, FN at HIGH threshold
        val elapsed = mutableListOf<Long>()
        var review = 0
        for (row in rows) {
            val (_, label, url) = row.split('\t')
            require(label == "phishing" || label == "legitimate")
            val start = System.nanoTime()
            val result = SafetyBarrier.analyzeUrl(url)
            elapsed += System.nanoTime() - start
            if (result.risk in listOf(Risk.UNKNOWN, Risk.REVIEW)) review++
            val positive = label == "phishing"
            val high = result.risk == Risk.HIGH
            counts[if (positive && high) 0 else if (high) 1 else if (positive) 3 else 2]++
        }
        assertEquals(16, counts.sum())
        val (tp, fp, tn, fn) = counts
        fun ratio(a: Int, b: Int) = if (b == 0) Double.NaN else a.toDouble() / b
        println("PhiUSIIL preview TP=$tp FP=$fp TN=$tn FN=$fn review=$review " +
            "precision=${ratio(tp,tp+fp)} recall=${ratio(tp,tp+fn)} F1=${ratio(2*tp,2*tp+fp+fn)} " +
            "max_us=${elapsed.maxOrNull()!! / 1000}")
    }
}
