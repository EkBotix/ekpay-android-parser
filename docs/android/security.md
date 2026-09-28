# Android internal parser security boundary

Phase 10 adds explicit `READ_SMS` for a 30-minute/100-row inbox-only recovery scan and
`RECEIVE_BOOT_COMPLETED` to schedule WorkManager. It still excludes SEND_SMS, contacts,
call logs, phone identifiers and location. Notification access uses the system-protected
listener binding and processes content only for manually approved packages. FLAG_SECURE,
backup exclusions and the TEST-only build marker remain. No service/API/HMAC backend key is
present in the app.

Networking default-off; release URL blank/off; debug HTTPS destination restricted to host
loopback localhost/127.0.0.1/emulator 10.0.2.2. System CA validation and hostname checks;
debug user CA trust only on these domains. No HTTP option, trust-all, global developer CA,
TLS pinning hack, external redirect or certificate verifier override. Generated local CA
only in integration tests; remove emulator trust after testing. Production never opened.

Keystore/protected software differences are explicit; see [crypto](crypto.md). Encrypted
queue/state with device binding, no token persistence, candidates retained for uncertain
rotation, missing keys/revocation fail closed. Debug logs allow HTTP status/known queue
state only, default off; no payload, signature, key, token or Throwable detail.

Not production hardened: no verified OEM/hardware support matrix, attestation, user-auth
policy, penetration review, fleet operations, monitored limits, store permission review or
real-source provenance. Distributed device/IP/pairing attempt limits remain backend launch
blockers. Signing a synthetic observation does not prove provider-origin authenticity.
