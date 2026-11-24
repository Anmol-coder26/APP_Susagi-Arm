# Contributing to SuSagi Guardian V2

Thank you for your interest in contributing to SuSagi Guardian V2 (Bharat AI-SoC Challenge 2026–27 | PS3)!

## Development Setup
1. Clone the repository:
   ```bash
   git clone https://github.com/Anmol-coder26/APP_Susagi-Arm.git
   cd APP_Susagi-Arm
   ```
2. Open the project in Android Studio (Ladybug or newer).
3. Ensure Android SDK 34 (Android 14) and NDK 26+ are installed.
4. Copy `local.properties.template` to `local.properties` and set `sdk.dir`.

## Running Verification Tests
Execute the comprehensive JVM test suite:
```bash
./gradlew testDebugUnitTest
```
All 315 tests must pass before submitting pull requests.

## Code Style & Ethics
- Never disable or bypass security barriers.
- Maintain zero cloud leakage on user communications.
- Adhere to Master Directive 61 honesty guidelines: never fake model outputs or performance metrics.
