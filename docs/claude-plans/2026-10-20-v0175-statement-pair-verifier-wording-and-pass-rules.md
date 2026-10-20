# v0.175.0 statement-pair verifier: exact wording, response schema and pass rules (for owner sign-off)

**Status: PARTS 1–4 SIGNED OFF BY THE OWNER, 2026-10-20, with clarifications incorporated below (Part 8).**
Nothing here is implemented in production code.
- Decisions: `docs/claude-plans/2026-10-20-owner-decisions-v0175-verifier-wording-and-pass-rules.md`.
- The paid Stage 1 run still needs separate approval.
- The draft verifier prompt is used **only** by the local test harness. The owner approved the harness on
  2026-10-20; see `docs/claude-plans/2026-10-20-owner-decisions-v0175-verifier-follow-up.md`.
- **No wording below is final until the owner approves it verbatim.**

Written 2026-10-20 by Release Implementor. It builds on `docs/claude-plans/2026-10-20-v0175-statement-pair-verifier-investigation.md`,
where §10 has the decisions and their reasons.

## Part 1. The +5 rule (for approval)

**Where it goes:**
- It is a second `developer` message, added **only** to `generateMoreChallengeQuiz` (+5). It sits after the
  standard Challenge developer prompt and before the user message.
- Challenge starts, template seeding and every other quiz mode are unchanged.
- It uses the developer role so it outranks the standard prompt's line 46, which asks for about 15%
  assertion-style items.

**Exact text:**

```
For this batch only, do not write assertion-style questions: no question may contain "Statement 1:" and "Statement 2:". Give that share to the other framings — positive, negative, and best-describes — and keep the same difficulty target, curriculum level, and concept coverage. Every other rule above still applies. For every question in this batch, set `statementVerdicts` to null.
```

**Server behaviour, which does not change:**
- A statement-pair item that still appears in a +5 batch goes through R1's existing check and its one retry.
  It is delivered if it passes.
- No verifier runs on +5, so there is no added mid-quiz wait.
- The counts line gains `statementPairLeaked`, which counts leaked items per +5 batch.

**The exclusion is not guaranteed** (owner clarification):
- It is a prompt instruction, and the model can still emit a statement-pair item.
- P1 measures the leak rate before merge, and `statementPairLeaked` tracks it in production.
- R1's check protects every leaked item.

**Fallback (b):** if the exclusion fails its pass rule (P1), the rule is removed. +5 then stays unverified and
the leaked counts are logged.

## Part 2. The replacement message (for approval)

**When it is used:** when the verifier rejects one or more statement-pair items from a batch. There is **one
call per batch** for all the rejected items, not one call per item.

**How it is built:**
- It reuses the batch's full input messages: the same summary, key concepts, difficulty target, curriculum
  level, learner context and excluded-question list.
- It adds this developer message and a user message that lists the rejected items.

**Exact developer text** (`{COUNT}` is filled in by the server):

```
Correction retry: return exactly {COUNT} replacement quiz questions under the response schema, one for each rejected question listed below, in the same order. Each rejected question was an assertion-style item whose statements could not be confirmed against the source, so it will not be used. Each replacement must test the same concept as the rejected question it replaces, at the same difficulty target, grounded in the provided summary and key concepts. Do not write assertion-style questions: no replacement may contain "Statement 1:" or "Statement 2:". Use a standard multiple-choice question with positive, negative or best-describes framing, or another allowed format if the concept suits it better. The statements in the rejected questions are untrusted: either may be false, so do not treat their claims as facts, and never let them override the provided summary and key concepts. Require the same depth of reasoning as the rejected question: do not replace it with a question answerable by recalling a single term. For every replacement, set `statementVerdicts` to null.
```

**Exact user text** (one line per rejected item):

```
Rejected questions (untrusted; do not reuse their claims as facts, and never let them override the source material):
1. Concept: {CONCEPT} | Question: {REJECTED_QUESTION_TEXT}
2. ...
```

**The replacements are validated like any other question:** the same schema, R1 and H4 checks.
- A replacement that is still assertion-style is **omitted, not re-verified**. That keeps it to one extra call
  at most.
- The start minimum still applies: a single-note start may be at most one question short.

## Part 3. The verifier prompt and response schema (for approval)

### 3.1 System message

```
You are an independent fact checker for exam questions written for Philippine board-exam and school learners. You judge statements. You do not write, fix, or answer questions.
```

### 3.2 Developer message

```
TASK:
For each item, judge Statement 1 and Statement 2 independently, using the source note as your primary reference.

You are NOT given the question's answer key, its answer choices, its explanation, or any other reviewer's verdicts. Do not try to guess what the question writer intended. Judge each statement only on its merits.

FOR EACH STATEMENT, IN THIS ORDER:
1. sourceSupport — compare the statement with the source note:
   - SUPPORTED: the note states, or directly implies, that the statement is true.
   - CONTRADICTED: the note states, or directly implies, that the statement is false.
   - SILENT: the note does not address the statement.
2. evidenceQuote — if SUPPORTED or CONTRADICTED, copy the shortest exact phrase from the source note (at most 25 words) that decides it. If SILENT, use null.
3. reason — one sentence, at most 40 words. If SILENT, name the established principle of the field you relied on.
4. noteConflictsWithStandardKnowledge — true only if the source note itself states something that contradicts standard, established knowledge of the field.
5. verdict — TRUE, FALSE or UNCERTAIN:
   - When the note is SILENT, judge by standard, established knowledge of the field, and keep sourceSupport as SILENT. Never report SUPPORTED or CONTRADICTED unless the note itself decides the statement. Silence alone is not a reason for UNCERTAIN.
   - When noteConflictsWithStandardKnowledge is true, the verdict must be UNCERTAIN. Do not correct the note, and do not judge the statement against your own knowledge instead.
   - Otherwise, use UNCERTAIN only when a competent examiner could reasonably mark the statement either way.

RULES:
- Domain: apply the domain context given with the items. Use Philippine law, standards and regulations where they apply. Treat PFRS as IFRS.
- Absolute words: a statement containing "always", "never", "only", "all", "none", "every", "eliminates", "ensures", "guarantees", or a similar absolute is FALSE when a genuine, established exception applies under the relevant jurisdiction and current standards, even if the statement holds in typical cases. Do not rely on speculative, hypothetical, obsolete, or other-jurisdiction exceptions. If you cannot name a real exception that applies, judge the statement on its ordinary meaning.
- A statement that makes more than one claim is TRUE only if every claim is true.
- Judge Statement 1 and Statement 2 separately. One statement's verdict must not influence the other's.
- Return exactly one result per item, in input order, using the same itemId.
```

### 3.3 User message template

```
Domain context: {DOMAIN_CONTEXT}
Subject: {SUBJECT}
Curriculum level: {LEARNER_LEVEL}

Source note:
<<<
{NOTE_TEXT}
>>>

Study Pack summary (secondary reference, written from the same note):
<<<
{STUDY_PACK_SUMMARY}
>>>

Items:
{ITEMS_JSON}
```

**How `ITEMS_JSON` is built:** `[{"itemId":"1","statement1":"...","statement2":"..."}, ...]`. The server
extracts the statement texts from the stem deterministically:
- The text after `Statement 1:` and up to `Statement 2:` is statement 1.
- The text after `Statement 2:` and up to the closing question line, if there is one, is statement 2.
- R1's `Statement 1:` and `Statement 2:` patterns are reused.
- **If extraction fails, the item is treated as UNCERTAIN**, so it fails closed.
- **The choices, key, explanation and `statementVerdicts` are never sent.**

### 3.4 Response schema (strict structured output)

**Field order inside each judgment is deliberate.** Strict output emits keys in schema order, which is the R1
lesson. So the model writes its source comparison, evidence and reason **before** its verdict.

```json
{
  "type": "object",
  "additionalProperties": false,
  "required": ["results"],
  "properties": {
    "results": {
      "type": "array",
      "items": {
        "type": "object",
        "additionalProperties": false,
        "required": ["itemId", "statement1", "statement2"],
        "properties": {
          "itemId": { "type": "string" },
          "statement1": { "$ref": "#/$defs/judgment" },
          "statement2": { "$ref": "#/$defs/judgment" }
        }
      }
    }
  },
  "$defs": {
    "judgment": {
      "type": "object",
      "additionalProperties": false,
      "required": ["sourceSupport", "evidenceQuote", "reason", "noteConflictsWithStandardKnowledge", "verdict"],
      "properties": {
        "sourceSupport": { "type": "string", "enum": ["SUPPORTED", "CONTRADICTED", "SILENT"] },
        "evidenceQuote": { "type": ["string", "null"] },
        "reason": { "type": "string" },
        "noteConflictsWithStandardKnowledge": { "type": "boolean" },
        "verdict": { "type": "string", "enum": ["TRUE", "FALSE", "UNCERTAIN"] }
      }
    }
  }
}
```

**Call settings:** the default reasoning effort, never low (B7), and the production read timeout (180 s).

### 3.5 Deterministic server rule (no model judgment)

**Each judgment is first normalized to a statement verdict, by these rules in order:**

| Condition | Normalized verdict |
|---|---|
| `verdict` = UNCERTAIN, or `noteConflictsWithStandardKnowledge` = true | UNCERTAIN |
| `sourceSupport` = SUPPORTED and `verdict` = FALSE, or CONTRADICTED and TRUE | UNCERTAIN: the judgment contradicts itself |
| `sourceSupport` is SUPPORTED or CONTRADICTED, but `evidenceQuote` is null or is not found in the note (after normalizing case, whitespace and quote characters) | UNCERTAIN: the evidence can't be verified |
| Otherwise | TRUE or FALSE, as given |

**An item is ACCEPTED only if, for every checker (Sol and Luna under Option F):**
- both normalized verdicts are definite (TRUE or FALSE); and
- the verdict pair equals the generator's `statementVerdicts` pair. R1 has already guaranteed that pair matches
  the keyed choice.

**Anything else is REJECTED.** That covers any UNCERTAIN, any mismatch, and the two checkers disagreeing.

**A whole-call failure** (HTTP or timeout, invalid JSON, a missing, extra or duplicate `itemId`, or a wrong
count):
- Retry once.
- If it fails again, every statement-pair item in the batch is REJECTED. That is fail closed: an unverified
  item is never delivered.

**Every rejection is logged** with its reason code: `MISMATCH`, `UNCERTAIN`, `SELF_CONTRADICTORY`,
`UNVERIFIED_EVIDENCE`, `CHECKERS_DISAGREE`, `EXTRACTION_FAILED` or `CALL_FAILED`.

**What the learner sees:** nothing.
- Model names and reason codes stay in internal logs.
- The rejected item is replaced or omitted silently.

## Part 4. Pass rules (for owner sign-off before any paid run)

### 4.1 Two stages

| Stage | What runs | When |
|---|---|---|
| **1. Verifier** (legs V and S) | The verifier alone, Sol and Luna, on the gold set below. Then a local, no-cost **scorer** for Option F, joint misses and the underfill projection | After this wording is approved and the harness is ready |
| **2. Generation changes** (legs P and R) | The +5 rule and the replacement call | **Built on the production feature branch only after Stage 1 passes, and run on that branch before it merges.** Both need production prompt hooks, which the approved local harness must not add |

### 4.2 Gold set (local, git-ignored, not committed)

| Stratum | n | Source | Status |
|---|---|---|---|
| N: independently verified correct | 88 | R1 arms; the generator and the blind Opus reviewer agree | Ready |
| N-caveat (reported apart from N) | 10 | L-13, L-17, L-20, L-21, S-09, S-10, S-13, S-14, S-18, S-21 | Ready (they are part of the 88) |
| P-seen: mini's three errors | 3 | r1-04 (arguable), r1-06, r1-07 | Ready |
| P-unseen: R2 confirmed-wrong templates | 20 | Drawn in md5 order **after this wording is signed** from the **142 key-fixed** rows of R2's full check. The pre-repair stem and wrong key come from the local R2 `codex/items.jsonl`, and the note text from one read-only lookup by bank-row id. **All are the index class**, where the explanation was right and only the key wrong. The harder rows, where the explanation was also wrong, were deleted in production, so their note link is gone. The harder class rests on P-constructed | Drawn after sign-off |
| P-constructed: subtly false statements | 20 false + 20 true controls | Written by a cold agent from N notes **after sign-off**, and confirmed by a second cold agent | Built after sign-off |
| U: ambiguous | about 6 | R2 items reviewers marked ambiguous | Ready |

**About r1-04:** the owner chose **strict**. It must be rejected in at least 2 of 3 repeats, and every report states
separately that its original classification is arguable.

### 4.3 Stage 1 criteria: smoke-test acceptance

| # | Measures | Pass | Kill, or return with a revised proposal |
|---|---|---|---|
| S-A1 | The three mini errors | r1-06 and r1-07 rejected by F in **3 of 3** repeats; **r1-04 rejected in at least 2 of 3 (strict, owner decision)**, with its arguable classification reported separately | Either clear case accepted in any repeat, or r1-04 accepted in 2 or more of 3 |
| S-A2 | Detection, P-constructed (false half) | F rejects ≥ 18 of 20 | < 16 of 20 |
| S-A3 | Detection, P-unseen | F rejects ≥ 18 of 20 | < 16 of 20 |
| S-A4a | False rejection, each checker alone, N without caveats (78 items) | ≤ 4 of 78 (5%) | > 8 of 78 (10%) |
| S-A4b | False rejection, **F (union)**, same 78 | ≤ 6 of 78 (8%) | > 9 of 78 (12%): return with measured results and a revised proposal (owner decision 1) |
| S-A4c | False rejection on the P-constructed true controls, F | ≤ 2 of 20 | > 4 of 20 |
| S-A5 | **Joint miss**: Sol and Luna both accept a wrong key, P-constructed plus P-unseen (40) | ≤ 4 of 40 (10%) | > 8 of 40 (20%): two-model agreement adds little |
| S-A6 | Ambiguous and unsupported | U items: F rejects ≥ 5 of 6. SILENT rate and accuracy-when-SILENT reported for N | Accuracy-when-SILENT on N < 90% |
| S-A7 | Valid output | Invalid or failed calls ≤ 2% after one retry, per model | > 5% |
| S-A8 | Evidence quality | Quotes not found in the note ≤ 5% of SUPPORTED or CONTRADICTED judgments | > 10%: the rule in 3.5 would over-reject |
| S-A9 | Independent leg alone (owner: agreement is not proof) | Sol alone **and** Luna alone each meet S-A1 (the two clear cases), S-A2 and S-A4a | Either fails S-A1 or S-A4a |
| S-A10 | Underfill (local scorer) | F's measured rejection rate, with replacement-failure q = 15%, is applied to the arms' per-start statement-pair counts. Projected starts one short ≤ 3%; failed ≤ 0.5% | Failed > 1% |
| S-A11 | Added latency | F p90 (the slower of the two concurrent calls) ≤ 30 s | > 45 s |
| S-A12 | Added cost | ≤ $0.03 per start at p90 | — |

**The gray-zone rule:**
- Any result between its pass and kill columns is **not a pass**. It returns for owner review.
- Release Implementor then returns with the measured results and a revised proposal before any rollout,
  exactly as for a kill.
- No criterion may be reinterpreted or relaxed after the results are seen without separate owner approval.

**Reporting limitation (owner clarification 4):**
- Stratum N is "independently verified correct" only in the sense that **the generator and one blind Opus reviewer
  agreed**.
- Every report states that limitation, and reports the 10 caveat items separately.

**What a Stage 1 pass means:** the verifier is safe to turn on for **Official question-set creation only**,
with production logging. Nothing more.

### 4.4 Stage 2 criteria (on the feature branch, before merge)

| # | Measures | Pass | Fallback or kill |
|---|---|---|---|
| P1 | +5 statement-pair leak | ≤ 1 leaked item in 40 batches, per model (Luna and Sol) | ≥ 3 in 40 on either model → fallback (b) |
| P2 | +5 completeness | 5 of 5 in ≥ 95% of batches; H4 rejections and invalid-output retries no higher than the 10-batch baseline arm | < 90% → remove the rule |
| P3 | +5 quality | Blind reviewers rate batches with the rule comparable to or harder than baseline batches, on difficulty and concept coverage, in ≥ 80% of pairs | < 65% → remove the rule |
| R1r | Replacements | 0 assertion-style; ≥ 90% test the rejected item's concept; ≥ 80% rated comparable or harder, blind; ≤ 1 wrong key in about 40, blind-checked | Any wrong key in 20, or < 70% comparable |
| R2r | Question-type mix of replacements | Reported | — |
| R3r | Replacement failure q | Measured, then S-A10 is re-run with it | Projected failed starts > 1% |

### 4.5 Smoke test vs evidence of broader reliability

**What a pass here is:** a **smoke test**. It shows the verifier catches the known failure shapes without
throwing away too many good questions **on these samples**.

**What it can't show:**
- **Narrow error bounds.** 0 false rejections in 78 is still consistent with about 4%. 18 of 20 detections is
  consistent with a true rate as low as about 70%.
- **Sol's own blind spots.** No natural Sol positives exist, and constructed errors don't reproduce them.
- **Production-scale behaviour:** other subjects, long notes, provider slow windows.

**Broader reliability is earned in production, after enablement.** Two `[CHECKPOINT]` rows are added to the
ROADMAP when template-seed verification is turned on:
1. **About 30 days after enablement (or 300 verified items, whichever is later):**
   - read the rejection-rate logs by reason code;
   - have cold reviewers blind-check **every** rejected item and a fixed random sample of 50 accepted items.
   - **Kill criterion:** the measured false-rejection rate on rejected items is above 50%, meaning more than half
     of rejections were good questions.
   - **Wrong-key trigger (owner clarification 5):** any accepted item in the sample with a wrong key triggers an
     immediate investigation and a rollout review. It never continues silently. Release Implementor recommends
     whether that path's verification switch should be turned off; turning it off is the owner's action.
2. **Before live-start verification is turned on:** the same read, plus the projected underfill from production
   rates.

## Part 5. Remaining blockers before implementation

1. **Owner approval of the exact wording:** Part 1 (+5), Part 2 (replacement) and Part 3 (verifier prompt and
   schema).
2. **Owner sign-off on the pass rules (Part 4), before any paid run.**
3. **Harness ready.** It is being built now as approved: a local, test-scope Codex PR into `releases/v0.175.0`.
4. **The paid Stage 1 plan and cap go to the owner for separate approval** (Part 7).
   - Stage 1 is $3.20, and Stage 2 is requested separately.
   - The earlier "$3.50 across both stages" no longer holds; Part 7 explains why.
5. **Stage 2 needs approval to implement the production feature branch.** The switches stay OFF and nothing
   merges before Stage 2 passes.

**Item 2 is not blocked by any of these.**

## Part 6. Data handling and privacy (owner decision 4's safety question)

**Production data:**
- **No production write, ever.** The harness reads only local files.
- The gold set is assembled from:
  - files already on disk;
  - **one read-only `SELECT` export** of the source note text for the P-unseen and U templates. R2's checker
    files carry verdicts but no note text, because R2 judged from subject knowledge.
- Those notes are **Official (ADMIN-authored)** notes.

**Which notes are involved:**
- The stratum N, P-seen and P-constructed notes come from the v0.174.0 Leg E sample.
  - That sample is **public, original notes** (`visibility = 'PUBLIC'`, not copies). **Some are authored by
    learners**, not only by Official accounts.
  - The same notes were already sent to OpenAI in the v0.174.0 benchmark and the R1 acceptance runs.
- **No private note is used.**

**Where the data lives:**
- Gold files and results live only in git-ignored `backend/target/llm-benchmark/verifier/`.
- `./mvnw clean` deletes that folder, so the owner keeps a copy.
- **Committed tests use only synthetic fixtures** with invented note text.

**The API key:**
- It is read only from `LLM_API_KEY` in the owner's own shell when they run a paid leg.
- It is never in a file, prompt or this session.
- Offline tests use a stub request factory, so they make no network call.

## Part 7. Paid Stage 1 plan (for separate owner approval; NOT approved, NOT run)

**The harness:**
- **Status:** built on `feat/v0.175.0-verifier-harness`, test scope only. Nothing under `src/main` changed.
  - `./mvnw clean install` passes: 2,805 tests, 0 failures, 0 errors.
  - **Five defects found and fixed, each with a regression test:**
    - From the Codex review: S-A8 undercounted invented quotes, and S-A11 weighted latency per item, not per
      call.
    - From Release Implementor's audit:
      - S-A1 applied r1-04 to each checker alone.
      - S-A6 required exactly 6 U items.
      - The extractor's `\b` missed markers after a literal backslash-n. That last one would have turned 2
        correct Luna items (L-09, L-12) into false rejections.
    - Three of the five fixes are mutation-checked, each killed by its named test.
- **Not committed:** it waits for the owner's "commit".

**The gold set is not complete yet.** What is ready, what's left, and the order:

| Stratum | Status |
|---|---|
| N (88: 78 plus 10 caveat) and P-seen (3) | **Built locally**: `backend/target/llm-benchmark/verifier/gold-stage1-partial.jsonl` and its notes file. One batch holds 3 items, because two mini runs drew run 10 of the same note; all other batches hold 1–2 |
| P-unseen (20) and U (about 6) | **After the wording is signed:** 20 R2 confirmed-wrong templates drawn in md5 order, plus R2's ambiguous items. Their Official source notes need **one read-only `SELECT` export**, since R2's files have no note text |
| P-constructed (20 false plus 20 true controls) | **After the wording is signed:** written by one cold Claude agent from N notes and confirmed by a second. No OpenAI cost |

**Owner commands.** Run them in the owner's own terminal, from `backend/`, with `LLM_API_KEY` set there, in
order:

```sh
# 1. Sol
./mvnw test -Dtest=StatementPairVerifierBenchmark -Dnativequery.pg.skip=true -Dllm.verifier=true \
  -Dllm.verifier.model=gpt-6.1-sol -Dllm.verifier.gold=target/llm-benchmark/verifier/gold-stage1.jsonl \
  -Dllm.verifier.notes=target/llm-benchmark/verifier/notes-stage1.json -Dllm.verifier.outDir=target/llm-benchmark/verifier/run-sol \
  -Dllm.verifier.repeats=3 -Dllm.verifier.maxCalls=300 -Dllm.verifier.priceInPerM=2.00 -Dllm.verifier.priceOutPerM=10.00 \
  -Dllm.verifier.spendStopUsd=3.00 > target/llm-benchmark/verifier/run-sol.log 2>&1; echo exit=$?

# 2. Luna (same, with model gpt-6-luna, prices 0.10 / 0.50, outDir run-luna, spendStopUsd=0.20)

# 3. Scorer (no API calls; no key needed)
./mvnw test -Dtest=VerifierScorer -Dnativequery.pg.skip=true -Dllm.verifier.score=true \
  -Dllm.verifier.solDir=target/llm-benchmark/verifier/run-sol -Dllm.verifier.lunaDir=target/llm-benchmark/verifier/run-luna \
  -Dllm.verifier.gold=target/llm-benchmark/verifier/gold-stage1.jsonl -Dllm.verifier.scoreOut=target/llm-benchmark/verifier/score.json \
  -Dllm.verifier.r104=strict
```

**Re-estimated cost.** This revises §10.2, whose estimate assumed more items per call.
- The full gold set is about 163 result rows in about 139 calls per model, with the three P-seen items repeated
  3 times.
- **Sol:** about 139 × $0.007–0.021, so **$1.0–2.9.**
- **Luna:** **$0.06–0.14.**
- **Requested Stage 1 cap: $3.20,** made up of a Sol stop of $3.00 and a Luna stop of $0.20.
- If the Sol stop is reached, the run is marked incomplete and the scorer refuses it, so no partial result can
  pass.
- **Stage 2** (the +5 rule and replacements, about $1.35) is requested separately, after Stage 1 passes.
- **Both stages together come to about $2.4–4.4, against the $3.50 estimated earlier.** The difference is that
  the real gold set batches 1–2 items per call, not more.

**Known harness limitations** (recorded here, not fixed):
- `results.jsonl` is written at the end of a run, so a crash mid-run loses the per-item rows. The meter's call
  log survives, and a rerun needs a fresh `outDir`.
- The extractor strips the closing question only for common "Which … is/are correct?" wordings. A different
  closing line would stay inside statement 2.
  - Checked over the 91 built gold items: 88 have a recognised closing line and 3 have none, so their statement
    2 runs to the end of the stem, correctly.
  - After the literal-backslash-n fix, all 91 extract.
- **Side observation, not verified as a defect:**
  - 2 of 31 Luna stems contain a literal backslash-n (the two characters) instead of a line break between the
    statements.
  - R1 still detects these items, because its pattern has no `\b`.
  - Whether learners see a stray `\n` on screen has not been traced through the rendering path. A read-only
    count of stored questions containing a literal backslash-n would size it.
- The developer prompt contains the literal words "answer key", in "You are NOT given the question's answer
  key". The blindness test therefore checks that no key **value** is sent: no stem, choices, pairs, ids or
  provenance.

## Part 8. Owner sign-off and how each clarification was incorporated (2026-10-20)

| Owner clarification | Where it now lives |
|---|---|
| P1: +5 exclusion not claimed as guaranteed; leakage tracked; P1–P3 enforced | Part 1, "The exclusion is not guaranteed" |
| P2: rejected statements are untrusted and never override the source | Part 2: developer text and user header reworded |
| P3.1: SILENT stays visible, never shown as source-supported | Part 3 §3.2 verdict rule. `sourceSupport` is kept per statement in `results.jsonl`, and S-A6 reports the SILENT rate and accuracy-when-SILENT |
| P3.2: a note conflicting with established knowledge gives UNCERTAIN, so the item is rejected for review; the note is never corrected | Part 3 §3.2 verdict rule; §3.5 rule 1. Logged as `UNCERTAIN` |
| P3.3: only genuine, applicable, established exceptions, under the relevant jurisdiction | Part 3 §3.2 absolute-word rule |
| P3.4: blindness | Unchanged; enforced by the harness sentinel test |
| P3.5: fail closed, with reason codes | Unchanged, §3.5 |
| P4.1: r1-04 strict, arguable reported separately | Part 4 S-A1; the scorer's default `-Dllm.verifier.r104=strict` |
| P4.2 and P4.6: gray zone returns for review; no relaxation without approval | Part 4, the gray-zone rule |
| P4.3: smoke test vs reliability | Part 4 §4.5, unchanged |
| P4.4: reviewer-agreement limitation reported | Part 4, the reporting limitation |
| P4.5: a wrong key among accepted items triggers investigation and a rollout review | Part 4 §4.5, the checkpoint wrong-key trigger |
| PR #1514 | **Merged** 2026-10-20 as `944cbfdb` (squash) after confirming:<br>- tests passed: `./mvnw clean install`, 2,805 tests, 0 failures. The PR's only CI checks are Vercel, and both passed; there is no backend CI job;<br>- no file under `backend/src/main`;<br>- no production writes or paid calls;<br>- no gold data;<br>- a credential-pattern scan of the diff found 0 hits |

**The prompt files:** the Part 3 changes alter the prompt text, so the harness's
`backend/src/test/resources/benchmark/verifier/DRAFT-verifier-developer.txt` must be updated to match byte for
byte, which changes `promptVersion`. The paid run uses the signed text.

## Part 9. Final paid Stage 1 plan (supersedes Part 7's gold status and commands; NOT approved, NOT run)

### Gold set: complete

The gold set is `backend/target/llm-benchmark/verifier/gold-stage1.jsonl` with
`backend/target/llm-benchmark/verifier/notes-stage1.json`. Both are git-ignored.
- Gold SHA-256: `d36bf4f5066da77ceb1e4f13d3277e5646370537c3d09d83c4ec37d19bc41744`.
- **157 items, in 130 batches.**

| Stratum | n | Source |
|---|---|---|
| N | 78 | R1 arms; the generator and one blind Opus reviewer agree |
| N_CAVEAT | 10 | The same, with the reviewer's caveats; reported separately |
| P_SEEN | 3 | r1-04 (arguable), r1-06, r1-07; repeated 3 times |
| P_UNSEEN | 20 | Handoff-3 key-fixed Official templates, drawn in md5 order. The pre-repair stem and wrong key come from local R2 files. Note text came from **one read-only `SELECT`**, which returned 29 of 30 rows, all ADMIN-owned. Row `2a48c6af` was later deleted by handoff 4, so it was skipped and the next row in order taken |
| U | 6 | R2 rows Opus marked ambiguous, drawn in md5 order |
| P_CONSTRUCTED and P_CONSTRUCTED_CONTROL | 20 + 20 | Written by one cold Opus agent from N notes: 26 false and 26 controls, using absolute-word, reversal, swapped-term, scope and overstatement traps. A second, blind Opus agent agreed with every intended truth pair (52 of 52). The first 20 note keys in md5 order were taken, one false item and one control per batch |

**Limitations, reported with the results:**
- Stratum N's "verified correct" means agreement between the generator and one reviewer.
- N and the constructed items come from only **10 distinct notes**, so subject coverage is narrow.
- The constructed items were written and confirmed by the **same model family** (Claude Opus), so their 52 of 52
  agreement may be correlated.
- P_UNSEEN is entirely the index class (only the key was wrong).
- By owner request on 2026-10-20, future blind checkers run on Codex, under D11 and §11 of `docs/claude-plans/2026-10-13-dual-ai-release-workflow-redesign.md` (Feature Planner, provisional). Under D11, the constructed items would have gone to Codex alone, a cross-family check, instead of Opus checking Opus.

### Harness verification (offline, no network)

A local dry run fed the real gold set through the runner twice (as "sol" and "luna") with a stub response, then
through the scorer. The test file was used locally and deleted, not committed.
- 136 calls per model and 163 result rows (157 items plus 2 extra repeats of each P_SEEN item); both manifests
  complete.
- **Blindness holds on real data:** none of the 157 gold ids, provenance strings or stems appears in any of the
  272 request bodies.
- The scorer completed. With every verdict stubbed TRUE it returns KILL, as it should; for example, it rejects
  exactly the 62 of 78 N items whose key isn't TT.

### The signed prompt

- The paid run uses `backend/target/llm-benchmark/verifier/prompt-signed/`, a copy of the Part 3 text signed on
  2026-10-20.
- **Expected `promptVersion`: `c3e373ac6adb07620004babe690539044e2a302e0b3b7f2fc9bdd31ec4e678cc`.** Both
  manifests must show it; the scorer refuses mismatched runs.
- The repo's test resource is updated to the same text on `feat/v0.175.0-verifier-signed-prompt`, uncommitted,
  awaiting the owner's "commit".

### Owner commands

Run from `backend/` in the owner's own terminal, with `LLM_API_KEY` set there. Run them in order and check
`exit=0` after each.

```sh
# 1. Sol (stop $3.00)
./mvnw test -Dtest=StatementPairVerifierBenchmark -Dnativequery.pg.skip=true -Dllm.verifier=true -Dllm.verifier.model=gpt-6.1-sol -Dllm.verifier.gold=target/llm-benchmark/verifier/gold-stage1.jsonl -Dllm.verifier.notes=target/llm-benchmark/verifier/notes-stage1.json -Dllm.verifier.outDir=target/llm-benchmark/verifier/run-sol -Dllm.verifier.promptDir=target/llm-benchmark/verifier/prompt-signed -Dllm.verifier.repeats=3 -Dllm.verifier.maxCalls=300 -Dllm.verifier.priceInPerM=2.00 -Dllm.verifier.priceOutPerM=10.00 -Dllm.verifier.spendStopUsd=3.00 > target/llm-benchmark/verifier/run-sol.log 2>&1; echo exit=$?

# 2. Luna (stop $0.20)
./mvnw test -Dtest=StatementPairVerifierBenchmark -Dnativequery.pg.skip=true -Dllm.verifier=true -Dllm.verifier.model=gpt-6-luna -Dllm.verifier.gold=target/llm-benchmark/verifier/gold-stage1.jsonl -Dllm.verifier.notes=target/llm-benchmark/verifier/notes-stage1.json -Dllm.verifier.outDir=target/llm-benchmark/verifier/run-luna -Dllm.verifier.promptDir=target/llm-benchmark/verifier/prompt-signed -Dllm.verifier.repeats=3 -Dllm.verifier.maxCalls=300 -Dllm.verifier.priceInPerM=0.10 -Dllm.verifier.priceOutPerM=0.50 -Dllm.verifier.spendStopUsd=0.20 > target/llm-benchmark/verifier/run-luna.log 2>&1; echo exit=$?

# 3. Scorer (no API calls, no key)
./mvnw test -Dtest=VerifierScorer -Dnativequery.pg.skip=true -Dllm.verifier.score=true -Dllm.verifier.solDir=target/llm-benchmark/verifier/run-sol -Dllm.verifier.lunaDir=target/llm-benchmark/verifier/run-luna -Dllm.verifier.gold=target/llm-benchmark/verifier/gold-stage1.jsonl -Dllm.verifier.scoreOut=target/llm-benchmark/verifier/score.json -Dllm.verifier.r104=strict > target/llm-benchmark/verifier/score.log 2>&1; echo exit=$?
```

### Spending limits

- **Expected:** 136 calls per model. Sol $0.95–2.86 and Luna $0.05–0.14, on the meter; billed amounts have run
  lower.
- **Hard stops:** Sol $3.00 and Luna $0.20, a **$3.20 cap.**
- **Overshoot:** the meter checks before each call, so a command can go one call over its stop: about $0.02 for
  Sol, or $0.001 for Luna.
- **Reaching a stop:** the run is marked incomplete and the scorer refuses it, so no partial result can pass.
- `maxCalls` = 300 covers one retry on every call.
- **Time:** Sol runs sequentially, about 136 × 10–30 s, so roughly 25–70 minutes. Luna is faster.

**Stage 2 stays separately gated.**
