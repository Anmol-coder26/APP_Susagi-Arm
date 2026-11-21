package com.susagi.network

/**
 * Represents a normalized network flow record for on-device anomaly detection.
 * Modeled after standard IPFIX / NetFlow / CICIDS flow attributes.
 */
data class NetworkFlowRecord(
    val timestampMs: Long = System.currentTimeMillis(),
    val srcIp: String,
    val dstIp: String,
    val srcPort: Int,
    val dstPort: Int,
    val protocol: String = "TCP",
    val packetCount: Long = 1,
    val byteCount: Long = 64,
    val durationMs: Long = 10,
    val tcpFlags: Set<String> = emptySet(),
    val isOutbound: Boolean = true
)

enum class NetworkAnomalyType {
    PORT_SCAN,
    CONNECTION_SPIKE,
    C2_BEACONING,
    DATA_EXFILTRATION,
    BENIGN,
    AMBIGUOUS_DATA
}

data class NetworkAnomalyVerdict(
    val anomalyType: NetworkAnomalyType,
    val riskScore: Int,
    val severity: String, // "HIGH", "MEDIUM", "LOW"
    val confidence: Float,
    val indicators: List<String>,
    val explanation: String,
    val recommendedActions: List<String>,
    val observedEvent: String = "",
    val riskCategory: String = anomalyType.name,
    val relevantMeasurements: Map<String, Any> = emptyMap(),
    val thresholdUsed: String = "",
    val uncertainty: Float = 1.0f - confidence
) {
    val isAnomalous: Boolean get() = anomalyType != NetworkAnomalyType.BENIGN && anomalyType != NetworkAnomalyType.AMBIGUOUS_DATA && riskScore >= 50
}
