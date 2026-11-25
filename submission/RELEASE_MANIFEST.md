# SuSagi Guardian V2 — Build & Package Manifest

* **Application ID:** `com.susagi.guardian2` (`namespace com.guardian.app`)
* **Version:** `3.0.0` (`versionCode 3`, Build `D74`)
* **SDK Range:** `minSdk = 26`, `compileSdk = 35`, `targetSdk = 35`
* **Native ABI & Libraries:** `arm64-v8a` (`libexecutorch.so` v1.5.1, `libc++_shared.so`, `libbarhopper_v3.so`, `libagora-rtc-sdk.so`)
* **Automated JVM Baseline:** `318 / 318` passing across `51` XML suites (`12.368 s`)
* **Distribution Guidance (Part 19 Security Policy):** Legacy third-party Gofile/LAN APK links from pre-hygiene commits are retired. Build from clean source (`./gradlew assembleDebug`) and rotate historical Agora/cloud credentials in provider consoles prior to shared backend deployment.
