# ui.md - NoteLib UI Patterns

## Top Bar Utility Grouping

Top-bar utility controls should stay compact and visually separate from primary navigation or CTA actions.

Current theme-control rule:

- desktop top bars use an icon-only inline theme group for `Light`, `Dark`, and `System`
- each icon-only theme button should keep a tooltip so the mode remains clear without visible labels
- mobile top bars use a compact collapsed trigger that expands the same theme options inline
- the expanded mobile theme control should stay anchored to the trigger and render fully on screen

## Public Header Separation

In the public header, utility controls and action buttons should not blur together.

Current grouping:

- navigation links
- theme utility control
- subtle vertical separator
- `Login` and `Get Started`

The separator should be visually subtle and should create spacing without drawing more attention than the actions themselves.
