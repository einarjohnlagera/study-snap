# LLM model migration — benchmark design (gpt-4.1 / gpt-4.1-mini decommission)

**Status: design only — not run, not costed-and-approved. Tiering decision is now FINAL** (owner +
Product UX, 2026-10-16, see `docs/claude-plans/2026-10-16-v0174-llm-tiering-remediation-tightened.md`):
**FREE → `gpt-6-luna`, Plus → `gpt-6.1-sol`, Pro → `gpt-6.1-sol`.** This design's earlier "send to Product
UX for the tiering call" framing is resolved — the Pass-1/Pass-2 round trip this doc describes below
already happened; what's recorded here is now the tightened benchmark spec for that decision, folded into
the companion audit/scope file. Next hop is Release Implementor for execution scoping into `v0.174.0`
kickoff, subject to the release-size challenge raised in that audit file. No API calls have been made
against any candidate model. No code, config, or production data was touched producing this document.
Written by Feature Planner per the owner's "NoteLib — Quiz Reliability, LLM Model Strategy & Retention
Investigation" brief, objective 4, after Prod Investigator's production read
(`docs/claude-findings/2026-10-15-quiz-quality-retention-llm-cost-production-read.md`) landed. Updated
2026-10-16 (twice): first to add `gpt-6-sol` as a full test candidate, then corrected to `gpt-6.1-sol` —
the exact model the tightened plan names, not its sibling — once the owner's decision landed.

## Why this exists, and what it is not

`gpt-4.1` and `gpt-4.1-mini` shut down **2027-01-14** (`gpt-4.1-nano`, not used by this app, shuts down
2026-10-23 — confirmed by grep, no hardcoded reference anywhere in `backend/`). Three config slots are
affected, not two:

| Slot | Env var | Current default | Used by |
|---|---|---|---|
| FREE | `LLM_MODEL_FREE` | `gpt-4.1-mini` | Study Pack / Quick Review generation, all free-tier accounts |
| PREMIUM | `LLM_MODEL_PREMIUM` | `gpt-4.1` | Intended for paid-tier generation — **currently dead code**, see below |
| CRITIQUE | `LLM_MODEL_CRITIQUE` | `gpt-4.1-mini` | Interview Practice feedback scoring **and** Ask Companion (`OpenAiAskCompanionLlmService.java:80` — confirmed this pass, not previously documented as a second consumer) |

This is **not** the fix for the already-filed PREMIUM-tier bug (`ROADMAP.md` Backlog, routed to
`v0.174.0`): `generateStudyPackOnce` never calls the premium resolver, so today FREE and PREMIUM both run
`gpt-4.1-mini-2025-04-14` in production regardless of which model this benchmark recommends. That bug and
this migration are two different changes that should land together, per Release Implementor's own
reasoning already on that Backlog row — a model swap plus a routing fix is one coherent change, not two.

**This document designs a test. It does not run one.** Running it means live OpenAI API calls against the
owner's `LLM_API_KEY` — real spend, and a script making those calls is code, which is outside this
session's charter either way. The owner approves execution and cost; Release Implementor routes it once
there's a recommendation to route.

## What this benchmark can and cannot show — state this before anyone spends money on it

The production defect rate this whole investigation is measuring is **~0.06%** (71 candidates found across
115,333 questions in the original incident scan; 4 new confirmed defects across ~148,000 in the 2026-10-15
re-scan). The Tier-3 semantic/ambiguity audit already hit this wall at n=65 with zero confirmed defects —
that result ruled out a *common* defect rate, not a rare one, and said so explicitly.

**No benchmark at a feasible sample size (tens to low hundreds of generations) can show that a replacement
model produces fewer answer-key defects than `gpt-4.1-mini` does today.** The true rate is too low relative
to sample size for that comparison to be statistically meaningful in either direction.

**What this benchmark CAN show, and is designed to show:** that the replacement model is **not worse** on
every structural/mechanical property this pipeline actually depends on, plus real cost and latency. Frame
it to the owner as a **non-inferiority gate**, not a quality-improvement measurement. If the owner's
expectation is "upgrading the model will fix the defects we found," that expectation is not supported by
what this test — or any test at this scale — can actually demonstrate.

## Candidate models

Checked against `developers.openai.com`'s pricing/deprecation pages where available; cross-checked against
third-party aggregators where OpenAI's own pages weren't directly reachable this pass (flagged below —
confirm before relying on any number here for a cost decision).

**`gpt-6-luna`** — OpenAI's own named successor to the `gpt-4.1` family (per the deprecations page). This
is the only cost-tier candidate: the GPT-6 line is **Astra / Sol / Luna**, and Luna is the cheap tier —
there is **no `gpt-6-luna-mini` variant**. Approximate pricing (third-party aggregators; not independently
confirmed on OpenAI's own page this pass): **$0.10/M input, $0.50/M output** — cheaper than `gpt-4.1-mini`'s
current rate. Not a reasoning model (no `reasoning_effort` concept), consistent with the current request
shape (see below).

**⚠️ Known compatibility risk, found this pass, not previously flagged anywhere in this investigation:**
this codebase's entire LLM call path (`OpenAiLlmStudyPackService.buildJsonSchemaRequest`,
`:368-378`/`executeJsonSchemaRequest`, `:396-420`) uses the **Responses API** (`/responses`) with
`text.format.type=json_schema`, `strict: true`, and nothing else (no `temperature`, no `max_tokens` — the
request is already minimal). There are **developer-forum reports** that `gpt-6-luna` on exactly this API
shape (Responses API, `strict: true` structured outputs) **occasionally returns an extra output item**
alongside the schema-valid one, which breaks naive single-item parsing, and a prior-generation sibling
(`gpt-5.6-luna`) was reported to leak stray/foreign-script tokens inside string values under the same
mode while Chat Completions on the same request was clean. These are community reports, not an OpenAI
changelog admission, and severity/frequency is unconfirmed — but it means **"does this model return
clean, single-item structured JSON over OUR actual request shape, at volume" is itself one of the things
the benchmark must measure, not an assumption it can skip.** This app's `executeJsonSchemaRequest` already
throws on an empty response; it has no handling today for a *non-empty but malformed/multi-item* response,
so this failure mode — if it occurs at any real rate — would surface as a new, previously-unseen class of
generation failure, not a graceful fallback.

**`gpt-5.6`** (GA mid-2026) — one generation behind `gpt-6-luna`, included only as a fallback comparison
point if `gpt-6-luna`'s structured-output reliability issue above turns out to be real at a non-trivial
rate during testing. Not the primary candidate.

**`gpt-6.1-sol`** — **corrected 2026-10-16: the owner/Product UX-tightened plan names `gpt-6.1-sol`
specifically, not `gpt-6-sol`.** These are different model IDs; do not reuse one model's numbers for the
other without checking. Checked this pass: list pricing is **identical** — $2/M input, $10/M output for
both (cached input differs: $0.10/M for 6.1 vs $0.20/M for 6-Sol, not relevant to this pipeline since
nothing here uses prompt caching). The two differ in **reasoning-token efficiency** (third-party
measurements found 6.1 uses meaningfully fewer reasoning tokens per task than 6-Sol for comparable work)
and in **reasoning-effort options** (6.1: low/medium(default)/high/xhigh/max; 6-Sol additionally accepts
`none`, which 6.1 does not). This pipeline is a **full test candidate now** (owner direction 2026-10-16,
superseding this doc's earlier "informational only" framing — Product UX needs complete comparative data,
not a partial read), priced in the same tier as `gpt-4.1` (PREMIUM's current, currently-dead-code default).

**Reasoning-token billing is the compatibility risk to test, not an assumption to make.** OpenAI bills
reasoning tokens as output tokens, and this pipeline's request sends no `reasoning_effort` parameter at
all today — meaning Sol would run at its **default (`medium`)** unless the request is changed. That
default reasoning overhead is invisible in the visible completion text; it only shows up in
`usage.output_tokens_details.reasoning_tokens` on the actual response. **Cost-per-generation for Sol
cannot be estimated from token-rate math alone — it must be measured from a real response's usage object**,
because "output tokens" for Sol includes reasoning tokens the model never shows. If the benchmark finds
Sol's real per-generation cost (visible output + reasoning overhead) is materially higher than the
$2/$10 sticker rate implies, that is itself a finding for §8's cost/margin analysis, not a benchmark bug.
Whether this pipeline should explicitly set `reasoning_effort` (e.g., `low`, to bound reasoning-token
spend) rather than accept Sol's default is an open question the benchmark should answer with data, not
assume either way. Astra (~$10/$50 per M) remains excluded — flagship-tier pricing with no stated product
reason to test it.

## What this reopens, beyond "pick a replacement string" — now decided, not open

**Resolved 2026-10-16:** FREE gets `gpt-6-luna`, Plus and Pro both get `gpt-6.1-sol`. FREE and PREMIUM no
longer converge once the routing bug is fixed, because Luna and Sol are genuinely different models —
the exact scenario this section originally flagged as a risk is avoided by the owner's own choice, not by
this design.

**This also directly answers one of the tightened plan's own §1 questions: "whether the existing
FREE/PREMIUM model resolver can support these assignments without unnecessary new abstractions" — yes,
with no new abstraction needed.** `PlanType` (`entity/PlanType.java`) is `FREE`/`PLUS`/`PRO`, and
`isPaid()` already collapses `PLUS` and `PRO` into one boolean (`entity/PlanType.java:8-10`). Since the
owner wants Plus and Pro to resolve to the **same** model (Sol), the existing binary
`ModelTier.FREE`/`ModelTier.PREMIUM` split (`StudyPackService.java:721`, already keyed off
`planType.isPaid()`) is structurally sufficient — PREMIUM just needs to mean "Sol" instead of meaning
nothing, which is the routing-bug fix itself, not a new tier concept. **Do not introduce a three-way
model-tier enum or a per-plan-type resolver branch; it would be unused complexity given Plus and Pro are
identical for this purpose.**

## Benchmark design

**Sample:** 40–60 real production notes, stratified across the 5 quiz modes
(`docs/product/EXAM_MODES.md`) and across the Subject/Domain Context axes `ADR-001` governs, pulled
read-only from `notes` (no PII in the prompt — notes content only, same data the generation pipeline
already sends to OpenAI today). Reuse Prod Investigator's or the Tier-3 audit's existing sampling query
if one is still usable, rather than writing a new one.

**Models under test — three, all full candidates:** `gpt-4.1-mini` (current baseline, run same-day for an
apples-to-apples comparison, not pulled from historical data), `gpt-6-luna` (FREE candidate, confirmed),
and `gpt-6.1-sol` (Plus/Pro candidate, confirmed). All three get the same sample, same metrics, same
write-up depth.

**CRITIQUE's candidate is not decided by this benchmark and needs its own call** (see Open Items #3,
widened below) — the tightened plan's §10 says "evaluate Luna as the default replacement... require a
separate feedback-quality assessment." Note also, found this pass and not previously stated anywhere in
this investigation: `LLM_MODEL_CRITIQUE` is not Interview-Practice-only.
`OpenAiAskCompanionLlmService.java:80` (`properties.getSettings().getModelCritique()`) uses the same
slot for the **Ask Companion** feature. §10's "separate feedback-quality and compatibility assessment"
needs to cover both consumers, not just Interview Practice scoring — framing it as "the CRITIQUE model
migration" rather than "the Interview Practice model migration" avoids under-scoping this.

**Metrics, per model, per generation — deterministic tier (cheap, automatable, run on every generation):**
1. **Request success rate** — did `/responses` return a non-empty, single-item, schema-valid response at
   all. This is the check that directly targets the structured-output risk flagged above.
2. **Schema validity** — does the returned JSON parse and satisfy the existing JSON schema
   (`buildJsonSchemaRequest`'s schema, unchanged) without any code modification.
3. **Tier-1 structural answer-key checks** — the same deterministic checks the original incident doc's
   §H describes (choice count, index bounds, exactly-one-correct-answer invariants per question type).
4. **H4 check** — run `QuizValidationUtils.isAnswerExplanationInternallyInconsistent` (existing method,
   called read-only against generated content, not shipped) against each generated question. Reports a
   rejection rate per model; does **not** claim to measure the true defect rate (see the statistical
   caveat above) — only whether the new model trips the existing deterministic gate more or less often.
5. **Cost per generation** — actual input/output token counts **read from each response's own `usage`
   object**, not estimated, priced at the candidate's real per-token rate including any reasoning-token
   line. Compare against Prod Investigator's real baseline: **3,426 avg input / 986 avg output tokens per
   original generation** (`§5` of the 2026-10-15 production read, 2,233 packs).
6. **Latency** — wall-clock per generation. No longer purely informational: tightened plan §1 requires an
   explicit fallback policy for a failed/slow call, and Sol's reasoning overhead makes latency a real
   input to that policy, not just a number to report.
7. **Retry and rejection rates** — tightened plan §4 adds this explicitly; track separately from #1
   (API-level failure) and #4 (H4 rejection) since they're different failure classes with different costs.

**Metrics requiring human/LLM-judge adjudication (expensive, small-n, the rubric tightened plan §4 adds
beyond the first draft of this design) — run on a subset, not every generation:**
8. **Factual correctness**, **single-best-answer defensibility**, **distractor quality**, **explanation
   quality**, **faithfulness to source notes.** These need a human or an independent LLM-judge pass per
   item, and **must be blind to which model produced each item** — an adjudicator who knows "this one is
   Sol" will bias toward it. State this explicitly as the pass criterion when this section is executed, or
   the "Enhanced Generation" marketing claim in the tightened plan's messaging sections ships on no real
   evidence.

**Include, per tightened plan §4:** known defective-question regression examples (the confirmed defects
already catalogued in the 2026-09-19 incident doc and the 2026-10-15 re-scan) run through each candidate
model's equivalent prompt, to see whether the same source material now produces a self-consistent answer
— informative, but still not a claim about the true production rate (same statistical caveat as above,
just applied to a hand-picked adversarial sample instead of a random one).

**Explicitly not measured, and the design should say so in the final write-up rather than imply it was
tried:** genuine answer-key correctness improvement *at the true production rate* (statistically
infeasible at this sample size, per above — the adjudicated-sample metrics above test something related
but smaller-scale and hand-picked, not this), and anything about retention (Prod Investigator's own
finding: n=1, not buildable from current data regardless of model).

## Pass/fail framing for the owner

Recommend adoption only if the candidate is **non-inferior** on the deterministic tier (1–4: no new
failure mode, no higher H4 rejection rate) **and** cost is flat or improved, **and** the blind-adjudicated
tier (8) shows no regression on factual correctness, distractor quality, or faithfulness. If 1 (request
success rate) shows a real, repeated rate of malformed/multi-item responses, that is a blocking finding
regardless of cost — it would need either a code change (defensive parsing of the extra output item) or a
different candidate, not a silent ship. If Sol's reasoning-token overhead pushes real cost well past the
sticker-rate estimate, that feeds §8's margin analysis directly — it does not by itself block adoption,
since Plus/Pro pricing already doesn't change in this release regardless of generation cost.

## Execution cost estimate, for the owner's approval before anything runs

50 generations x ~3,426 input + ~986 output tokens each, x 3 models (`gpt-4.1-mini` baseline +
`gpt-6-luna` + `gpt-6.1-sol`) is roughly 514k input + 148k output tokens total across all three **if
every model's real output matched the FREE-tier baseline's visible-token count** — which Sol's figure
will not, per the reasoning-token point above. At `gpt-6-luna`'s ~$0.10/$0.50 per M and `gpt-4.1-mini`'s
current published rate, those two legs stay in the low single-digit dollars. `gpt-6.1-sol`'s ~$2/$10 per M
leg is the one that actually costs something to notice, and the estimate here is a **floor, not a real
number**: ~171k input + ~49k visible-output tokens at that rate is roughly **$0.34 input + $0.49 output ≈
$0.83** before any reasoning-token overhead is added — Sol's default `medium` reasoning effort will add to
the output-token bill on top of this, by an amount this estimate cannot predict and the benchmark itself
must measure. Tightened plan §4 independently proposes a **$10 bounded spend cap for the initial
benchmark** — treat that as the real ceiling to get owner approval against, not this per-model floor
estimate. Flagging the number anyway because the "ask before spending" boundary isn't about the amount,
it's about this session never being the one that spends it.

## Open items before this can actually run

1. **Who writes and runs the test script.** Not this session (charter boundary: no code, no builds). Likely
   a Codex brief once the owner approves running this at all, or Release Implementor running it directly as
   part of `v0.174.0` scoping — owner's call.
2. **Confirm `gpt-6-luna` and `gpt-6.1-sol` pricing, availability, and Responses-API/structured-output
   support directly against `developers.openai.com`** (tightened plan §4's own requirement) before relying
   on the third-party figures used throughout this document — this pass could not reach OpenAI's own pages
   directly for either model and is explicitly flagging that gap rather than treating aggregator numbers
   as ground truth for a real cost decision.
3. **CRITIQUE's replacement model is not decided by this benchmark.** Tightened plan §10 asks to "evaluate
   Luna as the default" with "a separate feedback-quality and compatibility assessment" — and per the
   finding above, that assessment now needs to cover Ask Companion as well as Interview Practice. Needs its
   own small design (a feedback-quality rubric does not yet exist to reuse), sequenced after this benchmark,
   not blocking it.
4. **The `reasoning_effort` question is now the main open technical unknown for Sol**, not the FREE/PREMIUM
   tiering choice (that's decided — see above). Does this pipeline send Sol a `reasoning_effort` override,
   or accept its default? The benchmark's job is to produce the data (real reasoning-token counts, latency,
   cost at default vs. at an explicit lower setting if tested) that this decision needs — recommend testing
   at default first, and only adding an explicit low-effort leg if default proves materially expensive.

## Handoff

**Tiering decision is resolved; this design is now the benchmark spec Release Implementor scopes into
`v0.174.0`'s benchmark step**, subject to the release-size challenge raised in the companion audit file
(`docs/claude-plans/2026-10-16-v0174-llm-tiering-remediation-audit-and-scope.md`) — the benchmark itself
is in scope for `v0.174.0` either way; what's in question is how much of the surrounding plan (remediation,
pricing copy, announcements) folds into the same release versus becomes separate candidates.

This session hands this design, plus a routing-table tier (`CLAUDE.md` Task routing: new backend-adjacent
work, multi-step — Codex-prompt tier), to Release Implementor, who scopes execution into `v0.174.0`
kickoff per its own stated plan, pending the owner's approval of the $10 spend cap (tightened plan §4).
This session does not write the test script, does not run it, and does not pick a model unilaterally —
execution, spend approval, and release scoping are Release Implementor's and the owner's calls from here.
