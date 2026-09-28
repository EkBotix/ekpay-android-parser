# Internal provider registry

Sender and notification package entries use four states: `UNVERIFIED`, `OBSERVED`, `INTERNAL_APPROVED`, and `DISABLED`. Only `INTERNAL_APPROVED` entries may feed real-message parsing. The fixed `TEST_*` senders are internal synthetic fixtures.

The registry is encrypted with the app's Android Keystore-backed protected storage and is capped at 100 entries. It stores identity metadata, provider mapping, state, kind, and observation time. It never stores raw SMS or notification text. Approval applies only to this owner's private build and is not a production/provider certification.

Provider Learning reads at most 25 redacted candidates from the same 30-minute inbox window, excludes OTP and promotions, and masks phone numbers, balance values, and most reference characters. Selecting a provider and approving a sender creates the local parser fixture mapping; parsing remains deterministic and ambiguity checks remain unchanged.
