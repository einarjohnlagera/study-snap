# feedback-system.md - NoteLib Feature Context

## Goal

Collect soft-launch feedback from authenticated users without sending them out of the app.

## Flow

- authenticated users can open `Send Feedback` from one of two intentional entry points:
  - a small header feedback icon on focused app routes such as Note Editor, Note Detail / Study Pack view, and quiz flows
  - the floating launcher only on safe non-critical pages such as Dashboard, Library, Public Library, and Settings
- feedback is submitted through `POST /api/feedback`
- the request body contains only `message`
- the frontend also sends the current page through `X-Page-Url`
- successful submission should show:
  - `Thanks! Your feedback helps improve NoteLib.`

## Placement Rules

- core learning flows must stay distraction-free:
  - New Note / Edit Note
  - Study Pack / Note Detail view
  - Quick Review, Challenge Quiz, Board Exam Mode, and Adaptive Practice
  - quiz results and `Review Answers`
- the floating launcher must not appear on those focused flows
- quiz result screens should collect feedback inline with:
  - prompt: `Was this quiz helpful?`
  - actions: `Yes` and `Give Feedback`
- quiz review sections may still show inline issue-reporting actions such as:
  - `Report Question`
  - `Confusing Explanation`
  - `Something is wrong`
- avoid page-specific offset hacks or overlap fixes for feedback; route-aware placement rules are the source of truth

## Stored Data

`feedback` rows store:

- `user_id`
- `email`
- `message`
- `page_url`
- `created_at`
- `status` (`NEW`, `REVIEWED`, `CLOSED`)

## Admin Visibility

Admin Dashboard should show a read-only `Recent Feedback` table with:

- date
- user email
- message
- page URL
- status

## Email Notification

- feedback submission should attempt a best-effort support notification email
- notification failure must not block feedback persistence
- support destination comes from backend email configuration

## Scope Notes

- this is an internal launch-feedback workflow, not a public contact form
- feedback is currently read-only in admin; status update actions can be added later
