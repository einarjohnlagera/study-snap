# RELEASES.md - NoteLib

## v0.154.0 - Closing the Loop

**Status: In Progress**

Theme: close out four independently-verified, gate-true Backlog Index items instead of one — none
gated on an owner action or a production read, none sharing a file or a shared method with any other,
each anchored to current code before being scoped rather than trusted from its row's prose.

### Planned Scope

- **Health-check-on-Hikari-pool decoupling (backend).** Direct continuation of `v0.153.0`'s Leg A2 —
  that release's own Backlog Index row explicitly flagged this as the one thing not folded in:
  `DataSourceHealthIndicator` is Spring Boot's default autoconfigured health check and shares the same
  Hikari pool it monitors, so pool saturation starves the health check itself and the platform restarts
  an instance whose only problem was that it was busy — the actual mechanism behind all four
  `v0.153.0`-era occurrences. Confirmed no custom `HealthIndicator` exists in
  `backend/src/main/java` before scoping. Shape: a liveness path that doesn't compete for a pool
  connection (a dedicated 1-connection validation datasource, or excluding `db` from
  `management.health` in favor of Leg A2's own saturation signal), not a general APM integration.
- **`course_programs.is_active` write path (backend + Admin frontend).** Confirmed dead column:
  `CourseProgramCatalogRepository.java` reads `is_active` in several places but no code anywhere in
  `backend/src/main/java` ever writes it; `CourseProgramCatalogService`/`Controller` have zero
  references. Adds the missing write path so Admin can actually deactivate a catalog program — the
  prerequisite for retiring the two legacy fused rows (`Nursing · Medicine`, `Nursing · Pharmacy`).
- **Recovery for `generation_enqueued_at IS NULL` notes stranded in `GENERATING` (backend).**
  `GenerationRecoveryService.java:107` explicitly skips this row class and logs "leaving them
  untouched"; every other stale `GENERATING` row is swept back within ~2h10m
  (`noteBoundMinutes` default 120 plus sweep cadence), but a row missing this timestamp never recovers
  — a genuine, user-visible stuck note. Root cause already diagnosed at kickoff; this release closes it
  rather than re-diagnosing it.
- **Topic-note generation passes `subject` into the LLM context (backend + frontend).**
  `GenerateNoteFromTopicRequest.java` carries `topic`, `courseProgramIds`/`courseProgramText` and
  `domainContext`, but no `subject` — `NoteGenerationService` builds context with `subject = null`, so a
  note authored under a specific subject via "Create from topic" never tells the model that. Degrades
  quality rather than failing requests. Must preserve ADR-001's hierarchy: Domain Context is the sole
  authoritative domain constraint, Subject only narrows within it.

Anti-drift: no APM/general observability layer for the health-check item — scoped narrowly to
decoupling liveness from the Hikari pool; no bulk `is_active` editor or catalog deletion, and no
`course_programs.program_family_id` write path revival; the `GENERATING`-recovery fix extends the
existing sweep's row selection, it does not change `noteBoundMinutes` or the sweep cadence; the
topic-note `subject` change does not let Subject override or compete with Domain Context per ADR-001,
and does not touch `courseProgramText`/`domainContext` resolution.

**Routing:** Claude Code inline for the health-check decoupling and the `GENERATING`-recovery fix
(isolated root cause, 1-3 files each); Codex for the `is_active` write path and the topic-note
`subject` context gap (new endpoint/DTO + multi-surface frontend each). **Verification tier:** each
item's own tier as scoped (direct verification for the two inline items, normal `/audit-diff` for the
two Codex items), plus one whole-release `advisor()` summary at signoff — no shared files or methods
between any of the four items, so no full pressure test is triggered by CLAUDE.md's own gate.

Carried forward from `v0.153.0`'s signoff, not this release's problem to solve: A1 (owner action —
enabling Render's own per-request logging) still not enabled as of `v0.153.0` signoff; the Leg A2
saturation detector's registry has no coverage of non-request threads (Known Limitation, not re-scoped
here).

### Shipped

_(nothing yet)_

## v0.153.0 - The Missing Telemetry

**Status: Released** (signed off 2026-09-18)

Theme: stop re-investigating the same unidentified production outage a fifth time, and ship the one
thing that would actually answer it — the diagnostic instrumentation this recurring failure has been
missing across all four occurrences so far. **Folded in 2026-09-17, mid-cycle, while this release was
still open: a second, unrelated fix (F1/F2 below) for a separate production-reliability gap found while
auditing an overdue product checkpoint** — a metadata field (Authored Depth) whose backlog was
discovered to be 3.5× larger than believed and actively growing. The two problems share no code, no
files, and no root cause; they are bundled here only because `v0.153.0` was still open when the second
one was scoped, per an explicit owner call to avoid opening a second release branch mid-cycle.
**⚠️ Verification-tier consequence of folding a second, unrelated item into an open release, stated per
CLAUDE.md's own rule:** this release is now four items (Leg A2, Leg B, F1, F2) across two unrelated
problem domains instead of two. Per-item tiers stay as declared for each (Leg A2 keeps its cold-agent
falsification pass; Leg B, F1 and F2 each get one `advisor()` call) — no item's own tier moves — but a
whole-release `advisor()` summary at signoff must now explicitly check the two halves don't interact
(they touch disjoint files: `backend/.../hikari`/`ThreadLocal` filter/`application.yaml` for the pool
work vs. `NoteBulkGenerationService`/`private-note-detail-page-client.tsx`/`bulk-generation-page-client.tsx`/
admin Applicable Programs for the depth work), and the per-PR `/audit-diff` stays scoped to whichever
half a given PR actually touches rather than being asked to reason about both at once.

Source: `docs/claude-plans/2026-09-17-pool-exhaustion-instrumentation-fix-plan.md` (Prod Investigator
session, written on request from a peer session relaying the owner's report that prod goes down almost
daily), built on `docs/claude-findings/2026-09-10-prod-pool-exhaustion-trigger-unresolved.md` (§11 adds
today's occurrence). **The `[CHECKPOINT — due 2026-09-17]` in `ROADMAP.md`'s Backlog Index fired at
kickoff:** today's incident (05:56:29–05:58:46 UTC, ~90s impact, already recovered) is a **fourth**
confirmed occurrence of the identical signature (2026-09-04, 2026-09-05, 2026-09-10, now 2026-09-17) —
HikariCP pool exhaustion (`active=20/20`) causes `DataSourceHealthIndicator` to starve on the same pool,
so the platform restarts an instance whose only problem was that it was busy. Every discriminating check
from the prior three investigations repeats identically: no connection leak (no hold ≥60s), no OOM, no
recent deploy, the database itself near-idle, nothing scheduled. Per the checkpoint's own stated kill
criterion, this release does **not** attempt a fifth root-cause hunt — three priors plus a cold-agent
falsification pass already narrowed the mechanism as far as existing telemetry allows (a non-DB,
non-CPU blocking wait under 60 seconds, under OSIV). **The owner asked directly whether this could be a
docker-compose / app-config issue: ruled out.** `docker-compose.yml` is local-dev-only (hardcoded
`localhost` values) and is never part of the deploy path — Render builds and runs `backend/Dockerfile`
directly under its own orchestration.

### Planned Scope

- **Leg A2 — Hikari-saturation-triggered diagnostic logging (backend).** When the pool is saturated
  (`activeConnections >= maximumPoolSize` with threads waiting, sustained), log which request paths are
  in flight at that moment — captured via a cross-thread `ConcurrentHashMap<Thread, InFlightRequest>`
  registry set in a servlet filter at request entry (alongside where `RequestIdFilter` already runs),
  polled against `HikariPoolMXBean` on a short interval. **This is the one thing that would have answered every one of
  the four incidents on the spot**, instead of leaving "narrowed, not identified" as the outcome each
  time. Scope is exactly detect-saturation-and-log-in-flight-paths — explicitly not a general APM
  integration.
- **Leg B — close the structural Tomcat/Hikari mismatch (backend, config).** `server.tomcat.threads.max`
  (currently 25, `application.yaml`) exceeds `spring.datasource.hikari.maximum-pool-size` (20), so under
  `spring.jpa.open-in-view: true` roughly 21 concurrent requests alone can exhaust the pool regardless of
  query speed. Lower `threads.max` to at or below 20, not raise the pool (raising it is an explicit
  non-fix — already tried once, 10→20 after 2026-09-04, and the identical failure recurred three more
  times at 20 since). This does not identify or fix whatever is actually holding connections for tens of
  seconds; it closes a different, independently-real exposure. **⚠️ Both values are
  `${ENV_VAR:default}` — `${SERVER_TOMCAT_THREADS_MAX:25}` and `${DB_POOL_MAX_SIZE:20}` — and Render
  environment variables cannot be read with any tool available to Claude (the only such tool is a write,
  which is the owner's). Before this ships, the owner must confirm on the Render dashboard's Environment
  tab whether either variable is set explicitly.** If `SERVER_TOMCAT_THREADS_MAX` is overridden, editing
  the YAML default is a silent no-op in production — the fix is then an owner-run env-var change, not a
  code diff, and the release notes must say which one actually happened.
- **A1 — Render platform request logging (owner action, not a code change).** Confirm in the Render
  dashboard whether per-request logging (path, status, duration) can be enabled for this service, and if
  so, enable it. Deploy/env/plan-tier actions are owner-only per standing rule; not part of this
  release's diff.
- **F1 — publication-time Authored Depth warning (frontend only).** Per
  `docs/claude-plans/authored-depth-legacy-backfill-audit-and-plan.md` (§F), a fresh audit found that a
  curator-owned public note with no Authored Depth is not merely unfilterable — it generates a less
  precisely calibrated Study Pack (no curriculum floor, ambiguous subject guidance). 282 such notes
  exist today, 173 created in the 30 days since `v0.83.0` shipped the Public Library `?level=` filter,
  entirely via the bulk-generate `makePublic` path, which never inspects depth. Add a non-blocking
  warning line — *"this note will not appear under any Authored Depth filter"* — to the existing *Make
  public* confirmation dialog (`private-note-detail-page-client.tsx`) and to the Bulk Generate form when
  `makePublic` is checked with no depth selected (`bulk-generation-page-client.tsx`). Publication still
  proceeds either way; this is copy plus one conditional in each of two existing components, no API
  change, no migration.
- **F2 — admin missing-depth count (multi-system).** Add a *missing Authored Depth* filter/count to the
  existing curator-scoped `/admin/course-programs` Applicable Programs surface
  (`AdminNoteApplicableProgramsController` / `admin-applicable-programs-section.tsx`), which today lists
  a curator's own notes but does not even carry `learnerLevel` in its response DTO. No new dashboard, no
  new route, no notification system — one column and one filter on a page that already exists for
  exactly this class of metadata repair.

Anti-drift, carried forward from the plan and the source finding, do NOT re-propose: raising
`maximum-pool-size` further (duration-bound holds, not throughput-bound — a bigger pool buys time
proportional to nothing); touching `spring.jpa.open-in-view` (real blast radius, needs a staging run
first, this incident does not change that calculus); adding PgBouncer (addresses too-many-clients, not
connections-held-too-long); chasing the "synchronous external call" lead from the finding's §11 without
new evidence (opened, not confirmed — Leg A2 is what would actually confirm or kill it on the next
occurrence). Leg A2 and Leg B are independent — neither blocks the other.

**Anti-drift for F1/F2, locked by the owner's decision and confirmed against current code by the audit
— do NOT re-propose:** inferring Authored Depth from Course/Program (different semantic axis;
`ADR-001:62,68,485`); adding a learner-facing "Unclassified" depth chip (describes curator metadata
quality, not a learner's desired level; current behavior — NULL-depth notes fully visible unfiltered,
excluded only by an explicit depth chip — already matches the requirement and needs no change); a hard
publication-time requirement as the first move (`NoteBulkGenerationService.java:336-346` swallows a
publish exception into `log.warn`, so a hard throw there would make a `makePublic` batch silently fail N
notes with a success receipt — F3, a hard requirement, is explicitly deferred pending a 30-day post-F1
inflow re-read); a bulk Authored Depth editor (no bulk write path exists for `learnerLevel` today — only
single-note create/update/copy touch it — and 80-plus one-time dropdown edits cost less than the
endpoint a bulk tool would need); retiring Public Library depth-based discovery (zero instrumentation
exists on that surface, so this checkpoint has no learner-demand evidence either way). Full audit:
`docs/claude-plans/authored-depth-legacy-backfill-audit-and-plan.md`.

Pre-declared guards (do not accept a diff without these — a detector that doesn't provably fire under
load, or that false-positives under ordinary load, is the same silent-no-op class this repo has shipped
twice before): (1) a test that actually saturates a small test Hikari pool and asserts the saturation
log line fires and names the blocking path, not just that the detector compiles; (2) a test asserting
the detector does **not** fire under ordinary, non-saturated concurrent load; (3) for Leg B, confirm the
application context still starts and a burst of ~20 concurrent requests queues at the Tomcat acceptor
rather than erroring, after lowering `threads.max`; (4) for F1, a test that actually renders each dialog
with the branch condition met (no depth + `makePublic`/publish) and asserts the warning copy appears —
not just that the component compiles; (5) for F2, if it adds any endpoint, one real `MockMvc` request
test with `.contentType(MediaType.APPLICATION_JSON)` per CLAUDE.md's non-negotiable rule for every new
endpoint.

**Routing: Codex** for Leg A2 (backend service + filter + config, anti-drift care against scope-creeping
into a general APM layer) and for **F2** (backend DTO + service filter + frontend section, multi-system,
via `docs/skills/codex-prompt-generator.md` — scope locked to one column + one filter on the existing
admin surface, no new dashboard/route/notification system). **Routing: Claude Code inline** for Leg B
(one YAML line, existing pattern, clear regression guard) and for **F1** (two existing components,
copy + one conditional each, well under ~50 LOC, no new infrastructure). **Verification tier: Leg A2 —
one scoped cold agent, falsification-framed** (recurring four-incident production-reliability history;
no auth/cross-user/money-semantics trigger fires on its own, but the incident history is reason enough
per the plan's own recommendation) — hand it the plan plus finding §11 and ask it to disprove that the
detector actually fires under load and doesn't false-positive under normal traffic. **Leg B, F1 and
F2 — one `advisor()` call each** on their diffs; none moves an authorization boundary, changes
money/quota/production-data semantics, or shares a method with another PR in this release. **No full
three-agent pressure test for either half** — neither meets any of that tier's triggers, and defaulting
to the heaviest option regardless is itself the error CLAUDE.md names.

**Backlog Index obligations, this release's own signoff:** (1) update the
`[CHECKPOINT — due 2026-09-17]` pool-exhaustion row — its kill criterion fired, scope changes from
"identify the trigger" to "ship the instrumentation that would identify it," not resolved until Leg A2
has shipped and fired at least once (in the guard test per above — a fifth production occurrence is not
something to wait for). If Leg A2 ever does capture a real trigger on a future occurrence, that is a
new, separate findings file, not a retrofit into the "trigger unresolved" title. (2) The Authored Depth
row (`ROADMAP.md`, `v0.83.0 — will curators actually classify…`) needs a new
`[CHECKPOINT — due <F1 deploy + 14 days>]` added for the manual cleanup's completion re-read (kill
criterion, stated now: if the checkpoint query still returns more than 10 unclassified notes at that
read, escalate to tooling per the audit's §H re-evaluation, not a third extension) — the exact date
depends on when F1 actually deploys, so it cannot be written until then.

### Shipped

- **Hikari saturation request-path diagnostics (Leg A2).** A servlet filter now keeps a cleanup-safe, thread-keyed snapshot of request paths currently in flight. A fixed-delay detector reads the live Hikari MXBean every two seconds and, after two consecutive samples with `activeConnections >= maximumPoolSize` and waiters present, logs the pool counts and every request in flight at saturation. It emits once per saturation episode, rearms after recovery, and disables safely for a non-Hikari datasource. Real-pool guards exhaust a two-connection Hikari pool and prove the warning names the tracked path, while false-positive and throwing-filter guards prove a single blip, ordinary load, and request failures do not leave misleading telemetry. `InFlightRequestTrackingFilter` is now pinned `@Order(HIGHEST_PRECEDENCE + 1)`, ahead of the Spring Security chain, so a connection held inside `JwtAuthenticationFilter`'s per-request user lookup is visible to the snapshot rather than silently excluded (found by the release-wide Opus falsification pass below; the original filter order was Spring's default `LOWEST_PRECEDENCE`, which placed it after security).
- **Curator-owned Authored Depth cleanup queue (F2).** The existing Admin Applicable Programs table now displays each owned note's Authored Depth and can filter to notes where it is missing. The filter preserves the page's requester-owner scope, visibility-agnostic population, pagination, and `updatedAt DESC` order; depth remains editable only from the existing per-note editor.
- **⚠️ Leg B — code half only. NOT effective in production yet.** `server.tomcat.threads.max`'s YAML default lowered 25→20→**18** (see the correction below for why 20 wasn't the final value), and `TomcatThreadPoolHikariAlignmentTest` pins `threads.max < hikari.maximum-pool-size` — a **strict** inequality, algebraically (re-read from both files every run, not a hardcoded pair of numbers) — so the two settings can't silently drift apart again. **The owner confirmed on the Render dashboard (2026-09-17) that `SERVER_TOMCAT_THREADS_MAX=25` is set explicitly there — this overrides the YAML default entirely, so production is still running at 25 today and this fix does nothing until the owner changes or removes that variable.** Recommendation: **delete** the Render env var rather than set it to a number, since deleting it also removes the shadowing that made this a live question — but it's the owner's call. The pre-declared guard "confirm a burst of ~20 concurrent requests queues at the Tomcat acceptor rather than erroring" is intentionally NOT covered by a bespoke test: that behavior is standard Apache Tomcat NIO-connector queueing, not code this repo owns, and building the codebase's first full-embedded-server concurrency test to re-prove a 20-year-old servlet-container feature would exceed this leg's own declared `advisor()`-only verification tier. Stated here explicitly rather than silently assumed.
- **⚠️ Correction (Opus falsification pass) + final value decision: 20 was tried first and found insufficient; shipped at 18.** At the originally-shipped equality (20 == 20), this closed only the OVERFLOW exposure (a 21st–25th admitted request exhausting the pool by itself) — it did NOT close the STARVATION mechanism actually behind all four outages. `spring.jpa.open-in-view` is unset, so Boot's default (`true`) applies and a connection is held for a request's whole lifecycle; 20 fully-concurrent requests alone could still consume every connection and leave zero for `DataSourceHealthIndicator`, reproducing the exact `active=20/20` signature. The test's own docstring previously overclaimed "close this specific structural exposure" without that qualification — corrected. **Owner decision after reviewing the tradeoff explicitly (queueing at the Tomcat acceptor under a 2-thread-narrower ceiling vs. reserved health-check/scheduled-job headroom): lower to 18**, reserving 2 connections of headroom. This meaningfully reduces the odds of recurrence; it is **not** an absolute guarantee, since the registry/pool-coverage Known Limitation below (2 executors + 16 `@Scheduled` jobs drawing on the same pool, unbounded by `threads.max`) means a health check can still theoretically lose a race against those. `TomcatThreadPoolHikariAlignmentTest` and `application.yaml`'s comment both updated to the strict-inequality framing.
- **Publication-time Authored Depth warning (F1).** A non-blocking warning now appears in both real publish surfaces that reach `performVisibilityUpdate("PUBLIC")` for an individual note — the "Make this note public?" confirmation and the "This note is private" → "Publish & Share Link" dialog (`private-note-detail-page-client.tsx`) — plus the Bulk Generate form's Public toggle when no Authored Depth is selected (`bulk-generation-page-client.tsx`). Publication always proceeds either way; the warning only tells the curator the note will not surface under any Authored Depth filter until one is set. Scope grew by one dialog beyond the original two named surfaces: the private-share modal's own "Publish & Share Link" button calls the identical publish path and was silently missing the warning otherwise. Guarded by real-render tests asserting the warning appears exactly when depth is unset and disappears once it is set, across all three surfaces.
- **Pre-signoff Opus falsification pass, all four shipped items, all 19 pre-declared claims CONFIRMED.** Escalated past the pre-declared per-item tiers (Leg A2's own scoped Sonnet cold agent; one `advisor()` call each for Leg B/F1/F2) at the owner's explicit request given the production-reliability stakes — one `model: opus` cold agent, no inherited context, falsification-framed across the whole release rather than three separate agents. Verified empirically throughout: a genuinely exhausted real Hikari pool, a mutated YAML value that correctly failed the alignment test, a full Spring context boot, and real `MockMvc` requests — not read-only inspection. No code defect found; every claim about detection reliability, false-positive avoidance, cleanup-on-throw, fail-safe behavior, publish-path correctness, and query scoping/visibility/serialization held. It did surface written claims that outran what the code/tests actually proved, all fixed in this same release rather than carried forward silently: the `@Order` fix and Leg B docstring correction above, plus two new tests (`rearmsAfterRecovery`, `doesNotLogUnderOrdinaryConcurrentLoad` — the latter closing this release's own pre-declared "does not false-positive under concurrent load" guard, which the original suite tested single-threaded only) added to `PoolSaturationDetectorTest`. See Known limitations for the one gap left open rather than fixed: the saturation detector's registry has no coverage of non-request threads.

### Known limitations

- **RESOLVED (Opus falsification pass, this cycle).** `InFlightRequestTrackingFilter` previously had no explicit `@Order` and ran after Spring Security (measured: `LOWEST_PRECEDENCE` vs. security's `-100`), so a connection held inside `JwtAuthenticationFilter`'s DB lookup was invisible to the snapshot — a present gap, not the hypothetical one originally recorded here ("correct today ... would silently break if a future filter changed that assumption" was itself inaccurate). Fixed: `@Order(Ordered.HIGHEST_PRECEDENCE + 1)`, mirroring `RequestIdFilter`.
- **The saturation registry has no coverage of non-request threads — the larger of the two remaining gaps, not fixed here.** `llmParallelTaskExecutor` / `studyPackGenerationTaskExecutor` (used by `LongExamService`, `ExamQuestionPoolService`, `AdminStudyPackService`, `OfficialChallengeQuizTemplateService`) and 16 `@Scheduled` jobs never pass through the servlet filter that populates the registry. A connection held by a generation task across a slow OpenAI call — precisely the unconfirmed "synchronous external call" lead this release's source finding carries — produces `requests in flight at saturation=[]`, which is ambiguous between "nothing was in flight" and "the holder was never eligible for the registry." Not fixed in this release: closing it means deciding whether non-request threads should register themselves too, a larger design question than this release's scope. Read the empty-list case with this caveat during incident five.
- **`PoolSaturationDetector.poll()` shares Spring's default single-threaded scheduler with 16 other `@Scheduled` jobs, several DB-bound** (`GenerationRecoveryJob` every 10 min, `BulkGenerationResultCleanupJob` and `NotificationCleanupJob` hourly, plus the rate-limit purges). During saturation, any of those blocking on a connection up to `connection-timeout: 5000` stalls the 2-second poll for that duration — a latent detection-latency risk, count corrected from the original "several other low-frequency jobs" to the actual 16. `spring.task.scheduling.pool.size: 2` would remove it; not changed here to keep this leg's diff minimal.
- **`sanitize()` on the logged request path strips only `\n`/`\r`, with no length bound or control-character stripping beyond that.** Low severity, since it fires only during genuine saturation on a codebase with no existing log-injection-hardening convention to hold it against. Unchanged from the original finding.
- **`InFlightRequestTrackingFilterTest` drives the filter directly (`filter.doFilter(...)`) rather than asserting it is actually registered in the chain or at what position.** Passes by construction regardless of registration — the same shape as the `v0.119.0` `Content-Type` defect class CLAUDE.md names. The `@Order` fix above was verified by booting the real Spring context during the falsification pass, not by this unit test; no regression guard exists for the ordering itself. Flagged, not fixed — would need a `@SpringBootTest` asserting filter registration order, judged not worth the cost for a one-line annotation.
- **A1 (owner action, Render per-request logging) — checked at signoff, confirmed NOT enabled, still open.** The owner reported having heard it was on by default; verified otherwise via a read-only `list_log_label_values` query against the production service's logs (`type` label returns only `["app", "build"]` across the prior ~28 hours — no `request` type exists at all), plus a direct spot-check of a live hour showing only Spring Boot application/job log lines, no per-request path/status/duration entries. Enabling it (a paid add-on or plan-tier feature on Render, not a code change) remains the owner's own action, not done as of this signoff.

## v0.152.0 - The Missing Half of v0.150.0

**Status: Released** (signed off 2026-09-17)

Theme: give the many-to-many Program Family architecture (v0.150.0) the curator UX it needed to
actually get finished — family-first Admin management, one canonical catalog-create modal, and an
additive backfill of the approved initial membership matrix.

Source: `docs/claude-plans/program-family-catalog-management-ux-overhaul-plan.md` (FINAL, owner-approved,
subagent audit + owner-tightening pass; untracked on disk, indexed in `ROADMAP.md`'s Backlog Index at
this kickoff) and its companion Codex prompt `docs/codex-prompts/v0.152.0-program-family-catalog-management.md`
(Long mode, Slices 1-3 only). **Why now, from production data, not a redesign impulse:** Engineering
(18/18) and Education (8/8) were fully populated the day `v0.150.0` shipped; three weeks and one release
later, Health Sciences and Computing & Technology are still at zero members, Accounting 2/5, Built
Environment & Design 1/8. The many-to-many data model did not fail — the one-program-at-a-time admin
workflow (open a program, pick its one family from a `<select multiple>`, repeat) made finishing the
backfill through it tedious enough that it didn't get finished. This release is the missing curator UX,
not a data-model change.

### Planned Scope

- **Slice 1 — Backend catalog contracts + data (backend).** `POST /course-program-catalog/families`
  gains optional `programIds` (atomic create-with-members, mirroring the existing program-side
  `create()` shape); new `PATCH /course-program-catalog/families/{id}` (rename + family-side membership
  replace, one transaction); the family duplicate-name predicate is weakened relative to the program
  one (`lower(trim(name))` vs. `regexp_replace`-whitespace-collapsing) and gets aligned; rename adds
  `id <> ?` self-exclusion so renaming a family to a case/whitespace variant of its own name doesn't
  reject itself as a conflict with itself. New migration `V147__program_family_initial_membership.sql`
  — purely additive, exact-name inner joins over a locked 50-pair matrix, `ON CONFLICT DO NOTHING`, no
  `RAISE`, no fuzzy matching, does **not** write the vestigial `course_programs.program_family_id`.
  Production: 29 existing pairs untouched, 21 new rows inserted (Health Sciences 5, Accounting 3,
  Computing & Technology 6, Built Environment & Design 7), `course_program_family` goes 29→50.
- **Slice 2 — Shared catalog selection + creation UX (frontend).** New `CatalogMultiSelect`
  (`components/ui/catalog-multi-select.tsx`) — a searchable, client-side-filtered checkbox picker with
  a `selectedSummary: "count" | "chips"` density prop, replacing both remaining raw `<select multiple>`
  instances in the codebase. New `CourseProgramCreateModal` extraction, mounted from both Admin and the
  three authorized Note-authoring surfaces, collapsing today's two divergent create forms (Admin's
  weaker single-family form vs. the note-authoring modal's already-multi-family one) into one component,
  one contract, one validation path.
- **Slice 3 — Family-first Admin IA (frontend).** `/admin/course-programs` gains a two-tab switch
  (`?view=families|programs`, URL-reflected), Program Families as the default/primary tab (a table:
  name, member count, Edit — zero-member families included, not `is_active`-filtered), Course / Programs
  demoted to the inverse-convenience secondary tab. Removes the permanently-visible inline "New Program
  Family" box and inline create grid in favor of header `+` buttons opening modals.
- **Slice 4 — Verification + production acceptance + docs (Claude Code, not sent to Codex).** One
  scoped cold agent, falsification-framed, on the shared catalog create/membership path (7 claims, see
  below). Post-deploy production acceptance is an anti-join of the same 50-pair matrix against
  `course_program_family` (expect 0 missing pairs) — the primary proof, not a family-count check, since
  a count can be right for the wrong reason. `docs/features/program-families.md` rewritten to correct
  its now-false "a family is created empty" and "membership is set on program creation or edited later
  from the Admin catalog row" claims.

Anti-drift, owner-locked: **no ADR-001 amendment** (its amended clause 2 is already storage-neutral and
ratifies many-to-many; nothing here changes what expansion means, only who can edit membership from
which side). **Program Family name is display data, Program Family ID is identity** — V147's exact-name
matching is a scoped migration-only exception (runtime-generated UUIDs, no portable literal) and must
not be copied into any application code. No family deletion, no program deletion, no `is_active` write
path, no `Business & Finance` family, no general Popover/Command primitive — the new control is a
catalog picker for small in-memory lists, not a platform layer. The legacy fused rows (`Nursing ·
Medicine`, `Nursing · Pharmacy`) stay in the catalog, unassigned, not folded into Health Sciences.
`course_programs.program_family_id` stays vestigial — not written, not dropped. No Program Family
reaches a prompt, is persisted on a Note, or triggers a live update to existing Notes — that boundary is
untouched by a management view, a rename, or a backfill.

**Routing: Codex** (new endpoint + migration + service logic, multi-system frontend+backend, ~17
must-change files — three independent task-routing triggers). Prompt already written (Long mode, Slices
1-3 only; slice 4 is this session's own work after the diff returns). **Verification tier: one scoped
cold agent, falsification-framed** — elected now rather than deferred to signoff, because all three
implementation slices touch the shared catalog create/membership path (CLAUDE.md's "two or more PRs
touched the same shared method" trigger). Seven claims to disprove: (1) family-side replace cannot evict
a program from another family; (2) rename preserves id, every membership, and every note's
applicability; (3) V147 is additive, idempotent, and cannot fail a fresh-database Flyway run; (4) V147
does not write `course_programs.program_family_id`; (5) no ordinary user can create a shared catalog
entry through any path; (6) creating a program with two families adds only that program to the note;
(7) the new multi-select's checkbox `checked` state is real, not `AddNotesModal`'s list-membership hack.
Full scope, all owner-tightened decisions, and the production membership audit are in the plan file.

### Shipped

- **Backend catalog contracts and initial membership data.** Program Families can be created with initial members and renamed or full-set edited by UUID through an ADMIN-only endpoint. Family-name duplicate matching now collapses internal whitespace and excludes the renamed row itself. `V147` additively declares the locked 50-pair matrix with exact-name joins and `ON CONFLICT DO NOTHING`; it neither deletes memberships nor writes the vestigial scalar family column.
- **One shared catalog selection and program-creation flow.** `CatalogMultiSelect` replaces both raw multi-selects with searchable native-checkbox editing in count and chip modes. `CourseProgramCreateModal` now serves Admin and authorized Note-authoring surfaces, supports several families, preserves Exam Goal behavior, and selects only the newly created program on the current Note.
- **Family-first Admin catalog management.** `/admin/course-programs` now opens on a URL-reflected Program Families tab for counts, create, rename, and family-side membership replacement. The retained Course / Programs tab provides the inverse per-program workflow and opens `+ New program` in the shared modal.
- **Cold agent falsification pass: all seven pre-declared claims CONFIRMED.** Five of the seven are backed by real-database (Testcontainers PostgreSQL) or real-HTTP-request (MockMvc with a live `@PreAuthorize` interceptor) tests, not mocked assertions. The pass surfaced one previously-unflagged, out-of-scope-of-the-seven-claims defect: the Admin rename modal always re-sent the family's full membership set even when only the name changed, using a stale snapshot that could silently overwrite a concurrent admin's membership edit on the same family (never crossed family boundaries, never touched note applicability, never corrupted data — a lost-update window, not a correctness break). Fixed in the same release rather than carried as a Known limitation, since the feature had not yet deployed: `AdminProgramFamiliesSection`'s save path now omits `programIds` entirely unless the picker was actually touched (`draft.membershipDirty`), so an ordinary rename is a true no-op on membership. Two tests added distinguishing the rename-only and rename-plus-membership-edit cases.
- **Feature-doc sweep, signoff gate.** Corrected two `docs/features/notes.md` claims stale since `v0.150.0`'s many-to-many migration (family expansion described as reading the vestigial scalar `program_family_id` column instead of the `programFamilies` join; catalog creation described as single-family-only instead of the list `CreateCourseProgramRequest.programFamilyIds` has supported since before this release). `docs/features/program-families.md`'s membership-replace description was missing half its own contract — added the omitted-vs-explicit-empty distinction the #1409 fix depends on.

### Known limitations

- **RESOLVED 2026-09-17 (during the `v0.153.0` cycle).** The production-acceptance anti-join (this release's own Slice 4 proof) ran against production (read-only) once `main` had deployed on `2547da67`: **0 missing pairs** across the full 50-pair matrix — `V147` did exactly what this release claimed. Extras report: 7 pairs present in production but outside the approved matrix (6 Accounting — `Business Administration`, `Chartered Financial Analyst`, `Economics`, `Entrepreneurship`, `Finance`, `Financial Management`; 1 Engineering — `Manufacturing Engineering`), consistent with ordinary post-deploy curator work, not a defect. Count sanity check reconciles exactly (57 = 50 + 7). Closes the `[CHECKPOINT — due 2026-09-24]` row in `ROADMAP.md`'s Backlog Index.
- **A rename that also edits membership still computes its full replacement set from an in-modal snapshot.** The #1409 fix closed the lost-update window for a rename-only save (which now omits `programIds` entirely), but an admin who *does* touch the membership picker still sends a full set read at modal-open time — a genuine concurrent edit during that window is still last-write-wins. Inherent to full-set replace; fixing it is optimistic concurrency, a different feature, not scoped here.
- **`course_programs.is_active` still has no write path anywhere in the codebase.** Unchanged by this release, deliberately — see the "Course / Program catalog lifecycle management" Backlog Index row. This release's own Admin family/program editors already use the unfiltered catalog specifically so an eventual inactive row stays manageable, but nothing can set `is_active = false` today.

## v0.151.0 - No Backdoor Left, Round Two

**Status: Released**

Theme: close the same gate gap `v0.143.0`/`v0.144.0` already closed for the exam question pool, this
time for shared quiz links.

Source: `docs/product/ROADMAP.md` Backlog Index row, found 2026-09-11 while tracing `v0.143.0` item 2's
scope, verified not-currently-live at kickoff (re-run 2026-09-16, unchanged since 2026-09-12: exactly
1 active `quiz_share_links` row, its `generated_quizzes.generated_at` predates the link's own
`created_at`, so it is not exposed to a post-share content change).

### Planned Scope

- **Shared quiz links are not deactivated on a `STUDY_PACK`-only regeneration (backend, 2 files).**
  `StudyPackService.java:928` calls `generatedQuizService.deactivateShareLinksForNote(noteId,
  ownerUserId)` only inside `if (regeneratingNoteContent)` — the combined Note+Study-Pack
  regeneration path. `POST /notes/{id}/regenerate` defaults to `NoteRegenerationScope.STUDY_PACK`
  (an absent/blank scope resolves to it), which reaches the same shared worker method with
  `regeneratingNoteContent = false`, so the deactivation never fires on that path even though
  `saveStudyPack` replaces the quiz content either way. Fix: drop the call out of the `if` gate, same
  as `v0.143.0` already did one line above it for `examQuestionPoolService.refreshPool`.
  **⚠️ SCOPE GREW MID-IMPLEMENTATION, found by the full backend build, not by the original scoping:**
  `NoteRegenerationConsequenceService.notesWithLiveShareLink` (the bulk-regeneration path's
  consequence-counting method, backing both the preflight modal's `sharedQuizzesToDeactivate` count
  and `NoteBulkRegenerationService`'s per-item `hadLiveShareLink` receipt flag, captured *before*
  dispatch from the same method) carried the identical scope gate, deliberately mirrored to match the
  single-Note primitive's then-current (buggy) behavior. Fixing only `StudyPackService` would have made
  the bulk path actively **worse**: the preflight would promise zero deactivations for a
  `STUDY_PACK`-only batch, the run would deactivate some anyway, and the receipt — reading the same
  gated method — would falsely confirm nothing happened. Fixed together: the gate condition in
  `notesWithLiveShareLink` was removed (scope no longer distinguishes any share-link consequence, since
  `saveStudyPack` replaces the quiz for either scope); its stale Javadoc, which justified the gate as
  intentional, was removed. **The confirmation dialog inherited the same assumption**:
  `bulk-regenerate-modal.tsx` gated its shared-quiz warning behind `combined &&`, so even a fixed
  backend would have shown a curator zero warning on the default `STUDY_PACK`-only scope; that gate is
  dropped too, and its component test (which had asserted the warning's *absence* on `STUDY_PACK` as
  correct) is corrected along with it. Two feature docs stated the old scope restriction explicitly and
  are corrected: `docs/features/bulk-regeneration.md` and `docs/features/study-pack-generation.md`.
  Isolated bug fix, clear root cause once traced — Claude Code implements inline, no Codex prompt.

Anti-drift: no other regeneration-path behavior changes; the two `refreshPool` calls immediately above
`StudyPackService`'s deactivation call are already unconditional and stay untouched; the
note-generation-unit meter stays genuinely scope-specific (STUDY_PACK-only still spends zero) —
unrelated to this fix and not touched by it.

Verification tier: **one `advisor()` call on the diff plus one scoped cold agent at signoff, falsification-framed.**
`advisor()` judged the diff itself (a two-line gate removal plus its stale Javadoc, covered end-to-end
by a real-Postgres integration test) adequate for a single `advisor()` call. At signoff the owner asked
for a cold agent if a pressure test was warranted — one of this repo's own triggers had in fact fired:
the implementing session's own first-pass delivery (fixing `StudyPackService` alone) was itself an
incomplete blind spot the full build caught mid-session, and a second one (the frontend modal) was
caught the same way after that — a measured blind-spot signal. The cold agent (`model: sonnet`, fresh
context) was handed 7 specific claims to disprove across the backend, the bulk driver, and the frontend
modal. 5 REFUTED outright (meter untouched, first-ever-generation no-op, frontend warning correctness,
single-note/bulk-list consistency, double-deactivation safety). 2 surfaced real but narrow, **pre-existing**
gaps in the bulk-regeneration design, not introduced by this diff — see "Known limitations" below.

`GeneratedQuizService.deactivateShareLinksForNote`'s existing null/empty-guard (read, not re-tested)
makes a first-ever-generation no-op safe by construction, and is exercised incidentally by every other
bulk-regeneration test that seeds no quiz. One added cost, not worth a test: `notesWithLiveShareLink`
now runs its lookup on every `STUDY_PACK`-only item instead of short-circuiting immediately, one extra
empty query per note with no existing quiz.

### Known limitations (found by the signoff cold agent, pre-existing, not introduced by this fix)

- **Readiness-window race can make the preflight's `sharedQuizzesToDeactivate` count OVERSTATE what a
  batch actually deactivates — the safe direction, not a correctness hole.** The preflight counts a note
  as READY-with-a-live-link at preflight time; `NoteBulkRegenerationService.processItem` re-evaluates
  readiness per-note at dispatch time and returns `BLOCKED`/`NOT_ELIGIBLE` before `hasLiveShareLink` is
  even read if the note's readiness changed in between (e.g. its Domain Context was cleared by a
  concurrent edit). That note is never dispatched, so its content (and its shared quiz) is never
  replaced, and correctly not deactivated — the preflight simply counted a consequence that then didn't
  happen, same as it would for the regeneration itself. This is the existing "preflight is a snapshot,
  not authoritative" behavior `docs/features/bulk-regeneration.md` already documents, applying uniformly
  to the share-link count too; not specific to this fix and not fixed here.
- **Narrow TOCTOU on the per-item receipt's `shareLinkDeactivated` flag.** `NoteBulkRegenerationService`
  captures `hadLiveShareLink` synchronously before `dispatchItem`, then reuses that boolean for the
  receipt once the async worker finishes seconds-to-minutes later. If a share link is newly created on
  that note's quiz in that window, the (unconditional) deactivation call still deactivates it, but the
  receipt records `false` — a stale prediction rather than a fresh read. Narrow (requires a share link
  created mid-item-processing) and not a regression from this diff; flagged as found, not fixed.

### Shipped

- **Shared quiz links now deactivate on either regeneration scope** (backend, bulk-consequence path,
  and the confirmation dialog). PR #1406, commit `aea7c12f`, merged to `releases/v0.151.0` as
  `f9013f5c`. `StudyPackService.java:919` calls `deactivateShareLinksForNote` unconditionally;
  `NoteRegenerationConsequenceService.notesWithLiveShareLink` dropped the identical scope gate backing
  the bulk preflight count and per-item receipt; `bulk-regenerate-modal.tsx` dropped the matching
  `combined &&` gate on its warning copy. `docs/features/bulk-regeneration.md` and
  `docs/features/study-pack-generation.md` corrected to match. Backend 2403/2403, frontend 2450/2451
  (1 pre-existing unrelated skip), `tsc --noEmit` clean. `ROADMAP.md` Backlog Index row updated with
  file:line evidence.

## v0.150.0 - Membership, Not a Slot

**Status: Released**

Theme: Program Family membership becomes many-to-many — a Course/Program can belong to zero, one, or
several families — closing a production bug where two admin-created families (Health Sciences,
Accounting) were structurally invisible to every Note-authoring surface, and where an existing
program's family could not be changed at all except by a database migration.

Source: `docs/claude-plans/program-family-many-to-many-final-plan.md` (FINAL, Opus architecture audit,
independently verified by the Feature Planner session 2026-09-15; owner-approved 2026-09-16). Supersedes
`docs/claude-plans/program-family-health-accounting-expansion-final-plan.md` (pass 2) on the schema
question only — that file's Health Sciences/Accounting membership decisions carry forward unchanged;
its single-FK schema, API and migration sections do not. Codex prompt:
`docs/codex-prompts/v0.150.0-program-family-many-to-many.md` (gitignored, not committed).

### Planned Scope

- **ADR-001 amendment (docs-only, Slice 0).** Constraint 2 (`ADR-001:92`) currently forbids "any preset
  table beyond `course_programs.program_family_id`" — a literal blocker for a membership table. Owner
  approved storage-neutral replacement text (plan §A) that keeps the constraint's substance (unconditional,
  membership-driven expansion) while permitting many-to-many storage.
- **`course_program_family` migration (backend).** New join table copying every existing single-FK
  membership (Engineering 18, Education 8 = 26 rows), with a relationship-level (not count-only) parity
  assertion that aborts the migration on any mismatch. `course_programs.program_family_id` is retained,
  unread by application code after cutover — no dual-write.
- **Catalog API becomes additive (backend).** `GET /course-program-catalog` gains `programFamilies: []`;
  deprecated `programFamilyId`/`programFamilyName` stay populated (alphabetical-first) for one release of
  frontend-deploy tolerance. `PATCH /course-program-catalog/{id}` becomes an authoritative
  `programFamilyIds` replacement — a free breaking change, since it has zero existing frontend clients.
- **Note-authoring bug fix (frontend).** The "Add Course/Program" family picker currently derives its
  options by scanning catalog rows that already carry a family, so a brand-new empty family is invisible
  to it — exactly what happened to Health Sciences and Accounting in production. Fixed by fetching the
  canonical `/course-program-catalog/families` endpoint instead, same one Admin already uses.
- **Admin Edit action (frontend, new).** Admins can edit an existing Course/Program's family memberships
  through a multi-select modal — this did not exist at all before this release, despite `v0.149.0`'s
  release notes claiming it did (see Corrections below).
- **Populate all four empty families (owner-run, post-deploy).** Health Sciences, Accounting, and the
  two owner-approved additions Computing & Technology and Built Environment & Design (17 memberships
  total) — via the Admin UI as the primary path, which doubles as this release's own production
  acceptance test.

### Corrections to the v0.149.0 record

Verified against current code and production, not inferred, per the many-to-many plan's audit:

- **`v0.149.0`'s release notes claim "Admins can now move an existing Course/Program catalog entry into
  a different family." They cannot, through any UI.** The `PATCH /course-program-catalog/{id}` endpoint
  shipped and is well-tested, but no frontend client ever called it — `admin-course-program-catalog-section.tsx`
  has no Edit action and `frontend/lib/api.ts` has no `updateCourseProgram` function.
- **`v0.149.0`'s release notes claim "A catalog program can now be marked inactive." No application code
  ever writes `is_active`.** New rows get `true` only from the column's DB-level `DEFAULT` (`V145`) — the
  `INSERT` statement's own column list does not include `is_active` — and there is no `UPDATE`, endpoint,
  or admin control to change it after creation. Production confirms 0 rows with `is_active = false`. This
  also means the Known Limitation recorded as "documented for the next post-deploy pass" (the two legacy
  fused rows' deprecation) was never actually reachable by any owner action — it needed a code change that
  was never scoped, not a data operation that was merely pending. Tracked as its own Backlog Index item;
  out of scope for this release (plan §P item 4).

Anti-drift: Program Family stays an authoring convenience only — never Note-persisted, never a discovery
axis, never Domain Context, never Authored Depth, never sent to generation. Exam Goal editing is dropped
from this release entirely (not even read-only display). `is_active`, the two legacy fused catalog rows,
family deletion, program deletion, and family-side membership editing (Family → Programs) are all
explicitly out of scope. No react-query/TanStack/websocket/polling is introduced — this frontend has no
query cache today and this release adds none.

### Shipped

- **Program Family membership is many-to-many end to end.** `V146` adds and relationship-validates the
  canonical `course_program_family` join while retaining the legacy scalar FK as an unread compatibility
  artifact. Catalog create and Admin Edit now write complete membership sets atomically; catalog responses
  expose ordered `programFamilies` while retaining deprecated scalar aliases. The Note-authoring Add
  Course/Program modal reads the canonical families endpoint lazily, so empty families are selectable on
  Single Note and Bulk Note surfaces, while expansion chips still appear only for families with members.
  The Admin catalog now displays zero/one/many family chips and provides the working Edit UI path that
  `v0.149.0` had overclaimed.
- **Pre-signoff falsification pass (one scoped cold agent, per plan §Q) confirmed 8 of 9 pre-declared
  claims cleanly and found one real test-quality gap, fixed before signoff.** Confirmed: migration
  relationship-parity (proven against a real PostgreSQL container, not just the H2 harness), no
  dual-write to the legacy scalar column, no family id ever reaching Note persistence, unchanged
  `@PreAuthorize` annotations, overlapping-family deduplication, honest documentation of what the H2
  migration test does and doesn't execute, tolerant JSON parsing across the deploy window, and
  `is_active` genuinely untouched. **Found and fixed:** the single highest-value new test — creating a
  program in two families must select only that program on the Note — used non-exclusive
  `toHaveBeenCalledWith`; a mutation (adding a `handleFamilyExpansion` call the boundary forbids) proved
  the old assertion would still pass. Strengthened to `toHaveBeenCalledTimes(1)`, re-verified the same
  mutation now fails and the real implementation still passes all 28 tests in the file.

## v0.149.0 - Precision Before Coverage

**Status: Released**

Theme: two new Program Family shortcuts for curators (Health Sciences, Accounting), built on the
existing generic family mechanism, plus the admin capability and legacy-catalog cleanup needed to
maintain families going forward without another release.

Source: `docs/claude-plans/program-family-health-accounting-expansion-final-plan.md` (FINAL, Product
UX-approved, tightening pass 2 of 2; untracked on disk, indexed in `ROADMAP.md`'s Backlog Index).
Supersedes `docs/claude-plans/program-family-health-accounting-expansion-product-ux-consultation-prompt.md`
(pass 1) — that file's facts are preserved as historical trace only; do not re-read it for anything
load-bearing.

### Planned Scope

- **`is_active` lifecycle column on `course_programs` (backend, migration).** `course_programs` has no
  lifecycle field today (`id, name, program_family_id, exam_goal_slug, created_at` only — confirmed
  against current migrations at kickoff). Adds `is_active BOOLEAN NOT NULL DEFAULT TRUE`, reusing the
  existing `discount_vouchers`/`quiz_share_links` convention rather than inventing a new one (confirmed:
  both already carry `is_active BOOLEAN NOT NULL DEFAULT TRUE`). This migration adds the column ONLY.
  **⚠️ ANTI-DRIFT: do NOT bundle the 2-row backfill (below) into this same migration** — see the
  deployment-ordering item.
- **Legacy fused catalog rows deprecated from new authoring, not deleted (backend, follow-up
  step).** "Nursing · Medicine" (20 notes) and "Nursing · Pharmacy" (1 note) get `is_active = false`,
  targeted by name (not id, since ids are runtime-generated). **A SEPARATE step from the column-add
  migration, deployed only after Health Sciences family population is confirmed live in production** —
  bundling them would strand curators between losing the fused shortcut and gaining its replacement.
  Existing Notes referencing these rows are never touched; `course_programs` rows are never deleted.
- **`PATCH /course-program-catalog/{id}` — new ADMIN-only endpoint (backend).** No endpoint exists today
  to reassign an *existing* catalog program's family (confirmed: `CourseProgramCatalogController` is
  GET-only at kickoff). `UpdateCourseProgramCatalogRequest(UUID programFamilyId)` — nullable;
  `null` clears membership, a valid id sets/changes it. `@PreAuthorize("hasRole('ADMIN')")`, mirroring
  `NoteCollectionController`'s existing `PATCH /{id}` convention. Real `MockMvc` request test with
  `Content-Type: application/json` required (not a bare service-method call — this repo's own `v0.119.0`
  lesson), plus ADMIN-only guard, assign/change/clear, unknown-program, unknown-family cases.
- **List-endpoint filtering (backend).** The authoring combobox's pickable list excludes `is_active =
  false` rows; a Note that already references an inactive row must still resolve and render it as a
  normal chip (fetch selected-by-id regardless of `is_active`, filter only the *pickable* list). **⚠️
  Confirm which endpoint the admin catalog management screen uses and keep that one unfiltered** —
  filtering the wrong list would hide an inactive row from the one screen meant to manage it.
- **Family-chip UX redesign (frontend, `applicable-programs-combobox.tsx`).** Replaces full-sentence
  "Add all N programs" buttons with compact states: none selected `Family · N`; partial
  `Family · N remaining` (click adds only the missing ones); full `✓ Family · N` — **LOCKED, inert,
  non-interactive**, status text or a disabled element rather than a clickable toggle (`aria-pressed`
  would misrepresent state, since the Note never persists "family selected" — only individual programs
  do); removing a member after full immediately reverts to partial. **⚠️ ANTI-DRIFT: do NOT touch
  `availableProgramFamilies`/`handleFamilyExpansion`** — already family-count-agnostic, confirmed
  unchanged at kickoff. 18+ selected-program mobile wrapping gets an explicit acceptance check at
  implementation time (render Engineering's 18 at desktop + 375px mobile width; pass/fail criteria in
  the plan's §K) rather than a pre-guessed threshold fix.
- **Health Sciences program family (data + existing mechanism, no new schema).** Nursing, Medicine,
  Pharmacy — **LOCKED per Product UX, not reopened this release**. Evidence: 18 notes carrying a genuine
  3-way tag accumulated across five weeks, plus 21 more notes across the two fused rows above — two
  independent curator actions converging on the same trio. Physical Therapy and Radiologic Technology
  excluded (no comparable co-selection evidence).
- **Accounting program family (data + existing mechanism, no new schema).** Accountancy, Management
  Accounting, Accounting Information Systems, Internal Auditing (4 members) — decided from the CPALE
  curriculum's own subject-plan structure (Management Services and Auditing sections are, by curriculum
  design, shared core coursework for these four program types), not from the 35-note bulk-tagging action
  (which proves curators need cross-program bulk assignment as a workflow, not that all 10 originally
  bulk-tagged programs belong in one family). **Business Administration explicitly EXCLUDED** — its
  genuine overlap is concentrated in RFBT + finance-flavored Management Services content, ~22% of the
  CPALE plan, which fails the family's own precision bar (a program that needs manual removal on ~78%
  of uses isn't a good default). Entrepreneurship, Economics, Senior High–ABM, Real Estate Management,
  CMA, CFA all excluded for the release too (thin or no signal, or — for CMA/CFA — an unresolved
  credential-vs-program taxonomy question, not evidence against inclusion). **⚠️ Flagged for the owner,
  not blocking kickoff: the Business Administration exclusion rests on external domain knowledge about
  how Philippine accounting-track programs share curricula, not a row-level fact the database can
  confirm — worth a sanity check before this family ships.**
- **`docs/features/program-families.md` updated (docs).** Documents both new families, the `is_active`
  mechanism, and the reverse Domain-Context guard already noted in the pass-1 audit.
- **One low-risk copy unification (frontend).** `bulk-generation-page-client.tsx`'s Domain Context
  helper text ("Required when this note applies to more than one program") is looser than the other two
  surfaces' phrasing ("Needed before you can generate a Study Pack for a note in more than one program")
  — unified to match. **No fix for a suspected Domain Context UI phrasing bug from the pass-1 audit** —
  it was not reproduced this pass; do not implement a fix for a defect that isn't there.

**Explicitly out of scope, not folded in:**
- Finance as a Course/Program is deferred to CPALE curation itself — the plan's own trigger (the first
  canonical Finance-applicable note) looks likely to fire soon (16 of 359 planned CPALE notes are
  textbook Finance/Financial-Management topics) but has not fired yet. Finance Domain Context: still NO.
- The CPALE curriculum TSV's `applicable_programs` column is uniformly under-tagged (`Accountancy` on
  all 359 rows, zero cross-program exceptions), and several RFBT titles bake "Accountancy"/"Business Law"
  into the title text itself (a Note Title doctrine violation). Both are real findings surfaced while
  reading the plan, both are curriculum-content issues belonging to the curriculum strategist pipeline,
  and neither is fixed by this release — flagged to the owner separately.
- Legacy-Note normalization for the 21 notes already carrying a fused-row tag: deferred/follow-up, not
  this release.
- Program Family overlap (one nullable FK, single family per program) stays unsupported — a known,
  recorded limitation, no schema change here.

Anti-drift (whole release): Program Family stays authoring convenience only — never persisted on Note
generation payloads, never a discovery axis, never sent to generation, never changes Domain Context or
Authored Depth, never inferred from Review Set, never retroactively synced onto existing Notes (nothing
stores which family a Note's programs came from, so this is structurally impossible, not just a rule —
a focused repository test should confirm `note_course_program` rows are never written with a
family-derived value). Family selection stays purely additive. **Data operations — assigning the 7
catalog rows to their new families via the new endpoint, and the later `is_active` backfill — are
owner-run post-deploy, sequenced per the plan's §O, not part of this release's code diff.**

**Routing: Codex** — this touches a backend migration, a new endpoint, and a frontend redesign together,
per `CLAUDE.md`'s task-routing table. Implementation slices per the plan's §Q: (1) backend catalog
lifecycle + family-reassignment API, (2) frontend family-chip redesign, (3) docs update — one Codex
prompt or two, decided at prompt-writing time. **Verification tier: at minimum one scoped cold agent,
falsification-framed** — re-decide once the actual diff exists. Three triggers already fire at kickoff:
this adds an ADMIN-only mutation endpoint with no prior tests to anchor against; the new lifecycle
column is consumed by two different list paths where filtering the wrong one hides inactive rows from
the admin screen meant to manage them; and the Business Administration exclusion is the plan's own
flagged external-domain-knowledge conclusion, not a verifiable row. Full scope, evidence, and the
plan's complete decision block are in
`docs/claude-plans/program-family-health-accounting-expansion-final-plan.md`.

### Shipped

- Added `course_programs.is_active` through additive migration V145, defaulting every existing and future
  catalog row to active. The migration contains no fused-row retirement backfill; that remains a gated,
  owner-run follow-up after Health Sciences is populated.
- Added the ADMIN-only `PATCH /course-program-catalog/{id}` endpoint for assigning, changing, and clearing
  an existing program's family. Missing or malformed program ids share `404 COURSE_PROGRAM_NOT_FOUND`;
  an unknown submitted family remains `400 UNKNOWN_PROGRAM_FAMILY`.
- Kept the shared catalog list unfiltered for both admin management and authoring fetches. The authoring
  combobox alone excludes inactive programs from new individual selection and family expansion while
  preserving inactive programs that an existing Note already selected.
- Reworked family shortcuts into visible none, partial, and full states (`Family · N`, `Family · N
  remaining`, `✓ Family · N`), with the full state accessible and inert, and aligned Bulk Generate's
  Domain Context helper copy with the other authoring surfaces.
- **⚠️ CORRECTED AT AUDIT — the original text here claimed a browser check "passed at 1440×900" and
  measured specific pixel/coordinate values (a 502px chip row, Save visible at y=550–590) at 375×812.
  No headless-browser or screenshot tool exists in this repo or in the Codex/Claude environments that
  built and reviewed this release, so those coordinates could not have come from an actual render —
  the plan's own §K explicitly warned against exactly this failure mode ("do not invent a threshold
  without looking").** What actually shipped: a mobile-only collapse to 8 visible chips past that
  count, with an accessible "Show all N selected programs" toggle exposing every remove action,
  built as a judgment call (18 unwrapped chips plus their own labels is a lot of vertical space on a
  375px-wide screen) rather than a verified measurement. The acceptance check in the plan's §K has
  **not actually been run** — flagged here rather than left standing as a false "passed" claim; a
  real device/viewport check before this ships to production would confirm or correct this.
- **Post-merge cold-agent falsification pass on the actual shipped diff** (PR #1399, commit `2ad837d6`)
  re-ran both full test suites directly (not trusted from the PR's own report — genuine pass, 31 backend
  + 26 frontend tests targeted at this change) and checked 8 specific claims against real code. One more
  real finding: `docs/features/program-families.md` overclaimed that inactive programs "do not appear in
  individual suggestions" — true only for the Applicable Programs axis (`applicable-programs-combobox.tsx`);
  the separate, legacy singular `courseProgram` free-text suggestion list (`use-course-program-catalog.ts`
  → `course-program-combobox.tsx`, used on onboarding/profile/both note surfaces) is untouched and still
  offers a retired program's name — a pre-existing gap this release did not widen (that field already
  accepted arbitrary free text), not fixed here, corrected in the doc to state its actual scope. Also
  added one "Known limitations" line (a fully-selected family's inert chip count can shrink silently if a
  member is later retired — unreachable today, noted for the future) and a fifth doc file,
  `docs/claude-plans/v0.149.0-program-family-data-ops-handoff.md`, giving the owner the exact API calls
  and verified production catalog ids for the two families and 7 assignments, sequenced per plan §O.
  Everything else the pass checked held: the shared-endpoint filtering scope, already-selected-inactive
  chips resolving correctly end to end, `CourseProgramNotFoundException`/`UnknownCourseProgramException`
  staying genuinely separate (4 untouched pre-existing call sites), and the update endpoint's two-read
  transaction being race-safe by construction (Postgres row-lock + MVCC, not luck).
- **The mobile-collapse UI (above) was removed, not left as an owed check.** Reading the actual layout
  of all four consumers (`note-editor-form.tsx`, `private-note-detail-page-client.tsx`'s inline panel,
  `bulk-generation-page-client.tsx`, and `admin-applicable-programs-section.tsx` via `AppModal`) found
  it solves a problem that cannot occur in any of them: three sit in ordinary page flow, where scrolling
  to a Save button below a tall chip row is normal mobile behavior; the fourth renders inside
  `AppModal`, whose `flex-1 overflow-y-auto` content region plus `shrink-0` actions row (`app-modal.tsx`)
  already guarantees the actions stay visible regardless of content height — a deterministic CSS
  property, not a guess, though still not the same as an actual device render. `MOBILE_SELECTED_PROGRAM_LIMIT`,
  the `matchMedia` viewport listener, and the "Show all N" toggle were removed; every selected program
  now renders unconditionally at any width, which is the `NO CHANGE — CURRENT WRAPPING ACCEPTABLE`
  outcome the plan's own §K asked for if the check passed, arrived at by reading the layout architecture
  rather than by measuring a screenshot. `tsc --noEmit`, the full frontend suite, and lint all re-verified
  clean after the removal — this pass also fixed one unrelated, pre-existing TypeScript compile error in
  the same test file (a fixture cast that needed to go through `unknown` first) that had shipped in PR
  #1399 uncaught, since neither the pre-merge audit nor the post-merge cold agent had run `tsc --noEmit`.
- Added migration, repository, service, real-request controller, and component coverage for lifecycle
  defaults, joined row mapping, family reassignment and clearing, endpoint errors and authorization,
  inactive candidates, and all family-chip states.
