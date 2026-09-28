# Notification recovery

Notification recovery is disabled until the user explicitly opens Android Notification Access settings and enables the service. The app cannot grant this access itself.

The listener first records bounded package-name metadata as `OBSERVED`. Notification text is read only for a package that the owner has mapped to a provider and marked `INTERNAL_APPROVED`. Unapproved notification content is discarded immediately. Approved title/text is parsed in memory; unrelated text and raw provider text are neither logged nor persisted. OTP, promotional, balance-only, outgoing, failed, cancelled, refund, and reversal content cannot become evidence.

Package names are never guessed or pre-approved. Generic listener delivery and real provider notification formats require physical-device observation before either can be marked verified.
