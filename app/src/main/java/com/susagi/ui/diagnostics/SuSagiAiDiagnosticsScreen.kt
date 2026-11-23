package com.susagi.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.guardian.app.ui.theme.GxPrimarySoft
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
import com.susagi.edgeai.DiagnosticReport
import com.susagi.edgeai.DiagnosticStepId
import com.susagi.edgeai.DiagnosticStepResult
import com.susagi.edgeai.DiagnosticStepStatus
import com.susagi.edgeai.ExecuTorchLlamaEngine
import com.susagi.edgeai.ModelManager
import com.susagi.edgeai.SuSagiAiDiagnosticsRunner
import kotlinx.coroutines.launch

/**
 * SuSagi AI Diagnostics Screen
 *
 * Implements Directive 54 Phase 4 & Phase 14:
 * Developer-accessible diagnostics inspecting the genuine state of the ExecuTorch runtime.
 * Evaluates all 10 diagnostic steps, displays precise error codes, and provides sanitized export.
 */
@Composable
fun SuSagiAiDiagnosticsScreen(
    onBack: () -> Unit,
    onOpenModelManager: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val modelManager = remember { ModelManager(ctx) }
    val llamaEngine = remember { ExecuTorchLlamaEngine(ctx, modelManager) }
    val conversationManager = remember { ConversationManager() }
    val runner = remember { SuSagiAiDiagnosticsRunner(ctx, modelManager, llamaEngine, conversationManager) }

    var isRunning by remember { mutableStateOf(false) }
    var currentReport by remember { mutableStateOf<DiagnosticReport?>(null) }
    var stepResults by remember {
        mutableStateOf<Map<DiagnosticStepId, DiagnosticStepResult>>(
            DiagnosticStepId.values().associateWith { id ->
                DiagnosticStepResult(
                    stepId = id,
                    title = id.title,
                    status = DiagnosticStepStatus.PENDING,
                    details = "Pending execution. Tap 'Run AI Diagnostics' to verify."
                )
            }
        )
    }

    val hardwareSummary = remember { modelManager.getHardwareSummary() }

    fun runDiagnostics() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                val report = runner.runAllDiagnostics { update ->
                    stepResults = stepResults + (update.stepId to update)
                }
                currentReport = report
            } finally {
                isRunning = false
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Bar
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GxTextHi,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "SuSagi AI Health Check",
                        color = GxTextHi,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Arm ExecuTorch Native Runtime Self-Test & Diagnostics",
                        color = GxTextLo,
                        fontSize = 12.sp
                    )
                }
                Surface(
                    color = GxSurfaceAlt,
                    shape = GxShapeSm,
                    border = BorderStroke(0.5.dp, GxBorder)
                ) {
                    Text(
                        "DEV MODE",
                        color = GxPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Hardware & Environment Summary
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = GxSurface),
                shape = GxShapeMd,
                border = BorderStroke(1.dp, GxBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "HARDWARE TELEMETRY",
                            color = GxPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Icon(Icons.Default.Memory, contentDescription = null, tint = GxPrimary, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("CPU Architecture", color = GxTextLo, fontSize = 11.sp)
                            Text(
                                hardwareSummary["cpu_abi"] ?: "arm64-v8a",
                                color = GxTextHi,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column {
                            Text("Available RAM", color = GxTextLo, fontSize = 11.sp)
                            Text(
                                "${hardwareSummary["available_ram_mb"] ?: 0} MB",
                                color = GxTextHi,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Column {
                            Text("Free Storage", color = GxTextLo, fontSize = 11.sp)
                            Text(
                                "${hardwareSummary["storage_free_mb"] ?: 0} MB",
                                color = GxTextHi,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Control Panel & Overall Status
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = GxSurface),
                shape = GxShapeMd,
                border = BorderStroke(1.dp, GxBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    val statusText = currentReport?.overallStatus ?: if (isRunning) "RUNNING DIAGNOSTICS..." else "READY TO TEST"
                    val (statusColor, statusBg) = when (currentReport?.overallStatus) {
                        "PASS" -> GxSafe to GxSafeSoft
                        "FAIL" -> GxDanger to GxDangerSoft
                        "CONDITIONAL / BLOCKED" -> GxWarning to GxWarningSoft
                        else -> GxPrimary to GxPrimarySoft
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DIAGNOSTIC STATUS", color = GxTextLo, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Surface(color = statusBg, shape = GxShapePill) {
                            Text(
                                statusText,
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { runDiagnostics() },
                            enabled = !isRunning,
                            colors = ButtonDefaults.buttonColors(containerColor = GxPrimary),
                            shape = GxShapeSm,
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            if (isRunning) {
                                CircularProgressIndicator(color = GxBase, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Testing...", color = GxBase, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = GxBase, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Run Health Check", color = GxBase, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (currentReport != null) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("SuSagi AI Diagnostics", currentReport?.toRedactedMarkdown() ?: "")
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(ctx, "Redacted diagnostic report copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                shape = GxShapeSm,
                                border = BorderStroke(1.dp, GxBorder),
                                modifier = Modifier.height(42.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GxTextHi, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Export", color = GxTextHi, fontSize = 12.sp)
                            }
                        }
                    }

                    if (currentReport?.overallStatus == "CONDITIONAL / BLOCKED") {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onOpenModelManager,
                            shape = GxShapeSm,
                            border = BorderStroke(1.dp, GxPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = GxPrimary, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Model Manager to Download Weights", color = GxPrimary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Section Header
        item {
            Text(
                "VERIFICATION GATES (10 NATIVE TESTS)",
                color = GxTextLo,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // 10 Diagnostic Steps
        items(DiagnosticStepId.values().toList()) { stepId ->
            val result = stepResults[stepId] ?: DiagnosticStepResult(
                stepId = stepId,
                title = stepId.title,
                status = DiagnosticStepStatus.PENDING,
                details = "Pending..."
            )

            var isExpanded by remember { mutableStateOf(false) }

            val (badgeColor, badgeBg, badgeIcon) = when (result.status) {
                DiagnosticStepStatus.PASS -> Triple(GxSafe, GxSafeSoft, Icons.Default.CheckCircle)
                DiagnosticStepStatus.FAIL, DiagnosticStepStatus.FAILED -> Triple(GxDanger, GxDangerSoft, Icons.Default.Error)
                DiagnosticStepStatus.BLOCKED -> Triple(GxWarning, GxWarningSoft, Icons.Default.Warning)
                DiagnosticStepStatus.RUNNING -> Triple(GxPrimary, GxPrimarySoft, Icons.Default.HourglassTop)
                DiagnosticStepStatus.PENDING, DiagnosticStepStatus.NOT_RUN -> Triple(GxTextLo, GxSurfaceAlt, Icons.Default.BugReport)
            }

            Surface(
                color = GxSurface,
                shape = GxShapeMd,
                border = BorderStroke(0.5.dp, if (result.status == DiagnosticStepStatus.PASS) GxSafe.copy(alpha = 0.3f) else GxBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                color = GxSurfaceAlt,
                                shape = GxShapeSm,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        "${stepId.stepNumber}",
                                        color = GxTextHi,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    stepId.title,
                                    color = GxTextHi,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (result.latencyMs > 0) {
                                    Text(
                                        "${result.latencyMs} ms",
                                        color = GxTextLo,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }

                        Surface(
                            color = badgeBg,
                            shape = GxShapePill
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(badgeIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    result.status.name,
                                    color = badgeColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Text(
                        result.details,
                        color = if (result.status == DiagnosticStepStatus.FAIL) GxDanger else GxTextMid,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )

                    if (result.errorCode != null) {
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = GxSurfaceAlt,
                            shape = GxShapeSm,
                            border = BorderStroke(0.5.dp, GxBorder)
                        ) {
                            Text(
                                "Error Code: ${result.errorCode.name}",
                                color = GxWarning,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
