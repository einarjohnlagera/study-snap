# mobile-ui.md - NoteLib Feature Context

## Goal

Mobile UI should prioritize clarity over minimal chrome so first-time users can understand major actions without guessing icons.

## Mobile Button Rule

Important mobile actions must display:

- icon + text

This applies across:

- Dashboard
- Note Detail
- Library
- Public Library
- Public Note Detail
- Public Profile
- Profile
- Settings
- Admin when actions are present
- auth/public marketing pages when icon-bearing actions exist

## Important Actions

Examples of actions that should keep text on mobile:

- `Start Quick Review`
- `Challenge Quiz`
- `Adaptive Practice`
- `Summary`
- `Quiz`
- `Make a Copy`
- `Share`
- `Create Note`
- `Library`
- `Public Library`
- `Profile`
- `Settings`
- `Upgrade`
- `Save`
- `Log in`
- `Sign up`

## Authenticated Mobile Bottom Tab Bar

Below the `md` breakpoint, the authenticated app shell provides a persistent four-tab bar for:

- Dashboard
- Library
- profile-aware collection navigation (`getCollectionLabels(profileType).navLabel`, such as Review Sets, Study Plans, or Lesson Plans)
- Public Library

Each tab keeps its icon and text label. Desktop continues to use the existing sidebar, while the mobile hamburger drawer remains available for Progress and account-area navigation.

The tab bar is hidden while exam focus is active and whenever an active assessment or answer review owns a fixed bottom footer, so mobile quiz controls never stack with navigation. `AddToHomeScreenNudge` renders above the tab bar with the same safe-area-aware offset, keeping the install prompt visible without overlap.

## Allowed Icon-Only Exceptions

These may stay icon-only when space is genuinely constrained:

- `Edit`
- `Delete`
- `Back`
- hamburger menu
- theme toggle
- notifications
- avatar/profile circle

## Layout Expectations

- use full-width buttons for primary mobile actions when appropriate
- stack related actions vertically on narrow screens
- place icon before text
- keep outline buttons readable in dark mode
- do not hide major-action labels behind icon-only treatment
