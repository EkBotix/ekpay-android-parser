# Internal permissions

This internal build declares:

- `INTERNET` and `ACCESS_NETWORK_STATE` for the existing TEST-only signed API.
- `RECEIVE_SMS` for best-effort realtime receipt.
- `READ_SMS` for explicit, bounded missed-message recovery.
- `RECEIVE_BOOT_COMPLETED` only to enqueue lightweight WorkManager catch-up.
- Notification listener binding through the system-protected `BIND_NOTIFICATION_LISTENER_SERVICE` service contract.

It does not request `SEND_SMS`, contacts, call-log, phone-state, or location permissions. Notification Access and battery settings are opened through standard Android settings and are never granted or changed automatically.
