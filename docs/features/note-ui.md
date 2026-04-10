# note-ui.md - NoteLib Note UI Rules

## Private Note Detail Header

Private Note Detail should prioritize study actions over note-management utilities.

Rules:

- keep learning actions such as `Generate Study Pack`, `Start Quick Review`, and `Challenge Quiz` visually primary
- move secondary note-management actions into a shared overflow menu in the note header
- use the same overflow menu on mobile and desktop for consistency

## Note Actions Menu

The private note header overflow menu is the canonical secondary-action pattern for Note Detail.

Menu trigger:

- top-right `⋯` button inside the header card
- compact tap target
- opens on click or tap
- closes on outside click
- closes on `Escape` when practical

Menu actions:

- `Edit`
- `Delete`
- `Make a Copy`
- `Share`

Rules:

- do not render these actions inline in the header when the overflow menu is available
- keep menu items readable on mobile with full text labels
- destructive actions such as `Delete` should stay visually distinct
- inline `Save` / `Cancel` controls are still allowed while metadata edit mode is active because they are part of the current editing task

## Mobile Priority

Mobile Note Detail should avoid utility-button rows that compete with study actions.

Rules:

- long note titles may wrap freely
- the overflow trigger must remain visible without pushing content out of the card
- primary study actions should stay easier to spot than note-management actions
