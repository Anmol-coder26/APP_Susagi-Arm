# Verified Test & Verification Evidence Summary

**Project:** SuSagi Guardian (`com.susagi.guardian2`, Version `3.0.0`, Build `D74`)  
**Verification Policy:** Automated host JVM tests and inspected physical handset behavior are reported separately. Hardware telemetry benchmarks not yet captured end-to-end are explicitly listed as unmeasured.

---

## 1. Host JVM Automated Test Suite (`./gradlew testDebugUnitTest`)
Measured directly across all **51 XML test reports** in `app/build/test-results/testDebugUnitTest/`:

- **Total Executed Tests:** **318**
- **Passed:** **318 / 318 (100%)**
- **Failures:** **0** · **Errors:** **0** · **Skipped:** **0**
- **Cumulative Runner Duration:** **12.368 seconds**

| Subsystem Group | Suites | Tests | Failures | Runner Time | Representative Verified Classes |
| :--- | :---: | :---: | :---: | :---: | :--- |
| Safety & Contextual Engine | 13 | 55 | 0 | 1.079 s | `SafetyBarrierTest`, `PrizeContextTest`, `ContextPairsTest`, `AdversarialBarrierTest`, `BarrierEvaluationTest`, `PaymentMutationTest` |
| Intelligence & Correlation | 13 | 101 | 0 | 1.664 s | `UnifiedThreatEngineTest`, `AstraDedicatedSecurityClassifierTest`, `IncidentCorrelationEngineTest`, `SecurityFlowIntegrationTest` |
| Audio, STT & Calling | 12 | 66 | 0 | 4.003 s | `AudioFrameBridgeTest`, `AudioFramePipelineTest`, `PcmResamplerTest`, `WhisperModuleTest`, `VoiceShieldPipelineTest`, `VoipCallingSafetyTest`, `LiveRiskEngineTest` |
| Link, QR, Browser & Evidence | 7 | 45 | 0 | 2.707 s | `LinkCheckSecurityTest`, `QrScannerTest`, `BrowserRoleManagerTest`, `IncidentHistoryStoreTest`, `EvidenceCaptureTest`, `RoleClassificationTest` |
| Edge AI & Model Management | 4 | 35 | 0 | 2.887 s | `ModelManagerTest`, `ConversationManagerTest`, `SuSagiAiDiagnosticsTest`, `SuSagiAssistantTest` |
| Network Anomaly Classifier | 2 | 16 | 0 | 0.028 s | `NetworkAnomalyClassifierTest`, `NetworkFlowImporterTest` |
| **Total Verified JVM Baseline** | **51** | **318** | **0** | **12.368 s** | Clean 100% JVM pass baseline |

---

## 2. Physical Device Evidence vs Explicit Open Gaps (Build D74)
- **Observed Passing on Vivo iQOO Z7 Pro 5G (MediaTek Dimensity 7200 / ARMv9-A / `arm64-v8a` / 79.17 GB Free):**
  - Home dashboard navigation, Link Shield canonical hostname extraction & external browser confirmation gate (`cybercrime.gov.in`).
  - CameraX + ML Kit local QR vision scan, Offline Demo A (Hindi contraband -> `HIGH`), Offline Demo B (UPI reverse-debit -> flagged `UNVERIFIED DIRECT UPI LINK`), Offline Demo H (CSV `c2_beaconing_telemetry.csv` -> `85/100 C2_BEACONING`).
  - Model Manager storage telemetry & `READY & VERIFIED` package status (`Llama 3.2 1B SpinQuant INT4` `1.08 GB`, `Whisper Tiny INT8` `116.58 MB`); pre-flight `ET12` + byte check preventing `SIGABRT` crash discovered on truncated `1.96 GB` file on Vivo V2141.
  - Short offline Llama assistant liveness turn (`"hi"` -> clean non-echo personal assistant response), Call Audit 1930 sheet, Threat Log (`http://192.168.1.100/sbi_kyc_update.apk` -> `HIGH`), and 10 regional UI scripts.
- **Honest Open Items (Not Claimed Resolved):**
  - Speaker Guard live SBI simulation returned `LOW (5/100)` (`No scam indicators detected yet`) during Build D74.
  - Live handset Llama tok/s, TTFT, active peak multi-model RAM, and physical Whisper live microphone WER/RTF are **unbenchmarked**.
  - Live two-device Agora VoIP calling remains **Experimental** (token server / rotated credentials required); Trusted Contact remote relay delivery remains **verification pending**.
