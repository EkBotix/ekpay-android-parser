# SMS privacy boundary

EkPay uses RECEIVE_SMS for best-effort realtime delivery. In the Phase 10 internal build,
READ_SMS can be granted separately from the Recovery screen for a bounded inbox-only scan.
It does not inspect sent SMS, request contacts or phone state, or infer the receiver account
from SIM/slot data. See [sms-recovery](sms-recovery.md).

Parsing occurs in memory. WorkManager never receives a raw body or sender. Room stores an
encrypted protocol body plus normalized dedupe/status metadata. Raw SMS and raw sender are
not stored in WorkManager, Room or logs.
Without an approved deterministic provider timestamp, the receiver creates no evidence job;
local receipt time is never substituted as provider time.

SHA-256 is deterministic for exact UTF-8 body bytes and stable across retries, enabling
duplicate detection without retaining the body. Whitespace changes change the hash. A hash
is an identifier, not proof of sender or payment authenticity. Debug observation retains
only short hash prefixes in memory; release telemetry is a no-op.
