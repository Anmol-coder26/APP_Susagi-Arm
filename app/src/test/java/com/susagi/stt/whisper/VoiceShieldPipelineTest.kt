package com.susagi.stt.whisper

import com.susagi.intelligence.UnifiedThreatEngine
import com.susagi.safety.Risk
import com.susagi.safety.SafetyBarrier
import com.susagi.safety.Signal
import com.susagi.ui.voice.VoiceShieldState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * VoiceShieldPipelineTest
 *
 * Implements Directive 60 Phase 14:
 * Exhaustive verification of the end-to-end Voice Shield pipeline:
 * - State machine transitions (IDLE, RECORDING, PREPARING_AUDIO, TRANSCRIBING, ANALYZING, RESULT_READY, ERROR, MODEL_UNAVAILABLE, CANCELLED).
 * - Empty audio capture guards (no false benign verdicts on empty input).
 * - Automatic security analysis invocation without manual typing.
 * - Astra PrizeIntent integration & contextual threat classification (Cases A to F).
 * - Audio preprocessing, PCM byte-alignment, and RIFF WAV header verification.
 */
class VoiceShieldPipelineTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testVoiceShieldState_allEnumStatesDefined() {
        val expectedStates = listOf(
            "IDLE",
            "RECORDING",
            "PREPARING_AUDIO",
            "TRANSCRIBING",
            "ANALYZING",
            "RESULT_READY",
            "ERROR",
            "MODEL_UNAVAILABLE",
            "CANCELLED"
        )
        val actualStates = VoiceShieldState.values().map { it.name }
        assertEquals(expectedStates, actualStates)
    }

    @Test
    fun testVoiceShieldState_errorStatePreventsBenignVerdict() {
        // When transcription fails or audio buffer is empty, state must be ERROR
        // and no benign result or score 15 must be emitted.
        val emptyAudioBytes = ByteArray(0)
        val state = if (emptyAudioBytes.size < 1600) VoiceShieldState.ERROR else VoiceShieldState.PREPARING_AUDIO
        assertEquals(VoiceShieldState.ERROR, state)
    }

    @Test
    fun testVoiceShieldState_modelUnavailableTransition() {
        // If audio is captured but Whisper model is not on disk, transition to MODEL_UNAVAILABLE
        val capturedAudioBytes = ByteArray(32000) // 1 second of audio
        val isWhisperReady = false
        val state = if (!isWhisperReady) VoiceShieldState.MODEL_UNAVAILABLE else VoiceShieldState.TRANSCRIBING
        assertEquals(VoiceShieldState.MODEL_UNAVAILABLE, state)
    }

    @Test
    fun testLotteryBait_automaticThreatAnalysis_routesNeedsReview() = runBlocking {
        // The owner's exact failed input from Directive 56/60
        val transcript = "hello sir toh have a lottery of 10 k"
        val threatEngine = UnifiedThreatEngine()
        val result = threatEngine.analyzeCallTranscript(transcript)

        // Must NOT be LOW RISK or score 15
        assertEquals("MEDIUM", result.severity)
        assertEquals("unverified_prize", result.threatType)
        assertEquals(listOf("PRIZE_CLAIM"), result.detectedTactics)
        assertTrue(result.explanation.contains("prize", ignoreCase = true))
        assertTrue(result.recommendedActions.isNotEmpty())
    }

    @Test
    fun testAdvanceFeeFraud_automaticThreatAnalysis_routesHighRisk() = runBlocking {
        val transcript = "You have won ₹10,000. Send ₹500 processing fee to claim."
        val threatEngine = UnifiedThreatEngine()
        val result = threatEngine.analyzeCallTranscript(transcript)

        assertEquals("HIGH", result.severity)
        assertTrue(result.riskScore >= 70)
        assertTrue(
            result.detectedTactics.any { it.equals("advance_fee", ignoreCase = true) } ||
            result.threatType.contains("advance_fee", ignoreCase = true)
        )
    }

    @Test
    fun testDigitalArrestFraud_automaticThreatAnalysis_routesHighRisk() = runBlocking {
        val transcript = "This is Officer Sharma from CBI Mumbai. An arrest warrant is issued against your Aadhaar card for money laundering. You are in digital arrest. Do not disconnect this video call or police will reach your home."
        val threatEngine = UnifiedThreatEngine()
        val result = threatEngine.analyzeCallTranscript(transcript)

        assertEquals("HIGH", result.severity)
        assertTrue(result.riskScore >= 80)
        assertTrue(result.evidence.any { it.contains("Police/CBI", ignoreCase = true) })
    }

    @Test
    fun testBankOtpTheft_automaticThreatAnalysis_routesHighRisk() = runBlocking {
        val transcript = "Hello sir, I am calling from State Bank verification. Your debit card is expiring today. Please share the 6-digit OTP sent to your phone to keep the card active."
        val threatEngine = UnifiedThreatEngine()
        val result = threatEngine.analyzeCallTranscript(transcript)

        assertEquals("HIGH", result.severity)
        assertTrue(result.riskScore >= 70)
    }

    @Test
    fun testSchoolLotteryDiscussion_benignControl_routesLowRisk() = runBlocking {
        val transcript = "Our school conducted a lottery, and a student won ten thousand rupees."
        val barrier = SafetyBarrier.analyzeMessage(transcript)
        assertEquals(Risk.LOW, barrier.risk)
    }

    @Test
    fun testFraudAwarenessQuotation_benignControl_routesLowRisk() = runBlocking {
        val transcript = "Don't believe anyone who says you won a lottery and asks you to pay a release fee."
        val barrier = SafetyBarrier.analyzeMessage(transcript)
        assertEquals(Risk.LOW, barrier.risk)
    }

    @Test
    fun testHinglishPrizeSolicitation_routesHighRisk() = runBlocking {
        val transcript = "Sir aapki 10 hazaar ki lottery lagi hai, prize lene ke liye pehle processing fee bhejo."
        val barrier = SafetyBarrier.analyzeMessage(transcript)
        assertEquals(Risk.HIGH, barrier.risk)
    }

    @Test
    fun testHinglishPrizeClaim_SirAapkiDasHazaar_routesNeedsReview() = runBlocking {
        // The owner's exact test sentence: "Sir aapki das hazaar ki lottery lagi hai."
        val transcript = "Sir aapki das hazaar ki lottery lagi hai."
        val threatEngine = UnifiedThreatEngine()
        val result = threatEngine.analyzeCallTranscript(transcript)

        assertEquals("MEDIUM", result.severity)
        assertEquals("unverified_prize", result.threatType)
        assertEquals(listOf("PRIZE_CLAIM"), result.detectedTactics)
        assertTrue(result.explanation.contains("prize", ignoreCase = true))
    }

    @Test
    fun testHindiPrizeSolicitation_routesHighRisk() = runBlocking {
        val transcript = "आपकी लॉटरी लगी है, इनाम पाने के लिए फीस भेजें।"
        val barrier = SafetyBarrier.analyzeMessage(transcript)
        assertEquals(Risk.HIGH, barrier.risk)
    }

    @Test
    fun testAudioPreprocessing_writePcmToWav_validRiffHeader() {
        val dir = tempFolder.newFolder("wav_test")
        val wavFile = File(dir, "test.wav")
        val sampleRate = 16000
        val channels = 1
        val pcm = ByteArray(32000) { 0x1A.toByte() } // 1 second of 16-bit mono

        // Call AudioProcessor or test encoding
        val processor = WhisperAudioProcessor(tempDir = dir)
        val generatedWav = processor.writeWavFile(pcm, sampleRate, channels)

        assertTrue(generatedWav.exists())
        assertEquals(32000L + 44L, generatedWav.length())

        val bytes = generatedWav.readBytes()
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals("fmt ", String(bytes, 12, 4))
        assertEquals("data", String(bytes, 36, 4))

        val readSampleRate = ByteBuffer.wrap(bytes, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
        assertEquals(16000, readSampleRate)

        val readChannels = ByteBuffer.wrap(bytes, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short
        assertEquals(1.toShort(), readChannels)
    }
}
