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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Mic
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
import com.guardian.app.KeywordScorer
import com.susagi.edgeai.ExecuTorchLlamaEngine
import com.susagi.edgeai.ModelManager
import com.susagi.edgeai.ThreatChannel
import com.susagi.edgeai.ThreatInput
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

data class AssistantMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val evidence: List<String> = emptyList(),
    val threatCategory: String? = null,
    val riskBand: String? = null, // "LOW", "REVIEW", "HIGH"
    val suggestedActions: List<String> = emptyList(),
    val isStreaming: Boolean = false
)

/**
 * SuSagi AI Assistant Screen
 *
 * Dedicated conversational cybersecurity advisor and threat explainer.
 * Operates in two truthful modes:
 * - Local AI Conversation Mode (ExecuTorch Arm LLM) when provisioned.
 * - Security Guidance Mode (Deterministic, Grounded in SafetyBarrier) when unprovisioned.
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
    val isLlmReady = remember { llamaEngine.isReady }

    val messages = remember {
        mutableStateListOf(
            AssistantMessage(
                isUser = false,
                text = "Hello! I am your SuSagi Cybersecurity Assistant. You can paste suspicious messages, ask if a payment request or UPI link is safe, or get guidance on how to avoid digital arrest extortion.",
                suggestedActions = listOf(
                    "Check suspicious message",
                    "Is UPI cashback safe?",
                    "What to avoid sharing"
                )
            )
        )
    }

    var inputText by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var currentGenerationJob by remember { mutableStateOf<Job?>(null) }
    var lastEvaluatedThreatContext by remember { mutableStateOf<String?>(null) }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun stopGeneration() {
        currentGenerationJob?.cancel()
        currentGenerationJob = null
        isGenerating = false
        if (messages.isNotEmpty() && messages.last().isStreaming) {
            val last = messages.last()
            messages[messages.size - 1] = last.copy(isStreaming = false)
        }
    }

    fun submitUserQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank() || isGenerating) return

        inputText = ""
        messages.add(AssistantMessage(isUser = true, text = trimmed))

        currentGenerationJob = coroutineScope.launch {
            isGenerating = true

            // Temporary streaming placeholder message
            val assistantMsgId = UUID.randomUUID().toString()
            val initialAssistantMsg = AssistantMessage(
                id = assistantMsgId,
                isUser = false,
                text = "",
                isStreaming = true
            )
            messages.add(initialAssistantMsg)

            if (isLlmReady) {
                // ExecuTorch Local AI Mode
                try {
                    val threatInput = ThreatInput(
                        rawContent = trimmed,
                        channel = ThreatChannel.MESSAGE,
                        metadata = mapOf("context" to (lastEvaluatedThreatContext ?: ""))
                    )
                    val result = llamaEngine.analyzeThreat(threatInput)

                    // Stream response tokens
                    val fullResponse = buildString {
                        append(result.explanation)
                        if (result.recommendedActions.isNotEmpty()) {
                            append("\n\n🛡️ Recommended Actions:\n")
                            result.recommendedActions.forEach { act ->
                                append("• $act\n")
                            }
                        }
                    }

                    // Simulated smooth token stream for UI responsiveness
                    val words = fullResponse.split(" ")
                    val accumulated = StringBuilder()
                    for (i in words.indices) {
                        accumulated.append(words[i]).append(" ")
                        val index = messages.indexOfFirst { it.id == assistantMsgId }
                        if (index != -1) {
                            messages[index] = messages[index].copy(
                                text = accumulated.toString().trim(),
                                threatCategory = result.threatType,
                                riskBand = result.severity,
                                suggestedActions = result.recommendedActions,
                                evidence = result.evidence
                            )
                        }
                        delay(25)
                    }

                    val finalIndex = messages.indexOfFirst { it.id == assistantMsgId }
                    if (finalIndex != -1) {
                        messages[finalIndex] = messages[finalIndex].copy(isStreaming = false)
                    }
                } catch (e: Exception) {
                    val fallbackAnswer = generateDeterministicGuidance(trimmed, lastEvaluatedThreatContext)
                    val index = messages.indexOfFirst { it.id == assistantMsgId }
                    if (index != -1) {
                        messages[index] = fallbackAnswer.copy(id = assistantMsgId, isStreaming = false)
                    }
                }
            } else {
                // Deterministic Security Guidance Mode (Truthful, immediate, no fake token delays)
                val response = generateDeterministicGuidance(trimmed, lastEvaluatedThreatContext)
                if (response.threatCategory != null) {
                    lastEvaluatedThreatContext = "${response.threatCategory}: ${response.text}"
                }

                val finalIndex = messages.indexOfFirst { it.id == assistantMsgId }
                if (finalIndex != -1) {
                    messages[finalIndex] = response.copy(id = assistantMsgId, isStreaming = false)
                }
            }

            isGenerating = false
            currentGenerationJob = null
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
                                "Conversational Cybersecurity Advisor",
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
                                messages.clear()
                                messages.add(
                                    AssistantMessage(
                                        isUser = false,
                                        text = "Conversation reset. How can I help you stay safe today?",
                                        suggestedActions = listOf(
                                            "Check suspicious message",
                                            "Is UPI cashback safe?",
                                            "What to avoid sharing"
                                        )
                                    )
                                )
                                lastEvaluatedThreatContext = null
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Clear Chat",
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
                            color = if (isLlmReady) GxSafe else GxPrimary,
                            modifier = Modifier.size(7.dp)
                        ) {}
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (isLlmReady) "Local AI Ready (ExecuTorch Arm)" else "Security Guidance Mode (Deterministic)",
                            color = if (isLlmReady) GxSafe else GxTextMid,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!isLlmReady) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "• Manage AI Models",
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
                    AssistantChatBubble(msg = msg, onActionClick = { submitUserQuery(it) })
                }
            }
        }

        // ----------------------------------------------------
        // Quick Action Chips Row
        // ----------------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(GxSurface)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                QuickPromptChip("🔍 Analyze SMS/Text") {
                    inputText = "CBI officer calling: arrest warrant issued against your Aadhaar. Pay 50000 immediately to avoid digital arrest."
                }
                QuickPromptChip("💳 Is UPI PIN for receiving money?") {
                    submitUserQuery("Can I receive money by entering my UPI PIN?")
                }
                QuickPromptChip("🚨 Why is Digital Arrest fake?") {
                    submitUserQuery("Explain why digital arrest calls are fake and what law enforcement actually does.")
                }
                QuickPromptChip("🔒 What should I never share?") {
                    submitUserQuery("What confidential credentials should I never share on calls or SMS?")
                }
                QuickPromptChip("📚 Educational Advisory Check") {
                    inputText = "Cybersecurity researchers found an advisory explaining how to protect against digital arrest and phishing attacks."
                }
            }

            Spacer(Modifier.height(8.dp))

            // ----------------------------------------------------
            // Bottom Chat Input Bar
            // ----------------------------------------------------
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Ask SuSagi Security Assistant...", color = GxTextLo, fontSize = 13.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = GxShapeMd,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = GxSurfaceAlt,
                        unfocusedContainerColor = GxSurfaceAlt,
                        focusedBorderColor = GxPrimary,
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
                        colors = ButtonDefaults.buttonColors(containerColor = GxPrimary),
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
    onActionClick: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            colors = CardDefaults.cardColors(containerColor = GxSurface),
            border = BorderStroke(1.dp, GxBorder),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header badge row if threat/risk info exists
                if (msg.riskBand != null || msg.threatCategory != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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

                        if (msg.threatCategory != null) {
                            Text(
                                text = msg.threatCategory,
                                color = GxTextMid,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // Main body text
                Text(
                    text = msg.text,
                    color = GxTextHi,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

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
                            text = "Analyzing security signals...",
                            color = GxTextLo,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Evidence list
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

                // Suggested Next Action Chips
                if (msg.suggestedActions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Recommended Next Steps:",
                        color = GxTextLo,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
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
 * Provides complete, high-quality, truthful answers without requiring LLM weights.
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
