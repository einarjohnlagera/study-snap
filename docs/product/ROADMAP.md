# ROADMAP.md - NoteLib

Rebrand note: StudySnap has been rebranded to NoteLib while preserving current behavior and database schema.

Goal: evolve NoteLib from an AI study pack generator into a reusable note-based study workspace without overengineering.

## V2 (current direction)

Scope priorities:

- NoteLib rebrand across product surfaces and documentation
- Notes foundation (users can create and save notes)
- Notes library for revisiting saved notes
- one current Study Pack per note (`1 Note <-> 1 current Study Pack`)
- regenerate replaces the current Study Pack for that same note
- no visible Study Pack version history in V2
- Premium Challenge Quiz (timed exam-style mode using existing Study Pack quiz data)
- separate Premium review quotas for Challenge Quiz and Adaptive Practice (50/month each)
- Plan & Billing usage visibility with separate buckets (Study Packs, Challenge Quiz, Adaptive Practice)
- real email verification (tokenized links + resend flow) using provider-agnostic email service abstraction
- unverified users can access account flows but are blocked from Study Pack generation until verified
- guided review entry hierarchy:
  - Study Pack detail: Quick Review primary, Challenge secondary, Adaptive only when weak concepts exist
  - Quick Review results: recommend Adaptive when struggling, Challenge when strong
- public study library support and sharing flow continuity
- library UX improvements (browse, filtering, sorting, scanability, low-friction revisit)

Implementation stance:

- keep UX calm, practical, and non-overengineered
- keep data model and backend flows simple
- avoid introducing multi-pack-per-note architecture in V2

## V3 (future direction)

Potential expansion areas:

- richer note workspace
- advanced note editing
- optional Study Pack version history
- future multi-version capabilities if product value is proven

Versioning direction (if needed):

- add a dedicated `study_pack_history` snapshot table
- preserve the V2 primary relationship and keep version history additive
- likely snapshot fields: `id`, `parent_study_pack_id`, `note_id`, `title`, `summary`, `subject`, `concepts_json`, `questions_json`, `version_number`, `archived_at`

## Legacy planning context

Older phase-by-phase roadmap details are preserved in `/legacy/ROADMAP.md`.
