# v0.174.0 — Challenge Quiz startup latency: root cause, UX recommendation, solution comparison

**Status: COMPLETE, updated 2026-10-19 against Release Implementor's targeted benchmark results**
(`docs/claude-findings/2026-10-19-v0174-targeted-challenge-start-benchmark-results.md`) — the latency
estimates this document made were wrong in the pessimistic direction (real waits are worse, not better: a
15-question Sol start projects to p50 ≈291 s / p90 ≈471 s in production, not the 90–175 s first estimated),
and a new, measured failure mode (a single-note Sol start can time out with no fallback) is now the single
most important open decision in §5. Attribution (deliverable #3) was separately settled against real
production data (Release Implementor's two read-only queries,
`docs/claude-findings/2026-10-17-v0174-llm-benchmark-results.md`, caveat 1, "Third read"), not estimated.
Deliverables #3, #4, #5 of the owner's conditional-approval message
(`docs/claude-plans/2026-10-17-owner-conditional-approval-challenge-quiz-ux.md`, §11). This session owns
latency root-cause (#3), the recommended UX solution (#4), and the solution comparison (#5), per the split
agreed with Release Implementor. Release Implementor owns the routing matrix, the targeted-benchmark
proposal, Board/Long Exam validation, monitoring/rollback, and the v0.174.0-vs-future split.

**Four rounds of correction, stated plainly because each one changed the actual recommendation — this is
not a tidy story, and the untidiness is the point:**
- **Pass 1 (`advisor()`):** async dispatch doesn't remove the 180 s failure mode or make generation
  faster; reuse eligibility hadn't actually been read before being called safe; an executor was proposed
  with no capacity check.
- **Pass 2 (`advisor()`):** the first "templates are organically bootstrapped by admin play" theory was
  unsupported — there's a real, automatic seeding pipeline this document hadn't read yet.
- **Pass 3 (`advisor()`, after reading the seeding method's body):** the template pool is seeded once,
  capped at 20 questions, never topped up — concluded this was a **structural draw-down problem** and the
  **dominant** cause, since 11,783 copies trace to official content.
- **Pass 4 (Release Implementor's actual production queries, after this document was already sent):**
  **pass 3's draw-down theory is empirically false.** Of 34 live starts (re-derived from the real data,
  not the 33 estimated earlier), **zero** went live because a learner exhausted a template. The real
  split: 10 on the learner's own (never-templated) content, 10 on adopted packs with **no template at
  all**, 14 on adopted packs whose template was seeded **after** that session (every seed in the sample
  dates 2026-09-02 to 09-11 — this is the pipeline's rollout window, not an ongoing gap), and 31 of 34 were
  the learner's **first** Challenge on that pack. **Template enlargement has no evidence behind it and is
  withdrawn as a recommendation; backfilling the 432 permanently-unseeded official packs — using a job that
  already exists — replaces it, below.** This is left visible rather than quietly fixed because the
  previous version of this document was already sent to Release Implementor, and the correction needs to
  reach the owner too, not just this file.

All facts below are from reading `ChallengeQuizService.java`, `ChallengeQuizQuestionBankService.java`,
`ChallengeQuizQuestionBankRepository.java`, `OfficialChallengeQuizTemplateService.java` in full,
`AdminStudyPackTransactionHelper.java` in full, `StudyPackService.java` (the async generation-completion
method, lines ~860-968), `GenerationRecoveryRowWriter.java`, `AppConfig.java`,
`OpenAiLlmStudyPackService.generateLongExamParallel`, table/column names (verified individually), and
`frontend/app/study-packs/[id]/challenge-quiz/page.tsx`, on `releases/v0.174.0`, 2026-10-17 — plus the two
production query results Release Implementor ran and relayed (`docs/claude-findings/2026-10-17-v0174-llm-
benchmark-results.md`, caveat 1, "Third read").

## 1. Root cause (§2.1, §2.2, §2.3, §2.11) — settled against real data

**Both Start and "+5" are already pool-first.** Start (`ChallengeQuizService.java:425-476`) and
"+5"/`generateMoreQuestions` (`:1143-1197`) both claim from the learner's own bank
(`challengeQuizQuestionBankService.claimEligibleQuestions`), then copy from an Official template if one
exists (`officialChallengeQuizTemplateService.copyTemplateQuestions`), and call the model live **only for
the shortfall**.

**Why those starts went live, from the actual production read (34 live starts, all `USER` accounts):**
- **10 — the learner's own, non-adopted content.** First Challenge, no bank, no template possible at all.
- **10 — adopted Official packs that still have no template today.**
- **14 — adopted Official packs whose template was seeded *after* that session.** Every seed in this
  sample dates 2026-09-02 to 09-11 — read as the seeding pipeline's rollout window, not a recurring gap.
- **0 — a learner draining an existing template.** This document's earlier conclusion (a fixed 20-question
  pool that every adopter permanently draws down) is **empirically wrong** and withdrawn.
- **31 of 34 — the learner's first-ever Challenge session on that pack.** Repeat-session live starts are
  rare in the data that exists, not the structural certainty the withdrawn theory implied.

**The steady-state rate, post-rollout, is much better than the historical 33/68 (or 34/68) figure
suggests.** Since 2026-09-12: **4 live starts in 26 learner Challenge sessions (≈15%)** — 3 on adopted
packs with no template, 1 on own content; 8 starts in that window were served from a template. The
historical blended figure is inflated by sessions that predate the seeding pipeline existing at all.

**Official template coverage today, read directly (not inferred from the 34 sessions):** of 1,997 public
Official Study Packs, **1,554 hold the full 20-question template, 11 hold 10–32, and 432 hold none.** This
is the real, bounded, sizeable gap — not an ongoing draw-down.

**How the seeding mechanism actually works, read in full:** `seedTemplateAsync`
(`OfficialChallengeQuizTemplateService.java:276-329`) generates up to
`ChallengeQuizService.MAX_CHALLENGE_QUIZ_QUESTIONS` (20) questions once, at a fixed `TEMPLATE_DIFFICULTY =
"medium"`, dispatched after commit via `queueSeedIfEligible` from `NoteService.java:538` (on a visibility
change) and `StudyPackService.java:165,417,470,967` (on Study Pack save, including regeneration) — gated
both before and after the LLM call by `existsByUserIdAndStudyPackId`, so it never re-runs once any row
exists. There is also a one-time whole-catalog admin trigger: `POST /seed-official-challenge-quiz-templates`
→ `queueBackfill()`, which seeds any currently-public Official pack with **zero** rows. **This existing
endpoint is exactly the tool needed to close the 432-pack gap** — see §5.

**Checked, not fully resolved: whether a regenerated Official pack's template ever goes stale or stops
re-seeding.** `StudyPackService.java:922` wipes the bank (`invalidateForStudyPack`, keyed on `studyPackId`
— this wipes whoever owns *that* pack's own rows, not an adopter's separately-id'd copy) on both
regeneration scopes, and `:967` (same method, same transactional flow, unconditional) calls
`queueOfficialChallengeQuizTemplateSeed` right after — which should re-trigger seeding once the delete is
visible. `AdminStudyPackTransactionHelper.regenerateOnePack` (admin bulk summary regeneration) does the
same: it wipes the bank itself (`:83`) and its caller (`AdminStudyPackService.java:57-67`) explicitly calls
`queueSeedIfEligible` right after. **Read both paths that actually wipe an Official pack's template rows
and found both re-queue a reseed; did not find a path that wipes without a follow-up.** This doesn't fully
settle Release Implementor's flagged question about generation-stamp staleness on template reads
specifically, and a third, unread path can't be ruled out — but the production data gives no sign this is
a live problem today (none of the 34 live starts, nor the post-2026-09-12 steady state, show a pattern
consistent with "template existed, then went stale after a regeneration"). Not pursued further; flagged
here rather than asserted either way.

**A related reuse-safety fact, named per §2.3's "verify reuse correctness," not scoped as a fix:**
`copyTemplateQuestions` doesn't check the source pack's `generationStamp` against the adopter's own copy —
a learner who copied official content *before* a later regeneration still receives template questions
generated from the newer content whenever they copy after that regeneration. Latent, pre-existing, not
caused by this migration.

**Reuse eligibility (§2.3) — verified against `findClaimableForUpdate`'s actual query, unaffected by the
pass-3 correction:**
- **Repetition avoidance works, via the claim lifecycle, not a dedicated filter.** A row's
  `claimedSessionId` is set once claimed and only cleared on failure — never reclaimable by a different
  session after a completed one.
- **Difficulty consistency is NOT guaranteed** — no `difficulty` column exists on
  `ChallengeQuizQuestionBankEntity`, confirmed by Release Implementor's own code check too
  (`copyTemplateQuestions:195-235` "has no level or difficulty filter"). Real, pre-existing, not caused by
  this migration — named, not scoped as a fix.
- **Outcome is not filtered on this path either** — only the separate "redo missed questions" method
  checks `lastKnownOutcome`.

**Startup profiles (§2.11), from `resolveGenerationProfile:1364-1395`, and the real wait by size, from
production (not the blended figure):** no completed Quick Review yet → 12, medium; score < 50% → 10, easy;
50–80% → 12, medium; ≥ 80% → 15, hard; multi-note → fixed 18. **The size-specific waits matter more than
the blended p50:** a 15-question live start is **p50 35.7 s / p90 72.6 s (n = 21)**; 12-question is
**p50 34.4 s / p90 42.0 s (n = 4)**. The often-cited blended ≈30 s figure is pulled down by nine 5-question
starts from July and **understates today's actual 12–15 question wait** — the Luna/Sol projections in §2
should be read against these size-specific figures, not the blended one.

**The latency-and-reliability exposure is architectural, independent of the attribution correction above:
the entire Challenge start path, including the live shortfall call, runs synchronously inside the HTTP
request, inside a class-level `@Transactional` boundary, and — verified — under a row-level lock on the
Study Pack itself** (`findOwnedStudyPackForGenerationOrThrow:2560-2563` →
`studyPackRepository.findByIdAndOwnerUserIdForUpdate`, taken at `:224`, before generation starts). See §2.

**Added after Release Manager's follow-up, 2026-10-17: multi-note Challenge is a fourth, distinct case —
not covered above, and likely the worst wait in the product, not a variant of causes 1–3.** A multi-note
start **always** generates live — `startSession:419-424` never consults the bank or a template for this
path at all, for any pack, regardless of history. `generateChallengeQuizForSources` (`:1831-1860`) calls
`quizGenerationService.generateChallengeQuiz` **once per source note, in a sequential loop, inside the
request** — not one call for the combined set. The 18-question multi-note total splits across up to 3
sources on FREE (`freeMultiNoteSourceCap = 3`, `StudySnapProperties.java:135`) and up to 6 on Plus/Pro
(`resolveMaxChallengeSourceNotes:1784-1790`, `18 / 3 = 6` per the code's own comment at `:171-178`) — so a
Plus/Pro multi-note start can be **6 sequential Sol calls**, each independently exposed to the 180 s
timeout, with no parallelism and no reuse mechanism at any source. Production: only 3 learner multi-note
sessions exist so far, all 2026-10-13 → 10-17 with 2–3 sources each — real usage, just starting. This
document's §2/§4/§5 are updated below to cover this as its own case, since neither the async-dispatch fix
nor option G (template backfill) touches it — multi-note has no template path to backfill into.

## 2. What async dispatch actually buys — unaffected by the attribution correction

**It does not make generation faster, and does not remove the 180 s failure mode.**
`LLM_READ_TIMEOUT_SECONDS` (`application.yaml:202`) fires on a slow model response identically whether the
call runs in the request thread or a background task.

**The verified, concrete argument for async: today's synchronous design already lets one slow generation
tie up a second request's thread and connection, via a real row lock.** `startSession` takes a pessimistic
lock on the Study Pack (`:224`) before calling the model, held for the whole synchronous request. A learner
who reloads or retries while their first request is still generating on that pack blocks the second request
on that lock for up to the remainder of the 180 s window — a second thread and connection held doing
nothing but waiting, for a scenario this document expects to become *more* common as Luna/Sol make
generation slower. This is the real, verifiable benefit of moving generation off the request thread — not
an unverified claim about tab-close survival.

**"Stops holding a Hikari connection" — only true if the implementation departs from Board Exam's exact
pattern, stated as a requirement.** `generateBoardExamAsync` (`:610`) runs the LLM call *inside*
`studyPackGenerationTransactionOperations.execute(...)` — copying that pattern verbatim holds a connection
for the whole generation, just on an executor thread instead of a request thread. Either (a) restructure
into a short read transaction, an LLM call with no open transaction, then a short write transaction, or
(b) deliberately size the dedicated executor's max threads against the connection budget (documented
elsewhere as 20 in production), accepting the hold. Don't let "it's async now" imply the connection problem
is solved by construction.

**What async genuinely buys:** bounding how many requests can be blocked on the Study Pack row lock at once
(to the executor's size, not every concurrent reload), and moving failure handling onto the proven, swept
`GenerationRecoveryRowWriter` path instead of one synchronous catch block.

**What it does NOT do: make the wait shorter, for either tier.** The owner's requirement is "fast,
reliable... regardless of subscription tier." Async alone answers "reliable," not "fast," and this is not
a Sol-only problem — Luna's own projected wait (≈55–90 s against a realistic 12–15 question baseline of
≈34–36 s today, not the blended ≈30 s) already regresses.

**The real "fast" lever, corrected after pass 4: backfill the 432 unseeded official packs, using the
mechanism that already exists — not enlarging or topping up templates, which has no evidence behind it.**
`queueBackfill()` already seeds any zero-template public Official pack; the owner's decision is whether
and how much to run it (paid generation on Sol once item 2 lands, and a production write performed by a
job — Release Implementor's own framing, which this document adopts: size it by how many learners actually
adopt those 432 packs, don't backfill blindly, and treat it with the same care as the "no mass regeneration
of question pools" rule even though it's templates, not learner content, being generated).

**Parallel fan-out (V-fan) and lower reasoning effort — now measured by Release Implementor's targeted
benchmark, not estimated, and the real numbers are worse than this document's earlier projection, not
better.** This document's own §4 earlier estimated a 25–35% V-fan cut from an interpolated 5-question
figure; the targeted benchmark instead measured real single-note sizes end to end
(`docs/claude-findings/2026-10-19-v0174-targeted-challenge-start-benchmark-results.md`) and found a real
15-question Sol start at **p50 106.7 s / p90 123.4 s / max 160.5 s**, and a real 10-question Sol start hit
the **180 s read timeout once in 10 runs.** Projected into production terms (the benchmark's own pre-stated
rule — report both an additive and a proportional overhead translation and use the larger), **a
15-question Sol start could reach p50 ≈291 s / p90 ≈471 s** in production — far beyond this document's
original 90–175 s estimate and well past the 180 s timeout at the tail. **V-fan itself was not directly
benchmarked**, and the timeout finding below (not latency) turns out to be the more urgent problem it
can't fix anyway. **Lower reasoning effort is measured and confirms this document's hope, asymmetrically:**
it cuts Luna's 15-question p50 by **43%** (48.8 s → 27.8 s) but Sol's by only **16%** (106.7 s → 89.3 s) —
Sol's wait is spent generating output, not reasoning, so this lever helps FREE far more than Plus/Pro, and
**has no blind quality review yet, so it isn't adoptable as-is.**

**Multi-note's own latency lever: parallelize per-source calls (H-fan) — now measured, and confirmed as
the strongest lever in this entire document.** V-fan (§ above) splits one source's shortfall into smaller
concurrent sub-calls; H-fan runs multi-note's existing separate per-source calls concurrently instead of
sequentially. The targeted benchmark measured the real 6×3 Sol case directly: **sequential, it runs p50
216.8 s / p90 255.7 s / max 255.7 s — 3 to 4 minutes, every run** — while the **slowest single per-source
call averaged only 44.1 s.** Parallelizing those 6 calls would bound the start near that single-call figure
instead of their sum: **roughly a 4–5× cut**, far larger than V-fan's. **This is now the single strongest,
best-evidenced recommendation in this document** — not a hypothesis pending a benchmark, as it was when
first proposed. H-fan and V-fan remain combinable (parallelize across sources and split each source's own
shortfall), but size any dedicated pool against the *product* of the two if both are adopted.

**A second, independent problem with multi-note, on reliability rather than latency: one failed source
fails the whole start, and failure compounds across the sequential calls.** `generateChallengeQuizForSources`
(`:1831-1863`) has **no catch around each source's call** — confirmed by reading it again: it's a plain
loop, any exception from any single source's `generateChallengeQuiz` call propagates straight out and fails
the entire start, discarding whatever the other sources already generated. This is the opposite of Board
Exam's own resilient design in this same service (`generateBoardExamQuizResiliently:667-706`), which
already catches per-source failures and accepts a result above a floor rather than failing outright.
Per-call failure rates **compound** across a sequential sequence of independent calls: at a 2% per-call
failure rate, a 6-call Plus/Pro multi-note start fails roughly **11% of the time** (`1 - 0.98^6`), not 2%.
**Per-source parallelization (option H) and a per-source catch-with-a-floor, mirroring Board Exam's
existing pattern, address two different problems that happen to share one fix location** — H helps the
wait, the catch-with-floor helps the failure rate, and both should land together rather than only chasing
the latency half. Release Implementor's targeted benchmark records `failedSourceIndex` per call, so the
real failure rate (not just the 2% illustrative figure above) will come from that data, not this document.

**The single most important new finding, from the targeted benchmark, and it's about single-note, not
multi-note: a single-note Sol start has no fallback when it times out, and async dispatch does not fix
this.** The benchmark observed one real 180 s read timeout in 40 single-note Sol starts — not a
hypothetical. It fell inside a roughly 10-minute provider-side slow window (runs 7–10 all ran 135–180 s,
while the *same packs* ran 19–26 s on Luna in a different window): **"in production a window like that
times out several learners at once, not one in 40."** Critically: **the service makes no retry on a read
timeout** (the timed-out run made exactly 1 attempt), and **a single-note start is one call** — unlike
multi-note, there is no partial result to fall back on. So converting single-note to async dispatch (this
document's own Required item 2) makes the start *safe to wait on*, but **a timed-out async Sol start is
still a failed start, discovered after three minutes, with no fallback this document's Required list
currently covers.** Three options exist, and none is this document's to pick:
- **Retry once** — implementable without owner approval, but doesn't guarantee success inside a
  provider-side slow window that can last ~10 minutes.
- **Fail with a clear recovery screen** — implementable without owner approval; the "safe" half of async
  dispatch (§5 items 2–3) already covers the mechanics (`GenerationRecoveryRowWriter`, the existing `FAILED`
  UI branch) — this option requires no new UX beyond what's already required.
- **Fall back to Luna** — **this is a downgrade, which the owner's §1 explicitly forbids doing silently**;
  it cannot ship without an explicit owner decision, however tempting as a pure reliability fix.
**A smaller first batch does not avoid this either** — the benchmark's own timeout occurred on a
10-question start, the smallest size this release ships, so shrinking the batch doesn't dodge the tail.

**Checked, 2026-10-19: Render's proxy request limit is very unlikely to be the binding constraint here,
with one real caveat.** No Render docs page states a numeric request/proxy timeout (checked
`render.com/docs/web-services` and `render.com/docs/troubleshooting-deploys` directly; `render.com/docs/limits`
404s). The best available evidence is Render's own staff, repeated across community forum threads, stating
a **100-minute** timeout for a direct web-service HTTP request — this is staff commentary, not an official
docs page with a date, so treat it as directionally right rather than a confirmed number. **Also checked
and more load-bearing for ruling this out: `frontend/lib/api.ts:2500` calls `NEXT_PUBLIC_API_BASE_URL`
directly from the browser, and `next.config.ts` has no `rewrites` entry** — so there is no Vercel
serverless-function proxy hop in this request path at all (Vercel's own function-timeout limits, which
are shorter and well-documented, don't apply here, since the browser talks straight to Render). Against
either the ~100-minute staff figure or any plausible shorter platform limit, even the worst measured
multi-note case (6 sequential Sol calls at up to ~90 s projected each, ≈9 minutes cumulative; the targeted
benchmark's own measured 6×3 Sol figure was 217 s ≈ 3.6 minutes) stays well under it — **the application's
own 180 s per-call LLM read timeout fires first in every realistic scenario, not Render's platform limit.**
**Downgrading this from "a real open risk" to "checked, not the actual constraint"** — the single-note Sol
timeout finding above remains the real, observed failure mode; nothing about multi-note's longer
cumulative wait is newly exposed to an outside platform limit the way it looked before this was checked.

**Timer-during-absence — already an accepted tradeoff for Board Exam, now being extended to a much
higher-volume mode.** `markSessionReady` (`:2614-2636`) sets the timer-start timestamp when it runs; for
Board Exam that's already inside the background task, so a learner who leaves and returns can already find
the timer running or the session forfeited. Worth an explicit decision given the volume difference, not
because the risk is new.

**Already proven NOT to need new work:** `resolveExistingChallengeSession` (`:1397-1472`) already returns
an in-progress session for *any* mode in `GENERATING` status — mode-agnostic. The frontend's `"generating"`
poll loop and `QuizGenerationOverlay` are already generic.

**Operational capacity — a required decision, with a concrete scenario.** `studyPackGenerationTaskExecutor`
(core 2 / max 2) is shared with Study Pack generation; a bulk-generation driver loop already holds one of
only two threads for an entire batch's duration. Owner decision #4 commits to Sol-routed curation runs
under the admin account (which would now also include the §5 backfill, if approved) — during such a run, a
Challenge start sharing this pool would queue behind it for the whole batch. Challenge needs its own
dedicated executor, sized against the connection budget.

## 3. "+5 Questions" is a different problem — unaffected by the attribution correction (§2.12)

**"+5" is observed low-risk at the benchmarked sample size (n = 8 per model on `generateChallengeQuiz`, not
`generateMoreChallengeQuiz` itself), not proven safe.** The timer is already running when "+5" fires, so
applying the Start fix would mean pausing an active timer — a different change with its own fairness
question, not a natural extension. **Recommend leaving "+5" as-is for v0.174.0.**

## 4. Solution comparison (§4, deliverable #5)

| Option | What it changes | Complexity | New generation cost | Latency effect | Risk |
|---|---|---|---|---|---|
| **A. Smaller start, backfill in background** | Generate 5 to begin, fill the rest async | Medium | None extra | Contradicted by Leg B: a 5-question call alone is already p50 25 s (Luna) / 43 s (Sol) | New "quiz grew under you" handling |
| **B. Pre-generate after Quick Review** | Generate a Challenge bank proactively | High | Yes — pays for learners who never open Challenge | Near-instant for those who do | Largest new surface; and now a smaller target population than thought, since 31/34 live starts are first sessions on content with no reuse mechanism at all, not a reuse-exhaustion problem |
| **C. Faster setting/model for Challenge only** | Cheaper model or lower reasoning effort, Challenge-specific | Low to apply, needs its own benchmark | Lower per-call | Unmeasured | A third/fourth routing destination — flagged, not recommended |
| **D. Better waiting state only** | Progress copy, same synchronous flow | Low | None | None | Doesn't touch the verified row-lock contention |
| **E. Ship as-is, measure first** | No change | None | None | None | Leaves row-lock contention and executor-capacity gap live |
| **F. Parallel fan-out (V-fan)** | Split one source's shortfall into N parallel calls, own pool | Low to apply, needs its own pool | ~3× input tokens | Not directly benchmarked; real single-note Sol waits (p50 107 s, one 180 s timeout in 40) make this secondary to the timeout problem below, which it can't fix | Needs dedup/merge check |
| **H. Per-source parallelization + per-source floor for multi-note (H-fan)** — **now measured, the strongest lever in this document** | Run multi-note's existing per-source calls (up to 6 on Plus/Pro) concurrently instead of sequentially, **and** catch per-source failures with a floor, mirroring Board Exam's existing resilient pattern | Low to apply — no new call shape, just concurrency and a catch block that already exists elsewhere in this file; needs its own pool sized for up to 6 at once | None extra | **Measured: 6×3 Sol sequential is p50 216.8 s / max 255.7 s; the slowest single source averaged 44.1 s — a ~4–5× cut from parallelizing.** Reliability half: 0 failures in 48 Sol per-source calls, so the catch-with-floor rests on design, not an observed need, at this sample size | Needs the same dedup-across-sources correctness `generateChallengeQuizForSources` already does sequentially; stacks with V-fan if both adopted, multiplying concurrent-call sizing |
| **I. Single-note Sol timeout handling** (new, from the targeted benchmark — not this document's call) | Retry once, fail with a recovery screen, or fall back to Luna on a read timeout | Low (retry/recovery screen) to an owner decision (Luna fallback) | None (retry/recovery screen); none (Luna fallback, but it's a downgrade) | Doesn't reduce the wait; resolves what happens when the wait fails outright | **A real, observed failure** (1 timeout in 40 single-note Sol starts, clustered in a ~10-minute provider-side slow window) with **no fix on this document's Required list before this update** — async dispatch alone does not cover it |
| **G. Backfill the 432 unseeded Official packs** (corrected after pass 4 — replaces the withdrawn "enlarge/top-up" idea) | Run the existing `queueBackfill()` job against packs with zero template rows | Low — the mechanism already exists; only sizing/prioritization is new | Yes, under decision #4's cap, sized by actual adoption | **Directly reduces live starts on adopted content** — 10 of 34 historical live starts are exactly this population | Production write by a job, owner-sized; no evidence needed beyond the Query-1 counts already in hand |
| **Recommended for reliability: Board-Exam-style async dispatch for Challenge start** | Return immediately (GENERATING), generate after commit | Moderate — needs explicit connection-handling and executor-capacity decisions | None extra | **None by itself** | Resolves the verified row-lock contention; does not shorten the wait |

**Honest bottom line, restated twice now — the targeted benchmark made the real numbers worse than this
document's own estimates, not better.** The owner's 33/68-ish figure narrows under the real attribution
data but doesn't disappear — first starts on own content and on the 432 unseeded packs still wait for live
generation, and nothing here makes that wait fast. Async dispatch makes it safe, not fast: a real
15-question Sol start measures p50 106.7 s / max 160.5 s, projecting to **p50 ≈291 s / p90 ≈471 s** in
production terms — far past this document's original 90–175 s estimate. Backfilling the 432 packs
(option G) reduces how often the adopted-content half of it happens at all. Own-content first starts
(10/34) have **no reuse lever available at all.**

**Two findings now outrank everything else in this document, because they're measured, not estimated, and
because one of them is an outright failure mode, not a wait:**
1. **A single-note Sol start can time out with no fallback** (option I) — observed once in 40 runs,
   clustered in a provider-side slow window that "times out several learners at once." Async dispatch does
   not fix this; it needs an explicit owner decision (retry once, recovery screen, or an owner-approved
   Luna fallback) before Sol ships for single-note starts at all.
2. **Multi-note on Sol (option H) is confirmed as the single worst wait in the product, and the single
   best-leveraged fix.** 6×3 sequential measures p50 216.8 s / max 255.7 s — 3 to 4 minutes, every run —
   against a measured 44.1 s slowest single call. Parallelizing cuts this ~4–5×, the largest win on this
   entire table, now backed by real data rather than a pending benchmark.

## 5. Required / Optional / Future split (deliverable #4)

**Required for v0.174.0:**
1. Thread the resolved model parameter through Challenge Quiz's call sites as part of the already-scoped
   PREMIUM-routing fix. **Include `seedTemplateAsync`'s call to `generateChallengeQuiz`** — official
   curation content, should route to Sol under decision #4 like the rest of official generation.
2. Convert single-note and multi-note Challenge start's live-generation shortfall to post-commit async
   dispatch, with connection-handling, executor-capacity, and timer-during-absence decisions resolved
   explicitly (§2), not inherited by default.
3. Add a Challenge-specific pair to `GenerationRecoveryRowWriter`, mirroring Board Exam's, plus releasing
   bank claims on failure.
4. Move Challenge's quota charge to session-creation time, before dispatch, mirroring Board Exam's design;
   re-examine the multi-note row lock (`:259`) the same way Board Exam's own comments already did.
5. Carry over Board Exam's floor-based graceful degradation for Challenge.
6. **Apply the same async-dispatch conversion to the multi-note path** (`generateChallengeQuizForSources`,
   not just the single-note branch) — it has the same row-lock and connection-holding exposure as
   single-note, compounded by sequential multi-call duration, and real (if small) production usage already
   exists. (Render's own proxy timeout, once flagged as a risk here, was checked 2026-10-19 and is very
   unlikely to bind — see §2; the 180 s per-call LLM timeout remains the actual constraint.)
7. **Add a per-source catch-with-a-floor to `generateChallengeQuizForSources`, mirroring Board Exam's
   already-shipped `generateBoardExamQuizResiliently` pattern.** Today, one failed source call fails the
   entire multi-note start with no catch at all — a real, present reliability gap independent of the model
   migration. This is cheap relative to its payoff — Board Exam already proves the pattern in this same
   file — and should land alongside item 6, not be treated as optional.
8. **Promoted from Optional, now that it's measured: parallelize multi-note's per-source calls (the
   latency half of option H).** 6×3 Sol sequential measures p50 216.8 s / max 255.7 s against a measured
   44.1 s slowest single call — a ~4–5× cut, the largest measured win in this document. This is no longer
   pending a benchmark; it's confirmed, and should be sized and scoped alongside item 6, not deferred.
9. **A new, required decision this document cannot make: what happens when a single-note Sol start times
   out (option I).** The targeted benchmark observed exactly this — 1 timeout in 40 single-note Sol runs,
   inside a provider-side slow window. Async dispatch (item 2) does not resolve it, because a single-note
   start is one call with nothing to fall back on. **Before Sol ships for single-note Challenge starts,
   the owner needs to choose: retry once, fail with a recovery screen (both implementable without further
   approval — the recovery-screen path reuses item 3's own `FAILED`-status machinery), or fall back to
   Luna (a downgrade, which needs the owner's explicit sign-off under their own §1 rule, not a silent
   implementation choice).**

**State this to the owner exactly as derived:** items 1–8 make Challenge reliable and, for multi-note,
meaningfully faster. They do not make single-note fast on either tier — and item 9 is not optional
polish, it's a real failure mode the targeted benchmark already observed once in 40 runs, which this
release cannot ship past without an answer.

**Optional for v0.174.0:**
- **Backfill the 432 unseeded Official packs (option G)** — the job already exists; the open work is
  sizing (by adoption) and the owner's go-ahead on the paid-generation/production-write cost.
- Investigating generation-stamp staleness on templates further, per §1's "checked, not fully resolved"
  note — low priority given no production evidence it's currently live.
- Any fix to the "+5" countdown cosmetic issue — pre-existing, unrelated to this migration.

**Future enhancement (not v0.174.0):**
- **Pre-generation after Quick Review (option B).** Smaller target population than originally framed —
  mostly relevant to own-content first starts (10/34), which option G can't touch. Revisit only if that
  residual wait, after async + options G/H, is still judged unacceptable.
- **Lower reasoning effort, or a third routing destination, for Challenge specifically (§2.4/§2.5).**
  Measured now: Luna's p50 drops 43% at low effort, Sol's only 16% (Sol's time is output-bound, not
  reasoning-bound). Real data, but **no blind quality review exists for low effort yet** — not adoptable
  until that review happens, which this document cannot do itself.
- **Cross-pool reuse** (Challenge drawing from `exam_question_pool`) — plausible, no supporting data.

## 6. Release-size note, per `CLAUDE.md`'s own rule

This adds a quota-timing change and an async/concurrency change to a release already Tier H for the model
swap alone, plus now a bounded production-write job (option G) that itself needs owner sizing. Release
Implementor's call on whether to size this inside the existing Tier-H pass or as its own.

## 7. Handoff — thank you for running both benchmarks

The attribution read and now the targeted benchmark both corrected this document in the pessimistic
direction — every time this document estimated instead of measuring, the real number came back worse, not
better. That pattern, not any single number, is the thing worth carrying into how this release is
reviewed: don't let a future estimate in this chain stand in for a measurement if one is affordable.

**No further benchmark is needed from this document's side.** §5 items 8 and 9 (multi-note
parallelization, the single-note Sol timeout decision) are now the two load-bearing open items — item 8 is
an implementation scoping question, item 9 is an owner decision this document explicitly cannot make
(retry / recovery screen / Luna-fallback-as-downgrade).

**Explicit flag on the routing matrix:** still no third/fourth destination recommended for Challenge Quiz
*generation* itself (low reasoning effort isn't adoptable yet pending a blind quality review, and the
Luna-fallback-on-timeout option is a decision gate, not a routing rule). **Confirmed row:**
`seedTemplateAsync`'s call should move to Sol under decision #4.

**Render's proxy request limit — checked 2026-10-19, no longer open.** Staff commentary (not an official
docs page) points to a ~100-minute platform request timeout; the frontend calls Render directly with no
Vercel proxy hop in between (`lib/api.ts:2500`, no `next.config.ts` rewrite). Either way, the application's
own 180 s per-call LLM timeout binds first in every realistic single- or multi-note scenario measured so
far. See §2 for the full check.

**For the monitoring/rollback deliverable (#8):** the executor-capacity and connection-handling flags from
§2, option G's backfill job, option H's multi-note async-dispatch-plus-parallelization, and **a new one:
whatever the owner decides for option I (single-note Sol timeout) needs its own success/failure metric** —
if it's retry-once, track retry-exhausted-then-failed rate; if it's Luna fallback, track how often it
fires, since silent-downgrade-by-volume is exactly what the owner's §1 rule is guarding against.
