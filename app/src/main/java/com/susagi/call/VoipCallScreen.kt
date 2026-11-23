package com.susagi.call

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guardian.app.ui.theme.GxBase
import com.guardian.app.ui.theme.GxBorder
import com.guardian.app.ui.theme.GxDanger
import com.guardian.app.ui.theme.GxDangerSoft
import com.guardian.app.ui.theme.GxPrimary
import com.guardian.app.ui.theme.GxPrimaryGlow
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
import com.guardian.app.ui.theme.GxVoid
import com.guardian.app.ui.theme.GxWarning
import com.guardian.app.ui.theme.GxWarningSoft
import com.guardian.app.voip.CallDiagnostics
import com.guardian.app.voip.DiagnosticEntry
import com.guardian.app.voip.LogLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MicOff

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoipCallScreen(
    uiState: VoipUiState,
    onToggleMute: () -> Unit = {},
    onToggleSpeaker: () -> Unit = {},
    onSimulateScam: () -> Unit = {},
    onStopSimulation: () -> Unit = {},
    onEndCall: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showDebugOverlay by remember { mutableStateOf(false) }

    // Live Call Timer
    var elapsedSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected) {
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    LaunchedEffect(uiState.transcriptLines.size) {
        if (uiState.transcriptLines.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(uiState.transcriptLines.size - 1) }
        }
    }

    val riskAccent = when {
        uiState.riskScore >= 75 -> GxDanger
        uiState.riskScore >= 40 -> GxWarning
        else -> GxSafe
    }

    val roomCodeDisplay = remember(uiState.channelName) {
        val clean = uiState.channelName.removePrefix("gx_")
        if (clean.length == 6) "${clean.take(3)} ${clean.takeLast(3)}" else clean.ifBlank { "ACTIVE" }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp) // Space for bottom call controls
        ) {
            // ==========================================
            // TOP BAR: Call Metadata & Timer
            // ==========================================
            Surface(
                color = GxSurface,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, GxBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = GxPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "SECURE VOIP DEFENSE",
                                color = GxTextLo,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                val clip = ClipData.newPlainText("Room Code", roomCodeDisplay.replace(" ", ""))
                                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                                Toast.makeText(context, "Room code copied: $roomCodeDisplay", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text(
                                "ROOM $roomCodeDisplay",
                                color = GxTextHi,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = "Copy code",
                                tint = GxTextMid,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Call Timer Pill
                        Surface(
                            color = GxSurfaceAlt,
                            shape = GxShapePill,
                            border = BorderStroke(1.dp, GxBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PulsingLiveIndicator(color = GxSafe, size = 6.dp)
                                Spacer(Modifier.width(6.dp))
                                val mins = elapsedSeconds / 60
                                val secs = elapsedSeconds % 60
                                Text(
                                    String.format("%02d:%02d", mins, secs),
                                    color = GxTextHi,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        // Diagnostics Terminal Button
                        Surface(
                            color = if (showDebugOverlay) GxPrimarySoft else GxSurfaceAlt,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, if (showDebugOverlay) GxPrimary else GxBorder),
                            modifier = Modifier
                                .size(36.dp)
                                .clickable { showDebugOverlay = !showDebugOverlay }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Security,
                                    contentDescription = "Diagnostics",
                                    tint = if (showDebugOverlay) GxPrimary else GxTextMid,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // ==========================================
            // HERO THREAT RADAR CARD (Vibrant Amber/Coral Alert Capsule)
            // ==========================================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = GxShapeMd,
                colors = CardDefaults.cardColors(containerColor = if (uiState.riskScore > 65) Color(0xFFFFFBEB) else GxSurface),
                border = BorderStroke(2.dp, if (uiState.riskScore > 65) Color(0xFFFCD34D) else GxSafe.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "REAL-TIME SCAM THREAT RADAR",
                                color = GxTextLo,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = when {
                                    uiState.riskScore >= 75 -> "CRITICAL EXTORTION THREAT"
                                    uiState.riskScore >= 40 -> "SUSPICIOUS COERCIVE PATTERNS"
                                    else -> "PROTECTED · NORMAL FLOW"
                                },
                                color = riskAccent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Dual-speaker acoustic analysis + on-device Gemini inference",
                                color = GxTextLo,
                                fontSize = 11.sp
                            )
                        }

                        // Circular Risk Score
                        Surface(
                            color = riskAccent.copy(alpha = 0.12f),
                            shape = CircleShape,
                            border = BorderStroke(2.dp, riskAccent),
                            modifier = Modifier.size(58.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "${uiState.riskScore}%",
                                    color = GxTextHi,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }

                    // Scammer Role Attribution Banner
                    if (uiState.scammerRole != "Unknown") {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            color = GxDangerSoft,
                            shape = GxShapeSm,
                            border = BorderStroke(1.dp, GxDanger.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = GxDanger,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${uiState.scammerRole.uppercase()} IDENTIFIED AS LIKELY SCAMMER (${(uiState.roleConfidence * 100).toInt()}% confidence)",
                                    color = GxDanger,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Psychological Pressure / Social Panic / Coercion Banner
                    val pressure = uiState.psychologicalPressure
                    if (!pressure.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            color = GxWarningSoft,
                            shape = GxShapeSm,
                            border = BorderStroke(1.dp, GxWarning.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("⚡", fontSize = 12.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "PSYCHOLOGICAL COERCION: ${pressure.uppercase()}",
                                    color = GxWarning,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.connectionBanner?.contains("SIMULATION") == true) {
                            OutlinedButton(
                                onClick = onStopSimulation,
                                shape = GxShapeSm,
                                border = BorderStroke(1.dp, GxDanger),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(14.dp), tint = GxDanger)
                                Spacer(Modifier.width(4.dp))
                                Text("Stop Simulation", fontSize = 11.sp, color = GxDanger, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = onSimulateScam,
                                shape = GxShapeSm,
                                border = BorderStroke(1.dp, GxPrimary),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp), tint = GxPrimary)
                                Spacer(Modifier.width(4.dp))
                                Text("Simulate Scammer Voice Stream", fontSize = 11.sp, color = GxPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ==========================================
            // CONNECTION / PEER STATUS NOTICE
            // ==========================================
            if (!uiState.isConnected) {
                Surface(
                    color = GxWarningSoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = GxShapeSm,
                    border = BorderStroke(1.dp, GxWarning.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PulsingLiveIndicator(color = GxWarning, size = 6.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            uiState.connectionBanner ?: "Connecting to encrypted Agora channel...",
                            color = GxWarning,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else if (uiState.pills.peerPresent != PillStatus.GREEN) {
                Surface(
                    color = GxPrimarySoft,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = GxShapeSm,
                    border = BorderStroke(1.dp, GxPrimary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PulsingLiveIndicator(color = GxPrimary, size = 6.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Channel connected. Waiting for peer to enter room...",
                            color = GxPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // ==========================================
            // LIVE TRANSCRIPTION CONVERSATION FEED
            // ==========================================
            if (uiState.transcriptLines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = GxSurfaceAlt,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, GxBorder),
                            modifier = Modifier.size(56.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = GxTextMid,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        Text(
                            "Live Transcription Stream",
                            color = GxTextHi,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Speak or listen. Live transcription is active via ${uiState.activeSttEngine}.",
                            color = GxTextLo,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 280.dp),
                            lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onSimulateScam,
                            colors = ButtonDefaults.buttonColors(containerColor = GxPrimary),
                            shape = GxShapeSm,
                            modifier = Modifier.height(40.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Simulate Scammer Voice Call", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(uiState.transcriptLines) { _, line ->
                        ModernTranscriptBubble(line)
                    }
                }
            }
        }

        // ==========================================
        // FLOATING CALL CONTROLS (Bottom Bar)
        // ==========================================
        Surface(
            color = GxSurface,
            border = BorderStroke(1.dp, GxBorder),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(84.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive Mute Toggle Pod
                Surface(
                    color = if (uiState.isMuted) GxDangerSoft else GxSurfaceAlt,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, if (uiState.isMuted) GxDanger else GxBorder),
                    modifier = Modifier
                        .size(52.dp)
                        .clickable { onToggleMute() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (uiState.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (uiState.isMuted) "Unmute Microphone" else "Mute Microphone",
                            tint = if (uiState.isMuted) GxDanger else GxTextHi,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Interactive Speakerphone Toggle Pod
                Surface(
                    color = if (uiState.isSpeakerphone) GxPrimarySoft else GxSurfaceAlt,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, if (uiState.isSpeakerphone) GxPrimary else GxBorder),
                    modifier = Modifier
                        .size(52.dp)
                        .clickable { onToggleSpeaker() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (uiState.isSpeakerphone) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeMute,
                            contentDescription = if (uiState.isSpeakerphone) "Switch to Earpiece" else "Switch to Speakerphone",
                            tint = if (uiState.isSpeakerphone) GxPrimary else GxTextMid,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // AI Shield / Threat Status Pod
                Surface(
                    color = if (uiState.riskScore >= 70) GxDangerSoft else GxSafeSoft,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, if (uiState.riskScore >= 70) GxDanger.copy(alpha = 0.5f) else GxSafe.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .size(52.dp)
                        .clickable { showDebugOverlay = !showDebugOverlay }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Threat Monitor",
                            tint = if (uiState.riskScore >= 70) GxDanger else GxSafe,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // End Call Big Red Circular Pod
                Surface(
                    color = GxDanger,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(58.dp)
                        .clickable { onEndCall() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End Call",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // ==========================================
        // SLIDING DIAGNOSTICS TERMINAL OVERLAY
        // ==========================================
        AnimatedVisibility(
            visible = showDebugOverlay,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GxVoid.copy(alpha = 0.95f))
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SYSTEM TELEMETRY TERMINAL",
                                color = GxSafe,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${uiState.logs.size} log entries captured",
                                color = GxTextLo,
                                fontSize = 11.sp
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("CallDiagnostics", CallDiagnostics.getFormattedLogs())
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "All logs copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                shape = GxShapeSm,
                                border = BorderStroke(1.dp, GxBorder),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("COPY ALL", fontSize = 11.sp, color = GxPrimary, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { showDebugOverlay = false },
                                shape = GxShapeSm,
                                colors = ButtonDefaults.buttonColors(containerColor = GxSurfaceAlt),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("CLOSE", fontSize = 11.sp, color = GxTextHi, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TelemetryPill("RTC", uiState.pills.rtcJoined)
                        TelemetryPill("PEER", uiState.pills.peerPresent)
                        TelemetryPill("MIC", uiState.pills.localPcmFlowing)
                        TelemetryPill("REMOTE", uiState.pills.remotePcmFlowing)
                        TelemetryPill("STT-LOC", uiState.pills.sttLocalStatus)
                        TelemetryPill("STT-REM", uiState.pills.sttRemoteStatus)
                    }

                    Text(
                        text = "ACTIVE STT ENGINE: ${uiState.activeSttEngine.uppercase()}",
                        color = GxSafe,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(GxSurface, shape = GxShapeSm)
                            .border(1.dp, GxBorder, GxShapeSm)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(uiState.logs) { entry ->
                            ModernLogEntryRow(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryPill(label: String, status: PillStatus) {
    val pillColor = when (status) {
        PillStatus.GREEN -> GxSafe
        PillStatus.AMBER -> GxWarning
        PillStatus.RED -> GxDanger
    }

    Surface(
        color = GxSurface,
        shape = GxShapeSm,
        border = BorderStroke(1.dp, GxBorder),
        modifier = Modifier.padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(pillColor, CircleShape)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                color = GxTextMid,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ModernTranscriptBubble(line: TranscriptUiLine) {
    val isLocal = line.speaker == Speaker.LOCAL
    val isScammer = line.roleLabel.contains("Scammer", ignoreCase = true)

    val bubbleBg = if (isLocal) GxSurfaceAlt else if (isScammer) GxDangerSoft else GxSurface
    val borderStroke = when {
        isScammer -> BorderStroke(1.5.dp, GxDanger.copy(alpha = 0.6f))
        isLocal -> BorderStroke(1.dp, GxBorder)
        else -> BorderStroke(1.dp, GxBorder)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isLocal) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .background(bubbleBg, shape = GxShapeMd)
                .border(borderStroke, shape = GxShapeMd)
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isLocal) Icons.Default.Person else if (isScammer) Icons.Default.WarningAmber else Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = if (isScammer) GxDanger else if (isLocal) GxTextHi else GxSafe,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (isLocal) "YOU" else if (isScammer) "CALLER (FLAGGED)" else "CALLER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isScammer) GxDanger else if (isLocal) GxTextHi else GxTextMid,
                        letterSpacing = 1.sp
                    )
                }

                if (isScammer) {
                    Surface(
                        color = GxDangerSoft,
                        shape = GxShapePill,
                        border = BorderStroke(0.5.dp, GxDanger)
                    ) {
                        Text(
                            "SCAMMER",
                            color = GxDanger,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = line.text,
                fontSize = 14.sp,
                color = GxTextHi,
                lineHeight = 20.sp
            )
        }
    }
}

@Composable
private fun ModernLogEntryRow(entry: DiagnosticEntry) {
    val levelColor = when (entry.level) {
        LogLevel.INFO -> GxPrimary
        LogLevel.WARN -> GxWarning
        LogLevel.ERROR -> GxDanger
    }
    val sdf = remember { java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US) }
    val timeStr = sdf.format(java.util.Date(entry.timestampMs))

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "[$timeStr] [${entry.tag}] ",
            color = GxTextLo,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
        Text(
            text = entry.message,
            color = levelColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun PulsingLiveIndicator(
    color: Color = GxSafe,
    size: androidx.compose.ui.unit.Dp = 8.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse-trans")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size * 2)) {
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .alpha(alpha)
                .background(color, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(size)
                .background(color, CircleShape)
        )
    }
}
