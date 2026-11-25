# Testing & Verification Evidence

SuSagi Guardian strictly distinguishes **automated host JVM unit tests** from **physical ARM64 Android runtime behavior** and **controlled offline simulations**. Desktop JVM tests verify rule logic, parsing, and contracts; they do **not** establish on-device ExecuTorch neural throughput or real-world call interception.

---

## 1. Automated JVM Test Suite (`./gradlew testDebugUnitTest`)

Measured directly from generated XML test reports in `app/build/test-results/testDebugUnitTest/` (`TEST-*.xml`):

| Metric | Observed Result |
| :--- | :--- |
| **Test Suites Executed** | **51 suites** |
| **Total Test Cases** | **318 tests** |
| **Failures / Errors / Skipped** | **0 failures · 0 errors · 0 skipped** |
| **Cumulative Test Runner Time** | **12.37 seconds** |

### Selected Suite Coverage

| Suite File | Scope Verified on JVM |
| :--- | :--- |
| `SafetyBarrierTest` / `AdversarialBarrierTest` / `ContextPairsTest` | NFKC normalization, homoglyph/IDN parsing, `.apk` trap rules, contextual vs warning phrasing |
| `PrizeContextTest` / `LotteryScamEvaluationTest` / `PrizeIntentRegression` | Benign celebration vs unverified prize claim vs advance-fee disambiguation |
| `LinkCheckSecurityTest` / `PaymentMutationTest` | Raw IP hosts, brand spoofing (`sbi.co.in.fake.com`), nested redirects, `upi://pay` reverse-debit detection |
| `UnifiedThreatEngineTest` / `SecurityFlowIntegrationTest` | Risk floor enforcement (`HIGH >= 85`, `REVIEW >= 40`), heuristic fallback continuity |
| `ModelManagerTest` / `ConversationManagerTest` / `ExecuTorch` sanitize tests | `ET12` header/size checks, prompt-echo detection (`isPromptEcho`), special-token stripping |
| `NetworkAnomalyClassifierTest` / `NetworkFlowImporterTest` | Admin port access, SYN bursts, C2 beaconing jitter calculation (`<= 0.15`), CSV parsing |
| `AudioFrameBridgeTest` / `PcmResamplerTest` / `WhisperModuleTest` | 16 kHz mono PCM resampling, RIFF WAV header assembly, hallucination repetition suppression |

---

## 2. Instrumented Android Harnesses (`app/src/androidTest/`)

These tests require a physical `arm64-v8a` device (with staged ExecuTorch weights for native suites) and are **not** run inside the JVM suite:

| Instrumented Harness | Verification Target | Current Status |
| :--- | :--- | :--- |
| `EndToEndScamDetectionDeviceTest` | Device context inspection flow | Source present; requires attached Android target |
| `LlamaNativeInferenceDeviceTest` | Verifies pinned SHA-256 `LLAMA_3_2_1B_SPINQUANT` load & `LocalEvidenceExplainer` native output (`OTP` prompt check) | Hardware & provisioned weights required |
| `WhisperNativeInferenceDeviceTest` | Executes native `AsrModule` transcription on physical ARM64 hardware | Hardware & Whisper `.pte` assets required |
| `AgoraVoipDeviceTest` | Tests two-way Agora RTC room flow on device | Experimental; backend + peer device required |

---

## 3. Benchmarks & Metrics Policy

- **Deterministic Guards:** Static URL canonicalization, `SafetyBarrier` regex/contextual rules, and `NetworkAnomalyClassifier` run locally in JVM/device memory without network round-trips.
- **No Unverified AI Metrics Exported:** Because reliable end-to-end physical-device profiling for native Llama 3.2 1B token-per-second throughput and Whisper Word Error Rate (WER) across full multi-turn conversations remains pending, SuSagi Guardian does not publish generalized device tokens/sec, memory overhead, or detection accuracy percentages as established benchmarks.
- **Controlled UI Scenario Results:** In recorded Build D74 walk-through runs, the scripted *Speaker Guard SBI KYC* demonstration scored **LOW (5/100)** (`No scam indicators detected yet`), confirming ongoing heuristic/threshold tuning rather than overclaiming passive call interception accuracy.
