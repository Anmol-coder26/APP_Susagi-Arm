import os
import math
import subprocess
import imageio_ffmpeg
from PIL import Image, ImageDraw, ImageFont

ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
print("Using ffmpeg:", ffmpeg_exe)

base_dir = r"D:\SuSagi_Guardian_V2\SUSAGI_VIDEO_PRODUCTION"
frames_dir = os.path.join(base_dir, "editing_project", "scene_03_frames")
os.makedirs(frames_dir, exist_ok=True)

raw_video_path = os.path.join(base_dir, "raw_device_recordings", "scene_03_message_shield.mp4")
has_raw_video = os.path.exists(raw_video_path)

approved_screen_path = os.path.join(base_dir, "approved_app_screenshots", "01_authentic_ai_assistant_raw.jpg")
approved_screen = Image.open(approved_screen_path).convert("RGB")
sw, sh = approved_screen.size

audio_path = os.path.join(base_dir, "narration", "scene_03_narration.mp3")

# Fonts
f_brand = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 24)
f_meta = ImageFont.truetype("C:/Windows/Fonts/consolab.ttf", 15)
f_title = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 30)
f_sub = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 18)
f_section = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 17)
f_body = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 15)
f_mono = ImageFont.truetype("C:/Windows/Fonts/consolab.ttf", 14)
f_subtitles = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 22)
f_badge = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 13)

fps = 30
intro_pad = 2.0
narration_duration = 13.13
outro_pad = 3.5
total_duration = intro_pad + narration_duration + outro_pad # 18.63s
total_frames = int(fps * total_duration) # ~559 frames
w, h = 1920, 1080

sub_cues = [
    (0.0, 4.8, "SuSagi examines context, requested actions and supporting evidence."),
    (4.8, 8.5, "An unverified prize announcement deserves caution,"),
    (8.5, 13.2, "while an advance-payment demand provides stronger evidence of fraud.")
]

print(f"Rendering {total_frames} frames ({total_duration:.2f}s) for Directive 78 Scene 03...")

for i in range(total_frames):
    t = i / float(fps)
    frame = Image.new("RGB", (w, h), "#0A0E17")
    draw = ImageDraw.Draw(frame)

    # Ambient radial gradient
    glow_x, glow_y = 520, 540
    for r in range(480, 0, -20):
        alpha = int((1.0 - (r / 480.0)) * 25)
        c = (10 + alpha, 16 + int(alpha * 1.5), 26 + alpha * 2)
        draw.ellipse([glow_x - r, glow_y - r, glow_x + r, glow_y + r], fill=c)

    # Top Brand Header Bar
    draw.rectangle([0, 0, w, 68], fill="#0F172A")
    draw.line([(0, 68), (w, 68)], fill="#1E293B", width=1)
    draw.ellipse([60, 28, 74, 42], fill="#10B981")
    draw.text((90, 21), "SUSAGI GUARDIAN", fill="#FFFFFF", font=f_brand)
    draw.text((330, 26), "•  CONTEXTUAL THREAT INTELLIGENCE (SCENE 03)", fill="#94A3B8", font=f_meta)

    # Top Right Technical Badge
    draw.rounded_rectangle([w - 440, 18, w - 60, 50], radius=16, fill="#064E3B", outline="#10B981", width=1)
    draw.text((w - 420, 24), "⚡ MASTER DIRECTIVE 78 • AUTONOMOUS VERIFICATION", fill="#6EE7B7", font=f_badge)

    # -------------------------------------------------------------
    # LEFT PANEL: LIVE PRODUCT EXPERIENCE (40–45% WIDTH)
    # -------------------------------------------------------------
    phone_w = 405
    phone_h = int(phone_w * sh / sw) # ~935 px
    phone_x = 160
    phone_y = 95

    # Outer phone titanium bezel
    bezel_pad = 14
    draw.rounded_rectangle(
        [phone_x - bezel_pad, phone_y - bezel_pad, phone_x + phone_w + bezel_pad, phone_y + phone_h + bezel_pad],
        radius=36,
        fill="#1E293B",
        outline="#334155",
        width=2
    )

    # Inner bezel
    draw.rounded_rectangle(
        [phone_x - 3, phone_y - 3, phone_x + phone_w + 3, phone_y + phone_h + 3],
        radius=26,
        fill="#000000"
    )

    # Scaled device footage
    screen_scaled = approved_screen.resize((phone_w, phone_h), Image.Resampling.LANCZOS)
    mask = Image.new("L", (phone_w, phone_h), 0)
    mask_draw = ImageDraw.Draw(mask)
    mask_draw.rounded_rectangle([0, 0, phone_w, phone_h], radius=24, fill=255)
    frame.paste(screen_scaled, (phone_x, phone_y), mask)

    # Speaker notch
    speaker_w, speaker_h = 70, 5
    speaker_x = phone_x + (phone_w - speaker_w) // 2
    draw.rounded_rectangle([speaker_x, phone_y + 6, speaker_x + speaker_w, phone_y + 6 + speaker_h], radius=3, fill="#0F172A")

    # Footnote provenance tag
    draw.text((phone_x + 35, phone_y + phone_h + 16), "PROVENANCE: GENUINE ANDROID HANDSET CAPTURE", fill="#64748B", font=f_meta)

    # -------------------------------------------------------------
    # RIGHT PANEL: THE ENGINEERING BEHIND THE FEATURE (55–60%)
    # -------------------------------------------------------------
    rx = 670
    rw = 1170

    draw.rounded_rectangle([rx, 95, rx + rw, 950], radius=24, fill="#0F172A", outline="#1E293B", width=1)
    draw.text((rx + 40, 125), "CONTEXTUAL FRAUD DETECTION PIPELINE", fill="#FFFFFF", font=f_title)
    draw.text((rx + 40, 168), "Multi-stage evidence synthesis isolating predatory action demands", fill="#94A3B8", font=f_sub)
    draw.line([(rx + 40, 205), (rx + rw - 40, 205)], fill="#1E293B", width=1)

    t_rel = max(0.0, t - intro_pad)
    
    stages = [
        ("01", "Text Normalization", "Unicode NFKC, zero-width strip, Levenshtein homoglyph fold", t_rel >= 0.5),
        ("02", "Intent & Pretext Analysis", "Detects narrative themes: Lottery, Tax, Digital Arrest, KYC", t_rel >= 3.0),
        ("03", "Action Extraction", "Isolates demands: Advance Fee, OTP, Reverse-Debit, APK sideload", t_rel >= 6.0),
        ("04", "Evidence Synthesis", "Aggregates multi-channel risk markers & evaluates uncertainty", t_rel >= 9.0),
        ("05", "Qualitative Risk Band", "Assigns SAFE / REVIEW / HIGH RISK without arbitrary percentages", t_rel >= 12.0),
    ]

    stage_y = 225
    for idx, (num, title, desc, active) in enumerate(stages):
        box_bg = "#064E3B" if active else "#1E293B"
        border_col = "#10B981" if active else "#334155"
        txt_col = "#FFFFFF" if active else "#94A3B8"
        dot_col = "#34D399" if active else "#475569"

        draw.rounded_rectangle([rx + 40, stage_y, rx + rw - 40, stage_y + 48], radius=10, fill=box_bg if active else "#131C2E", outline=border_col, width=1)
        draw.ellipse([rx + 60, stage_y + 16, rx + 76, stage_y + 32], fill=dot_col)
        draw.text((rx + 90, stage_y + 14), f"STAGE {num}: {title.upper()}", fill=txt_col, font=f_section)
        draw.text((rx + 490, stage_y + 16), f"• {desc}", fill="#A7F3D0" if active else "#64748B", font=f_mono)

        stage_y += 58

    draw.line([(rx + 40, 530), (rx + rw - 40, 530)], fill="#1E293B", width=1)

    # -------------------------------------------------------------
    # CASE COMPARISON: THE LOTTERY BENCHMARK CASE
    # -------------------------------------------------------------
    draw.text((rx + 40, 550), "BENCHMARK COMPARISON: UNVERIFIED CLAIM VS. ADVANCE-FEE DEMAND", fill="#F8FAFC", font=f_section)

    card_w = (rw - 100) // 2

    # Card A: Unverified Lottery Claim
    card_a_x = rx + 40
    card_a_y = 590
    card_h = 330
    draw.rounded_rectangle([card_a_x, card_a_y, card_a_x + card_w, card_a_y + card_h], radius=16, fill="#182235", outline="#D97706", width=2)
    draw.rounded_rectangle([card_a_x + 20, card_a_y + 20, card_a_x + 180, card_a_y + 50], radius=8, fill="#451A03", outline="#D97706", width=1)
    draw.text((card_a_x + 30, card_a_y + 25), "CASE A: UNVERIFIED CLAIM", fill="#FBBF24", font=f_badge)

    draw.text((card_a_x + 20, card_a_y + 68), "INPUT PAYLOAD:", fill="#94A3B8", font=f_mono)
    draw.rounded_rectangle([card_a_x + 20, card_a_y + 92, card_a_x + card_w - 20, card_a_y + 140], radius=8, fill="#0F172A")
    draw.text((card_a_x + 32, card_a_y + 104), "\"Congratulations! You have won\na lottery prize of ₹10,000.\"", fill="#F1F5F9", font=f_body)

    draw.text((card_a_x + 20, card_a_y + 155), "CONTEXTUAL ENGINE ANALYSIS:", fill="#94A3B8", font=f_mono)
    draw.text((card_a_x + 25, card_a_y + 180), "• Narrative Pretext: Prize / Lottery Claim", fill="#CBD5E1", font=f_body)
    draw.text((card_a_x + 25, card_a_y + 205), "• Requested Action: NONE (No advance fee / No link)", fill="#6EE7B7", font=f_body)
    draw.text((card_a_x + 25, card_a_y + 230), "• Legacy Keyword Shield: False Positive (100% Critical)", fill="#F87171", font=f_mono)

    draw.rounded_rectangle([card_a_x + 20, card_a_y + 265, card_a_x + card_w - 20, card_a_y + 310], radius=10, fill="#292524", outline="#D97706", width=1)
    draw.text((card_a_x + 32, card_a_y + 276), "SUSAGI VERDICT: REVIEW / CAUTIOUS", fill="#FBBF24", font=f_section)

    # Card B: Advance-Fee Extortion Trap
    card_b_x = rx + 40 + card_w + 20
    card_b_y = 590
    draw.rounded_rectangle([card_b_x, card_b_y, card_b_x + card_w, card_b_y + card_h], radius=16, fill="#24141E", outline="#DC2626", width=2)
    draw.rounded_rectangle([card_b_x + 20, card_b_y + 20, card_b_x + 200, card_b_y + 50], radius=8, fill="#450A0A", outline="#DC2626", width=1)
    draw.text((card_b_x + 30, card_b_y + 25), "CASE B: ADVANCE-FEE TRAP", fill="#FCA5A5", font=f_badge)

    draw.text((card_b_x + 20, card_b_y + 68), "INPUT PAYLOAD:", fill="#94A3B8", font=f_mono)
    draw.rounded_rectangle([card_b_x + 20, card_b_y + 92, card_b_x + card_w - 20, card_b_y + 140], radius=8, fill="#0F172A")
    draw.text((card_b_x + 32, card_b_y + 104), "\"Pay ₹500 processing fee first to\nrelease your ₹10,000 prize.\"", fill="#F1F5F9", font=f_body)

    draw.text((card_b_x + 20, card_b_y + 155), "CONTEXTUAL ENGINE ANALYSIS:", fill="#94A3B8", font=f_mono)
    draw.text((card_b_x + 25, card_b_y + 180), "• Narrative Pretext: Prize / Lottery Claim", fill="#CBD5E1", font=f_body)
    draw.text((card_b_x + 25, card_b_y + 205), "• Requested Action: ADVANCE FEE DEBIT (₹500)", fill="#EF4444", font=f_body)
    draw.text((card_b_x + 25, card_b_y + 230), "• Evidence: Financial Coercion, Reverse-Debit Trap", fill="#FCA5A5", font=f_mono)

    draw.rounded_rectangle([card_b_x + 20, card_b_y + 265, card_b_x + card_w - 20, card_b_y + 310], radius=10, fill="#3F1212", outline="#DC2626", width=1)
    draw.text((card_b_x + 32, card_b_y + 276), "SUSAGI VERDICT: HIGH RISK / CONFIRMED FRAUD", fill="#F87171", font=f_section)

    # -------------------------------------------------------------
    # SUBTITLES OVERLAY AT BOTTOM
    # -------------------------------------------------------------
    active_sub = ""
    if t >= intro_pad and t <= (intro_pad + narration_duration):
        sub_time = t - intro_pad
        for start_s, end_s, text in sub_cues:
            if start_s <= sub_time <= end_s:
                active_sub = text
                break

    if active_sub:
        bbox = draw.textbbox((0, 0), active_sub, font=f_subtitles)
        tw = bbox[2] - bbox[0]
        th = bbox[3] - bbox[1]
        pill_w = tw + 48
        pill_h = th + 24
        pill_x = (w - pill_w) // 2
        pill_y = h - 90

        draw.rounded_rectangle([pill_x, pill_y, pill_x + pill_w, pill_y + pill_h], radius=14, fill="#0F172A", outline="#334155", width=1)
        draw.text((pill_x + 24, pill_y + 10), active_sub, fill="#F8FAFC", font=f_subtitles)

    frame_path = os.path.join(frames_dir, f"frame_{i:04d}.png")
    frame.save(frame_path)

    if i % 60 == 0 or i == total_frames - 1:
        print(f"Rendered frame {i}/{total_frames} ({i/total_frames*100:.1f}%)")

print("All frames rendered successfully.")

# Mux frames with audio into MP4
out_mp4 = os.path.join(base_dir, "exports", "Scene_03_Message_Shield_Cinematic.mp4")

cmd = [
    ffmpeg_exe,
    "-y",
    "-framerate", str(fps),
    "-i", os.path.join(frames_dir, "frame_%04d.png"),
    "-itsoffset", str(intro_pad),
    "-i", audio_path,
    "-c:v", "libx264",
    "-preset", "fast",
    "-crf", "18",
    "-pix_fmt", "yuv420p",
    "-c:a", "aac",
    "-b:a", "192k",
    "-shortest",
    out_mp4
]

print("Encoding Scene 03 MP4 via ffmpeg...")
subprocess.run(cmd, check=True)
print("Scene 03 MP4 saved to:", out_mp4)
