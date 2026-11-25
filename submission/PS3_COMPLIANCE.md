# SuSagi Guardian V2 — PS3 Compliance & Traceability Summary

Canonical traceability is maintained in `FINAL_SUBMISSION/04_Evidence/PS3_Requirement_Traceability.md` and `Table D.1` of `SuSagi_Guardian_Final_Technical_Report.pdf`.

| PS3 Requirement | SuSagi Implementation | Component(s) | Status |
| :--- | :--- | :--- | :--- |
| **PS3-R1: Phishing URL Detection** | Offline URI canonicalization, raw-IP, missing TLS & `.apk` checks gating browser launch | `LinkCheckActivity.kt`, `SafetyBarrier.kt` | **Device verified** |
| **PS3-R2: Contextual Scam Messages** | Unicode NFKC cleaning, advisory suppression, requested-action `(clause, nextClause)` rules | `SafetyBarrier.kt`, `PrizeIntent.java`, `FraudIntent.kt`, `CredentialIntent.kt` | **Device verified** |
| **PS3-R3: UPI & QR Payment Safety** | CameraX + ML Kit decode + `upi://pay` reverse-debit & duplicate-key inspection | `QrScannerActivity.kt`, `PaymentFields.kt` | **Device verified** |
| **PS3-R4: Arm Edge SLM Integration** | ExecuTorch `1.5.1` (`arm64-v8a` XNNPACK) + ET12/SHA-256 `ModelManager` + D74 echo sanitization | `ExecuTorchLlamaEngine.kt`, `ModelManager.kt`, `ModelManifest.kt` | **Implemented — verification pending** |
| **PS3-R5: Speech & Voice Extortion Defense** | 16 kHz PCM RMS-VAD + Whisper INT8 `AsrModule` + experimental Agora VoIP + Speaker Guard | `WhisperSttEngine.kt`, `AgoraEngine.kt`, `LiveRiskEngine.kt` | **Known issue / Experimental** |
| **PS3-R6: Network Anomaly Classification** | Statistical CSV batch port-scan, SYN & low-jitter C2 beaconing engine (`85/100` Demo H) | `NetworkAnomalyClassifier.kt`, `NetworkFlowImporter.kt` | **Device verified** (CSV) / **Planned** (Live `tun0`) |
| **PS3-R7: Local Privacy & Escalation** | Local Room incident logs, opt-in permissions, `tel:1930` / `cybercrime.gov.in` shortcuts, release key stripping | `IncidentHistoryStore.kt`, `AndroidManifest.xml`, `app/build.gradle.kts` | **Device verified** |
