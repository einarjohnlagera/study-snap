# ROADMAP.md - NoteLib

Rebrand note: StudySnap has been rebranded to NoteLib while preserving current database schema naming unless explicitly changed.

Goal: evolve NoteLib from a one-shot generator into a reusable note-first study workspace.

## V2 (current direction)

Scope priorities:

- NoteLib rebrand across product surfaces and documentation
- Notes foundation (users can create and save notes)
- Note lifecycle states:
  - `Draft`
  - `Study Pack Ready`
- Note visibility states:
  - `PRIVATE`
  - `PUBLIC`
- Study Pack generation from Note content
- copy-based versioning (no overwrite flow)
- copy behavior:
  - copy user-authored fields (`title`, `subject`, `tags`, `content`)
  - do not copy generated outputs or performance history
  - do not copy quiz sessions
- navigation structure:
  - Main: Dashboard, My Library, Public Library
  - Account: Profile, Settings
- route structure:
  - `/library` (My Library)
  - `/library/public` (Public Library)
  - `/notes/{id}` (Note Detail)
  - `/public/library/{subject}` (Public Subject Listing, SEO)
  - `/public/library/{subject}/{slug}` (Public Note Detail, SEO)
- Premium Challenge Quiz (timed mode generated from Study Pack summary + key concepts)
- Premium Adaptive Practice (targeted generation from weak concepts)
- session resilience for Challenge/Adaptive (resume in-progress sessions)
- Plan & Billing usage visibility with separate buckets (Study Packs, Challenge Quiz, Adaptive Practice)
- PayMongo recurring subscription checkout (monthly/yearly) from Settings
- webhook-driven subscription lifecycle sync (activation, renewal, failed/unpaid/cancel handling)
- real email verification + generation gating for unverified users
- OCR upload gating for unverified users on Create/Edit Note
- My Library continuity (browse, filter, sort, revisit)
- Public Library continuity (browse public notes and copy into My Library)
- Public Library community polish (include your own public notes, public display names, and backend-driven NoteLib official badge)
- Public Library subject-page polish (subject listing completeness, subject badge consistency, sitemap coverage)
- sharing and remixing continuity (`/p/{token}`, copy to library without LLM call)
- note-centric ownership across generated content and quiz sessions (`noteId`)

Implementation stance:

- keep UX calm, practical, and non-overengineered
- keep data model and backend flows simple

Future Improvement - Public Profiles

- build lightweight public profile pages on top of persisted `users.display_name`
- keep impersonation guardrails through reserved display-name validation
- extend official/system-author handling without exposing private account emails
- keep Note as primary entity and Study Pack as generated enhancement state

## V3 (future direction)

Potential expansion areas:

- richer note workspace
- advanced note editing and note comparison
- copy lineage visualization
- deeper progress insights from quiz history
- optional snapshot/history table if product value is proven

## v0.4.0 - Profile-Based Experience & UX

- Profile Identity Save
- Email Change Verification
- Onboarding per Profile Type
- Personalized Dashboard
- Teacher Dashboard
- Note Editor UX Improvements
- Mode-based Note Creation
- Create Note from Library
- First-Time User Activation Flow

Implementation details:

- keep `Profile` focused on identity and account information
- keep `Settings` focused on preferences and app behavior
- support email-change verification through `pendingEmail`
- keep one shared note-first learning engine across all profile types

Future Improvement - Normalize Subjects Table

- create a dedicated `subjects` table (`id`, `name`, `created_at`)
- migrate distinct `notes.subject` values into `subjects`
- add `notes.subject_id` while keeping a safe backfill path from existing `notes.subject`
- eventually use normalized subject IDs for filtering, SEO subject pages, and reuse
- this is intentionally deferred; the current product continues to persist subject directly in `notes.subject`
- change dashboard CTA, section order, labels, and recommendations by `profileType`
- let teacher CTA buttons share one note pipeline through mode-based routing
- keep short profile-based primary button labels with helper text in the Note Editor
- repeat note-editor actions at the top and bottom on desktop and keep a floating CTA on mobile
- add explicit first-time activation guidance from verification through first Study Pack and first Challenge Quiz

## v0.5.0 - Board Exam Mode

- Exam Countdown
- Exam Readiness Score
- Study Plan
- Mock Exam Mode
- Performance Analytics

## Product learning loop

Capture -> Generate -> Review -> Improve -> Copy -> Repeat

Roadmap decisions should reinforce this loop rather than one-time output generation.

## Phase: Retention System (Future)

- Study Reminder Emails
  - Inactivity reminder
  - Weak concept reminder
  - Study pack not generated reminder
  - Come back reminder
  - Renewal reminder
  - Payment failed reminder
  - Win-back email after cancellation

- Learning Style affects reminder frequency
  - Focused → minimal reminders
  - Consistency → moderate reminders
  - Streak → frequent reminders + streak tracking

- Streak System
  - Consecutive study days
  - Longest streak
  - Dashboard streak UI

- Reminder Scheduler (daily job)
- Email templates via Resend
- Standardize user-facing emails around first-name personalization, consistent NoteLib branding, and current Free vs Premium plan messaging

## Future Feature — Public Profiles & Community

NoteLib will introduce public profiles to allow users to showcase their public notes and contributions.

Planned features:
- Public profile page (/u/{displayName})
- Display public notes created by the user
- Show total public notes
- Show total copies
- Show total views (future)
- Show total likes (future)
- Show top subjects
- Allow users to share their profile
- Official NoteLib profile for curated content

Purpose:
- Encourage knowledge sharing
- Motivate users to create high-quality notes
- Build a learning community around NoteLib
- Support board exam preparation content

## Legacy planning context

Older phase-by-phase roadmap details are preserved in `/legacy/ROADMAP.md`.
