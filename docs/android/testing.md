# Android sandbox validation

Run Gradle test/lint/assembleDebug with JDK17/SDK36. Core tests cover shared Node vector,
RFC8032 signature/public encoding, exact canonical/raw bytes/hash, header/nonce/stable ID,
safe destinations/test references and retry policy. Android unit tests use Robolectric
API28 and Room to exercise pairing/token non-persistence/rotation, encrypted queue,
idempotent retry nonce refresh, auth/revocation stop and missing-key fail closed.
They use an in-memory test vault/keys; not a substitute for Android Keystore runtime tests.

At D:\ekpay run npm run test:phase7 for real Kotlin HTTPS client → existing Phase 6 handler
→ native disposable PostgreSQL. Tests register/pair, heartbeat, evidence/stable retry,
altered signature, stale time, nonce semantic conflict, wrong version, approved re-pair
rotation/old-key denial and revocation. Exact accepted nonce retry returns cached result
per Phase 6; reused nonce with changed ID/facts is rejected. DB final asserts one evidence
and zero authoritative transactions. No hosted data/source/verification enabled.

Phase 10 manifest tests allow RECEIVE_SMS, READ_SMS and boot scheduling for the internal
build while rejecting SEND_SMS, contacts/calls/phone identifiers, location, cleartext,
backups and unguarded exports. WorkManager's necessary boot/
wake/foreground/scheduler declarations are reviewed separately, not mistaken for SMS access.
Instrumentation on the Vivo covers key storage, Room
migration, atomic SMS queue/dedupe, retry and dedupe edge cases. A genuine carrier-delivered
SMS was verified when the tested Vivo app was foregrounded/released. Realtime background
delivery was deferred on that device; synthetic tests never establish runtime delivery.
Phase 10 adds source-independent receiver/inbox/notification dedupe and migration 3-to-4
coverage. Physical-device recovery/listener/boot/process-death results must remain
UNVERIFIED when the device is not connected.
