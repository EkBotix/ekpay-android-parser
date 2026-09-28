# Phase 10 internal working parser

This is an **INTERNAL BUILD** for the owner's phone. It is **NOT FOR CLIENT DISTRIBUTION**, **NOT PLAY STORE READY**, and **LIVE PAYMENT AUTOMATION IS DISABLED**.

The canonical flow is `AcquiredPaymentMessage -> direction and registry checks -> provider parser -> NormalizedPaymentEvidence -> atomic Room dedupe and encrypted queue -> signed protocol-v1 request -> EkPay TEST backend`. Acquisition source is metadata: `SMS_RECEIVER`, `SMS_INBOX_RECOVERY`, `NOTIFICATION_LISTENER`, or `SYNTHETIC_TEST`; it does not weaken parsing rules.

An incoming message must have one exact positive BDT amount, one deterministic uppercase transaction ID, and a supported provider timestamp before it can enter the signed queue. Android receipt time is retained as `localObservedAt` and is never substituted for provider time. Unknown senders, unknown packages, ambiguous fields, outgoing payments, cash-outs, refunds, reversals, failures, cancellations, OTP, promotions, and balance-only messages fail closed.

The app retains the Phase 7 Ed25519 identity, stable ingestion ID, encrypted durable queue, retry policy, and TEST-only network boundary. It does not perform any BD21topup order, wallet, top-up, or Telegram action.

Verification status: physical-device pairing, bounded SMS recovery, generic notification listener, and physical synthetic evidence through signed protocol-v1 to sandbox backend DB persistence are **VERIFIED**. Tamper rejection is **VERIFIED**. Replay protection and offline retry are **NOT TESTED**. Real provider SMS and notification formats remain **UNVERIFIED**. BD21topup automation is **DISABLED / NOT IN SCOPE**.
