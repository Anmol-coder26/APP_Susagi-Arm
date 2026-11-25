import os

readme_content = """<div align="center">

<img src="assets/branding/susagi-logo.svg" alt="SuSagi Guardian" width="80">

# SuSagi Guardian

**Think before you click. Know before you trust.**

*A context-aware Android cybersecurity assistant with an expandable ARM64 edge AI architecture.*

<p>
  <img src="https://img.shields.io/badge/Android-8.0%E2%80%9315%20(API%2026%E2%80%9335)-059669?style=for-the-badge&logo=android&logoColor=white" alt="Android 8-15">
  <img src="https://img.shields.io/badge/ABI-arm64--v8a-0F172A?style=for-the-badge&logo=arm&logoColor=white" alt="ARM64-v8a">
  <img src="https://img.shields.io/badge/ExecuTorch-v1.5.1-0891B2?style=for-the-badge&logo=pytorch&logoColor=white" alt="ExecuTorch 1.5.1">
  <img src="https://img.shields.io/badge/JVM%20Tests-318%2F318%20Pass-10B981?style=for-the-badge" alt="318 Tests Passing">
</p>

[Product Demonstration](#product-demonstration) &nbsp;•&nbsp;
[Application Spotlight](#application-spotlight) &nbsp;•&nbsp;
[System Architecture](#system-architecture) &nbsp;•&nbsp;
[Core Capabilities](#core-capabilities) &nbsp;•&nbsp;
[On-Device Edge AI](#on-device-edge-ai) &nbsp;•&nbsp;
[Quick Start](#quick-start) &nbsp;•&nbsp;
[Team Breakthrough](#team-breakthrough--official-submission)

<br>

<img src="assets/branding/github_hero.png" alt="SuSagi Guardian Security Suite" width="96%">

</div>

---

## Why SuSagi Guardian?

Mobile cybercrime in India rarely relies on simple malware. Attackers exploit psychological urgency—combining **digital arrest extortion**, fake **KYC deactivation notices**, and **reverse-debit UPI cashback traps** with code-mixed Hinglish and disguised URLs. Traditional keyword filters over-trigger on benign bank notices (*"never share your OTP"*), while cloud AI assistants leak private communications and fail offline.

SuSagi Guardian (`com.susagi.guardian2`) bridges this gap through a **two-tier architecture** developed over **four months of focused engineering**:

<table>
  <tr>
    <td width="33%" valign="top">
      <h4>1. Deterministic Shields First</h4>
      <sub>Offline contextual regex, Unicode NFKC normalization, host canonicalization, CameraX QR parsing, and <code>upi://pay</code> validation execute locally in milliseconds with zero remote reliance.</sub>
    </td>
    <td width="33%" valign="top">
      <h4>2. Requested-Action Precision</h4>
      <sub>Rather than flagging isolated keywords like <i>"lottery"</i> or <i>"police"</i>, cross-clause rules differentiate benign alerts from live fee, OTP, or authority coercion.</sub>
    </td>
    <td width="33%" valign="top">
      <h4>3. Expandable ARM64 Edge AI</h4>
      <sub>Optional <code>ExecuTorch 1.5.1</code> packages (<code>Llama 3.2 1B SpinQuant</code> &amp; <code>Whisper INT8</code>) validate via pre-flight <code>ET12</code> header and SHA-256 checks without gating core shields.</sub>
    </td>
  </tr>
</table>

> [!NOTE]
> **Engineering Transparency:** Deterministic shields, `318/318` unit tests across 51 suites, and all primary UI surfaces (`Link Barrier`, `CameraX QR Vision`, `UPI Guard`, `Model Manager`, `Call Audit 1930`, `CSV NetFlow`) are verified on physical handset Build `D74`. Experimental two-device VoIP calling, live Speaker Guard calibration (`5/100` observed in staged SBI test), and live Whisper WER benchmarks remain transparently disclosed under [Current Boundaries & Honest Engineering](#current-boundaries--honest-engineering).

---

## Product Demonstration

<div align="center">

<a href="SUSAGI_FINAL_SUBMISSION/SuSagi_Original_Handset_Recording.mp4">
  <img src="assets/branding/video_thumbnail.png" alt="SuSagi Guardian Demonstration" width="86%">
</a>

<br><br>

### [▶ Play Authentic Handset Recording (02:28 • Direct MP4)](SUSAGI_FINAL_SUBMISSION/SuSagi_Original_Handset_Recording.mp4)
*Direct, unedited screen capture recorded on physical Vivo iQOO Z7 Pro 5G (`Build D74`). Fully web-optimized for instant playback.*

<br>

[**▶ Watch Master Walkthrough Film (01:51 • 1080p MP4)**](SUSAGI_FINAL_SUBMISSION/SuSagi_Guardian_Master_Film.mp4) &nbsp;&nbsp;|&nbsp;&nbsp;
[**Synchronized Subtitles (.srt)**](SUSAGI_FINAL_SUBMISSION/Subtitles.srt) &nbsp;&nbsp;|&nbsp;&nbsp;
[**Narration Script**](SUSAGI_FINAL_SUBMISSION/Narration_Script.txt) &nbsp;&nbsp;|&nbsp;&nbsp;
[**Shot-Timing Manifest**](FINAL_SUBMISSION/01_Video/Shot_Timing_Manifest.md)

<sub>Both MP4 files are web-optimized with faststart moov atoms (&lt;10 MB) for direct native streaming within GitHub. Personal identifiers, contact suggestions, and session tokens have been permanently redacted.</sub>

</div>

---

## Application Spotlight

Six genuine physical handset surfaces spanning incoming threat interception, contextual disambiguation, edge model management, and 1930 incident remediation:

<div align="center">
<table>
  <tr>
    <td width="33%" align="center" valign="top">
      <img src="assets/screenshots/gallery/04_link_shield.png" width="92%" alt="Link Shield"><br>
      <b>Link Safety Barrier</b><br>
      <sub>Intercepts <code>ACTION_VIEW</code>/<code>SEND</code> intents, canonicalizes tracking URLs, and gates external browsers.</sub>
    </td>
    <td width="33%" align="center" valign="top">
      <img src="assets/screenshots/gallery/05_upi_protection.png" width="92%" alt="UPI Protection"><br>
      <b>UPI Reverse-Debit Guard</b><br>
      <sub>Parses <code>upi://pay</code> parameters (<code>pa</code>, <code>am</code>, <code>cu</code>) and unmasks fake refund/cashback traps.</sub>
    </td>
    <td width="33%" align="center" valign="top">
      <img src="assets/screenshots/gallery/06_qr_inspection.png" width="92%" alt="QR Inspection"><br>
      <b>CameraX QR Vision</b><br>
      <sub>Decodes barcodes locally via Google ML Kit before launching external URLs or payment schemes.</sub>
    </td>
  </tr>
  <tr>
    <td width="33%" align="center" valign="top">
      <img src="assets/screenshots/gallery/03_contextual_assessment.png" width="92%" alt="Contextual Scam Assessment"><br>
      <b>Contextual Scam Engine</b><br>
      <sub>Runs offline Hindi digital-arrest, UPI reverse-debit, and benign banking disambiguation scenarios.</sub>
    </td>
    <td width="33%" align="center" valign="top">
      <img src="assets/screenshots/gallery/11_intelligence_tiers.png" width="92%" alt="Intelligence Mode Tiers"><br>
      <b>Arm64 Telemetry &amp; AI Tiers</b><br>
      <sub>Displays <code>arm64-v8a</code> telemetry alongside rule-only vs optional <code>ExecuTorch</code> comparisons.</sub>
    </td>
    <td width="33%" align="center" valign="top">
      <img src="assets/screenshots/gallery/15_incident_remediation.png" width="92%" alt="Incident Remediation"><br>
      <b>Incident History &amp; 1930 Escalation</b><br>
      <sub>Maintains an immutable threat log, 1930 cybercrime helpline dialing, and emergency contact alerts.</sub>
    </td>
  </tr>
</table>
</div>

---

## System Architecture

SuSagi enforces a strict **deterministic-first** pipeline. Incoming inputs pass through local parsing and contextual rule assessment before any optional on-device AI runtime is queried:

```mermaid
flowchart LR
    classDef core fill:#ECFDF5,stroke:#059669,stroke-width:2px,color:#0F172A;
    classDef dark fill:#0F172A,stroke:#10B981,stroke-width:2px,color:#F8FAFC;
    classDef opt fill:#ECFEFF,stroke:#0891B2,stroke-width:2px,stroke-dasharray: 5 4,color:#0F172A;
    classDef exp fill:#FFFBEB,stroke:#D97706,stroke-width:2px,stroke-dasharray: 5 4,color:#0F172A;

    subgraph Inputs ["1. Input Channels"]
        U["URLs & Share Intents<br/>(ACTION_VIEW / SEND)"]:::core
        Q["CameraX QR / UPI<br/>(ML Kit 17.2.0)"]:::core
        M["Messages & Permitted<br/>Notifications"]:::core
        N["Imported NetFlow CSV<br/>(NetworkFlowImporter)"]:::core
        V["Voice / VoIP Audio<br/>(16kHz PCM / Agora Exp.)"]:::exp
    end

    subgraph Engine ["2. Deterministic & Contextual Core"]
        LB["LinkCheckActivity &<br/>PaymentFields"]:::core
        SB["SafetyBarrier &<br/>ContextualScamRules"]:::core
        NC["NetworkAnomalyClassifier"]:::core
        VAD["WhisperAudioProcessor<br/>(RMS VAD + RIFF WAV)"]:::exp
    end

    subgraph Output ["3. Evidence, Assessment & Optional AI"]
        UT["UnifiedThreatEngine &<br/>Risk Precedence"]:::dark
        ST["IncidentHistory &<br/>Call Audit (1930 / PDF)"]:::core
        MM["ModelManager<br/>(ET12 + SHA-256 Gate)"]:::opt
        LL["ExecuTorchLlamaEngine<br/>(Llama 3.2 1B Opt.)"]:::opt
        WH["WhisperSttEngine<br/>(ExecuTorch AsrModule)"]:::exp
    end

    U --> LB
    Q --> LB
    M --> SB
    N --> NC
    V -. "PCM frames" .-> VAD

    LB --> UT
    SB --> UT
    NC --> UT
    UT --> ST

    MM -. "Validated .pte" .-> LL
    MM -. "Validated .pte" .-> WH
    VAD -. "WAV segment" .-> WH
    WH -. "Transcript" .-> SB
    UT -. "Structured Evidence" .-> LL
    LL -. "Enriched Advice" .-> ST
```

<details>
<summary><b>View High-Resolution Architecture Diagram</b></summary>

<br>

<p align="center">
  <img src="assets/diagrams/system_architecture_d89.png" alt="SuSagi Guardian Architecture Diagram" width="92%">
</p>

</details>

---

## Core Capabilities

Status follows strict engineering evidence: **Device verified** (tested end-to-end on handset) · **Implemented — verification pending** (source & JVM tests pass; physical device run pending) · **Experimental** (active prototype) · **Known issue**.

| Module | Core Functionality | Primary Component | Device Verification Status |
| :--- | :--- | :--- | :--- |
| **Link Shield** | Detects raw IPs, Punycode, brand spoofs, URL shorteners, and disguised `.apk` links | `LinkCheckActivity.kt`, `SafetyBarrier.kt` | **Device verified** |
| **CameraX QR Vision** | Scans QR codes locally; inspects decoded endpoints prior to navigation | `QrScannerActivity.kt`, ML Kit `17.2.0` | **Device verified** |
| **UPI Protection** | Validates `upi://pay` (`pa`, `pn`, `am`, `cu`) and flags reverse-debit refund scams | `PaymentFields.kt`, `SafetyBarrier.kt` | **Device verified** |
| **Contextual Scam Engine** | Disambiguates benign alerts from fee, OTP, and digital-arrest coercion | `SafetyBarrier.kt`, `ContextualScamRules.kt` | **Device verified** |
| **Message & Notification Scan** | Inspects pasted messages and opt-in status notifications (zero DB access) | `MessageScanScreen.kt`, `NotificationScannerService.kt` | **Implemented — verification pending** |
| **Call Audit & 1930 Helpline** | Logs threat assessments, dials National Cybercrime Helpline 1930, exports records | `IncidentHistoryStore.kt`, `CallHistoryEntity.kt` | **Device verified** |
| **Network Flow Intelligence** | Analyzes imported CSV NetFlow for admin ports, SYN floods, and C2 beaconing | `NetworkAnomalyClassifier.kt`, `NetworkFlowImporter.kt` | **Device verified** (CSV) |
| **Edge AI Model Manager** | Enforces `ET12` FlatBuffer magic, byte size bounds, and SHA-256 integrity | `ModelManager.kt`, `ModelManifest.kt` | **Device verified** |
| **On-Device Llama Assistant** | ExecuTorch `LlmModule` reasoning with echo sanitization | `ExecuTorchLlamaEngine.kt`, `ConversationManager.kt` | **Implemented — verification pending** |
| **Offline Whisper STT** | 16 kHz PCM RMS-VAD framing + ExecuTorch `AsrModule` transcription | `WhisperSttEngine.kt`, `WhisperAudioProcessor.kt` | **Implemented — verification pending** |
| **Speaker Guard Live Radar** | Monitors speakerphone audio; evaluates live conversation risk | `CallProtectionService.kt`, `LiveRiskEngine.kt` | **Known issue** (`5/100` score in Build D74) |
| **VoIP Secure Calling** | Room-code encrypted audio backed by Agora RTC/RTM channels | `AgoraEngine.kt`, `VoipCallViewModel.kt` | **Experimental** (requires backend token relay) |

---

## On-Device Edge AI

SuSagi Guardian incorporates **ExecuTorch 1.5.1** (`arm64-v8a`) to execute quantized language and speech models locally without leaking private conversations to cloud servers:

1. **Llama 3.2 1B Instruct (`SpinQuant INT4 XNNPACK`):**
   - Packaged as a ~`1.08 GB` `.pte` model file with a ~`9.45 MB` `tokenizer.json`.
   - Loaded lazily through `LlmModule(..., 0.7f)` with `resetContext()` and automated prompt-echo filtering (`sanitizeLlamaOutput()`).
   - Purely optional: deterministic shields function independently with zero dependency on model availability.

2. **Whisper Speech-to-Text (`INT8 XNNPACK`):**
   - Real-time RMS voice-activity detection (VAD) slices 16 kHz mono PCM into contiguous speech utterances (`0.8 s–5.0 s`).
   - Utterances are wrapped in standard 44-byte RIFF WAV headers and processed by ExecuTorch `AsrModule` (~`116.58 MB` Tiny / ~`395.05 MB` Small).

3. **Pre-Flight Model Integrity Gate (`ModelManager.kt`):**
   - Validates `ET12` magic bytes at byte offsets 4 and 0, verifies file size against expected limits (±50 MB), and checks SHA-256 digests.
   - Truncated or corrupted downloads transition cleanly to `MODEL_INVALID` without causing native `SIGABRT` crashes.

<p align="center">
  <img src="assets/diagrams/model_lifecycle_d89.png" alt="Expandable Model Lifecycle and Integrity Gate" width="92%">
</p>

---

## Quick Start

### Build Prerequisites
- Android Studio Ladybug (2024.2.1+) or command-line Gradle
- Android SDK 26 to 35 (Android 8.0 to Android 15)
- Physical ARM64 device (`arm64-v8a`) recommended for hardware validation

### 1. Clone & Set Up SDK
```bash
git clone https://github.com/Anmol-coder26/APP_Susagi-Arm.git
cd APP_Susagi-Arm
cp local.properties.template local.properties
# Edit local.properties and set sdk.dir to your Android SDK path
```

### 2. Run Verification Tests
All 318 deterministic unit tests run completely offline:
```bash
./gradlew testDebugUnitTest --offline
```

### 3. Assemble Debug APK
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

*Note: Core shields require no external API keys. Optional keys (`GEMINI_API_KEY`, `AGORA_APP_ID`, `ABLY_API_KEY`) may be left blank for offline usage.*

---

## Current Boundaries & Honest Engineering

| Area | Current Boundary | Next Milestone |
| :--- | :--- | :--- |
| **Llama Inference** | Short offline greeting verified; prompt-echo guard active (`sanitizeLlamaOutput`); throughput unmeasured | Profile quantized `SpinQuant INT4` tokens/sec &amp; benchmark multi-turn context |
| **Whisper STT** | 16 kHz WAV framing &amp; VAD pass unit tests; Tiny INT8 staged; physical microphone WER unbenchmarked | Benchmark noisy Hindi/Hinglish speech on physical device microphone |
| **Speaker Guard &amp; VoIP** | Staged SBI simulation returned `LOW (5/100)` in Build D74; two-device VoIP requires backend token relay | Calibrate live risk thresholds against `SafetyBarrier`; deploy secure token server |
| **Trusted Contact Alerts** | Emergency contact UI saves locally; dispatches alert JSON to `/alerts`; remote SMS unverified | Implement delivery confirmation and opt-in offline SMS fallback |
| **Network Inspection** | Analyzes imported CSV NetFlow batches (`85/100 C2_BEACONING` verified in Demo H) | Implement opt-in local `VpnService` (`tun0`) metadata collector |

---

## Team Breakthrough • Official Submission

**Bharat AI-SoC Student Challenge 2026–27**  
**Problem Statement:** PS3 — On-Device Threat, Phishing and Scam Detection  
**Institute:** Shri G. S. Institute of Technology and Science (SGSITS), Indore  

- **Team Name:** Breakthrough
- **Team Leader:** Harsh Bamer
- **Core Contributor & Lead Engineer:** Anmol Singh ([@Anmol-coder26](https://github.com/Anmol-coder26))

### Official Deliverables
- [**Final Technical Report (PDF • 32 Pages)**](SUSAGI_FINAL_SUBMISSION/SuSagi_Guardian_Final_Technical_Report.pdf)
- [**Editable Technical Report (DOCX)**](SUSAGI_FINAL_SUBMISSION/SuSagi_Guardian_Final_Technical_Report.docx)
- [**Official Demonstration Video (Direct MP4)**](SUSAGI_FINAL_SUBMISSION/SuSagi_Original_Handset_Recording.mp4)
- [**Official Submission Bundle**](FINAL_SUBMISSION/05_Submission/Submission_Information.txt) • [**Feature Verification Matrix**](FINAL_SUBMISSION/03_Repository/Feature_Verification_Matrix.md)
"""

with open(r'D:\SuSagi_Guardian_V2\README.md', 'w', encoding='utf-8') as f:
    f.write(readme_content)

print("Successfully written clean README.md")
