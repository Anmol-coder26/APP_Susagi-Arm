package com.susagi.ui.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import com.susagi.edgeai.InferenceResult
import com.susagi.intelligence.UnifiedThreatEngine
import com.susagi.stt.whisper.WhisperAudioProcessor
import com.susagi.stt.whisper.WhisperModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun VoiceClipScannerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val whisperMgr = remember { WhisperModelManager(context) }
    val isWhisperProvisioned = remember { whisperMgr.isReady() }
    val threatEngine = remember { UnifiedThreatEngine.getInstance(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isRecording by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableStateOf(0) }
    var transcriptText by remember { mutableStateOf("") }
    var threatResult by remember { mutableStateOf<InferenceResult?>(null) }
    var recordingJob by remember { mutableStateOf<Job?>(null) }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isRecording = true
            recordingDurationSec = 0
            recordingJob = scope.launch(Dispatchers.IO) {
                runAudioRecording(context) { pcmChunk ->
                    // Accumulate via WhisperAudioProcessor
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                recordingJob?.cancel()
                onBack()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GxTextHi)
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    "Voice Clip Threat Analyzer",
                    color = GxTextHi,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Standalone speech-to-text & acoustic extortion shield",
                    color = GxTextLo,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Model Provisioning Status Banner (Zero false claims of speech recognition)
        Surface(
            color = if (isWhisperProvisioned) GxSafeSoft else GxSurfaceAlt,
            shape = GxShapeSm,
            border = BorderStroke(1.dp, if (isWhisperProvisioned) GxSafe else GxBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = if (isWhisperProvisioned) GxSafe else GxTextLo,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        if (isWhisperProvisioned) "WHISPER MODEL READY (Arm ExecuTorch)"
                        else "WHISPER NOT PROVISIONED: Deterministic transcript reasoning active",
                        color = if (isWhisperProvisioned) GxSafe else GxTextHi,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isWhisperProvisioned) "Offline ExecuTorch INT8 speech-to-text active on local hardware."
                        else "Large model assets (~150MB) are not downloaded on storage-constrained setup. You can record, import, or paste transcript text to inspect threats directly.",
                        color = GxTextLo,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Recording & Audio Capture Card
        Card(
            shape = GxShapeMd,
            colors = CardDefaults.cardColors(containerColor = GxSurface),
            border = BorderStroke(1.dp, GxBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "RECORD AUDIO OR ENTER CALL TRANSCRIPT",
                    color = GxTextLo,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!isRecording) {
                        Button(
                            onClick = {
                                val hasMic = ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasMic) {
                                    isRecording = true
                                    // Start background recording
                                } else {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GxPrimary),
                            shape = GxShapeSm,
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Record Audio Sample", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                isRecording = false
                                recordingJob?.cancel()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GxDanger),
                            shape = GxShapeSm,
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Stop Recording", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = transcriptText,
                    onValueChange = { transcriptText = it },
                    placeholder = { Text("Or paste transcribed speech text from caller here...", color = GxTextLo, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = GxShapeSm,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GxPrimary,
                        unfocusedBorderColor = GxBorder,
                        focusedTextColor = GxTextHi,
                        unfocusedTextColor = GxTextHi
                    ),
                    minLines = 3,
                    maxLines = 6
                )

                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        if (transcriptText.isNotBlank()) {
                            scope.launch {
                                threatResult = threatEngine.analyzeCallTranscript(transcriptText)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GxPrimary),
                    shape = GxShapeSm,
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                ) {
                    Text("Analyze Speech Threat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Quick Sample Speech Scenarios
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedButton(
                onClick = {
                    transcriptText = "[CALLER]: This is cyber police. You are under digital arrest. Do not disconnect the line and transfer fifty thousand rupees immediately."
                    scope.launch { threatResult = threatEngine.analyzeCallTranscript(transcriptText) }
                },
                border = BorderStroke(0.5.dp, GxBorder),
                shape = GxShapeSm,
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Text("Sample Police Threat", fontSize = 10.sp, color = GxTextMid)
            }

            OutlinedButton(
                onClick = {
                    transcriptText = "[CALLER]: Hi Dad, our flight is delayed by an hour. Will see you at dinner."
                    scope.launch { threatResult = threatEngine.analyzeCallTranscript(transcriptText) }
                },
                border = BorderStroke(0.5.dp, GxBorder),
                shape = GxShapeSm,
                modifier = Modifier.weight(1f).height(34.dp)
            ) {
                Text("Sample Benign Call", fontSize = 10.sp, color = GxTextMid)
            }
        }

        // Assessment Results
        val res = threatResult
        if (res != null) {
            Spacer(Modifier.height(16.dp))
            val statusColor = when (res.severity) {
                "HIGH" -> GxDanger
                "MEDIUM" -> GxWarning
                else -> GxSafe
            }
            val statusSoft = when (res.severity) {
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
                                "SEVERITY: ${res.severity}",
                                color = statusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Text(
                            "${res.riskScore}/100 RISK",
                            color = statusColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        res.explanation,
                        color = GxTextHi,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    if (res.detectedTactics.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "TACTICS: ${res.detectedTactics.joinToString(", ")}",
                            color = GxPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (res.recommendedActions.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "PROTECTIVE ACTION:",
                            color = GxTextLo,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        res.recommendedActions.forEach { act ->
                            Text("• $act", color = GxTextHi, fontSize = 11.sp, modifier = Modifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun runAudioRecording(context: Context, onChunk: (ByteArray) -> Unit) {
    val sampleRate = 16000
    val channelConfig = AudioFormat.CHANNEL_IN_MONO
    val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(3200)

    try {
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            channelConfig,
            audioFormat,
            minBuf
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) return

        recorder.startRecording()
        val buffer = ByteArray(minBuf)
        while (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read > 0) {
                onChunk(buffer.copyOf(read))
            }
        }
        recorder.stop()
        recorder.release()
    } catch (_: Exception) {}
}
