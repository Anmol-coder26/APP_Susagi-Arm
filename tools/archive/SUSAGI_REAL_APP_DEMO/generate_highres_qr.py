import os
import qrcode
from qrcode.image.styledpil import StyledPilImage
from qrcode.image.styles.moduledrawers import RoundedModuleDrawer, SquareModuleDrawer
from qrcode.image.styles.colormasks import SolidFillColorMask
from PIL import Image, ImageDraw, ImageFont

# Target paths
DEMO_DIR = r"D:\SuSagi_Guardian_V2\SUSAGI_REAL_APP_DEMO"
ARTIFACT_DIR = r"C:\Users\Tanya\.gemini\antigravity\brain\74546262-ee4b-4cfd-bf27-e92aa5f188b2"

URL = "http://192.168.29.62:8080/susagi-guardian-v2.apk"

# 1. Create Base QR with High Error Correction (allows center logo up to 30%)
qr = qrcode.QRCode(
    version=2,
    error_correction=qrcode.constants.ERROR_CORRECT_H,
    box_size=20,
    border=4,
)
qr.add_data(URL)
qr.make(fit=True)

# Generate crisp base QR
qr_img = qr.make_image(fill_color="black", back_color="white").convert("RGBA")
qr_w, qr_h = qr_img.size

# 2. Draw branded center emblem (Cyber Shield with 'S')
logo_size = int(qr_w * 0.22)
logo = Image.new("RGBA", (logo_size, logo_size), (0, 0, 0, 0))
draw_logo = ImageDraw.Draw(logo)

# Rounded background badge for logo
draw_logo.rounded_rectangle([2, 2, logo_size - 2, logo_size - 2], radius=int(logo_size * 0.25), fill=(11, 18, 32, 255), outline=(0, 216, 255, 255), width=4)

# Draw stylized 'S' in center
try:
    font_s = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", int(logo_size * 0.55))
except Exception:
    font_s = ImageFont.load_default()

bbox = draw_logo.textbbox((0, 0), "S", font=font_s)
s_w = bbox[2] - bbox[0]
s_h = bbox[3] - bbox[1]
s_x = (logo_size - s_w) // 2
s_y = (logo_size - s_h) // 2 - 4

# Draw S with glow
draw_logo.text((s_x, s_y), "S", font=font_s, fill=(124, 245, 197, 255))

# Paste logo into center of QR code
pos = ((qr_w - logo_size) // 2, (qr_h - logo_size) // 2)
qr_img.paste(logo, pos, mask=logo)

# Save high-res standalone QR
highres_path1 = os.path.join(DEMO_DIR, "susagi_qr_highres.png")
highres_path2 = os.path.join(ARTIFACT_DIR, "susagi_qr_highres.png")
qr_img.convert("RGB").save(highres_path1, "PNG", dpi=(300, 300))
qr_img.convert("RGB").save(highres_path2, "PNG", dpi=(300, 300))
print(f"High-res standalone QR saved: {qr_img.size}")

# 3. Create High-Quality Presentation Card (1200 x 1650)
card_w, card_h = 1200, 1650
card = Image.new("RGBA", (card_w, card_h), (11, 18, 32, 255))
draw = ImageDraw.Draw(card)

# Background subtle grid / accent lines
for y in range(0, card_h, 60):
    draw.line([(0, y), (card_w, y)], fill=(16, 27, 46, 255), width=1)
for x in range(0, card_w, 60):
    draw.line([(x, 0), (x, card_h)], fill=(16, 27, 46, 255), width=1)

# Outer decorative border
draw.rounded_rectangle([30, 30, card_w - 30, card_h - 30], radius=32, outline=(30, 48, 80, 255), width=3)
draw.rounded_rectangle([36, 36, card_w - 36, card_h - 36], radius=28, outline=(0, 216, 255, 120), width=1)

# Load fonts
try:
    font_badge = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 26)
    font_title = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 64)
    font_sub = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 32)
    font_scan = ImageFont.truetype(r"C:\Windows\Fonts\segoeuib.ttf", 36)
    font_url = ImageFont.truetype(r"C:\Windows\Fonts\consola.ttf", 26)
    font_meta = ImageFont.truetype(r"C:\Windows\Fonts\segoeui.ttf", 24)
    font_hash = ImageFont.truetype(r"C:\Windows\Fonts\consola.ttf", 20)
except Exception:
    font_badge = font_title = font_sub = font_scan = font_url = font_meta = font_hash = ImageFont.load_default()

# Header: Challenge Badge
badge_text = "ARM AI-SOC BHARAT CHALLENGE 2026–27  •  PS3"
bb = draw.textbbox((0, 0), badge_text, font=font_badge)
bw = bb[2] - bb[0]
bx = (card_w - bw) // 2
draw.rounded_rectangle([bx - 24, 70, bx + bw + 24, 120], radius=16, fill=(15, 34, 56, 255), outline=(0, 216, 255, 200), width=2)
draw.text((bx, 78), badge_text, font=font_badge, fill=(0, 216, 255, 255))

# Header: Title
title = "SuSagi Guardian V2"
tb = draw.textbbox((0, 0), title, font=font_title)
tw = tb[2] - tb[0]
draw.text(((card_w - tw) // 2, 150), title, font=font_title, fill=(240, 244, 248, 255))

# Header: Subtitle
sub = "On-Device Threat, Phishing & Scam Detection"
sb = draw.textbbox((0, 0), sub, font=font_sub)
sw = sb[2] - sb[0]
draw.text(((card_w - sw) // 2, 235), sub, font=font_sub, fill=(148, 163, 184, 255))

# QR Container Card (White rounded container for maximum contrast & scanning speed)
cont_w, cont_h = 760, 760
cont_x = (card_w - cont_w) // 2
cont_y = 310
draw.rounded_rectangle([cont_x, cont_y, cont_x + cont_w, cont_y + cont_h], radius=32, fill=(255, 255, 255, 255), outline=(0, 216, 255, 255), width=6)

# Resize & paste QR into white card
qr_display = qr_img.resize((680, 680), Image.Resampling.LANCZOS)
card.paste(qr_display, (cont_x + 40, cont_y + 40), mask=qr_display)

# Call to Action
scan_text = "Scan with Camera or Google Lens to Download"
scb = draw.textbbox((0, 0), scan_text, font=font_scan)
scw = scb[2] - scb[0]
draw.text(((card_w - scw) // 2, 1110), scan_text, font=font_scan, fill=(124, 245, 197, 255))

# URL Box
url_display = URL
ub = draw.textbbox((0, 0), url_display, font=font_url)
uw = ub[2] - ub[0]
ux = (card_w - uw) // 2
draw.rounded_rectangle([ux - 20, 1175, ux + uw + 20, 1225], radius=12, fill=(17, 28, 48, 255), outline=(30, 48, 80, 255), width=2)
draw.text((ux, 1187), url_display, font=font_url, fill=(0, 216, 255, 255))

# Info specs card at bottom
meta_y = 1270
draw.rounded_rectangle([100, meta_y, card_w - 100, meta_y + 260], radius=20, fill=(15, 26, 44, 255), outline=(30, 48, 80, 255), width=2)

lines = [
    ("Package:", "com.susagi.guardian2", "Build:", "Directive 60 Verified Release"),
    ("File Size:", "131.6 MB (137,989,587 B)", "JVM Tests:", "315 / 315 Passed (100%)"),
    ("Architecture:", "100% On-Device / Offline", "Hardware:", "Arm Cortex / NPU Ready"),
]

curr_y = meta_y + 24
for col1_lbl, col1_val, col2_lbl, col2_val in lines:
    draw.text((130, curr_y), col1_lbl, font=font_meta, fill=(148, 163, 184, 255))
    draw.text((270, curr_y), col1_val, font=font_meta, fill=(240, 244, 248, 255))
    draw.text((640, curr_y), col2_lbl, font=font_meta, fill=(148, 163, 184, 255))
    draw.text((790, curr_y), col2_val, font=font_meta, fill=(240, 244, 248, 255))
    curr_y += 42

curr_y += 8
draw.text((130, curr_y), "SHA-256:", font=font_meta, fill=(148, 163, 184, 255))
sha_str = "301B7C1E3D82F37D4625B5C2763D0E6C8170F53011047D401AFA603B4E7FA40E"
draw.text((270, curr_y + 3), sha_str, font=font_hash, fill=(124, 245, 197, 255))

# Footer
footer = "SuSagi Guardian V2  •  Arm AI-SoC Bharat Challenge  •  Confidential & Verified"
fb = draw.textbbox((0, 0), footer, font=font_meta)
fw = fb[2] - fb[0]
draw.text(((card_w - fw) // 2, 1580), footer, font=font_meta, fill=(100, 116, 139, 255))

# Save presentation card
card_path1 = os.path.join(DEMO_DIR, "susagi_qr_card.png")
card_path2 = os.path.join(ARTIFACT_DIR, "susagi_qr_card.png")
card.convert("RGB").save(card_path1, "PNG", dpi=(300, 300))
card.convert("RGB").save(card_path2, "PNG", dpi=(300, 300))
print(f"Presentation QR Card saved: {card.size}")

# 4. Generate SVG Vector QR for infinite scaling
import qrcode.image.svg
factory = qrcode.image.svg.SvgPathImage
svg_qr = qrcode.QRCode(
    version=2,
    error_correction=qrcode.constants.ERROR_CORRECT_M,
    box_size=10,
    border=4,
    image_factory=factory
)
svg_qr.add_data(URL)
svg_qr.make(fit=True)
svg_img = svg_qr.make_image()
svg_path1 = os.path.join(DEMO_DIR, "susagi_qr.svg")
svg_path2 = os.path.join(ARTIFACT_DIR, "susagi_qr.svg")
svg_img.save(svg_path1)
svg_img.save(svg_path2)
print("SVG vector QR code saved.")
