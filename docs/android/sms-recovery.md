# Bounded SMS recovery

`READ_SMS` is requested only from the Recovery screen after the user reads why it is needed. It is never requested on first launch. The reader queries `Telephony.Sms.Inbox` only; sent SMS, contacts, calls, and location are never accessed.

Each automatic scan is limited to 100 rows and the newer of a persisted cursor minus three minutes or 30 minutes ago. Rows are ordered oldest to newest. The cursor advances only after the processing attempt returns; a crash after queue insertion but before cursor update causes a safe replay handled by durable source-independent dedupe. A successful scan time is persisted. Manual sync rechecks the full 30-minute window so a newly approved sender can be tested safely.

The sender registry is checked before provider parsing. Raw bodies are parsed in memory and are not stored in Room, WorkManager input, logs, or cloud storage. The app stores only encrypted protocol payloads and normalized dedupe/status fields. Learning previews are in-memory, limited, and redacted.
