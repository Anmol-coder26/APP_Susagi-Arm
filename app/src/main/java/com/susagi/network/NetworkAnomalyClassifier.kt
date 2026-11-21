package com.susagi.network

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * On-Device Network Anomaly Classifier for Arm Bharat AI-SoC Challenge PS3.
 * Evaluates network telemetry against signature heuristics and statistical flow anomalies
 * completely offline with zero cloud dependency.
 */
class NetworkAnomalyClassifier(
    private val portScanThreshold: Int = 8,
    private val spikeRateThresholdPps: Float = 50f,
    private val beaconMinIntervalCount: Int = 4,
    private val beaconMaxJitterRatio: Float = 0.15f
) {

    companion object {
        // Standard benign periodic services (ports) that exhibit legitimate heartbeats
        val BENIGN_PERIODIC_PORTS = setOf(53, 123, 67, 68) // DNS, NTP, DHCP

        val SENSITIVE_ADMIN_PORTS = mapOf(
            22 to "SSH",
            23 to "Telnet",
            3389 to "RDP",
            445 to "SMB",
            135 to "RPC",
            5555 to "ADB"
        )
    }

    /**
     * Analyzes a single flow record in isolation.
     */
    fun classifySingleFlow(flow: NetworkFlowRecord): NetworkAnomalyVerdict {
        // Sanity & Missing Data Check
        if (flow.srcIp.isBlank() || flow.dstIp.isBlank() || flow.srcPort < 0 || flow.dstPort < 0) {
            return NetworkAnomalyVerdict(
                anomalyType = NetworkAnomalyType.AMBIGUOUS_DATA,
                riskScore = 20,
                severity = "LOW",
                confidence = 0.40f,
                indicators = listOf("Flow record contains missing or negative IP/Port values"),
                explanation = "Incomplete telemetry record: invalid IP addresses or port specifications.",
                recommendedActions = listOf("Verify telemetry capture source configuration."),
                observedEvent = "Incomplete/corrupt flow record",
                relevantMeasurements = mapOf("srcIp" to flow.srcIp, "dstIp" to flow.dstIp, "dstPort" to flow.dstPort),
                thresholdUsed = "Input validation schema",
                uncertainty = 0.60f
            )
        }

        val indicators = mutableListOf<String>()

        // 1. Raw IP connection to sensitive administrative ports
        if (flow.dstPort in SENSITIVE_ADMIN_PORTS.keys && flow.isOutbound) {
            val service = SENSITIVE_ADMIN_PORTS[flow.dstPort]
            indicators.add("Unusual outbound connection to sensitive administrative port: $service (${flow.dstPort})")
            return NetworkAnomalyVerdict(
                anomalyType = NetworkAnomalyType.PORT_SCAN,
                riskScore = 75,
                severity = "HIGH",
                confidence = 0.85f,
                indicators = indicators,
                explanation = "Detected direct outbound attempt to sensitive administrative service $service on host ${flow.dstIp}.",
                recommendedActions = listOf(
                    "Block outbound traffic to ${flow.dstIp}:${flow.dstPort}",
                    "Inspect originating application for potential unauthorized remote access trojan (RAT)"
                ),
                observedEvent = "Direct administrative port access ($service)",
                riskCategory = "LATERAL_RECONNAISSANCE",
                relevantMeasurements = mapOf("dstPort" to flow.dstPort, "service" to (service ?: ""), "bytes" to flow.byteCount),
                thresholdUsed = "Administrative port whitelist",
                uncertainty = 0.15f
            )
        }

        // 2. High-volume single flow spike
        val flowDurationSec = (flow.durationMs.coerceAtLeast(1L)).toDouble() / 1000.0
        val safePacketCount = flow.packetCount.coerceAtLeast(0L).toDouble()
        val pps = if (flowDurationSec > 0.0) safePacketCount / flowDurationSec else 0.0

        if (pps > 100.0 && flow.tcpFlags.contains("SYN") && !flow.tcpFlags.contains("ACK")) {
            indicators.add("High-frequency SYN flood pattern: ${String.format("%.1f", pps)} pps")
            return NetworkAnomalyVerdict(
                anomalyType = NetworkAnomalyType.CONNECTION_SPIKE,
                riskScore = 85,
                severity = "HIGH",
                confidence = 0.90f,
                indicators = indicators,
                explanation = "Detected rapid SYN connection spike targeting ${flow.dstIp}:${flow.dstPort}.",
                recommendedActions = listOf(
                    "Enable TCP SYN cookies / rate limiting on local network interface",
                    "Isolate offending application socket"
                ),
                observedEvent = "High packet-rate SYN burst",
                riskCategory = "DENIAL_OF_SERVICE",
                relevantMeasurements = mapOf("pps" to pps, "durationSec" to flowDurationSec, "packets" to flow.packetCount),
                thresholdUsed = "pps > 100.0 (SYN only)",
                uncertainty = 0.10f
            )
        }

        // Benign default
        return NetworkAnomalyVerdict(
            anomalyType = NetworkAnomalyType.BENIGN,
            riskScore = 10,
            severity = "LOW",
            confidence = 0.95f,
            indicators = listOf("Normal flow parameters: ${flow.protocol} to ${flow.dstIp}:${flow.dstPort}"),
            explanation = "Traffic metadata consistent with normal benign baseline.",
            recommendedActions = listOf("No defensive action required."),
            observedEvent = "Benign single flow",
            riskCategory = "NORMAL_COMMUNICATION",
            relevantMeasurements = mapOf("protocol" to flow.protocol, "bytes" to flow.byteCount),
            thresholdUsed = "Standard baseline",
            uncertainty = 0.05f
        )
    }

    /**
     * Analyzes a sequence of flow records within an observation window.
     * Robust against out-of-order timestamps, missing data, and benign periodic traffic.
     */
    fun classifyFlowBatch(flows: List<NetworkFlowRecord>): NetworkAnomalyVerdict {
        if (flows.isEmpty()) {
            return NetworkAnomalyVerdict(
                anomalyType = NetworkAnomalyType.BENIGN,
                riskScore = 0,
                severity = "LOW",
                confidence = 1.0f,
                indicators = emptyList(),
                explanation = "No network telemetry records provided.",
                recommendedActions = emptyList(),
                observedEvent = "Empty telemetry batch",
                riskCategory = "NO_DATA",
                thresholdUsed = "Empty input check",
                uncertainty = 0.0f
            )
        }

        // Filter and sanitize valid flows
        val validFlows = flows.filter { it.srcIp.isNotBlank() && it.dstIp.isNotBlank() }
        if (validFlows.isEmpty()) {
            return NetworkAnomalyVerdict(
                anomalyType = NetworkAnomalyType.AMBIGUOUS_DATA,
                riskScore = 15,
                severity = "LOW",
                confidence = 0.35f,
                indicators = listOf("All records in batch contained empty or invalid IP addresses"),
                explanation = "Unable to classify batch: records lack necessary network address metadata.",
                recommendedActions = listOf("Inspect network sensor telemetry pipeline."),
                observedEvent = "Invalid batch metadata",
                riskCategory = "AMBIGUOUS_DATA",
                thresholdUsed = "Valid IP check",
                uncertainty = 0.65f
            )
        }

        // Robust sort by timestamp to handle out-of-order arrivals
        val sortedByTime = validFlows.sortedBy { it.timestampMs }
        val indicators = mutableListOf<String>()

        // 1. Vertical Port Scan Detection: Single src contacting multiple dst ports on a single dstIp
        val flowsBySrcAndDst = sortedByTime.groupBy { it.srcIp to it.dstIp }
        for ((pair, pairFlows) in flowsBySrcAndDst) {
            val (srcIp, dstIp) = pair
            val uniqueDstPorts = pairFlows.map { it.dstPort }.distinct()
            val synOnlyCount = pairFlows.count { it.tcpFlags.contains("SYN") && !it.tcpFlags.contains("ACK") }

            if (uniqueDstPorts.size >= portScanThreshold) {
                indicators.add("Vertical scan: $srcIp probed ${uniqueDstPorts.size} distinct ports on host $dstIp")
                if (synOnlyCount >= portScanThreshold / 2) {
                    indicators.add("Stealth SYN scan: $synOnlyCount unanswered SYN probes")
                }

                return NetworkAnomalyVerdict(
                    anomalyType = NetworkAnomalyType.PORT_SCAN,
                    riskScore = 90,
                    severity = "HIGH",
                    confidence = 0.94f,
                    indicators = indicators,
                    explanation = "Vertical port scan detected from $srcIp targeting host $dstIp across ${uniqueDstPorts.size} distinct ports (sample: ${uniqueDstPorts.take(5).joinToString(", ")}).",
                    recommendedActions = listOf(
                        "Terminate scanning socket connection immediately",
                        "Audit device for unauthorized network discovery tools",
                        "Block communication to target $dstIp"
                    ),
                    observedEvent = "Vertical port scan",
                    riskCategory = "RECONNAISSANCE",
                    relevantMeasurements = mapOf(
                        "srcIp" to srcIp,
                        "dstIp" to dstIp,
                        "uniquePorts" to uniqueDstPorts.size,
                        "synProbes" to synOnlyCount
                    ),
                    thresholdUsed = "uniqueDstPorts >= $portScanThreshold",
                    uncertainty = 0.06f
                )
            }
        }

        // 2. Horizontal Port Scan Detection: Single src contacting multiple distinct dst hosts on the SAME port
        val flowsBySrcAndPort = sortedByTime.groupBy { it.srcIp to it.dstPort }
        for ((pair, pairFlows) in flowsBySrcAndPort) {
            val (srcIp, dstPort) = pair
            // Exclude benign broadcast/multicast DNS/DHCP
            if (dstPort in BENIGN_PERIODIC_PORTS) continue

            val uniqueDstIps = pairFlows.map { it.dstIp }.distinct()
            if (uniqueDstIps.size >= portScanThreshold) {
                indicators.add("Horizontal sweep: $srcIp swept ${uniqueDstIps.size} distinct hosts on port $dstPort")
                return NetworkAnomalyVerdict(
                    anomalyType = NetworkAnomalyType.PORT_SCAN,
                    riskScore = 88,
                    severity = "HIGH",
                    confidence = 0.92f,
                    indicators = indicators,
                    explanation = "Horizontal subnet sweep detected from $srcIp scanning port $dstPort across ${uniqueDstIps.size} distinct hosts.",
                    recommendedActions = listOf(
                        "Isolate scanning application",
                        "Inspect local subnet firewall rules"
                    ),
                    observedEvent = "Horizontal subnet sweep",
                    riskCategory = "RECONNAISSANCE",
                    relevantMeasurements = mapOf(
                        "srcIp" to srcIp,
                        "dstPort" to dstPort,
                        "uniqueHosts" to uniqueDstIps.size
                    ),
                    thresholdUsed = "uniqueDstIps >= $portScanThreshold",
                    uncertainty = 0.08f
                )
            }
        }

        // 3. High-frequency Connection Spike (e.g. DoS / Flooding)
        val timeSpanMs = (sortedByTime.last().timestampMs - sortedByTime.first().timestampMs).coerceAtLeast(1L)
        val timeSpanSec = (timeSpanMs.toDouble() / 1000.0).coerceAtLeast(0.1)
        val totalPackets = sortedByTime.sumOf { it.packetCount.coerceAtLeast(0L).toDouble() }
        val overallPps = totalPackets / timeSpanSec

        if (overallPps >= spikeRateThresholdPps && sortedByTime.size >= 10) {
            indicators.add("Aggregate packet rate exceeded threshold: ${String.format("%.1f", overallPps)} pps (threshold: $spikeRateThresholdPps pps)")
            return NetworkAnomalyVerdict(
                anomalyType = NetworkAnomalyType.CONNECTION_SPIKE,
                riskScore = 80,
                severity = "HIGH",
                confidence = 0.88f,
                indicators = indicators,
                explanation = "Abnormal surge in network traffic frequency detected (${sortedByTime.size} connections, ${totalPackets.toLong()} packets in ${String.format("%.2f", timeSpanSec)}s).",
                recommendedActions = listOf(
                    "Throttle connection rate to destination endpoints",
                    "Verify active background network processes in Android Settings"
                ),
                observedEvent = "High connection-rate spike",
                riskCategory = "DENIAL_OF_SERVICE",
                relevantMeasurements = mapOf(
                    "pps" to overallPps,
                    "packets" to totalPackets.toLong(),
                    "timeSpanSec" to timeSpanSec
                ),
                thresholdUsed = "overallPps >= $spikeRateThresholdPps",
                uncertainty = 0.12f
            )
        }

        // 4. Command-and-Control (C2) Periodic Beaconing vs Benign Periodic Traffic
        val flowsByDst = sortedByTime.filter { it.isOutbound }.groupBy { it.dstIp }
        for ((dstIp, dstFlows) in flowsByDst) {
            if (dstFlows.size >= beaconMinIntervalCount) {
                // Check if all flows in this periodic group target well-known benign maintenance ports (e.g. NTP port 123, DNS port 53)
                val allBenignPorts = dstFlows.all { it.dstPort in BENIGN_PERIODIC_PORTS }
                val avgBytes = dstFlows.map { it.byteCount }.average()

                val intervals = mutableListOf<Long>()
                for (i in 1 until dstFlows.size) {
                    intervals.add(dstFlows[i].timestampMs - dstFlows[i - 1].timestampMs)
                }

                if (intervals.isNotEmpty()) {
                    val meanInterval = intervals.average()
                    val variance = intervals.map { (it - meanInterval).pow(2) }.average()
                    val stdDev = sqrt(variance)
                    val jitterRatio = if (meanInterval > 0) (stdDev / meanInterval).toFloat() else 1f

                    if (meanInterval >= 500 && jitterRatio <= beaconMaxJitterRatio) {
                        // If it's a known benign maintenance service (e.g. NTP sync every 10s with small packets), do NOT flag as malicious C2
                        if (allBenignPorts && avgBytes < 256.0) {
                            // Recognize as benign periodic
                            indicators.add("Benign periodic service synchronization to $dstIp (ports: ${dstFlows.map { it.dstPort }.distinct()}, interval: ${meanInterval.toLong()}ms)")
                            return NetworkAnomalyVerdict(
                                anomalyType = NetworkAnomalyType.BENIGN,
                                riskScore = 15,
                                severity = "LOW",
                                confidence = 0.90f,
                                indicators = indicators,
                                explanation = "Periodic traffic matches legitimate network time/name service synchronization.",
                                recommendedActions = listOf("No defensive action required."),
                                observedEvent = "Benign periodic service heartbeat",
                                riskCategory = "INFRASTRUCTURE_SYNC",
                                relevantMeasurements = mapOf(
                                    "dstIp" to dstIp,
                                    "meanIntervalMs" to meanInterval,
                                    "jitterRatio" to jitterRatio
                                ),
                                thresholdUsed = "Benign port whitelist (53, 123)",
                                uncertainty = 0.10f
                            )
                        }

                        // Malicious / Suspicious C2 Beaconing
                        indicators.add("Suspicious periodic outbound heartbeat to $dstIp: mean interval = ${meanInterval.toLong()}ms, jitter ratio = ${String.format("%.3f", jitterRatio)}")
                        return NetworkAnomalyVerdict(
                            anomalyType = NetworkAnomalyType.C2_BEACONING,
                            riskScore = 85,
                            severity = "HIGH",
                            confidence = 0.91f,
                            indicators = indicators,
                            explanation = "Periodic command-and-control (C2) beaconing detected to $dstIp with mathematical periodicity (interval: ${meanInterval.toLong()}ms, jitter: ${String.format("%.3f", jitterRatio)}).",
                            recommendedActions = listOf(
                                "Block outbound communication to destination $dstIp",
                                "Quarantine calling background process and check APK package identity"
                            ),
                            observedEvent = "Regular periodic outbound beaconing",
                            riskCategory = "COMMAND_AND_CONTROL",
                            relevantMeasurements = mapOf(
                                "dstIp" to dstIp,
                                "meanIntervalMs" to meanInterval,
                                "jitterRatio" to jitterRatio,
                                "sampleCount" to dstFlows.size
                            ),
                            thresholdUsed = "jitterRatio <= $beaconMaxJitterRatio and meanInterval >= 500ms",
                            uncertainty = 0.09f
                        )
                    }
                }
            }
        }

        // 5. Benign Baseline
        return NetworkAnomalyVerdict(
            anomalyType = NetworkAnomalyType.BENIGN,
            riskScore = 15,
            severity = "LOW",
            confidence = 0.92f,
            indicators = listOf("All observed flows within normal operational baselines (${validFlows.size} flows evaluated)"),
            explanation = "Network flow telemetry indicates standard benign application communication.",
            recommendedActions = listOf("No immediate intervention needed."),
            observedEvent = "Normal traffic sequence",
            riskCategory = "NORMAL_COMMUNICATION",
            relevantMeasurements = mapOf("flowCount" to validFlows.size, "durationMs" to timeSpanMs),
            thresholdUsed = "Standard statistical baseline",
            uncertainty = 0.08f
        )
    }
}
