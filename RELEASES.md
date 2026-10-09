# RELEASES.md - NoteLib

## v0.168.0 - Free Quota, Fairly Metered

**Status: Released** (signed off 2026-10-09; commits `0ce371a3`/`ad6caa8c` on `releases/v0.168.0`)

Theme: fix a confirmed production defect where Free-tier Study Pack quota counted note copies and
shared-pack remixes the same as paid LLM generations, incorrectly blocking real generation capacity
for 76 real users.

### Background

Found by a same-day, four-correction-pass product health audit
(`docs/claude-findings/2026-10-08-product-health-funnel-audit.md`, indexed at this kickoff), confirmed
independently by a concurrent session ("Feature Planner") working the same evidence, and root-caused
jointly: `StudyPackUsageService.resolveUsage` takes `max(trackedUsage.studyPackGenerations(),
persistedStudyPackCount)`, where `persistedStudyPackCount` is a raw `COUNT(*)` of `study_packs` rows
by owner and period — with no distinction between a real LLM generation and a copy/remix.
`copySourceStudyPack` (`NoteService.java`, shipped `v0.52.0`-era per the audit, confirmed 2026-06-04)
and `remixSharedStudyPack` (`ShareService.java`) both insert a `study_packs` row with no LLM call and
never call `incrementStudyPackGeneration` — correctly, since neither is a generation. The floor's raw
count doesn't make that distinction, so a user who copies/adopts heavily can hit `remaining = 0` on
their real generation quota having generated nothing. **75 of the 76 affected users hit this via Study
Plan adoption in the prior 30 days** — the single behavior the same audit's §6 found most predictive of
retention, meaning the bug actively penalizes the product's best-performing behavior.

**Considered and explicitly excluded from this release**, per the owner's own request to scope
narrowly after a wider "fully implement Study Journey" option was evaluated and found not ready:
- **CE dashboard goal-card framing** — re-checked 2026-10-09 and found **already resolved** as a side
  effect of `v0.167.0` (Civil Engineering is now a valid `exam_goal_slug`, so `buildGoalNudge` already
  resolves `GOAL_TYPE_EXAM` for it); zero production users currently have this goal set either way.
  Nothing to ship. Not to be re-proposed without new evidence.
- **Exam Hub page for Civil Engineering** — Product UX explicitly deferred this pending adoption
  evidence; unchanged since that decision.
- **Multi-goal support** — re-verified live 2026-10-09: still exactly 2 production users show the
  pattern (checkpoint due 2026-12-07, not yet due; see `ROADMAP.md`'s Backlog Index).
- **Degree Journey catalog (Phase C of `docs/claude-plans/degree-study-journeys-stage1-architecture-audit.md`)**
  — architecturally fully approved with zero open decisions (§22), but its own stated dependency
  ("at least two published Years") is unmet: exactly one BS Computer Science Year root exists today,
  and it is unpublished. Content-authoring gate, not a code gate.
- **Public Note → Study Journey discovery** — the original discovery brief's §14 keeps
  "recommendations"/"Explore" changes explicitly forbidden without their own fresh decision.

### Fix

`StudyPackUsageService.resolveUsage` drops the `persistedStudyPackCount` floor and the `max()`
entirely, trusting `trackedUsage.studyPackGenerations()` alone. Verified safe by enumerating every
`StudyPackEntity` construction site in the backend (exactly three: the real generation path in
`StudyPackService.saveStudyPack`, which always increments except for the existing, intentional
`ADMIN`-bypass via `enforceLimits`; and the two copy/remix paths above, which never should). No
evidence found that the floor has ever caught a genuine undercount — its only observable effect was
inflating usage for copies, remixes, and (incidentally) failing to respect the existing ADMIN
quota-exemption either, since a raw count has no exemption logic.

**Spend decision (owner, 2026-10-09):** restoring quota to the 76 blocked users is bounded by real
production token averages at ~$2.08/month at full utilization by every affected user (derived from
`study_packs`' own 90-day average input/output tokens on the FREE tier — 3,077 input + 943 output
tokens/generation, `gpt-4.1-mini` pricing) — not the audit's own abstract "2.8M tokens" framing, which
had no dollar figure attached. **Owner decided: ship uncapped, no spend ceiling** — the exposure is
immaterial at this scale.

**Known limitation, explicitly not fixed here:** `study_packs.estimated_cost` is NULL on 100% of
production rows (FREE and PREMIUM alike) — confirmed while deriving the number above. The column
exists but whatever is meant to populate it (`GeneratedStudyPackContent.estimatedCost()`) does not.
Not bundled into this release — unsized, and this release is deliberately kept to one fix.

Anti-drift: no change to quota VALUES, no change to plan tiers, no change to any other usage-metered
feature (Challenge Quiz, Adaptive Practice, Long Exam). Additive-safe: `UsageSnapshot`'s shape is
unchanged, only its internal computation.

**Delivery:** isolated bug fix, 1 file + its test — Claude Code implements directly per `CLAUDE.md`'s
task-routing table, no Codex prompt.

**Verification tier:** one scoped cold falsification agent at signoff (money/quota semantics changed,
per `CLAUDE.md`'s gate) — run via Codex (`codex exec`, `workspace-write` sandbox) against an isolated
local clone, per owner request, rather than the Agent tool used for `v0.167.0`'s equivalent pass.

**Falsification pass results (2026-10-09, Codex, 8 claims checked against live code in an isolated
clone, no credentials available or needed):** 7 of 8 confirmed — the method no longer queries
`study_packs` at all, the real generation path's `incrementStudyPackGeneration` call is unconditional
except the pre-existing `ADMIN` bypass, exactly 3 `StudyPackEntity` construction sites exist and only
one increments, the actual enforcement path (`assertMonthlyStudyPackQuotaAvailable`, which throws
`MONTHLY_STUDY_PACK_LIMIT_REACHED`) calls the fixed method directly — not just a display surface —
no other caller was missed, the 3 targeted unit tests pass fresh (46 tests, 0 failures), and the diff
touches exactly the 3 stated files with no other usage-metered feature touched. **1 of 8 refuted:**
the regression-guard test's name overclaimed — it verified pure pass-through logic, not an actual
copy/remix. **Fixed, not just documented:** confirmed the real guarantee is structural (`NoteService`
and `ShareService` have no `UserUsageService` dependency anywhere in their constructors or anything
either depends on, including `ActivityTrackingService` — copying/remixing is architecturally incapable
of reaching `incrementStudyPackGeneration`, not merely observed not to), renamed the test and
documented this precisely (PR #1483, commit `ad6caa8c`).

### Shipped

- **Fix (PR #1482, commit `0ce371a3`):** `StudyPackUsageService.resolveUsage` drops the quota floor,
  trusting the tracked generation counter alone. Full backend suite + Docker-backed
  `NativeQueryPostgresIntegrationTest` both clean before merge.
- **Falsification correction (PR #1483, commit `ad6caa8c`):** test naming/documentation fix per the
  Codex pass above — no production code change.

## v0.167.0 - Study Journey Purpose

**Status: Released** (signed off 2026-10-08; commits `22584379`/`1dd1c80c` on `releases/v0.167.0`)

Theme: activate the parked "Study Journey Purpose / Learner Goal" architecture discovery, resolve
its Q1 ("what problem are we solving"), and implement the chosen scope. **Started as investigation-only
per the discovery brief's §14** (`docs/claude-plans/study-journey-purpose-learner-goal-discovery-brief.md`)
— **scope expanded in place, owner decision 2026-10-08, after the investigation (§11, all 12 questions)
completed and the owner picked Q1's scope.** The brief's blanket "no implementation" constraint is
superseded for exactly the scope named below; everything else in §14 (no certification/role catalog,
no `ProfileType` change, no recommendation-engine change, no BSCS backfill) still holds.

### Investigation phase (complete)

Worked the brief's §11 open questions in order, as a companion decision file
(`docs/claude-plans/study-journey-purpose-learner-goal-discovery-decision.md`), corrected and
re-verified across several passes (see that file's own §0a and the git history on this branch for
the full trail). Found the brief's own premise partially stale (`UserEntity.studyGoal` and
`course_programs.exam_goal_slug` already persisted crude versions of both concepts), found a
genuine presentation gap (Journey terminology keyed to viewer `ProfileType`, never to the Journey
itself), and left Q1 to the owner with three evidenced candidate scopes.

### Implementation phase — Q1 resolved as Candidate A + B (+ Civil Engineering), owner decision 2026-10-08

**A second verification pass, done before any implementation, recalibrated both candidates** —
smaller and better-evidenced than the decision doc's speculative Q12 sizing in both cases:

- **Candidate A is a labeling fix, not a mode-selection fix.** `getAvailableExamModes` proves
  `EXAM_MODES.md`'s Audience & Profile-Type Mapping table is load-bearing: Board Exam Mode is never
  offered as a tile to a non-Board-Taker profile. Letting `resolvePlanPremiumExamMode` (the terminal
  CTA on a collection page) override viewer profile with Journey nature would route a viewer toward
  a mode their own profile-gated screen doesn't list — a real contract violation, not a risk.
  **`resolvePlanPremiumExamMode` and `EXAM_MODES.md` are explicitly OUT of scope.** Only
  `getCollectionLabels` (terminology) changes, resolved server-side from a collection's root
  ancestor's `courseProgram` → `course_programs.exam_goal_slug`, gated additionally on the root's
  own `learner_level = BOARD_EXAM_REVIEW` (a concrete guard against the future collision Q11 left
  open — two roots sharing one `courseProgram` with different purposes). Page-chrome labels
  (`navLabel`, empty states) stay viewer-keyed; only per-collection nouns become Journey-aware.
- **Candidate B's real gap is smaller than "single-valuedness."** Production read: only **2 users**
  have adopted roots spanning ≥2 distinct `exam_goal_slug`s — building multi-goal infrastructure
  now would ship ahead of its evidence. `docs/features/profile.md:97-99` documents the EXAM/SUBJECT
  split as intentional (not a bug). What's actually missing: no UI lets a learner deliberately
  choose or switch their exam goal — the only paths are Profile's "Clear" and the dashboard banner's
  single auto-suggestion; "Change" is documented to redirect into editing `focusSubjects` instead.
  **Fix: add a constrained exam-goal combobox (reusing the existing `SuggestionCombobox`/
  `CourseProgramCombobox` pattern, `allowCustom=false` per this repo's taxonomy-field rule) wired to
  the existing `updateStudyGoal` endpoint — no new column, no migration.** True multi-goal support
  is explicitly deferred, not silently dropped: re-open only if the ≥2-distinct-exam-goal population
  grows past a handful.
- **Civil Engineering fold-in (owner-confirmed, 2026-10-08):** widen `course_programs.exam_goal_slug`'s
  `CHECK` constraint to include `'ce'`, seed it onto the existing Civil Engineering row, add it to
  `ExamGoalConfig.java` and the new exam-goal combobox's options. The one piece of this release that
  needs a migration.

Shipped implementation:

- **A:** Collection detail, Goal detail, and summary responses now carry the exam slug resolved from the root collection's program and `BOARD_EXAM_REVIEW` level. Collection nouns show ALE, PNLE, LET, CPALE, or CELE Review Set terminology across profiles; library chrome stays profile-keyed.
- **B:** Profile's Study Focus now offers a constrained exam picker alongside Change and Clear. It lists the server's valid exam definitions, allows switching, and clears subject focus when a goal is saved.
- **CE:** V154 widens the catalog constraint and assigns `ce` to Civil Engineering, with a row-count guard. `ExamGoalConfig` names CELE and includes it in valid slugs. The optional Exam Hub page was deferred because it creates a new public SEO surface.

Anti-drift for the implementation phase: additive only — no change to `ProfileType`, no
recommendation-engine change, no certification/role catalog beyond the single `'ce'` slug, no
backfill of existing Study Journeys' presentation, no touch to `resolvePlanPremiumExamMode`/
`EXAM_MODES.md`. New DTO fields are additive (old clients ignore them; no field removed or renamed).

**Delivery:** Codex-routed per `CLAUDE.md`'s task-routing table (new backend service logic +
multi-system frontend/backend change). Claude Code designed the scope above and will write the
Codex prompt; Codex implements; `/audit-diff` runs on the diff before commit.

**Verification tier:** one scoped cold falsification agent on the diff (not the full three-agent
tier — no money/quota/permission surface is touched, per `CLAUDE.md`'s gate), plus a real-request
(`MockMvc`) test for the new/changed DTO fields, plus the standing Postgres migration harness for
the Civil Engineering `CHECK`-constraint change.

**Falsification pass results (2026-10-08, cold agent, 10 claims checked against live code, not the
commit message):** all 10 confirmed — both audit fixes (privacy, N+1), the collision-guard test,
the page-chrome/per-collection-noun split, the `EXAM_MODES.md`/`exam-mode-visibility.ts` exclusion
(empty diff, verified), mutual-exclusivity in both directions, real transport-level tests for every
new file, feature-doc accuracy, and 327 backend + 39 frontend tests passing. **Nothing found that
blocks signoff.** Three non-blocking follow-ups logged rather than silently dropped:
- No direct unit test pins `resolveWithoutInheritance` against a collection that itself has a
  non-null `parentCollectionId` (currently only exercised indirectly via a parentless collection,
  equivalent in effect, not in intent-signaling). Low priority — add if this area is touched again.
- No test exercises `resolve()`'s cross-owner defensive branch (fails closed, currently
  dead-from-a-coverage-perspective).
- `NativeQueryPostgresIntegrationTest`'s V154 migration test was verified by reading source only in
  this pass (the falsification run used `-Dnativequery.pg.skip=true`); it was run earlier in this
  session with Docker and passed, but wasn't re-run live in the same pass as the other 9 claims.
- Candidate B's "only 2 production users" figure is a production-state claim from the discovery
  phase, not re-verified in this pass — same decay risk as any other prod-state claim per
  `CLAUDE.md`; re-check before relying on it further rather than assuming it still holds.

### Shipped

- **Study Journey Purpose / Learner Goal — §11 question pass (2026-10-07/08).** Worked all 12 of
  the discovery brief's §11 open questions, in order, as a companion decision file:
  `docs/claude-plans/study-journey-purpose-learner-goal-discovery-decision.md`. Found and corrected
  a stale half of the brief's own "premise verified against current code" line: `UserEntity.studyGoal` and
  `course_programs.exam_goal_slug` already persist crude, shipped versions of Learner Goal and
  Target Credential at the profile/catalog grain — the brief's "nothing already represents either
  concept" claim held only at the Note/Collection grain it checked. Found a concrete,
  code-reproducible gap (not a bug — Journey terminology and the terminal exam-mode CTA are keyed
  solely to the viewer's `ProfileType`, never to the Journey itself, and catalog adoption is
  verified unrestricted by profile), but confirmed the quiz-mode half of that gap sits against a
  **locked** contract (`EXAM_MODES.md`'s audience-by-profile mapping), so any fix there is a product
  decision, not a cheap rewire. Q1 (which problem to solve) is explicitly left to the owner with 3
  evidenced candidate scopes, not picked unilaterally. One empirical question (whether published
  Journeys sharing a `courseProgram` are already distinguishable without new metadata) is blocked on
  a production read — Render MCP `CONNECT_TIMEOUT` all session — and left open rather than guessed.
  `ROADMAP.md`'s Backlog Index row for the brief (`:701`) updated to summarize these findings and
  point at the new decision file.
- **Six `[CHECKPOINT — due 2026-10-07]` ROADMAP rows read** (the handoff note's count of five was
  itself wrong — one row, the shared-quiz-promotion checkpoint, was missing from its list). One closed on its own kill criterion (archive
  convention: `RELEASES.md` at 6 sections, `CLAUDE.md`'s `Current version:` block at 1,304 chars,
  neither regrown — **but note `CLAUDE.md`'s total file size, 50,177 chars, remains over the
  checkpoint's separate ~45,000-char observation**, unrelated to the kill criterion itself). One
  partially advanced with a code-only finding (`generation_failure_reason` write path confirmed
  live via `StudyPackService.java:1316-1338`, prod count still unread). The remaining four
  (announcement fan-out pool saturation, `/notes/public` re-saturation, SEO `sort=recent`
  engagement, shared-quiz promotion floor) are genuinely production-only reads and are marked
  blocked rather than guessed at; none had their `Last reviewed` date advanced.
- **Render MCP reconnected, 2026-10-08 — every blocked read above was run.** All read-only
  `SELECT`s via `mcp__render__query_render_postgres` (self-wrapped in a read-only transaction) plus
  two Render platform reads (`get_metrics`, `list_logs`); no write made or considered.
  - **Study Journey Purpose's Q11 production read: RESOLVED (corrected once after an advisor pass
    caught that `published_at IS NOT NULL` alone isn't "official root" — any user can publish their
    own collection; re-run with an `owner.role` join).** Every published, curator-owned
    (`role=ADMIN`) `courseProgram` root in production is the only one for its program today — no
    academic/licensure-prep collision exists yet, so `courseProgram`/`exam_goal_slug` currently
    disambiguates, though this is a point-in-time fact (it breaks the day a program publishes a
    second root), not a structural guarantee. Sharper finding, also corrected once: Civil
    Engineering's curator-owned, 2-adopter official root ("🏗️ CELE Comprehensive Review") has
    `exam_goal_slug IS NULL` — but the goal-nudge *filter* itself still works for it via the
    free-text fallback (checked against `ProgressReportService.filterGoalStudyPacks:446-448`); the
    real gap is narrower — no Exam Hub discovery page, and `GOAL_TYPE_SUBJECT` framing instead of
    the recognized-exam `GOAL_TYPE_EXAM` framing on the dashboard goal card. Decision file and
    `ROADMAP.md:701` updated accordingly.
  - **Announcement pool-starve checkpoint:** 419 active users (197 max in one `profile_type`), only
    1 non-DRAFT announcement ever. Falls in the kill criterion's 200-500 advisory band, not a clean
    pass — re-dated to `2026-11-07` (this pass's own judgment call on cadence) with interim guidance
    to publish large announcements off-peak.
  - **Failure-reason column checkpoint: CLOSED.** 9 notes carry a failure code (2 distinct codes),
    and all 9 now sit at `status = GENERATED` while still carrying their failure reason — the
    retry-survival guard this column exists for is confirmed in production.
  - **`/notes/public` pool-saturation checkpoint: SUPERSEDED, not a clean close.** Postgres
    `active_connections` holding flat at 20-22 for 30 days is uninformative (HikariCP keeps
    `maximumPoolSize` connections open regardless of load). `list_logs` for the literal Hikari
    phrase `"Connection leak detection"` (timed out once, succeeded on retry) found zero matches
    2026-09-09 → 2026-10-01 and exactly two on 2026-10-04, neither naming `/notes/public`. Checked
    whether these two (both resolving to `NoteCollectionService.adoptGoal`) were a bystander effect
    of `/notes/public` DB saturation rather than their own thing: the existing incident's own
    fingerprint (the same note's `PUBLIC_NOTE_VIEWED` ≥3 times inside either hold window) and
    `server_failed`/`server_restarted` events both came back empty for that evening — negative on
    both, so folded as a dated data point into the pool-exhaustion row's already-open
    "connections held on unidentified blocking I/O" question (`ROADMAP.md`'s 2026-09-18 row)
    rather than opened as a new finding. **But this row's own narrow instrument still cannot close
    its real question: `/notes/public` saturation DID recur inside this same 30-day window** — 24
    restarts since 2026-09-01, diagnosed 2026-10-01, fixed as `v0.165.0` Leg B — through DB CPU
    exhaustion on an unbounded ranked-branch fan-out. A leak-detection trace names where a
    connection was *acquired*, not what actually saturated the database, so it can't reliably
    attribute a DB-wide slowdown to any one path either way. Superseded by the Leg B row and its
    own post-fix checkpoint (clock from 2026-10-05), not an independent pass.
  - **SEO `sort=recent` checkpoint: CLOSED, no material fall.** `PUBLIC_NOTE_VIEWED`/`pathType=seo`
    (56,444 all-time, 32,037 in the last 30 days) shows no drop around the deploy week and both full
    weeks after it are higher than any week before — though the deploy week itself (1,991) and the
    week right after (6,476) bracket a lower week in between (2,838), so "grew every week" overstates
    it; the honest read is "no material fall," not uninterrupted growth — a raw traffic count, not a
    controlled comparison, but the kill criterion's own metric shows no fall.
  - **Shared-quiz promotion-floor checkpoint: applied as written — promoted, floor unmet, hands to
    an owner decision, not reclassified.** 1 share link from 1 generator against a 20-link/5-generator
    floor; that one link was created 2026-09-05, a day *before* the promoting tip shipped (`v0.122.0`,
    2026-09-06), so there have been zero links since promotion; the 1,245 tip impressions are 7
    distinct users (6 learners, 1 admin), not 1,245 people — and 1,245 ÷ 7 ≈ 178 firings per user for
    a tip documented as one-time, recorded as a separate over-firing observation, not fixed. An
    earlier draft of this read reclassified the unmet floor as "not really promoted" to avoid the
    owner-decision branch; withdrawn as fitting the criterion to the data after the fact. The row now
    states the facts (near-zero reach, zero post-promotion links) and leaves the call to the owner.
  - **`INACTIVITY` retention-email checkpoint (separately tracked, `due 2026-10-08`): RE-DATED to
    `2026-11-23`, not a kill-criterion fire.** Went through two wrong conclusions before landing on
    the right one, kept in the row as the record: first read all-time `email_log` (5,003 sent, 0
    clicked) and read it as the kill criterion firing; corrected after an advisor pass to check
    whether click tracking was globally broken (it wasn't — `DUE_CONCEPTS_DIGEST` recorded real
    clicks, confirmed via `email_log` and `email_open_daily_counts`); corrected again on finding the
    actual error — tracking is Stage 1a instrumentation (`V149`, installed 2026-09-24), not
    `INACTIVITY`'s lifetime history, and re-anchored to that deploy, `INACTIVITY` sent only 537 (not
    5,003) with 0 clicks, meeting neither leg of the row's own 30-click-or-2,000-send floor. An
    unmet floor is a re-date per the row's own rule, not a verdict either way — re-dated to the
    doctrine's own pre-specified 60-day backstop from the `V149` deploy, not a fresh interval.

## v0.166.0 - Measured Twice

**Status: Released** (signed off 2026-10-07; PRs #1475, #1476, #1477, #1478 merged into `releases/v0.166.0`)

Theme: close out the two post-signoff follow-ups from `v0.165.0` that were already done and waiting
on branches targeting `main` — bundled here instead, since merging doc-only branches straight to
`main` triggers a production build/deploy the owner wants to avoid for changes this small. **Expanded
2026-10-06, owner decision, past the original doc-only scope**, to fix the Hikari long-connection-hold
mechanism the kickoff's own checkpoint sweep found and root-caused (see Shipped below) — two real
unbounded-work defects, not an LLM-hold pattern, so `v0.112.0`'s deferred Phase 3 would not have
addressed either.

### Planned Scope

**The first two items are docs-only, already written and reviewed on their own branches; this
release's work is landing them, not authoring them.**

- **Record `v0.165.0`'s confirmed deploy timestamp in its own `[CHECKPOINT]` row.** Branch
  `docs/v0.165.0-post-deploy-checkpoint` (PR #1475, opened against `main`, retargeted here). Render
  live 2026-10-05T15:21:14Z, Vercel Production deployment success 15:24:27Z — both confirmed serving
  the same commit via `gh api`. Gives the outage-fix checkpoint a real start time
  (`2026-10-12T15:24:27Z` due date) instead of "not yet measurable."
- **G2(a) title-leak finding, correction, and a measured decision not to build a deterministic
  fix.** Branch `docs/g2a-title-leak-finding-and-correction` (PR #1476, opened against `main`,
  retargeted here). A peer session's post-deploy production evidence showed `v0.165.0`'s G2(a)
  prompt fix doesn't reliably stop Subject leaking into Study Pack titles (1 of 3 regenerated packs
  still leaked). The obvious fix — a deterministic strip mirroring G2(b) — was scoped and then
  measured against production before being recommended: **3,390 existing Study Pack titles already
  end in `"{something} in {the note's own Subject}"`, and a 40-row random sample is, without
  exception, legitimate** ("Torsion in Strength of Materials", "Infection Control... in Nursing") —
  a blind strip would mangle thousands of good titles to catch an unknown, likely small number of
  real leaks, with no mechanical rule found to tell them apart. **Decision: do not build it.** Also
  corrects an earlier overstated claim (the five-Applicable-Programs mixup was called "root-caused"
  to a Program Family shortcut; a peer session's own verification showed the family's membership
  doesn't fully reproduce the observed set, so the mechanism is plausible, not confirmed).
- **Fix 1 — `PublicProfileService.buildPublicProfile`'s unbounded profile load (backend only).**
  Confirmed root cause of the dominant Hikari long-hold pattern (see Shipped below): loads every
  public note (full `content`) and every full `StudyPackEntity` (full quiz JSON) for one account with
  no limit, to let the frontend pick its top-8-by-metric display. Fix: compute `totalCopies`/
  `totalShares`/`totalViews`/the note count via SQL aggregates over ALL of the account's notes, and
  only fetch full content/summary for a generous top-N (by copy/view/share) — same response shape, no
  frontend change. Codex-scope (new aggregate + top-N query logic across `PublicProfileService.java`
  and its repositories, ~3-4 files).
- **Fix 2 — `NoteCollectionService.adoptGoal`'s sequential copy loop (backend + frontend — Option A,
  owner decision 2026-10-06).** Confirmed root cause of the secondary long-hold pattern: copies a
  Goal's children sequentially under one shared connection (open-in-view + HOLD mode), confirmed up to
  571 notes in a real production Goal. **Owner chose the background-job fix over the smaller
  synchronous EntityManager-clear option** — removes the connection-hold ceiling entirely rather than
  raising it, at the cost of a bigger blast radius: `adoptGoal`'s copy path is shared with plain
  `adopt()` and `applySourceUpdate` (row 782's mechanism), and the endpoint's response contract
  changes from synchronous to a started-job response, requiring frontend polling/progress UX. Scope is
  `adoptGoal` only — `adopt()`/`applySourceUpdate` stay synchronous (lower individual exposure: at most
  one Subject Plan's worth of notes per call, not a whole Goal's). Codex-scope, multi-system
  (frontend + backend).

Anti-drift: no production write. `adopt()` and `applySourceUpdate`'s existing synchronous behavior and
response shape must not change — only `adoptGoal`'s own endpoint moves to a background job. Fix 1 must
not change `PublicProfileResponse`'s shape (no new pagination params) — verify against the frontend
consumer (`public-profile-page-client.tsx`) before shipping, not just the backend tests.

**Verification tier:** doc-only items — single `advisor()` summary, as before. **Fix 1 — one scoped
cold falsification agent** (changes a production read path; verify the aggregate/top-N queries against
real data, not just unit tests). **Fix 2 — at least one scoped cold falsification agent, and consider
whether it rises to the full three-agent tier**: it changes a shared method's call-site contract
(`adoptGoal`) while leaving two siblings (`adopt()`, `applySourceUpdate`) on the old path, and it
introduces new async/job infrastructure — re-evaluate the tier once the Codex prompt's actual diff
shape is known.

### Shipped

- **Goal adoption now runs as a resumable background job.** The production Goal with 571 notes
  and confirmed 60–140s+ Hikari connection holds motivated moving its per-child copy loop off the
  request thread. The request still creates the personal Goal and returns its id immediately; a
  dedicated two-worker executor copies each published Subject Plan in its own transaction. A
  persisted job row exposes owner-scoped progress, and a configured sweep re-enqueues stale work
  after deploys. Completion stamps the Companion baseline and primary invariant and writes the
  Goal adoption analytics event atomically with the completed marker. Dashboard, public cards,
  discovery intent, and the collection page display progress and offer retry on status failure.
  `adopt()` and `applySourceUpdate` share the copy primitive but are unchanged — verified both by
  tracing every call path and by the existing test suite, which asserts their behavior unchanged.
  **Audited by a cold falsification pass (2026-10-07) against the Codex prompt's own acceptance
  criteria before merging** — no blocking defects found. One real gap it caught: the Testcontainers
  integration test only ever seeded PUBLIC children, so the "adopts every stamped child regardless
  of visibility" invariant (the exact defect `v0.161.0` PR #1453 fixed) had no test exercising the
  real job execution path for a PRIVATE one — traced the code and confirmed the path itself is
  unchanged by this diff, then closed the gap with `stampedPrivateChildIsAdoptedTheSameAsAPublicOne`,
  which passes against a real Postgres container. **Known limitations, not blocking:**
  `waitForGoalAdoption`'s poll loop has no caller-side cancellation, so a component that calls it
  directly (rather than through the effect-based poll already used by all 4 call sites) leaves a
  dangling promise after unmount — no crash, just a wasted request; the collection detail page's
  poll effect re-checks status on every view of an already-completed Goal rather than caching that
  it finished, which is wasteful but not incorrect; and `runGoalAdoption`'s initial claim
  transaction isn't covered by its own error logging, so a failure there would surface as an
  uncaught executor-thread exception rather than through this app's structured `goal_adoption_failed`
  log line.

- **Bounded Public Profile reads and a lightweight author-card summary (v0.166.0).** Production
  long connection holds of 63–142 seconds coincided with a DB-CPU pin and health-check failure;
  the largest creator had 1,997 public notes, all previously hydrated with full content and Study
  Pack quiz JSON on a profile request. SQL now sums copies, shares, and views across every public
  note while only ranked candidates are hydrated. The author card on each public note requests a
  three-field count-based summary instead of the full profile. Grouped full-catalog labels keep
  Learning Focus accurate when ranked cards favor a different subject. The full response shape,
  visitor display, profile ISR cache, and owner-only refetch gate stay the same. **Audited
  (2026-10-07) by tracing every changed file and running the full affected test suites, including
  the new `PublicProfilePostgresIntegrationTest`, which seeds 210 real notes with engineered ties
  and explicitly asserts the hydrated candidates' own totals are LESS than the true aggregate — a
  guard against a totals-computed-from-candidates regression, not just a passing count.** No
  blocking defects found; the Learning Focus split (a second lightweight aggregate endpoint,
  beyond what the originating prompt specified) was Codex's own catch of a real consequence of
  bounding `publicNotes` — the existing Learning Focus sentence derived its course/subject labels
  by iterating that list directly, which this fix would have silently narrowed to only the
  ranked-candidate subset. **Correction, 2026-10-07, from a second cold falsification pass run
  specifically on this fix (the release's own Planned Scope had called for one; it was skipped at
  merge time in favor of a direct audit, a process gap caught and closed after the fact):** the
  original "known limitation, not blocking" note below undersold a real finding. Splitting the
  server-rendered profile page into two sequential fetches (profile, then Learning Focus)
  introduced a genuine inter-request race that did not exist before — if an owner's profile
  visibility toggled in the gap between the two calls, the second request would 403 and throw,
  failing the whole page even though the first request had just succeeded. **Fixed same day:**
  `frontend/lib/server-public-profiles.ts` now fires both requests before awaiting either (profile
  fetch issued first, matching the existing test's call-order assertion; the Learning Focus
  promise carries a no-op `.catch` so an unawaited rejection on the private/not-found branches
  never surfaces as unhandled), closing the window to the same near-zero exposure the single-fetch
  page always had. Two new tests pin this: one proves both fetches fire before either resolves,
  one proves the private-profile branch cannot throw from the discarded focus promise. The same
  pass also found a second, lower-severity item, left as a known limitation rather than fixed: the
  hydrated candidates' SQL tie-break (`title collate "und-x-icu"`) is not provably identical to the
  frontend's `.localeCompare()` tie-break (runtime-default locale, not pinned to root) — they could
  disagree on accented or locale-sensitive titles. Theoretical, not observed in the 210-note test
  fixture, not worth a fix on its own.
- **PR #1475 — `v0.165.0` deploy-timestamp checkpoint record.** Merged into `releases/v0.166.0`
  (`7933a96a`). Records Render live 2026-10-05T15:21:14Z / Vercel Production deployment success
  15:24:27Z into the outage-fix `[CHECKPOINT]` row, starting its clock
  (due `2026-10-12T15:24:27Z`) instead of leaving it "not yet measurable."
- **PR #1476 — G2(a) title-leak finding + Applicable Programs root-cause correction.** Merged into
  `releases/v0.166.0` (`63bd1d68`). Adds `docs/claude-findings/2026-10-06-g2a-title-subject-leak-partial-failure.md`
  and the decision not to build a deterministic title strip (see Planned Scope above for the
  measurement); corrects the ROADMAP Backlog row's "root-caused" wording on the five-Applicable-Programs
  deviation to "plausible, not confirmed."
- **Kickoff's overdue-`[CHECKPOINT]` sweep, read and closed (doc-only, no code change).** The kickoff's
  own step-9 scan found 6 overdue (`due 2026-10-04`/`10-05`) and 3 due-today (`10-06`) checkpoint rows
  in `ROADMAP.md`'s Backlog Index, all genuinely unactioned. All 9 read against production (read-only
  SQL via `query_render_postgres`, Render app-log traces via `list_logs`, one owner-answered question
  that isn't data-derivable) and written up in `ROADMAP.md` with results. **Two kill criteria FIRED and
  are recorded plainly, not explained away:** (1) H5's coverage ratio FELL post-deploy (61.4% vs. the
  68.2% pre-H5 baseline, n=228 — not underpowered) — H5 did not achieve its stated goal; bears on
  whether H6 is worth scoping. (2) Additive-update apply uptake is 0.3% (1 of 367, against a healthy
  44% offer rate, so not an "offer gap") — per the `v0.116.0` checkpoint's own kill criterion, Slices
  4-5 (structural updates) must NOT be built on this mechanism. **One finding corrected same day:**
  HikariCP `Apparent connection leak` traces are real and recurring, but pairing all 9 against their
  `"...was returned to the pool (unleaked)"` lines confirms these are LONG HOLDS (63s–142s sampled),
  not permanent leaks — the first pass's "criterion (ii) fires" was an overclaim (a leak trace's stack
  shows where a connection was acquired, not what the request did afterward). None of the three
  pre-stated kill criteria cleanly fits; recorded as such rather than forced into one. **Two confirmed
  root causes found instead, both unbounded synchronous DB work, neither an LLM hold:**
  `PublicProfileService.buildPublicProfile` loads every public note plus every full Study Pack for one
  account with no limit (confirmed in production: the top account has 1,997 public notes), and
  `NoteCollectionService.adoptGoal` copies a Goal's children sequentially under one shared connection
  (confirmed: the largest production Goal has 571 notes), both amplified by `open-in-view=ON` +
  `DELAYED_ACQUISITION_AND_HOLD`. Neither is "connections held across slow external calls," so
  `v0.112.0`'s deferred Phase 3 (scoped to LLM-call transaction boundaries) does not address either —
  that conclusion survives the correction; only its justification changed.
  **Four re-dated** (populations still too small to read: Adaptive Practice proximal tier, combined-quiz
  tip impressions, bulk-regen TEACHER allowance). **Two closed clean:** classification-authority-transfer
  (zero-cohort, exactly as its own denominator clause predicted) and title-suggestion uptake (24.6% apply
  rate — meaningful use, kill criterion does not fire, keep the card). **One closed on owner input:**
  bulk-regen receipt TTL — no report of a lost receipt, no extension; a related owner question (does
  bulk regeneration survive a restart/deploy mid-batch) was checked in code and confirmed already
  correctly handled (`NoteBulkRegenerationReceiptService.getReceipt`'s `stale` flag plus the frontend's
  explicit restart-explanation banner) — no gap, nothing built.

## v0.165.0 - Bounded Discovery

**Status: Released**

Theme: stop a public Note page's discovery rails from fanning out into every page of a subject/program
list and saturating the production database — 24 backend restarts in the last month — and fix three
generation-behavior defects the Computing R4 BSCS pilot found.

### Planned Scope

**Two independent workstreams, bundled into one release at the owner's explicit direction** (urgency of
the reliability fix was the reason given for not shipping it standalone, against the fix plan's own
recommendation to the contrary — recorded here, not silently overridden).

**Workstream 1 — production reliability (urgent).** Diagnosis: `docs/claude-findings/2026-10-01-prod-restarts-public-note-page-fanout-db-saturation.md`
(Prod Investigator session). Fix plan: `docs/claude-plans/2026-10-01-public-note-page-fanout-fix-plan.md`.
Owner product decision on the one open question (rail ranking): `docs/claude-plans/2026-10-01-public-note-discovery-rail-ordering-decision.md`.

- **Root cause:** `frontend/app/public/library/[subject]/[slug]/page.tsx`'s two rails — "More from
  {Subject}" and "More {Program} notes" — walk every page of the subject's/program's entire public-note
  list via `fetchAllPublicNotePages` (`server-public-notes.ts:84-106`), which sends no `sort`, so every
  page takes the ranked branch (`NoteService.java:807-831`): a full popularity-ranking query plus a count,
  repeated per page (Accountancy: 8 pages; Civil Engineering: 18), to fill 7 cards the page then re-sorts
  itself. 23 of 24 `server_failed` restarts since 2026-09-01 trace to the liveness probe timing out while
  these queries saturate the `basic_256mb` (0.1 CPU) database.
- **Fix (Leg B of the plan):** both rails become one bounded `page=0&pageSize=N&sort=recent&readyOnly=true`
  fetch each, over-fetching by one row to exclude the current note (matching the existing pattern in
  `getServerPublicNotesBySubject`), instead of walking pages and ranking in JS. `fetchAllPublicNotePages`
  itself gains a required `sort=recent` parameter, which also fixes the subject listing page, exam hub
  pages, and the sitemap — the plan's other named walkers — for free.
- **Owner decision, not absorbed silently:** do **not** preserve live popularity ranking on these two
  rails — switch to most-recent, deterministically. Raising the database plan is explicitly off the table
  (pre-revenue). Full reasoning in the decision file above.
- **Audited, not touched:** Explore's `/notes/public/discovery-sections` is already bounded (fixed
  `limit=6`/section, no pagination walk, no count query) — not implicated in the outage, not changed.
  Still runs live `POPULAR`/`FEATURED` ranking per request (not cached), recorded as a Discovery v2 /
  precompute follow-up, not built here.
- **Legs explicitly not in this release:** Leg A (raise DB plan) is an owner Render-dashboard action, not
  code. Leg C (concurrency cap / query timeout on the ranked endpoint) is sequenced after A and B are
  measured. Leg D (slug-route caching) is recommended deferred — the backend records page views on that
  fetch, and caching would stop counting them.
- **Index/migration:** none. `EXPLAIN` (no `ANALYZE`) against production confirms the bounded query is
  already a large improvement over the current fan-out even without a dedicated index; the existing
  `idx_notes_visibility_updated_at` index doesn't perfectly match (`created_at`, not `updated_at`), and
  the subject/program filters are regexp/EXISTS-based and not trivially indexable regardless. Recorded as
  a measured follow-up if the catalog grows enough to matter, not built speculatively.
- **Count queries:** cannot be removed without new backend scope — `NoteService.listPublic` always runs a
  count alongside the rows query for any SQL-orderable paginated request, regardless of whether the
  caller needs `total`. Each rail costs 1 count + 1 bounded rows query (down from dozens of unbounded
  queries), which is not built around in this release.

**Workstream 2 — Computing R4 BSCS pilot generation-behavior fixes.** Evidence:
`docs/curriculum/r4-pilot/r4-pilot-report.md`, `r4-pilot-fix-plan.md`. The pilot confirmed `Computing`
Domain Context itself is correct on all five pilot Notes (do not touch Domain Context, Subject or
Authored Depth) — metadata correction (Applicable Programs) and content decisions are the owner's own,
through the production UI, not this release's.

- **G1 — shell commands render as LaTeX math in a Study Pack Summary** (`$g++\ program.cpp\ -o\ program$`).
  Measured against production first: 483/10,530 summaries contain `$`, essentially all genuine math
  (spot-checked, plus a literal-string search for `g++`/`.cpp`/`./program` across every summary and quiz
  in production found exactly **one** affected pack — the pilot's own). No existing-content risk to
  justify a renderer heuristic (which would risk false-positiving on real formulas, demonstrated by my own
  first-attempt regex producing 20 false positives). **Prompt-only fix**: add one instruction to the
  "Math notation" block (shell commands, flags, file paths and code are not math; never place them
  inside math delimiters or add a shell-prompt `$`) — the same new bullet applies to 10 files with a
  byte-identical Math notation block and to `note-generation-developer.txt`'s JSON-escaping variant.
- **G2 — Note Subject leaks into generated text as "{title} in {Subject}".** Two distinct mechanisms,
  both fixed: (a) the Study Pack's own title — the existing Title rule already forbids folding
  Course/Program into a title (`developer.txt:32-43`) but never named Subject; extend that *existing*
  bullet to also cover Subject, identically in `developer.txt` and `note-generation-developer.txt`, no new
  "don't do X" bullet (the `v0.96.0` anti-drift rule on wording). (b) the generated-note body's first
  line — verified as a *separate*, deterministic mechanism: the stored note title is the curator's clean
  topic, but the body's first line is the LLM's own independently-generated title
  (`backend/src/main/java/com/studysnap/backend/service/impl/OpenAiLlmStudyPackService.java`),
  never shown anywhere else (`GenerateNoteFromTopicResponse` returns only `content`). The prompt fix
  applies to both title-emitting prompts; the deterministic body-heading fix applies to **Bulk Generate
  only**, using its clean curator-supplied topic instead of the model's own title.
- **G5 — no log records the authoring domain or whether computation guidance fired.** One log line added
  to the Study Pack generation path. No stored prompts (would need a migration, not done), no note content
  logged.
- **Documented, not scoped (per the pilot's own instruction to measure, not change):** G3 (one quiz
  explanation asserted an unsupported "randomness" claim — single instance in 20 questions, doesn't trip
  the pilot's stop condition); G4 (computation guidance depends on auto-generated tags when Subject has no
  keyword — already documented as a known nuance in `v0.164.0`'s `DomainContext.java`, no regression
  observed in the pilot sample); G6 (Bulk Generate has no instruction field — explicitly a curation
  convention question under D-35, not a product gap).

Anti-drift: no DB plan change; no new ranking infrastructure, Redis, recommendation engine, vector search
or LLM call for discovery; no randomness; no Featured flag; no Explore or Public-Note-page redesign; no
change to global Public Library ordering; no BSCS TSV edits (strategist's own action); no bulk
generation; no `QUANTITATIVE_KEYWORDS` change; no production write of any kind.

**Verification tier, pre-declared:** this release touches prompt text shared across every generation
surface (11 files) and a production-reliability fix with no existing automated coverage of the request
shape it changes — **one scoped cold falsification agent before signoff**, per the standing gate
(delivery-introduced-defect trigger already fired once this cycle during the Computing pilot's own
review process, and the reliability fix's request-shape guards are exactly the class of thing a cold
agent should independently verify rather than trust from the implementing session).

**Checkpoint gate, pre-declared:** Workstream 1 ships ahead of its own evidence (the thread-occupancy
read in the diagnosis is INFERRED, not measured) — signoff owes a dated `[CHECKPOINT — due 7 days after
this release's production deploy]` per the fix plan's own pre-stated kill criterion (`server_failed`
events continuing at a comparable weekly rate means the diagnosis is wrong or incomplete).

### Shipped

- **Workstream 1 — Public Note discovery rail fan-out fix (frontend only, no backend change needed).**
  Both rails switched to one bounded `page=0&sort=recent&readyOnly=true` fetch each —
  `getServerPublicNotesBySubjectSlugRecent` and `getServerPublicNotesByCourseProgramRecent`
  (`frontend/lib/server-public-notes.ts:311-336`) — replacing the unbounded popularity-ranked walk (the
  24-restart mechanism). `fetchAllPublicNotePages` (`server-public-notes.ts:100-122`) now always sends
  `sort=recent`, which removes the expensive ranking-query multiplier from its other callers too — the
  subject listing page and exam hub pages (via `getServerPublicNotesBySubjectSlug`/
  `getServerPublicNotesByCourseProgram(s)`) and the sitemap — though those three still walk every page of
  their subject/program/catalog (that part of their job is unchanged and out of scope; see the
  full SURFACE/ENDPOINT audit in `docs/claude-plans/2026-10-01-public-note-discovery-rail-ordering-decision.md`).
  No visible-ordering regression on the subject listing page or exam hub pages: both re-derive their own
  Featured/Popular/Recent/remaining sections in JS from the full set via
  `lib/public-library-discovery.ts`'s own internal `.sort()`, independent of fetch order — audited, neither
  relies on arrival order. **Correction, found by the post-merge cold falsification pass**: the sitemap
  (`app/sitemap.ts`) is the exception — it maps the walker's result directly into entries with no re-sort
  step, so it now lists Notes most-recent-first instead of by the old popularity order. Not fixed, because
  it's not a regression worth fixing: a sitemap's entry order carries no ranking signal to a crawler, so
  this has no SEO or user-visible effect, unlike the subject/exam-hub pages' rendered card order. No caller
  passes its own `sort`, so no duplicate-param risk.
  `app/public/library/[subject]/[slug]/page.tsx` now derives `courseProgram` from the note's own joined
  `coursePrograms[0]` (`NoteService.resolvePublicDetailPrograms`, backed by
  `NoteCourseProgramRepository.findByNoteId`'s `ORDER BY course_programs.name` — the same ordering
  `findByNoteIds` used for the old list-item `applicablePrograms[0]` derivation, so multi-program notes
  resolve to the identical program as before, and the join is confirmed stable across ISR revalidations)
  instead of re-walking the program's note list. Tests: `frontend/lib/server-public-notes.test.ts`
  (mutation-tested — guards confirmed to actually fail without `readyOnly=true`),
  `frontend/app/public/library/[subject]/[slug]/page.test.tsx` (41 tests).
  `docs/features/public-library.md` section K updated to describe recency ordering instead of the stale
  engagement-score claim.
  - **Known limitation, intentionally not fixed this release:** the Subject and Program rails are not
    cross-deduped — after a visitor finishes the embedded Quick Check, a note sharing both the current
    note's Subject and Course/Program can appear in the quiz-preview rail and the Program section. The
    original rail contract's over-fetch covered the current note only; deduplicating Subject against
    Program would require its own bounded headroom decision and could starve the smaller rail when
    both top-N windows substantially overlap.
    Pinned as current, deliberate behavior by a dedicated test in `page.test.tsx`
    ("documents that the two most-recent-first rails are not cross-deduped"). Matches the owner's own
    instruction verbatim ("If cross-rail duplication already has a simple bounded exclusion mechanism,
    preserve it. If avoiding duplicates requires broad fetching/fan-out, production safety wins.").
  - **Known limitation, verified zero production impact:** `getServerPublicNotesBySubjectSlugRecent` drops
    the blank-subject/`general`-slug special case the unbounded walker carried (it used to fall back to a
    full-catalog scan to find blank-subject siblings — the worst fan-out of all, not reintroduced). A
    read-only `count(*)` against production (2026-10-02) confirms **0 public notes have a blank or
    whitespace-only subject**, so this has no current effect.
  - **Subject-vs-Subject duplication fixed:** the quiz-preview's "More from {Subject}" rail excludes
    notes shown in the always-visible "More in {Subject}" section, when that section has enough notes
    to render (`frontend/app/public/library/[subject]/[slug]/page.tsx:96-113`). Its single bounded
    recent fetch now requests six candidates plus the current-note headroom, then displays up to three.
    The "More in {Subject}" fetch and section are unchanged; when that section is hidden, the quiz
    rail retains its available related notes.
- **Workstream 2 — Computing R4 pilot generation behavior (G1, G2, G5).** G1 adds one identical
  shell/CLI-is-not-math bullet to all 11 Math notation prompts
  (`backend/src/main/resources/prompts/study-pack-v1/developer.txt:133`,
  `note-generation-developer.txt:89`). G2(a) extends the existing title rule to treat Subject as
  context when it only names a broader container, with the pilot's Algorithms example in both
  title-emitting prompts (`developer.txt:35-44`, `note-generation-developer.txt:21-30`). G2(b)
  replaces the model's body heading with the curator's topic on **initial Bulk Generate only**
  (`backend/src/main/java/com/studysnap/backend/service/NoteBulkGenerationService.java:359-371`).
  Owner decision, 2026-10-02: the single-note editor and onboarding accept an informal learner-typed
  topic distinct from a polished title, while Bulk Generate's topic is the clean curator title. The
  pilot observed clean stored Note titles beside Subject-leaking generated body headings
  (`docs/curriculum/r4-pilot/r4-pilot-report.md`, finding 6). G5 logs the effective authoring domain,
  Subject, guidance result and trigger once per input-message build
  (`backend/src/main/java/com/studysnap/backend/service/impl/OpenAiLlmStudyPackService.java:1698-1742`).
  **Verification limit:** G1 and G2(a) are prompt text; automated tests pin file content, not model
  behavior. **⚠️ Owner-verified 2026-10-06, result: G1 confirmed working (n=1); G2(a) PARTIALLY
  FAILS (n=3)** — regenerating three pilot Notes' Study Packs post-deploy left 1 of 3 titles still
  reading "{Title} in {Subject}" ("Algorithms and Their Properties in Programming Fundamentals"),
  confirmed directly against `study_packs` by two independent sessions. A prompt-only rule is not
  reliable for the title field; see `docs/claude-findings/2026-10-06-g2a-title-subject-leak-partial-failure.md`
  for the evidence and the owner's two open options (a deterministic title strip mirroring G2(b),
  or accepting curator-review as the only mitigation — not decided, not built). **Known limitations:** the single-note editor and onboarding
  rely on G2(a)'s prompt wording alone for generated titles; they do not get the deterministic body
  override. A bulk-created note's body heading can revert to the model title if its content is later
  regenerated through `StudyPackService.generateStudyPackFromExistingNoteAsync`, which bypasses
  `NoteBulkGenerationService`. G5 gives aggregate Subject/Domain visibility, not per-note or
  per-request correlation; the generation context has no note/request id, and one line is logged per
  actual attempt, including retries and Long Exam batches.

## v0.164.0 - Computing

**Status: Released** (signed off 2026-09-30; PR #1469 merged into `releases/v0.164.0`)

Theme: ratify `Computing` as a 13th Domain Context (ADR-001 amendment), ship the small enum/frontend value,
correct the two stale claims in the ratification package's own numbers, and sweep the context/spec docs the
strategist and curators read.

### Planned Scope

**Owner-directed, 2026-09-30, closing Workstream A** (`docs/claude-plans/computing-domain-context-final-decision-package.md`,
ratification-ready close-out; supersedes `computing-domain-context-decision.md` and its tightening prompt for
*action*, both kept as the evidence trail). Named "Workstream A" in `docs/curriculum/bscs-year1-decision-log.md`,
whose rows D-13 and D-34 are blocked on this closing.

- **ADR-001 amendment ratifying `Computing`** (enum `COMPUTING`, label `Computing`, `quantitative = false`) —
  a new dated section superseding the 2026-09-04 rejection, answering both of its grounds (naming borrowed from
  ACM/IEEE Computing Curricula 2020 and CHED CMO No. 25 s.2015; the comprehension test applies to curators, not
  learners), stating the two-test curator decision rule (object-of-study, then cross-sibling-reuse) as the durable
  operational boundary, and recording the corrected clause (a) evidence and the corrected governance ratio.
- **Two corrections to the package's own numbers, applied before anything cites them:** (1) the 34 Discrete
  Structures I rows do **not** get `applicable_programs` extended to IT/IS/SE — Domain Context = `Computing`,
  Applicable Programs stays Computer Science only, contrary to package §5/§9/§10; (2) the BSCS Year 1 corpus is
  136/136 classified (the 3 `AMBIGUOUS` rows resolved to `COMPUTING_SHARED`), not 133/136 as the package states.
- **Enum/frontend value** — `DomainContext.COMPUTING` (backend), `DOMAIN_CONTEXT_OPTIONS` entry + `DomainContext`
  union member (frontend), with a routing description (excludes CS-specific formalism, IT/IS/SE specialization
  treatment, and Precalculus/Algebra) and updated count/label tests on both sides.
- **Doc sweep** — `REVIEW_SET_SHAPING_CONTEXT.md` (closed vocabulary + two-test boundary paragraph),
  `review-set-workbook-spec.md:118-119` (independent stale-claim fix: the save-time rejection claim is wrong;
  the check is generation-time only), `GPT_CONTEXT.md` (12→13, and discharging the "don't propose a 13th value"
  line the same way the 9th/12th-value rules were discharged), `NOTES_AND_COLLECTIONS_CONTEXT.md` §0 (12→13).
- **R4 runbook addendum** (`docs/claude-prompt/canonical-knowledge-architecture-out/17-r4-verification-runbook.md`)
  — a Computing section for the 5 named pilot notes, prepared but **not executable today**: none of the 5 exist
  in production yet (read-only check, 2026-09-30), and driving the pilot is an owner action through the
  production UI in any case (a Domain Context write + regeneration), never Claude's to run.
- Close `docs/curriculum/bscs-year1-decision-log.md` D-13 and D-34, citing this package.

Anti-drift: no BSCS TSV edits (strategist's own action, out of this scope); no bulk generation; no
`QUANTITATIVE_KEYWORDS` change (no companion keyword decision here, unlike `BASIC_MEDICAL_SCIENCES`); no
prompt change (package §8 explicitly forbids preemptive prompt tuning); no production write of any kind.

### Scope disposition (signoff, 2026-09-30)

- **Every Planned Scope item: SHIPPED, verified against code with `file:line`, not from the release notes
  alone.** ADR-001 amendment: `docs/architecture/ADR-001-canonical-knowledge-architecture.md:417`. Enum:
  `DomainContext.java:47`. Frontend union member: `frontend/lib/api.ts:554`. R4 addendum:
  `17-r4-verification-runbook.md:189`. Decision-log closure: `bscs-year1-decision-log.md:33,54`.
- **The doc sweep EXPANDED beyond the four docs originally planned**, found during pre-commit review:
  `docs/features/notes.md`, `challenge-quiz.md`, and `study-pack-generation.md` also enumerate the closed
  Domain Context vocabulary and needed the same 12→13 update. Also fixed 6 stale file-path references left
  by an unrelated prior session's rename batch, in the same 4 files this release was already editing, and
  corrected a second stale claim discovered in `NOTES_AND_COLLECTIONS_CONTEXT.md` while re-stamping it
  (Interior Design / Landscape Architecture / Environmental Planning are now live catalog programs, not
  absent as it claimed; the catalog is 90 programs, not 41).
- **A pressure-test pass (`advisor()`) on the diff before commit found and fixed four real defects before
  they shipped**: an arithmetic error in the ADR (33/136 vs. the correct 34/136); a `[PROD]` tag on a count
  that actually came from the local TSV, not a production read; an R4 addendum methodology built on
  invented Subject-Plan assignments for the 5 pilot notes (none of which exist anywhere in the TSV), which
  broke for 4 of the 5 notes because a 3-program note cannot generate under a NULL-Domain-Context fallback
  at all under `ADR-001`'s own multi-program rule; and the kickoff's own commit-structure violation (code
  and doc-sweep feature content was about to ride on the kickoff's direct-to-release-branch commit instead
  of its own branch+PR).
- **Not shipped, by design:** the BSCS TSV itself is not edited (strategist's own action); the R4 pilot did
  not run (its 5 named notes don't exist in production yet); the 3 originally-`AMBIGUOUS` corpus rows are
  not separately re-verified here (the owner's resolution to `COMPUTING_SHARED` is taken as given, per the
  correction applied at kickoff).

### Shipped

- **ADR-001 amendment ratifying `Computing`** (13 values total): superseded the 2026-09-04 rejection
  (naming answered via ACM/IEEE Computing Curricula 2020 + CHED CMO No. 25 s.2015; comprehension test
  answered as curator-facing, not learner-facing); recorded the two-test curator rule; corrected the
  package's own 133/136 → 136/136 evidence and removed its erroneous 34-row `applicable_programs`
  extension instruction; re-read production rather than citing the stale `12:51` ratio — catalog is
  now 90 programs, ratio `13:90` = 0.144; carries `[CHECKPOINT — due when BSCS Year 1 Computing
  authoring reaches 10+ classified notes, backstop 2026-12-29 regardless]`, added to the Backlog Index
  at signoff with its full kill criterion and two-tier denominator rule. Inline dated flags added at
  the two lines that named `Computing` as previously rejected (not rewritten).
- **`DomainContext.COMPUTING`** (backend enum, `quantitative = false`) and the matching frontend
  `DOMAIN_CONTEXT_OPTIONS` entry / `DomainContext` union member, with a routing description (excludes
  CS-specific formalism, IT/IS/SE specialization, Precalculus/Algebra). Test coverage: `DomainContextTest`
  (13-label `containsExactly`, new CsvSource row, round-trip test updated to the newest value) and
  `domain-context.test.ts` (length 12→13, new routing-clause pinning test). Full backend build
  (2,636 tests) and full frontend suite (225 suites, 2,598 tests) both green.
- **Doc sweep**, all corrected to 13 values / the new two-test rule: `REVIEW_SET_SHAPING_CONTEXT.md`,
  `GPT_CONTEXT.md` (also discharged the "don't propose a 13th value" line, same pattern as the 9th/12th
  discharges), `NOTES_AND_COLLECTIONS_CONTEXT.md` §0, `docs/features/notes.md`, `challenge-quiz.md`,
  `study-pack-generation.md`. **Independent stale-claim fix**, `review-set-workbook-spec.md:118-119`:
  corrected "the server rejects the save" to "generation-time only" — found true regardless of this
  ratification. Bumped the "Last updated" stamps on `REVIEW_SET_SHAPING_CONTEXT.md` and
  `NOTES_AND_COLLECTIONS_CONTEXT.md` at implementation, and on `GPT_CONTEXT.md` itself at signoff (all
  three now read `v0.164.0 (Released)`, not `(In Progress)`); corrected a second stale claim found
  along the way in `NOTES_AND_COLLECTIONS_CONTEXT.md` (Interior Design, Landscape Architecture and
  Environmental Planning are now live catalog programs, not absent as it claimed; catalog is 90
  programs, not 41).
- **R4 runbook addendum** (`17-r4-verification-runbook.md`): a Computing section for the 5 named pilot
  notes, prepared but explicitly marked not-yet-runnable — none of the 5 exist in production yet
  (read-only check, 2026-09-30), execution is an owner action through the production UI regardless, and
  the comparison-arm mechanics are honestly scoped (a true A/B needs a single-program note; a
  multi-program note can only be scored on its `Computing` arm alone, since `ADR-001`'s own
  multi-program rule blocks a NULL-Domain-Context fallback from generating at all).
- **`docs/curriculum/bscs-year1-decision-log.md`** D-13 and D-34 closed, citing this package; D-33
  (Precalculus) stays open, unchanged, per the package's own scope.
- **`docs/claude-plans/computing-domain-context-final-decision-package.md`** — dated correction note
  added at the top recording both owner corrections, rather than silently editing the original.
- **`docs/product/ROADMAP.md`** Backlog Index row updated: gate cleared for ratification/code, corrected
  numbers, R4 pilot's actual blocker (notes don't exist) stated explicitly; at signoff, the row's Gate
  cell was rewritten to carry the formal `[CHECKPOINT — due when BSCS Year 1 Computing authoring
  reaches 10+ classified notes, backstop 2026-12-29 regardless]` tag with kill criterion,
  instrumentation and two-tier denominator rule, per the signoff checkpoint gate.

## v0.163.0 - No Peeking

**Status: Released**

Theme: stop a quiz from telling a learner the answer before they've committed to one, and stop Challenge
Quiz's question bank from racing itself during regeneration.

### Planned Scope

**Scope picked by the owner, 2026-09-27, from a Backlog Index survey (five candidates originally picked;
one dropped at kickoff — see the note after item 4 — leaving four, at the top of the documented 3-4 item
sweet spot).**

1. **Quiz answer-key redaction across 6 practice-session surfaces (full-stack, far larger than first
   scoped — see the design plan, revised after `advisor()` (3 rounds) and again after a cold Opus
   falsification pass, which itself needed one more round of direct re-verification before its findings were
   trusted).** `correctIndex`/`correctIndices`/`explanation` are served unfiltered, unconditionally, across
   Long Exam, Board Exam, Adaptive Practice, and Challenge Quiz. **Interview Practice, discovered mid-item to
   be a fully separate backend (`InterviewPracticeService`), added to scope: it has a real, already-shipping
   resubmission exploit** — directly re-verified, not taken from a subagent's report: `answerQuestion`
   (`:168-211`) takes a client-supplied `questionIndex` with no re-answer guard, generates a fresh LLM
   critique (which reveals correctness) on every call, and `buildReport` (`:461`) scores whatever was stored
   for that index at completion time — so submit-wrong-then-resubmit-correct is real and counts today. This
   is the learner's own practice record (an Interview Readiness Report), not a cross-user exposure — treat it
   as part of this item's normal ship cadence, not a separate hotfix, unless the owner decides otherwise.
   **Quick Review gets the LEAST protection of the 6, not the most, and is deferred to its own design pass:**
   its session has never stored a quiz at all (`session.setSessionState(null)` from creation) — this actually
   makes the original kickoff claim ("the `/progress` round trip carries no quiz-item data") TRUE, just true
   for a different, uglier reason than claimed: there's no session-side quiz to protect because the frontend
   never reads one — `quick-review/page.tsx:371,417` sources the entire rendered quiz, answer key included,
   from `getNote(noteId)` (`NoteResponse`), the exact endpoint decided to stay unredacted. **The "backend-only
   pitch" history in the original kickoff text was still false** (traced to the discarded unauthorized fork,
   echoed in without verification) — that correction stands; the response-type claim itself did not need
   correcting, only its conclusion did. **Full corrected design:**
   `docs/claude-plans/2026-09-28-quiz-answer-key-redaction-plan.md`. Owner decisions: Note Detail, Study
   Pack, the public note page, and DOCX `WITH_ANSWERS` export all stay **unredacted**; Quick Review gets a
   genuinely new quiz-less fetch, sized as its own short design pass, not dropped from scope; Challenge
   Quiz/Board Exam's post-completion answer review widens `ChallengeQuizSessionResponse` directly rather than
   adding a second round trip; Adaptive Practice's completion endpoint currently lets the client's submitted
   selections override server-stored ones for scoring, which also changes what `ConceptHealth` gets written
   from — fixed as part of this item, flagged for the pre-signoff falsification brief specifically. **⚠️
   Explicitly a practice-integrity fix, not a security boundary** for the 5 modes other than Interview
   Practice's already-live exploit — an account owner can still read their own note's answer key via the
   Note/Study Pack page; this closes the *accidental* exposure and the *resubmission* exploit, not
   account-owner self-access. Board Exam confirmed served by `ChallengeQuizService` (`MODE_BOARD_EXAM`), not
   `LongExamService`. Teacher share-link quiz path (`/quiz/[token]`) traced and confirmed **already safe**.
   Backlog Index row: "Quiz session wire payload already includes `correctIndex`/`correctIndices`/
   `explanation` for unanswered questions, across every shared quiz mode."
2. **Challenge Quiz `releaseClaims` deadlock risk.** The `REQUIRES_NEW` transaction can wait indefinitely
   on locks its own caller already holds (all three relevant timeouts are 0); dormant in production logs
   today, not actively firing. Needs a real two-connection Postgres integration test before any fix
   ships — a mocked-repository test would pass under the same defect by construction. Backlog Index row:
   "`ChallengeQuizQuestionBankService.releaseClaims`'s `REQUIRES_NEW` transaction can wait indefinitely on
   locks its own caller already holds."
3. **Challenge Quiz bank-invalidation race.** `generateMoreQuestions` can race the bank-invalidation path
   and let stale rows survive a regeneration. Needs a generation-stamp migration; no rewrite of existing
   rows. Backlog Index row: "`ChallengeQuizService.generateMoreQuestions` can race `v0.162.0`'s new bank
   invalidation, letting stale-content rows survive a regeneration."
4. **Challenge Quiz session-complete vs. bank-delete throw.** Lower-severity, same falsification pass
   that surfaced items 2-3, same shared `ChallengeQuizService`/bank code — bundled here rather than
   deferred, since items 2-4 all touch the same shared method. Backlog Index row: "A Challenge session
   completing at the exact moment a regeneration's bulk bank-delete commits could throw, not corrupt."
5. **Shared quiz-session-entity concurrency: unlocked writers can resurrect a completed session, and
   Quick Review's staleness anchor is wrong.** Found by the item 1 pre-signoff falsification pass (three
   cold Opus agents, one per surface), and by directly reading the shared entity's full writer set across
   all five services afterward — the three-way split let this fall between the agents, since none of them
   owned the shared entity itself. `QuickReviewSessionEntity` has no `@Version`/`@DynamicUpdate`; several
   writers per service (`updateSessionProgress`/`forfeitSession` on Challenge Quiz; `answerQuestion`/
   `completeSession` on Interview Practice; `completeSession`/`forfeitSession`/`saveConfidenceLevel` on
   Quick Review; `saveProgress`/`pauseSession`/`resumeSession`/`completeSession`/`forfeitSession` on Long
   Exam; `completeAdaptiveSession` on Adaptive Practice) read the row unlocked and later overwrite the
   *entire* row. A racing unlocked write that read before a completion commits and saves after silently
   reverts status to non-terminal — reproduced live on Postgres for Challenge Quiz by the falsification
   agent. Before this release that was cosmetic; after item 1's redaction, `/complete` is the one channel
   that reveals several modes' answer keys, so resurrecting a completed session is a real resubmit-with-
   the-revealed-key exploit, not just data corruption. Interview Practice's own resubmission fix (item 1)
   is separately incomplete: its per-index lock only stops a *sequential* resubmit of the *same* index; a
   race across *different* indexes lets the later-committing write erase the other index's stored
   critique, unlocking it for a real resubmit with the now-revealed answer. Separately, Quick Review's
   staleness check compares session creation time against `notes.generation_enqueued_at` (set at enqueue),
   but the quiz only changes at commit, and nothing blocks entry while the note is `GENERATING` — a
   session created in that window is never caught as stale, which can leak the new quiz's key on resume
   and let `completeSession`/`updateSessionProgress` (neither checks staleness at all) grant mastery from
   answers given on a different quiz. `study_packs.generation_stamp` cannot anchor this fix as-is: it
   advances on a summary-only admin repair that never touches the quiz (false stale) and does not advance
   on the malformed-quiz repair path that does change the quiz (false fresh). Fix direction, decided with
   the owner: extend the existing `PESSIMISTIC_WRITE`/`FOR UPDATE` pattern (already used by several other
   writers in each service) to every unlocked mutating writer above, rather than adding `@Version` —
   Challenge Quiz's `persistProgress` fires unserialized on every answer toggle with failures silently
   swallowed client-side, so a reject-based optimistic lock would need new retry/merge semantics on the
   highest-traffic path to avoid silently dropping legitimate overlapping writes; a wait-based lock closes
   the same defect with no new conflict-handling code, since each writer's *existing* status check
   correctly sees the fresh terminal state once unblocked. Interview Practice's fix does not hold a lock
   across the LLM call (would serialize all answers behind LLM latency): it re-reads locked only for the
   short merge-after-LLM step, matching the existing split-transaction precedent used elsewhere in this
   codebase for the same reason. Quick Review's staleness anchor becomes a dedicated `quiz_stamp` (distinct
   from `generation_stamp`), bumped only where `study_packs.quiz` is actually written in place, stored on
   the session at creation and compared by value (not timestamp) at `/answer`, `/progress`, `/complete`,
   and `findQuizMasteredAt`, with a defined fallback to the current timestamp check for sessions that
   predate the migration. Known Limitations NOT in this item's scope, carried forward: two concurrent
   recipient session starts can still 500 (insert-vs-insert, no row to lock yet); a lost `/progress`
   network write can still lose the active retry-question set on reload; Adaptive Practice's legacy
   concept-name fallback still trusts unfiltered client strings; and `AdminStudyPackTransactionHelper`'s
   admin-repair stamp race (documented in item 2-4's Known Limitations) is broader than originally
   described — it can revert a concurrent user regeneration's entire quiz and content, not just drop a
   stamp increment.

**A fifth candidate from the original kickoff survey — Study Plan Builder drag-persist race, unrelated to
item 5 above — was DROPPED at kickoff, not scoped in.** Its Backlog Index row ("Study Plan Builder drag persists per drop and races its own save")
had never carried a `Last reviewed` date; actually reading the current code at this kickoff (not just
grepping for the old, lost Codex prompt) showed the deferred "Save order" model it called for already
shipped in `v0.96.0` (`185e0cc7`, 2026-08-29) — `study-plan-builder-page-client.tsx`'s
`savePendingLeafOrder`/`persistLeafItems` implement exactly that model, and all three traps the row named
were addressed per that commit's own mutation-verified audit. The row is corrected to SHIPPED; see
`ROADMAP.md`.

Anti-drift: no automated Tier 3 question-quality gate this release; H4's validator, H5's wording, and
H6's exclusion (still gated on H5's post-ship checkpoint) are all unchanged; the bank-invalidation
migration (item 3) adds two generation-stamp columns only; the teacher share-link quiz path is a known,
explicitly out-of-scope gap for item 1, not silently ignored. Note/Study Pack pages, the public note page,
and DOCX `WITH_ANSWERS` export all stay unredacted by owner decision (item 1's own plan file). **⚠️ Quick
Review's fix does not add a quiz store**: the session continues to use the Study Pack's persisted quiz and
stores only locked selections plus navigation state in its existing `session_state` JSONB.

**Verification tier — the pre-signoff falsification pass this note called "still owed" has since run, in
three separate layers, and is now closed:**
1. **Three cold Opus agents, one per item-1 surface, no inherited context**, run against the merged
   diffs of PRs #1460-1464. This is what *found* item 5 (the shared-entity concurrency defect) — none of
   the three individually owned the shared `QuickReviewSessionEntity`, so the systemic gap fell between
   them; reading the entity's full writer set directly, afterward, is what actually surfaced it.
2. **A fourth, scoped Opus agent against item 5's own diff** (PR #1466's first commit) found four more
   issues in that delivery itself — most seriously, that Interview Practice's split-transaction fix did
   not actually isolate its two phases under this app's own `spring.jpa.open-in-view=true` — all fixed,
   each proven with a failing-first test (real Postgres or jsdom).
3. **A fifth, narrowly-scoped Opus agent against just the `AbortController`-based queue-ordering
   mechanism** added in step 2's fix, since it was the one piece no prior agent had reviewed — found the
   mechanism was itself asymmetric and could resolve a superseded caller's waiters too early. Fixed and
   proven failing-first; PR #1467.

Every `MockMvc` route test and `advisor()` checkpoint this note called for was applied across all three
layers. See RELEASES.md's Shipped section (items 1 and 5) for the full finding list and fixes.

### Shipped

- **Quiz-session concurrency and Quick Review quiz staleness (item 5):** Mutating writers across
  Challenge Quiz, Interview Practice, Quick Review, Long Exam, and Adaptive Practice now lock the
  session row before checking status and saving, so a writer that waits behind completion sees the
  terminal state. Interview answers validate in one short transaction, call the LLM with no
  database-transaction or row lock held, then lock and merge into a fresh session in a second
  transaction; this closes the cross-index critique-erasure/resubmit race left by item 1. **The split
  frees the transaction and row lock, not the pooled connection** — `spring.jpa.open-in-view` is ON here,
  so one connection is held for the whole HTTP request regardless of transaction boundaries (per
  `ConnectionLifetimeStartupLogger`'s own startup line); do not describe this as freeing a connection.
  Challenge Quiz coalesces client
  progress writes and awaits its final progress flush before leave. `V152` adds
  `study_packs.quiz_stamp BIGINT NOT NULL DEFAULT 0` and nullable
  `quick_review_sessions.quiz_stamp_at_creation`; quiz replacement bumps only the quiz stamp (inside
  `saveStudyPack` itself, guarded by `!isNewStudyPack`, so all five of its callers are covered, not only
  the async worker), and Quick Review compares the captured value on answer, progress, completion, and
  mastery lookup. Legacy sessions with a null capture retain the earlier enqueue-timestamp rule. Real
  PostgreSQL tests prove every newly locked writer waits behind a completion before reading terminal
  status, prove the split Interview lock boundary and cross-index merge, and cover Quick Review's stamp
  and legacy fallback. MockMvc requests pin both Adaptive completion aliases and the Quick Review
  no-share denial. **Audit corrections, found before commit:**
  - **The delivered Interview split did not actually split under production's own configuration.**
    `spring.jpa.open-in-view` is ON here (the Spring Boot default — `application.yaml`'s own startup
    logger confirms `open-in-view=ON` at boot), so one `EntityManager` spans the whole HTTP request; the
    two `TransactionOperations.execute()` calls each open their own database transaction but share that
    one `EntityManager`'s identity map, so phase B's "fresh" locked read handed back phase A's already-
    managed, now-stale Java object — the row lock was real, but it protected a write into stale memory,
    reopening the exact cross-index erasure this item exists to close. Confirmed with a new test that
    manually binds an `EntityManagerHolder` per worker thread (the same mechanism
    `OpenEntityManagerInViewInterceptor` uses), which failed against the delivered code and passes only
    after adding `entityManager.refresh(session)` immediately after phase B's locked read. The original
    two-thread test (no bound `EntityManagerHolder`) could not have caught this: absent open-in-view
    binding, each phase gets its own fresh `EntityManager`, so it never exercised the identity-map path
    at all.
  - **Challenge Quiz's `finalizeChallengeSession` did not await the last progress write before
    completing.** The server grades from stored `session_state`, not the client's request body — if the
    last answer's coalesced progress write was still queued when `/complete` fired, the server would
    score from a state missing that answer. `finalizeChallengeSession` now awaits the latest progress
    flush first, mirroring Long Exam's `await flushIdentificationAnswer()` before its own completion.
  - **The tab-hidden write regressed on real unload.** Coalescing made the `visibilitychange`-hidden
    write wait behind whatever was already in flight; on a real tab close or mobile background-kill the
    JS context can die before the queue ever drains, so the latest state might never be sent — before
    coalescing, that write fired immediately. Added a queue-bypassing `persistLatestProgressImmediately`
    used only by the tab-hidden path (send now, drop any stale queued entry for that session, and only
    resolve that dropped entry's own waiters once the immediate write itself settles — resolving them
    synchronously would let an awaiting caller like the completion flush above proceed before the
    immediate write is even sent, racing it for the row lock). The explicit Leave action keeps the
    original queue-respecting, awaited path deliberately — it already awaits the flush itself, so
    queueing costs nothing there, and an existing test pins that sequencing. **The
    `beforeunload`/route-change guard (`handleBeforeRouteLeave`) was deliberately left on the same
    queue-respecting path, unlike the tab-hidden handler, even though it is NOT awaited and the page can
    in principle close during it.** This is not verified to be safe by any test here — the assumption is
    that modern browsers also fire `visibilitychange`→hidden before or alongside an actual unload, so
    the tab-hidden bypass above already covers the real risk in practice. Known Limitation: a browser or
    OS path that triggers `beforeunload` without ever firing `visibilitychange`→hidden first would still
    queue behind an in-flight write with no bypass; not fixed here.
  - **A newly added staleness guard on `/progress` and `/complete` contradicted an existing, deliberate
    design decision.** `recheckMaterialAccess`'s empty-`Optional` case covers two paths, both
    deliberately tolerated by the existing code, neither a denial: an infrastructure fault reading the
    pack (its own comment: "completion has always tolerated a pack it cannot read"), and a pack that has
    genuinely been deleted (`findVisibleStudyPack`'s Javadoc: "a pack that... has been deleted has
    always succeeded — the caller owns the SESSION... denying it here would strand the learner's own
    session for a reason that has nothing to do with sharing"). A genuine access denial already throws
    through a separate path in the same method, so the added check could only ever fire on one of these
    two tolerated cases, and only ever wrongly. Removed; `isStale`'s existing
    `accessibleStudyPack.isPresent()` guard already does the right thing when the pack can't be read.
  - Added an in-flight guard on Quick Review's `initializeSession(force=true)`: three call sites
    (`/answer`, `/progress`, `/complete`) can each independently hit a stale-session 409 and each call
    `initializeSession(true)`, and without a guard that can fire more than one concurrent `startSession`
    — reaching this item's own already-accepted insert-vs-insert Known Limitation by a new path, not a
    new failure mode, but cheap to close off anyway.
  - **A second, independent falsification pass (a fresh Opus agent, no inherited context) found four
    more issues in the diff above, all fixed and each proven with a real Postgres or jsdom failing-first
    test:**
    - **The identical stale-read-after-lock pattern also exists in `ChallengeQuizService.
      resolveExistingChallengeSession`, pre-existing since July (`c76e5c1d`/`287f0069`), not introduced by
      this item.** It reads the session unlocked to capture `observedStatus`, then locks it and compares
      `lockedExisting.getStatus() != observedStatus` — the same object by Hibernate identity, so the
      comparison can never fire. Proven live on real Postgres (a session completed by one connection
      still read as `IN_PROGRESS` by a racing `startSession`). Fixed with the same
      `entityManager.refresh()` pattern as Interview Practice's fix above, proven with a new test that
      pauses between the two reads via an AOP interceptor and commits a status change from a second
      connection in that window.
    - **The coalescing queue had no per-request timeout, and nothing cleared it on
      `resetToPrestart`** — one hung progress write (a fetch has no default timeout) would have blocked
      every later progress write for the rest of the page's life, including a later session on the same
      page, and `handleLeaveSession`'s new await could hang indefinitely, contradicting this item's own
      "costs nothing there" claim above. Fixed: each queued write now carries an `AbortController` with
      a 30-second dead-request bound (generous on purpose — a legitimate `+5 Questions` LLM call can
      legitimately hold this row's lock that long), and `resetToPrestart` aborts and clears the queue.
    - **The tab-hidden immediate write and the queue were not ordered against each other** — an
      in-flight (or 401-refresh-retried) queued write carrying older state could still land on the
      server after the newer immediate write, overwriting it. `updateChallengeQuizSessionProgress` now
      accepts an optional `AbortSignal`. **This mechanism was itself corrected in a third round below —
      it was asymmetric and dropped waiters early; read that bullet for the actual current design.**
    - **A quiz-repair race could give two different quiz contents the same `quiz_stamp`.** Both
      `StudyPackService.saveStudyPack` and `AdminStudyPackTransactionHelper`'s malformed-quiz repair read
      the pack unlocked and incremented `quizStamp` in Java, so a repair racing a real user regeneration
      could compute the identical stamp value the regeneration just committed — defeating the invariant
      `V152` exists to protect. Fixed with a new atomic `StudyPackRepository.bumpQuizStamp` (`SET
      quiz_stamp = quiz_stamp + 1`, a targeted DB-level increment) replacing the Java-side
      read-increment-write in both places; neither caller's in-memory `quizStamp` field is set anymore,
      so nothing reads a value staler than what the atomic bump already applied.
  - **A third, narrowly-scoped falsification pass (a fresh Opus agent, no inherited context, targeted
    specifically at the abort/ordering mechanism above since it was the one piece no cold agent had yet
    reviewed) found the mechanism above was itself broken in two ways, both confirmed with a temporary
    probe test and both fixed here, each with its own failing-first test:**
    - **The queue never aborted the previous holder — only the immediate path did.** `drainProgressQueue`
      wrote its own controller into the shared ref without aborting whatever was there before it, so a
      tab-hidden write left running when a later queue item started became silently untracked (no longer
      abortable by anything).
    - **The "hold waiters until settle" fix only covered a write that was still merely QUEUED, not one
      already IN FLIGHT.** If the write a newer one superseded was the one currently being sent (e.g.
      `finalizeChallengeSession`'s own flush, sent immediately because the queue was idle when Submit was
      clicked), aborting it resolved its waiters right away — `/complete` could fire before the write that
      actually superseded it had landed, reopening the "server scores from stale state" defect on a
      narrower path.
    - **Fixed by redesigning the shared state as `progressLiveWriteRef` — one record of `{ controller,
      waiters }` for whichever write is currently live, queue-drained or immediate — and a shared
      `supersedeLiveWrite()` helper that both writers call before sending their own request: it aborts
      the current holder and returns its waiters, which the caller merges into its OWN waiters before
      taking over the ref.** Waiters therefore chain forward through any number of supersessions and
      resolve exactly once, when a write finally settles without itself being superseded again. The
      immediate path also gained the same 30-second dead-request timeout the queue already had, closing
      a case where a hung immediate write (with `handleLeaveSession`'s wait dropped into it) could stall
      forever.
    - **Known Limitation, not fixed here — inherent to a client-side-only ordering scheme:** an abort
      only stops the *client* from waiting; it cannot recall bytes a request already sent to the server,
      and the server has no sequence number to resolve two genuinely-simultaneous requests by anything
      other than which one's transaction takes the row lock first. Two writes that are BOTH actually
      in flight to the server at the same moment (not one queued behind the other) are still ordered by
      server arrival, not by which one the client considers "newer." The one case this fix does close
      completely is a write stuck mid-401-refresh-retry, since the abort signal is threaded through the
      retry (`fetchWithAuth`) and a fetch started with an already-aborted signal never sends at all.
      `/auth/refresh` itself is not abortable, and remains a narrow gap within that one case.

- **Challenge Quiz bank concurrency (items 2-4):** Real Spring-proxied, PostgreSQL 18 Testcontainers
  reproductions found three `releaseClaims` faults before the fix: `generateMoreQuestions` held a bank-row
  lock while its `REQUIRES_NEW` release waited for a second connection until PostgreSQL's test-only
  `lock_timeout` returned `55P03`; a failed start committed an unreleased claim on a `FAILED` session;
  and expired `+5` rolled back its forfeit but committed an independent claim release. `c76e5c1d`
  introduced `REQUIRES_NEW` on the mistaken premise that a rolled-back claim write needed a separate
  release. Release is now a count-tolerant bulk `UPDATE` in the caller's transaction: it sees a failed
  start's uncommitted claims, rolls back with a failed `+5`, and does not flush deleted entities at
  commit. The same reproductions pass without a lock wait, orphaned claim, or released claim on rollback.
  Read-only production checks on 2026-09-29 found zero claims owned by non-`IN_PROGRESS` sessions and zero
  idle-in-transaction connections: no live incident or cleanup write is owed.
- **Generation-stamp invalidation:** `V151` adds `study_packs.generation_stamp BIGINT NOT NULL DEFAULT 0`
  and nullable `challenge_quiz_question_bank.generation_stamp`. Both content replacement paths advance
  the pack stamp with the content write and bank delete in one transaction. Generation captures the
  stamp with the summary before the LLM call and passes it to every bank insert path. New-question
  claim and Redo Missed count/claim reads require the current stamp, accepting `NULL` only for bank
  rows that predate the migration; the owning session's release and outcome reads never stamp-filter.
  A two-connection LLM-window reproduction inserted five old-summary rows after invalidation: they
  survived physically but ceased to be claimable after the stamp fix. Existing rows are neither wiped
  nor made unclaimable at deploy. **Audit correction:** the delivered diff also stamp-filtered
  `existsByUserIdAndStudyPackId`, `findOwnerStudyPackPairsByStudyPackIdIn`, and
  `findQuestionKeysByUserIdAndStudyPackId` — these guard a WRITE (skip a re-seed, skip re-copying a
  key the caller already holds) rather than hand out content, so filtering them made a stale-but-present
  row invisible to the guard, letting the Official-template re-seed and adopter-copy paths reuse that
  row's `question_key` and collide with `uq_challenge_quiz_question_bank_user_pack_key` — the same
  rollback-only-transaction failure `docs/features/challenge-quiz.md` already documents for a same-level
  duplicate. Reverted the filter on those three; the two claim/content-serving read paths above are
  unaffected. The concurrency integration test's stale-stamp assertions for these three methods were
  corrected to match (they must still see the stale row, not treat it as absent). **This closes the
  template-copy and re-seed collision paths only — see Known Limitations for what it does not close.**
- **Known limitations (Challenge Quiz bank concurrency):**
  - The plain LLM-generation path (`ChallengeQuizService`'s shortfall call into
    `persistGeneratedQuestions`) still does not check the caller's existing bank keys — including a
    stale-stamped row's key — before inserting; it only dedupes within its own freshly generated batch.
    A stale row now sits unclaimable until the pack's next regeneration (instead of being claimed away
    quickly, as before this fix), so it occupies its key slot longer, raising the odds of hitting the
    same already-documented same-level-duplicate failure. Only `+5`/`generateMoreQuestions` and
    `seedTemplateAsync` can reach this collision: `startSession` holds the pack `FOR UPDATE` across its
    LLM call. A collision rolls back that request and surfaces as HTTP 500; it is not a silent drop.
    Not fixed here; would need the bank's existing
    keys threaded into the LLM dedup set the same way `copyTemplateQuestions` now does.
  - Unfiltering `existsByUserIdAndStudyPackId` trades one race for another: if `seedTemplateAsync` races
    the Official pack's own regeneration and leaves a stale-only template, `exists` now reports `true`
    forever (until that pack regenerates again), so the template is never re-seeded — adopters silently
    fall back to the LLM instead, and `queueBackfill` counts that pack as `skipped`. Accepted as a rare,
    self-resolving trade rather than fixed now.
  - `AdminStudyPackTransactionHelper.regenerateOnePack`'s stamp bump
    (`currentPack.setGenerationStamp(currentPack.getGenerationStamp() + 1)`) is a read-modify-write with
    no `@Version` and no `GENERATING`-status interlock — confirmed by reading
    `StudyPackGenerationContextResolver.assertGenerationReady`, which only checks the multi-program
    Domain Context rule, not note status. A concurrent user-initiated regeneration of the same pack can
    revert the concurrent user regeneration's entire quiz and content, including its stamp, rather
    than merely lose one increment. Pre-existing gap, not introduced by this release; not fixed here.
- **Completion/delete hypothesis resolved without a production fix:** The repository's owning-session
  read currently has `PESSIMISTIC_WRITE`. A first test harness substituted an unlocked query and
  produced the predicted stale-row throw, exposing an unfaithful fixture; the corrected real-query
  PostgreSQL test showed the delete waits for completion to commit. Completion succeeds, then the
  delete commits. The test pins this lock so removing it would fail the race guard.

- **Long Exam answer-key redaction:** `LongExamStartResponse` and `LongExamSessionResponse` now redact
  `correctIndex`, `correctIndices`, `explanation`, `workingSolution`, `acceptableAnswers`, and
  `acceptableAnswerGroups` from every question at every point in a Long Exam session. Responses retain
  `keyConcept` for the domain breakdown, while the stored session quiz remains complete for scoring. No frontend
  change or deploy ordering constraint applies: the existing frontend type already permits a null
  `correctIndex`, its resolver falls back to `-1`, and every Long Exam question component already renders with
  answer reveal disabled, so old and new frontend/backend combinations remain user-visible-behavior compatible.
- **Interview Practice answer-key redaction and answer lock:** `InterviewPracticeStartResponse.question` and
  `InterviewPracticeAnswerResponse.nextQuestion` now redact `correctIndex`, `correctIndices`, `explanation`,
  `workingSolution`, `acceptableAnswers`, and `acceptableAnswerGroups`; the natural-language critique remains
  the reveal for the question just answered. A question locks when its critique is first stored in
  `sessionState.aiFeedback`, rather than when its provisional selection is written: retrying the same choice
  returns the stored critique without another save or LLM call, while changing the choice returns HTTP 409 and
  leaves the first answer intact for a sequential same-index retry. Item 5 additionally closes the
  cross-index concurrent overwrite that could erase that critique. This also closes the unmetered, un-rate-limited critique loop: Interview
  Practice quota is charged once at session start, so repeated answers previously created unlimited additional
  LLM calls inside the same paid session.

  **Concurrency correction in item 5:** the earlier per-index guard stopped sequential same-index
  retries, but did not stop concurrent answers on different indexes from erasing one another. Two
  short locked transactions around the LLM call now recheck and merge fresh state. **Deploy order: either.** The frontend never reads the redacted answer fields and its
  normal flow always advances after a critique, so an old client does not submit a changed answer that the new
  409 guard would reject; old and new frontend/backend combinations remain compatible.
- **Challenge Quiz and Board Exam answer-key redaction:** active-session `ChallengeQuizStartResponse` payloads
  and Challenge Quiz `GenerateMoreChallengeQuizResponse.newQuestions` now redact `correctIndex`,
  `correctIndices`, `explanation`, `workingSolution`, and `acceptableAnswers`, and remove every accepted answer
  from `acceptableAnswerGroups` while preserving its outer length so Enumeration still renders the required
  number of inputs. `ChallengeQuizSessionResponse` now reveals the full unredacted `quiz` only at completion,
  together with `selectedChoices`, `selectedMultiChoices`, `selectedIdentificationAnswers`, and
  `selectedEnumerationAnswers`; both result branches use those completion fields for `QuizAnswerReview`.
  Stored session questions remain complete for scoring and completion review.

  **Deploy order: frontend and backend together.** An old frontend against the new backend would give
  `QuizAnswerReview` the redacted active-session quiz with answer reveal enabled and render every learner
  selection as incorrect, even though server scoring remains correct. The new frontend keeps a compatibility
  fallback to the old active-session fields for an older cached or pre-deploy completion response, so the
  reverse overlap is safe, but it does not make backend-first deployment safe. Run `scripts/check-deploys.sh`
  promptly after this release merges and confirm both Vercel and Render are on the release.
- **Adaptive Practice answer-key redaction, per-answer reveal, and server-owned scoring:** every
  `QuickReviewAdaptiveQuizResponse` now redacts `correctIndex`, `correctIndices`, `explanation`,
  `workingSolution`, and `acceptableAnswers` from unanswered questions and removes accepted-answer content
  from `acceptableAnswerGroups`; questions already answered through the new
  `POST /adaptive-practice/sessions/{sessionId}/answer` endpoint are revealed together with the persisted
  `selectedChoices`/`selectedMultiChoices` maps so a resumed session restores both position and feedback.
  The endpoint locks the session row and each question index on its first persisted selection: an identical
  retry is idempotent and performs no write, while a changed selection returns HTTP 409. MATCHING locks at
  the same per-item index granularity, and MULTI_SELECT uses an explicit Check Answer step so checkbox edits
  remain reversible until submission. The full stored quiz remains unchanged for scoring.

  **Production-data semantics change:** Adaptive Practice completion previously had no server-side selections
  and therefore used the client's `correctAnswers`, `selectedChoices`, and `selectedMultiChoices` as the only
  source for both the stored score and `ConceptHealth`. Completion now ignores those legacy request
  claims and derives both outputs from the server-persisted, locked selections for every session. The legacy
  fields remain accepted for request-shape compatibility; `correctConceptNames` is consulted only for an old
  edge-case row whose stored quiz is empty; a normal session with no stored selections scores zero and cannot
  claim correct concepts. Include this change explicitly in the pre-signoff falsification brief.

  **Deploy order: frontend and backend together; both overlap directions break.** An old frontend against the
  new backend never calls `/answer`, so no selection is ever persisted; completion then treats every question
  as unanswered and calls `recordIncorrectAnswers` for every concept in the quiz — an active miss on each
  concept, not merely a withheld credit, feeding weak-concept selection and `twiceMissedConcepts` for every
  learner who completes a session during the skew window. A new frontend against an old backend receives 404
  from `/answer` and cannot reveal or advance. Run `scripts/check-deploys.sh` promptly after merge and confirm
  both Vercel and Render are on the release.
- **Quick Review answer-key redaction for owners and share recipients:** the widened start/resume response is
  now the page's only pre-completion data source. It supplies current Note metadata and a Study Pack quiz whose
  unanswered items have `correctIndex`, `correctIndices`, `explanation`, `workingSolution`, and accepted-answer
  content redacted. `GET /notes/{id}` and `GET /study-packs/shared/{id}` remain fully unredacted and unchanged. The new
  `POST /quick-review/{sessionId}/answer` endpoint locks the session row, reveals one stored Study Pack question,
  treats the same selection in the same attempt as idempotent, and returns HTTP 409 for a changed selection.
  MATCHING keeps its whole-group reveal while recording each item independently; MULTI_SELECT remains editable
  until Submit. The cumulative selection maps retain the latest accepted answer for completion and mastery,
  while attempt-bucketed maps enforce separate INITIAL and RETRY locks.

  `/answer` also advances the stored `retryCount` to the client's requested attempt, bounded to `0` or `1`, so
  a lost best-effort retry-transition `/progress` write cannot leave a RETRY answer colliding with the INITIAL
  lock bucket. `/progress` now accepts only `retryQuestionIndexes` and `activeQuestionIndexes` from the client,
  preserves all four server-owned answer maps, and rejects retry-count or round regression. Sessions created
  before the Note's latest `generationEnqueuedAt` are stale: start forfeits and replaces them, resume treats them
  as absent, and `/answer` returns a distinct restartable error. The unscoped Note read happens only after the
  existing owner-or-live-share authorization, so the same staleness and title behavior applies to recipients.

  Removing the owner-only `getNote` preflight also fixes recipient entry through Dashboard-shaped
  `/notes/{id}/quick-review` links and due-concepts-digest links, in addition to the explicit shared-note link;
  `isOwner` from the authorized response now selects the valid Note-detail destination. Production currently has
  zero recipient Quick Review sessions and zero live `note_shares` rows, so these recipient paths ship before
  real usage has exercised the Dashboard in-progress reader, `ConceptHealth`, or mastery-unlock analytics for a
  recipient.

  **Deploy order: frontend and backend together.** A new frontend against an old backend gets 404 from
  `/answer`. An old frontend against the new backend never calls `/answer`, while narrowed `/progress` discards
  the four answer-bearing keys it still sends. With no stored selections,
  `computeConceptBreakdownForStoredSelections` returns an empty list; both `recordCorrectAnswers` and
  `recordIncorrectAnswers` short-circuit, `verifiedCorrectAnswers` stays unset, and `verifiedPerfect` is always
  false. This is a silent non-unlock rather than an active miss, but Quick Review's traffic makes it the
  largest-reach deploy-skew window among the six answer-key PRs. Run `scripts/check-deploys.sh` promptly after
  merge and confirm both frontend and backend are on the release.

## Archived releases

- `v0.162.0 — Say the Value` (Released) — archived to `docs/archive/RELEASES_ARCHIVE.md`.
- `v0.160.0 — Study Plans by Semester` (Released) — Degree Study Journeys Phase A0/A: academic-term placement on Subject Plans, Year-page term grouping, compact Subject cards, and the curriculum-pipeline `academic_term` column; moved at the `v0.166.0` kickoff.
- `v0.161.0 — Scannable Study Plans` (Released) — Degree Study Journeys Phase B: Study Plan Sections collapsed by default, an `Expand all`/`Collapse all` toggle, `Not started` wording at Section and plan grain; plus a pre-signoff pressure-test fix making `adoptGoal` adopt every published-but-private child instead of requiring `PUBLIC`. Moved at the `v0.167.0` kickoff.
