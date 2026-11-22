package com.susagi.history

import android.content.Context
import android.content.SharedPreferences
import com.susagi.safety.Assessment
import com.susagi.safety.Risk
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class IncidentRecord(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val channel: String, // "MESSAGE", "LINK", "UPI_QR", "VOICE", "NETWORK", "APK"
    val severity: String, // "HIGH", "REVIEW", "LOW"
    val riskScore: Int,
    val category: String,
    val title: String,
    val explanation: String,
    val detectedSignals: List<String>,
    val recommendations: List<String>,
    val sanitizedSnippet: String,
    val rawContentOmitted: Boolean = false
) {
    fun formattedDate(): String {
        return SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(timestampMs))
    }

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("timestampMs", timestampMs)
            put("channel", channel)
            put("severity", severity)
            put("riskScore", riskScore)
            put("category", category)
            put("title", title)
            put("explanation", explanation)
            put("detectedSignals", JSONArray(detectedSignals))
            put("recommendations", JSONArray(recommendations))
            put("sanitizedSnippet", sanitizedSnippet)
            put("rawContentOmitted", rawContentOmitted)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): IncidentRecord {
            val signals = mutableListOf<String>()
            val signalsArr = json.optJSONArray("detectedSignals")
            if (signalsArr != null) {
                for (i in 0 until signalsArr.length()) signals.add(signalsArr.getString(i))
            }

            val recs = mutableListOf<String>()
            val recsArr = json.optJSONArray("recommendations")
            if (recsArr != null) {
                for (i in 0 until recsArr.length()) recs.add(recsArr.getString(i))
            }

            return IncidentRecord(
                id = json.optString("id", UUID.randomUUID().toString()),
                timestampMs = json.optLong("timestampMs", System.currentTimeMillis()),
                channel = json.optString("channel", "MESSAGE"),
                severity = json.optString("severity", "LOW"),
                riskScore = json.optInt("riskScore", 0),
                category = json.optString("category", "General"),
                title = json.optString("title", "Assessment Event"),
                explanation = json.optString("explanation", ""),
                detectedSignals = signals,
                recommendations = recs,
                sanitizedSnippet = json.optString("sanitizedSnippet", ""),
                rawContentOmitted = json.optBoolean("rawContentOmitted", false)
            )
        }
    }
}

/**
 * Cross-channel persistent, local incident history store for SuSagi Guardian V2.
 * Manages chronological threat logs with privacy redaction, single-item deletion,
 * bulk wipe, and sanitized report export.
 */
class IncidentHistoryStore(private val context: Context? = null) {

    private val inMemoryList = mutableListOf<IncidentRecord>()
    private val prefs: SharedPreferences? by lazy {
        context?.getSharedPreferences("susagi_incident_history", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_INCIDENTS_JSON = "incidents_json_array"
        private const val MAX_STORED_INCIDENTS = 100

        @Volatile
        private var instance: IncidentHistoryStore? = null

        fun getInstance(context: Context): IncidentHistoryStore {
            return instance ?: synchronized(this) {
                instance ?: IncidentHistoryStore(context.applicationContext).also { instance = it }
            }
        }
    }

    @Synchronized
    fun recordAssessment(
        channel: String,
        title: String,
        assessment: Assessment,
        rawSnippet: String = "",
        omitRawContent: Boolean = false
    ): IncidentRecord {
        val severity = when (assessment.risk) {
            Risk.HIGH -> "HIGH"
            Risk.REVIEW -> "REVIEW"
            Risk.UNKNOWN -> "REVIEW"
            Risk.LOW -> "LOW"
        }
        val score = when (assessment.risk) {
            Risk.HIGH -> 90
            Risk.REVIEW, Risk.UNKNOWN -> 45
            Risk.LOW -> 5
        }
        val sanitized = if (omitRawContent) "[Content omitted by user privacy setting]"
        else rawSnippet.take(160).replace(Regex("""\b\d{4,8}\b"""), "******") // Mask OTP/PIN numbers

        val record = IncidentRecord(
            channel = channel,
            severity = severity,
            riskScore = score,
            category = assessment.categories.firstOrNull() ?: "General",
            title = title,
            explanation = assessment.explanation,
            detectedSignals = assessment.categories,
            recommendations = assessment.recommendations,
            sanitizedSnippet = sanitized,
            rawContentOmitted = omitRawContent
        )

        addRecord(record)
        return record
    }

    @Synchronized
    fun addRecord(record: IncidentRecord) {
        val current = getAllRecords().toMutableList()
        current.add(0, record) // Most recent first
        val trimmed = if (current.size > MAX_STORED_INCIDENTS) current.take(MAX_STORED_INCIDENTS) else current
        persist(trimmed)
    }

    @Synchronized
    fun getAllRecords(): List<IncidentRecord> {
        val sp = prefs
        if (sp == null) return inMemoryList.toList()

        val jsonStr = sp.getString(KEY_INCIDENTS_JSON, null) ?: return inMemoryList.toList()
        return try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<IncidentRecord>()
            for (i in 0 until arr.length()) {
                list.add(IncidentRecord.fromJson(arr.getJSONObject(i)))
            }
            list
        } catch (_: Exception) {
            inMemoryList.toList()
        }
    }

    @Synchronized
    fun deleteRecord(id: String) {
        val updated = getAllRecords().filterNot { it.id == id }
        persist(updated)
    }

    @Synchronized
    fun clearAll() {
        inMemoryList.clear()
        prefs?.edit()?.remove(KEY_INCIDENTS_JSON)?.apply()
    }

    @Synchronized
    private fun persist(list: List<IncidentRecord>) {
        inMemoryList.clear()
        inMemoryList.addAll(list)

        val sp = prefs ?: return
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString(KEY_INCIDENTS_JSON, arr.toString()).apply()
    }

    /**
     * Exports a sanitized plain-text report suitable for sharing with trusted contacts
     * or reporting to national cybercrime authorities (1930 / cybercrime.gov.in).
     */
    fun exportSanitizedReport(recordId: String? = null): String {
        val records = if (recordId != null) {
            getAllRecords().filter { it.id == recordId }
        } else {
            getAllRecords().take(5)
        }

        if (records.isEmpty()) return "SuSagi Guardian Security Report\nNo incidents recorded."

        return buildString {
            appendLine("=== SUSAGI GUARDIAN INCIDENT REPORT ===")
            appendLine("Report Generated: ${SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.getDefault()).format(Date())}")
            appendLine("Engine: On-Device Heuristic + Offline ML Intelligence")
            appendLine("Official Filing Helpline: India Cybercrime 1930 | cybercrime.gov.in")
            appendLine("=========================================\n")

            records.forEachIndexed { idx, rec ->
                appendLine("INCIDENT #${idx + 1} [${rec.severity}] - ${rec.title}")
                appendLine("Channel: ${rec.channel}")
                appendLine("Timestamp: ${rec.formattedDate()}")
                appendLine("Risk Score: ${rec.riskScore} / 100")
                appendLine("Threat Category: ${rec.category}")
                if (rec.detectedSignals.isNotEmpty()) {
                    appendLine("Signals: ${rec.detectedSignals.joinToString(", ")}")
                }
                appendLine("Explanation: ${rec.explanation}")
                if (rec.recommendations.isNotEmpty()) {
                    appendLine("Recommended Action: ${rec.recommendations.first()}")
                }
                if (rec.sanitizedSnippet.isNotBlank()) {
                    appendLine("Sanitized Snippet: \"${rec.sanitizedSnippet}\"")
                }
                appendLine("-----------------------------------------\n")
            }
        }
    }
}
