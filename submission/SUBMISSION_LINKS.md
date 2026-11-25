# SuSagi Guardian V2 — Verified Submission Links
**Challenge:** Bharat AI-SoC Student Challenge 2026–27 · Problem Statement PS3  
**Team:** Breakthrough · **Institute:** SGSITS, Indore · **Team Leader:** Harsh Bamer · **Contributors:** Anmol Singh (`@Anmol-coder26`), Harsh Bamer

---

## 1. Core Deliverables

| Deliverable | Path / Location | Status | Notes |
| :--- | :--- | :--- | :--- |
| **Source Repository** | [https://github.com/Anmol-coder26/APP_Susagi-Arm](https://github.com/Anmol-coder26/APP_Susagi-Arm) | **Public (`main`)** | Competition-independent product showcase + source |
| **Official Demo Video** | `FINAL_SUBMISSION/01_Video/SUSAGI_GUARDIAN_OFFICIAL_SUBMISSION.mp4` | **Verified (`01:50.99`, 1080p)** | H.264 High / AAC 48 kHz; awaiting owner public YouTube/Drive upload |
| **Final Technical Report (PDF)** | `FINAL_SUBMISSION/02_Report/SuSagi_Guardian_Final_Technical_Report.pdf` | **Verified (33 pages)** | Includes Methodology, Results, Arm Hardware, and Optimization sections |
| **Final Technical Report (DOCX)** | `FINAL_SUBMISSION/02_Report/SuSagi_Guardian_Final_Technical_Report.docx` | **Editable Master** | Fill Team / Institution / Mentor on Cover Page prior to portal submit |
| **Feature Verification Matrix** | `FINAL_SUBMISSION/03_Repository/Feature_Verification_Matrix.md` | **Verified SSOT** | Consistent status categories across video, report, and README |

> **Security Note (Part 19):** Legacy third-party Gofile/LAN (`192.168.29.62`) APK mirrors created prior to credential hygiene refactoring are deprecated and omitted intentionally. Build cleanly from source using `./gradlew assembleDebug` (see [docs/INSTALLATION.md](docs/INSTALLATION.md)).

---

## 2. Verified Technical Identity

* **Application ID:** `com.susagi.guardian2` (`namespace com.guardian.app`)
* **Version Name:** `3.0.0` (`versionCode 3`, Build `D74`)
* **SDK Targets:** `minSdk = 26` (Android 8.0 Oreo), `compileSdk = 35` / `targetSdk = 35` (Android 15)
* **ExecuTorch Runtime:** `org.pytorch:executorch-android:1.5.1` (`arm64-v8a` XNNPACK CPU)
* **Automated Host JVM Tests:** **318 / 318 passing** (`51` XML suites, `0` failures, `12.368 s`)
