# Development Roadmap

This roadmap outlines upcoming engineering priorities for **SuSagi Guardian**, keeping clear separation between implemented subsystems, active hardening tasks, and planned future work. Dates are intentionally omitted pending hardware milestone verification.

---

## Phase A — Physical Device Stabilization & Calibration (Active Focus)

| Area | Current State | Planned Milestone |
| :--- | :--- | :--- |
| **Live Call & Speaker Guard Scoring** | Controlled SBI simulation returned `LOW (5/100)` in Build D74 UI | Align `LiveRiskAnalyzer` / `SafetyBarrier` live utterance buffering with verified contextual scoring thresholds |
| **On-Device Whisper Native Validation** | `WhisperSttEngine` + `WhisperModelManager` + INT8 `.pte` manifests ready | Measure latency, real-time factor (RTF), and Hindi/Hinglish/English WER across representative physical devices |
| **On-Device Llama Reasoning Quality** | Offline greeting liveness demonstrated; sanitizer/echo guards implemented | Benchmark structured JSON explanation reliability (`ThreatPromptBuilder`) across `SpinQuant INT4` builds and prune false refusals |
| **Trusted Contact Dispatch** | Config UI & HTTP `/alerts/trusted-contact` caller implemented | Add verified end-to-end delivery status receipts and offline SMS fallback workflow tested on physical handsets |

---

## Phase B — Communication & OS Integration Hardening

- **Stabilized VoIP Signaling:** Replace hardcoded fallback App ID/LAN defaults in `AgoraEngine` / `BuildConfig` with environment-clean runtime configuration and automated token-renewal resilience.
- **Credential Scrubbing & Signed Releases:** Rotate legacy development keys, decouple Firebase / Agora / Ably build dependencies cleanly for offline forks, and publish reproducible signed release APKs verified clean of credentials.
- **Local `VpnService` Flow Telemetry (`NetworkShield`):** Connect `NetworkAnomalyClassifier` to an optional local Android `VpnService` (`tun0`) metadata extractor (5-tuple headers without decrypting payloads), extending inspection beyond CSV test imports.
- **Default Browser / Link Shield Hardening:** Expand domain reputation heuristics, shortened-URL expansion hints (when online/consented), and multi-app intent compatibility testing across Android 13–15.

---

## Phase C — Multilingual Accessibility & Edge Ecosystem

- **Consistent UI & Rule Parity Across Regional Languages:** Align all 10 UI script selections (Hindi, Tamil, Telugu, Bengali, Marathi, Kannada, Malayalam, Punjabi, Gujarati, English) across Compose screens (`Strings.kt`), regional keyword tables (`RegionalScamKeywords`), and contextual rules.
- **Companion Gateway / Home Edge Node:** Explore porting `NetworkAnomalyClassifier`, `IncidentCorrelationEngine`, and DNS sinkholing to lightweight home ARM Linux nodes (see [PHASE2_RASPBERRY_PI_ROADMAP.md](hardware/PHASE2_RASPBERRY_PI_ROADMAP.md)).
