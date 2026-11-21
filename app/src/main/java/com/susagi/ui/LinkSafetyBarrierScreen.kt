package com.susagi.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Security
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
import com.susagi.security.AstraSecurityBridge
import com.susagi.security.UrlSecurityDecision
import com.susagi.ui.localization.Strings
import kotlinx.coroutines.launch

@Composable
fun LinkSafetyBarrierScreen(
    initialUrl: String = "",
    onBack: () -> Unit = {},
    onOpenUrl: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputUrl by remember { mutableStateOf(initialUrl) }
    var decision by remember { mutableStateOf<UrlSecurityDecision?>(null) }
    var isAssessing by remember { mutableStateOf(false) }

    fun runAssessment(urlToAssess: String) {
        if (urlToAssess.isBlank()) return
        isAssessing = true
        scope.launch {
            decision = AstraSecurityBridge.assessUrl(urlToAssess)
            isAssessing = false
        }
    }

    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank()) {
            runAssessment(initialUrl)
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
                        text = Strings.linkBarrierTitle,
                        color = GxTextHi,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ZERO-TRUST BROWSER SHIELD",
                        color = GxPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Description banner
            Text(
                text = Strings.linkBarrierDesc,
                color = GxTextLo,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // URL Input Card
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
                        text = "DESTINATION URL",
                        color = GxTextLo,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )

                    OutlinedTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        placeholder = { Text("https://example.com/login", color = GxTextLo) },
                        singleLine = true,
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
                                    inputUrl = clip.trim()
                                    runAssessment(inputUrl)
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
                            Text("Paste URL", color = GxTextHi, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { runAssessment(inputUrl) },
                            enabled = inputUrl.isNotBlank() && !isAssessing,
                            modifier = Modifier.weight(1f),
                            shape = GxShapeSm,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = GxPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            if (isAssessing) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Inspect Link", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Assessment Decision Display
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
                                    text = d.title,
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
                                    text = if (d.isSafe) "SAFE" else if (d.isHighRisk) "MALICIOUS" else "SUSPICIOUS",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Text(
                            text = d.detail,
                            color = GxTextHi,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )

                        if (d.canonicalHost.isNotBlank()) {
                            Text(
                                text = "Canonical Host: ${d.canonicalHost}",
                                color = GxTextMid,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Breakdown of detected signals
                        if (d.reasons.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(GxSurface.copy(alpha = 0.7f), GxShapeSm)
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "SECURITY FINDINGS:",
                                    color = GxTextLo,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                d.reasons.forEach { reason ->
                                    Text(
                                        text = "• $reason",
                                        color = GxTextMid,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))

                        // Action Buttons: Open, Copy, Block
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (d.isSafe) {
                                Button(
                                    onClick = { onOpenUrl(d.url) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = GxShapeSm,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GxSafe,
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Open Safely in Browser", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        Toast.makeText(context, "Blocked navigation to unverified URL", Toast.LENGTH_SHORT).show()
                                        onBack()
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = GxShapeSm,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GxDanger),
                                    border = BorderStroke(1.dp, GxDanger)
                                ) {
                                    Text("Block Link", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }

                                Button(
                                    onClick = { onOpenUrl(d.url) },
                                    modifier = Modifier.weight(1f),
                                    shape = GxShapeSm,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = GxSurfaceAlt,
                                        contentColor = GxTextLo
                                    )
                                ) {
                                    Text("Proceed Anyway", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
