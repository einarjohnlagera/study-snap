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

- `Edit` -> full editor
- `Generate Study Pack`
- `Make a Copy`
- `Share`

Study Pack Ready actions:

- `Edit` -> inline metadata edit only
- `Start Quick Review`
- `Challenge Quiz`
- `Adaptive Practice` when weak concepts exist
- `Make a Copy`
- `Share`

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

Public note detail is a separate public/read-only surface.

- canonical route: `/public/library/{subject}/{slug}`
- public note detail should use the same `Summary` / `Key Concepts` / `Quiz` / `Full Notes` reading flow, but it keeps tab state local instead of query-driven routing
- public note detail should reuse the same `View Full Notes →` CTA inside the summary view so visitors can quickly inspect the source note
- owner sees `Open Note` and `Share`
- non-owner sees `Copy to My Library`, `Generate Study Pack`, and `Share`
- do not expose private editing or study actions there

Copy-first generation rule:

- `Generate Study Pack` on a public note must create a private copy first.
- generation continues on the viewer's own note route after the copy is created.
- do not run private study actions directly against the public source note
