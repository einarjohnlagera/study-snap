# feedback-system.md - NoteLib Feature Context

## Goal

Collect soft-launch feedback from authenticated users without sending them out of the app.

## Flow

- authenticated users can open `Send Feedback` from the in-app floating action button
- feedback is submitted through `POST /api/feedback`
- the request body contains only `message`
- the frontend also sends the current page through `X-Page-Url`
- successful submission should show:
  - `Thanks! Your feedback helps improve NoteLib.`

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
