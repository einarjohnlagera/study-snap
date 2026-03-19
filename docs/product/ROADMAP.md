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
- Premium Challenge Quiz (timed exam-style mode with LLM-generated questions from Study Pack summary + key concepts)
- Challenge Quiz session resilience and seriousness UX:
  - discourage accidental exits while in progress
  - persist progress for refresh/crash recovery
  - resume unfinished sessions with real-time timer recalculation
  - auto-complete resumed sessions when timer has already expired
  - stronger full-box selected-answer highlight styling
- Challenge Quiz post-completion statistics:
  - score summary + performance level
  - concept accuracy breakdown
  - weak concept extraction (< 60% accuracy)
  - weak concept handoff for Adaptive Practice targeting
- Study Pack detail performance visibility:
  - show Quick Review + Challenge Quiz stats together in a unified Performance Overview
  - show recent review history across quiz modes for the same Study Pack
- separate Premium review quotas for Challenge Quiz and Adaptive Practice (50/month each)
- Plan & Billing usage visibility with separate buckets (Study Packs, Challenge Quiz, Adaptive Practice)
- real email verification (tokenized links + resend flow) using provider-agnostic email service abstraction
- file-based parameterized transactional email templates for verification flows
- unverified users can access account flows but are blocked from Study Pack generation until verified
- verification-required generation actions should use a consistent structured 403 contract (`EMAIL_VERIFICATION_REQUIRED` + `RESEND_VERIFICATION`)
- campaign/blast email infrastructure remains out of current scope
- guided review entry hierarchy:
  - Study Pack detail: Quick Review primary, Challenge secondary, Adaptive only when weak concepts exist
  - Quick Review results: recommend Adaptive when struggling, Challenge when strong
- quiz cost controls:
  - Quick Review remains stored-quiz only (no LLM)
  - Challenge/Adaptive generate only on new session creation and reuse in-progress sessions
  - Challenge/Adaptive generation uses summary + key concepts (+ weak concepts for Adaptive), never extracted text
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
