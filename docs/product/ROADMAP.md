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
