package com.susagi.safety

import com.susagi.edgeai.ModelManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.pytorch.executorch.extension.llm.LlmCallback
import org.pytorch.executorch.extension.llm.LlmModule
import java.io.File
import java.security.MessageDigest

sealed class ExplanationResult {
    data class Generated(val evidenceOrder: List<Signal>, val text: String, val nativeOutput: String) : ExplanationResult()
    data class Unavailable(val reason: String) : ExplanationResult()
}

/** Optional, bounded evidence selection. The signed-off detector owns the verdict and wording. */
class LocalEvidenceExplainer(private val appPrivateModelDirectory: File) {
    private val gate = Mutex()
    private var loaded: LlmModule? = null

    suspend fun explain(result: Assessment): ExplanationResult = withContext(Dispatchers.IO) {
        gate.withLock {
            val allowed = result.evidence.map { it.id }.distinct()
            if (allowed.isEmpty()) return@withLock ExplanationResult.Unavailable("No detector evidence")
            try {
                val module = loaded ?: loadVerified().also { loaded = it }
                val prompt = "Select the most important evidence first. Return only supplied identifiers, separated by commas.\n" +
                    allowed.joinToString("\n") { it.name + ": " + it.explanation }
                val output = StringBuilder()
                var failed = false
                module.generate(prompt, 128, object : LlmCallback {
                    override fun onResult(token: String) { if (output.length + token.length <= 512) output.append(token) else failed = true }
                    override fun onError(code: Int, error: String) { failed = true }
                }, false)
                val signals = evidenceSelection(output.toString(), allowed)
                if (failed || signals == null)
                    ExplanationResult.Unavailable("Native output failed evidence validation")
                else ExplanationResult.Generated(signals, signals.joinToString(" ") { it.explanation }, output.toString())
            } catch (_: Exception) { ExplanationResult.Unavailable("Model verification or native execution failed") }
              catch (_: LinkageError) { ExplanationResult.Unavailable("Native runtime unavailable") }
        }
    }

    private fun loadVerified(): LlmModule {
        val spec = ModelManifest.LLAMA_3_2_1B_SPINQUANT
        fun verified(name: String, size: Long, sha: String): String {
            val file = File(appPrivateModelDirectory, name).canonicalFile
            require(file.parentFile == appPrivateModelDirectory.canonicalFile && file.isFile && file.length() == size)
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(262144)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            require(digest.digest().joinToString("") { "%02x".format(it) }.equals(sha, true))
            return file.path
        }
        val module = LlmModule(verified(spec.modelFileName, spec.modelSizeBytes, spec.modelSha256),
            verified(spec.tokenizerFileName, spec.tokenizerSizeBytes, spec.tokenizerSha256), 0f)
        try { module.load() } catch (failure: Throwable) { module.close(); throw failure }
        return module
    }

    suspend fun release() = withContext(Dispatchers.IO) {
        gate.withLock { loaded?.close(); loaded = null }
    }
}


internal fun evidenceSelection(output: String, allowed: List<Signal>): List<Signal>? {
    val ids = output.trim().split(',').map { it.trim() }
    val signals = ids.map { id -> allowed.find { it.name == id } ?: return null }
    return signals.takeIf { it.isNotEmpty() && it.distinct().size == it.size }
}
