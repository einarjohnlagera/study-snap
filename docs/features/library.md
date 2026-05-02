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
    - organize notes into editable sections such as `Section A`, `Section B`, and `Section C`
    - reorder sections with a drag handle
    - drag notes within a section or move notes across sections
    - rebalance pooled quiz questions with either:
      - `Even Balance` for deterministic equal counts only
      - `Smart Balance` for deterministic section size, topic, and note-mix balancing with soft template guidance when available
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
- `Draft`
- `Public`
- `Private`

Private Library sorting:

- `Recently Updated`
- `Recently Reviewed`
- `Newest`
- `Title (A-Z)`
- `Title (Z-A)`
- `Oldest`

## Public Library

Route:

- `/library/public` in the authenticated app shell
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
- AI-generated subjects should stay broad, reusable academic domains because backend normalizes subject metadata to domain-level labels before save.
- Notes now persist optional per-note `courseProgram` metadata so future library filters can group notes more accurately without relying only on the user's profile default.

## Public Profile

Public Profile is not a private library surface.

- route: `/public/profile/{userId}`
- purpose: public showcase only
- note cards reuse the same shared layout
- note cards stay action-free and open the canonical public note route
