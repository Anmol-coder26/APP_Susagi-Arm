package com.susagi.edgeai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit & Integration test suite for SuSagiAiDiagnostics
 *
 * Implements Directive 54 Phase 4:
 * Validates that diagnostics inspect real execution state and never report PASS
 * on unprovisioned model weights.
 */
class SuSagiAiDiagnosticsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var emptyModelsDir: File
    private lateinit var modelManager: ModelManager
    private lateinit var llamaEngine: ExecuTorchLlamaEngine
    private lateinit var conversationManager: ConversationManager
    private lateinit var runner: SuSagiAiDiagnosticsRunner

    @Before
    fun setup() {
        emptyModelsDir = tempFolder.newFolder("susagi_models_test")
        modelManager = ModelManager(overrideModelsDir = emptyModelsDir)
        llamaEngine = ExecuTorchLlamaEngine(modelManager = modelManager)
        conversationManager = ConversationManager()
        runner = SuSagiAiDiagnosticsRunner(
            context = null,
            modelManager = modelManager,
            llamaEngine = llamaEngine,
            conversationManager = conversationManager
        )
    }

    @Test
    fun testHardwareDiagnostics_evaluatesRealHardwareValues() {
        val result = runner.runHardwareTest()
        assertEquals(DiagnosticStepId.STEP_1_HARDWARE, result.stepId)
        assertTrue("Details should contain ABI or RAM telemetry", result.details.contains("ABI") || result.details.contains("RAM"))
        assertTrue("Status must be either PASS or FAIL based on hardware environment",
            result.status == DiagnosticStepStatus.PASS || result.status == DiagnosticStepStatus.FAIL)
    }

    @Test
    fun testNativeRuntimeDiagnostics_detectsExecuTorchBinding() {
        val result = runner.runNativeRuntimeTest()
        assertEquals(DiagnosticStepId.STEP_2_NATIVE_RUNTIME, result.stepId)
        // In JVM test environment, org.pytorch.executorch.extension.llm.LlmModule is on classpath from AAR dependency
        assertEquals(DiagnosticStepStatus.PASS, result.status)
        assertTrue("Details should mention ExecuTorch bindings", result.details.contains("ExecuTorch"))
    }

    @Test
    fun testModelArtifactDiagnostics_returnsBlockedWhenMissing() {
        val result = runner.runModelArtifactTest()
        assertEquals(DiagnosticStepId.STEP_3_MODEL_ARTIFACT, result.stepId)
        assertEquals(DiagnosticStepStatus.BLOCKED, result.status)
        assertEquals(DiagnosticErrorCode.MODEL_FILE_MISSING, result.errorCode)
        assertTrue("Details should explain model file is absent", result.details.contains("Model file absent"))
    }

    @Test
    fun testTokenizerDiagnostics_returnsBlockedWhenMissing() {
        val result = runner.runTokenizerTest()
        assertEquals(DiagnosticStepId.STEP_4_TOKENIZER, result.stepId)
        assertEquals(DiagnosticStepStatus.BLOCKED, result.status)
        assertEquals(DiagnosticErrorCode.TOKENIZER_MISSING, result.errorCode)
        assertTrue("Details should explain tokenizer is absent", result.details.contains("Tokenizer file absent"))
    }

    @Test
    fun testCancellationDiagnostics_executesCleanly() {
        val result = runner.runCancellationTest(isModelLoaded = false)
        assertEquals(DiagnosticStepId.STEP_9_CANCELLATION, result.stepId)
        assertEquals(DiagnosticStepStatus.PASS, result.status)
        assertTrue(result.details.contains("stopGeneration"))
    }

    @Test
    fun testOfflineDiagnostics_verifiesZeroCloudSocketRequirement() {
        val result = runner.runOfflineTest(isModelLoaded = false)
        assertEquals(DiagnosticStepId.STEP_10_OFFLINE, result.stepId)
        assertEquals(DiagnosticStepStatus.PASS, result.status)
        assertTrue(result.details.contains("0 cloud APIs"))
    }

    @Test
    fun testFullDiagnosticSuite_executesAllTenSteps() = runBlocking {
        val progressUpdates = mutableListOf<DiagnosticStepResult>()
        val report = runner.runAllDiagnostics { update ->
            progressUpdates.add(update)
        }

        // All 10 steps must be evaluated
        assertEquals(10, report.results.size)
        assertEquals(10, progressUpdates.size)

        for (i in 1..10) {
            val stepResult = report.results[i - 1]
            assertEquals(i, stepResult.stepId.stepNumber)
        }

        // With unprovisioned model weights, overall status must be CONDITIONAL / BLOCKED, never false PASS
        assertEquals("CONDITIONAL / BLOCKED", report.overallStatus)

        // Step 3 (Model) and Step 4 (Tokenizer) must report BLOCKED
        val step3 = report.results.first { it.stepId == DiagnosticStepId.STEP_3_MODEL_ARTIFACT }
        assertEquals(DiagnosticStepStatus.BLOCKED, step3.status)
        assertEquals(DiagnosticErrorCode.MODEL_FILE_MISSING, step3.errorCode)

        val step5 = report.results.first { it.stepId == DiagnosticStepId.STEP_5_MODEL_LOADING }
        assertEquals(DiagnosticStepStatus.BLOCKED, step5.status)
    }

    @Test
    fun testRedactedMarkdownExport_containsZeroPiiAndAllSteps() = runBlocking {
        val report = runner.runAllDiagnostics()
        val markdown = report.toRedactedMarkdown()

        assertNotNull(markdown)
        assertTrue(markdown.contains("# SuSagi AI Diagnostics Report"))
        assertTrue(markdown.contains("Step 1: Hardware & Resources"))
        assertTrue(markdown.contains("Step 2: ExecuTorch Native Runtime"))
        assertTrue(markdown.contains("Step 3: Model Binary Artifact"))
        assertTrue(markdown.contains("Step 4: Tokenizer Availability & Format"))
        assertTrue(markdown.contains("Step 5: Model Loading & Initialization"))
        assertTrue(markdown.contains("Step 6: Native Autoregressive Inference"))
        assertTrue(markdown.contains("Step 7: Token Callback Streaming"))
        assertTrue(markdown.contains("Step 8: Multi-Turn Context Preservation"))
        assertTrue(markdown.contains("Step 9: Native Generation Cancellation"))
        assertTrue(markdown.contains("Step 10: Offline Execution Verification"))
        assertTrue(markdown.contains("No user messages, credentials, tokens, or PII are contained in this export"))

        // Assert no credentials or sensitive keywords leaked
        assertFalse(markdown.contains("sk-"))
        assertFalse(markdown.contains("Bearer"))
        assertFalse(markdown.contains("password"))
    }
}
