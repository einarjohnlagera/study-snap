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

- segmented selector with `Light`, `Dark`, `System`

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

- the full theme selection experience belongs in `Settings`
- `Settings` is the canonical place where users can choose `Light`, `Dark`, or `System`
