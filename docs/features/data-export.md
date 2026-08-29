# Data Export

## Summary

NoteLib supports GDPR-style data portability through an authenticated account export. The export is additive and read-only: it does not create storage, enqueue jobs, or mutate user content.

## Endpoint

- `GET /auth/account/export`
- Requires authentication.
- Resolves the requester from the authenticated principal. The endpoint never accepts a `userId` parameter.
- Rate-limited with `AuthRateLimitService.assertAllowed("data-export", userId)`.
- Returns a single JSON attachment:
  - `Content-Type: application/json`
  - `Content-Disposition: attachment; filename="notelib-export-<yyyy-MM-dd>.json"`

## JSON Scope

Included:

- `meta`: `exportedAt`, `schemaVersion`
- `account`: profile basics owned by the user, including email, first/last/display name, username, profile type, learner level, course/program, study goal, focus subjects, exam date, account-global `birthYear`, `provisionalBirthYears[]`, and account creation time
- `account.provisionalBirthYears[]`: birth years the requester declared **as the learner** when redeeming an invitation link, each with its `relationshipId` and `declaredAt`. **⚠️ Deliberately separate from `birthYear` and never merged into it** — `users.birth_year` is account-global and write-once, while a provisional declaration is neither and becomes the account year only if the link's creator confirms. Merging them would make the one surface that exists to state what is held accurately assert an account-global value that was never written. **⚠️ It is a LIST because a learner can hold more than one**: the table is keyed by relationship, so someone who redeems two links before either creator confirms has two independent declarations. **⚠️ Only rows where the requester is the LEARNER are exported** — the table has no user column, so the query joins `linked_learner_relationships` on `learner_user_id`; that join is the privacy boundary, not a filter applied afterwards. Expiry and revocation delete the row, so it stops appearing — correct, not a regression.
- `notes[]`: owned public and private notes, including title, subject, note-level `domainContext`, note-level `learnerLevel`, content, visibility, copy source title, and timestamps; both new metadata fields may be `null`
- `studyPacks[]`: owned Study Packs, including linked `noteId`, title, summary, key concepts, and quiz
- `collections[]`: owned Study Plans/collections, with ordered owned note references by id and title
- `practiceSummary`: aggregate completed-session counts, per-mode counts, and the latest completed-session timestamp

Excluded:

- Password hashes, tokens, verification/reset secrets, `tokenVersion`, failed-login counters, lock state, and other internal/security fields
- Raw quiz/practice session state and per-question history
- Analytics events
- Financial and billing records, including payments, subscriptions, transactions, vouchers, and provider event data
- Any data not owned by the requester
- Linked-learner relationship and guardian-consent rows are not currently included. The export exposes the requester's current `birthYear` and their own provisional declarations, but **does not disclose counterparties** or relationship-specific attestations. `provisionalBirthYears[]` carries a `relationshipId` the requester is already a party to, and no counterparty identity.

## Design Decisions

- The export is synchronous JSON, not a ZIP and not an async "email me a link" job.
- Empty accounts are valid exports with empty arrays.
- Public notes owned by the requester are included because they are still the user's data.
- Copied notes created by the requester are included as the requester's own rows.
- Collection item references are emitted only when the referenced note exists in the requester's owner-scoped note set.

## Frontend

Settings includes a `Download my data` button in the Account section. It calls `downloadMyData()`, saves the returned blob as the server-provided filename, disables while in flight, and shows an inline error if the download or rate limit fails.
