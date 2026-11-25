# Security & Privacy Architecture (Reference Mirror)

This document summarizes the verified runtime security and privacy posture declared in `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, and `SafetyBarrier.kt`. For the full updated policy, see [SECURITY.md](SECURITY.md) and root [SECURITY.md](../SECURITY.md).

## Key Verified Boundaries

1. **Offline Core Engines:** `SafetyBarrier`, `LinkCheckActivity.evaluateLinkSafety`, `ContextualScamRules`, `NetworkAnomalyClassifier`, and `ExecuTorchLlamaEngine` execute threat analysis locally without mandatory cloud transmission.
2. **Scoped Permissions:** `NotificationScannerService` reads only notification payload text when explicitly authorized via Android Notification Access settings. SuSagi does not bypass app sandboxes to read third-party encrypted chat databases.
3. **Optional Network Pathways:** Experimental VoIP signaling (`AgoraEngine`), Bhashini STT streaming, Firebase Cloud Messaging, Ably alerts, and backend trusted-contact forwarding (`TrustedContactAlertSender`) transmit network requests only when active and configured.
4. **User Consent Gates:** External navigation overlays require explicit user confirmation (`Confirm Navigation to External Browser`), and release builds strip client-embedded `GEMINI_API_KEY`, `BHASHINI_*`, and `ABLY_API_KEY` properties in `app/build.gradle.kts`.
