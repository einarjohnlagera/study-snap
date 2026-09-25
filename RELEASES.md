# RELEASES.md - NoteLib

## v0.159.0 - Nothing Lost in the Batch

**Status: In Progress**

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

### Shipped

_(nothing yet)_

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

## v0.154.0 - Closing the Loop

**Status: Released** (signed off 2026-09-18)

Theme: close out three independently-verified, gate-true Backlog Index items — none gated on an owner
action or a production read, none sharing a file or a shared method with any other, each anchored to
current code before being scoped rather than trusted from its row's prose.

**⚠️ CORRECTED AT KICKOFF, BEFORE ANY CODE WAS WRITTEN: a fourth item, "health-check-on-Hikari-pool
decoupling," was scoped in by mistake and dropped.** The liveness/readiness split it proposed to build
already shipped in `v0.119.1` PR #1297 (`management.health.group.liveness.include: livenessState`,
excluding `db`, in `application.yaml`) — the pre-scoping check only grepped for a custom
`HealthIndicator` Java class and missed that the real fix is declarative YAML config, not a class. The
only piece still open is the Backlog Index's own existing row for it: an **owner action**, repointing
Render's `healthCheckPath` from `/api/actuator/health` to `/api/actuator/health/liveness` in the
dashboard — confirmed still unpointed via a live read-only Render API call at this kickoff
(2026-09-18). Not re-added to this release's code scope; it stays an owner action, same class as A1.

### Planned Scope

- **`course_programs.is_active` write path (backend + Admin frontend).** Confirmed dead column:
  `CourseProgramCatalogRepository.java` reads `is_active` in several places but no code anywhere in
  `backend/src/main/java` ever writes it; `CourseProgramCatalogService`/`Controller` have zero
  references. Adds the missing write path so Admin can actually deactivate a catalog program — the
  prerequisite for retiring the two legacy fused rows (`Nursing · Medicine`, `Nursing · Pharmacy`).
- **Recovery for `generation_enqueued_at IS NULL` notes stranded in `GENERATING` (backend).**
  `GenerationRecoveryService.java:107` explicitly skips this row class and logs "leaving them
  untouched"; every other stale `GENERATING` row is swept back within ~2h10m
  (`noteBoundMinutes` default 120 plus sweep cadence), but the automated sweep never touches a row
  missing this timestamp. **⚠️ Framing corrected at kickoff:** a live read-only production query
  (2026-09-18) found **zero** notes currently `GENERATING`, let alone with a null clock — this is a
  **latent structural gap in the automated sweep**, not an active stuck-note population, and (found
  mid-implementation, not at kickoff) **not a user-visible dead end either**: `NoteController`'s
  `POST /notes/{id}/recover-stranded-generation` already gives the note owner a tested, self-service
  recovery path for exactly this row class, using `updatedAt` as a fallback clock bounded by the same
  `noteBoundMinutes`. This item makes that same rule fire automatically as well as on request, rather
  than inventing new recovery logic or fixing a previously-unrecoverable state. Every current write path
  that sets `NoteStatus.GENERATING` (`StudyPackService.java:224-225`, `:326-327`) also sets
  `generationEnqueuedAt` atomically in the same method, and `V118__generation_recovery_clocks.sql`
  already one-time-backfilled any pre-existing null rows at its own deploy, so this is prospective,
  defense-in-depth coverage for a future non-atomic writer — not a fix for a live incident.
- **Topic-note generation passes `subject` into the LLM context (backend + frontend).**
  `GenerateNoteFromTopicRequest.java` carries `topic`, `courseProgramIds`/`courseProgramText` and
  `domainContext`, but no `subject` — `NoteGenerationService` builds context with `subject = null`, so a
  note authored under a specific subject via "Create from topic" never tells the model that. Degrades
  quality rather than failing requests. Must preserve ADR-001's hierarchy: Domain Context is the sole
  authoritative domain constraint, Subject only narrows within it.

Anti-drift: no bulk `is_active` editor or catalog deletion, and no `course_programs.program_family_id`
write path revival; the `GENERATING`-recovery fix extends the existing sweep's row selection, it does
not change `noteBoundMinutes` or the sweep cadence; the topic-note `subject` change does not let Subject
override or compete with Domain Context per ADR-001, and does not touch `courseProgramText`/
`domainContext` resolution.

**Routing:** Claude Code inline for the `GENERATING`-recovery fix (isolated root cause, 1-3 files);
Codex for the `is_active` write path and the topic-note `subject` context gap (new endpoint/DTO +
multi-surface frontend each). **Verification tier:** each item's own tier as scoped (direct verification
for the inline item, normal `/audit-diff` for the two Codex items). **⚠️ Escalated at signoff, past the
whole-release `advisor()` summary originally scoped here:** all three items independently tripped
CLAUDE.md's "delivery introduced a defect the same session then fixed" trigger (item 1's lost-update
defect, item 2's unbounded-recovery regression, item 3's stale-closure bug — each caught and fixed before
its own commit). A repeated same-session-defect pattern across every item in a release is a stronger
blind-spot signal than the rule anticipates from a single occurrence, so this release ran one scoped cold
agent, falsification-framed against the specific claims made in all three fixes, instead of the single
`advisor()` summary. **Result: nothing disproven** — all four falsifiable claims per item held under
direct code inspection (the lost-update fix, the recovery bound, the dependency-array fix, and their
respective transactional/normalization/negative-case guarantees), and no cross-item coupling was found.
**⚠️ One imprecision corrected, not a defect:** this section's original "no shared files or methods"
phrasing was wrong on the first half — `frontend/lib/api.ts` is touched by both item 1
(`updateCourseProgram`) and item 3 (`generateNoteFromTopic`), at non-overlapping functions with no logic
interaction. "No shared methods" is what actually holds and is what the no-full-pressure-test gate
depends on.

Carried forward from `v0.153.0`'s signoff, not this release's problem to solve: A1 (owner action —
enabling Render's own per-request logging) still not enabled as of `v0.153.0` signoff; the Leg A2
saturation detector's registry has no coverage of non-request threads (Known Limitation, not re-scoped
here). Also carried forward, from this release's own kickoff correction above: the Render
`healthCheckPath` repoint (owner action).

### Shipped

- **Admin write path for `course_programs.is_active`.** The existing catalog PATCH accepts an optional
  nullable `isActive` field and writes it transactionally through
  `CourseProgramCatalogService.java:132-140` / `CourseProgramCatalogRepository.java:63,113-115`;
  omission leaves the lifecycle flag unchanged, and an `isActive`-only PATCH also leaves family
  memberships untouched. The Course / Programs view initializes an Active checkbox from the edited
  row and marks inactive rows in both rendered layouts (`admin-course-program-catalog-section.tsx`).
  **⚠️ Pre-commit `advisor()` review found and fixed a real lost-update defect in the Codex delivery,
  the same class `v0.152.0`'s cold agent found on the sibling family-rename modal:** the save path
  originally re-sent `programFamilyIds` from its load-time snapshot on every save, including an
  Active-only toggle — so an admin flipping Active while a concurrent admin had just changed that
  program's family memberships would silently overwrite the concurrent edit. Fixed by mirroring
  `AdminProgramFamiliesSection`'s `membershipDirty` pattern: `programFamilyIds` is now omitted from
  the request entirely unless `CatalogMultiSelect` was actually touched this edit. Two guard tests
  added confirming an Active-only save carries no `programFamilyIds` key. The shared catalog read
  stays unfiltered, and the existing Applicable Programs active-only behavior is unchanged.
  **⚠️ Deploy-ordering statement, per CLAUDE.md's rule for a form whose omission-meaning changed:**
  this PATCH's frontend and backend must deploy together, and the safe direction is
  **backend-first**. If Render deploys the new `isActive`-aware backend before Vercel deploys the new
  frontend, the old frontend's existing family-save calls are unaffected (it always sent
  `programFamilyIds` and never sends `isActive`, both still handled). If Vercel deploys the new
  frontend first, the Active checkbox reaches users before the backend accepts `isActive` — Jackson
  silently drops the unknown field, the PATCH still 200s, and the toggle appears to save but has no
  effect until the backend catches up. Not a data-loss risk either order, but backend-first avoids a
  silently-inert control window. Coverage includes real JSON PATCH binding plus a follow-up catalog
  GET, service omission/application cases, the JDBC `UPDATE` executed and read back on Testcontainers
  PostgreSQL, request-body included/omitted cases in `api-course-program-catalog.test.ts`, and
  modal/desktop/mobile component cases including the two lost-update guard tests. Backend 2435/2435;
  frontend 2468/2469 with one pre-existing skipped test; frontend lint 0 errors (20 pre-existing
  warnings, all pre-existing and unrelated to this change).
- **Recovery for `generation_enqueued_at IS NULL` notes stranded in `GENERATING`.**
  `NoteRepository.findGeneratingIdsWithNullEnqueuedAt` (new, bounded on `updatedAt < cutoff`, same
  `noteBoundMinutes`) feeds a new `GenerationRecoveryRowWriter.recoverNoteWithMissingEnqueuedAt(UUID,
  OffsetDateTime)`, which applies the identical `updatedAt`-fallback rule
  `NoteController.recoverStrandedGeneration` already used for self-service recovery of this row class —
  now enforced by the scheduled sweep too. `GenerationRecoveryService.recoverStaleNotes` runs this
  alongside the existing timed-clock sweep and combines both results; the original
  `countByStatusAndGenerationEnqueuedAtIsNull` warning log is kept (uncapped by batch size) so the
  anomaly signal survives exactly as before — this item makes the row recover as well as get warned
  about, it does not remove the warning. **⚠️ First implementation was a live regression risk, caught by
  `advisor()` before commit:** it recovered every null-clock row unconditionally, with no age bound —
  unlike the self-service endpoint's `updatedAt` check, so a future non-atomic writer's in-flight
  generation would have been killed by the very next 10-minute sweep. Corrected to require
  `updatedAt.isBefore(cutoff)`, matching the endpoint's own rule; the fixture-driven test that first
  covered this (`note(null)` with no `updatedAt`) was itself rebuilt to a realistic row plus an added
  negative case (`updatedAt` 5 minutes old → left alone) that would have failed the original code.
  `docs/features/study-pack-generation.md` corrected to describe the sweep and the endpoint as two
  entry points to the same rule, not "left untouched" plus a separate manual-only path. Backend
  2434/2434 (full suite, including the real-PostgreSQL native-query harness).
- **Topic-note generation now carries the editor's Subject into generation context.**
  `GenerateNoteFromTopicRequest.java:11-35` accepts the optional, 64-character-bounded field while
  keeping the existing three- and four-argument Java constructors source-compatible (both are live:
  the three-arg form is still used by `StudyPackService.java:332`, the four-arg form by
  `NoteBulkGenerationService.java:311`); `NoteGenerationService.java:118-152` normalizes it once with
  `SubjectNormalizationUtils` and passes it through both the unchanged curator and learner resolver
  branches. The positional frontend API appends `subject` and omits blank values
  (`frontend/lib/api.ts:3603-3631`), while every note-editor call variant supplies the already-collected
  draft value (`note-editor-page-client.tsx:1143-1180`). Onboarding's separate two-argument call is
  unchanged. **⚠️ Pre-commit `npm run lint` found a real stale-closure bug in the Codex delivery:** the
  `useCallback` wrapping the generate-from-topic handler read `draft.subject` (via `resolvedSubject`) but
  omitted it from its dependency array, so typing Subject *after* Topic — a plausible order — would
  silently generate with the stale (often empty) subject captured at the callback's last recreation.
  Codex's own new test happened to type Subject before Topic, which recreates the callback via the
  already-listed `normalizedGenerateTopic` dependency and masked the gap. Fixed by adding `draft.subject`
  to the dependency array; a new regression test
  (`"uses the latest changed Subject even when it's typed after the Topic"`) exercises the reversed,
  bug-exposing order and was mutation-verified — confirmed failing against the pre-fix code, passing
  after. **No deploy-ordering statement needed:** `subject` is purely additive to an existing endpoint (no
  form's meaning changed, no field became required), and either deploy-skew direction only degrades
  generation quality rather than breaking a request. Coverage includes resolver-call and final context
  assertions for both branches, real `MockMvc` JSON binding plus over-length rejection before generation,
  the real frontend request body with present/blank subjects, and component calls with a selected,
  absent, or Subject-typed-after-Topic draft. Backend 2442/2442 (full suite, including the
  real-PostgreSQL native-query harness); frontend 2472/2473 with one pre-existing skipped test; frontend
  lint 0 errors (20 pre-existing warnings, back to baseline after the fix — 21 before it).
