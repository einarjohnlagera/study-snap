# Account Deletion

Account deletion ships in two phases so users get a real deletion control with a reversible grace window before irreversible erasure.

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

Phase 2 is irreversible. A scheduled backend job purges accounts whose grace window has elapsed:

`status = PENDING_DELETION AND deleted_at <= now - deletionGraceDays`

The default grace window is 30 days. Reactivated accounts are `ACTIVE`, have `deleted_at = NULL`, and are never selected.

## Deleted User Sentinel

The purge retains public contributions and legally retained financial records by reassigning them to a fixed sentinel user:

- ID: `00000000-0000-0000-0000-00000000d1ed`
- Email: `deleted-user@notelib.internal`
- Display name: `Deleted user`
- Status: `SUSPENDED`
- Email verified: no
- Optional-email flags: all false

The sentinel cannot log in, does not receive retention or marketing email, and is not treated as a real active user. Public-note attribution falls back to `Deleted user` with no author profile link.

## Purge Disposition

| Data | Purge action |
|---|---|
| Public notes | Reassign `owner_user_id` to the deleted-user sentinel and keep them public. |
| Study Packs attached to retained public notes | Reassign `owner_user_id` to the deleted-user sentinel so public notes remain readable and copyable. |
| Private notes and non-retained Study Packs | Hard-delete. |
| Drafts, generated quizzes, quick-review sessions, concept health, activity events, bulk-generation results, quiz share links, public-note likes, library filters, user usage, collections and collection items | Hard-delete. |
| Linked-learner relationships where the user is supporter or learner | Cascade-delete with the user. |
| Linked-learner guardian-consent records tied to those relationships or to the user as learner/attestor | Cascade-delete; no consent record survives without all referenced users and its relationship. |
| The user's current birth year and nullable last-corrected timestamp | Delete with the user row; no declaration history exists. |
| A provisional birth year on an unconfirmed invitation-link redemption (`v0.95.0`) | Deletes through the foreign-key cascade — the row references `linked_learner_relationships(id)`, which references `users(id)`, both `ON DELETE CASCADE`. It is a live pending declaration rather than history, and acceptance, revocation or request expiry removes it earlier. |
| Combined quizzes (`combined_quizzes`, `v0.110.0`) | **Cascade-delete only.** `AccountPurgeService.deletePersonalRows` names every other user-owned table explicitly but not this one; the rows go through `owner_user_id ... ON DELETE CASCADE` when the user row is deleted. **Verified against real PostgreSQL to leave zero rows — there is no retention leak.** Recorded because the table holds copied note titles and full answer keys, and because an explicit delete would be more robust than relying on the cascade. |
| Auth providers, refresh tokens, verification tokens, password reset tokens, email logs, feedback, premium waitlist rows | Hard-delete. |
| Payment transactions, subscriptions, voucher redemptions | Reassign `user_id` to the deleted-user sentinel and retain. Active subscriptions are marked canceled at purge time. |
| Analytics events | Leave untouched. The `analytics_events.user_id` value may become orphaned and is retained for aggregate reporting. |
| User row | Delete last after dependent rows are deleted or reassigned. |

## Isolation And Retry

Each candidate user is purged in its own `REQUIRES_NEW` transaction. A failure rolls back only that user, logs the failure, leaves the account in `PENDING_DELETION`, and allows the next scheduled run to retry. Other eligible users in the same batch continue.

## Configuration

Account deletion purge configuration lives under `studysnap.account`:

- `deletion-grace-days`: number of days between soft-delete request and purge eligibility. Default: `30`.
- `purge-cron`: scheduled purge cron expression. Default: `0 30 3 * * *`.
