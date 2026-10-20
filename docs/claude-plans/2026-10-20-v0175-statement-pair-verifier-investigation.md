# v0.175.0 candidate: independent statement-pair verifier (Option 3) — investigation and proposal

**Status: OWNER DECISIONS RECORDED 2026-10-20; planning and refinement only.** Go/no-go follows validation.
**Nothing is implemented, no paid call was made, and no production data was changed.**

**Decisions:** `docs/claude-plans/2026-10-20-owner-decisions-v0175-statement-pair-verifier.md`.
- Option F, conditionally.
- +5: option (c), conditionally.
- Replacement by another type: approved.
- A different provider: future investigation only.
- Official stock recheck: approved conditionally, read-only.

**Product UX:** the verifier must never become a prerequisite for background preparation.

**The answers to the owner's six follow-up questions are in §10, and they supersede earlier sections where
they differ.**

Written 2026-10-20 by Release Implementor, in answer to the owner's "Reconsideration of Option 3"
(2026-10-20).

**Evidence:**
- Code on `releases/v0.175.0` (`3937fb55`).
- The three R1 acceptance arms (`docs/claude-findings/2026-10-20-v0175-r1-acceptance-run.md`).
- Read-only production SELECTs, run 2026-10-20 (§0).

## 0. Two production facts to read first

**Facts:**

1. **The template stock is repaired.**
   - The R2 handoffs have been applied. Their own SELECT-only preview steps were re-run today:
     - Handoff 4's target row is gone.
     - All 43 of handoff 3's delete targets are gone.
     - A 14-row sample of key fixes across handoffs 1–3 now keys the intended choice (5 of 5, 4 of 4, 5 of 5).
   - Official template statement-pair rows number **1,333**: the 1,389 scanned, less the 56 deletes in
     handoffs 2–4.
   - **No template has been seeded since 2026-10-09.**
   - **How thoroughly the stock was checked:**
     - Opus checked all 170 items the screen flagged, plus 100 sampled items.
     - The remaining 1,119 got a **Codex first pass**. Opus re-checked Codex's 229 flags, plus 60 random items
       Codex had passed, and found 0 wrong keys in those 60.
     - About 890 Codex-passed rows remain that no Opus reviewer read. 0 of 60 still allows roughly 5% of them
       to be wrong: **up to about 45 wrong keys may still be live in stock** (point estimate 0).
2. **Live generation is a small share of what learners see.**
   - Learner (non-admin) Challenge-mode sessions since 2026-09-12: 33, of which **4 used any live generation**
     (non-zero `input_tokens`).
   - Since 2026-10-01: 28, of which 2.
   - These counts include Board Exam, which shares `session_mode='CHALLENGE'`.
   - Most served questions come from the learner's bank and the Official templates.

**What this means for a verifier:**
- A verifier at the generation boundary sees three paths: live starts, +5, and **future template seeding**.
- **Template seeding has the widest reach**, because one seeded template reaches every learner of that pack.
  The B6 backfill will seed templates for up to 432 Official packs on Sol after the v0.174.0 switch.
- At today's traffic, the live-start path touches a few sessions a month.

## 1. What a verifier would actually protect

| Path | Generator after v0.175.0 | Measured factual-key error rate | Verifier value |
|---|---|---|---|
| FREE single-note start, +5 | Luna | 0 of 31 (95% upper bound about 9%) | Insurance against an unmeasured rate |
| Plus/Pro single-note start, +5 | Sol | 0 of 28 here, 0 of 70 with C7 | Insurance against an unmeasured rate |
| Multi-note start | Luna/Sol after item 5; **mini if item 5 is split out** | mini: 3 of 32 (about 9%) | **Real, but only in the split case** |
| Official template seeding (B6 and future) | Sol (curation) | as Sol | **Highest reach**: one template reaches every learner of the pack |
| Stock already in production (1,333 template rows) | mini (pre-R1), repaired by R2 | about 890 rows passed by Codex only; up to about 45 wrong (§0) | None at serve time. **Option:** once validated, run the verifier offline over the stock and produce an owner-run repair `.sql`, as R2 did. **This is the use with the most reach today** |

**What can be said plainly:**
- On the models Challenge moves to, we have **no measured factual-key errors** (0 of 59 combined; the 95% upper
  bound is about 5%).
- The verifier's sure benefits:
  1. Template seeding.
  2. The split-item-5 case.
  3. **The first production signal for this error class**, because rejections would be logged per model.
- At a low base rate, **false rejections matter as much as detection**. A verifier that drops correct items
  costs learners questions, and possibly whole starts, to prevent an error we haven't yet observed on these
  models.

## 2. Architecture (owner question 1)

**Placement: inside the shared Challenge generation boundary**
(`OpenAiLlmStudyPackService.generateQuizWithSchemaOnce`, `:2377`). It runs after R1's consistency precheck and
before the batch is returned.

- **All three paths are covered:** start (single and multi-note), +5, and the template seed
  (`OfficialChallengeQuizTemplateService:301`). They all route through `generateChallengeQuizWithSchema`
  (`:2339`).
- **A rejected item never reaches the bank.** `persistGeneratedQuestions` runs only on what the boundary
  returns (`ChallengeQuizService:470-481`).
- **R1 stays the first layer, unchanged.** Only items that pass R1 are sent to the verifier.
- **Only statement-pair items are verified,** using R1's classifier (`QuizValidationUtils.checkStatementPair`).
  Other question types are untouched (owner scope 5).
- **One verifier call per batch** carries all of that batch's statement-pair items. In the acceptance arms that
  was usually 1 item, at most 2, and 0 on 2–3 starts in 30.

### Verifier contract

- **Input:**
  - The two statements of each item.
  - The **note text** (see §3) and the Study Pack summary.
  - The domain context, so Philippine law and standards apply.
- **The verifier is not shown:** the key, the choices, the generator's `statementVerdicts`, or the explanation.
  It is blind, as the acceptance reviewers were.
- **Output, per statement:** `verdict` (TRUE, FALSE or UNCERTAIN), `sourceSupport` (SUPPORTED, CONTRADICTED or
  SILENT), `noteConflictsWithStandardKnowledge` (boolean), and a short reason for logs only. It uses a strict
  schema and default reasoning effort, never low (B7).
- **The decision is made by the server, not the model:**
  - It maps the two verdicts to the expected choice through R1's existing classifier.
  - It compares that choice with the generator's key, and the verdicts with the generator's `statementVerdicts`.
  - It accepts the item only when both verdicts are definite and agree.

### Model options

The constraints that decide between them:
- the same standard for FREE and paid (owner 4);
- the verifier is not assumed independent when it is the generator's model (owner);
- a paying learner never gets a weaker model (locked);
- no low effort (B7);
- mini and gpt-4.1 shut down on 2027-01-14.

| Option | FREE items (Luna-generated) | Paid items (Sol-generated) | Verdict |
|---|---|---|---|
| A. Generator re-checks itself | Luna checks Luna | Sol checks Sol | **Rejected**: not independent on either tier |
| B. One fixed verifier, Sol | Sol (independent) | Sol checks Sol (not independent) | Fails the independence requirement on paid |
| C. Always the other model | Sol | Luna | Independent, but paying learners get the cheaper checker |
| D. Sol for everyone, plus Luna whenever Sol generated | Sol | Sol + Luna | Every item gets one checker that isn't its generator. **But on paid items that checker is Luna**: Sol re-checking Sol is the correlated check, so this is option C's problem with Sol layered on top. Paid gets two checkers and FREE one, which may not read as "the same standard" |
| **F. Sol and Luna on every item, whatever the tier** | **Sol + Luna** | **Sol + Luna** | **Recommended.** Literally the same verification on every tier, and Luna adds well under $0.001 on FREE. The independent leg is Sol for Luna- and mini-generated items and Luna for Sol-generated items and templates, so **each leg must pass on its own** (A9). Paid learners' independent checker is still Luna: no OpenAI-only design avoids that, and option E is the remedy |
| E. A different provider (for example a Claude model) | — | — | **The only checker with evidence of catching these errors** (the Opus blind reviewers found all three). **Out of v0.175.0:** a new integration and key, and a second processor of learner notes, which is the owner's call |

- **The cost of D or F** is more false rejections, since two checkers each get a chance to reject. Under F that
  applies to every item. The evaluation measures it (§6).
- **Configuration:** two model settings, `verifier-primary-model` (`gpt-6.1-sol`) and
  `verifier-secondary-model` (`gpt-6-luna`), plus **one enable switch per path, each defaulting to OFF**.
  - **Merging the code calls no new model.** v0.174.0 established that new models go live only through the
    owner's switch, and the same holds here.
  - **Enabling a path is an explicit owner step,** like the v0.174.0 Render switch.
- **The two calls run concurrently,** so the latency of D or F is the slower call, not the sum.

### Cost (measured generation cost, estimated verifier cost)

**Generation per start, meter, from the three arms:** mini $0.0056, Luna $0.0033, Sol about $0.050.

**Verifier assumptions:** these are unmeasured for this task, and reasoning tokens dominate.
- Input is about 1,700–2,300 tokens per call: instructions about 600; note about 600–750; summary about 450;
  items about 150.
  - The note size comes from a count-only production read on 2026-10-20. Official notes run p50 2,293, p90
    2,602 and p99 2,870 characters. The maximum is 7,893 characters (about 2,000 tokens), so a worst-case
    input is about 3,500.
- Output is 400–1,600 tokens, mostly reasoning.

**Per call:** Sol $0.007–0.021; Luna $0.0004–0.001.

| | Generation | Verifier added (design F) | Total per start |
|---|---|---|---|
| FREE start (Luna; Sol + Luna check under F) | $0.003 | $0.008–0.022 | $0.011–0.025 (about 3–8× a Luna start) |
| Plus/Pro start (Sol; Sol + Luna check) | $0.050 | $0.008–0.022 | $0.058–0.072 (+16–44%) |
| Template seed, per pack | as Plus/Pro | $0.008–0.022 | Assuming one verifier call per pack (one seed call, 1–2 statement-pair items): B6 batch A's 50 packs (about $4.30) gain about $0.40–1.10 |

- **Per 1,000 live starts:** about $8–24 added on either tier.
- **At today's traffic** (2 live starts since 2026-10-01): under $0.10 a month.
- **Replacement calls** after a rejection (§4) add the cost of a one-question regeneration each. That is rare
  at the rates in §5.

## 3. Grounding: note text, silence and ambiguity (owner question 2)

**What the generator sees:** the Study Pack **summary and key concepts, never the note text**
(`buildChallengeQuizInputMessages`, `:970`). The note is reachable through `StudyPackEntity.noteId`. Passing it
in means adding the note text to the generation call for all three paths.

**What the verifier sees:** the note text as the primary reference, plus the summary.

**Grounding rule (proposed; the exact wording is for owner sign-off):**
- **SUPPORTED or CONTRADICTED by the note:** the verdict follows the note.
- **SILENT:** judge on standard knowledge of the field (Philippine law and standards; PFRS = IFRS). Do **not**
  treat silence as uncertain.
  - The acceptance reviewers marked the source silent on at least 5 of the 59 correct Luna and Sol items
    (L-21, S-09, S-13, S-14, S-18), and said the note didn't address a statement on a few more.
  - A "the note must support it" rule would reject correct items at about that rate, roughly 8%.
- **The absolute-word rule:** a standard exception makes an "always / never / only / eliminates / ensures"
  statement false. Unlike the Option 2 prompt rule, this lives in the verifier prompt, so it doesn't change the
  generator, and the Luna and Sol passes stay valid.
- **UNCERTAIN is reserved for** two cases:
  1. a competent examiner could mark it either way;
  2. the note contradicts standard knowledge.
- **If the note conflicts with standard knowledge,** the item is rejected and the note id is logged for
  curation. Item R3, the impairment note, is the precedent.

## 4. Disagreement, uncertainty, failure and false rejection (owner question 3)

| Verifier result | Action |
|---|---|
| Both verdicts definite and they agree with the key | Keep |
| Definite but disagree with the key | **Reject.** Never flip the key: the explanation and verdicts were written for the original key, so flipping it would show an explanation that contradicts the key, which is the defect R1 fixed. One **replacement** question, requested as **not** a statement-pair item (so it needs no second check), through R1's existing one-question retry. If that fails, omit |
| Either verdict UNCERTAIN | Same as a disagreement: reject, replace, omit. Never accept an uncertain key (owner) |
| Under D or F, Sol and Luna disagree with each other | Reject, replace, omit |
| Verifier call fails (timeout, HTTP, invalid output) | Retry once (`retryOnceOnInvalidOutput` pattern). If it fails again, **fail closed**: treat every statement-pair item in the batch as rejected and replace it. Never deliver an unverified statement-pair item |

- **Exceptions:** every verifier exception is caught inside the boundary. No new exception type can escape past
  `ChallengeQuizService`'s existing failed-start handling. That must be pinned by a test.
- **Start minimum:** if replacements fail, R1's start minimum applies. A single-note start may be one short; a
  multi-note start fails only at zero. A failed start goes to FAILED, releases its claims and charges no quota.
- **False rejections:**
  - They cost one replacement call and slightly shift the question mix, since the replacement isn't a
    statement-pair item.
  - They are logged with the verifier's reasons, so a production false-rejection rate can be read from logs.
  - A rising rate would be a kill signal (§7).

## 5. Before or after background preparation, and the effect on latency, completeness and quota (owner questions 4–5)

### Enablement per path: every switch defaults to OFF, and the owner turns each one on

| Path | When | Why |
|---|---|---|
| Template seed | **First**, as soon as validated | Offline, so no learner waits. Widest reach. **Should be on before B6 batch A runs** |
| Challenge start (single and multi-note) | **Only after item 2** (background preparation) is merged | Today the start is synchronous and holds row locks across the LLM call (`ChallengeQuizService:254`). The owner forbade a startup regression |
| +5 | **Decided 2026-10-20: option (c).** +5 batches exclude statement-pair items through a prompt rule (§10.3). There is no verifier on +5; any leaked item keeps R1's protection. Fallback if the exclusion proves unreliable: (b), skip and log | It runs mid-quiz with the timer running and isn't covered by background preparation. It already takes 38–55 s on Sol |

### Latency

Latency is unmeasured, so these are **budgets to be met in the evaluation, not estimates:**
- Background start and template seed: added p90 ≤ 30 s.
- +5, if verified: added p90 ≤ 15 s.

### Completeness

- **Assumptions:** about 1 statement-pair item per start; a 3% factual-error rate; a 5% false-rejection rate,
  rising to about 10% under two checkers (D on Sol items, F on every item). That gives about 0.08–0.13 rejections per start.
- **Expected effect:**
  - Each rejection is replaced. R1's one-question retry failed to produce a usable replacement in 1 of 8 cases
    on mini.
  - So about 1–2% of starts end one question short.
  - A single-note start fails only when two items are lost, which is well under 0.5% of starts.
- **To confirm:** the evaluation measures the rejection rate, and the projection is re-run on it.

### Quota

- Verifier and replacement calls never touch quota.
- The charge stays as it is: once per session, at creation after item 2 (on success today), with item 2's
  refund on failure or expiry.
- There's no new session and no retry of a whole start, so there's no path to a double charge.

## 6. Controlled blind evaluation (owner question 6)

**What it requires (each step needs its own approval):**
1. A frozen verifier prompt and schema.
2. A test-scope harness, the owner-run `LlmModelBenchmark` pattern with a new leg.
3. A capped paid run.

**Gold set, built locally with no paid calls.** Per-statement labels, with provenance recorded per item:

| Stratum | Items | Source | Purpose |
|---|---|---|---|
| N: agreed correct items | 88 | R1 arms: 29 mini, 31 Luna, 28 Sol (generator and blind Opus agree) | False-rejection rate. **Caveat items** (L-13, L-17, L-20, L-21, S-09, S-10, S-13, S-14, S-18, S-21) are reported separately |
| P-seen: mini's three | 3 (r1-04, r1-06, r1-07) | R1 mini arm | The owner's named criterion. **Seen**: the verifier wording is written knowing these cases, so catching them proves little on its own |
| P-unseen: R2 confirmed-wrong templates | about 20, drawn deterministically (md5 order) **after the wording is frozen** | R2 checker data (local) | Unseen positives. Mostly the index class, where the generator's own reasoning was right, so they're easier. Reported in their own stratum |
| P-constructed: subtly false statements | about 40 (half false, half true controls) | Written by a cold agent from the stratum-N notes **after the wording is frozen**, checked by a second cold agent | Measures whether each checker, **Luna especially, acting alone**, catches hard false statements. It **cannot** show whether Sol misses its own blind spots: errors a cold agent writes don't reproduce Sol's. Nothing available can, since there are no natural Sol positives, short of option E |
| U: ambiguous | about 6 | R2 items the reviewers marked ambiguous | Checks that UNCERTAIN behaves as specified |

**Runs:**
- Configurations: Sol alone, Luna alone, design D and design F, on the full set.
- The three P-seen items are repeated 3 times per configuration, to measure stability.
- Measured: per-statement accuracy, item detection, false rejection, the UNCERTAIN rate, invalid output, p50
  and p90 latency, and cost.
- **Estimated cost** (about 160 items in about 80 calls per configuration, plus repeats): Sol about $1.2–1.8,
  Luna about $0.10. **Proposed cap: $2.50.**

**Scratchpad caveat:** the R2 checker files live in this session's temporary scratchpad. They should be
copied to `backend/target/llm-benchmark/verifier/` (git-ignored) when the gold set is built.

## 7. Acceptance criteria, stated before any run (owner question 7)

A configuration passes only if every row holds:

| # | Criterion | Kill if |
|---|---|---|
| A1 | r1-06 and r1-07 detected (disagree or UNCERTAIN) in 3 of 3 repeats; r1-04 in at least 2 of 3 | Either clear case missed in any repeat |
| A2 | P-constructed false statements detected ≥ 90% (for example ≥ 18 of 20) | < 80% |
| A3 | P-unseen detected ≥ 90% | < 80% |
| A4 | False rejection on stratum N ≤ 5% (≤ 4 of 88), excluding caveat items, which are reported separately | > 10% |
| A5 | Invalid or failed verifier output ≤ 2% after one retry | > 5% |
| A6 | Added latency p90 ≤ 30 s (start and seed); ≤ 15 s if +5 is verified | > 45 s |
| A7 | Added cost ≤ $0.03 per start at p90 | — |
| A8 | Projected single-note start failures caused by the verifier ≤ 0.5 percentage points (§5 projection re-run on measured rates) | > 1 point |
| A9 | Under D or F, **each tier's independent leg passes A1–A4 on its own**: Sol alone (the leg for Luna- and mini-generated items) and Luna alone (the leg for Sol-generated items and templates) | Either leg fails A1 or A4 |

**Smoke-test framing (requirement E):**
- Even 0 false rejections in 88 still allows about 3.4%.
- A pass means "safe to ship with production logging", not "proven".

## 8. Can it fit in v0.175.0 without delaying background preparation? (owner question 8)

**Yes, with conditions.**

**Why it can:**
- **It doesn't touch item 2's files.** The verifier lives in the generation boundary (`OpenAiLlmStudyPackService`,
  plus a new prompt pair and a validator). Item 2 changes the session lifecycle in `ChallengeQuizService`.
- **The shared seams are small:**
  - adding the note text to the generation call signature (callers in `ChallengeQuizService` and
    `OfficialChallengeQuizTemplateService`);
  - item 6's routing lines;
  - item 5's per-source calls.
- **Item 2 proceeds unblocked.** Development can run in parallel; start enablement waits for item 2's merge.

**What it does to the release:**
- It becomes a **7th item on a Tier H release** that is already above the size guidance.
- That raises the pre-signoff verification load. The verifier needs its own cold falsification pass: a new LLM
  call path, a failure policy, and a no-charge path.

**Recommendation:**
- **Include it in v0.175.0 only if the paid evaluation passes before item 2 is code-complete.**
- Otherwise, ship it as the next release, and in any case **before B6 batch A seeds templates**, since seeding
  is its highest-reach path.
- R1 stays either way.

## 9. Recommended sequence and approvals

| Step | Who | Gate |
|---|---|---|
| 1. Go on this design (option F or D, the grounding rule, the failure policy, per-path enablement) and the +5 choice | Owner | Decision |
| 2. Exact verifier prompt, schema and the replacement-retry wording | Release Implementor drafts, owner signs | Sign-off (it's a prompt and schema change) |
| 3. Build the gold set (local, no calls) and the test-scope harness leg (Codex brief, PR into `releases/v0.175.0`) | Release Implementor, Codex | Approval to implement the harness |
| 4. Paid evaluation | **Owner runs it** in their own terminal | Paid approval, cap $2.50 |
| 5. Results against §7, then go/no-go for v0.175.0 | Owner | Decision |
| 6. Production brief: verifier call, note plumbing, counts logging, tests (real-request where an endpoint changes; none expected) | Codex, then a cold falsification pass | Approval to implement |
| 7. Enable: template seed first, then start after item 2; +5 per step 1. Switches default OFF, so merging calls no new model | **Owner** | Owner enablement, in release order |
| 8 (optional). Offline verifier sweep of the 1,333 stock template rows. The output is an owner-run repair `.sql` with previews, as in R2 | Release Implementor runs SELECTs and the verifier through the owner-run harness; **the owner runs every write** | Separate paid approval |

**Logging (part of step 6):** `challenge_quiz_generation_counts` gains `verified`, `verifierRejected`,
`verifierUncertain`, `verifierFailed`, `verifierModels` and `verifierElapsedMs`. That gives the first
production read of this error class per model, and the false-rejection kill signal.

## Open questions for the owner (all five answered 2026-10-20; see the decisions file)

1. Option F (recommended), D, or another row of the model table?
2. +5: (a) verify, (b) skip and log, or (c) no statement-pair items in +5 batches?
3. Is it acceptable that a rejected statement-pair item is replaced by a different question type? That keeps the
   count but lowers the share of statement-pair items slightly.
4. Should a different provider (option E) be considered for a later release? It's the only checker with
   evidence on this error class, and it means a new data processor for learner notes.
5. Should the offline stock sweep (step 8) run once the verifier is validated? It has the most reach of any
   path today.

## 10. Follow-up after the owner's decisions (2026-10-20)

**Standing rule, from Product UX and consistent with §5:** the verifier is never a prerequisite for background
preparation.
- Items 2–6 ship and work with every verifier switch OFF.
- The verifier ships in its own PR, and each path has its own switch, off by default.
- Live-start verification is switched on only after preparation and shortfall recovery (items 2 and 5) are
  validated in production.

### 10.1 Does Option F create an unacceptable false-rejection or underfill risk? (follow-up 1)

**Underfill: no, on any plausible rate.**
- **Statement-pair items per start:** the acceptance arms averaged 1.07 (mini 32 in 30, Luna 34 in 30, Sol 28 in
  28), at most 2 per start.
- **Rejections:** each rejected item gets one replacement. A start loses a question only when that replacement
  also fails, and a single-note start fails only when it loses two.
- **The projection,** with r = the F rejection rate (true errors plus false rejections) and q = the
  replacement failure rate:

| Scenario | r (F, union of both checkers) | q | Starts one short | Starts failed |
|---|---|---|---|---|
| Expected | 8% | 10% | about 0.9% | under 0.01% |
| Pessimistic | 15% | 15% | about 2.4% | about 0.03% |
| Kill zone | 25% | 25% | about 6.5% | about 0.2% |

- q is unmeasured on Luna and Sol, which had no retries in 88 starts. On mini, 1 of 8 H4 replacements failed.
- **Multi-note** fails only at zero, so it is safer still.
- **Item 2's refund** covers the rare failed start: no quota charge.

**False rejection is the real cost.** It doesn't empty quizzes; it erodes three things:
1. The share of statement-pair items. The prompt asks about 15%; the arms delivered about 8–9%.
2. Preparation time: a replacement adds one generation call.
3. Cost.

Under F every statement-pair item faces two checkers, so the false rejections add. Two checkers at 4% each
reject about 8% of correct items.

**The pre-stated limit for "excessively restrictive" (owner decision 1):**
- F's union false-rejection rate is above 8% on stratum N, or either checker alone is above 5%.
- In that case I return with the measured results and a revised proposal before any rollout.
- Candidate revisions:
  - Reject only when both checkers disagree with the key, or when the independent leg does.
  - Treat a single-checker disagreement as a reason to ask the other checker for a reasoned tie-break.

**Correlated errors (owner: "don't assume two-model agreement guarantees correctness"):** the evaluation reports
these, not just accuracy:
- the Sol × Luna agreement matrix per statement;
- **joint misses** on the positives, where both checkers accept a wrong key. That is the correlated-error rate,
  and it is the number F can't reduce;
- disagreements between the checkers;
- UNCERTAIN rates;
- SILENT (unsupported by the note) rates, split by whether the checker then judged correctly.

A joint-miss rate above 10% on the constructed positives would mean two-model agreement adds little over one
checker. In that case the cross-provider option (decision 4, future) becomes the remedy.

### 10.2 Acceptance criteria and evaluation cost (follow-up 2)

**Superseded (2026-10-20):** the criteria below are replaced by Part 4 of `docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-wording-and-pass-rules.md`.
- It counts against the 78 non-caveat items.
- It adds a gray-zone rule.
- It splits the evaluation into two stages.

The cost table here still stands.

**Criteria.** §7's A1–A9 stand. A4 is now split, and three new groups are added for the +5 rule and
replacements. **All are stated before any run:**

| # | Criterion | Kill, or return with a revised proposal, if |
|---|---|---|
| A4a | Each checker alone: false rejection on stratum N ≤ 5% (≤ 4 of 88), with caveat items reported separately | > 10% |
| A4b | **F, the union:** false rejection on stratum N ≤ 8% (≤ 7 of 88) | > 12%: return with a revised proposal (decision 1) |
| A10 | Joint miss (both checkers accept a wrong key) on P-constructed ≤ 10% | > 20% |
| P1 | **+5 exclusion:** statement-pair leak ≤ 1 item in 40 batches per model (Luna and Sol) | ≥ 3 in 40 on either model: propose fallback (b) |
| P2 | +5 completeness: 5 of 5 delivered in ≥ 95% of batches; H4 rejections and invalid-output retries no higher than the baseline arm | Completeness < 90% |
| P3 | +5 difficulty and quality: blind reviewers rate the batches with the rule as comparable to the baseline batches on difficulty and concept coverage (≥ 80% of paired batches comparable or harder) | < 65% |
| R1r | **Replacements:** 0 statement-pair items among replacements; ≥ 90% test the rejected item's concept; ≥ 80% judged comparable or harder by a blind reviewer; ≤ 1 wrong key in about 40 (blind-checked as in the acceptance arms) | Any wrong key in 20, or < 70% comparable |
| R2r | Question-type distribution of replacements reported (MCQ framings, MULTI_SELECT, IDENTIFICATION and others) | Report only |

**Evaluation cost.** These are estimates on the meter; billed amounts have run lower.

| Leg | Calls | Estimate |
|---|---|---|
| V: verifier, Sol and Luna, about 160 items plus 3 repeats of the hard cases | about 80 per model | Sol $1.2–1.8; Luna about $0.10 |
| P: +5 batches, 40 per model with the rule, plus 10 per model baseline | 100 | Sol about $0.85 (5 questions is about 1/3 of a $0.05 start); Luna about $0.07 |
| R: replacements, about 40 per generator model, triggered from the eval's rejected items | 80 | Sol about $0.40; Luna about $0.02 |
| **Total** | | ~~about $2.6–3.2, cap $3.50~~ **Superseded:** the real gold set batches 1–2 items per call. Stage 1 alone is $1.0–3.0 (cap request $3.20) and both stages about $2.4–4.4. See Part 7 of the wording doc |

The blind quality reviews (P3, R1r) use cold Claude agents, at no OpenAI cost. As before, the raw items stay
in git-ignored folders.

### 10.3 The exact +5 prompt change, for review (follow-up 3)

**Superseded (2026-10-20):** the wording to approve is in Parts 1–2 of `docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-wording-and-pass-rules.md`. Part 2 batches every rejected item into one replacement call, rather than one call per item.

**Where it goes:**
- It's a second `developer` message, added **only** in `generateMoreChallengeQuiz` (+5), after the standard
  Challenge developer prompt.
- Starts and template seeding are unchanged, so R1's validated prompt and the Luna and Sol passes are unaffected.
- Developer-role text is used so it outranks the standard prompt's line 46, which asks for about 15%
  assertion-style items.

**Proposed text (verbatim):**

> For this batch only, do not write assertion-style questions: no question may contain "Statement 1:" and
> "Statement 2:". Give that share to the other framings — positive, negative, and best-describes — and keep
> the same difficulty target, curriculum level, and concept coverage. Every other rule above still applies.
> For every question in this batch, set `statementVerdicts` to null.

**Server behaviour:**
- **No new rejection.** A statement-pair item that still appears goes through R1's existing check and retry,
  unchanged, and is delivered if it passes. That is the owner's "preserve R1 protections".
- **Logging:** the counts line gains `statementPairLeaked` on +5, so the P1 leak rate stays visible in
  production.

**What this does not do:** it doesn't change the start or template prompts, the schema, or any other quiz mode.

**Fallback (b), if P1 fails:** remove the rule, keep +5 unverified, and log statement-pair counts on +5. That's a
one-constant revert.

### 10.4 How replacements preserve the learning objective and difficulty (follow-up 4)

**Superseded (2026-10-20):** the wording to approve is in Parts 1–2 of `docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-wording-and-pass-rules.md`. Part 2 batches every rejected item into one replacement call, rather than one call per item.

**Context it gets:** the replacement call reuses the original batch's full context (`retryInternallyInconsistentQuestion`,
`OpenAiLlmStudyPackService:2600`, which deep-copies the batch's input messages): the same summary, key
concepts, difficulty target, curriculum level, learner context and excluded-question list. It adds three
things:
1. The rejected item's **concept**: every question carries a required `concept` field.
2. Its **difficulty target**.
3. The rejected question's text, marked as possibly false.

**Proposed replacement message (verbatim, for owner sign-off with the verifier prompt):**

> Correction retry: return exactly one replacement quiz question under the response schema. The prior question
> was an assertion-style item whose statements could not be confirmed against the source, so it will not be
> used. Write one replacement that tests the same concept — {CONCEPT} — at the same difficulty target, grounded
> in the provided summary and key concepts. Do not write an assertion-style question: it must not contain
> "Statement 1:" or "Statement 2:". Use a standard multiple-choice question with positive, negative or
> best-describes framing, or another allowed format if the concept suits it better. Either statement in the
> rejected question may be false, so do not treat its claims as facts. Require the same depth of reasoning as
> the rejected question: do not replace it with a question answerable by recalling a single term.

**Requirement by requirement:**

| Owner requirement | How it is met |
|---|---|
| Same learning objective | The same concept is named, and the same summary and key concepts are reused; measured by R1r (≥ 90% same concept) |
| Source grounding | The same source context as the original; the rejected claims are flagged as possibly false |
| Comparable difficulty | The same difficulty target, plus the depth-of-reasoning clause; measured blind (R1r ≥ 80%) |
| Count preserved | One replacement per rejected item; omitted only if it fails, and the start minimum still applies |
| Normal validation | The replacement goes through the same schema, R1 and H4 checks as any question. If it comes back assertion-style anyway, it is omitted rather than re-verified (no second check call) |
| No repeated synchronous calls | **Batched:** all of a batch's rejected items are replaced in one call. Live starts are verified only inside background preparation; template seeding is offline; +5 is never verified. So no learner-facing blocking call is added anywhere |
| Distribution tracked | The counts line gains `verifierReplaced` and `replacementFormats`; R2r reports it in the evaluation |

**What changes from §4:** replacements are batched into one call per batch rather than one call per item.

### 10.5 Verifier decisions to finalize before the first rollout (follow-up 5)

**The first rollout is Official question-set creation (template seeding) only.** Before that switch is turned
on:

1. **The exact verifier prompt and schema.** Release Implementor drafts them; the owner signs them off. They
   cover the grounding rule (§3), the absolute-word rule, the UNCERTAIN definition, and the field list. **This
   is the next deliverable.**
2. **The replacement message (§10.4) and the +5 rule (§10.3).** Both are drafted above and await sign-off.
3. **The acceptance thresholds (§7 and §10.2),** signed **before** the paid run, so the results can't move them.
4. **The harness, built on owner approval:** legs V, P and R, test-scope, owner-run, in the `LlmModelBenchmark`
   pattern.
5. **The paid evaluation** (cap $3.50), then the go/no-go.
6. **Template-seed failure behaviour:**
   - If verification fails closed during a seed, the template is seeded with fewer statement-pair items. A
     template is a pool, so a short template only means the learner's live top-up generates the rest.
   - The decision needed: confirm that a short template is acceptable, rather than retrying the seed later.
7. **B6 ordering:** hold B6 batch A until seed verification is on (recommended), or let B6 run first and cover
   those templates with the stock recheck.
8. **The switch procedure:** the switch names, their defaults (OFF), and who flips them. The owner flips each,
   one path at a time; an env-var override is also the owner's.
9. **The stock recheck (decision 5)** follows a passed validation, not the seed switch:
   - Read-only export of the about 890 rows.
   - An owner-run harness pass.
   - A reviewed correction list with evidence and the ambiguous cases.
   - Any `.sql` is the owner's to authorize and run.

**Not needed before the first rollout:** anything on the live-start path. That waits for items 2 and 5.

### 10.6 Dependencies that could delay background preparation (follow-up 6)

**Structurally, none. Item 2 has no dependency on the verifier, by rule.** The remaining risks are coordination,
not design:

| Risk | Mitigation |
|---|---|
| **Merge conflicts:** the verifier needs the note text in the Challenge generation call, and `ChallengeQuizService`'s call sites are edited by items 2, 5 and 6 | Add the note text as a new overload, so existing callers compile unchanged. Land the verifier PR **after item 2**, rebased, and wire callers in the same PR |
| **Shared file:** `OpenAiLlmStudyPackService` is edited by R1 (merged), the verifier and the +5 rule; item 6 edits routing only | The verifier and the +5 rule go in one PR. Item 6 touches only caller routing |
| **Review capacity:** a 7th item on a Tier H release | The verifier gets its own cold falsification pass, scheduled after item 2's, so item 2's review never waits |
| **Owner time:** sign-offs, the paid run, switch flips | None blocks item 2. They gate only the verifier's own switches |
| **The +5 rule changes +5 for every model, including today's mini, from the day it deploys** | It ships only after P1–P3 pass. It needs no switch, because it removes a format rather than adding a call; it reverts with one constant |

### 10.7 Remaining blockers and recommended sequence

**Blockers before any implementation:**
1. Verifier prompt and schema: draft (next) → owner sign-off.
2. Owner sign-off on the +5 rule (§10.3) and the replacement message (§10.4).
3. Owner sign-off on the acceptance thresholds (§7 and §10.2).
4. Owner approval to build the harness legs (V, P, R).

**Sequence.** Track A is unchanged and has priority; Track B runs alongside it and never gates it.

| Track A (preparation) | Track B (verifier) |
|---|---|
| Item 2: state machine → item 3: screens → item 4: notification → item 5: multi-note floor → item 6: tier routing | B1. Draft the verifier prompt and schema; owner signs off on it plus §10.3, §10.4 and the thresholds |
| | B2. Build the gold set locally; Codex brief for harness legs V, P and R (test-scope PR into `releases/v0.175.0`) |
| | B3. Owner runs the paid evaluation (cap $3.50) → results against the criteria → go/no-go, or a revised proposal (decision 1) |
| | B4. Production PR, **after item 2 merges:** verifier, batched replacement, +5 rule, note-text overload, switches OFF, counts logging; cold falsification pass |
| | B5. Owner switches on **template-seed verification** → B6 batch A runs verified (if the ordering in §10.5 item 7 is chosen) |
| | B6. Read-only stock recheck → reviewed correction list → owner-authorized `.sql` |
| After items 2 and 5 are validated in production → | B7. Owner switches on **live-start verification** |

**Release fit:**
- B4 can make v0.175.0 if B1–B3 finish while item 2 is in flight.
- If not, B4 ships in the next release with no effect on items 2–6, since the switches are OFF and preparation
  doesn't depend on the verifier.
- **R1 stays shipped either way.**
