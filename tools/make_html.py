import base64

with open('susagi_apk_qr.png', 'rb') as f:
    b64 = base64.b64encode(f.read()).decode('utf-8')

target = r'C:\Users\Tanya\.gemini\antigravity\brain\dfac2439-3c92-47dc-81ce-97c94d3b87db\qr_code_widget.html'

html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>SuSagi VoIP APK</title>
<style>
  body {{ font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0b0f19; color: #e2e8f0; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; padding: 20px; box-sizing: border-box; }}
  .card {{ background: #151d2f; border: 1px solid #1e293b; border-radius: 20px; padding: 28px; max-width: 400px; width: 100%; text-align: center; box-shadow: 0 16px 36px rgba(0,0,0,0.5); }}
  .badge {{ display: inline-block; padding: 4px 12px; background: rgba(16, 185, 129, 0.2); color: #10b981; border: 1px solid #10b981; border-radius: 999px; font-size: 12px; font-weight: 700; text-transform: uppercase; margin-bottom: 14px; }}
  h1 {{ margin: 0 0 8px; font-size: 22px; color: #f8fafc; }}
  p {{ color: #94a3b8; font-size: 14px; margin: 0 0 20px; }}
  .qr-box {{ background: #ffffff; padding: 14px; border-radius: 16px; display: inline-block; margin-bottom: 20px; box-shadow: 0 4px 12px rgba(0,0,0,0.2); }}
  .qr-box img {{ width: 220px; height: 220px; display: block; }}
  .btn {{ display: block; background: linear-gradient(135deg, #10b981, #059669); color: #ffffff; text-decoration: none; padding: 12px 20px; border-radius: 12px; font-weight: 600; font-size: 15px; box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3); }}
  .btn:hover {{ background: #059669; }}
  .notes {{ margin-top: 18px; font-size: 12px; color: #64748b; line-height: 1.6; border-top: 1px solid #1e293b; padding-top: 14px; text-align: left; }}
</style>
</head>
<body>
<div class="card">
  <div class="badge">VoIP Call Fixed & Tested</div>
  <h1>SuSagi VoIP Debug APK</h1>
  <p>Scan with your phone camera to download & install:</p>
  <div class="qr-box">
    <img src="data:image/png;base64,{b64}" alt="APK Download QR Code" />
  </div>
  <a class="btn" href="http://192.168.29.62:8080/app-debug.apk" target="_blank">Direct Download (120 MB)</a>
  <div class="notes">
    <strong>• Zero-latency connect:</strong> Call joins Agora immediately without blocking on network tokens.<br>
    <strong>• Native Audio Routing:</strong> Automatic speakerphone, hardware AEC, and two-way voice transmission.<br>
    <strong>• Android 14 Safe:</strong> Foreground service lifecycle protected against timeouts and permission crashes.
  </div>
</div>
</body>
</html>
"""

with open(target, 'w', encoding='utf-8') as f:
    f.write(html)

print("Updated qr_code_widget.html successfully")
