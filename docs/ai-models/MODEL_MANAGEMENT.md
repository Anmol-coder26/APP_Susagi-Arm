# SuSagi Guardian — Model Integrity & Pre-Flight Validation Report

**Problem Statement:** PS3 — On-Device Threat, Phishing, and Scam Detection  
**Investigation:** Forensic Analysis of On-Device Native SIGABRT Termination  
**Component:** `com.susagi.edgeai.ModelManager` & `ExecuTorchLlamaEngine`  
**Target Hardware:** Vivo V2141 (Arm Cortex-A76 + Cortex-A55 / 8 GB RAM / Android 12)  
**Status:** Solved, Implemented & Verified Offline  
**Evidence Levels:**
- Truncated Model File & Physical Hardware State: `VERIFIED ON PHYSICAL DEVICE`
- Pre-Flight Binary Validation & Non-Eager Fallback: `VERIFIED BY AUTOMATED TEST` (101 Tests Passing)
- Physical Device Llama Native Inference: `BLOCKED` (Waiting on Valid INT4 Model Weight Acquisition)

---

## 1. Executive Summary

During testing on the physical Vivo Android device, launching the application caused an immediate and unrecoverable termination (**Fatal signal 6 - SIGABRT**) without an Android JVM exception stack trace.

Forensic analysis of the device filesystem, logcat output, and NDK symbols revealed:
1. A background download of the Llama model weights terminated prematurely, leaving a truncated **1.96 GB** file on disk (`/data/user/0/com.guardian.app/files/models/llama_3_2_1b_xnnpack_spinquant.pte`).
2. The legacy `ModelManager` implementation validated model readiness solely with a coarse check: `file.length() > 50_000_000L` (50 MB).
3. The legacy `ExecuTorchLlamaEngine` eagerly called `LlmModule.load()` inside its class constructor `<init>`.
4. The native ExecuTorch flatbuffer parser failed to find mandatory trailing metadata tables, causing a native C++ throw in `libexecutorch.so`, which triggered an unhandled abort in `libfbjni.so`.

This document details the forensic evidence, the FlatBuffers binary layout, the model profile bounds, and the dual-layer Pre-Flight Integrity Guard that resolves this failure mode.

---

## 2. Forensic File Analysis & Model Profiles

The project manifest defines two primary candidate model distributions for Llama-3.2-1B:

### Table 1: Model Profiles vs. The Corrupted Physical State

| Attribute | Profile A: SpinQuant INT4 (Target) | Profile B: Community FP16 (Legacy) | Physical File on Handset (Vivo V2141) |
| :--- | :--- | :--- | :--- |
| **Filename** | `llama_3_2_1b_xnnpack_spinquant.pte` | `llama3_2-1B.pte` | `llama_3_2_1b_xnnpack_spinquant.pte` |
| **Quantization** | INT4 Weight / FP16 Activation | FP16 Unquantized | Truncated / Incomplete |
| **Authoritative Expected Bytes** | **1,082,483,584 bytes** (~1.08 GB) | **2,471,922,052 bytes** (~2.47 GB) | **1,964,614,528 bytes** (~1.96 GB) |
| **Tolerance Window** | $\pm 5\text{ MB}$ ($1.077\text{ GB} - 1.087\text{ GB}$) | $\pm 5\text{ MB}$ ($2.466\text{ GB} - 2.476\text{ GB}$) | Matches Neither Profile ($\Delta = +882\text{ MB}$ vs INT4) |
| **FlatBuffer File Identifier** | `ET12` (Valid) | `ET12` (Valid) | `ET12` (Valid start, missing tail) |
| **File Completeness** | Target profile | Alternate distribution | **Truncated Stream (Terminated at byte 1,964,614,527)** |
| **Native JNI Load Outcome** | Loadable on Arm NEON / XNNPACK | Memory exhaustion on 8 GB RAM | **Fatal Signal 6 (SIGABRT)** |

### 2.1 The Truncation Sequence of Events
1. In an earlier session, `ModelManager` attempted an in-app download. The download server returned `HTTP 416 (Range Not Satisfiable)`.
2. A fallback download script connected to an unquantized FP16 community mirror serving a 2.47 GB `.pte` file, but saved it under the INT4 filename `llama_3_2_1b_xnnpack_spinquant.pte`.
3. Due to a network socket timeout or Android OS background download throttling, the stream terminated at **1,964,614,528 bytes**.
4. Because `1,964,614,528 > 50,000,000`, the legacy `ModelManager.isReady()` returned `true`.

---

## 3. FlatBuffers Binary Structure & Native Abort Mechanics

ExecuTorch models are serialized using Google FlatBuffers, an efficient cross-platform serialization format that enables zero-copy in-memory deserialization.

```text
+-----------------------------------------------------------------------------------+
|                           EXECUTORCH FLATBUFFER LAYOUT                            |
+-----------------------------------------------------------------------------------+
|  Offset 0x00 - 0x03 : Root Table Offset Pointer (uoffset_t, little-endian uint32) |
|  Offset 0x04 - 0x07 : FlatBuffer File Identifier Magic ('ET12' / 0x45 0x54 0x31 0x32) |
|  Offset 0x08 - ...  : [Tensor Allocations, Weights, Graph Nodes, Operator Bindings]|
|  Offset EOF - N     : Program Metadata, Method Descriptors, Trailing Schema       |
+-----------------------------------------------------------------------------------+
```

### 3.1 Why Kotlin `try / catch` Cannot Catch SIGABRT
When `LlmModule.load()` was invoked on the truncated file:
1. `libexecutorch.so` checked the 4-byte file identifier (`ET12`), passed initial validation, and began mapping internal tensor pointers.
2. When attempting to resolve the root execution plan located towards the end of the flatbuffer, the pointer referenced an offset beyond `1,964,614,528 bytes` (into non-existent file space).
3. `libexecutorch.so` threw a C++ runtime exception:
   `"Failed to load model runner: Flatbuffer integrity check failed / out of bounds read"`
4. The exception passed to `libfbjni.so`'s exception handler:
   `facebook::jni::getJavaExceptionForCppException(std::exception_ptr)`
5. In `libfbjni.so`, an internal pointer assertion failed because the native exception was encountered in an unmapped state.
6. `libfbjni.so` invoked `libc.so abort()`.
7. `SIGABRT` (Signal 6) is sent by the Linux kernel directly to the process. The Android zygote immediately kills the process without unwinding the JVM stack. Kotlin `catch (t: Throwable)` is completely bypassed.

---

## 4. The Engineering Solution: Pre-Flight Integrity Guard

To prevent native SIGABRT termination from invalid assets, SuSagi Guardian introduces a **Two-Tier Pre-Flight Integrity Guard** in `ModelManager.kt` and refactors `ExecuTorchLlamaEngine.kt` to enforce non-eager initialization and graceful fallback.

### Tier 1: Multi-Attribute Asset Validation (`validateModelAsset`)

Before JNI is ever invoked, the file undergoes three independent checks:

```kotlin
sealed class ModelValidationResult {
    object Valid : ModelValidationResult()
    object Missing : ModelValidationResult()
    data class Truncated(val actualBytes: Long, val expectedBytes: Long) : ModelValidationResult()
    data class InvalidHeader(val header: String) : ModelValidationResult()
    data class IncompatibleProfile(val reason: String) : ModelValidationResult()
}
```

1. **Existence Check:** Verifies file presence on disk.
2. **Strict Profile Size Bounds Verification:**
   - Authoritative SpinQuant INT4: $1,082,483,584 \pm 5,242,880$ bytes ($1.077\text{ GB} - 1.087\text{ GB}$).
   - Authoritative Community FP16: $2,471,922,052 \pm 5,242,880$ bytes ($2.466\text{ GB} - 2.476\text{ GB}$).
   - Any file outside these valid profile windows (such as the truncated $1.96\text{ GB}$ file) is flagged as `ModelValidationResult.Truncated` and categorized as `ModelState.MODEL_INVALID`.
3. **FlatBuffers Header & Offset Verification:**
   - Reads the initial 8 bytes via `FileInputStream`.
   - Validates that bytes 0..3 form a little-endian `uoffset_t` pointing to a root table within valid file bounds (`4 until size`).
   - Validates that bytes 4..7 match the ExecuTorch file identifier `"ET12"`.
   - If either check fails, returns `ModelValidationResult.InvalidHeader`.

### Tier 2: Non-Eager Lazy Engine Lifecycle

The class constructor `<init>` in `ExecuTorchLlamaEngine.kt` does not perform eager initialization:

```kotlin
class ExecuTorchLlamaEngine(
    private val context: Context,
    private val modelManager: ModelManager
) {
    // Constructor DOES NOT call native initialization
    
    suspend fun analyzeThreat(content: String, threatType: String): InferenceResult {
        // Pre-Flight Guard: Never invoke native JNI unless model is verified
        if (!modelManager.isReady()) {
            Log.w(TAG, "Native model not ready or invalid. Gracefully using deterministic cybersecurity heuristics.")
            return executeLocalHeuristicIntelligence(threatType, content, "")
        }

        return withContext(Dispatchers.Default) {
            // Safe native execution...
        }
    }
}
```

---

## 5. Tokenizer Format Verification

In addition to weight files, the tokenizer format is verified before execution:
- **Expected Tokenizer:** Byte-Pair Encoding (BPE) Tiktoken binary format (`tokenizer.bin`, 320,000 vocab).
- **Validation Guard:** `validateTokenizerAsset()` verifies that the tokenizer file size is between `1.5 MB` and `5.0 MB`.
- Files with `.json` extensions or non-matching vocab sizes are prohibited from being passed to binary tokenizers to prevent internal parser faults.

---

## 6. Verification and Regression Testing

The Pre-Flight Integrity Guard was validated through automated tests (`ModelManagerTest.kt` and `CybersecurityBenchmarkEvaluationTest.kt`):
1. **Truncated File Injection:** A mock file of size `1,964,614,528` bytes with `ET12` header was tested. `validateModelAsset()` correctly returned `ModelValidationResult.Truncated`.
2. **Missing File Injection:** Correctly returned `ModelValidationResult.Missing`.
3. **Corrupted Header Injection:** Random bytes returned `ModelValidationResult.InvalidHeader`.
4. **Offline Test Suite:** All 101 automated unit tests passed offline with 0 failures (`BUILD SUCCESSFUL` in 3m 22s).

**Conclusion:** The native SIGABRT startup crash vulnerability has been resolved through deterministic pre-flight validation and resilient heuristic fallback.
