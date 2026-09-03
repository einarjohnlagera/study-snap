# navigation.md - NoteLib Feature Context

## Goal

Keep in-app navigation predictable across private workspace, discovery, and public showcase routes.

## Public Navbar Hierarchy

- Shared public pages should reuse one navbar hierarchy across landing, learn, pricing, auth, and anonymous public-library flows.
- Public nav links should stay visually grouped as navigation.
- Public marketing nav should include `How it Works` as the dedicated product-walkthrough route.
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

Current public navigation order:

- `Home`
- `How it Works`
- `Explore`
- `Learn`
- `Pricing`

**`Explore` replaced `Public Library` here in `v0.84.0` (Discovery System Stage 2)**, once `/explore` gained anonymous rendering. **This changes navigation primacy only, not routes** — `/public/library` remains a live canonical route with its own metadata and sitemap entry, reachable directly and from search. `Exam Hubs` deliberately stays reachable from the footer: Explore has no exam-aware browsing mode to route an exam-seeking visitor to yet.

## Back Navigation Pattern

All sub-pages use a `BackLink` component (`components/ui/back-link.tsx`) that renders `← {Destination}` using `ArrowLeft` icon + destination label text.

Rules:
- Back link appears on sub-pages only. Main pages (Dashboard, Library, profile-aware Collections, Explore, Progress, Public Library, **Exam Hub index (`/exam`)**, Learning connections (`/linked-learners`), My Profile, Settings) have no default back link.
- **`/exam` was added to that list in `v0.83.2`, after it turned out to be the reason the bug survived.** The Exam Hub index carried a `BackLink` to `/public/library`, miscategorising a destination that is top-level in both the marketing `Navbar` and `PublicFooter` as a sub-page of the Public Library. This list omitted `/exam` entirely, so nothing contradicted the stray link. **The fix was removal, not repointing** — `/exam` is reached directly, not from anywhere in particular. Pinned by a test asserting the index renders no `Public Library` link.
- Back link uses explicit routing (not `router.back()`), so the destination is always predictable.
- Back link label is the destination page name only — no "Back to" prefix.
- Back link is positioned above the page header card, left-aligned.
- Style: blue link color (`text-blue-600 dark:text-blue-400`), underlines on hover, no button border — uses `BackLink` component, never a raw `Link` or `<a>` tag.

| Sub-page | Destination | Label |
|----------|-------------|-------|
| Note Details | Library | Library |
| Combined Quiz Builder (`/library/combined-quiz?notes=…`) | Library | Library |
| Combined Quiz Share (`/library/combined-quiz/{combinedQuizId}`) | Library | Library |
| Supporter progress (`/linked-learners/{id}/progress`) | Learning connections | `Learning connections` (`BackLink`, added `v0.90.0` — the success path previously had none, and the one place back navigation existed used a raw `<Link>` with a forbidden *"Back to"* prefix) |
| Public Note Details | Public Library, or Explore if reached via `/explore`'s Notes tab | `Public Library`, or `Explore` when the stored return path starts with `/explore` (`PublicLibraryBackLink`, `frontend/components/notes/public-library-back-link.tsx`) |
| Quiz pages (Quick Review, Challenge Quiz, Adaptive Practice) | Note Details | Note |
| Create Note | Library | Library |
| Edit Note | Note Details | Note |
| Profile (Edit Profile) | My Profile (public) | Profile |
| Public Profile (non-owner) | Public Library | Public Library |
| Learn articles | Learn index | Learn |
| Shared Study Pack | Home | Home |

**Progress is a main page (v0.59.0)** — reachable directly from primary nav (`/progress`), no default back link. It shows one exception: a contextual back link to the originating collection (`← {Plan Name}`, e.g. via a plan detail's `Check readiness` deep-link) only when reached with an explicit `?collectionId=` — this is a "return to where you came from" pattern, not the removed "Progress is a sub-page of Dashboard" pattern. Implemented in `ProgressHeader` (`frontend/app/progress/progress-report-client.tsx`).

## Authenticated Navigation

Authenticated desktop navigation order:

- `Dashboard`
- the existing profile-aware Collections label from `getCollectionLabels` (`Review Sets`, `Study Plans`, `Lesson Plans`, or `Collections`)
- `Library`
- `Explore`
- `Progress`
- `Learning connections`

`Learning connections` points to `/linked-learners`. It is profile-neutral because supporting another learner is independent of how the caller learns; it is not shown only to Teacher or supporter profile types. The relationship-scoped progress page is a sub-page and returns explicitly to Learning connections.

`Explore` points to `/explore`, where a segmented control reuses the existing Official Review Set catalog and Public Library rendering. The page is reachable anonymously and renders the same two-tab structure for visitors and members; it remains a main page with no back link. The Review Sets tab is labeled `Official {profile-aware plural}` via `getCollectionLabels` (e.g. `Official Review Sets`, `Official Study Plans`) — not the bare profile-aware label, since that would collide with the Collections nav item above for `BOARD_EXAM` profiles, whose Collections nav label is also `Review Sets`; the `Official` prefix disambiguates the two destinations (v0.67.1 fix). Anonymous visitors use the existing `STUDENT` vocabulary, so their label is deliberately `Official Study Plans` rather than the null-profile fallback `Official Collections`. The second tab is `Notes`. `/collections/published` and `/public/library` remain independent main pages with unchanged canonical routes and no default back links; they are no longer direct authenticated-nav items.

The optional mobile bottom tab bar remains four items: `Dashboard`, `Library`, the profile-aware Collections label, and `Explore`. Its existing `mobileTabBarEnabled` preference gate is unchanged.

## Public Profile Back Behavior

- My Profile (owner viewing their own public profile) is a main navigation page — no back link.
- Non-owner viewing another user's public profile sees `← Public Library` linking to `/public/library`.
- Do not use `router.back()` for public profile navigation — use explicit `/public/library` route.

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
