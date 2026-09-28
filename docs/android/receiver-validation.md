# Physical receiver validation

Target observed during Phase 9: vivo V2425A, Android API 36. `persist.radio.multisim.config`
reported `dsds` (dual-SIM dual-standby capability). No SIM identifier, phone number or
subscription value was collected. The receiver records only whether subscription/slot
metadata was present and never treats it as receiver identity.

Debug builds keep one in-memory observation: trigger flag, sender category, 12-hex sender
and body SHA-256 prefixes, character length, part count, timestamp, parser status and a
subscription-metadata-present boolean. Logcat contains the same bounded fields. Raw sender,
body, OTP and transaction IDs are excluded. Release uses a separate no-op implementation.

The safe generic test body is `EKPAY RECEIVER TEST 2026`, sent by another phone/SIM after
granting RECEIVE_SMS. A valid result requires a genuine system SMS_RECEIVED, an
UNSUPPORTED_SENDER/IGNORED status, unchanged evidence queue, and no raw text in logcat.
Synthetic UI analysis or direct worker tests do not count. Foreground, background, locked,
swiped-away and permission-revocation states must each be recorded only when actually run.

Permission UI validation passed on the connected device: revoked state offered the enable
action; the granted state survived force-stop/restart and displayed enabled. Package state
showed the permission granted, the app enabled/not stopped and the receiver resolved for the
framework-protected action.

A genuine harmless SMS was visibly delivered by the carrier to Google Messages. Android's
broadcast queue resolved EkPay's manifest receiver but deferred it while the background
process was frozen. Bringing EkPay to the foreground released that same pending broadcast;
safe telemetry reported `UNSUPPORTED_SENDER`, and queue/dedupe remained `0/0`. Thus the
manifest receiver is **VERIFIED** when foregrounded/released, while **BACKGROUND REALTIME
DELIVERY IS NOT RELIABLE** on the tested vivo V2425A/API 36 device.

Autostart did not fix the observed deferral. A foreground service and a dynamic receiver
registered inside that foreground service were separately tested and did not fix it; both
experiments were removed. Locked, swiped-away and revoked-state genuine delivery were not
established. No protected broadcast was spoofed.

Multipart assembly trusts the system-provided part order but additionally requires 1–10
parts, one normalized sender, timestamps within two minutes, non-null bodies and at most
4096 characters. Public Android APIs do not expose enough stable concatenation metadata to
prove a carrier message-instance ID, so malformed/inconsistent groups are rejected.
