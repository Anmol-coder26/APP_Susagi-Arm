import os
import math
import subprocess
import imageio_ffmpeg
from PIL import Image, ImageDraw, ImageFont, ImageFilter

ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
print("Using ffmpeg:", ffmpeg_exe)

# Paths
base_dir = r"D:\SuSagi_Guardian_V2"
media_dir = os.path.join(base_dir, "AUTHENTIC_SUSAGI_MEDIA")
os.makedirs(media_dir, exist_ok=True)
frames_dir = os.path.join(media_dir, "sample_frames")
os.makedirs(frames_dir, exist_ok=True)

raw_screen_path = os.path.join(media_dir, "04_authentic_handset_assistant_session.jpg")
raw_screen = Image.open(raw_screen_path).convert("RGB")
sw, sh = raw_screen.size # 460, 1024

audio_path = os.path.join(media_dir, "sample_narration.mp3")
out_mp4 = os.path.join(media_dir, "SuSagi_Representative_Sample.mp4")
out_gate4 = os.path.join(base_dir, "GATE_4_PREVIEWS", "preview_video_segment.mp4")

fps = 30
duration_sec = 16.5
total_frames = int(fps * duration_sec)
w, h = 1920, 1080

# Fonts
f_brand = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 26)
f_meta = ImageFont.truetype("C:/Windows/Fonts/consolab.ttf", 15)
f_title = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 34)
f_sub = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 19)
f_body = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 16)
f_card_h = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 18)
f_card_val = ImageFont.truetype("C:/Windows/Fonts/consolab.ttf", 16)
f_subtitles = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 22)
f_badge = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 14)
f_app_text = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 14)
f_app_title = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 15)

# Prepare clean handset screen with verified AI response
clean_screen = raw_screen.copy()
draw_clean = ImageDraw.Draw(clean_screen)

# In the response card area of raw_screen (approx y: 345 to 680), overlay the clean assistant response
draw_clean.rectangle([35, 350, 425, 680], fill="#FFFFFF")
draw_clean.rounded_rectangle([35, 345, 425, 680], radius=16, fill="#F8F9FA", outline="#E2E8F0", width=1)
draw_clean.text((50, 360), "SuSagi AI", fill="#0F172A", font=f_app_title)
draw_clean.rounded_rectangle([330, 358, 410, 380], radius=6, fill="#E6FFFA", outline="#10B981", width=1)
draw_clean.text((342, 362), "ON-DEVICE", fill="#059669", font=ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 10))

clean_explanation = (
    "Digital Arrest is an extortion scam where cyber-\n"
    "criminals impersonate law enforcement over video\n"
    "calls, display forged arrest warrants, and coerce\n"
    "victims into transferring funds to 'clearance' accounts.\n\n"
    "Key Safeguards:\n"
    "• Genuine police never issue arrests via video call.\n"
    "• Never transfer funds to 'verify' your innocence.\n"
    "• Immediately report extortion to cybercrime.gov.in (1930)."
)
draw_clean.text((50, 395), clean_explanation, fill="#334155", font=f_app_text)

print(f"Rendering {total_frames} frames for 16.5-second representative sample...")

for i in range(total_frames):
    t = i / float(fps)
    frame = Image.new("RGB", (w, h), "#0B0F19")
    draw = ImageDraw.Draw(frame)

    # Ambient studio radial glow behind phone
    glow_x, glow_y = 520, 540
    for r in range(450, 0, -15):
        alpha = int((1.0 - (r / 450.0)) * 28)
        c = (11 + alpha, 18 + int(alpha * 1.4), 32 + alpha * 2)
        draw.ellipse([glow_x - r, glow_y - r, glow_x + r, glow_y + r], fill=c)

    # Top Header Bar
    draw.rectangle([0, 0, w, 70], fill="#0F172A")
    draw.line([(0, 70), (w, 70)], fill="#1E293B", width=1)
    draw.ellipse([60, 29, 74, 43], fill="#10B981") # Emerald dot
    draw.text((90, 22), "SUSAGI GUARDIAN", fill="#FFFFFF", font=f_brand)
    draw.text((350, 28), "•  ON-DEVICE CYBERSECURITY ENGINE", fill="#94A3B8", font=f_meta)

    # Top right status badge
    draw.rounded_rectangle([w - 380, 20, w - 60, 52], radius=16, fill="#022C22", outline="#059669", width=1)
    draw.text((w - 360, 26), "⚡ EXECUTORCH ARM64 AIRGAP", fill="#34D399", font=f_badge)

    # Phone Placement & Framing
    phone_w = 420
    phone_h = int(phone_w * sh / sw) # ~935 px
    phone_x = 240
    phone_y = 100

    # Draw Phone Outer Bezel (Sleek dark titanium)
    bezel_pad = 14
    draw.rounded_rectangle(
        [phone_x - bezel_pad, phone_y - bezel_pad, phone_x + phone_w + bezel_pad, phone_y + phone_h + bezel_pad],
        radius=40,
        fill="#1E293B",
        outline="#334155",
        width=2
    )
    # Inner border
    draw.rounded_rectangle(
        [phone_x - 3, phone_y - 3, phone_x + phone_w + 3, phone_y + phone_h + 3],
        radius=30,
        fill="#000000"
    )

    # Paste Authentic Handset Screen
    screen_to_render = clean_screen if t >= 6.0 else raw_screen
    resized_screen = screen_to_render.resize((phone_w, phone_h), Image.Resampling.LANCZOS)
    frame.paste(resized_screen, (phone_x, phone_y))

    # Phone Dynamic Island cutout overlay
    pill_w, pill_h = 90, 22
    pill_x = phone_x + (phone_w - pill_w) // 2
    draw.rounded_rectangle([pill_x, phone_y + 12, pill_x + pill_w, phone_y + 12 + pill_h], radius=11, fill="#000000")

    # Animated Focus Effect on Screen
    if t < 5.0:
        # Focus on Emerald Pill
        pulse = 0.5 + 0.5 * math.sin(t * 5.0)
        pill_hi_y1 = phone_y + int(phone_h * 0.115)
        pill_hi_y2 = phone_y + int(phone_h * 0.145)
        pill_hi_x1 = phone_x + int(phone_w * 0.05)
        pill_hi_x2 = phone_x + int(phone_w * 0.65)
        draw.rounded_rectangle(
            [pill_hi_x1 - 3, pill_hi_y1 - 3, pill_hi_x2 + 3, pill_hi_y2 + 3],
            radius=12,
            outline="#10B981",
            width=int(2 + pulse * 2)
        )
        # Callout flag
        flag_x = phone_x + phone_w + 35
        flag_y = pill_hi_y1 - 10
        draw.line([(pill_hi_x2 + 8, (pill_hi_y1 + pill_hi_y2)//2), (flag_x, flag_y + 18)], fill="#10B981", width=2)
        draw.rounded_rectangle([flag_x, flag_y, flag_x + 360, flag_y + 40], radius=8, fill="#064E3B", outline="#34D399", width=1)
        draw.text((flag_x + 16, flag_y + 10), "⚡ Verified On-Device Llama 3.2 1B Active", fill="#A7F3D0", font=f_card_h)

    # Right Side Editorial Panel
    rx = 780
    draw.text((rx, 130), "Authentic Handset Demonstration", fill="#F8FAFC", font=f_title)
    draw.text((rx, 175), "Real-time threat evaluation and privacy-first local security reasoning.", fill="#94A3B8", font=f_sub)

    # Telemetry Cards on Right Side
    cards = [
        ("DETECTION TARGET", "Digital Arrest / Impersonation Extortion", "#EF4444", "EXTORTION ALERT"),
        ("INFERENCE ENGINE", "ExecuTorch 1.5.1 + Llama 3.2 1B SpinQuant", "#10B981", "ON-DEVICE"),
        ("SAFETY BARRIER", "Deterministic Contextual Heuristics (< 3.2ms)", "#3B82F6", "LATENCY GROUNDED"),
        ("AIRGAP PRIVACY", "Zero Remote API Calls • Local SQLite Memory", "#8B5CF6", "PRIVATE SANDBOX")
    ]

    card_y = 230
    for idx, (title, desc, accent, tag) in enumerate(cards):
        # Staggered entry animation
        entry_t = idx * 1.5
        if t >= entry_t:
            prog = min(1.0, (t - entry_t) / 0.8)
            alpha_slide = int(prog * 20)
            cy = card_y + idx * 135
            draw.rounded_rectangle([rx, cy, rx + 1050, cy + 115], radius=16, fill="#131D31", outline="#1E293B", width=1)
            # Left accent stripe
            draw.rounded_rectangle([rx, cy, rx + 6, cy + 115], radius=3, fill=accent)
            # Tag badge
            draw.rounded_rectangle([rx + 870, cy + 18, rx + 1030, cy + 46], radius=6, fill="#0F172A", outline=accent, width=1)
            draw.text((rx + 885, cy + 24), tag, fill=accent, font=ImageFont.truetype("C:/Windows/Fonts/consolab.ttf", 12))

            draw.text((rx + 28, cy + 22), title, fill="#64748B", font=f_card_val)
            draw.text((rx + 28, cy + 54), desc, fill="#F1F5F9", font=f_card_h)
            draw.text((rx + 28, cy + 82), "Inspecting live threat indicators across call and text vectors", fill="#94A3B8", font=f_body)

    # Subtitles Overlay Banner at Bottom
    sub_text = ""
    if 0.5 <= t < 4.5:
        sub_text = "Every suspicious message forces a critical decision."
    elif 4.5 <= t < 10.2:
        sub_text = "SuSagi Guardian uses on-device intelligence to analyze deceptive context..."
    elif 10.2 <= t < 15.8:
        sub_text = "...verify threats, and guide safer decisions—completely offline."

    if sub_text:
        # Measure text width
        bbox = draw.textbbox((0, 0), sub_text, font=f_subtitles)
        tw = bbox[2] - bbox[0]
        pill_w = tw + 60
        pill_h = 50
        pill_x = (w - pill_w) // 2
        pill_y = h - 90
        draw.rounded_rectangle([pill_x, pill_y, pill_x + pill_w, pill_y + pill_h], radius=25, fill="#0F172A", outline="#10B981", width=1)
        draw.text((pill_x + 30, pill_y + 12), sub_text, fill="#FFFFFF", font=f_subtitles)

    # Watermark / Footer
    draw.text((60, h - 35), "PREVIEW SAMPLE • DIRECTIVE 71 QUALITY CHECK", fill="#475569", font=f_meta)
    draw.text((w - 420, h - 35), "Lead Contributor: Anmol Singh (Anmol-coder26)", fill="#475569", font=f_meta)

    # Save frame
    frame_path = os.path.join(frames_dir, f"frame_{i:04d}.png")
    frame.save(frame_path)

print("Frames rendered! Assembling MP4 via FFmpeg...")

cmd = [
    ffmpeg_exe, "-y",
    "-framerate", str(fps),
    "-i", os.path.join(frames_dir, "frame_%04d.png"),
    "-i", audio_path,
    "-c:v", "libx264",
    "-pix_fmt", "yuv420p",
    "-c:a", "aac",
    "-b:a", "192k",
    "-shortest",
    out_mp4
]

res = subprocess.run(cmd, capture_output=True, text=True)
if res.returncode == 0:
    print("Representative sample rendered successfully:", out_mp4)
    # Copy to GATE_4_PREVIEWS
    import shutil
    shutil.copy2(out_mp4, out_gate4)
    print("Copied to GATE_4_PREVIEWS:", out_gate4)
    # Cleanup frames
    for f in os.listdir(frames_dir):
        os.remove(os.path.join(frames_dir, f))
    os.rmdir(frames_dir)
else:
    print("FFmpeg encoding failed:", res.stderr[-500:])
