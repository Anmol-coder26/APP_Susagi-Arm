package com.susagi.safety

import java.security.MessageDigest

/** Shadow-only until an independently evaluated model meets the false-alarm requirement. */
object MessageModel {
    const val SHA256 = "33ef536e8f8bd0eb7abefafb5d3a3eb07e56654e45bb76a9bbde577ed7444188"
    enum class Status { MODEL_UNAVAILABLE, INVALID, SCORED }
    data class Result(val status: Status, val margin: Double? = null, val flagged: Boolean = false)
    class Loaded internal constructor(private val weights: DoubleArray, private val bias: Double, private val threshold: Double) {
        fun score(text: String): Result {
            var end = 0
            var count = 0
            while (end < text.length && count++ < 2048) end += Character.charCount(text.codePointAt(end))
            val bytes = text.substring(0,end).toByteArray(Charsets.UTF_8)
            val seen = BooleanArray(512)
            var hash = 0
            fun lower(i: Int): Int {
                val value = bytes[i].toInt() and 255
                return if (value in 65..90) value + 32 else value
            }
            for (i in 2 until bytes.size) {
                hash = (lower(i-2)*961 + lower(i-1)*31 + lower(i)) and 511
                seen[hash] = true
            }
            var margin = bias
            for (i in seen.indices) if (seen[i]) margin += weights[i]
            return Result(Status.SCORED, margin, margin >= threshold)
        }
    }
    class Adapter private constructor(private val model: Loaded?, private val failure: Status) {
        fun score(text: String): Result = model?.score(text) ?: Result(failure)
        companion object {
            fun load(asset: ByteArray?): Adapter {
                if (asset == null) return Adapter(null, Status.MODEL_UNAVAILABLE)
                if (asset.size !in 1..32768) return Adapter(null, Status.INVALID)
                val normalized = if (asset.contains('\r'.code.toByte())) {
                    val out = java.io.ByteArrayOutputStream(asset.size)
                    for (b in asset) {
                        if (b != '\r'.code.toByte()) out.write(b.toInt())
                    }
                    out.toByteArray()
                } else asset
                val digest = MessageDigest.getInstance("SHA-256").digest(normalized).joinToString("") { "%02x".format(it) }
                if (digest != SHA256) return Adapter(null, Status.INVALID)
                val lines = normalized.toString(Charsets.US_ASCII).trimEnd().lines()
                if (lines.size != 516 || lines.first() != "susagi-byte3-bnb-v1") return Adapter(null, Status.INVALID)
                val values = lines.drop(2).map { it.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return Adapter(null, Status.INVALID) }
                return Adapter(Loaded(values.drop(2).toDoubleArray(), values[0], values[1]), Status.SCORED)
            }
        }
    }
    data class Comparison(val verdict: Assessment, val shadow: Result)
    suspend fun analyze(text: String, adapter: Adapter) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        Comparison(SafetyBarrier.analyzeMessage(text), adapter.score(text))
    }
}
