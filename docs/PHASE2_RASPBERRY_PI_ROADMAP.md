# SuSagi Guardian — Phase 2 Architecture & Raspberry Pi Edge Roadmap

**Vision:** From Mobile Endpoint Sentinel to Autonomous Home & SME Edge Security Appliance  
**Platform:** Raspberry Pi 5 (Quad-Core Arm Cortex-A76 @ 2.4 GHz) + Raspberry Pi AI HAT+ (Hailo-8 / Hailo-8L NPU)  
**Target Milestone:** Bharat AI-SoC Challenge 2026–27 | Phase 2 Implementation

---

## 1. Vision & Problem Statement Expansion

In Phase 1, SuSagi Guardian established an on-device personal cybersecurity sentinel on Arm smartphones. However, modern Indian households and small businesses face systemic network-wide threats that extend beyond a single phone:
- **Vulnerable Family Endpoints:** Elderly relatives and children using smartphones without dedicated security tools.
- **Unmanaged Smart Home / IoT Devices:** Smart TVs, Wi-Fi cameras, and smart meters with hardcoded credentials frequently enlisted into DDoS botnets (e.g. Mirai variants).
- **Rogue DNS & In-Home Phishing:** Malicious domains accessed across multiple devices on the same home Wi-Fi network.

**Phase 2 expands SuSagi Guardian into an autonomous Edge Security Gateway** running on the Raspberry Pi 5. Positioned between the home Wi-Fi router and the broadband ONT, it inspects network flows in real time, sinks malicious domains locally, and coordinates threat intelligence across family smartphones.

---

## 2. Hardware Specification: Raspberry Pi 5 + AI Acceleration

```
+-----------------------------------------------------------------------------------+
|               RASPBERRY PI 5 EDGE GATEWAY (Arm Cortex-A76 Architecture)           |
+-----------------------------------------------------------------------------------+
|  - CPU: Broadcom BCM2712 Quad-Core Arm Cortex-A76 @ 2.4 GHz (Armv8.2-A)           |
|  - RAM: 8 GB LPDDR4X-4267                                                         |
|  - Storage: High-speed NVMe SSD via PCIe M.2 HAT                                  |
|  - AI Acceleration: Raspberry Pi AI HAT+ (Hailo-8L @ 13 TOPS or Hailo-8 @ 26 TOPS)|
|  - Network Interfaces: Dual Gigabit Ethernet (Inline Bridge) + Wi-Fi 5            |
+-----------------------------------------------------------------------------------+
```

### Why the Arm Cortex-A76 & Hailo NPU Architecture?
1. **Computational Symmetry:** The Kotlin/JVM rule engine, statistical classifiers, and ExecuTorch runtime developed in Phase 1 compile natively on Linux `aarch64` without code refactoring.
2. **Dedicated Neural Processing (13-26 TOPS):** The Hailo NPU handles quantized LLM token generation and embedding similarity searches at `> 25 tokens/sec` with `< 3W` power consumption, freeing CPU cores for line-rate packet filtering.
3. **Thermal & Energy Efficiency:** The entire appliance runs at `< 10W` total power, making it suitable for 24/7 continuous operation without noisy cooling fans.

---

## 3. Edge Gateway Subsystem Architecture

```
                                 [HOME ROUTER / ONT]
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|                    SUSAGI EDGE SENTINEL (Raspberry Pi 5)                          |
+-----------------------------------------------------------------------------------+
|                                                                                   |
|  +--------------------------------+       +------------------------------------+  |
|  |     INLINE eBPF FLOW MONITOR   |       |       LOCAL ENCRYPTED DNS SINKHOLE |  |
|  |  - Kernel socket capture       |       |  - DNS over HTTPS (DoH) / Dot      |  |
|  |  - Flow 5-tuple extraction     |       |  - Punycode & homoglyph blocklist  |  |
|  |  - Zero payload decryption     |       |  - Sub-millisecond response        |  |
|  +----------------+---------------+       +-----------------+------------------+  |
|                   |                                         |                     |
|                   v                                         v                     |
|  +-----------------------------------------------------------------------------+  |
|  |       SHARED EDGE INCIDENT CORRELATION ENGINE (From Phase 1 Codebase)       |  |
|  |  - Connects network anomalies across smart TVs, IoT cameras & smartphones   |  |
|  |  - Automated C2 beacon isolation & socket termination                       |  |
|  +-------------------------------------+---------------------------------------+  |
|                                        |                                          |
|                                        v                                          |
|  +-----------------------------------------------------------------------------+  |
|  |          HAILO NPU ACCELERATED LLM EXPLAINER (Llama-3.2-1B / 3B)            |  |
|  |  - 13 TOPS inference generating incident briefings for family members       |  |
|  +-------------------------------------+---------------------------------------+  |
+----------------------------------------+------------------------------------------+
                                         |
                                         v (Local mDNS / Wi-Fi Sync)
         +-------------------------------+-------------------------------+
         |                               |                               |
         v                               v                               v
 [Smartphone A (Parent)]     [Smartphone B (Elderly)]         [Smart TV / IoT]
 (SuSagi Mobile App HUD)     (SuSagi Mobile App HUD)          (Isolated VLAN)
```

---

## 4. Key Phase 2 Deliverables & Features

### 4.1 Local DNS Sinkhole with Real-Time Phishing Blocking
- Intercepts port 53 DNS requests across the home LAN.
- Evaluates domain requests against the Phase 1 heuristic engine (homoglyphs, deceptive userinfo, malicious TLDs).
- Returns `0.0.0.0` for confirmed phishing domains, preventing users from ever landing on credential harvesting forms.

### 4.2 Kernel-Level eBPF Flow Telemetry
- Utilizes Linux extended Berkeley Packet Filters (`eBPF`) inside the Raspberry Pi OS kernel to ingest flow metadata with near-zero CPU overhead.
- Directly feeds the `NetworkAnomalyClassifier` developed in Phase 1, flagging compromised IoT cameras beaconing to foreign servers.

### 4.3 Multi-Device Incident Correlation
- Correlates attacks that target different devices in the same household (e.g. an SMS sent to the grandfather's phone linking to a server that then scans the home router).
- Pushes synchronized local alerts to family members via local mDNS/LAN broadcasts without routing through external cloud servers.

---

## 5. Phase 2 Implementation Timeline

| Month | Milestone Objective | Core Technical Output |
| :--- | :--- | :--- |
| **Month 1** | Linux aarch64 Porting | Port `NetworkAnomalyClassifier` and `IncidentCorrelationEngine` to a standalone Linux daemon (`susagi-daemon`). |
| **Month 2** | eBPF & DNS Interceptor | Integrate eBPF network probe and local DNS filter on Raspberry Pi OS. |
| **Month 3** | Hailo NPU Optimization | Quantize Llama-3.2 for Hailo-8L runtime (HEF format) and benchmark token latency. |
| **Month 4** | Mobile-Gateway Sync | Implement local Wi-Fi pairing protocol between SuSagi Android app and Pi 5 gateway. |
| **Month 5** | Field Trials & Validation | Deploy prototype in 5 domestic environments; publish latency, power, and false-positive metrics. |
