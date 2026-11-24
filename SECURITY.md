# Security Policy — SuSagi Guardian V2
Bharat AI-SoC Challenge 2026–27 | PS3: On-Device Threat, Phishing & Scam Detection

## Supported Versions
Only the latest release candidate branch (`main` / `v2.0.0-release`) receives security updates.

## Reporting a Vulnerability
If you discover a security vulnerability, model jailbreak vector, or false-negative bypass in SuSagi Guardian:
1. Please do NOT open a public GitHub issue.
2. Submit details privately to the project maintainers: Anmol Singh (Anmol-coder26).
3. Include a description of the attack vector, sample payloads, and observed behavior.

## Responsible Disclosure & Operating Boundaries
SuSagi Guardian runs deterministic on-device heuristics alongside small language model reasoning. It operates under standard Android application sandboxing (API 34) and does not require or request root privileges.
