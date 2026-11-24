# SuSagi Guardian — Network Anomaly & Intrusion Detection

**Problem Statement:** PS3 — On-Device Threat, Phishing, and Scam Detection  
**Module:** `com.susagi.network.NetworkAnomalyClassifier` & `NetworkFlowRecord`  
**Execution Environment:** 100% On-Device / Edge Android / Arm Heterogeneous LITTLE Cluster  
**Verification:** Automated Unit & Benchmark Tests Passing Offline (`CybersecurityBenchmarkEvaluationTest.kt` & `NetworkAnomalyClassifierTest.kt`)  
**Evidence Level:** `VERIFIED BY AUTOMATED TEST` (Evaluated on Synthetic / Replay Flow Batches)

---

## 1. Motivation, Edge Threat Model & Platform Constraints

Mobile endpoints in India are frequently targeted by multi-stage cyber threats:
1. **Periodic Background Communication:** Background telemetry or malware command-and-control (C2) polling using periodic outbound heartbeats.
2. **Local Reconnaissance & Lateral Probing:** Compromised apps or rogue devices scanning internal subnets or local loopback interfaces for open ports (e.g., ADB port 5555, debug ports).
3. **Volumetric Data Exfiltration:** Sudden bursts of outbound connections transmitting sensitive data immediately following permission elevation.

### 1.1 Android Non-Root Security Boundaries & Ingestion Architecture
Standard Android security policy (Linux UID sandbox, SELinux policies, and permission architecture) prohibits non-root applications from opening promiscuous raw network sockets or capturing unrestricted operating system packet streams (`pcap`).

To operate within production Android security boundaries without requiring device rooting:
- SuSagi Guardian ingests lightweight **connection flow summaries** (`NetworkFlowRecord`).
- In production deployment, flow summaries are sourced via Android's sanctioned `VpnService` virtual network interface or local socket statistics (`/proc/net/tcp` / Netlink).
- In automated testing and benchmarking, flow batches are evaluated using simulated and replayed flow sequences representing realistic network scenarios.
- Zero raw packet payload data is collected or transmitted off-device, guaranteeing strict compliance with India's Digital Personal Data Protection (DPDP) Act 2023.

---

## 2. Telemetry Ingestion: `NetworkFlowRecord`

Network traffic is modeled as lightweight flow metadata records without payload inspection:

```kotlin
data class NetworkFlowRecord(
    val timestampMs: Long,
    val sourceIp: String,
    val destinationIp: String,
    val sourcePort: Int,
    val destinationPort: Int,
    val protocol: String,       // "TCP", "UDP", "ICMP"
    val bytesTransferred: Long,
    val packetCount: Int,
    val durationMs: Long
)
```

---

## 3. Statistical Detection Algorithms & Thresholds

The classifier applies four deterministic statistical algorithms across sliding time windows:

```text
                          NETWORK FLOW BATCH (Sliding Window)
                                          |
        +------------------+--------------+---------------+------------------+
        |                  |                              |                  |
        v                  v                              v                  v
 [Vertical Scan]   [Horizontal Scan]             [Periodic Traffic]    [Connection Burst]
  Target: 1 Host    Target: N Hosts               Interval Jitter:      Window Threshold:
  Distinct Ports:   Distinct IPs:                 J = StdDev/Mean       Count:
  >= 15 ports       >= 20 IPs                     J <= 0.15 (N >= 4)    >= 30 conns / 5s
        |                  |                              |                  |
        +------------------+--------------+---------------+------------------+
                                          |
                                          v
                         AGGREGATED INTRUSION VERDICTS
                      (Threat Type, Severity, Confidence)
```

### 3.1 Vertical Port Scan Detection
- **Mechanism:** Detects a single remote endpoint probing numerous local ports, or a suspicious local process rapidly interrogating external ports.
- **Algorithm:** Groups flows by `(sourceIp, destinationIp)`. Counts unique `destinationPort` occurrences within the sliding window.
- **Formal Threshold:** `distinctPorts.size >= 15`.
- **Classification:** `ThreatType.NETWORK_PORT_SCAN`, Severity: `HIGH`, Confidence: `0.92`.

### 3.2 Horizontal Port Scan Detection
- **Mechanism:** Detects sweeping reconnaissance across an IP range targeting a specific service port (e.g., scanning for ADB port 5555 across a subnet).
- **Algorithm:** Groups flows by `(sourceIp, destinationPort)`. Counts unique `destinationIp` targets.
- **Formal Threshold:** `distinctIps.size >= 20`.
- **Classification:** `ThreatType.NETWORK_PORT_SCAN`, Severity: `HIGH`, Confidence: `0.90`.

### 3.3 Periodic Outbound Traffic / Beaconing Analysis
- **Engineering Principle:** Periodic traffic is classified as *potential automated beaconing*, not unconditionally as confirmed malware. Many benign services (such as NTP synchronization, OS push heartbeat signals, and mail clients) also poll at regular intervals. SuSagi Guardian flags low-jitter periodicity as an informational or moderate signal for correlation with message/link vectors, escalating to critical only when combined with suspicious endpoints or prior scam telemetry.
- **Algorithm:**
  1. For each destination IP with $N \ge 4$ flow observations, calculate the sequence of inter-arrival intervals:
     $$\Delta t_i = t_{i+1} - t_i$$
  2. Compute interval mean ($\mu$) and standard deviation ($\sigma$):
     $$\mu = \frac{1}{M} \sum_{i=1}^M \Delta t_i, \quad \sigma = \sqrt{\frac{1}{M} \sum_{i=1}^M (\Delta t_i - \mu)^2}$$
  3. Compute the **Coefficient of Variation / Interval Jitter** ($J$):
     $$J = \frac{\sigma}{\mu}$$
- **Formal Threshold:** $J \le 0.15$ with $\mu \ge 1000\text{ ms}$ indicates regular, automated periodic communication (distinguishing automated heartbeats from human web browsing jitter where typically $J > 0.60$).
- **Classification:** `ThreatType.C2_BEACONING`, Severity: `MODERATE / HIGH` (Escalated via Cross-Channel Correlation).

### 3.4 Volumetric Connection Burst Detection
- **Mechanism:** Detects sudden anomalous surges in concurrent outbound socket creation typical of automated scraping, exfiltration, or DDoS bots.
- **Algorithm:** Computes connection count within a rolling 5-second window.
- **Formal Threshold:** $\ge 30$ connections within 5 seconds.
- **Classification:** `ThreatType.NETWORK_ANOMALY`, Severity: `MODERATE`, Confidence: `0.78`.

---

## 4. Integration into Unified Threat Engine

The anomaly classifier is integrated directly into `UnifiedThreatEngine.kt`:

```kotlin
fun analyzeNetworkBatch(flows: List<NetworkFlowRecord>): List<InferenceResult> {
    val anomalies = networkClassifier.classifyBatch(flows)
    return anomalies.map { anomaly ->
        InferenceResult(
            threatScore = anomaly.confidence,
            threatSeverity = anomaly.severity,
            explanation = anomaly.description,
            recommendedActions = anomaly.suggestedMitigation,
            analysisSource = AnalysisSources.LOCAL_CLASSIFIER,
            modelInferenceMs = anomaly.processingDurationMs
        )
    }
}
```

---

## 5. Offline Automated Verification Summary

Evaluated in `NetworkAnomalyClassifierTest.kt` and `CybersecurityBenchmarkEvaluationTest.kt`:

| Test Name | Flow Profile Description | Expected Result | Actual Outcome | Status |
| :--- | :--- | :--- | :--- | :---: |
| `testVerticalPortScan` | Single IP probing 20 sequential ports (21–443) | `NETWORK_PORT_SCAN` detected | Detected (ports=20, severity=HIGH) | **PASS** |
| `testHorizontalPortScan` | Probing port 5555 across 25 distinct LAN IPs | `NETWORK_PORT_SCAN` detected | Detected (IPs=25, severity=HIGH) | **PASS** |
| `testC2PeriodicBeaconing` | 6 outbound requests at exact 15.0s intervals ($\pm 0.1s$) | Low-jitter beacon detected | Detected ($J = 0.006 \le 0.15$) | **PASS** |
| `testHumanJitterNonBeacon` | 6 web requests at random intervals (2s, 14s, 5s, 30s) | No periodic alert | Normal traffic ($J = 0.82 > 0.15$) | **PASS** |
| `testConnectionSpike` | Burst of 40 connections in 2 seconds | `NETWORK_ANOMALY` detected | Detected (burst=40, severity=MODERATE) | **PASS** |
| **Benchmark Suite (30 Batches)** | 15 Anomalous Batches + 15 Benign Batches | $TP=15, FP=0, TN=15, FN=0$ | Exact Conservation ($N=30$) | **PASS** |
