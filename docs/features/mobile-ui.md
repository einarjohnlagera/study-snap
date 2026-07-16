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

The tab bar always keeps its labels, including on Note Detail and the standard Challenge Quiz result screen — an icon-only "compact" variant was tried on those two screens and reverted after a consumer-psychology review (`docs/claude-prompt/tab-bar-icon-labels-out/01-consumer-psychology.md`) found it paid a recognition/accessibility cost (icon-only labels render as `display:none`, dropping the accessible name) without the chrome-reduction benefit it was meant to buy — the bar's footprint and all four tap targets stay identical either way. Quiz-taking screens still hide the bar entirely through their bottom-viewport claim; that full-hide mechanism, unlike icon-only, actually reclaims viewport and remains the correct pattern for a genuine focus treatment. The Library tab reuses a valid private `?ref=` return URL when one is present, while the Public Library tab reuses the validated `notelib_public_library_return_url` session value, so either tab returns to the learner's filtered view instead of discarding it. Users can turn off `Show mobile navigation bar` in Settings > Preferences; it is enabled by default, including for accounts whose nullable persisted value is unset.

## Allowed Icon-Only Exceptions

These may stay icon-only when space is genuinely constrained:

- `Edit`
- `Delete`
- `Back`
- hamburger menu
- theme toggle
- notifications
- avatar/profile circle

The mobile bottom tab bar is explicitly not on this list — do not reintroduce a label-free variant of it without a fresh product decision; see the reverted attempt above.

## Layout Expectations

- use full-width buttons for primary mobile actions when appropriate
- stack related actions vertically on narrow screens
- place icon before text
- keep outline buttons readable in dark mode
- do not hide major-action labels behind icon-only treatment
