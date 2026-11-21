package com.susagi.edgeai

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile

class ModelManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var modelsDir: File
    private lateinit var modelManager: ModelManager

    @Before
    fun setUp() {
        val baseDir = File("D:/SuSagi_Guardian_V2/app/build/tmp/test_models").apply { mkdirs() }
        modelsDir = File(baseDir, "run_${System.nanoTime()}").apply { mkdirs() }
        modelManager = ModelManager(context = null, overrideModelsDir = modelsDir)
    }

    @org.junit.After
    fun tearDown() {
        try {
            modelsDir.deleteRecursively()
        } catch (_: Exception) {}
    }

    /**
     * Helper to write ExecuTorch FlatBuffer magic header ('ET12' at offset 4).
     */
    private fun writeExecuTorchMagic(file: File, totalSize: Long = 2048L) {
        val raf = RandomAccessFile(file, "rw")
        raf.setLength(totalSize)
        raf.seek(0)
        // 4 bytes root table offset (e.g. 16) + 4 bytes 'ET12' magic (0x45, 0x54, 0x31, 0x32)
        val header = byteArrayOf(0x10, 0x00, 0x00, 0x00, 'E'.code.toByte(), 'T'.code.toByte(), '1'.code.toByte(), '2'.code.toByte())
        raf.write(header)
        raf.close()
    }

    @Test
    fun testMissingModel_returnsNotProvisioned() {
        // No model or tokenizer created
        val state = modelManager.getModelState()
        assertEquals(ModelManager.ModelState.NOT_PROVISIONED, state)
        assertFalse(modelManager.isReady())
    }

    @Test
    fun testTruncatedModel_returnsModelInvalid() {
        val modelFile = File(modelsDir, ModelManager.DEFAULT_MODEL_NAME)
        // Simulate a truncated SpinQuant file (e.g. 500MB instead of 1.08GB)
        writeExecuTorchMagic(modelFile, totalSize = 500_000_000L)

        val tokenizerFile = File(modelsDir, ModelManager.DEFAULT_TOKENIZER_NAME)
        tokenizerFile.writeText("{\"version\": \"1.0\"}".padEnd(1024, ' '))

        val valResult = modelManager.validateModelAsset(modelFile)
        assertTrue("Expected Truncated result but got $valResult", valResult is ModelManager.ModelValidationResult.Truncated)
        val truncated = valResult as ModelManager.ModelValidationResult.Truncated
        assertEquals(500_000_000L, truncated.actualBytes)
        assertEquals(ModelManifest.LLAMA_3_2_1B_SPINQUANT.modelSizeBytes, truncated.expectedBytes)

        val state = modelManager.getModelState()
        assertEquals(ModelManager.ModelState.MODEL_INVALID, state)
        assertFalse(modelManager.isReady())
    }

    @Test
    fun testInvalidHeader_returnsModelInvalid() {
        val modelFile = File(modelsDir, ModelManager.DEFAULT_MODEL_NAME)
        // Set to exact SpinQuant size, but with random corrupted non-ET12 bytes
        val raf = RandomAccessFile(modelFile, "rw")
        raf.setLength(ModelManifest.LLAMA_3_2_1B_SPINQUANT.modelSizeBytes)
        raf.seek(0)
        raf.write(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte(), 0x00, 0x00, 0x00, 0x00))
        raf.close()

        val tokenizerFile = File(modelsDir, ModelManager.DEFAULT_TOKENIZER_NAME)
        tokenizerFile.writeText("{\"version\": \"1.0\"}".padEnd(1024, ' '))

        val valResult = modelManager.validateModelAsset(modelFile)
        assertTrue("Expected InvalidHeader but got $valResult", valResult is ModelManager.ModelValidationResult.InvalidHeader)

        val state = modelManager.getModelState()
        assertEquals(ModelManager.ModelState.MODEL_INVALID, state)
        assertFalse(modelManager.isReady())
    }

    @Test
    fun testUnknownModelIdentity_returnsUnverified() {
        val modelFile = File(modelsDir, "custom_unregistered_model.pte")
        // Has valid ET12 magic, but size does not match any profile in ModelManifest
        writeExecuTorchMagic(modelFile, totalSize = 100_000_000L)

        val valResult = modelManager.validateModelAsset(modelFile)
        assertTrue("Expected UnknownIdentity but got $valResult", valResult is ModelManager.ModelValidationResult.UnknownIdentity)
    }

    @Test
    fun testMissingTokenizer_returnsNotProvisioned() {
        val modelFile = File(modelsDir, ModelManager.DEFAULT_MODEL_NAME)
        writeExecuTorchMagic(modelFile, totalSize = ModelManifest.LLAMA_3_2_1B_SPINQUANT.modelSizeBytes)

        // Tokenizer is NOT created
        val state = modelManager.getModelState()
        assertEquals(ModelManager.ModelState.NOT_PROVISIONED, state)
        assertFalse(modelManager.isReady())
    }

    @Test
    fun testIncorrectTokenizerFormat_returnsTokenizerMismatch() {
        val modelFile = File(modelsDir, ModelManager.DEFAULT_MODEL_NAME)
        writeExecuTorchMagic(modelFile, totalSize = ModelManifest.LLAMA_3_2_1B_SPINQUANT.modelSizeBytes)

        // Tokenizer is named .json but contains raw binary data without starting with '{'
        val tokenizerFile = File(modelsDir, ModelManager.DEFAULT_TOKENIZER_NAME)
        val raf = RandomAccessFile(tokenizerFile, "rw")
        raf.setLength(2048)
        raf.seek(0)
        raf.write(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0xFF.toByte()))
        raf.close()

        val tokVal = modelManager.validateTokenizerAsset(tokenizerFile)
        assertTrue("Expected FormatMismatch but got $tokVal", tokVal is ModelManager.TokenizerValidationResult.FormatMismatch)

        val state = modelManager.getModelState()
        assertEquals(ModelManager.ModelState.TOKENIZER_MISMATCH, state)
        assertFalse(modelManager.isReady())
    }

    @Test
    fun testCorrectlyPairedMetadata_returnsReady() {
        val modelFile = File(modelsDir, ModelManager.DEFAULT_MODEL_NAME)
        writeExecuTorchMagic(modelFile, totalSize = ModelManifest.LLAMA_3_2_1B_SPINQUANT.modelSizeBytes)

        val tokenizerFile = File(modelsDir, ModelManager.DEFAULT_TOKENIZER_NAME)
        // Ensure size >= 1024 bytes and valid JSON start
        val jsonContent = "{\n  \"version\": \"1.0\",\n  \"truncation\": null\n}".padEnd(1024, ' ')
        tokenizerFile.writeText(jsonContent)

        val state = modelManager.getModelState()
        assertEquals(ModelManager.ModelState.READY, state)
        assertTrue(modelManager.isReady())

        val report = modelManager.getModelValidationReport()
        assertTrue(report.contains("VALID"))
        assertTrue(report.contains("READY"))
    }

    @Test
    fun testNativeLoaderNeverInvokedWhenPrevalidationFails() = runBlocking {
        // Model is unprovisioned / invalid
        val engine = ExecuTorchLlamaEngine(context = null, modelManager = modelManager)
        assertFalse("Engine must not report ready when prevalidation fails", engine.isReady)

        val input = ThreatInput(
            channel = ThreatChannel.MESSAGE,
            rawContent = "URGENT: CBI has issued an arrest warrant. Connect on Skype immediately.",
            structuredEvidence = listOf("Authority Coercion")
        )

        // analyzeThreat should immediately fall back to local heuristic without throwing native SIGABRT
        val result = engine.analyzeThreat(input)
        assertNotNull(result)
        assertEquals(AnalysisSources.LOCAL_HEURISTIC, result.analysisSource)
        assertTrue(result.explanation.isNotBlank())
        assertTrue(result.riskScore >= 70)
        assertTrue("Deterministic heuristic fallback must execute in < 50ms, took ${result.inferenceLatencyMs}ms", result.inferenceLatencyMs < 50L)
    }

    @Test
    fun testModelCryptographicIntegrity_detectsSha256Mismatch() {
        val modelFile = File(modelsDir, ModelManager.DEFAULT_MODEL_NAME)
        // Write file with valid magic and SpinQuant size, but dummy content
        writeExecuTorchMagic(modelFile, totalSize = ModelManifest.LLAMA_3_2_1B_SPINQUANT.modelSizeBytes)

        val integrity = modelManager.getModelIntegrity()
        assertNotNull(integrity)
        // Dummy file content does not match the real SpinQuant model's pinned SHA-256
        assertFalse("ModelIntegrity must be false when SHA-256 digest does not match manifest", integrity!!.isValid)
        assertTrue(integrity.sha256.isNotBlank())

        // validateModelAsset with verifySha256=true must return Incompatible
        val shaValidation = modelManager.validateModelAsset(modelFile, verifySha256 = true)
        assertTrue("Expected Incompatible due to SHA-256 mismatch, got: $shaValidation",
            shaValidation is ModelManager.ModelValidationResult.Incompatible)
    }
}
