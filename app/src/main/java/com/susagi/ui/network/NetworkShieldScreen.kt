package com.susagi.ui.network

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
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
import com.susagi.network.NetworkAnomalyType
import com.susagi.network.NetworkFlowImporter
import com.susagi.network.NetworkImportReport

@Composable
fun NetworkShieldScreen(
    onBack: () -> Unit
) {
    val importer = remember { NetworkFlowImporter() }
    var currentReport by remember { mutableStateOf<NetworkImportReport?>(null) }
    val scrollState = rememberScrollState()

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
                    "Network Flow Shield",
                    color = GxTextHi,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "On-device NetFlow & CICIDS anomaly classifier",
                    color = GxTextLo,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Privacy & Limitation Disclaimer
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
                    "PRIVACY & AUDIT NOTICE: This module evaluates user-imported network flow telemetry files (.csv) strictly on-device. It does not perform invasive live device-wide packet inspection, TLS decryption, or background traffic interception.",
                    color = GxTextLo,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // Sample Fixture Selection / Import Triggers
        Text(
            "TEST CAPTURES & SAMPLES",
            color = GxTextLo,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { currentReport = importer.loadSampleCapture("c2_beacon") },
                border = BorderStroke(1.dp, GxBorder),
                shape = GxShapeSm,
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = GxDanger, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("C2 Beacon", fontSize = 11.sp, color = GxTextHi, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = { currentReport = importer.loadSampleCapture("port_scan") },
                border = BorderStroke(1.dp, GxBorder),
                shape = GxShapeSm,
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = GxWarning, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Port Scan", fontSize = 11.sp, color = GxTextHi, fontWeight = FontWeight.SemiBold)
            }

            OutlinedButton(
                onClick = { currentReport = importer.loadSampleCapture("clean") },
                border = BorderStroke(1.dp, GxBorder),
                shape = GxShapeSm,
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = GxSafe, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Clean Traffic", fontSize = 11.sp, color = GxTextHi, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Assessment Results Card
        val report = currentReport
        if (report != null) {
            val verdict = report.overallVerdict
            val statusColor = when (verdict.severity) {
                "HIGH" -> GxDanger
                "MEDIUM" -> GxWarning
                else -> GxSafe
            }
            val statusSoft = when (verdict.severity) {
                "HIGH" -> GxDangerSoft
                "MEDIUM" -> GxWarningSoft
                else -> GxSafeSoft
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
                                "ANOMALY: ${verdict.anomalyType.name}",
                                color = statusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            "${verdict.riskScore}/100 RISK",
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        "File: ${report.fileName}",
                        color = GxTextMid,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "Parsed Flows: ${report.totalFlowsParsed} | Suspicious: ${report.suspiciousFlowCount}",
                        color = GxTextHi,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(12.dp))

                    Text(
                        verdict.explanation,
                        color = GxTextHi,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    if (verdict.indicators.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "OBSERVED INDICATORS:",
                            color = GxTextLo,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        verdict.indicators.forEach { indicator ->
                            Text(
                                "• $indicator",
                                color = GxTextMid,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }

                    if (report.anonymizedEndpoints.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "ANONYMIZED DESTINATION ENDPOINTS:",
                            color = GxTextLo,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        report.anonymizedEndpoints.forEach { ep ->
                            Text(
                                "• $ep",
                                color = GxPrimary,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (verdict.recommendedActions.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "RECOMMENDED DEFENSE:",
                            color = GxTextLo,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        verdict.recommendedActions.forEach { act ->
                            Text(
                                "• $act",
                                color = GxTextHi,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
