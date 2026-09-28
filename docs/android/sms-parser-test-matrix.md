# SMS parser test matrix

Automated core tests cover all four TEST_* provider rule sets; exact decimal conversion;
amount plus fee/balance; multiple amounts and IDs; OTP/authentication English and Bangla;
promotion English and Bangla; balance-only, sent, cash-out, refund, reversal and failure;
NBSP, Unicode punctuation and Bangla digits; sender normalization; exact-body hashing; and
bounded multipart ordering/sender/time checks.

Connected tests cover atomic queue/dedupe insertion, duplicate suppression, retry after
failure, provider-scoped transaction IDs, cross-message hash dedupe, Room 2→3 preservation,
and Android key storage. They do not claim genuine SMS_RECEIVED delivery.

Expected dedupe behavior: same provider+transaction is duplicate despite whitespace;
the same transaction ID across providers is allowed when hashes differ; the same body hash
is duplicate across provider/transaction/timestamp; multipart redelivery resolves to the
same body hash. The hash has no authenticity meaning.
