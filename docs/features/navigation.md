# navigation.md - NoteLib Feature Context

## Goal

Keep in-app navigation predictable across private workspace, discovery, and public showcase routes.

## Public Navbar Hierarchy

- Shared public pages should reuse one navbar hierarchy across landing, learn, pricing, auth, and anonymous public-library flows.
- Public nav links should stay visually grouped as navigation.
- Theme toggle should stay in the utility cluster, not between `Login` and `Get Started`.
- `Login` is the secondary public action.
- `Get Started` is the primary public CTA.
- On mobile, the opened menu should show:
  - nav links
  - a divider
  - `Login`
  - `Get Started`
- Keep the theme toggle in the mobile header utility cluster instead of duplicating it inside the opened menu.
- Do not duplicate the visible primary CTA between the public header and the opened mobile menu.

## Public Profile Back Behavior

- Public Profile can be opened from Public Library, Public Note, or other entry points.
- Public Profile should use a page-level `Back` button driven by navigation history.
- Keep the `Back` button above the header card so it reads as navigation rather than header content.
- Do not hardcode Public Profile back navigation to `Library` or `Public Library`.

## Button Label Rule

- If a button already uses an action icon, do not append arrow glyphs to the label for in-app navigation.
- Reserve arrow styling for true external or leave-the-app actions only.

## Note Card Navigation Rule

- Note cards are preview and navigation surfaces first.
- Whole-card click opens the relevant detail route.
- Note actions belong in:
  - owned-note context menus on allowed surfaces
  - Note Detail

## Owned Note Card Menus

- `Library` may show an owned-note menu.
- Owner-view `Public Profile` may show an owned-note menu.
- `Public Library` must not show a note-card menu.
- Other users' Public Profiles must not show a note-card menu.
