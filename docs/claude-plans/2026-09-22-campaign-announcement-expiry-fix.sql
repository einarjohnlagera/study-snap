-- Campaign Feedback announcement — set expires_at to match the campaign's own close boundary.
--
-- ⚠️ OWNER-RUN ONLY. Claude does not execute this (production write, prohibited by CLAUDE.md's
-- read-only rule). Paste into DBeaver / pgAdmin / JetBrains and run the UPDATE yourself.
--
-- WHY THIS IS NEEDED (found 2026-09-22, same day the announcement was published):
-- The "Help us improve NoteLib" announcement (cta_path = '/feedback') was published with
-- expires_at = NULL. Inbox visibility is evaluated on READ against status/expires_at
-- (NotificationRepository.findVisibleInbox) — there is no sweep. With expires_at NULL and
-- status PUBLISHED, the invitation stays live in every learner's bell INDEFINITELY, even after
-- the campaign itself stops accepting responses at notelib.campaign.study-friction-2026-09.closes-at
-- (2026-10-06T00:00:00Z). A learner who taps it after that date lands on the graceful CLOSED
-- STATE, so nothing breaks — but the bell keeps offering a survey that can no longer be answered.
--
-- The Admin UI cannot fix this directly: once PUBLISHED, an announcement's fields are immutable
-- in the form (announcement.editable is false post-publish) — only an "End" button is exposed,
-- which retracts it immediately via the ENDED status. That is a valid fix TOO, but requires the
-- owner to remember to click it on 2026-10-06 specifically. This statement is the alternative:
-- set the expiry to line up with the campaign's own close date NOW, so the invitation retracts
-- itself automatically on schedule, matching what the announcement's own copy promises ("takes
-- about a minute" — implicitly, before the window is closed).
--
-- Both fixes are equally correct. Do EITHER this UPDATE, OR click "End" on 2026-10-06 in
-- Admin → What's New. Do not do both (clicking End after this UPDATE has already taken effect is
-- harmless — ENDED and an expired PUBLISHED row both retract the same way — but there is no need).
--
-- ⚠️ If notelib.campaign.study-friction-2026-09.closes-at is ever moved (it is env-overridable via
-- CAMPAIGN_CLOSES_AT on Render, per RELEASES.md's v0.156.0 section), re-run this UPDATE with the
-- new timestamp, or the two boundaries drift apart again.

-- Pre-check: confirm the one row this targets, and that it is still exactly what we expect.
SELECT id, status, published_at, expires_at, cta_path, cta_label, audience
FROM announcements
WHERE cta_path = '/feedback' AND status = 'PUBLISHED';
-- Expected: exactly 1 row, expires_at = NULL, before running the UPDATE below.

-- The write. Idempotent: re-running it after it has already applied is a harmless no-op (the
-- WHERE clause only matches while expires_at is still NULL).
UPDATE announcements
SET expires_at = '2026-10-06T00:00:00Z'
WHERE cta_path = '/feedback'
  AND status = 'PUBLISHED'
  AND expires_at IS NULL;
-- Expected: UPDATE 1.

-- Post-check: confirm exactly one row now carries the matching expiry.
SELECT id, status, published_at, expires_at, cta_path
FROM announcements
WHERE cta_path = '/feedback';
-- Expected: expires_at = 2026-10-06T00:00:00Z (or NULL if you instead chose to click "End" later
-- and never ran this file — in which case this SELECT is just a status check, not a discrepancy).
