# feedback-system.md - NoteLib Feature Context

## Goal

Collect soft-launch feedback from authenticated users without sending them out of the app.

## Flow

- authenticated users can open `Send Feedback` from intentional passive entry points:
  - a small header feedback icon on focused app routes such as Note Editor, Note Detail / Study Pack view, and quiz flows
  - the floating launcher only on safe non-critical pages such as Dashboard, Library, Public Library, and Settings
- the app also makes two proactive, inline asks through the same freeform pipeline:
  - after the user's first-ever completed quiz session across Quick Review, Challenge Quiz / Board Exam, Adaptive Practice, and Long Exam
  - on Dashboard when a user returns after the configured inactivity threshold
- feedback is submitted through `POST /api/feedback`
- the request body contains only `message`
- the frontend also sends the current page through `X-Page-Url`
- successful submission should show:
  - `Thanks! Your feedback helps improve NoteLib.`
- quick-action templates open the modal with a dismissible `Reporting: {action}` context chip; dismissing the chip clears the prefilled message without submitting or closing the modal
- Admin's `Recent Feedback` table keeps compact, truncated cells and provides a per-row `View` modal for the complete submission already present in the dashboard response

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
- on the first-ever completed quiz result, replace that generic prompt with `How did your first quiz go?` and templated clear/confusing actions; never render both asks together
- quiz review sections may still show inline issue-reporting actions such as:
  - `Report Question`
  - `Confusing Explanation`
  - `Something is wrong`
- avoid page-specific offset hacks or overlap fixes for feedback; route-aware placement rules are the source of truth

## Proactive Trigger Rules

- first-quiz detection is owner-wide and mode-neutral: any completed quiz session on any Study Pack makes later results use the existing generic panel
- return-after-inactivity reuses `RetentionService`'s `MEANINGFUL_STUDY_ACTIVITIES` checks and `StudySnapProperties.Retention.inactivityDays`; it does not define a second threshold or activity list
- both proactive asks use user-scoped keys under the existing `notelib-guidance-dismissed-` localStorage convention and are marked seen on first display; dismissing without submitting therefore does not make an ask reappear
- the welcome-back ask requires prior quiz history and checks a session-scoped marker for the first-quiz ask, so a returning user's first-ever quiz receives only the higher-priority first-quiz ask even if return context is fetched again on the same visit; the inactivity ask remains eligible on a later qualifying return if it has not been shown
- failures loading first-quiz context fall back to the generic quiz-results panel; failures loading return context render no proactive Dashboard ask
- all quick actions prefill the existing freeform feedback message and submit unchanged through `POST /api/feedback`; no rating or prompt-specific fields are stored

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
- a per-row `View` action that opens the full message, clickable page URL, user email, full submission timestamp, and read-only status without issuing another request

## Email Notification

- feedback submission should attempt a best-effort support notification email
- notification failure must not block feedback persistence
- support destination comes from backend email configuration

## Scope Notes

- this is an internal launch-feedback workflow, not a public contact form
- feedback is currently read-only in admin; status update actions can be added later
