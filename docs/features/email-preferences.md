# Email Preferences

Settings includes a dedicated Email Preferences center for optional email categories.

## Optional Email

Users can opt in or out of:

- Study reminders: `inactivityRemindersEnabled`
- Weak-concept nudges: `weakConceptRemindersEnabled`
- Weekly summary: `weeklySummaryRemindersEnabled`
- Product news & tips: `marketingEmailsEnabled`

All four flags default to `false` until the user opts in. The write endpoint is `POST /auth/preferences/email-preferences`.

## Unsubscribe Links

Optional emails carry a signed stateless unsubscribe token. The token encodes only the user id and unsubscribe category, is HMAC-signed, has no expiry, and is verified by `POST /email/unsubscribe` without requiring login. The public frontend confirmation page is `/unsubscribe?token=...`.

Category mapping:

- `MARKETING` -> `marketingEmailsEnabled`
- `WEEKLY_SUMMARY` -> `weeklySummaryRemindersEnabled`
- `STUDY_REMINDERS` -> `inactivityRemindersEnabled`
- `WEAK_CONCEPT` -> `weakConceptRemindersEnabled`

Unsubscribe is idempotent. Valid tokens for missing or already-deleted users succeed as a no-op so the endpoint never reveals account existence. Users re-enable or manage all categories from the authenticated Email Preferences center in Settings.

## Always Sent

Transactional email is disclosed as always sent and is not represented as a toggle:

- Account & security: sign-in verification and password resets
- Billing: payment receipts, plan-expiry reminders, and refunds

Transactional sends must not be gated by optional email preferences and must not include unsubscribe links or `List-Unsubscribe` headers.
