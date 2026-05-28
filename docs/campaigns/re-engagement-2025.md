# Re-engagement Campaign 2025

One-time campaign targeting users who signed up before v0.19.0 and have been inactive. Treated as transactional — sent regardless of `inactivityRemindersEnabled`.

## Eligibility

- Email verified
- Account not deactivated
- Has not received `RE_ENGAGEMENT_2025` email (deduplicated via `email_log`)
- Last active more than 30 days ago (or never active beyond signup)

## Trigger

Admin-initiated via the admin dashboard. A dry-run mode returns an eligible user count without sending. The send button fires the actual campaign.

## Email Log Type

`RE_ENGAGEMENT_2025` — version-stamped so future campaigns use a different stamp without conflicting.

## Segmentation

Segmented by `profileType`. Four variants:

---

### Variant: STUDENT / BOARD_EXAM / null (generic fallback)

**Subject:** NoteLib has grown a lot since you signed up

**Body:**

Hi {{firstName}},

When you first joined, NoteLib was just getting started. A lot has been built since then — here's what's waiting for you:

**Adaptive Practice** — focuses your review on concepts you keep missing, not ones you already know.

**Board Exam Mode** — simulates the real exam: timed, no feedback during the session, full question set. You can now span it across multiple notes for full-coverage exams.

**Spaced Repetition signals** — Key Concepts now tracks which topics are overdue for review and surfaces them automatically.

**Public Library** — discover and copy notes from the community to supplement your own.

Come back and see how much has changed.

[Go to Dashboard]

---

### Variant: PROFESSIONAL

**Subject:** NoteLib has grown a lot since you signed up

**Body:**

Hi {{firstName}},

A lot has been added since you first joined — including a mode built specifically for Professional users:

**Interview Practice** — scenario-based questions from your own notes, with AI feedback after each answer. Soft 2-minute timer simulates real interview pacing.

**Multi-note sessions** — span a study session across multiple notes for broader coverage.

**Public Library** — discover and copy notes from the community.

Come back and see how much has changed.

[Go to Dashboard]

---

### Variant: TEACHER

**Subject:** NoteLib has grown a lot since you signed up

**Body:**

Hi {{firstName}},

A lot has been added since you first joined — including tools built around how teachers actually work:

**Exam Builder** — select multiple notes, organize questions into sections, and export a combined DOCX exam.

**Shareable Quiz Links** — share a quiz directly with students, no signup required.

**Multi-note exams** — span Long Exam and Board Exam sessions across multiple notes for broader exam coverage.

Come back and see how much has changed.

[Go to Dashboard]

---

## CTA

Single CTA for all variants: **Go to Dashboard** → `{{dashboard_url}}`

## Implementation Notes

- New `RetentionEmailType.RE_ENGAGEMENT_2025` enum value
- New admin endpoint: `POST /admin/campaigns/re-engagement/send?dryRun=true|false`
  - `dryRun=true` → returns `{ eligibleCount: N }` without sending
  - `dryRun=false` → sends in batches, returns `{ sent: N, skipped: N }`
- New admin dashboard button on the Users or Engagement section: "Send Re-engagement Campaign" with a confirm step showing the dry-run count
- Send in batches (~50/sec) to avoid Resend rate limits
- New HTML + TXT templates for each variant under `templates/email/`
- Template names: `re-engagement-student`, `re-engagement-professional`, `re-engagement-teacher`
  - Student template is the generic fallback for null/unknown profile types
