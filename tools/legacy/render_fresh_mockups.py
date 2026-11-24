import os
import subprocess
import time
from PIL import Image, ImageDraw, ImageFont

ARTIFACT_DIR = r"C:\Users\Tanya\.gemini\antigravity\brain\dfac2439-3c92-47dc-81ce-97c94d3b87db"
EDGE_PATH = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"

files = [
    ("mockup_home_unique.html", "screen_home.png"),
    ("mockup_voip_unique.html", "screen_voip_call.png"),
    ("mockup_room_unique.html", "screen_room_code.png"),
    ("mockup_link_unique.html", "screen_link_shield.png")
]

for html_file, png_file in files:
    html_path = os.path.join(ARTIFACT_DIR, html_file)
    png_path = os.path.join(ARTIFACT_DIR, png_file)
    
    if os.path.exists(png_path):
        try:
            os.remove(png_path)
        except:
            pass

    cmd = [
        EDGE_PATH,
        "--headless=new",
        "--disable-gpu",
        f"--screenshot={png_path}",
        "--window-size=412,890",
        f"file:///{html_path.replace(os.sep, '/')}"
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    time.sleep(1)
    if os.path.exists(png_path):
        print(f"Rendered {png_file}: {os.path.getsize(png_path)} bytes")
    else:
        print(f"FAILED {png_file}: {res.stderr}")

screens = [
    ("1. Orbital Sentinel Console", os.path.join(ARTIFACT_DIR, "screen_home.png")),
    ("2. Scam Matrix Spectrogram HUD", os.path.join(ARTIFACT_DIR, "screen_voip_call.png")),
    ("3. Cryptographic Sonic Vault", os.path.join(ARTIFACT_DIR, "screen_room_code.png")),
    ("4. Hazard Interceptor Barrier", os.path.join(ARTIFACT_DIR, "screen_link_shield.png"))
]

gap = 40
top_margin = 110
bottom_margin = 50
side_margin = 44
sw, sh = 412, 890

total_w = side_margin * 2 + 4 * sw + 3 * gap
total_h = top_margin + sh + bottom_margin

canvas = Image.new("RGB", (total_w, total_h), "#E5E1D8")
draw = ImageDraw.Draw(canvas)

try:
    font_title = ImageFont.truetype("arialbd.ttf", 34)
    font_sub = ImageFont.truetype("arial.ttf", 17)
    font_label = ImageFont.truetype("arialbd.ttf", 18)
except:
    font_title = font_sub = font_label = ImageFont.load_default()

draw.text((side_margin, 26), "SuSagi Guardian // Bespoke Avant-Garde Industrial UI", fill="#121316", font=font_title)
draw.text((side_margin, 68), "Champagne Titanium • Acid Chartreuse Accents • Orbital Sentinel Core • Counter-Intervention Tactics", fill="#525252", font=font_sub)

for i, (title, path) in enumerate(screens):
    x = side_margin + i * (sw + gap)
    y = top_margin
    draw.text((x, y - 30), title, fill="#121316", font=font_label)
    
    if os.path.exists(path):
        im = Image.open(path)
        canvas.paste(im, (x, y))
        draw.rounded_rectangle([x-3, y-3, x+sw+3, y+sh+3], radius=28, outline="#121316", width=3)

banner_path = os.path.join(ARTIFACT_DIR, "ui_overview_banner.png")
canvas.save(banner_path, "PNG", optimize=True)
print(f"Generated Avant-Garde Banner: {banner_path} ({os.path.getsize(banner_path)} bytes)")
