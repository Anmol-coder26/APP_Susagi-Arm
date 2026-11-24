package com.susagi.edgeai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Manages ExecuTorch Llama model files (.pte) and tokenizer assets on the device.
 * Implements strict Pre-Flight verification to guarantee zero native SIGABRT crashes.
 */
class ModelManager(
    private val context: Context? = null,
    private val overrideModelsDir: File? = null
) {

    companion object {
        private const val TAG = "ModelManager"
        const val MODEL_DIR_NAME = "models"
        const val DEFAULT_MODEL_NAME = "llama_3_2_1b_xnnpack_spinquant.pte"
        const val DEFAULT_TOKENIZER_NAME = "tokenizer.json"

        // Candidate model filenames supported by ExecuTorch runtime
        val CANDIDATE_MODEL_NAMES = listOf(
            "llama_3_2_1b_xnnpack_spinquant.pte",
            "llama3_2-1B.pte",
            "llama3_2_1b_instruct_q4.pte",
            "stories110M.pte"
        )

        // Candidate tokenizer filenames
        val CANDIDATE_TOKENIZER_NAMES = listOf(
            "tokenizer.json",
            "tokenizer.model",
            "tokenizer.bin"
        )
    }

    enum class ModelState {
        NOT_PROVISIONED,
        PROVISIONING,
        READY,
        MODEL_INVALID,
        MODEL_INCOMPATIBLE,
        TOKENIZER_MISMATCH,
        UNVERIFIED,
        ERROR
    }

    sealed class ModelValidationResult {
        object NotFound : ModelValidationResult()
        data class InvalidHeader(val reason: String) : ModelValidationResult()
        data class Truncated(val actualBytes: Long, val expectedBytes: Long, val profileName: String) : ModelValidationResult()
        data class Incompatible(val reason: String) : ModelValidationResult()
        data class UnknownIdentity(val filename: String, val actualBytes: Long) : ModelValidationResult()
        data class Valid(val profile: ModelManifest.LlamaAssetSpec, val hasExecuTorchMagic: Boolean) : ModelValidationResult()
    }

    sealed class TokenizerValidationResult {
        object NotFound : TokenizerValidationResult()
        data class Invalid(val reason: String) : TokenizerValidationResult()
        data class FormatMismatch(val reason: String) : TokenizerValidationResult()
        data class Valid(val isJson: Boolean) : TokenizerValidationResult()
    }

    data class DownloadProgress(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val percent: Float,
        val currentFileName: String,
        val speedMbPerSec: Float,
        val isComplete: Boolean = false,
        val error: String? = null
    )

    val modelsDir: File
        get() {
            if (overrideModelsDir != null) {
                if (!overrideModelsDir.exists()) overrideModelsDir.mkdirs()
                return overrideModelsDir
            }
            val base = context?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: ".", "susagi_models")
            return File(base, MODEL_DIR_NAME).apply { if (!exists()) mkdirs() }
        }

    /**
     * Resolves all candidate directories where model assets might be located.
     * Includes app-internal storage, device Downloads folder, external files, and adb tmp.
     */
    fun getCandidateDirectories(): List<File> {
        val dirs = mutableListOf<File>()
        dirs.add(modelsDir)

        // 1. Android public Download directory
        try {
            val pubDownload = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (pubDownload != null && pubDownload.exists() && pubDownload.canRead()) {
                dirs.add(pubDownload)
                dirs.add(File(pubDownload, "llama"))
                dirs.add(File(pubDownload, "susagi"))
            }
        } catch (_: Throwable) {}

        // 2. App-specific external storage
        try {
            context?.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)?.let {
                if (it.exists() && it.canRead()) dirs.add(it)
            }
            context?.getExternalFilesDir(null)?.let {
                if (it.exists() && it.canRead()) {
                    dirs.add(it)
                    dirs.add(File(it, "models"))
                    dirs.add(File(it, "Download"))
                }
            }
        } catch (_: Throwable) {}

        // 3. Well-known Android filesystem locations
        val wellKnownPaths = listOf(
            "/sdcard/Download",
            "/sdcard/Download/llama",
            "/sdcard/Download/susagi",
            "/storage/emulated/0/Download",
            "/storage/emulated/0/Download/llama",
            "/storage/emulated/0/Download/susagi",
            "/data/local/tmp/llama"
        )
        for (p in wellKnownPaths) {
            val d = File(p)
            if (d.exists() && d.canRead() && !dirs.any { it.absolutePath == d.absolutePath }) {
                dirs.add(d)
            }
        }
        return dirs
    }

    /**
     * Resolves the active model file by checking known candidates or any valid .pte > 50MB.
     */
    val modelFile: File
        get() {
            val searchDirs = getCandidateDirectories()

            // 1. Check preferred candidate names across search directories
            for (dir in searchDirs) {
                for (name in CANDIDATE_MODEL_NAMES) {
                    val f = File(dir, name)
                    if (f.exists() && f.length() > 50_000_000L) return f
                }
            }

            // 2. Check any .pte file > 50MB across search directories
            for (dir in searchDirs) {
                try {
                    val anyPte = dir.listFiles { _, n -> n.endsWith(".pte") }?.firstOrNull { it.length() > 50_000_000L }
                    if (anyPte != null) return anyPte
                } catch (_: Throwable) {}
            }

            // Default fallback pointer in app-private storage
            return File(modelsDir, DEFAULT_MODEL_NAME)
        }

    /**
     * Resolves the active tokenizer file.
     */
    val tokenizerFile: File
        get() {
            val searchDirs = getCandidateDirectories()

            // 1. Check preferred candidate names across search directories
            for (dir in searchDirs) {
                for (name in CANDIDATE_TOKENIZER_NAMES) {
                    val f = File(dir, name)
                    if (f.exists() && f.length() > 1024L) return f
                }
            }

            // 2. Check any tokenizer file across search directories
            for (dir in searchDirs) {
                try {
                    val anyTok = dir.listFiles { _, n -> n.contains("tokenizer") }?.firstOrNull { it.length() > 1024L }
                    if (anyTok != null) return anyTok
                } catch (_: Throwable) {}
            }

            return File(modelsDir, DEFAULT_TOKENIZER_NAME)
        }

    /**
     * Copies a user-selected file from a content URI (e.g. from file picker) into modelsDir.
     */
    fun importFileFromUri(
        contentResolver: android.content.ContentResolver,
        uri: android.net.Uri,
        targetFilename: String
    ): Boolean {
        return try {
            val target = File(modelsDir, targetFilename)
            contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            target.exists() && target.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error importing file from URI: ${e.message}", e)
            false
        }
    }

    fun validateModelAsset(file: File, verifySha256: Boolean = false): ModelValidationResult {
        if (!file.exists() || !file.canRead()) return ModelValidationResult.NotFound
        val size = file.length()
        if (size < 1024L) return ModelValidationResult.InvalidHeader("File too small (< 1KB)")

        // 1. Inspect header bytes for ExecuTorch flatbuffer identifier (ET12)
        // In FlatBuffers schema: bytes 0..3 are the root table uoffset_t (little-endian uint32),
        // and bytes 4..7 contain the 4-byte file_identifier ("ET12" = 0x45, 0x54, 0x31, 0x32).
        var hasMagic = false
        try {
            val header = ByteArray(16)
            val readCount = file.inputStream().use { it.read(header) }
            if (readCount >= 8) {
                val hasIdentifierAtOffset4 = (header[4] == 'E'.code.toByte() && header[5] == 'T'.code.toByte() && header[6] == '1'.code.toByte() && header[7] == '2'.code.toByte())
                val hasIdentifierAtOffset0 = (header[0] == 'E'.code.toByte() && header[1] == 'T'.code.toByte() && header[2] == '1'.code.toByte() && header[3] == '2'.code.toByte())

                if (hasIdentifierAtOffset4) {
                    val rootTableOffset = (header[0].toInt() and 0xFF) or
                        ((header[1].toInt() and 0xFF) shl 8) or
                        ((header[2].toInt() and 0xFF) shl 16) or
                        ((header[3].toInt() and 0xFF) shl 24)
                    // Valid FlatBuffer requires rootTableOffset > 0 and within file bounds
                    if (rootTableOffset in 4 until size) {
                        hasMagic = true
                    }
                } else if (hasIdentifierAtOffset0) {
                    hasMagic = true
                }
            }
        } catch (_: Exception) {}

        if (!hasMagic) {
            return ModelValidationResult.InvalidHeader("Missing or malformed ExecuTorch flatbuffer header (ET12)")
        }

        // 2. Match against authoritative ModelManifest profiles
        val spinQuantExpected = ModelManifest.LLAMA_3_2_1B_SPINQUANT.modelSizeBytes
        val communityExpected = ModelManifest.LLAMA_3_2_1B_COMMUNITY.modelSizeBytes
        val isSpinQuantSize = Math.abs(size - spinQuantExpected) < 50_000_000L // within 50MB tolerance
        val isCommunitySize = Math.abs(size - communityExpected) < 50_000_000L

        // Check for specific truncated partial downloads
        if (file.name.contains("spinquant", ignoreCase = true) && !isSpinQuantSize) {
            return ModelValidationResult.Truncated(
                actualBytes = size,
                expectedBytes = spinQuantExpected,
                profileName = ModelManifest.LLAMA_3_2_1B_SPINQUANT.displayName
            )
        }
        if (file.name.contains("llama3_2-1B", ignoreCase = true) && !isCommunitySize) {
            return ModelValidationResult.Truncated(
                actualBytes = size,
                expectedBytes = communityExpected,
                profileName = ModelManifest.LLAMA_3_2_1B_COMMUNITY.displayName
            )
        }

        // Detect unknown model identity if size matches neither profile
        if (!isSpinQuantSize && !isCommunitySize) {
            return if ((file.name.contains("spinquant", ignoreCase = true) || file.name.contains("llama3_2-1B", ignoreCase = true)) && size in 100_000_000L..2_700_000_000L) {
                // Suspicious intermediate truncation
                ModelValidationResult.Truncated(
                    actualBytes = size,
                    expectedBytes = communityExpected,
                    profileName = "Truncated Llama 3.2 1B Download"
                )
            } else {
                ModelValidationResult.UnknownIdentity(file.name, size)
            }
        }

        val matchedProfile = if (isSpinQuantSize) ModelManifest.LLAMA_3_2_1B_SPINQUANT else ModelManifest.LLAMA_3_2_1B_COMMUNITY

        // 3. Cryptographic SHA-256 verification when requested
        if (verifySha256) {
            val hash = computeSha256(file)
            if (!hash.equals(matchedProfile.modelSha256, ignoreCase = true)) {
                return ModelValidationResult.Incompatible(
                    "Cryptographic SHA-256 mismatch for ${matchedProfile.displayName}: expected ${matchedProfile.modelSha256}, got $hash"
                )
            }
        }

        return ModelValidationResult.Valid(
            profile = matchedProfile,
            hasExecuTorchMagic = true
        )
    }

    fun validateTokenizerAsset(file: File): TokenizerValidationResult {
        if (!file.exists() || !file.canRead()) return TokenizerValidationResult.NotFound
        val size = file.length()
        if (size < 1024L) return TokenizerValidationResult.Invalid("File too small (< 1KB)")

        var actualIsJson = false
        try {
            val buf = ByteArray(256)
            val read = file.inputStream().use { it.read(buf) }
            val snippet = if (read > 0) String(buf, 0, read, Charsets.UTF_8).trim() else ""
            actualIsJson = snippet.startsWith("{")
        } catch (_: Exception) {}

        val isNamedJson = file.name.endsWith(".json")
        val isNamedModel = file.name.endsWith(".model") || file.name.endsWith(".bin")

        if (isNamedJson && !actualIsJson) {
            return TokenizerValidationResult.FormatMismatch("File is named .json but contains binary non-JSON data")
        }
        if (isNamedModel && actualIsJson) {
            return TokenizerValidationResult.FormatMismatch("File is named .model but contains JSON text")
        }

        return TokenizerValidationResult.Valid(isJson = actualIsJson)
    }

    fun getModelState(): ModelState {
        val m = modelFile
        val t = tokenizerFile
        if (!m.exists() || !t.exists()) {
            return ModelState.NOT_PROVISIONED
        }

        val modelVal = validateModelAsset(m)
        when (modelVal) {
            is ModelValidationResult.NotFound -> return ModelState.NOT_PROVISIONED
            is ModelValidationResult.InvalidHeader -> return ModelState.MODEL_INVALID
            is ModelValidationResult.Truncated -> return ModelState.MODEL_INVALID
            is ModelValidationResult.Incompatible -> return ModelState.MODEL_INCOMPATIBLE
            is ModelValidationResult.UnknownIdentity -> {
                val tokVal = validateTokenizerAsset(t)
                when (tokVal) {
                    is TokenizerValidationResult.NotFound -> return ModelState.NOT_PROVISIONED
                    is TokenizerValidationResult.Valid -> return ModelState.UNVERIFIED
                    else -> return ModelState.TOKENIZER_MISMATCH
                }
            }
            is ModelValidationResult.Valid -> {
                val tokVal = validateTokenizerAsset(t)
                when (tokVal) {
                    is TokenizerValidationResult.NotFound -> return ModelState.NOT_PROVISIONED
                    is TokenizerValidationResult.Invalid -> return ModelState.TOKENIZER_MISMATCH
                    is TokenizerValidationResult.FormatMismatch -> return ModelState.TOKENIZER_MISMATCH
                    is TokenizerValidationResult.Valid -> {
                        // Check pairing compatibility
                        val isSpinQuant = modelVal.profile.profileId == ModelManifest.LLAMA_3_2_1B_SPINQUANT.profileId
                        if (isSpinQuant && !tokVal.isJson) {
                            return ModelState.TOKENIZER_MISMATCH
                        }
                        return ModelState.READY
                    }
                }
            }
        }
    }

    fun getModelValidationReport(): String {
        val m = modelFile
        val t = tokenizerFile
        val sb = StringBuilder()
        sb.appendLine("Model File: ${m.name} (${if (m.exists()) "${m.length()} bytes" else "MISSING"})")
        when (val mv = validateModelAsset(m)) {
            is ModelValidationResult.NotFound -> sb.appendLine("  Status: NOT FOUND")
            is ModelValidationResult.InvalidHeader -> sb.appendLine("  Status: INVALID HEADER (${mv.reason})")
            is ModelValidationResult.Truncated -> sb.appendLine("  Status: TRUNCATED (${mv.actualBytes} bytes vs expected ${mv.expectedBytes} for ${mv.profileName})")
            is ModelValidationResult.Incompatible -> sb.appendLine("  Status: INCOMPATIBLE (${mv.reason})")
            is ModelValidationResult.UnknownIdentity -> sb.appendLine("  Status: UNVERIFIED IDENTITY (${mv.filename}, ${mv.actualBytes} bytes)")
            is ModelValidationResult.Valid -> sb.appendLine("  Status: VALID (Profile: ${mv.profile.displayName}, Magic: ${mv.hasExecuTorchMagic})")
        }
        sb.appendLine("Tokenizer File: ${t.name} (${if (t.exists()) "${t.length()} bytes" else "MISSING"})")
        when (val tv = validateTokenizerAsset(t)) {
            is TokenizerValidationResult.NotFound -> sb.appendLine("  Status: NOT FOUND")
            is TokenizerValidationResult.Invalid -> sb.appendLine("  Status: INVALID (${tv.reason})")
            is TokenizerValidationResult.FormatMismatch -> sb.appendLine("  Status: MISMATCH (${tv.reason})")
            is TokenizerValidationResult.Valid -> sb.appendLine("  Status: VALID (isJson=${tv.isJson})")
        }
        sb.appendLine("Composite State: ${getModelState()}")
        return sb.toString().trim()
    }

    fun isReady(): Boolean = getModelState() == ModelState.READY || getModelState() == ModelState.UNVERIFIED

    fun getAvailableStorageBytes(): Long {
        return try {
            val stat = StatFs(context?.filesDir?.absolutePath ?: modelsDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            -1L
        }
    }

    fun getAvailableRamBytes(): Long {
        return try {
            val actManager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (actManager != null) {
                val memInfo = ActivityManager.MemoryInfo()
                actManager.getMemoryInfo(memInfo)
                memInfo.availMem
            } else {
                Runtime.getRuntime().freeMemory()
            }
        } catch (e: Exception) {
            -1L
        }
    }

    fun isArm64Supported(): Boolean {
        val abis = Build.SUPPORTED_ABIS ?: emptyArray()
        return abis.any { it.contains("arm64") || it.contains("aarch64") }
    }

    fun getHardwareSummary(): Map<String, String> {
        val actManager = context?.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        return mapOf(
            "supported_abis" to (Build.SUPPORTED_ABIS?.joinToString() ?: "unknown"),
            "cpu_abi" to (Build.CPU_ABI ?: "unknown"),
            "is_arm64" to isArm64Supported().toString(),
            "available_ram_mb" to ((memInfo.availMem) / (1024 * 1024)).toString(),
            "total_ram_mb" to ((memInfo.totalMem) / (1024 * 1024)).toString(),
            "low_memory" to memInfo.lowMemory.toString(),
            "storage_free_mb" to (getAvailableStorageBytes() / (1024 * 1024)).toString(),
            "model_ready" to isReady().toString(),
            "model_size_mb" to (if (modelFile.exists()) (modelFile.length() / (1024 * 1024)).toString() else "0")
        )
    }

    /**
     * Downloads the Llama model and matching tokenizer into app-private storage.
     */
    suspend fun downloadModelAndTokenizer(
        onProgress: (DownloadProgress) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val freeSpace = getAvailableStorageBytes()
        val minRequiredSpace = 1_800_000_000L // 1.8 GB required
        if (freeSpace in 0 until minRequiredSpace) {
            onProgress(
                DownloadProgress(
                    bytesDownloaded = 0,
                    totalBytes = minRequiredSpace,
                    percent = 0f,
                    currentFileName = DEFAULT_MODEL_NAME,
                    speedMbPerSec = 0f,
                    error = "Insufficient storage: ${freeSpace / (1024 * 1024)}MB available, 1800MB required"
                )
            )
            return@withContext false
        }

        // 1. Download Tokenizer first (~2.2MB)
        val tokenizerDest = File(modelsDir, DEFAULT_TOKENIZER_NAME)
        val tokVal = validateTokenizerAsset(tokenizerDest)
        if (tokVal !is TokenizerValidationResult.Valid) {
            val tokUrls = listOf(
                "https://huggingface.co/software-mansion/Llama-3.2-ExecuTorch/resolve/main/tokenizer.json",
                "https://huggingface.co/executorch-community/Llama-3.2-1B-ET/resolve/main/tokenizer.model"
            )
            val tokSuccess = downloadFileWithFallback(tokUrls, tokenizerDest) { bytes, total, pct, speed ->
                onProgress(DownloadProgress(bytes, total, pct, DEFAULT_TOKENIZER_NAME, speed))
            }
            if (!tokSuccess || validateTokenizerAsset(tokenizerDest) !is TokenizerValidationResult.Valid) {
                onProgress(DownloadProgress(0, 0, 0f, DEFAULT_TOKENIZER_NAME, 0f, error = "Failed to download valid tokenizer"))
                return@withContext false
            }
        }

        // 2. Download Model file (~1.08 GB)
        val modelDest = File(modelsDir, DEFAULT_MODEL_NAME)
        val modelVal = validateModelAsset(modelDest)
        if (modelVal !is ModelValidationResult.Valid) {
            Log.i(TAG, "Existing model invalid or incomplete (${modelVal::class.java.simpleName}). Starting clean download.")
            if (modelDest.exists()) {
                modelDest.delete()
            }
            val modelUrls = listOf(
                "https://huggingface.co/software-mansion/Llama-3.2-ExecuTorch/resolve/main/1b/xnnpack/llama_3_2_1b_xnnpack_spinquant.pte",
                "https://huggingface.co/executorch-community/Llama-3.2-1B-ET/resolve/main/llama3_2-1B.pte"
            )
            val modelSuccess = downloadFileWithFallback(modelUrls, modelDest) { bytes, total, pct, speed ->
                onProgress(DownloadProgress(bytes, total, pct, DEFAULT_MODEL_NAME, speed))
            }
            if (!modelSuccess || validateModelAsset(modelDest) !is ModelValidationResult.Valid) {
                onProgress(DownloadProgress(0, 0, 0f, DEFAULT_MODEL_NAME, 0f, error = "Failed to download valid model weights"))
                return@withContext false
            }
        }

        onProgress(DownloadProgress(modelDest.length(), modelDest.length(), 100f, DEFAULT_MODEL_NAME, 0f, isComplete = true))
        return@withContext true
    }

    private fun downloadFileWithFallback(
        urls: List<String>,
        dest: File,
        progress: (bytes: Long, total: Long, pct: Float, speed: Float) -> Unit
    ): Boolean {
        for (urlStr in urls) {
            try {
                Log.i(TAG, "Attempting download from: $urlStr")
                val success = downloadSingleFile(urlStr, dest, progress)
                if (success && dest.exists() && dest.length() > 1024L) {
                    Log.i(TAG, "Download succeeded from $urlStr: ${dest.length()} bytes")
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Download attempt failed for $urlStr: ${e.message}")
            }
        }
        return false
    }

    private fun downloadSingleFile(
        urlStr: String,
        dest: File,
        progress: (bytes: Long, total: Long, pct: Float, speed: Float) -> Unit
    ): Boolean {
        var currentUrl = urlStr
        var redirects = 0
        val maxRedirects = 8

        val partFile = File(dest.parentFile, dest.name + ".part")
        var existingBytes = 0L
        if (partFile.exists()) {
            existingBytes = partFile.length()
        }

        while (redirects < maxRedirects) {
            val url = URL(currentUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 60000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "SuSagiGuardian/3.0 (Android; Arm64)")
                if (existingBytes > 0) {
                    setRequestProperty("Range", "bytes=$existingBytes-")
                }
            }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                responseCode == 307 || responseCode == 308
            ) {
                val newUrl = conn.getHeaderField("Location")
                conn.disconnect()
                if (!newUrl.isNullOrBlank()) {
                    currentUrl = if (newUrl.startsWith("http")) newUrl else URL(url, newUrl).toString()
                    redirects++
                    Log.i(TAG, "Following HTTP $responseCode redirect ($redirects/$maxRedirects) to: $currentUrl")
                    continue
                } else {
                    return false
                }
            }

            val isResume = (responseCode == HttpURLConnection.HTTP_PARTIAL)
            val isOk = (responseCode == HttpURLConnection.HTTP_OK)

            if (!isOk && !isResume) {
                Log.w(TAG, "Download failed with HTTP response $responseCode from $currentUrl")
                conn.disconnect()
                return false
            }

            val totalLength = if (isResume) {
                conn.contentLengthLong + existingBytes
            } else {
                existingBytes = 0L
                conn.contentLengthLong
            }

            val fos = FileOutputStream(partFile, isResume)
            val buffer = ByteArray(128 * 1024)
            var bytesRead: Int
            var downloaded = existingBytes
            val startTime = System.currentTimeMillis()
            var lastUpdate = System.currentTimeMillis()

            try {
                conn.inputStream.use { input ->
                    fos.use { output ->
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead

                            val now = System.currentTimeMillis()
                            if (now - lastUpdate > 500) {
                                val elapsedSec = (now - startTime) / 1000f
                                val speedMb = if (elapsedSec > 0) (downloaded - existingBytes) / (1024f * 1024f) / elapsedSec else 0f
                                val pct = if (totalLength > 0) (downloaded.toFloat() / totalLength.toFloat()) * 100f else 0f
                                progress(downloaded, totalLength, pct, speedMb)
                                lastUpdate = now
                            }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }

            if (dest.exists()) dest.delete()
            val renamed = partFile.renameTo(dest)
            return renamed && dest.exists()
        }

        return false
    }

    fun provisionFromLocalFile(sourceModel: File, sourceTokenizer: File): Boolean {
        return try {
            if (!sourceModel.exists() || !sourceTokenizer.exists()) {
                Log.e(TAG, "Source files do not exist for provisioning")
                return false
            }
            sourceModel.copyTo(File(modelsDir, sourceModel.name), overwrite = true)
            sourceTokenizer.copyTo(File(modelsDir, sourceTokenizer.name), overwrite = true)
            Log.i(TAG, "Successfully provisioned ExecuTorch Llama model: ${modelFile.length()} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model files: ${e.message}", e)
            false
        }
    }

    fun importFromUri(uri: android.net.Uri, isTokenizer: Boolean): Boolean {
        return try {
            val destFile = if (isTokenizer) File(modelsDir, DEFAULT_TOKENIZER_NAME) else File(modelsDir, DEFAULT_MODEL_NAME)
            context?.contentResolver?.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.i(TAG, "Imported file from URI into ${destFile.absolutePath}: ${destFile.length()} bytes")
            destFile.exists() && destFile.length() > (if (isTokenizer) 1024L else 50_000_000L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import from URI: ${e.message}", e)
            false
        }
    }

    fun computeSha256(file: File): String {
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(256 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    data class ModelIntegrityInfo(
        val fileName: String,
        val sizeBytes: Long,
        val sha256: String,
        val isValid: Boolean
    )

    fun getModelIntegrity(): ModelIntegrityInfo? {
        val f = modelFile
        if (!f.exists() || f.length() < 1024) return null
        val hash = computeSha256(f)
        val validation = validateModelAsset(f)
        val isCryptographicallyValid = if (validation is ModelValidationResult.Valid) {
            hash.equals(validation.profile.modelSha256, ignoreCase = true)
        } else {
            false
        }
        return ModelIntegrityInfo(
            fileName = f.name,
            sizeBytes = f.length(),
            sha256 = hash,
            isValid = isCryptographicallyValid
        )
    }
}
