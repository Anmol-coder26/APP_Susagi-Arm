package com.susagi.safety

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SafetyBarrierTest {
    @Test fun authorizationDemandsNeedFinancialContext() = runBlocking {
        assertEquals(Risk.LOW, SafetyBarrier.analyzeMessage("To receive the newsletter, approve the request in settings.").risk)
        assertEquals(Risk.LOW, SafetyBarrier.analyzeMessage("UPI payment received. Scan QR for the product manual.").risk)
        assertEquals(Risk.LOW, SafetyBarrier.analyzeMessage("OTP kisi ko mat batana.").risk)
        assertEquals(Risk.LOW, SafetyBarrier.analyzeMessage("अपना ओटीपी किसी को न बताएं।").risk)
        assertTrue(SafetyBarrier.analyzeMessage("OTP batana please").evidence.any { it.id == Signal.OTP })
        assertTrue(SafetyBarrier.analyzeMessage("UPI refund के लिए approve this mandate").evidence.any { it.id == Signal.UPI })
    }

    @Test fun destinationAndBoundaries() = runBlocking {
        for (host in listOf("axisbank.com","login.axisbank.com","bücher.de","xn--bcher-kva.de"))
            assertEquals(host, Risk.LOW, SafetyBarrier.analyzeUrl("https://$host").risk)
        for (host in listOf("axisbank.com.attacker.test","fakeaxisbank.com","аxisbank.com"))
            assertTrue(host, SafetyBarrier.analyzeUrl("https://$host").categories.contains("impersonation"))
    }
    @Test fun malformedAndUnsupportedCannotProceed() = runBlocking {
        for (url in listOf("", "file:///etc/passwd","javascript:alert(1)","https://a..b","https://bad:abc","https://a.test/%zz","upi://other"))
            assertEquals(url, Action.DO_NOT_OPEN, SafetyBarrier.analyzeUrl(url).action)
    }
    @Test fun linkEvidenceIsScopedToTheDestination() = runBlocking {
        assertEquals(Risk.LOW,SafetyBarrier.analyzeUrl("https://example.org/?help=bit.ly/file.apk").risk)
        val r = SafetyBarrier.analyzeUrl("https://user:secret@example.org/get%2Eapk")
        assertEquals(setOf(Signal.APK,Signal.USERINFO),r.evidence.map { it.id }.toSet())
        assertFalse((r.explanation + r.evidence + r.recommendations).contains("secret"))
        val redirect = SafetyBarrier.analyzeUrl("https://axisbank.com/?next=https%3A%2F%2Ffakeaxisbank.com")
        assertTrue(redirect.evidence.contains(Evidence(Signal.BRAND,"fakeaxisbank.com")))
    }
    @Test fun multipleInputsAndMultipleLinksRetainEvidence() = runBlocking {
        val r = SafetyBarrier.analyze("https://axisbank.com","Refund: approve collect request with UPI PIN. https://ok.test https://other.test/a.apk")
        assertTrue(r.evidence.any { it.id == Signal.UPI })
        assertTrue(r.evidence.contains(Evidence(Signal.APK,"other.test")))
        assertEquals(Action.RECOMMEND_BLOCK,r.action)
    }
    @Test fun warningsAndFollowingDemands() = runBlocking {
        assertEquals(Risk.LOW,SafetyBarrier.analyzeMessage("Never share your OTP. Account credited.").risk)
        assertEquals(Risk.LOW,SafetyBarrier.analyzeMessage("अपना ओटीपी किसी को मत भेजें।").risk)
        assertEquals(Risk.HIGH,SafetyBarrier.analyzeMessage("Never share your OTP. But send your verification code to me.").risk)
        assertEquals(Risk.UNKNOWN,SafetyBarrier.analyzeMessage("x".repeat(33000)).risk)
    }
    @Test fun extractOnlyObservedEntitiesAndKeepReviewDistinct() = runBlocking {
        val result = SafetyBarrier.analyzeUrl("https://bit.ly/story")
        assertEquals(listOf(Evidence(Signal.SHORT,"bit.ly")), result.evidence)
        assertEquals(Action.ASK_CONFIRMATION, result.action)
        assertEquals("OFFLINE_RULES", result.source)
        assertEquals(Risk.LOW, SafetyBarrier.analyzeUrl("https://example.org/help/bit.ly").risk)
        assertEquals(Action.ASK_CONFIRMATION,SafetyBarrier.analyzeUrl("upi://pay?pa=merchant%40bank").action)
        assertEquals(Risk.REVIEW,SafetyBarrier.analyzeUrl("https://192.0.2.44/").risk)
    }
    @Test fun unsupportedNativeClaimsAreRejected() {
        val evidence = listOf(Signal.OTP, Signal.BRAND)
        assertNull(evidenceSelection("ARREST",evidence))
        assertNull(evidenceSelection("",evidence))
        assertNull(evidenceSelection("OTP,OTP",evidence))
        assertNull(evidenceSelection("OTP, transfer funds",evidence))
        assertEquals(listOf(Signal.BRAND,Signal.OTP),evidenceSelection("BRAND, OTP",evidence))
    }
    @Test fun sharedContentPreservesTheFullMessageAndChecksEncodedRedirectKeys() = runBlocking {
        val message = SafetyBarrier.analyze(null, "Update KYC to unblock your account. https://axisbank.com/")
        assertTrue(message.evidence.any { it.id == Signal.KYC })
        assertNull(message.destinationHost)
        val target = SafetyBarrier.analyzeUrl("https://axisbank.com/?%6eext=https%3A%2F%2Ffakeaxisbank.com")
        assertEquals("axisbank.com", target.destinationHost)
        assertEquals(Action.RECOMMEND_BLOCK, target.action)
        assertTrue(target.evidence.contains(Evidence(Signal.BRAND, "fakeaxisbank.com")))
    }

    @Test fun sharedDestinationsKeepIndividualVerdictsAndOriginalPaths() = runBlocking {
        val urls = listOf("https://axisbank.com/info?q=send+otp", "https://fakeaxisbank.com/A%2Fb", "custom://example")
        val result = SafetyBarrier.analyzeMessage(urls.joinToString(" "))
        assertEquals(urls, result.destinations.map { it.originalUrl })
        assertEquals(listOf(Risk.LOW, Risk.HIGH, Risk.UNKNOWN), result.destinations.map { it.assessment.risk })
        assertFalse(result.evidence.any { it.id == Signal.OTP })
        assertEquals(Action.DO_NOT_OPEN, result.destinations.last().assessment.action)
    }

    @Test fun utilityPressureNeedsPaymentEvidence() = runBlocking {
        val link = "https://billing.example/account"
        assertEquals(Risk.LOW, SafetyBarrier.analyzeMessage("Electricity maintenance power cut tomorrow. Details: $link").risk)
        assertTrue(SafetyBarrier.analyzeMessage("Electricity disconnection tonight. Pay now at $link").evidence.any { it.id == Signal.UTILITY })
    }

    @Test fun sharedSchemesAndIncompleteUrlsRemainVisible() = runBlocking {
        val input = "http://bank.example tel:123 mailto:help@example.org https:// http://bank.example"
        val items = SafetyBarrier.analyzeMessage(input).destinations
        assertEquals(4, items.size)
        assertEquals(listOf(Risk.REVIEW, Risk.UNKNOWN, Risk.UNKNOWN, Risk.UNKNOWN), items.map { it.assessment.risk })
        assertEquals(Signal.HTTP, items.first().assessment.evidence.single().id)
        assertEquals("https://", items.last().originalUrl)
    }

    @Test fun excessiveRedirectParametersRequireSeparateInspection() = runBlocking {
        val query = List(9) { "next=/help" }.joinToString("&")
        assertEquals(Action.DO_NOT_OPEN, SafetyBarrier.analyzeUrl("https://bank.example/?$query").action)
    }

    @Test fun hindiGuidanceReflectsOnlyDetectedEvidence() = runBlocking {
        val warning = SafetyBarrier.analyzeMessage("Never share your OTP.").hindiGuidance().single()
        assertTrue(warning.contains("कोई चेतावनी संकेत नहीं मिला"))
        val demand = SafetyBarrier.analyzeMessage("send OTP 123456").hindiGuidance().single()
        assertTrue(demand.contains("कोड अपने पास रखें"))
        assertFalse(demand.contains("123456"))
    }

    @Test fun missingModelReturnsUnavailable() = runBlocking {
        val result = LocalEvidenceExplainer(File("nonexistent-model-directory")).explain(SafetyBarrier.analyzeMessage("send OTP"))
        assertTrue(result is ExplanationResult.Unavailable)
    }
}
