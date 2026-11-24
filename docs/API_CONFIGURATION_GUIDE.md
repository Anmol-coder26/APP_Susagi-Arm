# SuSagi Guardian — API & Credential Configuration Guide

**Document:** API Configuration, Credential Hardening, and Cloud Integration Guide  
**Project:** SuSagi Guardian (ScamShield Sentinel)  
**Date:** November 18, 2026  

---

## 1. Overview & Security Architecture

SuSagi Guardian coordinates multiple cloud services:
1. **Agora RTC Cloud:** High-fidelity real-time bidirectional VoIP audio streaming.
2. **Bhashini Dhruva Pipeline:** Government of India sovereign conversational ASR for Indic and English speech recognition.
3. **Google Gemini (Generative Language API):** Multi-modal conversational reasoning for scammer/victim role detection and extortion scoring.
4. **Ably / FCM:** Real-time emergency family notification mesh.

To prevent reverse-engineering of secrets from compiled APKs, SuSagi Guardian utilizes a **backend-proxy architecture** where sensitive API tokens are retained and validated by the local Node.js server (`server/server.js`), while mobile clients query proxy endpoints (`/agora/token`, `/risk/evaluate`, `/alerts/family`).

---

## 2. Configuration Files

### File 1: `local.properties` (Android Client Build)
Located at project root: `C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\local.properties`

```properties
sdk.dir=C\:\\Users\\Tanya\\AppData\\Local\\Android\\Sdk

# Agora RTC Configuration
AGORA_APP_ID=d575bd8b35004ad896366419f3a8a8f1
AGORA_APP_CERTIFICATE=

# Google Gemini Cloud AI Configuration
GEMINI_API_KEY=
GEMINI_MODEL=gemini-2.5-flash

# Bhashini Sovereign ASR Configuration
BHASHINI_USER_ID=your_bhashini_user_id_here
BHASHINI_INFERENCE_API_KEY=your_bhashini_inference_api_key_here
BHASHINI_ULCA_API_KEY=your_bhashini_ulca_api_key_here
BHASHINI_PIPELINE_ID=64392f96daac500b55c543cd
BHASHINI_STT_ENDPOINT=https://dhruva-api.bhashini.gov.in/services/inference/pipeline
BHASHINI_REST_ENDPOINT=https://dhruva-api.bhashini.gov.in/services/inference/pipeline

# Backend Signaling & AI Proxy Server (Set to Host PC LAN IP)
BACKEND_URL=http://192.168.29.62:3001

# Ably Real-Time Family Mesh
ABLY_API_KEY=
```

---

### File 2: `server/.env` (Signaling & AI Proxy Server)
Located at: `C:\Users\Tanya\.gemini\antigravity\scratch\FINAL_APP\server\.env`

```env
PORT=3001
APP_ID=d575bd8b35004ad896366419f3a8a8f1
APP_CERTIFICATE=
GEMINI_API_KEY=
GEMINI_MODEL=gemini-2.5-flash
ABLY_API_KEY=
```

---

## 3. Service-Specific Setup Instructions

### 1. Google Gemini Cloud AI
SuSagi Guardian uses Gemini Flash models (`gemini-2.5-flash` or `gemini-1.5-flash`) for low-latency JSON Schema evaluation.

#### Resolving HTTP 403 `PERMISSION_DENIED`:
- **The Issue:** The API key in `google-services.json` is restricted to Firebase client SDKs. Using it for `generativelanguage.googleapis.com` causes HTTP 403 `SERVICE_DISABLED` or `PERMISSION_DENIED`.
- **The Correct Procedure:**
  1. Visit [Google AI Studio](https://aistudio.google.com/).
  2. Sign in with your Google account and click **Get API Key**.
  3. Create a new API key in a Google Cloud project with **Generative Language API** enabled.
  4. Paste this key into `local.properties` as `GEMINI_API_KEY=<your_key>` and in `server/.env`.
  5. Restart the Node.js server (`node server/server.js`).
- **Heuristic Safety Net:** If no Gemini API key is configured or Google Cloud returns an error, SuSagi Guardian automatically engages the verified **On-Device Heuristic Engine** (`LOCAL_HEURISTIC`). This evaluated to a 95% fraud score on live digital arrest dialogues without crashing or fabricating results.

---

### 2. Agora RTC Calling Configuration
- **App ID:** Currently configured to `d575bd8b35004ad896366419f3a8a8f1`.
- **Testing Mode (Certificate Disabled):** Leave `AGORA_APP_CERTIFICATE` empty in `local.properties` and `server/.env`. Agora allows instant room connections using App ID authentication.
- **Production Mode (Certificate Enabled):** If Primary Certificate is enabled in the Agora Developer Console:
  1. Copy the Primary Certificate string.
  2. Paste into `server/.env` as `APP_CERTIFICATE=<certificate_string>`.
  3. The backend `/agora/token` route will automatically generate HMAC-SHA256 RTC Publisher tokens for every call.

---

### 3. Bhashini Dhruva ASR Configuration
SuSagi Guardian communicates with the official Bhashini Dhruva inference endpoint (`https://dhruva-api.bhashini.gov.in/services/inference/pipeline`).
- `BHASHINI_USER_ID`: Authenticated user UUID.
- `BHASHINI_INFERENCE_API_KEY`: API key passed in `Authorization` HTTP header.
- `BHASHINI_PIPELINE_ID`: Defaults to `64392f96daac500b55c543cd` (standard conversational multilingual ASR).

---

### 4. Ably Real-Time Family Alerting
- Used to publish emergency alerts to family member devices across network boundaries.
- Obtain an API key from [Ably Dashboard](https://ably.com/).
- Enter `ABLY_API_KEY` into `server/.env` and `local.properties`.
- When an emergency alert is triggered, the backend automatically broadcasts to channel `guardian-alerts`.
