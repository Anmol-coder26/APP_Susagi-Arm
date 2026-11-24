import os
import math
from PIL import Image, ImageDraw, ImageFont

WINDIR = os.environ.get('WINDIR', 'C:\\Windows')
FONT_BOLD = os.path.join(WINDIR, 'Fonts', 'segoeuib.ttf')
FONT_REG = os.path.join(WINDIR, 'Fonts', 'segoeui.ttf')

def create_phone_frame(screen_img_path, target_height):
    screen = Image.open(screen_img_path).convert('RGBA')
    orig_w, orig_h = screen.size
    aspect = orig_w / float(orig_h)
    
    target_w = int(target_height * aspect)
    screen_resized = screen.resize((target_w, target_height), Image.Resampling.LANCZOS)
    
    border = 10
    phone_w = target_w + border * 2
    phone_h = target_height + border * 2
    
    phone = Image.new('RGBA', (phone_w, phone_h), (0, 0, 0, 0))
    pdraw = ImageDraw.Draw(phone)
    
    # Outer dark phone body
    pdraw.rounded_rectangle([(0, 0), (phone_w, phone_h)], radius=24, fill=(15, 23, 42, 255), outline=(51, 65, 85, 255), width=2)
    
    # Rounded corner mask for screen
    mask = Image.new('L', (target_w, target_height), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.rounded_rectangle([(0, 0), (target_w, target_height)], radius=16, fill=255)
    
    phone.paste(screen_resized, (border, border), mask)
    
    # Top speaker
    speaker_w, speaker_h = 36, 4
    speaker_x = (phone_w - speaker_w) // 2
    pdraw.rounded_rectangle([(speaker_x, 3), (speaker_x + speaker_w, 3 + speaker_h)], radius=2, fill=(71, 85, 105, 255))
    
    return phone

def create_hero_banner():
    width, height = 1600, 600
    base = Image.new('RGBA', (width, height), (11, 17, 32, 255))
    draw = ImageDraw.Draw(base)

    # Clean geometric grid background
    grid_color = (24, 34, 53, 255)
    for x in range(0, width, 40):
        draw.line([(x, 0), (x, height)], fill=grid_color, width=1)
    for y in range(0, height, 40):
        draw.line([(0, y), (width, y)], fill=grid_color, width=1)

    # Ambient radial glow behind phones (Right side only)
    glow = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    for r in range(350, 0, -10):
        alpha = int(14 * (1 - r / 350.0))
        gdraw.ellipse([(1280 - r, 300 - r), (1280 + r, 300 + r)], fill=(14, 165, 233, alpha))
    base = Image.alpha_composite(base, glow)
    draw = ImageDraw.Draw(base)

    # Top accent cyan line
    draw.line([(0, 0), (width, 0)], fill=(56, 189, 248, 255), width=4)

    # Left Content Positioning
    x_left = 90

    # Badge Pill
    font_badge = ImageFont.truetype(FONT_BOLD, 14)
    draw.rounded_rectangle([(x_left, 55), (x_left + 270, 87)], radius=6, fill=(15, 23, 42, 240), outline=(56, 189, 248, 200), width=1)
    draw.ellipse([(x_left + 12, 67), (x_left + 20, 75)], fill=(34, 197, 94, 255))
    draw.text((x_left + 28, 64), "ON-DEVICE CYBERSECURITY", font=font_badge, fill=(56, 189, 248, 255))

    # Product Title
    font_title = ImageFont.truetype(FONT_BOLD, 68)
    draw.text((x_left, 110), "SuSagi Guardian", font=font_title, fill=(255, 255, 255, 255))

    # Positioning Statement
    font_pos = ImageFont.truetype(FONT_BOLD, 26)
    draw.text((x_left, 205), "Intelligent cybersecurity for everyday digital life.", font=font_pos, fill=(56, 189, 248, 255))

    # Primary Tagline
    font_tag = ImageFont.truetype(FONT_BOLD, 22)
    draw.text((x_left, 250), "Think before you click. Know before you trust.", font=font_tag, fill=(241, 245, 249, 255))

    # Supporting Description
    font_desc = ImageFont.truetype(FONT_REG, 17)
    draw.text((x_left, 290), "Contextual threat intelligence and privacy-conscious on-device defense.", font=font_desc, fill=(148, 163, 184, 255))

    # Technology Badges
    pills = [
        ("Android 15 (API 35)", (34, 197, 94)),
        ("Arm64 Architecture", (245, 158, 11)),
        ("ExecuTorch 1.5.1", (168, 85, 247)),
        ("100% On-Device Engine", (56, 189, 248)),
        ("Open Source Apache 2.0", (100, 116, 139))
    ]
    x_pill = x_left
    y_pill = 350
    font_pill = ImageFont.truetype(FONT_BOLD, 13)
    for name, col in pills:
        bbox = font_pill.getbbox(name)
        pw = bbox[2] - bbox[0] + 24
        draw.rounded_rectangle([(x_pill, y_pill), (x_pill + pw, y_pill + 32)], radius=6, fill=(15, 23, 42, 240), outline=col, width=1)
        draw.ellipse([(x_pill + 8, y_pill + 12), (x_pill + 16, y_pill + 20)], fill=col)
        draw.text((x_pill + 22, y_pill + 8), name, font=font_pill, fill=(241, 245, 249, 255))
        x_pill += pw + 12

    # Horizontal Divider & Features
    font_feats = ImageFont.truetype(FONT_REG, 14)
    draw.line([(x_left, 420), (840, 420)], fill=(30, 41, 59, 255), width=1)
    feats_text = "Message Shield  *  Link Shield  *  Payment & QR Guard  *  Contextual AI Radar"
    draw.text((x_left, 435), feats_text, font=font_feats, fill=(148, 163, 184, 255))

    # Sub-footer
    font_sub = ImageFont.truetype(FONT_REG, 13)
    draw.text((x_left, 530), "Authored by Anmol Singh (@Anmol-coder26)  |  https://github.com/Anmol-coder26/APP_Susagi-Arm", font=font_sub, fill=(100, 116, 139, 255))

    # Right Section: Genuine Phone Mockups
    screen1_path = 'D:/SuSagi_Guardian_V2/assets/screenshots/01_home_security_hub.png'
    screen2_path = 'D:/SuSagi_Guardian_V2/assets/screenshots/04_ai_assistant.jpg'

    if os.path.exists(screen2_path):
        phone2 = create_phone_frame(screen2_path, target_height=450)
        base.paste(phone2, (1260, 85), phone2)

    if os.path.exists(screen1_path):
        phone1 = create_phone_frame(screen1_path, target_height=500)
        base.paste(phone1, (980, 50), phone1)

    # Save
    hero_path = 'D:/SuSagi_Guardian_V2/assets/branding/github_hero.png'
    base.save(hero_path, 'PNG')
    banner_path = 'D:/SuSagi_Guardian_V2/assets/branding/github_banner.png'
    base.save(banner_path, 'PNG')
    print(f"Generated Hero Banner: {hero_path} ({os.path.getsize(hero_path)} bytes)")

def create_social_preview():
    width, height = 1280, 640
    base = Image.new('RGBA', (width, height), (11, 17, 32, 255))
    draw = ImageDraw.Draw(base)

    # Grid
    grid_color = (24, 34, 53, 255)
    for x in range(0, width, 40):
        draw.line([(x, 0), (x, height)], fill=grid_color, width=1)
    for y in range(0, height, 40):
        draw.line([(0, y), (width, y)], fill=grid_color, width=1)

    # Ambient glow on right
    glow = Image.new('RGBA', (width, height), (0, 0, 0, 0))
    gdraw = ImageDraw.Draw(glow)
    for r in range(250, 0, -10):
        alpha = int(14 * (1 - r / 250.0))
        gdraw.ellipse([(1020 - r, 320 - r), (1020 + r, 320 + r)], fill=(14, 165, 233, alpha))
    base = Image.alpha_composite(base, glow)
    draw = ImageDraw.Draw(base)

    # Top border
    draw.line([(0, 0), (width, 0)], fill=(56, 189, 248, 255), width=4)

    # Left content
    x_left = 80
    font_badge = ImageFont.truetype(FONT_BOLD, 14)
    draw.rounded_rectangle([(x_left, 80), (x_left + 270, 112)], radius=6, fill=(15, 23, 42, 240), outline=(56, 189, 248, 200), width=1)
    draw.ellipse([(x_left + 12, 92), (x_left + 20, 100)], fill=(34, 197, 94, 255))
    draw.text((x_left + 28, 89), "ON-DEVICE CYBERSECURITY", font=font_badge, fill=(56, 189, 248, 255))

    font_title = ImageFont.truetype(FONT_BOLD, 56)
    draw.text((x_left, 140), "SuSagi Guardian", font=font_title, fill=(255, 255, 255, 255))

    font_pos = ImageFont.truetype(FONT_BOLD, 22)
    draw.text((x_left, 220), "Intelligent cybersecurity for everyday digital life.", font=font_pos, fill=(56, 189, 248, 255))

    font_tag = ImageFont.truetype(FONT_BOLD, 18)
    draw.text((x_left, 260), "Think before you click. Know before you trust.", font=font_tag, fill=(241, 245, 249, 255))

    font_desc = ImageFont.truetype(FONT_REG, 15)
    draw.text((x_left, 295), "Context-aware threat analysis and local AI for messages, links & payments.", font=font_desc, fill=(148, 163, 184, 255))

    # Tech stack
    font_stack = ImageFont.truetype(FONT_REG, 14)
    draw.line([(x_left, 360), (720, 360)], fill=(30, 41, 59, 255), width=1)
    draw.text((x_left, 380), "Android 15 (SDK 35)  *  ARM64 Architecture  *  ExecuTorch 1.5.1  *  Local Core", font=font_stack, fill=(56, 189, 248, 255))

    font_auth = ImageFont.truetype(FONT_REG, 14)
    draw.text((x_left, 530), "github.com/Anmol-coder26/APP_Susagi-Arm", font=font_auth, fill=(100, 116, 139, 255))

    # Phone mockup on right
    screen1_path = 'D:/SuSagi_Guardian_V2/assets/screenshots/01_home_security_hub.png'
    if os.path.exists(screen1_path):
        phone = create_phone_frame(screen1_path, target_height=520)
        base.paste(phone, (820, 60), phone)

    out_path = 'D:/SuSagi_Guardian_V2/assets/branding/social-preview.png'
    base.save(out_path, 'PNG')
    print(f"Generated Social Preview: {out_path} ({os.path.getsize(out_path)} bytes)")

def create_svg_logo():
    svg_content = '''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200" width="100%" height="100%">
  <defs>
    <linearGradient id="shieldGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#0284C7"/>
      <stop offset="50%" stop-color="#0EA5E9"/>
      <stop offset="100%" stop-color="#38BDF8"/>
    </linearGradient>
    <linearGradient id="glowGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#22C55E"/>
      <stop offset="100%" stop-color="#10B981"/>
    </linearGradient>
  </defs>
  <!-- Background Shield -->
  <polygon points="100,15 175,45 175,115 100,185 25,115 25,45" fill="#0F172A" stroke="url(#shieldGrad)" stroke-width="6" stroke-linejoin="round"/>
  <!-- Inner Shield Accent -->
  <polygon points="100,32 155,56 155,110 100,165 45,110 45,56" fill="#1E293B" opacity="0.6"/>
  <!-- Radar Nodes -->
  <circle cx="100" cy="95" r="42" fill="none" stroke="#38BDF8" stroke-width="2.5" stroke-dasharray="6,4" opacity="0.7"/>
  <circle cx="100" cy="95" r="26" fill="none" stroke="#22C55E" stroke-width="2" opacity="0.8"/>
  <!-- Center Checkmark -->
  <path d="M84 95 L96 107 L122 78" fill="none" stroke="url(#glowGrad)" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>
</svg>'''
    logo_path = 'D:/SuSagi_Guardian_V2/assets/branding/susagi-logo.svg'
    with open(logo_path, 'w', encoding='utf-8') as f:
        f.write(svg_content)
    print(f"Generated Logo SVG: {logo_path}")

if __name__ == '__main__':
    create_hero_banner()
    create_social_preview()
    create_svg_logo()
