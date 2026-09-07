# RELEASES.md - NoteLib

## v0.128.0 - Onboarding Unfrozen

**Status: Released** (kicked off and signed off 2026-09-07, base branch `releases/v0.128.0`, cut from `main` after `v0.127.0` merged and tagged)

Theme: the work that was blocked only by the onboarding freeze, now that the read the freeze protected has been taken.

**⚠️ THE FREEZE IS LIFTED BY OWNER DECISION (2026-09-07), AND THE READ IT PROTECTED WAS TAKEN FIRST RATHER THAN ABANDONED.** `[CHECKPOINT — due 2026-09-11]` froze `frontend/app/onboarding` to protect a signup-funnel read against a **62.4% completion baseline that cannot be re-run**. Taken read-only on 2026-09-07, four days early: **393 signups all-time, 249 completed, 63.4%** — essentially flat.

**⚠️⚠️ THE FINDING THAT DISCHARGES THE CHECKPOINT IS THE DENOMINATOR, NOT THE RATE. The cohort since the `v0.73.0` redesign (2026-08-12) is EIGHTEEN SIGNUPS** — 15 completed, 83.3%. At ~0.7 signups/day, waiting to 2026-09-11 adds about three more. **83.3% on n=18 is noise, not a result.** The checkpoint was never going to be answerable on its own date; **the freeze was protecting a read that cannot be taken.** That is exactly the underpowered-denominator failure this repo's own checkpoint doctrine names, and it is why lifting is not a trade-off.

**⚠️ Do NOT re-freeze onboarding for this checkpoint, and do NOT quote 62.4% or 83.3% as a current figure** — the first is a stale baseline, the second has n=18.

### Planned Scope

**(1) Summary maths renders. ⚠⚠ WITHDRAWN — THIS ALREADY SHIPPED IN `v0.96.0`, AND THE ITEM AS WRITTEN DESCRIBED CODE THAT NO LONGER EXISTED AT KICKOFF.**

**The contract was wrong, not merely stale.** This section claimed `components/ui/summary-markdown.tsx` "runs `react-markdown` + `remark-gfm` with **no math plugin**." It does not, and had not for nine days. Verified 2026-09-07 against the working tree at `74cc0df5` (clean):

| evidence | result |
|---|---|
| `git describe --contains 3652e3d6` | **`v0.96.0`** (commit dated 2026-08-29) |
| `RELEASES_ARCHIVE.md:8280` | records it shipped, "on all nine consumers including the SEO-indexed public pages" |
| file on disk | `remark-math` wired as tokenizer → `renderExtractedMath`, with the "`rehype-katex` deliberately NOT used" design comment intact |
| declared guards (a)/(b) | already present in `components/ui/summary-markdown.test.tsx` — including the underscore fixture at `:16`, the one that **cannot** pass under the defect |

**⚠️ The residual was checked, not assumed.** The concern behind guard (b) was a straggler rendering Summary through its own `react-markdown` instance. `grep -rln 'from "react-markdown"'` across `app/`, `components/` and `lib/` returns **exactly one file** — `summary-markdown.tsx` itself. Every consumer routes through `SummaryMarkdown`, so there is no leg (b) remainder. (The handoff's "ten consumers" against the archive's "nine" is doc drift in the count, not a missed surface.)

**⚠️ CAUSE — recorded because it is a process finding, not a typo: the kickoff copied `v0.96.0`'s PLANNED-SCOPE PROSE FORWARD instead of re-reading the code.** `RELEASES_ARCHIVE.md:8188–8226` carries the same sentences ("runs `react-markdown` +", "Add `remark-math` for TOKENIZATION only"). Nothing was lost — no code was written against the false premise — but a release opened with an item that could never have been done. **⚠️ A kickoff that quotes a defect must anchor that quote to the current file, not to the section that first described it.**

**⚠️ `v0.86.0`'s CORRUPTED-ESCAPE item is still open and is NOT closed by this** — it is a different defect, on stored content.

**(2) Catalog-first suggestions in onboarding** — the deferred half of `v0.79.0`, **and by that release's own baseline the load-bearing one**. Onboarding was excluded from `v0.79.0` precisely to protect the read now discharged. **⚠️ WITH ITEM 1 WITHDRAWN THIS IS THE WHOLE RELEASE.**

**⚠️ DO NOT BUILD A NEW MECHANISM — `v0.79.0` ALREADY SHIPPED IT.** `buildCatalogFirstCourseProgramSuggestions` (`lib/learning-profile.ts:133`) and the `useCourseProgramCatalogNames` hook are live on **four** surfaces (`app/profile/page.tsx`, `components/dashboard/lightweight-profile-completion-prompt.tsx`, `components/notes/note-editor-page-client.tsx`, `components/notes/private-note-detail-page-client.tsx`). Onboarding is the one deliberate exclusion and still passes the raw constant at `app/onboarding/page.tsx:1524`. The work is to fetch the catalog on that screen and route through the existing helper, mirroring the dashboard-prompt call site.

**Instrumentation is included, by owner decision (2026-09-07).** `trackCourseProgramValueSelected` fires from the **existing** commit point at `page.tsx:1119` (`updateLearningProfileContext`), which is the direct analogue of the dashboard prompt's call site — **no new flow step, no reordering.** **⚠️ This adds ONE member (`"onboarding"`) to the frontend `CourseProgramSelectionSurface` union and NOTHING ELSE: the `COURSE_PROGRAM_VALUE_SELECTED` enum value already exists, `surface` is NOT server-validated (it rides in the free-form metadata map), so there is NO backend change and NO migration.** Rationale: `v0.79.0`'s whole measurement is the off-catalog selection rate, and without onboarding that metric has a hole exactly where pick volume is highest — every account passes this screen once.

**⚠️ FREE TEXT STAYS ALLOWED.** `v0.79.0` shipped the **counter-proposal**; locking the field and the *"Request Program"* queue remain **PROPOSED AND UNRATIFIED** under `ADR-001`, and shipping this does **not** ratify them.

### Anti-drift

**⚠️ NO Learning Connections work.** `[CHECKPOINT — due 2026-09-19]` is **twelve days out**, its kill criterion keys on `ACCEPTED`, and it decides whether that arc continues at all — touching it would contaminate the one number that decides. **⚠️ Item 2 must NOT lock the course/program field or add a request queue**: `v0.79.0` shipped the **counter-proposal**, free text **stays allowed**, and the `ADR-001` amendment remains unratified — shipping this does not ratify it. **⚠️ Do NOT add, remove or reorder an onboarding FLOW step beyond what these two items require** — the freeze is lifted, not the judgment behind it. **⚠️ Do NOT change what `BOARD_EXAM_STARTED`, `ADAPTIVE_PRACTICE_STARTED`, `QUIZ_SHARE_LINK_*` or `GUIDANCE_TIP_SHOWN` record. NO migration, no quota/entitlement/meter change, no new mode or sub-mode, no `ProfileType` gate.**

### Verification

**⚠️ TIER RE-DECIDED 2026-09-07 AFTER ITEM 1 WAS WITHDRAWN — RECORDED, NOT SILENTLY DOWNGRADED.** The pre-declared tier was one scoped cold agent, justified by **two** things: a renderer shared by nine-plus consumers including SEO-indexed public pages (item 1), and the signup path (item 2). **Item 1 turned out to have shipped in `v0.96.0`, so half that rationale does not exist** and guards (a) and (b) are already-passing `v0.96.0` tests rather than work this release owes.

**The tier is KEPT, narrowed to item 2, on `model: "sonnet"`.** The release is now one file plus a test, which does not justify Opus or a full pressure test — but the surviving half of the rationale is the signup path, and the specific risk is the one this repo has been bitten by twice (`v0.116.0`, `v0.117.0`): **a change that compiles, passes, and does nothing.** `useCourseProgramCatalogNames` swallows every failure to `null`, and `buildCatalogFirstCourseProgramSuggestions(null, …)` falls straight back to `COURSE_PROGRAM_SUGGESTIONS`, so a catalog that never arrives renders **identically to today and passes any test written against it**.

**Single falsification claim handed to the agent:** *the catalog actually reaches the onboarding screen for an account with `onboardingCompletedAt == null`, and the rendered suggestion list differs from `COURSE_PROGRAM_SUGGESTIONS`.*

**⚠️ THAT CLAIM WAS PRE-CHECKED AGAINST PRODUCTION AND THE BACKEND BEFORE ANY CODE WAS WRITTEN, so the agent is falsifying a claim with evidence behind it rather than guessing:** `CourseProgramCatalogController.list()` is `@PreAuthorize("hasAnyRole('USER','ADMIN')")` — **role-gated only, NOT gated on `onboardingCompletedAt`**, so it is reachable mid-onboarding; and a read-only production count returned **44 rows in `course_programs`** against **31** hardcoded `COURSE_PROGRAM_SUGGESTIONS`, so the list demonstrably changes.

**⚠️ PRE-DECLARED GUARDS — status, each naming the fixture that proves nothing: (a)** a Summary containing `$x_1 + x_2$` renders maths and **not** `<em>` — **a fixture without an underscore passes under the defect**. **ALREADY MET by `v0.96.0`** (`summary-markdown.test.tsx:16`); not re-claimed here. **(b)** a Summary containing **no** maths renders **byte-identically** to today. **ALREADY MET by `v0.96.0`**, and the straggler check behind it (one `react-markdown` importer repo-wide) was re-run for this release. **(c)** onboarding completes end to end with a catalog-sourced program **and** with free text, because free text staying allowed is the counter-proposal's whole point — **THE ONLY GUARD THIS RELEASE OWES.**

**⚠️ GUARD (c) FOUND A TEST ASSERTING THE OLD BEHAVIOUR, WHICH IS WHY IT IS WRITTEN AND NOT ASSUMED.** `app/onboarding/page.test.tsx` carried a source-level pin — *"deliberately keeps the hardcoded Course / Program suggestions until the checkpoint"* — asserting `suggestions={COURSE_PROGRAM_SUGGESTIONS}` **and** `not.toContain("useCourseProgramCatalogNames")`. It was correct when written: it pinned `v0.79.0`'s deliberate exclusion of onboarding, whose whole reason was the now-discharged checkpoint. **⚠️ It is removed because its PREMISE is gone, not because it broke** — and it is **not** replaced with an inverted source assertion, since reading `page.tsx` as a string cannot tell whether the catalog actually reaches the screen, which is the only thing worth guarding. The replacement guards are behavioural, in the new `app/onboarding/onboarding-course-program.test.tsx`.

**⚠️ CARRIED LESSON FROM `v0.127.0`: confirm a mutation is PRESENT before trusting a green suite** — one silently failed to apply and the run passed.

**Routing: CLAUDE CODE inline.**

### Shipped

- **Onboarding's Course / Program field is catalog-first, closing the deferred half of `v0.79.0`.**
  `app/onboarding/page.tsx` was the one surface of five still passing the hardcoded
  `COURSE_PROGRAM_SUGGESTIONS`; it now resolves suggestions through the **existing**
  `useCourseProgramCatalogNames` hook and `buildCatalogFirstCourseProgramSuggestions` helper,
  mirroring `lightweight-profile-completion-prompt.tsx`. **No new mechanism was built.** Catalog names
  come first, the hardcoded list is appended rather than replaced, and **free text stays allowed** —
  `v0.79.0` shipped the counter-proposal, so the field is **not** locked and no request queue was
  added; the `ADR-001` amendment remains unratified.
- **The selection is instrumented, closing the hole in `v0.79.0`'s own metric.**
  `trackCourseProgramValueSelected("onboarding", …)` fires from the **existing** commit point inside
  `selectLearnerLevel`, the awaited `updateLearningProfileContext` write — **no flow step was added,
  removed or reordered.** One member added to the frontend `CourseProgramSelectionSurface` union;
  **no backend change and no migration**, because `COURSE_PROGRAM_VALUE_SELECTED` already exists and
  `surface` rides in the free-form metadata map. **⚠️ De-duplication is real, not incidental:** the
  event is passed the last *committed* value as its `previousValue`, so a learner who returns and
  changes only their learner level — which re-runs the same write — does not fire a second selection
  for a program they never re-picked.
- **⚠️ The event is deliberately SUPPRESSED when the catalog fails to load.** `matchedCatalog` cannot
  be computed without a catalog, and an unclassifiable selection is worse than a missing one — it
  would silently inflate the off-catalog rate the metric exists to measure.
- **A stale test that pinned the old behaviour was removed, and the removal is the finding.**
  `app/onboarding/page.test.tsx` asserted `suggestions={COURSE_PROGRAM_SUGGESTIONS}` and
  `not.toContain("useCourseProgramCatalogNames")` under the name *"deliberately keeps the hardcoded
  Course / Program suggestions until the checkpoint"*. **It was correct when written** — it pinned the
  exclusion protecting the read this release discharged — and is removed because its premise is gone,
  **not** to accommodate a change that broke it. **⚠️ It was NOT replaced by an inverted source
  assertion:** reading the file as a string cannot tell whether the catalog reaches the screen.

- **⚠️ Item 1 (Summary maths) was WITHDRAWN, not shipped — it was already done in `v0.96.0`.** See the
  Planned Scope note above; the cause was a kickoff copying `v0.96.0`'s planned-scope prose forward
  instead of re-reading the file, and the residual check (one `react-markdown` importer repo-wide) was
  re-run to confirm nothing was left over.

**Verification.** New `app/onboarding/onboarding-course-program.test.tsx` — four behavioural tests
covering guard (c) in both directions. **⚠️ ALL THREE MUTATIONS WERE CONFIRMED PRESENT IN THE FILE
BEFORE THEIR RUN, per the carried `v0.127.0` lesson**, and each named test that killed them:
- **Reverting the wiring** to the raw constant — the `v0.116.0`/`v0.117.0` silent no-op, and the one
  mutation that matters here — fails *"lists catalog names ahead of the hardcoded suggestions"* and
  *"completes the step with a catalog-sourced program"*. **⚠️ It fails only because the catalog fixture
  is disjoint from `COURSE_PROGRAM_SUGGESTIONS`; a fixture reusing a constant entry would PASS under
  the no-op.**
- **Dropping the analytics call** fails the catalog-sourced and free-text completion tests.
- **Locking the field** (`allowCustom={false}`, the unratified `ADR-001` amendment) fails exactly
  *"still completes the step with free text"* — the guard that keeps the counter-proposal from being
  silently ratified.
- **Removing the resume seeding** fails exactly *"does not re-report a program the learner already
  committed in an earlier session"*.

**A de-duplication hole was found in review and closed.** The tracking ref is per-page-load, so it
started at `null` on every mount: a learner **resuming** onboarding with a program already committed
in an earlier session, who stepped back through the learner-level screen, would fire a second
`COURSE_PROGRAM_VALUE_SELECTED` for a value that never changed. The ref is now seeded from
`me.courseProgram` when the draft loads. **⚠️ Seeded from the STORED value only, not the draft** — a
draft value that was never committed has never been reported and must still be able to fire.

Full frontend suite green (204 suites, 2253 tests); `tsc --noEmit` clean; ESLint 0 errors.

**Cold agent (scoped, falsification, `sonnet`) — primary claim CONFIRMED, and confirmed the expensive
way.** It did not merely read the wiring: it forced `useCourseProgramCatalogNames` to return `null`
unconditionally — the exact `v0.116.0`/`v0.117.0` no-op class — and **all four tests then failed**,
then verified its revert restored the file. It also independently confirmed the catalog is reachable
mid-onboarding (no `OnboardingGuardService.assertProfileComplete` anywhere on the controller or
service path; `SecurityConfig`/`JwtAuthenticationFilter` contain no onboarding gate), that the
analytics call cannot fire on a failed save, that `allowCustom` is untouched, and that no forbidden
item was tripped. **It found no runtime defect in the change.**

**⚠️ IT FOUND TWO DOC-DRIFT DEFECTS, AND BOTH WERE FIXED RATHER THAN NOTED — this is exactly the
"sweep by SURFACE, not by diff" class this repo has now been bitten by in four consecutive releases:**
- **`docs/features/notes.md` contradicted itself inside this very diff.** The surface list two lines
  above was updated to include onboarding while the line below still read *"Onboarding deliberately
  continues using the hardcoded `COURSE_PROGRAM_SUGGESTIONS` list until after the 2026-09-11
  completion checkpoint."* Removed, with the correction recorded in place. The neighbouring free-text
  line was also stale and now names onboarding.
- **⚠️ `ROADMAP.md`'s Backlog Index row for this very item still read "NOT SHIPPED — deliberately
  deferred", and cited "a source-text test asserts this" — the test THIS RELEASE DELETED.** That row
  is what kickoff scan steps 8 and 9 read, so leaving it would have handed the next kickoff false
  state about the work just completed. Row closed, and its `[EVIDENCE]` gate marked discharged. The
  adjacent `v0.79.0` checkpoint row was updated separately: its distal 2026-10-15 read was explicitly
  gated on "the post-2026-09-11 onboarding follow-up", which is what shipped here, so that read must
  now include `surface: "onboarding"` — **the fifth fire site, and the one the 13.9% profile baseline
  actually turns on**, since the original four measure edits of existing values.

**⚠️ A THIRD STALE CLAIM WAS FOUND BY WIDENING THE SWEEP, AND THE SCOPING IS THE LESSON.** The first
sweep grepped `docs/features/` and `docs/architecture/` — **`docs/product/` was never in it**, which
is precisely why the agent found the `ROADMAP.md` row and the implementing session did not. Two
whole-tree greps closed it: `2026-09-11` and `COURSE_PROGRAM_SUGGESTIONS`, both excluding
`docs/archive/`. They surfaced `docs/claude-plans/learning-connections-phase-plan.md:486`, which told
a future session that `[CHECKPOINT — due 2026-09-11]` is a **live measurement window** and that
editing the onboarding flow would **destroy** it. That is now false in both halves. A dated
correction was added above the paragraph. **⚠️ It is scoped strictly to the 2026-09-11 onboarding
read and explicitly reaffirms that `[CHECKPOINT — due 2026-09-19]` — that arc's own `ACCEPTED`-keyed
kill criterion — remains live and untouched. NO Learning Connections code was modified; the only
code files in this release are `app/onboarding/page.tsx` and `hooks/use-course-program-catalog.ts`.**
`docs/product/SPEC.md` was checked and is clean. `docs/gpt-contexts/GPT_CONTEXT.md` is version-stamped
to `v0.127.0` and is left to its normal per-release refresh. Historical files — `docs/releases/*.md`
and `docs/archive/` — were deliberately not rewritten; they are the record of what was true then.

## v0.127.0 - Failure Attribution and Learner Dates

**Status: Released** (kicked off and signed off 2026-09-07, base branch `releases/v0.127.0`, cut from `main` after `v0.126.0` merged and tagged)

Theme: a learner stops losing a date they set themselves, and a failed regeneration stops leaving no trace of why.

**⚠️ SCOPE IS TWO REAL DEFECTS, AND THE SELECTION REASON IS BUDGET SHAPE RATHER THAN PRIORITY — stated so a later session does not read it as the roadmap's ordering.** The Claude Max window closes **2026-09-10**, and what it buys is **cold-agent verification**, not the ability to edit documents. Both items here need the heavy tier; the largest remaining token cost (`ROADMAP.md`'s ~140k-token Backlog Index) is **cheap to verify and deliberately left for later**. Spending the last of an expensive-verification budget on work that needs none would waste it.

### Planned Scope

**(1) A learner loses an exam date they set themselves, and it is not recoverable.** `NoteCollectionService:778`'s reparent branch NULLs `targetCompletionDate` when a collection becomes a child, so a learner who sets a date and later adopts a parent Goal has it silently discarded. Nothing else records it.

**⚠️ THE ASYMMETRY IS THE TELL, AND IT IS THE SHAPE OF THE FIX:** the sibling path at `:1005` **already promotes** the earliest child date up to the Goal (`:1023`). One path preserves the learner's date by rollup; the other simply drops it.

**⚠️ THE EXISTING NULL IS NOT GRATUITOUS AND MUST NOT BE DELETED BLIND.** It carries a documented reason: `targetCompletionDate` and Companion are **top-level-Goal-only fields**, and a child that keeps carrying one **resurfaces stale top-level data** if later detached via `updateParent(null)`. The fix preserves the learner's date somewhere it survives; it does not simply stop nulling.

**(2) A production regeneration failure left zero database trace.** `notes` has no failure-reason column, the async worker discards the exception, and the owner's manual retry overwrote `status` — the incident was reconstructable **only because Render logs had not rotated**. **⚠️ THIS IS `v0.87.0` (*Failure Attribution*) REPEATING ON THE SINGLE-NOTE SURFACE.** `v0.119.0` surfaced remaining allowance and made bulk report quota as `BLOCKED` without persisting the underlying reason. **⚠️ It needs a migration, which is why it was parked; this release may add one, so it un-parks here.** **⚠️ Consider at the same time whether the SECOND, in-worker quota assert should exist at all** — the caller already checked, and it converts a clean rejection into an opaque failure.

### Anti-drift

**⚠️ ITEM 1 WAS DEFERRED TWICE ON PURPOSE** (the `v0.115.0` adoption audit, then explicitly not folded into `v0.116.0`) because it is a data-loss defect on a **different axis** from the adoption-update contract. **Do NOT fold it into adoption work now either.** **⚠️ NEVER SURFACE RAW EXCEPTION TEXT for non-`AppException` failures** — `v0.87.0`'s standing rule, and the one rule a cold review found genuinely unique to `AGENTS.md`.

**⚠️ No quota, entitlement, limit or meter CHANGE** — item 2 records *why* a failure happened and must not alter what anyone is entitled to. **⚠️ Do NOT change what `BOARD_EXAM_STARTED`, `ADAPTIVE_PRACTICE_STARTED`, `QUIZ_SHARE_LINK_*` or `GUIDANCE_TIP_SHOWN` record.** **⚠️ Do NOT raise the connection pool; do NOT start `v0.112.0` Phase 3** (gated on `[CHECKPOINT — due 2026-10-04]`). **⚠️ Do NOT prune the ROADMAP Backlog Index, and do NOT trim the `AGENTS.md` preamble** — `v0.126.0` held that on a method argument that survived its own pressure test: it needs hand-separation line by line, never a sweep.

**⚠️ `frontend/app/onboarding` STAYS FROZEN — the freeze lifts when the `[CHECKPOINT — due 2026-09-11]` READ IS TAKEN, not when the date passes.** **⚠️ NO Learning Connections promotion before `2026-09-19`.** Neither defect approaches a surface the twelve dated reads measure.

### Verification

**ONE SCOPED COLD AGENT framed as falsification, per defect.** Item 1 is **learner data loss on a shared adoption path**; item 2 adds a **migration**. Either fires the gate on its own.

**⚠️ ANY NEW OR CHANGED ENDPOINT OWES ONE REAL-REQUEST TEST** (`MockMvc` + `.contentType(...)` + body).

**⚠️ PRE-DECLARED GUARDS, EACH NAMING THE FIXTURE THAT PROVES NOTHING:** **(a)** a learner-set date must SURVIVE reparenting end to end — **a fixture whose collection has no date passes under the defect**; **(b)** the stale-data hazard the existing NULL guards must STILL be closed — detaching via `updateParent(null)` must not resurface a top-level date the learner never set; **(c)** a failed regeneration must leave a persisted reason readable **AFTER a retry overwrites `status`**, since the retry is what destroyed the evidence in production; **(d)** a failure whose cause is NOT an `AppException` must persist a safe reason and never raw exception text.

**⚠️ CARRIED LESSONS FROM `v0.126.0`: verify a claim on the CLAIMS, not on a proxy — a check that a thing is MENTIONED is not a check that it can be FOUND (that mistake deleted two corrections and mis-scored an entire hold); and MUTATION-VERIFY every guard.**

**Routing: CLAUDE CODE inline for item 1; re-run the routing test for item 2** once the migration's shape is known.

### Shipped

**Item 3 — the RECOMMENDED public-library ranking reads index-only.** (Folded in 2026-09-07, because item 2 opened a migration and this was parked waiting for exactly that.)

- **`V137` adds `(event_type, entity_id)` to `analytics_events`.** `RANK_VIEWS_JOIN` runs on the **DEFAULT** public-library sort and reads only those two columns, so the composite index removes the heap access entirely.
- **⚠️ SIZED AGAINST REAL ROW COUNTS BEFORE BEING WRITTEN, WHICH IS WHAT THE BACKLOG ROW REQUIRED** — its gate said *"this is a metrics table and the index is not free to maintain"*. A read-only production query returned **49,265 rows, 36,523 with a non-null `entity_id`, 14 MB total, 5.2 MB of existing indexes, 112 distinct event types, 4,601 events in the trailing 7 days (~660/day)**. Maintenance at that write rate is negligible.
- **⚠️ AND THE INDEX WAS PROVEN USEFUL BEFORE BEING ADDED, NOT ASSUMED: `EXPLAIN` on the live query showed a Bitmap Index Scan on `event_type` followed by a BITMAP HEAP SCAN over ~14,663 rows purely to fetch `entity_id`.** An index the planner would not pick is pure maintenance cost, so this was checked rather than reasoned.
- **⚠️ PLAIN `CREATE INDEX`, NOT `CONCURRENTLY`, read from the numbers rather than defaulted:** `CONCURRENTLY` cannot run inside a transaction, every migration in this repo is a plain `CREATE INDEX`, and at 49k rows the lock is well under a second. **The migration records that a future index here needs the concurrent form if the table reaches the millions.**
- **⚠️ NO BEHAVIOUR CHANGE — an index changes the PLAN, never the RESULT**, so there is no guard to write and none is faked. The `PREPARE` sweep in `NativeQueryPostgresIntegrationTest` applies the real Flyway set, which is what proves `V137` applies.
- The comment at `PublicLibraryRepositoryImpl:66` stated the table had **no** `entity_id` index and is corrected — it now also warns that adding a column to that subquery's select list would silently reintroduce the heap access.

**Item 1 — a learner's own exam date survives reparenting.**

- **`NoteCollectionService.updateParent` now PROMOTES the date to the parent before clearing it on the child.** The earliest date wins; a parent that already holds a nearer deadline keeps it. **⚠️ THE CLEAR STAYS — it was never the defect.** Deleting it would let a nested collection carry a top-level-only field and resurface a stale date on detach, so the fix preserves the value rather than removing the guard.
- **⚠️ THE FIX IS THE SIBLING PATH'S OWN RULE, NOT A NEW ONE.** `persistAdoptedGoal` already promoted the earliest child date to the Goal, with its reasoning written out — *"a completion target is a DEADLINE and the nearest one is the binding one"*. The two paths disagreed; that asymmetry was the whole defect, and the fix makes them agree rather than inventing semantics.
- **⚠️ THE PRE-EXISTING TEST CAUGHT THE CHANGE BEFORE ANY NEW ONE DID** — strict stubbing rejected the new `save(parent)`. Its stub is widened and it now also asserts the date moved up, so it pins **both** halves: the child is still cleared **and** the value is not destroyed.
- **Four guards, all mutation-verified, each discriminating a different property:** dropping the promotion kills three tests; making it always overwrite kills only `updateParent_keepsTheNearerDeadlineWhenBothParentAndChildCarryOne`; removing the child's clear kills two. **⚠️ A fixture whose child has NO date passes under the defect, and one whose parent has no date cannot tell "earliest wins" from "always overwrite" — both cases are covered explicitly.**
- `docs/features/collections.md:168` documented the old behaviour verbatim and is corrected.

**Item 2 — a failed generation records WHY, and the record survives the retry.**

**⚠️ AUDIT ADDITIONS — three things verified independently rather than accepted from the delivery.**

- **The critical mutant was re-run at audit and lands where it claims.** Clearing the columns on the SUCCESS path — the exact production evidence loss — is killed by `noteAndStudyPackRegeneration_failureReasonSurvivesASuccessfulRetryThatOverwritesStatus` and by nothing else. **⚠️ THE FIRST ATTEMPT AT THAT MUTATION SILENTLY DID NOT APPLY and the suite passed green; checking that the mutation was PRESENT before trusting the result is what caught it** — the same protocol failure a cold agent self-reported on this codebase two releases ago.
- **No response payload exposes the new columns** — checked directly, because a shared mapper putting a new field on an anonymous payload is a defect this project shipped in `v0.125.0`. `generationFailureCode`/`Reason` appear in `NoteEntity` and nowhere else in `src/main/java`.
- **The non-clearing property holds by construction, not by test alone:** `setGenerationFailure*` is called only in the failure path; `markNoteGenerated` never touches those fields.
- **⚠️ THE FIX IS WIDER THAN THIS RELEASE'S OWN SCOPE TEXT SAID, AND THE DELIVERY SURFACED IT RATHER THAN LETTING IT BE FOUND: the catch is SHARED.** `generateStudyPackFromExistingNoteAsync` also serves `startAsyncGenerationFromNote`, and both bulk services route through it — so **first generation, bulk generation and bulk regeneration now stamp the note row too.** Same defect, same gap, so it is correct; recorded because "single-note regeneration" understates the blast radius.

- **`notes` gains `generation_failure_code`, `generation_failure_reason` and `generation_failed_at` (`V136`), written by the async worker's `catch`.** The 2026-09-05 incident left ZERO database trace: the exception reached the log and never the database, and the owner's manual retry then overwrote `status`, so by investigation time the database held zero `FAILED` notes and the whole thing was reconstructable only because Render logs had not rotated.
- **⚠️ THE REASON IS `v0.87.0`'S SHAPE, REUSED RATHER THAN REIMPLEMENTED.** `BulkGenerationFailureReasonNormalizer` gained a package-private `static` entry point and `StudyPackService` calls it: an `AppException` contributes its own code and safe message, anything else contributes `UNEXPECTED_ERROR` and a template naming only the exception CLASS. **Raw exception text is never persisted.** Static rather than injected because `StudyPackService` is constructed positionally in eight places — two implementations of the never-surface-raw-text rule is a correctness risk; a class named `Bulk*` called from the single-note path is a naming nit.
- **⚠️ THE COLUMNS ARE A LAST-FAILURE RECORD AND ARE NEVER CLEARED ON A LATER SUCCESS.** That is the whole point: the retry is what destroyed the evidence. `generation_failed_at` is what makes them interpretable, because `updated_at` is bumped by the retry and cannot distinguish a current failure from a recovered one.
- **⚠️ THE SECOND, IN-WORKER QUOTA ASSERT STAYS — and the argument is the code's own.** `NoteGenerationService`'s javadoc states that asserting and charging move together (*"charging without asserting bills past the limit"*), and the regeneration path charges the note meter at commit, so disabling the assert while still charging is verbatim the state that comment forbids. The assert also runs BEFORE the LLM call, so it SAVES a call rather than wasting one — corroborated by the recovered trace failing at 4612 ms against 10–27 s successes. **The defect was that its rejection was OPAQUE, not that it fired.** No quota, entitlement, limit or meter changed, and the race is deliberately still open (a user-row lock across the LLM call is forbidden by name, and reserve-then-refund breaks `v0.118.0`'s one-commit property).
- **⚠️ THE CATCH IS SHARED, SO FIRST GENERATION GAINS ATTRIBUTION TOO** — stated rather than left to be found. `generateStudyPackFromExistingNoteAsync` serves `startAsyncGenerationFromNote` as well, and bulk generation and bulk regeneration both route through those entry points, so their items now stamp the note row alongside `note_bulk_regeneration_item.reason_code`. The recovery sweeper's own entry point records `GENERATION_INTERRUPTED`; it can never overwrite a specific reason, because `isRecoverableNote` matches only `GENERATING` rows.
- **Six guards, all mutation-verified, and two of them are REAL-ROW.** `NativeQueryPostgresIntegrationTest` reaches the async leg for real rather than by stubbing an exception: the account is left with exactly one note-generation unit so the pre-dispatch check PASSES, and a new dispatcher hook spends the last unit before the worker runs, so the worker's own assert throws against real `user_usage` rows. **⚠️ The finding's *"a window the harness cannot open"* is scoped to the BULK driver's harness; the regeneration harness can open it.** Isolated by two SINGLE-VARIABLE fixture mutations rather than one that moved two things at once: removing ONLY the hook makes the run SUCCEED (so the hook is what opens the window), and exhausting the meter at request time instead makes the exception propagate SYNCHRONOUSLY out of the call (the clean 403 leg, which the passing test is therefore not measuring).
- **⚠️ GUARD (c) ASSERTS BOTH POINTS IN ONE TEST, because asserting only after the retry passes under a version that never wrote the reason at all** — both reads are NULL. Adding a clear-on-success to `markNoteGenerated` kills exactly the two guard-(c) tests and nothing else. Guard (d) uses a secret-shaped exception message, since an innocuous one passes under a version that stores `ex.getMessage()` verbatim.
- **The nine H2 fixtures that mirror `notes` were updated** — two of them failed the build until they were, which is the mechanism working.
- `NoteBulkRegenerationService`'s comment saying the exact reason *"cannot be persisted without a column this release may not add"* is corrected; repointing its quota heuristic at the new column is recorded as a deliberate follow-up, because it moves a `BLOCKED`/`FAILED` classification the curator's retry keys on.

## v0.126.0 - Context Budget

**Status: Released** (kicked off and signed off 2026-09-07, base branch `releases/v0.126.0`, cut from `main` after `v0.125.0` merged and tagged)

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

**Cold pressure test, and it found that this release's own headline word was wrong.**

- **⚠️⚠️ `CLAUDE.md` LINE 39 WAS A *DELETE*, NOT A *MOVE* — AND IT TOOK A LIVE CORRECTION WITH IT.** `RELEASES.md` and `ROADMAP.md` were archived properly; the 322,329-char chain was simply removed, surviving only in git history, against `docs/archive/README.md`'s own *"a MOVE, not a delete"*. **⚠️ THE VERIFICATION THAT PRECEDED IT CHECKED THE WRONG THING: it proved every VERSION named in the chain had a `RELEASES.md` section — true, and beside the point — and never asked whether the chain carried CLAIMS with no duplicate.** It did. *"ALL THREE OF THOSE COPY FIXES HAVE SHIPPED — VERIFIED AGAINST CODE 2026-09-04"* and *"the ALE curator copy is NOT the blocker any more"* returned **zero** repo-wide matches, while `AGENTS.md:53` — in the file `CLAUDE.md` calls *"Always check this first"* — went on asserting the blocker those sentences had cleared. **A session would have redone shipped work or re-rejected `ARCHITECTURE`, which is precisely what the deleted *"Do NOT re-reject `ARCHITECTURE`"* line existed to prevent.** Fixed by making it a real move (`docs/archive/CLAUDE_ARCHIVE.md`) and correcting `AGENTS.md:53` in place. **⚠️ CARRIED LESSON, GENERALISED: "the content is duplicated" must be tested on the CLAIMS, not the version numbers — a chain of release narrative accumulates corrections to ITSELF, and a correction is by nature the one sentence with no duplicate.**
- **⚠️ `AGENTS.md` FORBADE EXACTLY WHAT THE NEW `signoff.md` STEP MANDATES.** `AGENTS.md:1729` read *"Do not delete old release entries"* full stop, with **zero** mentions of the archive — so a session reading the anti-drift document refuses the step and one reading `signoff.md` performs it. **That contradiction is the better explanation of why the convention lapsed for 85 releases**, and this release had patched the command file and left the rule standing. Reconciled: archive ≠ delete, stated at the rule itself.
- **⚠️ THE NEW INSTRUCTION WAS SELF-DELETING.** It lived in exactly two places — `signoff.md`, and `CLAUDE.md`'s `Current version:` block, **which the next kickoff REPLACES by this release's own new rule**. `kickoff.md` has no archive step, and `CLAUDE.md`'s own 9-step checklist said *"Bump"*, not REPLACE. **⚠️ A fix that survives one release is not a fix**; both are now in `CLAUDE.md`'s durable checklist, deliberately duplicated.
- **⚠️ THE ROADMAP HALF POINTED AT A STRUCTURE THAT NO LONGER EXISTS.** It archived *"newly-`(Released)` per-version retrospectives"* — but kickoff stopped creating those at `v0.85.0` and live `ROADMAP.md` has **zero** `## v` sections, so it would have matched **nothing, forever**, while `## Current Release Baseline` — **247,345 chars naming 121 versions, one paragraph per release, structurally identical to the chain this release just removed** — grew unguarded. Repointed.
- **⚠️ THE ITEM 4 HOLD WAS OVER-CAUTIOUS, AND ITS EVIDENCE WAS AN ARTIFACT OF THE SAME MISTAKE AS THE FIRST BULLET.** The "two of three rules exist nowhere else" test matched text **verbatim**; this repo restates rules in different words. Hand-checking ~45 rules put genuinely-unique at **~2%**, not 67%, and **zero** checkpoints are unique to `AGENTS.md` — all eight dates appear in `ROADMAP.md` with 8-35 references each. **⚠️ THE METHOD CONCERN SURVIVES AND IS WHY THE HOLD STANDS ANYWAY:** the largest block (27,031 chars) does **not** begin with a version tag, so a tag-based sweep cuts the small blocks and leaves the big one — and bullet 1 proves an in-place replace loses content. The shape is: classify each rule by destination, restore the nowhere-else ones first, then move the chain to a `docs/archive/AGENTS_ARCHIVE.md`. **Its own release.**
- **Figures corrected:** `RELEASES.md` is **133,497** not 130,778; `ROADMAP.md` **844,282** not 842,841; live checkpoints **222** not 223 — verified by set comparison, no gate lost. **On a release whose deliverable is numbers, wrong numbers matter.**
- **Not refuted:** both moves are byte-for-byte (117/117 sections, 60/60 retrospectives, independently re-split from `bc21785d`), and `diff` of old versus new `CLAUDE.md` is **exactly one changed line**, so nothing outside line 39 could have been lost.

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

**Items 1, 2 and 5 shipped.** `RELEASES.md` **1,866,861 → 133,497 chars (~466k → ~33k tokens)** — 111 sections (`v0.41.0` → `v0.120.0`) moved to `docs/archive/RELEASES_ARCHIVE.md`, keeping current + last 5 live. `ROADMAP.md` **1,189,862 → 844,282** — 60 `(Released)` retrospectives moved to `ROADMAP_ARCHIVE.md`. **⚠️ BOTH VERIFIED BYTE-FOR-BYTE, WHICH IS THE GUARD THAT MATTERED: all 117 `RELEASES.md` sections and all 60 ROADMAP retrospectives are present and unchanged across live + archive.** The only two flagged diffs in each case were the index block relocating and a section-parser boundary artifact, both confirmed by inspection rather than assumed. **222 live `[CHECKPOINT]` rows and every live ROADMAP section survive.** Item 5 patched `kickoff.md` to REPLACE rather than prepend and added an archive step to `signoff.md`, **which is the half that stops items 1-3 regrowing** — its absence is why the 2026-07-10 pass never ran again.

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

**⚠️ The `editing`-gated debounced combobox writer STAYS (`v0.88.0`)** — the prohibition is recorded at `docs/archive/CLAUDE_ARCHIVE.md` (it lived in `CLAUDE.md`'s `Current version:` chain until the `v0.126.0` archive pass) and in `v0.88.0`'s own section, and it is 500 ms of exactly the machinery this release is touching.

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

## Archived releases (v0.122.0 and earlier)

Full detail for every version below lives in `docs/archive/RELEASES_ARCHIVE.md`. `v0.40.1` and
earlier moved there on 2026-07-10; `v0.41.0` through `v0.120.0` moved there on 2026-09-07 in the
`v0.126.0` pass, which resumed a convention that had lapsed for 85 releases; `v0.121.0` followed at
the `v0.127.0` signoff and `v0.122.0` at the `v0.128.0` signoff, the step now running every time. Both are MOVES, not
deletes — content preserved verbatim and searchable with `git grep`. Condensed per-version
summaries (user-facing, no implementation detail) also exist at `docs/releases/vX.Y.Z.md`.

- `v0.122.0` - Shared Quiz Discoverability
- `v0.121.0` - Shared Quiz Recipient Experience
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
