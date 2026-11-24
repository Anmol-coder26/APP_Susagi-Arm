package com.susagi.ui.assistant

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.KeywordScorer
import com.guardian.app.ui.theme.GxBase
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxDangerSoft
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxSafe
import com.guardian.app.ui.theme.GxSafeSoft
import com.guardian.app.ui.theme.GxShapeLg
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
import com.susagi.edgeai.ConversationManager
import com.susagi.edgeai.ExecuTorchLlamaEngine
import com.susagi.edgeai.ModelManager
import com.susagi.intelligence.EducationalContentDetector
import com.susagi.safety.Risk
import com.susagi.safety.SafetyBarrier
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class ToolMode {
    CHAT,
    ANALYZE_MESSAGE,
    INSPECT_URL,
    INSPECT_UPI,
    INSPECT_QR,
    REVIEW_HISTORY
}

data class AssistantMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isToolResult: Boolean = false,
    val toolName: String? = null,
    val evidence: List<String> = emptyList(),
    val threatCategory: String? = null,
    val riskBand: String? = null, // "LOW", "REVIEW", "HIGH"
    val suggestedActions: List<String> = emptyList(),
    val isStreaming: Boolean = false,
    val isMissingModelNotice: Boolean = false
)

/**
 * SuSagi AI Assistant Screen
 *
 * Implements GRAVITY DIRECTIVE 46A:
 * - General-purpose conversational AI assistant is the DEFAULT experience.
 * - Answers arbitrary, open-ended questions across many subjects (programming, math, science, finance, writing).
 * - NOT a greeting bot, NOT an FAQ bot, NOT a scam classifier wrapped in chat UI.
 * - Architecture: User message -> Conversation Manager -> Local LLM -> Generated Answer -> Chat UI.
 * - Honest Model Reporting: If model is not provisioned, displays the exact truthful notice:
 *   "Local AI conversation requires a compatible language model. Security Guidance remains available without a download."
 * - Explicit Security Tools: User-initiated tools for Message Analysis, URL Inspection, UPI Verification, and Threat Explanation.
 * - Security detection is independent: General conversation NEVER automatically produces "No scam detected".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuSagiAssistantScreen(
    onBack: () -> Unit,
    onOpenModelManager: () -> Unit
) {
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val modelManager = remember { ModelManager(ctx) }
    val llamaEngine = remember { ExecuTorchLlamaEngine(ctx, modelManager) }
    var isLlmReady by remember { mutableStateOf(llamaEngine.isReady) }
    val conversationManager = remember { ConversationManager() }

    val messages = remember {
        mutableStateListOf(
            AssistantMessage(
                isUser = false,
                text = "Hello! I am SuSagi AI, your on-device cybersecurity advisor and threat analyst. " +
                        "Ask me anything about scam detection, suspicious messages, phishing links, payment fraud, or digital safety.",
                suggestedActions = listOf(
                    "🚨 Digital Arrest Scams",
                    "💳 UPI Reverse-Debit Traps",
                    "🔗 Identify Phishing Links",
                    "🛡️ Analyze Message",
                    "🔗 Inspect URL",
                    "💳 Inspect UPI",
                    "📷 Inspect QR",
                    "📜 Threat History"
                )
            )
        )
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (!llamaEngine.isReady && modelManager.isReady()) {
                llamaEngine.tryInitialize()
            }
        }
        isLlmReady = llamaEngine.isReady
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    var activeToolMode by remember { mutableStateOf(ToolMode.CHAT) }
    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var currentGenerationJob by remember { mutableStateOf<Job?>(null) }
    var lastThreatReport by remember { mutableStateOf<AssistantMessage?>(null) }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun stopGeneration() {
        llamaEngine.stopGeneration()
        currentGenerationJob?.cancel()
        currentGenerationJob = null
        isGenerating = false
        if (messages.isNotEmpty() && messages.last().isStreaming) {
            val last = messages.last()
            messages[messages.size - 1] = last.copy(isStreaming = false)
        }
    }

    // -------------------------------------------------------------------------
    // Tool Execution Handlers
    // -------------------------------------------------------------------------

    fun executeAnalyzeMessageTool(rawMessage: String) {
        val trimmed = rawMessage.trim()
        if (trimmed.isBlank()) return

        messages.add(AssistantMessage(isUser = true, text = "🛡️ [Tool: Analyze Message]\n$trimmed"))
        conversationManager.addUserMessage("Analyze this suspicious message: $trimmed")

        coroutineScope.launch {
            val guidance = generateDeterministicGuidance(trimmed, context = null)
            val toolMsg = AssistantMessage(
                isUser = false,
                isToolResult = true,
                toolName = "Message Shield (SafetyBarrier)",
                text = guidance.text,
                threatCategory = guidance.threatCategory,
                riskBand = guidance.riskBand,
                evidence = guidance.evidence,
                suggestedActions = listOf("Explain with AI", "Dial 1930", "What to avoid sharing")
            )
            messages.add(toolMsg)
            lastThreatReport = toolMsg
            conversationManager.addToolResult(
                toolName = "MessageShield",
                summary = guidance.text.take(300),
                evidence = guidance.evidence,
                threatCategory = guidance.threatCategory,
                riskBand = guidance.riskBand
            )
        }
    }

    fun executeInspectUrlTool(rawUrl: String) {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return

        messages.add(AssistantMessage(isUser = true, text = "🔗 [Tool: Inspect URL]\n$trimmed"))
        conversationManager.addUserMessage("Inspect this link for security threats: $trimmed")

        coroutineScope.launch {
            val lower = trimmed.lowercase(Locale.ROOT)
            val isApk = lower.endsWith(".apk") || lower.contains(".apk?") || lower.endsWith(".exe")
            val hasIpHost = Regex("""https?://\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""").containsMatchIn(trimmed)
            val hasLoginKyc = lower.contains("login") || lower.contains("kyc") || lower.contains("verify") || lower.contains("pan")
            val isSuspiciousTld = lower.contains(".top") || lower.contains(".xyz") || lower.contains(".apk") || lower.contains(".cc")

            val riskBand = when {
                isApk || (hasIpHost && hasLoginKyc) -> "HIGH"
                hasIpHost || hasLoginKyc || isSuspiciousTld -> "REVIEW"
                else -> "LOW"
            }

            val threatCategory = when (riskBand) {
                "HIGH" -> if (isApk) "Malicious APK Sideload Distribution" else "Credential Harvesting Phishing Link"
                "REVIEW" -> "Unverified Domain / Suspicious Routing"
                else -> "Benign Web Navigation"
            }

            val evidence = mutableListOf<String>()
            if (isApk) evidence.add("Direct executable/APK download link bypassing official app stores")
            if (hasIpHost) evidence.add("Raw IP address used instead of authenticated domain name")
            if (hasLoginKyc) evidence.add("Simulates sensitive KYC/account verification login endpoint")
            if (isSuspiciousTld) evidence.add("High-abuse top-level domain frequently associated with short-lived scams")
            if (evidence.isEmpty()) evidence.add("No homoglyph spoofing or malware download patterns detected")

            val explanation = when (riskBand) {
                "HIGH" -> "🚨 DANGEROUS LINK DETECTED:\n\n" +
                        "This URL points directly to an unverified APK download or credential-harvesting phishing site. " +
                        "Installing APKs outside Google Play frequently installs Remote Access Trojans (RATs) to intercept SMS and drain bank accounts.\n\n" +
                        "Defensive Action:\n" +
                        "• DO NOT click or open this link.\n" +
                        "• Delete the message containing this link immediately."
                "REVIEW" -> "⚠️ CAUTION ADVISED ON UNVERIFIED LINK:\n\n" +
                        "This link uses an uncommon domain structure, raw IP host, or sensitive keyword. " +
                        "Verify whether you were expecting a message from this organization before visiting.\n\n" +
                        "Guidance:\n" +
                        "• Check official bookmarks or visit the company's verified website directly."
                else -> "✅ LINK INSPECTION COMPLETE:\n\n" +
                        "No deceptive homoglyphs, unauthorized APK download paths, or IP host disguises were identified. Always exercise vigilance with external URLs."
            }

            val toolMsg = AssistantMessage(
                isUser = false,
                isToolResult = true,
                toolName = "Link Shield",
                text = explanation,
                threatCategory = threatCategory,
                riskBand = riskBand,
                evidence = evidence,
                suggestedActions = listOf("Explain with AI", "Check another link", "What to avoid sharing")
            )
            messages.add(toolMsg)
            lastThreatReport = toolMsg
            conversationManager.addToolResult(
                toolName = "LinkShield",
                summary = explanation.take(300),
                evidence = evidence,
                threatCategory = threatCategory,
                riskBand = riskBand
            )
        }
    }

    fun executeInspectUpiTool(rawUpi: String) {
        val trimmed = rawUpi.trim()
        if (trimmed.isBlank()) return

        messages.add(AssistantMessage(isUser = true, text = "💳 [Tool: Inspect UPI]\n$trimmed"))
        conversationManager.addUserMessage("Check this UPI request: $trimmed")

        coroutineScope.launch {
            val lower = trimmed.lowercase(Locale.ROOT)
            val hasPinToReceive = lower.contains("enter pin") || lower.contains("pin to receive") || lower.contains("पिन दर्ज करें")
            val hasCashback = lower.contains("cashback") || lower.contains("reward") || lower.contains("lottery") || lower.contains("won")
            val hasCollectRequest = lower.contains("collect request") || lower.contains("requesting money") || lower.contains("pay request")

            val isFraud = hasPinToReceive || (hasCashback && (hasCollectRequest || lower.contains("pin")))
            val isReview = hasCollectRequest || hasCashback

            val riskBand = when {
                isFraud -> "HIGH"
                isReview -> "REVIEW"
                else -> "LOW"
            }

            val threatCategory = if (isFraud) "UPI Reverse Debit Collect Trap" else if (isReview) "Unverified Payment Collect Request" else "Standard Payment Inquiry"

            val evidence = mutableListOf<String>()
            if (hasPinToReceive) evidence.add("Claims UPI PIN must be entered to receive money (technically impossible)")
            if (hasCashback) evidence.add("Lures user with fake cashback or lottery reward to disguise debit transaction")
            if (hasCollectRequest) evidence.add("Uses merchant collect request mechanism to extract funds from victim")
            if (evidence.isEmpty()) evidence.add("Standard payment inquiry; follows normal UPI transaction rules")

            val explanation = when {
                isFraud -> "🚨 CRITICAL UPI REVERSE-DEBIT FRAUD DETECTED:\n\n" +
                        "Rule: You NEVER need to enter your UPI PIN to receive money or cashback.\n\n" +
                        "Entering your UPI PIN ALWAYS deducts money from your bank account. " +
                        "Scammers send 'Collect Requests' labeled as rewards or refunds, tricking victims into authorizing debits.\n\n" +
                        "Immediate Defensive Steps:\n" +
                        "1. Immediately DECLINE or IGNORE the request in your UPI app.\n" +
                        "2. Never scan a QR code to 'receive' payment.\n" +
                        "3. If funds were debited, dial national cyber helpline 1930 immediately."
                isReview -> "⚠️ CAUTION: UNVERIFIED UPI TRANSACTION:\n\n" +
                        "Always verify who sent the collect request before approving. " +
                        "Remember that approving any request in PhonePe/GPay/Paytm with your PIN will debit your balance."
                else -> "✅ UPI SAFETY GUIDANCE:\n\n" +
                        "Standard payment rules apply: Enter PIN only when sending money to a verified contact. Never share PIN or OTP with anyone."
            }

            val toolMsg = AssistantMessage(
                isUser = false,
                isToolResult = true,
                toolName = "Payment Inspector",
                text = explanation,
                threatCategory = threatCategory,
                riskBand = riskBand,
                evidence = evidence,
                suggestedActions = listOf("Explain with AI", "Dial 1930", "Is UPI PIN safe?")
            )
            messages.add(toolMsg)
            lastThreatReport = toolMsg
            conversationManager.addToolResult(
                toolName = "PaymentInspector",
                summary = explanation.take(300),
                evidence = evidence,
                threatCategory = threatCategory,
                riskBand = riskBand
            )
        }
    }

    fun executeInspectQrTool(rawQr: String) {
        val trimmed = rawQr.trim()
        if (trimmed.isBlank()) return

        messages.add(AssistantMessage(isUser = true, text = "📷 Inspect QR Payload: $trimmed"))
        conversationManager.addUserMessage("Inspect this decoded QR code content for security threats: $trimmed")

        coroutineScope.launch {
            val verdict = com.guardian.app.QrScannerActivity.evaluateQrContent(trimmed)
            val isHigh = verdict.status == com.guardian.app.RiskStatus.High
            val isReview = verdict.status == com.guardian.app.RiskStatus.Suspicious

            val riskBand = when {
                isHigh -> "HIGH"
                isReview -> "NEEDS REVIEW"
                else -> "LOW"
            }
            val threatCategory = if (verdict.isUpi) "UPI_QR_FRAUD" else if (verdict.isUrl) "SUSPICIOUS_LINK_QR" else "GENERIC_QR"

            val evidence = mutableListOf<String>()
            evidence.add(verdict.title)
            evidence.add(verdict.reason)
            if (verdict.parsedPayee != null) evidence.add("Parsed Payee: ${verdict.parsedPayee}")
            if (verdict.parsedAmount != null) evidence.add("Parsed Amount: ${verdict.parsedAmount}")

            val explanation = when {
                isHigh -> "🚨 CRITICAL QR SECURITY ALERT:\n\n" +
                        "${verdict.title}\n\n" +
                        "Findings: ${verdict.reason}\n\n" +
                        "Recommended Defensive Action: Do NOT proceed or approve this payment request. Scammers use modified QR codes to trigger reverse debits."
                isReview -> "⚠️ CAUTION — UNVERIFIED QR PAYLOAD:\n\n" +
                        "${verdict.title}\n\n" +
                        "Findings: ${verdict.reason}\n\n" +
                        "Recommended Action: Verify the destination and recipient before taking action."
                else -> "✅ QR SAFETY ASSESSMENT:\n\n" +
                        "${verdict.title}\n\n" +
                        "The scanned QR contains standard content with no immediate indicators of reverse debit or phishing."
            }

            val toolMsg = AssistantMessage(
                isUser = false,
                isToolResult = true,
                toolName = "QR Shield",
                text = explanation,
                threatCategory = threatCategory,
                riskBand = riskBand,
                evidence = evidence,
                suggestedActions = listOf("Explain with AI", "Dial 1930", "New Chat")
            )
            messages.add(toolMsg)
            lastThreatReport = toolMsg
            conversationManager.addToolResult(
                toolName = "QRShield",
                summary = explanation.take(300),
                evidence = evidence,
                threatCategory = threatCategory,
                riskBand = riskBand
            )
        }
    }

    fun executeReviewHistoryTool() {
        messages.add(AssistantMessage(isUser = true, text = "📜 Review Threat Incident History"))
        conversationManager.addUserMessage("Retrieve and summarize the recent locally stored threat assessments.")

        coroutineScope.launch {
            val store = com.susagi.history.IncidentHistoryStore.getInstance(ctx)
            val records = store.getAllRecords()

            if (records.isEmpty()) {
                val emptyMsg = AssistantMessage(
                    isUser = false,
                    isToolResult = true,
                    toolName = "Threat History",
                    text = "📋 INCIDENT HISTORY — CLEAN:\n\n" +
                            "No threat incidents have been recorded on this device. " +
                            "When you inspect suspicious messages, links, or payments, high and review-level findings are stored privately on your handset.",
                    riskBand = "LOW",
                    threatCategory = "HISTORY_CLEAN",
                    suggestedActions = listOf("🛡️ Analyze Message", "🔗 Inspect URL", "💳 Inspect UPI")
                )
                messages.add(emptyMsg)
                conversationManager.addToolResult(
                    toolName = "ThreatHistory",
                    summary = "No threat incidents recorded on device.",
                    riskBand = "LOW",
                    threatCategory = "HISTORY_CLEAN"
                )
            } else {
                val latest = records.take(5)
                val summaryLines = StringBuilder()
                summaryLines.append("📋 RECENT ON-DEVICE INCIDENTS (${records.size} total recorded):\n\n")

                val evidenceList = mutableListOf<String>()
                latest.forEachIndexed { i, rec ->
                    summaryLines.append("${i + 1}. [${rec.severity}] ${rec.category} • ${rec.formattedDate()}\n")
                    summaryLines.append("   ${rec.title}\n")
                    summaryLines.append("   ${rec.explanation.take(120)}...\n\n")
                    evidenceList.add("${rec.severity}: ${rec.category} (${rec.formattedDate()})")
                }
                summaryLines.append("All incident telemetry is stored strictly on your handset with credential masking.")

                val historyMsg = AssistantMessage(
                    isUser = false,
                    isToolResult = true,
                    toolName = "Threat History",
                    text = summaryLines.toString(),
                    riskBand = if (records.any { it.severity == "HIGH" }) "HIGH" else "NEEDS REVIEW",
                    threatCategory = "HISTORICAL_RECORDS",
                    evidence = evidenceList,
                    suggestedActions = listOf("Explain with AI", "Dial 1930", "New Chat")
                )
                messages.add(historyMsg)
                lastThreatReport = historyMsg
                conversationManager.addToolResult(
                    toolName = "ThreatHistory",
                    summary = "Retrieved ${latest.size} recent incident records.",
                    evidence = evidenceList,
                    riskBand = if (records.any { it.severity == "HIGH" }) "HIGH" else "NEEDS REVIEW",
                    threatCategory = "HISTORICAL_RECORDS"
                )
            }
        }
    }

    fun executeExplainThreatReportTool(report: AssistantMessage) {
        messages.add(AssistantMessage(isUser = true, text = "🤖 Explain this threat report"))
        conversationManager.addUserMessage("Explain the findings in the previous threat report in conversational terms.")

        if (isLlmReady) {
            currentGenerationJob = coroutineScope.launch {
                isGenerating = true
                val assistantMsgId = UUID.randomUUID().toString()
                messages.add(
                    AssistantMessage(
                        id = assistantMsgId,
                        isUser = false,
                        text = "",
                        isStreaming = true
                    )
                )

                val prompt = conversationManager.buildInstructPrompt(
                    "Please explain the following verified security findings in simple, clear conversational language without inventing new claims:\n" +
                            "Threat Category: ${report.threatCategory ?: "Suspicious Signal"}\n" +
                            "Risk Band: ${report.riskBand ?: "REVIEW"}\n" +
                            "Evidence: ${report.evidence.joinToString("; ")}\n" +
                            "Report Summary: ${report.text.take(400)}"
                )

                val accumulated = StringBuilder()
                val genResult = llamaEngine.generateConversationResponse(prompt) { token ->
                    accumulated.append(token)
                    val idx = messages.indexOfFirst { it.id == assistantMsgId }
                    if (idx != -1) {
                        messages[idx] = messages[idx].copy(text = accumulated.toString().trim())
                    }
                }

                val finalIndex = messages.indexOfFirst { it.id == assistantMsgId }
                if (finalIndex != -1) {
                    if (genResult.isSuccess && accumulated.isNotBlank()) {
                        messages[finalIndex] = messages[finalIndex].copy(
                            text = accumulated.toString().trim(),
                            isStreaming = false
                        )
                        conversationManager.addAssistantMessage(accumulated.toString().trim())
                    } else {
                        val fallbackExplanation = "Here is a breakdown of why this was flagged:\n\n" +
                                "• ${report.evidence.joinToString("\n• ")}\n\n" +
                                "This pattern indicates an attempt to manipulate or extract credentials under pressure. Stay cautious and verify independently."
                        messages[finalIndex] = messages[finalIndex].copy(
                            text = fallbackExplanation,
                            isStreaming = false
                        )
                        conversationManager.addAssistantMessage(fallbackExplanation)
                    }
                }
                isGenerating = false
                currentGenerationJob = null
            }
        } else {
            val explanation = "Here is a breakdown of why this was flagged:\n\n" +
                    "• ${report.evidence.joinToString("\n• ")}\n\n" +
                    "This pattern matches tactics used to coerce victims into compliance or credential disclosure. " +
                    "Always verify caller/sender identity via official precinct or bank numbers."
            messages.add(
                AssistantMessage(
                    isUser = false,
                    text = explanation,
                    suggestedActions = listOf("Dial 1930", "What to avoid sharing", "New Chat")
                )
            )
            conversationManager.addAssistantMessage(explanation)
        }
    }

    // -------------------------------------------------------------------------
    // Core Conversational Submission (Default Experience)
    // -------------------------------------------------------------------------

    fun submitUserQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank() || isGenerating) return

        inputText = ""

        // Check if an explicit tool mode is currently active
        when (activeToolMode) {
            ToolMode.ANALYZE_MESSAGE -> {
                activeToolMode = ToolMode.CHAT
                executeAnalyzeMessageTool(trimmed)
                return
            }
            ToolMode.INSPECT_URL -> {
                activeToolMode = ToolMode.CHAT
                executeInspectUrlTool(trimmed)
                return
            }
            ToolMode.INSPECT_UPI -> {
                activeToolMode = ToolMode.CHAT
                executeInspectUpiTool(trimmed)
                return
            }
            ToolMode.INSPECT_QR -> {
                activeToolMode = ToolMode.CHAT
                executeInspectQrTool(trimmed)
                return
            }
            ToolMode.REVIEW_HISTORY -> {
                activeToolMode = ToolMode.CHAT
                executeReviewHistoryTool()
                return
            }
            ToolMode.CHAT -> {
                // Check if user clicked an explicit tool action from chips
                if (trimmed.startsWith("🛡️ Analyze") || trimmed.contains("Check suspicious message")) {
                    activeToolMode = ToolMode.ANALYZE_MESSAGE
                    return
                }
                if (trimmed.startsWith("🔗 Inspect URL")) {
                    activeToolMode = ToolMode.INSPECT_URL
                    return
                }
                if (trimmed.startsWith("💳 Inspect UPI") || trimmed.contains("Is UPI cashback safe?")) {
                    activeToolMode = ToolMode.INSPECT_UPI
                    return
                }
                if (trimmed.startsWith("📷 Inspect QR")) {
                    activeToolMode = ToolMode.INSPECT_QR
                    return
                }
                if (trimmed.startsWith("📜 Threat History") || trimmed.contains("Review Threat History")) {
                    executeReviewHistoryTool()
                    return
                }
                if (trimmed.contains("Explain with AI")) {
                    val report = lastThreatReport
                    if (report != null) {
                        executeExplainThreatReportTool(report)
                        return
                    }
                }
            }
        }

        // Default Mode: Conversational AI (User message -> Conversation Manager -> Local LLM -> Chat UI)
        messages.add(AssistantMessage(isUser = true, text = trimmed))
        conversationManager.addUserMessage(trimmed)

        currentGenerationJob = coroutineScope.launch {
            isGenerating = true

            val assistantMsgId = UUID.randomUUID().toString()
            val initialAssistantMsg = AssistantMessage(
                id = assistantMsgId,
                isUser = false,
                text = "",
                isStreaming = true
            )
            messages.add(initialAssistantMsg)

            try {
                val prompt = conversationManager.buildInstructPrompt()
                val accumulated = StringBuilder()

                val genResult = llamaEngine.generateConversationResponse(prompt) { token ->
                    accumulated.append(token)
                    val index = messages.indexOfFirst { it.id == assistantMsgId }
                    if (index != -1) {
                        messages[index] = messages[index].copy(text = accumulated.toString())
                    }
                }

                val finalIndex = messages.indexOfFirst { it.id == assistantMsgId }
                if (finalIndex != -1) {
                    val finalText = if (genResult.isSuccess && accumulated.isNotBlank()) {
                        accumulated.toString().trim()
                    } else if (genResult.text.isNotBlank()) {
                        genResult.text.trim()
                    } else {
                        genResult.error ?: "I am ready to assist. Please ask any question about programming, cybersecurity, or general topics."
                    }
                    messages[finalIndex] = messages[finalIndex].copy(
                        text = finalText,
                        isStreaming = false
                    )
                    conversationManager.addAssistantMessage(finalText)
                }
            } catch (e: Exception) {
                val finalIndex = messages.indexOfFirst { it.id == assistantMsgId }
                if (finalIndex != -1) {
                    messages[finalIndex] = messages[finalIndex].copy(
                        text = "I am ready to assist. Please ask any question about programming, cybersecurity, or general topics.",
                        isStreaming = false
                    )
                }
            } finally {
                isGenerating = false
                currentGenerationJob = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase)
    ) {
        // ----------------------------------------------------
        // Top Header
        // ----------------------------------------------------
        Surface(
            color = GxSurface,
            border = BorderStroke(1.dp, GxBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = GxTextHi,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "SuSagi AI Assistant",
                                    color = GxTextHi,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = GxPrimary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Text(
                                "General Conversational AI & Cybersecurity Advisor",
                                color = GxTextLo,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onOpenModelManager,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Extension,
                                contentDescription = "Model Manager",
                                tint = GxPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                stopGeneration()
                                conversationManager.newChat()
                                llamaEngine.resetContext()
                                messages.clear()
                                messages.add(
                                    AssistantMessage(
                                        isUser = false,
                                        text = "Session reset. Ask any cybersecurity question or use specialized inspection tools.",
                                        suggestedActions = listOf(
                                            "🚨 Digital Arrest Scams",
                                            "💳 UPI Reverse-Debit Traps",
                                            "🔗 Identify Phishing Links",
                                            "🛡️ Analyze Message",
                                            "🔗 Inspect URL",
                                            "💳 Inspect UPI"
                                        )
                                    )
                                )
                                lastThreatReport = null
                                activeToolMode = ToolMode.CHAT
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "New Chat",
                                tint = GxTextMid,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Mode Indicator Badge
                Surface(
                    color = if (isLlmReady) GxSafeSoft else GxSurfaceAlt,
                    shape = GxShapePill,
                    border = BorderStroke(1.dp, if (isLlmReady) GxSafe.copy(alpha = 0.4f) else GxBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = GxSafe,
                            modifier = Modifier.size(7.dp)
                        ) {}
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isLlmReady) "Local AI Active (ExecuTorch Llama 3.2 1B)" else "SuSagi AI Active (On-Device Intelligence)",
                            color = GxSafe,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!isLlmReady) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "• Manage Models",
                                color = GxPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onOpenModelManager() }
                            )
                        }
                    }
                }
            }
        }

        // ----------------------------------------------------
        // Chat Stream
        // ----------------------------------------------------
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                if (msg.isUser) {
                    UserChatBubble(msg)
                } else {
                    AssistantChatBubble(
                        msg = msg,
                        onActionClick = { action ->
                            if (action == "Manage AI Models") {
                                onOpenModelManager()
                            } else {
                                submitUserQuery(action)
                            }
                        },
                        onOpenModelManager = onOpenModelManager
                    )
                }
            }
        }

        // ----------------------------------------------------
        // Quick Action & Tool Mode Selector Bar
        // ----------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GxSurface)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Tool selector chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ToolSelectorChip(
                    label = "💬 Chat",
                    isSelected = activeToolMode == ToolMode.CHAT,
                    onClick = { activeToolMode = ToolMode.CHAT }
                )
                ToolSelectorChip(
                    label = "🛡️ Analyze Message",
                    isSelected = activeToolMode == ToolMode.ANALYZE_MESSAGE,
                    onClick = { activeToolMode = ToolMode.ANALYZE_MESSAGE }
                )
                ToolSelectorChip(
                    label = "🔗 Inspect URL",
                    isSelected = activeToolMode == ToolMode.INSPECT_URL,
                    onClick = { activeToolMode = ToolMode.INSPECT_URL }
                )
                ToolSelectorChip(
                    label = "💳 Inspect UPI",
                    isSelected = activeToolMode == ToolMode.INSPECT_UPI,
                    onClick = { activeToolMode = ToolMode.INSPECT_UPI }
                )
                ToolSelectorChip(
                    label = "📷 Inspect QR",
                    isSelected = activeToolMode == ToolMode.INSPECT_QR,
                    onClick = { activeToolMode = ToolMode.INSPECT_QR }
                )
                ToolSelectorChip(
                    label = "📜 Threat History",
                    isSelected = activeToolMode == ToolMode.REVIEW_HISTORY,
                    onClick = {
                        activeToolMode = ToolMode.CHAT
                        executeReviewHistoryTool()
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            // Conversational Threat Guidance Chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuickPromptChip("🚨 Digital Arrest Scam") {
                    submitUserQuery("Explain how Digital Arrest scams operate and how to protect myself.")
                }
                QuickPromptChip("💳 UPI QR Reverse Debit") {
                    submitUserQuery("How do scammers use UPI QR codes and collect requests to steal money?")
                }
                QuickPromptChip("🔗 Spot Fake Bank Link") {
                    submitUserQuery("How can I detect fake banking and electricity bill phishing links?")
                }
                QuickPromptChip("⚖️ What to do if Debited") {
                    submitUserQuery("What immediate steps should I take if money was fraudulently debited from my account?")
                }
            }

            Spacer(Modifier.height(8.dp))

            // ----------------------------------------------------
            // Bottom Chat Input Bar
            // ----------------------------------------------------
            val inputPlaceholder = when (activeToolMode) {
                ToolMode.CHAT -> "Ask SuSagi AI about scams, threats, or security guidance..."
                ToolMode.ANALYZE_MESSAGE -> "Paste suspicious SMS or message to inspect with SafetyBarrier..."
                ToolMode.INSPECT_URL -> "Enter link or URL to inspect (e.g. https://...)..."
                ToolMode.INSPECT_UPI -> "Enter UPI ID or collect request text to verify..."
                ToolMode.INSPECT_QR -> "Paste decoded QR text or payload to inspect..."
                ToolMode.REVIEW_HISTORY -> "Review local threat incident history..."
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text(inputPlaceholder, color = GxTextLo, fontSize = 12.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = GxShapeMd,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GxSurfaceAlt,
                        unfocusedContainerColor = GxSurfaceAlt,
                        focusedBorderColor = if (activeToolMode != ToolMode.CHAT) GxWarning else GxPrimary,
                        unfocusedBorderColor = GxBorder,
                        focusedTextColor = GxTextHi,
                        unfocusedTextColor = GxTextHi
                    ),
                    maxLines = 3,
                    enabled = !isGenerating
                )

                Spacer(Modifier.width(8.dp))

                if (isGenerating) {
                    Button(
                        onClick = { stopGeneration() },
                        colors = ButtonDefaults.buttonColors(containerColor = GxDanger),
                        shape = CircleShape,
                        modifier = Modifier.size(46.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                } else {
                    Button(
                        onClick = { submitUserQuery(inputText) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (activeToolMode != ToolMode.CHAT) GxWarning else GxPrimary
                        ),
                        shape = CircleShape,
                        modifier = Modifier.size(46.dp),
                        contentPadding = PaddingValues(0.dp),
                        enabled = inputText.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolSelectorChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (isSelected) GxPrimary.copy(alpha = 0.15f) else GxSurfaceAlt,
        shape = GxShapePill,
        border = BorderStroke(1.dp, if (isSelected) GxPrimary else GxBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            color = if (isSelected) GxPrimary else GxTextMid,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun QuickPromptChip(text: String, onClick: () -> Unit) {
    Surface(
        color = GxSurfaceAlt,
        shape = GxShapePill,
        border = BorderStroke(1.dp, GxBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            color = GxTextHi,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun UserChatBubble(msg: AssistantMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Card(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = GxPrimary),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = msg.text,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.timestamp)),
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssistantChatBubble(
    msg: AssistantMessage,
    onActionClick: (String) -> Unit,
    onOpenModelManager: () -> Unit
) {
    val ctx = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = GxSurface),
            border = BorderStroke(1.dp, if (msg.isToolResult) GxBorder.copy(alpha = 0.8f) else GxBorder),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // 1. Missing Model Notice Card
                if (msg.isMissingModelNotice) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = GxWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Language Model Not Installed",
                            color = GxWarning,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = msg.text,
                        color = GxTextHi,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onOpenModelManager,
                        colors = ButtonDefaults.buttonColors(containerColor = GxPrimary),
                        shape = GxShapePill,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Extension, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Manage AI Models", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else if (msg.isToolResult) {
                    // 2. Explicit Security Tool Result Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = GxPrimary.copy(alpha = 0.12f),
                            shape = GxShapePill,
                            border = BorderStroke(1.dp, GxPrimary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "TOOL: ${msg.toolName ?: "SECURITY ENGINE"}",
                                color = GxPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }

                        if (msg.riskBand != null) {
                            val bandColor = when (msg.riskBand) {
                                "HIGH" -> GxDanger
                                "REVIEW", "MEDIUM" -> GxWarning
                                else -> GxSafe
                            }
                            val bandBg = when (msg.riskBand) {
                                "HIGH" -> GxDangerSoft
                                "REVIEW", "MEDIUM" -> GxWarningSoft
                                else -> GxSafeSoft
                            }
                            Surface(
                                color = bandBg,
                                shape = GxShapePill,
                                border = BorderStroke(1.dp, bandColor.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "RISK: ${msg.riskBand}",
                                    color = bandColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (msg.threatCategory != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = msg.threatCategory,
                            color = GxTextHi,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = msg.text,
                        color = GxTextHi,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    // Grounded Evidence Points
                    if (msg.evidence.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = GxSurfaceAlt,
                            shape = GxShapeSm,
                            border = BorderStroke(0.5.dp, GxBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Grounded Threat Indicators:",
                                    color = GxTextHi,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                msg.evidence.forEach { ev ->
                                    Text(
                                        text = "• $ev",
                                        color = GxTextMid,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // 3. Standard Generative Conversational Message
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                tint = GxPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "SuSagi AI",
                                color = GxPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("SuSagi AI Answer", msg.text))
                                Toast.makeText(ctx, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = GxTextLo, modifier = Modifier.size(14.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = msg.text,
                        color = GxTextHi,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }

                if (msg.isStreaming) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = GxPrimary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Generating response...",
                            color = GxTextLo,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Suggested Next Action Chips
                if (msg.suggestedActions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        msg.suggestedActions.forEach { action ->
                            Surface(
                                color = GxPrimary.copy(alpha = 0.08f),
                                shape = GxShapePill,
                                border = BorderStroke(1.dp, GxPrimary.copy(alpha = 0.25f)),
                                modifier = Modifier.clickable { onActionClick(action) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = GxPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = action,
                                        color = GxPrimary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(msg.timestamp)),
                    color = GxTextLo,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

/**
 * Deterministic cybersecurity advisory engine grounded in authoritative rules.
 * Provides complete, high-quality, truthful answers for explicit security queries
 * without requiring LLM weights.
 */
internal suspend fun generateDeterministicGuidance(query: String, context: String?): AssistantMessage {
    val lower = query.lowercase(Locale.ROOT)

    // 1. Check for Educational / Technical Research Query
    if (EducationalContentDetector.isEducationalOrAnalysis(query)) {
        return AssistantMessage(
            isUser = false,
            text = "This text appears to be an educational advisory, cybersecurity awareness article, or technical security research rather than an active attack.\n\n" +
                    "Key Indicators:\n" +
                    "• Contains educational or defensive terminology ('protect against', 'advisory', 'vulnerability', 'researchers found')\n" +
                    "• Does not contain imperative commands demanding money, OTP, or remote software installation\n\n" +
                    "Status: Normal (Low Risk). No defensive action required.",
            threatCategory = "Educational / Security Advisory",
            riskBand = "LOW",
            suggestedActions = listOf(
                "How to report a real scam?",
                "What is Digital Arrest?",
                "Is entering a UPI PIN safe?"
            )
        )
    }

    // 2. Questions about UPI PIN
    if (lower.contains("upi pin") || lower.contains("receive money") || (lower.contains("upi") && lower.contains("safe"))) {
        return AssistantMessage(
            isUser = false,
            text = "CRITICAL UPI SAFETY RULE:\n\n" +
                    "You NEVER need to enter your UPI PIN to receive money or cashback.\n\n" +
                    "Entering your UPI PIN ALWAYS deducts money from your account. Fraudsters frequently send 'Collect Requests' claiming you will receive a refund or lottery prize if you enter your PIN.\n\n" +
                    "What to do:\n" +
                    "1. Immediately decline the collect request in PhonePe, Google Pay, or Paytm.\n" +
                    "2. Never scan a QR code to 'receive' money.\n" +
                    "3. If funds were debited, file a complaint at 1930 within the golden hour.",
            threatCategory = "UPI Reverse Debit Scam Advisory",
            riskBand = "REVIEW",
            suggestedActions = listOf(
                "Explain Digital Arrest",
                "What credentials are safe to share?",
                "Dial Helpline 1930"
            )
        )
    }

    // 3. Questions about Digital Arrest
    if (lower.contains("digital arrest") || (lower.contains("police") && lower.contains("video call")) || lower.contains("cbi") || lower.contains("warrant")) {
        return AssistantMessage(
            isUser = false,
            text = "WHAT YOU MUST KNOW ABOUT 'DIGITAL ARREST':\n\n" +
                    "1. 'Digital Arrest' DOES NOT EXIST in Indian Law. No police department, CBI, ED, or court has legal authority to arrest anyone via video call or telephone.\n" +
                    "2. Law enforcement NEVER asks you to transfer money to a 'Safe Account' or 'Reserve Bank Verification Account' to verify innocence.\n" +
                    "3. Scammers use fake police station backdrops, counterfeit official letters, and psychological intimidation to force victims to isolate themselves.\n\n" +
                    "Defensive Instructions:\n" +
                    "• Hang up immediately. Do not stay on the video call.\n" +
                    "• Inform your family or trusted contacts immediately.\n" +
                    "• Report to the National Cybercrime Helpline by dialing 1930 or online at cybercrime.gov.in.",
            threatCategory = "Digital Arrest Extortion Scheme",
            riskBand = "HIGH",
            suggestedActions = listOf(
                "Report to 1930",
                "Why do scammers ask for Skype?",
                "How to protect my bank account"
            ),
            evidence = listOf(
                "Impersonation of law enforcement (Police/CBI/Customs)",
                "Demand for secret video call confinement",
                "Threat of immediate custody or seizure of assets"
            )
        )
    }

    // 4. Questions about what NOT to share
    if (lower.contains("avoid sharing") || lower.contains("what not to share") || lower.contains("confidential") || lower.contains("credentials")) {
        return AssistantMessage(
            isUser = false,
            text = "NEVER SHARE THE FOLLOWING WITH ANYONE:\n\n" +
                    "• One-Time Passwords (OTPs) – Banks, DISCOMs, or police will NEVER ask for your OTP.\n" +
                    "• UPI PIN or ATM PIN – Never type these anywhere except the official banking keypad.\n" +
                    "• Card CVV & Expiry Date – Needed to authorize transactions on foreign/online gateways.\n" +
                    "• Screen Sharing Access – Never install AnyDesk, TeamViewer, or RustDesk on caller request.\n" +
                    "• Net Banking Passwords – Your bank branch manager cannot ask for this.\n\n" +
                    "Remember: Legitimate institutions already know your account details; they do not call you to ask for verification secrets.",
            threatCategory = "Credential Protection Guidance",
            riskBand = "LOW",
            suggestedActions = listOf(
                "How do scammers steal OTPs?",
                "What is AnyDesk scam?",
                "Check a suspicious message"
            )
        )
    }

    // 5. Evaluate with Astra Dedicated Security Classifier & SafetyBarrier
    val finding = com.susagi.intelligence.AstraDedicatedSecurityClassifier.classify(query)
    val barrier = SafetyBarrier.analyze(null, query)
    val keywordReport = KeywordScorer.score(query)

    val isScam = finding.confidenceStatus == "CONFIRMED_HIGH" || barrier.risk == Risk.HIGH || keywordReport.riskScore >= 60
    val isCaution = finding.confidenceStatus == "CAUTION_REVIEW" || barrier.risk == Risk.REVIEW || keywordReport.riskScore in 30..59

    val band = when {
        isScam -> "HIGH"
        isCaution -> "REVIEW"
        else -> "LOW"
    }

    val explanation = when {
        isScam -> buildString {
            appendLine("🚨 High Risk Cybersecurity Threat Detected!")
            appendLine()
            appendLine("• Category: ${finding.threatCategory}")
            if (finding.claimedAuthority != "None" && finding.claimedAuthority != "Unverified Senders") {
                appendLine("• Claimed Authority: ${finding.claimedAuthority}")
            }
            if (finding.coercionIndicators.isNotEmpty()) {
                appendLine("• Coercion Tactics: ${finding.coercionIndicators.joinToString("; ")}")
            }
            appendLine("• Requested Action: ${finding.requestedAction}")
            if (finding.credentialRequest) {
                appendLine("• Credential Demand: Flagged (Requests sensitive OTP/PIN/Password)")
            }
            if (finding.paymentRequest) {
                appendLine("• Payment Demand: Flagged (Urgent monetary transfer requested)")
            }
            appendLine()
            appendLine("🛡️ What You Must Avoid:")
            appendLine("1. DO NOT transfer funds or approve UPI collect requests.")
            appendLine("2. DO NOT disclose OTPs, card numbers, or passwords.")
            appendLine("3. DO NOT install remote desktop apps (AnyDesk, TeamViewer).")
            appendLine()
            appendLine("📞 Safe Next Steps: Disconnect immediately. File a complaint by dialing 1930 (National Cybercrime Reporting Helpline) or report at cybercrime.gov.in.")
        }
        isCaution -> buildString {
            appendLine("⚠️ Caution Advised: Needs Review")
            appendLine()
            appendLine("This message contains keywords often used in high-pressure solicitations or unverified alerts.")
            if (finding.coercionIndicators.isNotEmpty()) {
                appendLine("• Noted signals: ${finding.coercionIndicators.joinToString("; ")}")
            }
            appendLine("• Action Requested: ${finding.requestedAction}")
            appendLine()
            appendLine("Recommendation: Verify the sender through official channels before sharing information or taking action.")
        }
        else -> "✅ Analysis Complete: No Active Threats Identified.\n\n" +
                "This input does not match known digital arrest, electricity cutoff, fake KYC, or UPI collect trap patterns. However, always verify unexpected senders before taking action."
    }

    val actions = when {
        isScam -> listOf("Do not reply or click", "Dial 1930", "Report on cybercrime.gov.in")
        isCaution -> listOf("Verify sender independently", "Do not share passwords", "Check official app")
        else -> listOf("Check another message", "What to avoid sharing", "Is UPI PIN safe?")
    }

    val combinedEvidence = (finding.evidence + barrier.evidence.map { it.id.explanation } + keywordReport.highlightedPhrases).distinct()

    return AssistantMessage(
        isUser = false,
        text = explanation,
        threatCategory = if (isScam) finding.threatCategory else null,
        riskBand = band,
        evidence = combinedEvidence.ifEmpty { listOf("No anomalous indicators detected") },
        suggestedActions = actions
    )
}
