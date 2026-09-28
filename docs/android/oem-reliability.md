# OEM reliability

On the tested Vivo V2425A/API 36, genuine `SMS_RECEIVED` can be deferred while the app process is frozen. Foreground-service and dynamic-receiver experiments did not fix that behavior and remain removed.

Phase 10 treats realtime delivery as best effort and adds two explicit fallbacks: a 15-minute WorkManager inbox recovery with a persisted overlap cursor, and an opt-in Notification Listener restricted to manually approved packages. Boot handling only schedules WorkManager; it performs no database or inbox work in the broadcast receiver. The UI offers standard battery optimization settings without hidden Vivo APIs or automatic whitelisting.

No architecture can guarantee realtime delivery under OEM process controls. Recovery correctness depends on permission retention, a recent message remaining inside the 30-minute window, deterministic provider rules, and durable dedupe.
