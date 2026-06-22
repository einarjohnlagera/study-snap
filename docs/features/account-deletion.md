# Account Deletion

Account deletion ships in two phases so users get a real deletion control without making the first release destructive.

## Phase 1: Soft Delete

Phase 1 is reversible. A signed-in user requests deletion from Settings by typing `DELETE`.

- The backend sets `users.status = PENDING_DELETION`.
- The backend records `users.deleted_at`.
- All refresh tokens for the user are revoked.
- Normal login is blocked with `ACCOUNT_PENDING_DELETION`.
- No notes, Study Packs, sessions, payments, subscriptions, vouchers, public content, or analytics rows are deleted or anonymized.

The grace window is 30 days from `deleted_at`.

## Reactivation

Users can reactivate during the grace window by logging in with valid credentials through `POST /auth/account/reactivate`.

Supported credentials:

- Email or username + password
- Google credential for a linked or matching verified Google account

Reactivation restores `users.status = ACTIVE`, clears `deleted_at`, and issues normal auth tokens.

## Phase 2: Purge And Anonymization

Phase 2 is intentionally separate from the soft-delete release.

At purge time:

- Private notes and generated study data are hard-deleted.
- Public notes are retained but anonymized by reassigning them to the deleted-user sentinel.
- Payment, subscription, and voucher records are anonymized and retained for financial records.
- `analytics_events` are retained and never deleted as part of account deletion.

Phase 2 must be transactional, idempotent, and audited before enabling irreversible deletion.
