# SuSagi Guardian V2 — Verified Engineering Evidence Summary

See `FINAL_SUBMISSION/04_Evidence/Test_Evidence_Summary.md` and Chapter 15 of `SuSagi_Guardian_Final_Technical_Report.pdf` for complete tables.

* **Gradle JVM Unit Suite (`./gradlew testDebugUnitTest`):** **51 XML suites · 318 tests · 0 failures · 0 errors · 0 skipped · 12.368 s** cumulative runner time.
* **Physical Build D74 Observations (Vivo iQOO Z7 Pro 5G / MediaTek Dimensity 7200 / ARMv9-A / `79.17 GB` free):**
  - **Verified in UI / Video:** Home dashboard, Link Shield (`instagram.com` canonical host, `cybercrime.gov.in` confirmation gate, `192.168.1.100/...apk` threat log entry), Camera QR scan, Offline Demo A/B/C/H (`85/100 C2_BEACONING`), Model Manager Ready status (`Llama 3.2 1B SpinQuant INT4`, `Whisper Tiny INT8`), short offline `"hi"` greeting, Call Audit 1930 sheet, 10 Indian script toggles.
  - **Open / Unverified:** Speaker Guard live SBI simulation scored `LOW (5/100)` in Build D74; live handset tok/s, TTFT, memory footprint, physical Whisper WER/RTF, two-device Agora WAN VoIP, and remote Trusted Contact delivery remain unbenchmarked / under calibration.
