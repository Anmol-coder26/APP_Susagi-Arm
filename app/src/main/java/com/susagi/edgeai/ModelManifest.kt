package com.susagi.edgeai

/**
 * ModelManifest
 *
 * Single authoritative specification of verified ExecuTorch model packages, tokenizers,
 * and executable preprocessors for SuSagi Guardian on Arm64 Android hardware.
 *
 * Pinned to verified repository commit revisions and genuine SHA-256 binary digests
 * (distinguished from HTTP ETags and Git blob identifiers).
 */
object ModelManifest {

    // =========================================================================
    // WHISPER SPEECH-TO-TEXT MODELS (Arm XNNPACK ExecuTorch)
    // =========================================================================

    data class WhisperAssetSpec(
        val profileId: String,
        val displayName: String,
        val repository: String,
        val commitRevision: String,
        val modelFileName: String,
        val modelDownloadUrl: String,
        val modelSizeBytes: Long,
        val modelSha256: String,
        val preprocessorFileName: String,
        val preprocessorDownloadUrl: String,
        val preprocessorSizeBytes: Long,
        val preprocessorSha256: String,
        val tokenizerFileName: String,
        val tokenizerDownloadUrl: String,
        val tokenizerSizeBytes: Long,
        val tokenizerSha256: String,
        val architecture: String,
        val quantization: String,
        val requiredOperators: List<String>,
        val nativeRunnerClass: String = "org.pytorch.executorch.extension.asr.AsrModule",
        val recommendedUse: String
    )

    /**
     * Recommended Production Profile: Arm Whisper Small (INT8 XNNPACK)
     * High accuracy on Indian accented English, Hindi, and conversational code-mixing (Hinglish).
     */
    val WHISPER_SMALL_ARM = WhisperAssetSpec(
        profileId = "whisper_small_arm",
        displayName = "Arm Whisper Small (Int8 XNNPACK)",
        repository = "Arm/whisper-small-int8-xnnpack-executorch",
        commitRevision = "15b7b44efa5696e1ba1bcd5751bfa116abcf310f",
        modelFileName = "whisper_small_vivo_executorch_optimized.pte",
        modelDownloadUrl = "https://huggingface.co/Arm/whisper-small-int8-xnnpack-executorch/resolve/15b7b44efa5696e1ba1bcd5751bfa116abcf310f/whisper_small_vivo_executorch_optimized.pte",
        modelSizeBytes = 414_235_776L, // ~395.05 MB
        modelSha256 = "e40e6010a9b9f658d6f532e286124bd3f1dbc62dfb5b2b8ccad1c4bf329f59a9",
        preprocessorFileName = "whisper_preprocessor.pte",
        preprocessorDownloadUrl = "https://huggingface.co/Arm/whisper-small-int8-xnnpack-executorch/resolve/15b7b44efa5696e1ba1bcd5751bfa116abcf310f/whisper_preprocessor.pte",
        preprocessorSizeBytes = 79_392L, // ~78 KB
        preprocessorSha256 = "96c62662de021200fecb21be8f9e8bf5845951f5e40944bf692ccbb19b85656a",
        tokenizerFileName = "tokenizer.json",
        tokenizerDownloadUrl = "https://huggingface.co/Arm/whisper-small-int8-xnnpack-executorch/resolve/15b7b44efa5696e1ba1bcd5751bfa116abcf310f/tokenizer.json",
        tokenizerSizeBytes = 2_480_466L, // ~2.37 MB
        tokenizerSha256 = "27fc476bfe7f17299480be2273fc0608e4d5a99aba2ab5dec5374b4482d1a566",
        architecture = "Whisper-Small (244M params, encoder-decoder)",
        quantization = "INT8 Weight-Only (XNNPACK quantized for Arm Cortex/Neoverse)",
        requiredOperators = listOf("quantized::linear", "xnnpack::convolution2d", "fb::sdpa"),
        recommendedUse = "Production VoIP conversational ASR (Hindi, Hinglish, English)"
    )

    /**
     * Low-Resource Alternative Profile: Arm Whisper Tiny (INT8 XNNPACK)
     * Ultra-fast execution for memory-constrained devices (< 4GB RAM).
     */
    val WHISPER_TINY_ARM = WhisperAssetSpec(
        profileId = "whisper_tiny_arm",
        displayName = "Arm Whisper Tiny (Int8 XNNPACK)",
        repository = "Arm/whisper-tiny-int8-xnnpack-executorch",
        commitRevision = "377732a1c4cee1ddaca9e6eb15df09e83a88eaff",
        modelFileName = "whisper_tiny_vivo_executorch_optimized.pte",
        modelDownloadUrl = "https://huggingface.co/Arm/whisper-tiny-int8-xnnpack-executorch/resolve/377732a1c4cee1ddaca9e6eb15df09e83a88eaff/whisper_tiny_vivo_executorch_optimized.pte",
        modelSizeBytes = 122_241_024L, // ~116.58 MB
        modelSha256 = "5d7595871c44fccd1262fbfec08bd31682fbe912eee967c6bad9104d1f4e42d2",
        preprocessorFileName = "whisper_preprocessor.pte",
        preprocessorDownloadUrl = "https://huggingface.co/Arm/whisper-tiny-int8-xnnpack-executorch/resolve/377732a1c4cee1ddaca9e6eb15df09e83a88eaff/whisper_preprocessor.pte",
        preprocessorSizeBytes = 79_392L,
        preprocessorSha256 = "96c62662de021200fecb21be8f9e8bf5845951f5e40944bf692ccbb19b85656a",
        tokenizerFileName = "tokenizer.json",
        tokenizerDownloadUrl = "https://huggingface.co/Arm/whisper-tiny-int8-xnnpack-executorch/resolve/377732a1c4cee1ddaca9e6eb15df09e83a88eaff/tokenizer.json",
        tokenizerSizeBytes = 2_480_466L,
        tokenizerSha256 = "27fc476bfe7f17299480be2273fc0608e4d5a99aba2ab5dec5374b4482d1a566",
        architecture = "Whisper-Tiny (39M params, encoder-decoder)",
        quantization = "INT8 Weight-Only (XNNPACK)",
        requiredOperators = listOf("quantized::linear", "xnnpack::convolution2d"),
        recommendedUse = "Resource-constrained devices / rapid benchmark verification"
    )

    // =========================================================================
    // LLAMA 3.2 1B REASONING MODELS (ExecuTorch Arm64)
    // =========================================================================

    data class LlamaAssetSpec(
        val profileId: String,
        val displayName: String,
        val repository: String,
        val commitRevision: String,
        val modelFileName: String,
        val modelDownloadUrl: String,
        val modelSizeBytes: Long,
        val modelSha256: String,
        val tokenizerFileName: String,
        val tokenizerDownloadUrl: String,
        val tokenizerSizeBytes: Long,
        val tokenizerSha256: String,
        val tokenizerFormat: String,
        val architecture: String,
        val quantization: String,
        val vocabularySize: Int = 128256,
        val nativeRunnerClass: String = "org.pytorch.executorch.extension.llm.LlmModule",
        val recommendedUse: String
    )

    /**
     * Recommended Production Profile: Software Mansion Llama 3.2 1B Instruct (SpinQuant Int4)
     * Fits in ~1.08 GB storage and requires ~1.2 GB RAM during token generation.
     */
    val LLAMA_3_2_1B_SPINQUANT = LlamaAssetSpec(
        profileId = "llama_3_2_1b_spinquant",
        displayName = "Llama 3.2 1B Instruct (SpinQuant Int4 XNNPACK)",
        repository = "software-mansion/Llama-3.2-ExecuTorch",
        commitRevision = "14abd0ac2a4c559114cb4f2944c51249d6656ed5",
        modelFileName = "llama_3_2_1b_xnnpack_spinquant.pte",
        modelDownloadUrl = "https://huggingface.co/software-mansion/Llama-3.2-ExecuTorch/resolve/14abd0ac2a4c559114cb4f2944c51249d6656ed5/1b/xnnpack/llama_3_2_1b_xnnpack_spinquant.pte",
        modelSizeBytes = 1_082_483_584L, // ~1.08 GB
        modelSha256 = "cd9b0bcc5a1d71072a818b9237dba45fc84fdf7b244fb455f63806bfc7111ad2",
        tokenizerFileName = "tokenizer.json",
        tokenizerDownloadUrl = "https://huggingface.co/software-mansion/Llama-3.2-ExecuTorch/resolve/14abd0ac2a4c559114cb4f2944c51249d6656ed5/tokenizer.json",
        tokenizerSizeBytes = 9_906_781L, // ~9.45 MB
        tokenizerSha256 = "0b9897f5668a5d202662c4bab2be785eb987daf194b557315a690f3d2dff1ce0",
        tokenizerFormat = "HuggingFace Tiktoken JSON (supported by HFTokenizer in ExecuTorch 1.5.1)",
        architecture = "Llama-3.2-1B-Instruct (16 layers, 2048 hidden dim, 32 heads)",
        quantization = "SpinQuant Int4 Weight-Only (XNNPACK)",
        recommendedUse = "On-device scam reasoning and coercion tactic extraction in Airplane Mode"
    )

    /**
     * Community Profile: Llama 3.2 1B with Tiktoken binary model
     * Recommended by official PyTorch ExecuTorch contributors (issue #17069).
     */
    val LLAMA_3_2_1B_COMMUNITY = LlamaAssetSpec(
        profileId = "llama_3_2_1b_community",
        displayName = "Llama 3.2 1B Community (FP16)",
        repository = "executorch-community/Llama-3.2-1B-ET",
        commitRevision = "5579e104f9b253ea2e8e3f98a6b1fc56a1bb86a2",
        modelFileName = "llama3_2-1B.pte",
        modelDownloadUrl = "https://huggingface.co/executorch-community/Llama-3.2-1B-ET/resolve/5579e104f9b253ea2e8e3f98a6b1fc56a1bb86a2/llama3_2-1B.pte",
        modelSizeBytes = 2_471_922_052L, // ~2.47 GB
        modelSha256 = "f86e32172c0c114ff059a5d6e2b2868b90485edc904bf35c5d5fcec483f277e7",
        tokenizerFileName = "tokenizer.model",
        tokenizerDownloadUrl = "https://huggingface.co/executorch-community/Llama-3.2-1B-ET/resolve/5579e104f9b253ea2e8e3f98a6b1fc56a1bb86a2/tokenizer.model",
        tokenizerSizeBytes = 2_183_982L, // ~2.08 MB
        tokenizerSha256 = "82e9d31979e92ab929cd544440f129d9ecd797b69e327f80f17e1c50d5551b55",
        tokenizerFormat = "TikToken Binary (tiktoken.model, supported by native Tiktoken loader in ExecuTorch)",
        architecture = "Llama-3.2-1B Unquantized",
        quantization = "FP16 / BF16",
        recommendedUse = "Devices with 8GB+ RAM capable of unquantized execution"
    )
}
