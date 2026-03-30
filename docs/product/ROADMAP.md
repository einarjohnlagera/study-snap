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
- sharing and remixing continuity (`/p/{token}`, copy to library without LLM call)
- note-centric ownership across generated content and quiz sessions (`noteId`)

Implementation stance:

- keep UX calm, practical, and non-overengineered
- keep data model and backend flows simple
- keep Note as primary entity and Study Pack as generated enhancement state

## V3 (future direction)

Potential expansion areas:

- richer note workspace
- advanced note editing and note comparison
- copy lineage visualization
- deeper progress insights from quiz history
- optional snapshot/history table if product value is proven

## v0.4.0 Account & Personalization

- Profile identity save polish
  - keep `Profile` focused on identity and account information
  - keep `Settings` focused on preferences and app behavior
  - support email-change verification through `pendingEmail`
- Profile type remains editable in `Profile`
- Learning Style and Study Reminders remain editable only in `Settings > Preferences`
- Onboarding personalization
  - reuse the existing onboarding flow
  - add `Profile Type` before Learning Style
  - add conditional `Exam Date` for `BOARD_EXAM`
  - keep `Learning Style` and `Study Reminder Frequency` as the existing middle steps
- Personalized Dashboard
  - keep one shared note-first learning engine across all profile types
  - change dashboard CTA, section order, labels, and recommendations by `profileType`
  - `STUDENT` emphasizes review continuity and weak concepts
  - `BOARD_EXAM` emphasizes challenge-quiz practice, exam countdown, and weak-area drilling
  - `TEACHER` emphasizes material upload and quiz creation without introducing teacher-specific entities
  - add frontend-only note entry modes so teacher CTA buttons can share one note pipeline with clearer routing intent

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

## Future Feature - Profile Types and Board Exam Mode

NoteLib will support different Profile Types so the app can adapt to the user's goal.

Profile Types:

- Student -> Normal study flow (Study Packs, Quick Review, Challenge Quiz, Weak Concepts)
- Teacher -> Generate quizzes and question banks from notes and reviewers
- Board Exam Taker -> Focus on Challenge Quiz, Weak Concepts, Adaptive Practice, and Progress Tracking

Future Board Exam Mode features may include:

- Exam readiness indicator
- Study schedule
- Weekly progress tracking
- Recommended study topics based on weak concepts

## Legacy planning context

Older phase-by-phase roadmap details are preserved in `/legacy/ROADMAP.md`.
