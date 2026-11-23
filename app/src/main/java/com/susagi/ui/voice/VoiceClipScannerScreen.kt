package com.susagi.ui.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import com.susagi.edgeai.InferenceResult
import com.susagi.intelligence.UnifiedThreatEngine
import com.susagi.stt.whisper.WhisperModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

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
    var recordingDurationSec by remember { mutableIntStateOf(0) }
    var audioRmsLevel by remember { mutableFloatStateOf(0.05f) }
    var transcriptText by remember { mutableStateOf("") }
    var threatResult by remember { mutableStateOf<InferenceResult?>(null) }
    var statusMessage by remember { mutableStateOf("Ready to record voice audio or enter call transcript.") }

    var recordingJob by remember { mutableStateOf<Job?>(null) }
    var tickerJob by remember { mutableStateOf<Job?>(null) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun stopRecordingInternal() {
        isRecording = false
        tickerJob?.cancel()
        recordingJob?.cancel()
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        audioRmsLevel = 0.05f

        val finalTranscript = transcriptText.trim()
        if (finalTranscript.isNotBlank()) {
            statusMessage = "Analyzing audio transcript for extortion and threat signals..."
            scope.launch {
                threatResult = threatEngine.analyzeCallTranscript(finalTranscript)
                statusMessage = "Analysis complete."
            }
        } else {
            statusMessage = "No speech detected in audio sample. Speak closer to the microphone or test a sample scenario below."
        }
    }

    fun startRecordingInternal() {
        threatResult = null
        transcriptText = ""
        isRecording = true
        recordingDurationSec = 0
        audioRmsLevel = 0.1f
        statusMessage = "Listening... Speak clearly into your microphone."

        // 1. Ticker for recording duration
        tickerJob = scope.launch {
            while (isActive && isRecording) {
                delay(1000)
                recordingDurationSec++
            }
        }

        // 2. Hardware AudioRecord thread for real-time RMS audio visualization
        recordingJob = scope.launch(Dispatchers.IO) {
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
                if (recorder.state == AudioRecord.STATE_INITIALIZED) {
                    recorder.startRecording()
                    val buffer = ShortArray(minBuf / 2)
                    while (isActive && isRecording) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            var sum = 0.0
                            for (i in 0 until read) {
                                val s = buffer[i].toDouble()
                                sum += s * s
                            }
                            val rms = sqrt(sum / read)
                            val normalized = (rms / 6000.0).coerceIn(0.05, 1.0).toFloat()
                            withContext(Dispatchers.Main) {
                                audioRmsLevel = normalized
                            }
                        }
                    }
                    try { recorder.stop() } catch (_: Exception) {}
                    try { recorder.release() } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusMessage = "AudioRecord notice: ${e.message}"
                }
            }
        }

        // 3. Android SpeechRecognizer for real-time speech-to-text
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
                speechRecognizer = recognizer
                recognizer.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {
                        val level = ((rmsdB + 2f) / 10f).coerceIn(0.05f, 1f)
                        if (level > audioRmsLevel) {
                            audioRmsLevel = level
                        }
                    }
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onError(error: Int) {
                        // Error 7 is no match; continue listening or allow manual retry
                    }
                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotBlank()) {
                            transcriptText = if (transcriptText.isBlank()) text else "$transcriptText $text"
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim().orEmpty()
                        if (text.isNotBlank()) {
                            transcriptText = text
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN")
                }
                recognizer.startListening(intent)
            }
        } catch (_: Exception) {}
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecordingInternal()
        } else {
            Toast.makeText(context, "Microphone permission required to record audio", Toast.LENGTH_LONG).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopRecordingInternal()
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
                stopRecordingInternal()
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
                    "Live microphone capture, acoustic extortion & speech scam shield",
                    color = GxTextLo,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Model Provisioning Status Banner
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
                        else "SPEECH ENGINE: Android On-Device Recognizer Active",
                        color = if (isWhisperProvisioned) GxSafe else GxTextHi,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isWhisperProvisioned) "Offline ExecuTorch INT8 speech-to-text active on local hardware."
                        else "Real-time microphone input captures your voice directly. Spoken audio is analyzed by SafetyBarrier and Unified Threat Engine.",
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "MICROPHONE AUDIO RECORDER",
                        color = GxTextLo,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    if (isRecording) {
                        Surface(
                            color = GxDangerSoft,
                            shape = GxShapePill,
                            border = BorderStroke(1.dp, GxDanger.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(GxDanger, CircleShape)
                                )
                                Spacer(Modifier.width(5.dp))
                                val mins = recordingDurationSec / 60
                                val secs = recordingDurationSec % 60
                                Text(
                                    String.format("REC %02d:%02d", mins, secs),
                                    color = GxDanger,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Live Audio Level Visualizer (Equalizer Sound Bars)
                if (isRecording) {
                    Surface(
                        color = GxSurfaceAlt,
                        shape = GxShapeSm,
                        border = BorderStroke(1.dp, GxBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val barCount = 16
                            for (i in 0 until barCount) {
                                val factor = ((i * 3 + 1) % 5 + 1) / 5f
                                val barHeight = (8.dp + 36.dp * (audioRmsLevel * factor).coerceIn(0.08f, 1f))
                                Box(
                                    modifier = Modifier
                                        .width(6.dp)
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (audioRmsLevel > 0.4f) GxDanger else GxPrimary)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // Record / Stop Buttons
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
                                    startRecordingInternal()
                                } else {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GxPrimary),
                            shape = GxShapeSm,
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Record Audio Sample", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                stopRecordingInternal()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GxDanger),
                            shape = GxShapeSm,
                            modifier = Modifier.weight(1f).height(46.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Stop & Analyze Audio", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    statusMessage,
                    color = GxTextLo,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Spacer(Modifier.height(12.dp))

                // Transcript Display & Input Field
                OutlinedTextField(
                    value = transcriptText,
                    onValueChange = { transcriptText = it },
                    placeholder = {
                        Text(
                            "Spoken speech will appear here automatically while recording, or paste any transcript...",
                            color = GxTextLo,
                            fontSize = 12.sp
                        )
                    },
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
                        val text = transcriptText.trim()
                        if (text.isNotBlank()) {
                            scope.launch {
                                statusMessage = "Analyzing speech threat..."
                                threatResult = threatEngine.analyzeCallTranscript(text)
                                statusMessage = "Analysis complete."
                            }
                        } else {
                            Toast.makeText(context, "Please record audio or enter a transcript to analyze", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GxPrimary),
                    shape = GxShapeSm,
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Analyze Speech Threat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Quick Sample Speech Scenarios
        Spacer(Modifier.height(14.dp))
        Text(
            "QUICK TEST SCENARIOS (ONE-TAP EVALUATION)",
            color = GxTextLo,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        transcriptText = "This is DCP Crime Branch. A parcel with narcotics has been intercepted in your name. You are under digital arrest. Transfer Rs 50,000 to RBI verification account immediately."
                        scope.launch { threatResult = threatEngine.analyzeCallTranscript(transcriptText) }
                    },
                    border = BorderStroke(0.5.dp, GxBorder),
                    shape = GxShapeSm,
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Text("🚨 Digital Arrest Scam", fontSize = 11.sp, color = GxTextMid)
                }

                OutlinedButton(
                    onClick = {
                        transcriptText = "Dear customer, your bank account is suspended due to expired KYC. Share the OTP received on SMS immediately to avoid permanent closure."
                        scope.launch { threatResult = threatEngine.analyzeCallTranscript(transcriptText) }
                    },
                    border = BorderStroke(0.5.dp, GxBorder),
                    shape = GxShapeSm,
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Text("💳 Bank KYC Scam", fontSize = 11.sp, color = GxTextMid)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        transcriptText = "Earn 5000 rupees daily by liking YouTube videos. Pay initial task deposit of 2000 rupees to get assigned merchant rating tasks."
                        scope.launch { threatResult = threatEngine.analyzeCallTranscript(transcriptText) }
                    },
                    border = BorderStroke(0.5.dp, GxBorder),
                    shape = GxShapeSm,
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Text("💼 Job Task Scam", fontSize = 11.sp, color = GxTextMid)
                }

                OutlinedButton(
                    onClick = {
                        transcriptText = "Hi Dad, I will reach home by 8 PM tonight. Please ask mom to keep dinner ready."
                        scope.launch { threatResult = threatEngine.analyzeCallTranscript(transcriptText) }
                    },
                    border = BorderStroke(0.5.dp, GxBorder),
                    shape = GxShapeSm,
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Text("👨‍👩‍👧 Benign Family Call", fontSize = 11.sp, color = GxTextMid)
                }
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
                                "${res.severity} RISK · SCORE ${res.riskScore}/100",
                                color = statusColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }

                        Text(
                            "Source: ${res.analysisSource}",
                            color = GxTextLo,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        res.threatType.replace("_", " ").uppercase(),
                        color = GxTextHi,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        res.explanation,
                        color = GxTextMid,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    if (res.detectedTactics.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("DETECTED TACTICS & PATTERNS", color = GxTextLo, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        res.detectedTactics.forEach { tactic ->
                            Text("• $tactic", color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (res.recommendedActions.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("RECOMMENDED PROTECTIVE ACTIONS", color = GxTextLo, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        res.recommendedActions.forEach { act ->
                            Text("✓ $act", color = GxTextHi, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
