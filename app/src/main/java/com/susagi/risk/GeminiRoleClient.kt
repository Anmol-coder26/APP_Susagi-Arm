package com.susagi.risk

import android.util.Log
import com.guardian.app.BuildConfig
import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * GeminiRoleClient
 *
 * Real-time semantic analysis client targeting Google Generative Language API.
 * Uses structured JSON Schema outputs with strict role labeling (victim vs scammer),
 * backend-managed credential proxying, single-flight concurrency gating, rate limit
 * exponential backoff, and on-device heuristic fallback when offline or unconfigured.
 */
class GeminiRoleClient(
    val apiKey: String = BuildConfig.GEMINI_API_KEY,
    private val modelName: String = BuildConfig.GEMINI_MODEL.ifBlank { "gemini-2.5-flash" },
    val backendUrl: String = BuildConfig.BACKEND_URL.trimEnd('/'),
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val TAG = "GeminiRoleClient"
    }

    private val isAnalysisInFlight = AtomicBoolean(false)
    private var lastAnalyzedResult: RiskResult? = null

    /**
     * Analyzes conversation transcript turns and returns structured [RiskResult].
     * Guarantees non-blocking, single-flight execution.
     */
    suspend fun analyzeConversation(transcriptBuffer: String): RiskResult = withContext(Dispatchers.IO) {
        val trimmed = transcriptBuffer.trim()
        if (trimmed.isBlank()) {
            return@withContext RiskResult(
                riskScore = 0,
                victimRole = "none",
                scammerRole = "none",
                roleConfidence = 1.0,
                explanation = "Conversation is quiet. Monitoring for speech...",
                source = "LOCAL_HEURISTIC",
                isFallback = false
            )
        }

        // Single-flight gating: skip if previous call is still in flight
        if (!isAnalysisInFlight.compareAndSet(false, true)) {
            CallDiagnostics.log("RISK-AI", "Analysis already in flight, returning cached posture", LogLevel.INFO)
            return@withContext lastAnalyzedResult ?: evaluateHeuristic(trimmed)
        }

        try {
            // Priority 1: Backend-Managed AI Evaluation (preferred for credential shielding)
            val backendResult = executeBackendEvaluation(trimmed)
            if (backendResult != null) {
                lastAnalyzedResult = backendResult
                return@withContext backendResult
            }

            // Priority 2: Direct Google Generative Language API if direct key is present
            if (apiKey.isNotBlank() && apiKey != "YOUR_GEMINI_API_KEY") {
                val directResult = executeGeminiWithRetry(trimmed)
                lastAnalyzedResult = directResult
                return@withContext directResult
            }

            // Priority 3: On-Device Heuristic Engine
            CallDiagnostics.log("RISK-AI", "Gemini unconfigured, engaging on-device heuristic engine", LogLevel.INFO)
            val heuristicResult = evaluateHeuristic(trimmed, isFallback = true, failureReason = "Cloud AI unconfigured")
            lastAnalyzedResult = heuristicResult
            heuristicResult
        } catch (e: Exception) {
            CallDiagnostics.log("RISK-AI", "Gemini evaluation error: ${e.message}, falling back to on-device heuristic", LogLevel.WARN)
            val fallback = evaluateHeuristic(trimmed, isFallback = true, failureReason = e.message ?: "Evaluation error")
            lastAnalyzedResult = fallback
            fallback
        } finally {
            isAnalysisInFlight.set(false)
        }
    }

    /**
     * Calls backend-managed AI evaluation proxy.
     */
    fun executeBackendEvaluation(transcript: String): RiskResult? {
        if (backendUrl.isBlank()) return null
        try {
            val url = "$backendUrl/risk/evaluate"
            val payload = JSONObject().apply {
                put("transcript", transcript)
            }
            val request = Request.Builder()
                .url(url)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val (code, bodyString, isSuccess) = httpClient.newCall(request).execute().use { response ->
                Triple(response.code, response.body?.string().orEmpty(), response.isSuccessful)
            }

            if (isSuccess && bodyString.isNotBlank()) {
                val json = JSONObject(bodyString)
                val score = json.optInt("risk_score", 0).coerceIn(0, 100)
                val victim = json.optString("victim_role", "none").lowercase()
                val scammer = json.optString("scammer_role", "none").lowercase()
                val conf = json.optDouble("role_confidence", 0.0).coerceIn(0.0, 1.0)
                val source = json.optString("source", "LOCAL_HEURISTIC")
                val isFallback = json.optBoolean("is_fallback", false)
                val failureReason = json.optString("failure_reason", "")
                val explanation = json.optString("explanation", "")

                val tacticsList = mutableListOf<String>()
                val tacticsArr = json.optJSONArray("tactics")
                if (tacticsArr != null) {
                    for (i in 0 until tacticsArr.length()) {
                        val t = tacticsArr.optString(i)
                        if (t.isNotBlank()) tacticsList.add(t)
                    }
                }

                CallDiagnostics.log("RISK-AI", "Evaluated via backend: score=$score, source=$source, fallback=$isFallback")
                return RiskResult(
                    riskScore = score,
                    victimRole = victim,
                    scammerRole = scammer,
                    roleConfidence = conf,
                    tactics = tacticsList,
                    explanation = explanation,
                    source = source,
                    isFallback = isFallback,
                    failureReason = failureReason,
                    analyzedAtMs = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            CallDiagnostics.log("RISK-AI", "Backend /risk/evaluate unreachable: ${e.message}")
        }
        return null
    }

    private fun executeGeminiWithRetry(transcript: String, maxRetries: Int = 2): RiskResult {
        var backoffMs = 800L
        var lastException: Exception? = null

        for (attempt in 0..maxRetries) {
            try {
                val url = "$BASE_URL/$modelName:generateContent?key=$apiKey"
                val requestPayload = JSONObject().apply {
                    put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", CoercionPrompt.SYSTEM_INSTRUCTION)
                            })
                        })
                    })
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "Analyze the following phone conversation transcript:\n\n$transcript")
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.1)
                        put("responseMimeType", "application/json")
                        put("responseSchema", CoercionPrompt.createJsonSchema())
                    })
                }

                val request = Request.Builder()
                    .url(url)
                    .addHeader("Content-Type", "application/json")
                    .post(requestPayload.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                val (code, bodyString, isSuccess) = httpClient.newCall(request).execute().use { response ->
                    Triple(response.code, response.body?.string().orEmpty(), response.isSuccessful)
                }

                if (isSuccess) {
                    return parseGeminiResponse(bodyString)
                }

                if (code == 429 || code in 500..599) {
                    CallDiagnostics.log("RISK-AI", "Gemini HTTP $code (attempt $attempt), backing off ${backoffMs}ms", LogLevel.WARN)
                    Thread.sleep(backoffMs)
                    backoffMs *= 2
                    continue
                }

                CallDiagnostics.log("RISK-AI", "Gemini HTTP $code error: ${bodyString.take(150)}", LogLevel.ERROR)
                return evaluateHeuristic(transcript, isFallback = true, failureReason = "HTTP $code")
            } catch (e: Exception) {
                lastException = e
                CallDiagnostics.log("RISK-AI", "Gemini network error (attempt $attempt): ${e.message}", LogLevel.WARN)
                if (attempt < maxRetries) {
                    Thread.sleep(backoffMs)
                    backoffMs *= 2
                }
            }
        }

        return evaluateHeuristic(transcript, isFallback = true, failureReason = lastException?.message ?: "Max retries exceeded")
    }

    /**
     * Parses the strict JSON structured response from Gemini.
     */
    fun parseGeminiResponse(jsonString: String): RiskResult {
        val root = JSONObject(jsonString)
        val candidates = root.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            throw IllegalStateException("Empty candidates array from Gemini")
        }

        val content = candidates.getJSONObject(0).optJSONObject("content")
        val parts = content?.optJSONArray("parts")
        if (parts == null || parts.length() == 0) {
            throw IllegalStateException("Empty parts array from Gemini candidate")
        }

        val rawText = parts.getJSONObject(0).optString("text", "{}").trim()
        val cleanedJson = rawText.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()

        val json = JSONObject(cleanedJson)
        val score = json.optInt("risk_score", 0).coerceIn(0, 100)
        val victim = json.optString("victim_role", "none").lowercase()
        val scammer = json.optString("scammer_role", "none").lowercase()
        val conf = json.optDouble("role_confidence", 0.0).coerceIn(0.0, 1.0)

        val tacticsList = mutableListOf<String>()
        val tacticsArr = json.optJSONArray("tactics")
        if (tacticsArr != null) {
            for (i in 0 until tacticsArr.length()) {
                val t = tacticsArr.optString(i)
                if (t.isNotBlank()) tacticsList.add(t)
            }
        }

        val explanation = json.optString("explanation", "Call monitored.")

        return RiskResult(
            riskScore = score,
            victimRole = victim,
            scammerRole = scammer,
            roleConfidence = conf,
            tactics = tacticsList,
            explanation = explanation,
            source = "GEMINI_CLOUD",
            isFallback = false,
            analyzedAtMs = System.currentTimeMillis()
        )
    }

    /**
     * On-device rule-based semantic heuristic.
     * Evaluates high-risk keywords across Hindi, Hinglish, and English dialogues.
     */
    fun evaluateHeuristic(
        transcript: String,
        isFallback: Boolean = false,
        failureReason: String = ""
    ): RiskResult {
        val lower = transcript.lowercase()

        // Keywords indicating caller authority impersonation & extortion
        val remoteScamKeywords = listOf(
            "digital arrest", "digital warrant", "arrest warrant", "cbi", "mumbai police",
            "police", "cyber crime", "cybercrime", "crime branch", "narcotics", "customs",
            "illegal package", "drugs found", "aadhaar misused", "sim blocked", "official secrets act",
            "disconnect", "do not disconnect", "video call disconnect मत करना", "कमरा बंद",
            "गिरफ्तार", "सीबीआई", "पुलिस", "नारकोटिक्स", "वारंट", "कोर्ट", "सुप्रीम कोर्ट",
            "trai", "inspector", "officer", "साइबर", "क्राइम", "अपराध", "डिजिटल अरेस्ट",
            "गिरफ्तारी", "अरेस्ट", "काटना मत", "फोन मत काटना", "डस्कनेक्ट", "डिस्कनेक्ट", "थाना", "अफसर"
        )

        // Keywords indicating financial demands
        val financialKeywords = listOf(
            "rbi verification", "safe account", "transfer money", "transfer", "rtgs", "imps",
            "upi transfer", "otp", "atm pin", "secret pin", "fifty thousand", "rupees", "रुपये",
            "खाता फ्रीज", "पैसे ट्रांसफर", "खाते में भेजो", "वेरिफिकेशन कोड", "क्लियरेंस", "फ्रीज",
            "ट्रांसफर", "रुपीस", "पैसे", "रुपए", "खाता", "खाते", "ओटीपी", "बैंक", "भेजो", "रकम",
            "फिफ्टी थाउजेंड", "पचास हजार"
        )

        var scamScore = 0
        val detectedTactics = mutableListOf<String>()

        val remoteTurns = lower.split("\n")
            .filter { it.contains("[remote") || it.contains("caller:") || it.contains("remote:") }
            .joinToString(" ")

        val textToCheck = if (remoteTurns.isNotBlank()) remoteTurns else lower

        val foundScamWords = remoteScamKeywords.filter { textToCheck.contains(it) }
        val foundFinancialWords = financialKeywords.filter { textToCheck.contains(it) }

        if (foundScamWords.isNotEmpty()) {
            scamScore += 45 + (foundScamWords.size * 10)
            detectedTactics.add("authority_impersonation")
            if (foundScamWords.any { it.contains("arrest") || it.contains("गिरफ्तार") || it.contains("वारंट") }) {
                detectedTactics.add("digital_arrest")
            }
            if (foundScamWords.any { it.contains("disconnect") }) {
                detectedTactics.add("intimidation")
            }
        }

        if (foundFinancialWords.isNotEmpty()) {
            scamScore += 35 + (foundFinancialWords.size * 10)
            detectedTactics.add("financial_coercion")
        }

        val finalScore = scamScore.coerceIn(0, 95)

        val (scammer, victim, confidence, explanation) = when {
            finalScore >= 60 -> {
                val prefix = if (isFallback) "[On-Device Shield] " else ""
                Quadruple(
                    "remote",
                    "local",
                    0.85,
                    prefix + "High-risk digital arrest / authority extortion patterns detected."
                )
            }
            finalScore in 25..59 -> {
                val prefix = if (isFallback) "[On-Device Shield] " else ""
                val reasonSuffix = if (isFallback && failureReason.isNotBlank()) " (Cloud AI unavailable: $failureReason)" else ""
                Quadruple(
                    "none",
                    "none",
                    0.50,
                    prefix + "Suspicious financial or regulatory keywords detected. Exercise caution." + reasonSuffix
                )
            }
            else -> {
                if (isFallback) {
                    Quadruple(
                        "none",
                        "none",
                        0.40,
                        "[Degraded / Cloud AI Unavailable] Monitoring call with on-device heuristic." +
                            if (failureReason.isNotBlank()) " ($failureReason)" else ""
                    )
                } else {
                    Quadruple(
                        "none",
                        "none",
                        0.90,
                        "Normal conversation. No threat detected."
                    )
                }
            }
        }

        return RiskResult(
            riskScore = finalScore,
            victimRole = victim,
            scammerRole = scammer,
            roleConfidence = confidence,
            tactics = detectedTactics,
            explanation = explanation,
            source = "LOCAL_HEURISTIC",
            isFallback = isFallback,
            failureReason = failureReason,
            analyzedAtMs = System.currentTimeMillis()
        )
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
