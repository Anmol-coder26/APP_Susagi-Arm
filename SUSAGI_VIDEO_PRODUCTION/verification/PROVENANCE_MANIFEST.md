# 🛡️ SuSagi Video Production — Provenance & Verification Manifest

**Product:** SuSagi Guardian V2  
**Challenge:** Bharat AI-SoC Challenge 2026–27 (Problem Statement PS3)  
**Lead Contributor & Author:** Anmol Singh ([@Anmol-coder26](https://github.com/Anmol-coder26))  
**Application ID:** `com.susagi.guardian2`  
**Namespace:** `com.guardian.app`  
**Version:** `3.0.0` (versionCode `3`)  
**Build Identifier:** `Build D74`  
**Verified Git Commit:** `284c386`  
**Automated Unit Tests:** `318 / 318 passing` (51 test suites, 0 failures, 0 errors)  
**Production APK Checksum (SHA-256):** `395474996BD0119A086F5BE0BA96CE5EB7C71F3CAE13E8712A77A78DB710C00A`  

---

## 1. Absolute UI Authenticity Protocol

As mandated by **Master Directive 78**:
1. All application visuals appearing within the phone viewport (Layer 3) must originate strictly from verified Android screen recordings or physical device captures.
2. Recreations via HTML, CSS, React, Figma, Dribbble mockups, or AI-generated mobile interfaces are **strictly prohibited**.
3. Personal troubleshooting screenshots containing background music indicators or unhandled runtime prompts are **strictly excluded**.
4. Touch events, navigation transitions, and text entries must reflect actual user interaction.

---

## 2. Scene Footage & Provenance Register

| Scene | Demonstrated Capability | Required Interaction Input | Application Result | Recording Source File | Status |
|:---|:---|:---|:---|:---|:---|
| **Scene 01** (00:00–00:08) | Problem Statement | Fictional SMS, URL, and extortion call warnings (External graphics) | Brand introduction | `technical_animations/scene_01_problem_intro.mp4` | Ready |
| **Scene 02** (00:08–00:18) | App Launch & Dashboard | App launch, tab navigation (Home, Audit Log, Threats, Settings) | Responsive dashboard & status indicators | `raw_device_recordings/scene_02_dashboard_nav.mp4` | Pending Device Stream |
| **Scene 03** (00:18–00:38) | Contextual Threat Intelligence | **Input 1:** *"Sir, you have won a lottery of 10,000 rupees."*<br>**Input 2:** *"Pay 500 rupees first to collect your prize."* | **Verdict 1:** `REVIEW / SUSPICIOUS`<br>**Verdict 2:** `HIGH RISK / ADVANCE-FEE FRAUD` | `raw_device_recordings/scene_03_message_shield.mp4` | **Target Scene (Section 15)** |
| **Scene 04** (00:38–00:53) | Multichannel Protection | Link Shield inspection (`sbi-kyc-update.xyz`), QR payload inspection | Homoglyph/phishing block, Reverse-debit alert | `raw_device_recordings/scene_04_link_qr_shield.mp4` | Pending Device Stream |
| **Scene 05** (00:53–01:04) | Network Intelligence | Traffic flow analysis, connection frequency | Periodic beaconing anomaly flagged | `raw_device_recordings/scene_05_network_shield.mp4` | Pending Device Stream |
| **Scene 06** (01:04–01:25) | Intelligence Transformation | Model Manager view, local model readiness, AI query & response | Clean on-device reasoning (Build D74) | `raw_device_recordings/scene_06_model_assistant.mp4` | Pending Device Stream |
| **Scene 07** (01:25–01:43) | System Architecture | SafetyBarrier -> Contextual Engine -> ExecuTorch / Whisper | Architecture animation (External Layer 4) | `technical_animations/scene_07_architecture.mp4` | Ready |
| **Scene 08** (01:43–01:54) | Product Differentiation | Multi-channel defense montage | 100% offline airgap, explainable evidence | `raw_device_recordings/scene_08_montage.mp4` | Pending Device Stream |
| **Scene 09** (01:54–02:00) | Brand Closing | SuSagi Guardian Official Brand & Tagline | Final product identity | `technical_animations/scene_09_closing.mp4` | Ready |

---

## 3. Device Connection & Capture Protocol

When physical handset is connected via USB:
```bash
# 1. Verify ADB connection
adb devices -l

# 2. Verify installed package
adb shell dumpsys package com.susagi.guardian2 | grep -E "versionName|versionCode"

# 3. Check screen dimensions & density
adb shell wm size
adb shell wm density

# 4. Enable native visual touch feedback for recording
adb shell settings put system show_touches 1

# 5. Capture Scene 03 Message Shield (30 seconds)
adb shell screenrecord --bit-rate 8000000 --time-limit 30 /sdcard/message_shield_demo.mp4

# 6. Pull raw recording to production workspace
adb pull /sdcard/message_shield_demo.mp4 D:\SuSagi_Guardian_V2\SUSAGI_VIDEO_PRODUCTION\raw_device_recordings\scene_03_message_shield.mp4

# 7. Restore original touch settings
adb shell settings put system show_touches 0
```
