# Session Plan — Production Performance Audit (Private Library, Public Library, Note Collection Detail, Dashboard)

> **Purpose.** The user is experiencing production slowness specifically on 4 pages: Private Library,
> Public Library, Note Collection page, Dashboard. This session plans what to fix and in what order — it
> is not a scoping pass, not implementation. Same discipline as other Fable sessions in this directory:
> hard facts gathered first via direct codebase investigation (not assumed), hard constraints stated up
> front, output classified through `docs/skills/roadmap-feature-audit.md`'s four-tier framework.

## Why this needed real investigation before writing the prompt

Four parallel investigations were run against the actual codebase (not guessed) — one per reported-slow
page. Every finding below has a file:line reference and was independently verified, not inferred from the
user's report. This matters because there's real precedent for silent regression risk: `v0.38.0 - Read-Path
Optimization Pass` already fixed this *exact class* of bug (unpaged/unbounded queries, full-entity loads
instead of lean projections) on 4 specific paths — but 3 of the 4 slow pages reported now either weren't
covered by that pass at all, or have grown new read cost since it shipped.

## What v0.38.0 already fixed (treat as done, don't re-propose)

Per `docs/releases/v0.38.0.md`: (1) session-history read path (feeds collections list/detail, recent
sessions) — grouped JPQL projection, no JSONB loaded, bounded before in-memory filtering; (2) Note
Collection detail — lean `NoteEntity`/`StudyPackEntity`/`GeneratedQuizEntity` projections; (3) Private
Library list — lean projection dropping unused `quiz`/`source_text` columns, with a note claiming "cursor
ordering, pagination... unchanged"; (4) Goal per-child readiness — batched, was N+1 before. All shipped as
byte-identical responses, verified with real-Hibernate tests, not mocks.

**An unresolved discrepancy to flag directly to Fable, not silently resolve:** v0.38.0's own release notes
say Private Library's `listMine` has "pagination... unchanged" (implying it was already paginated before
and after). Direct code inspection now (`NoteService.java:333-336`) shows `listMine(ownerUserId)` calling
`noteRepository.findByOwnerUserIdOrderByUpdatedAtDesc(ownerUserId)` with **no `Pageable` parameter at
all** — genuinely unbounded, returns every note a user owns. Only one `listMine` method exists in the
service (verified, no second/renamed variant found). Whether this is release-note imprecision (referring
to client-side slicing, not a backend cursor) or an actual regression since v0.38.0 shipped is unresolved
and worth Fable naming as a fact to verify against `git blame`/the original v0.38.0 PR, rather than
assuming either way.

## What direct investigation found, per page (facts only — this is the ground truth for the prompt)

**1. Private Library (`/library`).** `listMine()` is unbounded end-to-end (see above) — `NoteController`
has no `Pageable`/size param on the endpoint at all. Frontend pagination (`LIBRARY_PAGE_SIZE`,
`visibleCount`) is 100% client-side slicing of an already-fully-loaded array; it does not limit what's
fetched. A background poller (`STUDY_PACK_GENERATION_POLL_INTERVAL_MS`, `setInterval`) re-runs the same
unbounded `listNotes()` repeatedly whenever any note is `GENERATING` — not just once on page load. Once
notes are fetched, 8-9 separate `IN`-batched (not per-row N+1, but still) enrichment queries run against
the full unbounded ID list every time: copy/like/share/view counts, study packs, generated quizzes,
session-completion, owner lookups. No caching/memoization anywhere on this path (no React Query/SWR, no
ETag) — every mount and every poll tick re-runs the full fetch + enrichment fan-out from scratch.

**2. Public Library index (`/public/library`) — the most severe single finding.** `NoteController.listPublic`
supports a `size` param (clamped 1-50) — but the frontend (`public-library-page-client.tsx`, a Client
Component) **never sends it**. With `size` null, `NoteService.listPublic` runs
`findByVisibilityOrderByUpdatedAtDesc(...)` — unbounded — then fully enriches (same class of batched-but-
unbounded queries as above) **every public note in the database**, then `limitPublicLibraryItems(items,
size)` returns `items` unchanged when `size` is null — no limit applied, ever. A separate
`loadCopiedNotes()` call on the same page also calls the unbounded `listNotes()` (all of the current
user's own notes) just to build a copied-note lookup map. No ISR/caching — pure client-side browser fetch.
Given the 2026-07-17 production depth inventory (130+ distinct subject buckets, several with 15-27 notes
each — see `docs/claude-prompt/public-library-seo-expansion-out/02-subject-depth-inventory.sql`), this is
very likely several hundred notes fully loaded, enriched, and shipped to the client on every single visit,
and the cost grows unboundedly as the Public Library grows — this is the opposite direction from every
other optimization pass in the app's history.

**3. Note Collection detail (`/collections/{id}`) — the list page is lean, the detail page is not.** The
list page (`/collections`) parallelizes its 2 calls correctly and its backend batches per-collection
rollups — no issue found. The **detail** page is a `"use client"` component (2600+ lines) with **zero
server-side data fetching** — a genuine request waterfall after JS hydration: Wave 1 (`getCollection` +
`listNotes()` in `Promise.allSettled`) → Wave 2 (`getCollectionGoal`, `await`-ed *serially* after Wave 1
resolves instead of being included in the parallel wave, for any top-level Goal collection) → Wave 3
(fires only once state is "ready": 3 independent `useEffect`s each firing their own request —
`getMe()`, `getNoteConceptCounts()`, `getPlanReadiness()`). A likely-duplicate `listNotes()` call was
found at a second call site (`loadNoteVisibility`) — not confirmed whether it's mount-triggered or
user-triggered, flagged for Fable/implementation to verify, not assumed. Net: a Goal-type collection visit
is at minimum 3 sequential round-trip waves before the page is interactive, with no SSR/streaming at all
(unlike Exam Hub and Public Library subject pages, which are Server Components). Not verified in this
pass: whether `getCollectionGoal`'s v0.38.0 batching still holds now that Companion (v0.41.0), weekly
countdown (v0.40.0), and Mentor Tips (v0.43.1) data ride along in the same response — flag as "verify still
batched," not "assume regressed."

**4. Dashboard.** Stage 1 is well-parallelized (5 calls in one `Promise.allSettled`: `listNotes`, `getMe`,
`getContinueStudyingRecommendation`, `getTodayFocus`, `getDashboardOverview`). But Stage 2 is a **second,
sequential wave gated on Stage 1 finishing**, branching by profile type: Teacher does up to 8 individual
`getNote(note.id)` calls (no batch endpoint used, just to read each note's `generatedQuiz` field);
Student/Board-Exam/Professional does up to 4 individual `getQuickReviewPerformanceSummary(note.id)` calls.
Every Dashboard load is a 2-wave waterfall, not fully parallel. Stage 1 also includes the same unbounded
`listNotes()`/`listMine()` from finding #1 — a fix there helps both pages. Separately,
`getDashboardOverview` (`DashboardService.java:244-297`) has **two of its own unpaged full-history scans**
never touched by v0.38.0: `Pageable.unpaged()` over every completed quiz session ever (both Quick/Long-Exam
and Challenge modes), and — Board-Exam users only — `Pageable.unpaged()` over every `StudyPackEntity` a
user has ever created (full entities, not a lean projection) to build a concept map for exam pacing. This
is the identical bug class v0.38.0 explicitly fixed for 4 other paths, just never applied here. Not
deep-audited (flagged only, out of investigation budget): `getContinueStudyingRecommendation` and
`getTodayFocus` — both in the same critical-path Stage-1 wave, worth a look in the actual session.

## The cross-cutting pattern (state this to Fable directly, don't make it re-derive it)

Every one of the 4 pages shares at least one of two root patterns: **(a) an unbounded backend query**
(`listMine`/`listPublic`'s underlying repository calls, `getDashboardOverview`'s two unpaged scans) that
scales linearly with total row count and has no ceiling, or **(b) a multi-wave frontend request
waterfall** (Collection detail's 3 waves, Dashboard's 2 waves) where round-trips that could be parallel are
sequential. `listMine`'s unbounded pattern specifically is shared by *both* Private Library and Dashboard —
fixing it once plausibly helps both. No page in this audit uses any caching layer (React Query, SWR,
HTTP caching) — every navigation re-fetches everything from zero, every time, including re-fetching on a
poll-timer for Private Library specifically.

## Hard constraints

- This is a **read-path performance pass**, not a feature or business-logic change — mirror v0.38.0's own
  discipline: prefer byte-identical API responses via lean projections/added bounds over changing what
  data is returned, unless a genuine pagination UX change is the right fix (see below).
- If backend pagination is recommended for a currently-unbounded list (Private Library, Public Library),
  address the **frontend UX implication directly** — these pages currently render as "load everything,
  slice/filter client-side." Moving to true backend pagination changes filter/search UX (a filter change
  would need to re-query the backend, not just re-slice a local array) and is a real product decision, not
  a free technical swap. Say so explicitly rather than treating it as a pure backend fix.
- Do not propose a new caching infrastructure (Redis, CDN edge caching, etc.) without weighing it against
  simpler fixes first (bounding queries, parallelizing waterfalls, adding `revalidate`/ISR to already-
  server-renderable pages) — this app has no caching layer today, and introducing one is a bigger
  architectural commitment than most of what's found here actually requires.
- Do not touch quiz session model, `ConceptHealth`/readiness computation semantics, profile-type branching
  logic, or any locked product behavior — bound/batch/parallelize the *reads*, don't change what they mean.
- Reuse the v0.38.0 precedent's verification bar: real-Hibernate/integration tests over mocks, byte-
  identical responses where the fix is purely a projection/bounding change.
- The Note Collection detail page's lack of server-side rendering (fully client-rendered, no SSR/streaming)
  is itself a legitimate finding to weigh, not just its waterfall — but changing rendering strategy
  (Server Component conversion) is a bigger structural change than a query fix; treat it as its own
  candidate, not bundled silently into a "just parallelize the waterfall" fix.

## What Fable must produce

1. **Root-cause ranking across the 4 pages** — which of the findings above is most responsible for
   *user-perceived* slowness (not just theoretically worst), and why. The unbounded-query findings and the
   waterfall findings are different failure modes with different fixes; don't flatten them into one bucket.
2. **A prioritized, concrete fix list**, each classified Core Feature / Polish / Future Enhancement /
   Low-Priority Idea (`docs/skills/roadmap-feature-audit.md`), with: what it is, which page(s) it fixes,
   rough effort (backend/frontend/both), whether it's a safe byte-identical fix or a real UX/behavior
   change, and dependencies (e.g., does fixing `listMine`'s unbounded query require a frontend pagination
   UX decision before it can ship, or can bounding be added transparently first with UX changes deferred).
3. **A sequencing recommendation** — given `listMine` is shared by 2 of the 4 pages, and Public Library's
   finding is the most severe in isolation, what should ship first for the best slowness-reduction-to-risk
   ratio, matching how v0.38.0 sequenced its own 4 fixes as separate, independently-verified PRs rather
   than one large change.
4. **Explicit rejections** — anything considered and deliberately not recommended (e.g., full caching
   layer, Server Component conversion for Collection detail if not worth it now, etc.), and why.
5. Resolve or explicitly flag-as-unresolved the v0.38.0 pagination discrepancy noted above.

## Prompt

Full paste-ready prompt: `production-performance-audit-prompts/01-production-performance-audit.txt`

## Output

`docs/claude-prompt/production-performance-audit-out/01-production-performance-audit.md` (once run)

## Status

Run 2026-07-17. Output in `production-performance-audit-out/01-production-performance-audit.md`. Resolved
the v0.38.0 `listMine` discrepancy with git-history evidence: two different methods with the same name —
`StudyPackService.listMine` (paginated, fixed by v0.38.0) vs. `NoteService.listMine` (unbounded since the
original notes commit, never in v0.38.0's scope, genuinely greenfield). 10 fix candidates (F1-F10) ranked
and sequenced; F1/F2 are zero-decision byte-identical fixes ready to Codex-prompt immediately, F3 (Public
Library bounding) needs one human UX call (cap + "Load more" now vs. full server-side browse later) before
it can start. Reviewed by the user before scoping — see `RELEASES.md`/`ROADMAP.md`.
