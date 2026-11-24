# SuSagi Guardian V2 — Release Manifest & Build Inventory
**Project:** SuSagi Guardian V2 (PS3 On-Device Threat, Phishing & Scam Detection)  
**Author & Contributor:** Anmol Singh (`Anmol-coder26`)  
**Build Date:** 24 November 2026

---

## 1. Primary Artifact Inventory

| Item | Details |
| :--- | :--- |
| **Package Name** | `com.guardian.app` |
| **Application Name** | SuSagi Guardian V2 |
| **Build Variant** | `debug` (pre-configured for challenge evaluation and testing) |
| **Release Artifact** | `susagi-guardian-v2.apk` |
| **Artifact Byte Size** | 137,989,585 bytes (131.60 MB) |
| **SHA-256 Checksum** | `028E77B94FB45FEE1E6E82048DB10F1075D4429FD2A1A8D1830F096C7FC21CC9` |
| **Min SDK** | 28 (Android 9.0 Pie) |
| **Target / Compile SDK**| 34 (Android 14) |
| **Source Commit SHA** | `5058a609b139477cf3ca39f97674e7d95cac9656` |
| **Git Release Tag** | `v2.0.0-final-release` |

---

## 2. Packaged Native Libraries & ABIs

The APK contains pre-compiled native runtimes targeting ARM64 mobile hardware:

* `lib/arm64-v8a/libexecutorch.so` (ExecuTorch Core Runtime with Arm NEON optimizations)
* `lib/arm64-v8a/libextension_data_loader.so` (ExecuTorch Memory-Mapped Asset Loader)
* `lib/arm64-v8a/libagora-rtc-sdk.so` (Real-Time Communication engine for VoIP audio capture)

---

## 3. Verified Functional Capabilities

| Subsystem | Execution Mode | Verification Status | Device Latency |
| :--- | :--- | :--- | :--- |
| **Message Shield** | 100% On-Device Heuristic + Regex | Verified on Android 14 Handset | < 3ms |
| **Link Shield** | 100% On-Device SafeBrowsing + Homoglyph | Verified on Android 14 Handset | < 2ms |
| **Payment Inspector**| 100% On-Device Reverse-Debit Guard | Verified on Android 14 Handset | < 1ms |
| **QR Shield** | 100% On-Device ZXing + Payload Analyzer | Verified on Android 14 Handset | < 15ms |
| **Incident History** | Encrypted Room DB on Handset | Verified on Android 14 Handset | < 5ms |
| **Voice Scam Radar** | Offline Synthetic Simulation Harness | Verified on Android 14 Handset | Real-time (100ms ticks) |
| **AI Assistant** | Hybrid: ExecuTorch Llama + On-Device Engine | Verified on Android 14 Handset | Streaming / 150ms |
| **VoIP Live Calling** | Agora RTC + LAN Token Server (Port 3001) | Experimental (Requires LAN Wi-Fi host) | Network dependent |

---

## 4. Known Boundaries & Limitations

1. **Live Agora VoIP Calling:** Requires the handset and token server to reside on the same Wi-Fi subnet (`192.168.29.x:3001`). If accessed via 4G cellular data, the integrated `Simulate Scammer Dialogue (Demo)` offline harness must be used.
2. **Generative LLM Weights:** Native ExecuTorch Llama 3.2 1B execution requires downloading the model weights via the in-app **Manage Models** interface. When weights are unprovisioned, the built-in on-device conversational engine provides instant answers.
3. **Debug Key Signing:** The APK is signed with the Android development debug key to allow frictionless sideloading on evaluators' test devices without Play Protect blocks.
