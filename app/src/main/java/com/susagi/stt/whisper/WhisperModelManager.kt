package com.susagi.stt.whisper

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.StatFs
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * WhisperModelManager
 *
 * Manages ExecuTorch Whisper speech recognition model files (.pte), preprocessor (.pte),
 * and tokenizer assets on the Android device.
 * Supports:
 * - App-private storage provisioning (/data/user/0/com.guardian.app/files/whisper)
 * - In-app direct downloads with multi-hop HTTP 301/302/307 redirect resolution
 * - Safe URI file importing from phone storage or SD card
 * - SHA-256 integrity verification
 * - Hardware and memory capacity checks
 */
class WhisperModelManager(private val context: Context) {

    companion object {
        private const val TAG = "WhisperModelManager"
        const val WHISPER_DIR_NAME = "whisper"

        const val DEFAULT_MODEL_NAME = "whisper_small_vivo_executorch_optimized.pte"
        const val DEFAULT_PREPROCESSOR_NAME = "whisper_preprocessor.pte"
        const val DEFAULT_TOKENIZER_NAME = "tokenizer.json"

        // Candidate model filenames supported by ExecuTorch runtime
        val CANDIDATE_MODEL_NAMES = listOf(
            "whisper_small_vivo_executorch_optimized.pte",
            "whisper_small.pte",
            "whisper.pte",
            "whisper_tiny.pte"
        )

        val CANDIDATE_PREPROCESSOR_NAMES = listOf(
            "whisper_preprocessor.pte",
            "preprocessor.pte"
        )

        val CANDIDATE_TOKENIZER_NAMES = listOf(
            "tokenizer.json",
            "tokenizer.bin",
            "tokenizer.model"
        )

        // Official Arm Whisper ExecuTorch model endpoints on Hugging Face
        const val HF_MODEL_URL = "https://huggingface.co/Arm/whisper-small-int8-xnnpack-executorch/resolve/main/whisper_small_vivo_executorch_optimized.pte"
        const val HF_PREPROCESSOR_URL = "https://huggingface.co/Arm/whisper-small-int8-xnnpack-executorch/resolve/main/whisper_preprocessor.pte"
        const val HF_TOKENIZER_URL = "https://huggingface.co/Arm/whisper-small-int8-xnnpack-executorch/resolve/main/tokenizer.json"

        private fun logD(msg: String) { try { Log.d(TAG, msg) } catch (_: Throwable) { println("[$TAG] $msg") } }
        private fun logI(msg: String) { try { Log.i(TAG, msg) } catch (_: Throwable) { println("[$TAG] $msg") } }
        private fun logW(msg: String) { try { Log.w(TAG, msg) } catch (_: Throwable) { println("[$TAG] $msg") } }
        private fun logE(msg: String, t: Throwable? = null) { try { Log.e(TAG, msg, t) } catch (_: Throwable) { println("[$TAG] $msg ${t?.message ?: ""}") } }
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

    val whisperDir: File
        get() = File(context.filesDir, WHISPER_DIR_NAME).apply { if (!exists()) mkdirs() }

    val tempAudioDir: File
        get() = File(context.cacheDir, "whisper_temp").apply { if (!exists()) mkdirs() }

    /**
     * Resolves the active Whisper model file (> 30MB).
     */
    val modelFile: File
        get() {
            for (name in CANDIDATE_MODEL_NAMES) {
                val f = File(whisperDir, name)
                if (f.exists() && f.length() > 30_000_000L) return f
            }
            val anyPte = whisperDir.listFiles { _, n -> n.endsWith(".pte") && !n.contains("preprocessor") }
                ?.firstOrNull { it.length() > 30_000_000L }
            if (anyPte != null) return anyPte

            return File(whisperDir, DEFAULT_MODEL_NAME)
        }

    /**
     * Resolves the active preprocessor file (> 10KB).
     */
    val preprocessorFile: File
        get() {
            for (name in CANDIDATE_PREPROCESSOR_NAMES) {
                val f = File(whisperDir, name)
                if (f.exists() && f.length() > 10_000L) return f
            }
            val anyPrep = whisperDir.listFiles { _, n -> n.contains("preprocessor") && n.endsWith(".pte") }
                ?.firstOrNull { it.length() > 10_000L }
            if (anyPrep != null) return anyPrep

            return File(whisperDir, DEFAULT_PREPROCESSOR_NAME)
        }

    /**
     * Resolves the active tokenizer file (> 1KB).
     */
    val tokenizerFile: File
        get() {
            for (name in CANDIDATE_TOKENIZER_NAMES) {
                val f = File(whisperDir, name)
                if (f.exists() && f.length() > 1024L) return f
            }
            val anyTok = whisperDir.listFiles { _, n -> n.contains("tokenizer") }
                ?.firstOrNull { it.length() > 1024L }
            if (anyTok != null) return anyTok

            return File(whisperDir, DEFAULT_TOKENIZER_NAME)
        }

    /**
     * Returns true if all required model assets are present on device storage.
     */
    fun isReady(): Boolean {
        val modelOk = modelFile.exists() && modelFile.length() > 30_000_000L
        val prepOk = preprocessorFile.exists() && preprocessorFile.length() > 10_000L
        val tokOk = tokenizerFile.exists() && tokenizerFile.length() > 1024L
        return modelOk && prepOk && tokOk
    }

    /**
     * Imports a file from an Android content Uri into the whisper directory.
     */
    suspend fun importFromUri(uri: Uri, targetFileName: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            logI("Importing $targetFileName from URI: $uri")
            val targetFile = File(whisperDir, targetFileName)
            val tempFile = File(whisperDir, "$targetFileName.import.tmp")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            } ?: return@withContext Result.failure(Exception("Cannot open input stream for URI"))

            if (tempFile.length() < 100L) {
                tempFile.delete()
                return@withContext Result.failure(Exception("Imported file is too small or empty (${tempFile.length()} bytes)"))
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            logI("Imported $targetFileName successfully: ${targetFile.length()} bytes")
            Result.success(targetFile)
        } catch (e: Exception) {
            logE("Failed to import from URI: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Downloads all three Whisper assets (model, preprocessor, tokenizer) sequentially.
     */
    suspend fun downloadWhisperAssets(
        onProgress: (DownloadProgress) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Check disk space (need at least 600 MB free)
            val freeSpace = getFreeStorageBytes()
            if (freeSpace < 600_000_000L) {
                return@withContext Result.failure(
                    Exception("Insufficient mobile storage: ${freeSpace / (1024 * 1024)} MB available, 600 MB required.")
                )
            }

            val itemsToDownload = listOf(
                Triple(HF_TOKENIZER_URL, DEFAULT_TOKENIZER_NAME, 2_400_000L),
                Triple(HF_PREPROCESSOR_URL, DEFAULT_PREPROCESSOR_NAME, 100_000L),
                Triple(HF_MODEL_URL, DEFAULT_MODEL_NAME, 414_235_776L)
            )

            for ((url, fileName, approxSize) in itemsToDownload) {
                val targetFile = File(whisperDir, fileName)
                if (targetFile.exists() && targetFile.length() >= (approxSize * 0.95)) {
                    logI("File $fileName already exists (${targetFile.length()} bytes), skipping download.")
                    continue
                }

                logI("Starting download of $fileName from $url")
                val downloadResult = downloadWithRedirects(url, fileName, onProgress)
                if (downloadResult.isFailure) {
                    return@withContext Result.failure(downloadResult.exceptionOrNull() ?: Exception("Failed to download $fileName"))
                }
            }

            onProgress(
                DownloadProgress(
                    bytesDownloaded = 416_700_000L,
                    totalBytes = 416_700_000L,
                    percent = 100f,
                    currentFileName = "Complete",
                    speedMbPerSec = 0f,
                    isComplete = true
                )
            )

            Result.success(Unit)
        } catch (e: Exception) {
            logE("Whisper asset download error: ${e.message}", e)
            Result.failure(e)
        }
    }

    private suspend fun downloadWithRedirects(
        initialUrl: String,
        targetFileName: String,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val targetFile = File(whisperDir, targetFileName)
        val tempFile = File(whisperDir, "$targetFileName.download.tmp")

        try {
            var currentUrl = initialUrl
            var connection: HttpURLConnection? = null
            var redirectCount = 0
            val maxRedirects = 8

            while (redirectCount < maxRedirects) {
                val urlObj = URL(currentUrl)
                connection = (urlObj.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "SuSagiGuardian-Android-ExecuTorch/3.1")
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER ||
                    responseCode == 307 || responseCode == 308) {
                    val location = connection.getHeaderField("Location")
                        ?: return@withContext Result.failure(Exception("Redirect with missing Location header"))
                    currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                        location
                    } else {
                        val base = URL(currentUrl)
                        URL(base, location).toString()
                    }
                    redirectCount++
                    connection.disconnect()
                } else if (responseCode in 200..299) {
                    break
                } else {
                    return@withContext Result.failure(Exception("HTTP error $responseCode downloading $targetFileName"))
                }
            }

            val conn = connection ?: return@withContext Result.failure(Exception("Connection failed"))
            val totalBytes = conn.contentLengthLong.let { if (it > 0) it else 414_000_000L }

            var downloadedBytes = 0L
            val startTime = System.currentTimeMillis()
            var lastProgressTime = startTime

            conn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(65536)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastProgressTime > 250) {
                            val elapsedSec = (now - startTime) / 1000f
                            val speedMb = if (elapsedSec > 0) (downloadedBytes / (1024f * 1024f)) / elapsedSec else 0f
                            val pct = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes) * 100f else 0f
                            onProgress(
                                DownloadProgress(
                                    bytesDownloaded = downloadedBytes,
                                    totalBytes = totalBytes,
                                    percent = pct.coerceIn(0f, 99.9f),
                                    currentFileName = targetFileName,
                                    speedMbPerSec = speedMb
                                )
                            )
                            lastProgressTime = now
                        }
                    }
                    output.flush()
                }
            }

            if (tempFile.length() < 1024L) {
                tempFile.delete()
                return@withContext Result.failure(Exception("Downloaded file too small: ${tempFile.length()} bytes"))
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            logI("Download complete for $targetFileName (${targetFile.length()} bytes)")
            Result.success(targetFile)
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            Result.failure(e)
        }
    }

    /**
     * Computes the SHA-256 checksum of a file.
     */
    suspend fun computeSha256(file: File): String = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext "FILE_NOT_FOUND"
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { fis ->
            val buffer = ByteArray(65536)
            var n: Int
            while (fis.read(buffer).also { n = it } != -1) {
                digest.update(buffer, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun getFreeStorageBytes(): Long {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }

    fun getAvailableRamBytes(): Long {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            memInfo.availMem
        } catch (_: Exception) {
            Long.MAX_VALUE
        }
    }
}
