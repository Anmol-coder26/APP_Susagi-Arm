package com.susagi.evaluation

import com.guardian.app.LinkCheckActivity
import com.guardian.app.RiskStatus
import com.susagi.correlation.CorrelationEvaluation
import com.susagi.correlation.IncidentCorrelationEngine
import com.susagi.correlation.SecuritySignalEvent
import com.susagi.edgeai.AnalysisSources
import com.susagi.edgeai.InferenceResult
import com.susagi.edgeai.ThreatChannel
import com.susagi.intelligence.UnifiedThreatEngine
import com.susagi.network.NetworkAnomalyClassifier
import com.susagi.network.NetworkAnomalyType
import com.susagi.network.NetworkFlowRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Scientifically rigorous, reproducible offline evaluation harness for Bharat AI-SoC Challenge PS3.
 * Evaluates held-out benchmark corpora across Message Shield, Link Shield, Network Sentinel,
 * and Cross-Channel Correlation with exact, mathematically verifiable confusion matrices:
 * TP + FP + TN + FN = N (Sample Count) bit-for-bit.
 */
class CybersecurityBenchmarkEvaluationTest {

    private lateinit var threatEngine: UnifiedThreatEngine
    private lateinit var networkClassifier: NetworkAnomalyClassifier
    private lateinit var correlationEngine: IncidentCorrelationEngine

    @Before
    fun setUp() {
        threatEngine = UnifiedThreatEngine()
        networkClassifier = NetworkAnomalyClassifier()
        correlationEngine = IncidentCorrelationEngine(correlationWindowMs = 3600_000L)
    }

    data class LabeledSample<T>(
        val id: String,
        val category: String,
        val input: T,
        val isMalicious: Boolean,
        val description: String
    )

    data class ConfusionMatrix(
        val name: String,
        val totalSamples: Int,
        val tp: Int,
        val fp: Int,
        val tn: Int,
        val fn: Int
    ) {
        val precision: Double get() = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 0.0
        val recall: Double get() = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 0.0
        val f1: Double get() = if (precision + recall > 0.0) 2.0 * (precision * recall) / (precision + recall) else 0.0
        val fpr: Double get() = if (fp + tn > 0) fp.toDouble() / (fp + tn) else 0.0

        fun printSummary() {
            println("==========================================================================")
            println("BENCHMARK EVALUATION RESULTS: $name")
            println("Total Samples: $totalSamples | Verified Conservation: TP($tp) + FP($fp) + TN($tn) + FN($fn) = ${tp + fp + tn + fn}")
            println("Precision: ${String.format("%.4f", precision * 100)}%")
            println("Recall:    ${String.format("%.4f", recall * 100)}%")
            println("F1 Score:  ${String.format("%.4f", f1)}")
            println("FPR:       ${String.format("%.4f", fpr * 100)}%")
            println("==========================================================================")
        }
    }

    // =================================================================================
    // 1. MESSAGE SHIELD BENCHMARK (40 Labeled Samples: 20 Malicious, 20 Benign)
    // =================================================================================
    @Test
    fun testMessageShieldBenchmark_exactConservation() = runBlocking {
        val samples = listOf(
            // 20 Positive (Scam / Threat) Samples
            LabeledSample("MSG-01", "Digital Arrest", "FIR registered against you by cyber crime police. You are under digital arrest. Transfer fifty thousand rupees immediately to avoid non-bailable warrant.", true, "CBI/Police extortion"),
            LabeledSample("MSG-02", "Digital Arrest", "Supreme Court of India warrant issued against your Aadhaar. Connect on Skype id cbi.officer immediately to avoid physical detention.", true, "Digital arrest Skype demand"),
            LabeledSample("MSG-03", "Digital Arrest", "Mumbai Police notice: Money laundering case filed against your mobile number. You are under digital arrest. Do not disconnect call.", true, "Police money laundering fraud"),
            LabeledSample("MSG-04", "Digital Arrest", "CRIMINAL INVESTIGATION: ED and CBI officials require your immediate statement under digital arrest. Transfer bail clearance bond now.", true, "ED bail bond extortion"),
            LabeledSample("MSG-05", "Fake KYC", "Dear SBI customer, your net banking account is blocked. Update your PAN card immediately on this link: http://sbi-kyc-verify.top", true, "SBI PAN card block phish"),
            LabeledSample("MSG-06", "Fake KYC", "HDFC Alert: Your bank account suspended due to incomplete Re-KYC. Visit http://hdfc-kyc-online.info to avoid permanent deactivation.", true, "HDFC Re-KYC phish"),
            LabeledSample("MSG-07", "Fake KYC", "Your bank account has been blocked today! Submit Aadhaar and PAN immediately to unfreeze funds: http://verify-pan-update.com", true, "Unfreeze bank account phish"),
            LabeledSample("MSG-08", "Fake KYC", "Urgent: Your PNB Debit Card is deactivated due to KYC pending. Call officer at 9876543210 or update KYC at http://pnb-card-kyc.org", true, "Debit card deactivation phish"),
            LabeledSample("MSG-09", "Electricity Scam", "Dear Consumer, your electricity power cut will be done tonight at 9:30 PM from electricity office because your previous month bill was not updated. Call officer immediately.", true, "Power cut 9:30 PM threat"),
            LabeledSample("MSG-10", "Electricity Scam", "URGENT DISCOM NOTICE: Your electricity connection will be disconnected within 2 hours. Pay overdue bill or contact executive 9811223344.", true, "Electricity disconnection 2hr"),
            LabeledSample("MSG-11", "Electricity Scam", "Bijli Vibhag Alert: Power supply will be disconnected at 9:30 PM due to unpaid balance. Contact our bill officer immediately.", true, "Bijli vibhag disconnection"),
            LabeledSample("MSG-12", "Electricity Scam", "State Electricity Board: Power cut scheduled tonight for your consumer number. Update bill payment receipt immediately.", true, "SEB scheduled power cut"),
            LabeledSample("MSG-13", "UPI PIN Fraud", "Congratulations! You won ₹5000 cashback reward. Enter UPI PIN in PhonePe to receive money in your bank account.", true, "Enter UPI PIN to receive money"),
            LabeledSample("MSG-14", "UPI PIN Fraud", "GooglePay Reward: ₹2,499 credited to your scratch card. Enter your UPI PIN to claim payment into your account.", true, "GPay reward enter PIN"),
            LabeledSample("MSG-15", "UPI PIN Fraud", "Refund processed for failed order ₹1,200. Open link and enter UPI PIN to receive credit directly.", true, "Refund enter UPI PIN"),
            LabeledSample("MSG-16", "Courier Customs", "Your international parcel is held at Delhi Customs warehouse due to unpaid duty charges. Pay ₹1,450 clearance fee immediately to avoid seizure.", true, "Customs parcel seizure scam"),
            LabeledSample("MSG-17", "Courier Customs", "IndiaPost Alert: Your speed post parcel address is incomplete. Pay ₹25 re-delivery fee at http://indiapost-parcel-tracking.top to schedule delivery.", true, "IndiaPost re-delivery fee"),
            LabeledSample("MSG-18", "Courier Customs", "BlueDart Courier: Package on hold due to missing KYC documentation. Pay customs duty at http://bluedart-customs-duty.xyz", true, "Courier customs documentation"),
            LabeledSample("MSG-19", "Credential Theft", "Your credit card transaction of ₹45,000 at Flipkart is in progress. If not done by you, share OTP with support executive to cancel transaction.", true, "Share OTP to cancel fake charge"),
            LabeledSample("MSG-20", "Credential Theft", "Netbanking security alert: Unauthorized login from UK. Share verification OTP immediately with customer care to lock account.", true, "Share OTP to lock account"),

            // 20 Negative (Benign / Transactional / Harmless) Samples
            LabeledSample("MSG-21", "Bank Transaction", "Dear Customer, your A/C XX1234 is credited with INR 5,000.00 on 21-Nov-2026 by UPI. Avl Bal: INR 15,240.50. - State Bank of India", false, "Authentic UPI credit SMS"),
            LabeledSample("MSG-22", "Bank Transaction", "Your HDFC Bank A/C XX5678 debited for INR 1,250.00 on 21-NOV-26 at RELIANCE RETAIL. UPI Ref: 432611098765. Avl Bal: INR 42,100.00.", false, "Authentic debit POS alert"),
            LabeledSample("MSG-23", "Bank Transaction", "ICICI Bank: ATM cash withdrawal of INR 2,000.00 from A/C XX9900 on 21-Nov-2026. If not you, call 18002662.", false, "ATM cash withdrawal alert"),
            LabeledSample("MSG-24", "Bank Transaction", "Salary of INR 65,000.00 credited to your Axis Bank account XX4411 on 21-Nov-2026. Ref: NEFT-SAL-00921.", false, "Salary credit NEFT alert"),
            LabeledSample("MSG-25", "Bank Transaction", "PNB Alert: Interest of INR 450.00 credited to Savings A/C XX3322 for Q3. Total Avl Bal INR 28,900.00.", false, "Quarterly interest credit"),
            LabeledSample("MSG-26", "Bank Transaction", "Kotak Bank: Credit Card bill generated for Card ending 1234. Minimum Due: INR 500.00 by 05-Dec-2026. Total Due: INR 8,450.00.", false, "Credit card bill statement"),
            LabeledSample("MSG-27", "2FA OTP", "Your Aadhaar OTP for authentication is 918234. Valid for 10 minutes. Do not share with anyone. - UIDAI", false, "Aadhaar UIDAI official OTP"),
            LabeledSample("MSG-28", "2FA OTP", "Use OTP 482910 to verify your IRCTC booking for PNR 2419081234. Valid for 5 minutes. Never share OTP.", false, "IRCTC booking verification OTP"),
            LabeledSample("MSG-29", "2FA OTP", "619024 is your one time password to login to Swiggy. Valid for 3 mins. Swiggy staff never asks for OTP.", false, "Swiggy food login OTP"),
            LabeledSample("MSG-30", "2FA OTP", "Your Zomato login code is 884192. Enter code in app to sign in. Do not disclose to anyone.", false, "Zomato sign-in code"),
            LabeledSample("MSG-31", "Logistics", "India Post: Speed Post article ED987654321IN has been dispatched from New Delhi NSH to Bangalore Sorting Hub.", false, "Authentic Speed Post tracking"),
            LabeledSample("MSG-32", "Logistics", "BlueDart Waybill 54190876123 is out for delivery today. Delivery agent Ramesh will contact you. Delivery PIN: 4892.", false, "Authentic delivery PIN"),
            LabeledSample("MSG-33", "Logistics", "Delhivery: Package for order 402-998811 from Amazon will be delivered by 7 PM today. Track at delhivery.com/track", false, "Amazon Delhivery tracking"),
            LabeledSample("MSG-34", "Logistics", "Your Domino's Pizza order #42 is baked and out for delivery with our rider. Estimated arrival: 15 mins.", false, "Food delivery status update"),
            LabeledSample("MSG-35", "Telecom", "Jio True 5G Unlimited Plan of INR 349 successfully recharged on 9876543210. Valid for 28 days with 2GB/day data.", false, "Jio recharge confirmation"),
            LabeledSample("MSG-36", "Telecom", "Airtel Thanks: Bill payment of INR 899.00 received for postpaid number 9811002233. Current balance is INR 0.00.", false, "Airtel postpaid receipt"),
            LabeledSample("MSG-37", "Telecom", "Vodafone Idea: Data pack 1GB added successfully. Validity 1 day. Check balance in Vi App.", false, "Vi data pack confirmation"),
            LabeledSample("MSG-38", "Personal Chat", "Hi, I am leaving office now. Please keep the dinner ready.", false, "Ordinary personal message"),
            LabeledSample("MSG-39", "Personal Chat", "Can we reschedule our engineering sync to 4:30 PM today? Let me know if that works.", false, "Work meeting scheduling"),
            LabeledSample("MSG-40", "Ambiguous Urgent", "Please call me back immediately, it is very urgent regarding our team project deadline tomorrow.", false, "Ambiguous urgent personal text")
        )

        var tp = 0
        var fp = 0
        var tn = 0
        var fn = 0

        for (sample in samples) {
            val result = threatEngine.analyzeMessage(sample.input)
            val predictedMalicious = result.riskScore >= 70 // High threat threshold

            if (sample.isMalicious && predictedMalicious) {
                tp++
            } else if (!sample.isMalicious && predictedMalicious) {
                fp++
                println("FALSE POSITIVE [${sample.id}]: '${sample.input}' classified with score ${result.riskScore}")
            } else if (!sample.isMalicious && !predictedMalicious) {
                tn++
            } else {
                fn++
                println("FALSE NEGATIVE [${sample.id}]: '${sample.input}' classified with score ${result.riskScore}")
            }
        }

        val matrix = ConfusionMatrix("Message Shield", samples.size, tp, fp, tn, fn)
        matrix.printSummary()

        // Strict Mathematical Conservation
        assertEquals("Total outcomes MUST equal sample count", samples.size, tp + fp + tn + fn)
        assertEquals("Expected 20 ground-truth positives", 20, tp + fn)
        assertEquals("Expected 20 ground-truth negatives", 20, tn + fp)

        // Performance Assertions (Precision >= 90%, Recall >= 90%, F1 >= 0.90, FPR <= 5%)
        assertTrue("Message Shield Precision must be >= 90%, was ${matrix.precision}", matrix.precision >= 0.90)
        assertTrue("Message Shield Recall must be >= 90%, was ${matrix.recall}", matrix.recall >= 0.90)
        assertTrue("Message Shield F1 must be >= 0.90, was ${matrix.f1}", matrix.f1 >= 0.90)
        assertTrue("Message Shield FPR must be <= 5%, was ${matrix.fpr}", matrix.fpr <= 0.05)
    }

    // =================================================================================
    // 2. LINK SHIELD BENCHMARK (40 Labeled Samples: 20 Malicious, 20 Benign)
    // =================================================================================
    @Test
    fun testLinkShieldBenchmark_exactConservation() = runBlocking {
        val samples = listOf(
            // 20 Positive (Malicious Phishing / Sideloading) URLs
            LabeledSample("URL-01", "APK Sideload", "https://download-center-sbi.top/update_kyc_app.apk", true, "Direct APK download"),
            LabeledSample("URL-02", "APK Sideload", "http://malicious-server.net/payloads/rat_agent.dex", true, "Direct DEX binary payload"),
            LabeledSample("URL-03", "APK Sideload", "https://power-bill-discom.info/billing_app.apk?v=2", true, "APK with query parameter"),
            LabeledSample("URL-04", "APK Sideload", "http://support-desk-anydesk.top/quicksupport_installer.apk", true, "RAT sideload APK"),
            LabeledSample("URL-05", "Raw IP Host", "http://198.51.100.42/auth/login.php", true, "Raw public IP host"),
            LabeledSample("URL-06", "Raw IP Host", "https://203.0.113.88/secure/bank_verify", true, "Raw IP with banking path"),
            LabeledSample("URL-07", "Raw IP Host", "http://192.0.2.1/update-pan", true, "Raw IP with PAN path"),
            LabeledSample("URL-08", "Raw IP Host", "http://185.220.101.5:8080/c2_endpoint", true, "Raw IP with non-standard port"),
            LabeledSample("URL-09", "UserInfo Spoof", "https://onlinesbi.sbi@verification-portal-kyc.top/login", true, "SBI userinfo disguise"),
            LabeledSample("URL-10", "UserInfo Spoof", "https://hdfcbank.com@login-check-secure.online/auth", true, "HDFC userinfo disguise"),
            LabeledSample("URL-11", "UserInfo Spoof", "https://incometax.gov.in@tax-refund-claim.xyz/verify", true, "IncomeTax userinfo disguise"),
            LabeledSample("URL-12", "UserInfo Spoof", "https://icicibank.com@unverified-payment-hub.net/pay", true, "ICICI userinfo disguise"),
            LabeledSample("URL-13", "Punycode Homoglyph", "https://xn--sbi-9ja.com/kyc-update", true, "Punycode domain impersonation"),
            LabeledSample("URL-14", "Punycode Homoglyph", "https://xn--hdfcbnk-mwa.info/netbanking", true, "Punycode HDFC homoglyph"),
            LabeledSample("URL-15", "Punycode Homoglyph", "https://login.xn--pnb-6na.org/auth", true, "Punycode PNB subdomain"),
            LabeledSample("URL-16", "Punycode Homoglyph", "https://xn--pple-43d.com/support", true, "Punycode lookalike character"),
            LabeledSample("URL-17", "Subdomain Spoof", "https://sbi.co.in.account-update-portal.com/login", true, "Multi-subdomain SBI spoof"),
            LabeledSample("URL-18", "Subdomain Spoof", "https://incometax.gov.in.refund-claim-dispatch.xyz/index.php", true, "Gov.in subdomain spoof"),
            LabeledSample("URL-19", "Subdomain Spoof", "https://electricity-bill-pay.com/update-kyc", true, "Phishing keyword in domain"),
            LabeledSample("URL-20", "Subdomain Spoof", "https://uidai.gov.in.aadhaar-pan-link.top/auth", true, "Aadhaar link spoof"),

            // 20 Negative (Benign / Official) URLs
            LabeledSample("URL-21", "Official Gov", "https://incometax.gov.in/iec/foportal/", false, "Official Indian Income Tax portal"),
            LabeledSample("URL-22", "Official Gov", "https://cybercrime.gov.in/Default.aspx", false, "Official National Cybercrime portal"),
            LabeledSample("URL-23", "Official Gov", "https://uidai.gov.in/en/my-aadhaar.html", false, "Official UIDAI portal"),
            LabeledSample("URL-24", "Official Gov", "https://www.irctc.co.in/nget/train-search", false, "Official IRCTC railway portal"),
            LabeledSample("URL-25", "Official Gov", "https://passportindia.gov.in/AppOnlineProject/welcomeLink", false, "Official Passport Seva portal"),
            LabeledSample("URL-26", "Official Gov", "https://epfindia.gov.in/site_en/index.php", false, "Official EPFO portal"),
            LabeledSample("URL-27", "Official Bank", "https://onlinesbi.sbi/portal/index.html", false, "Official State Bank of India portal"),
            LabeledSample("URL-28", "Official Bank", "https://www.hdfcbank.com/personal/ways-to-bank/online-banking", false, "Official HDFC Bank portal"),
            LabeledSample("URL-29", "Official Bank", "https://www.icicibank.com/personal-banking/insta-banking", false, "Official ICICI Bank portal"),
            LabeledSample("URL-30", "Official Bank", "https://www.axisbank.com/retail/accounts/savings-account", false, "Official Axis Bank portal"),
            LabeledSample("URL-31", "Official Bank", "https://www.pnbindia.in/retail.html", false, "Official Punjab National Bank"),
            LabeledSample("URL-32", "Official Bank", "https://www.bankofbaroda.in/personal-banking", false, "Official Bank of Baroda"),
            LabeledSample("URL-33", "Clean Tech", "https://google.com/search?q=current+weather+delhi", false, "Google search query"),
            LabeledSample("URL-34", "Clean Tech", "https://amazon.in/dp/B08N5WRWNW", false, "Amazon India product page"),
            LabeledSample("URL-35", "Clean Tech", "https://github.com/torvalds/linux", false, "GitHub source repository"),
            LabeledSample("URL-36", "Clean Tech", "https://en.wikipedia.org/wiki/Arm_architecture_family", false, "Wikipedia encyclopedia page"),
            LabeledSample("URL-37", "Clean Tech", "https://stackoverflow.com/questions/tagged/kotlin", false, "StackOverflow technical thread"),
            LabeledSample("URL-38", "Clean Tech", "https://developer.android.com/reference/packages", false, "Official Android Developer documentation"),
            LabeledSample("URL-39", "Clean Tech", "https://www.flipkart.com/search?q=laptop", false, "Flipkart shopping catalogue"),
            LabeledSample("URL-40", "Clean Tech", "https://www.youtube.com/watch?v=dQw4w9WgXcQ", false, "YouTube video stream")
        )

        var tp = 0
        var fp = 0
        var tn = 0
        var fn = 0

        for (sample in samples) {
            val result = threatEngine.analyzeUrl(sample.input)
            val predictedMalicious = result.riskScore >= 70 // High threat threshold

            if (sample.isMalicious && predictedMalicious) {
                tp++
            } else if (!sample.isMalicious && predictedMalicious) {
                fp++
                println("FALSE POSITIVE [${sample.id}]: '${sample.input}' classified with score ${result.riskScore}")
            } else if (!sample.isMalicious && !predictedMalicious) {
                tn++
            } else {
                fn++
                println("FALSE NEGATIVE [${sample.id}]: '${sample.input}' classified with score ${result.riskScore}")
            }
        }

        val matrix = ConfusionMatrix("Link Shield", samples.size, tp, fp, tn, fn)
        matrix.printSummary()

        assertEquals("Total outcomes MUST equal sample count", samples.size, tp + fp + tn + fn)
        assertEquals("Expected 20 ground-truth positives", 20, tp + fn)
        assertEquals("Expected 20 ground-truth negatives", 20, tn + fp)

        assertTrue("Link Shield Precision must be >= 90%, was ${matrix.precision}", matrix.precision >= 0.90)
        assertTrue("Link Shield Recall must be >= 90%, was ${matrix.recall}", matrix.recall >= 0.90)
        assertTrue("Link Shield F1 must be >= 0.90, was ${matrix.f1}", matrix.f1 >= 0.90)
        assertTrue("Link Shield FPR must be <= 5%, was ${matrix.fpr}", matrix.fpr <= 0.05)
    }

    // =================================================================================
    // 3. NETWORK SENTINEL BENCHMARK (30 Labeled Batches: 15 Anomalous, 15 Benign)
    // =================================================================================
    @Test
    fun testNetworkSentinelBenchmark_exactConservation() {
        val baseTime = 1700000000000L

        // Helper to generate batches
        fun verticalScanBatch(portsCount: Int): List<NetworkFlowRecord> =
            (1..portsCount).map { i ->
                NetworkFlowRecord(
                    timestampMs = baseTime + i * 40L,
                    srcIp = "192.168.1.100",
                    dstIp = "192.168.1.1",
                    srcPort = 40000 + i,
                    dstPort = 20 + i,
                    tcpFlags = setOf("SYN"),
                    isOutbound = true
                )
            }

        fun horizontalSweepBatch(hostsCount: Int, port: Int): List<NetworkFlowRecord> =
            (1..hostsCount).map { i ->
                NetworkFlowRecord(
                    timestampMs = baseTime + i * 35L,
                    srcIp = "192.168.1.50",
                    dstIp = "192.168.1.$i",
                    srcPort = 50000 + i,
                    dstPort = port,
                    tcpFlags = setOf("SYN"),
                    isOutbound = true
                )
            }

        fun periodicBeaconBatch(intervalsMs: List<Long>, port: Int): List<NetworkFlowRecord> =
            intervalsMs.map { offset ->
                NetworkFlowRecord(
                    timestampMs = baseTime + offset,
                    srcIp = "192.168.1.50",
                    dstIp = "203.0.113.88",
                    srcPort = 54321,
                    dstPort = port,
                    packetCount = 4,
                    byteCount = 512,
                    tcpFlags = setOf("PSH", "ACK"),
                    isOutbound = true
                )
            }

        fun connectionFloodBatch(count: Int): List<NetworkFlowRecord> =
            (1..count).map { i ->
                NetworkFlowRecord(
                    timestampMs = baseTime + i * 10L,
                    srcIp = "192.168.1.100",
                    dstIp = "192.168.1.200",
                    srcPort = 30000 + i,
                    dstPort = 80,
                    packetCount = 40,
                    byteCount = 1800,
                    durationMs = 20,
                    tcpFlags = setOf("SYN"),
                    isOutbound = true
                )
            }

        fun benignTrafficBatch(): List<NetworkFlowRecord> =
            listOf(
                NetworkFlowRecord(timestampMs = baseTime, srcIp = "192.168.1.10", dstIp = "142.250.190.46", srcPort = 55100, dstPort = 443, protocol = "TCP", packetCount = 8, byteCount = 1200, durationMs = 120, tcpFlags = setOf("ACK"), isOutbound = true),
                NetworkFlowRecord(timestampMs = baseTime + 2000L, srcIp = "192.168.1.10", dstIp = "1.1.1.1", srcPort = 55101, dstPort = 53, protocol = "UDP", packetCount = 2, byteCount = 150, durationMs = 25, isOutbound = true),
                NetworkFlowRecord(timestampMs = baseTime + 8500L, srcIp = "192.168.1.10", dstIp = "142.250.190.46", srcPort = 55100, dstPort = 443, protocol = "TCP", packetCount = 12, byteCount = 4500, durationMs = 200, tcpFlags = setOf("ACK"), isOutbound = true)
            )

        fun benignNtpBatch(): List<NetworkFlowRecord> =
            listOf(0L, 10000L, 20000L, 30000L, 40000L).map { offset ->
                NetworkFlowRecord(timestampMs = baseTime + offset, srcIp = "192.168.1.10", dstIp = "162.159.200.1", srcPort = 51234, dstPort = 123, protocol = "UDP", packetCount = 1, byteCount = 48, isOutbound = true)
            }

        val batches = mutableListOf<LabeledSample<List<NetworkFlowRecord>>>()

        // 15 Anomalous Batches
        batches.add(LabeledSample("NET-01", "Vertical Scan", verticalScanBatch(12), true, "Vertical scan 12 ports"))
        batches.add(LabeledSample("NET-02", "Vertical Scan", verticalScanBatch(16), true, "Vertical scan 16 ports"))
        batches.add(LabeledSample("NET-03", "Vertical Scan", verticalScanBatch(20), true, "Vertical scan 20 ports"))
        batches.add(LabeledSample("NET-04", "Vertical Scan", verticalScanBatch(10), true, "Vertical scan 10 ports"))
        batches.add(LabeledSample("NET-05", "Horizontal Sweep", horizontalSweepBatch(10, 5555), true, "ADB subnet sweep"))
        batches.add(LabeledSample("NET-06", "Horizontal Sweep", horizontalSweepBatch(12, 445), true, "SMB subnet sweep"))
        batches.add(LabeledSample("NET-07", "Horizontal Sweep", horizontalSweepBatch(15, 3389), true, "RDP subnet sweep"))
        batches.add(LabeledSample("NET-08", "Horizontal Sweep", horizontalSweepBatch(9, 22), true, "SSH subnet sweep"))
        batches.add(LabeledSample("NET-09", "C2 Beacon", periodicBeaconBatch(listOf(0L, 5010L, 10005L, 14990L, 20020L), 8443), true, "Periodic beacon 5s"))
        batches.add(LabeledSample("NET-10", "C2 Beacon", periodicBeaconBatch(listOf(0L, 10020L, 20010L, 30040L, 40010L), 9001), true, "Periodic beacon 10s"))
        batches.add(LabeledSample("NET-11", "C2 Beacon", periodicBeaconBatch(listOf(0L, 3010L, 6005L, 8990L, 12015L), 4444), true, "Periodic beacon 3s"))
        batches.add(LabeledSample("NET-12", "C2 Beacon", periodicBeaconBatch(listOf(0L, 15010L, 30020L, 45015L, 60030L), 8080), true, "Periodic beacon 15s"))
        batches.add(LabeledSample("NET-13", "Connection Spike", connectionFloodBatch(15), true, "High pps flood 15 flows"))
        batches.add(LabeledSample("NET-14", "Connection Spike", connectionFloodBatch(20), true, "High pps flood 20 flows"))
        batches.add(LabeledSample("NET-15", "Connection Spike", connectionFloodBatch(12), true, "High pps flood 12 flows"))

        // 15 Benign Batches
        batches.add(LabeledSample("NET-16", "Benign Web", benignTrafficBatch(), false, "Standard web browsing"))
        batches.add(LabeledSample("NET-17", "Benign Web", benignTrafficBatch(), false, "Standard web browsing 2"))
        batches.add(LabeledSample("NET-18", "Benign Web", benignTrafficBatch(), false, "Standard web browsing 3"))
        batches.add(LabeledSample("NET-19", "Benign Web", benignTrafficBatch(), false, "Standard web browsing 4"))
        batches.add(LabeledSample("NET-20", "Benign NTP", benignNtpBatch(), false, "NTP synchronization 1"))
        batches.add(LabeledSample("NET-21", "Benign NTP", benignNtpBatch(), false, "NTP synchronization 2"))
        batches.add(LabeledSample("NET-22", "Benign NTP", benignNtpBatch(), false, "NTP synchronization 3"))
        batches.add(LabeledSample("NET-23", "Benign NTP", benignNtpBatch(), false, "NTP synchronization 4"))
        batches.add(LabeledSample("NET-24", "Benign DNS", listOf(
            NetworkFlowRecord(timestampMs = baseTime, srcIp = "192.168.1.10", dstIp = "8.8.8.8", srcPort = 53100, dstPort = 53, protocol = "UDP", packetCount = 2, byteCount = 120, isOutbound = true),
            NetworkFlowRecord(timestampMs = baseTime + 12000L, srcIp = "192.168.1.10", dstIp = "8.8.8.8", srcPort = 53101, dstPort = 53, protocol = "UDP", packetCount = 2, byteCount = 120, isOutbound = true)
        ), false, "DNS queries 1"))
        batches.add(LabeledSample("NET-25", "Benign DNS", listOf(
            NetworkFlowRecord(timestampMs = baseTime, srcIp = "192.168.1.10", dstIp = "1.1.1.1", srcPort = 54100, dstPort = 53, protocol = "UDP", packetCount = 2, byteCount = 120, isOutbound = true)
        ), false, "DNS queries 2"))
        batches.add(LabeledSample("NET-26", "Benign Stream", listOf(
            NetworkFlowRecord(timestampMs = baseTime, srcIp = "192.168.1.10", dstIp = "142.250.190.50", srcPort = 44321, dstPort = 443, protocol = "TCP", packetCount = 50, byteCount = 60000, durationMs = 2000, isOutbound = true)
        ), false, "Media streaming chunk"))
        batches.add(LabeledSample("NET-27", "Benign Stream", listOf(
            NetworkFlowRecord(timestampMs = baseTime + 5000L, srcIp = "192.168.1.10", dstIp = "142.250.190.50", srcPort = 44321, dstPort = 443, protocol = "TCP", packetCount = 80, byteCount = 95000, durationMs = 2500, isOutbound = true)
        ), false, "Media streaming chunk 2"))
        batches.add(LabeledSample("NET-28", "Benign Idle", listOf(
            NetworkFlowRecord(timestampMs = baseTime, srcIp = "192.168.1.10", dstIp = "192.168.1.1", srcPort = 51200, dstPort = 80, protocol = "TCP", packetCount = 2, byteCount = 100, isOutbound = true)
        ), false, "Router local ping"))
        batches.add(LabeledSample("NET-29", "Benign Idle", emptyList(), false, "Empty flow buffer"))
        batches.add(LabeledSample("NET-30", "Benign Single", listOf(
            NetworkFlowRecord(timestampMs = baseTime, srcIp = "192.168.1.10", dstIp = "172.217.168.206", srcPort = 52000, dstPort = 443, protocol = "TCP", packetCount = 4, byteCount = 500, isOutbound = true)
        ), false, "Single HTTPS connect"))

        var tp = 0
        var fp = 0
        var tn = 0
        var fn = 0

        for (sample in batches) {
            val verdict = networkClassifier.classifyFlowBatch(sample.input)
            val predictedAnomalous = verdict.isAnomalous

            if (sample.isMalicious && predictedAnomalous) {
                tp++
            } else if (!sample.isMalicious && predictedAnomalous) {
                fp++
                println("FALSE POSITIVE [${sample.id}]: '${sample.description}' classified as ${verdict.anomalyType} score ${verdict.riskScore}")
            } else if (!sample.isMalicious && !predictedAnomalous) {
                tn++
            } else {
                fn++
                println("FALSE NEGATIVE [${sample.id}]: '${sample.description}' classified as ${verdict.anomalyType} score ${verdict.riskScore}")
            }
        }

        val matrix = ConfusionMatrix("Network Sentinel", batches.size, tp, fp, tn, fn)
        matrix.printSummary()

        assertEquals("Total outcomes MUST equal sample count", batches.size, tp + fp + tn + fn)
        assertEquals("Expected 15 ground-truth positives", 15, tp + fn)
        assertEquals("Expected 15 ground-truth negatives", 15, tn + fp)

        assertTrue("Network Sentinel Precision must be >= 90%, was ${matrix.precision}", matrix.precision >= 0.90)
        assertTrue("Network Sentinel Recall must be >= 90%, was ${matrix.recall}", matrix.recall >= 0.90)
        assertTrue("Network Sentinel F1 must be >= 0.90, was ${matrix.f1}", matrix.f1 >= 0.90)
        assertTrue("Network Sentinel FPR must be <= 5%, was ${matrix.fpr}", matrix.fpr <= 0.05)
    }

    // =================================================================================
    // 4. CROSS-CHANNEL CORRELATION BENCHMARK (10 Labeled Scenarios: 5 True, 5 Negative)
    // =================================================================================
    @Test
    fun testCrossChannelCorrelationBenchmark_exactConservation() {
        val now = System.currentTimeMillis()

        data class CorrelationScenario(
            val id: String,
            val eventA: SecuritySignalEvent,
            val eventB: SecuritySignalEvent,
            val shouldCorrelate: Boolean,
            val description: String
        )

        fun createDummyResult(score: Int): InferenceResult =
            InferenceResult("threat", score, "HIGH", emptyList(), emptyList(), "desc", emptyList(), AnalysisSources.LOCAL_RULES)

        val scenarios = listOf(
            // 5 True Correlated Scenarios
            CorrelationScenario("CORR-01",
                SecuritySignalEvent(timestamp = now, channel = ThreatChannel.MESSAGE, rawContent = "KYC at http://sbi-kyc-verify.top", extractedDomain = "sbi-kyc-verify.top", extractedIp = "185.220.101.5", assessment = createDummyResult(80)),
                SecuritySignalEvent(timestamp = now + 15000L, channel = ThreatChannel.URL, rawContent = "http://sbi-kyc-verify.top/update", extractedDomain = "sbi-kyc-verify.top", extractedIp = "185.220.101.5", assessment = createDummyResult(85)),
                true, "Shared domain sbi-kyc-verify.top across SMS and URL"
            ),
            CorrelationScenario("CORR-02",
                SecuritySignalEvent(timestamp = now, channel = ThreatChannel.URL, rawContent = "https://download-center.top/app.apk", extractedDomain = "download-center.top", extractedIp = "198.51.100.42", assessment = createDummyResult(85)),
                SecuritySignalEvent(timestamp = now + 30000L, channel = ThreatChannel.NETWORK, rawContent = "Flow to 198.51.100.42:8443", extractedDomain = "download-center.top", extractedIp = "198.51.100.42", assessment = createDummyResult(80)),
                true, "Shared domain download-center.top across URL and Network"
            ),
            CorrelationScenario("CORR-03",
                SecuritySignalEvent(timestamp = now, channel = ThreatChannel.MESSAGE, rawContent = "Pay bill at http://power-pay.info", extractedDomain = "power-pay.info", extractedIp = "203.0.113.15", assessment = createDummyResult(75)),
                SecuritySignalEvent(timestamp = now + 20000L, channel = ThreatChannel.NETWORK, rawContent = "Flow to 203.0.113.15:443", extractedDomain = "power-pay.info", extractedIp = "203.0.113.15", assessment = createDummyResult(75)),
                true, "Shared domain and IP across SMS and Network"
            ),
            CorrelationScenario("CORR-04",
                SecuritySignalEvent(timestamp = now, channel = ThreatChannel.MESSAGE, rawContent = "HDFC re-kyc at http://hdfc-verify.xyz", extractedDomain = "hdfc-verify.xyz", extractedIp = null, assessment = createDummyResult(80)),
                SecuritySignalEvent(timestamp = now + 10000L, channel = ThreatChannel.URL, rawContent = "http://hdfc-verify.xyz/auth", extractedDomain = "hdfc-verify.xyz", extractedIp = null, assessment = createDummyResult(85)),
                true, "Shared domain hdfc-verify.xyz without IP"
            ),
            CorrelationScenario("CORR-05",
                SecuritySignalEvent(timestamp = now, channel = ThreatChannel.URL, rawContent = "http://198.51.100.99/login", extractedDomain = null, extractedIp = "198.51.100.99", assessment = createDummyResult(75)),
                SecuritySignalEvent(timestamp = now + 5000L, channel = ThreatChannel.NETWORK, rawContent = "Outbound socket to 198.51.100.99:443", extractedDomain = null, extractedIp = "198.51.100.99", assessment = createDummyResult(80)),
                true, "Shared verified IP endpoint 198.51.100.99"
            ),

            // 5 Negative (Uncorrelated) Scenarios
            CorrelationScenario("CORR-06",
                SecuritySignalEvent(timestamp = now, channel = ThreatChannel.MESSAGE, rawContent = "SBI KYC suspended by CBI order at http://sbi-fake-a.top", extractedDomain = "sbi-fake-a.top", extractedIp = "198.51.100.1", threatIndicators = listOf("KYC", "CBI"), assessment = createDummyResult(85)),
                SecuritySignalEvent(timestamp = now + 1000L, channel = ThreatChannel.URL, rawContent = "http://hdfc-fake-b.top/login", extractedDomain = "hdfc-fake-b.top", extractedIp = "203.0.113.88", threatIndicators = listOf("KYC", "CBI"), assessment = createDummyResult(85)),
                false, "Similar keywords ('KYC', 'CBI') but different domains and IPs"
            ),
            CorrelationScenario("CORR-07",
                SecuritySignalEvent(timestamp = now - 7200_000L, channel = ThreatChannel.MESSAGE, rawContent = "http://phish-site.top", extractedDomain = "phish-site.top", extractedIp = "198.51.100.5", assessment = createDummyResult(80)),
                SecuritySignalEvent(timestamp = now, channel = ThreatChannel.URL, rawContent = "http://phish-site.top", extractedDomain = "phish-site.top", extractedIp = "198.51.100.5", assessment = createDummyResult(80)),
                false, "Same domain but 2 hours apart (> 1-hour window)"
            ),
            CorrelationScenario("CORR-08",
                SecuritySignalEvent(timestamp = now, channel = ThreatChannel.MESSAGE, rawContent = "Hello call me back immediately.", extractedDomain = null, extractedIp = null, assessment = createDummyResult(20)),
                SecuritySignalEvent(timestamp = now + 2000L, channel = ThreatChannel.NETWORK, rawContent = "Socket connection", extractedDomain = null, extractedIp = null, assessment = createDummyResult(15)),
                false, "Incomplete source evidence: null domains and IPs"
            ),
            CorrelationScenario("CORR-09",
                SecuritySignalEvent(timestamp = now, channel = ThreatChannel.MESSAGE, rawContent = "Order 402 delivered.", extractedDomain = null, extractedIp = null, assessment = createDummyResult(10)),
                SecuritySignalEvent(timestamp = now + 5000L, channel = ThreatChannel.NETWORK, rawContent = "HTTPS to 142.250.190.46 (Google)", extractedDomain = "google.com", extractedIp = "142.250.190.46", assessment = createDummyResult(10)),
                false, "Independent benign events"
            ),
            CorrelationScenario("CORR-10",
                SecuritySignalEvent(timestamp = now, channel = ThreatChannel.MESSAGE, rawContent = "Electricity bill at http://power-scam.xyz", extractedDomain = "power-scam.xyz", extractedIp = "192.0.2.10", assessment = createDummyResult(80)),
                SecuritySignalEvent(timestamp = now + 4000L, channel = ThreatChannel.NETWORK, rawContent = "Outbound to 104.244.42.1 (Twitter)", extractedDomain = "twitter.com", extractedIp = "104.244.42.1", assessment = createDummyResult(10)),
                false, "Completely different domain and IP"
            )
        )

        var tp = 0
        var fp = 0
        var tn = 0
        var fn = 0

        for (scenario in scenarios) {
            val eval = correlationEngine.evaluateCorrelation(scenario.eventA, scenario.eventB)
            val predictedCorrelated = eval.isCorrelated

            if (scenario.shouldCorrelate && predictedCorrelated) {
                tp++
            } else if (!scenario.shouldCorrelate && predictedCorrelated) {
                fp++
                println("FALSE POSITIVE [${scenario.id}]: '${scenario.description}' correlated with reason: ${eval.reason}")
            } else if (!scenario.shouldCorrelate && !predictedCorrelated) {
                tn++
            } else {
                fn++
                println("FALSE NEGATIVE [${scenario.id}]: '${scenario.description}' failed to correlate with reason: ${eval.reason}")
            }
        }

        val matrix = ConfusionMatrix("Cross-Channel Correlation", scenarios.size, tp, fp, tn, fn)
        matrix.printSummary()

        assertEquals("Total outcomes MUST equal sample count", scenarios.size, tp + fp + tn + fn)
        assertEquals("Expected 5 ground-truth positives", 5, tp + fn)
        assertEquals("Expected 5 ground-truth negatives", 5, tn + fp)

        assertEquals("Correlation Precision should be 100%", 1.0, matrix.precision, 0.001)
        assertEquals("Correlation Recall should be 100%", 1.0, matrix.recall, 0.001)
        assertEquals("Correlation F1 should be 1.0", 1.0, matrix.f1, 0.001)
        assertEquals("Correlation FPR should be 0.0%", 0.0, matrix.fpr, 0.001)
    }
}
