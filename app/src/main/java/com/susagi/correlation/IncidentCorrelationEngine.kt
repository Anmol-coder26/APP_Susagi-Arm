package com.susagi.correlation

import com.susagi.edgeai.InferenceResult
import com.susagi.edgeai.ThreatChannel
import com.susagi.edgeai.ThreatSeverity
import java.util.UUID

/**
 * Standard security event captured by one of SuSagi Guardian's edge defense shields
 * (Message Shield, Link Shield, Network Shield, Call Shield).
 */
data class SecuritySignalEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val channel: ThreatChannel,
    val rawContent: String,
    val extractedDomain: String? = null,
    val extractedIp: String? = null,
    val threatIndicators: List<String> = emptyList(),
    val assessment: InferenceResult
) {
    val normalizedDomain: String?
        get() {
            val d = extractedDomain?.trim()?.lowercase() ?: return null
            if (d.isBlank()) return null
            return d.removePrefix("http://").removePrefix("https://").removePrefix("www.").trimEnd('/')
        }
}

/**
 * High-confidence correlated security incident linking multiple threat vectors
 * across messaging, URL browsing, and network telemetry.
 */
data class CorrelatedIncident(
    val incidentId: String = "INC-" + UUID.randomUUID().toString().take(8).uppercase(),
    val title: String,
    val compositeRiskScore: Int,
    val severity: ThreatSeverity,
    val participatingChannels: Set<ThreatChannel>,
    val events: List<SecuritySignalEvent>,
    val sharedIndicators: List<String>,
    val narrativeExplanation: String,
    val recommendedRemediation: List<String>,
    val createdAt: Long = System.currentTimeMillis()
)

data class CorrelationEvaluation(
    val isCorrelated: Boolean,
    val reason: String,
    val matchedEntity: String? = null
)

/**
 * IncidentCorrelationEngine
 *
 * Evidence-driven edge threat correlation engine for Bharat AI-SoC Challenge PS3.
 * Links discrete observations across disparate telemetry streams (SMS, URLs, Network Flows)
 * using deterministic entity matching within a sliding temporal window.
 *
 * Strict Rules:
 * 1. Do not assume an IP connection proves a domain association unless that association was actually supplied.
 * 2. Do not infer relationships solely because two events contain similar words or descriptions.
 * 3. Correlation is 100% deterministic and auditable.
 */
class IncidentCorrelationEngine(
    val correlationWindowMs: Long = 3600_000L // 1 hour sliding time window
) {
    private val activeEvents = mutableListOf<SecuritySignalEvent>()
    private val incidents = mutableListOf<CorrelatedIncident>()

    @Synchronized
    fun ingestEvent(event: SecuritySignalEvent): CorrelatedIncident? {
        val now = event.timestamp
        // Evict expired events outside the correlation window
        activeEvents.removeAll { (now - it.timestamp) > correlationWindowMs }

        // Find existing events that share evidence with the incoming event
        val matches = activeEvents.filter { existing ->
            isCorrelated(existing, event)
        }

        activeEvents.add(event)

        if (matches.isNotEmpty()) {
            val allLinkedEvents = (matches + event).distinctBy { it.eventId }
            val correlatedChannels = allLinkedEvents.map { it.channel }.toSet()

            // Only form multi-channel correlation if more than one channel is involved
            if (correlatedChannels.size >= 2) {
                val incident = buildCorrelatedIncident(allLinkedEvents, correlatedChannels)
                incidents.add(incident)
                return incident
            }
        }

        return null
    }

    /**
     * Evaluates why two security events are or are not correlated.
     */
    fun evaluateCorrelation(a: SecuritySignalEvent, b: SecuritySignalEvent): CorrelationEvaluation {
        // 1. Check temporal window
        val timeDelta = Math.abs(a.timestamp - b.timestamp)
        if (timeDelta > correlationWindowMs) {
            return CorrelationEvaluation(
                isCorrelated = false,
                reason = "Events fall outside documented correlation window (time delta: ${timeDelta / 1000}s > ${correlationWindowMs / 1000}s)"
            )
        }

        // 2. Check for missing or incomplete evidence
        val domainA = a.normalizedDomain
        val domainB = b.normalizedDomain
        val ipA = a.extractedIp?.trim()
        val ipB = b.extractedIp?.trim()

        if (domainA.isNullOrBlank() && domainB.isNullOrBlank() && ipA.isNullOrBlank() && ipB.isNullOrBlank()) {
            return CorrelationEvaluation(
                isCorrelated = false,
                reason = "Incomplete source evidence: Neither event provides a verifiable domain or IP entity"
            )
        }

        // 3. Condition A: Shared normalized domain / hostname
        if (!domainA.isNullOrBlank() && !domainB.isNullOrBlank()) {
            if (domainA.equals(domainB, ignoreCase = true)) {
                return CorrelationEvaluation(
                    isCorrelated = true,
                    reason = "Shared verified destination domain: '$domainA'",
                    matchedEntity = domainA
                )
            }
        }

        // 4. Condition B: Shared observed IP address
        if (!ipA.isNullOrBlank() && !ipB.isNullOrBlank()) {
            if (ipA == ipB) {
                return CorrelationEvaluation(
                    isCorrelated = true,
                    reason = "Shared verified IP endpoint: '$ipA'",
                    matchedEntity = ipA
                )
            }
        }

        // 5. Non-matching entities (even if descriptions are similar)
        return CorrelationEvaluation(
            isCorrelated = false,
            reason = "No shared verifiable entity: domains ('$domainA' vs '$domainB') and IPs ('$ipA' vs '$ipB') do not match"
        )
    }

    /**
     * Determines whether two security events share definitive cryptographic or behavioral indicators.
     */
    fun isCorrelated(a: SecuritySignalEvent, b: SecuritySignalEvent): Boolean {
        return evaluateCorrelation(a, b).isCorrelated
    }

    private fun buildCorrelatedIncident(
        events: List<SecuritySignalEvent>,
        channels: Set<ThreatChannel>
    ): CorrelatedIncident {
        val maxScore = events.maxOfOrNull { it.assessment.riskScore } ?: 70
        // Elevated composite score when multiple vectors corroborate the threat
        val compositeScore = (maxScore + 10).coerceAtMost(100)

        val severity = when {
            compositeScore >= 85 -> ThreatSeverity.CRITICAL
            compositeScore >= 70 -> ThreatSeverity.HIGH
            compositeScore >= 40 -> ThreatSeverity.MODERATE
            else -> ThreatSeverity.LOW
        }

        val sharedDomain = events.mapNotNull { it.normalizedDomain }.firstOrNull()
        val sharedIp = events.mapNotNull { it.extractedIp }.firstOrNull()
        val sharedIndicators = events.flatMap { it.threatIndicators }.distinct()

        val channelNames = channels.joinToString(" + ") { it.name }
        val title = if (sharedDomain != null) {
            "Cross-Channel Campaign targeting domain '$sharedDomain' ($channelNames)"
        } else if (sharedIp != null) {
            "Coordinated Attack involving IP $sharedIp ($channelNames)"
        } else {
            "Multi-Vector Threat Incident ($channelNames)"
        }

        val explanation = StringBuilder().apply {
            append("Correlated threat activity detected across ${channels.size} independent channels ($channelNames). ")
            if (sharedDomain != null) {
                append("Suspicious domain '$sharedDomain' was independently observed across channels. ")
            }
            if (sharedIp != null) {
                append("Outbound socket connection coincides with fraudulent communication. ")
            }
            append("This multi-vector alignment significantly increases the probability of an active, targeted intrusion or fraud attempt.")
        }.toString()

        val remediation = mutableListOf<String>().apply {
            add("Do not interact with or click links from the flagged communication.")
            if (sharedDomain != null) add("Block DNS requests and outbound routing to '$sharedDomain'.")
            if (sharedIp != null) add("Terminate active TCP/UDP sockets to IP $sharedIp.")
            add("Audit device background applications for unauthorized remote access trojans (RATs).")
        }

        return CorrelatedIncident(
            title = title,
            compositeRiskScore = compositeScore,
            severity = severity,
            participatingChannels = channels,
            events = events,
            sharedIndicators = sharedIndicators,
            narrativeExplanation = explanation,
            recommendedRemediation = remediation
        )
    }

    @Synchronized
    fun getActiveIncidents(): List<CorrelatedIncident> = incidents.toList()

    @Synchronized
    fun clear() {
        activeEvents.clear()
        incidents.clear()
    }
}
