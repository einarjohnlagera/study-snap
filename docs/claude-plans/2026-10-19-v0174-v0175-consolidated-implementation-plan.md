# v0.174.0 and v0.175.0 — consolidated implementation plan (for owner review)

**Status: v0.174.0 decisions A1–A7 ANSWERED 2026-10-19** (verbatim:
`docs/claude-plans/2026-10-19-owner-final-decisions-v0174-a1-a7.md`). **The item 2 brief awaits owner approval.
v0.175.0 decisions B1–B7 are open** (with Product UX). Not authorization to implement, deploy or make paid calls. Consolidated
2026-10-19 by Release Implementor from:
- the owner's decisions, verbatim (`docs/claude-plans/2026-10-19-owner-decisions-v0174-split-and-challenge-preparation-ux.md`;
  Set 2, the hybrid preparation UX, is authoritative where it refines Set 1);
- **the v0.174.0 half:** `docs/claude-plans/2026-10-19-v0174-routing-scope-plan.md` (Release Implementor; its
  routing table falsified by a cold Opus pass and corrected);
- **the v0.175.0 half:** `docs/claude-plans/2026-10-19-v0175-challenge-quiz-preparation-plan.md` (Feature
  Planner; architecture verified from code).

This document carries the decisions, conflicts and dependencies. The two halves carry the detail and the
`file:line` evidence.

**What each part is:**
- **Completed findings (measured):** both benchmarks
  (`docs/claude-findings/2026-10-17-v0174-llm-benchmark-results.md`,
  `docs/claude-findings/2026-10-19-v0174-targeted-challenge-start-benchmark-results.md`) and the routing
  check (`docs/claude-findings/2026-10-17-v0174-routing-matrix-and-exam-path-validation.md`).
- **Approved design decisions:** the owner's two sets.
- **Future implementation:** everything below, all unbuilt.

---

## Part A0 — The owner's answers to A1–A7 (2026-10-19), and what was verified

| | Decision | Verified against code or production |
|---|---|---|
| A1 | The Study Pack family is routed by tier (FREE Luna; Plus/Pro and ADMIN Sol). Interactive quiz and practice paths are pinned to today's models. Challenge routing and the hybrid UX go to v0.175.0 | The routing table was re-checked by a cold Opus pass (2026-10-19). Every call site and every foreground/background classification is confirmed; four corrections were applied in the routing plan |
| A2 | `LLM_MODEL_PAID_INTERACTIVE` = `gpt-4.1` for Interview Practice quiz and the companion; `LLM_MODEL_CRITIQUE` unchanged; every pin must be replaced before 2027-01-14, and v0.175.0 does not cover them all | Production `LLM_MODEL_PREMIUM` = `gpt-4.1` (owner verified on Render). The pins are tracked by a new `[CHECKPOINT — due 2026-12-15]` row in `ROADMAP.md`, which lists the paths no plan covers yet |
| A3 | The repair job uses the pack owner's **current effective** tier. Stale `model_used` / `model_tier` is documented | The repair rewrites only `quiz` (`AdminStudyPackTransactionHelper:139`), so the metadata stays as it was. Nothing new is written that would mislead |
| A4 | The Long Exam pool stays at its current size in v0.174.0; the reduction moves to v0.175.0 | **Effective value is 48:** `application.yaml:249` `${LONG_EXAM_POOL_SIZE:48}`; Java default `StudySnapProperties:169` = 48; no override in `application-prod.yaml`; no Render override (owner). All 626 production Long Exam pools have `pool_size` 48. Spring would also bind a relaxed-name variable `STUDYSNAP_PRICING_LONGEXAMPOOLSIZE`; the owner checked only the named variable, but the data shows 48 was in effect |
| A5 | Synchronous `/study` is accepted temporarily, with loading feedback, a duplicate guard, an actionable error and retry, no time promise, and no new automatic retry | **Exists:** the loading spinner and copy (`study-input-card.tsx:172`, `confirm-text-card.tsx:49`); the duplicate-submit guard (`use-study-pack.ts:191`) plus disabled buttons. **Missing:** a retry action in the error card (`study-page-client.tsx:156-162`), which goes in the brief. The service's existing `retryOnceOnInvalidOutput` (malformed output, within one request) is pre-existing, not a new blind retry |
| A6 | $10 per calendar month; alert only, never block; deduplicated per month; scope stated as partial | `NotificationService.deliver` enforces one notification per `(recipient, dedupKey)`; a per-month key gives one alert per month. `notifications.type` is `VARCHAR(64)` with no check constraint (`V139:4`), so a new `NotificationType` needs no migration. `study_packs` stores `cached_input_tokens` (confirmed in production) |
| A7 | `LLM_MODEL_PREMIUM` = `gpt-4.1`; no `LONG_EXAM_POOL_SIZE` override | See A2 and A4 |

**Remaining blockers and contradictions after A1–A7:**
1. **None blocking.** The item 2 brief can go to Codex once the owner approves it.
2. **The same gap as A5, in a second place (not blocking, folded into the brief):** single note-from-topic
   is also synchronous and foreground (`NoteGenerationService:92`), used in onboarding and the note editor.
   - On Sol (paid, note editor) it takes about 20–28 s.
   - **The brief applies A5's conditions there too:** verify the loading and error-and-retry states exist,
     and add a retry only where it is missing.
3. **A6 needs one price the plan does not have:** Sol's cached-input rate. The brief makes all rates
   configuration.
   - It defaults the cached rate to the full input rate, which **overstates** cost, so the alert fires
     early, never late, until the owner supplies the published cached rate.
   - Overstating is the safe direction for an alert.
4. **"Prevent accidental duplicate submissions" (A5)** is met by the client guard: a double click cannot
   submit twice.
   - A deliberate reload-and-resubmit can still create a second pack, because the server has no
     idempotency key.
   - That is accepted as outside "accidental"; the v0.175.0 async rework would close it.
5. **Merge gate unchanged:** #1505 must merge after the 2026-10-22 read, plus the cache deploy + 7 days read.
   The RC-3 checkpoint is due 2026-10-21.
6. **The rollout trigger depends on which env vars are set on Render (owner check; not blocking the
   briefs).**
   - The briefs change the `application.yaml` defaults of `LLM_MODEL_FREE` / `LLM_MODEL_PREMIUM`.
     `LLM_MODEL_PREMIUM` is set on Render (`gpt-4.1`, A7), so Plus/Pro and ADMIN move to Sol only when the
     owner changes that variable.
   - **If `LLM_MODEL_FREE` is NOT set on Render, the merge deploy itself moves FREE Study Packs to Luna.**
     If it is set, the owner's env-var change does.
   - The new pin keys are unset, so they fall back to today's models, which is correct.
   - **Answered by the owner 2026-10-19:** `LLM_MODEL_FREE` **is set** on Render (`gpt-4.1-mini`);
     `LLM_MODEL_CRITIQUE` is **not set**.
     - **So the merge deploy changes no model.** FREE stays on mini and Plus/Pro and ADMIN stay on `gpt-4.1`
       until the owner changes the env vars.
     - The critique paths keep the YAML default `gpt-4.1-mini`, which the briefs do not touch.
     - **Rollout:** merge, then the owner sets `LLM_MODEL_FREE=gpt-6-luna` and
       `LLM_MODEL_PREMIUM=gpt-6.1-sol` in one Render change (one redeploy), under the merge gate.
     - **Rollback:** set them back.
7. **A deleted or missing pack owner in the repair job** routes as FREE and logs a warning (brief 2a). It
   applies A3's "current effective tier" to an owner who no longer has one.
8. **Delivery is split into three pieces** for proportionate verification: 2a routing (Tier H), 2b the cost
   alert (Tier N), 2c the `/study` and note-editor retry (Tier S, inline).

## Part A — v0.174.0: model migration and safe routing

### Scope

1. **Route Study Pack generation by tier**, through one resolver.
   - **Who gets which model:**
     - **FREE → Luna; Plus/Pro → Sol; ADMIN (official curation) → Sol**, whatever the admin account's plan.
     - The plan is read through `SubscriptionService.resolvePlan`, which treats an expired subscription as
       FREE.
   - **Paths covered:**
     - Study Pack generation, both the async and the synchronous routes;
     - summary regeneration;
     - note-from-topic, single and bulk;
     - the Official template seed (Sol);
     - the malformed-quiz repair (the pack owner's plan; decision A3).
   - The model is passed in by each caller, because Challenge start, "+5" and the template seed share one
     method.
2. **Pin every other path to the model it uses today, for every plan, through two new config keys:**
   - `LLM_MODEL_INTERACTIVE_QUIZ` = `gpt-4.1-mini`: Challenge start, "+5" and multi-note; Adaptive Practice;
     the study tip; the teacher quiz; Board Exam and Long Exam, sessions and pools.
   - `LLM_MODEL_PAID_INTERACTIVE` = `gpt-4.1`: Interview Practice and the companion (decision A2).
   - `LLM_MODEL_CRITIQUE` is unchanged: Interview critique and Ask Companion (owner §9).
3. **Defaults:** `LLM_MODEL_FREE` = `gpt-6-luna`, `LLM_MODEL_PREMIUM` = `gpt-6.1-sol`. **The production env
   vars are an owner action**, held to the merge gate.
4. **Monitoring.** Each item below is labelled with who reads it.
   - **Data only, read by a Render log search; nothing reads it automatically yet.** One structured log line
     per LLM call: operation, config key, model, tier, ADMIN flag, latency, attempts, outcome and tokens.
     - It covers the owner's list: model selection per tier, success and failure, parse failures, latency
       by workflow and model, and timeouts and retries.
     - Reasoning tokens are for observability only. `output_tokens` already includes them, so cost needs
       nothing extra (first benchmark).
   - **A fix:** the Study Pack `model_used` fallback records the model actually sent.
   - **Automatic: the curation cost monitor (item 4), a scheduled job that alerts and never blocks.** It sums
     **persisted** tokens on ADMIN-owned Study Packs (`input_tokens`, `output_tokens`, `cached_input_tokens`,
     confirmed in production) with the cached-input discount. **It cannot see Sol spend on template seeds,
     summary regeneration or the malformed-quiz repair:** those persist no tokens, and persisting them would
     need a migration, which v0.174.0 rules out.
     - **That spend is visible only on the OpenAI billing dashboard.** It is stated, not hidden.
   - **Question validation and rejection monitoring** (dedup rejections, H4 flags): **not in v0.174.0.** The
     dedup counts are already computed in the quiz paths, but nothing records them. Proposed for v0.175.0.
5. **Rollback:** each of the four keys is an env var, so each route can be reverted on its own. No migration
   and no data rewrite.

### The temporary Challenge routing, and the customer-claim check (the owner's explicit ask)

- **Challenge Quiz in v0.174.0 runs on `gpt-4.1-mini` for every plan, as today.** That covers start
  (single-note and multi-note) and "+5", through the new `LLM_MODEL_INTERACTIVE_QUIZ` pin.
- **It is a pin, not a substitution.** No paid user gets a lower-tier model than they get today, and no path a
  learner waits on gets slower.
- **The one thing that does change:** Official template questions are seeded on Sol (curation) and copied
  into Challenge sessions on every plan, including FREE. That is Official content being Sol by design.
- **There is no conflict with customer-facing claims.** Plan copy promises only quantities: Study Packs,
  Challenge quizzes per month, share links, 20- and 30-question teacher quizzes
  (`frontend/src/config/plans.ts:47-138,262`).
  - A cold Opus grep found no claim about model, quality, speed or "enhanced", tied to a plan, anywhere in
    the frontend.
  - Nothing is announced in v0.174.0.
  - **Constraint:** "Enhanced Study Pack Generation" copy must not be extended to quizzes before v0.175.0.

### Acceptance criteria

`docs/claude-plans/2026-10-19-v0174-routing-scope-plan.md` §7.
- A test per path asserts the model **actually sent** for each of: FREE, Plus, Pro, ADMIN on a FREE plan,
  and a lapsed paid plan.
- Pinned paths ignore changes to `LLM_MODEL_FREE` / `LLM_MODEL_PREMIUM`.
- A test fixes the fields of the structured log line.
- No migration and no historical rewrite.
- `./mvnw clean install` passes with Docker.
- A Tier-H cold falsification pass runs before signoff.

### Size

- **Implementation surface:** tier routing in about 6 caller services (the Study Pack family). The two pin
  keys are plumbed through the shared quiz methods, with the model passed in by the caller. Plus the log
  line.
- **Test surface: still all 11 caller services.** Acceptance criterion 1 needs a sent-model test on every
  pinned row too.
- The verification tier follows the test surface: **Tier H, one Codex brief.** It is smaller in code than the
  kickoff version, but not in verification.

---

## Part B — v0.175.0: Challenge Quiz preparation (planned; to be kicked off after v0.174.0)

Detail: `docs/claude-plans/2026-10-19-v0175-challenge-quiz-preparation-plan.md`.

**Headline finding: no mode (Challenge, Board Exam or Long Exam) has a "ready, confirm, then start the
timer" step today.** Each one starts the timer the moment its content is ready: `markSessionReady` at
`ChallengeQuizService:2614-2636`, and `LongExamService:783`. The owner's hybrid UX builds that step for the
first time.

**What already exists:**
- **In-app notifications with exactly-once delivery and a deep link** (`NotificationService.deliver`,
  unique `(recipient, dedupKey)`, `ctaPath`, the `ASYNC_RESULT` category). "One notification, no repeats" is
  already guaranteed by how they are built. There is no email or push channel.
- **Reuse of the learner's question bank and the Official template** (start without waiting when enough
  questions are ready).
- **The generating overlay and its polling.**
- **`resolveExistingChallengeSession`**, which finds a learner's in-flight session.

**What must be built:**
- **Background generation (async) for single-note and multi-note starts.** It lands in a new
  ready-but-not-started state.
- **The new state itself.** Whether it is a flag or a new status is a real trade-off: the partial unique
  index `WHERE status = 'IN_PROGRESS'` protects a flag automatically, but every consumer of `IN_PROGRESS`
  would then need a guard.
- **Splitting `markSessionReady`** into "ready" and an explicit **Start Quiz** action (modelled on
  `LongExamService.resumeSession`).
- **A frontend pre-screen phase.**
- **The preparation screen:** primary **"Stay on this page"**, secondary **"Continue reviewing"**.
- **The readiness notification and a deep-link landing** that handles four cases: ready, already started,
  expired or forfeited, and not owned.
- **Multi-note:**
  - each note's call runs in parallel (measured 4–5× cut);
  - a failed note no longer fails the start: the start continues if enough questions survive (floor);
  - token usage is recorded (it is discarded today).
- **Retry once when safe.** Lock the session and re-check its status before retrying, as
  `generateBoardExamAsync:626-633` already does. Then show a recovery state with an actionable retry. **No
  Luna fallback for Plus/Pro.**
- **Quota at session creation.** Plus the recovery-sweep entries for Challenge sessions, so a stuck
  generating or ready-not-started session gets cleaned up.
- **Routing Challenge by tier** (FREE → Luna, Plus/Pro → Sol), validated with `model_used` on the session.
- **Board Exam and Long Exam:** evaluated for fit now, but the retrofit itself is **recommended for after
  Challenge's version ships**. Board Exam is cheap to retrofit (same file, same async dispatch). Long Exam
  fits in spirit but not in code.

**Acceptance criteria:** the v0.175.0 plan §4 covers readiness, failure recovery and timeout behaviour, with
states, timings and observable assertions.

**Size:** about 15 distinct pieces of work across quota timing, concurrency and a new state machine.
**A full Tier-H release before any Board Exam or Long Exam retrofit.** Expect the full pre-signoff pressure
test.

---

## Part C — Conflicts found, and how they resolve

1. **"Correct and verify the relevant model-routing defects" vs "no unvalidated Challenge latency
   regression"** (both in Set 1 §3, both for v0.174.0).
   - Fixing Challenge's routing in v0.174.0 would either slow it (Luna or Sol) or need v0.175.0's async
     work first.
   - **Proposed:** read "relevant" as the Study Pack family. Challenge's routing fix is v0.175.0 item 17,
     which the owner's own v0.175.0 list names ("validation of tier-specific generation routing for
     Challenge Quiz"). **Needs the owner's confirmation (A1).**
2. **"Maximum blocking wait of 60 s" (Set 1) vs "show a preparation screen" (Set 2).**
   - **Proposed:** under Set 2, the preparation screen appears at once whenever generation is needed, so no
     blocking wait remains. "30 s target / 60 s maximum" then describes how long preparation should
     typically take, which is what Set 1's own "not hard deadlines for background generation jobs" says.
     **Needs the owner's confirmation (B1).**
3. **The Long Exam pool reduction to 30** (decided 2026-10-17 to give Sol headroom). Under the v0.174.0 pin,
   Long Exam stays on mini, so the reduction is unnecessary in v0.174.0 and would end pool reuse.
   **Proposed:** move it to v0.175.0, together with routing Long Exam to Sol (A4).
4. **The Sol timeout rule (Set 1 §2: retry once when safe, then a clear recovery state) applies to every
   path, but v0.174.0 puts Sol on the synchronous `/study` Study Pack routes, which have no timeout retry.**
   - **Exposure today:** the only active paid subscription in production is the ADMIN account's PRO grant
     (read-only, 2026-10-19), so no paying learner reaches Sol on `/study` yet. A timeout there would show
     an error after up to 180 s.
   - **Proposed:** accept for v0.174.0 while no paying learner exists, and apply the retry-and-recovery rule
     to `/study` in v0.175.0, by making those routes async or adding the retry. **Needs the owner's
     confirmation (A5).**
5. **No conflict found between Set 1 and Set 2 beyond item 2.** Set 2 refines Set 1, as the owner framed it.

---

## Part D — Decisions requiring the owner

**For v0.174.0: ALL ANSWERED 2026-10-19; see Part A0. The questions are kept below as asked.**
- **A1.** Confirm "relevant routing defects" means the Study Pack family; Challenge's routing moves to
  v0.175.0 (Part C1).
- **A2.** Interview Practice and the companion: **pin to `gpt-4.1`** (proposed: no change, no regression),
  or move to Sol unbenchmarked?
- **A3.** Malformed-quiz repair, which runs over every owner's packs: **use the pack owner's plan**
  (proposed), or a fixed model? Its stale `model_used` / `model_tier` is recorded as known.
- **A4.** Move the Long Exam pool reduction to v0.175.0 (proposed), or keep it in v0.174.0?
- **A5.** The synchronous Study Pack routes (`/study`, live) on Sol take about 27–34 s for the model alone,
  plus production overhead. That is within 60 s, but over the 30 s target, and they have no timeout retry
  (Part C4). **Proposed:** accept for v0.174.0, since today only the ADMIN account reaches Sol there, and
  bring them under the retry and recovery rule in v0.175.0.
- **A6.** **The monthly curation cost-alert threshold** (owed since kickoff; blocks item 4's brief). Also
  accept that seed, regeneration and repair spend is visible on the billing dashboard only (Part A4).
- **A7.** **Confirm two production values.** Read them on Render yourself; Claude does not read env vars,
  because doing so would pull the API key into the session. The values:
  - `LLM_MODEL_PREMIUM`: is it `gpt-4.1`? This decides the Interview Practice and companion pin.
  - `LONG_EXAM_POOL_SIZE`: is it unset or 48?

  The free path is verified from data: 7,441 Study Packs and the Challenge sessions in the last 30 days
  record `gpt-4.1-mini-2025-04-14`. **No table records the premium path**, so "pin = today" for Interview
  Practice and the companion rests on this answer.

**For v0.175.0 (needed before its kickoff):**
- **B1.** Confirm the blocking-wait reading (Part C2).
- **B2.** If the learner closes the tab without choosing either action, do they still get the readiness
  notification?
- **B3.** How long an un-started ready quiz is kept before it is swept as expired.
- **B4.** **The "two Start Quiz buttons" copy problem.** Today's Start Quiz *triggers* generation; the new
  one *starts the timer*. The learner can't know in advance which screen follows. **A Product UX copy
  question.**
- **B5.** When to schedule the Board Exam and Long Exam retrofits (proposed: after Challenge's version is
  observed in production). Also whether Long Exam's `PAUSED` state is shared with the new ready state.
- **B6.** **The 432-pack Official template backfill (option G)** is in neither release.
  - It cuts how often generation is needed at all.
  - It is paid generation on Sol plus a production write by a job, so it is the owner's to run.
  - Place it in v0.175.0, in a later release, or drop it.
- **B7.** Before low reasoning effort is ever adopted, the blind quality review it needs (owner Set 1 §4) has
  to be scheduled and approved as a paid run.

---

## Part E — Dependencies and remaining risks

**Dependencies:**
- **The v0.174.0 merge gate (unchanged).** The merge to `main` and the Render env-var change both wait for:
  - #1505 to merge, after the `[CHECKPOINT — due 2026-10-22]` signal-9 read;
  - and the cache deploy + 7 days read.
- **The RC-3 checkpoint:** due 2026-10-21.
- **v0.175.0 starts after v0.174.0 signs off** (kickoff discipline).
- **Hard deadline: `gpt-4.1` and `gpt-4.1-mini` shut down 2027-01-14**
  (`docs/claude-plans/2026-10-16-llm-model-migration-benchmark-design.md:19`).
  - v0.175.0 must ship before then, or every pinned path stops working.
  - `LLM_MODEL_CRITIQUE`'s own assessment has the same deadline.
  - About 12 weeks remain, for a full Tier-H release.

**Risks:**
1. **The pin deadline.** If v0.175.0 slips past 2027-01-14, the pins must move to Luna, which reintroduces
   the latency v0.174.0 avoided. **Recommend a `[CHECKPOINT]` row dated about 2026-12-15** to decide
   between shipping v0.175.0 and a fallback.
2. **Render's request time limit: checked, not a blocker** (Feature Planner, 2026-10-19).
   - The browser calls the Render backend directly: `NEXT_PUBLIC_API_BASE_URL` (`frontend/lib/api.ts:2499`),
     with no `rewrites` in `next.config`. So no Vercel function hop applies, which would have had a shorter
     limit.
   - Render documents no numeric limit. Staff forum posts (unofficial) indicate about 100 minutes.
   - The app's own 180 s per-call read timeout fires first in every measured case.
3. **Sol's slow windows cluster.** One timeout in 40 single-note starts fell inside a 10-minute
   provider-side slow window. In v0.174.0 this hits background and synchronous Study Pack generation only.
   - Async Study Packs already mark failures and recover.
   - **The synchronous `/study` routes would show an error after up to 180 s.**
4. **Sol cost.**
   - About $0.023 per Study Pack against $0.003 on mini.
   - **About $0.085 per Official template seed** (20 Sol questions).
   - Summary regenerations and quiz repairs cost about one Study Pack call each.
   - Curation so far is about 2,000 packs. There is no mass regeneration, so the exposure is new curation
     and admin-triggered jobs only.
   - The item 4 alert covers Study Packs only (Part A4); seeds, regenerations and repairs show on the billing
     dashboard.
5. **Template questions written by Sol reach FREE learners.** This is by design (curation), but it is not
   visible in a session's `model_used`.
6. **Quality evidence is "no regression", not "better".** n = 20 per model and one adjudicator. Customer
   copy must not claim improved accuracy (already an anti-drift rule).

## Part F — What happens after the owner's answers

1. **Release Implementor** revises v0.174.0's Planned Scope in `RELEASES.md` (item 2 shrinks, items 3 and 4
   gain the new keys and the log line). It also adds to `ROADMAP.md`:
   - a v0.175.0 Backlog row;
   - the pin-deadline `[CHECKPOINT]` row, written with its kill criterion once the owner has answered.
2. **The item 2 Codex brief:** `advisor()` first, then Codex at high effort. It needs A1–A3; item 4 also needs
   A6.
3. **v0.175.0** is kicked off after v0.174.0 signs off, with B1–B7 answered.
