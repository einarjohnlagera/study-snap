# Fable Output — App-Like UI for High-Traffic Pages

> Raw output from running `docs/claude-prompt/app-shape-prompts/02-app-like-ui.txt` through Fable. Unedited except for this header.

## Note Detail (`/notes/{id}`)

The page already has the right skeleton of an app screen (view tabs, overflow menu, no-refetch tab switching). What makes it read as "website" is that its chrome scrolls away and its secondary blocks stack as an article below the tabs.

**1. Sticky tab bar + condensing header on scroll**
- **What/why:** When the user scrolls past the header card, the `Summary / Key Concepts / Quiz / Full Notes` tab row pins below the top bar (mobile especially), optionally with the note title condensed into it. Native apps never let their primary view-switcher scroll off-screen; websites do. This directly reinforces the existing "switching tabs must not jump the page back to the top" rule — right now that rule is honored but the tabs themselves may be off-screen when you'd want them.
- **Classification:** Polish.
- **Reuse:** the existing underline-style tab component and its icon+text rule (`note-detail.md` § Note Detail Tabs); the Mobile Button Rule already requires icon+text here, so the sticky variant keeps labels, not icon-only compression.

**2. Skeleton-first initial load, reusing the GENERATING placeholders**
- **What/why:** The page already owns skeleton/placeholder content for Summary, Key Concepts, and Quiz during `GENERATING` (`note-detail.md`). Use the *same* skeleton shapes for the initial note fetch instead of a spinner/blank state. A skeleton that matches the final layout says "the app knows its own shape"; a spinner says "waiting for a server page." Zero new visual language — the components exist.
- **Classification:** Polish.
- **Reuse:** existing GENERATING skeleton components; `motion-fade-enter` for content replacing skeletons (sanctioned "non-critical section entry" use in `ui.md`).

**3. Horizontal swipe between tabs on mobile**
- **What/why:** Segmented views you can swipe between are one of the strongest "this is an app" gestures. Swiping left/right moves between the four tabs with a small transform, honoring the existing constraints: no refetch, no remount, no scroll-to-top, `?tab=` state preserved. Motion must stay within `ui.md`'s 150–250ms ease-out budget — a short slide, not a springy carousel.
- **Classification:** Future Enhancement — gesture handling plus scroll-conflict edge cases (horizontal-scroll rails inside tab content) make this bigger than a polish pass.
- **Reuse:** shared motion tokens in `globals.css`; the existing tab-state URL model.

**4. Collapse-by-default for below-the-fold reference tiers (the Coach lesson, applied)**
- **What/why:** The Companion finding generalizes here: Recent Sessions and Performance Overview are reference material stacked article-style under the active tab. Give them the `CompanionDisplayCard` treatment — header always visible with a light inline text-plus-chevron toggle ("View sessions" / "Hide sessions"), body collapsed by default, expanding via `motion-collapse`. The readiness rollup stays visible (it's a compact signal, already the right density); the long lists behind it become opt-in. The page then reads as *current view + one glanceable signal + optional history*, not a scroll of stacked cards.
- **Classification:** Polish.
- **Reuse:** the exact toggle pattern documented in `companion.md` § "View Full Guide" collapse (including its rationale for text-label-not-bare-chevron: session history isn't self-evident content); `motion-collapse` / `motion-collapse-inner`.

## Review Sets List (`/collections`)

**1. "Jump back in" row above the plan cards**
- **What/why:** The list is currently plans in `updatedAt desc` order plus the Recommended section — a directory. Apps lead with resumption. Render the top plan (already first by `updatedAt desc`) as a visually distinct compact "Continue" row — title, the same execution hint data the cards already carry, one CTA into the plan — with the remaining plans as the normal grid below. No new data, no new endpoint; it's a hierarchy change over the same response, which is precisely the lever the Coach redesign proved (lead with the dynamic layer, demote the catalog).
- **Classification:** Polish (frontend-only re-presentation of already-fetched data). If it grows toward resolving a per-plan next action on the *list* page (calling readiness per plan), that's a Future Enhancement — don't let it creep there.
- **Reuse:** profile-aware copy through `getCollectionLabels` (never hardcode "Review Set"); `motion-pressable` on the row.

**2. Skeleton cards + card-press feedback**
- **What/why:** Initial load shows skeleton plan-cards matching the real card geometry; every plan card gets `motion-pressable` pressed-state feedback and `motion-surface` hover/transition. Instant tactile response on tap is the cheapest, highest-coverage "native" signal there is — websites have hover states, apps have press states.
- **Classification:** Polish.
- **Reuse:** `motion-pressable` / `motion-surface` tokens (`ui.md` — explicitly what they exist for); skeleton precedent from the Recommended section, which already has a loading skeleton state (`collections.md` § Browse published plans).

**3. Create-modal → bottom sheet on mobile**
- **What/why:** The create modal (title + description) should present as a dismissible bottom sheet on narrow screens. The repo has already ruled that mobile confirmation surfaces use bottom sheets, not centered modals (Public Library copy confirmation, `public-library.md` § Note Cards), and Filter/Sort already use the shared sheet on mobile. Extending that to the create flow makes form entry feel like an OS-level sheet rather than a web dialog.
- **Classification:** Polish.
- **Reuse:** the shared bottom-sheet/modal component pattern (`library-sheet-modal.tsx` lineage + the AppModal-on-desktop / sheet-on-mobile split already specced for the copy confirmation).

## Review Set / Study Plan Detail (`/collections/[id]`)

This page is the proven precedent — v0.43.0 already did hierarchy + progressive disclosure here. The remaining gaps are persistence of the primary action and the feel of the disclosure interactions themselves.

**1. Sticky condensed "Continue" bar when Today's Focus scrolls away**
- **What/why:** `TodaysFocusCard` resolves the one primary action, but it scrolls off as the learner moves into sections and notes. When it leaves the viewport, pin a slim bar (mobile: bottom-docked; desktop: below the top bar) carrying just the resolved `Continue` CTA with icon + text. Apps keep the primary verb reachable at all times; that's the core difference between a screen and a page. This must stay a *condensation* of the existing card — same resolved action, no new resolution logic — and must respect the existing rule that the terminal exam CTA is visually secondary (it does not join the sticky bar).
- **Classification:** Polish, but flag it as the largest polish item on this list — sticky/docked bars need per-breakpoint care and must not overlap the section cards' tap targets. If it turns into a shell-level docked action framework, split it out.
- **Reuse:** the Primary Action card's existing resolution (`getNextPlanAction` / continue-item logic — do not re-derive); Mobile Button Rule (icon + text, never icon-only for a major action).

**2. Animate the disclosures that already exist**
- **What/why:** Section cards already collapse below `lg` with title peeks, and `CompanionDisplayCard` collapses by default — but disclosure that snaps open/closed reads as DOM toggling (website); disclosure that animates 150–200ms reads as a surface moving (app). Route every collapse on this page — section cards, View Full Guide — through `motion-collapse` + `motion-collapse-inner` so the whole page shares one disclosure feel with the Question Navigator.
- **Classification:** Polish.
- **Reuse:** `motion-collapse` tokens; do not invent per-component durations (`ui.md` explicitly forbids one-off timings).

**3. Hero `⋯` authoring menu as a bottom sheet on mobile**
- **What/why:** The compact authoring cluster's `⋯` menu (`Edit` / `Set primary` / `Manage Companion` / `Delete`) is a desktop-style dropdown. On mobile, open it as a bottom sheet with full-text rows and the destructive `Delete` visually distinct. Action sheets are the native idiom for exactly this "secondary actions on the current object" case, and it reinforces the design rule already written on this page: authoring chrome stays compact, never a page section — a sheet is maximally compact when closed.
- **Classification:** Polish.
- **Reuse:** shared bottom-sheet pattern; the Note Actions Menu rules (`note-detail.md`: full text labels on mobile, distinct destructive styling) apply verbatim.

**4. Tier entry transitions on load**
- **What/why:** As Progress/Guidance tiers resolve (readiness is lazy-loaded), fade them in with `motion-fade-enter` rather than popping into the layout, and reserve their space with compact skeletons so the page doesn't reflow under the reader's thumb. Layout shift is one of the most reliable "this is a website" tells; apps compose their screens once.
- **Classification:** Polish.
- **Reuse:** `motion-fade-enter` (sanctioned for non-critical section entry); `ReadinessSummary`'s compact geometry for the skeleton shape.

## Private Library (`/library`)

**1. Sticky search/filter toolbar**
- **What/why:** The `Search → Filter → Sort` control row pins to the top on scroll (condensed: search field + Filter/Sort buttons with active-filter dot). A list screen whose controls stay put while content scrolls under them is the canonical native list pattern; controls that scroll away with the content are the canonical web pattern. The stats strip and rails stay in-flow — only the control row pins.
- **Classification:** Polish.
- **Reuse:** `library-toolbar.tsx` as-is (condense, don't fork); the existing active-dot / summary-badge affordances for Course/Program so active state survives condensation.

**2. Stale-while-revalidate on filter changes + skeleton-only-first-load**
- **What/why:** Adopt the Public Library's already-shipped SWR rule (`library.md` § Public Library search responsiveness): full skeleton only on the very first load; on every filter/sort/search change the current cards stay mounted with a small `Filtering…` indicator, never collapsing back to skeletons or a spinner. The list never "blanks" — the single behavior users most associate with web pages. The private library is client-filtered over a fully loaded list, so most of this is instant already; the rule mainly guards the initial-fetch and refetch paths.
- **Classification:** Polish.
- **Reuse:** the documented Public Library SWR pattern — same `hasLoadedOnce` gating, same inline-error-over-stale-results behavior. Do not design a second variant.

**3. Press feedback on the shared note card**
- **What/why:** Add `motion-pressable` + `motion-surface` to `shared-note-card.tsx`. Because the card is shared, this one change gives tactile press response across Private Library, Public Library, and Dashboard simultaneously — the highest-leverage single edit in this whole plan. Whole-card tap already owns navigation (cards are "preview/navigation only"), so pressed-state feedback has no interaction conflicts except the Public Library's inline CTA/heart, which already stop propagation.
- **Classification:** Polish.
- **Reuse:** shared motion tokens; the shared-card contract in `library.md` (don't add actions while adding feedback).

**4. Compact card density on mobile — tighten, don't strip**
- **What/why:** On narrow screens, tighten the shared card's vertical rhythm: preview and summary at tighter line-clamps, badges and metrics on denser rows, smaller type scale for secondary metadata. Marketing sites are airy; apps are dense. The Public Library doc already states the governing constraint — density comes from tighter limits, *not* from stripping metadata — so every field stays, at reduced footprint. More cards per viewport also makes the list feel scrollable-through rather than read-through.
- **Classification:** Polish.
- **Reuse:** the existing shared card layout order (courseProgram line → title → badges → previews → tags → metrics) — reflow spacing only, keep the documented order.

## Public Library (`/public/library`)

**1. Horizontal card rails for the discovery sections**
- **What/why:** In discovery mode, Featured / Most Popular / Recently Added render as vertically stacked lists — the layout of a blog index. Present each as a horizontally swipeable card rail (section header + `View More` on the right, cards scrolling horizontally with the right-edge fade) — the App Store / streaming-app idiom, and the strongest single "app" signal available on this page. It also satisfies two written rules at once: featured content stays "visually special through stronger section framing," and density improves "from tighter section limits and focused section views, not by stripping metadata from cards."
- **Classification:** Polish, upper bound — it's layout-only over existing data, but touches card sizing at every breakpoint; if cards need a dedicated rail variant, treat it as a small scoped feature rather than sneaking it in.
- **Reuse:** the horizontal-rail + right-edge-fade affordance already established for the Subject/Tags filter rails; the existing `View More` focused-section route as the "see all" destination; `shared-note-card` (a width-constrained instance, not a new card).

**2. Sticky search toolbar (mirror of Private Library)**
- **What/why:** Same pinning behavior as Private Library rec 1 — the layout spec already says the search toolbar "remains visible at the top" in focused section views; make that literal via stickiness in all modes. One shared behavior, two surfaces, consistent muscle memory.
- **Classification:** Polish.
- **Reuse:** whatever condensed-toolbar treatment Private Library ships — build once.

**3. Drill-in transition for `View More` focused sections**
- **What/why:** Opening a section's focused view and `Back to Discovery` currently behave as page swaps. Give the focused section `motion-fade-enter` on entry and preserve/restore the discovery scroll position on back. Master→detail drill-in with restored scroll is how apps navigate; losing your place is how websites navigate. (The sessionStorage return-URL pattern already solves this for note-detail back-navigation — this extends the same care to section navigation.)
- **Classification:** Polish.
- **Reuse:** `motion-fade-enter`; the `notelib_public_library_return_url` precedent for scroll/state restoration logic.

**4. Optimistic inline feedback on the card heart and copy CTA**
- **What/why:** The heart toggle and `Add to Library` CTA should respond instantly on press (`motion-pressable` pressed state, heart fills optimistically, CTA swaps to a brief pending state) rather than waiting on the round-trip, rolling back on failure with the existing inline-error affordances. Instant acknowledgment of touch is a defining native trait. The copy flow's confirmation already has the right shape (desktop AppModal / mobile bottom sheet) — this closes the gap between tap and confirmation.
- **Classification:** Polish. (Guest behavior unchanged: taps still open the auth prompt modal per the existing rule — no anonymous state, ever.)
- **Reuse:** `motion-pressable`; the existing stop-propagation and guest-auth-prompt rules on both controls.

## Cross-cutting

- **The generalized Coach lesson, stated once:** relabeling, recoloring, and copy tweaks change vocabulary, not medium. What made the Review Set detail stop reading as documentation was structural — *lead with the dynamic/actionable layer, keep one glanceable signal, demote long-form/reference content behind an invited disclosure, and make chrome persistent while content scrolls.* Every recommendation above is an application of one of those four moves. When evaluating any future "make X feel more app-like" request against this list, ask which of the four it applies; if it applies none (e.g., "friendlier headings"), it's the exhausted lever.
- **Mobile bottom tab bar (flagged as bigger than Polish):** a persistent bottom tab bar on mobile (Dashboard / Library / {Review Sets} / Public Library, icon + text per the Mobile Button Rule, profile-aware label via `getCollectionLabels`) is the single strongest app signal available and would benefit all five pages at once — but it's an app-shell navigation change with keyboard-overlap, safe-area, and per-route-visibility concerns. **Core Feature**, own roadmap item, not something to fold into a polish pass. If it lands, the sticky-Continue bar on plan detail must coordinate with it (stacked docked bars are a native anti-pattern too).
- **PWA install/offline layer — explicitly flagged, and recommended against for now:** nothing in the "reads like a website" complaint is caused by the absence of a manifest or service worker; it's caused by scroll-away chrome, spinners, layout shift, and article-shaped hierarchy — all addressed above at far lower cost and risk. A minimal manifest (installability, home-screen icon, standalone display) is a cheap **Future Enhancement** worth a later look *after* the interaction work ships, because standalone display amplifies good in-app feel and amplifies bad web feel equally. Offline caching is a genuinely separate initiative (stale-note and quiz-session-consistency questions) and should not ride along.
- **Sequencing note:** recs Private-Library-3 (shared-card press feedback), Note-Detail-2 (skeleton reuse), and the two sticky toolbars are the highest leverage-to-cost items — each is one component or one token application with multi-surface payoff. The rails, swipeable tabs, and sticky Continue bar are where the design risk lives; prototype those before committing them to a release's Planned Scope.
