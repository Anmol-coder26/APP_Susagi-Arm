package com.susagi.intelligence

import com.guardian.app.LinkCheckActivity
import com.guardian.app.RiskStatus
import com.susagi.audio.AudioFramePipeline
import com.susagi.audio.AudioSource
import com.susagi.edgeai.ExecuTorchLlamaEngine
import com.susagi.pipeline.GuardianPipelineCoordinator
import com.susagi.pipeline.PipelineState
import com.susagi.stt.DualSttController
import com.susagi.stt.SttEngineMode
import com.susagi.stt.TranscriptLine
import com.susagi.stt.TranscriptMerger
import com.susagi.stt.whisper.WhisperAudioProcessor
import com.susagi.stt.whisper.WhisperDecoder
import com.susagi.stt.whisper.WhisperModelManager
import kotlinx.coroutines.flow.first
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
 * WorkflowWiringIntegrationTest
 *
 * Comprehensive integration test suite validating real application wiring across
 * all 5 core workflows defined in the Master Engineering Directive:
 *
 * Workflow 1: Whisper Audio & Provisioning Wiring
 * Workflow 2: Llama Engine & Provisioning Lifecycle Wiring
 * Workflow 3: VoIP Pipeline, Dual STT Mode, & Audio Chunking Wiring
 * Workflow 4: Link Shield Intent Extraction & URL Safety Verdict Wiring
 * Workflow 5: Message Threat Reasoning & Structured Evidence Wiring
 */
class WorkflowWiringIntegrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // =========================================================================
    // WORKFLOW 1: WHISPER PROVISIONING & AUDIO PROCESSING WIRING
    // =========================================================================

    @Test
    fun workflow1_whisperEndpointsAndFileCandidates_validContracts() {
        // Verify default assets contract
        assertEquals("whisper_small_vivo_executorch_optimized.pte", WhisperModelManager.DEFAULT_MODEL_NAME)
        assertEquals("whisper_preprocessor.pte", WhisperModelManager.DEFAULT_PREPROCESSOR_NAME)
        assertEquals("tokenizer.json", WhisperModelManager.DEFAULT_TOKENIZER_NAME)

        // Verify official Arm Hugging Face URLs
        assertTrue(WhisperModelManager.HF_MODEL_URL.contains("Arm/whisper-small-int8-xnnpack-executorch"))
        assertTrue(WhisperModelManager.HF_PREPROCESSOR_URL.contains("Arm/whisper-small-int8-xnnpack-executorch"))
        assertTrue(WhisperModelManager.HF_TOKENIZER_URL.contains("Arm/whisper-small-int8-xnnpack-executorch"))
    }

    @Test
    fun workflow1_whisperAudioProcessor_turnsAndRiffHeaderGenerated() {
        val tempDir = tempFolder.newFolder("whisper_wiring_test")
        val processor = WhisperAudioProcessor(
            tempDir = tempDir,
            vadThresholdRms = 100.0,
            minTurnBytes = 6400, // 200ms
            maxSegmentBytes = 32000, // 1s
            silenceHangoverFrames = 2
        )

        // 1. Feed speech audio (RMS > 100)
        val speechPcm = ByteBuffer.allocate(3200).order(ByteOrder.LITTLE_ENDIAN).apply {
            for (i in 0 until 1600) putShort(500.toShort())
        }.array()

        // Push 3 active speech chunks
        processor.processPcmChunk(speechPcm)
        processor.processPcmChunk(speechPcm)
        processor.processPcmChunk(speechPcm)

        // 2. Feed silence chunks to trigger hangover seal
        val silencePcm = ByteArray(3200)
        processor.processPcmChunk(silencePcm)
        val wavFile = processor.processPcmChunk(silencePcm)

        assertNotNull("Speech segment should seal and produce a WAV file after silence hangover", wavFile)
        assertTrue(wavFile!!.exists())
        assertTrue("WAV file size must exceed standard 44-byte header", wavFile.length() > 44L)

        // Validate RIFF header
        val header = ByteArray(44)
        wavFile.inputStream().use { it.read(header) }
        val riff = String(header, 0, 4)
        val wave = String(header, 8, 4)
        val fmt = String(header, 12, 4)
        assertEquals("RIFF", riff)
        assertEquals("WAVE", wave)
        assertEquals("fmt ", fmt)

        wavFile.delete()
    }

    // =========================================================================
    // WORKFLOW 2: LLAMA ENGINE & PROVISIONING LIFECYCLE WIRING
    // =========================================================================

    @Test
    fun workflow2_llamaEngine_unprovisionedStateSafety() = runBlocking {
        val engine = ExecuTorchLlamaEngine(context = null)

        // Engine must NOT claim ready without model weights on device
        assertFalse("Llama engine must be unready without provisioned model weights", engine.isReady)

        // Raw benchmark in airplane mode without model must return informative result, NEVER crash
        val benchmark = engine.generateRawInference("Test cybersecurity prompt")
        assertNotNull(benchmark)
        assertFalse("Model must not report loaded when weights are absent", benchmark.isModelLoaded)
        assertEquals("MODEL_NOT_PROVISIONED", benchmark.analysisSource)
        assertTrue("Benchmark error must be reported", benchmark.error!!.isNotEmpty())
    }

    @Test
    fun workflow2_llamaEngine_fallbackToDeterministicHeuristicsWhenUnprovisioned() = runBlocking {
        val threatEngine = UnifiedThreatEngine()

        // Digital arrest extortion test
        val res = threatEngine.analyzeMessage("Cyber crime police department. You are under digital arrest. Transfer 50000 rupees immediately.")
        assertNotNull(res)
        assertTrue("Risk score must be high for digital arrest extortion", res.riskScore >= 85)
        assertEquals("LOCAL_HEURISTIC", res.analysisSource)
        assertEquals("HIGH", res.severity)
        assertTrue("Tactics must include authority impersonation", res.detectedTactics.contains("authority_impersonation"))
    }

    // =========================================================================
    // WORKFLOW 3: VOIP PIPELINE & DUAL STT CONTROLLER WIRING
    // =========================================================================

    @Test
    fun workflow3_pipelineCoordinator_strictBhashiniDefault() {
        val coordinator = GuardianPipelineCoordinator(
            geminiApiKey = "test_key",
            bhashiniEndpoint = "https://dhruva-api.bhashini.gov.in/services/inference/pipeline"
        )

        // Strict non-regression rule: Whisper is kept DISABLED in live calls
        assertEquals(
            "Live calling must strictly default to Bhashini online to prevent OOM",
            SttEngineMode.BHASHINI_ONLINE,
            coordinator.sttEngineMode.value
        )
        assertEquals(PipelineState.IDLE, coordinator.pipelineState.value)
    }

    @Test
    fun workflow3_transcriptMerger_interleaving() = runBlocking {
        val merger = TranscriptMerger()

        val line1 = TranscriptLine(AudioSource.LOCAL, "Hello, who is calling?", true, timestampMs = 1000L)
        val line2 = TranscriptLine(AudioSource.REMOTE, "I am Inspector from Delhi Police.", true, timestampMs = 2000L)

        merger.onTranscript(line1)
        merger.onTranscript(line2)

        val all = merger.transcriptHistory.value
        assertEquals(2, all.size)
        assertEquals("Hello, who is calling?", all[0].text)
        assertEquals("I am Inspector from Delhi Police.", all[1].text)
    }

    @Test
    fun workflow3_audioFramePipeline_chunkingAndRms() {
        var chunkCount = 0
        var totalBytesReceived = 0

        val pipeline = AudioFramePipeline(label = "test_pipeline") { chunk ->
            chunkCount++
            totalBytesReceived += chunk.size
        }
        pipeline.start()

        // Push 6400 bytes (equivalent to 200ms of 16kHz 16-bit mono PCM)
        val audioData = ByteArray(6400) { 10 }
        pipeline.onRawPcm(audioData, 0, audioData.size, 16000, 1)

        assertTrue("Received frames counter must track total frames", pipeline.receivedFramesCount > 0)
        pipeline.stop()
    }

    // =========================================================================
    // WORKFLOW 4: LINK SHIELD INTENT & SAFETY VERDICT WIRING
    // =========================================================================

    @Test
    fun workflow4_linkCheckActivity_urlExtractionAndThreatVerdicts() {
        // Extraction from plain and wrapped text
        val textWithLink = "Urgent: update your KYC immediately at https://sbi-card-verify.top/login before tonight"
        val extracted = LinkCheckActivity.extractUrlFromText(textWithLink)
        assertEquals("https://sbi-card-verify.top/login", extracted)

        // Phishing evaluation
        val verdictPhish = LinkCheckActivity.evaluateLinkSafety("https://electricity-power-cut.com/bill-pay")
        assertTrue("Phishing link must receive High or Medium risk", verdictPhish.status != RiskStatus.Low)

        // Malicious APK download evaluation
        val verdictApk = LinkCheckActivity.evaluateLinkSafety("https://bank-support-online.net/app.apk")
        assertEquals(RiskStatus.High, verdictApk.status)
        assertTrue("APK download must trigger malware alert", verdictApk.title.contains("Malware") || verdictApk.detail.contains("APK"))

        // IP address host evaluation
        val verdictIp = LinkCheckActivity.evaluateLinkSafety("http://192.168.1.100/login")
        assertTrue("Direct IP address host must be flagged", verdictIp.status != RiskStatus.Low)

        // Benign URL
        val verdictClean = LinkCheckActivity.evaluateLinkSafety("https://www.google.com")
        assertEquals(RiskStatus.Low, verdictClean.status)
    }

    // =========================================================================
    // WORKFLOW 5: MESSAGE SCAM SHIELD THREAT REASONING WIRING
    // =========================================================================

    @Test
    fun workflow5_messageThreatReasoning_allScamPatterns() = runBlocking {
        val engine = UnifiedThreatEngine()

        // 1. Digital Arrest Coercion
        val resArrest = engine.analyzeMessage("Police warrant issued against you for money laundering. Immediate arrest scheduled. Transfer funds.")
        assertTrue("Digital arrest must score >= 85", resArrest.riskScore >= 85)
        assertEquals("HIGH", resArrest.severity)

        // 2. Fake KYC SMS
        val resKyc = engine.analyzeMessage("Dear customer, your bank account is suspended due to pending PAN KYC. Update immediately.")
        assertTrue("Fake KYC must score >= 80", resKyc.riskScore >= 80)
        assertEquals("HIGH", resKyc.severity)

        // 3. Electricity Power Cut Scam
        val resPower = engine.analyzeMessage("Your electricity power will be disconnected at 9:30 PM due to unpaid electricity bill. Contact officer.")
        assertTrue("Power cut scam must score >= 80", resPower.riskScore >= 80)
        assertEquals("HIGH", resPower.severity)

        // 4. OTP Request
        val resOtp = engine.analyzeMessage("Share your 6-digit OTP verification code with our executive to avoid account suspension.")
        assertTrue("OTP theft request must score >= 75", resOtp.riskScore >= 75)
        assertEquals("HIGH", resOtp.severity)

        // 5. Harmless message
        val resBenign = engine.analyzeMessage("Let's meet for lunch at the cafeteria at 1 PM.")
        assertTrue("Harmless message must score < 40", resBenign.riskScore < 40)
        assertEquals("LOW", resBenign.severity)
    }
}
