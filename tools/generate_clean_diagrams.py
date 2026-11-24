import os
from PIL import Image, ImageDraw, ImageFont

base_dir = r"D:\SuSagi_Guardian_V2"
out_dir = os.path.join(base_dir, "SUSAGI_FINAL_SUBMISSION", "Technical_Diagrams")
os.makedirs(out_dir, exist_ok=True)

w, h = 1920, 1080

# Fonts
f_title = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 34)
f_sub = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 20)
f_card_title = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 22)
f_section = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 18)
f_body = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 16)
f_bold = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 16)
f_mono = ImageFont.truetype("C:/Windows/Fonts/consolab.ttf", 15)
f_badge = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 13)

def create_base(title, subtitle):
    img = Image.new("RGB", (w, h), "#0A0E17")
    draw = ImageDraw.Draw(img)

    # Subtle ambient background grid
    for x in range(0, w, 60):
        draw.line([(x, 0), (x, h)], fill="#111827", width=1)
    for y in range(0, h, 60):
        draw.line([(0, y), (w, y)], fill="#111827", width=1)

    # Top Brand Header
    draw.rectangle([0, 0, w, 80], fill="#0F172A")
    draw.line([(0, 80), (w, 80)], fill="#1E293B", width=1)
    draw.ellipse([60, 32, 76, 48], fill="#10B981")
    draw.text((95, 26), "SUSAGI GUARDIAN", fill="#FFFFFF", font=ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 26))
    draw.text((370, 31), "•  TECHNICAL ARCHITECTURE & SPECIFICATION", fill="#94A3B8", font=f_mono)

    # Diagram Title & Subtitle
    draw.text((60, 110), title, fill="#FFFFFF", font=f_title)
    draw.text((60, 155), subtitle, fill="#94A3B8", font=f_sub)
    draw.line([(60, 195), (w - 60, 195)], fill="#1E293B", width=1)

    # Footer
    draw.line([(60, h - 60), (w - 60, h - 60)], fill="#1E293B", width=1)
    draw.text((60, h - 45), "SuSagi Guardian V2 • 100% On-Device Cybersecurity • Zero Server Inference Dependency", fill="#64748B", font=f_mono)
    draw.text((w - 450, h - 45), "Evidence-Based & Context-Aware Engine", fill="#10B981", font=f_mono)

    return img, draw

# ==============================================================================
# DIAGRAM C: Context-Aware Scam Classification Engine
# ==============================================================================
def render_diagram_c():
    img, draw = create_base(
        "Diagram C: Context-Aware Scam Classification Engine",
        "Multi-stage pipeline isolating intent and requested actions from unverified narrative pretexts"
    )

    box_y = 260
    box_h = 580

    # Box 1: Raw Input Ingestion
    b1_x = 80
    b1_w = 400
    draw.rounded_rectangle([b1_x, box_y, b1_x + b1_w, box_y + box_h], radius=20, fill="#0F172A", outline="#334155", width=2)
    draw.rounded_rectangle([b1_x + 25, box_y + 25, b1_x + 220, box_y + 60], radius=8, fill="#1E293B")
    draw.text((b1_x + 35, box_y + 32), "STAGE 1: INGESTION", fill="#94A3B8", font=f_badge)
    draw.text((b1_x + 25, box_y + 80), "Multi-Channel Input", fill="#FFFFFF", font=f_card_title)
    
    desc_1 = [
        "Ingests raw untrusted data:",
        "• Incoming SMS & messaging alerts",
        "• Intercepted HTTP/HTTPS URLs",
        "• Scanned UPI QR payloads",
        "• 16 kHz PCM call audio streams",
        "",
        "Normalization Pipeline:",
        "• Unicode NFKC canonical folding",
        "• Zero-width & invisible character strip",
        "• Levenshtein homoglyph resolution",
        "• Phone/UPI token isolation"
    ]
    cur_y = box_y + 130
    for line in desc_1:
        draw.text((b1_x + 25, cur_y), line, fill="#CBD5E1", font=f_body)
        cur_y += 28

    # Arrow 1 -> 2
    draw.line([(b1_x + b1_w + 10, box_y + 250), (b1_x + b1_w + 80, box_y + 250)], fill="#10B981", width=3)
    draw.polygon([(b1_x + b1_w + 80, box_y + 250), (b1_x + b1_w + 65, box_y + 242), (b1_x + b1_w + 65, box_y + 258)], fill="#10B981")

    # Box 2: Contextual Analysis (Intent + Action)
    b2_x = 570
    b2_w = 680
    draw.rounded_rectangle([b2_x, box_y, b2_x + b2_w, box_y + box_h], radius=20, fill="#0F172A", outline="#10B981", width=2)
    draw.rounded_rectangle([b2_x + 25, box_y + 25, b2_x + 240, box_y + 60], radius=8, fill="#064E3B", outline="#10B981", width=1)
    draw.text((b2_x + 35, box_y + 32), "STAGE 2: CONTEXT ENGINE", fill="#6EE7B7", font=f_badge)
    draw.text((b2_x + 25, box_y + 80), "Dual-Track Context Disambiguation", fill="#FFFFFF", font=f_card_title)

    # Subcard 2A: Pretext Track
    c2a_x = b2_x + 25
    c2a_y = box_y + 130
    c2a_w = b2_w - 50
    draw.rounded_rectangle([c2a_x, c2a_y, c2a_x + c2a_w, c2a_y + 180], radius=14, fill="#182235", outline="#D97706", width=1)
    draw.text((c2a_x + 20, c2a_y + 15), "Track A: Narrative Pretext Recognition", fill="#FBBF24", font=f_bold)
    pretexts = [
        "• Prize & Lottery Claims (e.g. KBC, Lucky Draw)",
        "• Authority Coercion (e.g. CBI, Police, Digital Arrest)",
        "• Utility Disconnection (e.g. Electricity, Bijli bill, SIM KYC)",
        "• Delivery & Courier Impersonation (e.g. IndiaPost, FedEx parcel)"
    ]
    py = c2a_y + 45
    for p in pretexts:
        draw.text((c2a_x + 20, py), p, fill="#CBD5E1", font=f_body)
        py += 26

    # Subcard 2B: Action Extraction Track
    c2b_y = c2a_y + 200
    draw.rounded_rectangle([c2a_x, c2b_y, c2a_x + c2a_w, c2b_y + 200], radius=14, fill="#24141E", outline="#DC2626", width=1)
    draw.text((c2a_x + 20, c2b_y + 15), "Track B: Requested Financial Action Extraction", fill="#F87171", font=f_bold)
    actions = [
        "• Advance Fee / Verification Transfer (e.g. 'Pay ₹500 first to release prize')",
        "• Reverse-Debit UPI Request (e.g. 'Approve request / enter PIN to receive money')",
        "• OTP / Credential Harvesting (e.g. 'Share OTP immediately to prevent blockage')",
        "• Malicious APK Sideloading (e.g. 'Install support app via suspicious link')"
    ]
    ay = c2b_y + 45
    for a in actions:
        draw.text((c2a_x + 20, ay), a, fill="#CBD5E1", font=f_body)
        ay += 26

    # Arrow 2 -> 3
    draw.line([(b2_x + b2_w + 10, box_y + 250), (b2_x + b2_w + 80, box_y + 250)], fill="#10B981", width=3)
    draw.polygon([(b2_x + b2_w + 80, box_y + 250), (b2_x + b2_w + 65, box_y + 242), (b2_x + b2_w + 65, box_y + 258)], fill="#10B981")

    # Box 3: Qualitative Risk Synthesis
    b3_x = 1340
    b3_w = 500
    draw.rounded_rectangle([b3_x, box_y, b3_x + b3_w, box_y + box_h], radius=20, fill="#0F172A", outline="#334155", width=2)
    draw.rounded_rectangle([b3_x + 25, box_y + 25, b3_x + 220, box_y + 60], radius=8, fill="#1E293B")
    draw.text((b3_x + 35, box_y + 32), "STAGE 3: VERDICT", fill="#94A3B8", font=f_badge)
    draw.text((b3_x + 25, box_y + 80), "Qualitative Risk Bands", fill="#FFFFFF", font=f_card_title)

    # 3 Qualitative Verdict Cards (NO percentages!)
    v1_y = box_y + 130
    draw.rounded_rectangle([b3_x + 25, v1_y, b3_x + b3_w - 25, v1_y + 120], radius=12, fill="#064E3B", outline="#10B981", width=1)
    draw.text((b3_x + 40, v1_y + 15), "SAFE / BENIGN", fill="#34D399", font=f_bold)
    draw.text((b3_x + 40, v1_y + 45), "• Educational context or standard verification", fill="#E2E8F0", font=f_body)
    draw.text((b3_x + 40, v1_y + 75), "• No predatory financial demands detected", fill="#A7F3D0", font=f_body)

    v2_y = v1_y + 140
    draw.rounded_rectangle([b3_x + 25, v2_y, b3_x + b3_w - 25, v2_y + 120], radius=12, fill="#451A03", outline="#D97706", width=1)
    draw.text((b3_x + 40, v2_y + 15), "REVIEW / CAUTIOUS", fill="#FBBF24", font=f_bold)
    draw.text((b3_x + 40, v2_y + 45), "• Unverified claims without explicit financial demands", fill="#E2E8F0", font=f_body)
    draw.text((b3_x + 40, v2_y + 75), "• User advised to verify through official channels", fill="#FDE68A", font=f_body)

    v3_y = v2_y + 140
    draw.rounded_rectangle([b3_x + 25, v3_y, b3_x + b3_w - 25, v3_y + 120], radius=12, fill="#450A0A", outline="#DC2626", width=1)
    draw.text((b3_x + 40, v3_y + 15), "HIGH RISK / CONFIRMED FRAUD", fill="#F87171", font=f_bold)
    draw.text((b3_x + 40, v3_y + 45), "• Coercion combined with advance-fee or debit demand", fill="#E2E8F0", font=f_body)
    draw.text((b3_x + 40, v3_y + 75), "• Safety barrier activates with immediate safety guidance", fill="#FECACA", font=f_body)

    img.save(os.path.join(out_dir, "Diagram_C_Contextual_Engine.png"))
    print("Rendered Diagram C successfully.")

# ==============================================================================
# DIAGRAM D: Contextual Disambiguation — The Lottery Benchmark Case
# ==============================================================================
def render_diagram_d():
    img, draw = create_base(
        "Diagram D: Contextual Disambiguation — The Lottery Benchmark Case",
        "Demonstrating why keyword-only filters fail and how contextual action analysis eliminates false positives"
    )

    box_y = 260
    box_w = 860
    box_h = 480

    # Case A: Unverified Lottery Claim
    ca_x = 80
    draw.rounded_rectangle([ca_x, box_y, ca_x + box_w, box_y + box_h], radius=20, fill="#0F172A", outline="#D97706", width=2)
    draw.rounded_rectangle([ca_x + 30, box_y + 30, ca_x + 280, box_y + 70], radius=10, fill="#451A03", outline="#D97706", width=1)
    draw.text((ca_x + 45, box_y + 38), "CASE A: UNVERIFIED CLAIM", fill="#FBBF24", font=f_section)

    draw.text((ca_x + 30, box_y + 90), "INPUT MESSAGE:", fill="#94A3B8", font=f_mono)
    draw.rounded_rectangle([ca_x + 30, box_y + 115, ca_x + box_w - 30, box_y + 175], radius=10, fill="#182235")
    draw.text((ca_x + 45, box_y + 130), "\"Congratulations! You have won a lottery of ₹10,000 from KBC.\"", fill="#F1F5F9", font=f_card_title)

    draw.text((ca_x + 30, box_y + 200), "ENGINE EVALUATION:", fill="#94A3B8", font=f_mono)
    ca_lines = [
        "• Narrative Pretext: Prize / Lottery Announcement",
        "• Requested Action: NONE (No advance fee, no link, no OTP demand)",
        "• Legacy Keyword System: Flaggable (100% Critical Alert) -> FALSE POSITIVE",
        "• SuSagi Contextual Engine: Recognizes unverified claim without harmful action",
        "• Qualitative Risk Band: REVIEW / SUSPICIOUS (Cautious advisory, no panic)"
    ]
    cy = box_y + 230
    for line in ca_lines:
        color = "#F87171" if "FALSE POSITIVE" in line else "#CBD5E1"
        if "REVIEW / SUSPICIOUS" in line: color = "#FBBF24"
        draw.text((ca_x + 30, cy), line, fill=color, font=f_body)
        cy += 30

    draw.rounded_rectangle([ca_x + 30, box_y + 400, ca_x + box_w - 30, box_y + 455], radius=12, fill="#292524", outline="#D97706", width=1)
    draw.text((ca_x + 50, box_y + 415), "OUTCOME: User alerted to verify independently; notification fatigue prevented.", fill="#FDE68A", font=f_bold)

    # Case B: Advance-Fee Extortion Trap
    cb_x = 980
    draw.rounded_rectangle([cb_x, box_y, cb_x + box_w, box_y + box_h], radius=20, fill="#0F172A", outline="#DC2626", width=2)
    draw.rounded_rectangle([cb_x + 30, box_y + 30, cb_x + 300, box_y + 70], radius=10, fill="#450A0A", outline="#DC2626", width=1)
    draw.text((cb_x + 45, box_y + 38), "CASE B: ADVANCE-FEE TRAP", fill="#FCA5A5", font=f_section)

    draw.text((cb_x + 30, box_y + 90), "INPUT MESSAGE:", fill="#94A3B8", font=f_mono)
    draw.rounded_rectangle([cb_x + 30, box_y + 115, cb_x + box_w - 30, box_y + 175], radius=10, fill="#24141E")
    draw.text((cb_x + 45, box_y + 130), "\"Pay ₹500 registration fee first to release your ₹10,000 lottery prize.\"", fill="#F1F5F9", font=f_card_title)

    draw.text((cb_x + 30, box_y + 200), "ENGINE EVALUATION:", fill="#94A3B8", font=f_mono)
    cb_lines = [
        "• Narrative Pretext: Prize / Lottery Announcement",
        "• Requested Action: EXPLICIT ADVANCE-FEE DEBIT (₹500 financial demand)",
        "• Structured Evidence: Financial Coercion, Reverse-Debit Trap, Urgency",
        "• SuSagi Contextual Engine: Isolates predatory advance-fee pattern",
        "• Qualitative Risk Band: HIGH RISK / CONFIRMED FRAUD (Immediate Block)"
    ]
    cy = box_y + 230
    for line in cb_lines:
        color = "#F87171" if "HIGH RISK" in line or "EXPLICIT" in line else "#CBD5E1"
        draw.text((cb_x + 30, cy), line, fill=color, font=f_body)
        cy += 30

    draw.rounded_rectangle([cb_x + 30, box_y + 400, cb_x + box_w - 30, box_y + 455], radius=12, fill="#3F1212", outline="#DC2626", width=1)
    draw.text((cb_x + 50, box_y + 415), "OUTCOME: Immediate threat barrier; dial 1930 & cybercrime reporting actions offered.", fill="#FECACA", font=f_bold)

    # Bottom Architectural Takeaway Box
    bot_y = 780
    draw.rounded_rectangle([80, bot_y, w - 80, bot_y + 140], radius=16, fill="#064E3B", outline="#10B981", width=2)
    draw.text((110, bot_y + 25), "CORE ENGINEERING DISTINCTION: CONTEXTUAL EVIDENCE OVER KEYWORDS", fill="#34D399", font=f_section)
    takeaway = (
        "Legacy keyword engines cannot differentiate harmless talk from extortion traps, causing users to ignore repeated false alarms. "
        "SuSagi's contextual engine evaluates the combination of narrative pretext and explicit financial demand, maintaining 100% detection "
        "on confirmed threats while eliminating notification fatigue."
    )
    draw.text((110, bot_y + 65), takeaway, fill="#F0FDF4", font=f_body)

    img.save(os.path.join(out_dir, "Diagram_D_Lottery_Case_Comparison.png"))
    print("Rendered Diagram D successfully.")

# ==============================================================================
# DIAGRAM E: On-Device ExecuTorch & Llama 3.2 1B Architecture
# ==============================================================================
def render_diagram_e():
    img, draw = create_base(
        "Diagram E: On-Device ExecuTorch & Llama 3.2 1B Native Runtime",
        "ARM64 native execution pipeline with KV cache reset, sequence length expansion, and defensive output validation"
    )

    box_y = 260
    stages = [
        ("User Inquiries", "General conversational queries or structured security findings from local inspection tools.", "#1E293B", "#334155"),
        ("Conversation Manager", "Bounded multi-turn memory (max 10 turns), Llama 3.2 Instruct header formatting, KV cache reset.", "#064E3B", "#10B981"),
        ("ExecuTorch Native Runtime", "XNNPACK-optimized execution via libexecutorch.so & libllama_jni.so. SeqLen=2048, echo=false.", "#1E1B4B", "#6366F1"),
        ("Defensive Validation", "Sanitizes tokens, discards prompt echo, validates completed generation before UI rendering.", "#451A03", "#D97706"),
        ("Clean Response", "Rendered in Compose UI without token streaming leakage or hallucinated persona.", "#064E3B", "#10B981"),
    ]

    cx = 80
    cw = (w - 160 - (len(stages) - 1) * 30) // len(stages)
    for idx, (title, desc, bg, border) in enumerate(stages):
        bx = cx + idx * (cw + 30)
        draw.rounded_rectangle([bx, box_y, bx + cw, box_y + 360], radius=16, fill="#0F172A", outline=border, width=2)
        draw.rounded_rectangle([bx + 15, box_y + 20, bx + cw - 15, box_y + 55], radius=8, fill=bg)
        draw.text((bx + 25, box_y + 28), f"STEP 0{idx+1}", fill="#FFFFFF", font=f_badge)
        draw.text((bx + 15, box_y + 75), title, fill="#FFFFFF", font=f_section)
        draw.line([(bx + 15, box_y + 140), (bx + cw - 15, box_y + 140)], fill="#1E293B", width=1)
        draw.text((bx + 15, box_y + 160), desc, fill="#CBD5E1", font=f_body)

        if idx < len(stages) - 1:
            arr_x = bx + cw + 5
            arr_y = box_y + 180
            draw.line([(arr_x, arr_y), (arr_x + 20, arr_y)], fill="#10B981", width=3)

    # Core Mode Fallback Card below
    fb_y = 660
    draw.rounded_rectangle([80, fb_y, w - 80, fb_y + 260], radius=20, fill="#182235", outline="#38BDF8", width=2)
    draw.text((115, fb_y + 25), "TRUTHFUL REPORTING: SUSAGI CORE MODE (UNPROVISIONED WEIGHTS)", fill="#38BDF8", font=f_section)
    fb_desc = (
        "When the ~1.08 GB Llama model file is not provisioned or active, SuSagi explicitly reports Core Mode rather than simulating "
        "fake token streaming or invented TPS statistics. In Core Mode, all deterministic security tools (Message Shield, Link Shield, "
        "UPI Inspector, QR Shield) remain 100% operational, guaranteeing authoritative security guidance without cloud reliance."
    )
    draw.text((115, fb_y + 70), fb_desc, fill="#F1F5F9", font=f_body)

    # Key Engineering Safeguards in Build D74
    draw.text((115, fb_y + 140), "BUILD D74 PRODUCTION SAFEGUARDS:", fill="#6EE7B7", font=f_bold)
    safeguards = [
        "1. module.resetContext() executed before every generation to purge residual KV cache across turns.",
        "2. seqLen expanded to 2048 to prevent prefill prompt budget starvation.",
        "3. Visible token streaming disabled in UI until validated non-streaming generation completes successfully.",
        "4. Strict prompt-echo filter strips any echoed prefill tokens or internal control markers (<|...|>)."
    ]
    sy = fb_y + 170
    for sg in safeguards:
        draw.text((115, sy), sg, fill="#CBD5E1", font=f_mono)
        sy += 24

    img.save(os.path.join(out_dir, "Diagram_E_ExecuTorch_Llama_Path.png"))
    print("Rendered Diagram E successfully.")

render_diagram_c()
render_diagram_d()
render_diagram_e()
