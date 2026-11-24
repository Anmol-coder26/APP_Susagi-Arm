# SuSagi Guardian V2 — Real App Demonstration & Verification Guide
**Target Submission:** Arm AI-SoC Bharat Challenge 2026–27 (PS3: On-Device Threat, Phishing & Scam Detection)  
**Package Name:** `com.susagi.guardian2`  
**Git Baseline:** Commit `336a382` (Tracking `a36672a`) on `feature/gravity-product-calling`  
**Date:** 23 November 2026  

---

## 1. Verified Release Candidate APK
- **File Name:** `app-debug-verified-336a382.apk` (Copy preserved in this directory)
- **Primary Build Path:** `D:\SuSagi_Guardian_V2\app\build\outputs\apk\debug\app-debug.apk`
- **File Size:** `137,351,908` bytes
- **SHA-256:** `C8BDDC1A91F4BBE5133CFD0FDB7635D3228FBB26E56CFA57C15226A9EE5F61A9`
- **Unit Test Status:** 233 / 233 JVM unit tests passing offline (0 failures, 0 ignored).

---

## 2. Audio & VoIP Fixes Resolved in Build 336a382

### A. Live Microphone Recording & Voice Clip Analyzer (`VoiceClipScannerScreen`)
- **Microphone Access:** `RECORD_AUDIO` permission triggers live 16kHz mono `AudioRecord` thread.
- **Dynamic Sound Visualizer:** Real-time RMS audio equalizer bouncing with your voice volume.
- **Live Speech Transcription:** Android's on-device `SpeechRecognizer` transcribes spoken words directly into the text field in real-time.
- **Instant Scam Scoring:** Tapping "Stop & Analyze Audio" automatically passes transcript to `SafetyBarrier` and `UnifiedThreatEngine`.
- **One-Tap Quick Scenarios:** 4 pre-loaded scenario chips (Digital Arrest, Bank KYC, Task Scam, Benign Family Call) to test detection without speaking aloud.

### B. Live Call Speaker Guard (`CallRiskActivity`)
- Re-routed "Live Call Speaker Guard" on Home Dashboard to open `CallRiskActivity`.
- Auto-starts live speakerphone / microphone listening, live decibel meter, and real-time speech threat analysis.
- Includes "DEMO CALL SIMULATION" to test audio threat detection in one tap.

### C. Secure VoIP Calling & Radar (`VoipCallActivity` / `VoipCallScreen`)
- Added dedicated "Secure Two-Way VoIP Calling" card on Home Dashboard.
- Enabled native audio pipeline attachment (`enableAiPipeline = true`) to Agora.
- **Interactive Scammer Voice Stream Simulator:** Added a prominent **"Simulate Scammer Voice Stream"** button on the VoIP screen! Even if testing on a single device without another phone, you can tap it to watch a realistic 7-turn extortion call unfold in real time, see the Threat Radar jump to 92% (CRITICAL), detect digital arrest coercion, and trigger the emergency family alert.

---

## 3. How to Test on Your Phone

1. Transfer [app-debug-verified-336a382.apk](file:///D:/SuSagi_Guardian_V2/SUSAGI_REAL_APP_DEMO/app-debug-verified-336a382.apk) to your Android phone and install it.
2. **Test Voice Recording:**
   - Tap **Voice Clip Threat Analyzer** on Home Dashboard.
   - Tap **Record Audio Sample**.
   - Notice the microphone indicator, the pulsing red `REC 00:03` timer, and the animated green/cyan equalizer bars moving with your voice!
   - Speak: *"This is police. You are under digital arrest. Transfer 50000 rupees now."*
   - Words appear live. Tap **Stop & Analyze Audio** $\rightarrow$ Score 85+ (CRITICAL THREAT) is flagged!
3. **Test Live Call Speaker Guard:**
   - Tap **Live Call Speaker Guard** on Home Dashboard.
   - Microphone opens automatically and begins listening with real-time audio level ring.
4. **Test VoIP Call Defense:**
   - Tap **Secure Two-Way VoIP Calling** on Home Dashboard $\rightarrow$ Tap **START CALL AS HOST**.
   - On the VoIP call screen, tap **Simulate Scammer Voice Stream**.
   - Watch the conversational dialogue stream in, the Threat Radar spike to 92%, and scam badges populate!
