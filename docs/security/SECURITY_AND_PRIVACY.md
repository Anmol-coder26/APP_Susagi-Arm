# SuSagi Guardian — Security, Privacy & Compliance Architecture

**Problem Statement:** PS3 — On-Device Threat, Phishing, and Scam Detection  
**Privacy Posture:** Zero-Cloud, Zero-Telemetry, 100% On-Device Processing  
**Regulatory Alignment:** Digital Personal Data Protection (DPDP) Act 2023 (India) & CERT-In Guidelines

---

## 1. Core Security & Privacy Philosophy

Traditional cybersecurity and anti-fraud mobile applications collect extensive telemetry from user devices—including call logs, complete SMS messages, browsing history, and network destination endpoints—and upload this data to centralized corporate cloud servers for analysis.

This paradigm introduces catastrophic privacy risks:
1. **Confidentiality Breach:** Personal financial transactions, two-factor authentication OTPs, medical notices, and personal communications reside on third-party cloud infrastructure.
2. **Regulatory Non-Compliance:** Uncontrolled cloud exfiltration violates Chapter II (Obligations of Data Fiduciary) of India's **Digital Personal Data Protection (DPDP) Act 2023**.
3. **API Key Compromise:** Reverse-engineering client APKs frequently reveals embedded cloud API tokens, exposing backends to unauthorized access and billing exhaustion.

**SuSagi Guardian enforces a strict Zero-Cloud, Zero-Telemetry architecture.** All intelligence models, heuristic rules, and correlation graphs operate entirely within the local smartphone sandbox.

---

## 2. Zero-Telemetry & Air-Gapped Operation

```
+-----------------------------------------------------------------------------------+
|                        LOCAL ANDROID APPLICATION SANDBOX                          |
|                       (/data/user/0/com.guardian.app/)                            |
|                                                                                   |
|  [SMS Ingestion]  --> [Rule Engine]     --> [Correlation Graph]                   |
|  [URL Intent]     --> [Punycode Parser] --> [Risk Scorer]                         |
|  [Flow Telemetry] --> [Flow Window]     --> [ExecuTorch Llama-3.2 INT4]           |
|                                                     |                             |
|                                                     v                             |
|                                             [Compose Local UI]                    |
+-----------------------------------------------------------------------------------+
                                      |
                     [PHYSICAL HARDWARE BOUNDARY]
                                      |
                                      X  <--- ZERO OUTBOUND TELEMETRY (Blocked)
                                      |
                                      v
                             [PUBLIC INTERNET]
```

### Key Guarantees
- **No Analytics / Tracking SDKs:** The application binary contains zero analytics libraries (no Firebase Analytics, Google Analytics, Adjust, or AppsFlyer).
- **Ephemeral In-Memory Processing:** Incoming SMS messages and URL strings are evaluated in volatile memory and immediately discarded. No unencrypted user communications are persisted to disk.
- **Offline Integrity:** All core detection modules pass 100% of automated tests with the Gradle `--offline` flag active.

---

## 3. Android Permission Minimization

The application requests only the minimal set of Android runtime permissions necessary to protect the user:

### Table 1: Android Runtime Permission Analysis

| Permission | Technical Purpose | Privacy Safeguard & Isolation |
| :--- | :--- | :--- |
| `android.permission.RECEIVE_SMS` | Required to intercept incoming fraud messages in real-time before user interaction. | Processed ephemerally in RAM. Message text is never saved to files or SQLite databases. |
| `android.permission.INTERNET` | Required exclusively for local WebRTC / Agora audio testing sessions. | Detection modules (`UnifiedThreatEngine`, `NetworkAnomalyClassifier`, `ExecuTorchLlamaEngine`) contain zero network HTTP clients. |
| `android.permission.RECORD_AUDIO` | Required exclusively for interactive voice call simulation & Whisper STT demonstration. | Audio buffers are processed in temporary ring buffers and overwritten immediately. |
| `android.permission.POST_NOTIFICATIONS` | Alerts user when a high-severity threat is detected in the background. | Alerts contain defensive advice; no sensitive message content is shown on the lock screen. |

**Permissions Explicitly Prohibited:**
- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE`: No access to user photos, documents, or personal media.
- `READ_CONTACTS`: No harvesting of address books.
- `ACCESS_FINE_LOCATION`: Zero geolocation tracking.

---

## 4. Secret & Credential Safety Architecture

1. **Zero Hardcoded Secrets in APK:**
   - The production APK contains zero embedded cloud LLM API keys (no OpenAI, Anthropic, or external Gemini keys embedded in bytecode).
   - All machine learning inference executes through native ExecuTorch C++ libraries using local weights.
2. **Isolated Agora Backend:**
   - Agora WebRTC credentials (App ID and App Certificate) reside strictly in the local Node.js token server (`server/.env`).
   - The token server generates short-lived RTC tokens (24-hour expiration) on demand, preventing permanent key compromise.

---

## 5. Memory Safety & Native Crash Prevention

- **FlatBuffer Header Verification:** `ModelManager.kt` verifies the `ET12` magic byte sequence before JNI handoff, preventing malformed model files from causing native buffer overflows or memory corruption in `libexecutorch.so`.
- **Heap Segregation:** Native C++ allocations in ExecuTorch are bounded and isolated from the JVM runtime heap, preventing garbage collection stalls and out-of-memory cascades.
- **Constant-Time Regex Matching:** Regular expression patterns in `UnifiedThreatEngine` are constructed without unbounded catastrophic backtracking (`(a+)+`), preventing Denial of Service (ReDoS) attacks from adversarial SMS inputs.
