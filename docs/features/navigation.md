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

## Back Navigation Pattern

All sub-pages use a `BackLink` component (`components/ui/back-link.tsx`) that renders `← {Destination}` using `ArrowLeft` icon + destination label text.

Rules:
- Back link appears on sub-pages only. Main pages (Dashboard, Library, Public Library, My Profile, Settings) have no back link.
- Back link uses explicit routing (not `router.back()`), so the destination is always predictable.
- Back link label is the destination page name only — no "Back to" prefix.
- Back link is positioned above the page header card, left-aligned.
- Style: small muted text (`text-foreground/70`), brightens on hover, no button border.

| Sub-page | Destination | Label |
|----------|-------------|-------|
| Note Details | Library | Library |
| Public Note Details | Public Library | Public Library |
| Quiz pages (Quick Review, Challenge Quiz, Adaptive Practice) | Note Details | Note |
| Create Note | Library | Library |
| Edit Note | Note Details | Note |
| Profile (Edit Profile) | My Profile (public) | Profile |
| Public Profile (non-owner) | Public Library | Public Library |
| Learn articles | Learn index | Learn |
| Shared Study Pack | Home | Home |

## Public Profile Back Behavior

- My Profile (owner viewing their own public profile) is a main navigation page — no back link.
- Non-owner viewing another user's public profile sees `← Public Library` linking to `/library/public`.
- Do not use `router.back()` for public profile navigation — use explicit `/library/public` route.

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
