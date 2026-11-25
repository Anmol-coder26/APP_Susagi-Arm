# Installation & Developer Setup Guide

**Supported Android Targets:** Android 8.0 Oreo (API 26) through Android 15 (API 35)  
**Target ABI:** `arm64-v8a` (required for ExecuTorch native inference; deterministic JVM/UI features build across standard Android targets)

---

## 1. Prerequisites

- **JDK:** Java 17 (`sourceCompatibility = VERSION_17`, `targetCompatibility = VERSION_17` in `app/build.gradle.kts`)
- **Android Studio / CLI:** Android Gradle Plugin `8.5.2`, Kotlin `2.0.21`, Android SDK `compileSdk = 35`
- **Node.js (Optional):** Node 18+ only if running the companion `server/` proxy for experimental Agora token minting or Firebase contact lookup

---

## 2. Clone & Safe Local Configuration

```bash
git clone https://github.com/Anmol-coder26/APP_Susagi-Arm.git
cd APP_Susagi-Arm
```

Copy the sanitized configuration template:

```bash
cp local.properties.template local.properties
```

Edit `local.properties` and specify your local Android SDK path (Windows example):

```properties
sdk.dir=C\:\\Users\\YourUsername\\AppData\\Local\\Android\\Sdk
```

> [!IMPORTANT]
> Core offline shields (`SafetyBarrier`, `LinkCheckActivity` heuristics, CameraX/ML Kit QR parsing, `ContextualScamRules`, `NetworkAnomalyClassifier`, local `Room` incident history, and `ExecuTorch` native weight loading) operate **without** cloud API keys. Keep `GEMINI_API_KEY`, `AGORA_APP_ID`, `ABLY_API_KEY`, and `BHASHINI_*` fields empty unless testing optional integrations on your own private accounts.

If compiling with the Google Services Gradle plugin active (`com.google.gms.google-services`), provide your own non-committed `app/google-services.json` from Firebase Console, or configure an isolated flavor when building offline forks.

---

## 3. Gradle Build & Unit Verification

Run the complete local JVM test suite cleanly offline:

```bash
./gradlew testDebugUnitTest --offline
```

Assemble a development debug APK:

```bash
./gradlew assembleDebug
```

Install onto a USB/ADB-connected physical `arm64-v8a` Android handset:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Release Signing Configuration

Never commit release keystores or credentials. Copy `keystore.properties.template` to an untracked `keystore.properties` file or export `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, and `RELEASE_KEY_PASSWORD` before running `./gradlew assembleRelease`. Note that `app/build.gradle.kts` (`buildTypes.release`) explicitly zeroes client-side `GEMINI_API_KEY`, `BHASHINI_ULCA_API_KEY`, `BHASHINI_INFERENCE_API_KEY`, and `ABLY_API_KEY` build fields.

---

## 4. Optional Backend Service (`server/`)

Experimental features (Agora RTC token generation, Firebase contact lookup, Bhashini/Gemini proxy endpoints) expect the Express backend in `server/`:

```bash
cd server
cp .env.example .env
# Populate your own APP_ID, APP_CERTIFICATE, and PORT
npm install
node server.js
```

Configure `BACKEND_URL` in your local `local.properties` to point to your private host rather than modifying tracked build files.

---

## 5. Runtime Permissions Requested on Device

Depending on which optional shield modules you enable, SuSagi Guardian may prompt for:
- `CAMERA` — QR & barcode scanning (`QrScannerActivity`)
- `RECORD_AUDIO` / `FOREGROUND_SERVICE_MICROPHONE` — Speaker Guard and Voice Clip Threat Analyzer testing
- `BIND_NOTIFICATION_LISTENER_SERVICE` — Optional opt-in inspection of incoming message notifications
- `BIND_SCREENING_SERVICE` — Call screening framework registration
- `SYSTEM_ALERT_WINDOW` / `USE_FULL_SCREEN_INTENT` — Threat alert overlays
- `READ_CONTACTS` / `MANAGE_EXTERNAL_STORAGE` — Guardian directory lookup and external `.pte` weight discovery in `Downloads`
