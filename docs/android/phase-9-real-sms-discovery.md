# Phase 9 real SMS discovery and parser hardening

Starting checkpoint was clean `main` at `d5cb10c`. Physical device discovery found vivo
V2425A, API 36, serial expected by the operator and `dsds` capability; no personal device
identifier is reproduced in diagnostics or application storage.

Provider formats and production senders remain UNVERIFIED and disabled. Format Lab is a
debug-only navigation path for manually redacted samples and performs local in-memory
candidate analysis only. It reports amount/transaction candidates, direction, status,
timestamp absence and ambiguity. It does not persist or upload samples.

Rules are explicit per provider and only TEST_* senders are active. Authentication,
promotion, balance-only, outgoing, refund, reversal, failure and cancellation semantics
fail closed. Amount candidates exclude fee/balance/cashback context and multiple unresolved
amounts or transaction IDs return AMBIGUOUS. Bangla digits/Unicode spaces/punctuation are
normalizable utilities, not claims about observed provider formats.

Provider and local timestamps remain distinct. No provider timestamp format/timezone has
been observed and approved, so providerTimestamp stays null and the receiver does not enqueue
backend evidence. Receiver identity also stays null. Backend evidence schema/protocol and
trust semantics are unchanged.

Genuine carrier SMS delivery and the manifest receiver were verified on the tested vivo
V2425A/API 36 device when the app was foregrounded or its process was released. With the
app backgrounded, Android's broadcast queue showed the manifest delivery deferred while
the cached process was frozen; bringing the app forward released the same pending broadcast.
Autostart, a foreground-service experiment and a dynamic-receiver-inside-FGS experiment did
not make background delivery realtime on this device. Those two workaround experiments were
removed. This is a scoped device/OS observation, not a universal Android limitation.

The supported architecture therefore treats SMS observation as best-effort:

- `RECEIVED_REALTIME`: the receiver runs near system delivery time.
- `RECEIVED_DELAYED`: a queued broadcast runs later after the process is released.
- `NOT_OBSERVED`: EkPay has no receiver evidence for that SMS.

Delayed or absent SMS evidence never proves that a payment failed. Verification remains
evidence-driven and cannot infer payment state from silence.

NotificationListenerService is a separate research option and is **NOT EVALUATED**. It
would require explicit notification access and analysis of notification privacy, OEM and
provider-app formatting, provider-app dependence, Play policy and user trust. It is not part
of the supported Phase 9 architecture.

Observed permission lifecycle: after APK install, ADB revocation made the UI show “Enable
SMS Detection”; granting RECEIVE_SMS, force-stopping and restarting the app preserved the
grant and the UI showed “SMS Detection is ENABLED.” This confirms permission UI/restart
handling. It does not confirm that a revoked receiver ignores delivery because no genuine
SMS was sent during that state.
