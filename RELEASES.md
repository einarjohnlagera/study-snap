# RELEASES.md - NoteLib

## v0.160.0 - Study Plans by Semester

**Status: In Progress**

Theme: let a curator place each Subject Plan in an academic term, so a Year reads as a semester-by-semester study
plan, without adding a level to the collection hierarchy and without touching any Note.

### Planned Scope

**Scope picked and release shape confirmed by the owner, 2026-09-26: Degree Study Journeys, Phase A0 and Phase A.**
Source: `docs/claude-plans/degree-study-journeys-stage1-architecture-audit.md` (decision-complete feature plan;
**§21 is the implementor handoff, §18 the phases and decisions A-F, §22 confirms no owner decisions remain**).
The learner-facing promise is **"BS Computer Science - 1st Year Study Plan"**, never a complete Degree Study Journey.

- **Phase A0 (documentation first, Claude-direct):** `docs/architecture/ADR-003-curriculum-placement-and-hierarchy-depth.md`
  (decisions A-F exactly as enumerated in plan §18; ADR-002 is taken by the quiz-answer-identity proposal), plus the
  `docs/features/collections.md` update. No application behaviour.
- **Phase A (implementation, Codex in slices: backend, then frontend, then pipeline):**
  1. Two nullable columns on `note_collections`, `term_label VARCHAR(60)` and `term_order SMALLINT`; one additive
     migration, no backfill, no index.
  2. Persistence, DTO, service and **adoption preservation** of the term.
  3. Curator term assignment in the Year builder: a combobox over terms already used in that Year, never raw freetext.
  4. Year-page conditional term grouping (all-NULL flat / all-placed grouped / mixed with a trailing `Term not specified`).
  5. Compact Subject cards on the Year page (title, note count, ONE progress signal), gated on the SAME condition as
     term grouping (any non-null `term_label`), no count threshold.
  6. `academic_term` in the curriculum pipeline: extend `review-set-workbook-spec.md` and `build_review_set_workbook.py`
     and regenerate; never hand-add the column to a generated workbook.
  7. The regression and invariant tests in plan §21.5.
- **Endpoint form (decided here, plan §21.9):** extend the existing collection update with two OPTIONAL fields. That is
  additive in both directions (optional on request, nullable on response), so frontend and backend may deploy in either
  order, and **the release notes must say so explicitly.** If a new endpoint is added instead, that stops being true and
  the release owes a deploy-ordering statement and a real-request `MockMvc` test with `.contentType(MediaType.APPLICATION_JSON)`.
- **Backend Academic Term slice:** migration `V150` adds nullable `term_label` / `term_order`; the existing collection
  PATCH accepts optional `termLabel` / `termOrder`; `persistAdoptedPlan`, `createSubjectAddition`, and the `adoptGoal()`
  re-parent branch carry child placement; and the Goal-child plus owned/public detail DTOs expose it. The PATCH fields
  are optional on request and nullable on response, so frontend and backend may deploy in either order.
- **Frontend Academic Term slice:** `lib/collection-terms.ts` holds the single `hasTermPlacement` gate that drives BOTH
  Year-page term grouping and compact Subject cards (no count threshold); the Year page renders ordered static term
  headers with a subject count and an in-progress count (shown only when above zero), a trailing `Term not specified`
  group in the mixed case, and compact cards (title, note count, ONE of `N% ready` / `Not started`); with every child
  term NULL the existing full-size grid is byte-for-byte unchanged. The Year builder gains a per-Subject term combobox
  over the Year's existing terms (a new label is allowed; the order is assigned, never typed). The PATCH fields it sends
  are optional on request, so this slice also deploys in either order relative to the backend.
- **Pipeline Academic Term slice:** `build_review_set_workbook.py` accepts an OPTIONAL `academic_term` column, constant
  per plan, validated per Study Plan: unused for all Subject Plans or assigned to all of them, and a partial
  assignment is refused with an error naming the Study Plan and the unassigned Subject Plans (also refused: mixed
  values inside one plan, over 60 characters, `Term not specified`, and case/spacing-variant duplicates). The term
  order is derived from first-seen file order and printed in the workbook. With no terms the output is unchanged:
  ALE, CPALE, LET and PNLE were rebuilt with the old and new builder and compared on cell values, fonts, fills, borders, merges, column widths, row heights and freeze panes: identical. The new `docs/curriculum/test_build_review_set_workbook.py` runs by hand in the venv and is NOT in CI.
  `docs/curriculum/review-set-workbook-spec.md` and the strategist module `docs/gpt-contexts/REVIEW_SET_SHAPING_CONTEXT.md`
  now carry the column, and the strategist module's TSV header was corrected to include `applicable_programs`, which the
  builder has required since 2026-09-10 (a contract drift, not a behaviour change).
- **Phase B (collapsed-by-default Sections, and so on) is NOT in this release**; it has no dependency on Phase A and rides
  in a later one. **Phase C (a Degree entity and landing page) is out.**
- **⚠️ A gap in the plan, found and verified in code at kickoff (and since corrected in the plan, §7.1a), that the
  implementation MUST close:** the plan's adoption invariant named only the adoption path, but a child Subject Plan copy
  is built field by field in TWO places. `persistAdoptedPlan` (`NoteCollectionService.java:1956-2017`) serves BOTH `adopt()`
  and `adoptGoal()`, because `adoptGoal` creates each child by calling `adopt()` and then re-parents it. The SECOND is the
  Official-update addition, `createSubjectAddition` (`:2476-2515`, `CreatedSubjectAddition`), which copies title,
  description, course program, learner level, estimated hours and the source-sync fields and, without the term, lands a
  Subject added to an already-adopted Year with a NULL term next to siblings that have terms. That manufactures the mixed
  state the plan calls a curator-quality defect and shows a `Term not specified` group with no curator involved. The term
  must be carried at BOTH, each with its own test that fails when it is dropped. **`persistAdoptedGoal` (`:2019-2057`,
  the root copy) must NOT get the term**: a root has no parent and therefore no term placement, so adding it there would be
  a silent dead column, not a fix.

Anti-drift (plan §21.2 and §21.4, binding): with every child term NULL the existing Review Set and Goal rendering is
UNCHANGED, with no term headers, no `Term not specified` group, and FULL-SIZE cards, protecting five live Review Sets
(PNLE, CPALE, ALE, LET, Civil Engineering); density is gated on the same condition as grouping and never on a count;
Official update stays additive-only forever; NO Degree progress in any phase (a permanent product rule); academic
placement never touches the Note and `applicable_programs` is never overloaded to carry a term (ADR-001); the hierarchy
stays at exactly two persisted levels; no Degree landing page and no `journey_key`/`journey_order` fallback, no Degree
entity, no whole-Degree adoption, no learner curriculum customization, no term entity/catalog/enum, no collection
`type`/`kind` enum, no change to `ConceptHealth`. Subject Plans are NOT reusable across Degree Journeys; only canonical
Notes are. **Verification:** `advisor()` BEFORE the Codex prompt is written and on each diff; a diff that changes
behaviour must touch a test that runs it; mutation-check every new test and name the killer; the all-NULL regression must
assert full-size cards; frontend `tsc --noEmit`, lint and tests plus the full backend build with Docker; and, because the
release touches the adoption engine and five live Review Sets, ONE scoped Opus cold agent framed as falsification of
invariants 1, 2 (every copy site), 3 and 4 before signoff. Seven items is a large release; say what that does to the
verification tier if more is folded in. **Owner-side, not this release's work:** the Note Strategist keeps authoring
Subject to Section to Note; Year and term placement stays in a separate editorial file until the pipeline extension ships.

### Shipped

_(nothing yet)_

## v0.159.0 - Nothing Lost in the Batch

**Status: Released** (signed off 2026-09-25; Release A merged as #1444; release PR merged as #1446 and tagged; deployed and verified: Render live 15:24Z, Vercel production 15:28Z)

Theme: stop batch operations losing their result. A bulk generation that fails topics leaves no trace once its
consume-once receipt is read or swept, and a bulk regeneration finishes with no signal at all. Also close the one
evidence question this project still owes an answer on: what the 5 s connection timeout is doing to users.

### Planned Scope

**Scope picked by the owner at kickoff (2026-09-25): Notifications Release A, and the `connection-timeout` follow-up.**
Source for item 1: `docs/claude-plans/learning-relevant-notifications-stage1-plan.md` (audit and plan, written
2026-09-24, NOT yet owner-approved to build; §12 is the release slice, §14 the decisions). **Every production
figure in that plan is a 2026-09-24 snapshot and one had already decayed; re-read before any of it reaches a
prompt.**

0. **PREREQUISITE, OWNER DECISION, NOTHING IS BUILT UNTIL IT IS MADE: the badge/retention flag split.**
   `NotificationCategory` (`entity/NotificationCategory.java`) derives badge eligibility and retention expiry from
   ONE boolean as exact complements, so a completion notification cannot be both badge-eligible and
   retention-expirable. The plan recommends option (c): split into `badgeEligible` and `retentionExpirable`, add
   `ASYNC_RESULT(true, true)`, and REWRITE (not delete) the two XOR partition tests. Java-only, no migration; it
   deliberately changes a documented invariant, which is why it is the owner's call.
1. **Notifications Release A: async completion for BULK operations only (backend, no migration, no API/DTO/frontend
   change).** Two new `NotificationType` values (`BULK_GENERATION_INCOMPLETE`, `BULK_REGENERATION_COMPLETE`), one
   new `NotificationCategory` (`ASYNC_RESULT`), one producer service, two call sites:
   `NoteBulkGenerationService.java:247-274` inside the existing `finally`, after the `recordResult` block, only
   when something failed; `NoteBulkRegenerationService.java:439-443`, deliberately NOT in a `finally`. Destination
   `/library`; dedup on `resultId` / `batchId`. ~9-11 files; routed to **Codex** (write the prompt from the plan,
   with `advisor()` BEFORE it is written and on the diff, then `/audit-diff`). Still-open plan decisions
   (§14): ship both triggers or one (recommend both), the failure-copy truncation budget (recommend ~850 chars then
   "and N more"), and whether a zero-accepted or all-quota-blocked batch delivers nothing or the failure form.
2. **`connection-timeout: 5000` follow-up.** (a) A proper read-only 500-cause read: classify the 500s by cause
   (pool timeout vs database I/O drop vs other) over the retained window, since the 2026-09-24 read sampled only
   the newest 30 log lines. (b) The owner's verdict against the row's kill criterion, which has no numeric
   "material and sustained" threshold, so the owner sets it. (c) Any resulting change (revert to 30 s, or a
   structural fix on pool holds) is its own owner-scoped item, never an inline fix.
3. **One doc correction, found by the plan's audit and verified in code:** `CLAUDE.md` names
   `NoteService.startAsyncGenerationFromNote()`, which does not exist; the real entry point is
   `StudyPackService.startAsyncGenerationFromNote` (`StudyPackService.java:171`).

Anti-drift: no single-note notification of any kind (the learner is on a page polling every 3 s); NO "Study Packs
are ready" notification for bulk generation (the count it would use over-reports, plan §1.1); no per-item
notifications, presence, websocket or SSE; no retry promise in the copy (the receipt is consume-once); never state a
reconciled "N of M" count; never add a `finally` to `NoteBulkRegenerationService.processBatch` (`:64-65` forbids
it); never call `deliver()` inside a transaction; no migration, endpoint, DTO field or `notification-inbox.tsx`
change; Release B (learning continuity) is DEFERRED, not scheduled, and `RetentionEmailType.UNFINISHED_NOTE` stays
untouched. No retention Stage 3; do not cap or reorder `INACTIVITY` here (its effectiveness and the budget-starvation
rows are separate owner decisions). The notes and curriculum files other sessions left untracked are not this
release's. **Verification:** a diff that changes behaviour must touch a test that runs it, so both call sites need
a test that executes them; mutation-check every new test; one `advisor()` on the diff (no permission, money or
production-data semantics change), escalating to one scoped falsification agent only if delivery introduces a defect
the same session then fixes.

### Scope disposition (signoff, 2026-09-25)

- **Prerequisite decision (badge/retention flag split): DECIDED and SHIPPED**, option (c).
- **Notifications Release A: SHIPPED** (#1444). Both triggers; 850-character topic budget; an all-quota-blocked batch
  delivers the quota form. Anchors: `NoteBulkGenerationService.java:296`, `NoteBulkRegenerationService.java:449`,
  `NotificationCategory.java:14-16`, `BulkOperationNotificationService.java`.
- **`connection-timeout` follow-up: PARTLY DONE.** (a) the 500-cause read is DONE, recorded below and on its Backlog
  row; (b) the owner's VERDICT is NOT made and carries forward on the row; (c) nothing was changed, by design.
- **`CLAUDE.md` entry-point correction: SHIPPED** in the kickoff commit (`StudyPackService.java:171`).
- **Not from the scope list, left open by the owner's call:** the `[CHECKPOINT — due 2026-09-27]` click/open read and the
  2026-09-28 publication-boundary read.

### Checkpoint gate

Release A shipped ahead of its own evidence (one user drove regeneration; bulk generation volume had no direct metric),
so it owes a checkpoint, added in this signoff commit: `[CHECKPOINT — due deploy + 30 days, backstop 2026-11-10]`
with a kill criterion, a denominator clause, and the `notifications` table as the instrument. The instrument is the
table, and the first production row is what proves it emits; both call sites are exercised by mutation-checked tests.

### Known limitations

- The regeneration call site has no try/catch of its own; the producer swallows every delivery failure and a test
  (mutant M10) guards that, so an escape could only come from a future change to the producer.
- The notification copy (exact titles and bodies) was drafted by the release and not separately reviewed by the owner.
- The 500-cause read is a subagent's report, not independently re-run; application logs only go back to
  2026-09-18 05:40Z, so ~350 of 439 500s (2026-09-04..09-18) cannot be attributed, and its log event count (68) exceeds
  the metric 500 count (62) by ~5 unexplained.
- The bulk generation RECEIPT still marks every accepted topic failed after an interruption (the outer catch), including
  notes already created; only the notification was corrected. Whether the row actually persists during a real shutdown
  was not verified.
- Verification: `advisor()` before the Codex prompt and on the diff, mutation checks (21 killed), and one Opus cold
  agent as a scoped falsification pass. No authorization, money or production-data semantics changed, so no full
  three-agent test was warranted.

### Shipped

- **Bulk-operation in-app results.** Notifications now keep badge eligibility and unread expiry as
  independent category policies. Failed or capacity-blocked bulk generation records its topic strings in
  one bounded notification and stays silent on success; normally completed bulk regeneration sends one
  completion notification, while an interrupted run sends none. Unit, call-path, badge/retention, and
  real-database length guards exercise these claims.
  Copy is fixed and exact (`docs/features/notifications.md`); bodies are bounded to an 850-character topic
  budget in code, and the regeneration retry mints its own batch id so it notifies too. The pre-commit audit
  found and fixed a contradiction in `notifications.md` (it still said every unread actionable row is retained
  forever, which is false for `ASYNC_RESULT`) and a test gap (nothing pinned the dedup id of either trigger).
  13 of 13 planted mutants were killed at first, each by a named test. **⚠️ That figure overstated the guard:** the
  pressure test below found five mutants the merged suite did not kill (regeneration count arguments, separator
  budget accounting, mixed-case suffix, one-per-group interleave); all are killed now (21 in total).
- **Pre-signoff pressure test (one Opus cold agent, isolated worktree, framed as falsification) and its fixes, PR #1445.**
  It held nine claims and broke three, plus test overstatement and doc defects; each was verified in code before it
  was fixed. **(1)** An interrupted or failed-before-loop bulk generation notified that EVERY accepted topic failed,
  including notes already created: the delay between items throws outside the per-item try and the outer catch
  overwrites the lists. The notification now lists only topics that were NOT created (the receipt keeps the older
  behaviour, see Known limitations). **(2)** In the mixed failed and quota-blocked case "and N more" attached to the
  quota list although the omitted topics could all be failed ones; it is now `Plus N more not listed.` after both
  sentences. **(3)** Regeneration copy claimed Study Packs were "unchanged" although a timed-out item may still succeed;
  it now says they still work. **(4)** Five mutants survived the merged suite; new tests kill them. **(5)** Doc defects:
  a self-contradicting ROADMAP row, a checkpoint SQL that omitted dismissals from its own kill criterion, and a
  rationale that ignored the polling regenerate modal. The full build passed (2,527 tests) and all 21 mutants are killed.
- **`connection-timeout: 5000` 500-cause read (read-only, no code change).** Application logs are retained only from
  2026-09-18 05:40Z. In the observable week: ONE real saturation cluster (09-18 14:46-14:48, 34 requests, pool 20/20,
  peak waiting 6); pool timeouts on 09-18 16:03 and 09-22 06:04 that followed database I/O drops with a collapsed pool;
  26 database I/O drops in bursts on the first requests after a deploy goes live; 33 client-abort broken pipes not counted
  as 500s; one 405 logged as a 500; one unknown. The 5 s timeout produced 500s in one incident; most other 500s are
  deploy-time DB drops it does not cause. The verdict remains the owner's; a deploy-time-burst finding has its own row.

## v0.158.0 - Reading the Evidence

**Status: Released** (signed off 2026-09-25; merged as #1443 and tagged; deployed and verified: Render live 06:30Z, Vercel production 06:33Z)

Theme: discharge the evidence reads this project already owes (three overdue checkpoint reads and the first
readings of the retention instrumentation) before any new feature scope is chosen, plus one ready one-line fix.

### Planned Scope

**PROVISIONAL: this release was kicked off without an owner scope pick. Amend this list before any
implementation.**

- **Three overdue checkpoint reads (read-only).** Each row's own kill criterion stays authoritative, and a fired
  criterion becomes its own owner-scoped item rather than being fixed inline.
  - `[CHECKPOINT — due 2026-09-22]` publication boundary (`docs/claude-plans/v0.132.0-publication-boundary-checkpoint-read.sql`):
    if it shows stranded curriculum, the response is to build F5, a publication surface in the Builder.
  - `[CHECKPOINT — due 2026-09-18]` `connection-timeout: 5000`: read Render `http_request_count` by `statusCode`
    for the 14 days after deploy against the pre-deploy window; if 5xx is material and sustained, revert to the 30 s default.
  - `[CHECKPOINT — due 2026-09-19]` Learning Connections demand: `linked_learner_relationships` grouped by status.
- **Retention instrumentation readings (`v0.157.0` follow-through, read-only).** (a) The first daily run after
  deploy: `retention.email.*.dispatch` logs show digest, then weak-concept, then `INACTIVITY`, with `INACTIVITY`
  near 40-47. (b) `[CHECKPOINT — due 2026-09-27]`: click/open tracking is emitting. (c) Re-date the retention
  checkpoint rows if the real deploy date matters. Owner prerequisite: Resend click and open tracking and the
  `email.clicked`/`email.opened` webhook events.
- **`RetentionEmailScheduler.runMonthly()` zone pin (backend, one line plus a test) — DONE and MERGED into this branch (PR #1442, `540b866b`); full backend build green, 2,511 tests.** Pin it to `Asia/Manila`
  like `runDaily`/`runWeekly`; the Backlog Index row has the detail. Routing: Claude-direct on its own branch and
  PR into this release branch (isolated, one file).
- **`INACTIVITY` email effectiveness: OWNER DECISION PENDING (evidence read, added 2026-09-24 at the owner's
  request; no implementation).** A read-only production read found 4,466 `INACTIVITY` sends to 235 users
  (2026-04-22 to 2026-09-23), 222 of them sent 10 or more, max 44, while only 6 of 408 accounts logged in during
  the last 7 days. Return rate after a send (any `analytics_events` row within 7 days, sends at least 7 days old):
  1st send 4.7%, 2nd 2.2%, 3rd-5th 1.3%, 6th-10th 1.0%, 11th+ 0.7%; 20 of 235 emailed users have any recorded event
  after their first send. **Limits: no control group (some return unprompted, so lift is lower than shown), "return"
  is any analytics event and may miss a plain login, and `marketing_emails_enabled` is on for 0 users with the
  consent basis of these sends unchecked.** This is evidence against the rationale for "do not cap `INACTIVITY`'s
  share (gated on opt-in growth)"; the rule itself is the owner's and is unchanged until the owner decides. Options
  to scope if wanted: cap sends per user, stop after N unanswered emails, or check the consent basis first. Any
  change is its own owner-scoped item, not an inline fix.

Anti-drift: no retention Stage 3 (it waits on the three retention `[CHECKPOINT]` rows); do not cap `INACTIVITY`'s
share (gated on opt-in growth); do not reorder retention dispatch (`docs/features/retention-emails.md`); nothing
here is feature scope. Choose feature scope explicitly.

### Scope disposition (signoff, 2026-09-25)

- **Three overdue checkpoint reads: SHIPPED as reads**, results above and on each Backlog row. Publication
  boundary not fired (re-dated to 2026-09-28); Learning Connections kill criterion does not fire; `connection-timeout`
  inconclusive, owner decision.
- **Retention readings (a) first daily run: SHIPPED**, as designed. **(b) `[CHECKPOINT — due 2026-09-27]`: NOT DONE,
  by the owner's call** — it is read on or after 2026-09-27 and stays an open Backlog row. **(c) re-date the retention
  rows: NOT NEEDED**, the real deploy was 2026-09-24 as assumed.
- **`runMonthly()` zone pin: SHIPPED** (#1442, `RetentionEmailScheduler.java:79`, guard `ScheduledJobCronContractTest`).
- **Added mid-release, owner-requested:** the `INACTIVITY` effectiveness evidence item and indexing the notifications
  Stage 1 plan; both have Backlog rows and are undecided.

### Checkpoint gate

Nothing in this release shipped ahead of its own evidence, so no new `[CHECKPOINT]` row is owed. The open
checkpoints are all carried from earlier releases and are re-stated on their rows.

### Known limitations

- The `connection-timeout` read is inconclusive (10-day pre-window, traffic growth, `v0.116.0`/`v0.123.0`
  confounds, log sample was the newest 30 lines only).
- The `INACTIVITY` return-rate read has no control group and measures any analytics event.
- Verification tier: one small code change with no authorization, money or production-data semantics, so a single
  `advisor()` pass rather than a pressure test.

### Shipped

- **`RetentionEmailScheduler.runMonthly()` zone pin** merged as PR #1442 (`540b866b`); full backend build green.
- **Checkpoint reads, run 2026-09-24 (read-only production and Render reads; results are also on each Backlog row).**
  - **Publication boundary (due 2026-09-22): NOT FIRED, re-date to 2026-09-28.** One public Review Set, `CPALE
    Comprehensive Review`, holds 325 unpublished topics and has never been published since `V141` (only the
    backfill stamp). Its oldest unpublished row is 2026-09-14, 9 days old, under the 14-day threshold; it crosses
    on 2026-09-28. `LET` (2026-09-10) and `PNLE` (2026-09-14) were really published, so the control is being used.
  - **Learning Connections demand (due 2026-09-19): kill criterion does NOT fire; re-read at the next release.**
    1 `ACCEPTED` relationship, unchanged since 2026-09-05; 2 invitations (1 `ACCEPTED`, 1 `PENDING`); no new
    activity in 19 days. One pair is weak evidence and may be a test pair.
  - **`connection-timeout: 5000` (due 2026-09-18): INCONCLUSIVE, leaning concerning, owner decision.** 500s went
    from 25 in the 10 available pre-window days (about 0.07%) to 352 in 09-05..09-18 (about 0.27%), with spikes on
    09-17 (59) and 09-18 (68); 502s peaked at 329 on 09-17. Assumptions: deploy taken as 2026-09-04; Render keeps
    only 30 days so the pre-window is 10 days; traffic also grew. Logs: 09-18 14:48 genuine pool saturation
    (`total=20, active=20, waiting=4`); 09-18 16:03 and 09-22 06:04 pool timeouts following Postgres I/O errors
    (pool collapsed to 7 then 2), which looks like the DB dropping rather than load. Only the newest 30 log lines were
    read, so this is not a count. `v0.116.0` and `v0.123.0` confound it. The row's "material and sustained" has no
    number, so the kill criterion was not applied.
  - **First `v0.157.0` retention run, read 2026-09-25 (fired 2026-09-24T18:45Z = 02:45 Manila): AS DESIGNED.** Dispatch
    order was digest (18:45:06.497), then weak-concept (18:45:06.530), then `INACTIVITY` (18:45:23.422), about 17 s
    total, no errors, one instance. Digest `budget=60 attempted=14 sent=14 skippedForBudget=0`; weak-concept
    `budget=46 attempted=0`; `INACTIVITY` `budget=46 sentToday=14 attempted=46 sent=46 skippedForBudget=75`, so it
    landed inside the expected 40-47 band instead of the old pin at 60. `email_log` agrees (14 `DUE_CONCEPTS_DIGEST` +
    46 `INACTIVITY` = 60, the full shared budget). The pre-deploy baseline was the 2026-09-23T18:45Z run on
    `v0.156.0` code: `inactivity budget=60 sent=60`, `dueConceptsDigest=23`, no per-type dispatch lines. The digest
    due-count differs day to day (14 vs 23), so the two are not a like-for-like "14 of 23". `email_log.clicked_at` is
    still 0 (click tracking not enabled yet); `email_open_daily_counts` had 6 (09-23) and 3 (09-24) before this run. The open date is Resend's own event `created_at` in UTC (`ResendWebhookService.java:123`), so opens dated 09-23 that arrived after the 02:39Z deploy are late delivery, not a dating bug.
    `[CHECKPOINT — due 2026-09-27]` remains open. 75 eligible learners were skipped for budget; see the
    `INACTIVITY` effectiveness item above for whether that matters.
  - **Cross-note review re-check:** `quick_review_sessions` 906 total, `source_collection_id` NULL on all 906
    (179 since the Stage 1 audit); DEFER stands, gate is `[CHECKPOINT — due 2026-10-13]`.

## v0.157.0 - Watching More Closely

**Status: Released** (signed off and deployed 2026-09-24: Render live 02:40Z, `V149` applied 02:39Z, Vercel production 02:43Z)

Theme: bring in five already-open, independently-produced PRs — traffic analytics, two production
incident findings, refreshed GPT product-context docs, and a resolved retention-communication channel
doctrine — onto one release branch instead of merging each straight to `main`, then implement the
scoped pool-observability and retention-email instrumentation follow-ups without triggering a deploy
until the owner is ready.

### Planned Scope

- **Vercel Web Analytics (frontend).** PR #1426, auto-generated by Vercel's own GitHub integration
  after the owner enabled Web Analytics on the (free/Hobby) Vercel plan: adds `@vercel/analytics`
  and one `<Analytics />` component to the root layout. Confirmed earlier this cycle: 50,000
  events/month included, no charge risk on overage (collection just pauses). Independently re-verify
  its own build/lint/test claims before signoff rather than trusting the PR body as-is.
- **Pool observability scoping and implementation.** PR #1427 started the `threads.max` checkpoint
  clock (owner confirmed removing Render's `SERVER_TOMCAT_THREADS_MAX` override) and scoped closing
  the saturation detector's two known gaps (non-request-thread registry coverage and scheduler
  contention) via a design verified against this project's actual Spring 7.0.5 jar. The implementation
  is recorded under Shipped below.
- **2026-09-22 production restart finding (docs only).** PR #1428 — a same-day incident where the
  known four-occurrence pool-exhaustion signature is explicitly absent; trigger left genuinely
  unidentified rather than rounded up to a guess.
- **GPT context docs refreshed to v0.156.0 (docs only).** PR #1429 — `GPT_CONTEXT.md` and
  `SURFACES_AND_FEATURES_CONTEXT.md` brought current for the notification-CTA and Campaign Feedback
  work; other modules left flagged, not silently touched.
- **Retention communication channel doctrine and Stages 1a–1b.** PR #1430 resolved "should retention
  email move to in-app notification" with a channel-role doctrine rather than a binary answer. Key
  finding: two of the four retention email intents already have live Dashboard current-state surfaces,
  so no in-app notification is recommended for them independent of further evidence. Stage 1a's
  click/open instrumentation and Stage 1b's budget governance are recorded under Shipped below; the
  evidence-dependent Stages 2–3 remain separate.

Anti-drift: the other docs-only PRs remain plans and findings, not diffs, until their own gates clear.
Pool observability and retention Stages 1a–1b are the implemented follow-ups to that original set.

### Shipped

- **Vercel Web Analytics.** `@vercel/analytics` 2.0.1 and one `<Analytics />` in the root layout
  (`frontend/app/layout.tsx:99`). Not taken from the PR body: `npm ci` accepts the lockfile, whose diff adds that
  package and also refreshes the stale root `version` field (0.96.0 to 0.156.0; no other dependency changed),
  `tsc --noEmit` is clean, lint has 0 errors, the production build succeeds and frontend Jest passes 2,489
  tests (1 skipped). The free-plan limit (50,000 events/month, collection pauses rather than charging) was
  checked against Vercel's published limits earlier this cycle and is not repository-verifiable.

- **Stage 2 evidence bound for the retention instrumentation (docs).** From a read-only production read (queries and results in
  `docs/claude-plans/2026-09-23-retention-volume-read.sql`): only `INACTIVITY` and `DUE_CONCEPTS_DIGEST` have a measurable audience (`WEAK_CONCEPT` 2 opted in,
  `WEEKLY_SUMMARY` 1, `KNOWLEDGE_IMPACT_DIGEST` 0 — zero sends in 90 days each). Two-tier bound on clicks: 14
  days, 100 recipients, and 30 clicks or 2,000 sends; 60-day backstop reads an unmet type as underpowered (a
  re-date, not a verdict); kill criterion `INACTIVITY` click-through under 1%. Plan §I. The dated checkpoint
  rows are in `ROADMAP.md`.

- **Cold pressure test and its remediation.** Four cold reviews (Opus on retention, Sonnet and then Opus on
  pool observability, and Codex across the whole release) tried to falsify the release against its plan; each
  finding was verified in code before fixing, and several were rejected or downgraded with reasons below.
  Verification at signoff: backend `clean install` BUILD SUCCESS with 2,511 tests including the real-Postgres
  suite, frontend Jest 2,489. Fixed
  (PR #1437): a non-ISO click timestamp escaped the catch and would 500 (`DateTimeParseException` is not an
  `IllegalArgumentException`); the digest-first order coupled a digest failure to the day's `INACTIVITY` sends,
  so the digest call is now isolated; three of four executors' decoration was unguarded by any test. Doc
  corrections: `WELCOME` does have a writer, `clicked_at` is first-processed, the open counter is
  whole-account. Two of these were defects in this release's own earlier work.

- **Docs-only inputs, shipped as documents:** the pool-observability plan (#1427), the 2026-09-22 restart
  finding (#1428, trigger still unidentified), GPT context refreshed to `v0.156.0` (#1429), and the retention
  channel doctrine (#1430).

- **The retention budget now governs all five scheduled retention email types against a retention-only
  count.** `INACTIVITY`, `WEAK_CONCEPT`, `WEEKLY_SUMMARY`, `DUE_CONCEPTS_DIGEST`, and
  `KNOWLEDGE_IMPACT_DIGEST` each recompute the available budget before bounding candidates; the orphaned
  public `sendInactiveUserEmails()` entry point now shares the same budgeted inactivity path. The count
  explicitly excludes transactional mail, dead/unclassified enum values, and the separately capped
  admin-triggered `RE_ENGAGEMENT_2025` campaign, removing that campaign's accidental cross-talk with
  automated retention dispatch. **Send order is now the priority mechanism, and that was found only by
  reading production before signoff:** `INACTIVITY` sat at exactly 60/day (the 100-limit minus 40-reserve
  ceiling) on 10 of the last 14 days while `DUE_CONCEPTS_DIGEST` sent 0–22/day unbudgeted. Extending the
  budget to the digest with `INACTIVITY` first would have starved the digest to ~0 on most days, so
  `runDaily` now dispatches the digest, then `WEAK_CONCEPT`, then `INACTIVITY` (`RetentionEmailScheduler.java:30`).
  Daily sends stay at the cap and `INACTIVITY` yields roughly the digest's volume (about 60 down to 40–47/day);
  **the later-running weekly and monthly types do not get the same protection — see Known limitations.** Two
  guards fail if the order regresses. `transactionalReserve` (40) is unchanged, though real transactional
  volume is 0–1/day; lowering `EMAIL_TRANSACTIONAL_RESERVE` would NOT help, because `INACTIVITY` has more
  eligible learners than budget and would absorb the extra room.

- **Retention email clicks now correlate to a send record without Resend message-id plumbing.** The
  five dispatched retention types reserve their UUID `email_log.id` before rendering and add inert
  `source` and `e` query parameters to the CTA, while persisting the row only after a successful send.
  Verified `email.clicked` webhooks read Resend's documented `data.click.link` and
  `data.click.timestamp`, then set that row's nullable `clicked_at`; unknown, purged, mismatched or
  malformed correlations are acknowledged and skipped. `email.opened` uses top-level `created_at` to
  increment `email_open_daily_counts` by UTC day with no per-send correlation. `EmailService`, Resend
  message ids, and `UNFINISHED_NOTE` remain unchanged; Stage 1b's budget governance is described above.
  Source doctrine:
  `docs/claude-plans/retention-communication-channel-doctrine-final-plan.md` §D/§I.

- **Pool saturation diagnostics now cover DB-bound background work.** A shared task decorator registers
  the four DB-touching executors and every `@Scheduled` job (all route through the six guarded scheduling methods, which one test exercises) in `InFlightRequestRegistry`, using executor
  thread names or Spring's exact `ClassName.methodName` scheduled-task description; the detector's own
  `poll()` is explicitly excluded (test-proven mid-cycle, not just after). The custom scheduler subclasses
  `ThreadPoolTaskScheduler` rather than using plain `setTaskDecorator()` — verified against the actual
  resolved jar (bytecode) that `ThreadPoolTaskScheduler` hands the configured `TaskDecorator` a
  `RunnableScheduledFuture` wrapper, not the user's task, which would have silently discarded every
  scheduled job's description; the subclass pre-decorates the real task before Spring wraps it, and the
  decorator no-ops on a `RunnableScheduledFuture` it's handed directly to avoid double-instrumenting.
  Two `scheduled-task-` threads mean ONE slow DB-bound job can no longer starve the detector's polling; two DB-bound jobs firing at the same instant (for example 02:45Z) still can, for up to Hikari's connection timeout.
  `runDaily`/`runWeekly` (`RetentionEmailScheduler`) are runtime-verified still anchored to `Asia/Manila`
  after the scheduler swap (real `CronTrigger.nextExecution()` assertions, not inspection). **`runMonthly`
  was NOT part of that verification and has no zone pinning at all — a pre-existing gap, not introduced
  here, out of scope for this change and tracked as its own Backlog Index row** (see
  `docs/product/ROADMAP.md`). Diagnostic registration
  and cleanup fail open, and cleanup is unconditional when work throws. Source and design rationale:
  `docs/claude-plans/done/2026-09-22-pool-observability-non-request-thread-coverage-plan.md`.

### Known limitations

- **`WEEKLY_SUMMARY` and `KNOWLEDGE_IMPACT_DIGEST` are budget-starved, permanently, while `INACTIVITY` saturates
  the cap.** They run Sunday 18:00 Manila and on the 1st at 09:00 host time (17:00 Manila), after the 02:45 run
  has used the day's budget, so they start with budget 0 and "eligible later" only reaches the next week or
  month. Immaterial today (1 and 0 opted-in learners; no sends in 90 days) but more opt-ins would NOT unlock
  them. Owner chose to document rather than cap `INACTIVITY`'s share now; a Backlog row gates the fix on
  opt-in growth. `sendWeeklySummaryEmails_independentlyRespectsExhaustedBudget` asserts the starvation as
  correct behaviour.
- **The admin `RE_ENGAGEMENT_2025` campaign no longer counts toward the budget.** A campaign batch of up to
  100 plus ~60 retention sends can exceed Resend's 100/day on the same day.
- **`clicked_at` is the first click PROCESSED, not necessarily the earliest.** The webhook IS now exercised over
  real HTTP (`ResendWebhookHttpTest`: signed click, non-ISO timestamp, signed open, unsigned request), but the
  real check that Resend delivers these events is still the deploy + 3 day smoke read.
- **The click marker is a capability, not a binding.** `e=<uuid>` is an unguessable v4 id, so a learner cannot
  guess another learner's, but anyone who HOLDS one (for example from a forwarded email) can flag that one send
  as clicked; the handler checks the email type, not the recipient. Impact is one analytics flag. It is
  inert in the sense that no frontend code reads `e` (checked by search, not by a test).
- **A send whose row fails to persist leaves an orphan marker.** If Resend accepts the email and the
  `email_log` save or the surrounding commit then fails, the delivered link carries an `e` with no row (the
  click is logged and skipped) and no cooldown row exists. The same window existed before this release.
- **Budget and cooldown checks are not atomic.** Count, check and send have no lock, so two overlapping
  instances or a duplicate cron fire could overspend the budget or double-send. Pre-existing, not introduced
  here; the service runs one instance (checked in Render), so overlap is limited to deploy hand-over.
- **A database failure in the click or open handler returns a 5xx on purpose,** so Resend retries a transient
  outage instead of losing the event; only malformed payloads are acknowledged and skipped.
- **The scheduler bean calls `initialize()` and Spring calls it again,** abandoning one executor that never
  started a thread. Harmless; the existing executors follow the same pattern.
- **The open counter is whole-account** (verification and password-reset opens are included) and keyed by UTC
  day; directional only.
- **Instrumentation is unverified emitting until deploy.** It needs Resend click and open tracking on the
  sending domain and the webhook subscribed to `email.clicked`/`email.opened`; `RESEND_WEBHOOK_SECRET` is staged
  in Render.
- **`InFlightRequestRegistry` is keyed by `Thread`,** so an inner decorated task's removal would wipe an outer
  entry on the same thread. No reachable trigger was found (latent).
- **A registry entry means "running", not "holding a connection".** A generation thread in the middle of an LLM
  call appears in a saturation log line exactly as one holding a connection does; read the log with that in mind.
- **The decorator is a Spring bean, and the scheduler subclass is what makes it work.** Spring Boot applies a
  lone `TaskDecorator` bean to its own executor and scheduler builders, and on Boot's default scheduler it would
  silently register nothing (it receives the internal future, not the job). The bean-level test in
  `AppConfigTest` fails if the custom scheduler is removed or replaced.
- **`runMonthly` has no zone pin** (pre-existing; Backlog row).

### Deploy notes

- `V149` runs on deploy: a nullable `ADD COLUMN` on `email_log` plus a new table; additive.
- No API form is removed, renamed or made required, so there is no frontend/backend deploy-ordering constraint.
- **Behaviour change to expect:** `INACTIVITY` drops from about 60 to 40–47 a day. Confirm with
  `retention.email.*.dispatch` log lines after the first daily run.
- Owner check at deploy + 3 days: `SELECT count(*) FROM email_log WHERE clicked_at IS NOT NULL` and
  `email_open_daily_counts`; zero of both means tracking or the webhook subscription is off.

### Signoff scope record

- Vercel Web Analytics: shipped (`layout.tsx:99`). Pool observability: shipped, and CHANGED from the plan —
  plain `setTaskDecorator` on the scheduler would have lost every job's description, so a subclass was needed
  (`InFlightThreadRegisteringTaskScheduler`; `AppConfig.java:41-44`). Restart finding, GPT context refresh,
  retention doctrine: shipped as documents. Retention Stages 1a and 1b: shipped
  (`ResendWebhookService.java:96,119`; `RetentionService.java:88,691,724,771`; `V149`). Stage 2/3: not started
  by design. Nothing in Planned Scope is unbuilt.

## v0.156.0 - Say What You Meant to Show

**Status: Released** (signed off 2026-09-22)

Theme: make the existing announcement `ctaLabel` visible as a real call-to-action in the notification
inbox, then ship Campaign Feedback — a bounded, single-instrument in-app research campaign that asks
every learner one structured question from that same linked-notification pattern and persists
categorized responses, closing on a configured date.

Source: `docs/claude-plans/actionable-announcements-campaign-feedback-stage1-plan.md` (Stage 1 audit +
plan for "Actionable Announcements + Campaign Feedback"). **⚠️ Originally scoped as two releases
(Release A frontend-only, Release B backend+frontend as a separate `v0.157.0`) — owner decision
2026-09-22 folded them into this one release instead.** Release A shipped first (PR #1423, merged into
this branch) and is documented below exactly as it shipped; Release B's Codex prompt
(`docs/codex-prompts/v0.157.0-campaign-feedback.md`, untracked, gitignored per convention) had its one
open input — the campaign's `closes-at` timestamp — resolved by the owner 2026-09-22
(`2026-10-06T00:00:00Z`, ~2 weeks after this release deploys) and is now dispatched.

### Planned Scope

**Release A — notification inbox / Admin polish (shipped, see below).**

- **CTA affordance in the notification inbox (frontend).**
  `frontend/components/notifications/notification-inbox.tsx` — render the already-stored,
  already-transmitted, never-rendered `notification.ctaLabel` as a non-interactive `<span>` inside the
  existing body `<Link>` (never a nested link/button — the one trap in this change). Extend
  `aria-labelledby` to include the CTA span id (WCAG 2.5.3 Label in Name). Add `line-clamp-3` to the
  body. Bump the dismiss button to a 44px (`min-h-11 min-w-11`) touch target. Move row padding onto the
  `<Link>`/dismiss button so the full card width is tappable.
- **Admin authoring guidance (frontend).**
  `frontend/app/admin/announcements/page.tsx` — live body character counter with a ~160-char soft-target
  helper (following the existing counter pattern in `send-feedback-widget.tsx`), plus helper text on the
  Link label field clarifying it is the visible CTA text learners see and tap. Field names ("Link
  label"/"Link path") are unchanged — zero production usage, renaming would only churn tests.
- **Tests.** `notification-inbox.test.tsx` (28 existing tests stay green + new coverage for CTA
  render/absent cases, the anti-nesting guard — exactly one interactive element in the row body —
  accessible-name, and the clamp) and `announcements/page.test.tsx` (counter).
- **Docs.** `docs/features/notifications.md` — document the CTA affordance contract, the span-not-link
  rule, and the stored-vs-displayed body split (full body stored/delivered, inbox displays a clamped
  view; no "Read more", no announcement detail page).

Release A anti-drift: frontend-only — no migration, no API/DTO change, no admin lifecycle change. Does
not make `ANNOUNCEMENT` badge-eligible (would resurrect the `v0.134.0` immortal-row defect) and does
not touch badge-decrement logic.

Release A routing: Claude Code inline (frontend-only, ~2 source + 2 test files, no new infrastructure —
too small to justify a Codex prompt). Verification tier: one `advisor()` call on the diff — no
permission, money, or quota surface touched.

**Release B — Campaign Feedback (backend + frontend).**

- **Data model (backend).** New `campaign_feedback_responses` table (`V148`), one row per
  `(user_id, campaign_id)` via a unique constraint. `CAMPAIGN_ID` is a named `String` constant
  (`"STUDY_FRICTION_2026_09"`), deliberately **not** a Java enum or a campaign registry/table — this is
  one fixed instrument, not a framework. `PrimaryBlocker`, `QuizIssue`, and `PlanIssue` **do** get real
  enums (genuine 5–9-option closed sets, unlike `CampaignId`).
- **Endpoints (backend).** `GET /feedback/campaign` (status: `submitted` / `campaignOpen`, no path
  param — there is exactly one instrument) and `POST /feedback/campaign`. **Locked precedence:** the
  unique-constraint duplicate check runs before the `closes-at` check, so a learner who already
  responded and submits again after close sees their own already-submitted state (200), never a
  "closed" rejection (409) — the close boundary only gates a genuinely new response.
- **Close boundary (backend).** A configured property, not a DB column:
  `notelib.campaign.study-friction-2026-09.closes-at`, bound as `String` and parsed to `Instant`
  explicitly (`@Value` has no `Instant` converter in this codebase). No default — a missing or
  malformed value fails application startup (fail-closed). **Owner-set value: `2026-10-06T00:00:00Z`**
  (~2 weeks after this release deploys, set 2026-09-22).
- **Frontend.** `/feedback` route (auth-gated), one adaptive selection screen (9 primary options,
  conditional multi/single-select follow-ups, always-visible optional free text), three terminal states
  in place on the same route (thank-you, already-responded, closed) — no wizard, no second route, no
  announcement detail page.
- **Account deletion.** `AccountPurgeService` gains a `CampaignFeedbackResponseRepository` purge call,
  mirroring the existing `feedback` purge — `notifications.md:370-373` records this exact step shipping
  missing for a different table in `v0.130.0`; do not repeat that omission.

Release B anti-drift: no campaign table/entity/registry, no dynamic form schema, no `CampaignId` enum.
Does not touch `notification-inbox.tsx` rendering, `AnnouncementEntity`, or the Admin announcements
page (that is Release A, already shipped). Does not relax `developer.txt:105` or touch anything gated
by `ADR-002` (unrelated H5/H6 threads from the `v0.155.0` incident). The permanent free-text Send
Feedback channel is untouched.

Release B routing: **Codex** (`docs/codex-prompts/v0.157.0-campaign-feedback.md`, Long mode) — new
endpoint, migration, and service logic, per this repo's own routing rule. Verification: `/audit-diff`
on the delivered diff before commit, per the standing rule for Codex-delivered work.

### Shipped

**Release B — Campaign Feedback:**

- Added the fixed `STUDY_FRICTION_2026_09` research instrument: `V148` stores one structured response
  per user, `GET /feedback/campaign` reports independent submitted/open state, and
  `POST /feedback/campaign` validates and persists the response through a unique-index-backed,
  concurrency-safe transaction. The backend closes new submissions at `2026-10-06T00:00:00Z`
  (overridable via the `CAMPAIGN_CLOSES_AT` Render env var, added post-`advisor()`-review so the window
  can move without a deploy) while preserving 200 idempotency for learners who already responded,
  including after close.
- Added the protected `/feedback` page with the nine-option primary question, four conditional
  follow-ups, optional free text, accessible checkbox/radio controls, loading/form/thank-you/
  already-responded/closed states, and fail-soft status loading. Campaign rows are explicitly removed
  by account purge.
- **Additive-only — no existing endpoint changed or removed**, including the permanent
  `POST /feedback` Send Feedback channel. This does NOT mean deploy order is unconstrained: deploy both
  backend and frontend, confirm both actually landed (`scripts/check-deploys.sh` — a merge is not a
  deploy), **THEN** publish the announcement below. A frontend-first window would 404 `GET
  /feedback/campaign` into the page's own fail-soft-to-form path (cosmetic — no wrong state reaches the
  learner) but a genuinely new submission in that window 404s into a generic error rather than a
  handled one.
- **⚠️ OWNER ACTION REQUIRED POST-DEPLOY, NOT CODE: the campaign has no in-app entry point until this
  is done.** `/feedback` is deliberately linked from nowhere (§1's lock keeps it separate from the
  permanent Send Feedback channel) — its only door is an announcement authored and published in
  Admin → What's New, using the plan's §F copy exactly:
  Title `Help us improve NoteLib` · Body `What gets in the way when you study? Tell us what we should
  improve — it takes about a minute.` · Link label `Share feedback` · Link path `/feedback` · Audience
  `EVERYONE`. **The `closes-at` clock starts at deploy regardless of whether this is done** — `zero`
  `announcements` rows have ever existed in production, so there is no existing muscle memory for this
  step. Publish it promptly after confirming the deploy landed.
- **`[CHECKPOINT — due 2026-10-08]` added to `ROADMAP.md`'s Backlog Index** — this campaign was
  approved on a measured 1.8% click-through floor with no impression denominator (plan §A3); the read
  is owed regardless of how the numbers land.

**Release A (PR #1423, merged):**

- **CTA affordance, clamp, hit-area and touch-target fixes in the notification inbox (frontend).**
  `notification-inbox.tsx` now renders `ctaLabel` as a non-interactive `<span>` inside the body
  `<Link>`/`<button>`, extends `aria-labelledby` to `${titleId} ${ctaId}` when a CTA is present, clamps
  the body to 3 lines, and gives the dismiss button a 44px touch target with row padding moved onto the
  interactive elements. **⚠️ Caught during `advisor()` review before commit: the body span was initially
  `line-clamp-3 block` — Tailwind emits `.block { display: block }` AFTER `.line-clamp-3` in this
  project's compiled CSS, so `block` would have silently overridden the clamp's `display: -webkit-box`
  and shipped the clamp as a no-op.** Confirmed by compiling this project's actual Tailwind output
  (`tailwindcss@4.2.1`) — `.line-clamp-3` at output index 4610, `.block` at 4741 — and independently
  re-confirmed the same way during the pre-signoff falsification pass. Fixed by dropping `block`
  (`-webkit-box` is already block-level); jsdom does no layout/cascade, so the test suite's
  class-presence assertions could not have caught this on their own.
- **Admin body character counter and Link label helper text (frontend).**
  `app/admin/announcements/page.tsx` — live counter with a ~160-char soft target (neutral below it,
  amber above; the 1000-char hard column/validator/`maxLength` are unchanged), plus helper text on the
  Link label field.
- **Tests.** `notification-inbox.test.tsx`: 28 pre-existing tests updated for the new accessible name
  (the default fixture carries a `ctaLabel`, so several `getByRole("link", { name: … })` queries needed
  the CTA label appended) plus new coverage for CTA render/absent, the anti-nesting guard, the clamp,
  and the dismiss touch target — 31 tests, all passing. `announcements/page.test.tsx`: 3 new counter
  tests. Full frontend suite: 220/220 suites, 2478/2479 tests passing (1 pre-existing skip), `tsc
  --noEmit` clean, `next lint` clean (no new warnings).
- **Docs.** `docs/features/notifications.md` updated with the CTA affordance contract, the
  span-not-link/anti-nesting rule, the WCAG 2.5.3 `aria-labelledby` requirement, the clamp's `min-w-0`
  dependency, and the Admin authoring-guidance section.

## v0.155.0 - Say What You Checked

**Status: Released** (signed off 2026-09-22)

Theme: fix a real quiz-grading correctness defect a learner caught and reported, and ship the
validator that would have rejected it at generation time.

Source: `docs/claude-findings/2026-09-19-quick-review-percentage-increase-correctness-incident.md`
(full incident audit, §A–T), owner decisions locked 2026-09-21 (§Q.1).

**What happened:** a learner answered a Quick Review question correctly, was graded wrong, re-ran the
quiz picking the answer they knew was wrong to confirm the bug, then reported it. Root cause: the LLM
emitted the wrong answer *letter* while its own explanation derived the correct value — a stored MCQ's
`correctIndex` pointed at `"25%"` while its `explanation`/`workingSolution` both derived and stated
`30%`. This is a generation-inconsistency defect, not parsing, persistence, shuffling, assembly,
evaluation, or rendering — all four downstream layers were traced and confirmed correct. A deterministic
corpus scan (zero LLM calls, re-run twice) across 115,333 production questions in four stores found
**31 confirmed defects**, each independently hand-verified by re-deriving the correct answer from the
question's own stated inputs, not trusted from its own suspect explanation. Realized learner exposure is
exactly one person, two sessions — every other instance sits in never-served exam pools or the owner's
own test account. **⚠️ CORRECTED 2026-09-22, discovered by the owner mid-repair, not caught at kickoff:**
this count included a false "duplicate defect" in pool `2437d442` — a live re-read found the pool's
second, similarly-worded question has a genuinely different choices array and was already correctly
keyed, not a duplicate of the confirmed defect. **True count: 30 confirmed defects, not 31.** See the
repair-SQL bullet below for the corrected per-store breakdown.

### Planned Scope

- **Repair SQL, owner-run, independent of code (data).**
  `docs/claude-plans/2026-09-21-quiz-answer-key-repair.sql` — 38 idempotent statements across four
  sections: A (12 `study_packs` rows), B (14 `exam_question_pool` rows, 14 array-element fixes), C (10
  `challenge_quiz_question_bank` rows, zero real learner exposure), D (retroactive correction of session
  `1e78a11d-…` and its `concept_health` row — kept deliberately separate per the owner's explicit "do not
  silently rewrite history" condition; A–C run independently of D). Every statement's `WHERE` clause
  re-asserts the current wrong value, so re-running the file is a safe no-op. **Claude does not execute
  this file** — production write-only, owner's to run per this repo's read-only rule. **⚠️ CORRECTED
  2026-09-22:** Section B originally claimed 15 array-element fixes across those 14 rows (one pool
  supposedly carrying a genuine duplicate defect). The owner's own pre-check for that section returned 14
  rows, not the expected 15; investigating found the "duplicate" was a different, already-correctly-keyed
  question with a different choices array. Corrected to 14 rows / 14 fixes (38 total statements, not 39);
  the actual `UPDATE` statement was always safely scoped regardless of the comment error, since it
  matches on the defective question's specific choices array, which the correct question never shares.
- **H4 — internal-consistency validator at the shared generation boundary (backend, the actual fix).**
  For an MCQ whose choices are all numeric/unit literals, rejects the generated question if the keyed
  choice's text does not appear in `explanation + workingSolution` while some other choice's text does
  — narrow, deterministic, mirrors the exact detector measured against production this incident (1.3%
  flag rate on 5,443 numeric-literal-answer questions, 30/30 confirmed genuine on manual re-derivation —
  corrected 2026-09-22 from an originally-claimed 31st that turned out to be a different, already-correct
  question, not a genuine defect).
  **Locked retry chain (owner decision, §Q.1 item 2): retry the rejected question once; if still
  invalid, omit it (pack generates with N−1) — never fail the whole pack.** Runs on the shared
  generation boundary every quiz mode consumes, not once per mode.
- **H1 — schema tightening (backend).** Constrains the LLM structured-output `answer` field to the
  `A`/`B`/`C`/`D`/`null` enum, closing an existing schema/Java-side divergence. Free, no behavior change
  on well-formed generations.
- **H2 — dead-code removal (backend).** Deletes `QuizValidationUtils.randomizeChoices` — reorders
  choices without remapping `correctIndex`, a real answer-identity-corruption hazard if ever wired into
  a live path, currently called only by its own test.
- **H3 — dead-code removal, Java only, no migration (backend).** Deletes `QuizQuestionEntity` /
  `QuizQuestionRepository` and their tests — zero references anywhere outside themselves, the
  `quiz_questions` table holds 0 production rows. Table drop itself is out of scope for this task (a
  DDL change, owner-execution protocol); the Codex delivery states explicitly whether it left a
  follow-up note or prepared a separate non-migration drop-table SQL artifact.
- **H3b — MATCHING block-integrity check at generation (backend).** Measured non-zero yield (4 of 50
  production MATCHING blocks, 8%, violate block-size or identical-choices rules already stated in the
  prompt as CRITICAL but not enforced on every construction path). Enforced at generation; a violation
  demotes to MCQ, mirroring the existing partial `normalizeMatchingGroups` behavior.

**Explicitly out of scope, not folded in:**
- **H5** (relax `developer.txt:105` so explanations must state the answer's value, still forbidding
  letter references) — approved by the owner (§Q.1 item 3) but ships as its own later prompt, once this
  validator's rejection-rate baseline exists in production; bundling it would make a post-ship
  rejection-rate change unattributable to either change alone.
- **H6** (replace the A/B/C/D letter contract with verbatim answer-text identity) — approved in concept
  by the owner (§Q.1 item 4) but gated on `docs/architecture/ADR-002-quiz-answer-identity-by-text.md`,
  currently **PROPOSED, not Accepted**. Not implemented until ratified.
- Structural answer-key validation (index-in-range, exactly-one-correct, duplicate choices,
  MULTI_SELECT key agreement) — the incident's own corpus scan found zero violations of any of these
  across all 115,333 production questions; explicitly not the fix, not built.
- The historical-sanitation `DETERMINISTIC_SCAN` and semantic (is-the-explanation-actually-right)
  verification — both out of scope, per the incident doc's three-tier discipline (STRUCTURAL /
  INTERNAL-CONSISTENCY / SEMANTIC, strictly separate; this release ships INTERNAL-CONSISTENCY only).

Anti-drift: H4 evaluates MCQ-with-numeric-choices only — TRUE_FALSE, MULTI_SELECT, MATCHING,
IDENTIFICATION, ENUMERATION, and prose-choice MCQ pass through unchanged; a question passing H4 is
never to be represented as "verified correct" anywhere in logs/docs/UI, only as internally consistent.
No file under `docs/architecture/ADR-001-*.md`, `docs/architecture/ADR-002-*.md`,
`developer.txt:105` (or any sibling file's equivalent line), `StudyPackGenerationContextResolver`, or
any Note-persistence path is touched by this release. `QuizItem.java`'s canonical constructor and
`resolveCorrectIndex` precedence ladder are unmodified — this release only decides whether a `QuizItem`
gets constructed, not how it resolves once constructed.

**Routing: Codex** (`docs/codex-prompts/v0.155.0-quiz-answer-key-integrity-validator.md`, Long mode) —
touches shared backend generation infrastructure across every quiz mode, per `CLAUDE.md`'s task-routing
table. **Verification tier: one scoped cold agent, falsification-framed** — trigger: a generated-content
semantics change reachable from every quiz mode. Framed against the specific claims the implementing
session makes, same pattern as this repo's established precedent.

### Shipped

- **H4 — generated MCQ answer/explanation consistency gate.**
  `QuizValidationUtils.java:187` implements the deliberately narrow numeric/unit-literal matcher with
  LaTeX-wrapper cleanup, choice-precision rounding and numeric-token boundaries; the shared conversion
  seam in `OpenAiLlmStudyPackService.java:2417` now retries one rejected question and omits a still-invalid
  replacement without failing the rest of the pack. `OpenAiLlmStudyPackServiceTest.java:959-1065` proves
  the exact reported defect, retry/omit behavior, and reach from Quick Review, Adaptive Practice,
  Challenge Quiz, Long Exam, Board Exam and Teacher Generate Quiz; `QuizValidationUtilsTest.java:199-262`
  covers the normalization and substring-collision cases. Short generated results now retain their
  actual count through `ChallengeQuizService`, `QuickReviewAdaptivePracticeService` and
  `GeneratedQuizService` instead of being converted back into whole-generation failures.
  **⚠️ Pre-commit audit mutation-verified the two safety-critical pieces of this delivery, not just
  read them:** reverting the boundary-aware match (`QuizValidationUtils.java:227-230`) to a plain
  `contains()` check killed `answerExplanationConsistency_usesNumericBoundariesForOverlappingChoices` —
  confirming the substring-collision guard the incident doc called out as "a REAL hazard" is genuinely
  load-bearing, not decorative. Restored and re-verified green. **Quota-accounting confirmed
  independently** (the Codex delivery's own output did not state this explicitly, per the prompt's
  OUTPUT item 4 requirement): `recordUsage`/`incrementUsage` calls happen once per top-level generation
  request in `StudyPackService.java`/`NoteGenerationService.java`, never per individual quiz question —
  a question-level retry or omission inside `buildQuizItemOrRetry` is invisible to quota accounting by
  construction, not merely by observed behavior.
- **H1 — structured-output answer enum.**
  `prompts/study-pack-v1/schema.json:70` constrains `answer` to `A`/`B`/`C`/`D`/`null`, matching the
  existing Java parser contract; `OpenAiLlmStudyPackServiceTest.java:948` pins the deployed schema resource.
- **H2 — hazardous dead choice randomizer removed.**
  Deleted `QuizValidationUtils.randomizeChoices`, which shuffled choices without remapping the answer,
  and its two self-only tests after confirming `backend/src` had no production caller.
- **H3 — orphaned quiz-question Java mapping removed.**
  Deleted `QuizQuestionEntity.java` and `QuizQuestionRepository.java` after confirming neither class was
  referenced outside those two files. The zero-row `quiz_questions` table remains unchanged; dropping it
  is a separate owner-run DDL follow-up, and this release includes no migration for it.
- **H3b — MATCHING block integrity enforced on every generated path.**
  `OpenAiLlmStudyPackService.java:575` now routes ungrouped MATCHING items through the existing 2–4-item,
  identical-choices normalizer instead of letting them escape as singletons. Tests at
  `OpenAiLlmStudyPackServiceTest.java:1132-1169` cover the previously escaping singleton and an oversized,
  non-identical-choice block; both demote to MCQ. **⚠️ Pre-commit audit correction, not a defect:**
  mutation-testing the new ungrouped-routing branch found the oversized/non-identical-choices test
  (`generateLongExam_demotesOversizedMatchingBlockWithDifferingChoices`) still passes with that branch
  removed — the pre-existing `resolveInvalidMatchingGroupReason` size/choice check already caught that
  case whenever a block was properly grouped; only the ungrouped-singleton escape was a genuine gap this
  diff closes. The test is a correct regression lock, but only the singleton fix is new behavior — the
  incident's reported size-6 violation was already covered by code that predates this release.
- **Data repair executed by the owner, 2026-09-22.**
  `docs/claude-plans/2026-09-21-quiz-answer-key-repair.sql` run in full — Sections A (12 `study_packs`
  rows), B (14 `exam_question_pool` rows), C (10 `challenge_quiz_question_bank` rows), and D (the
  retroactive session/`concept_health` correction) — every per-section post-check returned clean.
  **While running Section B, the owner's own pre-check surfaced a real documentation defect**: the
  plan claimed 15 array-element fixes across those 14 rows (one pool supposedly carrying a genuine
  duplicate defect); the pre-check returned 14. A live read-only query against the pool in question
  found the "duplicate" was a different, already-correctly-keyed question with a different choices
  array — not a duplicate at all. Corrected across all six places the wrong count was recorded (the
  repair SQL's own comments, the incident finding doc, `ADR-002`, `RELEASES.md`, `ROADMAP.md`,
  `CLAUDE.md`) plus two misleading labels in the plan file's own final-summary query that the
  correction pass initially missed. The `UPDATE` statements themselves were always safely scoped
  regardless of the documentation error — verified by the clean post-checks above.

### Known Limitations

- H4 has near-zero recall for prose-answer MCQs while current prompts avoid restating the answer value.
  H5 remains a separately approved prompt change so this release first establishes an attributable
  production rejection-rate baseline. Passing H4 means only internally consistent, never semantically
  verified; H6 and the separate single-best-answer Question Quality audit remain deferred. All three
  (H5, H6, the Question Quality audit) now have their own Backlog Index rows in `ROADMAP.md`, added at
  this commit since the Codex delivery's own output explicitly deferred that question to this session.
