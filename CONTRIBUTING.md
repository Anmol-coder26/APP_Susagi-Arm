# Contributing to SuSagi Guardian

Thank you for helping improve **SuSagi Guardian**. Please follow these development and engineering honesty guidelines when submitting changes.

---

## 1. Local Development Setup

```bash
git clone https://github.com/Anmol-coder26/APP_Susagi-Arm.git
cd APP_Susagi-Arm
cp local.properties.template local.properties
# Set sdk.dir in local.properties (JDK 17, Android SDK API 26–35)
```

---

## 2. Verification Requirements

Run the full host unit test suite before opening a pull request:

```bash
./gradlew testDebugUnitTest
```

Verify that all suites (`318` unit tests across `51` suites at baseline) pass cleanly without regressions or suppressed assertions.

---

## 3. Security & Honesty Standards

- **Never commit secrets or private test data:** Keep `local.properties`, `keystore.properties`, `app/google-services.json`, `.env`, phone numbers, private URLs, and personal captures untracked.
- **Separate verification levels honestly:** Never cite host JVM unit tests or controlled scripted demos as proof of native Android LLM, Whisper ASR, or two-device VoIP hardware execution.
- **No bypasses:** Never remove `SafetyBarrier`, `ModelManager` pre-flight `ET12` validation, or release-build key stripping.
- **Do not commit large binary weights:** Provision `.pte` and tokenizer files externally via `ModelManager` / user Downloads.
