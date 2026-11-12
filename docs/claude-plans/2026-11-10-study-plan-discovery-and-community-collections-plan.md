# Study Plan Discovery & Community Collections: Investigation and Phased Plan

**Status:** PROPOSAL, Product UX direction APPROVED WITH REFINEMENTS 2026-11-10; **owner decisions R1-R6 FINAL
2026-11-11** (section E, "Owner decisions R1-R6 (SETTLED 2026-11-11)"); planning approval only; no
implementation, merge, deploy, migration or production-data change is authorized; no version numbers or dates
assigned. **Date:** 2026-11-10 (revised the same day; revised 2026-11-11 for R1-R6). **Role:** Feature Planner
(planning only). Every phase still needs owner approval and a normal kickoff before any work starts.

**Code read:** `origin/main` at `2cf895f6cb4558dfc3994239d8914d87f1fae661` (fetched 2026-11-10), via
`git show` / `git grep` only. **Re-read 2026-11-11 at `d5eaf3837812c965f4f7ca03d9a8bd9948269ac6`** (after
`v0.180.0`'s merge #1575): every `Svc` line cited in this file (283-335, 762-779, 1057-1090, 1091-1164,
1564-1581, 2068-2080, 3066, 3391-3395, 3630-3673, 3828-3834) is unchanged; `catalog-cache.ts` lines moved (see
"Cache freshness versus revocation"). **Production:** read-only aggregates supplied by the planner (section C of
the briefs, 2026-11-10 and 2026-11-11); no database or production URL was touched while writing this file.

**Companion artifacts (2026-11-11, planning only):**
`docs/claude-plans/2026-11-11-collection-publication-boundary-phase0-scope.md` (implementation-ready Phase 0
scope) and `docs/claude-plans/2026-11-11-public-collection-http-status-spike-protocol.md` (the R1 Preview
spike).

**Evidence labels used throughout:**
- **CONFIRMED**: read in code on `origin/main`, with a `file:line` reference.
- **AUDIT**: taken from the four Sonnet audits and not re-read by this author. Treat these as unverified.
- **ASSUMPTION**: an inference this author has not checked.
- **PROPOSAL**: a recommendation that is not yet decided.

Path abbreviations: `Svc` = `backend/src/main/java/com/studysnap/backend/service/NoteCollectionService.java`,
`Ctl` = `backend/.../controller/NoteCollectionController.java`, `Card` =
`frontend/components/study-plan/public-study-plan-card.tsx`, `PPC` =
`frontend/app/collections/published/published-plans-page-client.tsx`, `EPC` =
`frontend/app/explore/explore-page-client.tsx`, `Labels` = `frontend/lib/collection-labels.ts`.

---

## Product UX decisions (2026-11-10) and material changes

**Verdict:** direction approved with refinements; planning approval only. The decisions below are binding
planning inputs. Three read-only verification passes on the same day (cache freshness, HTTP status, label
resolution) are folded in as CONFIRMED facts with `file:line`, or as ASSUMPTION where they are not checked.

**Accepted:** the phase order (0, 1a, 2a, 2b, 1b, 3 deferred); profile-aware Official tab labels, with
content-based card labels; grouping by existing `courseProgram` with owner-normalized titles; the public page
as the card's primary destination, with the adoption action (**Add study plan** / **Add review set**, owner R2
2026-11-11, replacing the earlier "Add to my library") kept as a secondary action until a 60-day
measurement says otherwise; `/explore/collections/{id}`; Phase 0 as a prerequisite for the public page, with the
existing published-stamp contract preserved; indexable pages with the sitemap deferred; community collections
deferred. Not part of v0.180.0, and not bundled with the interactive LLM migration.

**What changed in this plan:**
- The not-found promise was weakened from "a real 404" to "a verified status, or 200 plus `noindex` with no
  crawler-status claim". A genuine 404 is probably impossible under the root layout (Conflicts, C1).
- The privacy promise is now "about two minutes on NoteLib's own systems", not "immediate" (Cache freshness
  versus revocation).
- A three-way publication contract (parent, stamped child, note) is written down. The nine legacy sections now
  have a reconciliation procedure that is optional.
- Phase 1a gains a collection-purpose label resolver that never says "Lesson Plan" for a board-exam set.
- Phase 1b gains a 60-day analytics gate.
- Acceptance criteria were added for loading, empty states, counts, disclosure, share, adoption failure,
  analytics and backend safety.

**What changed on 2026-11-11 (owner R1-R6 FINAL):**
- R1: only option (a) remains; middleware/`proxy.ts` and a root-layout change are **not to be introduced**.
- R2: the adoption action is **Add study plan / Add review set** (purpose noun); "Start this …" is retired on
  public cards; after Phase 2b the primary action is **View study plan / View review set**.
- R3: null `learnerLevel` → "Study Plan". R4: the 120 s cap stays; the TTL lever is closed.
- R5: the nine-section reconciliation is **DORMANT** indefinitely; guardrail: never unpublish and republish ALE
  or PNLE. R6: the freshness wording rule is fixed.
- Release coordination re-written for `v0.180.0` deployed and `v0.181.0` in progress (section F).

| Product UX decision | Where it landed |
|---|---|
| 1 Phase order; 1a independent; not in v0.180.0 | §D order line; Phase 1a dependencies; §F |
| 2 Tab and card labels, purpose not viewer | Phase 1a scope items 1-2 and the label table; E1 settled |
| 3 Program grouping, title convention, no year inference, no "Coming soon" | Phase 1a items 3, 7-8; E2 |
| 4 Card actions, 60-day gate | Phase 1b gate; measure-first item 3 |
| 5 Public page behaviour | Phase 2b scope and acceptance |
| 6 Privacy safeguards, stamp contract, cache | Phase 0 acceptance; "Publication contract"; "Cache freshness versus revocation" |
| 7 Indexing, not-found verification | Phase 2b "HTTP status and not-found strategy"; E5 |
| 8 Community deferred, MVP shape | Phase 3 |
| 9 Extra acceptance criteria | Phases 0, 2a, 2b, 1b |

## Conflicts with confirmed repository behaviour

| # | Product UX expectation | Repository fact | Resolution in this plan |
|---|---|---|---|
| C1 | A genuine not-found response, verified in a production build (§7) | CONFIRMED `frontend/app/layout.tsx:88-96` wraps every route in `<Suspense fallback={null}>`. The repo records that this commits the HTTP status first. `RELEASES.md:22` (v0.180.0 branch): "the HTTP status stays 200". The v0.179.0 Preview measured `/exam/[slug]` `notFound()` returning **200** with a `noindex` shell, but that route also has `loading.tsx`. No route without `loading.tsx` has been measured. No test or script asserts a rendered page's status | A pre-implementation spike (Phase 2b; protocol in `2026-11-11-public-collection-http-status-spike-protocol.md`). **SETTLED R1 (2026-11-11):** if a genuine 404 is not feasible, HTTP 200 plus a generic `noindex, nofollow` shell and no crawler-status claim; no middleware, no global-layout change. The not-found head is generic and identical for never-existed and withdrawn (the backend already 404s both). A backend failure throws and is never cached |
| C2 | Bounded revocation, never stale authorization (§6) | CONFIRMED: a per-process 120 s hard cap. The sitemap cap is 1 h. Some loaders use `next:{revalidate:300}`. No invalidation hook exists | Collection loaders use `getCatalogCached` with `hardCapMs`, do not use `revalidate`, and do not cache a null result. The promise is worded as "about two minutes" |
| C3 | Card labels follow content, never "Lesson Plan" for a board-exam set (§2) | CONFIRMED `Labels:98-99`, `ce` → **"CELE"**. The exam override is already viewer-independent (pinned `collection-labels.test.ts:60-73`). The gaps: the 3 computing roots (no slug); a `BOARD_EXAM_REVIEW` collection with no slug; null `learnerLevel`; every Goal card's `goalSingular` CTA ("Start this Goal / Course / Collection"); the dashboard does not pass the slug; the hard-coded EPC:76 description; and the docs call the labels profile-specific | Phase 1a resolver and fixes. **The `goalSingular` CTA problem is resolved by R2:** the adoption action no longer uses `goalSingular` at all on public cards ("Add study plan" / "Add review set") |
| C4 | No incorrect Add flash; "Open my copy" (§5) | The only lookup is the client join of `GET /collections` on `sourcePlanId` (PPC:71-78, :116). The exam hub concedes a brief "Start" flash | Gate the action area on resolved auth state. Anonymous visitors need no lookup. An optional small backend lookup by source id is a PROPOSAL. Never put viewer data in the cached payload |
| C5 | Academic Term grouping where data supports it | Term data exists only on BSCS/BSIT/BSIS children (First/Second Semester). Board-exam roots have none. The public Goal detail returns no children today | The 2a outline carries `termLabel`/`termOrder`. 2b renders without terms when none exist |
| C6 | Accurate counts (§9) | `listPublic` counts ignore note visibility (Svc:309-318) | Phase 0 item 4, plus the same predicate in 2a. The 9 stamped sections are **included** in all three counts |
| C7 | Preserve or map the analytics semantics (§9) | The `*_PREVIEWED` / `*_ADOPT_CLICKED` events fire only when `discoverySource` is set (AUDIT). New types need the `AnalyticsEventType` enum first | 2b analytics item; the 1b gate accounts for this |
| C8 | Separate reconciliation of the nine sections (§6) | CONFIRMED `publishChildCollections` (Svc:2068-2080) sets **every** ordered child PUBLIC on a Goal publish | "Publication contract" subsection. **New finding:** any unpublish then re-publish of ALE or PNLE (including a Phase 0 cascade round trip) silently flips the 9 sections PUBLIC. That reconciles them as a side effect. **SETTLED R5 (2026-11-11): reconciliation is deferred indefinitely; guardrail: never unpublish and republish ALE or PNLE** (re-verified at `d5eaf383`: Svc:2068-2080 sets every child returned by `findOrderedChildrenByParentCollectionIdAndOwnerUserId` PUBLIC with no condition) |
| C9 | 1a planned independently of Phase 0 | 1a ships before the page exists | 1a must introduce **no link** to `/explore/collections/{id}` |
| C11 | Distinguish failure from not-found (§7) | CONFIRMED `catalog-cache.ts:168-265` (was :153-230 at `2cf895f6`; backoff throw :210-213, `shouldCache` :242, failure record :251-258): any rejection inside `getCatalogCached` triggers the 15 s backoff | Not-found returns `null` uncached (`shouldCache`) and is never thrown (Phase 2b item 1) |
| C10 | Not-found and failure heads (§7) | The original journey C3.3 said "no `noindex`" on backend failure | Changed: a failure renders a static `noindex` "unavailable" head (the v0.180.0 profile pattern) |

---

## 0. Executive summary

1. Today an "official" collection is one that is PUBLIC, top-level, not an adopted copy, and ADMIN-owned. Publishing is backend-ADMIN-only. Explore's "Recommended" section is an exact client-side `courseProgram` match, and nothing else feeds it.
2. Academic year is not structured data anywhere: not on collections, notes or profiles. It exists only in root titles, and those titles are inconsistent. ADR-003 limits collections to two persisted levels.
3. The roughly 5-note preview is a frontend slice of an unpaginated API. For a Goal, that API flattens all items, ordered by collection UUID and then position, and returns no Subject Plan outline.
4. **Two lifecycle paths fail open in code (latent today: a read-only snapshot on 2026-11-10 found 0 PUBLIC child plans under a non-public parent and 0 non-Official PUBLIC roots, so nothing is exposed right now):** (a) unpublishing a Goal leaves its published children PUBLIC, readable and adoptable by id; (b) deleting a Goal orphans its PUBLIC children into roots (`ON DELETE SET NULL`), and they then show on Explore as Official cards.
5. A public collection page would expose child ids, so **(a) and (b) must be fixed before that page ships.** It is a small backend change that can ship independently. Also measured: 9 child sections inside live Official sets (ALE 7 of 8, PNLE 2 of 9) are PRIVATE but published-stamped; they are already shown and copied through the Goal, so the stamp-based rule must be preserved, not tightened (E4).
6. Recommended order: Phase 0 privacy fix (backend), Phase 1a copy and labels (frontend), Phase 2a bounded outline plus paged titles API (backend), Phase 2b public page (frontend, cached), Phase 1b simplified card (frontend).
7. Phase 1b's card CTA points at the Phase 2b page, so **the brief's Phase 2 must come before Phase 1's card change.** Phase 1's copy, grouping and type labels do not depend on it and can ship first.
8. Program grouping and the Study Plan vs Review Set distinction need **no schema change.** `courseProgram`, `learnerLevel` and `resolvedExamGoalSlug` are already on every public summary and are populated on all 8 roots.
9. Year-aware ranking (the brief's tier 1) is **not achievable honestly today.** Neither the learner's year nor the plan's year is structured. Recommend deferring it, and showing the available years instead.
10. **Community collections (Phase 3): recommend NOT starting now.** Six named prerequisites are listed in Phase 3. The public page should ship first and run stably on official content alone.

### Decisions needed (owner / Product UX)

**Status 2026-11-10:** Product UX has settled D1, D3, D5, D6, D7 (sitemap deferred) and D8 as recommended
below. D4 is settled as a title convention, with no structured year. D2 and D2b stand. The remaining owner
decisions are in section E, "Remaining owner decisions". The table is kept as the original record.

| # | Decision | Recommended answer |
|---|---|---|
| D1 | Fix design for the two fail-open cases (cascade on write vs a check at read time) | Both: a read-time ancestor check (fails closed however the state arose), plus cascades on unpublish and Goal delete (keep the data clean). See section D, Phase 0 |
| D2 | When Phase 0 ships | As its own small backend deploy after v0.180.0's +24 h read, away from the pricing env save and **not** riding with the deadline-sensitive interactive LLM migration. Exposure today needs a child UUID, so it is important but not an emergency; every backend deploy carries kill risk, which argues for one small, isolated deploy |
| D2b | Should a PUBLIC Goal include a child that is PRIVATE but stamped (E4)? | **MEASURED 2026-11-10: 9 such sections already sit in live Official sets (ALE 7 of 8, PNLE 2 of 9).** Recommend **do not tighten**; keep the stamp-based rule and build Phase 0, 2a and 2b around it (see E4). Optional owner-run hygiene write later |
| D3 | Explore tab label: keep "Official {profile plural}" or rename to "Collections" | Keep the profile-aware label. Fix the hard-coded description. Add per-card type labels. Reject the bare "Collections" (section B) |
| D4 | Academic year: title convention now, structured column later? | Normalize titles now (an owner data write; display only). A structured year waits for a demonstrated need and an ADR-003 amendment (section E) |
| D5 | Keep direct adoption on the card as a secondary action? | Yes, until usage data says otherwise (section B) |
| D6 | Public page URL shape | `/explore/collections/{id}` (section E) |
| D7 | Public page indexable and in the sitemap? | Indexable with `buildPageMetadata`. Add to the sitemap only after a measured walk budget (section E) |
| D8 | Start community publishing? | Not now. Gate it on the Phase 3 prerequisites |

---

## Verification record (audit claims checked against `origin/main`)

| Claim | Result | Evidence |
|---|---|---|
| (a) A published child stays readable and adoptable after its Goal is unpublished | **CONFIRMED, and extended** | `getPublic` checks only `findByIdAndVisibility(PUBLIC)` plus `assertPublishedIfSubjectPlan` (Svc:762-779, 3391-3395); there is no parent check. `updateVisibility` (Svc:1057-1090) cascades PUBLIC to children through `publishChildCollections` (Svc:2068-2080) but never cascades PRIVATE. The cascade runs only one way |
| (new) Deleting a Goal orphans its children | **CONFIRMED** (was AUDIT B2(d)) | `V83__collection_parent_hierarchy.sql:2`: `REFERENCES note_collections(id) ON DELETE SET NULL`. `delete` (Svc:1564-1580) deletes the row only. An orphaned PUBLIC child then has no parent, so `listPublic`'s root query returns it, and it matches the Official predicate |
| (new) A child's own visibility does not decide whether it belongs to a public Goal | **CONFIRMED** | `getPublic`, `listPublic` and `adoptGoal` select children by `published_at != null` only (Svc:309, 769, 1158-1164; comment at 1158: "The publication stamp, rather than visibility, decides"). A child set PRIVATE inside a PUBLIC Goal is therefore still shown and adopted through the Goal. Whether any UI lets a curator unpublish a single child is an **ASSUMPTION**, not checked |
| (new) The public detail response emits `parentCollectionId` and `sourcePlanId` | **CONFIRMED** | Svc:3657-3658. For a PUBLIC child under a PRIVATE parent, this emits the private parent's id. It is an id only, but it is an id of a row the caller cannot see |
| (b) `listPublic` is unbounded, and its counts use `published_at` only | **CONFIRMED** | Svc:283-339: no limit; children and items are filtered by `getPublishedAt() != null` in Java; nothing checks note visibility. `readyCountsForNoteIds` (Svc:3066) runs over the same unfiltered note ids |
| (c) The 5-note preview is a frontend slice; Goal items are flattened in UUID order | **CONFIRMED** | Card:22 `PREVIEW_NOTE_LIMIT = 5`; Card:136-141 shows all items when there are 6 or fewer; Card:249 renders "+ N more notes". Svc:768-776 calls `findByParentCollectionIdIn` (unordered), then `findByCollectionIdInOrderByCollectionIdAscPositionAsc` |
| (d) The tab label is profile-aware; the description is hard-coded | **CONFIRMED** | EPC:33 sets anonymous to `"STUDENT"`; EPC:48 is the template `` `Official ${collectionLabels.plural}` ``; EPC:76 description is "Find curated Official Review Sets…". `Labels`: STUDENT = Study Plans, BOARD_EXAM = Review Sets, TEACHER = Lesson Plans, PROFESSIONAL and null = `DEFAULT_LABELS` "Collections" (navLabel "Collections"). **Possible extension:** if a signed-in user can have a null `profileType` (ASSUMPTION: not checked whether onboarding guarantees one), they get "Official Collections", because only the anonymous path is forced to STUDENT |
| (new) Production child counts reconcile | **RESOLVED 2026-11-10 by a read-only count (planner)**: 69 children = 60 PUBLIC + 9 PRIVATE-but-stamped (ALE 7, PNLE 2); none PUBLIC-but-unstamped. Original audit note follows. | Brief section C: the child sections of the 8 PUBLIC roots sum to 69 (10+6+8+6+10+10+10+9), but only 60 PUBLIC ADMIN children carry `published_at`, and 13 PRIVATE ADMIN children also carry it. About 9 sections under live public roots are therefore probably PRIVATE-but-stamped. If so, they are already flattened into `getPublic`, copied by `adoptGoal` and counted by `listPublic` (Svc:1158 behaviour). The 13 may also include drafts under the 1 PRIVATE root. This plan builds on neither reading; see E4 and "measure first" item 1 |
| (e) The publish guard is ADMIN-only; private notes are rejected | **CONFIRMED** | Ctl `@PostMapping("/{id}/visibility") @PreAuthorize("hasRole('ADMIN')")`; `SecurityConfig.java:16` `@EnableMethodSecurity`, `:60` `GET /collections/public/**` permitAll. `validatePublishable`, `validateGoalPublishable` and `validateLeafPublishable` (Svc:2028-2066) throw `CollectionNotPublishableException` when a note is missing or not PUBLIC |
| Purge hard-deletes every collection the user owns | **CONFIRMED**, line numbers corrected | `AccountPurgeService.java:193-200` (`findByOwnerUserId` then `deleteByOwnerUserId`); private notes deleted at :240. The audit cited :225-240 |
| The note-level Official predicate differs | **CONFIRMED** | `PublicLibraryRepositoryImpl.java:47` hard-codes `OFFICIAL_AUTHOR_EMAIL`, used by `officialAuthorPredicate` at :783. The collection predicate is role-only (`NoteCollectionItemRepository.java:63` `owner.role = 'ADMIN'`) |
| `source_plan_id` has no FK | **CONFIRMED** | `V76__…sql:4` adds the column with no `REFERENCES`; :9-11 is the partial unique index |
| The summary carries `learnerLevel` and `resolvedExamGoalSlug` | **CONFIRMED** | `NoteCollectionSummaryResponse.java` record fields |
| Catalog-cache constants | **CONFIRMED** | `frontend/lib/catalog-cache.ts:2-13` (120 s page cap, 1 h sitemap, 15 s backoff, 1024 keys, 15,000 items) |
| Walk allowlist | **CONFIRMED** | `PublicApiDiagnosticsAggregator.java:50` `{"sitemap","exam-hub","subject","other","none"}` |
| Recommended = exact client-side match | **CONFIRMED** | PPC:131-156 (`getMe`, `normalizeCourseProgram`, `listPublicStudyPlans({courseProgram})`); PPC:120 sorts Browse All alphabetically |
| Withdrawn note returns `noindex` and `notFound` | **CONFIRMED** | `frontend/app/public/library/[subject]/[slug]/page.tsx:50-56` |
| Everything else in B1-B4 (adoption internals, analytics events, the moderation and rate-limit inventory, the deploy rules) | **AUDIT**, not re-read | Cited as AUDIT where used |

---

## A. Current-state investigation: the brief's 15 questions

| # | Question | Answer | Evidence |
|---|---|---|---|
| 1 | How are official collections selected and recommended? | **Selection:** there is no `official` column. Official is derived (PUBLIC, top-level, not adopted, ADMIN-owned, not the deleted-user sentinel). **Listing:** all PUBLIC roots, `updatedAt DESC`. **Recommended:** only for signed-in users. The client calls `getMe()` and makes a second `listPublic` call filtered by exact normalized `courseProgram`. With no program set, users see a "Set your course or program first" card. Anonymous users get no Recommended section. Browse All is the full list, sorted by title in the browser | CONFIRMED: Svc:283-290, NoteCollectionItemRepository:56-76, PPC:120-156, 228 |
| 2 | Role of Learner Level? | Stored on the collection (`learner_level`). Used for authoring inheritance, and for exam-goal resolution (`BOARD_EXAM_REVIEW` plus program gives `exam_goal_slug`). **Explore ignores it.** The profile `learnerLevel` is also unused by Explore. Production: COLLEGE on the 3 computing roots, BOARD_EXAM_REVIEW on 5 | CONFIRMED (DTO field); AUDIT (grep of Explore); production section C |
| 3 | Is academic year structured? | **No.** It is only in root titles ("BSCS Year 1 — …" vs "BS Information Technology — 1st Year"). `term_label` / `term_order` exist on child Subject Plans only (semester grain). ADR-001 puts Year 1-4 out of scope for LearnerLevel; ADR-003 forbids a Year level and rejects `journey_key`/`journey_order` | ADR-003 A, C, E and rejected alternatives; production section C |
| 4 | How is Course/Program stored and matched? | On collections: free-text `VARCHAR(120)`, no FK to the catalog. Matched by exact string after `CourseProgramNormalizationUtils.normalizeForStorage` (Svc:284); the frontend mirrors it. The catalog `course_programs` and families exist, but families are an authoring shortcut, not a discovery relationship. No related-program logic | CONFIRMED Svc:284-290; AUDIT for the catalog and families |
| 5 | Why does the preview show ~5 notes? | A frontend constant (`PREVIEW_NOTE_LIMIT = 5`, all items shown when there are 6 or fewer). The API returns every published item | CONFIRMED Card:22, 136-141, 249 |
| 6 | Can the preview API support full paginated browsing? | **No, not as designed.** It is a single unpaginated payload. A Goal's items are flattened across children in UUID-then-position order. There is no child list, child title, term or grouping key on items, and the payload includes the full `companion` JSON. It technically contains every title, but a 571-item CELE payload with no outline is the wrong shape for a page and a crawler target | CONFIRMED Svc:762-779, 3630-3673 |
| 7 | Are there public collection detail routes? | **No.** `/collections/[id]` is owner-scoped. The only deep link is `/collections/published?highlight={id}`. Collections are not in the sitemap | AUDIT B1/B3 (routes); CONFIRMED sitemap.ts:63-81 has no collection URLs |
| 8 | What stops ordinary learners from publishing? | **The backend:** `@PreAuthorize("hasRole('ADMIN')")` on `/visibility` (and on `/publish-update` plus `assertAdmin`), with method security enabled. Create and update DTOs carry no visibility field. The UI also hides the button for non-admins | CONFIRMED Ctl (visibility), SecurityConfig:16; AUDIT (publish-update, DTOs, UI) |
| 9 | How is official status protected? | It is a derived predicate, not an assignable flag, so it is exactly as protected as the ADMIN role plus the publish guard. **Gap:** an orphaned child (Goal delete) becomes "official" with no curator action. **Gap:** note-level official adds a hard-coded email that the collection path lacks | CONFIRMED (see verification record) |
| 10 | What happens when a published note becomes private? | `getPublic` filters item notes to PUBLIC at read time, so titles disappear immediately (fails closed). `listPublic` counts (`itemCount`, `readyCount`) do **not** check note visibility, so a card can over-count. No title leaks through counts. Adoption skips non-public notes | CONFIRMED Svc:3825-3833 (filter), 309-318 (counts); AUDIT for adoption skipping (Svc:2157) |
| 11 | How are adopted copies linked to sources? | `source_plan_id` (no FK) plus sync columns, and a unique partial index for one copy per owner and source. Notes are copied (PRIVATE, with provenance). Updates are additive-only and applied only when the learner chooses. Unpublish or delete detaches the copy, which stays usable | CONFIRMED V76:4, 9-11; AUDIT for the copy and update internals |
| 12 | Safest architecture for public pages today? | A server-rendered Next page reading two **new bounded** anonymous endpoints (outline; paged titles), through `getCatalogCached` (TTL ≤ 120 s, single-flight, never stale). Fail closed: a generic not-found shell plus `noindex` for withdrawn content (status per the Phase 2b spike), and an error state (never an empty 200) on backend failure. No warmers. Reuse the public note viewer for note bodies | PROPOSAL (section D, Phases 2a and 2b) |
| 13 | Smallest changes with meaningful UX value? | (i) A profile-aware Explore description (one string). (ii) A per-card type label from `learnerLevel` / `resolvedExamGoalSlug` (data already present). (iii) Grouping Browse All by `courseProgram` (data already present). (iv) Shortening the repeated "Adds a private, editable copy" caption. All frontend only | PROPOSAL (Phase 1a) |
| 14 | What can ship independently? | Phase 0 (backend privacy), Phase 1a (frontend copy and grouping) and Phase 2a (backend, invisible until used) are each independent. Phase 2b needs 2a and 0. Phase 1b needs 2b | PROPOSAL (section F) |
| 15 | Conflicts? | v0.180.0 (built, undeployed, frontend-only, anti-drift says "no Public Library or Explore redesign"); pricing (an owner env save that redeploys Render); interactive LLM migration (deadline 2027-01-14; checkpoint 2026-12-15); Challenge Quiz quality; the BSIS/Year 2 curriculum work (titles and roots are about to change); the 24 h deploy spacing; ADR-003 Phase C (Degree entity, not shipped), which overlaps "program and year" grouping | AUDIT B4; section F |

---

## B. Product UX assessment of the brief's proposed decisions

| Proposal | Verdict | Reason |
|---|---|---|
| Keep Explore as the discovery destination, two tabs | **Agree** | Matches `explore.md` and the v0.67.0 "Explore owns discovery" decision |
| Rename the tab "Official Review Sets" to "Collections" | **Reject as stated; refine** | (1) The current tab is not "Official Review Sets" for most viewers. It is profile-aware, and anonymous visitors see "Official Study Plans" (CONFIRMED EPC:33, 48). (2) `collections.md` says the anonymous chrome must not fall through to the null-profile "Official Collections". (3) For PROFESSIONAL users, a bare "Collections" tab collides with their personal nav item "Collections" (`DEFAULT_LABELS.navLabel`), a collision `collections.md` already warns about (AUDIT :1151). (4) "Collection" is not one of the formal names in VOICE_AND_TONE. **Refinement:** keep `Official {plural}`; make the EPC:76 description profile-aware ("Find curated Study Plans and Review Sets…"); put the Study Plan vs Review Set type label on each card. **Superseded 2026-11-10:** Product UX chose one universal intro and purpose-based card labels (Phase 1a) |
| Describe content as Study Plans and Review Sets | **Agree** | The catalog really is mixed: 3 COLLEGE Study Plans and 5 board-exam Review Sets. The type comes from `learnerLevel = BOARD_EXAM_REVIEW` plus `resolvedExamGoalSlug` (an exam-labelled Review Set, e.g. "CELE Review Set" via `getCollectionLabels(profile, slug)`); otherwise it is a Study Plan. No new field |
| No per-level, program or year top-level tabs | **Agree** | Grouping goes inside the tab (section headers by program), not into navigation |
| Relevance hierarchy tiers 1-5 | **Refine** | Tier 1 (program plus academic year) cannot be computed: there is no structured year on the plan or the profile. Implementing it would mean title parsing or inferring from adoptions, and the brief forbids both. Tier 4 (related programs) has no supported relationship; families are explicitly "not a curriculum model". **Workable deterministic order:** (1) exact program, all years, ordered within the group; (2) same Learner Level class (COLLEGE vs BOARD_EXAM_REVIEW) as the profile, labelled "More for {level}", not "Recommended"; (3) everything else in Browse All |
| Program/year grouping, each year separately adoptable | **Agree** | Already the model: one Year root per adoptable plan (ADR-003 A). Show a program group with one card per Year |
| Simplified card, one primary CTA to a detail page | **Agree, but only after the page exists** | Without Phase 2b the CTA has no destination. Shipping the simplified card first would remove Preview and leave nothing in its place |
| Remove direct adoption from the card? | **Refine: keep it as a secondary action** | Evidence: board-exam sets carry 3 to 49 adoptions each, and 60% of Quick Review sessions in the last 60 days ran on adopted-copy packs (production section C). Adoption is the conversion path that works today. The `*_ADOPT_CLICKED` and `*_PREVIEWED` events (AUDIT) let us measure preview-then-adopt vs direct adopt before deciding (section F, "measure first"). The secondary action must say what it does ("Add study plan" / "Add review set" per R2, 2026-11-11; earlier "Add to my library"), so a learner expecting a preview is not silently adopted |
| A public, shareable read-only page per published collection | **Agree** | The core of the initiative. Needs new bounded backend projections (section D, Phase 2a) |
| No sign-in to browse | **Agree** | Already true for the catalog (`collections.md`). Adoption stays authenticated, using the existing discovery-intent cookie path |
| "Open my copy" for an already-adopted learner | **Agree** | Reuse the client join on `sourcePlanId` that the card already does (AUDIT PPC:116). Do not add viewer state to the anonymous, cacheable payload |
| Community publishing (Phase 3) | **Defer** | See Phase 3 gates |
| Do not merge the Notes tab into one feed; no new workspace | **Agree** | — |

---

## C. Recommended target experience (journeys)

**Shared model:** Explore lets the learner discover. The public collection page (`/explore/collections/{id}`,
PROPOSAL) lets them evaluate and share. Their Review Sets / Study Plans library (`/collections`) is where they
study their own copies.

### C1. A BSCS learner browsing Year 1 and Year 2
1. Profile `courseProgram = Computer Science`, `learnerLevel = COLLEGE`. Opens `/explore`. The tab reads "Official Study Plans".
2. **"For Computer Science"** group (exact program match): one card per Year root, in title order (the convention is fixed per decision D4). Each card shows: type label "Study Plan", the title, a one-line description, "10 Subject Plans · 216 notes", and the Official badge.
3. If no year is known for this learner, nothing pretends otherwise. Both Year cards appear side by side and there is no "your year" highlight.
4. Taps **View study plan** and lands on the public page. Sees the outline: First Semester and Second Semester headers (from `term_label` / `term_order`), each listing Subject Plans with note counts. Expands a Subject Plan and sees paged note titles. Opens a note in the existing public note viewer, without adopting.
5. Taps **Add study plan** (R2). If signed out, the existing discovery-intent cookie flow runs. Adopts Year 1 only, and Year 2 stays independent.
6. If no Year 2 is published yet, nothing says "coming soon" unless the owner asks for it. The group simply shows what exists.

### C2. A board-exam taker discovering a relevant Review Set
1. BOARD_EXAM profile, program Civil Engineering. The tab reads "Official Review Sets".
2. The "For Civil Engineering" group shows CELE Comprehensive Review, labelled "CELE Review Set", with "10 Subject Plans · 571 notes".
3. If the program has no set, the empty state uses Product UX's copy: "We haven't found a review set for your program yet. You can still explore other available plans below." The noun is profile-aware. Browse All stays directly underneath, so the empty state never implies the catalog is empty.
4. **View review set** opens the public page with 10 Subject Plans in curator order (the ordering fix in Phase 2a), each showing counts. Titles load one page at a time.
5. The exam hub `/exam/{slug}` keeps its embedded section, and its cards link to the same public page.

### C3. A learner opening a shared public collection link
1. Opens `https://www.notelib.app/explore/collections/{id}`. The page renders server-side with title, description, attribution ("Curated by NoteLib"), Official badge, program and level, and the outline. Canonical URL, OG title and description come from `buildPageMetadata`.
2. Unpublished, deleted, orphaned or never-published: `notFound()` with `noindex, nofollow` metadata (the public note page precedent). The not-found title and body are generic and identical in every case, so the page does not say whether the collection ever existed. **The HTTP status is unverified and probably 200** (C1; Phase 2b spike). Per R1, 200 with this shell is accepted if the spike shows a genuine 404 is not feasible.
3. Backend failure: an error state with a calm retry and a static `noindex` "unavailable" head. It is never an empty outline and never cached (revised from "no `noindex`", C10).
4. A child Subject Plan URL resolves only if every ancestor is PUBLIC (Phase 0).

### C4. A learner adopting a collection
1. On the public page, the secondary action **Add study plan** / **Add review set** (R2; noun from the collection's purpose) calls the existing `adopt` or `adoptGoal`, unchanged. "Already adopted" uses the existing `alreadyAdopted` response. Before the click, the page asks the authenticated `GET /collections` list on the client, and if the learner already has a copy shows **Open my copy** (`/collections/{copyId}`). To avoid the known "Start" flash, render a neutral button while that check is pending.
2. After adoption, the learner goes to their copy, as today. The copy's update model is unchanged: additions only, applied only when the learner chooses.
3. Source withdrawn after adoption: the copy is detached and keeps working (existing behaviour). The public page stops resolving within about two minutes on NoteLib's own systems (R6; see "Cache freshness versus revocation").
4. Source withdrawn between viewing and adopting: the adopt call fails with not-found. The page explains calmly that this collection is no longer available and offers a way back to Explore, keeping the visitor's browsing context. It is not a dead end. PROPOSAL: a Back link to `/explore`. Whether the Explore tab and scroll position survive a history back is an ASSUMPTION to check in Preview.

### C5. A future community creator publishing (Phase 3, not now)
1. The creator has a PRIVATE collection over their own PUBLIC notes and taps "Share publicly". The confirmation explains that the plan becomes visible to everyone, that only its public notes show, and that any private notes will be hidden, not published.
2. Publishing never changes any note's visibility. Making a note public stays a separate per-note action (with the verified-email rule).
3. The page shows "Shared by {display name}" with a Community badge, never Official. The creator can unpublish at any time, and the page stops resolving within the cache TTL.
4. Reporting a page sends it to an admin queue (prerequisite P3-3).

---

## D. Phased plan

**Order (APPROVED by Product UX 2026-11-10; CONFIRMED by the owner 2026-11-11, planning only):** Phase 0 → Phase 1a → Phase 2a → Phase 2b → Phase 1b;
Phase 3 deferred. (Phase 1a has no code dependency on Phase 0, but the confirmed order places it after 0.) The brief's Phase 2 comes before Phase 1's **card** change, because the card's single CTA
needs a destination. Phase 1's **copy and grouping** do not depend on Phase 2 and can ship first.

### Phase 0: Publication-boundary fail-closed fix (backend)

**Implementation-ready scope (2026-11-11):** `docs/claude-plans/2026-11-11-collection-publication-boundary-phase0-scope.md`
carries the exact methods, named acceptance tests, the owner-run read-only pre-checks and the deploy inputs. The
text below stays the summary; where they differ, the scope file is newer.

- **Learner problem:** content a curator withdrew can still be reached by id, or reappears on Explore as an Official card. This must be fixed before a public page starts printing child URLs.
- **User story:** as a curator, when I unpublish or delete a Goal, none of its Subject Plans stays publicly reachable.
- **Scope (PROPOSAL, decision D1):**
  1. **Read-time ancestor check (fail closed):** `getPublic`, `adopt`/`adoptSource` and `adoptGoal`'s source gate also require every ancestor to be PUBLIC (with two levels, that is one parent lookup). A missing parent is treated as fine only for a true root. A child whose parent row is gone is handled by item 3.
  2. **Unpublish cascade:** `updateVisibility(PRIVATE)` on a Goal also sets its children PRIVATE. This mirrors `publishChildCollections` and keeps the data honest. Re-publish already re-flips the children (Svc:2068-2080), and `last_update_published_at` keeps the boundary semantics (Svc:1066-1083).
  3. **Goal delete:** on the source side (`sourcePlanId == null`), either delete the children together with the Goal, or set them PRIVATE before the parent is deleted. Recommended: **set them PRIVATE**. Deleting would cascade items and orphan sessions, a bigger behavioural change; owner decision D1. **A read-time check cannot catch an orphan**: after `ON DELETE SET NULL`, a former child is indistinguishable from a root (no column records "was a child"; `source_parent_id_at_sync` exists only on adopted copies, ASSUMPTION from the column list). So the write-side guard is the only real fix for this case, and an owner-run read-only check for existing orphans is part of the release.
  4. **`listPublic` count parity:** count only items whose note is currently PUBLIC, matching `getPublic`. **Cost check (planner, 2026-11-10):** `listPublic` already loads every published item row for every root and child on each call (about 2,400 rows today) and already does one bulk note lookup (`readyCountsForNoteIds`), so the parity filter must **reuse that existing lookup and add no new query**; if it cannot, move this item into 2a, where `listPublic` is bounded and cached. Today the overcount is zero in practice (all 2,393 items point at PUBLIC notes), so this item is correctness hygiene, not urgent. The filter is Java-side over the projection, which keeps the comment's invariant (Svc:299-303) of not changing the shared queries.
  5. Stop emitting `parentCollectionId` when the parent is not PUBLIC (or always on the public mapper; ASSUMPTION: no public consumer reads it).
- **Exclusions:** no migration, no API shape change, no UI change, no change to adoption semantics or adopted copies.
- **Reuse:** `findByIdAndVisibility`, `publishChildCollections` (as the mirror), and the existing projection filter.
- **Frontend:** none.
- **Backend:** Svc only (plus tests). Under roughly 80 LOC (ASSUMPTION).
- **DB/schema:** none. **Owner-run read-only check before release:** count PUBLIC child rows whose parent is PRIVATE, and PUBLIC roots with `published_at` that are not in the known official 8. Write it into a `docs/claude-plans/*.sql` file for the owner. Any existing bad rows are fixed by an owner-run write, never by Claude.
- **Privacy risk:** the change only narrows what is reachable. Risk is that a check that is too strict hides a legitimate root. Mitigation: a parity test over all 8 production-shaped roots.
- **Performance:** one extra indexed `findById` per public detail read or adopt. Negligible.
- **Testing:**
  - `MockMvc` real requests:
    - `GET /collections/public/{childId}` returns 404 after the parent is unpublished;
    - `POST /collections/{childId}/adopt` returns 404 in the same state;
    - `POST /collections/{id}/visibility` returns **403 for ROLE_USER** (closes the gap noted in AUDIT B2: today the ADMIN guard is only checked through reflection).
  - A real-PostgreSQL integration test for unpublish, Goal delete and listPublic count parity with a note made private. **Correction 2026-11-11 (CONFIRMED):** `NoteCollectionServiceProjectionIntegrationTest` runs on **H2** in PostgreSQL mode with Flyway disabled (`backend/src/test/resources/application.yaml:3`, `:9-10`), so it is not a real-PostgreSQL test; the pattern to follow is `GoalAdoptionPostgresIntegrationTest` (Testcontainers, Flyway on, :28-33), which already pins the ALE shape in `stampedPrivateChildIsAdoptedTheSameAsAPublicOne` (:159).
  - Update `NoteCollectionServiceTest:4342` (`updateVisibility_unpublishingGoalDoesNotCascadeToChildren`), which pins the current behaviour; changing it is the point, so say so in the release.
- **Acceptance:**
  - None of the three lifecycle cases leaves an anonymous read or adopt succeeding. Card counts equal the detail's visible item count.
  - **The stamp contract is preserved (Product UX §6):** ALE still shows and adopts 8 sections and PNLE 9, the 9 PRIVATE-but-stamped children included. A real-PostgreSQL test seeds a PUBLIC Goal with a PRIVATE stamped child and asserts that `getPublic`, `adoptGoal` and `listPublic` counts all **include** it. The ancestor check keys on the **parent**, never on the child's own visibility.
  - Goal delete sets the children PRIVATE and does not delete them; the test asserts the rows survive.
  - Anonymous `MockMvc` reads and adopts are covered for every case above.
- **Dependencies:** none. **Interaction (CONFIRMED Svc:2068-2080):** after the cascade, a re-publish sets every child PUBLIC, the 9 legacy sections included (C8). Say so in the release notes. **Guardrail (R5):** never unpublish and republish ALE or PNLE, as a test, a cleanup shortcut or a rollback; the nine sections are already PRIVATE, so the cascade is a no-op for them.
- **Complexity:** S.
- **Risk tier:** **one scoped cold agent** (it moves a privacy boundary). Frame it as falsification of the four claims above.
- **Rollout:** its own small Render deploy, with separate owner approval and the section 5 checklist (D2; section F), at least 24 h from any other deploy including `v0.181.0`'s env save. Watch: the 404 rate on `/collections/public/{id}` must not rise for the 8 known roots.
- **Rollback:** revert the commit. There is no data migration to undo.

### Phase 1a: Discovery copy, type labels and program grouping (frontend only)

- **Learner problem:** a mixed catalog with no type cue; a page description that contradicts the tab; Browse All is one alphabetical list; a repeated caption makes cards taller.
- **User stories:** as a BSCS learner I see my program's plans grouped first; as any visitor I can tell a Study Plan from a Review Set at a glance.
- **Scope:**
  1. The EPC:76 description is replaced with Product UX's universal intro, the same for every viewer: "Find study plans, review sets, and notes to help you prepare for what you're learning." The tab label stays `Official {viewer plural}` (EPC:48, unchanged; not renamed to "Collections").
  2. **A collection-purpose label resolver** (PROPOSAL; a new pure function beside `getCollectionLabels`, which stays as it is). It is decided by the **collection**, never by the viewer:

     | Collection data (CONFIRMED on the summary/detail DTOs, `api.ts:2170-2175`, `:2225-2229`) | Card type label and CTA noun |
     |---|---|
     | `resolvedExamGoalSlug` maps in `EXAM_SHORT_NAMES` (`Labels:98-99`) | "{X} Review Set" (ALE, PNLE, LET, CPALE, **CELE**) |
     | otherwise `learnerLevel === BOARD_EXAM_REVIEW` | "Review Set" |
     | otherwise `learnerLevel` non-null | "Study Plan" |
     | `learnerLevel` null (no backfill, `collections.md:118`; a public child's own level, with no inheritance) | **"Study Plan" (SETTLED R3, 2026-11-11)** |

     The viewer chrome (tab, nav, empty-state headings, list description) stays viewer-keyed: STUDENT and anonymous → "Official Study Plans", BOARD_EXAM → "Official Review Sets", TEACHER → "Official Lesson Plans", PROFESSIONAL, PARENT and null → "Official Collections". So a TEACHER sees an "Official Lesson Plans" tab above cards labelled "CELE Review Set". **This is intended.** A TEACHER who adopts that set still sees "Lesson Plan" in their own library. That is acceptable: the cards follow the content and the library follows the viewer. **No card ever says "Lesson Plan" for a board-exam set.** Validate the resolver against all 5 profile types plus null × all 8 production roots in a unit test.
     - **Adoption action wording (SETTLED R2, 2026-11-11):** every Official root is a Goal (`isGoal = childCount > 0`, CONFIRMED Card:63 at `d5eaf383`; CTA Card:79-83). So every card today says "Start this Goal" (STUDENT), "Start this Course" (TEACHER) or "Start this Collection" (null), and "Continue this …" once adopted (Card:80). Phase 1a changes the **adoption action on public cards** to **"Add study plan"** or **"Add review set"**, the noun taken from the collection's PURPOSE (the resolver row above: an exam-slug or `BOARD_EXAM_REVIEW` row → "Add review set"; every other row, null `learnerLevel` included → "Add study plan"), never from the viewer's profile and never "Start" ("Start" suggests a study session; the action creates a private copy). The adoption behaviour (`adopt` / `adoptGoal`, the discovery-intent path, routing to the new copy) is unchanged. **This retires the `goalSingular` noun problem ("Start this Goal") on public cards** because the wording no longer uses `goalSingular`. `getCollectionLabels`' own `goalSingular` stays as it is for owner surfaces.
     - **Adopted state (PROPOSAL):** "Open my copy", matching journey C4 and the Phase 2b page, replacing "Continue this {goalSingular|singular}" (Card:80). Pinned today by `published-plans-page-client.test.tsx:176`, `dashboard-study-plan-section.test.tsx:212`, `:289`, and the docs `collections.md:164`, `:1157-1158`, `:1165` (CONFIRMED). The "Sign in to adopt" label (Card:82) is outside R2; leave it unless Product UX says otherwise.
     - **Where it applies (CONFIRMED):** `PublicStudyPlanCard` renders on `/collections/published` (PPC:267, :315) **and on the exam hub** (`exam-hub-official-review-sets.tsx:6`), so the hub's card actions change with it (its heading does not). `DashboardStudyPlanSection` has its own CTA (`dashboard-study-plan-section.tsx:361-362`, "Open this plan" for an owned source) and is rendered on the dashboard (`dashboard/page.tsx:932`, `:1016`) **and in onboarding** (`onboarding/page.tsx:1850`, `:2197`). Whether R2 covers this section (it shows public plans) is **To verify with Product UX**; if yes, `onboarding/page.test.tsx:558`, `:614`, `:869` move too.
     - **After Phase 2b (Phase 1b and the 2b page):** primary **"View study plan" / "View review set"**; secondary **"Add study plan" / "Add review set"**, or **"Open my copy"** when the viewer already has one.
     - **Open question for Product UX, not blocking:** do `subjectSingular`, `sectionSingular` and `primarySingular` follow the purpose rule on public surfaces? Recommend yes only on the 2b public page, which is a later phase.
     - **Also fix:** `dashboard-study-plan-section.tsx:171` does not pass the slug, which leaves a hole in the rule. `public-library-page-client.tsx:1598` hard-codes "Looking for a full Study Plan…".
     - **Leave unchanged:** `exam-hub-official-review-sets.tsx`'s own strings (hubs are exam-specific; "Official Review Sets for {exam}"), but **its cards inherit R2** because it renders `PublicStudyPlanCard` (CONFIRMED :6); the backend exception text.
  3. Browse All grouped by `courseProgram` (exact string), with groups ordered alphabetically, cards within a group ordered by title, and an "Other" group for null programs. A group with a single card shows no header (the brief's "no filter with one result").
  4. Recommended keeps the exact-program fetch. When it is empty, add a "More for {level}" row filtered client-side by `learnerLevel` from the already-fetched Browse All list (no extra request). Signed-out visitors still get no Recommended section.
  5. Show the "Adds a private, editable copy" caption once (in the section intro, or as helper text on the adopt button), not on every card.
  6. Fix the other hard-coded "Official Review Sets" strings only where the viewer is not a board-exam surface. The exam hub stays "Official Review Sets for {exam}", which is correct there.
  7. **Titles and year (Product UX §3):**
     - Ordering by title within a program group is **display ordering, not authoritative curriculum metadata**. It is valid only while the owner keeps a consistent "Year 1-4" convention through the curriculum workflow.
     - No structured year, no Degree level, no third collection level, and no `siblingPosition` on roots.
     - Never infer a learner's year from adoption history. Until a structured learner-year field exists, show every available year for the program and highlight none.
     - No "Coming soon" for unpublished years without an explicit owner decision.
  8. No-match empty state (Product UX copy, profile-aware noun): "We haven't found a study plan for your program yet. You can still explore other available plans below."
- **Exclusions:** no CTA destination or behaviour change (the wording changes, the adoption call does not), no preview removal, no new requests, no backend, no change to personal `/collections`, and **no link to `/explore/collections/{id}`** (C9).
- **Docs to amend in the same change:**
  - `docs/features/collections.md` :156, :164 and :316-326: the viewer-keyed claims, so that cards are by collection and chrome by viewer. :34 and :43 are already consistent.
  - `docs/agents/gpt/NoteLib Product UX.md` ~113-117: it says the labels are profile-specific names of one entity. Keep "one entity" and add that card labels follow the collection's purpose.
  - Also `docs/features/explore.md`.
- **Voice:** sentence-case actions, warm and conversational, no model references, no gamification (`VOICE_AND_TONE.md`).
- **Reuse:** `getCollectionLabels`, `normalizeCourseProgram`, and the existing `listPublicStudyPlans` responses.
- **Backend, DB:** none. **Privacy:** none (same data). **Performance:** fewer requests is not a goal here, and there are no more requests.
- **Testing:**
  - Add resolver tests (profile × purpose, including null `learnerLevel`).
  - Keep `collection-labels.test.ts:60-73` unchanged; it already pins `('TEACHER','ce').singular === 'CELE Review Set'`.
  - Update the tests that pin "Start this …" / "Continue this …" (re-verified at `d5eaf383`): `public-study-plan-card.test.tsx` (:99, :116, :120, :133, :152, :190, :202), `published-plans-page-client.test.tsx` (:140, :176-177, :193, :225, :249, :321), `dashboard-study-plan-section.test.tsx` (:79-289, :631; only if the dashboard section is in R2's scope), `explore-page-client.test.tsx` (:50, :58, :71, :84, :195; not re-verified).
  - Check for collateral changes: `exam/[slug]/page.test.tsx` (no CTA string pinned, CONFIRMED by grep), `onboarding/page.test.tsx:558`, `:614`, `:869` (moves if the dashboard section is in scope: onboarding renders it), `app/collections/[id]/page.test.tsx:3446`, and the owner builder string "Start this {goalSingular} by adding the first child plan" (`study-plan-builder-page-client.tsx:2962`), which is an owner surface and must not change.
  - Add a grouping unit test.
- **Acceptance:**
  - Explore shows the universal intro.
  - Every card shows a purpose label correct for all 8 roots under every profile type. No board-exam card says "Lesson Plan", and no public card's adoption action says "Start this …": it reads "Add study plan" or "Add review set" by purpose, and "Open my copy" when adopted.
  - A program group of 1 renders without a header. The no-match state appears only for a signed-in viewer whose program has no plan, and Browse All stays directly below it.
- **Dependencies:** none (planned independently of Phase 0, Product UX §1). **Conflict:** `v0.181.0`'s anti-drift (CONFIRMED `CLAUDE.md:45`) says "no Public Library or Explore redesign" (as `v0.180.0`'s did). Phase 1a is copy and grouping, not a redesign, but it must not land in `v0.181.0`.
- **Complexity:** S. **Risk tier:** `advisor()` only.
- **Rollout:** a Vercel deploy, never in the same Vercel deploy as a price change (section F).
- **Rollback:** a Vercel one-step rollback or a revert.

### Phase 2a: Bounded public outline and paged titles API (backend)

- **Learner problem:** there is no way to see a whole collection's structure, and a 571-note set cannot reasonably be sent as one payload.
- **User stories:** as a visitor I see a set's Subject Plans in curriculum order, with honest counts, before loading any note titles; as a visitor I can page through one Subject Plan's note titles.
- **Frontend:** none (consumed by Phase 2b).
- **Scope (PROPOSAL; API shapes are for Product UX review only):**
  1. `GET /collections/public/{id}/outline`: the root's title, description, `courseProgram`, `learnerLevel`, `resolvedExamGoalSlug`, `estimatedStudyHours`, `adoptionCount`, attribution (`Curated by NoteLib` for Official; no owner name, email or id), and an ordered list of published children `{id, title, termLabel, termOrder, siblingPosition, visibleNoteCount}`, ordered the way the owner's Goal view orders them (`findOrderedChildrenByParentCollectionIdAndOwnerUserId`, `siblingPosition`, then terms grouped by `min(term_order)` per ADR-003 C). For a leaf, `children = []` and the root's own count. **No item titles, no companion content.** Bounded by the child count (≤ 10 in production; enforce a hard cap, e.g. 100, and fail rather than truncate silently).
  2. `GET /collections/public/{rootId}/items?child={childId}&page=&size=` (omit `child` for a leaf root): `{noteId, title, subject, label, position, publicPath}` for published items whose note is currently PUBLIC. **The route is always keyed by the root**, and the server verifies that `childId` belongs to `rootId`, that the root is PUBLIC, and that the child is **published (stamped) under that PUBLIC root, the same `published_at` rule `getPublic` and `adoptGoal` use** (the child's own `visibility` is not required; 9 live sections are PRIVATE-but-stamped, E4). So the ancestor check is built into the route, and a child id alone opens nothing. **Id-first paging** (the v0.179.0 pattern): page the item ids by `position` (keyset or `LIMIT/OFFSET`), then project only those notes. Size capped (e.g. 50). `totalVisible` comes from a count with the same predicates. There is no flattened Goal-wide page: the client pages per child, which is what keeps it bounded.
  3. Both endpoints apply Phase 0's ancestor check and the `published_at` plus live note PUBLIC filter. Both return 404 rather than an empty 200 for an unpublished collection.
  4. Item `publicPath` reuses the public note SEO path builder (ASSUMPTION: the backend can return enough for `buildPublicLibraryNotePathFromDetail`, i.e. the subject slug and note slug; else return `noteId` and let the frontend use `/public/notes/{id}`, which redirects).
  5. Leave the existing `GET /collections/public/{id}` unchanged (the card preview and adoption still use it), so there is no breaking API change and no deploy-ordering hazard.
- **Exclusions:** no migration, no change to `listPublic`'s shape, no viewer-specific state ("do I have a copy") in these anonymous responses, no rate limit (forbidden by the current anti-drift; revisit only with the owner).
- **Reuse:** `findCollectionNoteProjectionsByIdIn`, `CollectionExamGoalResolver.resolveWithoutInheritance`, `loadAdoptionCounts`, the existing child-ordering query, and the existing `permitAll` matcher (`/collections/public/**` already covers the new paths, CONFIRMED SecurityConfig:60).
- **DB/schema:** none required. ASSUMPTION to verify: an index on `note_collection_items (collection_id, position)` exists. If not, propose one as a separate owner-approved migration rather than folding it in.
- **Privacy risks:** a new projection that forgets one of the three filters (collection visibility plus ancestors, the `published_at` stamps, live note visibility). Mitigation: parity tests.
- **Performance risks:** anonymous crawlers paging every child of every set. Bounded per request (≤ 50 rows plus one count); total rows ≈ 2,393 across all sets today. Diagnostics: extend the walk allowlist with `collection` (a backend change in `PublicApiDiagnosticsAggregator`), or these requests count as `other`.
- **Testing:**
  - (a) `MockMvc` real-request tests for both endpoints: anonymous 200, unpublished 404, child-of-private-parent 404, `size` above the cap clamped or rejected.
  - (b) **Parity tests against real PostgreSQL rows:** for a seeded Goal with private notes, unstamped items, an unstamped child and a PRIVATE-but-stamped child, the union of all `items` pages across children equals `getPublic(...).items()` as a set, and outline counts equal per-child page totals. **Settled by Product UX §6: the PRIVATE-but-stamped child is included** in both endpoints and in `getPublic`. The Goal paths are not tightened.
  - (c) `NativeQueryPostgresIntegrationTest` covers any native SQL.
  - (d) An ordering test: the outline order equals the owner Goal view order.
- **Acceptance:**
  - CELE's outline is returned in a single bounded response with no item rows. Any child pages in ≤ 50-row pages, and parity holds.
  - **Counts agree (Product UX §9):** the outline total, each Subject Plan's `visibleNoteCount` and the union of its loaded pages describe the same material: stamped items whose note is currently PUBLIC. The 9 PRIVATE-but-stamped sections are **included** in all three, matching `getPublic` (C6).
  - **No N+1:** the outline computes every child's count in one grouped query, never one query per Subject Plan.
  - Withdrawn, never-existed and private parents return the same 404 body.
  - The responses carry no `Cache-Control` that would let a shared cache extend the 120 s bound. The backend sets none today (B1 verification).
  - No unbounded read, no warmer, no production load test.
- **Dependencies:** Phase 0 (shared ancestor check).
- **Complexity:** M. **Risk tier:** one scoped cold agent (it adds a new anonymous read path, a first-of-kind public projection).
- **Rollout:** a backend deploy, invisible until Phase 2b uses it. A `[CHECKPOINT]` row for the request rate on the new endpoints after 2b ships.
- **Rollback:** revert. Nothing depends on it until 2b.

### Phase 2b: Public collection page (frontend)

- **Learner problem:** learners cannot evaluate or share a set before adopting it.
- **User stories:** as a learner I can send a classmate a link to "BSCS Year 1" and they see the whole outline without signing in; as a learner I can open any note in the set without adopting; as a learner who already adopted it I see "Open my copy".
- **Reuse:** `getCatalogCached`, `buildPageMetadata`, the public note page as the note viewer, the discovery-intent cookie and the adopt calls, `getCollectionLabels`, the `PageHeader` and `BackLink` components (per `docs/ui-standards.md`), and the v0.180.0 route error-state pattern.
- **DB/schema:** none.
- **Scope:**
  1. Route `/explore/collections/[id]` (D6), `force-dynamic` and server-rendered. It fetches the outline through `getCatalogCached` with a key class such as `collection-outline`, **passing `hardCapMs: CATALOG_PAGE_TTL_CAP_MS`** (the cap is opt-in, as `server-public-study-plans.ts:45` does), a TTL of **≤ 120 s**, single-flight, never stale, and the 15 s failure backoff. The fetch is `cache: "no-store"`, **never `next: { revalidate: 300 }`** (C2). A backend 404 or 403 makes the loader **return `null` with `shouldCache: (v) => v !== null`**, so the result is neither cached nor backed off. It is **never thrown**: CONFIRMED `catalog-cache.ts:153-230`, where any rejection records a failure and, with `failureBackoffMs` set, makes the key throw `CatalogBackoffError` for 15 s. That would turn the next 15 s of not-found visits into the failure path. Any other non-OK response throws, which is the failure path, with backoff.
  2. The outline renders term headers (when any child has a term; otherwise the plain Subject Plan list, per ADR-003 consequences), Subject Plan rows with counts, and expandable rows. Expanding a row fetches the first page of titles **on demand, client-side**, with "Show more" (no automatic paging and no prefetch of all children). Note titles link to the public note page (the existing viewer). This reuses the public-note page as is; there is no new note viewer.
  3. Metadata: `buildPageMetadata` (canonical `https://www.notelib.app/explore/collections/{id}`, OG title and description, `CollectionPage` JSON-LD as Explore does). A per-collection OG image is optional and deferred.
  4. Fail closed: a backend 404 → `notFound()` plus `robots {index:false, follow:false}` and a generic title. A backend 5xx or timeout → the route error state with a static `noindex` "unavailable" head (the v0.180.0 profile pattern). It is never a 200 with an empty outline. The HTTP status is governed by the spike below.
  5. Adoption block (R2 wording; R2's primary/secondary rule is about the cards, where "View …" becomes primary once this page exists; on the page itself "View" is redundant, so whether adoption is the page's first-viewport primary action stays the Product UX §5c call):
     - **Add study plan** / **Add review set** by purpose (signed out: the discovery-intent cookie path) or **Open my copy** (from the client join on authenticated `GET /collections`).
     - The action area renders nothing actionable until the auth state and the lookup resolve: **no Add flash** (C4). Anonymous visitors skip the lookup.
     - After a successful adoption, the learner goes to the learner-owned collection, as today. The additive-only update behaviour is unchanged.
     - An adoption that fails with not-found shows journey C4.4.
  5b. **Share (Product UX §9):** a "Copy link" action copies the canonical URL with a calm confirmation ("Link copied"). It needs no sign-in and no adoption, and no analytics beyond the minimum below.
  5c. **First viewport (mobile-first, §5):** what it is (type label plus title), who curated it ("Curated by NoteLib" plus the Official badge), how much (Subject Plans and visible notes), and the primary action. The outline follows immediately. Use a short description and no stacked badges.
  6. Child Subject Plan pages: **not** separate URLs in the first release; the page is per root (Product UX may want `#subject-{id}` anchors). **This does not hide child ids**: the outline returns them to anonymous callers. Safety comes from Phase 0 and from the root-keyed `/items` route, not from omitting URLs.
  7. Analytics: reuse `*_PREVIEWED` semantics for the page view, or add `PUBLIC_COLLECTION_VIEWED` and `PUBLIC_COLLECTION_ITEMS_PAGED` (the enum first, per the repo rule). Adoption stays the server-side event. **Map, do not break (C7):** an adopt from the page carries a `discoverySource` naming the page, plus the arrival source where known (Explore, exam hub, shared link). Then the existing `*_ADOPT_CLICKED` funnel stays comparable. Add only the page-view event and, if needed, the expand event.
  8. **Generic not-found and existence privacy:** the same head and body for never-existed, withdrawn, deleted and orphaned collections. A cached positive outline can outlive a withdrawal by up to the bound in "Cache freshness versus revocation". That is the accepted bound, and it never authorizes note content.
- **Exclusions:** no sitemap entry in the first release (D7); no change to `robots.ts`; no warmers; no polling; no community attribution.
- **Backend:** none beyond Phase 2a (and the allowlist value, if the walker header is used).
- **Privacy:** the cache TTL bounds a withdrawn collection's staleness at about 120 s from the backend read (see "Cache freshness versus revocation"; the 120 s cap stays, R4). Any public-facing explanation uses the R6 wording only. Creator and profile pages are still on the 300 s data cache (a recorded defect), so this page must not copy them. Withdrawn note titles disappear from item pages on the next uncached fetch (item pages are fetched client-side directly from the backend, uncached).
- **Performance:** one cached server fetch per page view per 120 s per id (8 ids today); item pages are anonymous client fetches, ≤ 50 rows each.
- **Testing:**
  - `lib/api-*.test.ts` request-shape tests for the two new client calls.
  - Page tests for 404 → `notFound` plus `noindex`, 5xx → error state, and a Goal with terms vs without.
  - A Vercel Preview verification pass against the real backend (as v0.179.0 Stage B did).
  - No production load test.
- **Acceptance (Product UX §5 and §9):**
  - **Sharing and not-found:** a shared URL opens the exact collection anonymously, and a visitor can open its publicly readable notes without signing in. A withdrawn set renders the generic not-found shell with `noindex, nofollow`, and its status is whatever the spike recorded. No status is claimed beyond that.
  - **Progressive loading:** CELE's first paint fetches no item titles. No Subject Plan auto-expands, and nothing fetches every page.
  - **Distinct loading states:** the page-level outline load (server) and a row's titles loading (an inline indicator inside that row) are distinguishable. "Show more notes" shows its own pending state.
  - **Empty Subject Plan:** a published Subject Plan with no currently visible notes says so in that row ("No notes to show here right now"). It never implies the whole collection is empty, and the collection-level count stays correct.
  - **Counts:** the header totals, the row totals and the loaded titles agree (2a parity), subject only to the cache bound.
  - **Accessible disclosure:** each Subject Plan row is a `button` with `aria-expanded` and `aria-controls`, works from the keyboard (Enter and Space), and moves focus sensibly after "Show more". A screen reader announces the expanded or collapsed state.
  - **Adoption:** no Add flash while the lookup is pending. "Open my copy" shows when a copy exists. An adoption failure shows C4.4.
  - **Share:** copy link works signed out, with a calm confirmation.
  - **Metadata:** checked separately from status (see the spike). Canonical, OG, title and robots are correct for found, not-found and failure.
  - **Backend safety:** one cached outline read per id per ≤ 120 s per process; item pages ≤ 50 rows; no N+1, no warmers, no polling, no production load test.
- **Dependencies:** 2a, 0, **and the HTTP-status spike result** (R1 is settled: the spike decides only whether the real page can return 404; otherwise 200 plus the generic `noindex, nofollow` shell). **Complexity:** M. **Risk tier:** one scoped cold agent (a new anonymous surface plus cache).
- **Rollout:** Vercel, deployed separately from 2a (≥ 24 h apart). **Rollback:** a Vercel rollback; the backend endpoints stay unused.

#### HTTP status and not-found strategy (pre-implementation spike)

**Why (CONFIRMED):**
- `frontend/app/layout.tsx:88-96` places `<Suspense fallback={null}>` around every route.
- `/exam/[slug]`'s `notFound()` measured HTTP 200 on the v0.179.0 Preview, but that route also has `loading.tsx`, so the measurement does not isolate the root Suspense.
- No route without `loading.tsx` has been measured.
- `frontend/app/` has no `not-found.tsx` or `global-error.tsx`, and `frontend/` has no `proxy.ts` or `middleware.ts`.
- No test or script asserts a rendered page's real status.

Next 16.1.6, React 19.2.3 (`frontend/package.json`).

**Spike (to run on a throw-away branch and a Vercel Preview; the owner or the Release Implementor runs it, never against production):**
1. Build a throw-away route set to `force-dynamic`, with **no `loading.tsx` in it or any parent segment** and no Suspense of its own. Its server component awaits an existence check (a stub that returns null for one id, throws for another and returns data for a third), then either calls `notFound()`, lets the throw reach `error.tsx`, or renders.
2. For each of the three ids, run:
   `curl -s -o /dev/null -w '%{http_code}\n' -H 'x-vercel-protection-bypass: <secret>' <preview>/<route>/<id>`,
   then `curl -s -D - …` for the headers and the body.
   Alternatively, reuse `frontend/preview-verify/run.mjs` from the unmerged `verify/v0179-cache` branch (`redirect:'manual'`, `cache:'no-store'`).
3. **Record** for each id:
   - the status;
   - `Cache-Control` and `x-vercel-cache`;
   - from the HTML: `<title>`, `<meta name="robots">`, the canonical link and the `og:*` tags;
   - the visible heading.
   Check status and head separately: curl for the status; an HTML parse or a browser for the head and body.
4. **Pass** if not-found returns 404 and failure returns 5xx or a documented 200 with `noindex`. **Otherwise** record 200 and apply R1.

**Superseded 2026-11-11:** the full protocol, variants, commands and results template are in
`docs/claude-plans/2026-11-11-public-collection-http-status-spike-protocol.md`; steps 1-4 above are kept as
the record.

**Outcome rule (SETTLED R1, owner 2026-11-11):** run the Preview spike first. If a genuine 404 is feasible for
the real route structure under every tested user agent, use it. If not, **accept HTTP 200 with a generic
`noindex, nofollow` shell**, identical for never-existed and withdrawn, and make **no crawler-status claim**, as
`v0.180.0` did. The former fallbacks (b) a `proxy.ts`/middleware existence check and (c) narrowing the root
layout Suspense are **removed as options and are not to be introduced**: no middleware and no change to the
global layout just to force a 404.

**Structural rules for the real route:**
- No `loading.tsx` on `/explore/collections/[id]` or any parent (`explore/` has none today, CONFIRMED). This also avoids the v0.179.0 problem where a prefetch ran `generateMetadata`.
- No Suspense above the existence decision. Any in-page Suspense sits below it.
- The loader returns the result, or `null` on a backend 404 or 403, and throws on any other non-OK (`fetchPublicNote`'s pattern, `lib/server-public-notes.ts:33-44`).
- `generateMetadata` uses the same cached loader, so the head and body agree. `opengraph-image`, if added, does its own live fetch.
- Note titles load client-side on expand.

### Phase 1b: Simplified card with a single primary CTA (frontend)

- **Learner problem:** tall mobile cards with two competing actions and an inline preview that shows only 5 notes.
- **User stories:** as a mobile learner I can scan several cards per screen; as a learner, tapping the main button lets me look before I commit.
- **Reuse:** `PublicStudyPlanCard` (modified in place, not a new card), the existing adoption handlers and analytics events.
- **Backend, DB:** none. **Privacy:** none (the same summary data, less of it shown). **Performance:** removing the inline preview removes the per-click `GET /collections/public/{id}` (the full flattened payload). That is a net DB saving, and the main performance argument for this phase.
- **Scope:** the card shows the type label, title (clamped), a short description, counts, the Official badge, the program, and a year only if D4 lands a structured year (otherwise the year stays in the title). Primary CTA: **View study plan** / **View review set** → the public page. The secondary action, **Add study plan** / **Add review set** (or **Open my copy** when adopted), stays (D5, R2). The inline preview accordion is removed **only after** the page exists. The `?highlight=` deep link and the public-note "Official" callout point to the new page.
- **Exclusions:** no change to adoption code; no removal of the adoption count rule.
- **Testing:** card tests; the analytics event names that `*_PREVIEWED` dashboards depend on are kept or mapped.
- **Gate before finalizing scope (Product UX §4):** a read-only 60-day analytics comparison (measure-first item 3).
  - Compare direct adoption without a preview, preview then adoption, and preview without adoption.
  - Split anonymous from authenticated, and separate bots where that is possible.
  - Remember that `*_PREVIEWED` / `*_ADOPT_CLICKED` fire only with `discoverySource` set (C7), so an untagged adopt is invisible to the split.
  - If direct adoption is used materially, keep **Add study plan** / **Add review set** on the card. If most learners inspect first, Product UX reconsiders the secondary action.
  - Never remove the shortcut on visual preference alone. Adoption behaviour, provenance and the event semantics stay intact.
- **Acceptance:** a mobile card height reduction (to be measured in Preview); no click silently adopts; the event mapping lets the 60-day funnel be re-run after the change.
- **Dependencies:** 2b, and the 60-day gate. **Complexity:** S. **Risk tier:** `advisor()` only. **Rollout:** Vercel. **Rollback:** a Vercel one-step rollback; the page from 2b keeps working either way.

### Phase 3: Community Collections (gated, recommend not starting)

**Why not now (CONFIRMED and AUDIT facts):**
- "Official" is derived, partly from `visibility = PUBLIC`.
- Cards hard-code the Official pill (Card:195).
- The two Official predicates disagree (`PublicLibraryRepositoryImpl:47` vs `NoteCollectionItemRepository:63`).
- Several surfaces read "public collection" to mean "official": the Explore list, the exam hub, the public-note callout, and `PostSessionNextStepService` routing to Explore (AUDIT).
- There is no report or takedown, no moderation of collection title or description (AUDIT: `ContentModerationService` covers notes, study packs and topics only), and no publish rate limit.
- Purge hard-deletes a user's PUBLIC collections (CONFIRMED AccountPurgeService:193-200), and there is no owner-status filter during the 30-day pending window (AUDIT).
- No owner decision on main allows learner publishing (AUDIT B4).

**Prerequisites (each its own reviewed item):**
- **P3-1 Explicit Official designation:** separate "official" from "public": either a derived predicate that requires an ADMIN owner on *every* read path (and is applied in `listPublic`), or a proposed nullable `curated_by` / `official_at` column (a migration, owner-approved). Unify it with the note-level predicate (remove the hard-coded email path or make both use the same rule).
- **P3-2 Surface audit:** every surface that equates "public" with "official" filters to Official or labels Community (Explore groups, the exam hub, the public-note callout, post-session routing, adoption counts, notifications).
- **P3-3 Report and takedown:** a minimal report action plus an admin unpublish (which exists today as `/visibility`), and an audit log.
- **P3-4 Moderation:** run the existing `ContentModerationService` on collection title and description at publish time and on every edit while PUBLIC.
- **P3-5 Abuse bounds:** a per-user cap on PUBLIC collections and a publish rate limit. This needs owner approval, because the current anti-drift bans new rate limits.
- **P3-6 Lifecycle:** decide what purge and pending-deletion do to community collections (recommend: set PRIVATE at deletion request, hard-delete at purge), and the attribution after purge.
- **Product UX §8 (binding):**
  - The deferral and the six prerequisite groups stand. No community publishing UI or backend in the current phases.
  - **Official and Community must never be visually or semantically interchangeable.**
  - The future MVP is small and reversible:
    - verified, onboarded creators only;
    - leaf collections only;
    - the creator's own already-public notes only;
    - no automatic change to note visibility;
    - an explicit Community designation and clear creator attribution;
    - report and unpublish;
    - a feature flag that is off by default, with a controlled rollout;
    - an owner-approved rollback procedure.
- **MVP once the gates pass:** a feature flag via `@ConditionalOnProperty` or a properties-driven enable (the repo pattern; no generic flag framework exists). Only verified-email accounts with a completed onboarding. A collection over the creator's own PUBLIC notes only. Leaf collections only (no Goals) at first. A Community badge and "Shared by {name}". Listed in a separate Explore group below Official, or reachable by URL only for a first stage. Publishing never changes note visibility (the current UI "Make N public" button is admin-only convenience; for community, a private note must block publication with a clear message, not offer a bulk flip).
- **User stories:** as a teacher I share my unit plan publicly so students can adopt it; as a learner I can tell community plans from Official ones; as an admin I can take down a reported plan within minutes.
- **Exclusions (MVP):** Goals, notifications to adopters, adoption counts on community cards, sitemap inclusion, featured placement, any change to note visibility on publish, and any monetization.
- **Testing:** `MockMvc` 403 for ROLE_USER while the flag is off and 200 while on; moderation rejection on title and description; real-PostgreSQL tests that a community collection never matches any Official predicate, on every surface listed in P3-2; purge and pending-deletion tests; the full pressure test.
- **Acceptance:** with the flag off, behaviour is byte-identical to today; with it on, no community collection appears with an Official badge or in an Official-only surface; after a takedown the page stops resolving within ≤ 120 s (generic not-found shell, `noindex`; status per the spike).
- **Rollout:** the flag starts off in production; an allowlisted cohort (e.g. TEACHER accounts) first, then wider. The flag is env-driven, so enabling it is an owner env save that redeploys Render, and is subject to the same deploy rules.
- **Rollback:** flag off, plus an owner-run bulk set of community collections to PRIVATE (an owner write; the `.sql` is prepared in advance).
- **Complexity:** L. **Risk tier:** **full pressure test** (a permission substrate and first-of-kind user-generated public content).

### Privacy and fail-closed design (brief section 4.5)

| Lifecycle case | Today | After this plan |
|---|---|---|
| Publish with private notes | Backend rejects (`validateLeafPublishable`, Svc:2052-2065); the admin UI offers "Make N public" then Publish (AUDIT) | Unchanged for Official. Community: block, no bulk flip (P3) |
| A note becomes private after publication | `getPublic` hides it at read time (CONFIRMED Svc:3825-3833); `listPublic` counts still include it (CONFIRMED) | Counts also filtered (Phase 0.4); the new endpoints filter at read time; the page shows it gone within ≤ 120 s for the outline count and immediately on item pages |
| Note removed from a collection | Item row deleted; shown at the next read (AUDIT) | Same |
| Note added to a published collection | Hidden until publish-update (`published_at` null) (AUDIT) | Same; the new endpoints honour `published_at` |
| Unpublish a leaf or root | 404 on read and adopt (CONFIRMED `findByIdAndVisibility`) | Same; the page renders the generic not-found shell plus `noindex` within about 120 s (status per the spike) |
| Unpublish a Goal | **Children stay PUBLIC and reachable by id (fail open)** | Ancestor check plus cascade (Phase 0.1-0.2) |
| Unpublish one child inside a PUBLIC Goal | Still shown and adopted via the Goal (selected by `published_at`) (CONFIRMED Svc:1158) | **E4, MEASURED: 9 such sections already exist in live sets; recommend do NOT tighten** (it would strip 7 of ALE's 8 sections) |
| Delete a collection | Row and items deleted; adopted copies detach (AUDIT, V76 no FK CONFIRMED) | Same |
| Delete a Goal | **Children become roots; PUBLIC children appear as Official cards (fail open)** | Children set PRIVATE before delete (Phase 0.3) |
| Creator account deletion (pending) | Public content stays live 30 days (AUDIT) | Official: unchanged (an admin account). Community: P3-6 |
| Account purge | All collections hard-deleted (CONFIRMED); public notes kept as "Deleted user" (AUDIT) | Page 404s. Community: P3-6 |
| Ownership change | No code path (AUDIT) | Out of scope |
| Stale caches | Exam hub ≤ 120 s; no backend `Cache-Control` | Public page ≤ 120 s from the backend read, per process, hard TTL, never stale; item pages uncached; third-party caches uncontrollable (see "Cache freshness versus revocation") |
| Public API authorization | GET `/collections/public/**` permitAll; writes authenticated (CONFIRMED) | The new GETs sit under the same matcher; no viewer state in responses |
| Search indexing after withdrawal | Collections not indexed today | `notFound` plus `noindex` per page; no robots rule; not in the sitemap until D7 |

**Invariant to pin in a test (PROPOSAL):** a collection's published status never overrides a note's current visibility, and no public collection response contains a field derived from a row the caller could not read directly.

#### Publication contract: parent, stamped child, note

Three cases. They must not be collapsed into one rule.

| Case | Contract | Evidence |
|---|---|---|
| (i) **Withdrawn, private or deleted PARENT** | Blocks all public access to the parent and to every child reached through it, on reads, adoption and the new endpoints | Phase 0 items 1-3 (PROPOSAL); today it fails open (CONFIRMED Svc:762-779, V83:2) |
| (ii) **PRIVATE-but-published-stamped CHILD under a PUBLIC Goal** | **Included.** It is shown, counted and adopted through the Goal, because the existing Goal contract selects children by `published_at` (CONFIRMED Svc:1158, :768-769). Requested **by its own id**, it 404s, since `getPublic` requires the collection itself PUBLIC. So the public page shows such sections inline under the root and never links to a per-child URL | E4. 9 live sections: ALE 7, PNLE 2 (read-only count, planner, 2026-11-10) |
| (iii) **Private or withdrawn NOTE** in any published collection | **Never publicly readable** through membership. It is filtered at read time from every response, and the note viewer re-checks visibility on each request | CONFIRMED Svc:3833; `NoteService.getPublicById:1150` |

**Do not tighten (ii).** It would remove 7 of ALE's 8 sections (46 adopters) and 2 of PNLE's 9 (20 adopters).

**⚠️ DORMANT (owner R5, 2026-11-11): reconciliation is deferred indefinitely unless a concrete reason appears.**
The existing published-stamp contract is preserved, particularly for ALE and PNLE. No production data write, and
no unpublish/republish of these sets as a cleanup shortcut. **Guardrail: never unpublish and republish ALE or
PNLE.** Reason (CONFIRMED Svc:2068-2080 at `d5eaf383`): a publish calls `publishChildCollections`, which sets
every ordered child PUBLIC with no condition, so a round trip would flip the nine sections PUBLIC (exposing them
by their own id) as an unreviewed side effect; and on a re-flip `last_update_published_at` is already set
(Svc:1080-1085), so nothing else in the round trip is a reset either. The procedure below is kept only as the
record of what a future, separately approved reconciliation would look like.

**Reconciliation of the nine sections (DORMANT; kept as record):**
1. **Dry-run, read-only (Claude may run it):** a `SELECT` listing each of the 9 child ids with its root id and title, its own title, `visibility`, `published_at`, `last_update_published_at`, stamped item count, PUBLIC-note item count, and the count of adopted copies whose `source_plan_id` is the root.
2. **Before counts:** per root, the children by visibility, and the `getPublic` item count.
3. **The write:** an `UPDATE … SET visibility = 'PUBLIC'` keyed by the 9 explicit ids. It goes into a `docs/claude-plans/*.sql` file with the expected row count (9) and is **run only by the owner**, never by Claude.
4. **After counts:** these must equal the before counts for children shown and items shown. Only `visibility` changes, so nothing a learner sees should move.

**Timing:** none (R5: deferred indefinitely). **Note C8:** any unpublish then re-publish of ALE or PNLE runs `publishChildCollections` and performs this reconciliation implicitly, which is why the guardrail above forbids it.

#### Cache freshness versus revocation

Read-only verification, 2026-11-10, against `frontend/lib/catalog-cache.ts`. **Line drift at `d5eaf383`
(2026-11-11):** the failure backoff constant is now :16 (not :12), `catalogTtlMs` :145 (not :132),
`getCatalogCached` :168 (not :153); the behaviour described is unchanged (`Math.min` :148, hard cap :183,
backoff throw :210-213, `shouldCache` :242, failure record :251-258).

**CONFIRMED:**
- **The cap:** the page TTL cap is `120_000` ms (:2) and the failure backoff is 15 s (:12). `catalogTtlMs` (:132) returns `Math.min(parsed, cap)`, so `NOTELIB_CATALOG_PAGE_TTL_MS` can only lower the TTL.
- **When an entry is fresh:** `getCatalogCached` (:153) stamps `startedAt` at the **start** of the load. A hit needs `now - startedAt < ttl`, so a slow fetch shortens the window and never extends it. A value is stored only if `now - startedAt` is under `min(deadline, ttl)` (90 s for pages). Followers share the leader's promise.
- **On failure:** the entry is dropped and the key rejects for 15 s. There is no stale fallback.
- **The cap must be passed:** the hard cap is opt-in (`hardCapMs`, as `server-public-study-plans.ts:45` passes it).
- **Scope of the cache:** caches are per-process `Map`s. The current loaders use `cache: "no-store"` (`server-public-notes.ts:36,56,157`; `server-public-study-plans.ts:15`). Others use `next:{revalidate:300}` (`server-public-notes.ts:77,290`; `server-public-profiles.ts:20,45,53`), which a collection loader must not copy.
- **Nothing else caches:** no route-segment `revalidate`, no `vercel.json`, no middleware or proxy, an empty `next.config.ts`, no `Cache-Control` in the frontend source. There is no `revalidateTag`, `revalidatePath`, `unstable_cache` or `use cache`, and no `app/api` route. The backend sets no cache headers.

**ASSUMPTION:**
- Next 16.1.6 dynamic responses are `private, no-store`, and Spring Security's defaults are `no-cache, no-store`.
- The Vercel instance count and reuse are unknown.
- Crawler, link-preview and search-engine caches are outside the repo.

| Product UX question | Answer |
|---|---|
| (1) Maximum window a withdrawn collection's metadata stays visible | **About 120 s from the backend read** (the last serve is at most `t_read + 120 s`), applying to title, description and structure alike. It is per instance, so different visitors see the withdrawal at any point from 0 to 120 s. A sitemap listing collections could name the URL for up to **1 h** (sitemap cap), but the page itself stops resolving within 120 s. CONFIRMED (code) |
| (2) Does note-content access always recheck visibility? | **Yes.** `fetchPublicNote` is a direct `no-store` fetch outside `getCatalogCached`, and the backend checks `visibility = PUBLIC` per request (`getPublicById:1150`). `getPublicBySeoPath` resolves the id with the predicate, then reloads without it (:1163-1175), a millisecond race. CONFIRMED |
| (3) Could stale metadata expose a withdrawn collection? | Its title, description and outline can, for up to about 120 s, and so can `generateMetadata` and OG if they share the loader. A click on a stale title then hits a fresh check and fails closed. The existing "More from subject" rails show a now-private note's title for up to 120 s too (pre-existing, note-side). Stale metadata **never** authorizes note content. CONFIRMED |
| (4) Can any cache or CDN extend 120 s? | Nothing in the repo does. The platform (Vercel) is ASSUMPTION. Third-party caches can keep a title for hours to weeks and cannot be purged from the repo |
| (5) Is immediate invalidation feasible? | **Not cheaply.** It needs an authenticated backend-to-frontend webhook plus `revalidateTag` over the Data Cache, **replacing** `getCatalogCached` for these fetches. That is disproportionate, and it still cannot reach third-party caches. The cheap lever was lowering `NOTELIB_CATALOG_PAGE_TTL_MS`; **CLOSED by R4 (2026-11-11): keep the 120 s cap, no environment change, no additional backend load** |

Explore's client fetches (`api.ts:5603-5622`) are plain `fetch` with no memo, and there is no ETag, so withdrawal shows on the next fetch. Content already on screen stays until the page is refreshed.

**Wording rule (SETTLED R6, 2026-11-11):** use "within about two minutes" only when a public-facing explanation
is necessary; always qualify that it applies to NoteLib's own systems, not to external caches or search engines;
never promise immediate removal.

**What we can honestly promise (for any public copy):** within NoteLib's own systems, a withdrawn collection's title, description and structure stop being served by our public pages within about two minutes, plus response time. The cap is enforced in code, and the env var can only shorten it. Note content is checked against current visibility on every request, so a stale outline can never open a private note. We promise nothing about copies held by search engines, link previews, screenshots or browsers. **Do not promise immediate withdrawal.**

### Performance and DB-safety design

- **No new unbounded anonymous reads.** The outline is bounded by a child cap. Item pages are bounded by `size ≤ 50` and are id-first. Goals have no flattened-items endpoint.
- **`listPublic` stays as is** (8 roots, ~2,393 item rows per call) for now. It is a known whole-catalog read, called client-side from Explore (twice for signed-in users), `/collections/published`, the dashboard and the public library (AUDIT). **Do not grow it.** Phase 0.4's note-visibility filter may add one batched note-visibility lookup over ids it already holds (ASSUMPTION: `readyCountsForNoteIds` does not already load note visibility; if it does, the filter adds no query). Either way, the extra cost is one bounded `IN` query, not per-row. Paging or summarizing `listPublic` is a separate item, recommended only after measurement shows it matters as the catalog grows (a 20+ root catalog, or Year 2 roots doubling the computing item count).
- **Reuse `getCatalogCached`** for server-side reads: TTL ≤ 120 s, no stale-while-revalidate, no speculative warming, failure backoff unchanged.
- **Crawler shape:** with no sitemap entry and item pages fetched on interaction, crawlers that do not run JS see only the outline. JS-running bots (~97% of Web Analytics, per the memory reference) can still click. Size that before adding to the sitemap.
- **Diagnostics:** add an allowlisted `collection` walk value, or accept `other`; record which before 2b.
- **Must be MEASURED before enabling 2b:** the current request rate on `/collections/public` and `/collections/public/{id}` and their p95 statement time (read-only `pg_stat_statements` / Render metrics); DB memory headroom at the time; and how many distinct collection ids are previewed per day.

---

## E. Open decisions (material only)

**Settled by Product UX, 2026-11-10:**
- E1/D3: tab label (option a), plus content-based card labels.
- D5: direct adoption kept as a secondary action, pending the 60-day gate.
- E3/D6: route.
- E5/D7: indexable, sitemap deferred.
- D8 and Phase 3: deferred.
- D1/E7: the Phase 0 design (fail-closed read, unpublish cascade, children set PRIVATE on Goal delete).
- E2/D4: option (a) plus (d), no structured year.
- E4/D2b: do not tighten.

The option text below is kept as the record.

### Owner decisions R1-R6 (SETTLED 2026-11-11, binding; planning approval only)

| # | Decision | Final decision (owner, 2026-11-11) | Applied in |
|---|---|---|---|
| R1 | Not-found strategy | **SETTLED: option (a).** Run the Preview spike first. If a genuine 404 is not feasible, accept HTTP 200 with a generic `noindex, nofollow` shell. Do **not** introduce middleware or modify the global layout just to force a 404. Options (b) and (c) are removed | C1; journey C3.2; Phase 2b spike section; spike protocol artifact |
| R2 | Public card CTA | **SETTLED with a refinement.** The noun comes from the collection's PURPOSE, not the viewer's profile. The adoption action is **"Add study plan" / "Add review set"** (not "Start this …": "Start" suggests a study session while the action creates a private copy). Adoption behaviour unchanged. Once Phase 2b exists the PRIMARY card action is **"View study plan" / "View review set"**, adoption SECONDARY. Adopted state: "Open my copy" (PROPOSAL, consistent with the page) | C3; Phase 1a item 2; journeys C1/C4; Phases 2b and 1b |
| R3 | Card label when `learnerLevel` is null | **SETTLED: "Study Plan"** (and so "Add study plan") | Phase 1a label table |
| R4 | Cache TTL | **SETTLED: keep the existing 120 s cap.** No environment change, no additional backend load. The TTL lever is closed (the variable is shared with the note and study-plan loaders, `server-public-notes.ts:52,141`, `server-public-study-plans.ts:36`, not re-verified 2026-11-11) | "Cache freshness versus revocation" (5); Phase 2b privacy |
| R5 | Nine legacy PRIVATE-but-stamped sections | **SETTLED: defer reconciliation INDEFINITELY** unless a concrete reason appears. Preserve the published-stamp contract, particularly ALE and PNLE. No production data writes; no unpublish/republish of these sets as a cleanup shortcut | C8; "Publication contract" (DORMANT); Phase 0 |
| R6 | Freshness wording | **SETTLED:** "within about two minutes" only when a public-facing explanation is necessary; qualify that it applies to NoteLib's own systems, not external caches or search engines; never promise immediate removal | "Cache freshness versus revocation"; journey C4.3; Phase 2b |

**Also confirmed by the owner, 2026-11-11:** phase order 0 → 1a → 2a → 2b → 1b, Phase 3 deferred; Explore =
discovery, the public collection page = evaluation and sharing, personal collections = studying; community
publishing out of current scope; no structured academic-year metadata and no migrations now; existing
privacy/publication safeguards preserved, including the nine PRIVATE-but-stamped sections.

- **E1 (D3) Tab label.** Options: (a) keep `Official {plural}` (recommended); (b) "Collections" (rejected: the null-profile rule and the PROFESSIONAL nav collision); (c) "Study Plans & Review Sets" (accurate but long on mobile, and wrong for TEACHER). Recommend (a) plus a profile-aware description plus per-card type labels.
- **E2 (D4) Academic year.** Options:
  - (a) A **title convention** normalized by the owner, display-only. Ordering by title within a program group works while years are "Year 1..Year 4"; it is display order, not data, and recommendation tier 1 is not attempted.
  - (b) A nullable `sequence_order SMALLINT` on roots for ordering within a program group: the smallest structured change. It **sits close to ADR-003's rejected `journey_key`/`journey_order`**, so it needs an ADR-003 amendment that argues it orders cards within a discovery group and builds no Degree surface.
  - (c) Reuse `sibling_position` on roots: rejected. It already orders siblings and owner lists (`NoteCollectionRepository:29-30`, CONFIRMED), so overloading it is the kind of semantic drift ADR-003 B warns against.
  - (d) Goal grouping by `course_program`, already possible, and it is the grouping key in all options.
  - **Recommend (a) plus (d) now; (b) only if the learner profile ever gains a structured year** (which tier 1 also needs). A year on plans alone cannot personalize anything. The profile half is a separate product decision. Do not infer it from adoptions.
- **E3 (D6) URL.** `/explore/collections/{id}` (under the discovery IA, not confusable with the owner route `/collections/{id}`). A slug in the URL is optional later; the id is the stable key, since titles are about to change.
- **E4 Child visibility inside a public Goal. MEASURED 2026-11-10 (read-only, planner): the gap is real.** Children of the 8 PUBLIC Official roots: CELE 10/10 PUBLIC, BSCS 10/10, BSIT 10/10, BSIS 10/10, LET 6/6, CPALE 6/6, **PNLE 7 PUBLIC and 2 PRIVATE of 9, ALE 1 PUBLIC and 7 PRIVATE of 8**; all 9 PRIVATE children carry `published_at` (so 69 children, 60 PUBLIC, 9 private-but-stamped), and none are PUBLIC-but-unstamped. Because every Goal path selects children by `published_at` only (CONFIRMED Svc:1158, `getPublic` :768), **ALE's public preview and adoption already include 7 sections whose own `visibility` is PRIVATE**, and ALE has 46 adopters, PNLE 20. Likely cause (INFERENCE, not verified): V141 stamped `published_at` on every pre-existing row regardless of visibility (Svc:1062-1070 comment), and these legacy sections were never flipped PUBLIC. **Consequences:** (1) **Do NOT tighten the Goal paths to require child PUBLIC**: it would silently remove 7 of ALE's 8 sections and 2 of PNLE's 9 from live Official sets. (2) The Phase 0 ancestor check is the right shape because it keys on the **parent** being PUBLIC, not the child. (3) Phase 2a/2b must build the outline from the same `published_at` rule `getPublic` uses, or ALE's page would show 1 section while its adopters got 8. (4) A per-child public URL would 404 for these 9 sections (`getPublic` requires the child itself PUBLIC), so the public page should present sections inline under the root and not link to per-child pages. (5) The cleanup (an owner-run write flipping these 9 sections PUBLIC so `visibility` matches reality, written as a `docs/claude-plans/*.sql` for the owner, never run by Claude) is **optional hygiene, a separate owner decision**, and only after Phase 0 so the cascade semantics are consistent. Not a blocker for any phase if (1) to (4) are followed. **Superseded by R5 (2026-11-11): (5) is DORMANT, deferred indefinitely; never unpublish and republish ALE or PNLE.**
- **E5 (D7) Indexing.** Recommend indexable pages, with the sitemap deferred until the 2b request rate is measured; then add 8-20 URLs under the 1 h sitemap cache (bounded, no walk).
- **E6 Attribution text.** Recommend "Curated by NoteLib" for Official, never the admin's personal name.
- **E7 Goal delete semantics (D1).** Recommend children set PRIVATE, not deleted.

---

## F. Release coordination

**State re-read 2026-11-11 (CONFIRMED `CLAUDE.md:39-45` on this worktree; a snapshot, re-read before
repeating):** `v0.180.0` is **merged (#1575) and DEPLOYED** (T0 2026-11-11T10:59:58Z); stabilization pending:
+24 h read due 2026-11-12T10:59:58Z, +72 h due 2026-11-14T10:59:58Z; **every merge to `main` is held until the
+24 h read** (a merge rebuilds Vercel production and removes the one-step rollback target). `v0.181.0`
"Affordable NoteLib" is IN PROGRESS on `releases/v0.181.0`: frontend only, two separately authorized deployments
(the frontend merge, then the owner's Render env save at least 24 h later); its anti-drift says no backend code
change and no Public Library or Explore redesign. A non-deploy Postgres kill occurred 2026-11-11T07:48:46Z; the
owner knowingly waived the 24 h kill gate for `v0.180.0`, and **no deploy may rely on that waiver again**.
Render deploys on any pushed commit that changes `backend/` against its first parent (inference, not documented
Render behaviour), so a frontend-only release needs a squash merge and the per-commit range check.

**In force:** every deploy needs separate explicit owner approval and the section-5 checklist (verified backup,
watch and stop conditions, attended, no deploy within 24 h of a non-deploy kill, off-peak per the deploy plans);
hard stops in `docs/claude-plans/2026-11-04-v0179-stage-b-deploy-plan.md` section 5 and
`docs/claude-plans/2026-11-10-v0180-deploy-plan.md` section 6 (CONFIRMED: any signal 9 after T0; database memory
above 260 MB of 268.4 MB at two consecutive reads); an env save that redeploys counts as a deploy; no load test
against production; Claude never merges, deploys or edits env vars.

**Placement (PROPOSAL, no numbers):**
1. **Nothing merges to `main` before `v0.180.0`'s +24 h read passes.** `v0.180.0` is deployed; it is no longer
   "before it deploys".
2. **Phase 0** (backend only, so it redeploys Render) is **its own deploy**, at least 24 h from any other deploy,
   **including `v0.181.0`'s frontend merge and its env save**, and at least 24 h after any non-deploy kill (no
   waiver). It must not ride with `v0.181.0` (whose anti-drift forbids backend change) nor with the
   deadline-sensitive interactive LLM migration. **It is a two-platform event:** the merge also rebuilds Vercel
   production with unchanged frontend code and moves the one-step rollback target, so it must not merge while
   `v0.181.0`'s frontend could still need a one-step Vercel rollback. Natural slots: after `v0.181.0`'s env save has passed its own
   read, or between `v0.180.0`'s stabilization and `v0.181.0`'s first deploy if the owner prefers; the choice is
   the owner's.
3. **Phase 1a** (frontend copy and grouping) follows Phase 0 in the confirmed order, never in the same Vercel
   deploy as a price change (separate attribution), and not inside `v0.181.0`.
4. **Phases 2a then 2b:** after the interactive LLM migration has deployed and stabilized (deadline-sensitive: checkpoint 2026-12-15, shutdown 2027-01-14), and independent of the Challenge Quiz quality release, which touches different code. 2a and 2b are separate deploys ≥ 24 h apart.
5. **Phase 1b** after 2b passes its read.
6. **Phase 3:** not scheduled; the gates first.
7. **Curriculum interplay:** the BSIS and Year 2 work changes root titles and adds roots. Do D4's title normalization in that owner-run curriculum write, not as a separate one.

### Proposed Backlog Index row text (for the Release Implementor; not written here)

> **Study Plan Discovery & Community Collections**: PROPOSAL, Product UX direction approved with refinements 2026-11-10; owner R1-R6 FINAL 2026-11-11 (planning only), `docs/claude-plans/2026-11-10-study-plan-discovery-and-community-collections-plan.md`, with `2026-11-11-collection-publication-boundary-phase0-scope.md` and `2026-11-11-public-collection-http-status-spike-protocol.md`. 2b needs the HTTP-status Preview spike first (R1: if no genuine 404, 200 plus a generic `noindex, nofollow` shell; no middleware, no layout change). Card adoption action "Add study plan / Add review set" (R2). Nine-section reconciliation DORMANT; never unpublish and republish ALE or PNLE (R5). Phases 0 (fail-closed fix: a Goal unpublish or delete leaves PUBLIC children reachable or listed as Official, CONFIRMED Svc:762-779, 1057-1090, V83:2), 1a (Explore copy, type labels, program grouping; frontend), 2a (bounded outline plus paged titles API), 2b (public page `/explore/collections/{id}`), 1b (simplified card), 3 (community, gated on 6 prerequisites). Gate: owner approval per phase; no merge to `main` before `v0.180.0`'s +24 h read; Phase 0 is its own backend deploy, ≥ 24 h from any other deploy (including `v0.181.0`'s env save), not riding with the LLM migration; E4 measured 2026-11-10 (9 PRIVATE-but-stamped sections in live ALE/PNLE sets; do not tighten the stamp-based rule). Status: not started. Last reviewed: 2026-11-11.

### What to measure first (read-only)

1. (a) DONE 2026-11-10, see E4 (9 private-but-stamped children: ALE 7, PNLE 2). (b) PUBLIC child rows whose parent is not PUBLIC: **0 (snapshot 2026-11-10)**: the 60 PUBLIC ADMIN children all sit under the 8 PUBLIC roots (10+10+10+10+6+6+7+1). (c) PUBLIC roots that are not the 8 known Official roots: **0 (snapshot)**: no USER-owned PUBLIC collection exists. So today's fail-open paths are **latent, not live**; re-run (b) and (c) at Phase 0 release time because a snapshot decays.
2. Daily request counts and p95 for `GET /collections/public` and `/collections/public/{id}` from the diagnostics recorder / `pg_stat_statements`.
3. **The Phase 1b gate (Product UX §4):** `EXPLORE_OFFICIAL_SET_PREVIEWED` vs `EXPLORE_OFFICIAL_SET_ADOPT_CLICKED` (and the exam-hub and public-note variants) over 60 days.
   - Compare direct adoption without a preview, preview then adoption, and preview without adoption.
   - Split anonymous from authenticated, and separate bots where that is possible (the Web Analytics caveat).
   - Count adoptions that carry no `discoverySource` separately (C7).
   - Read-only `SELECT`s over the analytics events table.
4. DONE 2026-11-10: items per published child Subject Plan across the 8 Official roots: 69 children, min 8, median 37, p95 about 75, max 90, 2,393 items in total, so a single page of up to 100 titles per section covers every current section; keep the paging contract for growth.
5. DONE 2026-11-10 (`pg_indexes`): `note_collection_items` has indexes on `id`, unique `(collection_id, note_id)`, `(collection_id)` and `(note_id)`; **there is no `(collection_id, position)` index**. For at most 90 rows per section the sort is trivial, so no migration is needed for 2a's page size; revisit only if sections grow into the hundreds.
6. DB memory headroom at the time of each proposed deploy.
7. **The HTTP-status spike** (Phase 2b): the status, head and body for found, not-found and backend failure, on a Preview route with no `loading.tsx`. Not production.

## Smallest sensible next planning step (2026-11-10; DONE 2026-11-11, see the next section)

Produce two planning artifacts. Neither is implementation.
1. **An implementation-ready Phase 0 scope:**
   - the exact methods and the falsifiable claims for the cold agent;
   - the owner-run read-only pre-checks (measure-first 1(b) and 1(c), re-run at release time), written as a `docs/claude-plans/*.sql` read file;
   - the stamp-contract tests named above.
2. **The HTTP-status spike protocol**, written so the owner or the Release Implementor can run it on a Preview, plus a results template.

Then: owner approval to commit this plan and those two artifacts on a docs branch.

## Settled / To verify / Smallest next action (2026-11-11)

**Settled (owner, 2026-11-11):** R1-R6 (section E table); phase order 0 → 1a → 2a → 2b → 1b, Phase 3
deferred; Explore = discovery, public page = evaluation and sharing, personal collections = studying; no
community publishing, no structured year, no migrations now; the nine PRIVATE-but-stamped sections keep behaving
exactly as today.

**To verify (before the relevant brief; none blocks the artifacts):**
- Whether R2 also covers `DashboardStudyPlanSection` (dashboard and onboarding; it shows public plans with its
  own CTA, `dashboard-study-plan-section.tsx:361-362`). Product UX call.
- "Open my copy" as the adopted-state label (PROPOSAL); the "Sign in to adopt" label is untouched by R2.
- Phase 0 open items listed in the scope artifact, section 8 (including `inspectSourceUpdate`'s ancestor gate).
- `explore-page-client.test.tsx` and `server-public-*` line numbers carried from 2026-11-10 were not re-read.

**Artifacts:**
- `docs/claude-plans/2026-11-11-collection-publication-boundary-phase0-scope.md`: Phase 0, implementation-ready
  (methods, named tests, owner-run read-only pre-checks, deploy inputs).
- `docs/claude-plans/2026-11-11-public-collection-http-status-spike-protocol.md`: the R1 Preview spike,
  decision tree and results template.

**Smallest next action:** owner review of the two artifacts, then (separately) owner approval to commit the three
files on a docs branch. After that, an `advisor()` call and a Release Implementor Codex brief for Phase 0, only
once a release is kicked off for it.

**Nothing in this file authorizes implementation, a merge, a deploy, a migration or a production-data change.**
