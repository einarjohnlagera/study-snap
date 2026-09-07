# RELEASES.md - NoteLib

## v0.126.0 - Context Budget

**Status: In Progress** (kicked off 2026-09-07, base branch `releases/v0.126.0`, cut from `main` after `v0.125.0` merged and tagged)

Theme: the governing documents stop charging every session for history that is already recorded somewhere else.

**⚠️ IT RESUMES A DOCUMENTED CONVENTION, IT DOES NOT INVENT A TRIM — and that distinction is the whole safety argument.** `docs/archive/README.md` states the rationale verbatim: cold storage for content *"moved here specifically so it doesn't cost tokens to read on every pass through the live docs, while staying searchable (`git grep`)"*, and it is *"a MOVE, not a delete"*. It names the design for the two big files as **current + last few versions**. **`RELEASES.md` holds 116 sections (`v0.41.0` → `v0.125.0`).**

**⚠️ THE CADENCE LAPSED 85 RELEASES AGO, AND THE REASON IS MECHANICAL RATHER THAN NEGLECT: the 2026-07-10 pass at `v0.40.1` was a ONE-OFF WITH NO RECURRING STEP.** Neither `.claude/commands/kickoff.md` nor `signoff.md` mentions archiving, so nothing has triggered it since. **Item 5 is therefore not housekeeping — without it items 1-4 regrow.**

**MEASURED, NOT ESTIMATED (2026-09-07):** `CLAUDE.md` was **357,706 chars**, of which **one line was 322,329** — 63 chained `Previous:` blocks, ~80,000 tokens, **~90% of the file, on every session**. `AGENTS.md`'s preamble is **58,797 chars**. A live `/context` reading showed **memory files at 148.1k tokens, 14.8% of the window, before any code is read.**

### Planned Scope

**(1)** Archive `RELEASES.md` `v0.41.0` → `v0.120.x` into `docs/archive/RELEASES_ARCHIVE.md`, keeping current + last 5 live plus the one-line index, and update the archive header's version range. **(2)** Archive `ROADMAP.md`'s `(Released)` per-version retrospectives into `ROADMAP_ARCHIVE.md`, leaving one-line pointers — exactly the class that archive already holds. **(3)** `CLAUDE.md` line 39 → product description plus the OPEN release's anti-drift only. **(4)** `AGENTS.md` preamble → durable rules plus a pointer to `RELEASES.md`. **(5)** Patch `kickoff.md` to REPLACE rather than bump-and-prepend, and add an archiving step to `signoff.md`.

### Anti-drift

**⚠️ ITEMS 3 AND 4 ARE SAFE ONLY BECAUSE THE CONTENT IS DUPLICATED, AND THAT WAS VERIFIED ACROSS THE FULL `v0.37.0` → `v0.125.0` RANGE RATHER THAN SPOT-CHECKED.** All 72 versions named in `CLAUDE.md` were checked against `RELEASES.md`; two had no `## ` section and were chased individually — **`v0.37.0` is a rule citation** (*"locked since `v0.37.0`"*) that survives inside the rule itself, and **`v0.111.1` is a ruling about a version number that was REJECTED**, so it has no section, but its reasoning is duplicated in `RELEASES.md`'s `v0.110.1` section. **⚠️ A naive section-based check flags those two as data loss; they are not.**

**⚠️ ITEMS 1 AND 2 ARE MOVES — content preserved VERBATIM, exactly as the 2026-07-10 pass did.** **⚠️ ITEMS 3 AND 4 DELETE FROM THE DOCUMENTS THAT GOVERN THIS PROJECT'S ANTI-DRIFT:** `## Key conventions`, `## Task routing`, the PRODUCTION DATABASE READ-ONLY rule, the source-of-truth list, and **every** `AGENTS.md` Anti-Drift Rules entry MUST survive untouched. Only shipped-release narrative goes.

**⚠️ Do NOT prune the ROADMAP Backlog Index** (plan item 6, deliberately excluded) — it mixes LIVE `[CHECKPOINT — due …]` gates with closed rows, and kickoff step 8 requires every `docs/claude-plans/` and `docs/claude-findings/` file to keep a row. It needs judgment, not a script. **⚠️ Do NOT split `AGENTS.md`'s Required Product Architecture section** (plan item 7, held back) — this repo has **twice** recorded that a rule which stops being found stops being followed.

**⚠️ NO product code, NO migration, NO quota/entitlement/meter change, NO behaviour change of any kind.** **⚠️ `frontend/app/onboarding` STAYS FROZEN** (`[CHECKPOINT — due 2026-09-11]`, four days out) and **NO Learning Connections promotion before `2026-09-19`** — this release touches **no product surface at all**, which is precisely why it can run inside the twelve-read window.

### Verification

**A single `advisor()` call on the diff** — no permission substrate, no cross-user read, no money semantics, no migration, and by construction no behaviour change.

**⚠️ PRE-DECLARED GUARD, AND IT NAMES THE CHECK THAT PROVES NOTHING: a diff that merely looks smaller is not evidence.** The discriminating checks are **(a)** a `grep` for every surviving governing rule before and after, **(b)** that `RELEASES.md` + `RELEASES_ARCHIVE.md` together still contain every archived section **byte-for-byte**, and **(c)** a re-measurement of all four files against the figures above.

**Routing: CLAUDE CODE inline.**

### Shipped

**⚠️ ITEM 4 IS NOT SHIPPED, AND IT IS HELD ON EVIDENCE RATHER THAN DEFERRED FOR TIME.** The plan's own safety premise — *"items 3 and 4 are safe ONLY because the content is duplicated in `RELEASES.md`"* — **is true for item 3 and FALSE for item 4**, which the plan could not see because it spot-checked three releases rather than testing the premise per line.

**`CLAUDE.md`'s line 39 was pure release narrative. `AGENTS.md`'s preamble is not the same shape: it is durable RULES tagged with the version that introduced them.** Measured on the twelve candidate lines: **every one contains rule language**, and **line 53 alone carries 59 rule-words and 13 live `[CHECKPOINT — due …]` references**. Three rules were then tested directly against `RELEASES.md` plus its archive:

| Rule text in `AGENTS.md` | Copies in `RELEASES.md` + archive |
|---|---:|
| `only after `lockAndReadBirthYear`` | **0** |
| `Target Audience must never become a runtime` | 2 |
| `age-threshold sweeper, NOT a shutdown drain` | **0** |

**Two of three exist NOWHERE ELSE.** Trimming that preamble by pattern would have deleted rules — and live checkpoint pointers — from the document whose entire job is anti-drift, which is the failure this repo has recorded twice as *a rule that stops being found stops being followed.* **⚠️ The remaining work is hand-separating rule from narrative line by line; it is judgment, not a pass, and it wants its own release. `AGENTS.md` is therefore UNCHANGED at 211,638 chars.**

**⚠️ `ROADMAP.md` is still ~210k tokens, and that is the excluded item 6 rather than a miss:** its Backlog Index (~139k) and Current Release Baseline (~62k) were deliberately left alone.

**Item 3 landed in the kickoff commit itself, deliberately.** Prepending a `v0.126.0` block to line 39 in the normal way would have added another release's narrative to the very line this release exists to remove, then removed it hours later. **`CLAUDE.md`: 357,718 → 39,311 chars (~89k → ~9k tokens), matching the plan's prediction.** All twelve governing rules verified present after the replacement.

**Items 1, 2 and 5 shipped.** `RELEASES.md` **1,866,861 → 130,778 chars (~466k → ~32k tokens)** — 111 sections (`v0.41.0` → `v0.120.0`) moved to `docs/archive/RELEASES_ARCHIVE.md`, keeping current + last 5 live. `ROADMAP.md` **1,189,862 → 842,841** — 60 `(Released)` retrospectives moved to `ROADMAP_ARCHIVE.md`. **⚠️ BOTH VERIFIED BYTE-FOR-BYTE, WHICH IS THE GUARD THAT MATTERED: all 117 `RELEASES.md` sections and all 60 ROADMAP retrospectives are present and unchanged across live + archive.** The only two flagged diffs in each case were the index block relocating and a section-parser boundary artifact, both confirmed by inspection rather than assumed. **223 live `[CHECKPOINT]` rows and every live ROADMAP section survive.** Item 5 patched `kickoff.md` to REPLACE rather than prepend and added an archive step to `signoff.md`, **which is the half that stops items 1-3 regrowing** — its absence is why the 2026-07-10 pass never ran again.

## v0.125.0 - Bounded Reads

**Status: Released** (kicked off and signed off 2026-09-07, base branch `releases/v0.125.0`, cut from `main` after `v0.124.0` merged and tagged)

Theme: the last two places that read a whole collection when they needed a slice of one.

**⚠️ IT CLOSES AN ARC RATHER THAN OPENING ONE, AND THE ARC IS EXPENSIVE: THE SAME DEFECT SHAPE — an unbounded read over a growing catalog — HAS NOW CAUSED THREE PRODUCTION EVENTS** (the 2026-09-01 build failure, and the 2026-09-04 and 2026-09-05 outages), and `v0.119.1`, `v0.123.0` and `v0.124.0` each removed one instance. **These are the two the Backlog Index still names.**

**⚠️ SCOPE IS SET BY A DATED CLUSTER, NOT BY PREFERENCE — STATED SO IT IS NOT READ AS THE ROADMAP'S PRIORITY ORDER.** `[CHECKPOINT — due 2026-09-10]` is **THREE DAYS OUT** and `[CHECKPOINT — due 2026-09-11]` is **FOUR**, with twelve dated reads running to `2026-09-19`. **The onboarding freeze lifts when the `2026-09-11` READ IS TAKEN, not when the date passes**, and `2026-09-19` gates Learning Connections. **So the substantial onboarding work — catalog-first onboarding suggestions, the onboarding redesign, supporter onboarding — is BLOCKED FOR FOUR MORE DAYS and is next, not now.** This release is scoped to surfaces **none of the twelve measure.**

### Planned Scope

**(1) `OfficialChallengeQuizTemplateService.queueBackfill:81` stops loading the whole public catalog.** **⚠️ THE BACKLOG ROW UNDERSTATES IT, CORRECTED HERE BY READING THE CODE AT KICKOFF: IT IS THREE DEFECTS, NOT ONE.** It loads **every** public `NoteEntity` — **1,442 rows including `content`** — then runs **two queries PER NOTE** inside the loop: `isOfficialAuthor` calls `userRepository.findById` (`:104`) and the eligibility check calls `existsByUserIdAndStudyPackId`. **That is ~2,884 queries plus two full entity loads for one admin action.** **⚠️ THE RISK CLASS IS GENUINELY LOWER THAN THE THREE INCIDENTS AND MUST NOT BE OVERSOLD: it is `ADMIN`-only (`AdminStudyPackController:38`), manually triggered, and on no hot or anonymous path.** It is in scope because it is the LAST instance of a shape that has already cost three events, not because it is about to cause a fourth.

**(2) The note picker gets server-side search, which is the stated prerequisite for bounding it.** **⚠️ THE ORDER IS A CORRECTNESS CONSTRAINT AND `v0.123.0` RECORDED WHY IT DECLINED THE BOUND: the picker filters CLIENT-SIDE over the whole library, so a `limit` alone would silently make every note beyond it UNADDABLE** — an invisible correctness loss traded for an invisible performance win. **⚠️ SO SEARCH SHIPS FIRST OR WITH THE BOUND, NEVER A BOUND ALONE.** **⚠️ SMALLER THAN IT READS, CHECKED NOT ASSUMED: `GET /notes` ALREADY TAKES A CLAMPED `limit`** (`NoteController:684-694`, `Math.clamp(limit, PRIVATE_NOTES_MIN_LIMIT, PRIVATE_NOTES_MAX_LIMIT)`), so this is an **additive `q` parameter on an existing endpoint**, not a new one — existing callers are unaffected by construction.

**⚠️ THE SEARCH MUST MATCH THE SAME FIELDS THE CLIENT FILTER MATCHES, AND THIS IS THE ITEM'S SHARPEST EDGE.** `filterPickerNotes:194-206` matches **title, subject, courseProgram AND tags**. A server search over `title` alone would look correct in every demo and would silently make tag-findable notes unreachable — the same class of invisible narrowing the bound itself was declined for.

### Anti-drift

**⚠️ `frontend/app/onboarding` STAYS FROZEN** — `[CHECKPOINT — due 2026-09-11]` is FOUR DAYS OUT, the closest this constraint has ever been to its date. **⚠️ NO Learning Connections promotion before `2026-09-19`.** **⚠️ Do NOT raise the connection pool** (`AppConfig:52-72`; a `v0.112.0` Phase 3 decision gated on `[CHECKPOINT — due 2026-10-04]`, and a raise already failed once at 20). **⚠️ Do NOT trim `contentPreview`/`summaryPreview`** — `v0.100.0` rejected it, it only moves the threshold. **⚠️ Do NOT start `v0.112.0` Phase 3.** **⚠️ NO migration, no quota/entitlement/meter change, no new mode or sub-mode, no `ProfileType` gate.** **⚠️ Do NOT change what `BOARD_EXAM_STARTED`, `ADAPTIVE_PRACTICE_STARTED`, `QUIZ_SHARE_LINK_CREATED/OPENED/COMPLETED` or `GUIDANCE_TIP_SHOWN` record or when they fire** — several dated reads depend on them, and `v0.122.0`'s signoff caught exactly this class.

**⚠️ ITEM 1 MUST NOT CHANGE WHICH NOTES ARE ELIGIBLE — only how they are found.** The eligibility predicate (`isEligibleOfficialTemplate`, `isOfficialAuthor` including its `OFFICIAL_AUTHOR_EMAIL` and `DELETED_USER_ID` legs) is **unchanged**; this release changes the QUERY SHAPE around it. **⚠️ Do NOT "simplify" the predicate while you are in there.** **⚠️ AND DO NOT PAGE THE BACKFILL** — its response is three counts over the whole catalog, so paging changes what the numbers MEAN; a projection plus batched lookups removes the cost without touching the contract.

**⚠️ ITEM 2 MUST NOT NARROW WHAT THE PICKER CAN ADD.** Same four fields, same case-insensitive substring semantics. **⚠️ `v0.124.0`'s detail-page `AddNotesModal` finding rides on this item: it still calls unbounded `listNotes()` on open and is inert ONLY because it is unreachable (`setAddOpen(true)` appears nowhere on that page). If this release makes the picker lazy-and-bounded, it must NOT leave that second copy on the old shape.**

### Verification

**ONE SCOPED COLD AGENT framed as falsification.** Trigger: item 2 changes a **shared endpoint** (`GET /notes` has many consumers) and changes what a search can REACH, which is a correctness predicate rather than a performance one. **⚠️ NOT the three-agent tier** — no permission substrate, no cross-user read, no money or quota semantics, no migration. **⚠️ `GET /notes` is a CHANGED endpoint, so it owes ONE REAL-REQUEST test** (`MockMvc` + a real query string); a direct handler call passes under a binding defect by construction (`v0.119.0`).

**⚠️ PRE-DECLARED GUARDS, EACH NAMING THE FIXTURE THAT PROVES NOTHING:**
- **(a)** a note matching ONLY by **tag** — not by title — must still be returned by the server search. **A title-matching fixture passes under a title-only implementation and is the single most likely way this release silently narrows the product.**
- **(b)** with the bound in place, a note **beyond the limit** must still be addable **via search**. A fixture with fewer notes than the limit passes under both the defect and the fix.
- **(c)** item 1 must assert the **QUERY COUNT** with N > 1 eligible notes — a correctness-only test (right counts returned) passes under the N+1 by construction, which is `v0.124.0`'s own carried lesson.
- **(d)** item 1's queued/skipped/rejected counts must be **IDENTICAL** before and after for a mixed fixture (eligible, ineligible, already-seeded).

**⚠️ CARRIED LESSONS FROM `v0.124.0`, ALL PAID FOR: MUTATION-VERIFY every guard — one survived its own mutation and was found only by a cold agent; a diff that changes behaviour while no test beside it moves is UNVERIFIED; SWEEP BY SURFACE, not by diff; and ⚠️ WHEN A SHARED MAPPER OR ENDPOINT GAINS A FIELD OR PARAMETER, ENUMERATE EVERY CALLER — that release put a new field on an anonymous `permitAll` payload and `tsc` plus 4,400 tests stayed green.**

**Routing: COLD AGENT implementation + inline audit** (no Codex token), the shape `v0.124.0` used successfully.

### Shipped

**⚠️ ONE AUDIT CORRECTION, RECORDED BECAUSE IT CHANGED A PUBLIC HTTP CONTRACT: the search parameter is `search`, NOT `q`.** The kickoff brief named `q`, the delivery followed it, and then **surfaced rather than buried** that `NoteController` already declares `SEARCH_REQUEST_PARAM = "search"` for three sibling endpoints. **Two names for one concept in one controller is drift the moment it merges**, so the duplicate constant was deleted and `GET /notes` reuses the existing one. Caught while uncommitted — **the brief was wrong, not the delivery.**


- **`OfficialChallengeQuizTemplateService.queueBackfill` reads the catalog in three bounded queries instead of two per note.** It loaded every `PUBLIC` `NoteEntity` (~1,442 rows **including `content`**) plus every matching `StudyPackEntity`, then ran `userRepository.findById` **and** `existsByUserIdAndStudyPackId` inside the loop — ~2,884 queries and two full entity loads for one admin action. It now reads `NoteOwnerVisibilityProjection` and `StudyPackOwnerProjection` (id, owner, visibility / note id only), resolves Official authors once for the distinct owner set through `findAllById`, and batches the bank check into `ChallengeQuizQuestionBankRepository.findOwnerStudyPackPairsByStudyPackIdIn`.
- **Eligibility is unchanged, and it is now one predicate rather than two copies.** `isEligibleOfficialTemplate`'s non-author legs were extracted to `matchesOfficialTemplateShape`, and `isOfficialAuthor`'s account test to `isOfficialAuthorAccount`; the entity path and the backfill's projection path both call them, so the two cannot drift. The bank check is still asked only about notes that already passed shape **and** author, exactly as the old `||` short-circuit did, so `queued`/`skipped`/`rejected` mean what they meant before. The backfill is **not** paged and the dispatch order stays `updated_at desc`.
- **`GET /notes` takes an additive optional `search`.** It flows through `NoteService.listMine` — reusing the existing `toLibrarySearchPattern` escaping rather than a second helper — into a new `NoteLibraryRepositoryImpl.appendOwnedNoteSearchFilter` that matches **title, subject, course program AND tags**, case-insensitively, with the Postgres `unnest` / H2 `array_to_string` split the library filter already uses. **⚠️ `appendSearchFilter` was NOT widened** — it serves the public library, a different surface with SEO-indexed pages.
- **The Study Plan builder's note picker is bounded and searches server-side.** It requests `listNotes(50, query)` (debounced 300 ms) instead of downloading the whole library and filtering it in the browser. **⚠️ The order was a correctness constraint: `v0.123.0` declined the bound outright because a limit over a client-side filter makes every note past it unaddable. Search ships with the bound, never a bound alone.**
- **The picker's selection now holds the notes themselves, not just their ids.** `notes` became the current result page, so a note selected under one query is absent once the query changes — deriving "Selected (N)" or the optimistic add rows from `notes` dropped it silently. `AddNotesModal` keeps a `Map<id, note>` and hands the selected notes to `handleAddNotes` / `handleAddLeafNotes`.
- **A truncated picker list says it is truncated.** A filled page renders "Showing your 50 most recently updated notes. Search to reach any of the others." (or "Showing the first 50 matches" when searching) — the same reason the empty state distinguishes loading and failure from an actually-empty library.
- **The loop guard survived the rewrite in a different form.** The picker effect still does not depend on `refreshingNotes` (that dependency plus a `finally` reset is what produced 3,743 calls in five seconds in `v0.123.0`); `lastPickerRequestRef` records the query whose fetch has already been started, set before the first await and never cleared on failure, so a failed search cannot retry itself while a changed query still refetches. A request-sequence ref discards out-of-order responses.
- **`v0.124.0`'s second copy of the picker is gone.** `collection-detail-page-client.tsx`'s `AddNotesModal` — which called unbounded `listNotes()` on open and was inert only because `setAddOpen(true)` appeared nowhere — was **deleted** along with that file's duplicate `filterPickerNotes`, its `handleAdd` and its `addOpen` state, rather than bounded. Bounding it would have duplicated the debounce-and-search machinery into code nothing renders, where no test could exercise it. Note addition lives in the Builder.

**Cold-agent falsification pass, and it found the class of defect this repo keeps paying for.**

- **⚠️⚠️ THE THREE NEW JPQL PROJECTIONS WERE EXECUTED BY NO TEST, AND A TRANSPOSED CONSTRUCTOR ARGUMENT PASSED ALL 2,213 TESTS — MEASURED, NOT SUSPECTED.** Every test reaching `queueBackfill` MOCKS the repositories and hands back correctly-ordered records; `NativeQueryPostgresIntegrationTest`'s `PREPARE` sweep covers **native** queries only; and Spring's bootstrap proves the JPQL parses but not the argument order, because **every component is a `UUID`** bar one enum. Swapping `s.id` and `s.noteId` made the admin backfill report `queued=0, skipped=1442` over the whole catalog — **a successful-looking response that seeds nothing** — with a green suite. **⚠️ THE LOGIC WAS WELL GUARDED AND THE DATA PLUMBING FEEDING IT WAS NOT GUARDED AT ALL**, the same shape as `v0.116.0`'s no-op and `v0.124.0`'s uncovered Board Exam branch. Now pinned in `NativeQueryPostgresIntegrationTest` — the **real Flyway schema on real PostgreSQL**, chosen over a hand-written H2 fixture so the DDL cannot drift from the migrations. **⚠️ EVERY ID IN THE FIXTURE IS DISTINCT AND ASSERTED BY IDENTITY: a fixture whose note, owner and pack ids could coincide passes under a transposition, which is exactly why this was invisible.** The real schema paid for itself immediately — it rejected an invented `last_known_outcome` value an H2 fixture would have accepted.
- **`handleAddLeafNotes` was entirely unexercised, so the release's own fix covered the Goal path only.** Reverting its `noteById` to the pre-fix shape passed all 59 builder tests, and so did a bare `throw` as its first statement. **⚠️ `/collections/{leafId}/builder` is the COMMON curator surface**, and `handleAddNotes`/`handleAddLeafNotes` are separate functions with the same defect and the same fix — only one was pinned. Now guarded, with the add held IN FLIGHT (letting it resolve repaints from the server and passes under the defect) and the assertion scoped OUTSIDE the dialog (the modal's own *Selected* list still shows the title).
- **The queue-order guard survived the exact reorder its own name forbids.** N=2 with random UUIDs meant a sort by pack id preserved the fixture order roughly half the time — **sound in principle, a coin flip in practice.** Now N=3 with deterministic descending ids, so any ascending sort differs every run; mutation-verified.
- **⚠️ A COMMENT INSTRUCTED THE OPPOSITE OF THE CODE, AND THIS SESSION'S OWN RENAME LEFT IT THERE.** `api.ts` read *"MUST STAY `q`"* directly above `parameters.set("search", …)`. A later session obeying it would bind `search = null` server-side, appending no filter — **the picker keeps its 50-row bound while search reaches nothing**, precisely the every-note-past-the-bound-is-unaddable loss `v0.123.0` refused to ship. Seven further prose sites (javadoc, test comments, a test NAME, `collections.md`, and a `RELEASES.md` bullet contradicting its own audit note twelve lines above) still said `q`. **The rename swept the wire-format strings and not the prose — a rename is not done when the code compiles.**
- **Not refuted, checked rather than assumed:** eligibility is leg-for-leg identical including null handling; `appendSearchFilter` is **byte-identical** to the pre-release commit; all nine `listNotes` call sites pass no search; the deleted modal left zero orphans; and the two dialects diverge only on a search string containing the literal `|||LIBRARY_TAG_BOUNDARY|||` separator — unreachable, and inherited unchanged from the existing public-library filter.

### Known limitations

- **The deleted detail-page `AddNotesModal` was unreachable, so its removal is unverifiable by test and is recorded rather than pinned.** No fixture can reach a component no code path renders; writing one would have hand-built a state production cannot produce, which is the `v0.116.0` / `v0.117.0` failure. What *is* pinned is the surrounding claim: `app/collections/[id]/page.test.tsx` still asserts a learner opening a collection issues zero `listNotes()` calls, and every pre-existing frontend suite passes unchanged (203 suites / 2,248 tests, up from the 202 / 2,240 baseline by exactly this release's new guards).
- **`loadNoteVisibility` on the collection detail page still calls unbounded `listNotes()`.** It is ADMIN-only and deliberately untouched: it needs the visibility of *every* note in the collection, so bounding it would make the private-note count wrong on the publish path, which `v0.124.0` made fail closed. It is a separate decision, not this release's.
- **The picker bound is 50, the server clamp, so a curator with a large library sees a genuinely partial list until they search.** That is the intended trade and the truncation copy states it; there is no "load more". **⚠️ ONE REACHABLE CONSEQUENCE IS NAMED RATHER THAN LEFT TO BE REPORTED AS A BUG: the bound is applied SERVER-SIDE and the already-in-this-plan exclusion is applied CLIENT-SIDE AFTER it, so a curator whose 50 most recently updated notes are ALL already in the plan opens an EMPTY picker reading "No notes available." while addable notes exist beyond the bound.** It is plausible — a plan built from recent authoring is exactly that shape — and it is **recoverable and disclosed**, because the truncation notice sits directly beneath and says *"Search to reach any of the others."* **⚠️ The fix is NOT to raise the bound (it would only move the threshold, which `v0.100.0` already rejected for `contentPreview`); it is to exclude present notes SERVER-SIDE, which needs the collection id on the request and is a contract change this release does not take.**

## v0.124.0 - Collection Path Performance

**Status: Released** (kicked off and signed off 2026-09-06, base branch `releases/v0.124.0`, cut from `main` after `v0.123.0` merged and tagged)

**⚠️ IT OVERRIDES ITS OWN GATE BY EXPLICIT OWNER DECISION (2026-09-06), AND THE OVERRIDE IS WRITTEN DOWN RATHER THAN ROUTED AROUND.** `v0.123.0`'s Backlog row for the performance audit says: *"Re-read after `v0.123.0` deploys: if the Goal path is still the dominant cost on large Review Sets, sequencing 4 becomes its own Codex-routed release."* **`v0.123.0` HAS NOT BEEN OBSERVED IN PRODUCTION.** The owner took it anyway. **⚠️ A later session reading that row in isolation must come here first — recorded override, not drift.** **⚠️ THE ACCEPTED RESIDUAL, STATED AT KICKOFF: the release is sized from the audit's STATIC READ rather than from post-deploy evidence, so if `v0.123.0`'s lazy note list turns out to have already removed most of the felt cost, this release's benefit is smaller than its request-count arithmetic implies.** The arithmetic itself is not in doubt; what is unobserved is how much of it a curator actually feels.

**⚠️ WIDENED MID-RELEASE BY OWNER DECISION (2026-09-06), FROM *Goal Path Request Cost* TO *Collection Path Performance*, AND THE TRIGGER WAS A FALSE CLAIM IN THE SHIPPED RECORD RATHER THAN A NEW IDEA.** Asked whether opening a collection was still slow, this session checked instead of answering from `RELEASES.md` — and found that **`v0.123.0` claimed the unbounded `listNotes()` had been made lazy on BOTH page loads when it had only been made lazy in the BUILDER.** `collection-detail-page-client.tsx` was never in that release's diff. **⚠️ SO THE AUDIT'S LEVER 1 — its own "biggest, and it repeats" — WAS STILL LIVE ON THE PAGE LEARNERS OPEN MOST**, and the record said otherwise. The release therefore covers **both** surfaces on the collection path, and `v0.123.0`'s bullet is corrected IN PLACE below rather than quietly rewritten.

**THE DEFECT, AND IT IS ARITHMETIC RATHER THAN A BUG:** `refreshBuilder` loads the Goal, then issues **one `getCollection` per child Subject Plan** (`study-plan-builder-page-client.tsx:1447`). A Review Set with 20 plans is **21 requests and ~147 queries to render one page.** `Promise.all` makes them concurrent, **not cheap** — and **⚠️ CONCURRENCY IS THE SHARP EDGE HERE, NOT LATENCY: the connection pool is 20** (`application.yaml`), and `v0.112.0` documents pool exhaustion as a **live production failure mode**, so one curator opening one large Review Set can burst against the whole pool.

**⚠️ THE SHAPE IS DECIDED AT KICKOFF BECAUSE THE OBVIOUS OPTION IS WRONG, AND IT WAS CHECKED RATHER THAN ASSUMED.** The audit offers *"return child detail WITH the goal, or add a batch read."* **`getCollectionGoal` HAS SEVEN FRONTEND CONSUMERS** — `progress-report-client`, `dashboard`, the **three** quiz prescreens (`challenge-quiz`, `adaptive-practice`, `quick-review`), `collection-detail-page-client` and the builder. **⚠️ SIX OF THEM NEED THE GOAL SHAPE AND NOT ONE CHILD'S ITEMS**, so fattening `GoalCollectionDetailResponse` — already a 24-field record carrying `weeklyFocusByDay` — would inflate the payload on the Dashboard and on three exam prestart paths to serve the builder alone. **⚠️ THAT IS THE `/notes/public` MISTAKE IN MINIATURE: a payload that grows with content on a surface that does not need it.** **DECISION: A SEPARATE BATCH READ. `GoalCollectionDetailResponse` IS NOT WIDENED.**

### Planned Scope

**(1)** A batch read returning the item detail for a Goal's children in **one** request, replacing the per-child fan-out at `:1447`. **⚠️ It must return the SAME per-child shape the builder already consumes** — `buildSubjects` takes `GoalCollectionDetailResponse` plus `NoteCollectionDetail[]`, so a divergent shape turns a request-count fix into a rendering rewrite.

**(2)** The builder consumes it and stops fanning out. **⚠️ The Goal path keeps `refreshBuilder`; only the child loop is replaced.**

### Anti-drift

**⚠️ Do NOT widen `GoalCollectionDetailResponse`** — the seven-consumer finding above is the whole reason this is a batch read.

**⚠️ Do NOT "fix" this by raising the connection pool.** `AppConfig:52-72` records that bound as a `v0.112.0` Phase 3 decision **gated on `[CHECKPOINT — due 2026-10-04]`**, and a raise has already failed once at 20.

**⚠️ Do NOT restructure `toItemResponses`** — five bulk queries keyed by `noteIdIn`, no N+1, already correct. **The waste is the REQUEST COUNT, not the per-request work**, and a batch read must not become a reason to touch it.

**⚠️ Do NOT re-open `v0.123.0`.** Its retry bound, canonical section labels, `applyLeafDetail` and lazy note list all stay exactly as shipped; **the deferred-save model (`v0.96.0`), the flush-first `savePendingLeafOrder`, `refreshBuilder`'s `skipNotes` option and the `editing`-gated combobox writer (`v0.88.0`) all remain locked.**

**⚠️ NO migration. No quota, entitlement, limit or meter change. No new mode or sub-mode.**

**⚠️ THE DATED-READ CLUSTER IS FOUR DAYS OUT AND THIS RELEASE MUST NOT TOUCH WHAT IT MEASURES.** `2026-09-10` (retention H1+H5 proximal), `2026-09-11` (onboarding funnel, and the freeze lifts), then ten more through `2026-09-19`. **⚠️ `frontend/app/onboarding` STAYS FROZEN — the freeze lifts when the `2026-09-11` READ IS TAKEN, not when the date passes.** **⚠️ NO Learning Connections promotion before `2026-09-19`.** The Goal builder path is measured by **none** of the twelve, which is what makes this release compatible with the window.

### Verification

**ONE SCOPED COLD AGENT framed as FALSIFICATION.** A new or changed endpoint is a response-contract change on a path six other surfaces share, and **⚠️ `v0.123.0` proved this session's own claims need attacking: its cold agent refuted two of six, one of them a defect that release INTRODUCED.**

**⚠️ ANY NEW OR CHANGED ENDPOINT OWES ONE REAL-REQUEST TEST** — `MockMvc` with `.contentType(MediaType.APPLICATION_JSON)` and a body, as `NoteControllerTest` already does. **A direct handler call passes under the defect by construction (`v0.119.0`, where both JSON POSTs sent no `Content-Type` and 2,182 frontend tests stayed green).**

**⚠️ PRE-DECLARED GUARDS, EACH NAMING THE FIXTURE THAT PROVES NOTHING: (a) assert the REQUEST COUNT — one call for N children, with N > 1.** A fixture with a **single** child cannot tell a batch read from a fan-out and passes under both. **(b) Assert the rendered subject shape is UNCHANGED for a multi-child Goal** — a request-count test alone would pass while the page renders wrong. **(c) Assert `GoalCollectionDetailResponse` did NOT grow** — pin its field set, because the cheapest way to make (a) pass is the widening this release forbids.

**⚠️ CARRIED LESSONS, ALL PAID FOR IN `v0.123.0`: a guard must be MUTATION-VERIFIED, not merely written — three of its guards survived their own mutation before they discriminated, twice on timer/assertion shape and once on a fixture that put the state on the wrong note; a diff that changes behaviour while no test beside it moves is UNVERIFIED; and SWEEP BY SURFACE, not by diff — `collections.md` carried a false grouping rule that no PR touched.**

**Routing: CODEX** — a backend DTO, a service method, an endpoint and a client, with a contract shared by seven consumers.

### Shipped

**Items 1-2 — the batch read and its consumer.**

- **`GET /collections/{id}/goal/child-items` returns every child Subject plan's items in ONE request**,
  replacing the per-child `getCollection` fan-out in `refreshBuilder`. **⚠️ COUNTING CONVENTION, STATED
  RATHER THAN LEFT IMPLICIT: the kickoff's "21 requests" counts the goal read plus 20 children and
  EXCLUDES the builder's own initial `getCollection` on the Goal, which the fix does not remove.** Whole
  render, 20 plans: **22 requests → 3** (`getCollection` on the Goal, `getCollectionGoal`, one batch
  read). By the kickoff's convention it is 21 → 2. The lazy note list stays lazy either way (`v0.123.0`).
  **⚠️ `GoalCollectionDetailResponse` was NOT widened** — a backend test pins its
  exact 24-component list, because hanging items on the goal response is the cheapest way to make the
  request count fall and is precisely what this release forbids.
- **The response is deliberately minimal — `(collectionId, items[])` per child, and nothing else.**
  `buildSubjects` never read anything more off a child detail, so returning N full
  `NoteCollectionDetailResponse` payloads would have defeated much of the point. `items` is the same
  `NoteCollectionItemResponse` shape `getCollection` already returns, so the rendered shape is unchanged.
- **Authorization is exactly the fan-out's, because the endpoint takes no id list.** Child ids are
  derived server-side from the owner-scoped parent lookup, and the children query is itself
  owner-filtered. **⚠️ Do NOT add an id-list request shape** — that is the IDOR version of this endpoint
  and would need per-id authorization.
- **Items for all children are built in ONE `toItemResponses` pass**, so the read costs the same five
  bulk queries at 2 children as at 20. **⚠️ `toItemResponses` is untouched** — the waste was the request
  count, not the per-request work.
- **Both calls sit BELOW `applyLeafDetail`'s early return.** Hoisting either into the opening
  `Promise.all` would add a wasted request to every leaf-plan refresh — a regression on the path this
  release's anti-drift says is untouched.

**Item 3 — opening a collection stops downloading the note library.** (Folded in 2026-09-06 by owner
decision; audit Lever 1 on the detail page.)

- **`collection-detail-page-client.tsx` no longer calls `listNotes()` on load.** It ran on EVERY
  collection open, unbounded — the backend caps nothing when `limit` is null — with each row carrying
  `contentPreview` and `summaryPreview`, **the same two fields that pushed `/notes/public` past
  Next.js's 2 MB data-cache limit in the 2026-08-31 build failure.** For a curator that is ~900 rows
  before the page renders.
- **⚠️ WHAT IT WAS FOR IS THE PART THAT JUSTIFIES THE FIX: exactly two derived values, and for a
  learner BOTH were discarded.** `noteVisibility` feeds four render sites that are **every one**
  `isAdmin &&`-gated — the code's own comment already called it *"admin-only progressive
  enhancement"* — and `noteStudyPackIdByNoteId` resolved a **single** value, `primaryExamStudyPackId`.
  So a non-admin downloaded their whole library to produce **one Study Pack id**.
- **`NoteCollectionItemResponse` gains `studyPackId`, and it is FREE.** `StudyPackProgressView.getId()`
  was **already loaded** in `toItemResponses` for the due-concept lookup, so this adds **no query** —
  one more argument to a record already being built. `CollectionExamCandidate`'s `Pick` widens with it.
- **Visibility now loads in its own ADMIN-gated effect, deliberately not inside the loader.** `authUser`
  resolves in its own effect, so `isAdmin` is false on first render; gating the loader on it there would
  either read stale state or re-arm the whole load — **the same defect the cold agent found in the
  builder, which this release also fixes.** A learner now issues **zero** `listNotes()` calls on open.
- **`noteListLoadFailed` is deleted rather than left inert.** It existed only to disable the exam CTA
  when the note list failed and the pack id could not be resolved; the id now arrives on the item, and
  the remaining `!primaryExamStudyPackId` clause still covers the Board Exam case.
- **⚠️ THE BOARD EXAM BRANCH WAS COMPLETELY UNCOVERED AND IS NOW GUARDED.** Hardcoding
  `primaryExamStudyPackId = null` passed the **entire** detail-page suite, so the id's
  provenance was unverified in both directions — `BOARD_EXAM` + `PRO` is the only combination reaching
  `/study-packs/{packId}/challenge-quiz`, and nothing exercised it. The new guard asserts that route
  **and** that `listNotes` is unused, so the id cannot be coming from the old lookup.
- **⚠️⚠️ THE SHARED MAPPER PUT THE NEW FIELD ON AN ANONYMOUS PAYLOAD, AND NOTHING CAUGHT IT — FOUND
  ONLY BY ASKING WHO ELSE CALLS `toItemResponse`.** `toPublicItemResponses` delegates to the SAME
  mapper, and it backs `GET /collections/public/{id}`, which `SecurityConfig` declares `permitAll`.
  **So a field added for the owner's detail page began riding on a payload served to callers with no
  account** — `tsc`, 2204 backend tests and 2237 frontend tests all stayed green. **⚠️ THE PUBLIC
  MAPPER NOW WITHHOLDS IT**, and `studyPackId` became a CALLER-SUPPLIED parameter rather than one
  derived inside the mapper, following the precedent already set by `generatedQuizId` — the two callers
  legitimately disagree, so the shared mapper must not decide. **⚠️ EXPOSURE WAS LOW, STATED HONESTLY
  RATHER THAN INFLATED:** the notes are already `PUBLIC` and `/study-packs/{id}` is
  `findByIdAndOwnerUserId`, so the id 404s for anyone else. **It was an unintended widening of an
  anonymous contract, not a credential leak — and the lesson is the mechanism, not the blast radius.**
  Guarded, and the fixture MUST stub an existing pack: with no pack the field is null under both the
  defect and the fix.
- **⚠️ GUARD FIXTURES ARE NON-ADMIN AND `PRO`-BOARD ON PURPOSE — an admin fixture still legitimately
  calls `listNotes` for the badges and passes under the defect.** Mutation-verified: removing the admin
  gate fails the learner guard (and the admin guard, which pins **exactly once**); nulling the pack id
  fails the Board Exam guard and nothing else.

**Cold-agent finding, folded in rather than deferred: the curator loaded the builder TWICE.**

- **A `TEACHER` ran the whole builder load twice — 6 requests, not 3 — and the release's own headline
  arithmetic was false for exactly the persona it is written about.** `labels` is memoized on
  `authUser?.profileType`, and `authUser` resolves in a MOUNT EFFECT, so the first render sees
  `undefined`. **⚠️ `STUDENT` and `BOARD_EXAM` both resolve `goalSingular` to the same `"Goal"` the
  unresolved default gives, so the memo is referentially stable and they load once — `TEACHER` resolves
  it to `"Course"`, which changed `loadBuilder`'s identity and re-armed the load effect.** The label was
  read only for one error string, so it now goes through a ref (`v0.123.0`'s own `onLabelChangeRef`
  shape, not a new one) and leaves the dependency array. **⚠️ PRE-EXISTING, NOT INTRODUCED HERE — it
  doubled the fan-out too (44 requests, not 22).** It is fixed in this release rather than filed because
  the release exists to cut Goal-path request cost and a curator is who owns 20-plan Review Sets.
  **⚠️ THE GUARD'S FIXTURE IS A `TEACHER` FOR THAT REASON, AND A `STUDENT` FIXTURE PASSES UNDER THE
  DEFECT** — which is why the shipped request-count guard could not see it.

**Round-2 cold agent, run because the release doubled after round 1 covered only the first commit.**

- **⚠️ IT FOUND A REGRESSION THIS RELEASE INTRODUCED, AND THE MECHANISM IS THE PRICE OF ITEM 3'S OWN
  FIX: an ADMIN could reach an ENABLED Publish button while note visibility was still unknown.** Moving
  the visibility fetch out of `loadCollection` means the page now reaches READY while that request is in
  flight — and **an empty `noteVisibility` map is indistinguishable from "nothing is private"**, so
  `privateCount` read 0, the *"N notes are private"* affordance did not render, and the admin met the
  server's raw rejection instead. **⚠️ FIXED BY FAILING CLOSED: unknown blocks, only a COMPLETED read
  unblocks**, so a failed visibility fetch also keeps Publish disabled. Server-side validation always
  held, so no bad data was reachable. **⚠️ THE GUARD HOLDS `listNotes` UNRESOLVED — letting it resolve
  reproduces the settled state, which is correct under both the defect and the fix.**
- **⚠️ A LINE THIS RELEASE CHANGED WAS EXECUTED BY NO TEST, PROVEN BY MUTATION RATHER THAN SUSPECTED:**
  replacing the builder's load-error copy outright left **all 54 tests green**. It is the ONE surviving
  read of `labels.goalSingular` behind the new ref, so the ref's correctness rested entirely on reading.
  **⚠️ IT IS UNCOVERED FOR A NARROW REASON WORTH RECORDING: `makeErrorMessage` returns `error.message`
  for anything `instanceof Error`, so the fallback string is reachable ONLY when a NON-`Error` is
  thrown** — an `Error` fixture renders the thrown message and never exercises the label. Now guarded,
  and the mutant dies.
- **Two comments still justified themselves by a dependency this release DELETED**, one of them stating
  that `loadBuilder` re-fires for TEACHER profiles and that a ref absorbs it. **⚠️ That is precisely the
  premise on which a later session restores `labels.goalSingular` to the deps array believing it is
  covered**, so both are corrected in place rather than removed.
- **`toOptimisticItem` hardcoded `studyPackId: null`** while holding the real value. Inert today, but
  `CollectionExamCandidate` now carries the field, so an optimistically-added note would have read as
  packless to any future exam-eligibility check over builder state.
- **⚠️ THE ZERO-`listNotes` CLAIM IS TRUE FOR A REASON THAT IS NOT THE FIX, AND THE FEATURE DOC NOW SAYS
  SO:** the detail page's own `AddNotesModal` still fetches unbounded on open and is simply
  **UNREACHABLE** — `setAddOpen(true)` appears nowhere on that page. Re-wiring an *Add notes* button
  there would silently restore the fetch this release removed.
- **Not refuted, checked rather than assumed:** `toItemResponse` has exactly two callers and no third
  path reaches the DTO; every study-pack-id endpoint is role-gated and the share routes are
  token-addressed, so the brief anonymous exposure was harmless as stated; `uq_study_packs_note_id`
  means the owner-unfiltered projection cannot disagree with the old client-side source.

**Known limitations.**

- **The endpoint's real-request test does NOT run the security filter chain, so `@PreAuthorize` is
  pinned by reflection rather than enforced.** `buildMockMvc` uses `standaloneSetup`, which is the whole
  controller test class's existing shape, so this is **pre-existing infrastructure and not specific to
  this endpoint** — changing it would restructure a shared harness this release's anti-drift does not
  open. **⚠️ What IS covered was verified by mutation: changing the `@GetMapping` path fails
  `goalChildItemsResolveToTheirOwnHandlerAndSerializeOverTheWire`,** so routing and over-the-wire
  serialization are genuinely exercised — and `v0.119.0`'s defect class (a JSON POST sent with no
  `Content-Type`) cannot apply to a bodyless GET. The residual is that method security and the
  application's own `ObjectMapper` bean are bypassed in that class.
- **A child deleted mid-load now renders as an EMPTY plan instead of erroring.** The goal read and the
  batch read are concurrent, so a child present in `goal.children` but absent from the batch response
  falls through `?? []`. Under the fan-out, `getCollection(deletedChildId)` threw 404 and the page
  surfaced `not-found`. The window is a delete from another tab or device between two in-flight
  requests; it is arguably the better degradation, but it is a **behaviour change** and is recorded as
  one rather than discovered later.
- **The positional-partition guard is defensive and UNTESTED, and that is stated rather than papered
  over.** `groupItemResponsesByCollectionId` throws if `toItemResponses` ever returns a different number
  of responses than items — the shape that would silently misattribute an item to the wrong plan. It is
  **unreachable today** (`toItemResponses` is a bare 1:1 `map`), so no mutant can kill it and no test
  asserts it. It exists because its sibling `toPublicItemResponses` carries a `.filter()` and
  `v0.104.0` already paid for this class once.
- **`v0.123.0`'s residual stands: the release is sized from a static read.** It was not observed in
  production before this release opened, so how much of the request cost a curator actually felt is
  still unmeasured. The arithmetic is not in doubt.

## v0.123.0 - Collection Builder Integrity

**Status: Released** (kicked off and signed off 2026-09-06, base branch `releases/v0.123.0`, cut from `main` after `v0.122.0` merged and tagged)

**⚠️ IT OPENS ON TWO UNTRACKED FILES THAT KICKOFF STEP 8 SURFACED, NOT ON A ROADMAP QUEUE — and both point at the SAME surface, which is why they are one release rather than two.** `docs/claude-findings/2026-09-06-study-plan-builder-section-label-refresh-loop.md` (an owner-reported incident, diagnosed from code) and `docs/claude-plans/note-collection-page-performance-audit.md`. **⚠️ Both were unindexed; both get Backlog Index rows in this kickoff commit** — the finding's own §8 says so outright, and `docs/claude-findings/` was added to step 8 at the `v0.112.0` kickoff precisely because incident files had gone unindexed there **four kickoffs running**.

**THE DEFECT, OWNER-REPORTED 2026-09-06 AND SELF-RESOLVED BEFORE IT COULD BE READ:** `/collections/{id}/builder` *"keeps on refreshing"* after a section name was accidentally set to a long pasted value. **⚠️ THE SELF-RESOLUTION IS ITSELF EVIDENCE — deleting the note carrying the label stopped it, so the driver is a PER-CARD effect, not a page-level poller, an auth loop or ISR.** **⚠️ THE LOOP MECHANICS ARE PROVEN FROM CODE; THE INGRESS IS NOT.** The offending row was deleted before it could be read and the Render MCP was unreachable throughout, so **nothing rests on production data** and §4's ingress question stays **UNRESOLVED** rather than guessed.

**THE STRUCTURAL DEFECT, IN ONE SENTENCE:** `LeafSortableNoteCard`'s auto-save effect (`:445-455`) decides whether a write is still pending by comparing `item.label` against a **locally normalised** form of its own input, while the value that actually reaches the server is chosen by a **different** normalisation in `handleLeafLabelChange` (`:1646`) — so when the two disagree the guard never clears, and there is **no attempt cap, no backoff and no failure short-circuit.**

**⚠️ THE TWO DEFECTS COMPOUND, WHICH IS THE ARGUMENT FOR ONE RELEASE RATHER THAN TWO: the loop's DAMAGE MULTIPLIER IS THE PERFORMANCE AUDIT'S LEVER 1.** Each iteration calls `refreshBuilder`, which refetches the curator's **entire unbounded note list** — neither the failure path (`:1374`) nor `persistLeafItems` (`:1541`) passes `skipNotes`. **So a wedged page issues a heavy `listNotes()` every ~500 ms–1 s, from every affected client — a CLIENT-DRIVEN LOAD AMPLIFIER against the same backend the 2026-09-05 outage showed has no headroom.** **⚠️ And adoption propagates it: an uncollapsed label on a published source plan wedges every learner who adopts it.**

### Planned Scope

**(1) ONE CANONICAL NORMALISATION, applied on both sides**, so the effect's guard compares against the form the write path actually produces. **⚠️ The mismatch is the defect; whitespace is only how it surfaces.**

**(2) BOUND THE RETRY.** A failed write must not be retried unconditionally. **⚠️ The pattern already exists in this repo — `LIBRARY_GENERATION_POLL_MAX_TICKS = 100`, commented *"Absolute backstop so a wedged backend can never poll forever."* Reuse that idea; do not invent a second shape.**

**(3) MEMOIZE `handleLeafLabelChange` (`:1646`).** Re-creating it every render puts a changing identity in the effect's dependency array, which is what re-arms the write on every render.

**(4) `persistLeafItems` PASSES `skipNotes: true`** (audit sequencing 1). One line; removes the heaviest fetch from every section edit. **⚠️ A section label change CANNOT change the note set — which is exactly the condition `refreshBuilder`'s own comment names as safe.**

**(5) CONSUME THE PUT RESPONSE** (audit sequencing 2). `PUT /collections/{id}/items/order` **already returns `NoteCollectionDetailResponse`** — byte-for-byte what `getCollection` returns (`NoteCollectionController:322-324`) — and the client **discards it, then refetches the same payload plus the whole note list.** For a leaf collection the refresh is **entirely redundant**: `refreshBuilder` returns early once `childCount === 0`, after setting exactly the state the PUT response already describes.

**(6) LAZY-LOAD AND BOUND THE PICKER NOTE LIST** (audit sequencing 3). `listNotes()` is called with **no limit** and the backend imposes none (`limit == null` means no cap), it carries `contentPreview` and `summaryPreview` per row, and **it fires three times in this journey** — while in the Builder its primary consumer is a modal that **may never be opened**. Fetch on picker open; pass an explicit `limit`. **⚠️ VERIFY EACH REMAINING `noteById` CONSUMER FIRST (`:1031`, `:1738`, `:1852`) — if any needs a note NOT in the collection, that path must TRIGGER the lazy load rather than read an empty map.**

**(7) SWEEP THE DETAIL PAGE'S SIX REFETCH SITES** (audit sequencing 5) — `getCollection` at `:3000`, `:3081` and `getCollectionGoal` at `:3002`, `:3083`, `:3645`, `:4010`. Apply item 5's rule uniformly: after any mutation that returns `NoteCollectionDetailResponse`, consume it instead of refetching. **⚠️ AUDIT EACH SITE INDIVIDUALLY — some follow mutations that genuinely invalidate more than the collection.**

### Anti-drift

**⚠️ AUDIT SEQUENCING 4 (batch the Goal children read) IS OUT, AND THE REASON IS ITS SHAPE, NOT ITS VALUE.** The Goal path is a real HTTP N+1 — a Review Set with 20 plans costs **21 requests** and ~147 queries to render one page — but fixing it is a **RESPONSE-CONTRACT CHANGE** spanning a DTO, a service and the client, which is Codex-routed and a different verification tier. **⚠️ Do NOT take it opportunistically because `refreshBuilder` is already open in the diff.** It carries a Backlog Index row.

**⚠️ FIVE THINGS THAT LOOK LIKE THE FIX AND ARE NOT — each rejected with its reason recorded, four of them load-bearing corrections this repo has already paid for:**
- **Do NOT remove the case-snap (`:1652`).** It prevents lookalike sections, and its removal reintroduces a defect the code documents at `:1662-1664`.
- **Do NOT "fix" this by collapsing whitespace in the BACKEND.** That silently rewrites stored learner data on an unrelated write path **and masks the ingress rather than closing it** — and the ingress is still unresolved.
- **Do NOT restructure `toItemResponses`** — it issues **five bulk queries keyed by `noteIdIn` with no N+1 inside the item loop**, so one read costs the same at 5 notes or 500. It is the part that usually goes wrong and it is already right.
- **`refreshBuilder`'s `skipNotes` OPTION AND ITS COMMENT STAY** — a recorded fix for a measured problem. **Paths that genuinely change the note set must keep passing `skipNotes: false`.**
- **The DEFERRED-SAVE MODEL STAYS (`v0.96.0`)** — autosave-per-drop raced itself and was fixed at cost; **reducing requests must not reintroduce it** — and **KEEP the flush-first behaviour** (`savePendingLeafOrder({ refreshAfter: false })`), which is a correctness guard, not a spare request.

**⚠️ The `editing`-gated debounced combobox writer STAYS (`v0.88.0`)** — `CLAUDE.md` forbids removing it and its comment, and it is 500 ms of exactly the machinery this release is touching.

**⚠️ Do NOT raise the connection pool** — `AppConfig:52-72` records that bound as a `v0.112.0` Phase 3 decision gated on `[CHECKPOINT — due 2026-10-04]`. **⚠️ Do NOT trim `contentPreview`/`summaryPreview`** — `v0.100.0` rejected it because it only **moves the threshold**, which is what caused the 2026-08-31 build failure in the first place.

**⚠️ `frontend/app/onboarding` STAYS FROZEN — `[CHECKPOINT — due 2026-09-11]` is FIVE DAYS OUT.** **⚠️ NO Learning Connections promotion before `2026-09-19`** — that kill criterion keys on `ACCEPTED` and is 13 days out. **⚠️ NO migration; no backend change beyond reading it; no quota, entitlement, limit or meter change; no new mode or sub-mode.**

### Verification

**ONE SCOPED COLD AGENT framed as FALSIFICATION.** **⚠️ THE TIER IS STATED AS A CONSEQUENCE OF AN OWNER DECISION RATHER THAN INHERITED: the recommendation was FOUR items (the loop fix plus sequencing 1 and 2), and the owner took SEVEN.** That fires the gate's named trigger outright — **two or more changes touching the same shared method**, here `refreshBuilder`, `persistLeafItems` and `handleLeafLabelChange`, which every one of the seven items reaches. **⚠️ It is NOT the three-agent tier:** frontend-only, no permission substrate, no cross-user read, no money or quota semantics, no migration.

**⚠️ PRE-DECLARED GUARDS, AND THE FIRST ONE NAMES THE FIXTURE THAT PROVES NOTHING: a label already single-spaced and under 120 characters PASSES UNDER BOTH THE DEFECT AND THE FIX.** The discriminating fixtures are **(a)** a stored label containing a **double space**, asserting the write is issued **AT MOST ONCE**, and **(b)** a label whose write is **REJECTED**, asserting **NO second attempt**. **⚠️ A third is owed at the performance end and has the same shape: assert the REQUEST COUNT — that `listNotes` is NOT called on the section-edit path — because a test asserting the label saved correctly passes under the defect by construction.** **⚠️ CARRIED LESSON: this repo has shipped two silent no-ops (`v0.116.0`, `v0.117.0`) whose tell was a behaviour change with NO test exercising it.**

**⚠️ A DEPLOY-SPLIT CAVEAT IS OWED AND IS RECORDED NOW, BEFORE THE READ, NOT DISCOVERED IN SEPTEMBER: `[CHECKPOINT — due 2026-09-18]` asks whether `connection-timeout: 5000` converts load into user-visible errors — and THIS RELEASE REDUCES CLIENT-DRIVEN BACKEND LOAD** (items 4-7 remove repeated unbounded `listNotes()` calls; items 1-3 remove an unbounded retry). **So a quiet read after this deploys is consistent with the timeout being adequate AND with the load simply having dropped, and the two cannot be separated after the fact.**

**Routing: CLAUDE CODE inline** — one component, one handler, one shared helper, plus a bounded sweep of six known call sites.

### Shipped

**Items 1-3 — the loop (inline).**

- **One canonical normalisation.** `canonicalSectionLabel` is now the single definition of the display
  form (trim + collapse internal runs, case preserved) and **`normalizeSectionValue` is DERIVED from it**,
  so the display and comparison forms cannot drift. It replaced **five duplicated copies** of
  `.trim().replaceAll(/\s+/g, " ")` in the builder. **⚠️ A unit test pins the derivation, and the mutant
  that re-inlines a divergent normalisation fails it.**
- **The retry is bounded, and this is the load-bearing half.** The card records the one transition it has
  already asked for as `(server label it saw) -> (label it requested)` and will not ask again until one of
  them actually moves. **⚠️ Recorded at FIRE time, not arm time — a cleared timeout never asked for
  anything, and recording it there would block a legitimate first attempt.**
- **The handler no longer re-arms the effect.** `onLabelChange` moved into a ref and out of the dependency
  array. **⚠️ THE FINDING ASKED FOR A `useCallback` AND THAT IS DELIBERATELY NOT WHAT SHIPPED — recorded so
  a later session does not read item 3 as skipped:** `handleLeafLabelChange` closes over `moveLeafNote`,
  itself a plain function, so memoizing it requires memoizing `moveLeafNote` → `persistLeafItems` →
  `savePendingLeafOrder` — a cascade landing on the deferred-save model `v0.96.0` fixed at cost and this
  release's anti-drift locks. The ref achieves the stated purpose (a stable dependency) without it.
  **⚠️ It is a churn reduction, not the loop fix.**

**⚠️ THE FINDING'S DIAGNOSIS IS NARROWED BY IMPLEMENTATION, AND THE CORRECTION IS THE MOST USEFUL THING
THIS PR PRODUCED — its §4 left "which loop fired" open, and one of the two candidates turns out to be
UNREACHABLE.** Verified by reading, then empirically by a failing fixture:

- **`v0.95.1`'s card key is `${noteId}:${item.label ?? ""}`, so ANY write that CHANGES the label REMOUNTS
  the card** with a fresh `labelValue` read from the new label — the guard clears on its own. **So every
  successful, value-changing write self-heals, under the defect as well as the fix.**
- **⚠️ SUPERSEDED AT SIGNOFF — READ THE COLD-AGENT SECTION BELOW BEFORE TRUSTING THIS BULLET. The claim is TRUE for the CASE axis and FALSE as a general statement about Loop B**, because the WHITESPACE axis was never checked and does not snap. Corrected in place rather than left standing, because this repo has already paid for one obligation described by two rows where only one carried the correction.
- **Loop B cannot fire ON THE CASE AXIS, through the combobox.** `SuggestionCombobox.handleInputChange` calls
  `onChange(matchedOption?.value ?? nextValue)` — it **snaps a typed value to a matching option at the
  INPUT layer** — so `labelValue` can never diverge in case from an existing section. A fixture built to
  reproduce it issued **zero** writes, and instrumenting the source showed the guard returning early
  because `labelValue` had already been snapped to `"Algebra"`.
- **⚠️ SO THE REACHABLE LOOP IS LOOP A — A WRITE THE SERVER REFUSES — AND IT IS MORE GENERAL THAN THE LABEL
  CASE:** the rollback leaves `item.label` untouched while the error state forces a re-render, so **any**
  failed section-label write retried forever. Not just an over-long label: a 500, a network failure or any
  validation refusal did it.
- **⚠️ SUPERSEDED AT SIGNOFF — THE INGRESS WAS FOUND AND CLOSED. It is `bulk-generation-page-client.tsx:416`/`:282`; see the cold-agent section below.** The reasoning that follows is kept because it is what NARROWED the search — ruling out a length refusal is why the bulk path was the remaining candidate.
- **The ingress was unresolved as of this PR.** Client and server both cap the label
  at **120** (`LABEL_MAX_LENGTH`; `NoteCollectionService:2806`; `V72` `VARCHAR(120)`) and the input
  truncates on the way in, so a length refusal is not obviously reachable — which sharpens the open
  question rather than answering it. The finding's §7 detection query and its §8 bulk-path check are still
  owed.

**Guards — two, both mutation-verified with the killing test named.** **⚠️ AND THE FIXTURE CHOICE IS THE
GUARD: a SUCCEEDING write proves nothing here, because the remount clears the condition under the defect
too.** An earlier draft of these tests passed against the un-fixed code for exactly that reason and was
rewritten.

| Mutant | Test that failed |
|---|---|
| remove the retry bound (restore the loop) | *does not retry a section-label write the server rejected* |
| stop deriving `normalizeSectionValue` from the canonical form | *is exactly normalizeSectionValue without the lowercasing* |

`tsc --noEmit` exit 0 · `npm run lint` exit 0 · `npm test` exit 0, **2224 passed / 201 suites**.

**Items 4-7 — the request layer (inline).**

- **The write consumes its own response (items 4 and 5, ONE mechanism, not two).** `PUT
  /collections/{id}/items/order` already returns the same `NoteCollectionDetailResponse` that
  `getCollection` returns; `persistLeafItems` **discarded it and then refetched that payload plus the
  curator's entire note library.** It now applies the response directly through a new `applyLeafDetail`
  helper, extracted from `refreshBuilder`'s own leaf branch so both paths set state identically.
  **⚠️ ITEM 4 (`skipNotes: true`) IS THE AUDIT'S OWN "MINIMUM VIABLE VERSION" OF ITEM 5, so shipping
  both on the same path would leave a redundant flag on a call that no longer happens.** The leaf path
  takes item 5 and makes **no follow-up request at all**; the **Goal path keeps `refreshBuilder`** — the
  response does not carry the child fan-out — **and takes `skipNotes: true` there**, which is where
  item 4 actually lives.
- **The note library is lazy (item 6).** `listNotes()` — unbounded, `contentPreview` and
  `summaryPreview` per row — used to fire on collection load, on builder load, and again after every
  non-drag mutation. It now loads **on picker open**. **⚠️ CORRECTED AT THE `v0.124.0` SIGNOFF, IN
  PLACE RATHER THAN SILENTLY REWRITTEN: THIS ITEM WAS BUILDER-ONLY, AND THE TWO SENTENCES BELOW
  OVERCLAIMED IT.** `collection-detail-page-client.tsx` was never in this release's diff — `git log
  v0.122.0..v0.123.0 -- <that file>` is EMPTY — and its own `listNotes()` at `:2832` kept firing on
  every collection open. Item 6's scope text and its `:1031`/`:1738`/`:1852` refs are all BUILDER line
  numbers; the summary generalised past them. **`v0.124.0` closed the detail-page half.** **⚠️ EVERY CONSUMER WAS VERIFIED DOWNSTREAM OF THE
  PICKER rather than assumed**, which is the audit's own precondition: all three `noteById` maps sit on
  add-note paths that cannot be reached without opening it, so nothing can read an empty map expecting a
  note outside the collection. `refreshBuilder` still refreshes the list for paths that change the note
  set — **but only once it exists**.
- **⚠️ THE AUDIT'S "BOUND IT" SUB-ITEM IS DELIBERATELY NOT TAKEN, AND THE REASON IS A REGRESSION IT DOES
  NOT RECKON WITH:** the picker filters **client-side over the whole library**, so passing a `limit`
  would silently make notes beyond it **unaddable**. The audit names server-side picker search as the
  longer-term fix; until that exists, bounding the fetch trades an invisible performance win for an
  invisible correctness loss. Lazy loading removes the fetch from the BUILDER's page load (see the
  correction above); the collection detail page's own call was closed separately in `v0.124.0`.

**⚠️ ITEM 7 IS CLOSED AS NOT-APPLICABLE, AND THIS IS AN EXECUTED RESULT RATHER THAN A SKIPPED ITEM.** The
audit named six detail-page refetch sites and instructed that each be audited individually because "some
may follow mutations that genuinely invalidate more than the collection." **All six turn out to be in that
category, and the audit's premise — that they follow a mutation returning `NoteCollectionDetailResponse` —
does not hold at any of them:**

- **`:3000`, `:3002`** are inside `refetchAfterFailure`. There is **no successful mutation response to
  consume** — resyncing after a failure is the whole point.
- **`:3081`, `:3083`** follow `removeCollectionItem`, which is **`Promise<void>`** — the DELETE returns no
  body, so there is nothing to consume. Making it return a detail is a **response-contract change**, which
  is precisely the shape this release put out of scope for sequencing 4.
- **`:3645`, `:4010`** are `getCollectionGoal` refetches that **already carry comments explaining why they
  must stay**: `weeksRemaining`, `conceptsRemaining` and `todaysConceptBudget` exist **only** on
  `GoalCollectionDetailResponse`, not on the `NoteCollectionDetail` the modal saves, so a client-side field
  copy cannot refresh them.

**⚠️ SO IMPLEMENTING ITEM 7 AS WRITTEN WOULD HAVE BEEN A NO-OP AT BEST AND A REGRESSION AT WORST** — the
audit was written from a call-site list rather than from the mutations' return types. Recorded here so a
later reader does not re-derive it, and its Backlog row is updated.

**Guards — three, all mutation-verified with the killing test named.** **⚠️ The request-count assertion is
the guard: a test asserting the label saved correctly passes under the defect by construction, because the
defect was never about correctness.**

| Mutant | Test that failed |
|---|---|
| restore the unconditional `refreshBuilder()` | *consumes the write's own response instead of refetching the collection and the note library* |
| take item 4 alone (`skipNotes` but still refetch the collection) | *consumes the write's own response …* |
| restore the eager note fetch on page load | *does not download the note library until a picker is opened* |
| make the list lazy but never load it | *does not download the note library …* **and** *adds notes into one subject through the existing add-items endpoint* |

**⚠️ ONE TEST-HARNESS CORRECTION WAS OWED AND IS RECORDED: the default `setCollectionItemOrder` mock
returned a FIXED, UNRELATED collection's detail.** Harmless while the client discarded it; once the client
consumes it, that mock fed the builder another collection's items. It now defers to whatever
`getCollection` is configured to return for the same id, **which is what the server actually does** — and
the `v0.95.1` stale-card-state guard passes unchanged against the faithful mock.

`tsc --noEmit` exit 0 · `npm run lint` exit 0 · `npm test` exit 0, **2226 passed / 201 suites**.

**Cold-agent findings — the declared verification, and it refuted two of six claims (inline).**

**⚠️ THE WORST FINDING IS A DEFECT THIS RELEASE INTRODUCED, IN THE SAME CLASS IT EXISTS TO CLOSE.** Item 6's
`refreshNotes` was `try/finally` with **no `catch`**, and `refreshingNotes` was a dependency of the
picker-open effect: a REJECTED fetch cleared the flag in `finally`, the deps changed, the effect re-ran and
refetched — **at event-loop speed, against the unbounded `listNotes()` endpoint, precisely when the backend
is already failing.** The agent measured **3,743 calls in five seconds**. The pre-fix code could not do
this: `listNotes()` lived inside `refreshBuilder`'s `Promise.all`, so a rejection propagated to the caller.
**⚠️ It fires under exactly the conditions `[CHECKPOINT — due 2026-09-18]` exists to measure.** Fixed with a
`catch` plus a one-attempt bound; the modal's Refresh control remains the deliberate retry.

**⚠️ `C2` WAS WRONG, AND THE CORRECTION IS NARROWER THAN THE AGENT'S TOO — BOTH ARE RECORDED.** This release
claimed the case-snap loop was unreachable. **The case axis is** (the combobox snaps typed input to a
matching option), **but the WHITESPACE axis was not checked and does not snap:** `getSectionName` and
`getSectionLabel` used bare `.trim()` and never collapsed internal runs, so a stored
`"Cash  and Receivables"` was a section name the canonical form could never equal. **⚠️ BUT THE AGENT'S
"the identical loop fires" IS ALSO OVERSTATED, AND THIS WAS ESTABLISHED BY MUTATION RATHER THAN ARGUMENT:
the retry bound from items 1-3 ALREADY caps it at ONE write.** Its unbounded reproduction ran against
`eb8d1940` — the kickoff commit, before the bound existed. **The true residual on merged code was one
pointless no-op write per affected card per page load, plus a label that could not be repaired through the
combobox** (every attempt was the suppressed transition). Both helpers now use `canonicalSectionLabel`,
which completes scope item 1's *"one normalisation on both sides"*, **repairs such a label on its next
write**, and folds lookalike sections.

**⚠️ THE INGRESS IS CLOSED — the finding's §8 obligation, and it was the bulk path all along.**
`bulk-generation-page-client.tsx:416` sent `sectionLabel.trim()` (no collapse) and **`:282` pre-fills that
field from the free-text Subject**, so a doubled space in a pasted Subject minted the label with no action
on the Section field at all. `buildAdoptedItems` copies labels verbatim, so a published source plan carrying
one **propagated the wedge to every adopter** — the amplification this release's own scope names. Now
canonicalised at the authoring surface. **⚠️ The BACKEND is deliberately unchanged and still stores what it
is given**, per anti-drift: collapsing there would rewrite stored learner data on an unrelated write path.

**Also fixed:** the picker rendered **`"No notes available."`** — a terminal claim about the library — during
the async lazy load and permanently after a failed one; loading and failure now have their own copy. And a
write the server REFUSED left that exact value **unsavable** (retyping the same name did nothing), so
**focus now clears the retry bound** — the loop is render-driven and never touches focus, so it cannot
reach that escape, while a curator refocusing the field unambiguously can.

**⚠️ TWO PROCESS FAILURES, RECORDED BECAUSE THEY ARE THE REUSABLE PART. (1) THE PRE-DECLARED GUARD THAT WAS
SKIPPED IS THE ONE THAT WOULD HAVE CAUGHT `C2`.** This release's own scope named two discriminating fixtures
— *"(a) a stored label containing a DOUBLE SPACE … and (b) a label whose write is REJECTED"* — and **only
(b) was written.** Fixture (a) is the whitespace case verbatim. **(2) THE REPLACEMENT GUARD THEN SURVIVED
ITS OWN MUTATION TWICE BEFORE IT DISCRIMINATED.** The first draft advanced timers in **one large jump**,
which fires only the first callback in a timer→write→re-render→timer chain; the second asserted a **call
count**, which the retry bound already satisfies. Only asserting the **written payload** killed the mutant.
**⚠️ Neither would have been caught by reading — both were found by running the mutant.**

**Guards — four added, all mutation-verified with the killing test named.**

| Mutant | Test that failed |
|---|---|
| revert `getSectionName`/`getSectionLabel` to bare `trim()` | *repairs a stored section label that differs only by internal whitespace, in one write* |
| remove the lazy-load attempt bound / the `catch` | *stops re-fetching the note library after a failed lazy load* |
| stop clearing the retry bound on focus | *lets a curator retry a section label the server refused* |

**⚠️ ONE EXISTING ASSERTION IS VACUOUS AND IS ANNOTATED RATHER THAN DELETED:** in *consumes the write's own
response*, the `listNotes` expectation cannot fail while item 6 ships (the list is lazy, so it is never
fetched there either way). The `getCollection` assertion is what discriminates; the comment says so.

**⚠️ CONFIRMED UNREFUTED: `C1`** (the retry bound works — `[1,1,1,…]` against `[2,3,4,…]` on pre-fix
source), **`C3`** (the card key remounts on a value change), **`C4`** (`applyLeafDetail` matches the original
leaf branch exactly; `childCount` 0→non-zero falls through to the Goal path), **`C6`** (item 7
not-applicable, all six sites re-verified, no seventh site). **No anti-drift violations found.**

**⚠️ ITEM 3 REMAINS UNGUARDED AND IS DISCLOSED RATHER THAN PINNED:** reverting `onLabelChangeRef` passes the
whole suite. That is consistent with this release calling it *"a churn reduction, not the loop fix"*, but
the repo has shipped two silent no-ops whose tell was exactly this, so it is stated plainly.

`tsc --noEmit` exit 0 · `npm run lint` exit 0 · `npm test` exit 0, **2229 passed / 201 suites** (four
consecutive clean full runs; one earlier run exited 139 on a worker crash with zero failing suites and did
not reproduce).

## v0.122.0 - Shared Quiz Discoverability

**Status: Released** (kicked off and signed off 2026-09-06, base branch `releases/v0.122.0`, cut from `main` after `v0.121.0` merged and tagged)

**⚠️ THIS RELEASE EXISTS TO MAKE `v0.121.0`'s OWN CHECKPOINT ANSWERABLE, AND THAT IS A DEPENDENCY RATHER THAN A THEME.** `[CHECKPOINT — due 2026-10-07]` asks *"has the shared-quiz capability been PROMOTED, so that any recipient metric has a denominator at all?"* and its kill criterion states outright: **"if the capability has NOT been promoted by this date, the distal checkpoint is RE-DATED, never answered."** The distal row (`[CHECKPOINT — due 2026-12-04]`) carries `v0.121.0`'s entire thesis — that the recipient experience was what suppressed use. **⚠️ So shipping anything else next spends the window and answers neither row.**

**⚠️ THE ALTERNATIVE WAS SLICE 5 AND IT WAS DECLINED ON EVIDENCE, NOT PREFERENCE (owner, 2026-09-06).** A read-only production query the same day returned **1 `linked_learner_relationships` row, 1 `ACCEPTED`, 0 `PENDING`, 2 invitations, 3 live grants (1 `PROGRESS`, 2 `ACTIVITY`)**. Slice 5 enriches a **supporter** surface, so it would have built for a population of one — and worse, added a **second** unpromoted surface inside the checkpoint's own window, leaving two capabilities silent instead of one.

**⚠️ AND THE SCOPE LINE IS SET BY A CHECKPOINT RATHER THAN BY TASTE — THIS IS THE FINDING THAT SHAPED THE RELEASE.** The instinct is to promote **both** silent capabilities at once. **That is wrong, and the reason is dated: `[CHECKPOINT — due 2026-09-19]` (`v0.89.0`, *"does anyone actually form a learning connection?"*) is THIRTEEN DAYS OUT, and its kill criterion keys on `ACCEPTED`** — *"if ZERO relationships have reached `ACCEPTED`, the demand hypothesis is unsupported — stop investing in this direction and do NOT build further phases on it."* **Promoting Learning Connections inside that window would contaminate the one number that decides whether the whole arc continues.** So this release promotes **shared quizzes ONLY**.

**⚠️ THE `2026-09-19` READ ALSO INDEPENDENTLY CONFIRMS THE SLICE 5 DECISION, AND ITS ROW IS UPDATED IN THIS KICKOFF COMMIT RATHER THAN LEFT STALE.** That row carries a mid-window observation from **2026-08-26: the table was EMPTY, zero rows of any status**. Today's read supersedes it factually — **1 relationship, 1 `ACCEPTED`** — so the kill criterion does **not** fire, and the row's own instruction for a non-zero count applies verbatim: ***"A non-zero count, however small, means re-read at the next release rather than expand."*** **⚠️ Recorded as a mid-window observation and NOT as the verdict; the denominator clause (~381 accounts, needs TWO to act in concert) still governs and the verdict is due 2026-09-19.**

**⚠️ THIS IS *NOT* `v0.109.0`'s STALE-GUIDE SHAPE, VERIFIED BY READING RATHER THAN ASSUMED FROM THE PRECEDENT.** That release's central finding was that `board-exam-guide.tsx` contained the string *"Review Set"* **zero times**. **Here the Help Center is already correct**: `export-sharing-guide.tsx:18` lists *"Quiz for someone — a link anyone can open and answer without an account"*, and `learning-connections-guide.tsx:35` explains the whole flow. **⚠️ Do NOT scope a guide rewrite — there is nothing stale to fix, and "sweep the guides" is the wrong lesson to carry over from `v0.109.0`.**

**⚠️ THE MEASURED GAP IS IN-APP ANNOUNCEMENT AND MARKETING, AND BOTH NUMBERS WERE COUNTED AT KICKOFF:** the product ships **eleven** guidance tips (four on Library, three on Dashboard, two on note detail, two on collection detail) and **not one** mentions sharing a quiz with anyone; `app/page.tsx` names **Board Exam (4), Interview Practice (1), Adaptive Practice (2)** and shared quizzes **zero** times; `app/how-it-works/page.tsx` names **Board Exam (7)** and nothing else. The entry point itself is fine — it is a live menu item at `private-note-detail-page-client.tsx:2535` — **so nobody needs to WIRE anything.**

### Planned Scope

**(1) An in-app announcement where learners already are.** A one-time tip on the note detail page, routed through `pickActiveGuidance()` like every other tip. **⚠️ MODELLED VERBATIM ON `assessment-covers-whole-plan` (`collection-detail-page-client.tsx:3372`), INCLUDING ITS COMMENT — that tip is this repo's own solution to exactly this problem, and its comment is what stops a later session "improving" it into a second entry point.** **⚠️ IT HAS NO ACTION BUTTON, DELIBERATELY.** The *Quiz for someone* menu item already exists; adding a second click target for the same capability would confound the very read this release un-blocks.

**⚠️ THE CONDITION IS DECIDED AT KICKOFF BECAUSE AN UNREACHABLE ONE IS THIS REPO'S MOST EXPENSIVE RECURRING DEFECT — `v0.116.0` widened a predicate that could never fire and `v0.117.0` added a callback to a branch nothing renders, both shipping green and both found only by a cold agent.** Fire on `isStudyPackReady && !note?.generatedQuiz` — **both inputs verified to exist at kickoff** (`isStudyPackReady` at `private-note-detail-page-client.tsx:984`; `generatedQuiz: GeneratedQuizResponse | null` on the note type, `lib/api.ts:1799`) — which is precisely the population that can act and has not: a learner holding a quiz-ready note who has never made a shared quiz. **⚠️ It must sort BELOW the two existing note-detail tips (both `priority: 10`), since `pickActiveGuidance` shows exactly one and ordering is how they coexist.**

**(2) The marketing surfaces name the capability.** `app/page.tsx` and `app/how-it-works/page.tsx` describe a product in which sharing a quiz with someone does not exist. **⚠️ THE SHAPE IS DECIDED AT KICKOFF, NOT LEFT TO IMPLEMENTATION, BECAUSE *"name the capability"* COMES BACK AS A HERO SECTION:** the landing page already lists its assessment modes as sibling lines, so this is **ONE MORE LINE BESIDE THEM** — not a new section, not a hero, not a re-ordering of what is already there. **⚠️ COPY ONLY — no new route, no new component, no pricing or plan claim** (`v0.76.0`'s money-surface doctrine keeps pricing copy out and makes positioning an OWNER decision rather than an implementation one), **and do NOT claim it is new** — it has shipped since `v0.110.0`.

### Anti-drift

**⚠️ LEARNING CONNECTIONS PROMOTION IS OUT, AND THE PROHIBITION IS DATED RATHER THAN PERMANENT — IT UNBLOCKS AFTER `2026-09-19`.** Two specific temptations are named because **both look like copy and neither is**: **(a) do NOT move `Learning connections` from `secondaryNav` to `mainNav`** (`app-shell.tsx:421`, where it currently sits beside Profile / Settings / Help) — that is a **reachability change to the surface under measurement**, not a tidy-up; **(b) do NOT add any guidance tip naming supporters, connections or linked learners.** A later session reading only "the nav item is in the wrong menu" will find that an obvious fix, which is why the reason is written here.

**⚠️ NO SECOND ENTRY POINT — `v0.109.0`'s explicit rule, and it binds here for the same reason it bound there:** *"do NOT add a second entry point because the capability feels hidden — that solves the wrong problem AND confounds the very reads item 3 re-arms."* The CTAs exist and work; **there is nothing to wire.**

**⚠️ NO BEHAVIOUR CHANGE.** This release changes what the product **says**, not what it does. **⚠️ NO migration; no quota, entitlement, limit or meter change; no new mode or sub-mode; no `ProfileType` gate.**

**⚠️ Do NOT change what `QUIZ_SHARE_LINK_OPENED` or `QUIZ_SHARE_LINK_COMPLETED` record, or their firing conditions.** `[CHECKPOINT — due 2026-12-04]` reads both, and its denominator correction depends on `OPENED` keeping **exactly** its two existing fire sites — page load with `metadata = {token}`, and the results-screen signup CTA with `metadata.source = "shared_quiz_results_signup_cta"` — so that the read can filter `metadata.source IS NULL`. **⚠️ Do NOT "tidy" the CTA onto its own event type; `v0.121.0` deliberately left it alone.**

**⚠️ `frontend/app/onboarding` STAYS FROZEN — `[CHECKPOINT — due 2026-09-11]` is FIVE DAYS OUT, the closest this constraint has ever been to its date.** A promotion release is exactly the shape that violates it, because the instinct for *"nobody knows this exists"* is to say so during first run. **Do NOT add, remove or reorder an onboarding FLOW step.**

**⚠️ Do NOT scope slice 5 (`Learning Connection integration`), and do NOT scope `quiz-assignments-learning-connections-stage1.md`**, which its own author marks NOT SCHEDULED. **⚠️ Do NOT record shared quiz results** (migration plus a privacy decision, `v0.121.0` §1B). **⚠️ Do NOT weaken `uq_generated_quizzes_note_id`.**

**VERIFICATION: a single `advisor()` call on the diff** — copy plus one guidance tip; no permission substrate, no cross-user read, no money semantics, no migration, and by construction no behaviour change.

**⚠️ THE VERIFICATION RISK IS UNUSUAL AND IS NAMED DELIBERATELY, CARRYING `v0.109.0`'s OWN LESSON: A COPY RELEASE HAS NO FAILING TEST TO CATCH A FALSE CLAIM.** Anchor every marketing claim to the code that implements it, and prefer a pinned string in a test over prose nothing checks.

**⚠️ PRE-DECLARED GUARD, aimed at what this release could silently ship as a no-op: the tip's `condition()` must be asserted TRUE under a realistic fixture — a quiz-ready note with no generated quiz — and FALSE once one exists.** A test that merely asserts the rule is present in the array **passes under an unreachable condition and proves nothing**, which is the exact shape of `v0.116.0` and `v0.117.0`. **⚠️ A second guard is owed at the ordering seam: with an existing note-detail tip unseen, `pickActiveGuidance` must still return THAT tip, not this one.**

**Routing: CLAUDE CODE inline.**

### Shipped

**(1) The note detail page announces that you can send someone a quiz.** A one-time tip through
`pickActiveGuidance()`, firing on `isStudyPackReady && !note?.generatedQuiz` — the population that can act
and has not.

- **⚠️ IT SHARES ONE RULE ARRAY WITH `copied-study-pack-regenerate-hint`, AND THAT IS LOAD-BEARING RATHER
  THAN TIDINESS.** The two note-detail tips were in **separate** `pickActiveGuidance` calls, and
  `priority` cannot order across arrays — the function returns exactly one rule *per array*. Left split,
  both tips render at once on a **copied, quiz-ready note**, which is a state that genuinely occurs, and
  the pre-declared ordering guard would have had **no subject to assert against**. `quiz-tab-full-notes-nudge`
  keeps its own array: it renders inside the quiz tab beside `PracticeQuizCard`, and co-rendering there is
  today's behaviour, not something this release introduced.
- **⚠️ THE ANNOUNCEMENT HAS NO ACTION BUTTON, AND THE SHARED RENDER SLOT NEARLY TOOK THAT AWAY.** That slot
  passed `action={{ label: "Regenerate", … }}` **unconditionally**; merging the arrays without gating it
  would have shipped the announcement with a Regenerate button — an action on a tip specified to have none,
  wired to the wrong handler. The action is now gated on the rule id, and **a guard pins its absence**.
- **⚠️ THE COPY DELIBERATELY DOES NOT NAME THE CONTROL, which is a correction to the obvious
  implementation.** The two populations reach this through **different** affordances — the *Quiz for
  someone* menu item is gated `!isTeacherMode && isStudyPackReady` (`:2518`), while a teacher gets the
  *Generate Quiz* button (`:2887`) — so naming either one makes the tip false for the other half of its own
  audience. The condition covers both, so neither is told about something it cannot reach.
- **⚠️ `trackAnalytics` IS ON FOR THE ANNOUNCEMENT — REVERSED AT SIGNOFF, AND THE REVERSAL IS RECORDED
  BECAUSE THE FIRST DECISION WAS WRONG.** It was delivered OFF on the reasoning that it is *"a new event
  stream in a release that states no behaviour change"*. **Both halves of that are false.**
  `GUIDANCE_TIP_SHOWN` **already fires from three sites**, so this adds a `tipId` value to an existing
  stream rather than a new event; and the release's own anti-drift bars a *new event*, which this is not.
- **⚠️ THE SIGNOFF CHECKPOINT GATE CAUGHT IT, AND THE PRECEDENT WAS IN THE SAME FILE ALL ALONG.**
  `generate-quiz-combined-multi-note` (`:3546`) carries `trackAnalytics` with a comment stating it *"is
  what makes the `v0.110.0` checkpoint readable — the outcome metric cannot separate 'nobody was told'
  from 'nobody wanted it'."* **Same capability, same outcome metric, same confound.**
  **⚠️ WITHOUT IT `[CHECKPOINT — due 2026-10-07]` WOULD HAVE BEEN DECORATIVE BY THE GATE'S OWN
  DEFINITION:** that row concludes *"the question becomes whether shared quizzes are wanted at all"* when
  promotion happened and the link floor is unmet — **an inference that is INVALID unless we know the
  announcement reached anyone.** `GUIDANCE_TIP_SHOWN` supplies the denominator.
- **⚠️ It is scoped to the announcement, not the shared slot** — the copied-pack hint has no dated read and
  would only add noise. **Both directions are pinned**, and the mutant that turns it on wholesale fails a
  named test.

**(2) The marketing surfaces name the capability.**

- `app/page.tsx` gains **one sibling line** in the mode showcase, beside the existing *Also: Interview
  Practice* band. **⚠️ NOT a sixth `studyModes` card, and the reason is a false claim rather than the
  locked contract alone:** that section's own heading reads **"Five study modes, one workspace"** and its
  grid is `lg:grid-cols-5`, so an entry there would make the page **state something untrue** — in the one
  release whose named risk is exactly that. **⚠️ It carries NO plan chip** (the capability is gated on
  nothing) and uses the page's sky accent rather than the amber the Interview Practice band uses to mean
  *Pro · Professional profile*.
- `app/how-it-works/page.tsx` gains a fourth **value** entry. **⚠️ Deliberately in `valueSummaries` and not
  in `steps`, whose heading counts its own items ("Simple 3-Step Flow") and whose cards render
  `Step {n}`** — a fourth step would falsify the heading. The grid widens to `md:grid-cols-2 lg:grid-cols-4`
  so the entry does not strand a card alone on a second row. **⚠️ DISCLOSED BECAUSE IT IS THE ONE EDIT IN
  THE RELEASE THAT IS NOT STRICTLY COPY: that grid-arity change reflows the THREE PRE-EXISTING CARDS at the
  `md` breakpoint (3-up becomes 2-up), not only the new one.** **Naming the capability was chosen over
  extending a neighbouring description, because the point of the item is that it APPEARS AT ALL.**
- **⚠️ NO CLAIM ABOUT SEEING THE RECIPIENT'S SCORE, ON ANY SURFACE, AND THIS IS THE COPY INSTINCT THAT WOULD
  HAVE SHIPPED A LIE:** `getSharedQuizResults` grades **in memory with zero `.save(`** (`v0.121.0`'s
  contradiction B), so *"see how they did"* is false. **⚠️ Nothing claims the capability is new** — it
  shipped in `v0.110.0`. Both prohibitions are written into the code comments at each site.
- **⚠️ ALL THREE STRINGS REUSE THE HELP CENTER'S CANONICAL WORDING VERBATIM** (`export-sharing-guide.tsx:18`)
  — *"a link **anyone** can open and answer without an account"* — so one claim is not quietly two.
  **`anyone` is the load-bearing word: it is the claim that a recipient needs NO ACCOUNT AND NO
  RELATIONSHIP**, which is the property `v0.110.0` shipped and the note-detail menu item's own comment
  protects. A first draft said *"they"*, which weakened it without looking like a change; **do not
  re-soften it.**

**(3) `docs/features/guidance.md`'s tip table is repaired to match code — added at owner request after the
release's own doc re-read surfaced the drift.** **⚠️ THE FIRST COUNT WAS WRONG AND THE CORRECTION IS THE
POINT: it was reported as FIVE missing tips and is EIGHT**, because the first sweep grepped literal
`tipId="…"` strings and **rule-based tips pass `{rule.id}`**, so all three Dashboard tips were invisible to
it. The table documented **11** of the **20** tips that ship.

- Added: `dashboard-post-completion`, `teacher-dashboard-intro`, `dashboard-review-rhythm`,
  `post-adopt-target-date`, `assessment-covers-whole-plan`, `quiz-tab-full-notes-nudge`,
  `teacher-docx-export`, `teacher-note-content-quality`.
- **⚠️ TWO EXISTING ROWS WERE WRONG, NOT MERELY ABSENT, AND BOTH OVERSTATED A TIP'S REACH.**
  `note-detail-try-quiz` was documented as firing **always**; it actually requires the Performance Overview
  to be **expanded** AND **zero attempts on both** Quick Review and Challenge Quiz — a first-run nudge, not
  a permanent fixture, so anyone reasoning from the table had it backwards. `library-study-plan-grouping`
  omitted its `!selectionMode` clause. Both corrections are recorded in the doc rather than quietly swapped.
- The table is now **grouped by rule set with `priority` shown**, because within a set exactly one tip
  renders and priority is the only thing ordering them — the fact this release had to discover by reading.
- **Verified bidirectionally by script:** 20 ids in code, 20 rows in the doc, **nothing in code missing from
  the table and nothing in the table absent from code.** Rule ids were extracted by bracket-matching each
  `GuidanceRule[]` literal rather than by line-grep, which is what the first undercount got wrong.

**(4) A FALSE INSTRUCTION IN THE HELP CENTER, FOUND BY SWEEPING BY SURFACE RATHER THAN BY DIFF.**
`learning-connections-guide.tsx:35` read *"**From a note's actions menu**, choose Quiz for someone"* — but
that menu item is gated `!isTeacherMode` (`private-note-detail-page-client.tsx:2548`), so **a teacher has no
such item** and reaches the same capability through a button labelled **Generate Quiz**. The guide
instructed teachers to do something they cannot.

- **⚠️ THIS IS THE EXACT CLASS THE REPO ALREADY PAID FOR, MIRRORED.** `guidance.md` records
  `teacher-generate-quiz-multi-note` being replaced in `v0.110.0` because it *"rendered unconditionally
  while naming a `TEACHER`-gated CTA, so most of its readers were told to do something they could not."*
  Here it was the **non-teacher** CTA, failing the other population.
- **⚠️ THE RELEASE MADE IT WORSE BEFORE IT FOUND IT — which is why it is fixed here rather than deferred:**
  this release exists to **promote** the capability, so it drives more readers into that instruction. It
  was also **inconsistent with the release's own reasoning**, since the new tip deliberately does not name
  the control for precisely this reason.
- Fixed to name both: *"On a note with a ready Study Pack, choose Quiz for someone — teachers see the same
  action as Generate Quiz."* **⚠️ An earlier draft of the fix said only *Quiz for someone* and was STILL
  WRONG for teachers**, whose control carries a different label; the labels were read from code
  (`:2565`, `:2931`) rather than assumed.
- **⚠️ THE FILE WAS NOT IN THIS RELEASE'S DIFF and has never been in one when the behaviour it describes
  changed** — the standing sweep-by-surface rule names exactly that pattern. A guard now pins it, and was
  confirmed to **fail against the text that shipped**.
- **⚠️ It does NOT contaminate `[CHECKPOINT — due 2026-09-19]`.** The sentence describes **shared quizzes**,
  which require no connection; nothing about connection reachability changed, no nav item moved, and no tip
  naming supporters or connections was added.

**Guards — ten, all mutation-verified with the killing test named.** The pre-declared risk was a green
no-op, so each mutant was run and the named failure recorded:

| Mutant | Test that failed |
|---|---|
| drop `!note?.generatedQuiz` from the condition | *stops announcing … once a quiz for someone exists* |
| drop `isStudyPackReady` from the condition | *does not announce … on a note with no Study Pack* |
| `priority: 20` → `priority: 1` | *lets the copied-Study-Pack hint win over the share-a-quiz announcement* |
| pass the Regenerate action unconditionally | *gives the share-a-quiz announcement no action button* |
| remove the rule entirely | *announces the share-a-quiz capability on a quiz-ready note …* |
| rename the landing sibling line | *names the share-a-quiz capability beside the study modes …* |
| rename the how-it-works entry | *names the share-a-quiz capability without inflating the 3-step flow* |
| restore the guide's *"From a note's actions menu"* instruction | *names the share-a-quiz control for BOTH populations, not just the learner one* |
| revert `trackAnalytics` to OFF | *emits GUIDANCE_TIP_SHOWN for the announcement, so a low link count is readable* |
| turn `trackAnalytics` on for the whole slot | *does not emit a tip impression for the copied-pack hint, which has no dated read* |

Each marketing guard also pins the **counting heading** beside its claim, so a later session that promotes
the line into `studyModes` or `steps` fails a test rather than shipping a false count. **⚠️ `npm test`
2220 passed / 201 suites (exit 0), `tsc --noEmit` exit 0, `npm run lint` 0 errors and the same 18
pre-existing warnings as `HEAD`** — all four exit statuses read directly, not through a pipe.

### Known limitations

- **For a teacher the tip is redundant**, since *Generate Quiz* is already their primary button in exactly
  the state the tip fires in. Recorded as an observation rather than narrowed inline: the condition is the
  kickoff's verified one, and adding `!isTeacherMode` would be the larger deviation.
- **The announcement and `quiz-tab-full-notes-nudge` can both be on screen** when the learner is on the quiz
  tab. That is pre-existing behaviour for the copied-pack hint too, and is **not** a regression.

## v0.121.0 - Shared Quiz Recipient Experience

**Status: Released** (kicked off and signed off 2026-09-06, base branch `releases/v0.121.0`, cut from `main` after `v0.120.0` merged and tagged)

**SLICES 1-4 of `docs/claude-plans/shared-quiz-recipient-experience-plan.md` — EXECUTE ITS DECISIONS, DO NOT RE-DERIVE THEM.** Its companion is `docs/claude-plans/supporter-progress-visibility-audit.md`, and **the recipient plan's §8 supersedes the audit where they differ.**

**⚠️ THE ROADMAP BLOCKED THIS ON AN OWNER CONVERSATION, AND THAT CONVERSATION HAPPENED AT KICKOFF — the three contradictions in §1 are resolved below and MUST NOT BE RE-OPENED MID-IMPLEMENTATION.** (A) A combined quiz has **no source-note identity** — `CombinedQuizSection(String title, List<QuizItem> questions)` carries a copied title string and nothing else — so §13's *"multiple eligible public sources"* cannot ship for combined quizzes without storing note ids. (B) **Shared quiz results are never recorded** — `getSharedQuizResults` (`QuizShareLinkService:182`) grades in memory with zero `.save(`, so a readiness trend has no substrate; recording is **OUT OF SCOPE** (migration plus a privacy decision). (C) **No readiness history exists**, so a trend is impossible; §22's recent-results view is a projection change needing no migration.

**⚠️ THE JUSTIFICATION IS FIX-BEFORE-PROMOTE, NOT A LIVE DEFECT POPULATION — STATED AT KICKOFF RATHER THAN IMPLIED, AND MEASURED RATHER THAN ASSUMED.** A read-only production query on 2026-09-06 returned **8 `generated_quizzes` from ONE distinct generator** (2026-04-17 → 2026-09-05), **1 `quiz_share_links` row** (active, single-note, source note `PUBLIC`), and **0 `combined_quizzes`**. **⚠️ THE COMBINED-QUIZ ZERO IS UNINFORMATIVE — `v0.110.0` shipped that capability on 2026-09-04, two days before this read.** So the §2 defects are **confirmed in code and reachable**, but they are not currently harming a population: this is the `v0.109.0` shape, where *a read on an unpromoted capability measures DISCOVERABILITY, NOT DEMAND.* **⚠️ CONSEQUENCE FOR SIGNOFF: any checkpoint on recipient behaviour has a denominator near ZERO and needs the gate's TWO-TIER design — a proximal rate with a readable denominator carrying the kill criterion, plus a distal outcome carrying a denominator clause that says "not yet measurable" is a RE-DATE, not a verdict.** **⚠️ Do NOT let this become an argument for skipping the release — fixing a recipient path BEFORE promoting it is the correct order, and slice 1 is a correctness defect nobody should meet.**

**⚠️ SLICE 1'S DEFECT WAS PROVEN REACHABLE AT KICKOFF, NOT ASSUMED FROM THE DTO — this repo has twice paid for a guard whose subject could never fire (`v0.116.0`'s predicate, `v0.117.0`'s unrendered branch).** `generated_quizzes` is populated via `quizGenerationService.generateTeacherQuiz` (`GeneratedQuizService:126`), and **`teacher-quiz-developer.txt:23-26` EXPLICITLY INSTRUCTS the model to emit a matching block** — *"You may include 1 MATCHING block … set the same short `questionGroup` id such as `group-1` on each item."* `PublicQuizItem` is `(question, choices, concept, questionFormat)` with **no `questionGroup`** (present on `QuizItem:27`), and the recipient page branches on `isMultiSelect` alone (`quiz/[token]/page.tsx:82`). **So a matching block reaches a recipient as 2-4 consecutive MCQs with identical choices and no indication they form a block, each scored independently.**

### Planned Scope

**(1) Recipient assessment integrity — slice 1.** **⚠️ DECISION TAKEN AT KICKOFF: matching questions are EXCLUDED from shared quizzes, server-side.** Option B (carry `questionGroup`, reuse `QuizMatchingGroup`, change grading) is **REJECTED FOR THIS RELEASE WITH THE REASON RECORDED** — it pulls `isAnswerCorrect`'s matching path onto a `permitAll` route scored for an anonymous recipient and **changes the grading contract**, a verification-tier escalation rather than a preference; recorded as a follow-up, not rejected on merit.

**⚠️ THE FILTER POINT IS DECIDED AT KICKOFF, NOT LEFT TO IMPLEMENTATION, BECAUSE THE OBVIOUS PLACE IS WRONG AND FAILS LOUDLY ON EVERY SUBMIT — verified by reading both methods, not inferred.** `getActivePublicQuiz:156` builds the `PublicQuizItem` projection the recipient sees, while `getSharedQuizResults:188` **re-derives its own list** via `resolveSharedQuestions(link)` — the UNFILTERED `List<QuizItem>`. **So filtering in the projection alone leaves the grader walking a longer list, `answers.size() != questions.size()` (`:189`) throws `InvalidSharedQuizAnswersException`, and EVERY recipient submit 400s** — verbatim the `v0.110.2` shape. **⚠️ THE EXCLUSION MUST THEREFORE LAND ON THE SHARED DERIVATION (`resolveSharedQuiz` / `resolveSharedQuestions`) SO BOTH THE PROJECTION AND THE GRADER WALK THE SAME FILTERED LIST.** **⚠️ Do NOT filter in `getActivePublicQuiz`; do NOT relax the size check to paper over a divergence — the check is correct and is the thing that would catch this.** **⚠️ The stored owner-facing quiz is UNCHANGED — this is a projection, not a mutation (`v0.110.1`).**

**⚠️ THE SECOND HALF IS UNAMBIGUOUSLY LIVE AND NEEDS NO WIRE CHANGE, WHICH WAS CHECKED RATHER THAN ASSUMED: answers are APPEND-ONLY** (`const nextAnswers = [...answers, ...]`, `quiz/[token]/page.tsx:99`), so a recipient cannot correct a wrong answer. **The fix is an INDEX-ADDRESSED, FULL-LENGTH array, and the existing contract already accepts it** — the grader reads positionally and explicitly tolerates a null slot (`if (answer != null)`, `:203`), so **`null` is the representation of an unanswered question and `answers.size() == questions.size()` still holds.** **⚠️ Do NOT send a sparse or short array (the size check rejects it), and do NOT "fix" correctability by adding a confirm step.**

**(2) Mobile focus — slice 2, inline.** The result CTA overflows: `buttonVariants` carries `whitespace-nowrap` with a fixed `h-10` at `size=default` (`button.tsx:18,:31`), so a 54-character label in `w-full` can neither wrap nor grow. Authenticated recipients also see the app's bottom tab bar while answering (`app-shell.tsx:63,:510`).

**(3) Result legibility — slice 3, inline.** The bare concept string renders unlabelled directly under the stem (`page.tsx:274-275`) and reads as part of the question.

**(4) Continue learning — slice 4, and its shape is an OWNER-RULED CAPABILITY RULE rather than a product distinction.** **⚠️ RULED 2026-09-06: continue-learning may surface a source ONLY when NoteLib has a DURABLE source-Note identity AND the source is LEGITIMATELY ACCESSIBLE TO THE RECIPIENT at read/result time. NEVER infer source identity from a copied title.** Under today's architecture `generated_quizzes.note_id` qualifies (`V43:4`, `NOT NULL` with `uq_generated_quizzes_note_id`) and `CombinedQuizSection` does not, so combined recipients simply receive the normal result experience. **⚠️ IMPLEMENT THIS AS A PROVENANCE CAPABILITY, NOT A HARD-CODED SINGLE-NOTE BRANCH — the plan's §5 shape already fits: `PublicSharedQuizResponse` gains `sourceNotes: List<PublicSourceNote(id, title)>`, a LIST populated from whatever has durable identity, so a combined quiz yields an EMPTY list with no `isCombined` test anywhere.** **⚠️ THE ACCESSIBILITY HALF IS NOT OPTIONAL AND THE PLAN ALREADY ENCODES IT VERBATIM: populate ONLY from sources whose `visibility == PUBLIC`, resolved SERVER-SIDE (§5) — *"`visibility == PUBLIC`, full stop — relationship state is never an input to it"* (§8). ⚠️ PRIVATE SOURCES ARE OMITTED ENTIRELY — not counted, not hinted, not placeheld: no *"1 source is private"*, no *"2 of 3 available"*, no disabled card. If no source is public the field is empty and no continue-learning affordance renders.** **⚠️ When combined quizzes later gain trustworthy provenance, extend the SAME rule source-by-source rather than adding a second one. ⚠️ Do NOT add sharer-facing warning copy about the limitation (owner, explicitly); record combined source identity as a Known limitation and a follow-up row.**

### Anti-drift

**⚠️ SLICE 5 (Learning Connection integration) IS ITS OWN FOLLOW-UP RELEASE AND IS NOT IN THIS ONE — the plan's §11, its §29 and the ROADMAP sequencing all say so independently, and folding it would put a CROSS-USER READ and an IDENTITY DISCLOSURE into a UX release.** **⚠️ Do NOT record shared quiz results** — that is contradiction (B), needs a migration and a privacy decision, and the privacy half is already settled as *signed-in recipients only, within an accepted relationship, anonymous NEVER recorded*; it does not become in-scope because slice 4 is nearby. **⚠️ NO MIGRATION** (if one appears needed the scope is wrong). **⚠️ No quota, entitlement, limit or meter change; no new mode or sub-mode; `NoteVisibility` stays `PRIVATE | PUBLIC`.** **⚠️ Do NOT change `QUIZ_SHARE_LINK_CREATED`, `BOARD_EXAM_STARTED` or `ADAPTIVE_PRACTICE_STARTED` — several dated checkpoints read them.** **⚠️ `frontend/app/onboarding` STAYS FROZEN** — twelve dated reads fall between `2026-09-10` and `2026-09-17`, and the cluster is FOUR DAYS OUT. **⚠️ Do NOT scope `docs/claude-plans/quiz-assignments-learning-connections-stage1.md`** — its own author marks it NOT IMPLEMENTED, NOT SCHEDULED, and it exists precisely to stop this work drifting into the wrong abstraction (*"Sharing is lightweight. Assignment is relational."*). **⚠️ Do NOT re-open `v0.110.2`'s regenerate-deactivates-the-link decision, and do NOT drop or weaken `uq_generated_quizzes_note_id`.**

### Verification

**Slices 1-3: a single `advisor()` call on the diff** — no permission substrate, no cross-user read, no money semantics, no migration. **⚠️ SLICE 4 ESCALATES TO ONE SCOPED COLD AGENT framed as FALSIFICATION**, because it adds a field to an **anonymous `permitAll` payload** and the field's whole correctness is a visibility predicate. **⚠️ PRE-DECLARED GUARDS, each naming the fixture that would pass under the defect: a shared quiz whose source note is PRIVATE must return an EMPTY `sourceNotes` and render NO affordance — a public-source fixture passes under a version that leaks every source and proves nothing; a MIXED public/private multi-source case must list the public and omit the private WITHOUT TRACE; a recipient must be able to CHANGE an earlier answer and have the CHANGED value graded, not the first one; and a quiz whose generation emitted a MATCHING block must reach the recipient with those items ABSENT rather than ungrouped.** **⚠️ AND THE TRANSPORT LESSON FROM `v0.119.0` BINDS AT EVERY TIER: any new or changed endpoint owes ONE test that issues a REAL REQUEST** — `MockMvc` with `.contentType(MediaType.APPLICATION_JSON)` and a body, as `NoteControllerTest` already does — because a direct handler call bypasses content negotiation and passes under the defect by construction; on the client side `lib/api-*.test.ts` pins request shape, and a component test that mocks `lib/api` wholesale proves nothing about it. **⚠️ CARRIED LESSONS: a negative assertion needs a REACHABLE subject; write the guard at the LAYER THE DEFECT LIVES AT; MUTATE and confirm a NAMED test fails; read `./mvnw`'s EXIT STATUS directly, never through a pipe; COUNT executed tests from `target/surefire-reports/*.xml` and CLEAN the directory first; run `npm test`; SWEEP BY SURFACE, not by diff; and if a diff changes behaviour while no test beside it moves, either write the guard or say plainly that the item is unverified.**

**⚠️ THE CHECKPOINT'S INSTRUMENTATION IS SCOPE AND IS DECIDED NOW, BECAUSE THE SIGNOFF GATE REQUIRES IT SHIPPED IN THIS RELEASE AND VERIFIED *EMITTING*, AND TODAY THERE IS NO RECIPIENT COMPLETION EVENT.** Verified in the enum: `QUIZ_SHARE_LINK_CREATED`, `QUIZ_SHARE_LINK_OPENED` and `QUIZ_SHARE_LINK_TOGGLED` exist, and `QUIZ_SHARE_LINK_OPENED` **already fires from the recipient page** (`quiz/[token]/page.tsx:58`) — **so the OPEN half of the funnel is instrumented and the COMPLETE half is not.** **⚠️ THIS RELEASE SHIPS ONE NEW EVENT — a recipient COMPLETED a shared quiz — with a real fire site, enum value first per the standing convention.** That gives the proximal tier a rate with a readable denominator (**opened → completed**, which the §2 defects directly suppress) while the distal tier carries the near-zero-denominator clause. **⚠️ Without it the checkpoint is DECORATIVE by the gate's own definition — enum membership is not instrumentation. ⚠️ Do NOT change `QUIZ_SHARE_LINK_CREATED` or `QUIZ_SHARE_LINK_OPENED`'s fields or firing conditions while adding it.**

**Routing: SPLIT — slices 2 and 3 inline; slices 1 and 4 CODEX** (slice 1 changes a serialization contract plus the recipient answer model; slice 4 spans a backend DTO, a visibility predicate and the recipient result screen).

### Shipped

**Slice 2 — mobile focus (inline).**

- A signed-in recipient answering a shared quiz now gets the same focused page an anonymous one does.
  `AppShell.shouldUseAuthenticatedShell` excluded nothing under `/quiz/`, so an authenticated recipient was
  served the full app shell **including the mobile bottom tab bar** while taking a scored assessment — the
  same link behaving differently depending on whether the viewer happened to be logged in, with navigation
  escape hatches mid-quiz. **⚠️ The exclusion is the `/quiz/` recipient route ONLY; the in-app quiz surfaces
  under `/notes/…` and `/study-packs/…` keep the shell, and a second test pins that side** — a guard
  asserting only the `/quiz/` case would pass under a predicate that matched both.
- The results call-to-action stops overflowing on a narrow screen. `buttonVariants` gains an opt-in `wrap`
  that swaps `whitespace-nowrap` for `whitespace-normal text-center` and the fixed `h-10`/`h-9` for
  `min-h-10`/`min-h-9`. **⚠️ It is an OPTION rather than a call-site override for a reason that is easy to
  get wrong: `cn` is a plain join, NOT tailwind-merge** (`frontend/lib/utils.ts`), so passing
  `whitespace-normal` through `className` would leave **both** classes in the list and let stylesheet order
  decide. Defaults are unchanged, so every existing button renders byte-identically.

**Slice 3 — result legibility (inline).**

- The concept under the question stem is now a labelled *Topic* chip. It rendered as a bare grey string
  directly beneath the question, where it read as a second sentence of the question itself.

**Slice 4 — continue learning.** **⚠️ Implemented inline (no Codex token), then put through the scoped
cold-agent falsification pass the kickoff declared owed.**

- `PublicSharedQuizResponse` gains **`sourceNotes: List<PublicSourceNote>`**, populated server-side from
  sources whose **`visibility == PUBLIC`** only. A recipient who did poorly now has somewhere to go.
- **⚠️ IT IS IMPLEMENTED AS THE OWNER'S CAPABILITY RULE, NOT AS A SINGLE-NOTE SPECIAL CASE — a source is
  offered only where there is DURABLE source-Note identity AND legitimate recipient accessibility, and
  identity is NEVER inferred from a copied title.** `generated_quizzes.note_id` qualifies (`V43:4`);
  `CombinedQuizSection` carries a copied title string and no id, so a combined quiz contributes nothing
  **through the ordinary path — there is no `isCombined` branch anywhere**, and combined quizzes light up
  under the same rule once they gain provenance.
- **⚠️ PRIVATE SOURCES ARE OMITTED ENTIRELY** — not counted, not hinted, not placeheld. An empty list
  renders **nothing**, not an empty state explaining the absence. **Relationship state is never an input**;
  the test is `visibility == PUBLIC`, full stop.
- Anonymous recipients get **View Note** (`/public/notes/{id}`); authenticated recipients get **Add to
  Library**, reusing the existing `PublicLibraryCopyAction` rather than a second copy implementation.
  **⚠️ Anonymous deliberately does NOT get copy-first, and the reason is a repo fact:**
  `public-library-copy-action.tsx:161` routes them to `/signup?redirect=…`, but
  `resolvePostLoginDestination` returns the gated home **before** reading the redirect param, so a new
  signup loses it and the path dead-ends.
- **⚠️ Sender context (the plan's §7 — *"Quiz from Maria"*) is deliberately NOT built.** It is slice 5's
  cross-user read and identity disclosure, and it stays out of this release.

**⚠️ A DEFECT THIS RELEASE SHIPPED AND THEN CORRECTED, RECORDED RATHER THAN QUIETLY FIXED.** Slice 2's CTA
overflow fix **landed on the wrong button**: `wrap: true` was applied to the short *"Learn about NoteLib"*
button on the inactive-link screen (`page.tsx:222`) instead of the 54-character results call-to-action
(`:247`) that actually overflows. It merged in PR #1302 and the real defect stayed live. **The
`buttonVariants` unit tests passed throughout because they exercise the helper directly and nothing
asserted the CTA itself** — verbatim the standing rule that *a diff which changes behaviour while no test
beside it moves is unverified, not verified*. Both the fix and **the missing guard** are in this release,
and the guard was confirmed to fail against the state that shipped.

**Slice 1 — recipient assessment integrity.** **⚠️ Implemented inline rather than by Codex: no Codex token
was available, following the `v0.119.1` precedent that routed its Codex leg the same way.**

- **Matching questions are no longer served to shared-quiz recipients.** `teacher-quiz-developer.txt:23-26`
  instructs the model to emit a MATCHING block and `generated_quizzes` is populated through that very prompt
  (`GeneratedQuizService:126`), but `PublicQuizItem` carries no `questionGroup` and the recipient page has no
  control for one — so a block arrived as 2-4 consecutive MCQs with identical choices, scored independently.
  Excluded on `questionFormat`, never on `questionGroup`, since a non-matching item may legitimately carry a
  group. **⚠️ Carrying matching through instead would change the GRADING CONTRACT on a `permitAll` route
  scored for an anonymous recipient; recorded as a follow-up, not rejected on merit.**
- **⚠️ THE FILTER LIVES AT ONE POINT, AND THAT IS THE LOAD-BEARING PART.** `QuizShareLinkService` had **two
  independent derivations of the same list** — `getActivePublicQuiz` projects what the recipient SEES,
  `getSharedQuizResults` re-derived what the grader SCORES. Filtering only the projection would leave the
  grader on a longer list, so `answers.size() != questions.size()` throws and **every submit 400s** — the
  `v0.110.2` shape. `resolveSharedQuestions` now delegates to `resolveSharedQuiz(link).questions()`, making
  divergence structurally impossible. **⚠️ The extra `noteRepository.findById` this puts on the grading path
  is an ACCEPTED cost; do NOT re-split these methods to avoid one lookup.** It cannot fail in production:
  `generated_quizzes.note_id` is `NOT NULL REFERENCES notes(id) ON DELETE CASCADE` (`V43:4`), so a quiz cannot
  outlive its note.
- A quiz consisting only of matching questions now fails closed to the existing *"no longer active"* screen
  rather than serving a zero-question quiz that would score `0/0`.
- **Answers are index-addressed instead of append-only**, and a **Back control** lets a recipient return and
  correct one. The arrays are full-length and null-filled at load; the `answers[i]` / `multiAnswers[i]`
  pairing (exactly one populated per question, decided by format) is preserved, so **no wire change was
  needed** — the grader already tolerates a null slot.
- **⚠️ A SECOND LOCK WAS FOUND THAT THE PLAN DOES NOT NAME, AND FIXING ONLY THE FIRST WOULD HAVE SHIPPED AN
  OBSERVABLE NO-OP FOR SINGLE-CHOICE QUESTIONS.** `disabled={!isMultiSelect && selectedAnswer !== null}`
  disabled **every** choice the moment a single-choice answer was picked, so Back would have landed on a
  locked question. Removed. **⚠️ The test asserting that lock — commented *"Single-choice selection stays
  one-shot, exactly as it shipped"* — was REWRITTEN, NOT DELETED**, since it pinned the defect; its
  answers-slot half is genuine and was kept.
- **⚠️ A REAL-REQUEST TEST NOW COVERS THE ROUTE, WHICH HAD NONE.** `QuizShareControllerTest` uses `MockMvc`
  with `.contentType(MediaType.APPLICATION_JSON)` and a real body. Verified to catch the `v0.119.0` defect
  class: removing the content type produces `HttpMediaTypeNotSupportedException` / 415 before the handler
  runs. **⚠️ The pre-existing controller tests in this area call handlers as METHODS and pass under that
  defect by construction.**

**Post-implementation `advisor()` review of slices 1-3 (the declared tier), which added two guards.** The
review pointed at slice 1's navigation state as the least-reviewed code in the release — the cold agent was
scoped to slice 4's visibility predicate — and named two paths the existing tests did not walk. Both were
checked in code and **neither was a defect**, but both are now pinned:

- **Emptying an answer you returned to re-gates Continue.** Only MULTI_SELECT can be emptied (single-choice
  can be re-set but never cleared), and `handleContinue` gates on `hasSelection`, so a recipient cannot
  advance past a question they blanked. **⚠️ That upholds the kickoff's *correctable, not skippable* ruling
  on a path that did not exist before this release** — you could never return to a question at all — so it
  was unpinned rather than wrong.
- **Repeated navigation keeps every answer in its own slot.** The prior guards walked forward, back once,
  and forward again; a two-hop walk in each direction is what discriminates an off-by-one once the path is
  longer than the special case. Mutating the slot index is killed by three named tests, including this one.

**Recipient completion instrumentation (inline).**

- `QUIZ_SHARE_LINK_COMPLETED` is added to `AnalyticsEventType` and fires from the recipient page **only
  after grading succeeds**, carrying `token`, `score` and `total`. Paired with the existing
  `QUIZ_SHARE_LINK_OPENED` (fired on load) this gives the funnel a readable **opened → completed** rate,
  which is the proximal metric this release's checkpoint reads. **⚠️ Without it that checkpoint would be
  DECORATIVE by the signoff gate's own definition — enum membership is not instrumentation.**
- **⚠️ NO MIGRATION IS NEEDED AND THIS WAS VERIFIED, NOT ASSUMED:** `AnalyticsEventEntity` maps the enum
  `@Enumerated(EnumType.STRING)` onto a plain `VARCHAR(64)` with no CHECK constraint (`V25:4`), so a new
  value is safe to insert anywhere in the enum and no existing row is affected. No test pins the value
  count.
- The guard has two halves and the second is the discriminating one: a completion **is** recorded when
  answers are graded, and **is not** recorded when grading fails — an event fired before the await would
  satisfy the first while inflating the very rate the checkpoint reads.

**Verification of the inline slices.** Five mutants planted and killed, each by a **named** test:
reverting the `/quiz/` exclusion failed *AppShell › gives a signed-in shared-quiz recipient the focused
page, not the authenticated shell*; making `wrap` emit the conflicting class anyway failed *buttonVariants
› emits no conflicting nowrap class when wrapping is requested*; restoring the bare concept string failed
*shared quiz page › labels the concept so it does not read as part of the question*. **⚠️ The button guard
asserts `whitespace-nowrap` is ABSENT rather than that `whitespace-normal` is present — the latter passes
under the plain-join bug and proves nothing.** `npx tsc --noEmit` clean, `npm run lint` 0 errors (18
pre-existing warnings, none in touched files), the frontend suite green at **201 suites / 2,210 tests**
and the backend suite at **2,198 tests / 0 failures** (counted from `target/surefire-reports/*.xml` after
cleaning the directory first), each command's exit status read directly rather than through a pipe.
Removing the completion event failed *shared quiz page › records a completion once a recipient's answers
are graded*; firing it before the await failed *shared quiz page › records no completion when grading
fails*. **⚠️ Slice 1's second mutant is the one worth recording: re-splitting `resolveSharedQuestions` onto its own unfiltered derivation was killed by EXACTLY ONE test — *sharedQuizGradesASubmissionSizedToTheFilteredQuestionList* — the guard that exists to prove the delegation matters.** Admitting every question format was killed by three. **⚠️ Slice 4's own mutants are recorded too, because the first draft of this paragraph omitted them:** making `eligibleSources` ignore visibility was killed by *aPrivateSourceNoteIsNeverOfferedToAShareRecipient* and *aPrivateSourceNoteIdNeverLeaksAnywhereInThePayload*; giving a combined quiz a title-inferred source — **the exact shape the owner rule forbids** — was killed by *combinedQuizIsFlatPublicPayloadWithoutAnswerKeyAndMultiSelectGradesExactly*; and reverting the results CTA to what shipped in PR #1302 was killed by *lets the long results call-to-action wrap instead of overflowing*.

**⚠️ A SECOND SCOPED COLD AGENT RAN OVER SLICES 1-3 BEFORE SIGNOFF, AND IT FOUND A REAL DEFECT — THE
ESCALATION WAS OWED AND HAD BEEN MISSED.** The gate escalates from `advisor()` to one scoped cold agent when
**delivery introduces a defect the same session then fixes** — a measured blind-spot signal. That trigger
fired when the CTA fix landed on the wrong button, and slices 1-3 were nonetheless given only the
`advisor()` tier. The agent was briefed to hunt **the same shape**: a change correct in its mechanism but
wrong or unobservable at the call site.

- **⚠️ REMOVING THE AUTHENTICATED SHELL DID NOT MEAN "NO CHROME" — IT FELL THROUGH TO THE MARKETING
  `<Navbar />`, WHICH HAS NO AUTH AWARENESS.** A signed-in recipient was shown **Login** and **Get Started**
  — invited to log into the account they were already using — inside a sticky header carrying **eight
  navigation destinations**, on desktop and mobile alike. **The mobile tab bar had simply been traded for a
  marketing bar, so the release's own stated goal of "no escape hatches mid-assessment" was not met.** The
  recipient page now renders with **no chrome at all**. **⚠️ Verbatim the shape that triggered this review:
  the predicate was right and the `!shouldUseShell` call site was never examined.**
- **A failed submit's error followed the recipient onto an unrelated question.** `setSubmitError(null)` sat
  after the `isLastQuestion` early return and `handleBack` never cleared it — reachable **only because Back
  now exists**, so it is a defect this release created.
- **The misapplied `wrap: true` was supplemented, never reverted.** The earlier correction added it to the
  real CTA and left it on the short *"Learn about NoteLib"* button, where a later reader would take the
  mistake for a decision. Reverted.
- **Dead code removed:** `Button`'s `wrap` prop had **zero call sites** (every caller styles a `<Link>`
  through `buttonVariants`), and `disabled:hover:bg-background` outlived the `disabled` attribute it paired
  with.
- **⚠️ THREE TESTS WERE VACUOUS AND ONE MATTERS: deleting `answeredQuestions`' entire non-current-index
  branch left all 19 tests green**, because the only counter assertion never left question 0 — the reduce is
  this release's own rewrite and nothing exercised its actual job. Also unexercised: the `onCopySuccess` →
  *"Saved to your Library"* path, and **what a recipient actually sees** (both app-shell guards asserted only
  absences, which is why the `Navbar` defect was invisible to them).
- **⚠️ A METRIC CORRECTION, MADE BEFORE THE READ RATHER THAN AFTER: the checkpoint's denominator was wrong.**
  `QUIZ_SHARE_LINK_OPENED` fires **twice** from the recipient page — on load, and again from the
  results-screen signup CTA, which fires **only after a recipient has already completed**. Counting both
  inflates the denominator with a subset of completers and reads the rate low. The checkpoint row now
  specifies **`metadata.source IS NULL`**. **Pre-existing mis-instrumentation, not a regression** — and the
  CTA's event type was deliberately left alone, because this release's anti-drift forbids moving
  `QUIZ_SHARE_LINK_OPENED`'s firing conditions.

**⚠️ WHAT HELD UNDER ATTACK, recorded so it is not re-derived:** the `answers[i]`/`multiAnswers[i]` pairing
survived every constructed path including failed-submit-then-retry and Back from the last question after a
failure; `?? null` (not `||`) correctly preserves choice index `0`; the completion event cannot double-fire;
and `isSharedQuizRoute` captures nothing it should not.

### Known limitations

- **Combined quizzes offer no continue-learning source.** `CombinedQuizSection` carries a copied title
  string and no note id, so there is no durable source identity — and none is inferred, which is the rule
  rather than an omission. **Follow-up:** give combined quiz sections a source-note reference, after which
  they light up under the existing rule with no branch to remove.
- **The shared quiz TITLE is not visibility-gated, and it is PRE-EXISTING rather than introduced here.**
  `PublicSharedQuizResponse.noteTitle` has always carried the source note's title regardless of visibility;
  it names the quiz, and the sharer exposed it by choosing to share. What this release gates is the ability
  to **read** the note — its id, which is what a link navigates by. Narrowing the title is a separate
  product decision and is not taken here.
- **⚠️ ONE PRE-DECLARED GUARD COULD NOT BE WRITTEN, AND IS STATED RATHER THAN SILENTLY DROPPED.** The
  kickoff declared *"mixed public/private must omit the private WITHOUT TRACE"*. That fixture has **no
  reachable subject today**: `generated_quizzes.note_id` is `NOT NULL` with `uq_generated_quizzes_note_id`,
  so a single-note quiz has exactly one candidate source, and a combined quiz always yields an empty list.
  **No unreachable fixture was written to fake it** — this repo's own carried lesson is that a negative
  assertion needs a reachable subject. It becomes writable when combined quizzes gain provenance.
- **The authenticated *Add to Library* branch is pinned by a mocked component.** `PublicLibraryCopyAction`
  is `jest.mock`'d in the recipient page tests, so what is proven is **which branch renders**, not that the
  component works on this page. Its own behaviour is covered where it lives.


## Archived releases (v0.120.0 and earlier)

Full detail for every version below lives in `docs/archive/RELEASES_ARCHIVE.md`. `v0.40.1` and
earlier moved there on 2026-07-10; `v0.41.0` through `v0.120.0` moved there on 2026-09-07 in the
`v0.126.0` pass, which resumed a convention that had lapsed for 85 releases. Both are MOVES, not
deletes — content preserved verbatim and searchable with `git grep`. Condensed per-version
summaries (user-facing, no implementation detail) also exist at `docs/releases/vX.Y.Z.md`.

- `v0.120.0` - Canonical Note Title Integrity
- `v0.119.1` - Public Catalog Bounds
- `v0.119.0` - Curator Bulk Regeneration
- `v0.118.0` - Note and Study Pack Regeneration
- `v0.117.0` - Authoring and Quiz Legibility
- `v0.116.0` - Additive Review Set Updates
- `v0.115.0` - Learner Publication Authority
- `v0.114.0` - Connection Evidence
- `v0.113.1` - Anchoring Hardening
- `v0.113.0` - Session Anchoring
- `v0.112.0` - Connection Pool Integrity
- `v0.111.0` - Multidisciplinary Domain Context
- `v0.110.2` - Shared Link Integrity
- `v0.110.1` - Quiz Text Integrity
- `v0.110.0` - Supporter Combined Quiz
- `v0.109.0` - Assessment Discoverability
- `v0.108.0` - Session Identity
- `v0.107.0` - Curriculum-Scale Remediation
- `v0.106.0` - Board Exam Review Set Identity
- `v0.105.0` - Curriculum-Scale Exams
- `v0.104.0` - Assessment Source Provenance
- `v0.103.0` - Mixed Retrieval for Free and Plus
- `v0.102.0` - Plan-Sourced Assessment
- `v0.101.0` - Language and Observability
- `v0.100.0` - Domain Context Resolution
- `v0.99.0` - Connection Completeness
- `v0.98.0` - Connection Consistency
- `v0.97.0` - Connection Lifecycle
- `v0.96.0` - Authoring Integrity
- `v0.95.1` - Rendering and Reorder Fixes
- `v0.95.0` - Redemption Integrity
- `v0.94.0` - Connection Experience
- `v0.93.0` - Progress Refinement
- `v0.92.0` - Activity Sharing
- `v0.91.0` - Shared Learning Material
- `v0.90.0` - Invitation Integrity
- `v0.89.1` - Birth Year Correction
- `v0.89.0` - Support Another Learner
- `v0.88.0` - Section Authoring
- `v0.87.0` - Failure Attribution
- `v0.86.0` - Generation Recovery
- `v0.85.0` - Domain Signal Integrity
- `v0.84.0` - Public Explore
- `v0.83.2` - Anonymous Discovery Access
- `v0.83.1` - Note Creation Integrity
- `v0.83.0` - Target Audience Removal (Phase 2)
- `v0.82.0` - Authored Depth Backfill
- `v0.81.0` - Challenge Bank Integrity
- `v0.80.0` - Instrumentation Integrity
- `v0.79.0` - Catalog-First Vocabulary
- `v0.78.0` - Post-Mastery Next Step
- `v0.77.0` - Evidence-Gated Weak Concept Recommendation
- `v0.76.1` - Adaptive Practice Entry Attribution
- `v0.76.0` - Messaging Architecture: The Money Surfaces
- `v0.75.0` - Authoring by Inference
- `v0.74.0` - Quiz Progression
- `v0.73.0` - Onboarding Redesign
- `v0.72.1` - Constraint Check
- `v0.72.0` - Return Loop
- `v0.71.2` - Catalog Management
- `v0.71.1` - Applicable Programs Follow-ups
- `v0.71.0` - Applicable Programs
- `v0.70.0` - Canonical Knowledge Completion
- `v0.69.0` - Canonical Knowledge Foundation
- `v0.68.0` - Topic Note Rename
- `v0.67.1` - Explore Convergence Follow-ups
- `v0.67.0` - Explore Convergence
- `v0.66.2` - Card Surface Token Fix
- `v0.66.1` - Goal Detail Due-Concept Signal
- `v0.66.0` - Challenge Quiz Result Clarity
- `v0.65.0` - Study Effectiveness Polish
- `v0.64.0` - Add to Review Set
- `v0.63.0` - Ask Companion
- `v0.62.0` - Knowledge Impact
- `v0.61.0` - Challenge Quiz Quota Increase
- `v0.60.3` - Challenge Quiz Shaping
- `v0.60.2` - Challenge Quiz Known-Limitations Cleanup
- `v0.60.1` - Challenge Quiz Fix Pass
- `v0.60.0` - Shared Official Pool Foundation
- `v0.59.0` - Dashboard & Progress Reorg
- `v0.58.0` - Reusable Practice Assets & the Return Loop
- `v0.57.0` - Practice-First Activation Onboarding
- `v0.56.0` - Weak-Concept Explanation Links
- `v0.55.0` - Result-Screen Companion Bridge
- `v0.54.1` - Public Note Copy Correctness Fixes
- `v0.54.0` - CPALE Exam Hub (Wave 2)
- `v0.53.0` - SEO Discoverability: Exam Hub Depth & Organic Attribution
- `v0.52.1` - Early-Lifecycle Feedback Signals
- `v0.52.0` - Proactive In-App Feedback Prompts
- `v0.51.1` - Dashboard Stage-1 Limit Wiring
- `v0.51.0` - Read-Path Performance Pass II
- `v0.50.4` - Exam Hub Discovery Polish
- `v0.50.3` - Public Note Copy Flow & Related-Notes Consistency
- `v0.50.2` - Note Card Content Consistency
- `v0.50.1` - Mobile UI Polish
- `v0.50.0` - Mobile Bottom Tab Bar
- `v0.49.0` - Progress Page: Private Library Links
- `v0.48.0` - Retention Experiment: Open Loop & Digest Trigger
- `v0.47.1` - V82 Migration Collision Hotfix
- `v0.47.0` - Conversion Audit Tier 4: Cleanup Batch
- `v0.46.0` - Retention Depth: Due-Concepts Digest & Exam Pacing
- `v0.45.2` - Public Plan Preview Rollup Fix
- `v0.45.1` - Study Plan Collection Fixes
- `v0.45.0` - Conversion Audit Tier 3 — Landing, Pricing & Discovery Polish
- `v0.44.0` - Conversion & Retention Polish
- `v0.43.1` - Companion Mentor Tips
- `v0.43.0` - Companion Coach Experience
- `v0.42.1` - Companion & Progress Polish
- `v0.42.0` - AI-assisted Companion authoring + regeneration
- `v0.41.1` - Review Set Detail Page: This-Set Study Dashboard
- `v0.41.0` - Learning Companion (MVP)

- `v0.40.1` - Public Review Set Reachability
- `v0.40.0` - Weekly Study Plan (Exam Countdown) + Primary Review Set
- `v0.39.2` - Public Library Learning Experience
- `v0.39.1` - Study Plan Builder Polish
- `v0.39.0` - Flexible Review Methods
- `v0.38.0` - Read-Path Optimization Pass
- `v0.37.4` - Idle GC & Metaspace Ceiling Hotfix
- `v0.37.3` - Study Plan Read-Path Memory Optimization
- `v0.37.2` - Plan Data Integrity Hotfix
- `v0.37.1` - Native Memory Hotfix
- `v0.37.0` - Readiness-First Plans & Mastery Integrity
- `v0.36.3` - OCR Fast-Follow: Messaging & Feedback
- `v0.36.2` - OCR Disable Hotfix
- `v0.36.1` - Post-Release Fixes
- `v0.36.0` - Readiness/Progress Merge
- `v0.35.0` - Mobile-First Builder
- `v0.34.0` - Journey: Goal-First Study Experience
- `v0.33.4` - Builder Surface Clarity
- `v0.33.3` - Recursive Goal Adopt
- `v0.33.2` - Plan Detail Redesign (view/edit split)
- `v0.33.1` - Study Plan polish & Curated Plan Coverage
- `v0.33.0` - Study Plans as a Retention Engine
- `v0.32.2` - Conversion Diagnosis & Quota Honesty
- `v0.32.1` - Monetization Surfacing & Pricing Clarity
- `v0.32.0` - Account & Communication Controls
- `v0.31.2` - Analytics Integrity & Funnel Visibility
- `v0.31.1` - Adoptable Study Plans Discovery & Status
- `v0.31.0` - Adoptable Study Plans
- `v0.30.1` - Copy Flow Polish
- `v0.30.0` - Readiness Signals
- `v0.29.1` - Bulk Generation Polish
- `v0.29.0` - Bulk Generation
- `v0.28.0` - Feature Discoverability & Activation
- `v0.27.0` - Material Import & Collections
- `v0.26.1` - Guidance System
- `v0.26.0` - Exam Depth
- `v0.25.1` - Polish & Quick Review Fixes
- `v0.25.0` - Exam Capture & Goal Setting
- `v0.24.1` - Content Moderation Hotfix
- `v0.24.0` - Guided Learning
- `v0.23.1` - Quiz Format Fix
- `v0.23.0` - From Readers to Learners
- `v0.22.0` - Course & Subject Discovery
- `v0.21.0` - Personalized Discovery & Library Organization
- `v0.20.0` - Conversion & Re-engagement
- `v0.19.0` - Multi-Note Depth & Simulation Parity
- `v0.18.0` - Profile Completeness & Communication
- `v0.17.0` - Quiz Quality & Depth
- `v0.16.0` - Conversion & Growth
- `v0.15.2` - UX Cleanup & Bug Fixes
- `v0.15.1` - Teacher Power Features
- `v0.15.0` - Premium Mode Uplift + Cost-Control Quota Refactor
- `v0.14.0` - Grow the Surface, Deepen the Practice
- `v0.13.0` - Complete the Promise, Reach New Audiences
- `v0.12.0` - Learning Experience, Discovery, and Retention
- `v0.11.0` - Learning Flow Foundation
- `v0.10.1` - Landing & Pricing Conversion Polish
- `v0.10.0` - Profile Type System & Teacher Flow Phase 1
- `v0.9.0` - Learning Experience & Product Polish
- `v0.8.0` - Board Exam Mode + Public Library Discovery System
- `v0.7.0` - Learning & Metadata Foundation
- `v0.6.0` - Landing Revamp & Positioning
- `v0.5.0` - Public Profiles & Public Notes
- `v0.4.0` - Profile-Based Experience & UX
