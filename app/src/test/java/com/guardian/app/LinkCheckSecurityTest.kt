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
}
