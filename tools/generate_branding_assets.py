import os
import math
from PIL import Image, ImageDraw, ImageFont

WINDIR = os.environ.get('WINDIR', 'C:\\Windows')
FONT_BOLD = os.path.join(WINDIR, 'Fonts', 'segoeuib.ttf')
FONT_REG = os.path.join(WINDIR, 'Fonts', 'segoeui.ttf')

def create_banner():
    width, height = 1200, 420
    img = Image.new('RGBA', (width, height), (10, 15, 29, 255))
    draw = ImageDraw.Draw(img)

    # Background grid lines
    grid_color = (25, 38, 65, 120)
    for x in range(0, width, 40):
        draw.line([(x, 0), (x, height)], fill=grid_color, width=1)
    for y in range(0, height, 40):
        draw.line([(0, y), (width, y)], fill=grid_color, width=1)

    # Accent glow circles in corners
    for r in range(150, 0, -5):
        alpha = int(25 * (1 - r / 150.0))
        draw.ellipse([(width - 200 - r, 50 - r), (width - 200 + r, 50 + r)], fill=(14, 165, 233, alpha))
        draw.ellipse([(100 - r, height - 50 - r), (100 + r, height - 50 + r)], fill=(99, 102, 241, alpha))

    # Top border cyan accent line
    draw.line([(0, 0), (width, 0)], fill=(56, 189, 248, 255), width=4)

    # Challenge Pill Badge
    font_badge = ImageFont.truetype(FONT_BOLD, 14)
    badge_text = "BHARAT AI-SoC CHALLENGE 2026-27  *  PROBLEM STATEMENT PS3"
    draw.rounded_rectangle([(60, 45), (570, 75)], radius=6, fill=(30, 41, 59, 220), outline=(245, 158, 11, 200), width=1)
    draw.text((75, 52), badge_text, font=font_badge, fill=(245, 158, 11, 255))

    # Main Title
    font_title = ImageFont.truetype(FONT_BOLD, 52)
    draw.text((60, 95), "SuSagi Guardian", font=font_title, fill=(255, 255, 255, 255))
    
    # Version Pill
    draw.rounded_rectangle([(490, 110), (555, 145)], radius=6, fill=(14, 165, 233, 200))
    font_v = ImageFont.truetype(FONT_BOLD, 20)
    draw.text((502, 116), "V2", font=font_v, fill=(255, 255, 255, 255))

    # Subtitle
    font_sub = ImageFont.truetype(FONT_BOLD, 22)
    draw.text((60, 170), "On-Device AI Cybersecurity & Scam Protection", font=font_sub, fill=(56, 189, 248, 255))

    # Tagline
    font_tag = ImageFont.truetype(FONT_REG, 17)
    draw.text((60, 210), "Detect suspicious behavior. Understand the evidence. Make safer decisions.", font=font_tag, fill=(148, 163, 184, 255))

    # Security Feature Badges
    pills = [
        ("Message Shield", (34, 197, 94)),
        ("Link Shield", (56, 189, 248)),
        ("Voice Shield", (245, 158, 11)),
        ("UPI / QR Inspector", (168, 85, 247)),
        ("ExecuTorch Llama", (236, 72, 153)),
        ("Arm64 Offline Core", (14, 165, 233))
    ]
    x_offset = 60
    y_pills = 265
    font_pill = ImageFont.truetype(FONT_BOLD, 13)
    for name, col in pills:
        bbox = font_pill.getbbox(name)
        w = bbox[2] - bbox[0] + 24
        draw.rounded_rectangle([(x_offset, y_pills), (x_offset + w, y_pills + 32)], radius=6, fill=(15, 23, 42, 230), outline=col, width=1)
        # Small colored dot
        draw.ellipse([(x_offset + 8, y_pills + 12), (x_offset + 16, y_pills + 20)], fill=col)
        draw.text((x_offset + 22, y_pills + 8), name, font=font_pill, fill=(241, 245, 249, 255))
        x_offset += w + 14

    # Bottom hardware / spec bar
    font_hw = ImageFont.truetype(FONT_REG, 13)
    hw_text = "Target Architecture: ARMv8-A / v9-A (Cortex-A78 / A55)  |  OS: Android 14+ (API 34)  |  100% On-Device Offline Boundary"
    draw.line([(60, 335), (width - 60, 335)], fill=(30, 41, 59, 255), width=1)
    draw.text((60, 355), hw_text, font=font_hw, fill=(100, 116, 139, 255))

    # Right side Shield Icon Graphic
    cx, cy = 980, 210
    shield_color = (14, 165, 233, 220)
    # Draw stylized shield
    points = [
        (cx - 90, cy - 100),
        (cx + 90, cy - 100),
        (cx + 90, cy + 20),
        (cx, cy + 110),
        (cx - 90, cy + 20)
    ]
    draw.polygon(points, fill=(15, 23, 42, 200), outline=shield_color, width=3)
    
    # Inner glowing checkmark/radar
    draw.arc([(cx - 60, cy - 60), (cx + 60, cy + 60)], start=210, end=330, fill=(56, 189, 248, 255), width=4)
    draw.arc([(cx - 40, cy - 40), (cx + 40, cy + 40)], start=210, end=330, fill=(34, 197, 94, 255), width=3)
    draw.text((cx - 28, cy - 15), "AI", font=ImageFont.truetype(FONT_BOLD, 36), fill=(255, 255, 255, 255))
    
    # Save banner
    out_path = 'D:/SuSagi_Guardian_V2/assets/branding/github_banner.png'
    img.save(out_path, 'PNG')
    print(f"Generated Banner: {out_path} ({os.path.getsize(out_path)} bytes)")

def create_video_thumbnail():
    width, height = 1280, 720
    img = Image.new('RGBA', (width, height), (10, 15, 29, 255))
    draw = ImageDraw.Draw(img)

    # Grid background
    grid_color = (20, 32, 54, 100)
    for x in range(0, width, 50):
        draw.line([(x, 0), (x, height)], fill=grid_color, width=1)
    for y in range(0, height, 50):
        draw.line([(0, y), (width, y)], fill=grid_color, width=1)

    # Top border
    draw.line([(0, 0), (width, 0)], fill=(245, 158, 11, 255), width=6)

    # Header Badge
    font_badge = ImageFont.truetype(FONT_BOLD, 18)
    draw.rounded_rectangle([(80, 60), (660, 100)], radius=6, fill=(30, 41, 59, 240), outline=(245, 158, 11, 255), width=2)
    draw.text((100, 70), "BHARAT AI-SoC CHALLENGE 2026-27  *  PS3 FINAL SUBMISSION", font=font_badge, fill=(245, 158, 11, 255))

    # Main Title
    font_title = ImageFont.truetype(FONT_BOLD, 64)
    draw.text((80, 130), "SuSagi Guardian V2", font=font_title, fill=(255, 255, 255, 255))

    font_sub = ImageFont.truetype(FONT_BOLD, 30)
    draw.text((80, 220), "Official 2-Minute Technical Demonstration", font=font_sub, fill=(56, 189, 248, 255))

    # Center Play Button Graphic
    cx, cy = 640, 440
    # Outer glowing ring
    draw.ellipse([(cx - 75, cy - 75), (cx + 75, cy + 75)], fill=(15, 23, 42, 230), outline=(56, 189, 248, 255), width=4)
    draw.ellipse([(cx - 65, cy - 65), (cx + 65, cy + 65)], fill=(14, 165, 233, 240))
    # Play triangle
    play_poly = [(cx - 20, cy - 35), (cx + 35, cy), (cx - 20, cy + 35)]
    draw.polygon(play_poly, fill=(255, 255, 255, 255))

    # Runtime Pill
    font_time = ImageFont.truetype(FONT_BOLD, 18)
    draw.rounded_rectangle([(cx - 85, cy + 105), (cx + 85, cy + 140)], radius=6, fill=(0, 0, 0, 200), outline=(255, 255, 255, 180), width=1)
    draw.text((cx - 60, cy + 112), "01:46 / 1080p HD", font=font_time, fill=(255, 255, 255, 255))

    # Feature labels
    font_feat = ImageFont.truetype(FONT_REG, 20)
    draw.text((80, 630), "Message Shield  *  Link Shield  *  UPI & QR Defense  *  Arm ExecuTorch Llama", font=font_feat, fill=(148, 163, 184, 255))

    out_path = 'D:/SuSagi_Guardian_V2/assets/branding/video_thumbnail.png'
    img.save(out_path, 'PNG')
    print(f"Generated Thumbnail: {out_path} ({os.path.getsize(out_path)} bytes)")

if __name__ == '__main__':
    create_banner()
    create_video_thumbnail()
