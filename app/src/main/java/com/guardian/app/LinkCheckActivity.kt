package com.guardian.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.protect.RegionalScamKeywords
import com.guardian.app.ui.theme.GuardianTheme
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxDangerSoft
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxPrimarySoft
import com.guardian.app.ui.theme.GxAccentAI
import com.guardian.app.ui.theme.GxAccentAISoft
import com.guardian.app.ui.theme.GxSafe
import com.guardian.app.ui.theme.GxSafeSoft
import com.guardian.app.ui.theme.GxShapeMd
import com.guardian.app.ui.theme.GxShapePill
import com.guardian.app.ui.theme.GxShapeSm
import com.guardian.app.ui.theme.GxSurface
import com.guardian.app.ui.theme.GxSurfaceAlt
import com.guardian.app.ui.theme.GxTextHi
import com.guardian.app.ui.theme.GxTextLo
import com.guardian.app.ui.theme.GxTextMid
import com.guardian.app.ui.theme.GxWarning
import com.guardian.app.ui.theme.GxWarningSoft
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.lifecycle.lifecycleScope
import com.susagi.edgeai.ModelManifest
import com.susagi.safety.Action
import com.susagi.safety.Assessment
import com.susagi.safety.Evidence
import com.susagi.safety.ExplanationResult
import com.susagi.safety.LlamaModelState
import com.susagi.safety.LocalEvidenceExplainer
import com.susagi.safety.Risk
import com.susagi.safety.SafetyBarrier
import com.susagi.safety.SharedDestination
import com.susagi.safety.Signal
import com.susagi.safety.hindiGuidance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

data class LinkVerdict(
    val url: String,
    val status: RiskStatus,
    val title: String,
    val detail: String,
    val riskScore: Int = when (status) {
        RiskStatus.High -> 85
        RiskStatus.Suspicious -> 50
        RiskStatus.Low -> 10
    },
    val supportingEvidence: List<String> = emptyList(),
    val recommendedAction: String = if (status == RiskStatus.Low) "Inspect destination carefully." else "Avoid opening this destination.",
    val explicitLimitations: String = "Offline static heuristic evaluation. Destination DNS, SSL certificates, and server-side redirects are not verified without internet connectivity.",
    val detectorRisk: Risk = when (status) {
        RiskStatus.High -> Risk.HIGH
        RiskStatus.Suspicious -> Risk.REVIEW
        RiskStatus.Low -> Risk.LOW
    },
    val destinationHost: String? = null
)

sealed class SharedPayload {
    object Empty : SharedPayload()
    data class ViewUrl(val url: String) : SharedPayload()
    data class SharedContent(
        val rawText: String,
        val urls: List<String>
    ) : SharedPayload()
}

data class UnifiedSecurityAssessment(
    val actualRiskScore: Int,
    val status: RiskStatus,
    val categoryTitle: String,
    val plainExplanationEn: String,
    val plainExplanationHi: String,
    val detectedSignals: List<String>,
    val evaluatedUrls: List<LinkVerdict>,
    val engineLabel: String = "Astra SafetyBarrier Offline Heuristic Rule Engine",
    val isTextOnly: Boolean = false,
    val rawTextSnippet: String = "",
    val riskLevel: Risk = when (status) {
        RiskStatus.High -> Risk.HIGH
        RiskStatus.Suspicious -> Risk.REVIEW
        RiskStatus.Low -> Risk.LOW
    },
    val uncertaintyNote: String = "Offline inspection cannot establish ownership, reputation or the destination after navigation.",
    val rawAssessment: Assessment? = null
)

class LinkCheckActivity : ComponentActivity() {

    private val assessmentState = mutableStateOf<UnifiedSecurityAssessment?>(null)
    private val isAnalyzing = mutableStateOf<Boolean>(true)
    private var analysisJob: Job? = null
    private var activeAnalysisId: Long = 0L

    // On-Device Llama 3.2 1B SpinQuant Explainer State
    private val llamaState = mutableStateOf(LlamaModelState.NOT_PROVISIONED)
    private val llamaExplanation = mutableStateOf<String?>(null)
    private val llamaNativeOutput = mutableStateOf<String?>(null)
    private val llamaFailureReason = mutableStateOf<String?>(null)
    private var llamaJob: Job? = null
    private var explainer: LocalEvidenceExplainer? = null

    override fun onDestroy() {
        super.onDestroy()
        analysisJob?.cancel()
        llamaJob?.cancel()
        val exp = explainer
        explainer = null
        if (exp != null) {
            lifecycleScope.launch(Dispatchers.IO) {
                try { exp.release() } catch (_: Throwable) {}
            }
        }
    }

    private fun updateInitialLlamaState() {
        val modelsDir = File(filesDir, "models")
        val spec = ModelManifest.LLAMA_3_2_1B_SPINQUANT
        val modelFile = File(modelsDir, spec.modelFileName)
        val tokenizerFile = File(modelsDir, spec.tokenizerFileName)

        llamaState.value = when {
            !modelFile.exists() || !tokenizerFile.exists() -> LlamaModelState.NOT_PROVISIONED
            modelFile.length() != spec.modelSizeBytes || tokenizerFile.length() != spec.tokenizerSizeBytes -> LlamaModelState.INVALID_ASSETS
            else -> LlamaModelState.NOT_PROVISIONED
        }
    }

    private fun triggerLlamaExplanation(assessment: UnifiedSecurityAssessment) {
        val raw = assessment.rawAssessment
        if (raw == null || raw.evidence.isEmpty()) {
            llamaState.value = LlamaModelState.FAILED
            llamaFailureReason.value = "No detector evidence triggers available to explain."
            return
        }

        llamaJob?.cancel()
        llamaJob = lifecycleScope.launch(Dispatchers.Default) {
            val modelsDir = File(filesDir, "models")
            val spec = ModelManifest.LLAMA_3_2_1B_SPINQUANT
            val modelFile = File(modelsDir, spec.modelFileName)
            val tokenizerFile = File(modelsDir, spec.tokenizerFileName)

            if (!modelFile.exists() || !tokenizerFile.exists()) {
                withContext(Dispatchers.Main) {
                    llamaState.value = LlamaModelState.NOT_PROVISIONED
                    llamaFailureReason.value = "Llama 3.2 1B SpinQuant model assets are not provisioned on this device storage."
                }
                return@launch
            }

            if (modelFile.length() != spec.modelSizeBytes || tokenizerFile.length() != spec.tokenizerSizeBytes) {
                withContext(Dispatchers.Main) {
                    llamaState.value = LlamaModelState.INVALID_ASSETS
                    llamaFailureReason.value = "Model files failed size verification (${modelFile.length()} vs ${spec.modelSizeBytes} bytes)."
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                llamaState.value = LlamaModelState.LOADING
                llamaFailureReason.value = null
            }

            val localExplainer = explainer ?: LocalEvidenceExplainer(modelsDir).also { explainer = it }

            withContext(Dispatchers.Main) {
                llamaState.value = LlamaModelState.GENERATING
            }

            val result = localExplainer.explain(raw)

            withContext(Dispatchers.Main) {
                when (result) {
                    is ExplanationResult.Generated -> {
                        llamaState.value = LlamaModelState.GENERATED
                        llamaExplanation.value = result.text
                        llamaNativeOutput.value = result.nativeOutput
                    }
                    is ExplanationResult.Unavailable -> {
                        val reason = result.reason
                        llamaState.value = when {
                            reason.startsWith("NOT_PROVISIONED") -> LlamaModelState.NOT_PROVISIONED
                            reason.startsWith("INVALID_ASSETS") -> LlamaModelState.INVALID_ASSETS
                            else -> LlamaModelState.FAILED
                        }
                        llamaFailureReason.value = reason
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        processIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIntent(intent)
    }

    private fun processIntent(incomingIntent: Intent?) {
        // Anti-recursion check: prevent recursive intent bouncing if SuSagi opened an external browser
        if (incomingIntent?.getBooleanExtra(EXTRA_PROCESSED_BY_GUARDIAN, false) == true) {
            val url = extractUrlFromIntent(incomingIntent)
            if (url.isNotBlank()) {
                startActivity(com.susagi.browser.SafeBrowsingActivity.createIntent(this, url))
            }
            finish()
            return
        }

        val payload = extractSharedPayload(incomingIntent)

        // Privacy-first logging: log metadata only, never raw PII or message body
        when (payload) {
            is SharedPayload.Empty -> Log.d(TAG, "Incoming intent: Empty or unsupported payload")
            is SharedPayload.ViewUrl -> Log.d(TAG, "Incoming intent: ACTION_VIEW URL (length: ${payload.url.length})")
            is SharedPayload.SharedContent -> Log.d(TAG, "Incoming intent: ACTION_SEND text (chars: ${payload.rawText.length}, urls: ${payload.urls.size})")
        }

        // Cancel previous in-flight analysis job to avoid race conditions or stale results
        analysisJob?.cancel()
        val currentId = System.nanoTime()
        activeAnalysisId = currentId
        isAnalyzing.value = true

        analysisJob = lifecycleScope.launch(Dispatchers.Default) {
            val assessment = assessPayloadAsync(payload)
            if (isActive && activeAnalysisId == currentId) {
                withContext(Dispatchers.Main) {
                    assessmentState.value = assessment
                    isAnalyzing.value = false
                    updateInitialLlamaState()
                }
                try {
                    val raw = assessment.rawAssessment
                    if (raw != null) {
                        val channel = when (payload) {
                            is SharedPayload.ViewUrl -> if (payload.url.startsWith("upi://", ignoreCase = true)) "UPI_QR" else "LINK"
                            is SharedPayload.SharedContent -> if (payload.urls.isNotEmpty()) "LINK" else "MESSAGE"
                            else -> "MESSAGE"
                        }
                        val rawText = when (payload) {
                            is SharedPayload.ViewUrl -> payload.url
                            is SharedPayload.SharedContent -> payload.rawText
                            else -> ""
                        }
                        com.susagi.history.IncidentHistoryStore.getInstance(applicationContext).recordAssessment(
                            channel = channel,
                            title = assessment.categoryTitle,
                            assessment = raw,
                            rawSnippet = rawText
                        )
                    }
                } catch (_: Exception) {}
            }
        }

        setContent {
            GuardianTheme {
                val currentAssessment = assessmentState.value
                val analyzing = isAnalyzing.value

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (analyzing || currentAssessment == null) {
                        Surface(
                            shape = GxShapeMd,
                            color = GxSurface,
                            border = BorderStroke(1.dp, GxBorder),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                CircularProgressIndicator(color = GxPrimary, modifier = Modifier.size(24.dp))
                                Text(
                                    "Analyzing content with Astra SafetyBarrier...",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = GxTextHi
                                )
                            }
                        }
                    } else {
                        UnifiedSecurityDialog(
                            assessment = currentAssessment,
                            llamaState = llamaState.value,
                            llamaExplanation = llamaExplanation.value,
                            llamaNativeOutput = llamaNativeOutput.value,
                            llamaFailureReason = llamaFailureReason.value,
                            onInvokeLlama = {
                                triggerLlamaExplanation(currentAssessment)
                            },
                            onOpenInSafeBrowser = { urlToOpen ->
                                startActivity(com.susagi.browser.SafeBrowsingActivity.createIntent(this@LinkCheckActivity, urlToOpen))
                                finish()
                            },
                            onProceedToUrl = { urlToOpen ->
                                // Guard against stale callbacks: Verify destination belongs to active assessment
                                if (currentAssessment.evaluatedUrls.any { it.url == urlToOpen }) {
                                    openInExternalBrowser(urlToOpen)
                                    finish()
                                }
                            },
                            onDismiss = {
                                finish()
                            },
                            onCopyReport = {
                                copyReportToClipboard(currentAssessment)
                            },
                            onAlertFamily = {
                                shareFamilyAlert(currentAssessment)
                            }
                        )
                    }
                }
            }
        }
    }

    private fun copyReportToClipboard(assessment: UnifiedSecurityAssessment) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val report = buildString {
            appendLine("🛡️ SuSagi Guardian Security Report")
            appendLine("Verdict: ${assessment.categoryTitle} [${assessment.riskLevel.name} RISK]")
            appendLine("Engine: ${assessment.engineLabel}")
            appendLine("Guidance: ${assessment.plainExplanationEn}")
            if (assessment.detectedSignals.isNotEmpty()) {
                appendLine("Signals: ${assessment.detectedSignals.joinToString(", ")}")
            }
            if (assessment.evaluatedUrls.isNotEmpty()) {
                appendLine("Destinations Inspected: ${assessment.evaluatedUrls.size}")
                assessment.evaluatedUrls.forEach { v ->
                    appendLine("- ${v.url} [${v.status}]")
                    if (v.supportingEvidence.isNotEmpty()) {
                        appendLine("  Evidence: ${v.supportingEvidence.joinToString("; ")}")
                    }
                    appendLine("  Action: ${v.recommendedAction}")
                }
            }
            appendLine("Limitations: Static offline evaluation; live DNS/redirects unverified without internet.")
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("SuSagi Security Report", report))
        Toast.makeText(this, "Security assessment copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun shareFamilyAlert(assessment: UnifiedSecurityAssessment) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "⚠️ SuSagi Security Alert: ${assessment.categoryTitle}")
            putExtra(
                Intent.EXTRA_TEXT,
                "⚠️ SuSagi Guardian Alert:\nThreat: ${assessment.categoryTitle} [${assessment.riskLevel.name} RISK]\n\n" +
                        "Advice: ${assessment.plainExplanationEn}\n\n" +
                        "हिंदी: ${assessment.plainExplanationHi}\n\n" +
                        "Stay safe! Do not send money, share OTPs, or click unverified links."
            )
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            startActivity(Intent.createChooser(shareIntent, "Alert Family Member via"))
        } catch (_: Exception) {
            Toast.makeText(this, "Could not open share chooser", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openInExternalBrowser(url: String) {
        val uri = try {
            val parsed = Uri.parse(url)
            if (parsed.scheme == null) Uri.parse("https://$url") else parsed
        } catch (_: Exception) {
            Uri.parse(url)
        }

        val isWeb = uri.scheme?.lowercase(Locale.ROOT) in listOf("http", "https")
        val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            if (isWeb) {
                addCategory(Intent.CATEGORY_BROWSABLE)
            }
            putExtra(EXTRA_PROCESSED_BY_GUARDIAN, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }

        if (isWeb) {
            val resolveInfos = packageManager.queryIntentActivities(browserIntent, PackageManager.MATCH_ALL)
            val targetApp = resolveInfos.firstOrNull {
                val pkg = it.activityInfo.packageName
                pkg != packageName && !pkg.contains("guardian") && !pkg.contains("susagi")
            }

            if (targetApp != null) {
                browserIntent.setClassName(targetApp.activityInfo.packageName, targetApp.activityInfo.name)
                try {
                    startActivity(browserIntent)
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "Cannot launch targeted browser: ${e.message}")
                }
            }
        }

        // If no external browser is available or launch failed, fall back safely to SuSagi Safe Browser
        try {
            startActivity(com.susagi.browser.SafeBrowsingActivity.createIntent(this, url))
        } catch (e: Exception) {
            Log.e(TAG, "Cannot launch safe browser fallback: ${e.message}")
        }
    }

    companion object {
        const val EXTRA_PROCESSED_BY_GUARDIAN = "com.guardian.app.PROCESSED_BY_GUARDIAN"
        private const val TAG = "SuSagiShareShield"

        val URL_REGEX = Regex(
            """(?i)(?:https?://|www\.|upi://|javascript:|data:|intent:|file:|content:)[a-zA-Z0-9\-\._~:/?#\[\]@!$&'()*+,;=%]+"""
        )

        private val IP_REGEX = Regex(
            "^(?:\\d{1,3}\\.){3}\\d{1,3}$"
        )

        fun extractUrlFromIntent(intent: Intent?): String {
            if (intent == null) return ""
            val payload = extractSharedPayload(intent)
            return when (payload) {
                is SharedPayload.ViewUrl -> payload.url
                is SharedPayload.SharedContent -> payload.urls.firstOrNull().orEmpty()
                is SharedPayload.Empty -> ""
            }
        }

        fun cleanTrailingPunctuation(rawUrl: String): String {
            var url = rawUrl.trim()
            while (url.endsWith(".") || url.endsWith(",") || url.endsWith("!") || url.endsWith("?") || url.endsWith(";") || url.endsWith(")")) {
                url = url.substring(0, url.length - 1).trim()
            }
            return url
        }

        fun extractAllUrls(text: String): List<String> {
            if (text.isBlank()) return emptyList()
            return URL_REGEX.findAll(text).map { match ->
                var url = cleanTrailingPunctuation(match.value)
                if (url.startsWith("www.", ignoreCase = true)) {
                    url = "https://$url"
                }
                url
            }.filter { it.isNotBlank() }.distinct().toList()
        }

        fun extractUrlFromText(text: String): String {
            return extractAllUrls(text).firstOrNull().orEmpty()
        }

        fun extractSharedPayload(intent: Intent?): SharedPayload {
            if (intent == null) return SharedPayload.Empty

            if (intent.action == Intent.ACTION_VIEW) {
                val dataStr = intent.dataString?.trim().orEmpty()
                if (dataStr.isNotBlank()) {
                    return SharedPayload.ViewUrl(url = dataStr)
                }
            }

            val extraText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            val clipText = if (intent.clipData != null && intent.clipData!!.itemCount > 0) {
                intent.clipData!!.getItemAt(0).text?.toString()?.trim().orEmpty()
            } else ""
            val clipUri = if (intent.clipData != null && intent.clipData!!.itemCount > 0) {
                intent.clipData!!.getItemAt(0).uri?.toString()?.trim().orEmpty()
            } else ""
            val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.trim().orEmpty()

            val combinedText = when {
                extraText.isNotBlank() && subject.isNotBlank() && !extraText.contains(subject) -> "$subject\n$extraText"
                extraText.isNotBlank() -> extraText
                clipText.isNotBlank() -> clipText
                clipUri.isNotBlank() -> clipUri
                else -> ""
            }

            if (combinedText.isBlank()) {
                return SharedPayload.Empty
            }

            val urls = extractAllUrls(combinedText)
            return SharedPayload.SharedContent(
                rawText = combinedText,
                urls = urls
            )
        }

        fun extractHost(url: String): String {
            val clean = url.trim().lowercase(Locale.ROOT)
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("upi://")
            val afterUser = if (clean.contains("@")) clean.substringAfter("@") else clean
            return afterUser.split('/', ':', '?', '#').firstOrNull()?.trim() ?: ""
        }

        fun extractUserInfo(url: String): String? {
            val clean = url.trim()
                .removePrefix("http://")
                .removePrefix("https://")
                .removePrefix("HTTP://")
                .removePrefix("HTTPS://")
            val beforeSlash = clean.split('/', '?', '#').firstOrNull() ?: ""
            return if (beforeSlash.contains("@")) {
                beforeSlash.substringBefore("@")
            } else {
                null
            }
        }

        fun isTrustedHost(host: String, trustedRoots: List<String>): Boolean {
            val cleanHost = host.lowercase(Locale.ROOT).trim().removeSuffix(".")
            if (cleanHost.isBlank()) return false
            return trustedRoots.any { root ->
                val cleanRoot = root.lowercase(Locale.ROOT).trim().removeSuffix(".")
                cleanHost == cleanRoot || cleanHost.endsWith(".$cleanRoot")
            }
        }

        fun hasMixedScriptHomoglyphs(label: String): Boolean {
            var hasLatin = false
            var hasNonLatinConfusable = false
            for (ch in label) {
                if ((ch in 'a'..'z') || (ch in 'A'..'Z') || (ch in '0'..'9') || ch == '-') {
                    hasLatin = true
                } else {
                    val script = Character.UnicodeScript.of(ch.code)
                    if (script == Character.UnicodeScript.CYRILLIC || script == Character.UnicodeScript.GREEK) {
                        hasNonLatinConfusable = true
                    }
                }
            }
            return hasLatin && hasNonLatinConfusable
        }

        fun normalizeHomoglyphs(input: String): String {
            val sb = StringBuilder(input.length)
            for (ch in input) {
                when (ch) {
                    '\u0430', '\u0410' -> sb.append('a') // Cyrillic a
                    '\u0435', '\u0415' -> sb.append('e') // Cyrillic e
                    '\u043E', '\u041E' -> sb.append('o') // Cyrillic o
                    '\u0440', '\u0420' -> sb.append('p') // Cyrillic p
                    '\u0441', '\u0421' -> sb.append('c') // Cyrillic c
                    '\u0443', '\u0423' -> sb.append('y') // Cyrillic y
                    '\u0445', '\u0425' -> sb.append('x') // Cyrillic x
                    '\u0456', '\u0406' -> sb.append('i') // Cyrillic i
                    '\u03BF', '\u039F' -> sb.append('o') // Greek omicron
                    '\u03B1', '\u0391' -> sb.append('a') // Greek alpha
                    else -> sb.append(ch)
                }
            }
            return sb.toString()
        }

        fun evaluateLinkSafety(url: String): LinkVerdict {
            val trimmed = url.trim()
            val lower = trimmed.lowercase(Locale.ROOT)

            // -1. Threat: Unsupported / Dangerous Schemes
            val unsupportedSchemes = listOf("javascript:", "file:", "content:", "data:", "intent:")
            for (s in unsupportedSchemes) {
                if (lower.startsWith(s)) {
                    return LinkVerdict(
                        url = url,
                        status = RiskStatus.High,
                        title = "🚨 UNSUPPORTED / POTENTIALLY DANGEROUS SCHEME",
                        detail = "The URI scheme '$s' is not an ordinary web destination and is frequently abused for local script execution, intent hijacking, or sandbox escapes.",
                        riskScore = 95,
                        supportingEvidence = listOf("Scheme: $s", "High-risk protocol handler", "Bypasses standard browser web sandbox"),
                        recommendedAction = "Do not open or execute. Close the overlay immediately.",
                        explicitLimitations = "Offline scheme classification. Local file contents or scripts are not executed."
                    )
                }
            }

            // -0.5. Threat: UPI Direct Payment Link Scams
            if (lower.startsWith("upi://")) {
                val query = trimmed.substringAfter('?', "")
                fun extractParam(key: String): String {
                    return try {
                        query.split('&').mapNotNull { pair ->
                            val parts = pair.split('=', limit = 2)
                            if (parts.size == 2 && parts[0].equals(key, ignoreCase = true)) {
                                java.net.URLDecoder.decode(parts[1], "UTF-8")
                            } else null
                        }.firstOrNull().orEmpty()
                    } catch (_: Exception) { "" }
                }

                val pa = extractParam("pa")
                val pn = extractParam("pn")
                val am = extractParam("am")

                val isGovOrUtilityName = pn.contains("electricity", true) || pn.contains("bill", true) ||
                        pn.contains("police", true) || pn.contains("cbi", true) || pn.contains("rbi", true) ||
                        pn.contains("sbi", true) || pn.contains("bank", true) || pn.contains("challan", true)

                val isSuspiciousVpa = pa.isNotBlank() && isGovOrUtilityName && !pa.contains("gov") && !pa.contains("billdesk") && !pa.contains("sbi")

                if (isSuspiciousVpa) {
                    return LinkVerdict(
                        url = url,
                        status = RiskStatus.High,
                        title = "🚨 SUSPICIOUS UNVERIFIED UPI PAYMENT REQUEST",
                        detail = "This UPI link requests payment to individual address '$pa' under the institutional name '$pn'. Legitimate government and utility agencies do not collect payments to personal UPI handles.",
                        riskScore = 94,
                        supportingEvidence = listOf("Scheme: upi://pay", "VPA: $pa", "Impersonated Name: $pn", if (am.isNotBlank()) "Requested Amount: ₹$am" else "Open Collect Request"),
                        recommendedAction = "Do not authorize payment or enter your UPI PIN. Official bills should only be paid through verified consumer portals.",
                        explicitLimitations = "Offline UPI parameter analysis. The live operational status of this VPA cannot be confirmed without payment gateway connectivity."
                    )
                } else if (pa.isNotBlank()) {
                    return LinkVerdict(
                        url = url,
                        status = RiskStatus.Suspicious,
                        title = "⚠️ UNVERIFIED DIRECT UPI LINK",
                        detail = "This link initiates a direct UPI payment request to address '$pa'. Verify the recipient identity before proceeding.",
                        riskScore = 55,
                        supportingEvidence = listOf("Scheme: upi://pay", "Recipient VPA: $pa", "Display Name: ${pn.ifBlank { "Unspecified" }}"),
                        recommendedAction = "Verify the recipient's phone number or invoice number directly before entering your UPI PIN.",
                        explicitLimitations = "Offline static check. Does not connect to NPCI or banking servers."
                    )
                }
            }

            val host = extractHost(trimmed)
            val userInfo = extractUserInfo(trimmed)
            val path = try {
                val afterScheme = lower.removePrefix("http://").removePrefix("https://")
                val slashIdx = afterScheme.indexOf('/')
                if (slashIdx != -1) afterScheme.substring(slashIdx).split('?', '#').firstOrNull() ?: "" else ""
            } catch (_: Exception) { "" }

            // 0. Threat: Malformed or Empty Hostname
            if (host.isBlank() || host.startsWith(".") || host.endsWith(".") || host.contains("..") ||
                (!host.contains(".") && host != "localhost")) {
                return LinkVerdict(
                    url = url,
                    status = RiskStatus.Suspicious,
                    title = "⚠️ MALFORMED OR EMPTY HOSTNAME",
                    detail = "The web address '$url' does not specify a valid, well-formed host domain.",
                    riskScore = 55,
                    supportingEvidence = listOf("Malformed hostname syntax", "Host: '$host'"),
                    recommendedAction = "Do not open. The web address structure is invalid or corrupt.",
                    explicitLimitations = "Offline syntactic validation only."
                )
            }

            // 1. Critical Threat: Direct IP Hostname (classic malware C2 & phishing tactic)
            if (host.isNotBlank() && IP_REGEX.matches(host)) {
                return LinkVerdict(
                    url = url,
                    status = RiskStatus.High,
                    title = "🚨 UNVERIFIED IP HOST DETECTED",
                    detail = "This link points directly to a raw IP address ($host) rather than a registered domain name. Legitimate institutions never use raw IP URLs.",
                    riskScore = 88,
                    supportingEvidence = listOf("Numeric IP format ($host)", "No registered domain name", "Commonly used in C2 and credential phishing"),
                    recommendedAction = "Do not open. Legitimate institutions never conduct business on raw IP addresses.",
                    explicitLimitations = "Offline heuristic check. IP geolocation and reverse DNS not queried."
                )
            }

            // 2. Critical Threat: UserInfo URL Deception (e.g. https://sbi.co.in@evil-server.com/login)
            if (!userInfo.isNullOrBlank()) {
                return LinkVerdict(
                    url = url,
                    status = RiskStatus.High,
                    title = "🚨 URL IMPERSONATION SPOOF DETECTED",
                    detail = "This link uses user-info prefixing ($userInfo) to disguise the true destination domain ($host).",
                    riskScore = 90,
                    supportingEvidence = listOf("UserInfo prefix: '$userInfo'", "True destination: '$host'", "Credential harvesting deception technique"),
                    recommendedAction = "Do not open. The link is designed to mislead you regarding the destination server.",
                    explicitLimitations = "Offline URL syntax evaluation."
                )
            }

            // 3. Critical Threat: Direct APK or Executable Sideloading
            if (lower.endsWith(".apk") || lower.contains(".apk?") || path.endsWith(".apk") ||
                lower.endsWith(".exe") || lower.endsWith(".dex") || lower.endsWith(".vbs") || lower.endsWith(".sh")) {
                return LinkVerdict(
                    url = url,
                    status = RiskStatus.High,
                    title = "🚨 MALICIOUS PACKAGE / APK SIDELOAD DETECTED",
                    detail = "This link initiates an unauthorized APK or binary download outside of Google Play, frequently used in RAT (Remote Access Trojan) fraud.",
                    riskScore = 92,
                    supportingEvidence = listOf("Direct package extension (.apk/.exe)", "Bypasses Google Play Store protection", "Common vector for Android RATs and trojans"),
                    recommendedAction = "Do not download or install this application package. Unverified APKs can compromise your device.",
                    explicitLimitations = "Offline URL pattern analysis. Binary payload is not downloaded or scanned."
                )
            }

            // 4. Critical Threat: Phishing Keywords in Path or Host
            val highRiskKeywords = listOf(
                "login-kyc", "rbi-claim", "rbi-alert", "update-pan", "bank-verify", "digital-arrest",
                "police-notice", "cbi-warrant", "customs-duty", "electricity-bill-pay", "aadhaar-link",
                "anydesk", "teamviewer", "quicksupport", "free-gift", "fastag-fine", "chalan-pay",
                "epfo-claim", "telegram-task", "sbi-kyc", "icici-kyc", "hdfc-update", "paytm-kyc"
            )
            for (keyword in highRiskKeywords) {
                if (lower.contains(keyword)) {
                    return LinkVerdict(
                        url = url,
                        status = RiskStatus.High,
                        title = "🚨 DANGEROUS PHISHING / EXTORTION LINK",
                        detail = "This destination has been flagged for active banking credential harvesting, digital arrest extortion, or fraudulent payment gateway impersonation.",
                        riskScore = 86,
                        supportingEvidence = listOf("Phishing keyword '$keyword' detected in path/host", "High correlation with credential harvesting campaigns"),
                        recommendedAction = "Do not enter login credentials, OTPs, or financial details on this website.",
                        explicitLimitations = "Offline keyword/pattern heuristic check."
                    )
                }
            }

            // 5. Critical Threat: Subdomain Impersonation of Financial/Gov Institutions
            val brandPrefixes = listOf("sbi", "hdfc", "icici", "pnb", "axis", "rbi", "incometax", "epfo", "cyberpolice", "uidai")
            val trustedBankAndGovRoots = listOf(
                "sbi.co.in", "onlinesbi.sbi", "rbi.org.in", "hdfcbank.com", "icicibank.com",
                "axisbank.com", "pnbindia.in", "bankofbaroda.in", "kotak.com", "gov.in", "nic.in"
            )

            val decodedHost = try {
                java.net.IDN.toUnicode(host)
            } catch (_: Exception) { host }
            val normalizedDecodedHost = normalizeHomoglyphs(decodedHost.lowercase(Locale.ROOT))

            for (brand in brandPrefixes) {
                val matchesHost = host.contains(brand) || normalizedDecodedHost.contains(brand)
                if (matchesHost && !isTrustedHost(host, trustedBankAndGovRoots)) {
                    return LinkVerdict(
                        url = url,
                        status = RiskStatus.High,
                        title = "🚨 FAKE BRAND IMPERSONATION DETECTED",
                        detail = "The domain '$host' mimics official financial or government infrastructure ($brand) on an unauthorized server.",
                        riskScore = 84,
                        supportingEvidence = listOf("Target brand: '$brand'", "Unverified host domain: '$host'", "Not found in trusted institution root registry"),
                        recommendedAction = "Do not open. Access your financial institution solely via official bookmarks or certified apps.",
                        explicitLimitations = "Checked against offline trusted domain registry. Third-party brand partnerships not verified."
                    )
                }
            }

            // 6. Threat Analysis: IDN Homoglyph / Punycode
            val isPunycode = host.startsWith("xn--") || host.contains(".xn--")
            if (isPunycode) {
                val labels = decodedHost.split('.')
                val hasHomoglyphSpoof = labels.any { hasMixedScriptHomoglyphs(it) }
                if (hasHomoglyphSpoof) {
                    return LinkVerdict(
                        url = url,
                        status = RiskStatus.High,
                        title = "🚨 PUNYCODE / HOMOGLYPH SPOOF DETECTED",
                        detail = "This link uses mixed-script internationalized characters ('$decodedHost') that look identical to genuine Latin letters to deceive users.",
                        riskScore = 89,
                        supportingEvidence = listOf("Punycode domain: '$host'", "Decoded IDN: '$decodedHost'", "Mixed-script visual character spoofing"),
                        recommendedAction = "Do not open. The website uses lookalike foreign characters to visually deceive you.",
                        explicitLimitations = "Offline Unicode script analysis."
                    )
                }

                val isNationalIdn = host.endsWith(".xn--h2brj9c") || host.endsWith(".xn--mgbbh1a71e") ||
                        host.endsWith(".xn--fpcrj9c3d") || host.endsWith(".xn--gecrj9c") ||
                        host.endsWith(".xn--s9brj9c") || host.endsWith(".xn--xkc2dl3a5ee0h") ||
                        host.endsWith(".xn--45brj9c") || host.endsWith(".xn--2scrj9c") ||
                        host.endsWith(".xn--rvcs84f") || host.endsWith(".xn--3hcrj9c")

                if (!isNationalIdn && (host.endsWith(".com") || host.endsWith(".net") || host.endsWith(".org") ||
                            host.endsWith(".xyz") || host.endsWith(".top") || host.endsWith(".info"))) {
                    return LinkVerdict(
                        url = url,
                        status = RiskStatus.High,
                        title = "🚨 PUNYCODE DOMAIN DETECTED",
                        detail = "This link uses internationalized encoding ('$decodedHost') on an unverified commercial registry.",
                        riskScore = 78,
                        supportingEvidence = listOf("Commercial Punycode: '$host'", "Non-national registry"),
                        recommendedAction = "Exercise caution. Confirm domain authenticity.",
                        explicitLimitations = "Offline TLD verification."
                    )
                }
            }

            // 7. Suspicious Threat: Masked Shorteners
            val shorteners = listOf(
                "bit.ly", "tinyurl.com", "is.gd", "t.co", "cutt.ly", "rb.gy", "ow.ly", "buff.ly", "shorte.st", "adf.ly"
            )
            if (shorteners.any { host.contains(it) || lower.contains(it) }) {
                return LinkVerdict(
                    url = url,
                    status = RiskStatus.Suspicious,
                    title = "⚠️ SUSPICIOUS MASKED URL SHORTENER",
                    detail = "This link uses a URL shortener that conceals the true landing page. Proceed with caution.",
                    riskScore = 52,
                    supportingEvidence = listOf("Shortener host: '$host'", "Destination concealed behind redirect service"),
                    recommendedAction = "Expand or verify the destination with the sender before clicking.",
                    explicitLimitations = "Offline evaluation. The redirected landing page cannot be resolved without internet."
                )
            }

            // 8. Suspicious Threat: High-Abuse TLDs
            val suspiciousTlds = listOf(
                ".xyz", ".top", ".cc", ".club", ".tk", ".ml", ".ga", ".cf", ".gq",
                ".buzz", ".rest", ".live", ".work", ".icu", ".quest", ".monster",
                ".click", ".stream", ".download", ".racing"
            )
            if (suspiciousTlds.any { host.endsWith(it) }) {
                return LinkVerdict(
                    url = url,
                    status = RiskStatus.Suspicious,
                    title = "⚠️ UNVERIFIED HIGH-RISK DOMAIN REGISTRY",
                    detail = "This website is hosted on a top-level domain frequently associated with throwaway phishing sites and malicious campaigns.",
                    riskScore = 58,
                    supportingEvidence = listOf("Registry TLD: '.${host.substringAfterLast('.')}'", "Statistically elevated phishing incidence"),
                    recommendedAction = "Exercise caution. Confirm the organization's authentic domain before proceeding.",
                    explicitLimitations = "Offline TLD reputation heuristic."
                )
            }

            // 9. Clean Domain
            return LinkVerdict(
                url = url,
                status = RiskStatus.Low,
                title = "🛡️ NO KNOWN THREAT DETECTED",
                detail = "This destination matches known legitimate domain standards with no active threat indicators.",
                riskScore = 8,
                supportingEvidence = listOf("Standard domain format", "No homoglyphs, IP hosts, or APK downloads detected"),
                recommendedAction = "Destination appears standard. Exercise normal online awareness.",
                explicitLimitations = "Offline static evaluation. Cannot guarantee server is free of zero-day vulnerabilities."
            )
        }

        fun assessPayload(payload: SharedPayload): UnifiedSecurityAssessment = runBlocking {
            assessPayloadAsync(payload)
        }

        suspend fun assessPayloadAsync(payload: SharedPayload): UnifiedSecurityAssessment = withContext(Dispatchers.Default) {
            when (payload) {
                is SharedPayload.Empty -> {
                    UnifiedSecurityAssessment(
                        actualRiskScore = 0,
                        status = RiskStatus.Low,
                        categoryTitle = "NO ANALYZABLE CONTENT",
                        plainExplanationEn = "The shared content did not contain readable message text or web links.",
                        plainExplanationHi = "साझा की गई सामग्री में कोई पठनीय संदेश या वेब लिंक नहीं मिला।",
                        detectedSignals = emptyList(),
                        evaluatedUrls = emptyList(),
                        engineLabel = "Astra SafetyBarrier Offline Heuristic Rule Engine",
                        isTextOnly = true,
                        riskLevel = Risk.LOW,
                        uncertaintyNote = "Offline inspection cannot establish ownership, reputation or the destination after navigation.",
                        rawAssessment = null
                    )
                }
                is SharedPayload.ViewUrl -> {
                    val astra = SafetyBarrier.analyzeUrl(payload.url)
                    val fallback = evaluateLinkSafety(payload.url)

                    val isHigh = astra.risk == Risk.HIGH || fallback.status == RiskStatus.High
                    val isReview = astra.risk == Risk.REVIEW || fallback.status == RiskStatus.Suspicious
                    val status = when {
                        isHigh -> RiskStatus.High
                        isReview -> RiskStatus.Suspicious
                        else -> RiskStatus.Low
                    }

                    val detectorRisk = when {
                        astra.risk != Risk.LOW -> astra.risk
                        fallback.status == RiskStatus.High -> Risk.HIGH
                        fallback.status == RiskStatus.Suspicious -> Risk.REVIEW
                        else -> Risk.LOW
                    }

                    val host = astra.destinationHost ?: extractHost(payload.url)
                    val signals = mutableListOf<String>()
                    if (astra.evidence.any { it.id == Signal.BRAND } || fallback.title.contains("IMPERSONATION", ignoreCase = true)) {
                        signals.add("Brand / Institutional Impersonation")
                    }
                    if (astra.evidence.any { it.id == Signal.IP } || fallback.title.contains("IP HOST", ignoreCase = true)) {
                        signals.add("Direct Numeric IP Host")
                    }
                    if (astra.evidence.any { it.id == Signal.MIXED } || fallback.title.contains("HOMOGLYPH", ignoreCase = true) || fallback.title.contains("PUNYCODE", ignoreCase = true)) {
                        signals.add("Homoglyph / Punycode Spoof")
                    }
                    if (astra.evidence.any { it.id == Signal.APK } || fallback.title.contains("APK", ignoreCase = true) || fallback.title.contains("PACKAGE", ignoreCase = true)) {
                        signals.add("Direct Executable / APK Sideload")
                    }
                    if (astra.evidence.any { it.id == Signal.SHORT } || fallback.title.contains("SHORTENER", ignoreCase = true)) {
                        signals.add("Masked Shortener Destination")
                    }
                    if (astra.evidence.any { it.id == Signal.PAYMENT } || fallback.title.contains("UPI", ignoreCase = true)) {
                        signals.add("Direct UPI Payment Scheme")
                    }
                    if (astra.evidence.any { it.id == Signal.HTTP } || payload.url.startsWith("http://", ignoreCase = true)) {
                        signals.add("Unencrypted HTTP Connection (Cleartext)")
                    }
                    astra.evidence.forEach { signals.add("[${it.id.name}] ${it.id.explanation}") }
                    fallback.supportingEvidence.forEach { if (!signals.contains(it)) signals.add(it) }

                    val evaluatedVerdict = LinkVerdict(
                        url = payload.url,
                        status = status,
                        title = if (status == RiskStatus.Low) "🛡️ UNFLAGGED DESTINATION" else fallback.title,
                        detail = astra.explanation.ifBlank { fallback.detail },
                        riskScore = when (status) {
                            RiskStatus.High -> maxOf(85, fallback.riskScore)
                            RiskStatus.Suspicious -> maxOf(50, fallback.riskScore)
                            RiskStatus.Low -> minOf(10, fallback.riskScore)
                        },
                        supportingEvidence = signals,
                        recommendedAction = astra.recommendations.firstOrNull() ?: fallback.recommendedAction,
                        explicitLimitations = astra.uncertainty,
                        detectorRisk = detectorRisk,
                        destinationHost = host
                    )

                    val astraHindiList = astra.hindiGuidance()
                    val astraHindi = astraHindiList.joinToString(" ")
                    val explanationHi = when {
                        astraHindi.isNotBlank() && !astraHindi.contains("कोई चेतावनी संकेत नहीं मिला") -> astraHindi
                        status == RiskStatus.High -> "यह गंतव्य दुर्भावनापूर्ण सर्वर या अनधिकृत भुगतान से जुड़ा है जो आपकी सुरक्षा या बैंक विवरण को खतरे में डाल सकता है।"
                        status == RiskStatus.Suspicious -> "इस लिंक में अपुष्ट डोमेन, शॉर्टनर या असुरक्षित HTTP का उपयोग किया गया है। सावधानी बरतें।"
                        else -> "इस गंतव्य के लिए कोई ज्ञात स्थानीय सुरक्षा खतरा या दुर्भावनापूर्ण पैटर्न नहीं मिला।"
                    }

                    val explanationEn = when (status) {
                        RiskStatus.High -> "This destination exhibits characteristics of malicious infrastructure designed to compromise your device, harvest credentials, or extract unverified payments."
                        RiskStatus.Suspicious -> "This link uses unverified domains, masked shorteners, unencrypted HTTP, or direct payment handlers. Proceed with caution."
                        RiskStatus.Low -> "No known local threat indicators detected. Offline inspection cannot verify destination reputation after navigation."
                    }

                    UnifiedSecurityAssessment(
                        actualRiskScore = evaluatedVerdict.riskScore,
                        status = status,
                        categoryTitle = if (status == RiskStatus.Low) "UNFLAGGED DESTINATION" else evaluatedVerdict.title,
                        plainExplanationEn = explanationEn,
                        plainExplanationHi = explanationHi,
                        detectedSignals = signals,
                        evaluatedUrls = listOf(evaluatedVerdict),
                        engineLabel = "Astra SafetyBarrier Offline Heuristic Rule Engine",
                        isTextOnly = false,
                        rawTextSnippet = payload.url,
                        riskLevel = detectorRisk,
                        uncertaintyNote = astra.uncertainty,
                        rawAssessment = astra
                    )
                }
                is SharedPayload.SharedContent -> {
                    val text = payload.rawText
                    val astra = SafetyBarrier.analyze(null, text)

                    // Multi-destination evaluation: Each distinct URL is mapped to its own independent assessment
                    val evaluatedUrls = if (astra.destinations.isNotEmpty()) {
                        astra.destinations.map { sharedDest ->
                            val destAssessment = sharedDest.assessment
                            val destFallback = evaluateLinkSafety(sharedDest.originalUrl)
                            val destHigh = destAssessment.risk == Risk.HIGH || destFallback.status == RiskStatus.High
                            val destReview = destAssessment.risk == Risk.REVIEW || destFallback.status == RiskStatus.Suspicious
                            val destStatus = if (destHigh) RiskStatus.High else if (destReview) RiskStatus.Suspicious else RiskStatus.Low
                            val destSignals = destAssessment.evidence.map { "[${it.id.name}] ${it.id.explanation}" }
                            val destDetectorRisk = when {
                                destAssessment.risk != Risk.LOW -> destAssessment.risk
                                destFallback.status == RiskStatus.High -> Risk.HIGH
                                destFallback.status == RiskStatus.Suspicious -> Risk.REVIEW
                                else -> Risk.LOW
                            }
                            LinkVerdict(
                                url = sharedDest.originalUrl,
                                status = destStatus,
                                title = when {
                                    destAssessment.evidence.any { it.id == Signal.APK } -> "🚨 MALICIOUS PACKAGE / APK SIDELOAD"
                                    destAssessment.evidence.any { it.id == Signal.BRAND } -> "🚨 FAKE BRAND IMPERSONATION"
                                    destAssessment.evidence.any { it.id == Signal.USERINFO } -> "🚨 URL USERINFO DECEPTION"
                                    destAssessment.evidence.any { it.id == Signal.IP } -> "⚠️ RAW NUMERIC IP HOST"
                                    destAssessment.evidence.any { it.id == Signal.SHORT } -> "⚠️ MASKED URL SHORTENER"
                                    destAssessment.evidence.any { it.id == Signal.MIXED } -> "⚠️ MIXED SCRIPT HOMOGLYPH"
                                    destAssessment.evidence.any { it.id == Signal.PAYMENT } -> "⚠️ DIRECT UPI PAYMENT LINK"
                                    else -> destFallback.title
                                },
                                detail = destAssessment.explanation.ifBlank { destFallback.detail },
                                riskScore = when (destStatus) {
                                    RiskStatus.High -> maxOf(85, destFallback.riskScore)
                                    RiskStatus.Suspicious -> maxOf(50, destFallback.riskScore)
                                    RiskStatus.Low -> minOf(10, destFallback.riskScore)
                                },
                                supportingEvidence = if (destSignals.isNotEmpty()) destSignals else destFallback.supportingEvidence,
                                recommendedAction = destAssessment.recommendations.firstOrNull() ?: destFallback.recommendedAction,
                                explicitLimitations = destAssessment.uncertainty,
                                detectorRisk = destDetectorRisk,
                                destinationHost = destAssessment.destinationHost ?: extractHost(sharedDest.originalUrl)
                            )
                        }
                    } else {
                        payload.urls.map { evaluateLinkSafety(it) }
                    }

                    // Real offline heuristic rule scoring
                    val keywordReport = KeywordScorer.score(text)
                    var highestRegionalScore = 0
                    val regionalMatches = mutableListOf<String>()
                    val supportedLangs = listOf("hi", "en", "ta", "te", "bn", "mr", "kn", "ml", "pa", "gu")
                    for (lang in supportedLangs) {
                        val sc = RegionalScamKeywords.score(text, lang)
                        if (sc > highestRegionalScore) highestRegionalScore = sc
                        val m = RegionalScamKeywords.match(text, lang)
                        m.forEach { regionalMatches.add(it.phrase) }
                    }

                    val maxUrlScore = evaluatedUrls.maxOfOrNull { it.riskScore } ?: 0
                    val textScore = maxOf(keywordReport.riskScore, highestRegionalScore)
                    val combinedScore = if (textScore >= 35 && maxUrlScore >= 50) {
                        (textScore + (maxUrlScore * 0.5f).toInt()).coerceAtMost(98)
                    } else {
                        maxOf(textScore, maxUrlScore)
                    }

                    val isAstraHigh = astra.risk == Risk.HIGH
                    val isAstraReview = astra.risk == Risk.REVIEW

                    val finalScore = when {
                        isAstraHigh -> maxOf(combinedScore, 85).coerceIn(0, 99)
                        isAstraReview -> maxOf(combinedScore, 50).coerceIn(0, 99)
                        else -> combinedScore.coerceIn(0, 99)
                    }

                    val status = when {
                        finalScore >= 70 || evaluatedUrls.any { it.status == RiskStatus.High } || isAstraHigh -> RiskStatus.High
                        finalScore >= 35 || evaluatedUrls.any { it.status == RiskStatus.Suspicious } || isAstraReview -> RiskStatus.Suspicious
                        else -> RiskStatus.Low
                    }

                    val riskLevel = when {
                        isAstraHigh || status == RiskStatus.High -> Risk.HIGH
                        isAstraReview || status == RiskStatus.Suspicious -> Risk.REVIEW
                        else -> Risk.LOW
                    }

                    val signals = mutableListOf<String>()
                    astra.evidence.forEach { signals.add("[${it.id.name}] ${it.id.explanation}") }
                    keywordReport.topSignals.forEach { signals.add(if (it.detail.isNotBlank()) "${it.title}: ${it.detail}" else it.title) }
                    regionalMatches.take(4).forEach { signals.add("Matched keyword: '$it'") }
                    evaluatedUrls.filter { it.status != RiskStatus.Low }.forEach {
                        signals.add("Destination Alert: ${it.title}")
                    }

                    val lower = text.lowercase(Locale.ROOT)
                    val category = when {
                        astra.evidence.any { it.id == Signal.SCREEN_CREDENTIAL } -> "SCREEN SHARING CREDENTIAL HARVESTING"
                        astra.evidence.any { it.id == Signal.REFUND_FEE } -> "REFUND & CASHBACK ADVANCE FEE FRAUD"
                        astra.evidence.any { it.id == Signal.ARREST } ||
                                lower.contains("digital arrest") || lower.contains("डिजिटल अरेस्ट") ||
                                (lower.contains("cbi") && lower.contains("arrest")) ||
                                (lower.contains("सीबीआई") && lower.contains("नोटिस")) -> "DIGITAL ARREST EXTORTION"
                        astra.evidence.any { it.id == Signal.ADVANCE_FEE } -> "ADVANCE FEE & JOB OFFER FRAUD"
                        astra.evidence.any { it.id == Signal.GUARANTEED_RETURN } -> "GUARANTEED INVESTMENT SCAM"
                        astra.evidence.any { it.id == Signal.REMOTE_ACCESS } -> "REMOTE ACCESS SCREEN SHARING FRAUD"
                        astra.evidence.any { it.id == Signal.FAMILY_PAYMENT } -> "FAMILY IMPERSONATION EMERGENCY SCAM"
                        astra.evidence.any { it.id == Signal.FASTAG } -> "FASTAG EXPIRATION / SUSPENSION FRAUD"
                        astra.evidence.any { it.id == Signal.UTILITY } ||
                                lower.contains("electricity") || lower.contains("power cut") || lower.contains("bijli") ||
                                lower.contains("बिजली") -> "UTILITY / ELECTRICITY BILL SCAM"
                        astra.evidence.any { it.id == Signal.KYC } ||
                                lower.contains("kyc") || lower.contains("pan") || lower.contains("aadhaar") ||
                                lower.contains("केवाईसी") -> "BANKING KYC / ACCOUNT BLOCK FRAUD"
                        astra.evidence.any { it.id == Signal.UPI } ||
                                evaluatedUrls.any { it.title.contains("UPI", true) } -> "UNVERIFIED UPI PAYMENT TRAP"
                        astra.evidence.any { it.id == Signal.OTP } -> "CREDENTIAL / OTP HARVESTING FRAUD"
                        astra.evidence.any { it.id == Signal.APK } ||
                                evaluatedUrls.any { it.title.contains("APK", true) } -> "MALICIOUS APK SIDELOAD TRAP"
                        evaluatedUrls.any { it.title.contains("HOMOGLYPH", true) || it.title.contains("PUNYCODE", true) } -> "HOMOGLYPH DOMAIN SPOOF"
                        lower.contains("lottery") || lower.contains("winner") || lower.contains("kbc") -> "LOTTERY & PRIZE SCAM"
                        finalScore >= 60 -> "SUSPICIOUS COERCIVE REQUEST"
                        finalScore >= 35 -> "UNVERIFIED URGENT MESSAGE"
                        else -> "NO KNOWN LOCAL THREAT INDICATORS DETECTED"
                    }

                    val explanationEn = when {
                        category.contains("SCREEN SHARING CREDENTIAL") ->
                            "The message asks you to share your screen while entering financial credentials (PIN, OTP, CVV). Fraudsters use screen mirroring to harvest banking credentials."
                        category.contains("REFUND & CASHBACK") ->
                            "The message demands an upfront fee to release a refund or cashback. Legitimate organizations never charge processing fees to credit refunds."
                        category.contains("DIGITAL ARREST") ->
                            "Law enforcement agencies (CBI, Police, ED) NEVER conduct arrests, trials, or interrogations via phone or video calls. This is a criminal extortion attempt."
                        category.contains("ADVANCE FEE") ->
                            "Legitimate recruiters and government programs never demand registration, processing, or security deposits before employment or releasing payouts."
                        category.contains("INVESTMENT") ->
                            "Guaranteed high daily or monthly trading profits are hallmark indicators of fraudulent schemes. Authorized institutions never promise guaranteed returns."
                        category.contains("REMOTE ACCESS") ->
                            "Fraudulent callers instruct victims to install remote screen-sharing tools (AnyDesk, TeamViewer) to access banking apps and steal funds."
                        category.contains("FAMILY IMPERSONATION") ->
                            "Attackers pose as family members using an unknown number claiming an urgent emergency. Always verify by calling their known saved number directly."
                        category.contains("FASTAG") ->
                            "Phishers threaten immediate FASTag deactivation with a fraudulent link to capture banking credentials. Check your tag balance only via your official banking app."
                        category.contains("ELECTRICITY") ->
                            "Power companies never disconnect electricity via urgent WhatsApp/SMS demands with third-party payment links. Contact your electricity board directly."
                        category.contains("KYC") ->
                            "Banks never demand immediate OTP, PIN, or PAN card updates via personal chat links or third-party websites."
                        category.contains("UPI") ->
                            "This message contains an unverified UPI payment link requesting money transfer. Legitimate organizations do not collect payments to personal handles."
                        category.contains("APK") ->
                            "This message instructs you to install an unauthorized app file (.apk) outside Google Play, which can give attackers remote access to your device."
                        status == RiskStatus.High ->
                            "This message displays coercive psychological pressure and unverified requests for funds, personal details, or credentials."
                        status == RiskStatus.Suspicious ->
                            "This message contains unverified urgency or obscured links. Exercise caution before responding or sharing any information."
                        else ->
                            "No known local threat indicators detected in this message."
                    }

                    val explanationHi = when {
                        category.contains("SCREEN SHARING CREDENTIAL") ->
                            "बैंकिंग कोड डालते समय स्क्रीन साझा करने को कहा गया है। कोड डालने से पहले स्क्रीन साझा करना बंद करें।"
                        category.contains("REFUND & CASHBACK") ->
                            "रिफंड या कैशबैक पाने के लिए पहले शुल्क माँगा गया है। आधिकारिक ऐप में लेनदेन जाँचें।"
                        category.contains("DIGITAL ARREST") ->
                            "कानून प्रवर्तन एजेंसियां (CBI, पुलिस) कभी भी फोन या व्हाट्सएप पर डिजिटल अरेस्ट या पूछताछ नहीं करती हैं। यह जबरन वसूली का प्रयास है।"
                        category.contains("ADVANCE FEE") ->
                            "कोई भी वैध कंपनी या सरकारी योजना नौकरी या भुगतान जारी करने से पहले पंजीकरण शुल्क या सुरक्षा जमा की मांग नहीं करती है।"
                        category.contains("INVESTMENT") ->
                            "गारंटीड दैनिक या मासिक मुनाफे के वादे धोखाधड़ी की पहचान हैं। कोई भी अधिकृत संस्था निश्चित रिटर्न की गारंटी नहीं देती।"
                        category.contains("REMOTE ACCESS") ->
                            "धोखेबाज बैंक खातों को नियंत्रित करने के लिए एनीडेस्क या टीमव्यूअर जैसे रिमोट एक्सेस ऐप डाउनलोड करने के लिए कहते हैं।"
                        category.contains("FAMILY IMPERSONATION") ->
                            "धोखेबाज नए नंबर से रिश्तेदार बनकर तत्काल पैसे ट्रांसफर करने की मांग करते हैं। हमेशा उनके पुराने नंबर पर बात करके पुष्टि करें।"
                        category.contains("FASTAG") ->
                            "फास्टैग बंद होने की धमकी देकर फर्जी लिंक से बैंक विवरण चुराने की कोशिश की जा रही है। केवल आधिकारिक ऐप से जांचें।"
                        category.contains("ELECTRICITY") ->
                            "बिजली वितरण कंपनियां व्हाट्सएप पर तत्काल बिजली काटने की धमकी नहीं देती हैं। किसी भी निजी लिंक पर भुगतान न करें।"
                        category.contains("KYC") ->
                            "बैंक कभी भी व्हाट्सएप लिंक के माध्यम से तत्काल केवाईसी, ओटीपी या पिन अपडेट करने के लिए नहीं कहते हैं।"
                        category.contains("UPI") ->
                            "इस संदेश में एक अनधिकृत यूपीआई भुगतान लिंक है। किसी भी व्यक्तिगत खाते में पैसे ट्रांसफर न करें।"
                        category.contains("APK") ->
                            "यह संदेश आपको गूगल प्ले स्टोर के बाहर अज्ञात ऐप (.apk) डाउनलोड करने के लिए कह रहा है, जिससे आपका फोन हैक हो सकता है।"
                        status == RiskStatus.High ->
                            "इस संदेश में जबरन दबाव और अनधिकृत वित्तीय या व्यक्तिगत जानकारी मांगने के स्पष्ट संकेत मिले हैं।"
                        status == RiskStatus.Suspicious ->
                            "इस संदेश में अपुष्ट दबाव या संदिग्ध लिंक हैं। कोई भी जानकारी साझा करने से पहले सतर्क रहें।"
                        else ->
                            "इस संदेश में कोई ज्ञात स्थानीय सुरक्षा खतरा या दुर्भावनापूर्ण पैटर्न नहीं मिला।"
                    }

                    UnifiedSecurityAssessment(
                        actualRiskScore = finalScore,
                        status = status,
                        categoryTitle = category,
                        plainExplanationEn = explanationEn,
                        plainExplanationHi = explanationHi,
                        detectedSignals = signals.distinct(),
                        evaluatedUrls = evaluatedUrls,
                        engineLabel = "Astra SafetyBarrier Offline Heuristic Rule Engine",
                        isTextOnly = evaluatedUrls.isEmpty(),
                        rawTextSnippet = text.take(150),
                        riskLevel = riskLevel,
                        uncertaintyNote = astra.uncertainty,
                        rawAssessment = astra
                    )
                }
            }
        }
    }
}

@Composable
fun UnifiedSecurityDialog(
    assessment: UnifiedSecurityAssessment,
    llamaState: LlamaModelState = LlamaModelState.NOT_PROVISIONED,
    llamaExplanation: String? = null,
    llamaNativeOutput: String? = null,
    llamaFailureReason: String? = null,
    onInvokeLlama: () -> Unit = {},
    onOpenInSafeBrowser: (String) -> Unit = {},
    onProceedToUrl: (String) -> Unit,
    onDismiss: () -> Unit,
    onCopyReport: () -> Unit,
    onAlertFamily: () -> Unit
) {
    var isHindi by remember { mutableStateOf(false) }
    var selectedUrlIndex by remember { mutableStateOf(0) }
    var showNavigationConfirmation by remember { mutableStateOf(false) }

    val statusColor = when (assessment.status) {
        RiskStatus.High -> GxDanger
        RiskStatus.Suspicious -> GxWarning
        RiskStatus.Low -> GxSafe
    }
    val softBg = when (assessment.status) {
        RiskStatus.High -> GxDangerSoft
        RiskStatus.Suspicious -> GxWarningSoft
        RiskStatus.Low -> GxSafeSoft
    }

    Card(
        shape = GxShapeMd,
        colors = CardDefaults.cardColors(containerColor = GxSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Column(modifier = Modifier.padding(20.dp)) {

            // Header Row: Brand, Language Toggle, and Actual Score Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = GxShapePill,
                        color = softBg,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when (assessment.status) {
                                    RiskStatus.High -> Icons.Default.WarningAmber
                                    RiskStatus.Suspicious -> Icons.Default.Security
                                    RiskStatus.Low -> Icons.Default.CheckCircle
                                },
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "SUSAGI GUARDIAN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GxTextLo,
                            letterSpacing = 1.2.sp
                        )
                        Text(
                            assessment.categoryTitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = statusColor
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Language Switch Pill
                    Surface(
                        shape = GxShapePill,
                        color = GxSurfaceAlt,
                        border = BorderStroke(1.dp, GxBorder),
                        modifier = Modifier
                            .clickable { isHindi = !isHindi }
                            .padding(end = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Language, contentDescription = "Language", tint = GxPrimary, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                if (isHindi) "EN" else "हिन्दी",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = GxPrimary
                            )
                        }
                    }

                    // Actual Detector Risk Badge (Grounded in Astra SafetyBarrier rules)
                    Surface(
                        color = softBg,
                        shape = GxShapePill,
                        border = BorderStroke(0.5.dp, statusColor)
                    ) {
                        Text(
                            text = when (assessment.riskLevel) {
                                Risk.HIGH -> "HIGH RISK"
                                Risk.REVIEW -> "NEEDS REVIEW"
                                Risk.LOW -> "LOW RISK"
                                Risk.UNKNOWN -> "UNKNOWN"
                            },
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Engine Attribution Pill (Explicit Rule Engine disclosure - zero false claim of Llama)
            Surface(
                color = GxSurfaceAlt,
                shape = GxShapeSm,
                border = BorderStroke(0.5.dp, GxBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "ENGINE: ",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = GxTextLo
                    )
                    Text(
                        assessment.engineLabel,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = GxTextMid
                    )
                }
            }

            // Honest Uncertainty Disclosure (Zero invented claims)
            Surface(
                color = GxSurfaceAlt,
                shape = GxShapeSm,
                border = BorderStroke(0.5.dp, GxBorder),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = GxTextLo, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        assessment.uncertaintyNote,
                        fontSize = 9.sp,
                        color = GxTextLo,
                        lineHeight = 12.sp
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Plain-Language Human Explanation Card
            Surface(
                color = GxSurfaceAlt,
                shape = GxShapeSm,
                border = BorderStroke(1.dp, GxBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        if (isHindi) "सुरक्षा मार्गदर्शन" else "SECURITY GUIDANCE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = GxTextLo,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (isHindi) assessment.plainExplanationHi else assessment.plainExplanationEn,
                        color = GxTextHi,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // On-Device Llama 3.2 1B Evidence Explainer Card
            Surface(
                color = GxSurfaceAlt,
                shape = GxShapeSm,
                border = BorderStroke(1.dp, when (llamaState) {
                    LlamaModelState.GENERATED -> GxSafe
                    LlamaModelState.INVALID_ASSETS, LlamaModelState.FAILED -> GxDanger
                    LlamaModelState.LOADING, LlamaModelState.GENERATING -> GxPrimary
                    LlamaModelState.NOT_PROVISIONED -> GxBorder
                }),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "ON-DEVICE AI (LLAMA 3.2 1B)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = GxTextLo,
                            letterSpacing = 1.sp
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = when (llamaState) {
                                LlamaModelState.GENERATED -> GxSafeSoft
                                LlamaModelState.LOADING, LlamaModelState.GENERATING -> GxPrimarySoft
                                LlamaModelState.INVALID_ASSETS, LlamaModelState.FAILED -> GxDangerSoft
                                LlamaModelState.NOT_PROVISIONED -> GxSurface
                            },
                            border = BorderStroke(0.5.dp, when (llamaState) {
                                LlamaModelState.GENERATED -> GxSafe
                                LlamaModelState.LOADING, LlamaModelState.GENERATING -> GxPrimary
                                LlamaModelState.INVALID_ASSETS, LlamaModelState.FAILED -> GxDanger
                                LlamaModelState.NOT_PROVISIONED -> GxBorder
                            })
                        ) {
                            Text(
                                text = llamaState.name,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (llamaState) {
                                    LlamaModelState.GENERATED -> GxSafe
                                    LlamaModelState.LOADING, LlamaModelState.GENERATING -> GxPrimary
                                    LlamaModelState.INVALID_ASSETS, LlamaModelState.FAILED -> GxDanger
                                    LlamaModelState.NOT_PROVISIONED -> GxTextLo
                                },
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    when (llamaState) {
                        LlamaModelState.NOT_PROVISIONED -> {
                            Text(
                                text = "Llama 3.2 1B SpinQuant model assets are not provisioned on this device storage. Deterministic Astra SafetyBarrier heuristic guidance is active.",
                                fontSize = 11.sp,
                                color = GxTextMid,
                                lineHeight = 15.sp
                            )
                        }
                        LlamaModelState.INVALID_ASSETS -> {
                            Text(
                                text = llamaFailureReason ?: "Model assets present but failed size or SHA-256 integrity verification. Falling back to deterministic heuristics.",
                                fontSize = 11.sp,
                                color = GxDanger,
                                lineHeight = 15.sp
                            )
                        }
                        LlamaModelState.LOADING -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = GxPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Loading ExecuTorch Arm XNNPACK runtime...", fontSize = 11.sp, color = GxTextHi)
                            }
                        }
                        LlamaModelState.GENERATING -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(color = GxPrimary, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Generating native evidence prioritization...", fontSize = 11.sp, color = GxTextHi)
                            }
                        }
                        LlamaModelState.GENERATED -> {
                            Column {
                                Text(
                                    text = llamaExplanation ?: "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = GxTextHi,
                                    lineHeight = 16.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Surface(
                                    color = GxSurface,
                                    shape = GxShapeSm,
                                    border = BorderStroke(0.5.dp, GxBorder)
                                ) {
                                    Text(
                                        text = "Native Tokens: ${llamaNativeOutput?.trim()}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GxPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "Generated 100% on-device via ExecuTorch Arm XNNPACK. SafetyBarrier verdict remains authoritative.",
                                    fontSize = 9.sp,
                                    color = GxTextLo
                                )
                            }
                        }
                        LlamaModelState.FAILED -> {
                            Text(
                                text = "Native inference unavailable: ${llamaFailureReason ?: "execution error"}. Operating with deterministic SafetyBarrier guidance.",
                                fontSize = 11.sp,
                                color = GxDanger,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    if (llamaState != LlamaModelState.LOADING && llamaState != LlamaModelState.GENERATING && assessment.rawAssessment != null && assessment.rawAssessment.evidence.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onInvokeLlama,
                            border = BorderStroke(1.dp, GxPrimary),
                            shape = GxShapeSm,
                            modifier = Modifier.fillMaxWidth().height(32.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Security, contentDescription = null, tint = GxPrimary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (llamaState == LlamaModelState.GENERATED) "Re-run Llama Inference" else "Explain Evidence with On-Device Llama",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = GxPrimary
                            )
                        }
                    }
                }
            }

            // Detected Evidence Signals
            if (assessment.detectedSignals.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    if (isHindi) "पहचाने गए खतरे के संकेत:" else "DETECTED EVIDENCE SIGNALS:",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = GxTextLo,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    assessment.detectedSignals.take(4).forEach { signal ->
                        Surface(
                            color = GxSurfaceAlt,
                            shape = GxShapeSm,
                            border = BorderStroke(0.5.dp, GxBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "• $signal",
                                fontSize = 11.sp,
                                color = GxTextMid,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            // Destinations / Links Section (Multi-Link Selection & Per-Destination Analysis Support)
            if (assessment.evaluatedUrls.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (assessment.evaluatedUrls.size > 1) {
                        if (isHindi) "पहचाने गए गंतव्य (जांच के लिए चुनें):" else "DETECTED DESTINATIONS (SELECT TO INSPECT):"
                    } else {
                        if (isHindi) "वेब गंतव्य:" else "TARGET DESTINATION:"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = GxTextLo,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(6.dp))

                assessment.evaluatedUrls.forEachIndexed { idx, urlVerdict ->
                    val isSelected = (idx == selectedUrlIndex)
                    val urlStatusColor = when (urlVerdict.status) {
                        RiskStatus.High -> GxDanger
                        RiskStatus.Suspicious -> GxWarning
                        RiskStatus.Low -> GxSafe
                    }

                    Surface(
                        color = if (isSelected) GxSurface else GxSurfaceAlt,
                        shape = GxShapeSm,
                        border = BorderStroke(
                            if (isSelected) 1.5.dp else 0.5.dp,
                            if (isSelected) urlStatusColor else GxBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { selectedUrlIndex = idx }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        LinkCheckActivity.extractHost(urlVerdict.url),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GxTextHi,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Text(
                                        urlVerdict.url.take(65) + if (urlVerdict.url.length > 65) "..." else "",
                                        fontSize = 10.sp,
                                        color = GxTextLo,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                Surface(
                                    shape = GxShapePill,
                                    color = urlStatusColor.copy(alpha = 0.15f),
                                    border = BorderStroke(0.5.dp, urlStatusColor)
                                ) {
                                    Text(
                                        when (urlVerdict.detectorRisk) {
                                            Risk.HIGH -> "HIGH"
                                            Risk.REVIEW -> "REVIEW"
                                            Risk.LOW -> "LOW RISK"
                                            Risk.UNKNOWN -> "UNKNOWN"
                                        },
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = urlStatusColor,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // If selected, display supporting evidence and recommended action
                            if (isSelected) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Recommended Action: ${urlVerdict.recommendedAction}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = GxTextMid
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    "Limitations: ${urlVerdict.explicitLimitations}",
                                    fontSize = 9.sp,
                                    color = GxTextLo
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            // Action Buttons: Safe Close Overlay (Truthful), Copy Report, Alert Family
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Primary Truthful Action: Close Overlay (Safe)
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (assessment.status == RiskStatus.Low) GxSafe else GxDanger,
                        contentColor = Color.White
                    ),
                    shape = GxShapeSm,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Text(
                        if (assessment.status == RiskStatus.Low) "CLOSE OVERLAY" else "CLOSE & DISMISS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Copy Assessment Report
                OutlinedButton(
                    onClick = onCopyReport,
                    border = BorderStroke(1.dp, GxBorder),
                    shape = GxShapeSm,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Report", tint = GxPrimary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", color = GxPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Alert Family
                OutlinedButton(
                    onClick = onAlertFamily,
                    border = BorderStroke(1.dp, GxBorder),
                    shape = GxShapeSm,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Alert Family", tint = GxPrimary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Alert", color = GxPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Destination Actions (Safe Browsing vs External Browser)
            if (assessment.evaluatedUrls.isNotEmpty()) {
                val chosenUrl = assessment.evaluatedUrls.getOrNull(selectedUrlIndex)?.url ?: assessment.evaluatedUrls.first().url
                Spacer(Modifier.height(10.dp))

                // Primary Action: In-App Sandboxed Safe Browser
                Button(
                    onClick = { onOpenInSafeBrowser(chosenUrl) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (assessment.status == RiskStatus.Low) GxSafe else GxPrimary,
                        contentColor = Color.White
                    ),
                    shape = GxShapeSm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (assessment.status == RiskStatus.Low) "Open in SuSagi Safe Browser" else "Open in Sandboxed Safe Browser",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(6.dp))

                if (!showNavigationConfirmation) {
                    OutlinedButton(
                        onClick = { showNavigationConfirmation = true },
                        border = BorderStroke(0.5.dp, GxBorder),
                        shape = GxShapeSm,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp)
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, tint = GxTextMid, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Open in External Browser...",
                            color = GxTextMid,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal
                        )
                    }
                } else {
                    // Explicit Navigation Confirmation Card
                    Surface(
                        color = GxDangerSoft,
                        shape = GxShapeSm,
                        border = BorderStroke(1.dp, GxDanger),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "⚠️ Confirm Navigation to External Browser",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GxDanger
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "Destination: $chosenUrl\nThis destination has not been verified safe. Are you sure you wish to leave SuSagi?",
                                fontSize = 10.sp,
                                color = GxTextHi,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onProceedToUrl(chosenUrl) },
                                    colors = ButtonDefaults.buttonColors(containerColor = GxDanger, contentColor = Color.White),
                                    shape = GxShapeSm,
                                    modifier = Modifier.weight(1f).height(34.dp)
                                ) {
                                    Text("Confirm & Open", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { showNavigationConfirmation = false },
                                    border = BorderStroke(1.dp, GxBorder),
                                    shape = GxShapeSm,
                                    modifier = Modifier.weight(1f).height(34.dp)
                                ) {
                                    Text("Cancel", fontSize = 10.sp, color = GxPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
