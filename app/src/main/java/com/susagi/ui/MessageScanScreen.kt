package com.susagi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.susagi.security.AstraSecurityBridge
import com.susagi.security.MessageSecurityDecision
import com.susagi.ui.localization.Strings
import kotlinx.coroutines.launch

@Composable
fun MessageScanScreen(
    initialText: String = "",
    onBack: () -> Unit = {},
    onInspectLink: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rawInput by remember { mutableStateOf(initialText) }
    var decision by remember { mutableStateOf<MessageSecurityDecision?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }

    fun runScan(text: String) {
        if (text.isBlank()) return
        isAnalyzing = true
        scope.launch {
            decision = AstraSecurityBridge.assessMessage(text)
            isAnalyzing = false
        }
    }

    LaunchedEffect(initialText) {
        if (initialText.isNotBlank()) {
            runScan(initialText)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = GxTextHi,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = Strings.messageScanTitle,
                        color = GxTextHi,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "MULTI-LINGUAL SCAM DETECTOR",
                        color = GxPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Android Sandboxing Honest Disclosure Note
            Surface(
                color = GxSurfaceAlt.copy(alpha = 0.5f),
                shape = GxShapeSm,
                border = BorderStroke(1.dp, GxBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = GxTextLo,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = Strings.notificationDisclaimer,
                        color = GxTextLo,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            // Input Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = GxShapeMd,
                colors = CardDefaults.cardColors(containerColor = GxSurface),
                border = BorderStroke(1.dp, GxBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "MESSAGE TEXT (HINDI, ENGLISH, HINGLISH, REGIONAL)",
                        color = GxTextLo,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )

                    OutlinedTextField(
                        value = rawInput,
                        onValueChange = { rawInput = it },
                        placeholder = { Text("Paste suspicious message, SMS, or WhatsApp text...", color = GxTextLo) },
                        minLines = 4,
                        maxLines = 8,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GxTextHi,
                            unfocusedTextColor = GxTextHi,
                            focusedBorderColor = GxPrimary,
                            unfocusedBorderColor = GxBorder,
                            focusedContainerColor = GxSurfaceAlt,
                            unfocusedContainerColor = GxSurfaceAlt
                        ),
                        shape = GxShapeSm,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                if (clip.isNotBlank()) {
                                    rawInput = clip.trim()
                                    runScan(rawInput)
                                } else {
                                    Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = GxShapeSm,
                            border = BorderStroke(1.dp, GxBorder)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = GxTextMid, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Paste Text", color = GxTextHi, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { runScan(rawInput) },
                            enabled = rawInput.isNotBlank() && !isAnalyzing,
                            modifier = Modifier.weight(1f),
                            shape = GxShapeSm,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GxPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            if (isAnalyzing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Analyze", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Decision Result Card
            decision?.let { d ->
                val accentColor = when {
                    d.isHighRisk -> GxDanger
                    d.isSuspicious -> GxWarning
                    else -> GxSafe
                }
                val bgSoft = when {
                    d.isHighRisk -> GxDangerSoft
                    d.isSuspicious -> GxWarningSoft
                    else -> GxSafeSoft
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = GxShapeLg,
                    colors = CardDefaults.cardColors(containerColor = bgSoft),
                    border = BorderStroke(1.5.dp, accentColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (d.isSafe) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = d.threatCategory,
                                    color = accentColor,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Surface(
                                color = accentColor,
                                shape = GxShapePill
                            ) {
                                Text(
                                    text = "RISK ${d.riskScore}%",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = d.guidance,
                            color = GxTextHi,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        // Extracted links, if any
                        if (d.extractedUrls.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(GxSurface.copy(alpha = 0.8f), GxShapeSm)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "DETECTED EMBEDDED LINKS:",
                                    color = GxTextLo,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                d.extractedUrls.forEach { url ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = url.take(35) + if (url.length > 35) "..." else "",
                                            color = GxPrimary,
                                            fontSize = 12.sp
                                        )
                                        OutlinedButton(
                                            onClick = { onInspectLink(url) },
                                            shape = GxShapeSm,
                                            border = BorderStroke(1.dp, GxPrimary)
                                        ) {
                                            Text("Inspect", fontSize = 10.sp, color = GxPrimary)
                                        }
                                    }
                                }
                            }
                        }

                        // Matched keywords
                        if (d.matchedKeywords.isNotEmpty()) {
                            Text(
                                text = "Flagged triggers: " + d.matchedKeywords.joinToString(", "),
                                color = GxTextMid,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
