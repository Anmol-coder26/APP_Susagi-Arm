package com.susagi.network

import java.io.BufferedReader
import java.io.InputStream
import java.io.StringReader

data class NetworkImportReport(
    val fileName: String,
    val totalFlowsParsed: Int,
    val overallVerdict: NetworkAnomalyVerdict,
    val suspiciousFlowCount: Int,
    val anonymizedEndpoints: List<String>,
    val timeWindowMs: Long,
    val isSampleFixture: Boolean = false,
    val rawParsedFlows: List<NetworkFlowRecord> = emptyList()
)

/**
 * On-Device Network Telemetry CSV Importer for SuSagi Guardian V2.
 * Streams and parses network flow records (CICIDS / IPFIX CSV schema),
 * anonymizes IP endpoints for user privacy, and executes offline
 * anomaly classification through [NetworkAnomalyClassifier].
 */
class NetworkFlowImporter(
    val classifier: NetworkAnomalyClassifier = NetworkAnomalyClassifier(),
    private val maxStreamRecords: Int = 5000
) {

    /**
     * Parses an input stream (e.g. from Storage Access Framework / URI).
     */
    fun importFromStream(inputStream: InputStream, displayName: String = "telemetry.csv"): NetworkImportReport {
        val reader = inputStream.bufferedReader()
        return parseReader(reader, displayName, isSample = false)
    }

    /**
     * Parses raw CSV string data.
     */
    fun importFromString(csvContent: String, displayName: String = "inline_flows.csv"): NetworkImportReport {
        val reader = BufferedReader(StringReader(csvContent))
        return parseReader(reader, displayName, isSample = false)
    }

    private fun parseReader(reader: BufferedReader, name: String, isSample: Boolean): NetworkImportReport {
        val flows = mutableListOf<NetworkFlowRecord>()
        val anonymizedHosts = mutableSetOf<String>()
        var minTime = Long.MAX_VALUE
        var maxTime = Long.MIN_VALUE

        try {
            reader.useLines { lines ->
                var isHeader = true
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isBlank() || trimmed.startsWith("#")) continue

                    if (isHeader) {
                        isHeader = false
                        if (trimmed.contains("src", ignoreCase = true) || trimmed.contains("ip", ignoreCase = true)) {
                            continue // Skip header row
                        }
                    }

                    if (flows.size >= maxStreamRecords) break

                    val record = parseCsvLine(trimmed)
                    if (record != null) {
                        flows.add(record)
                        anonymizedHosts.add(anonymizeIp(record.dstIp))
                        if (record.timestampMs < minTime) minTime = record.timestampMs
                        if (record.timestampMs > maxTime) maxTime = record.timestampMs
                    }
                }
            }
        } catch (_: Exception) {}

        if (flows.isEmpty()) {
            return NetworkImportReport(
                fileName = name,
                totalFlowsParsed = 0,
                overallVerdict = NetworkAnomalyVerdict(
                    anomalyType = NetworkAnomalyType.AMBIGUOUS_DATA,
                    riskScore = 0,
                    severity = "LOW",
                    confidence = 1.0f,
                    indicators = listOf("No valid flow records found in imported CSV"),
                    explanation = "Import file contains no readable flow data records.",
                    recommendedActions = listOf("Select a valid NetFlow or CICIDS formatted CSV file.")
                ),
                suspiciousFlowCount = 0,
                anonymizedEndpoints = emptyList(),
                timeWindowMs = 0L,
                isSampleFixture = isSample
            )
        }

        val verdict = classifier.classifyFlowBatch(flows)
        val suspiciousCount = flows.count {
            val single = classifier.classifySingleFlow(it)
            single.isAnomalous
        }

        val duration = if (maxTime >= minTime) maxTime - minTime else 0L

        return NetworkImportReport(
            fileName = name,
            totalFlowsParsed = flows.size,
            overallVerdict = verdict,
            suspiciousFlowCount = suspiciousCount,
            anonymizedEndpoints = anonymizedHosts.take(8).toList(),
            timeWindowMs = duration,
            isSampleFixture = isSample,
            rawParsedFlows = flows
        )
    }

    private fun parseCsvLine(line: String): NetworkFlowRecord? {
        val parts = line.split(',').map { it.trim().removeSurrounding("\"") }
        if (parts.size < 5) return null

        return try {
            // Flexible schema: timestamp, src_ip, dst_ip, src_port, dst_port, protocol, packet_count, byte_count, duration, flags, outbound
            val time = parts.getOrNull(0)?.toLongOrNull() ?: System.currentTimeMillis()
            val srcIp = parts.getOrNull(1) ?: "192.168.1.100"
            val dstIp = parts.getOrNull(2) ?: return null
            val srcPort = parts.getOrNull(3)?.toIntOrNull() ?: 45000
            val dstPort = parts.getOrNull(4)?.toIntOrNull() ?: 80
            val proto = parts.getOrNull(5)?.ifBlank { "TCP" } ?: "TCP"
            val packets = parts.getOrNull(6)?.toLongOrNull() ?: 1L
            val bytes = parts.getOrNull(7)?.toLongOrNull() ?: 64L
            val duration = parts.getOrNull(8)?.toLongOrNull() ?: 10L
            val flags = parts.getOrNull(9)?.split(';')?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
            val isOut = parts.getOrNull(10)?.toBooleanStrictOrNull() ?: true

            NetworkFlowRecord(
                timestampMs = time,
                srcIp = srcIp,
                dstIp = dstIp,
                srcPort = srcPort,
                dstPort = dstPort,
                protocol = proto,
                packetCount = packets,
                byteCount = bytes,
                durationMs = duration,
                tcpFlags = flags,
                isOutbound = isOut
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Anonymizes IPv4 and IPv6 addresses for user privacy.
     * e.g., 203.0.113.195 -> 203.0.113.xxx
     */
    fun anonymizeIp(ip: String): String {
        return if ('.' in ip) {
            val parts = ip.split('.')
            if (parts.size == 4) "${parts[0]}.${parts[1]}.${parts[2]}.xxx" else ip
        } else if (':' in ip) {
            val parts = ip.split(':')
            if (parts.size >= 3) "${parts[0]}:${parts[1]}:...:xxx" else ip
        } else ip
    }

    /**
     * Built-in pre-calibrated sample captures for offline demonstration.
     */
    fun loadSampleCapture(sampleId: String): NetworkImportReport {
        val (name, csv) = when (sampleId) {
            "c2_beacon" -> "c2_beaconing_telemetry.csv" to generateC2BeaconCsv()
            "port_scan" -> "port_scan_reconnaissance.csv" to generatePortScanCsv()
            else -> "clean_web_traffic.csv" to generateCleanTrafficCsv()
        }
        val report = importFromString(csv, name)
        return report.copy(isSampleFixture = true)
    }

    private fun generateC2BeaconCsv(): String = buildString {
        appendLine("timestamp,src_ip,dst_ip,src_port,dst_port,protocol,packet_count,byte_count,duration_ms,flags,is_outbound")
        val baseTime = 1732230000000L
        for (i in 0..6) {
            val t = baseTime + (i * 10_000L) + (i % 2 * 200L) // Regular interval ~10s with minimal jitter
            appendLine("$t,192.168.1.105,198.51.100.88,49152,8080,TCP,4,256,120,SYN;ACK,true")
        }
    }

    private fun generatePortScanCsv(): String = buildString {
        appendLine("timestamp,src_ip,dst_ip,src_port,dst_port,protocol,packet_count,byte_count,duration_ms,flags,is_outbound")
        val baseTime = 1732230000000L
        val targetPorts = listOf(21, 22, 23, 25, 80, 135, 443, 445, 1433, 3306, 3389, 5555, 8080)
        targetPorts.forEachIndexed { idx, port ->
            val t = baseTime + (idx * 50L) // Rapid burst < 2 seconds
            appendLine("$t,192.168.1.105,10.0.0.1,50000,$port,TCP,1,40,5,SYN,true")
        }
    }

    private fun generateCleanTrafficCsv(): String = buildString {
        appendLine("timestamp,src_ip,dst_ip,src_port,dst_port,protocol,packet_count,byte_count,duration_ms,flags,is_outbound")
        val baseTime = 1732230000000L
        appendLine("$baseTime,192.168.1.105,1.1.1.1,52000,53,UDP,2,120,15,,true")
        appendLine("${baseTime + 1200},192.168.1.105,142.250.190.46,52001,443,TCP,45,18200,850,ACK;PSH,true")
        appendLine("${baseTime + 4500},192.168.1.105,142.250.190.46,52002,443,TCP,12,4100,320,ACK,true")
    }
}
