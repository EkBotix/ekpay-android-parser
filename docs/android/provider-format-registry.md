# Provider format and sender registry

Real provider format state is **UNVERIFIED**. No production sender identifier is active.

| Provider | Active sender | State | Parser version | Approved transaction-ID label |
|---|---|---|---|---|
| bKash | `TEST_BKASH` | APPROVED synthetic only | `bkash-test-v2` | `TxnId` |
| Nagad | `TEST_NAGAD` | APPROVED synthetic only | `nagad-test-v2` | `TxnID` |
| Rocket | `TEST_ROCKET` | APPROVED synthetic only | `rocket-test-v2` | `TxnId` |
| Upay | `TEST_UPAY` | APPROVED synthetic only | `upay-test-v2` | `TrxID` |

Registry states are UNVERIFIED, OBSERVED, APPROVED and DISABLED. An unknown numeric or
alphanumeric sender remains UNVERIFIED and cannot parse. Observation never activates it.
APPROVED requires repeated consistent messages, defensible provider attribution, evidence
that OTP/promotional traffic does not reuse the identity, and explicit human approval.

Normalization removes formatting punctuation from numeric senders and canonicalizes a
leading `880` country code to `+880`; it deliberately does not equate local `01...` with
`+8801...`. Alphanumeric identifiers are trimmed and case-normalized. No real identifiers
are listed because none were supplied or approved.
