# settings.md - NoteLib Feature Context

## Goal

`Settings` is the private surface for app-level preferences, billing, and account controls.

Theme is part of product polish and user comfort, so the theme system should live in `Settings > Preferences` rather than being hidden only behind a utility toggle.

## Theme System

Theme options:

- `Light`
- `Dark`
- `System`

Default:

- `System`

## Theme behavior

- `System` follows `prefers-color-scheme`
- when the OS theme changes while the app is open, NoteLib should update automatically
- theme choice should persist in local storage
- when authenticated theme persistence is available, the selected preference should also sync through the existing theme-preference API

## Initial load rule

Theme must be applied before the main UI renders to avoid a flash of the wrong theme.

Implementation rule:

- apply theme classes on the root html element immediately during boot
- use explicit theme classes:
  - `theme-light`
  - `theme-dark`

## Settings UI

Theme should be shown inside the `Preferences` card as a clear selector, not as a single hidden state toggle.

Recommended presentation:

- inline segmented selector with `Light`, `Dark`, `System`

Supporting copy should explain:

- `System` follows the device setting automatically

## Smooth transitions

Theme changes should feel polished, not abrupt.

Rules:

- animate color-only properties
- keep transitions subtle and fast
- target roughly `150ms` to `300ms`
- do not animate layout or delay interaction

Recommended properties:

- background color
- text color
- border color

## Relationship to utility theme toggle

Public and authenticated headers may still keep a small theme utility control for quick access.

However:

- the top-bar utility control and `Settings` must expose the same three theme options
- the top-bar control should show the current mode through its icon and tooltip text such as `Theme: System`
- the top-bar should use a responsive inline control instead of a popup menu:
  - desktop uses an always-visible compact inline theme group
  - mobile uses a compact trigger that expands an inline theme panel from the header control
- the desktop top-bar group should be icon-only and rely on tooltips for `Light`, `Dark`, and `System`
- the top-bar `System` option should use a monitor-style icon on desktop and a phone-style icon on mobile so the behavior reads naturally on each device
- the mobile expanded control should animate quickly and stay anchored to the trigger without breaking the header layout
- the desktop inline group should stay compact and utility-like, with tighter icon and spacing treatment than the Settings selector
- `Settings` remains the canonical place for explicit theme selection and explanation copy
- `Settings` should keep the always-visible inline segmented selector so users can switch themes without opening another surface
