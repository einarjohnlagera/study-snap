# note-detail.md - NoteLib Feature Context

## Goal

Note Detail is the unified owner view for one note and its generated Study Pack state.

Route:

- `/notes/{id}`

## Private Note Detail

Private Note Detail owns:

- note metadata
- note content
- generation state
- share/visibility controls
- quiz entry points
- performance overview

Draft note actions:

- `Generate Study Pack`
- secondary note actions live in the header `⋯` menu:
  - `Edit` -> full editor
  - `Delete`
  - `Make a Copy`
  - `Share`

Generating note behavior:

- Note Detail is the status surface after a user starts Study Pack generation.
- `GENERATING` shows a clear in-page status message, friendly rotating loading copy, and placeholder/skeleton content for Summary, Key Concepts, and Quiz.
- actions that depend on finished Study Pack output stay disabled or hidden until the status becomes ready.
- Note Detail polls lightly while generation is active and stops polling when the note becomes `STUDY_PACK_READY` or `FAILED`.

Failed generation behavior:

- `FAILED` shows a friendly recovery state instead of empty Study Pack content.
- copy should make clear that the note is saved and generation did not complete.
- `Retry Generate` queues generation again without consuming quota unless a Study Pack is successfully persisted.

Study Pack Ready actions:

- `Start Quick Review`
- `Challenge Quiz`
- `Adaptive Practice` when weak concepts exist
- secondary note actions live in the header `⋯` menu:
  - `Edit` -> inline metadata edit only
  - `Delete`
  - `Make a Copy`
  - `Share`

Recent Sessions:

- Note Detail shows the actual completed quiz mode label for Quick Review, Challenge Quiz, Adaptive Practice, Long Exam, Board Exam, and Interview Practice.
- multi-note Long Exam sessions appear on every participating note in the session source-note set.
- when a Long Exam spans more than one note, show `Multi-note Long Exam · spans N notes` under its score line while preserving existing weak-concept context.

## Per-Note Readiness

Private Note Detail shows a compact readiness signal for owned notes whose Study Pack is ready and has key concepts.

The rollup renders below the tab content, immediately before the Performance Overview section, and shows on every tab (Summary, Key Concepts, Quiz, Full Notes) — it is not gated to a single tab. It previously rendered above the tab content, ahead of whatever the user was reading; moved down for less distraction (v0.36.1). Its own `unavailable`/neutral-state handling stays independent of Performance Overview's `!isTeacherMode` gate — the two conditions are not coupled.

The signal renders through the shared `ReadinessSummary` component in compact mode and uses the same vocabulary as Plan Readiness and My Progress:

- `ready`
- `mastered`
- `due`
- `not started`

The rollup is derived from the note's already-loaded `conceptHealth` response plus the note `keyConcepts`; it is not persisted and does not call AI. It shows:

- overall `% ready`
- `X/Y mastered`
- due concept count
- not-started concept count

Free-gate split:

- Free users can see the readiness signal and per-concept readiness status chips, including which concepts are `due`.
- PLUS and PRO users keep the review-timing detail, including `Due — Nd ago`, timestamp-backed fields, and the `Needs work` struggling chip.
- The backend concept-health response must redact review-timing fields for Free while still returning readiness status. Do not expose `daysSinceReview`, `lastCorrectAt`, or `lastIncorrectAt` to Free for note readiness.
- Free users should see the existing upgrade affordance for review timing; upgrade CTA labels must remain plan-aware through the shared plan config.

Error and empty states:

- If concept-health loading fails, the note content remains visible and the readiness rollup shows a neutral unavailable state.
- A ready Study Pack with no practiced concepts shows `0% ready` with concepts counted as `not started`.
- Notes without a Study Pack or without key concepts do not show the readiness rollup.

## Note Detail Tabs

`Summary`, `Key Concepts`, `Quiz`, and `Full Notes` are view tabs, not action buttons.

Rules:

- keep `Summary` as the default tab
- support the reading flow:
  - `Summary`
  - `Full Notes`
  - `Key Concepts`
  - `Quiz`
- use the order:
  - `Summary`
  - `Key Concepts`
  - `Quiz`
  - `Full Notes`
- place tabs below the header/actions and above the selected content
- active tab uses underline-style navigation
- desktop shows icon + text
- mobile shows icon + text
- switching tabs updates the note view without a full page reload
- private Note Detail preserves query-string state such as `?tab=quiz` and `?tab=full-notes`
- switching tabs must not jump the page back to the top
- keep the user anchored in the same content area when moving between tabs
- switching `?tab=` state must not refetch the note or remount Note Detail into a loading state
- `Full Notes` should render the complete original note content so users can review the source note without leaving Note Detail
- the `Summary` tab should include a subtle `View Full Notes →` CTA above the summary text that switches to `Full Notes` without reloading the page

## Public Note Detail

Public note detail is a separate public/read-only surface. For non-owners, it keeps one quiz-first primary CTA (`Quiz yourself on this note`) plus the secondary `Add to Library` and `Share this note` actions; it does not present editable-draft copying as a competing button. Related discovery is supplementary: same-subject notes link onward to the subject landing page and the author link uses the canonical creator-filtered Public Library URL; unavailable subsections are omitted silently.

- canonical route: `/public/library/{subject}/{slug}`
- public note detail should keep the same reading flow emphasis (`Summary` / `Key Concepts` / `Quick Check` / `Full Notes`) without turning the page into a quiz-first screen
- public note detail should keep `Summary`, `Key Concepts`, and `Full Notes` visible as stacked sections; `View Full Notes →` is a section deep link, not a tab switch
- the `Full Notes` target must use a native `id="full-notes"` section and the page should mount `HashScrollListener` so direct `/public/library/{subject}/{slug}#full-notes` visits auto-scroll after mount
- owner sees `Open Note` and `Share this note`
- non-owner sees `Create your own Study Pack`, `Copy to My Library`, and `Share this note`
- do not expose private editing or study actions there
- keep the note primary; Quick Check and CTA blocks should support the note instead of replacing it

Copy-first generation rule:

- `Create your own Study Pack` on a public note must create a private copy first.
- generation continues on the viewer's own note route after the copy is created.
- do not run private study actions directly against the public source note

## Note Actions Menu

The private note header overflow menu is the canonical secondary-action pattern for Note Detail.

Menu trigger:
- top-right `⋯` button inside the header card
- opens on click or tap
- closes on outside click
- closes on `Escape` when practical

Rules:
- do not render these actions inline in the header when the overflow menu is available
- keep menu items readable on mobile with full text labels
- destructive actions such as `Delete` should stay visually distinct
- study actions (Generate Study Pack, Start Quick Review, Challenge Quiz) must stay visually primary — the overflow menu is for secondary note-management actions only
- inline `Save` / `Cancel` controls are still allowed while metadata edit mode is active

## Share Modal Pattern

The Note Detail share modal is the canonical share UI pattern for the entire app:

- modal title: `Share this note`
- labeled field: `Shareable URL` (read-only, visibly truncated)
- buttons: `Close` and `Copy Link`
- `Copy Link` copies the full URL to the clipboard and shows `Link copied` feedback inline for ~2 seconds

Profile sharing must reuse the same AppModal component and the same layout, with only the title and URL differing (`Share this profile`).

Private note share rule:
- clicking Share on a private note opens a confirm modal: `This note is private`
- confirm offers `Cancel` and `Make Public & Share`
- `Make Public & Share` sets the note to public, then opens the share modal

Do not use toast-only or inline-text-only share flows for any shareable content type.
