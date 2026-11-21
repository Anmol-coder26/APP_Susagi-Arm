import qrcode
import socket
import os

def get_lan_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except Exception:
        return "192.168.29.62"
    finally:
        s.close()

lan_ip = get_lan_ip()
apk_name = "susagi-guardian-recovery.apk"
url = f"http://{lan_ip}:8080/{apk_name}"

qr = qrcode.QRCode(version=None, error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=12, border=4)
qr.add_data(url)
qr.make(fit=True)
img = qr.make_image(fill_color="black", back_color="white")

dist_dir = os.path.join(os.path.dirname(__file__), "dist")
os.makedirs(dist_dir, exist_ok=True)

img.save(os.path.join(os.path.dirname(__file__), "susagi_apk_qr.png"))
img.save(os.path.join(dist_dir, "susagi_apk_qr.png"))

print(f"LAN IP: {lan_ip}")
print(f"APK URL: {url}")
print(f"QR saved to susagi_apk_qr.png and dist/susagi_apk_qr.png")
