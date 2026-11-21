package com.guardian.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkCheckSecurityTest {

    @Test
    fun testExtractUrlFromText_variousFormats() {
        // Standard https URL embedded in text
        val text1 = "Dear customer, your electricity will be disconnected tonight. Pay immediately at https://electricity-bill-pay.com/update"
        val url1 = LinkCheckActivity.extractUrlFromText(text1)
        assertEquals("https://electricity-bill-pay.com/update", url1)

        // www prefix without scheme
        val text2 = "Check out www.bank-verify.xyz/login"
        val url2 = LinkCheckActivity.extractUrlFromText(text2)
        assertEquals("https://www.bank-verify.xyz/login", url2)

        // Raw URL only
        val text3 = "https://sbi.co.in"
        val url3 = LinkCheckActivity.extractUrlFromText(text3)
        assertEquals("https://sbi.co.in", url3)

        // Text without URL
        val text4 = "Hello, your OTP is 492019. Do not share it."
        val url4 = LinkCheckActivity.extractUrlFromText(text4)
        assertEquals("", url4)
    }

    @Test
    fun testEvaluateLinkSafety_rawIpAddress_flagsHighRisk() {
        val verdict = LinkCheckActivity.evaluateLinkSafety("http://192.168.1.50/banking/login")
        assertEquals(RiskStatus.High, verdict.status)
        assertTrue(verdict.title.contains("IP HOST"))
    }

    @Test
    fun testEvaluateLinkSafety_apkDownload_flagsHighRisk() {
        val verdict = LinkCheckActivity.evaluateLinkSafety("https://unverified-site.com/updates/sbi_quick_support.apk")
        assertEquals(RiskStatus.High, verdict.status)
        assertTrue(verdict.title.contains("MALICIOUS PACKAGE") || verdict.title.contains("APK"))
    }

    @Test
    fun testEvaluateLinkSafety_phishingKeywords_flagsHighRisk() {
        val verdictKyc = LinkCheckActivity.evaluateLinkSafety("https://my-portal.com/login-kyc")
        assertEquals(RiskStatus.High, verdictKyc.status)

        val verdictDigitalArrest = LinkCheckActivity.evaluateLinkSafety("https://notice-court.org/digital-arrest-warrant")
        assertEquals(RiskStatus.High, verdictDigitalArrest.status)

        val verdictRbi = LinkCheckActivity.evaluateLinkSafety("https://funds-release.net/rbi-claim")
        assertEquals(RiskStatus.High, verdictRbi.status)
    }

    @Test
    fun testEvaluateLinkSafety_bankSubdomainSpoofing_flagsHighRisk() {
        val verdict = LinkCheckActivity.evaluateLinkSafety("https://sbi.co.in.fake-portal.com/login")
        assertEquals(RiskStatus.High, verdict.status)
        assertTrue(verdict.title.contains("IMPERSONATION"))
    }

    @Test
    fun testEvaluateLinkSafety_urlShorteners_flagsSuspicious() {
        val verdict = LinkCheckActivity.evaluateLinkSafety("https://bit.ly/secret-offer")
        assertEquals(RiskStatus.Suspicious, verdict.status)
        assertTrue(verdict.title.contains("SHORTENER") || verdict.title.contains("MASKED"))
    }

    @Test
    fun testEvaluateLinkSafety_suspiciousTld_flagsSuspicious() {
        val verdict = LinkCheckActivity.evaluateLinkSafety("https://claim-portal.xyz/verify")
        assertEquals(RiskStatus.Suspicious, verdict.status)
        assertTrue(verdict.title.contains("REGISTRY") || verdict.title.contains("SUSPICIOUS"))
    }

    @Test
    fun testEvaluateLinkSafety_cleanTrustedDomains_flagsLowRisk() {
        val verdict1 = LinkCheckActivity.evaluateLinkSafety("https://www.google.com/search?q=cybercrime")
        assertEquals(RiskStatus.Low, verdict1.status)

        val verdict2 = LinkCheckActivity.evaluateLinkSafety("https://sbi.co.in")
        assertEquals(RiskStatus.Low, verdict2.status)
    }

    @Test
    fun testEvaluateLinkSafety_domainValidation_exactRootAndSubdomains() {
        // 1. Exact trusted root domain
        val vAxisRoot = LinkCheckActivity.evaluateLinkSafety("https://axisbank.com/retail/accounts")
        assertEquals(RiskStatus.Low, vAxisRoot.status)

        // 2. Legitimate subdomain separated by dot
        val vAxisSubdomain = LinkCheckActivity.evaluateLinkSafety("https://login.axisbank.com/secure")
        assertEquals(RiskStatus.Low, vAxisSubdomain.status)

        // 3. Suffix deception (fakeaxisbank.com) - MUST be flagged as brand impersonation
        val vFakeAxis = LinkCheckActivity.evaluateLinkSafety("https://fakeaxisbank.com/login")
        assertEquals(RiskStatus.High, vFakeAxis.status)
        assertTrue("Expected brand impersonation for fakeaxisbank.com, got: ${vFakeAxis.title}",
            vFakeAxis.title.contains("IMPERSONATION"))

        // 4. Subdomain spoofing (axisbank.com.attacker.test) - MUST be flagged
        val vAttackerDomain = LinkCheckActivity.evaluateLinkSafety("https://axisbank.com.attacker.test/auth")
        assertEquals(RiskStatus.High, vAttackerDomain.status)
        assertTrue("Expected brand impersonation for axisbank.com.attacker.test, got: ${vAttackerDomain.title}",
            vAttackerDomain.title.contains("IMPERSONATION"))
    }

    @Test
    fun testEvaluateLinkSafety_punycodeAndUnicodeEvaluation() {
        // 1. Mixed-script homoglyph spoof (pаypal.com with Cyrillic 'а' -> xn--pypal-4ve.com)
        val vHomoglyph = LinkCheckActivity.evaluateLinkSafety("https://xn--pypal-4ve.com/signin")
        assertEquals(RiskStatus.High, vHomoglyph.status)
        assertTrue(vHomoglyph.title.contains("HOMOGLYPH") || vHomoglyph.title.contains("PUNYCODE"))

        // 2. Unicode homoglyph bank impersonation (аxisbank.com with Cyrillic 'а')
        val vUnicodeBankSpoof = LinkCheckActivity.evaluateLinkSafety("https://аxisbank.com/login")
        assertEquals(RiskStatus.High, vUnicodeBankSpoof.status)
        assertTrue(vUnicodeBankSpoof.title.contains("IMPERSONATION") || vUnicodeBankSpoof.title.contains("HOMOGLYPH"))

        // 3. Legitimate national IDN (उदाहरण.भारत -> xn--p1b6ci4b4b.xn--h2brj9c)
        val vLegitNationalIdn = LinkCheckActivity.evaluateLinkSafety("https://xn--p1b6ci4b4b.xn--h2brj9c/home")
        assertEquals(RiskStatus.Low, vLegitNationalIdn.status)
    }

    @Test
    fun testEvaluateLinkSafety_malformedHostnames() {
        // Missing host
        val vNoHost = LinkCheckActivity.evaluateLinkSafety("https:///path/index.html")
        assertEquals(RiskStatus.Suspicious, vNoHost.status)
        assertTrue(vNoHost.title.contains("MALFORMED") || vNoHost.title.contains("EMPTY"))

        // Bare scheme
        val vBareScheme = LinkCheckActivity.evaluateLinkSafety("http://")
        assertEquals(RiskStatus.Suspicious, vBareScheme.status)

        // Leading dot in host
        val vLeadingDot = LinkCheckActivity.evaluateLinkSafety("https://.axisbank.com/login")
        assertEquals(RiskStatus.Suspicious, vLeadingDot.status)

        // Consecutive dots in host
        val vDoubleDot = LinkCheckActivity.evaluateLinkSafety("https://axis..com/login")
        assertEquals(RiskStatus.Suspicious, vDoubleDot.status)
    }

    @Test
    fun testExtractAllUrls_zeroOneAndMultipleLinks() {
        // Zero links
        val zero = LinkCheckActivity.extractAllUrls("Call 9876543210 immediately for CBI digital arrest verification.")
        assertTrue(zero.isEmpty())

        // One link
        val one = LinkCheckActivity.extractAllUrls("Pay electricity bill at https://power-bescom.top/pay right now.")
        assertEquals(1, one.size)
        assertEquals("https://power-bescom.top/pay", one[0])

        // Multiple distinct links
        val multi = LinkCheckActivity.extractAllUrls("Official portal is https://sbi.co.in but phishing clone is at http://sbi-login.xyz/auth. Also check www.google.com")
        assertEquals(3, multi.size)
        assertTrue(multi.contains("https://sbi.co.in"))
        assertTrue(multi.contains("http://sbi-login.xyz/auth"))
        assertTrue(multi.contains("https://www.google.com"))
    }

    @Test
    fun testAssessPayload_caseA_digitalArrest_textOnly() {
        val text = "URGENT: CBI Cyber Crime Branch has issued a digital arrest warrant against you for illegal money laundering. Do not disconnect or police will arrive. Call 9876543210 immediately."
        val payload = SharedPayload.SharedContent(
            rawText = text,
            urls = LinkCheckActivity.extractAllUrls(text)
        )
        val assessment = LinkCheckActivity.assessPayload(payload)

        assertEquals(RiskStatus.High, assessment.status)
        assertTrue(assessment.actualRiskScore >= 70)
        assertEquals("DIGITAL ARREST EXTORTION", assessment.categoryTitle)
        assertTrue("Expected empty URLs for text-only message", assessment.evaluatedUrls.isEmpty())
        assertTrue(assessment.isTextOnly)
        assertTrue(assessment.plainExplanationEn.contains("NEVER conduct arrests"))
        assertTrue(assessment.plainExplanationHi.contains("डिजिटल अरेस्ट"))
        assertTrue("Engine label must attribute to offline rule engine", assessment.engineLabel.contains("Offline Heuristic Rule Engine"))
    }

    @Test
    fun testAssessPayload_caseB_fakeElectricityBill_withPhishingLink() {
        val text = "Dear customer, your electricity bill is overdue and power will be disconnected tonight. Pay immediately at http://power-update-bescom.top/pay"
        val payload = SharedPayload.SharedContent(
            rawText = text,
            urls = LinkCheckActivity.extractAllUrls(text)
        )
        val assessment = LinkCheckActivity.assessPayload(payload)

        assertEquals(RiskStatus.High, assessment.status)
        assertTrue(assessment.actualRiskScore >= 70)
        assertEquals("UTILITY / ELECTRICITY BILL SCAM", assessment.categoryTitle)
        assertEquals(1, assessment.evaluatedUrls.size)
        assertEquals(RiskStatus.Suspicious, assessment.evaluatedUrls[0].status)
        assertTrue(assessment.plainExplanationEn.contains("Power companies never disconnect electricity"))
        assertTrue(assessment.plainExplanationHi.contains("बिजली"))
    }

    @Test
    fun testAssessPayload_caseC_legitimateFamilyMessage() {
        val text = "Hi Papa, reached office safely. Traffic was heavy today. Will call you around 8 PM."
        val payload = SharedPayload.SharedContent(
            rawText = text,
            urls = LinkCheckActivity.extractAllUrls(text)
        )
        val assessment = LinkCheckActivity.assessPayload(payload)

        assertEquals(RiskStatus.Low, assessment.status)
        assertTrue(assessment.actualRiskScore < 35)
        assertEquals("NO KNOWN LOCAL THREAT INDICATORS DETECTED", assessment.categoryTitle)
        assertTrue(assessment.evaluatedUrls.isEmpty())
        assertTrue(assessment.plainExplanationEn.contains("No known local threat indicators detected"))
    }

    @Test
    fun testAssessPayload_caseD_multipleLinks_evaluatesEachIndependently() {
        val text = "Please compare our genuine portal https://sbi.co.in with the fraudulent clone https://xn--pypal-4ve.com/signin"
        val payload = SharedPayload.SharedContent(
            rawText = text,
            urls = LinkCheckActivity.extractAllUrls(text)
        )
        val assessment = LinkCheckActivity.assessPayload(payload)

        assertEquals(2, assessment.evaluatedUrls.size)
        val sbiVerdict = assessment.evaluatedUrls.first { it.url == "https://sbi.co.in" }
        val homoglyphVerdict = assessment.evaluatedUrls.first { it.url.contains("xn--pypal-4ve") }

        assertEquals(RiskStatus.Low, sbiVerdict.status)
        assertEquals(RiskStatus.High, homoglyphVerdict.status)
        assertEquals(RiskStatus.High, assessment.status)
    }

    @Test
    fun testAssessPayload_caseE_emptyOrUnsupportedPayload() {
        val assessment = LinkCheckActivity.assessPayload(SharedPayload.Empty)
        assertEquals(RiskStatus.Low, assessment.status)
        assertEquals(0, assessment.actualRiskScore)
        assertEquals("NO ANALYZABLE CONTENT", assessment.categoryTitle)
        assertTrue(assessment.evaluatedUrls.isEmpty())
        assertTrue(assessment.isTextOnly)
    }

    @Test
    fun testAssessPayload_caseF_actionViewUrl() {
        val payload = SharedPayload.ViewUrl("https://sbi.co.in.fake-portal.com/login")
        val assessment = LinkCheckActivity.assessPayload(payload)

        assertEquals(RiskStatus.High, assessment.status)
        assertTrue(assessment.actualRiskScore >= 70)
        assertEquals(1, assessment.evaluatedUrls.size)
        assertTrue(assessment.detectedSignals.any { it.contains("Impersonation") })
    }

    @Test
    fun testEvaluateLinkSafety_upiPaymentLink_impersonationFlagsHighRisk() {
        val upiScam = "upi://pay?pa=scammer99@okhdfcbank&pn=Electricity%20Bill%20Office&am=1850"
        val verdict = LinkCheckActivity.evaluateLinkSafety(upiScam)

        assertEquals(RiskStatus.High, verdict.status)
        assertTrue(verdict.riskScore >= 90)
        assertTrue(verdict.title.contains("UPI PAYMENT REQUEST"))
        assertTrue(verdict.supportingEvidence.any { it.contains("VPA: scammer99@okhdfcbank") })
        assertTrue(verdict.supportingEvidence.any { it.contains("Electricity Bill Office") })
        assertTrue(verdict.recommendedAction.contains("Do not authorize payment"))
        assertTrue(verdict.explicitLimitations.contains("Offline UPI parameter analysis"))
    }

    @Test
    fun testEvaluateLinkSafety_upiPaymentLink_genericFlagsSuspicious() {
        val upiGeneric = "upi://pay?pa=friend123@okhdfcbank&pn=Rohan%20Verma"
        val verdict = LinkCheckActivity.evaluateLinkSafety(upiGeneric)

        assertEquals(RiskStatus.Suspicious, verdict.status)
        assertTrue(verdict.riskScore >= 50)
        assertTrue(verdict.title.contains("UNVERIFIED DIRECT UPI LINK"))
        assertTrue(verdict.supportingEvidence.any { it.contains("friend123@okhdfcbank") })
    }

    @Test
    fun testEvaluateLinkSafety_dangerousSchemes_flagsHighRisk() {
        val jsVerdict = LinkCheckActivity.evaluateLinkSafety("javascript:alert(document.cookie)")
        assertEquals(RiskStatus.High, jsVerdict.status)
        assertTrue(jsVerdict.title.contains("DANGEROUS SCHEME"))
        assertTrue(jsVerdict.supportingEvidence.any { it.contains("javascript:") })

        val fileVerdict = LinkCheckActivity.evaluateLinkSafety("file:///sdcard/Download/malware.apk")
        assertEquals(RiskStatus.High, fileVerdict.status)
        assertTrue(fileVerdict.title.contains("DANGEROUS SCHEME"))

        val intentVerdict = LinkCheckActivity.evaluateLinkSafety("intent://action/#Intent;package=com.evil.app;end")
        assertEquals(RiskStatus.High, intentVerdict.status)
        assertTrue(intentVerdict.title.contains("DANGEROUS SCHEME"))
    }

    @Test
    fun testExtractAllUrls_extractsUpiAndDangerousSchemes() {
        val text = "Please settle via upi://pay?pa=scam@okhdfcbank&pn=Fine or view file:///storage/emulated/0/warrant.pdf"
        val urls = LinkCheckActivity.extractAllUrls(text)

        assertEquals(2, urls.size)
        assertTrue(urls.any { it.startsWith("upi://pay") })
        assertTrue(urls.any { it.startsWith("file://") })
    }
}
