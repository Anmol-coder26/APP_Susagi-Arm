import os
import subprocess
import time

ARTIFACT_DIR = r"C:\Users\Tanya\.gemini\antigravity\brain\dfac2439-3c92-47dc-81ce-97c94d3b87db"
EDGE_PATH = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"

# =============================================================================
# 1. HTML TEMPLATE: RADICALLY NEW CONSUMER HOME DASHBOARD (NO GRIDS, NO NAVY)
# =============================================================================
HTML_HOME = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
* { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Segoe UI", Roboto, sans-serif; }
body { background: #fbfbfb; color: #111111; width: 412px; height: 890px; overflow: hidden; display: flex; flex-direction: column; }
.status-bar { display: flex; justify-content: space-between; padding: 12px 22px 6px 22px; font-size: 13px; font-weight: 600; color: #666666; background: #ffffff; }

/* Top App Header: Profile + Emergency Contact Chip */
.app-header { background: #ffffff; padding: 12px 20px 14px 20px; border-bottom: 1px solid #f0f0f0; display: flex; justify-content: space-between; align-items: center; }
.profile-group { display: flex; align-items: center; gap: 12px; }
.avatar-ring { width: 42px; height: 42px; border-radius: 50%; background: #111111; color: #ffffff; display: flex; align-items: center; justify-content: center; font-size: 15px; font-weight: 700; position: relative; }
.status-dot-mini { width: 11px; height: 11px; border-radius: 50%; background: #10b981; border: 2px solid #ffffff; position: absolute; bottom: 0; right: 0; }
.greeting-title { font-size: 16px; font-weight: 800; color: #111111; letter-spacing: -0.3px; }
.greeting-sub { font-size: 12px; color: #10b981; font-weight: 700; display: flex; align-items: center; gap: 4px; }
.contact-chip { background: #f4fbf7; border: 1px solid #d1fae5; padding: 6px 12px; border-radius: 999px; font-size: 11px; font-weight: 700; color: #047857; display: flex; align-items: center; gap: 6px; }

.main-scroll { flex: 1; padding: 16px 18px; overflow-y: auto; display: flex; flex-direction: column; gap: 16px; }

/* 1. Dynamic Hero Aura: Protection Posture */
.protection-hero { background: #ffffff; border: 1px solid #ebebeb; border-radius: 24px; padding: 22px; box-shadow: 0 4px 24px rgba(0,0,0,0.03); position: relative; overflow: hidden; }
.hero-glow { position: absolute; top: -40px; right: -40px; width: 130px; height: 130px; background: radial-gradient(circle, rgba(16,185,129,0.12) 0%, rgba(255,255,255,0) 70%); border-radius: 50%; pointer-events: none; }
.hero-header { display: flex; justify-content: space-between; align-items: flex-start; }
.hero-badge { display: inline-flex; align-items: center; gap: 6px; background: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0; padding: 4px 10px; border-radius: 999px; font-size: 11px; font-weight: 800; letter-spacing: 0.5px; }
.hero-badge-dot { width: 6px; height: 6px; border-radius: 50%; background: #16a34a; box-shadow: 0 0 6px #16a34a; }
.hero-heading { font-size: 26px; font-weight: 800; color: #111111; margin-top: 10px; letter-spacing: -0.8px; line-height: 30px; }
.hero-desc { font-size: 13px; color: #555555; line-height: 18px; margin-top: 6px; max-width: 310px; }

.hero-stats-row { display: flex; gap: 8px; margin-top: 18px; }
.stat-pill { background: #fafafa; border: 1px solid #eeeeee; border-radius: 12px; padding: 8px 12px; flex: 1; display: flex; flex-direction: column; gap: 2px; }
.stat-value { font-size: 16px; font-weight: 800; color: #111111; }
.stat-label { font-size: 10px; font-weight: 600; color: #777777; text-transform: uppercase; letter-spacing: 0.5px; }

/* 2. Featured Action: Direct Calling Hub */
.calling-hub-card { background: #111111; color: #ffffff; border-radius: 24px; padding: 22px; box-shadow: 0 10px 30px rgba(0,0,0,0.12); display: flex; flex-direction: column; gap: 14px; position: relative; overflow: hidden; }
.calling-pattern { position: absolute; right: -20px; bottom: -20px; font-size: 100px; opacity: 0.06; pointer-events: none; }
.hub-tag { font-size: 11px; font-weight: 700; color: #10b981; letter-spacing: 1px; text-transform: uppercase; }
.hub-title { font-size: 20px; font-weight: 800; letter-spacing: -0.4px; }
.hub-sub { font-size: 12px; color: #a1a1aa; line-height: 16px; margin-top: -8px; }
.hub-actions { display: grid; grid-template-columns: 1.3fr 1fr; gap: 10px; margin-top: 4px; }
.btn-hub-start { background: #10b981; color: #ffffff; border: none; border-radius: 14px; height: 46px; font-size: 13px; font-weight: 800; display: flex; align-items: center; justify-content: center; gap: 8px; box-shadow: 0 4px 14px rgba(16,185,129,0.35); }
.btn-hub-join { background: #27272a; color: #ffffff; border: 1px solid #3f3f46; border-radius: 14px; height: 46px; font-size: 13px; font-weight: 700; display: flex; align-items: center; justify-content: center; gap: 6px; }

/* 3. Real Active Protection Shields (Vertical, Clean) */
.section-label { font-size: 12px; font-weight: 800; color: #888888; letter-spacing: 1px; text-transform: uppercase; margin-top: 4px; }
.shields-stack { display: flex; flex-direction: column; gap: 10px; }
.shield-row-card { background: #ffffff; border: 1px solid #ebebeb; border-radius: 18px; padding: 14px 16px; display: flex; align-items: center; gap: 14px; box-shadow: 0 1px 4px rgba(0,0,0,0.02); }
.shield-icon-bubble { width: 42px; height: 42px; border-radius: 14px; display: flex; align-items: center; justify-content: center; font-size: 20px; flex-shrink: 0; }
.shield-details { flex: 1; }
.shield-title-row { display: flex; align-items: center; gap: 8px; }
.shield-title { font-size: 14px; font-weight: 700; color: #111111; }
.shield-tag { font-size: 9px; font-weight: 800; padding: 2px 7px; border-radius: 999px; }
.shield-desc { font-size: 11px; color: #666666; margin-top: 2px; line-height: 14px; }
.toggle-switch-on { width: 36px; height: 20px; background: #10b981; border-radius: 10px; position: relative; }
.toggle-knob { width: 16px; height: 16px; background: #ffffff; border-radius: 50%; position: absolute; right: 2px; top: 2px; box-shadow: 0 1px 3px rgba(0,0,0,0.2); }

/* Floating Bottom Navigation Bar */
.floating-bottom-nav { background: #ffffff; border-top: 1px solid #f0f0f0; height: 68px; display: flex; justify-content: space-around; align-items: center; padding: 0 16px; }
.nav-btn { display: flex; flex-direction: column; align-items: center; gap: 4px; font-size: 11px; font-weight: 600; color: #888888; }
.nav-btn.active { color: #111111; font-weight: 800; }
.nav-active-pill { width: 18px; height: 3px; background: #111111; border-radius: 2px; }
</style>
</head>
<body>
<div class="status-bar"><span>9:41 AM</span><span>5G 📶 98% 🔋</span></div>

<div class="app-header">
  <div class="profile-group">
    <div class="avatar-ring">
      TG
      <div class="status-dot-mini"></div>
    </div>
    <div>
      <div class="greeting-title">Tanya's Guardian</div>
      <div class="greeting-sub">✓ On-Device AI Active</div>
    </div>
  </div>
  <div class="contact-chip">
    <span>🛡️</span> Mom Linked
  </div>
</div>

<div class="main-scroll">
  <!-- 1. Hero Protection Aura -->
  <div class="protection-hero">
    <div class="hero-glow"></div>
    <div class="hero-header">
      <div class="hero-badge"><div class="hero-badge-dot"></div> CONTINUOUS SHIELD</div>
      <div style="font-size:22px;">✨</div>
    </div>
    <div class="hero-heading">All Systems Secure & Monitored</div>
    <div class="hero-desc">Your voice calls, background SMS, and tapped links are being screened in real time by Bhashini STT and Gemini AI.</div>
    
    <div class="hero-stats-row">
      <div class="stat-pill">
        <div class="stat-value" style="color:#10b981;">0</div>
        <div class="stat-label">Threats Today</div>
      </div>
      <div class="stat-pill">
        <div class="stat-value">16 kHz</div>
        <div class="stat-label">Bhashini Audio</div>
      </div>
      <div class="stat-pill">
        <div class="stat-value" style="color:#059669;">Instant</div>
        <div class="stat-label">Family Alert</div>
      </div>
    </div>
  </div>

  <!-- 2. Primary Action: Calling Hub -->
  <div class="calling-hub-card">
    <div class="calling-pattern">📞</div>
    <div class="hub-tag">E2EE Protected Calling</div>
    <div class="hub-title">Make a Secure VoIP Call</div>
    <div class="hub-sub">Call anyone with instant AI detection of digital arrest intimidation, money extortion, and scam tactics.</div>
    <div class="hub-actions">
      <div class="btn-hub-start">📞 Host New Call</div>
      <div class="btn-hub-join">🔑 Enter PIN</div>
    </div>
  </div>

  <!-- 3. Active Protection Shields -->
  <div class="section-label">Active Defense Shields</div>
  <div class="shields-stack">
    <div class="shield-row-card">
      <div class="shield-icon-bubble" style="background:#f0fdf4; color:#10b981;">🛡️</div>
      <div class="shield-details">
        <div class="shield-title-row">
          <div class="shield-title">Link Shield Interceptor</div>
          <div class="shield-tag" style="background:#f0fdf4; color:#15803d; border:1px solid #bbf7d0;">ACTIVE</div>
        </div>
        <div class="shield-desc">Blocks fraudulent phishing links and dangerous APK downloads before browser opens.</div>
      </div>
      <div class="toggle-switch-on"><div class="toggle-knob"></div></div>
    </div>

    <div class="shield-row-card">
      <div class="shield-icon-bubble" style="background:#fffbeb; color:#d97706;">🔔</div>
      <div class="shield-details">
        <div class="shield-title-row">
          <div class="shield-title">Notification Scam Guard</div>
          <div class="shield-tag" style="background:#fffbeb; color:#b45309; border:1px solid #fde68a;">SCREENING</div>
        </div>
        <div class="shield-desc">Inspects bank SMS & courier notifications for extortion and fake KYC traps.</div>
      </div>
      <div class="toggle-switch-on"><div class="toggle-knob"></div></div>
    </div>

    <div class="shield-row-card">
      <div class="shield-icon-bubble" style="background:#ecfdf5; color:#059669;">👥</div>
      <div class="shield-details">
        <div class="shield-title-row">
          <div class="shield-title">Emergency Family Alert (Ably + FCM)</div>
          <div class="shield-tag" style="background:#ecfdf5; color:#047857; border:1px solid #a7f3d0;">READY</div>
        </div>
        <div class="shield-desc">Auto-dispatches alert to Mom (+91 98765 43210) if call coercion reaches 75%.</div>
      </div>
      <div style="font-size:18px; color:#a1a1aa;">›</div>
    </div>
  </div>
</div>

<div class="floating-bottom-nav">
  <div class="nav-btn active">
    <div style="font-size:18px;">🛡️</div>
    <span>Security</span>
    <div class="nav-active-pill"></div>
  </div>
  <div class="nav-btn">
    <div style="font-size:18px;">📞</div>
    <span>VoIP Call</span>
  </div>
  <div class="nav-btn">
    <div style="font-size:18px;">⚡</div>
    <span>Threats</span>
  </div>
  <div class="nav-btn">
    <div style="font-size:18px;">⚙️</div>
    <span>Settings</span>
  </div>
</div>
</body>
</html>"""

# =============================================================================
# 2. HTML TEMPLATE: RADICALLY NEW VOIP CALL SCREEN (AUTHENTIC PHONE UI)
# =============================================================================
HTML_VOIP = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
* { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Segoe UI", Roboto, sans-serif; }
body { background: #fafafa; color: #111111; width: 412px; height: 890px; overflow: hidden; display: flex; flex-direction: column; justify-content: space-between; }
.status-bar { display: flex; justify-content: space-between; padding: 12px 22px 6px 22px; font-size: 13px; font-weight: 600; color: #666666; background: #ffffff; }

/* Top Call Header: Minimal, E2EE, Timer */
.call-header { background: #ffffff; border-bottom: 1px solid #f0f0f0; padding: 12px 20px; display: flex; justify-content: space-between; align-items: center; }
.call-room-pill { display: flex; align-items: center; gap: 8px; background: #f4f4f5; border: 1px solid #e4e4e7; border-radius: 999px; padding: 5px 12px; font-family: monospace; font-size: 13px; font-weight: 700; color: #111111; }
.call-timer { font-family: monospace; font-size: 13px; font-weight: 800; color: #10b981; display: flex; align-items: center; gap: 6px; }
.live-dot { width: 7px; height: 7px; border-radius: 50%; background: #10b981; box-shadow: 0 0 6px #10b981; }

.call-content-scroll { flex: 1; padding: 14px 18px; overflow-y: auto; display: flex; flex-direction: column; gap: 14px; }

/* 1. Caller Persona: Acoustic Soundwave Identity */
.caller-stage { display: flex; flex-direction: column; align-items: center; padding: 10px 0 6px 0; }
.caller-avatar-circle { width: 78px; height: 78px; border-radius: 50%; background: #fee2e2; border: 3px solid #ef4444; display: flex; align-items: center; justify-content: center; font-size: 32px; box-shadow: 0 0 25px rgba(239,68,68,0.2); position: relative; }
.caller-name { font-size: 20px; font-weight: 800; color: #111111; margin-top: 10px; letter-spacing: -0.4px; }
.caller-badge-flagged { background: #fef2f2; border: 1px solid #fca5a5; color: #dc2626; font-size: 11px; font-weight: 800; padding: 3px 10px; border-radius: 999px; margin-top: 4px; display: flex; align-items: center; gap: 5px; }

/* 2. Floating AI Co-Pilot Alert: Dynamic Threat Capsule */
.threat-capsule { background: #ffffff; border: 2px solid #ef4444; border-radius: 20px; padding: 16px; box-shadow: 0 10px 30px rgba(239,68,68,0.12); display: flex; flex-direction: column; gap: 10px; }
.capsule-top { display: flex; justify-content: space-between; align-items: center; }
.threat-badge { background: #ef4444; color: #ffffff; font-size: 10px; font-weight: 900; letter-spacing: 0.8px; padding: 4px 10px; border-radius: 999px; }
.threat-score { font-family: monospace; font-size: 16px; font-weight: 900; color: #ef4444; }
.capsule-title { font-size: 15px; font-weight: 800; color: #b91c1c; }
.capsule-desc { font-size: 12px; color: #4b5563; line-height: 16px; }
.coercion-pill { background: #fffbeb; border: 1px solid #fde68a; border-radius: 10px; padding: 6px 10px; font-size: 11px; font-weight: 700; color: #b45309; display: flex; align-items: center; gap: 6px; }
.capsule-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 2px; }
.btn-alert-family { background: #f0fdf4; border: 1px solid #bbf7d0; color: #15803d; border-radius: 10px; height: 36px; font-size: 11px; font-weight: 800; display: flex; align-items: center; justify-content: center; gap: 6px; }
.btn-end-urgent { background: #ef4444; color: #ffffff; border: none; border-radius: 10px; height: 36px; font-size: 11px; font-weight: 800; display: flex; align-items: center; justify-content: center; gap: 6px; }

/* 3. Live Captions Stream */
.captions-container { flex: 1; display: flex; flex-direction: column; gap: 10px; }
.caption-label { font-size: 11px; font-weight: 800; color: #888888; letter-spacing: 0.8px; text-transform: uppercase; }
.bubble-scammer { background: #ffffff; border: 1.5px solid #fca5a5; border-radius: 16px; padding: 12px 14px; box-shadow: 0 2px 8px rgba(239,68,68,0.04); display: flex; flex-direction: column; gap: 4px; }
.bubble-speaker { font-size: 11px; font-weight: 800; color: #dc2626; display: flex; align-items: center; justify-content: space-between; }
.bubble-text { font-size: 13px; color: #111111; line-height: 18px; }
.bubble-user { align-self: flex-end; max-width: 310px; background: #f4f4f5; border: 1px solid #e4e4e7; border-radius: 16px; padding: 12px 14px; display: flex; flex-direction: column; gap: 4px; }

/* 4. Bottom Call Controls Dock (Actual phone call circles) */
.call-dock-panel { background: #ffffff; border-top: 1px solid #f0f0f0; padding: 16px 24px 20px 24px; display: flex; justify-content: space-between; align-items: center; }
.dock-circle-btn { width: 54px; height: 54px; border-radius: 50%; background: #f4f4f5; border: 1px solid #e4e4e7; display: flex; flex-direction: column; align-items: center; justify-content: center; font-size: 18px; color: #333333; gap: 2px; }
.dock-btn-label { font-size: 9px; font-weight: 700; color: #666666; margin-top: -2px; }
.dock-end-call { width: 64px; height: 64px; border-radius: 50%; background: #ef4444; color: #ffffff; display: flex; align-items: center; justify-content: center; font-size: 26px; box-shadow: 0 6px 20px rgba(239,68,68,0.35); }
</style>
</head>
<body>
<div class="status-bar"><span>9:43 AM</span><span>5G 📶 98% 🔋</span></div>

<div class="call-header">
  <div class="call-room-pill">
    <span>🔒</span> ROOM 482 · 190
  </div>
  <div class="call-timer">
    <div class="live-dot"></div> 02:14
  </div>
</div>

<div class="call-content-scroll">
  <!-- Caller Persona -->
  <div class="caller-stage">
    <div class="caller-avatar-circle">
      👮
    </div>
    <div class="caller-name">Caller (Room 482-190)</div>
    <div class="caller-badge-flagged">
      <span>⚠️</span> High Probability Scammer (91%)
    </div>
  </div>

  <!-- Dynamic AI Co-Pilot Alert Capsule -->
  <div class="threat-capsule">
    <div class="capsule-top">
      <div class="threat-badge">🚨 CRITICAL EXTORTION THREAT</div>
      <div class="threat-score">88% RISK</div>
    </div>
    <div class="capsule-title">Digital Arrest Extortion Detected</div>
    <div class="capsule-desc">Caller falsely claims to be Mumbai Cyber Police and demands screen share to freeze bank accounts. Law enforcement never arrests over video call.</div>
    <div class="coercion-pill">
      <span>⚡</span> Tactic: Authority Intimidation & Urgency Coercion
    </div>
    <div class="capsule-actions">
      <div class="btn-alert-family">✓ Alert Sent to Mom</div>
      <div class="btn-end-urgent">🔴 Hang Up</div>
    </div>
  </div>

  <!-- Real-Time Captions -->
  <div class="captions-container">
    <div class="caption-label">Live Bhashini Speech-To-Text</div>
    
    <div class="bubble-scammer">
      <div class="bubble-speaker">
        <span>⚠️ CALLER (FLAGGED)</span>
        <span style="font-size:9px; color:#ef4444; font-weight:900;">DHRUVA HINDI STT</span>
      </div>
      <div class="bubble-text">This is Inspector Sharma from Mumbai Cyber Crime Branch. An FIR has been registered against your Aadhaar card. You are placed under digital arrest right now!</div>
    </div>

    <div class="bubble-user">
      <div style="font-size:11px; font-weight:800; color:#52525b;">👤 YOU</div>
      <div class="bubble-text">Which police station are you calling from? Please share your official departmental verification number.</div>
    </div>
  </div>
</div>

<!-- Phone Calling Dock -->
<div class="call-dock-panel">
  <div class="dock-circle-btn">
    <div>🎙️</div>
    <div class="dock-btn-label">Mute</div>
  </div>
  <div class="dock-circle-btn">
    <div>🔊</div>
    <div class="dock-btn-label">Speaker</div>
  </div>
  <div class="dock-circle-btn">
    <div>💬</div>
    <div class="dock-btn-label">Captions</div>
  </div>
  <div class="dock-end-call">
    📞
  </div>
</div>
</body>
</html>"""

# =============================================================================
# 3. HTML TEMPLATE: RADICALLY NEW ROOM CODE SCREEN (ELEGANT SEGMENTED TABS)
# =============================================================================
HTML_ROOM = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
* { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Segoe UI", Roboto, sans-serif; }
body { background: #fbfbfb; color: #111111; width: 412px; height: 890px; overflow: hidden; display: flex; flex-direction: column; }
.status-bar { display: flex; justify-content: space-between; padding: 12px 22px 6px 22px; font-size: 13px; font-weight: 600; color: #666666; background: #ffffff; }

.nav-header { background: #ffffff; border-bottom: 1px solid #f0f0f0; padding: 14px 20px; display: flex; align-items: center; gap: 14px; }
.btn-back { font-size: 20px; font-weight: 800; color: #111111; }
.nav-title { font-size: 18px; font-weight: 800; letter-spacing: -0.4px; }

.screen-body { flex: 1; padding: 20px 20px; display: flex; flex-direction: column; gap: 20px; }

/* Modern Segmented Control Switcher */
.segmented-control { background: #f4f4f5; border-radius: 14px; padding: 4px; display: flex; gap: 4px; }
.segment-tab { flex: 1; text-align: center; padding: 10px 0; border-radius: 11px; font-size: 13px; font-weight: 700; color: #71717a; }
.segment-tab.active { background: #ffffff; color: #111111; font-weight: 800; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }

/* Main Host Card */
.pin-display-card { background: #ffffff; border: 1px solid #ebebeb; border-radius: 24px; padding: 28px 20px; box-shadow: 0 4px 20px rgba(0,0,0,0.03); display: flex; flex-direction: column; align-items: center; text-align: center; }
.card-instruction { font-size: 13px; color: #666666; margin-bottom: 18px; }

.pin-digits-row { display: flex; align-items: center; gap: 8px; margin-bottom: 18px; }
.digit-tile { width: 46px; height: 58px; background: #fafafa; border: 1.5px solid #e4e4e7; border-radius: 14px; display: flex; align-items: center; justify-content: center; font-family: monospace; font-size: 28px; font-weight: 800; color: #111111; box-shadow: 0 2px 6px rgba(0,0,0,0.02); }
.digit-dash { width: 8px; height: 3px; background: #a1a1aa; border-radius: 2px; }

.copy-share-row { display: flex; gap: 10px; width: 100%; margin-bottom: 22px; }
.btn-action-pill { flex: 1; background: #f4f4f5; border: 1px solid #e4e4e7; border-radius: 12px; height: 42px; font-size: 12px; font-weight: 700; color: #333333; display: flex; align-items: center; justify-content: center; gap: 6px; }

.btn-launch-call { width: 100%; background: #111111; color: #ffffff; border: none; border-radius: 16px; height: 52px; font-size: 14px; font-weight: 800; display: flex; align-items: center; justify-content: center; gap: 8px; box-shadow: 0 4px 18px rgba(0,0,0,0.18); }

/* Architecture Trust Badge */
.trust-shield-card { background: #f0fdf4; border: 1px solid #bbf7d0; border-radius: 18px; padding: 16px; display: flex; flex-direction: column; gap: 8px; }
.trust-row { display: flex; align-items: center; gap: 8px; font-size: 12px; color: #15803d; font-weight: 600; }
.check-glyph { font-weight: 900; }
</style>
</head>
<body>
<div class="status-bar"><span>9:45 AM</span><span>5G 📶 98% 🔋</span></div>

<div class="nav-header">
  <div class="btn-back">←</div>
  <div class="nav-title">Protected VoIP Calling</div>
</div>

<div class="screen-body">
  <!-- Segmented Control -->
  <div class="segmented-control">
    <div class="segment-tab active">Host a New Call</div>
    <div class="segment-tab">Join Call with PIN</div>
  </div>

  <!-- Host Room PIN Card -->
  <div class="pin-display-card">
    <div style="font-size:28px; margin-bottom:8px;">🔒</div>
    <div style="font-size:18px; font-weight:800; letter-spacing:-0.4px;">Your Encrypted Room Code</div>
    <div class="card-instruction">Share this one-time secure PIN with the person you are speaking with. Both ends will have real-time AI scam defense active.</div>
    
    <div class="pin-digits-row">
      <div class="digit-tile">8</div>
      <div class="digit-tile">3</div>
      <div class="digit-tile">1</div>
      <div class="digit-dash"></div>
      <div class="digit-tile">4</div>
      <div class="digit-tile">9</div>
      <div class="digit-tile">2</div>
    </div>

    <div class="copy-share-row">
      <div class="btn-action-pill">📋 Copy Room PIN</div>
      <div class="btn-action-pill">🔗 Share Invite Link</div>
    </div>

    <div class="btn-launch-call">
      📞 Start Protected Call Now
    </div>
  </div>

  <!-- Verified Security Guarantee -->
  <div class="trust-shield-card">
    <div class="trust-row"><span class="check-glyph">✓</span> Agora 16 kHz High-Fidelity Audio Stream</div>
    <div class="trust-row"><span class="check-glyph">✓</span> Real-Time Bhashini Dhruva Speech-To-Text</div>
    <div class="trust-row"><span class="check-glyph">✓</span> On-Device Gemini 1.5 Flash Coercion Detector</div>
  </div>
</div>
</body>
</html>"""

# =============================================================================
# 4. HTML TEMPLATE: RADICALLY NEW LINK SHIELD (SYSTEM SECURITY SHEET)
# =============================================================================
HTML_LINK = """<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<style>
* { box-sizing: border-box; margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "SF Pro Text", "Segoe UI", Roboto, sans-serif; }
body { background: #f4f4f5; color: #111111; width: 412px; height: 890px; overflow: hidden; display: flex; flex-direction: column; justify-content: flex-end; }
.status-bar { display: flex; justify-content: space-between; padding: 12px 22px 6px 22px; font-size: 13px; font-weight: 600; color: #666666; }

/* System Bottom Sheet Warning */
.security-sheet { background: #ffffff; border-top-left-radius: 32px; border-top-right-radius: 32px; padding: 26px 24px 32px 24px; box-shadow: 0 -10px 40px rgba(0,0,0,0.15); display: flex; flex-direction: column; gap: 18px; border-top: 1px solid #e4e4e7; }

.sheet-handle { width: 40px; height: 4px; background: #e4e4e7; border-radius: 2px; align-self: center; margin-bottom: 4px; }

.sheet-alert-header { display: flex; align-items: center; gap: 14px; }
.alert-icon-ring { width: 54px; height: 54px; border-radius: 18px; background: #fee2e2; border: 2px solid #ef4444; display: flex; align-items: center; justify-content: center; font-size: 28px; flex-shrink: 0; }
.alert-heading-group { flex: 1; }
.alert-tag { font-size: 10px; font-weight: 800; color: #dc2626; letter-spacing: 1px; text-transform: uppercase; }
.alert-title { font-size: 20px; font-weight: 800; color: #111111; letter-spacing: -0.4px; margin-top: 2px; }

.threat-domain-box { background: #fafafa; border: 1.5px solid #fecaca; border-radius: 16px; padding: 14px 16px; display: flex; flex-direction: column; gap: 4px; }
.domain-label { font-size: 10px; font-weight: 700; color: #991b1b; text-transform: uppercase; letter-spacing: 0.8px; }
.domain-name { font-family: monospace; font-size: 16px; font-weight: 800; color: #dc2626; word-break: break-all; }

.explanation-body { font-size: 13px; color: #4b5563; line-height: 19px; }

.sheet-actions-stack { display: flex; flex-direction: column; gap: 10px; margin-top: 6px; }
.btn-block-safe { width: 100%; background: #dc2626; color: #ffffff; border: none; border-radius: 16px; height: 52px; font-size: 14px; font-weight: 800; display: flex; align-items: center; justify-content: center; gap: 8px; box-shadow: 0 4px 16px rgba(220,38,38,0.3); }
.btn-proceed-ghost { width: 100%; background: transparent; border: 1px solid #e4e4e7; border-radius: 16px; height: 46px; font-size: 12px; font-weight: 700; color: #71717a; display: flex; align-items: center; justify-content: center; }
</style>
</head>
<body>
<div class="status-bar"><span>9:48 AM</span><span>5G 📶 98% 🔋</span></div>

<div style="flex:1;"></div>

<div class="security-sheet">
  <div class="sheet-handle"></div>
  
  <div class="sheet-alert-header">
    <div class="alert-icon-ring">🚨</div>
    <div class="alert-heading-group">
      <div class="alert-tag">Guardian Link Shield · High Risk</div>
      <div class="alert-title">Phishing Attack Blocked</div>
    </div>
  </div>

  <div class="threat-domain-box">
    <div class="domain-label">Flagged Destination Host</div>
    <div class="domain-name">sbi-kyc-verification-update.xyz</div>
  </div>

  <div class="explanation-body">
    Guardian intercepted this link before your browser loaded it. Our domain threat model flagged this site for attempting <strong>fake SBI banking authentication</strong> and unauthorized APK payload distribution.
  </div>

  <div class="sheet-actions-stack">
    <div class="btn-block-safe">
      🛡️ Block & Keep Me Safe (Recommended)
    </div>
    <div class="btn-proceed-ghost">
      Advanced: View Raw URL or Proceed Unsafe
    </div>
  </div>
</div>
</body>
</html>"""

screens = [
    ("mockup_home.html", HTML_HOME, "screen_home.png"),
    ("mockup_voip.html", HTML_VOIP, "screen_voip_call.png"),
    ("mockup_room.html", HTML_ROOM, "screen_room_code.png"),
    ("mockup_link.html", HTML_LINK, "screen_link_shield.png")
]

for filename, html, output_img in screens:
    html_path = os.path.join(ARTIFACT_DIR, filename)
    img_path = os.path.join(ARTIFACT_DIR, output_img)
    with open(html_path, "w", encoding="utf-8") as f:
        f.write(html)
    
    cmd = [
        EDGE_PATH,
        "--headless=new",
        "--disable-gpu",
        f"--screenshot={img_path}",
        "--window-size=412,890",
        html_path
    ]
    subprocess.run(cmd, capture_output=True)
    time.sleep(1)
    if os.path.exists(img_path):
        print(f"Generated {output_img} ({os.path.getsize(img_path)} bytes)")
    else:
        print(f"Failed to generate {output_img}")
