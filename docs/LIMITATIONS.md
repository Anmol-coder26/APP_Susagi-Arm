# Current Limitations & Engineering Disclosures

SuSagi Guardian separates verified functionality from interfaces under active development or operating system restriction.

---

## 1. On-Device Generative LLM (`ExecuTorchLlamaEngine`)

- **Conversational Reliability & Echo Guard:** `ExecuTorchLlamaEngine` integrates `LlmModule` (`echo = false`) alongside `sanitizeLlamaOutput` and `isPromptEcho` defenses (`ExecuTorchLlamaEngine.kt`) specifically to prevent control-token leaks (`<|eot_id|>`) and system-prompt echoing seen on early mobile runs. General-purpose freeform reasoning quality on device (`Build D74`) has only been liveness-demonstrated via short greeting verification and remains under tuning.
- **Hardware & Storage Prerequisite:** Running `Llama 3.2 1B SpinQuant INT4` requires `~1.08 GB` disk storage (`llama_3_2_1b_xnnpack_spinquant.pte`) and sufficient free memory on `arm64-v8a`. When models are absent or invalid, the app automatically falls back to deterministic local rules.

---

## 2. Offline Speech Recognition (`WhisperSttEngine`)

- **Native Execution Validation Pending:** `WhisperAudioProcessor` (16 kHz mono PCM, RMS VAD, RIFF WAV framing), `WhisperDecoder`, and `WhisperModelManager` are implemented alongside `org.pytorch.executorch.extension.asr.AsrModule` bindings. Model readiness indicators reflect provisioned `.pte` assets on disk; end-to-end physical hardware transcription accuracy, multilingual dialect robustness, and speaker diarization on live phone audio require further device validation.

---

## 3. Experimental VoIP Calling & Acoustic Call Analysis

- **VoIP Room Interface (`Experimental`):** Agora RTC (`4.4.1`) and RTM (`2.2.1`) scaffolding plus room-code screens exist, but live bidirectional connectivity depends on an external token backend (`BACKEND_URL`), matching `AGORA_APP_ID` / `APP_CERTIFICATE` credentials, and two peer devices. Live multi-device calls are classified as experimental.
- **Stock Android Cellular Call Audio Block (OS Boundary):** On non-rooted stock Android (API 29+), `AudioSource.VOICE_CALL` uplink/downlink telephony streams are restricted to privileged system apps. Third-party speakerphone capture (`AudioRecord`) is affected by acoustic feedback and hardware mixer variations.
- **Simulation vs Live Threshold Calibration:** Speaker Guard's built-in staged scam scenarios remain **controlled test harnesses**. In observed Build D74 demonstration footage, the simulated SBI KYC script returned `LOW (5/100)` (`No scam indicators detected yet`), highlighting incomplete live-transcript risk calibration.

---

## 4. Trusted Contact Alert Delivery

- **Configuration vs Confirmed Dispatch:** `TrustedContactSettingsScreen` and `TrustedContactManager` persist recipient contact info locally in `SharedPreferences`. Actual dispatch (`TrustedContactAlertSender` & `VoipCallViewModel`) issues HTTP requests to `/alerts/trusted-contact` or `/alerts/family` on `BACKEND_URL` (or relies on dialer/intent helpers), so autonomous alert delivery requires a configured backend relay and lacks independent end-to-end delivery confirmation in offline mode.

---

## 5. Network Anomaly Analysis vs Live Packet Inspection

- **Imported Telemetry Focus (`NetworkShieldScreen`):** Android's non-root OS sandbox prevents apps from placing Wi-Fi or cellular adapters into promiscuous `pcap` mode to sniff traffic belonging to other applications. Accordingly, `NetworkAnomalyClassifier` and `NetworkFlowImporter` inspect **imported CSV flow records** and unit scenarios rather than passive live OS-wide packet capture. Live traffic monitoring would require activating a user-consented `VpnService` (`tun0`) pipeline or gateway deployment.

---

## 6. Android Link Interception Boundaries

- `LinkCheckActivity` intercepts links only when the user taps an `http`/`https` link and either sets SuSagi Guardian as default handler or picks it via Android's resolver (`ACTION_VIEW`) or shares text via `ACTION_SEND`. Links opened inside apps that force their own built-in WebViews or Custom Tabs bypass external Android Intent interception. Static inspection also cannot verify live DNS records, server-side redirects, or TLS certificates offline (`"Offline inspection cannot establish ownership, reputation or the destination after navigation"`).

---

## 7. Security & Credential Hygiene Notice

Earlier development iterations exposed an Agora development `APP_ID` in source/templates (`AgoraEngine.kt`, older `local.properties.template` history) and internal LAN IP defaults (`app/build.gradle.kts`). Treat previously committed development IDs/keys as **compromised**, rotate all external service credentials immediately before any production or hosted backend deployment, and never redistribute legacy APKs built before credential cleanup.
