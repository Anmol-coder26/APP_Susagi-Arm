package com.susagi.ui.demo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.susagi.network.NetworkFlowImporter
import com.susagi.network.NetworkImportReport
import com.susagi.safety.Assessment
import com.susagi.safety.Risk
import com.susagi.safety.SafetyBarrier
import kotlinx.coroutines.launch

data class DemoScenario(
    val id: String,
    val title: String,
    val description: String,
    val inputSnippet: String,
    val channel: String, // "MESSAGE", "LINK", "NETWORK"
    val expectedOutcome: String
)

@Composable
fun OfflineDemoScreen(
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val networkImporter = remember { NetworkFlowImporter() }

    val scenarios = listOf(
        DemoScenario(
            id = "s1_arrest",
            title = "Digital Arrest Extortion (Hinglish / Hindi)",
            description = "Simulates police impersonation coercing victim to stay on call and transfer funds.",
            inputSnippet = "Aap digital arrest mein hain. Do not disconnect the line and transfer fifty thousand rupees immediately to avoid FIR.",
            channel = "MESSAGE",
            expectedOutcome = "HIGH (Extortion, Arrest threat)"
        ),
        DemoScenario(
            id = "s2_bank_credit",
            title = "Legitimate Bank Credit (Benign Control)",
            description = "Simulates authentic bank credit alert with UPI reference number and account balance.",
            inputSnippet = "Dear Customer, your account XX1234 has been credited with INR 2,500.00 via UPI on 22-Nov-2026. Avl Bal: INR 18,450.00.",
            channel = "MESSAGE",
            expectedOutcome = "LOW (No known local threat indicators)"
        ),
        DemoScenario(
            id = "s3_multi_link_apk",
            title = "Multi-Link Phishing & APK Sideload",
            description = "Combines an electricity cutoff threat with a phishing portal and direct .apk download.",
            inputSnippet = "Electricity bill unpaid. Power cut tonight! Pay immediately at https://fake-power-bill.top or install our helper app https://bill-helper.example/update.apk",
            channel = "LINK",
            expectedOutcome = "HIGH (Utility extortion, APK sideload)"
        ),
        DemoScenario(
            id = "s4_upi_mandate",
            title = "Cashback UPI Mandate Trap",
            description = "Promises credit but demands collect approval / PIN entry in UPI app.",
            inputSnippet = "Congratulations! To receive your cashback of ₹4,500, scan this QR code and approve the collect mandate in your UPI app.",
            channel = "MESSAGE",
            expectedOutcome = "HIGH (Payment trap, UPI mandate fraud)"
        ),
        DemoScenario(
            id = "s5_screen_share",
            title = "Screen Sharing Credential Harvesting",
            description = "Fraudster requests screen sharing while instructing user to enter financial credentials.",
            inputSnippet = "Share your screen while entering your UPI PIN so our banking engineer can verify the refund.",
            channel = "MESSAGE",
            expectedOutcome = "HIGH (Screen credential harvesting)"
        ),
        DemoScenario(
            id = "s6_network_c2",
            title = "Network Anomaly & C2 Beaconing",
            description = "Evaluates imported NetFlow telemetry exhibiting periodic heartbeats to a remote C2 server.",
            inputSnippet = "c2_beaconing_telemetry.csv (Periodic 10s SYN;ACK heartbeats with minimal jitter)",
            channel = "NETWORK",
            expectedOutcome = "HIGH (C2 Beaconing Anomaly)"
        )
    )

    var selectedScenario by remember { mutableStateOf<DemoScenario?>(null) }
    var activeAssessment by remember { mutableStateOf<Assessment?>(null) }
    var activeNetworkReport by remember { mutableStateOf<NetworkImportReport?>(null) }
    var isExecuting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GxTextHi)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "Offline Demo Scenarios",
                    color = GxTextHi,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Live execution of real production security engines",
                    color = GxTextLo,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Grounded Audit Notice
        Surface(
            color = GxSurfaceAlt,
            shape = GxShapeSm,
            border = BorderStroke(1.dp, GxBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = GxPrimary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "DEMO ASSURANCE: Every scenario below is passed dynamically through the actual local SafetyBarrier and NetworkAnomalyClassifier rule-engines. Verdicts, risk categories, and advice are generated live on this phone.",
                    color = GxTextLo,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Scenario Selector Cards
        scenarios.forEach { sc ->
            val isSelected = selectedScenario?.id == sc.id
            Card(
                shape = GxShapeMd,
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) GxSurfaceAlt else GxSurface
                ),
                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) GxPrimary else GxBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        selectedScenario = sc
                        activeAssessment = null
                        activeNetworkReport = null
                        isExecuting = true
                        scope.launch {
                            if (sc.channel == "NETWORK") {
                                activeNetworkReport = networkImporter.loadSampleCapture("c2_beacon")
                            } else {
                                activeAssessment = SafetyBarrier.analyze(null, sc.inputSnippet)
                            }
                            isExecuting = false
                        }
                    }
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            sc.title,
                            color = GxTextHi,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            color = GxSurface,
                            shape = GxShapePill,
                            border = BorderStroke(0.5.dp, GxBorder)
                        ) {
                            Text(
                                sc.channel,
                                color = GxTextLo,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        sc.description,
                        color = GxTextMid,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )

                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Input: \"${sc.inputSnippet.take(90)}...\"",
                        color = GxTextLo,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Live Execution Output Card
        val assessment = activeAssessment
        val netReport = activeNetworkReport
        if (assessment != null || netReport != null) {
            Spacer(Modifier.height(20.dp))
            Text(
                "LIVE PRODUCTION ENGINE OUTPUT",
                color = GxTextLo,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(8.dp))

            if (assessment != null) {
                val statusColor = when (assessment.risk) {
                    Risk.HIGH -> GxDanger
                    Risk.REVIEW, Risk.UNKNOWN -> GxWarning
                    Risk.LOW -> GxSafe
                }
                val statusSoft = when (assessment.risk) {
                    Risk.HIGH -> GxDangerSoft
                    Risk.REVIEW, Risk.UNKNOWN -> GxWarningSoft
                    Risk.LOW -> GxSafeSoft
                }

                Card(
                    shape = GxShapeLg,
                    colors = CardDefaults.cardColors(containerColor = GxSurface),
                    border = BorderStroke(1.dp, GxBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = statusSoft,
                                shape = GxShapePill,
                                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    "VERDICT: ${assessment.risk.name}",
                                    color = statusColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                "ACTION: ${assessment.action.name}",
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            assessment.explanation,
                            color = GxTextHi,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        if (assessment.evidence.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "SIGNALS: ${assessment.evidence.joinToString { it.id.name }}",
                                color = GxPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (assessment.destinations.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "EXTRACTED DESTINATIONS (${assessment.destinations.size}):",
                                color = GxTextLo,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            assessment.destinations.forEach { dest ->
                                Text(
                                    "• ${dest.originalUrl} [${dest.assessment.risk}]",
                                    color = GxTextMid,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(
                            "ADVICE: ${assessment.recommendations.firstOrNull() ?: "Verify independently."}",
                            color = GxTextHi,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            if (netReport != null) {
                val verdict = netReport.overallVerdict
                val statusColor = if (verdict.severity == "HIGH") GxDanger else GxSafe

                Card(
                    shape = GxShapeLg,
                    colors = CardDefaults.cardColors(containerColor = GxSurface),
                    border = BorderStroke(1.dp, GxBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            "NETWORK ANOMALY DETECTED: ${verdict.anomalyType.name}",
                            color = statusColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            verdict.explanation,
                            color = GxTextHi,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Flows Evaluated: ${netReport.totalFlowsParsed} | Risk: ${verdict.riskScore}/100",
                            color = GxTextMid,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
