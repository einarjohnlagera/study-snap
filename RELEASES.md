# RELEASES.md - NoteLib

## v0.142.0 - Awareness Before Action

**Status: In Progress**

Theme: the notification inbox and the adopted-Review-Set update panel both went live for the first
time in `v0.134.0`–`v0.141.0` and have never been polished against real production shape. This
release fixes a bug that sits on 100% of today's live notification population, redesigns the card
for read/unread and one-tap activation, and replaces the update panel's uncapped raw diff with a
meaning-partitioned summary plus a progressive-disclosure detail surface.

Source: `docs/claude-plans/v0.142.0-adoption-and-notifications-plan.md`, written from a tightened
product spec the owner returned after a second GPT opinion. Verified against code and against a
2026-09-11 read-only production read.

### Planned Scope

- **A5 — the notification panel does not close on CTA activation (frontend, isolated bug).** The
  CTA `<Link>` at `notification-inbox.tsx:163-169` marks the notification read and never calls
  `setIsOpen(false)`. **⚠️ This fires on 100% of today's live notification population** — all 42
  production notifications share one type (`REVIEW_SET_UPDATE`), all carry a CTA, and the CTA is
  the only route in. Ship first; cheapest item in the release.
- **Workstream 1 — notification card (frontend).** Unread today is font-weight only
  (`font-medium` vs `font-semibold`, `:157`) with no background distinction — genuinely missing,
  though the owner's stated reason (no borders) is not: borders exist
  (`border-b border-border last:border-b-0`, `:154`) and are simply invisible with one notification
  on screen. Card body becomes the single tap target (mark read + close + navigate); CTA-less
  notifications mark read on tap with no separate button; dismiss stays a distinct control outside
  the tap area; add a relative timestamp (`createdAt` is already in the DTO, no backend work
  needed). Reconcile, not delete, the 5 of 20 existing tests that use the old **Mark read** button
  as their entry point.
- **Workstream 2 — Review Set update panel (frontend + one backend field).** Replace the two
  uncapped raw-diff lists with meaning-based partitioning (additions / unavailable / other
  curriculum changes — `SKIPPED_NOT_PUBLIC` currently sits, wrongly, under a heading that says "no
  action taken"), aggregate the three per-note fan-out types (`REORDERED`, `RETIRED`, `MOVED`) into
  counts, replace the raw wall with a compact summary plus a **Review update** detail surface, and
  rename **Apply additions** → **Add N new topics** (production copy check returned zero
  collisions — see Verified findings below). The topic count must come from counting `ADDED_NOTE`
  alone, not `additionsAvailable()` (`NoteCollectionService.java:3523-3529`), which also counts
  `ADDED_SUBJECT_PLAN` and would overstate the promised count.
- **Section grouping (owner decision, defaulted for kickoff): ship Option A — Subject Plan
  grouping only, no Section level.** `ReviewSetUpdateChange` carries no Section field and a
  Section is a string label on `NoteCollectionItemEntity.label`, not an entity — adding
  `sourceSectionLabel` is a real, small, zero-extra-query DTO addition (the variable is already in
  scope at the `ADDED_NOTE` construction site), but it makes this a backend release and raises the
  verification tier. Defaulting to A keeps the release frontend-only; B is a stated fast-follow if
  the owner wants the extra hierarchy level. **Revisit if the owner objects.**

### Verified findings this scope rests on (read-only, 2026-09-11)

- Production notifications: **1 distinct type** (`REVIEW_SET_UPDATE`), **42/42 with a CTA**,
  **41/42 unread**, **0 ever dismissed**, all created in one batch the day before this kickoff.
  The unread ratio means the card's unread treatment is what nearly every viewer sees, not an edge
  case.
- The rename-collision check returned **zero rows** — no notification title, body, or CTA label in
  production references "Apply additions", "upstream", or "addition".
- `docs/features/collections.md:932` and `frontend/app/collections/[id]/page.test.tsx:736,758` both
  reference "Apply additions" by exact string and must be swept in the same PR as the rename.

Anti-drift: do NOT let notification activation apply a Review Set update — activation navigates and
marks read only, the update itself stays an explicit, separate action; do NOT title-based-group the
detail surface — Subject Plan grouping uses stored `sourcePlanId`/`subjectTitle` identity, never a
note title; do NOT overwrite learner content, reset progress, or make adopted Review Sets
live-synced; do NOT change `additionsAvailable()` — it correctly gates whether the Apply action
renders at all and must stay a boolean threshold, not a display count; do NOT add pagination, a new
diff engine, or new notification infrastructure — the existing payload already carries what the
grouping/aggregation work needs under Option A; do NOT sweep `AGENTS.md`'s preamble or the Backlog
Index as a side effect of this release (both are explicitly held per
`docs/claude-plans/context-doc-token-reduction-plan.md`, items 4/6/7 — item 6 ran once this
kickoff, six rows, and is not repeated here).

Verification tier decided at kickoff: **one scoped cold agent minimum, falsification-framed** — two
PRs touch the same shared classification surface (the update panel's partitioning function feeds
both the compact summary and the detail surface), and this release changes what a user-facing claim
means (which "Changed upstream — no action taken" currently misstates for `SKIPPED_NOT_PUBLIC`) —
three releases running have been bitten by that surface-sweep gap. **Escalates to the full
three-agent test if Option B (Section grouping) is taken instead of A**, since that adds a backend
DTO change touching adopted-learner-content semantics.

Routing: **CODEX** for both workstreams — each exceeds the ≤50 LOC / 1–3 file inline threshold (A5
alone is inline-sized, and should be shipped as its own small PR ahead of the rest). Full scope,
verified findings, and the rejected alternatives are in
`docs/claude-plans/v0.142.0-adoption-and-notifications-plan.md`.

### Shipped

- **Notification rows are now coherent, single-target cards.** Unread rows have a theme-safe
  background tint, dot, and slightly stronger title; every row shows a relative timestamp. The
  title/body region is now the one primary control: a safe destination renders as a native link
  that marks read, closes the desktop dropdown or mobile sheet, and navigates, while a CTA-less or
  rejected destination renders as a mark-read-only button. The standalone **Mark read** button and
  duplicate CTA link are gone; dismiss remains an independently focusable sibling and does not
  mark read or navigate. All 21 existing tests were retained and reconciled, with seven focused
  interaction and visual guards added; all 16 changed tests failed against the pre-change row.
- **A5 — the notification panel now closes when a CTA is activated**, on both the desktop dropdown
  and the mobile sheet (both render the same `rows` block, so one fix — an added `setIsOpen(false)`
  alongside the existing `markRead` call — covers both). Of the three existing close-path tests in
  the suite (outside click, Escape, bell toggle), none covered the close path a learner actually
  takes; two tests were added (desktop and mobile), each verified to fail against the pre-fix code
  and pass against the fix. Workstream 1 subsequently moved this behavior from the deleted CTA
  link to the card body's native link and re-pointed both tests without dropping the coverage.


## v0.141.0 - Formulas That Render

**Status: Released** (kicked off 2026-09-10, signed off 2026-09-11, base branch `releases/v0.141.0`, cut from `main` after `v0.140.0` merged as #1373 and tagged)

Theme: a quiz question that mentions money and a formula in the same sentence prints the formula as raw LaTeX. Three surfaces render generated maths; each fails differently, and one does not render maths at all.

Source: the `v0.140.0` cold pressure test (`RELEASES.md` v0.140.0, and the raw-LaTeX Backlog row). **⚠️ Read the row before scoping — this session proposed the WRONG fix first and the row records why.**

### The defect, and why the obvious fix is a no-op

`isInlineDollarOpen` (`frontend/components/study-pack/quiz-working-solution.tsx:62`) opens a math span on **any** `$` not followed by whitespace — so **`$50,000` opens one.** `findInlineDollarCloseIndex` (`:68`) then correctly skips intervening `$` preceded by a space, walks past `$5,000` and `$A = …`, and closes on the formula's **final** `$`, whose previous character is a digit. The span swallows the sentence; KaTeX fails on it; `renderMathSegment` falls back to re-emitting the source. **The reader sees the raw formula, backslashes and all.** A sibling case fails the other way, absorbing prose into run-together italics.

**⚠️ `normalizeBareMath` IS NOT THE PROBLEM AND MUST NOT BE THE FIX.** `renderMathText` already calls it at every call site (`:206`), and it returns immediately on **any** delimiter (`math-normalization.ts:264-266`) — and **339 of 339** backslash-bearing questions plus **498 of 498** explanations already contain one. The repair is wired correctly and reaches nothing. **This release's predecessor proposed a fix aimed there and was wrong; a cold agent disproved it.** The bug is delimiter PAIRING, not missing delimiters.

**⚠️ THE CURRENCY HANDLING IS NOT ABSENT — IT IS INCOMPLETE IN ONE DIRECTION.** `findInlineDollarCloseIndex` is currency-aware (it rejects a `$` preceded by whitespace or an operator) and four passing tests cover currency-only strings — *"two currency amounts"*, *"$10-$20"*, *"$5+$3"*, *"$12/$4"*. **The untested case is currency AND a real formula in the same string**, which is exactly what the model produces for finance questions.

### Checkpoints landing during this release — recorded at kickoff, not discovered later

Step 9 found **nothing past due**, but **four checkpoints land on 2026-09-11 and 2026-09-12** and none carries a closure marker: `v0.114.0`'s *"was the startup line ACTUALLY READ from the production log"*, `v0.101.0` Slice 1, Learning Connections, and `v0.74.0`'s *"does the perfect-score gate work as a progression, or as a wall?"*. **⚠️ `v0.114.0`'s needs a Render LOG read, not a database read** — it requires a workspace confirmation the owner must give, so it cannot be closed from inside a release. They are named here so that closing this release does not quietly carry four overdue gates into the next kickoff.

### Planned Scope

1. **Pair `$` delimiters correctly when currency and a formula share a string (frontend).** The opener needs the currency-awareness the closer already has. **⚠️ THIS HEURISTIC HAS BEEN WRONG TWICE AND THE COMMENT AT `:55-60` RECORDS THE LAST TIME:** a previous fix captured `"10-"` as LaTeX, which KaTeX renders **happily** because a trailing binary operator is legal — so the error fallback never fired and the reader silently saw a subtraction with the dollar signs eaten. **A test that only asserts "no crash" or "something rendered" passes under both the defect and the fix.** Assert what the reader sees.

2. **`SummaryMarkdown` never repairs bare math (frontend).** `remark-math` tokenises **delimited** math only, and the component never calls `normalizeBareMath`, so an undelimited `\frac` in a summary renders literally on every surface that uses it. **36 production summaries carry a backslash and no delimiter at all.**

3. **`app/shared/study-packs/[id]/page.tsx:69,75,81` render summary, keyConcepts and fullNotes as raw `{value}` — no math rendering of any kind.** **⚠️ SCOPED HONESTLY AND NOT TO BE OVERSOLD: that route has ONE linked-learner relationship and SIX share events in 90 days, and `share_token` is 0 across all 7,573 packs.** It is cheap (wire in the renderer the sibling surfaces already use) and its traffic today is ~1 person. It is in scope because it is the same defect class, not because it is urgent.

### Verification tier, decided at kickoff

**Three surfaces ⇒ signoff owes ONE SCOPED COLD AGENT, falsification-framed.** Recorded now so it is not re-litigated later. **⚠️ And the `v0.140.0` precedent is the reason: its cold agent found a blocking defect that 2,375 passing tests did not, and disproved a claim that had already been merged.** A green suite is evidence only about paths the suite executes.

Anti-drift — locked:

- **⚠️ Do NOT "fix" this in `normalizeBareMath`, and do NOT widen `ALLOWED_COMMANDS` as the remedy.** Both are the wrong layer. The allowlist gaps (`\to`, `\text{m/s}`, `90^\circ`) are a **separate** finding and are NOT in this release.
- **⚠️ A DISPLAY PATH MUST NEVER REWRITE STORED CONTENT.** `v0.110.1` shipped a sanitizer that re-ran on every deserialization and progressively destroyed stored choice text. Every fix here returns nodes or a display string; nothing writes to the database.
- **⚠️ Do NOT regress the four passing currency tests.** They encode real prior bugs, and the new behaviour must satisfy them **and** the mixed case.
- **⚠️ No `rehype-katex`.** `summary-markdown.tsx` deliberately uses `remark-math` as a TOKENIZER and renders through the single existing KaTeX call; a second renderer is exactly what its comment forbids.
- **No new maths or markdown dependency.** `katex`, `react-markdown`, `remark-gfm` and `remark-math` are already present.
- **⚠️ NO hand regeneration of stored packs as part of this release.** The defect is in the display path; regenerating would spend curator time on the wrong layer and is the framing the raw-LaTeX row carried wrongly since `v0.78.0`.
- **`globalThis`, never `window` / `self` / `global`.**
- **New analytics events go in the `AnalyticsEventType` enum before being fired, with a real fire site.**

### Shipped

- **⚠️⚠️ ITEM 1 WAS BUILT, THEN REVERTED AT SIGNOFF. IT IS NOT IN THIS RELEASE.** A cold falsification pass replayed **1,158 affected production strings** through both the pre-fix and post-fix renderer and scored reader-visible raw LaTeX: **385 WORSE, 3 BETTER.** `startsCurrencyAmount` decided money-vs-maths from **the single character immediately after the digits**, and that character cannot discriminate — the dominant legitimate maths shape puts a space or an operator exactly where the predicate looked for a currency boundary. `$1.5 \times 10^4$` matched `1.5`, saw a space, was classified as money, its opener was skipped, **and a string that rendered CORRECTLY before printed raw after.** Scientific notation, arithmetic steps (`$0.4 + 0.5 = 0.9$`) and ratios (`$4:5$`) all broke — **concentrated in explanations and working solutions, the exact surface this release is named for.** Verified independently before reverting: the five cited production strings return `KATEX=0` with the fix and `KATEX=1` without it.
- **⚠️ THE GUARD TESTED THE CLAUSE, NOT THE POPULATION — the transferable lesson, and the reason four green mutations proved nothing.** The regression guard was `$3x^2$`, which survives **only because `x` is a letter**. Nothing tested `$3 \times 4$`. Every mutation killed, every test green, 2,383 passing — against a change that made 385 production strings worse. **A mutation test proves a guard discriminates for the case it encodes; it says nothing about whether the case is representative.**
- **What replaced it:** the original defect is carried as a **skipped** test in `quiz-question-text.test.tsx` with the full mechanism and the fix constraint, plus **three new guards built from the regression itself** — scientific notation, an arithmetic step and a ratio, all real production strings. Any future attempt must decide on the **whole candidate span**, not the next character, and must be measured against the 1,158-string corpus first.
- **Item 2 — `SummaryMarkdown` repairs bare maths before `remark-math` tokenises (frontend).** `remark-math` tokenises **delimited** maths only, so an undelimited `\frac` in a summary printed literally on every surface using the component. **36 production summaries** carry a backslash and no delimiter.
- **Item 3 — the shared Study Pack page renders maths at all (frontend).** `app/shared/study-packs/[id]/page.tsx` rendered summary, keyConcepts and fullNotes as raw `{value}` inside `whitespace-pre-wrap`. Summary now uses `SummaryMarkdown` — the component every sibling surface already used — and the other two use `renderMathText`. **⚠️ Traffic remains what the kickoff said: ONE linked-learner relationship, SIX share events in 90 days, `share_token` 0 across 7,573 packs.** Fixed as the same defect class, not as urgent work.
- **A new test file where none existed, and two mistakes in writing it are commented in place.** `app/shared/study-packs/[id]/page.test.tsx` had no equivalent to copy. **⚠️ The one that cost the most: a `useRouter` mock returning a FRESH OBJECT per call changes `router`'s identity every render, re-creating the `loadStudyPack` callback and re-firing its effect endlessly — the page sits in `loading` and the container holds only the BackLink, which looks exactly like "the content never rendered".** Also recorded: waiting on `screen` lets a stale render from a previous test satisfy the wait, and a negative assertion needs a positive settle signal or it passes on the blank loading frame.
- **Verification: 216 suites / 2,383 frontend tests green, `tsc --noEmit` clean, `npm run lint` 0 errors. Four mutations, each killed by a named test** — the naive currency rule (killed by *"still treats a properly-delimited number as math"*), dropping the letter/backslash check (*"still renders math that begins with a digit"*), removing `SummaryMarkdown`'s normalise call, and reverting all three shared-page fields to raw `{value}`.

### Checkpoint gate — no NEW checkpoint owed, and the reasoning is recorded rather than the step skipped

**Nothing in this release shipped ahead of its own evidence.** All three items fix a defect measured against production before any code was written: 44 quiz strings carrying both money and a formula, 36 summaries with a backslash and no delimiter, and a shared page rendering raw `{value}`. Item 3's traffic was measured and scoped honestly (one linked-learner relationship, six share events in 90 days) rather than inflated to justify inclusion.

**⚠️ AND A CHECKPOINT WOULD AGAIN HAVE BEEN DECORATIVE.** The gate requires instrumentation shipped in the same release and verified emitting. **This release added ZERO analytics events** — there is no metric for "a formula rendered as raw source", and adding one would mean instrumenting a render path to count its own failures. `v0.134.0`'s row is the standing example of a checkpoint whose instrumentation claim was wrong; writing one here to look thorough would repeat it.

**One checkpoint WAS added by this release, and it belongs to the incident rather than the feature:** `[CHECKPOINT — due 2026-09-17]` on the 2026-09-10 pool exhaustion. **⚠️ It declares itself a RECURRENCE WATCH, not a measurement** — `http_latency` returns an empty series, `httpPath` filtering returns empty, and log label `type` offers only `app`/`build`, so the metric that would name the cause does not exist. It is a tripwire and says so.

### ⚠️ Three checkpoints came due on 2026-09-11 and are NOT closed by this release

Named here so that closing this release does not carry them silently into the next kickoff, where step 9 would find them already overdue:

- **`v0.114.0`** — *was the startup line ACTUALLY READ from the production log?* **⚠️ This one needs a Render LOG read, not a database read, and the log tool requires a workspace confirmation only the owner can give — so it cannot be closed from inside a release at all.**
- **`v0.101.0` Slice 1** — Review Sets first-class + independent Notes + learner-facing "AI" language.
- **Learning Connections** — the ratified five-phase direction.


- **⚠️⚠️ THE SURFACE COUNT THAT SELECTED THE VERIFICATION TIER IS WHY THE TIER MISSED THE REGRESSION.** The kickoff recorded *"three surfaces ⇒ one scoped cold agent"* and pointed that agent at three files. But item 1 changed `findMathSpanFrom`, which is reached by **27 files** — every quiz mode (quick review, challenge, adaptive, long exam, interview practice, memorization, flashcards), the public library page, the public share quiz, onboarding and demo. **A 385-string regression sat outside the scope the release's own count had drawn.** The agent found it only because it was told to falsify the CLAIM rather than review the FILES. **When a change lands in a shared helper, count the call sites, not the files edited.**
- **⚠️ `SummaryMarkdown` normalises the whole markdown string, and markdown has literal-text regions it does not know about.** A fenced or inline code block containing a backslash command would be rewritten — `` `x^2 + y^2` `` becomes `$x^{2}$ + $y^{2}$`. **Latent, not live: 0 of 7,583 summaries and 0 of 91 companion rows contain a backtick with no `$`.** Tables, links, escaped characters, Windows paths and literal `\n` all survive correctly. Recorded because the component documents its *other* limitation carefully and was silent on this one.
- **⚠️ A visible behaviour change on the shared Study Pack page that the Key Features list does not mention:** the summary moved from `whitespace-pre-wrap` to markdown, so **single newlines now collapse**. **5,361 of 7,583** summaries contain one. This is convergence with every sibling surface rather than a regression — every other surface already rendered summaries as markdown — but it is a real visible change on that route.
- **Both curriculum Python scripts shipped with changed behaviour and nothing executing them.** `build_review_set_workbook.py` gained a required-column guard and `build_strategist_inputs_workbook.py` gained a `q4b` sheet and an optional README notes block. **There are no Python tests in this repo at all.** Verified by running the builder by hand against all four plan files (three build and reproduce their committed workbooks cell-for-cell; civil-engineering is refused) — but that is a manual check, not a guard, and it will not re-run.
- **Two soft spots in the new builder guard, neither reachable from the documented path:** a zero-row TSV raises `IndexError` on `rows[0]` instead of a message, and a cell containing only `","` passes the non-empty check while contributing no program.
- **`page.test.tsx`'s `@/lib/route-guards` mock is an allow-list** replacing four real exports with one. Harmless today because the page imports only `requireVerifiedOnboardedUser` — fragile if it ever imports another. The sibling `@/lib/api` mock deliberately uses `requireActual` for exactly this reason.
### Known limitations

- **⚠️ A FOURTH SURFACE HAS THE SAME DEFECT AND IS DELIBERATELY NOT FIXED HERE.** `app/study/study-pack-results.tsx:114` renders `keyConcepts` as raw `{concept}`, exactly as the shared page did. It is a one-line change, but the verification tier for this release was set at kickoff on **three** surfaces, and widening it after the fact is how a release quietly outgrows its own tier. Named so it is a known candidate rather than a future rediscovery.
- **⚠️ `SummaryMarkdown`'s repair covers the 36 measured summaries and no more.** `normalizeBareMath` returns early on **any** delimiter anywhere in the string it is handed, and a summary is one long multi-paragraph string — so a summary already containing a single `$` is left entirely alone, bare expressions elsewhere in it included. Splitting per paragraph to widen this would change what `remark-gfm` sees and was judged not worth the blast radius.
- **The allowlist gaps are still open and still out of scope**: `\to`, `\text{m/s}` and `90^\circ` render raw, the last because `readScriptValue` rejects a backslash as a script value. A separate finding, deliberately not bundled.

## v0.140.0 - Pending Work in Reach

**Status: Released** (kicked off 2026-09-10, signed off 2026-09-10, base branch `releases/v0.140.0`, cut from `main` after `v0.139.0` merged and tagged)

Theme: a curator arranging a 79-note review set works hundreds of pixels below the only control that commits their arrangement. This puts the commit in reach, and says honestly what is pending.

Source: `docs/claude-plans/authoring-and-quiz-legibility-fix-plan.md` §§5-7 and **§10** — owner-reported 2026-09-05 from real use with screenshots, audited against code, tightened by the owner, then a GPT tightening pass. **⚠️ Read §10 FIRST.** It is the plan's own record of three places where the code contradicts the tightening, and one of them removes a premise this release would otherwise ship on.

**⚠️ THIS IS A LEGIBILITY FIX, NOT A DATA-LOSS DEFECT — stated so the release is not oversold.** Navigation protection already exists and was **verified in code at kickoff** (`study-plan-builder-page-client.tsx:1663-1671`: a `beforeunload` handler plus an in-app click interceptor, both gated on `leafOrderDirty`). **Work is not silently lost today.** The failure is a curator who drags on a long plan, scrolls away from the header that holds Save, and redoes the arrangement. Recoverable, and worth fixing because it lands on the only people actually using the product.

**The trigger is verified live, not assumed:** production holds a **79-note** plan (General Education), two **77-note** plans (Engineering Mathematics), a **76-note** plan (Geotechnical Engineering) and five more at 59+. In the 14 days to 2026-09-10, **12 authors created 1,768 notes** — 7 `BOARD_EXAM` learners (919), the admin account (782) and 4 students (67). This release serves them.

### Planned Scope

1. **The dirty-state sticky bar (frontend).** Shown only while there are pending changes, absent otherwise, holding the pending-state text plus Discard and Save. **⚠️ No two equally-prominent Save controls** — the buttons at `study-plan-builder-page-client.tsx:2496-2515` move INTO the bar; if the card header retains anything it is status text, never a competing primary action.

2. **Copy that describes what is actually pending — `[DECISION]`, owner's call, owed before implementation.** **⚠️ Both obvious wordings are wrong in opposite directions**, per §10 Finding B: `Unsaved changes` **over-claims** (it implies the already-persisted combobox pick is pending), and `Order changes not saved` **under-claims** (`leafOrdersMatch` at `:118-125` compares `noteId` sequence **and** `label`, so a pending drag can also have moved a note between sections). The plan proposes `Arrangement not saved` or `Drag changes not saved`. **The semantic requirement is fixed even though the words are not: the bar must describe drag-originated order AND placement, and must not promise isolation.**

3. **§7 navigation protection — narrowed, and narrowed on evidence.** §7's stated premise (*"current silent loss is unacceptable"*) is **false** and §10 Finding C says so; the handler exists. What remains is real but smaller: the existing `confirm()` offers **two** choices where the owner requires **three**, plus §7's named coverage gap. **Scope it as the gap, not as the original item.**

4. **Backlog Index corrections — three rows that claim open work which has shipped.** **⚠️ Each was found by opening the code, and each would have been offered to the owner as a release candidate.** (a) The **LaTeX (b)** row reads *"BLOCKED until 2026-09-11 … candidate for `v0.96.0`"*; `remark-math` is at `summary-markdown.tsx:3`, shipped **`v0.100.0`, 2026-08-29**. (b) The **public-catalog unbounded read** row reads *"⚠️ STILL OPEN IN CODE, BUT ITS GATE BECAME TRUE AND NOBODY ACTED"*; **all three legs shipped in `v0.119.1` on 2026-09-06** (`d10d92bc` Legs B+C, `542622f1` Leg A) — the gate said *"un-parks the moment `v0.119.0` is signed off"* and the fix landed in the very next release, so somebody acted immediately. (c) That row was **"verified" as unfixed by the `v0.138.0` verification pass**, which is recorded as an error of that pass, not quietly repaired.

### The structural finding this release records

**Every stale row found across `v0.138.0`, `v0.139.0` and this kickoff was stale in the SAME direction — overstating open work — and every one shipped in a release that never re-read the row.** `v0.138.0` added a verification procedure to **kickoff**; nothing updates a Backlog row when the thing it describes **ships**. That is a missing signoff step, and it is the cheap fix. **⚠️ The near-miss that makes this concrete: on 2026-09-10 the onboarding redesign — shipped as `v0.73.0` a month earlier — came within one verification step of being scoped and rebuilt as an 8-screen rewrite of a 2506-line file.**

Anti-drift — locked:

- **⚠️ Autosave-per-drop stays REJECTED. Do not re-propose it.** It raced itself: each drop awaited a save plus a full refresh, nothing gated dragging meanwhile, so a second drag wrote from a diverging base and was clobbered when the first refresh landed. **Two releases were paid to close this.**
- **⚠️ Do NOT change the combobox flush (§10 Finding A).** `handleLeafLabelChange` calls `moveLeafNote(..., deferSave = false, ...)`, so a section pick persists the curator's pending drags too and clears the dirty state. That is deliberate — `CLAUDE.md` records that non-drag mutations must **"flush, never discard"** — and it is the safe direction, because pending work is saved rather than lost. **Requirement 9 is satisfied vacuously, not by isolation; fix the COPY, not the behaviour.**
- **⚠️ Item 4 (immediate section commit) already SHIPPED in `v0.117.0` and is not reopened here.** The reason items 4 and 5 were split stands: immediate commit makes the flush reachable in one click, so the sticky bar will disappear the moment a section is picked. **Benign — the work is saved — and it must not be "fixed".**
- **⚠️ The Challenge Quiz bank-write isolation is NOT in this release.** Verified genuinely unshipped at kickoff (`ChallengeQuizQuestionBankService.java:118-125`; the `REQUIRES_NEW` at `:235` is `releaseClaims`, a different method). It carries `[DECISION]` with three shapes that differ in **failure semantics**, not just mechanics. It needs that decision before it can be scoped, and must not be folded in because it is nearby.
- **⚠️ No `frontend/app/onboarding` work.** The `2026-09-11` checkpoint closed 2026-09-10 as **KILL CRITERION NOT CLEARED**; its own pre-committed wording says **reopen the framing rather than iterate on further onboarding polish**. Do not treat the closure as permission.
- **No new drag-and-drop or animation dependency.** Use the motion vocabulary already in `globals.css`.
- **`globalThis`, never `window` / `self` / `global`** — ESLint enforces it.
- **Collection vocabulary stays profile-aware** — no hardcoded "Study Plan" or "Review Set" in copy.
- **New analytics events go in the `AnalyticsEventType` enum before being fired, with a real fire site** (`v0.116.0` / `v0.117.0` both shipped events that could never fire).

### Shipped

- **The dirty-state sticky bar (frontend).** While drag changes are pending, a bar sticks to the bottom of the builder carrying **`Drag changes not saved`** plus Discard and Save changes. **The Save/Discard controls were MOVED out of the "Your notes" card header, not duplicated** — that header scrolls out of view on a long plan, which is the whole defect, and a test now asserts there is exactly **one** Save and **one** Discard in the document. The header keeps status text only (progress states plus the idle "Drag notes or …s to reorganize."), never a competing action.
- **Copy decided by the owner, with its reasoning pinned in the code.** `Drag changes not saved`, chosen over `Unsaved changes` (over-claims — implies the combobox section pick is pending, and it is not: that path persists immediately) and `Order changes not saved` (under-claims — `leafOrdersMatch` compares `noteId` sequence AND `label`, so a pending drag can also have moved a note between sections). The comment at the bar tells the next reader not to "improve" it without re-reading §10 Finding B.
- **A three-choice navigation dialog replaces the two-choice `confirm()` (frontend).** In-app link clicks while dirty now offer **Save and leave**, **Discard and leave** and **Keep editing**. The old dialog offered only "lose it" or "stay", so a curator who had genuinely finished had no way to leave *with* their work. **⚠️ A FAILED SAVE DOES NOT NAVIGATE** — the dialog stays open, says nothing was lost, and offers a retry; a dialog that navigated on a failed save would be a *new* way to lose pending work, strictly worse than what it replaced. `beforeunload` stays a **warning only**, deliberately: the browser permits no custom actions and no reliable async save, and promising a save path the page lifecycle cannot guarantee is worse than warning honestly.
- **Modified clicks are deliberately not intercepted.** A cmd/ctrl/shift/alt or middle click, and any `target` other than `_self`, opens elsewhere and leaves the builder and its pending drags exactly where they are — interrupting it would be a dialog for a problem that does not exist, and would cost the curator the new tab. Guarded by its own test.
- **Verification: 66 tests in `app/collections/[id]/builder/page.test.tsx` (6 new), `tsc --noEmit` clean, `npm run lint` 0 errors.** **Six mutations were applied and each was killed by a named test:** navigating despite a failed save; intercepting modified clicks; restoring a second Save control in the header; a *"Discard and leave"* that does not discard; **removing the bottom-viewport claim**; and **restoring the header control under its own old `"Save order"` label**. **⚠️ Five existing tests were CORRECTED rather than left passing for the wrong reason** — four pinned the old `"Save order"` label and one pinned the old `confirm()` two-choice guard.
- **⚠️⚠️ FIXED BEFORE SHIPPING, FOUND BY A COLD PRESSURE TEST AND BY NOTHING ELSE: the sticky bar covered the mobile navigation.** The bar is `sticky bottom-4 z-30`; `MobileBottomTabBar` is `fixed inset-x-0 bottom-0 z-20 md:hidden` and 5.5rem tall. A **higher** stacking order plus a 1rem offset means that on a phone the bar pinned directly over the tab bar and won — and `mobile_tab_bar_enabled` defaults **TRUE** (`V94`), so that was the default experience, not an edge case. The builder now calls **`useBottomViewportClaim(leafOrderDirty)`**, the repo's existing mechanism for exactly this (`app-shell.tsx:584` gates the tab bar on `!isBottomViewportClaimed`, and Long Exam, Challenge Quiz and Quick Review all already claim it). Claimed on `leafOrderDirty` specifically, so browsing a plan never removes the curator's navigation. **⚠️ This was determinable from CLASS NAMES ALONE, so the release's own "sticky positioning is unverified because jsdom computes no layout" caveat did NOT cover it** — the caveat named the right gap and still missed what was sitting inside it.
- **⚠️ A guard this release advertised as binding was bypassable, and the claim is corrected rather than quietly fixed.** Commit `49bc12ea` states *"a test asserts exactly one Save and one Discard exist in the document, so restoring the header buttons fails CI"*. **It did not.** The assertion pinned the accessible name `"Save changes"`, while the header control this release removed was labelled **`"Save order"`** — so restoring it under its own name passed every cited assertion. The guard now matches `/save/i` and `/discard/i` and asserts the single match lives inside the bar, which no relabelling satisfies twice. Mutation-verified by re-injecting a header `"Save order"` button: it now fails.

- **`/signoff` gains a Backlog-row closure gate, which is the structural fix for five stale rows (docs).** For every item a release ships, the row that described it must be opened and marked shipped **with a `file:line`** — plus its `Gate` cell when the release satisfied it, and **never from the release notes alone**. **⚠️ `v0.138.0` added a verification procedure to KICKOFF; nothing updated a row when the thing it describes SHIPPED**, so a row written at proposal time was never touched again. Every stale row found so far was stale in the **same direction — overstating open work** — which is what makes it dangerous rather than untidy: such a row gets offered to the owner as a release candidate. Recorded in `.claude/commands/signoff.md` and `CLAUDE.md`.
- **Eight Backlog rows had content in the wrong columns, and the repair recovered eight real dates (docs).** Distinct from `v0.138.0`'s four-column class. Three rows were missing a **`Source`** cell (folded into `Item`); five were missing a **`Gate`** cell, so `v0.138.0`'s appended `⚠️ never stamped` shunted a **genuine `Last reviewed` date into the `Gate` column** — where kickoff step 9 would read it as a gate condition. Each is repaired in place, and the five carry an explicit note that they never had a `Gate` cell rather than an invented one.
- **The Study Plan Builder section-label refresh loop is confirmed SHIPPED — the fifth stale row of this cycle, and the first found by the new gate (docs).** Verified at `study-plan-builder-page-client.tsx:498-531`: the guard now compares through the shared `canonicalSectionLabel`, holds a `lastRequestedLabelRef` keyed on both current and requested label, and reads `onLabelChangeRef` instead of putting a re-created callback in the dependency array — which was what made the effect re-run on every render. **⚠️ Its ingress question is still unresolved and is NOT closed by the mechanics fix.**
- **Two "unmeasured by decision" rows are now measured, both by read-only production `SELECT`s (docs).** **⚠️ Both rows also claimed the query was *"the owner's to run"* — that is wrong and is corrected: a `LIKE` scan is a `SELECT`, which `CLAUDE.md` permits; only WRITES are the owner's.**
  - **Contaminated note titles: the debt is 89 notes across 8 programs, and it is a closed population.** Discriminating on titles ending in the note's **own** `course_program` — the Bulk Generate overwrite shape — rather than the raw `% in %` scan, which returns 1,077 mostly-legitimate matches. Earliest 2026-05-23, **latest 2026-08-02, none since**. ⚠️ It stopped a month *before* `v0.120.0` shipped, so the row's claim that `v0.120.0` is what stopped it is **not** established by this read.
  - **⚠️ The raw-LaTeX row is not the closed curator backlog it describes — it is a live generation defect.** Re-running its own `v0.74.0` query: `NEEDS_FIX` = **15** (down from ~23, as the row predicted), `MIXED_CHECK_IT` = 189, `LIKELY_OK` = 224. **But 7 of the 15 were generated in the last 14 days**, and 184 of the 189 `MIXED_CHECK_IT` since `v0.74.0` deployed. Its `Math notation` prompt rule **reduces but does not eliminate** undelimited math. **⚠️⚠️ THIS BULLET WAS WRONG TWICE BEFORE A COLD PRESSURE TEST SETTLED IT, AND BOTH ERRORS ARE KEPT HERE BECAUSE THE SECOND ONE ALMOST DISMISSED A REAL LEARNER-FACING DEFECT.** (1) It first concluded an unscoped *engineering* half existed because the consuming components contain no call to `normalizeBareMath` — that inference was wrong, `renderMathText` calls it internally (`quiz-working-solution.tsx:206`). (2) It then concluded there was therefore **no defect and the work is curator-only** — **that was wrong too.** **What the cold agent established by running the real renderer against strings pulled from production:** `normalizeBareMath` returns immediately on **any** delimiter (`math-normalization.ts:264-266`), and **313 of 339** backslash-bearing questions and **470 of 498** explanations already contain one — so the repair fires on almost nothing in the live corpus, and the premise is true of the CODE while false of the DATA. **The real defect is delimiter MIS-PAIRING, not missing delimiters:** in a question reading *"if an asset costs `$50,000` … `$A = P \times \frac{i(1+i)^n}{(1+i)^n-1}$`"*, the currency `$` opens a math span that closes on the formula's `$`, KaTeX fails on the enclosed text and the fallback re-emits the source — **zero rendered math, full raw LaTeX on screen**. A sibling case swallows the prose instead, collapsing *"benefit of $10,000 received 3 years from now"* into run-together italics. **Population at risk: 22 questions and 49 explanations** carrying `$<digit>` with two or more `$`. **⚠️ NONE OF THIS IS FIXED IN `v0.140.0` and it must not be read as fixed** — it is recorded so the next scoping pass starts from the right mechanism. Two adjacent gaps the same pass found: `app/shared/study-packs/[id]/page.tsx:69,75,81` render summary/keyConcepts/fullNotes as raw `{value}` with **no math rendering at all** (372 production summaries carry a backslash), and `SummaryMarkdown` never calls `normalizeBareMath`, so bare math in a summary is never repaired (12 production summaries). **What `v0.140.0` ships is only the regression guard** in `quiz-question-text.test.tsx`, mutation-verified against deleting the repair call.

### Checkpoint gate — no checkpoint owed, and the reasoning is recorded rather than the step skipped

**Nothing in this release shipped ahead of its own evidence.** The sticky bar answers an owner report from real use whose trigger was verified in production before any code was written (a 79-note plan, two at 77, one at 76, five more at 59+; 12 authors creating 1,768 notes in 14 days). The copy is an owner `[DECISION]`, not a bet. §7 shipped **narrowed by evidence that falsified its own premise** — *"current silent loss is unacceptable"* was false, because `beforeunload` and an in-app interceptor already existed. The Backlog corrections, the closure gate and the two sizing reads are verification work and assert no outcome.

**⚠️ AND A CHECKPOINT HERE WOULD HAVE BEEN DECORATIVE, WHICH IS THE OTHER HALF OF THE GATE.** The gate requires instrumentation shipped in the same release and verified emitting. **This release added ZERO analytics events** — the only event the builder fires is `COLLECTION_SECTION_ASSIGNED`, which measures section assignment and not the save-order flow. With no metric and a denominator of 12 authors, any checkpoint would have been a date with nothing behind it. `v0.134.0`'s row is the standing example of a checkpoint whose instrumentation claim was wrong; writing one here to look thorough would repeat it.

**What this release owes instead is a VERIFICATION debt, not a measurement one, and it is the owner's:** one look in a real browser at the sticky bar against a long plan, at phone and desktop width. That is recorded below rather than counted as done.

### Known limitations

- **⚠️ The navigation interceptor still covers `a[href]` clicks only — named here rather than left to be discovered.** Programmatic `router.push`, browser back/forward, and any navigation from a control that is not an anchor are **not** covered. §7 of the plan put extending this in scope for Release B and instructed that the residual ship as a named limitation if it could not be done; App Router makes `popstate` interception unreliable enough that half-building it would give a false sense of coverage. **On those paths pending drags ARE lost silently** — `beforeunload` covers only refresh and tab close, not an in-app programmatic navigation, so nothing warns the curator.
- **⚠️ The chosen copy names an input device, and one pending path is not a drag.** The plan justified `Drag changes not saved` as *"everything pending came from a drag"*. That is not exactly true: the keyboard **Move up / Move down** controls are the accessible equivalent of dragging and also defer, so a curator who never touches a pointer can still be shown this wording. The owner chose it knowing the alternatives; `Arrangement not saved` is the candidate that covers both without naming a device. Recorded in the code comment beside the bar.
- **⚠️ The sticky positioning is still not covered by any test, but the gap is now NARROWER than first recorded.** jsdom computes no layout, so the suite passes whether the bar pins or sits in normal flow. **⚠️ This caveat was originally written as though "unverifiable" and "unknown" were the same thing, and they are not — a cold pressure test read the ancestor chain and the stacking context from class names and found a real, shipping defect inside the gap this bullet had already declared (the mobile tab-bar collision above).** What is now verified statically: the ancestor chain `body.min-h-screen` → AppShell → `<main>` → page `<main class="flex flex-col">` carries no `overflow` other than visible, so `sticky bottom-4` resolves against the document scroller; and the bar no longer competes with the mobile tab bar. **What remains genuinely unverifiable here is only whether it LOOKS right** — one look in a real browser against a long plan, at phone width and desktop.
- **⚠️ The cross-section drag case has no UI test, because the suite cannot reach it.** `leafOrdersMatch` comparing `label` is what makes the copy honest about section placement, but the only deferred path that changes `label` is `handleLeafDragEnd`, and this suite has **no dnd-kit simulation at all** — every existing "drag" test uses the arrow controls, which are within-section. Rather than hand-build a state no code path in the test can produce (the `v0.116.0` / `v0.117.0` failure), the gap is recorded. The within-section pending case **is** covered.

## v0.139.0 - Reopened

**Status: Released** (kicked off 2026-09-10, signed off 2026-09-10, base branch `releases/v0.139.0`, cut from `main` after `v0.138.0` merged as #1363 and tagged)

Source: `docs/claude-findings/2026-09-10-september-checkpoint-reads.md` — the reads that came due, run at this kickoff **before** scope was proposed. **Read it first: item 1 is not a metrics chore, it is a pre-committed rule firing.**

Theme: a checkpoint fired, so the thing it was watching gets reopened — and the detector that missed a deploy learns to report what it found.

### ⚠️⚠️ THE `v0.72.0` RETENTION CHECKPOINT FIRED ITS KILL CRITERION

**VERIFIED read-only, 2026-09-10, window 2026-08-11 (deploy) → 2026-09-09: nine learners were shown the review-commitment prompt. ZERO committed. One declined.**

**⚠️ THE ZERO IS REAL, AND TWO INDEPENDENT INSTRUMENTS AGREE — this was checked first, because `v0.116.0` and `v0.117.0` both shipped events that could never fire.** `COMMITTED` and `DECLINED` are emitted from **the same line** (`review-commitment-prompt.tsx:102`, a ternary); `DECLINED` fired once, so the call site provably executes and the other branch simply never happened. Independently, `SELECT count(*) FROM users WHERE cardinality(review_days) > 0` returns **0** — **the entity table agrees with the event stream, which rules out analytics delivery loss**, the bias that made `v0.80.0` necessary.

**The pre-committed rule, quoted from the row and written before the read:** *"the return-loop framing reverts to **unconfirmed** and is **reopened rather than iterated on with further nudge tuning**."*

### ⚠️ OWNER OVERRIDE, RECORDED RATHER THAN SMOOTHED OVER

**The owner elected to REDESIGN THE PROMPT rather than only reopen the framing — which is the "nudge tuning" the pre-committed rule names.** They were told that before choosing. It is recorded here because a pre-committed rule that is quietly stepped over stops being a rule, and the next checkpoint inherits the precedent. **The consequence: this release ships a redesign on a `n=9` signal, so it owes a checkpoint with a real denominator — see below.**

**⚠️ WHY `n=9` IS SMALL BUT NOT NOTHING, STATED HONESTLY:** at nine impressions a modest true commit rate (~10%) is not excluded by chance; a high one (≥30%, P(zero) ≈ 4%) effectively is. **The redesign is therefore a bet, not a correction.**

### Planned Scope

1. **Instrumentation first — the redesign is unmeasurable without it, and this is NOT optional.** (a) The prompt fires **only after a successful save** (`review-commitment-prompt.tsx:96-107`), so **8 of 9 learners vanished with nothing recorded** and the funnel cannot tell *ignored* from *considered and rejected*. Add a dismiss/abandon event. (b) All 11 `DUE_CONCEPTS_DIGEST_LANDED` rows carry a **NULL `user_id`**, so the checkpoint's second metric — *"digest → first answer **among committers**"* — **was never computable**. Give the event its user.
2. **Redesign the commitment ask** (owner decision, 2026-09-10). **⚠️ The design constraint that matters is the trigger, not the copy:** it renders on `isFirstCompletedSessionEver === true`, so it is **ONE impression per learner, ever**, asking for a weekday multi-select plus an exam date **immediately after a first session, before the learner has seen any payoff**. Nine impressions in a month is the trigger being narrow, not the copy being weak.
3. **`scripts/check-deploys.sh` — report a confirmed drift as drift.** **VERIFIED empirically against today's live miss:** the script prints *"VERCEL: serving 98ef1955, but origin/main is 05c367c4 — BEHIND"* and then **exits 2**, which its own contract defines as *"could not check"* rather than *"drift"*. `drift=1` is set at `:63` and discarded by the `exit 2` at `:82`. **A caller reading the exit code — `/signoff`, or any CI job — sees "I could not look" when the truth is "Vercel is definitively behind."**

### ⚠️ Vercel and the deploy-latency false positive — CORRECTED

**⚠️⚠️ CORRECTED 2026-09-10, SAME DAY: VERCEL DID NOT MISS `v0.138.0`. It deployed `05c367c4` at 01:21:06Z, **4 minutes 24 seconds after the 01:16:42Z merge** — it was IN FLIGHT when this session checked, and the check was read as an absence. **The true record is ONE confirmed miss (`v0.136.0`), not two of three: `v0.137.0` and `v0.138.0` both auto-deployed normally.** ⚠️ **THIS IS `v0.137.0`'s OWN RULE BROKEN THE DAY AFTER IT WAS WRITTEN** — a production-state reading taken at one instant and asserted as a standing property. **⚠️ AND IT IS A REAL LESSON FOR THE DETECTOR, NOT JUST AN EMBARRASSMENT: testing for ABSENCE requires waiting past the normal deploy latency, or the test manufactures its own false positive.** Observed latency is ~2–5 minutes on both platforms. **Item 3's defect is UNAFFECTED and still real** — it was reproduced by mutation against the pre-fix script, independently of any live drift.** 

**Original (wrong) reading, kept for the record:** Render auto-deployed `v0.138.0` (`dep-dah09v15efls739b7t9g`, `new_commit`, **live** 01:18:59Z); **Vercel has no Production deployment for that commit at all.** Missed `v0.136.0`, fired for `v0.137.0`, missed `v0.138.0`.

**⚠️ THE CONSEQUENCE WAS COSMETIC THIS TIME AND THAT IS LUCK, NOT DESIGN:** `v0.138.0`'s only frontend change is the `package.json` bump and **no controller or `lib/api` file changed**, so there is no API-form skew of the kind that killed Your Impact in `v0.136.0`. **⚠️ THE UPSTREAM CAUSE IS NOT FIXABLE FROM THIS REPO** — transient GitHub push-event delivery loss to the Vercel GitHub App, INFERRED and unchanged from the `v0.136.0` diagnosis. **This release fixes the reporting, not the cause, and must say so.**

### ⚠️ Anti-drift

- ❌ **Do NOT ship item 2 without item 1.** A redesign that cannot be measured reproduces the exact position this release is in — and the read that would judge it is already blind in two places.
- ❌ **Do NOT re-date the `v0.72.0` proximal checkpoint as though it had not fired.** It fired. The row records FIRED plus the owner override; a silent re-date would erase the only evidence the rule was overridden.
- ❌ **Do NOT claim this release fixes the Vercel auto-deploy.** It fixes the exit contract. The cause is upstream and stays unfixed.
- ❌ **Do NOT make `/actuator/metrics` public to make a checkpoint readable.** The `v0.134.0` row's *"externally readable"* claim is simply WRONG — `application.yaml`'s own comment says *"not permitAll … stays authenticated-only"*. **Correct the row, not the security posture.**
- ❌ **Do NOT add a new analytics event without a fire site** — that is the `v0.116.0`/`v0.117.0` defect, and this release's own headline finding only survived scrutiny because the fire site was proven live first.
- ❌ **No Learning Connections work** (`[CHECKPOINT — due 2026-09-19]`, denominator ONE). **No `frontend/app/onboarding` work before the `2026-09-11` read.**
- ⚠️ **The four `FAILED` notes from `v0.138.0` are still the OWNER's to re-run** — the backend is live on `v0.138.0` as of 01:18:59Z, so that clock has started.

### ⚠️ Pre-declared guards

- **Item 1a:** assert the dismiss event fires on the **close/ignore path specifically** — ⚠️ a test that only asserts "some event fires" passes under the current code, which already fires on save.
- **Item 1b:** assert the persisted `DUE_CONCEPTS_DIGEST_LANDED` row **carries a non-null `user_id`** — ⚠️ not that the client sent one; the 11 existing rows prove the gap is at persistence or auth-resolution time.
- **Item 3:** assert **exit code 1** when Vercel is behind and `RENDER_API_KEY` is absent. ⚠️ **A test asserting only that the message is printed passes under the defect** — the message already prints today; the exit code is the whole bug.
- **Item 2:** at least one test must exercise the trigger condition, not just render the component with `visible=true` — the trigger is the finding.

### Verification tier

**Three items, one of them docs-adjacent.** Items 1 and 3 are small and testable; item 2 is a frontend redesign across the five surfaces that render the prompt. **Tier: one `advisor()` call on the diff**, plus the four guards above. **⚠️ It rises to one scoped cold agent if item 2 grows a backend surface** — the `reviewCommitmentOutstanding` flag and `users.review_days` are already there, so it should not.

### Routing

**CODEX for items 1 and 2** — frontend across five call sites plus a backend analytics change; more than five files. **CLAUDE CODE inline for item 3** — one shell script, one exit path.

### Scope completeness — each planned item against the code that implements it

| # | Planned | Verdict | Evidence |
|---|---|---|---|
| 1 | Instrumentation first — a dismiss/abandon event, and a `user_id` on `DUE_CONCEPTS_DIGEST_LANDED` | **SHIPPED** | `REVIEW_COMMITMENT_DISMISSED` at `AnalyticsEventType:48` with **one real fire site** (`review-commitment-prompt.tsx:97`); the 401 at `AnalyticsController:28` |
| 2 | Redesign the ask — re-showable trigger, committing becomes an upgrade | **SHIPPED, and CHANGED mid-release** | `V144`; `AuthService#isReviewCommitmentPromptEligible`; `MeController:39`; `RetentionService:51`. **⚠️ Changed twice against evidence — see below** |
| 3 | `scripts/check-deploys.sh` reports a confirmed drift AS drift | **SHIPPED** | `scripts/check-deploys.sh` precedence block; `scripts/check-deploys.test.sh` (9 cases) |

**⚠️ ITEM 2 CHANGED TWICE AFTER IT WAS SCOPED, AND BOTH REVERSALS ARE RECORDED RATHER THAN SMOOTHED INTO THE ORIGINAL PLAN.** (1) The first design re-triggered on *return after a gap*; its own query refuted it — only **7 of 136** stranded learners had returned in 14 days, so any in-app trigger tops out at 7–13/month. (2) `advisor()` then found, **before the Codex prompt was written**, that the ask had **no benefit to offer at all** — `isEligibleReviewDay` returns `true` for empty `review_days`, so choosing days *restricted* eligibility rather than granting reminders. A one-tap *"remind me"* button would have shipped as a no-op. The owner then chose to make committing genuinely mean something, and later to stop asking learners whose digest is off. **The release describes what was built, not what was first proposed.**

### Shipped

- **Item 1 — commitment and digest instrumentation.** Review-prompt abandonment now emits
  `REVIEW_COMMITMENT_DISMISSED` with `exit=pagehide|unmount`, deduplicated to one dismissal per
  impression and suppressed after either saved outcome. `DUE_CONCEPTS_DIGEST_LANDED` now requires a
  resolved principal: an expired bearer on the otherwise-public analytics endpoint receives `401`,
  allowing the existing analytics refresh-and-retry path to persist the landing with its `user_id`;
  events that genuinely originate anonymously remain accepted.
- **⚠️ THE `401` EXCEEDED THIS ITEM'S STATED CONSTRAINT AND WAS ACCEPTED ON REVIEW — recorded as an expansion, not as the plan.** The prompt said the fix *"must not delay or drop the `LANDED` event"* and *"do not make the analytics endpoint reject anonymous events"*. The delivery does both, narrowly: a new `AuthenticationRequiredException`, a new status on a `permitAll` endpoint, and a behaviour change to a shared analytics path — none of which item 1 was scoped for. **It was accepted because the diagnosis is correct and the alternative is worse** (an unattributable landing can never answer the checkpoint's question), and because the rejection is scoped to one event type with `anonymousAnalyticsEvents_remainAccepted` guarding the boundary. **The reasoning is stated so a later reader does not mistake it for what was asked.**
- **⚠️ AND THE `401` HAS A COST THE NOTE MUST NOT OMIT: a landing whose token cannot be refreshed is now DROPPED, where it previously persisted with a NULL `user_id`.** `dueConceptsDigestLanding_withoutResolvedPrincipal_requestsAuthenticationRetry` asserts exactly that — 401 **and zero rows**. The trade is deliberate: an unattributable landing could never answer the checkpoint's question (*digest → first answer **among committers***), and the refresh-and-retry path it now reaches **already existed** at `lib/api.ts:3351` and was simply unreachable while the endpoint answered `200` to an expired bearer. **⚠️ But it changes what a landing COUNT means** — the 619-sends/11-landings ratio is not comparable across this change, and `trackAnalyticsEvent` still returns without retrying when `visibilityState === "hidden"`. **⚠️ FOR THE DISMISS EVENT THAT IS NOT AN EDGE CASE, IT IS THE COMMON PATH: abandonment fires on `pagehide`, when visibility is hidden BY DEFINITION**, so a dismissal sent with an expired token is lost **every time**, not occasionally. The `unmount` exit can still retry. **This is a known limitation of item 1's headline metric and is recorded rather than papered over** — the dismiss count is a floor, not a total.
- **Hardened the abandonment effect against a false positive found in the audit, and pinned it with a test.** `trackDismissed` closed over `noteId`, and **four of the five call sites pass `note?.id ?? null`** — so a `null → value` transition while mounted would run the effect's **cleanup**, firing a dismissal the learner never performed, with a stale `entityId`, and latching the state machine to `dismissed` so the real abandonment could never be recorded. Today's ordering makes it unlikely (the prompt renders only after a completed session), **but that is ordering, not a guarantee.** `noteId` is now read through a ref and the effect owns an empty dep array. ⚠️ **Mutation-verified: closing over `noteId` again fails `does not report abandonment when noteId resolves while the prompt is open`, and nothing else.**
- **The dismissal guard is discriminating, verified by mutation with the killing test named.** Deleting the `resolved` transition on save — so a saved outcome would later report as abandonment — fails **`does not report a saved decline as abandonment`** and only that test. ⚠️ **A test asserting merely that "some analytics event fires" passes under the defect**, because save already fired one; that is why this one asserts the absence after a save.
- **The forbidden over-broad change is guarded too.** Rejecting *every* anonymous analytics event — which the prompt explicitly ruled out, since other callers legitimately have no user — fails `anonymousAnalyticsEvents_remainAccepted`.
- **The zero first-answer result is genuine engagement data, not a dead query-string gate.** The email
  links directly to `/notes/{noteId}/quick-review?source=due-concepts-digest`; that route renders the
  quiz without navigation, session creation does not replace the URL, and the only legacy
  `/study-packs/{id}` redirect copies the full query string. The answer handlers therefore still see
  `source=due-concepts-digest`. No first-answer code changed. **⚠️ VERIFIED IN THE AUDIT RATHER THAN TAKEN ON REPORT** — the one `router.replace` on that page (`quick-review/page.tsx:396`) is the legacy `/study-packs/` → `/notes/` redirect, it sits inside an error handler, and it **explicitly copies the query string** into its target; the other two `router.push` calls are exits to `/dashboard`. **So the dead-gate hypothesis this release opened with is REFUTED, and the zero is a real product finding: 619 digests sent, 11 landings, 0 first answers.**
- **Documented the commitment surface and its actual scheduling meaning.** `users.review_days` is
  initially collected after a completed session, later editable in Settings, and narrows eligible
  digest weekdays; null or empty days do not disable the digest.
- **Item 2 — the commitment prompt is re-askable and committing is now an upgrade.** Server-owned
  eligibility allows an unanswered learner to see the prompt after a later completed session, with a
  14-day cooldown and a lifetime cap of three impressions. A transactional, row-locked
  `POST /me/review-commitment/prompted` records each eligible impression without writing
  `review_commitment_prompted_at`, which continues to mean *answered*; client `sessionStorage`
  deduplication and the server eligibility update make the impression idempotent across remounts and
  duplicate requests.
- **All five completion call sites now use the same server decision.** Long Exam, Adaptive Practice,
  Board Exam, Challenge Quiz, and Quick Review no longer pass `isFirstCompletedSessionEver` into the
  prompt. The prompt explains the existing weekly nudge and the benefit of choosing days, keeps
  Monday/Wednesday/Friday selected by default, and keeps the BOARD_EXAM exam-date field.
- **⚠️ CORRECTED IN THE AUDIT: the exam date was NOT "kept optional" — it was REQUIRED, and this release makes it optional.** A BOARD_EXAM learner previously could not commit at all without supplying one (`review-commitment-prompt.tsx:121-124`, *"Choose your exam date before setting your review plan."*). That gate is now removed, because the prompt's stated purpose is review days and the release brief said the exam date must not block the primary action. **⚠️ THE CONSEQUENCE IS A WEAKER COLLECTION PATH AND IT IS NAMED HERE RATHER THAN LEFT TO BE DISCOVERED: 79 of 185 BOARD_EXAM accounts (43%) still have a NULL `exam_date`, and this prompt was one of the few places that collected it.** Fewer will now be captured. The field still renders and still saves when filled. **Mutation-verified: restoring the requirement fails `lets a BOARD_EXAM learner commit while the optional exam date is empty`, so the change is deliberate and covered rather than incidental.**
- **⚠️ The completion gate moved from the component to its callers, which is a contract change worth stating.** The prompt used to hide itself unless `isFirstCompletedSessionEver` was true, so it was safe to render anywhere; server-owned eligibility is about the **ask**, not about whether a session just finished. All five call sites render inside a completion branch (`isComplete`, a `masteryReport`, or a `result`), **verified individually in the audit**, so behaviour is unchanged today — but a sixth call site placed outside such a branch would show the prompt on page load and burn one of three lifetime impressions. The contract is now documented at the component.
- **Choosing review days now improves the due-concepts digest schedule without removing anyone's
  existing digest.** Learners with null or empty `review_days` retain every-day eligibility and the
  seven-day cooldown. Learners with chosen days remain eligible only on those weekdays and use a
  one-day cooldown, allowing a digest on each chosen day when concepts are due. This may spread future
  sends across the week, but it does **not** fix R1: non-committers keep the synchronized default and
  committers can receive more messages.
- **Mutation-verified, killing tests named — the two destructive changes this design makes available are both guarded.** Flipping `isEligibleReviewDay`'s empty case to `false` — the naive reading of *make the commitment mean something*, which would cut off **all 115 current digest recipients** — fails four tests, including a **pre-existing** one (`findDueConceptsDigestUsers_nullAndEmptyReviewDaysKeepExistingScheduleEligibility`) that was already protecting it, plus `sendDueConceptsDigestEmails_stillSendsToAnUncommittedLearner`. Making an impression stamp `review_commitment_prompted_at` — which would silently resolve **395 outstanding rows** and close the ask permanently for every one of them — fails `recordReviewCommitmentPrompted_isIdempotentAndKeepsTheCommitmentOutstanding`, and only that test.
- **⚠️⚠️ THE SCOPED COLD AGENT CONFIRMED ALL SIX NAMED CLAIMS AND FOUND TWO DEFECTS BEYOND THEM — both are fixed here, and the second is the more serious.** This is the tier the owner re-decided for item 2, and it earned its cost.
- **⚠️ THE PROMPT TOLD 64% OF THE USER BASE SOMETHING FALSE.** The copy asserted *"You already get a weekly nudge when concepts are due"* unconditionally, but the digest is gated on `dueConceptsDigestRemindersEnabled` **and** a verified email (`RetentionService:188`). **VERIFIED read-only: 252 of 396 accounts have that preference OFF; 255 would have seen the false line; 79 of those are otherwise prompt-eligible.** ⚠️ **And for them the whole ask is INERT — choosing days changes nothing, because no digest is sent either way.** `MeResponse` already carried the flag and the component never read it. **⚠️ OWNER DECISION, SAME DAY: DO NOT ASK WHEN THE DIGEST IS OFF.** The conditional copy was an interim fix and is gone; `dueConceptsDigestRemindersEnabled` is now part of **server** eligibility (`AuthService#isReviewCommitmentPromptEligible`), which is the same preference `RetentionService` gates the digest on. **That makes the prompt's claim true BY CONSTRUCTION rather than by wording** — the off-branch became unreachable and was deleted rather than left as dead code, because dead code is how a false claim quietly returns. The invariant is documented at both ends, with the instruction to change them together or not at all. **⚠️ THE REACH COST IS REAL AND MEASURED, NOT WAVED AWAY: the eligible pool drops from 136 to 57.** But near-term in-app reach moves by **one** learner (7 → 6), because the 79 excluded were largely not returning — and every one of them was being asked to configure something that could not affect what they receive. **All 57 who remain are email-verified**, so no further clause was needed. ⚠️ **Do not "restore reach" by dropping the clause, and do not make committing switch the preference on — that is an email-consent change and is explicitly out of scope.** Mutation-verified: dropping the clause fails `getMe_doesNotOfferTheCommitmentPromptWhenTheDigestIsOff` and `recordReviewCommitmentPrompted_doesNotCountAnImpressionWhenTheDigestIsOff`, and nothing else.
- **⚠️ The new endpoint's CLIENT request shape was untested, which is the `v0.119.0` defect class exactly.** All five frontend suites mock `@/lib/api` wholesale, so not one line of the real request executed — no method, no headers, no body — while the repo already had **sixteen** `lib/api-*.test.ts` files establishing the pattern. `lib/api-review-commitment.test.ts` now pins it. **⚠️ THE DEMONSTRATION IS THE POINT: dropping the `Content-Type` — which makes Spring reject the request before the controller is entered — fails the new test and is MISSED by all sixteen component tests, which pass.** That is `v0.119.0` reproduced on demand.
- **One claim was confirmed for the wrong stated reason, and the agent said so.** `POST /me/review-commitment/prompted` cannot inflate the count — but not because `findByIdForUpdate` prevents a race. Under OSIV the `UserEntity` is already managed before the lock is taken (the anti-pattern `UserRepository:66-75` documents against itself), so two concurrent impressions read the same pre-lock state and both compute the same `+1`. **It under-counts rather than over-counts**, which is safe for a cap, and is recorded so nobody later "fixes" it on a wrong mental model.
- **⚠️⚠️ THE PRE-SIGNOFF PRESSURE TEST WAS OWED, WAS NEARLY SKIPPED, AND FOUND A CROSS-PR DEFECT — the owner asked for it before signoff, correctly.** The per-item cold agent did NOT discharge it: that one was scoped to item 2's diff and never saw item 1's code. **Items 1 and 2 both rewrote `review-commitment-prompt.tsx`**, which is precisely the interaction class `CLAUDE.md` says a diff-scoped review structurally cannot see.
- **⚠️ THE FINDING: a dismissal could fire for a prompt nobody ever saw.** `globalThis.sessionStorage` **THROWS** — it does not return null — when site data is blocked (Chrome *block all cookies*, some embedded webviews, restricted iOS contexts), and **optional chaining guards a null storage object, not a throwing accessor.** Item 2 put that access **inside item 1's guarded block**, after `shownTrackedRef` had advanced to `"shown"` and `promptVisibleRef` to `true`, but **before `REVIEW_COMMITMENT_PROMPT_SHOWN` fires**. The outer `.catch` reset only `visible`. Net: a later pagehide/unmount emitted `REVIEW_COMMITMENT_DISMISSED` with **no matching `PROMPT_SHOWN`** — inflating the exact abandonment funnel item 1 was built to measure. ⚠️ **Neither PR's review could have caught it: one supplied the guard, the other supplied the throw.** ⚠️ **And the repo already knew** — `lib/guidance.ts:9-33` wraps every storage access in try/catch; this was the only frontend access that did not. Fixed with total accessors; **mutation-verified** — restoring the unguarded form fails `still reports the impression, and never a phantom dismissal, when storage throws`, and nothing else. **No existing test could have caught it: jsdom's `sessionStorage` never throws.**
- **⚠️ A user-facing claim went stale on a file that appears NOWHERE in this release's diff.** `settings/page.tsx:971` still read *"A weekly reminder when concepts are due"* — false for a committed learner, who can now receive up to **seven** a week — and `:917` described only the no-days case. **`docs/features/email-preferences.md`, updated BY THIS RELEASE, points learners at that exact surface.** This is the *sweep by SURFACE, not by diff* failure `CLAUDE.md` records as having cost three releases running; it has now cost a fourth. Both strings corrected, and `retention-emails.md` now carries the standing obligation so the next cadence change sweeps the surface.
- **⚠️ Three findings are RECORDED, NOT FIXED — deliberately, because shipping unreviewed behaviour at signoff is worse than a stated limitation.** **(a) No volume ceiling on the digest:** `resolveReengagementBudget` gates the inactivity dispatch only, so the per-learner ceiling moves 1/week → 7/week with nothing capping it. **Zero impact today (0 accounts have chosen days), so it is a forward exposure — and it compounds R1**, whose cause this release already attributed to this same producer. **(b) A below-the-fold impression burns one of three lifetime chances:** the prompt renders inside a `weakConceptsRef` block far down long result pages — challenge-quiz even has a button that *scrolls to it*, which is in-repo evidence it is off-screen. Before item 2 an unseen render cost nothing; now it permanently consumes an impression and starts a 14-day cooldown. **This corrupts the checkpoint's own denominator and is named in its clause.** **(c) `PROMPT_SHOWN` and `DISMISSED` can carry different `entityId`s** — the release's own test fixture demonstrates the mismatched pair. Analytics-only.
- **⚠️ One overstated claim corrected rather than defended.** A code comment said the prompt's copy is true *"by construction"*. It is not: the digest audience is preference **AND** verified email **AND** active status, while eligibility checks only the preference. Three accounts today have the preference on with no verified email. Now documented as true-in-practice, not proven.
- **⚠️ Item 3's guard is MANUAL-ONLY and the release says so rather than implying enforcement.** `scripts/check-deploys.test.sh` is referenced nowhere outside itself — **this repo has no `.github/workflows` at all** — so nothing runs it automatically. The `/signoff` change added a wait warning, not a trigger.
- **The reach claim stays bounded by the production read.** This redesign compounds for future
  learners by giving them more than one chance to answer. It does **not** recover the 128 already
  stranded learners who no longer open the app; reaching them requires email work still blocked by R1.
- **Item 3 — `scripts/check-deploys.sh` reports a confirmed drift AS drift.** The precedence is now explicit and commented: **drift > unknown > ok**. Both platforms' "cannot check" paths set a flag instead of exiting early, so neither short-circuits the other — a confirmed Vercel drift survives a missing `RENDER_API_KEY`, and a confirmed **Render** drift now survives an unreadable Vercel, which the old order could not even reach.
- **The guard asserts the EXIT CODE, because the message was never the bug.** `scripts/check-deploys.test.sh` establishes a shell-test convention this repo did not have (no `.test.sh`, no CI workflow existed). It stubs only `gh`, `curl` and `git` on `PATH` — **`jq` stays real, because the script's `jq` filters are part of what is under test** — and covers nine exit-code cases.
- **Mutation-verified against the pre-fix script, with the killing cases named.** Restoring the original makes exactly two cases fail: *"Vercel BEHIND + no `RENDER_API_KEY` → DRIFT"* (`exit=2 want=1`) and *"Render BEHIND + Vercel API failure → DRIFT"*. The other seven pass under both, correctly — they were never affected. ⚠️ **The failing output reproduces the real symptom verbatim**: it prints `VERCEL … BEHIND` and still exits 2. **A test asserting the message passes under the defect; that is why the guard asserts the code.**
- **⚠️⚠️ THE MOTIVATING EXAMPLE WAS WRONG, AND THE CORRECTION IS THE MOST USEFUL THING IN THIS ITEM.** This session claimed Vercel had **missed** `v0.138.0` and had missed *"2 of the last 3 release merges"*. **Both false.** Vercel created the deployment at **01:21:06Z against a 01:16:42Z merge — 4m24s** — and Render went live at 01:18:59Z. **An in-flight deploy was read as an absence**, ~4 minutes after merge, and a release section was scoped around it before it was caught. **The true record is ONE confirmed miss (`v0.136.0`).**
- **⚠️ That broke `v0.137.0`'s own rule the day after it was written** — *a claim about production state is a snapshot, not a fact.* An instantaneous reading was asserted as a standing property. **Recorded rather than quietly fixed, because this is the second consecutive release in which a claim reached a tracker before it was re-read.**
- **⚠️ The generalizable lesson went into the code, not just the write-up: a test for ABSENCE must wait past the thing's normal latency or it manufactures its own false positive.** Observed auto-deploy latency is **~2–5 minutes** on both platforms. The script cannot know when you merged, so it cannot enforce the wait — **`/signoff` now states it, and the script's header explains why.**
- **⚠️ WHAT THE 9 PASSING GUARDS DO AND DO NOT COVER, because "9 passed" invites over-reading.** Every case is **stubbed** — `gh`, `curl` and `git` are faked on `PATH`. **The drift path is verified by MUTATION under stubs and has NOT been observed against a live drift**: the one live run (`RENDER_API_KEY` absent, 2026-09-10) exercised the *unknown* path, because Vercel matched `main` by then. **No drift was manufactured in production to test it, deliberately.**
- **⚠️ The defect itself was never contingent on the false reading.** It was reproduced by mutation under stubbed conditions, so item 3 stands exactly as scoped — but without the correction the release would have described a platform problem that does not exist.

## v0.138.0 - Stated and Enforced

**Status: Released** (kicked off 2026-09-09, signed off 2026-09-10, base branch `releases/v0.138.0`, cut from `main` after `v0.137.0` merged as #1360 and tagged)

Sources: `docs/claude-findings/2026-09-09-regeneration-invalid-title-failures.md` (the diagnosis) and `docs/claude-plans/2026-09-09-generated-title-bound-and-failure-observability-plan.md` (the fix plan). **Read the findings first — Leg A looks like a nice-to-have until you have seen its §4.**

Theme: make what is *enforced* match what is *stated* — in the prompt contract, and in the backlog.

### ⚠️⚠️ A LIVE PRODUCTION DEFECT, DETERMINISTIC, WITH FOUR NOTES STUCK `FAILED` RIGHT NOW

Owner-reported 2026-09-09. Seven `LLM_INVALID_OUTPUT` failures across four notes, all `BOARD_EXAM_REVIEW`. **⚠️ Batch `6a7a8fa9` is a RETRY of the three that failed in `f5c690f5`, and all three failed identically — so this is DETERMINISTIC and retrying is not a workaround.**

**The probable cause is a bound the model is never told.** The title is validated at **12 WORDS** (`MAX_GENERATED_NOTE_TITLE_WORDS`, `OpenAiLlmStudyPackService:75`), while the published contract carries **no numeric bound at all** — schema `maxLength: 160` **characters**, plus prose "concise and specific". The same prompt file already templates `{MAX_ITEM_CHARS}` (3×) and `{MAX_WORDS}`. **⚠️ AND THE CODEBASE DOCUMENTS THIS EXACT ANTI-PATTERN AGAINST ITSELF:** `OpenAiLlmStudyPackService:2553-2561` records removing an unpublished word ceiling from *bullets* and instructs *"do not reintroduce a bound the model cannot see."* **The title kept its.**

**⚠️⚠️ BUT THE FAILING BRANCH IS INFERRED, NOT CONFIRMED — AND THAT IS WHY LEG A COMES FIRST.** `normalizeGeneratedNoteText` throws on *either* a blank value or a word count outside `1..12`, and **the log records neither the rejected title, its word count, nor which bound failed.** Four notes failed seven times and produced zero evidence of what was wrong. **⚠️ The source-title length correlation is NOT discriminating and must not be cited as support** — the FAILED titles average 8.5 words, well inside the cap.

### Planned Scope

1. **Leg A — log what was actually rejected.** When `normalizeGeneratedNoteText` rejects: the field, which bound failed (`blank` vs `wordCount`), the measured count, the limits, and **the offending value TRUNCATED** (owner decision, 2026-09-09). **⚠️ Independently shippable and ships even if Leg B slips** — without it the next occurrence is equally unresolvable. **This is the `v0.87.0` lesson one layer down:** that release added `failed_topic_reasons` because "which topic failed" without "why" had already cost two investigations.
2. **Leg B1 — publish the bound** (owner decision, 2026-09-09). Template a `{MAX_TITLE_WORDS}` line into `note-generation-developer.txt`, exactly as `{MAX_ITEM_CHARS}` and `{MAX_WORDS}` already are. Smallest change, output shape unchanged, contract made honest.
3. **Backlog Index verification pass over the 16 scope-eligible rows** — every row claiming OPEN or NOT SHIPPED, checked against real code or a read-only query, stale ones corrected.
4. **Extend the `v0.137.0` rule to cover SHIPPED-CODE claims, not just production state**, and index the two artifacts above.

### ⚠️ Why item 3 exists: three stale rows surfaced by accident in ONE day

`v0.137.0` added the rule that a claim about *production state* is a snapshot. **That rule does not reach a claim about shipped CODE, and the third failure was exactly that.**

| Claim | Reality |
|---|---|
| `V141`/`V142` never run | Ran 2026-09-08 |
| Profile-string write pending | Already applied |
| **Study Plan Builder reorder NOT SHIPPED** | **Shipped in `v0.96.0` (`185e0cc7`, 2026-08-29) — 41 releases ago** |

**⚠️ THE THIRD ONE WAS OFFERED TO THE OWNER AS A RELEASE CANDIDATE AT THE `v0.137.0` KICKOFF AND IS RECORDED IN THAT RELEASE'S NARRATIVE AS AN OPTION THEY CHOSE AGAINST.** Had it been picked, a Codex prompt would have been written to build something that already exists — the `Save order` button is at `study-plan-builder-page-client.tsx:2513`, and two of that row's three "verified traps" were also already addressed. **A stale Backlog row costs a whole release, not a paragraph**, because the Index is the input to every kickoff's scope decision.

### ⚠️ Anti-drift

- ❌ **Do NOT do B1 and B2 together.** Two bounds on one field is how this defect was created. **B1 is authoritative; B2 and B3 are not in scope.**
- ❌ **Do NOT increase `MAX_INVALID_OUTPUT_ATTEMPTS`.** The failure is deterministic — the retry already ran and failed identically. More retries buy nothing and cost an LLM call each.
- ❌ **Do NOT skip or soften title validation on the regeneration path only.** The generated title becomes the note body's **first line**; a divergent rule between first generation and regeneration is a new defect.
- ❌ **Do NOT rename or retitle the four failed notes to work around it.** `v0.120.0` established the typed title as canonical. Their titles are correct; the validator rejected the model's *output*.
- ❌ **Do NOT bundle the `OfficialChallengeQuizTemplateService` seed failures** (findings §7) — separate symptom, unproven relation, and folding it moves the verification tier.
- ❌ **Do NOT touch the `subject value='Education' … overly broad ai suggestion ignored` path** — it appeared 6× in the window and is a working guard reporting normal operation.
- ⚠️ **Nothing here may make `generateStudyPackFromExistingNoteAsync` public or route it through the `@Transactional` proxy** — both LLM calls run with no transaction and no JDBC connection held (`v0.112.0`), and that must survive.
- ❌ **NO `frontend/app/onboarding` work before the `2026-09-11` read** (62.4% baseline, cannot be re-run). **NO Learning Connections work** — `[CHECKPOINT — due 2026-09-19]`, denominator ONE.
- ⚠️ **The four failed notes are NOT repaired by this release.** They stay `FAILED` until re-run, and **that re-run is the OWNER's** — it is also the only real end-to-end confirmation, since the failing branch could not be confirmed from logs.

### ⚠️ Pre-declared guards — written so a fixture cannot pass under both the defect and the fix

- **Leg A:** assert the emitted payload **names the bound and the measured count**. ⚠️ A test asserting only that generation fails **passes under both the defect and the fix** — it already fails today.
- **Leg B1:** assert the **RENDERED** prompt contains the numeric bound. ⚠️ Not the template file — the placeholder is substituted at build time, and a test that greps the raw template passes even if substitution is broken.
- **Regression, all legs:** the four real titles from findings §1 must round-trip. ⚠️ **Use those exact strings, not invented ones** — they are the only known-failing inputs, and an invented "long title" fixture is a guess about a failure mode the logs never confirmed.
- ⚠️ **Reach the validator the way production does** — through the real response-parsing path. A fixture that hand-builds a `PromptGeneratedNote` skips `repairJsonEatenLatexCommands` and the whitespace normaliser, **either of which could be the branch that actually fired.**

### ⚠️ Size, and what the fold does to the verification tier

**The owner took both the title fix and the backlog pass in one release, after being told the repo's rule that release size is the biggest lever on verification cost. Recorded rather than smoothed over.** **The honest consequence here is mild, and only because the second half changes NO code:** items 3–4 are docs, so the code diff stays at two files with no migration and no endpoint. **Tier: one `advisor()` call on the code diff**, per the plan's §7, plus the rendered-prompt guard. **⚠️ It would NOT stay there if Leg B2 were taken** — that changes what is accepted into a note body on a shipped generation path and would need a scoped cold agent. B2 is explicitly out of scope.

**⚠️ The transport lesson does not apply: no new endpoint is added, so nothing here owes a real-request `MockMvc` test.**

### Routing

**CLAUDE CODE inline** for Legs A and B1 — two files, no migration, no endpoint, per the plan's §7. Items 3–4 are docs and are Claude Code by definition.

### Scope completeness — each planned item against the code that implements it

| # | Planned | Verdict | Evidence |
|---|---|---|---|
| 1 | **Leg A** — log the field, failing bound, measured count, limits, value truncated | **SHIPPED** | `OpenAiLlmStudyPackService:2592`/`:2597` (both branches), emitter at `:2603-2612` |
| 2 | **Leg B1** — publish `{MAX_TITLE_WORDS}` into the prompt | **SHIPPED** | `note-generation-developer.txt:31`, substituted at `OpenAiLlmStudyPackService:727`; bound unchanged at `:87` |
| 3 | Verify **the 16 scope-eligible** Backlog rows | **⚠️ CHANGED — the stated number could not be reproduced** | No filter was ever written down at kickoff. A defensible filter yields **26 raw / 23 after 3 stated over-catches**; 7 read against code, 4 stale. **Deliberately not reverse-engineered to return 16** |
| 4 | Extend the `v0.137.0` rule to SHIPPED-CODE claims, and index the two artifacts | **SHIPPED** | Six-step procedure in `CLAUDE.md`; both source artifacts carry Backlog rows (3 references each) |

**⚠️ Item 3's verdict is recorded as CHANGED rather than SHIPPED on purpose.** The release was scoped on a number, the number turned out to be unverifiable, and a pass that quietly delivered "16 rows" would have reproduced the exact failure the release exists to fix — a stated figure nobody can re-derive. **The count found is reported instead of the count asserted.**

### Shipped

- **Leg A — every generated-note text rejection now names what failed.** `normalizeGeneratedNoteText` threw a bare `LLM_INVALID_OUTPUT` on either branch; it now emits `generated_note_text_rejected field=… bound=blank|wordCount words=… min=… max=… chars=… value="…"` before throwing, and the two branches are separated so the bound is reported rather than inferred. Applies to `title`, `overview` and `keyIdea` alike. **On the blank branch the RAW value is logged, not the normalized one** — what is diagnostic there is what arrived and collapsed to nothing, which a `null` cannot show.
- **Leg B1 — the title bound is published.** `note-generation-developer.txt` now states *"keep the title at or under `{MAX_TITLE_WORDS}` words"*, templated beside the existing `{MAX_ITEM_CHARS}` and `{MAX_WORDS}`. **The bound was NOT raised** — 12 words is unchanged; the model is simply told the number it is judged against.
- **Reused the class's existing `truncateForLog` rather than adding a second truncation rule.** The first attempt defined a duplicate helper, which failed the build; the existing one already backs thirteen other truncated-value logs and caps at `MAX_LOG_VALUE_LENGTH = 80`. ⚠️ **That does not mean the rejected value always survives intact, and the note should not be read as claiming it does:** the four production titles are 48–71 characters and fit, but a title rejected *for exceeding* the word bound is by construction longer than those, and the 15-word regression fixture (~105 chars) is truncated in the log. That is accepted deliberately — **the diagnostic payload is the field name, the failing bound and the measured count, all of which are logged unconditionally and in full**; the value is context, not the evidence.
- **Corrected the `:68` comment that predicted this defect.** It said all three word bounds were unpublished and "surviving deliberately", ending with a standing instruction: *"if one starts rejecting valid content, publish the bound in the prompt rather than raising it."* **That prediction came true and the instruction was followed.** The comment now records which bound was published and why the other two deliberately were not — the instruction is evidence-gated (*"if ONE starts rejecting valid content"*), and neither `overview` nor `keyIdea` has produced a single observed rejection.
- **Corrected `docs/features/study-pack-generation.md`, which claimed the two title-rule blocks are "byte-identical today".** That stopped being true with Leg B1. The divergence is deliberate and one line: only the note path enforces a title word bound, so publishing one in `developer.txt` would state a rule nothing enforces. The doc now says so and warns against "restoring" byte-equality.
- **Five guards, each mutation-verified with the killing test named.** Deleting the published bound from the real prompt kills `noteGenerationPromptResourceDeclaresTheTitleWordPlaceholder`; breaking the substitution kills `noteGenerationPromptStatesTheTitleWordBound`; silencing the rejection log kills `rejectedGeneratedTitleLogsWhichBoundFailedAndTheMeasuredCount`. The fourth, `theFourTitlesThatFailedInProductionRoundTripThroughTheRealParsingPath`, uses the four real production titles rather than invented ones and drives the real response-parsing path.
- **A fifth guard, added by `advisor()` on the diff, closes a claim that was asserted rather than tested.** Leg A logs the field name for all three bounds, and the release justifies leaving `overview` and `keyIdea` unpublished on the grounds that *a first rejection would announce itself*. **No test exercised either of them** — the discriminating guard covered `title` only, so that justification rested on two of three fields being untested. `rejectedGeneratedOverviewIsReportedUnderItsOwnFieldName` asserts `field=overview`, `words=93`, `max=90`; mutating the overview call site to log under the title's field name kills it, and kills nothing else. ⚠️ **Its first draft used a 90-word overview — exactly the bound — and did not throw, because the check is `> maxWords`.** A boundary fixture sitting ON the limit proves nothing about either side of it; the fixture is now 93 words and the comment says why.
- **Derived the asserted word count instead of fitting it to observed output.** The title fixture's `words=15` was reached by writing 17, watching it fail, and changing the number — the shape of an assertion tuned to whatever the code emits. The count is 15 because the fixture carries no LaTeX and no irregular whitespace, so `repairJsonEatenLatexCommands` and the whitespace collapse are the identity on it. That reasoning is now a comment on the fixture, so a future change to either normalizer fails the test loudly rather than quietly shifting the count.
- Backend suite: **2350 tests, 0 failures, 0 errors, 0 skipped**, PostgreSQL container included — up exactly five from the 2345 baseline, which is the five guards above and nothing else.
- **Item 3 — the Backlog Index was checked against code for the first time, and the release's own stated scope did not survive it.** The scope said *"the 16 scope-eligible rows"*. **That number was asserted at kickoff with no filter written down and could not be reproduced** — a defensible filter (live rows whose Status asserts something about **code state**, excluding date-gated checkpoints and already-struck rows) yields **26 raw, 23 after removing 3 over-catches** — rows the filter hit on the word *OPEN* inside their own strikethrough. **The over-catches are stated rather than quietly trimmed**, for the same reason the pass refused to reverse-engineer 16. ⚠️ **The filter was NOT reverse-engineered to return 16**, because fitting a filter to an expected number is the same error `advisor()` caught in this release's own `words=15` assertion hours earlier. 26 classified, **7 read against code** (the rest were classified as out-of-scope or already-corrected without opening a file).
- **Four rows were stale, and all four asserted the absence of code that exists.** (1) *"Unconfirmed connection requests never expire — no `expires_at` and no sweep, **verified**"* — **false on all three clauses**; `LinkedLearnerRelationshipEntity:56-59` carries `expiredAt` and `expiresAt`, `LinkedLearnerRequestExpiryJob:24` is the sweep, shipped `4b701532` 2026-08-30. (2) Domain Context curator copy read **NOT SHIPPED**; landed `607e12e5` 2026-08-31. (3) `adoptGoal` NULLing a learner's exam date read **STILL OPEN**; fixed in `v0.127.0` (`f7269474`) **two days before this release opened** — `NoteCollectionService.java:864-883` promotes the date instead. (4) *"notes strand in `GENERATING`"* — **mechanism true, headline false**: `AppConfig.java:102-110` still sets no drain, but `GenerationRecoveryJob:18` has swept `GENERATING` every ten minutes since 2026-08-18, so the strand is bounded at one interval.
- **⚠️ Two of the four were free to catch — the row's own cells contradicted each other.** One had `Gate` = *"✅ SHIPPED in `v0.97.0`"* sitting beside `Status` = *"NOT SHIPPED"*; the other a **struck-through title reading "DISCHARGED … verified shipped"** beside a live `Status` of NOT SHIPPED. **No code read was needed for either.** `CLAUDE.md` already named this detector after `v0.133.0`; this is its first deliberate application, and it out-yielded every grep in the pass.
- **⚠️ The word "verified" in a row turned out to be worth nothing.** The connection-request row stamped its claim as verified and was wrong on every clause. Every row that survived this pass now carries a **`file:line`** in its Status cell instead — a row with no anchor has been *read*, not verified.
- **⚠️ A structural defect was found that defeats kickoff step 8 itself: 19 rows carried FOUR columns instead of five**, so the ritual's `Last reviewed` bump could never have reached them, and nothing announced it. Eighteen now carry an explicit **`⚠️ never stamped`** (the nineteenth was verified in this pass and carries a real date) — deliberately **not** a back-dated guess, since a stamp advancing without the claim being re-read is worse than an old date (`v0.93.0`'s own scan note said exactly this). **When a check iterates a structure, verify the structure before trusting the iteration.**
- **⚠️ One Gate was found already true and unactioned.** The public-catalog unbounded read (a real production outage fix) un-parks *"the moment `v0.119.0` is signed off"* — **19 releases ago** — and the legacy branch is still live at `NoteService.java:774`/`:872`. **This is the other half of step 8**: it checks whether a Gate became true, and a Gate that quietly came true is as invisible as an unindexed file. Left open and anchored rather than folded in — it is a backend fix with a product decision attached, and folding it would move this release's tier.
- **Seven rows marked out of scope for a code check with the reason written into the row** — production reads, curator work, an unreproduced variance — so the next pass does not re-litigate them. **And the nine this pass did not treat are named in the scan note with a reason each**, because a completeness pass that silently skips a third of its own set reads as complete to the next scan. None is an unverified code claim: each is already closed, already gated on a read this release must not pre-empt, or is this release's own subject.
- **⚠️ One of the four corrections was itself wrong on first writing, was caught by `advisor()` before commit, and the error is kept on the record.** The `GENERATING` row's replacement text said notes strand *"at most one sweep interval"* — **mistaking the recovery job's 10-minute cron cadence for the strand bound**, which is actually `noteBoundMinutes` (default **120**, `application.yaml:535`) plus a cadence, understating it roughly twelve-fold. It also passed over a branch already visible on screen: rows with a null `generation_enqueued_at` are counted, logged *"leaving them untouched"*, and **strand indefinitely** — so the original headline is exactly right for that class. ⚠️ **A correction is a claim like any other and decays the same way.** `CLAUDE.md` step 2 now carries this as its worked example, including the near-miss: when a job bounds something the bound is a **configured property** — read the value — and always check what the sweep **refuses** to touch.
- **Item 4 — the rule is the procedure this pass actually ran, not a rule written beside it.** `v0.137.0`'s snapshot rule reached `flyway_schema_history`, row counts and deploy state; it did **not** reach *"X is NOT SHIPPED"* or *"nothing does Y"*, which rot just as fast because the fix ships in a release that never re-reads the row claiming it is missing. The extension carries six numbered steps — classify by claim type before reading, **verify the title's claim and not only the Status cell's** (the `GENERATING` row diverged exactly there), anchor every survivor to `file:line`, read the row's cells against each other first, never advance `Last reviewed` on a row you did not re-read, and check the table's structure before trusting an iteration over it — plus the standing instruction to **report the count you actually found rather than the one that was asserted**.

## v0.137.0 - Deploy Integrity

**Status: Released** (kicked off 2026-09-09, signed off 2026-09-09, base branch `releases/v0.137.0`, cut from `main` after `v0.136.0` merged as #1359. **⚠️ RE-SCOPED the same day — see below.** No feature or fix PRs: every commit is release-management or docs, plus one script.)

Theme: the deploy pipeline failed silently and the record of what was deployed was wrong. Make both true again.

### ⚠️⚠️ THIS RELEASE WAS SCOPED ON A FALSE PREMISE AND RE-SCOPED WITHIN THE HOUR — THE ORIGINAL IS RECORDED, NOT OVERWRITTEN

It opened as **Deploy and Read**, justified by *"`v0.132.0` through `v0.136.0` are merged and none is deployed"*, taken from `ROADMAP.md`. **The owner chose that scope over two feature candidates on the strength of it. Read-only production queries then disproved it:**

| Claim as written | Reality, read 2026-09-09 |
|---|---|
| `V141` has never been run | **RAN 2026-09-08T12:23:16Z**, `success = true`, on `v0.132.0`'s own auto-deploy |
| `V142` has never been run | **RAN 2026-09-08T15:47:36Z**, on `v0.133.0`'s |
| The profile-string write is still pending | **ALREADY APPLIED** — the read returns `0 old / 1 new` |
| Five releases are undeployed | **The BACKEND deployed automatically every time.** Only `v0.136.0`'s FRONTEND is missing |

`notifications` and `announcements` really are at zero rows, so the notification checkpoints genuinely remain unreadable. That half survived; the rest did not.

**⚠️ THE CHEAPEST DETECTOR EXISTED AND NOBODY USED IT: the `v0.133.0` row said `V142` HAS NOT BEEN RUN in its Gate cell while its own Status cell said the read was *"READ AND CLOSED 2026-09-09, one day after the deploy, exactly on its date"* — which is only possible if it HAD deployed.** The Status was updated when the read closed; the Gate was not. **A row that contradicts itself is free to detect and nobody read the two cells against each other.**

### ⚠️ The live defect this investigation actually found

**`v0.136.0` did not auto-deploy on EITHER platform, and the resulting version skew broke Your Impact in production.**

Render's config is correct and untouched (`autoDeploy: yes`, `autoDeployTrigger: commit`, `branch: main`) and every prior release fired in **2–3 seconds** with `trigger: new_commit`. For `4ee2c752` it never fired; the owner deployed manually 5.5 minutes later (`trigger: manual`). Vercel matches exactly — GitHub records a `vercel[bot]` Production deployment for every prior release merge and **ZERO** for this one. There are no repo-level webhooks and no CI workflows, so both platforms are GitHub App integrations consuming **the same push event**, and neither received it. GitHub declared no incident that day.

**The consequence, verified against the live commit rather than assumed:** Vercel's newest Production deployment is still `7f371e65` (`v0.135.0`) while Render runs `v0.136.0`. `v0.135.0`'s `api.ts:5990` calls `/creator-impact/me` with **no** parameters, from `public-profile-page-client.tsx:225` and `settings/page.tsx:281` — and `v0.136.0` made `impacted` **required**, so both calls now 400. The owner sees *"Could not load your impact."* with a Retry that can never succeed, and the Knowledge Impact digest toggle hidden in Settings. Both degrade rather than crash, which was deliberate, but the feature is dead until Vercel ships.

**⚠️ AND THE CHANGE WAS BREAKING IN BOTH DIRECTIONS — frontend-first would have 404'd on `/creator-impact/me/summary`. `v0.136.0` recorded no deploy-ordering requirement at all.**

### Planned Scope

1. **Restore the frontend deploy — OWNER ACTION.** Deploy `4ee2c752` (or later) on Vercel. Until then the skew above is live.
2. **A detector for a merge that produced no deploy.** Nothing in the repo or the process noticed; it was found only because the owner happened to look. The cheapest form is a signoff-time check that the deployed commit matches `main` on both platforms — decide detector shape before building anything.
3. **Two standing rules, written where they will be read.** (a) A release that removes, renames or makes-required an existing API form is **breaking in both directions** and must state its deploy ordering or that both sides ship together. (b) **A `ROADMAP.md` claim about PRODUCTION STATE is a snapshot, not a fact** — re-read it read-only before repeating it, and treat a row whose Gate and Status cells disagree as a defect in itself.
4. **The reads that are now live**, which the false premise had wrongly deferred: `2026-09-10` (`v0.72.0` proximal retention, `v0.134.0` `deploy + 1 day`) and `2026-09-11` (onboarding funnel, `v0.62.0` Knowledge Impact).

### ⚠️ Anti-drift

- ❌ **NO `frontend/app/onboarding` work before the `2026-09-11` read.** It measures signup-funnel completion against a **62.4%** baseline (234/375) that cannot be re-run.
- ❌ **NO Learning Connections work** — `[CHECKPOINT — due 2026-09-19]`, denominator ONE. Stage D2's connection-request notification is exactly the nudge that converts `PENDING` into `ACCEPTED`, the metric that checkpoint kills the arc on.
- ❌ **Do NOT change any analytics event, fire site or gating condition a live checkpoint reads.** `v0.136.0` moved and re-gated `KNOWLEDGE_IMPACT_DASHBOARD_VIEWED` two days before the `2026-09-11` read that depends on it; a second such change would corrupt another read.
- ❌ **Do NOT add a second entry point to `/impact`** before its own `[CHECKPOINT — due deploy + 30 days]` reads — adding one destroys the measurement.
- ❌ **Do NOT add CI infrastructure as a reflex.** The repo has **no** `.github/workflows/`, and item 2 is a detection problem, not automatically a CI problem. State the shape before building.
- ⚠️ **A quiet read is NOT a pass.** On rows with a denominator clause (`notifications` at zero, one curator, zero digest opt-ins), `no data` means **NOT YET MEASURABLE → RE-DATE**.
- ⚠️ **⚠️ THE PRODUCTION DATABASE IS READ-ONLY FOR CLAUDE, ALWAYS** — every diagnostic in this section came from `SELECT`s. The deploy is the owner's.

### ⚠️ What is actually deployed, as of 2026-09-09

- **Backend (Render):** `4ee2c752` = `v0.136.0`, live, deployed manually 12:04:31Z.
- **Frontend (Vercel):** `7f371e65` = `v0.135.0`. **One release behind, and that is the live defect.**
- **Database:** migrations current at **`V143`**, matching the repo's highest. Nothing pending.

### Scope completeness

| Planned item | Outcome |
|---|---|
| 1. Restore the frontend deploy | **✅ SHIPPED (owner).** Vercel Production now carries `4ee2c752` at 2026-09-09T14:00:53Z, `state=success`; Render and `main` agree. Skew resolved, verified read-only rather than assumed. |
| 2. Detector for a merge that produced no deploy | **✅ SHIPPED.** `scripts/check-deploys.sh`, wired into `/signoff`. Shape was decided before building, per the anti-drift, and CI was deliberately not introduced. |
| 3. Two standing rules | **✅ SHIPPED** in `CLAUDE.md` — the production-state-is-a-snapshot rule and the API-form-is-breaking-both-ways rule. |
| 4. The `2026-09-10` and `2026-09-11` reads | **❌ NOT SHIPPED — the dates had not arrived, and reading early is the failure those dates exist to prevent.** Nothing about them is blocked by this release; they are unchanged in the Backlog Index and land in the next cycle. Recorded here so a reader does not mistake the release's own scope list for work that silently vanished. |

### Known limitations

- **The Render half of `check-deploys.sh` has not been run against the live Render API.** No `RENDER_API_KEY` was available in the session that wrote it, so the request, auth and error paths are unexercised; only the response *parsing* is covered, by fixtures. **The first real run is the test** — if it fails, the fault is in the request/auth path, not the comparison. The Vercel half ran end-to-end against the live API and is confirmed working.
- **The detector is manual.** It runs when `/signoff` says to run it, so a release that never reaches signoff is not covered, and neither is a deploy that goes missing between releases. Making it automatic requires a `schedule:`-triggered workflow — **not `on: push`, which cannot observe the absence of the event that triggers it** — and that was deliberately deferred rather than introduce CI to a repo with no workflows.

### Routing

**CLAUDE CODE** for the doc corrections, the rules and the reads. Item 2 is re-routed once its shape is decided.

### Shipped

- Added `scripts/check-deploys.sh`, a detector for a merge that produced no deploy, and wired it into `/signoff` after the release PR merges. It compares `origin/main` against Vercel's newest successful Production deployment (read through GitHub's deployments API, so it needs no new secret) and Render's newest live deploy. **It tests for ABSENCE rather than failure**, because Render's `notifyOnFail` had nothing to fire on — nothing failed, nothing was queued — and **it exits 2 rather than 0 when it cannot check**, since "I could not look" reported as "all clear" is the same failure class it exists to catch.
- **Verified the detector against the defect itself rather than asserting it:** replayed today's 12:04 state (`main` at `4ee2c752`, Vercel newest `7f371e65`) through the comparison and confirmed it reports drift and exits 1. The Render response parser is covered by fixtures for the wrapped and unwrapped API shapes, a no-live-deploy list and an empty list.
- Added two standing rules to `CLAUDE.md`: a claim about production state is a snapshot that must be re-read before it is repeated (with internal Gate/Status contradiction named as the free detector), and removing/renaming/making-required an API form is breaking in both directions and owes a deploy-ordering statement.
- Corrected four false production-state claims in `ROADMAP.md` — the `V141`/`V142` gate cells and the profile-string write — each against the read-only query that disproves it, and recorded the self-contradicting row as the detector that was available and unused.
