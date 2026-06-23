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
- `account`: profile basics owned by the user, including email, first/last/display name, username, profile type, learner level, course/program, study goal, focus subjects, exam date, and account creation time
- `notes[]`: owned public and private notes, including title, subject, content, visibility, copy source title, and timestamps
- `studyPacks[]`: owned Study Packs, including linked `noteId`, title, summary, key concepts, and quiz
- `collections[]`: owned Study Plans/collections, with ordered owned note references by id and title
- `practiceSummary`: aggregate completed-session counts, per-mode counts, and the latest completed-session timestamp

Excluded:

- Password hashes, tokens, verification/reset secrets, `tokenVersion`, failed-login counters, lock state, and other internal/security fields
- Raw quiz/practice session state and per-question history
- Analytics events
- Financial and billing records, including payments, subscriptions, transactions, vouchers, and provider event data
- Any data not owned by the requester

## Design Decisions

- The export is synchronous JSON, not a ZIP and not an async "email me a link" job.
- Empty accounts are valid exports with empty arrays.
- Public notes owned by the requester are included because they are still the user's data.
- Copied notes created by the requester are included as the requester's own rows.
- Collection item references are emitted only when the referenced note exists in the requester's owner-scoped note set.

## Frontend

Settings includes a `Download my data` button in the Account section. It calls `downloadMyData()`, saves the returned blob as the server-provided filename, disables while in flight, and shows an inline error if the download or rate limit fails.
