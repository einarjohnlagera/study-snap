# RELEASES.md - NoteLib

## v0.170.0 - Study Journey, Closed

**Status: Released**

Theme: close out the Study Journey Purpose / Learner Goal architecture — the last discovery item
(Public Note → Official Review Set) plus multi-goal exam support, both shipping on explicit owner
overrides of their own evidence gates rather than waiting for either to clear naturally. **Renamed
same-day from "Public Note → Official Review Set Discovery"** when the owner expanded scope to include
item 2 below; nothing had deployed yet.

### Planned Scope

1. **Public Note → Official Review Set link (RC-5, backend + frontend). SHIPPED — see below.** On the
   public note detail page (`/public/library/[subject]/[slug]`), when the note sits in a qualifying
   Official Review Set — top-level, a true source (not an adopted copy), owned by a `UserRole.ADMIN`
   account — show a callout linking to `/collections/published?highlight={id}`, which scrolls to and
   expands that set's existing `PublicStudyPlanCard`. Reuses the existing signed-out intent-cookie
   handoff (`discovery-intent.ts`), widened to accept this new return path. 3 new analytics events
   (`PUBLIC_NOTE_OFFICIAL_SET_LINK_CLICKED/_PREVIEWED/_ADOPT_CLICKED`). No new migration — the lookup
   uses the existing `idx_note_collection_items_note_id` index and runs inside
   `NoteService.mapToPublicDetail`'s existing read-only transaction.

   **⚠️ Kicked off ahead of the `v0.166.0` Hikari checkpoint clearing (owner override, 2026-10-13).**
   This item's own gate — stated at the `v0.169.0` kickoff — was two stability checkpoints:
   `v0.165.0`'s (due 2026-10-12, closed clean via PR #1489) and `v0.166.0`'s Hikari long-hold checkpoint
   (`[CHECKPOINT — due 2026-10-14T11:19:29Z, backstop 2026-10-21]`, `ROADMAP.md`'s Backlog Index), which
   was **not yet due** at kickoff — roughly a day out. Codex's implementation (audited and committed as
   `dadac05a` on `feat/v0.170.0-public-note-review-set-link` before kickoff) adds exactly one new
   public-note-page database lookup, which is the class of load the Hikari checkpoint exists to watch
   for. **⚠️ Not actually blocking by the time this release reaches `main`**: nothing in this change
   deploys until `releases/v0.170.0` merges, and the checkpoint's own due date (2026-10-14T11:19:29Z) is
   earlier than this release — a 2-item release — is likely to sign off. **Read the checkpoint at
   signoff, before the `main` merge, not just at this kickoff** — the override may turn out to have cost
   nothing in practice. This release owes its own `[CHECKPOINT]` row at signoff either way, reading the
   post-merge `Apparent connection leak` trace rate for any new attribution to this lookup, distinct from
   `v0.166.0`'s own two traced methods (`PublicProfileController`/`NoteCollectionController`).

2. **Multi-goal exam support (backend + frontend). SHIPPED — PR #1491, commit `04435dde` on
   `releases/v0.170.0`.** The owner's 5
   direction points, recorded verbatim at the `v0.169.0` kickoff (2026-10-10):

   1. **Storage — a normalized join table**, one row per (user, exam goal), with one goal flagged
      active per user. No redundant active-state storage. Existing single `studyGoal` values migrate
      losslessly; current users keep their existing goal, now active.
   2. **Dashboard & nudges — the active goal only** drives general dashboard nudges; other goals stay
      visible/accessible but don't compete for nudge real estate. **Exception:** post-session next
      steps must reflect the actual session/Study Journey context even when it differs from the active
      goal.
   3. **EXAM/SUBJECT exclusivity — unchanged.** Multiple exam goals may coexist with each other; a
      subject focus still can't coexist with any exam goal. Profile-preference rule only, never a
      Note/Study Plan access restriction.
   4. **Exam-hub signup intent — add and activate**, never silently replace other exam goals. Clear
      disclosure before the intent flow overwrites an existing subject focus. Signing up via an Exam
      Hub must NOT auto-adopt any Review Set.
   5. **Picker — a real goal-management surface** in Profile's Study Focus (add/remove/set-active).
      Removing a goal must never delete adopted content or assessment history.

   **Validated feasible against current code before any Codex prompt**, per the owner's own
   instruction recorded above: `docs/claude-plans/2026-10-12-multi-goal-exam-support-feasibility-validation.md`
   (Feature Planner, 2026-10-12). Technical verdict: every point is buildable, no blocking
   contradiction found. **4 follow-up decisions made 2026-10-13, at this scope expansion, resolving the
   validation document's §8 open questions:**
   - Adding an additional exam goal while one is active: the new goal becomes the **sole** active goal
     (deactivates the previous one) — reads point 1's "one goal flagged active" literally.
   - Removing the currently-active goal when other adopted goals exist: **auto-promotes** another
     adopted goal to active (mirrors `NoteCollectionService.reassertPrimaryInvariant`'s existing
     pattern for Study Plan adoption — same shape, not the same code, since that operates on
     `NoteCollectionEntity` not exam slugs). **Tie-breaker when more than one goal is eligible for
     promotion: the most recently added remaining goal, then lowest `id`.**
   - Disclosure before a silent goal-clearing write is required in **both** directions — not just
     "exam-intent flow overwrites a subject focus" (point 4's literal wording) but also "setting a
     subject focus clears every adopted exam goal" (the reverse, previously unaddressed). Reuses the
     existing `AppModal`/`ProfileTypeSwitchModal` confirm-modal pattern; no new UI primitive.
   - **Bundled into this same work, not deferred:** `GoalPromptBanner`'s confirmed-live defect (gates
     only on `studyGoal`, never checks `focusSubjects`, so a user with subject focus sees "no goal yet"
     and one click silently wipes their `focusSubjects` with zero disclosure — verified against
     production, 1 of 426 real users is in exactly this state today) and its separate dismissal-state
     bug (component-local, re-shows on reload). PR #1488 (which had backlogged this defect for a future
     release) is closed unmerged as superseded — fixing it now, not later.

   **Applied defaults, per the validation document's own recommendation (low-stakes, argued well
   there, not re-litigated here):** the 3 legacy free-text `studyGoal` rows are left untouched (adding
   any active exam goal still clears the legacy value, closing the exclusivity gap this would
   otherwise open); "SUBJECT focus" in point 4 means `focusSubjects`, not legacy free-text; a single
   shared "resolve active exam goal(s)" method is introduced, consolidating 4 call sites
   (`AuthService`, `ProgressReportService` ×3, `PostSessionNextStepService`) that currently each
   independently re-derive "is this an EXAM goal" — closing a drift risk `ExamGoalConfig.java:38-43`'s
   own comment already documents one prior incident of.

   **⚠️ Kicked off ahead of the `[CHECKPOINT — due 2026-12-07]` population gate clearing — explicit
   owner override, 2026-10-13.** That checkpoint's own stated trigger ("re-open only if the
   ≥2-distinct-goal population grows past a handful") has **not** been met: still 2 of 426 users,
   unchanged since 2026-10-09, re-confirmed 2026-10-12. This is a deliberate reversal of three prior
   owner decisions (2026-10-08, -09, -10) that each declined to build this on the same number. **This
   release owes its own `[CHECKPOINT]`** at signoff, tracking whether the population actually grows now
   that the capability exists — the original checkpoint measured whether to build it; this one measures
   whether building it mattered.

   **Verification note:** this item adds a data-mutating migration, changes API/export response
   shapes, and touches profile, dashboard, post-session, exam-hub, and GDPR-export code — clears the
   bar for at least one scoped cold falsification agent at signoff per the pressure-test gate, and is a
   candidate for the full 3-agent pressure test given the permission-adjacent (goal/content exclusivity)
   surface. **Decided at signoff:** one scoped cold agent (fresh context, `opus`, read-only, instructed
   to falsify 7 named claims), not the full 3-agent test — `git diff --name-only` on both PRs found no
   shared Java method between PR #1490 and PR #1491 (3 shared files, disjoint lines each), and the
   goal/content exclusivity surface is a profile-preference rule, never an access-control boundary (no
   cross-user read, no money/quota change), so the full-test trigger does not fire. Findings below.

   **Audited before commit** against the Codex prompt's full acceptance-criteria list: migration
   backfill replay test against seeded (non-empty) data; a real-Postgres `@SpringBootTest` +
   `@Testcontainers` test proving the partial unique index exists and the actual Hibernate flush order
   holds under add/remove/idempotent-re-add (not H2, which has no such index); `AccountPurgeService`
   deletes `user_exam_goals` rows rather than sentinel-reassigning (the table enforces per-user
   uniqueness); GDPR export (schema 1.3) includes every adopted goal, not just the active one; the
   pre-existing `PUT /users/profile/goal` endpoint kept its request/response contract unchanged for
   un-upgraded clients; `EXAM_GOAL_ADDED/ACTIVATED/REMOVED` confirmed firing from real user actions (not
   just enum membership), verified by a test asserting the call. One false-positive raised and reverted
   during audit: "CELE" in `progress-focus-guide.tsx` is the correct, pre-existing Civil Engineering
   exam acronym (`ExamGoalConfig.java`), not a typo. Backend: 2,672 tests/0 failures (incl. the
   Testcontainers native-query and real-Postgres flush-order harnesses). Frontend: 231/231 suites,
   2,642/2,643 tests (1 pre-existing unrelated skip), `tsc --noEmit` clean, `npm run lint` clean.

Anti-drift: no `ProfileType` change, no recommendation-engine change, no Degree Journey/Academic
Term/ConceptHealth change, no change to `resolvePlanPremiumExamMode`/`EXAM_MODES.md`, no change to
`UserEntity.examDate`'s existing single-valuedness (a known, separate, out-of-scope gap).

**Explicitly excluded, unaffected by either override above:** the Degree Journey catalog (Phase C) —
its own dependency (≥2 published Years) is a content-authoring gap no decision overrides; exactly one
BSCS Year exists, unpublished — and Phase D (learner curriculum customization), not yet scoped, deferred
pending real adopters. Neither is "Study Journey" work this release can close by deciding to.

### Shipped

- **Public Note → Official Review Set link** (PR #1490, commit `dadac05a`). Backend: new
  `NoteCollectionItemRepository.findOfficialReviewSetRootForNote` native query (admin-owned,
  top-level, true-source root; a PUBLIC root collection is never filtered on `published_at`, a child
  collection and its item both require one — matching `listPublic`/`getPublic` exactly), wired into
  `NoteService.mapToPublicDetail`; `PublicNoteDetailResponse` gains `officialReviewSetId`/
  `officialReviewSetTitle`; 3 new `AnalyticsEventType` values. Frontend: `PublicNoteOfficialSetLink`
  callout on the public note detail page linking to `/collections/published?highlight={id}`;
  `PublicStudyPlanCard` widened with a `discoverySource: "public_note"` branch and a
  `defaultPreviewOpen` prop; `PublishedPlansPageClient`'s "Browse All" grid reads `?highlight=` to
  scroll to and auto-expand the matching card; `discovery-intent.ts`'s signed-out return-path
  allowlist widened (`isDiscoveryReturnPath`, renamed from `isExplorePath`) to accept
  `/collections/published` paths, with an adversarial test (`/collections/published-evil`).
  Audited before commit: fixed a misplaced code comment and generic "Collection" copy corrected to
  "Review Set" (`getCollectionLabels(null)` default was wrong here — this is a fixed product concept,
  not a viewer-varying one). **Known limitation, non-blocking:** the "Recommended" grid on
  `/collections/published` (course/program-filtered, authenticated-only) doesn't receive the
  highlight/`defaultPreviewOpen` wiring that the "Browse All" grid does — acceptance criteria are
  still met since Browse All is always the unfiltered superset and always renders the target plan.
  Backend: 2,659 tests/0 failures (incl. the Testcontainers native-query Postgres harness, 46 queries
  now PREPAREd). Frontend: 230/230 suites, 2,634/2,635 tests (1 pre-existing unrelated skip).

- **Multi-goal exam support.** V155 moves valid scalar exam slugs
  into `user_exam_goals`, preserving legacy free-text values; Profile can add, remove, and activate
  goals, and Dashboard uses the active goal while post-session nudges follow the completed note's
  adopted-goal context. Subject focus and exam goals remain exclusive with confirmation before
  either direction clears existing preferences. The goal prompt now respects subject focus and
  persists generic dismissal. Account data export schema 1.3 includes every goal. **Frontend and backend must deploy
  together.** After V155 runs, rolling the backend back to v0.169.0 is **not safe for the 16 migrated
  users**: their scalar `studyGoal` is empty and that backend cannot read the retained rows in
  `user_exam_goals`. At signoff, add a post-ship population checkpoint to measure whether the
  ≥2-distinct-goal cohort grows beyond the 2-of-426 baseline; this release shipped ahead of that
  evidence gate by owner decision. Audited before commit against the Codex prompt's acceptance
  criteria: migration backfill replay test, real-Postgres flush-order test (partial unique index
  verified present), purge deletes rows (not sentinel-reassigned), GDPR export includes every
  adopted goal, `PUT /users/profile/goal` kept contract-compatible for un-upgraded clients, and new
  `EXAM_GOAL_*` analytics events confirmed firing (not just enum members) via frontend tests.
  Backend: 2,672 tests/0 failures (incl. the Testcontainers native-query Postgres harness).
  Frontend: 231/231 suites, 2,642/2,643 tests (1 pre-existing unrelated skip), `tsc --noEmit` clean,
  lint clean. **Known limitations, found by the pre-signoff falsification agent, none blocking:**
  (1) `PUT /users/profile/goal`'s `studyGoal: null` branch now also empties `focusSubjects`
  (`AuthService.java:605-621`) — a deliberate "full reset" per the Codex prompt, but a genuine
  behavior change from `v0.169.0` (null used to leave `focusSubjects` untouched); an un-upgraded
  client sending `null` during the brief deploy-rollover window sees this new behavior before its own
  UI catches up. (2) V155 does not special-case a legacy row holding both a valid exam-slug
  `studyGoal` and non-empty `focusSubjects` — confirmed via a read-only production query at signoff
  (2026-10-13) that 0 of 426 users are in that state today, so the gap is theoretical, not live; if it
  ever becomes non-zero, such a migrated row would hold an active goal and focus subjects
  simultaneously, violating the new exclusivity invariant. (3) The migration's SQL `trim()` is
  whitespace-only, narrower than `ExamGoalConfig`'s Java-side slug normalization — a value like
  `"ale\n"` would not migrate and falls back harmlessly to legacy scalar handling via the resolver.
  (4) The Official Review Set predicate (Item 1) does not exclude a `PENDING_DELETION` admin
  account's collection until the account is actually purged. (5) During the deploy rollover, an old
  backend instance can still write a stale scalar `studyGoal` for a user who already has an active
  `user_exam_goals` row; the resolver prefers the row, so the stale write is silently ignored rather
  than losing data, and the window closes once rollover completes.

## v0.169.0 - Study Journey, Continued

**Status: Released** (signed off 2026-10-12; commit `3200c773` on `releases/v0.169.0`)

Theme: continue the Study Journey Purpose / Learner Goal architecture by shipping the one piece
that's actually ready — after an `advisor()` pass caught that the original 3-item plan had
sequencing and decision gaps, and the owner then explicitly chose to close this release on that
one piece rather than fold in the others.

### Planned Scope

- **Civil Engineering Exam Hub page.** Adds a discovery page for CE alongside the four that already
  exist (ALE, PNLE, LET, CPALE) in `frontend/lib/exam-hub-config.ts`. **Owner override of Product
  UX's earlier "defer pending adoption evidence" call** — no new evidence arrived; this is a
  deliberate reversal, recorded as such. Codex prompt: `docs/codex-prompts/v0.169.0-ce-exam-hub.md`.
  Found during scoping, not assumed: 4 files (not just the config) hardcode the 4 existing slugs by
  name, including one (`exam-hubs-guide.tsx`) already independently stale (missing CPALE) before
  this task touched it — fixed as one move, deriving all of them from the shared config instead of
  a 3rd/4th/5th duplicate list. Production size checked directly: CE's 113 public notes sit between
  the smallest (ALE, 90) and largest (CPALE, 154) already-live hubs — no new bound needed.

### Multi-goal exam support — owner direction recorded 2026-10-10, explicitly NOT authorized to implement

The 5 batched questions got real answers, but **the owner was explicit these are product-direction
preferences, not technical authorization** — before any Codex prompt, Feature Planner is to validate
the storage migration, state transitions, onboarding semantics, cost, and acceptance criteria against
the current code. No multi-goal implementation prompt exists, and none should until that validation
and a separate go-ahead happen. Recorded here so the direction isn't re-litigated or lost before that
validation happens:

1. **Storage — a normalized join table**, one row per (user, exam goal), with one goal flagged
   active per user. No redundant active-state storage (e.g. not a separate "active goal" column
   that can drift from the join rows). Existing single `studyGoal` values migrate losslessly;
   current users keep their existing goal, now active.
2. **Dashboard & nudges — the active goal only** drives general dashboard nudges; other goals stay
   visible/accessible but don't compete for nudge real estate. **Exception:** post-session next
   steps must reflect the actual session/Study Journey context even when it differs from the active
   goal — a session on a non-active goal's material must not get nudged toward the active goal
   instead.
3. **EXAM/SUBJECT exclusivity — unchanged.** Multiple exam goals may coexist with each other; a
   subject focus still can't coexist with any exam goal. This is a profile-preference rule only —
   it has never restricted which Notes/Study Plans are reachable, and broadening subject/exam
   coexistence needs its own separate decision, not an inference from this one.
4. **Exam-hub signup intent — add and activate**, never silently replace other exam goals. For a
   learner who currently has subject focus (not an exam goal), the existing exclusivity contract
   still requires clear disclosure before the intent flow overwrites it. Signing up via an Exam Hub
   must NOT auto-adopt any Review Set — goal-setting and content-adoption stay separate actions.
5. **Picker — a real goal-management surface** in Profile's Study Focus (add/remove/set-active),
   not a single-select-to-multi-select conversion with no active-goal concept. Removing a goal must
   never delete adopted content or assessment history — it only changes which goal drives nudges.

**Release boundary:** multi-goal stays out of `v0.169.0` entirely. It remains additionally gated on
the `[CHECKPOINT — due 2026-12-07]` population read (still 2 users as of 2026-10-09) and
prioritization against the product-health release plan — neither is resolved by this direction
record.

### Deferred out of this release (sequencing, not scope changes)

- **Public Note → Study Journey discovery — moved to `v0.170.0`.** Two problems caught by
  `advisor()`: (1) it already has a design — Feature Planner's RC-5 in
  `docs/claude-plans/2026-10-09-product-health-release-plan.md` §3 ("Part of Official Review Set
  X") — so this release does not need to design it from scratch, only reuse that spec; (2) RC-5's
  own stated gate is the two active stability checkpoints (`v0.165.0`'s discovery-rail fix, due
  2026-10-12, and `v0.166.0`'s Hikari long-hold fix, due 2026-10-14) — a new per-request lookup on
  public Note pages, shipped before those read out, makes both checkpoints' kill criteria
  uninterpretable (the same contamination pattern already recorded for Learning Connections).
  Sequencing this after 10-14 roughly lines up with the Claude budget reset on 10-12 anyway.

Anti-drift carried over from the original discovery brief, still binding for everything in this
release: no `ProfileType` change, no recommendation-engine change beyond the explicitly-decided
Public Note link, no Degree Journey / Academic Term / ConceptHealth change, no change to
`resolvePlanPremiumExamMode`/`EXAM_MODES.md`.

**Explicitly excluded — the one piece NOT in this release:** the Degree Journey catalog (Phase C).
Re-checked 2026-10-09: its own stated dependency ("≥2 published Years") is unmet — exactly one BSCS
Year exists and it's unpublished. This is a content-authoring gap, not a code gap, and no decision
override removes it. A separate, unmerged curriculum-authoring branch
(`docs/bscs-year1-curriculum-shaping`) exists with planning material for completing that one Year,
but it predates `v0.167.0`/`v0.168.0` (merging it as-is would roll both back) and even once rebased
and authored, completes only the existing Year — not a second one. Tracked separately; not part of
this release.

**Delivery:** all three items are new backend/multi-system work — Codex-routed per `CLAUDE.md`'s
task-routing table, one prompt per item. **Budget note:** the owner's Claude usage resets 2026-10-12;
implementation is deliberately routed through Codex rather than done inline to conserve it, with
Claude Code's role limited to scoping, prompt-writing, and post-delivery audit.

**Verification tier:** three separate Codex deliveries folding into one release raises verification
cost per `CLAUDE.md`'s own size-vs-cost rule. None of the three trigger the full three-agent tier
(no money/quota/permission-substrate change, no first-of-kind cross-user read). Default to one
`advisor()` pass per item after its audit, escalating to one scoped cold agent only if a trigger
fires during implementation (e.g. two items touching the same shared method, or a defect found and
fixed in the same session).

### Shipped

- **Civil Engineering Exam Hub (CELE) — PR #1485, commit `3200c773`.** Added `/exam/ce` and all 5
  consuming surfaces (index page, Help Center guide and card, server-side course-program resolution).
  Fixed `EXAM_HUB_ICONS` duplication along the way — moved into `exam-hub-config.ts` as the single
  source of truth, which also fixed `exam-hubs-guide.tsx`'s pre-existing CPALE omission. Implemented
  via Codex, independently re-verified (tests, `tsc --noEmit`, `npm run build` all re-run fresh, not
  trusted from the delivery report alone).

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

## Archived releases

- `v0.163.0 — No Peeking` (Released) — archived to `docs/archive/RELEASES_ARCHIVE.md`.
- `v0.162.0 — Say the Value` (Released) — archived to `docs/archive/RELEASES_ARCHIVE.md`.
- `v0.160.0 — Study Plans by Semester` (Released) — Degree Study Journeys Phase A0/A: academic-term placement on Subject Plans, Year-page term grouping, compact Subject cards, and the curriculum-pipeline `academic_term` column; moved at the `v0.166.0` kickoff.
- `v0.161.0 — Scannable Study Plans` (Released) — Degree Study Journeys Phase B: Study Plan Sections collapsed by default, an `Expand all`/`Collapse all` toggle, `Not started` wording at Section and plan grain; plus a pre-signoff pressure-test fix making `adoptGoal` adopt every published-but-private child instead of requiring `PUBLIC`. Moved at the `v0.167.0` kickoff.
- `v0.164.0 — Computing` (Released) — ratified `Computing` as a 13th Domain Context (ADR-001 amendment), shipped the enum/frontend value, swept the context/spec docs; carries a `[CHECKPOINT — due when BSCS Year 1 Computing authoring reaches 10+ classified notes, backstop 2026-12-29]`. Moved at the `v0.170.0` kickoff.
