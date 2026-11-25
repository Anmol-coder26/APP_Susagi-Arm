# Security & Privacy Boundaries

SuSagi Guardian prioritizes **local-first security analysis** while remaining transparent about permissions, optional cloud integrations, and Android platform constraints.

---

## 1. Local vs. Optional Network Processing

| Processing Mode | Components | External Network Dependency |
| :--- | :--- | :--- |
| **Offline Deterministic Core** | `SafetyBarrier`, `LinkCheckActivity.evaluateLinkSafety`, `ContextualScamRules`, `PrizeIntent`, `PaymentFields`, `NetworkAnomalyClassifier` | **None** — runs cleanly in Airplane Mode |
| **Optional Native Edge Models** | `ModelManager`, `ExecuTorchLlamaEngine`, `WhisperSttEngine` | **None at inference time** once user stages weights locally |
| **Optional Cloud / Relay Services** | `GeminiRoleClient`, `BhashiniSttClient`, `AgoraEngine`, `GuardianFirebaseMessagingService`, `ContactsManager`, `TrustedContactAlertSender` | **Requires network & credentials** via `BACKEND_URL` or local build properties |

SuSagi Guardian does **not** claim guaranteed scam prevention, zero false positives, or zero cloud dependency across all app screens. Core inspection warns users explicitly: *"Offline inspection cannot establish ownership, reputation or the destination after navigation."*

---

## 2. Declared Android Permissions (`AndroidManifest.xml`)

Every declared permission maps to a specific implemented component:

- `CAMERA` — CameraX live QR & barcode scanning (`QrScannerActivity`).
- `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS`, `BLUETOOTH_CONNECT`, `FOREGROUND_SERVICE_MICROPHONE` — Acoustic Voice Clip / Speaker Guard testing and VoIP capture.
- `BIND_NOTIFICATION_LISTENER_SERVICE` (`NotificationScannerService`) — Reads notification text **only after explicit user opt-in in Android Settings**; never reads encrypted databases of WhatsApp or messaging apps.
- `BIND_SCREENING_SERVICE` (`GuardianCallScreeningService`), `READ_PHONE_STATE`, `READ_PHONE_NUMBERS`, `ANSWER_PHONE_CALLS`, `CALL_PHONE`, `MANAGE_OWN_CALLS`, `FOREGROUND_SERVICE_PHONE_CALL` — Call screening framework & 1930 helpline dialer shortcuts.
- `SYSTEM_ALERT_WINDOW`, `USE_FULL_SCREEN_INTENT` — Presenting threat barrier warnings over external flows.
- `FOREGROUND_SERVICE_MEDIA_PROJECTION` — Explicit user-consented capture for incident evidence reports (`EvidenceCaptureService`).
- `READ_CONTACTS` — Guardian directory lookup (`GuardianContactsScreen`) with runtime rationale dialog.
- `READ_EXTERNAL_STORAGE` (`maxSdkVersion="32"`) & `MANAGE_EXTERNAL_STORAGE` — Discovering staged `.pte` model packages inside user `Downloads` folders.
- `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `VIBRATE` — Optional model transfer, notifications, and optional relay connectivity.

---

## 3. Release Hardening & Compromised Secret Rotation Policy

1. **Client Release Stripping:** `app/build.gradle.kts` (`buildTypes.release`) overrides `GEMINI_API_KEY`, `BHASHINI_ULCA_API_KEY`, `BHASHINI_INFERENCE_API_KEY`, and `ABLY_API_KEY` build fields to empty strings `""` so unrestricted client secrets are withheld from release APKs.
2. **Prior Secret Exposure Remediation:** Earlier development commits contained an Agora development `APP_ID` (`local.properties.template`, `AgoraEngine.kt`) and internal LAN defaults (`app/build.gradle.kts`). Treat previously exposed development identifiers as **compromised**. Removing a value from the latest commit does not purge git history or previously built binaries:
   - Rotate Agora App IDs / certificates and any Firebase/cloud keys in your provider console before deploying shared services.
   - Do not redistribute legacy prebuilt APKs from prior commits.
   - Keep `local.properties`, `keystore.properties`, `app/google-services.json`, and `server/.env` strictly untracked.
