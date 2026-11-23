package com.susagi.ui.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
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
import com.susagi.stt.whisper.WhisperSttEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt

/**
 * Primary state machine for Voice Shield (Directive 60 Phase 5).
 * Represents the lifecycle stages of on-device voice recording, transcription,
 * and automatic security analysis.
 */
enum class VoiceShieldState {
    IDLE,
    RECORDING,
    PREPARING_AUDIO,
    TRANSCRIBING,
    ANALYZING,
    RESULT_READY,
    ERROR,
    MODEL_UNAVAILABLE,
    CANCELLED
}

/**
 * VoiceClipScannerScreen
 *
 * Implements Directive 60: Operation Voice Shield Redemption.
 * Fully automated workflow:
 * USER SPEAKS -> SUSAGI LISTENS -> SUSAGI TRANSCRIBES -> SUSAGI UNDERSTANDS -> EXPLAINS RISK
 *
 * - Real hardware AudioRecord (16kHz 16-bit mono PCM capture).
 * - Real-time RMS waveform computation.
 * - Automatic PCM -> 44-byte WAV encoding.
 * - Offline native ExecuTorch Whisper multilingual transcription.
 * - Automatic routing into UnifiedThreatEngine + SafetyBarrier + Astra PrizeIntent.
 * - Truthful engine claims: NO Android SpeechRecognizer fallback.
 * - Honest states: IDLE, RECORDING, PREPARING_AUDIO, TRANSCRIBING, ANALYZING, RESULT_READY, ERROR, MODEL_UNAVAILABLE, CANCELLED.
 */
@Composable
fun VoiceClipScannerScreen(
    onBack: () -> Unit,
    onOpenModelManager: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val whisperMgr = remember { WhisperModelManager(context) }
    val isWhisperProvisioned = remember { whisperMgr.isReady() }
    val threatEngine = remember { UnifiedThreatEngine.getInstance(context) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var state by remember { mutableStateOf(VoiceShieldState.IDLE) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }
    var audioRmsLevel by remember { mutableFloatStateOf(0.05f) }
    var transcriptText by remember { mutableStateOf("") }
    var threatResult by remember { mutableStateOf<InferenceResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var decoderOutcome by remember { mutableStateOf("Not run") }
    var lastRecordedByteCount by remember { mutableIntStateOf(0) }
    var showDiagnostics by remember { mutableStateOf(false) }

    var recordingJob by remember { mutableStateOf<Job?>(null) }
    var tickerJob by remember { mutableStateOf<Job?>(null) }
    val recordedAudioBuffer = remember { ByteArrayOutputStream() }

    fun cancelRecordingInternal() {
        tickerJob?.cancel()
        recordingJob?.cancel()
        audioRmsLevel = 0.05f
        synchronized(recordedAudioBuffer) { recordedAudioBuffer.reset() }
        state = VoiceShieldState.IDLE
    }

    fun stopRecordingInternal() {
        if (state != VoiceShieldState.RECORDING) return
        tickerJob?.cancel()
        recordingJob?.cancel()
        audioRmsLevel = 0.05f

        val recordedBytes = synchronized(recordedAudioBuffer) { recordedAudioBuffer.toByteArray() }
        lastRecordedByteCount = recordedBytes.size

        if (recordedBytes.isEmpty() || recordedBytes.size < 1600) {
            state = VoiceShieldState.ERROR
            errorMessage = "No speech audio detected in recording. Please speak clearly into the microphone."
            decoderOutcome = "Empty audio buffer (${recordedBytes.size} bytes)"
            return
        }

        state = VoiceShieldState.PREPARING_AUDIO

        val wavFile = File(context.cacheDir, "recorded_voice_sample.wav")
        try {
            writePcmToWav(wavFile, recordedBytes, sampleRate = 16000, channels = 1)
        } catch (e: Exception) {
            state = VoiceShieldState.ERROR
            errorMessage = "Failed to encode WAV audio: ${e.message}"
            decoderOutcome = "WAV encoding failure"
            return
        }

        if (!isWhisperProvisioned) {
            state = VoiceShieldState.MODEL_UNAVAILABLE
            errorMessage = "Offline speech recognition is not installed. Install the Voice Safety model to analyze recordings automatically."
            decoderOutcome = "Whisper weights unprovisioned"
            return
        }

        state = VoiceShieldState.TRANSCRIBING
        scope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            try {
                val engine = WhisperSttEngine.getInstance(context)
                val res = engine.transcribeWav(wavFile)
                val elapsed = System.currentTimeMillis() - startTime

                withContext(Dispatchers.Main) {
                    if (res.isSuccess && res.text.isNotBlank()) {
                        val decoded = res.text.trim()
                        transcriptText = decoded
                        decoderOutcome = "Success (${decoded.length} chars, ${elapsed}ms)"
                        state = VoiceShieldState.ANALYZING

                        // Automatic threat evaluation
                        scope.launch(Dispatchers.IO) {
                            val evalResult = threatEngine.analyzeCallTranscript(decoded)
                            withContext(Dispatchers.Main) {
                                threatResult = evalResult
                                state = VoiceShieldState.RESULT_READY
                            }
                        }
                    } else {
                        state = VoiceShieldState.ERROR
                        val failMsg = res.errorMessage ?: "Whisper detected no speech tokens in the recording."
                        errorMessage = failMsg
                        decoderOutcome = "Decoder returned empty or error: $failMsg"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state = VoiceShieldState.ERROR
                    errorMessage = "Speech recognition runtime notice: ${e.message}"
                    decoderOutcome = "Exception: ${e.message}"
                }
            }
        }
    }

    fun startRecordingInternal() {
        threatResult = null
        transcriptText = ""
        errorMessage = null
        recordingDurationSec = 0
        audioRmsLevel = 0.1f
        decoderOutcome = "Recording in progress..."
        state = VoiceShieldState.RECORDING
        synchronized(recordedAudioBuffer) { recordedAudioBuffer.reset() }

        // 1. Duration Ticker
        tickerJob = scope.launch {
            while (isActive && state == VoiceShieldState.RECORDING) {
                delay(1000)
                recordingDurationSec++
            }
        }

        // 2. Hardware AudioRecord thread
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
                    while (isActive && state == VoiceShieldState.RECORDING) {
                        val read = recorder.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            synchronized(recordedAudioBuffer) {
                                for (i in 0 until read) {
                                    val s = buffer[i].toInt()
                                    recordedAudioBuffer.write(s and 0xFF)
                                    recordedAudioBuffer.write((s shr 8) and 0xFF)
                                }
                            }
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
                } else {
                    withContext(Dispatchers.Main) {
                        state = VoiceShieldState.ERROR
                        errorMessage = "AudioRecord failed to initialize hardware capture."
                        decoderOutcome = "AudioRecord init failed"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state = VoiceShieldState.ERROR
                    errorMessage = "Hardware microphone capture error: ${e.message}"
                    decoderOutcome = "AudioRecord error: ${e.message}"
                }
            }
        }
    }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startRecordingInternal()
        } else {
            Toast.makeText(context, "Microphone permission is required to capture voice clips.", Toast.LENGTH_LONG).show()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cancelRecordingInternal()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GxBase)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Top Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                cancelRecordingInternal()
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
                    "Hardware AudioRecord capture & on-device Whisper analysis",
                    color = GxTextLo,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // Speech Engine Status Banner (Truthful, No Fake Claims)
        Surface(
            color = if (isWhisperProvisioned) GxSafeSoft else GxSurfaceAlt,
            shape = GxShapeSm,
            border = BorderStroke(1.dp, if (isWhisperProvisioned) GxSafe.copy(alpha = 0.5f) else GxWarning.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isWhisperProvisioned) Icons.Default.Security else Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = if (isWhisperProvisioned) GxSafe else GxWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isWhisperProvisioned) "SPEECH ENGINE: On-Device Whisper Multilingual Active (Arm Offline)" else "SPEECH ENGINE: STT Unavailable (Whisper Weights Not Provisioned)",
                            color = GxTextHi,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isWhisperProvisioned)
                                "100% offline speech recognition active for local voice and call transcription."
                            else
                                "Speech recognition requires the Voice Safety Model (Pack 2: Whisper Multilingual). Audio capture & offline threat analysis are active.",
                            color = GxTextMid,
                            fontSize = 11.sp
                        )
                    }
                }
                if (!isWhisperProvisioned && onOpenModelManager != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onOpenModelManager,
                        shape = GxShapeSm,
                        border = BorderStroke(1.dp, GxPrimary),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    ) {
                        Text("Download Voice Safety Model in Model Manager", color = GxPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // State-Aware Primary Interaction Area
        when (state) {
            VoiceShieldState.RECORDING -> {
                Card(
                    shape = GxShapeMd,
                    colors = CardDefaults.cardColors(containerColor = GxSurface),
                    border = BorderStroke(1.5.dp, GxDanger),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "RECORDING IN PROGRESS",
                                color = GxDanger,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.2.sp
                            )
                            Surface(
                                color = GxDangerSoft,
                                shape = GxShapePill,
                                border = BorderStroke(1.dp, GxDanger.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    "LIVE PCM",
                                    color = GxDanger,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Real-time Audio Level Waveform
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .background(GxSurfaceAlt, shape = GxShapeSm)
                                .padding(horizontal = 12.dp)
                        ) {
                            val barCount = 28
                            for (i in 0 until barCount) {
                                val phase = ((i.toFloat() / barCount) * Math.PI).toFloat()
                                val heightMultiplier = (audioRmsLevel * (0.3f + kotlin.math.sin(phase) * 0.7f)).coerceIn(0.08f, 1.0f)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height((60 * heightMultiplier).dp.coerceAtLeast(4.dp))
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(GxPrimary)
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = "Duration: %02d:%02d".format(recordingDurationSec / 60, recordingDurationSec % 60),
                            color = GxTextHi,
                            fontSize = 16.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = { stopRecordingInternal() },
                            colors = ButtonDefaults.buttonColors(containerColor = GxDanger),
                            shape = GxShapePill,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Stop Recording & Analyze", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Spacer(Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { cancelRecordingInternal() },
                            shape = GxShapePill,
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Text("Cancel Recording", color = GxTextLo, fontSize = 12.sp)
                        }
                    }
                }
            }

            VoiceShieldState.PREPARING_AUDIO,
            VoiceShieldState.TRANSCRIBING,
            VoiceShieldState.ANALYZING -> {
                Card(
                    shape = GxShapeMd,
                    colors = CardDefaults.cardColors(containerColor = GxSurface),
                    border = BorderStroke(1.dp, GxPrimary.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = GxPrimary,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = when (state) {
                                VoiceShieldState.PREPARING_AUDIO -> "Preparing audio for on-device processing..."
                                VoiceShieldState.TRANSCRIBING -> "Transcribing voice audio via on-device Whisper (Arm)..."
                                VoiceShieldState.ANALYZING -> "Checking for suspicious requests with SafetyBarrier & Astra..."
                                else -> "Processing..."
                            },
                            color = GxTextHi,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Executing entirely offline on device. No cloud transmission.",
                            color = GxTextLo,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            VoiceShieldState.ERROR -> {
                Card(
                    shape = GxShapeMd,
                    colors = CardDefaults.cardColors(containerColor = GxSurface),
                    border = BorderStroke(1.5.dp, GxDanger),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = GxDanger, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Voice Pipeline Notice",
                                color = GxDanger,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = errorMessage ?: "An unexpected error occurred during speech capture or decoding.",
                            color = GxTextMid,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                errorMessage = null
                                state = VoiceShieldState.IDLE
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GxPrimary),
                            shape = GxShapePill,
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Try Recording Again", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            VoiceShieldState.MODEL_UNAVAILABLE -> {
                Card(
                    shape = GxShapeMd,
                    colors = CardDefaults.cardColors(containerColor = GxSurface),
                    border = BorderStroke(1.5.dp, GxWarning),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = GxWarning, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Speech Model Required",
                                color = GxWarning,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Offline speech recognition is not installed. Install the Voice Safety model to analyze recordings automatically.",
                            color = GxTextHi,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Captured ${recordingDurationSec}s of audio ($lastRecordedByteCount bytes). Your recording is safely stored locally.",
                            color = GxTextLo,
                            fontSize = 11.sp
                        )
                        Spacer(Modifier.height(14.dp))
                        if (onOpenModelManager != null) {
                            Button(
                                onClick = onOpenModelManager,
                                colors = ButtonDefaults.buttonColors(containerColor = GxPrimary),
                                shape = GxShapePill,
                                modifier = Modifier.fillMaxWidth().height(42.dp)
                            ) {
                                Text("Download Voice Safety Model in Model Manager", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        OutlinedButton(
                            onClick = { state = VoiceShieldState.IDLE },
                            shape = GxShapePill,
                            modifier = Modifier.fillMaxWidth().height(38.dp)
                        ) {
                            Text("Record Another Audio Clip", color = GxTextMid, fontSize = 11.sp)
                        }
                    }
                }
            }

            VoiceShieldState.RESULT_READY -> {
                // Assessment Card
                val result = threatResult
                if (result != null) {
                    val riskBand = when {
                        result.riskScore >= 70 || result.severity == "HIGH" -> "HIGH RISK"
                        result.riskScore in 40..69 || result.severity == "MEDIUM" -> "NEEDS REVIEW"
                        else -> "LOW RISK"
                    }
                    val (borderColor, cardColor, textColor) = when (riskBand) {
                        "HIGH RISK" -> Triple(GxDanger, GxDangerSoft, GxDanger)
                        "NEEDS REVIEW" -> Triple(GxWarning, GxWarningSoft, GxWarning)
                        else -> Triple(GxSafe, GxSafeSoft, GxSafe)
                    }

                    Card(
                        shape = GxShapeMd,
                        colors = CardDefaults.cardColors(containerColor = GxSurface),
                        border = BorderStroke(1.5.dp, borderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SECURITY ASSESSMENT",
                                    color = GxTextLo,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )

                                Surface(
                                    color = cardColor,
                                    shape = GxShapePill,
                                    border = BorderStroke(1.dp, borderColor.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = riskBand,
                                        color = textColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = result.threatType.replace('_', ' ').uppercase(),
                                color = GxTextHi,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = result.explanation,
                                color = GxTextMid,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )

                            if (result.evidence.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Text("Detected Threat Signals:", color = GxTextHi, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                result.evidence.forEach { ev ->
                                    Text("• $ev", color = GxTextMid, fontSize = 11.sp)
                                }
                            }

                            if (result.recommendedActions.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                Text("Recommended Defensive Steps:", color = GxTextHi, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                result.recommendedActions.forEach { act ->
                                    Text("✓ $act", color = GxSafe, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Voice Transcript (Editable) Card with Reanalyze Option
                    Card(
                        shape = GxShapeMd,
                        colors = CardDefaults.cardColors(containerColor = GxSurface),
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
                                    text = "Voice Transcript (Editable)",
                                    color = GxTextHi,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Transcribed Locally",
                                    color = GxSafe,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedTextField(
                                value = transcriptText,
                                onValueChange = { transcriptText = it },
                                placeholder = {
                                    Text(
                                        "Voice transcript appears here. You can correct words if needed...",
                                        color = GxTextLo,
                                        fontSize = 12.sp
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                shape = GxShapeSm,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = GxSurfaceAlt,
                                    unfocusedContainerColor = GxSurfaceAlt,
                                    focusedBorderColor = GxPrimary,
                                    unfocusedBorderColor = GxBorder,
                                    focusedTextColor = GxTextHi,
                                    unfocusedTextColor = GxTextHi
                                )
                            )

                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val trimmed = transcriptText.trim()
                                        if (trimmed.isNotBlank()) {
                                            scope.launch {
                                                threatResult = threatEngine.analyzeCallTranscript(trimmed)
                                                Toast.makeText(context, "Reanalysis complete.", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    enabled = transcriptText.isNotBlank(),
                                    shape = GxShapeSm,
                                    modifier = Modifier.weight(1f).height(40.dp)
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Reanalyze Transcript", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        threatResult = null
                                        transcriptText = ""
                                        state = VoiceShieldState.IDLE
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = GxPrimary),
                                    shape = GxShapeSm,
                                    modifier = Modifier.weight(1f).height(40.dp)
                                ) {
                                    Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Record Another", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            else -> { // IDLE or CANCELLED
                Card(
                    shape = GxShapeMd,
                    colors = CardDefaults.cardColors(containerColor = GxSurface),
                    border = BorderStroke(1.dp, GxBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "LIVE MICROPHONE CAPTURE",
                            color = GxTextHi,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp
                        )

                        Spacer(Modifier.height(10.dp))

                        // Waveform Standby
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .background(GxSurfaceAlt, shape = GxShapeSm)
                                .padding(horizontal = 12.dp)
                        ) {
                            val barCount = 28
                            for (i in 0 until barCount) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(GxTextLo.copy(alpha = 0.3f))
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Duration: 00:00",
                                color = GxTextLo,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )

                            Surface(
                                color = GxSurfaceAlt,
                                shape = GxShapePill,
                                border = BorderStroke(1.dp, GxBorder)
                            ) {
                                Text(
                                    text = "STANDBY",
                                    color = GxTextLo,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Button(
                            onClick = {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) {
                                    startRecordingInternal()
                                } else {
                                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GxPrimary),
                            shape = GxShapePill,
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Record Voice Audio", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Privacy Guarantee Banner
                Surface(
                    color = GxSurfaceAlt,
                    shape = GxShapeSm,
                    border = BorderStroke(1.dp, GxBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GxSafe, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "100% On-Device Privacy Guaranteed: Audio captured by SuSagi is processed strictly on your phone's Arm processor. Zero audio is ever transmitted off this device.",
                            color = GxTextMid,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Manual Transcript Inspection & Scenarios (Fallback & Verification)
        Card(
            shape = GxShapeMd,
            colors = CardDefaults.cardColors(containerColor = GxSurface),
            border = BorderStroke(1.dp, GxBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Manual Transcript Inspection & Scenarios (Fallback / Testing)",
                    color = GxTextHi,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Inspect text transcripts directly or run pre-loaded fraud scenarios to evaluate security engine responses without voice recording.",
                    color = GxTextLo,
                    fontSize = 10.sp
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            transcriptText = "hello sir toh have a lottery of 10 k"
                            scope.launch {
                                threatResult = threatEngine.analyzeCallTranscript(transcriptText)
                                state = VoiceShieldState.RESULT_READY
                            }
                        },
                        shape = GxShapeSm,
                        border = BorderStroke(1.dp, GxWarning.copy(alpha = 0.6f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Lottery Bait", color = GxWarning, fontSize = 10.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            transcriptText = "This is Officer Sharma from CBI Mumbai. An arrest warrant is issued against your Aadhaar card for money laundering. You are in digital arrest. Do not disconnect this video call or police will reach your home."
                            scope.launch {
                                threatResult = threatEngine.analyzeCallTranscript(transcriptText)
                                state = VoiceShieldState.RESULT_READY
                            }
                        },
                        shape = GxShapeSm,
                        border = BorderStroke(1.dp, GxDanger.copy(alpha = 0.6f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Digital Arrest", color = GxDanger, fontSize = 10.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            transcriptText = "Hello sir, I am calling from State Bank verification. Your debit card is expiring today. Please share the 6-digit OTP sent to your phone to keep the card active."
                            scope.launch {
                                threatResult = threatEngine.analyzeCallTranscript(transcriptText)
                                state = VoiceShieldState.RESULT_READY
                            }
                        },
                        shape = GxShapeSm,
                        border = BorderStroke(1.dp, GxDanger.copy(alpha = 0.6f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bank OTP", color = GxDanger, fontSize = 10.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Pipeline Diagnostics Card (Phase 12)
        Card(
            shape = GxShapeMd,
            colors = CardDefaults.cardColors(containerColor = GxSurface),
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
                        text = "Pipeline Diagnostics & Telemetry",
                        color = GxTextHi,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedButton(
                        onClick = { showDiagnostics = !showDiagnostics },
                        shape = GxShapePill,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(if (showDiagnostics) "Hide" else "Show", fontSize = 10.sp, color = GxTextMid)
                    }
                }

                AnimatedVisibility(visible = showDiagnostics) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Text("• Pipeline State: ${state.name}", color = GxTextMid, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text("• App Build: Git Commit e7a0fe3 (Debug)", color = GxTextMid, fontSize = 11.sp)
                        Text("• Audio Capture: 16,000 Hz, 16-bit Mono PCM (Hardware AudioRecord)", color = GxTextMid, fontSize = 11.sp)
                        Text("• Audio Buffer: ${lastRecordedByteCount} bytes (${recordingDurationSec}s recorded)", color = GxTextMid, fontSize = 11.sp)
                        Text("• Preprocessing: RIFF 44-byte WAV header encoding (16kHz mono)", color = GxTextMid, fontSize = 11.sp)
                        Text("• STT Engine: " + if (isWhisperProvisioned) "Whisper Multilingual (Offline ExecuTorch Arm)" else "STT Model Not Provisioned (Pack 2 required)", color = GxTextMid, fontSize = 11.sp)
                        Text("• Native Decoder Outcome: $decoderOutcome", color = GxTextMid, fontSize = 11.sp)
                        Text("• Transcript Telemetry: ${transcriptText.length} characters, ${transcriptText.split(Regex("\\s+")).filter { it.isNotBlank() }.size} words", color = GxTextMid, fontSize = 11.sp)
                        Text("• Security Core: UnifiedThreatEngine + SafetyBarrier + Astra PrizeIntent", color = GxTextMid, fontSize = 11.sp)
                        Text("• Inference Latency: ${threatResult?.inferenceLatencyMs ?: 0} ms", color = GxTextMid, fontSize = 11.sp)
                        Text("• Risk Classification: ${threatResult?.severity ?: "IDLE"}", color = GxTextMid, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                        Text("• Telemetry Privacy: Zero personal audio, passwords, OTPs, or private transcripts are exposed in logs.", color = GxSafe, fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(30.dp))
    }
}

/**
 * Encodes raw 16-bit PCM audio samples into a standard 44-byte header WAV file.
 */
private fun writePcmToWav(
    wavFile: File,
    pcmData: ByteArray,
    sampleRate: Int = 16000,
    channels: Int = 1
) {
    val totalAudioLen = pcmData.size.toLong()
    val totalDataLen = totalAudioLen + 36
    val byteRate = (sampleRate * channels * 16) / 8

    val header = ByteArray(44)
    // RIFF chunk
    header[0] = 'R'.code.toByte()
    header[1] = 'I'.code.toByte()
    header[2] = 'F'.code.toByte()
    header[3] = 'F'.code.toByte()
    header[4] = (totalDataLen and 0xff).toByte()
    header[5] = ((totalDataLen shr 8) and 0xff).toByte()
    header[6] = ((totalDataLen shr 16) and 0xff).toByte()
    header[7] = ((totalDataLen shr 24) and 0xff).toByte()
    // WAVE chunk
    header[8] = 'W'.code.toByte()
    header[9] = 'A'.code.toByte()
    header[10] = 'V'.code.toByte()
    header[11] = 'E'.code.toByte()
    // 'fmt ' chunk
    header[12] = 'f'.code.toByte()
    header[13] = 'm'.code.toByte()
    header[14] = 't'.code.toByte()
    header[15] = ' '.code.toByte()
    header[16] = 16 // Subchunk1Size for PCM
    header[17] = 0
    header[18] = 0
    header[19] = 0
    header[20] = 1 // AudioFormat 1 = PCM
    header[21] = 0
    header[22] = channels.toByte()
    header[23] = 0
    header[24] = (sampleRate and 0xff).toByte()
    header[25] = ((sampleRate shr 8) and 0xff).toByte()
    header[26] = ((sampleRate shr 16) and 0xff).toByte()
    header[27] = ((sampleRate shr 24) and 0xff).toByte()
    header[28] = (byteRate and 0xff).toByte()
    header[29] = ((byteRate shr 8) and 0xff).toByte()
    header[30] = ((byteRate shr 16) and 0xff).toByte()
    header[31] = ((byteRate shr 24) and 0xff).toByte()
    header[32] = (channels * 16 / 8).toByte() // BlockAlign
    header[33] = 0
    header[34] = 16 // BitsPerSample
    header[35] = 0
    // 'data' chunk
    header[36] = 'd'.code.toByte()
    header[37] = 'a'.code.toByte()
    header[38] = 't'.code.toByte()
    header[39] = 'a'.code.toByte()
    header[40] = (totalAudioLen and 0xff).toByte()
    header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
    header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
    header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

    FileOutputStream(wavFile).use { fos ->
        fos.write(header)
        fos.write(pcmData)
        fos.flush()
    }
}
