import base64

qr_png = r"C:\Users\Tanya\.gemini\antigravity\brain\dfac2439-3c92-47dc-81ce-97c94d3b87db\susagi_apk_qr.png"
with open(qr_png, "rb") as f:
    b64 = base64.b64encode(f.read()).decode("utf-8")

html = f"""<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>SuSagi Guardian 3.1 APK</title>
    <style>
        body {{ font-family: -apple-system, sans-serif; background: #0B0F19; color: #F3F4F6; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; }}
        .card {{ background: #111827; border: 1px solid #1F2937; border-radius: 16px; padding: 28px; text-align: center; max-width: 440px; }}
        .badge {{ background: rgba(16, 185, 129, 0.1); color: #10B981; border: 1px solid rgba(16, 185, 129, 0.3); border-radius: 9999px; padding: 4px 12px; font-size: 11px; font-weight: 600; text-transform: uppercase; }}
        h2 {{ margin: 12px 0 6px 0; color: #FFF; }}
        p {{ color: #9CA3AF; font-size: 14px; margin: 0 0 16px 0; }}
        .qr {{ background: #FFF; padding: 12px; border-radius: 12px; display: inline-block; margin-bottom: 16px; }}
        .qr img {{ display: block; width: 200px; height: 200px; }}
        .url {{ font-family: monospace; font-size: 13px; color: #60A5FA; background: #1F2937; padding: 8px 12px; border-radius: 6px; word-break: break-all; margin-bottom: 16px; border: 1px dashed #374151; }}
        .btn {{ display: inline-block; background: #10B981; color: #0B0F19; text-decoration: none; padding: 12px 24px; border-radius: 8px; font-weight: 700; font-size: 14px; }}
        .hash {{ font-family: monospace; font-size: 10px; color: #6B7280; margin-top: 10px; word-break: break-all; }}
        .steps {{ text-align: left; font-size: 12px; color: #9CA3AF; margin-top: 20px; border-top: 1px solid #1F2937; padding-top: 14px; }}
        .steps ol {{ padding-left: 20px; margin: 6px 0 0 0; }}
        .steps li {{ margin-bottom: 4px; }}
    </style>
</head>
<body>
    <div class="card">
        <span class="badge">Arm Bharat AI-SoC Challenge • PS3</span>
        <h2>SuSagi Guardian 3.1 APK</h2>
        <p>Scan with your phone camera on Wi-Fi</p>
        <div class="qr"><img src="data:image/png;base64,{b64}" alt="QR Code"></div>
        <div class="url">http://192.168.29.62:8080/susagi-guardian-3.1.apk</div>
        <a class="btn" href="http://192.168.29.62:8080/susagi-guardian-3.1.apk">Download SuSagi Guardian 3.1 (136 MB)</a>
        <div class="hash">SHA-256: EC29EDA6748196B31A869CFD3ACD87F86D46E93731F0F4C99023C3586C9D6395</div>
        <div class="steps">
            <strong>Key Features to Test:</strong>
            <ol>
                <li><strong>Offline Whisper Speech AI:</strong> Tap the Whisper card on Home screen to test 3s live mic recording in Airplane Mode (Hindi & English).</li>
                <li><strong>Dual STT Engine:</strong> Seamless dynamic switching between Whisper (Offline) & Bhashini (Online) during VoIP calls.</li>
                <li><strong>Message & SMS Shield:</strong> Tap 1-tap scenario chips (🚨 Digital Arrest, ⚡ Power Cut, 🏦 Bank KYC) for on-device Llama assessment.</li>
                <li><strong>Link Shield:</strong> Paste suspicious URLs or APK links for instant local inspection.</li>
            </ol>
        </div>
    </div>
</body>
</html>"""

out_html = r"C:\Users\Tanya\.gemini\antigravity\brain\dfac2439-3c92-47dc-81ce-97c94d3b87db\qr_code_widget.html"
with open(out_html, "w", encoding="utf-8") as f:
    f.write(html)
print("Updated qr_code_widget.html successfully!")
