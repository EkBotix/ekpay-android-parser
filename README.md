# EkPay Parser — Sandbox/Test Mode

Kotlin Android companion at `D:\ekpay-android-parser`, application namespace
`com.ekbotix.ekpayparser`; debug app ID adds `.sandbox`. minSdk 28, target/compileSdk 36.
Android Studio compatible, Gradle 8.13/AGP 8.13.2, JDK 17. No GitHub publication.

Capabilities: secure local identity, test pairing/dashboard re-pair rotation, Ed25519
signing, signed heartbeat, synthetic evidence generator, Room encrypted payload queue,
WorkManager retry, sandbox `RECEIVE_SMS` receiver, debug-only Format Lab/safe receiver
telemetry, and explicit synthetic TEST_* provider rules.

NOT implemented: inbox/history reading, production sender activation, verified real provider
formats, real payment ingestion, provider API, production verification or live deployment.
The app requests RECEIVE_SMS only; READ_SMS, SEND_SMS and READ_PHONE_STATE are absent.
SMS observation is best-effort: the tested vivo V2425A/API 36 device deferred manifest
delivery while the cached app process was frozen. The foreground/released path is verified,
but absence of SMS evidence never proves payment failure. Production provider formats and
senders remain disabled and unverified.

Every screen displays TEST MODE/SANDBOX. All references must start TEST_; optional sender
reference also TEST_ and hashed. No phone/hardware identifiers. No production destination
is accepted: debug networking is default-off, restricted to HTTPS loopback addresses;
release has blank URL and networking off. No certificate/hostname bypass or cleartext mode.

## Build and test

Configure JDK 17 and Android SDK 36, copy `local.properties.example` to ignored
`local.properties` and set only your SDK path initially. No backend secrets belong there.
On Windows use `gradlew.bat`, on other platforms `./gradlew`:

```powershell
.\gradlew.bat test
.\gradlew.bat lint
.\gradlew.bat assembleDebug
```

Generated APK: `app/build/outputs/apk/debug/app-debug.apk`. Do not publish this sandbox APK.
Unit tests cover protocol/RFC crypto/retry, Robolectric Room/repository states and manifest
policy. Real Android Keystore/OEM/background process tests require an emulator/device;
JVM tests do not establish hardware-backed support.

## Disposable integration only

Backend Phase 6 has no public pairing endpoint and rejects parser traffic outside NODE_ENV=test.
Do not weaken those routes. At `D:\ekpay` run `npm run sandbox:android` for a disposable
loopback PostgreSQL + HTTPS bridge using the exact existing handlers/RPCs. It exposes
local-only fixture registration/revoke/rotation controls, not hosted API capabilities.
Open its printed HTTPS URL to display one new pairing token once. Bridge logs no token.

The bridge generates a one-day ephemeral development CA certificate and prints its public
path. Import this CA into a debug emulator only; verify the displayed localhost certificate.
For Android emulator use HTTPS `10.0.2.2:<printed port>` (host loopback), not HTTP. The debug
domain config allows user-added CA only for 10.0.2.2/localhost/127.0.0.1. Release trusts
system CA only. Configure `ekpay.testBaseUrl` to that HTTPS URL and
`ekpay.sandboxNetworking=true` in ignored local.properties; rebuild debug. No real Android
device or provider account is needed. Remove this CA after the session; stopping the bridge
destroys fixture DB and TLS keys/certificate. Never enable hosted parser policies for testing.

At `D:\ekpay`, `npm run test:phase7` exercises the real Kotlin HTTPS client against the
disposable actual Phase 6 handlers/native PostgreSQL. It verifies pairing/heartbeat/evidence,
stable retries, bad signature/time/nonce/version, rotation and revocation without Android
hardware. It trusts only the generated test CA with standard hostname verification.

## Key security and recovery

Native non-exportable Ed25519 is capability-probed on API 33+. If unavailable, explicit
Settings consent permits sandbox-only software Ed25519 encrypted with non-exportable
Android Keystore AES-256-GCM in no-backup storage. No plaintext key file/private export API.
Software signing material exists temporarily in process memory, with weaker compromise
resistance than native Keystore signing. This fallback is not a production security claim.

Pending candidate/active key state persists atomically; token stays in UI/request memory only
and clears after each attempt. Keys switch only after confirmed server success. Uncertain
pairing preserves candidates and stops ingestion until owner/admin reviewed re-pairing.
Missing/corrupt key pauses all work, never silently replaces an active identity. Generic
401 pauses queue; it is not proof of revoked status. Confirmed/local terminal revocation
requires new registration, no reactivation. See [security](docs/android/security.md) and
[implementation report](docs/phase-7-implementation.md).
