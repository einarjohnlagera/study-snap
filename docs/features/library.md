# library.md - NoteLib Feature Context

## Goal

Library surfaces make NoteLib a reusable note-first workspace with a clear split between private work and public discovery.

## Library

Route:

- `/library`

Responsibility:

- private workspace for the current user's notes

Library contains:

- `PRIVATE` notes
- `PUBLIC` notes owned by the current user
- Draft and Study Pack Ready notes

Shared note-card layout:

- subtle `courseProgram` line when available
- title
- private-library visibility icon near the title when relevant
- subject badge
- Study Pack status badge
- `Note Preview`
- `Summary Preview`
- tags
- subtle discovery metrics row (`views`, `copies`) when a library-style surface exposes them

Interaction:

- whole card opens Note Detail
- note cards stay preview/navigation only
- note actions belong in Note Detail
- Teacher/Admin Library adds a teacher-only `Select` mode instead of changing default card behavior:
  - each note shows a checkbox while selection mode is active
  - only notes with a stored `generatedQuiz` can be selected for exam export
  - non-quiz-ready notes stay visible but show a disabled checkbox plus `Generate a quiz first` guidance
  - selected notes open `Exam Builder`, where teachers can:
    - start from `Start Blank` with one `Untitled section`, or choose a structured template
    - organize notes into editable sections such as `Section A`, `Section B`, and `Section C`
    - reorder sections with a drag handle
    - drag notes within a section or move notes across sections
    - rebalance pooled quiz questions with either:
      - `Even Balance` to spread questions equally across all sections
      - `Smart Balance` to balance question counts and spread topic diversity across sections, using each section's learning intent as a guide
    - review a per-section question-count breakdown in the footer before export
    - keep `Move up` / `Move down` note controls as the accessibility fallback
    - remove notes or delete sections before export
    - export one combined DOCX exam that preserves section order and exact question grouping

Shared list controls:

- order is always `Search`, `Filter`, `Sort`, then notes list
- on mobile, `Filter` and `Sort` open shared bottom-sheet/modal controls instead of staying always visible

Private Library filters:

- `Course / Program`
- `Subject`
- `Tags`
- `Study Pack Ready`
- `Quiz Ready` only for Teacher profile browsing and exam-export preparation
- `Draft`
- `Public`
- `Private`

Readiness visibility:

- `Study Pack Ready` is the learner-facing readiness signal and remains visible for Student, Board Taker, and Teacher profiles.
- `Quiz Ready` is a Teacher/exam-export workflow signal. Hide the `Quiz Ready` badge and filter for Student and Board Taker Library browsing.
- If the active profile changes while `Quiz Ready` is selected, reset the hidden filter instead of leaving users with an invisible active filter.
- Public Library does not expose `Quiz Ready` because public discovery should focus on notes, summaries, Quick Check, and copy/share flows.
- Exam Builder still uses generated-quiz data internally for note selection, question counts, disabled states, and DOCX export.

Private Library sorting:

- `Recently Updated`
- `Recently Reviewed`
- `Newest`
- `Title (A-Z)`
- `Title (Z-A)`
- `Oldest`

## Public Library

Route:

- `/public/library` as the canonical Public Library route in both authenticated and anonymous flows
- canonical public discovery route is `/public/library`

Responsibility:

- discovery of public notes from you, other creators, and official NoteLib content

Public Library cards use the same shared preview layout and whole-card interaction as Library.

Public Library controls:

- keep the same `Search`, `Filter`, `Sort`, notes-list structure as Library
- use the same mobile filter/sort sheet behavior as Library

Public Library filters:

- `Course / Program`
- `Learner Level`
- `Subject`
- `Tags`
- `By You`
- `Official`
- `Community`

Growth behavior:

- Public Library should encourage copying and studying, not only browsing.
- Discovery sorting should help users find useful community notes faster:
  - `Newest`
  - `Most Copied`
  - `Most Viewed`
  - `Title A-Z`

Metadata behavior shared with note authoring:

- `Course / Program` is the high-level shelf used to group notes by track/domain, while `Subject` remains the more specific academic topic.
- Library course/program filters depend on the same persisted `notes.courseProgram` values used by note cards and Note Detail metadata.
- When a user saves a custom course/program on a note or in their profile, that value becomes available in later course/program suggestions for authenticated authoring flows.
- Equivalent course/program variants should collapse through shared normalization so filters do not split labels only because of case or dash formatting.
- Library subject filters depend on the same persisted `notes.subject` values used by the Note Editor autocomplete.
- When a user saves a custom subject on a note, that subject becomes available in later `Subject` suggestions and filters.
- Equivalent subject variants should collapse through shared normalization so filters do not split labels only because of case or dash formatting.
- AI-generated subjects should stay reusable academic labels; overly broad AI suggestions such as `Engineering`, `Medicine`, `Business`, and `Law` are ignored safely instead of being saved as Library filters.
- Notes now persist optional per-note `courseProgram` metadata so future library filters can group notes more accurately without relying only on the user's profile default.

## Public Profile

Public Profile is not a private library surface.

- route: `/public/profile/{userId}`
- purpose: public showcase only
- note cards reuse the same shared layout
- note cards stay action-free and open the canonical public note route
