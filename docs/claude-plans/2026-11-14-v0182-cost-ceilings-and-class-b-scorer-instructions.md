# v0.182.0 freeze inputs for owner approval: cost ceilings and the class-B human scorer instructions

Status: **PROPOSED, 2026-11-14, nothing frozen.** Two documents the freeze needs and the preregistration names but does not yet contain: the per-workflow cost ceilings (section 5: "an owner-approved per-workflow forecast ceiling is fixed before generation; PROPOSED 1.25x that forecast per delivered case, applied identically to arms") and the instruction sheet the human reads when scoring the 66 primary class-B pairs (Product UX, 2026-11-13: all 66 receive blind human scoring). Both are drafts for the owner's edits. Nothing here authorizes a paid run, a model switch or a deployment.

## Part 1. Per-workflow cost ceilings (PROPOSED)

**Method (reproducible):** forecast per delivered case = lean calls per case-arm x (input tokens x input price + output tokens x output multiplier x output price) / 1,000,000, with the token sizes of preregistration section 5 (dated production logs: Long Exam 3,400 in and 3,600 out, Board Exam 1,750 and 1,800, Adaptive Practice 2,250 and 1,300, the study tip 250 and 30; every workflow with no logged call is priced at the largest observed size, 3,400 and 3,600, as a conservative assumption) and the standard-tier prices confirmed against official OpenAI pages on 2026-11-13 (preregistration Addendum 2): `gpt-4.1-mini` $0.40 / $1.60, `gpt-4.1` $2.00 / $8.00, `gpt-6-luna` $0.10 / $0.50, `gpt-6.1-sol` $2.00 / $10.00 per million input / output tokens. Lean calls: 1 per case-arm, except Long Exam (direct, Long pool and Board pool) which makes 2 batches. The baseline model is the route's production model (Addendum 1): `gpt-4.1-mini` for the interactive and critique workflows, `gpt-4.1` for Interview Practice and the collection companion. The reasoning multiplier `h` multiplies the OUTPUT tokens of the reasoning candidates.

| Workflow | Lean calls | Baseline model | Baseline forecast | Luna h=1 | Luna h=3 | Sol h=1 | **Proposed ceiling (1.25x baseline forecast)** |
|---|---:|---|---:|---:|---:|---:|---:|
| Note-scoped Adaptive Practice | 1 | `gpt-4.1-mini` | $0.00298 | $0.00088 | $0.00217 | $0.01750 | **$0.00373** |
| Plan-scoped Adaptive Practice | 1 | `gpt-4.1-mini` | $0.00298 | $0.00088 | $0.00217 | $0.01750 | **$0.00373** |
| Teacher quiz | 1 | `gpt-4.1-mini` | $0.00712 | $0.00214 | $0.00574 | $0.04280 | **$0.00890** |
| Board Exam generation | 1 | `gpt-4.1-mini` | $0.00358 | $0.00108 | $0.00287 | $0.02150 | **$0.00447** |
| Long Exam (direct, Long pool, Board pool) | 2 | `gpt-4.1-mini` | $0.01424 | $0.00428 | $0.01148 | $0.08560 | **$0.01780** |
| Quick Review study tip | 1 | `gpt-4.1-mini` | $0.00015 | $0.00004 | $0.00007 | $0.00080 | **$0.00018** |
| Interview Practice generation | 1 | `gpt-4.1` | $0.03560 | $0.00214 | $0.00574 | $0.04280 | **$0.04450** |
| Collection companion | 1 | `gpt-4.1` | $0.03560 | $0.00214 | $0.00574 | $0.04280 | **$0.04450** |
| Ask Companion | 1 | `gpt-4.1-mini` | $0.00712 | $0.00214 | $0.00574 | $0.04280 | **$0.00890** |
| Interview critique | 1 | `gpt-4.1-mini` | $0.00712 | $0.00214 | $0.00574 | $0.04280 | **$0.00890** |

**Reading to confirm or amend (the owner decides):**
1. **One ceiling per workflow, applied identically to both arms, set from the BASELINE forecast.** That is the reading that matches the preregistration's wording ("applied identically to arms") and the freeze record's one-value-per-workflow field. It makes the rule mean "the migration must not make a delivered case cost more than 1.25 times what production costs today", which is the product question. The baseline forecast is accurate: the dated production logs show Adaptive at 2,147 in and 1,288 out against a 2,250 and 1,300 forecast, and Long Exam batches at 3,305 and about 3,300 out against 3,400 and 3,600.
2. **Luna passes the ceiling at both reasoning multipliers** (the Luna columns sit below the ceiling everywhere, even at h=3). Solving for the multiplier at which Luna reaches the ceiling gives about **4.8 to 5.4** for the interactive and critique workflows, about **10** for the study tip and about **24** for the two paid workflows. A COST event on Luna therefore means its real output is roughly five or more times the non-reasoning size, which would already have breached the Stage 1 cap (the $12 cap holds only up to about 4.08 times, preregistration Addendum 2): the cap trips first, and the ceiling is the second alarm for the same condition.
3. **Sol cannot pass this ceiling on the interactive and critique workflows.** At h=1 Sol costs about **4.4 to 4.8 times** the ceiling on all eight of them; only the two paid workflows come in just under it (about 0.96 times, because the baseline there is the more expensive `gpt-4.1`). If Sol is ever run (Stage 3, a separate authorization), it would carry a T3 COST component on nearly every interactive case, so the Sol rule's "stay within every other gate" cannot be met unless the owner decides a separate Sol ceiling at that time. This is a consequence to know now, not a decision to make now.
4. **Five workflows have no logged token sizes** (teacher quiz, Interview Practice, the collection companion, Ask Companion, Interview critique), so their forecasts use the largest observed size and their ceilings are probably loose (an over-forecast makes a COST event less likely, never more). The owner may prefer to tighten these after seeing the baseline arm's actual token counts, but only BEFORE any candidate output is scored (never after seeing results).
5. The T3 COST component carries the weight of one noncritical event per case and arm inside the pooled D margin; it is not an independent gate.

## Part 2. Class-B human scorer instructions (DRAFT for the owner's edits)

**Who:** you, as the human scorer, for all 66 primary pairs (study tip 30, collection companion 12, Ask Companion 12, Interview critique 12). Automated scorers, if used, are a second opinion only and never decide.

**What you see:** a blinded packet. Each item shows the input the model was given (the synthetic fixture: the missed questions for the study tip, the collection and sections for the companion, the companion content and the question for Ask Companion, the question and the selected choice for the critique) and two outputs labelled by position only. You never see which output came from which model, a model name or a token count. The arm key is a separate file you do not open until all scoring is done.

**For each output, answer four yes-or-no questions, in this order, using ONLY the supplied input as the source of truth:**
1. **Task fulfilled.** Did the output do what this workflow is for? (Study tip: one short, usable tip about the listed questions. Companion: the requested sections, usable by a curator. Ask Companion: an answer to the learner's question, or a clear statement that the companion does not cover it. Critique: a verdict, a rationale and a follow-up that fit the selected choice.) No = off-task, empty, truncated, or the wrong language or format.
2. **Grounded in the supplied context.** Is every factual claim supported by the input? No = it states something the input does not support, or contradicts the input.
3. **Actionable and helpful without an unsupported claim.** Could the reader act on it, and does it avoid inventing resources, rules, scores, citations or promises? No = vague, circular, generic filler, or it invents.
4. **No critical misleading feedback.** Would a learner or curator be actively misled in a way that matters? **No on this question is a CRITICAL failure.** Examples that fail it: advice that contradicts the supplied answer or key; for Ask Companion, an answer that invents information beyond the companion's authored content; a fabricated source; telling a learner a wrong choice was right. A merely weak or bland output is NOT critical.

**Recording (one row per output, matching the validator `ClassBScoreFiles`):** the four booleans (`taskFulfilled`, `grounded`, `actionable`, `noCriticalMisleading`); `critical` is `true` exactly when question 4 is "no"; `reasonCode` is required whenever ANY question is "no" and forbidden otherwise, one of `TASK_NOT_FULFILLED`, `UNGROUNDED`, `UNSUPPORTED_OR_NOT_ACTIONABLE`, `CRITICAL_MISLEADING`, `MULTIPLE_DEFICITS` (use `MULTIPLE_DEFICITS` when more than one question is "no" and none of them is question 4; use `CRITICAL_MISLEADING` whenever question 4 is "no"); `borderline` is `true` when you were genuinely unsure between yes and no on any question; `scorerId` is the role label `HUMAN_OWNER`, never a name; `round` is 1 for a first pass.

**Rules that protect the result:**
- Score each output on its own merits first, then compare the two; never let the other output in the pair change a yes or no.
- A case passes quality only if all four are "yes". Do not average and do not give partial credit.
- If you cannot decide, mark `borderline` and make your best call; a borderline item may be re-scored in an adjudication round (a later round of yours that supersedes the earlier one), and an unresolved disagreement between two human rounds without that adjudication leaves the pair INCONCLUSIVE.
- A shared defect (both outputs have the same flaw on the same case) is recorded for BOTH; it is treated as pre-existing debt, not charged to the candidate.
- Do not use your memory of how either model usually writes. Do not open the arm key before you finish.
- Estimated time (a planning figure, not measured): about 3 minutes per pair, about 3.3 hours for all 66, best done in two or three sittings; stop and note it if fatigue changes your judgement, and re-score the last few items fresh.

**Not in this sheet:** class A (the answer-key review). Who scores class A, and whether it needs the same human or other-family validation, is still the owner's open decision, and the supplemental class-A review (multiple defensible answers, teaching value) is unpreregistered.
