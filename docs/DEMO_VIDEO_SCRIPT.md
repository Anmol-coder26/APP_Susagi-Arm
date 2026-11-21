# SuSagi Guardian — Demonstration Video Production Script
## Bharat AI-SoC Challenge 2026–27 | PS3 Submission Video (Target: 4-5 Minutes)

---

## Technical Setup Before Recording
1. **Device:** Vivo I2221 (MediaTek Dimensity 7200 / 8GB RAM / Android 14).
2. **Connectivity:** Airplane Mode ON (WiFi OFF, Mobile Data OFF) to visually prove 100% offline capability.
3. **Application:** SuSagi Guardian installed and running.
4. **Language:** English UI with Hindi/Tamil translation toggles demonstrated.

---

## Scene-by-Scene Script

### Scene 1: Title & The Cyber Fraud Crisis in India (0:00 - 0:45)
- **Visual:** Title card showing *SuSagi Guardian: Edge AI Cybersecurity for Bharat*. Cut to presenter holding the physical Vivo smartphone.
- **Presenter (Voiceover / On Camera):**
  > "Namaste and welcome. In India today, millions of citizens are targeted by ruthless digital fraud syndicates—from fake Digital Arrest warrants by impersonators claiming to be CBI officers, to electricity bill cutoff threats and bank KYC account suspensions.
  >
  > Most mobile security apps upload your private SMS and browsing history to the cloud, failing completely when internet is lost and violating your personal privacy.
  >
  > Today, we present **SuSagi Guardian**—the first 100% on-device, offline-first personal cybersecurity sentinel built specifically for Arm edge hardware under the Bharat AI-SoC Challenge Problem Statement 3."

---

### Scene 2: Demo 1 — Offline SMS Threat Shield (0:45 - 1:30)
- **Visual:** Close-up of phone screen. Notice the **Airplane Mode** icon clearly visible in the Android status bar. The presenter opens Message Shield.
- **Action:** Paste a real scam message into the analyzer:
  *`"URGENT: CBI New Delhi has issued a non-bailable arrest warrant against you for illegal money laundering. Connect immediately on Skype id cbi.officer.in to avoid physical detention."`*
- **Visual:** Taps "Analyze Threat". Within 3 milliseconds, a vibrant Red **CRITICAL ALERT** card pops up.
- **Presenter:**
  > "Watch this. Notice the device is completely in Airplane Mode. In under three milliseconds on the Arm efficiency cores, SuSagi Guardian intercepts this Digital Arrest extortion pattern.
  >
  > It explains in plain English—or Hindi and Tamil with one tap—that law enforcement agencies never issue warrants or interrogate citizens over Skype or WhatsApp. It also surfaces an immediate one-touch button to dial the 1930 Cyber Fraud Helpline."

---

### Scene 3: Demo 2 — Offline Phishing Link Shield (1:30 - 2:15)
- **Visual:** Presenter switches to the **Link Shield** tab.
- **Action:** Enters a sophisticated phishing link with deceptive user credentials and a malicious APK payload:
  *`"https://onlinesbi.sbi@verification-portal-kyc.top/sbi_yono_update.apk"`*
- **Visual:** Taps "Check Link". The screen instantly flags **CRITICAL PHISHING & MALWARE PAYLOAD**.
- **Presenter:**
  > "Phishing syndicates often disguise links using deceptive user authentication prefixes like 'onlinesbi.sbi@' pointing to an offshore domain, combined with direct APK download payloads.
  >
  > SuSagi Guardian's offline heuristic engine breaks down the URL authority, decodes homoglyphs, and warns the user that this link will attempt to sideload an untrusted Android package designed to steal OTPs."

---

### Scene 4: Demo 3 — Offline Network Anomaly Sentinel (2:15 - 3:00)
- **Visual:** Presenter navigates to the **Network Sentinel** interface.
- **Action:** Feeds a batch of recent flow telemetry representing background network events.
- **Visual:** The classifier flags two distinct intrusions:
  1. *Vertical Port Scan* (20 consecutive ports probed on LAN).
  2. *Periodic C2 Beaconing* (Heartbeat connections with jitter $J = 0.006$ every 15 seconds).
- **Presenter:**
  > "Requirement 1 of Problem Statement 3 demands on-device network intrusion detection. Here, SuSagi Guardian processes 50 network flow records in just 3.4 milliseconds.
  >
  > Without inspecting unencrypted payload content or invading user privacy, our statistical classifier identifies an automated C2 beacon—a telltale sign that a malicious background service is checking in with an external server."

---

### Scene 5: Demo 4 — Cross-Channel Correlation & Arm AI Reasoning (3:00 - 3:50)
- **Visual:** The **Threat HUD** aggregates the separate events.
- **Action:** The presenter opens the **Incident Graph View**.
- **Visual:** The SMS alert, the URL visit, and the network beacon to `198.51.100.42` are connected into a single **Consolidated Multi-Vector Incident**.
- **Presenter:**
  > "This is SuSagi Guardian's most powerful breakthrough: **Cross-Channel Threat Correlation**.
  >
  > An SMS alone might seem like spam. A network connection might seem like normal traffic. But when our correlation engine notices that the link received via SMS resolves to the exact same external IP address receiving C2 heartbeats within a 1-hour window, it unifies them into a single Critical Campaign.
  >
  > Running on the Dimensity 7200's Cortex-A715 cores, our quantized Llama-3.2 model synthesizes a complete incident briefing and generates an automated complaint draft ready for cybercrime.gov.in."

---

### Scene 6: Demo 5 — Hardware Efficiency & Phase 2 Vision (3:50 - 4:30)
- **Visual:** Split screen showing Android Studio Profiler metrics:
  - RAM: ~162 MB private dirty memory.
  - Zero outbound HTTP network traffic.
  - Test Runner showing **84/84 tests passing offline**.
- **Presenter (Conclusion):**
  > "With zero cloud dependencies, zero data leakage, and verified sub-5ms heuristic response times, SuSagi Guardian proves that enterprise-grade cybersecurity can run entirely on edge Arm silicon.
  >
  > For Phase 2, we are expanding SuSagi Guardian from mobile endpoints to Raspberry Pi 5 edge home gateways, protecting entire households at the network perimeter.
  >
  > Thank you, and let us build a safer digital Bharat together."
- **Visual:** Ending screen with project credits, Arm Bharat AI-SoC Challenge logos, and GitHub repository link.
