# Email Preferences

Settings includes a dedicated Email Preferences center for optional email categories.

## Optional Email

Users can opt in or out of:

- Study reminders: `inactivityRemindersEnabled`
- Weak-concept nudges: `weakConceptRemindersEnabled`
- Weekly summary: `weeklySummaryRemindersEnabled`
- Product news & tips: `marketingEmailsEnabled`

All four flags default to `false` until the user opts in. The write endpoint is `POST /auth/preferences/email-preferences`.

## Always Sent

Transactional email is disclosed as always sent and is not represented as a toggle:

- Account & security: sign-in verification and password resets
- Billing: payment receipts, plan-expiry reminders, and refunds

Transactional sends must not be gated by optional email preferences.
