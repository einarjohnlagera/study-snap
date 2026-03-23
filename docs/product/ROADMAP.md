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

## Product learning loop

Capture -> Generate -> Review -> Improve -> Copy -> Repeat

Roadmap decisions should reinforce this loop rather than one-time output generation.

## Legacy planning context

Older phase-by-phase roadmap details are preserved in `/legacy/ROADMAP.md`.
