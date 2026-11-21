package com.susagi.network

import com.susagi.intelligence.UnifiedThreatEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class NetworkAnomalyClassifierTest {

    private lateinit var classifier: NetworkAnomalyClassifier
    private lateinit var threatEngine: UnifiedThreatEngine

    @Before
    fun setUp() {
        classifier = NetworkAnomalyClassifier()
        threatEngine = UnifiedThreatEngine()
    }

    @Test
    fun testVerticalPortScan_multiplePortsScannedOnSingleHost_flagsHighRisk() {
        val baseTime = 1700000000000L
        val flows = (1..12).map { portIndex ->
            NetworkFlowRecord(
                timestampMs = baseTime + portIndex * 50,
                srcIp = "192.168.1.105",
                dstIp = "192.168.1.1",
                srcPort = 49152 + portIndex,
                dstPort = 20 + portIndex,
                protocol = "TCP",
                packetCount = 1,
                byteCount = 40,
                tcpFlags = setOf("SYN"),
                isOutbound = true
            )
        }

        val verdict = classifier.classifyFlowBatch(flows)
        assertEquals(NetworkAnomalyType.PORT_SCAN, verdict.anomalyType)
        assertTrue("Vertical port scan score should be >= 85, was ${verdict.riskScore}", verdict.riskScore >= 85)
        assertEquals("HIGH", verdict.severity)
        assertTrue(verdict.isAnomalous)
        assertTrue(verdict.indicators.any { it.contains("Vertical scan") || it.contains("scanned") })
        assertEquals("Vertical port scan", verdict.observedEvent)
        assertTrue(verdict.relevantMeasurements.containsKey("uniquePorts"))
    }

    @Test
    fun testHorizontalSubnetSweep_samePortAcrossMultipleHosts_flagsHighRisk() {
        val baseTime = 1700000000000L
        // Single source sweeping ADB port 5555 across 10 distinct local IP addresses
        val flows = (1..10).map { hostIndex ->
            NetworkFlowRecord(
                timestampMs = baseTime + hostIndex * 30,
                srcIp = "192.168.1.50",
                dstIp = "192.168.1.$hostIndex",
                srcPort = 40000 + hostIndex,
                dstPort = 5555,
                protocol = "TCP",
                packetCount = 1,
                byteCount = 64,
                tcpFlags = setOf("SYN"),
                isOutbound = true
            )
        }

        val verdict = classifier.classifyFlowBatch(flows)
        assertEquals(NetworkAnomalyType.PORT_SCAN, verdict.anomalyType)
        assertTrue("Horizontal scan score should be >= 80, was ${verdict.riskScore}", verdict.riskScore >= 80)
        assertEquals("HIGH", verdict.severity)
        assertTrue(verdict.isAnomalous)
        assertTrue(verdict.indicators.any { it.contains("Horizontal sweep") })
        assertEquals("Horizontal subnet sweep", verdict.observedEvent)
    }

    @Test
    fun testC2BeaconingDetection_rigidIntervalHeartbeats_flagsHighRisk() {
        val baseTime = 1700000000000L
        // 5 consecutive outbound heartbeats at almost exactly 5000ms intervals (low jitter)
        val intervals = listOf(0L, 5010L, 10005L, 14990L, 20020L)
        val flows = intervals.map { offset ->
            NetworkFlowRecord(
                timestampMs = baseTime + offset,
                srcIp = "192.168.1.50",
                dstIp = "203.0.113.88",
                srcPort = 54321,
                dstPort = 8443,
                protocol = "TCP",
                packetCount = 4,
                byteCount = 512,
                tcpFlags = setOf("PSH", "ACK"),
                isOutbound = true
            )
        }

        val verdict = classifier.classifyFlowBatch(flows)
        assertEquals(NetworkAnomalyType.C2_BEACONING, verdict.anomalyType)
        assertTrue("C2 beaconing score should be >= 80, was ${verdict.riskScore}", verdict.riskScore >= 80)
        assertEquals("HIGH", verdict.severity)
        assertTrue(verdict.isAnomalous)
        assertTrue(verdict.indicators.any { it.contains("Suspicious periodic outbound heartbeat") || it.contains("Periodic outbound heartbeat") })
        assertEquals("Regular periodic outbound beaconing", verdict.observedEvent)
        assertTrue(verdict.uncertainty < 0.20f)
    }

    @Test
    fun testBenignPeriodicTraffic_ntpSync_notFlaggedAsC2() {
        val baseTime = 1700000000000L
        // Legitimate NTP time synchronization: exactly every 10 seconds to pool.ntp.org on port 123
        val intervals = listOf(0L, 10000L, 20000L, 30000L, 40000L)
        val flows = intervals.map { offset ->
            NetworkFlowRecord(
                timestampMs = baseTime + offset,
                srcIp = "192.168.1.10",
                dstIp = "162.159.200.1", // pool.ntp.org
                srcPort = 51234,
                dstPort = 123, // NTP port
                protocol = "UDP",
                packetCount = 1,
                byteCount = 48, // Standard NTP packet
                isOutbound = true
            )
        }

        val verdict = classifier.classifyFlowBatch(flows)
        // Must NOT be classified as C2_BEACONING
        assertNotEquals("Benign NTP must not be classified as C2 beaconing", NetworkAnomalyType.C2_BEACONING, verdict.anomalyType)
        assertEquals(NetworkAnomalyType.BENIGN, verdict.anomalyType)
        assertTrue("Risk score must be low (< 30) for NTP, was ${verdict.riskScore}", verdict.riskScore < 30)
        assertEquals("LOW", verdict.severity)
        assertFalse(verdict.isAnomalous)
    }

    @Test
    fun testConnectionSpike_highPacketRate_flagsAnomaly() {
        val baseTime = 1700000000000L
        // 15 flows in 200ms with high packet count
        val flows = (1..15).map { i ->
            NetworkFlowRecord(
                timestampMs = baseTime + i * 10,
                srcIp = "192.168.1.100",
                dstIp = "192.168.1.200",
                srcPort = 30000 + i,
                dstPort = 80,
                protocol = "TCP",
                packetCount = 50,
                byteCount = 2000,
                durationMs = 20,
                tcpFlags = setOf("SYN"),
                isOutbound = true
            )
        }

        val verdict = classifier.classifyFlowBatch(flows)
        assertEquals(NetworkAnomalyType.CONNECTION_SPIKE, verdict.anomalyType)
        assertTrue("Connection spike score should be >= 75, was ${verdict.riskScore}", verdict.riskScore >= 75)
        assertEquals("HIGH", verdict.severity)
        assertTrue(verdict.isAnomalous)
    }

    @Test
    fun testSingleFlow_sensitivePortProbe_flagsImmediateAlert() {
        val flow = NetworkFlowRecord(
            srcIp = "10.0.0.5",
            dstIp = "198.51.100.25",
            srcPort = 51234,
            dstPort = 3389, // RDP
            protocol = "TCP",
            isOutbound = true
        )

        val verdict = classifier.classifySingleFlow(flow)
        assertEquals(NetworkAnomalyType.PORT_SCAN, verdict.anomalyType)
        assertTrue(verdict.riskScore >= 70)
        assertEquals("HIGH", verdict.severity)
        assertTrue(verdict.explanation.contains("RDP"))
    }

    @Test
    fun testBenignTraffic_standardHttps_flagsLowRisk() {
        val baseTime = 1700000000000L
        val flows = listOf(
            NetworkFlowRecord(
                timestampMs = baseTime,
                srcIp = "192.168.1.10",
                dstIp = "142.250.190.46", // Google HTTPS
                srcPort = 55100,
                dstPort = 443,
                protocol = "TCP",
                packetCount = 8,
                byteCount = 1200,
                durationMs = 120,
                tcpFlags = setOf("ACK"),
                isOutbound = true
            ),
            NetworkFlowRecord(
                timestampMs = baseTime + 1500,
                srcIp = "192.168.1.10",
                dstIp = "1.1.1.1", // Cloudflare DNS
                srcPort = 55101,
                dstPort = 53,
                protocol = "UDP",
                packetCount = 2,
                byteCount = 150,
                durationMs = 25,
                isOutbound = true
            )
        )

        val verdict = classifier.classifyFlowBatch(flows)
        assertEquals(NetworkAnomalyType.BENIGN, verdict.anomalyType)
        assertTrue("Benign score should be < 40, was ${verdict.riskScore}", verdict.riskScore < 40)
        assertEquals("LOW", verdict.severity)
        assertFalse(verdict.isAnomalous)
    }

    @Test
    fun testMissingData_handledSafely() {
        val invalidFlow = NetworkFlowRecord(
            srcIp = "",
            dstIp = "",
            srcPort = -1,
            dstPort = -1
        )
        val singleVerdict = classifier.classifySingleFlow(invalidFlow)
        assertEquals(NetworkAnomalyType.AMBIGUOUS_DATA, singleVerdict.anomalyType)
        assertFalse(singleVerdict.isAnomalous)
        assertTrue(singleVerdict.uncertainty >= 0.5f)

        val batchVerdict = classifier.classifyFlowBatch(listOf(invalidFlow))
        assertEquals(NetworkAnomalyType.AMBIGUOUS_DATA, batchVerdict.anomalyType)
        assertFalse(batchVerdict.isAnomalous)
    }

    @Test
    fun testEmptyInput_returnsZeroScore() {
        val verdict = classifier.classifyFlowBatch(emptyList())
        assertEquals(NetworkAnomalyType.BENIGN, verdict.anomalyType)
        assertEquals(0, verdict.riskScore)
        assertEquals(0.0f, verdict.uncertainty, 0.001f)
        assertFalse(verdict.isAnomalous)
    }

    @Test
    fun testExtremeNumericValues_handledWithoutOverflow() {
        val extremeFlow = NetworkFlowRecord(
            timestampMs = 1700000000000L,
            srcIp = "10.0.0.1",
            dstIp = "10.0.0.2",
            srcPort = 65535,
            dstPort = 443,
            packetCount = Long.MAX_VALUE,
            byteCount = Long.MAX_VALUE,
            durationMs = Long.MAX_VALUE
        )
        // Must execute cleanly without arithmetic overflow exception
        val verdict = classifier.classifySingleFlow(extremeFlow)
        assertNotNull(verdict)
    }

    @Test
    fun testOutOfOrderTimestamps_sortedAndClassifiedAccurately() {
        val baseTime = 1700000000000L
        // Intentionally deliver flows in reverse order
        val flows = listOf(
            NetworkFlowRecord(timestampMs = baseTime + 20000L, srcIp = "10.0.0.1", dstIp = "203.0.113.5", srcPort = 5000, dstPort = 8080),
            NetworkFlowRecord(timestampMs = baseTime + 15000L, srcIp = "10.0.0.1", dstIp = "203.0.113.5", srcPort = 5000, dstPort = 8080),
            NetworkFlowRecord(timestampMs = baseTime + 10000L, srcIp = "10.0.0.1", dstIp = "203.0.113.5", srcPort = 5000, dstPort = 8080),
            NetworkFlowRecord(timestampMs = baseTime + 5000L,  srcIp = "10.0.0.1", dstIp = "203.0.113.5", srcPort = 5000, dstPort = 8080),
            NetworkFlowRecord(timestampMs = baseTime,          srcIp = "10.0.0.1", dstIp = "203.0.113.5", srcPort = 5000, dstPort = 8080)
        )

        val verdict = classifier.classifyFlowBatch(flows)
        assertEquals(NetworkAnomalyType.C2_BEACONING, verdict.anomalyType)
        assertTrue(verdict.isAnomalous)
    }

    @Test
    fun testUnifiedThreatEngine_integrationWithNetworkBatch() = runBlocking {
        val baseTime = 1700000000000L
        val flows = (1..10).map { portIndex ->
            NetworkFlowRecord(
                timestampMs = baseTime + portIndex * 40,
                srcIp = "192.168.1.120",
                dstIp = "192.168.1.1",
                srcPort = 40000 + portIndex,
                dstPort = 100 + portIndex,
                protocol = "TCP",
                packetCount = 1,
                byteCount = 40,
                tcpFlags = setOf("SYN"),
                isOutbound = true
            )
        }

        val result = threatEngine.analyzeNetworkBatch(flows)
        assertTrue("Unified engine should return high risk score for port scan, was ${result.riskScore}", result.riskScore >= 80)
        assertEquals("HIGH", result.severity)
        assertTrue("Tactics should reflect network reconnaissance", result.detectedTactics.any { it.contains("reconnaissance") || it.contains("scan") })
        assertTrue("Recommendations must be present", result.recommendedActions.isNotEmpty())
    }
}
