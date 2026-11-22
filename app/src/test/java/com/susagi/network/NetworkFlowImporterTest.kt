package com.susagi.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NetworkFlowImporterTest {

    private lateinit var importer: NetworkFlowImporter

    @Before
    fun setUp() {
        importer = NetworkFlowImporter()
    }

    @Test
    fun testImportFromString_c2BeaconSample_detectsBeaconingAnomaly() {
        val report = importer.loadSampleCapture("c2_beacon")

        assertTrue(report.totalFlowsParsed >= 5)
        assertTrue("Verdict must detect C2_BEACONING, got ${report.overallVerdict.anomalyType}",
            report.overallVerdict.anomalyType == NetworkAnomalyType.C2_BEACONING)
        assertTrue(report.overallVerdict.riskScore >= 70)
        assertEquals("HIGH", report.overallVerdict.severity)
        assertTrue("Must anonymize IP endpoints", report.anonymizedEndpoints.any { it.endsWith(".xxx") })
    }

    @Test
    fun testImportFromString_portScanSample_detectsReconnaissance() {
        val report = importer.loadSampleCapture("port_scan")

        assertTrue(report.totalFlowsParsed >= 10)
        assertTrue("Verdict must detect PORT_SCAN, got ${report.overallVerdict.anomalyType}",
            report.overallVerdict.anomalyType == NetworkAnomalyType.PORT_SCAN)
        assertTrue(report.overallVerdict.riskScore >= 75)
        assertEquals("HIGH", report.overallVerdict.severity)
    }

    @Test
    fun testImportFromString_cleanTraffic_reportsBenign() {
        val report = importer.loadSampleCapture("clean")

        assertEquals(3, report.totalFlowsParsed)
        assertEquals(NetworkAnomalyType.BENIGN, report.overallVerdict.anomalyType)
        assertEquals("LOW", report.overallVerdict.severity)
        assertFalse(report.overallVerdict.isAnomalous)
    }

    @Test
    fun testAnonymizeIp_masksTrailingOctets() {
        assertEquals("192.168.1.xxx", importer.anonymizeIp("192.168.1.150"))
        assertEquals("10.0.0.xxx", importer.anonymizeIp("10.0.0.1"))
        assertEquals("invalid_ip", importer.anonymizeIp("invalid_ip"))
    }
}
