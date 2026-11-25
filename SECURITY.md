# Security Policy — SuSagi Guardian

## Supported Versions

Security fixes target the active development baseline (`main`). Legacy prebuilt release artifacts created prior to credential hygiene refactoring are deprecated and should not be redistributed.

## Reporting a Vulnerability

If you discover a security defect, parser bypass, XSS/intent injection, or accidental secret exposure:

1. **Do not** disclose exploit details or private credentials in public GitHub issues.
2. Contact the maintainers directly via GitHub ([Anmol-coder26](https://github.com/Anmol-coder26)) with clear reproduction steps and affected file paths.

## Security & Operating Boundaries

- Core inspection engines (`SafetyBarrier`, URL canonicalization, `NetworkAnomalyClassifier`, CameraX QR parsing, Room storage) run locally inside standard unprivileged Android application sandboxing (`minSdk = 26`, `targetSdk = 35`) with no root access required.
- Certain optional features contact external endpoints when explicitly configured and invoked (Gemini fallback, Bhashini speech endpoints, Agora RTC/RTM token relay, Firebase Auth/FCM, Ably realtime relay). See [docs/SECURITY.md](docs/SECURITY.md) for full disclosure and key-rotation policy.
