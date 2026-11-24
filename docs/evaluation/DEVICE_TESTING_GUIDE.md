# SuSagi Guardian — Physical Device Verification Guide

**Target Application:** `susagi-guardian-recovery.apk`  
**Host PC LAN IP:** `192.168.29.62`  
**Distribution Server:** `http://192.168.29.62:8080/`  
**Backend API Server:** `http://192.168.29.62:3001/`  
**Target Platform:** Android 8.0+ (API Level 26 through 35)  

---

## 1. Prerequisites for Device Testing

1. **Local Wi-Fi Network:**  
   Ensure your Android device(s) are connected to the **same Wi-Fi network** as the development computer (`192.168.29.62`).
2. **Prerequisite Services Running:**  
   Confirm that `START_SUSAGI.bat` has been launched and both servers are active:
   - Backend API: `http://192.168.29.62:3001/health`
   - Distribution Server: `http://192.168.29.62:8080/`

---

## 2. APK Installation Steps

### Option A: Via QR Code (Recommended)
1. Open the camera app or a QR scanner on your Android phone.
2. Scan the QR code displayed on your screen or generated in `dist/susagi_apk_qr.png`.
3. The phone will open `http://192.168.29.62:8080/susagi-guardian-recovery.apk`.
4. Tap **Download** and then **Install**.
   *(If prompted by Android Play Protect or "Install unknown apps", tap **Settings → Allow from this source**).*

### Option B: Via Mobile Browser
1. Open Google Chrome on your phone.
2. Navigate to: `http://192.168.29.62:8080/`
3. Tap the green **Download Recovery APK** button.
4. Open the downloaded file to install.

### Option C: Via ADB (If connected via USB)
```cmd
adb install -r susagi-guardian-recovery.apk
```

---

## 3. Scenario-by-Scenario Verification Procedures

### SCENARIO A: Link Shield Verification (Phishing & Malware Interception)

#### Test Case A.1: Intent Interception via Web Browser
1. On your phone, open Chrome or your SMS app.
2. Tap on this test malicious URL:  
   `https://electricity-bill-pay.com/update-kyc`
3. If prompted with "Open with", choose **SuSagi Guardian**.
4. **Expected Outcome:**  
   - SuSagi Guardian displays the **Link Shield Interception Dialog**.
   - Risk Badge: `95% FRAUD` with red warning alert.
   - Hostname identified: `electricity-bill-pay.com`.
   - Threat Explanation: *"This destination has been flagged for active banking credential harvesting, digital arrest extortion..."*
   - Options to **BLOCK & RETURN** (safe) or **Proceed (Unsafe)**.

#### Test Case A.2: Share Sheet Interception (WhatsApp / Telegram / Messages)
1. In WhatsApp or your Notes app, highlight and share this text:  
   `"Urgent: Your SBI account is blocked. Verify immediately at http://192.168.1.100/sbi-verify.apk"`
2. Select **SuSagi Guardian** from the Android Share Sheet.
3. **Expected Outcome:**  
   - SuSagi Guardian extracts the URL from the shared text.
   - Detects **UNVERIFIED IP HOST DETECTED** and **MALICIOUS PACKAGE / APK SIDELOAD DETECTED**.
   - Displays the high-risk alert dialog.

#### Test Case A.3: In-App Manual Link Inspector
1. Open SuSagi Guardian from your app launcher.
2. On the Home Dashboard, tap **Link Shield** or open the Link Inspector modal.
3. Paste: `https://bit.ly/bank-offer`
4. Tap **Analyze URL**.
5. **Expected Outcome:**  
   - Risk Score: `65% CAUTION`.
   - Threat Tag: `⚠️ SUSPICIOUS MASKED URL SHORTENER`.

---

### SCENARIO B: Real-Time In-App Agora VoIP Calling

To test bidirectional calling, two Android devices (or one physical phone and one Android emulator) are recommended.

#### Device 1 (Caller / Host):
1. Open SuSagi Guardian.
2. Navigate to **Secure VoIP Call** → **Room Code Calling**.
3. Note the generated 6-digit room code (e.g., `482910`).
4. Tap **Start Call as Host**.
5. Grant Microphone permission when prompted.
6. The screen transitions to `VoipCallScreen`:
   - Connection banner: "Connecting..." → "Connected"
   - Diagnostic Pills appear: RTC Joined (Green), Peer Present (Amber/Waiting).

#### Device 2 (Recipient / Joiner):
1. Open SuSagi Guardian.
2. Navigate to **Secure VoIP Call** → **Room Code Calling**.
3. In the "Enter Room Code" field, type the 6-digit code from Device 1 (`482910`).
4. Tap **Join Secure Call**.
5. Grant Microphone permission.

#### Live Speech & Scam Detection Test:
1. Speak clearly into Device 1 (Remote/Caller):  
   *"This is Crime Branch Police. You are under digital arrest. Transfer fifty thousand rupees immediately or an arrest warrant will be issued."*
2. **Observe Device 2 (Victim):**
   - Diagnostic Pills: `Local PCM` (Green), `Remote PCM` (Green), `Bhashini STT` (Green).
   - Transcript updates live on screen showing speaker-labeled lines:  
     `Caller: This is Crime Branch Police. You are under digital arrest...`
   - AI Threat Posture evaluates automatically:
     - **Risk Score:** Jumps to **85% - 95% (CRITICAL)**.
     - **Scammer Role:** `Caller (Flagged)`.
     - **Victim Role:** `You`.
     - **Top Signals:** `authority_impersonation`, `digital_arrest`, `financial_coercion`.
3. Tap **Hang Up / Safe Cut**:
   - Audio observer unregisters immediately.
   - Call service halts.
   - Agora session disconnects cleanly.

---

### SCENARIO C: Message & Notification Protection

1. Open SuSagi Guardian → Settings.
2. Ensure **Notification Listener Permission** is granted for SuSagi Guardian.
3. Have someone send you an SMS or WhatsApp containing:  
   *"Your electricity will be disconnected tonight at 9:30 PM due to unpaid bill. Call our officer at 9876543210 or click http://bijli-vibhag-pay.xyz"*
4. **Expected Outcome:**  
   - SuSagi Guardian intercepts the notification.
   - Suppresses the original phishing notification.
   - Posts a high-priority Guardian alert: `⚠️ Scam Alert Intercepted`.
   - Tapping the alert opens `ScamWarningActivity`, presenting full explanation, score (85%), and options to discard or inspect.

---

### SCENARIO D: Family Protection & Emergency Alerts

1. Open SuSagi Guardian → Family Mesh / Trusted Contacts.
2. Enter a trusted phone number and save.
3. When Risk Score exceeds 75% during a call (Scenario B):
4. **Expected Outcome:**  
   - `VoipCallViewModel` automatically triggers `POST /alerts/family` and dispatches alert payload to configured emergency contact.
   - Diagnostic log shows: `Emergency alert successfully delivered to saved trusted contact!`

---

## 4. Diagnostic & Troubleshooting Checklist

| Issue | Cause | Solution |
| :--- | :--- | :--- |
| Cannot download APK from browser | Phone and PC on different Wi-Fi networks | Connect phone to the same Wi-Fi SSID as PC (`192.168.29.62`). |
| "Connection failed" during VoIP call | Windows Firewall blocking port 3001 | In Windows Defender Firewall, allow Node.js through private network. |
| Diagnostic pill "Local PCM" is Red | Microphone permission denied | Go to Phone Settings → Apps → SuSagi Guardian → Permissions → Enable Microphone. |
| Transcript does not appear | Bhashini credentials or internet access down | Ensure phone has internet access; REST transport falls back gracefully with logged status. |
