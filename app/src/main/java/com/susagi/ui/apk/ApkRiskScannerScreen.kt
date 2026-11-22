package com.susagi.ui.apk

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.susagi.security.apk.ApkRiskReport
import com.susagi.security.apk.ApkRiskScanner
import kotlinx.coroutines.launch

@Composable
fun ApkRiskScannerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scanner = remember { ApkRiskScanner(context) }
    var inputTarget by remember { mutableStateOf("") }
    var report by remember { mutableStateOf<ApkRiskReport?>(null) }
    val scope = rememberCoroutineScope()
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
                    "APK & Install Risk Inspector",
                    color = GxTextHi,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Inspect sideloading links & manifest capabilities safely",
                    color = GxTextLo,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Input Card
        Card(
            shape = GxShapeMd,
            colors = CardDefaults.cardColors(containerColor = GxSurface),
            border = BorderStroke(1.dp, GxBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    "ENTER APK LINK OR SIDELOAD INSTRUCTION",
                    color = GxTextLo,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = inputTarget,
                    onValueChange = { inputTarget = it },
                    placeholder = { Text("https://example.com/update_app.apk", color = GxTextLo, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = GxShapeSm,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GxPrimary,
                        unfocusedBorderColor = GxBorder,
                        focusedTextColor = GxTextHi,
                        unfocusedTextColor = GxTextHi
                    ),
                    maxLines = 3
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (inputTarget.isNotBlank()) {
                                scope.launch {
                                    report = scanner.analyzeApkUrl(inputTarget)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GxPrimary),
                        shape = GxShapeSm,
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Analyze Risk", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            inputTarget = ""
                            report = null
                        },
                        border = BorderStroke(1.dp, GxBorder),
                        shape = GxShapeSm,
                        modifier = Modifier.height(40.dp)
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = GxTextLo, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Quick Preset Examples
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = {
                    inputTarget = "https://sbi-netbanking-support.top/sbi_kyc_quick_fix.apk"
                    scope.launch { report = scanner.analyzeApkUrl(inputTarget) }
                },
                border = BorderStroke(0.5.dp, GxBorder),
                shape = GxShapeSm,
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Text("Sample APK Link", fontSize = 10.sp, color = GxTextMid)
            }

            OutlinedButton(
                onClick = {
                    inputTarget = "https://play.google.com/store/apps/details?id=com.sbi.lotusintouch"
                    scope.launch { report = scanner.analyzeApkUrl(inputTarget) }
                },
                border = BorderStroke(0.5.dp, GxBorder),
                shape = GxShapeSm,
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Text("Play Store Link", fontSize = 10.sp, color = GxTextMid)
            }
        }

        // Results Card
        val curReport = report
        if (curReport != null) {
            Spacer(Modifier.height(16.dp))
            val statusColor = when (curReport.severity) {
                "HIGH" -> GxDanger
                "REVIEW" -> GxWarning
                else -> GxSafe
            }
            val statusSoft = when (curReport.severity) {
                "HIGH" -> GxDangerSoft
                "REVIEW" -> GxWarningSoft
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
                                "VERDICT: ${curReport.severity}",
                                color = statusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            "${curReport.riskScore}/100 RISK",
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        curReport.plainExplanation,
                        color = GxTextHi,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    if (curReport.detectedTactics.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "DETECTED SIGNALS: ${curReport.detectedTactics.joinToString(", ")}",
                            color = GxPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (curReport.recommendations.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "SAFE ACTION GUIDANCE:",
                            color = GxTextLo,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        curReport.recommendations.forEach { rec ->
                            Text("• $rec", color = GxTextHi, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
