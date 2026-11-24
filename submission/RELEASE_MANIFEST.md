# SuSagi Guardian V2 — Release Manifest & Build Specification
**Project:** SuSagi Guardian V2 (Problem Statement PS3: On-Device Threat, Phishing & Scam Detection)  
**Lead Contributor:** Anmol Singh (`Anmol-coder26`)  
**Build Date:** 24 November 2026

---

## 1. Verified Binary Specification

| Metadata Field | Value | Verification Source |
| :--- | :--- | :--- |
| **Application ID** | `com.susagi.guardian2` | `app/build.gradle.kts` |
| **Namespace** | `com.guardian.app` | `app/build.gradle.kts` |
| **Version Code** | `3` | `app/build.gradle.kts` |
| **Version Name** | `3.0.0` | `app/build.gradle.kts` |
| **Min SDK** | `26` (Android 8.0 Oreo) | `app/build.gradle.kts` |
| **Target SDK** | `35` (Android 15) | `app/build.gradle.kts` |
| **Compile SDK** | `35` (Android 15) | `app/build.gradle.kts` |
| **ExecuTorch Runtime** | `org.pytorch:executorch-android:1.5.1` | `app/build.gradle.kts` |
| **Native Library (ARM64)** | `lib/arm64-v8a/libexecutorch.so` | Verified inside APK package |
| **Release Artifact** | `susagi-guardian-v2.apk` | Release candidate build |
| **Artifact Byte Size** | `137,989,585 bytes` (131.60 MB) | Physical file inspection |
| **SHA-256 Checksum** | `028E77B94FB45FEE1E6E82048DB10F1075D4429FD2A1A8D1830F096C7FC21CC9` | SHA-256 verification |
| **Verified Test Count** | **315 tests passed / 0 failures** | Gradle `testDebugUnitTest` |

---

## 2. Public Distribution Mirrors

* **Primary Cloud Link:** [https://gofile.io/d/0oZ6NJXm](https://gofile.io/d/0oZ6NJXm)
* **Secondary Mirror:** [https://gofile.io/d/rzFD5zrP](https://gofile.io/d/rzFD5zrP)
* **Source Repository:** [https://github.com/Anmol-coder26/APP_Susagi-Arm](https://github.com/Anmol-coder26/APP_Susagi-Arm)
* **Git Release Tag:** `v2.0.0-final-release`

---

## 3. Verified Native Libraries in APK

* `lib/arm64-v8a/libexecutorch.so` (ExecuTorch Core Runtime with Arm NEON acceleration)
* `lib/arm64-v8a/libagora-rtc-sdk.so` (Real-Time Communication engine for audio capture)
* `lib/arm64-v8a/libbarhopper_v3.so` (Google ML Kit Barcode / QR recognition library)
