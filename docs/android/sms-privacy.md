# SMS privacy boundary

EkPay listens only to newly delivered SMS after explicit RECEIVE_SMS grant. It does not use
READ_SMS, scan inbox/history, request contacts or phone state, or infer the receiver account
from SIM/slot data. Receiver identity remains null without trustworthy provider data.

Parsing occurs in memory. WorkManager receives only provider, synthetic transaction ID,
minor-unit amount, SHA-256 message hash, local receipt time and an independently approved
provider timestamp. Raw SMS and raw sender are not stored in WorkManager, Room or logs.
Without an approved deterministic provider timestamp, the receiver creates no evidence job;
local receipt time is never substituted as provider time.

SHA-256 is deterministic for exact UTF-8 body bytes and stable across retries, enabling
duplicate detection without retaining the body. Whitespace changes change the hash. A hash
is an identifier, not proof of sender or payment authenticity. Debug observation retains
only short hash prefixes in memory; release telemetry is a no-op.
