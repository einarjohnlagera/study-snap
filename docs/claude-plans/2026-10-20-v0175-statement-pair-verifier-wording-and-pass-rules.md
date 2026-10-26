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

*(Step 2 revised 2026-10-20, owner-approved in Part 10, item 2.)*

```
TASK:
For each item, judge Statement 1 and Statement 2 independently, using the source note as your primary reference.

You are NOT given the question's answer key, its answer choices, its explanation, or any other reviewer's verdicts. Do not try to guess what the question writer intended. Judge each statement only on its merits.

FOR EACH STATEMENT, IN THIS ORDER:
1. sourceSupport — compare the statement with the source note:
   - SUPPORTED: the note states, or directly implies, that the statement is true.
   - CONTRADICTED: the note states, or directly implies, that the statement is false.
   - SILENT: the note does not address the statement.
2. evidenceQuote — if SUPPORTED or CONTRADICTED, copy the shortest phrase from the source note (at most 25 words) that decides it, exactly as written in the note, character for character. Do not add quotation marks. Do not paraphrase. If two places in the note decide it, copy both, separated by " ; ". If SILENT, use null.
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

## Part 10. Stage 1 result (owner-run, 2026-10-20) and revised proposal

### The result: KILL on S-A4b. Recorded as measured; no threshold is relaxed

**Both runs were complete:**
- 136 calls each, 0 failed, 0 invalid.
- Signed `promptVersion` `c3e373ac…`, gold `d36bf4f5…`.
- **Spend: $1.08 on the meter** (Sol $1.015, Luna $0.069), against the $3.20 cap.

| Criterion | Result | Class |
|---|---|---|
| S-A1: mini's three errors, under F | r1-04 3/3, r1-06 3/3, r1-07 3/3 rejected (r1-04's classification is arguable) | PASS |
| S-A2: constructed false items | 20/20 | PASS |
| S-A3: unseen Official wrong keys | 20/20 | PASS |
| S-A4a: false rejection, each checker alone (N, 78) | Sol 5/78, Luna 7/78 | GRAY, GRAY |
| **S-A4b: false rejection, F union (N, 78)** | **10/78 (12.8%)** | **KILL (> 9)** |
| S-A4c: constructed controls | 1/20 | PASS |
| S-A5: joint miss | 0/40 | PASS |
| S-A6: ambiguous rejected | 6/6. SILENT on 79 of 312 statement judgments; accuracy when SILENT 73/79 (92%) | PASS |
| S-A7: failed or invalid calls | 0/136 per model | PASS |
| S-A8: unverified quotes | Sol 1/207, Luna 9/199 | PASS |
| S-A9: each leg alone | Sol 20/20, Luna 20/20 detection, but S-A4a is gray | GRAY |
| S-A10: underfill projection | one short 1.8%, failed under 0.01% | PASS |
| S-A11: latency p90 | 14.2 s | PASS |
| S-A12: cost p90 per batch | $0.011 | PASS |

**Reporting limitations:**
- Stratum N's "correct" label is agreement between the generator and one blind Opus reviewer.
- N and the constructed items come from 10 distinct notes.
- The constructed items were written and confirmed by the same model family.

### What the 10 "false rejections" are (analysis after the result; it changes no threshold)

**Group 1: evidence-quote artifacts, 5 items, all Luna-only (r1-01, r1-17, r1-20, L-16, S-11).**
- **Luna's verdicts matched the key on all five.** The rejection came from rule 3 of §3.5: the quote did not match
  the note exactly.
- Counterfactual, information only, over all of Luna's 9 unverified quotes:
  - stripping wrapper quotation marks recovers 2;
  - also accepting `;`-joined fragments **only when every fragment appears exactly in the note** recovers 5 in
    total;
  - the other 4 are genuine paraphrases, which the rule correctly refuses.

**Group 2: absolute or necessity words, 5 items, all mini-generated (r1-02, r1-05, r1-08, r1-23, r1-30).**
- The rejecting checkers judged a "must / necessary / only if / ultimately" statement false and cited a specific
  established exception.
- **A blind Codex adjudication**, under D11 §11 (read-only, high effort, statements and notes only; mini is R2's
  measured pairing), **sided with the checkers on all 5.** It disagreed with the test-set label each time:

| Item | Test-set label | Codex | Codex's reason |
|---|---|---|---|
| r1-30 | T,T | F,T | Statutory endorsement warranties create liability without dishonor |
| r1-02 | T,F | F,F | Established clinical decision rules clear low-risk patients without imaging |
| r1-23 | T,F | F,F | Thrombolysis only for eligible patients; contraindications apply |
| r1-05 | T,F | F,F | Independent checks can manage cumulative error without supplementary control points |
| r1-08 | T,T | F,T | Under the Negotiable Instruments Law, the drawer may expressly limit liability |

- **What this means:** these five look like **real mini errors the original single-reviewer check missed**, which
  the checkers then caught correctly.
- **The caveat:**
  - Codex, Sol and Luna are all OpenAI-family models, and the original reviewer was Claude Opus.
  - On r1-23 and r1-30, Sol, Luna and Codex agree independently; r1-02, r1-05 and r1-08 rest on Sol plus Codex.
- **If confirmed, the R1 mini finding becomes about 8 of 32 wrong statement-pair keys, not 3 of 32.** Every one is
  a factual-verdict error. Luna's and Sol's arms are unaffected: none of their N items was rejected on judgment.

### Revised proposal (each item needs owner approval; nothing is run or changed)

1. **Gold correction, an owner decision.**
   - Move r1-02, r1-05, r1-08, r1-23 and r1-30 from N to a new positive stratum, `P_ADJUDICATED`, using Codex's
     pairs.
   - This changes the gold after seeing results, which is why it needs explicit approval and is recorded here.
   - Optional cross-family tie-break before deciding: one fresh blind Opus check of the same 5 items under the signed
     absolute-word rule, about 25k Claude tokens. That covers the same-family caveat above.
2. **Evidence-quote fix, for sign-off.**
   - **Prompt, §3.2 step 2, replacing the current line:**
     > 2. evidenceQuote — if SUPPORTED or CONTRADICTED, copy the shortest phrase from the source note (at most 25
     > words) that decides it, exactly as written in the note, character for character. Do not add quotation marks.
     > Do not paraphrase. If two places in the note decide it, copy both, separated by " ; ". If SILENT, use null.
   - **Server rule 3:** strip enclosing quotation marks. Split on ` ; `, and verify **every** fragment exactly as
     before. A paraphrase still fails, so the rule stays strict.
   - This is a harness change (test scope) and becomes the production rule later.
3. **Fresh Stage 1 re-run, same thresholds as rates.**
   - It uses the revised prompt (a new `promptVersion`) and the corrected gold, if item 1 is approved.
   - With N at 73, the signed percentages become these counts:
     - each checker alone: pass ≤ 3, kill > 7;
     - F union: pass ≤ 5, kill > 8.
   - Converting rates to counts is arithmetic, not a relaxation. It is stated here for sign-off.
   - The scorer hard-codes the N sizes (78 and 88), so it needs a matching test-scope change, with its own tests.
   - **Expected spend about $1.10; requested cap $2.20** (Sol $2.00, Luna $0.20).
4. **If the re-run still fails S-A4b, no further tuning:**
   - The fallback is to require only the independent leg to reject: Sol for Luna- or mini-generated items, Luna for
     Sol-generated items.
   - That is a change to owner decision 1 (Option F) and comes back to the owner, with measured results, first.

**R1 (merged) is unaffected. Item 2 continues and does not wait for any of this.**

## Part 11. Owner approvals (2026-10-20) and the re-run plan (NOT yet run)

**The owner approved all four Part 10 items, with the Opus tie-break first:** "approve all four, with the Opus
tie-break first".

### Opus tie-break

**The rule, stated before the result:** an item is relabeled only if a fresh blind Opus reviewer also judges the
disputed statement false. Otherwise it keeps its original N label, which is the stricter outcome for the checkers.

**Method:** the same blind input Codex saw. A cold Opus agent got statements and notes only, under the signed
absolute-word rule.

| Item | Codex | Opus | Outcome |
|---|---|---|---|
| r1-30 (endorser "only if" dishonored) | F,T | **F,T** (NIL Secs. 65–66 warranties) | **Relabeled to `P_ADJUDICATED`, gold F,T** |
| r1-02 (immobilize "until imaging") | F,F | T,F | Stays N, original label |
| r1-23 (thrombolysis "must be given") | F,F | T,F | Stays N, original label |
| r1-05 (supplementary control "necessary") | F,F | A,F | Stays N: ambiguous is not false |
| r1-08 (drawer "ultimately responsible") | F,T | T,T | Stays N, original label |

**What follows:**
- **The R1 mini finding becomes 4 of 32 wrong statement-pair keys, not 3 of 32.** The 8 of 32 that Part 10
  raised is not confirmed.
- Four absolute-word items remain genuinely disputed across model families, and they still count against the
  checkers if rejected again.

### Corrected gold

The corrected gold is `backend/target/llm-benchmark/verifier/gold-stage1b.jsonl`, with the same notes file.
- SHA-256 `c883705dd0b90450f25084e413dd20bc6e17fb360042d34b918d8d3da5465c71`.
- 157 items: **N 77**, N_CAVEAT 10, P_SEEN 3, P_UNSEEN 20, P_CONSTRUCTED 20, P_CONSTRUCTED_CONTROL 20,
  **P_ADJUDICATED 1**, U 6.

**The N = 77 thresholds** use the floor of the signed rates, the approved method:

| | Pass | Kill |
|---|---|---|
| Each checker alone (5% / 10%) | ≤ 3 | > 7 |
| F union (8% / 12%) | ≤ 6 | > 9 |

- Part 10 quoted counts for N = 73, which assumed five relabels. The same floor rule at 77 gives these.
- `P_ADJUDICATED` is reported separately and is not part of S-A5's signed set of 40.

### Harness changes (test scope, branch `feat/v0.175.0-verifier-rerun`, awaiting "commit")

- **The quote rule:**
  - Enclosing quotation marks are stripped.
  - A quote may join two exact fragments with ` ; `.
  - **Every fragment must appear exactly**, so a paraphrase still fails.
- **The scorer:**
  - Takes the N size from the gold file and applies the thresholds above; 78 keeps the signed counts.
  - Takes the S-A10 natural count from the gold file.
  - Adds the `P_ADJUDICATED` stratum.
- **The prompt:** §3.2 step 2 is replaced with the approved wording, and the other three files are unchanged.
  - New `promptVersion` `5da5423e503a34d1138e4cefc78c2820cceb812be96bf9ca677b4b9c048b7100`.
  - The signed copy is in `backend/target/llm-benchmark/verifier/prompt-signed-v2/`.
- **Tests:** a test for each change. A local dry run on the corrected gold (stub, no network) gave 136 calls per
  model, complete runs, blindness holding, and a scorer report with `nSize` 77. The dry-run file was deleted.

### Re-run commands

- **When:** after the branch merges into `releases/v0.175.0`, since the owner runs from the main checkout.
- **How:** in the owner's own terminal, with `LLM_API_KEY` set there.
- **Cap:** **$2.20** (Sol $2.00, Luna $0.20). Expected spend is about $1.10, based on Stage 1's $1.08.

```sh
cd ~/IdeaProjects/study-snap/backend
./mvnw test -Dtest=StatementPairVerifierBenchmark -Dnativequery.pg.skip=true -Dllm.verifier=true -Dllm.verifier.model=gpt-6.1-sol -Dllm.verifier.gold=target/llm-benchmark/verifier/gold-stage1b.jsonl -Dllm.verifier.notes=target/llm-benchmark/verifier/notes-stage1.json -Dllm.verifier.outDir=target/llm-benchmark/verifier/rerun-sol -Dllm.verifier.promptDir=target/llm-benchmark/verifier/prompt-signed-v2 -Dllm.verifier.repeats=3 -Dllm.verifier.maxCalls=300 -Dllm.verifier.priceInPerM=2.00 -Dllm.verifier.priceOutPerM=10.00 -Dllm.verifier.spendStopUsd=2.00 > target/llm-benchmark/verifier/rerun-sol.log 2>&1; echo exit=$?
./mvnw test -Dtest=StatementPairVerifierBenchmark -Dnativequery.pg.skip=true -Dllm.verifier=true -Dllm.verifier.model=gpt-6-luna -Dllm.verifier.gold=target/llm-benchmark/verifier/gold-stage1b.jsonl -Dllm.verifier.notes=target/llm-benchmark/verifier/notes-stage1.json -Dllm.verifier.outDir=target/llm-benchmark/verifier/rerun-luna -Dllm.verifier.promptDir=target/llm-benchmark/verifier/prompt-signed-v2 -Dllm.verifier.repeats=3 -Dllm.verifier.maxCalls=300 -Dllm.verifier.priceInPerM=0.10 -Dllm.verifier.priceOutPerM=0.50 -Dllm.verifier.spendStopUsd=0.20 > target/llm-benchmark/verifier/rerun-luna.log 2>&1; echo exit=$?
```

**Release Implementor runs the scorer.** It makes no API calls.

**If the re-run fails S-A4b again,** tuning stops and the independent-leg fallback (Part 10, item 4) comes back to
the owner.

## Part 12. Stage 1 re-run result (owner-run, 2026-10-20): PASS, as a smoke test

**Runs:**
- Both runs complete: 136 calls each, 0 failed, 0 invalid.
- `promptVersion` `5da5423e…`, gold `c883705d…`, N = 77, thresholds the floor of the signed rates.
- **Spend: $1.15 on the meter** (Sol $1.076, Luna $0.073), under the $2.20 cap.
- **Verifier evaluation total, both runs: $2.23.**

| Criterion | Result | Class |
|---|---|---|
| S-A1: mini's three errors, F | r1-04 3/3 (its classification is arguable), r1-06 3/3, r1-07 3/3 | PASS |
| S-A2 / S-A3: constructed and unseen positives | 20/20, 20/20 | PASS |
| S-A4a: each checker alone (N = 77) | Sol 3/77, Luna 3/77 | PASS, **at the limit (≤ 3)** |
| S-A4b: F union | 4/77 (5.2%) | PASS (≤ 6) |
| S-A4c: constructed controls | 2/20 | PASS, **at the limit (≤ 2)** |
| P_ADJUDICATED (r1-30) | rejected, 1/1 | REPORT |
| S-A5: joint miss | 0/40 | PASS |
| S-A6: ambiguous rejected | 6/6. SILENT on 73 of 308 judgments; accuracy when SILENT 70/73 | PASS |
| S-A7: failed calls | 0/136 per model | PASS |
| S-A8: unverified quotes | Sol 0/207, Luna 3/197 (down from 9) | PASS |
| S-A9: each leg alone | Sol and Luna each 20/20 detection, within S-A4a | PASS |
| S-A10: underfill projection | one short 0.9%, failed under 0.001% | PASS |
| S-A11: latency p90 | 13.6 s | PASS |
| S-A12: cost p90 per batch | $0.012 | PASS |

**The four F rejections in N:**
- Three are the absolute-word items whose labels the tie-break kept: r1-05 (both checkers), r1-23 (both), and
  r1-08 (Sol, self-contradictory).
- One is a Luna paraphrased quote: S-17.
- r1-02 was not rejected this time.
- The two control rejections are one per checker.

**What this pass means (owner clarification 3):**
- It is **a smoke test, not proof of broader reliability.**
- Two criteria pass exactly at their limit, so a single extra rejection would have made them GRAY.
- The reporting limitations still apply:
  - N is agreement between the generator and one reviewer, from 10 distinct notes;
  - the constructed items came from a single model family;
  - P_UNSEEN is entirely the index class.
- **It licenses proposing activation for Official question-set creation only**, with the production checkpoints in
  §4.5, including the wrong-key trigger.
- **It does not license:**
  - live-start verification, which waits for items 2 and 5;
  - Stage 2 (the +5 rule and replacements), which is separately gated;
  - any production code, which needs an implementation approval.

## Part 13. Stage 2 package (legs P and R): for owner approval, NOT run

**Status:** prepared 2026-10-21 by Release Implementor. **Owner approved the $4.60 total cap on 2026-10-21**,
after seeing this package. **In progress:** Luna legs P and R are done. Sol leg R is next. **Sol leg P is held**
pending the owner's P2 decision (see "Results so far").

### What runs
- **The production code under test:** branch `feat/v0.175.0-statement-pair-verifier` (draft PR #1520), at the
  SHA named in the commands. It is run from the verifier worktree, because the code is not on the release
  branch yet.
- **Leg P** (the +5 rule): the real `generateMoreChallengeQuiz` path. Per model it runs 40 batches with the rule
  and 10 baseline batches without it.
- **Leg R** (replacement): one R1-consistent gold item per case, with both checkers stubbed to reject it, then
  the real production replacement call.
- **Both models,** `gpt-6.1-sol` and `gpt-6-luna`, so 4 commands in all.

### Inputs (local, git-ignored, built without any production read)
- **`stage2-p.jsonl`** (sha256 `832a99face35…`): 40 cases, **40 distinct notes**. They were chosen in md5 order from
  the 76 local notes that have a Study Pack summary and key concepts: v0.174.0 Leg E sources plus `full.jsonl`.
- **`stage2-r.jsonl`** (sha256 `2b07d1dbc0c7…`, rebuilt once; see the run log): 40 stratum-N gold items, in md5 order of the 77. Each comes with
  its own case context, its R1 concept and its gold verdicts.
  - **Limitation: these 40 items span only 10 distinct notes**, the same notes as Stage 1's N.
- **Data handling** is as in Part 6. All of these are public original notes already sent to OpenAI in v0.174.0
  and Stage 1. No private note is used, and nothing is committed.
- The master copies are in the main tree at `backend/target/llm-benchmark/verifier/stage2/`. The commands copy
  them into the worktree, because the harness only reads under its own `target/`.

### Cost: the earlier "about $1.35" estimate was too low
Measured R1 Challenge calls average about 3.5k input and 3.8k output tokens, including reasoning.

| Run | Calls (approx.) | Expected | Spend stop |
|---|---|---|---|
| P, Sol | 50, plus retries | about $2.30 | **$2.80** |
| R, Sol | 40 | about $1.10 | **$1.40** |
| P, Luna | 50, plus retries | about $0.12 | **$0.25** |
| R, Luna | 40 | about $0.06 | **$0.15** |
| **Total** | | **about $3.60** | **$4.60** |

**The owner approved the $4.60 total cap (2026-10-21).** Each stop is under the README's per-command
ceiling of $3.50. The meter stops each command at its own limit, and a stopped run is incomplete, so it is
not a pass. Run Luna first: it is cheap and shows any setup problem before Sol spends.

### Pass and fail criteria (§4.4, verbatim, fixed before any result)
| # | Pass | Fallback or kill |
|---|---|---|
| P1 | ≤ 1 leaked item in 40 batches, per model (Luna and Sol) | ≥ 3 in 40 on either model → fallback (b) |
| P2 | 5 of 5 in ≥ 95% of batches; H4 rejections and invalid-output retries no higher than the 10-batch baseline arm | < 90% → remove the rule |
| P3 | Blind reviewers rate batches with the rule comparable to or harder than baseline batches, on difficulty and concept coverage, in ≥ 80% of pairs | < 65% → remove the rule |
| R1r | 0 assertion-style; ≥ 90% test the rejected item's concept; ≥ 80% rated comparable or harder, blind; ≤ 1 wrong key in about 40, blind-checked | Any wrong key in 20, or < 70% comparable |
| R2r | Question-type mix of replacements: reported | — |
| R3r | Replacement failure q measured, then S-A10 re-run with it | Projected failed starts > 1% |

**The gray-zone rule applies:** any result between the pass and kill columns is not a pass. It returns for owner
review, and no criterion is relaxed after the results are seen.

### After the paid runs (Release Implementor; no paid API)
1. Copy every output directory to the main tree **before any `clean` in the worktree**.
2. Compute P1, P2, R2r and q from `summary.json` and the logs.
3. **Blind reviews for P3 and R1r** run as read-only Codex blind checkers (D11), on the blind exports only, with
   the arm key held back. Wrong keys in R1r are judged against the source note.
4. **R3r:** re-run the local S-A10 scorer with `-Dllm.verifier.replacementFailure=<measured q>`.
5. Report every criterion as PASS, GRAY or KILL. #1520 merges only on all PASS.

### Owner commands (your terminal, with `LLM_API_KEY` set there only)
Every flag is written out in full. **Do not put the shared flags in a shell variable:** zsh does not word-split an
unquoted `$B`, so Maven receives one bogus test name (see the run log).
```sh
cd ~/IdeaProjects/study-snap-verifier/backend
git rev-parse --short HEAD          # must print c554637b
mkdir -p target/llm-benchmark/verifier
cp ~/IdeaProjects/study-snap/backend/target/llm-benchmark/verifier/stage2/stage2-p.jsonl ~/IdeaProjects/study-snap/backend/target/llm-benchmark/verifier/stage2/stage2-r.jsonl target/llm-benchmark/verifier/
# 1. Luna, leg P (stop $0.25); its scored output is p-luna2, see the run log
./mvnw test -Dtest=StatementPairStage2Benchmark -Dnativequery.pg.skip=true -Dllm.stage2=true -Dllm.stage2.leg=P -Dllm.stage2.model=gpt-6-luna -Dllm.stage2.input=target/llm-benchmark/verifier/stage2-p.jsonl -Dllm.stage2.outDir=target/llm-benchmark/verifier/p-luna2 -Dllm.stage2.batches=40 -Dllm.stage2.baselineBatches=10 -Dllm.stage2.maxCalls=200 -Dllm.stage2.priceInPerM=0.10 -Dllm.stage2.priceOutPerM=0.50 -Dllm.stage2.spendStopUsd=0.25 > target/llm-benchmark/verifier/p-luna2.log 2>&1; echo exit=$?
# 2. Luna, leg R (stop $0.15)
./mvnw test -Dtest=StatementPairStage2Benchmark -Dnativequery.pg.skip=true -Dllm.stage2=true -Dllm.stage2.leg=R -Dllm.stage2.model=gpt-6-luna -Dllm.stage2.input=target/llm-benchmark/verifier/stage2-r.jsonl -Dllm.stage2.outDir=target/llm-benchmark/verifier/r-luna -Dllm.stage2.batches=40 -Dllm.stage2.baselineBatches=0 -Dllm.stage2.maxCalls=80 -Dllm.stage2.priceInPerM=0.10 -Dllm.stage2.priceOutPerM=0.50 -Dllm.stage2.spendStopUsd=0.15 > target/llm-benchmark/verifier/r-luna.log 2>&1; echo exit=$?
# 3. Sol, leg P (stop $2.80); HELD until the owner decides P2
./mvnw test -Dtest=StatementPairStage2Benchmark -Dnativequery.pg.skip=true -Dllm.stage2=true -Dllm.stage2.leg=P -Dllm.stage2.model=gpt-6.1-sol -Dllm.stage2.input=target/llm-benchmark/verifier/stage2-p.jsonl -Dllm.stage2.outDir=target/llm-benchmark/verifier/p-sol -Dllm.stage2.batches=40 -Dllm.stage2.baselineBatches=10 -Dllm.stage2.maxCalls=200 -Dllm.stage2.priceInPerM=2.00 -Dllm.stage2.priceOutPerM=10.00 -Dllm.stage2.spendStopUsd=2.80 > target/llm-benchmark/verifier/p-sol.log 2>&1; echo exit=$?
# 4. Sol, leg R (stop $1.40)
./mvnw test -Dtest=StatementPairStage2Benchmark -Dnativequery.pg.skip=true -Dllm.stage2=true -Dllm.stage2.leg=R -Dllm.stage2.model=gpt-6.1-sol -Dllm.stage2.input=target/llm-benchmark/verifier/stage2-r.jsonl -Dllm.stage2.outDir=target/llm-benchmark/verifier/r-sol -Dllm.stage2.batches=40 -Dllm.stage2.baselineBatches=0 -Dllm.stage2.maxCalls=80 -Dllm.stage2.priceInPerM=2.00 -Dllm.stage2.priceOutPerM=10.00 -Dllm.stage2.spendStopUsd=1.40 > target/llm-benchmark/verifier/r-sol.log 2>&1; echo exit=$?
# 5. Save the outputs to the main tree right away (add p-sol if command 3 ran)
cp -R target/llm-benchmark/verifier/p-luna target/llm-benchmark/verifier/p-luna2 target/llm-benchmark/verifier/r-luna target/llm-benchmark/verifier/r-sol target/llm-benchmark/verifier/*.log ~/IdeaProjects/study-snap/backend/target/llm-benchmark/verifier/stage2/
```
Stop after any `exit` other than 0 and tell Release Implementor before running the next command.

### Run log (2026-10-21)
1. **The first attempt at commands 1 and 2 exited 1 with no spend.** The shared flags lived in `B="…"`, and zsh
   does not word-split an unquoted `$B`. Maven saw one test named `StatementPairStage2Benchmark
   -Dnativequery.pg.skip=true -Dllm.stage2=true` and ran nothing. The commands above are written out in full.
2. **The first real Luna leg P run (`p-luna`) was stopped by the owner** after 10 of its first 25 calls hit the
   180-second client timeout, attributed at the time to the owner's connection. It is kept as a record and is
   **not scored**. The re-run is `p-luna2`.
3. **Command 2's first attempt failed preflight with no spend:** "verdict pair required". The harness reads
   `rejected` as an **array**, and the input had one object per row (the harness README says "one `rejected`
   object"). The file was rebuilt with a one-element array. The items and order are unchanged, and the sha
   changed from `6e4966806122…` to `2b07d1dbc0c7…`. `stage2-p.jsonl` is byte-identical, so leg P is unaffected.
   The README wording is to be corrected on #1520.

### Results so far (scored as the signed rules compute them)
**Luna, leg P (`p-luna2`, meter $0.098, 50 of 50 calls, complete):**
| # | Result | Class |
|---|---|---|
| P1 | 0 leaked items in 40 rule batches | **PASS** |
| P2 | 31 of 40 rule batches complete (77.5%). H4 rejections and invalid-output retries 0, the same as the baseline (10 of 10 complete) | **KILL, as computed (< 90%)** |
| P3 | Blind review pending | — |

**The P2 confound, stated with the result rather than used to relax it:**
- All 9 incomplete rule batches are 180-second client timeouts. None is bad output.
- Only cases p01–p10 have a baseline arm. On those, it is 2 rule timeouts against 0 baseline timeouts, which
  suggests the rule but does not prove it.
- Three cases (p04, p12 and p15) timed out in both the stopped run and the re-run. That fits slow notes at least
  as well as it fits the rule.

**Options for the owner:**
- accept the kill: remove the rule and use fallback (b), skip-and-log, so command 3 is not needed;
- or approve a revised proposal under the gray-zone rule: run the baseline arm on the 9 timed-out cases, about
  $0.02 on Luna, before deciding.

**Production note, independent of the verdict:** some Luna +5 calls exceed the 180-second client timeout. That
must be handled before item 6 moves +5 to the tiered route.

**Luna, leg R (`r-luna`, meter $0.036, complete):**
- 40 of 40 replacements returned, so **q = 0%**.
- **R2r:** all 40 MCQ.
- R1r blind review and the R3r S-A10 re-run are pending.

**Sol, leg R (`r-sol`, meter $0.539, complete):**
- 40 of 40 replacements returned, so **q = 0%**.
- **R2r:** all 40 MCQ.

**R3r (local scorer, no API):** S-A10 was re-run on the Stage 1 re-run outputs with `replacementFailure = 0`:
projected one-short 0%, failed 0%. **PASS.**

**R1r, the mechanical part:** a direct scan of all 80 returned replacements found **0** containing either
statement marker. **"0 assertion-style": PASS** on both models.

### R1r blind-review scoring rules (written 2026-10-21, before any blind result exists)
- **Scored per model, n = 40 each.** The 80 items are pooled only so the reviewer is blind to the model. The arm
  key is held outside the reviewer's directory.
- **Same concept:** pass at ≥ 36 of 40 (90%). Below that is GRAY and goes to the owner; the signed table names no
  kill for this measure.
- **Comparable or harder:** pass at ≥ 32 of 40 (80%), kill below 28 of 40 (70%), GRAY in between.
- **Wrong key:** the signed text is "≤ 1 wrong key in about 40" for a pass and "any wrong key in 20" for a kill.
  Read together at n = 40: **0 or 1 WRONG is a PASS; 2 or more is a KILL.** There is no gray band.
- **AMBIGUOUS is not WRONG.** It is reported separately, and each AMBIGUOUS key gets one cross-family Opus look
  (the Stage 1 tie-break pattern). It counts as WRONG only if Opus also judges it wrong.
- **Reviewer:** Codex, read-only, blind (D11 default). **D11 §11 exception B applies,** because Codex checking
  Sol- and Luna-generated content is an unmeasured pairing. So a cold Opus agent blind-checks a sample of 20 of
  Codex's CORRECT keys (10 per model, md5 order). Any key Opus finds wrong there is reported and **counts toward
  that model's WRONG total**, the stricter outcome.


### R1r blind-review results (Codex, model-blind, 80 items; Opus exception-B sample)
| Measure (per model, n = 40) | Luna | Sol | Class |
|---|---|---|---|
| Assertion-style (direct scan; Codex agrees) | 0 | 0 | PASS |
| Wrong key (Codex) | 0 | 0 | PASS |
| Opus exception-B sample of Codex's CORRECT keys (10 per model) | 10/10 correct | 10/10 correct | PASS. The Codex-on-Sol/Luna pairing now has a clean sample, 0 misses at n = 20 |
| Comparable or harder | 36/40 (90%) | 40/40 (100%) | PASS |
| Same concept | 33/40 (82.5%) | 33/40 (82.5%) | **GRAY**, to the owner |

**About the concept GRAY (reported, not re-scored):**
- In all 14 misses, the rejected item's R1 concept label does not appear in the rejected question's own text.
- In every one, the replacement tested the **labelled** concept, which is what the production replacement prompt
  asks it to do.
- The reviewer brief asked about "`sourceConcept` and the rejected question" together, so it could not tell
  "follows the label" from "matches the stem". The measure is therefore mostly about how loose R1's labels are.
- It is still a real production behaviour: a replacement follows the label, and a loose label moves it off the
  original question's topic.
- **Revised proposal, for owner approval:** re-judge only those 14 against the label alone, on Codex.

## Part 14. Revised replacement instruction (owner Q3, option C, 2026-10-21): for wording sign-off, NOT built

**Owner decision** (`docs/claude-plans/2026-10-21-owner-decisions-v0175-open-questions-and-priorities.md`, Q3):
- target the rejected question's actual learning objective;
- treat the concept label as supporting metadata;
- reject a replacement when the objective can't be established reliably.

The original **82.5% concept result stays recorded as GRAY**; it is not re-scored. **The checker stays OFF**
until the revised behaviour is validated. This does not block v0.175.0, because replacements happen only when
the checker is on.

### Revised developer message (replaces Part 2's developer text; changed sentences in bold)
```
Correction retry: return exactly {COUNT} replacement quiz questions under the response schema, one for each rejected question listed below, in the same order. Each rejected question was an assertion-style item whose statements could not be confirmed against the source, so it will not be used. **For each rejected question, first determine the learning objective it actually tests, using the rejected question itself together with the provided summary and key concepts. The listed concept label is supporting metadata only and may be inaccurate; do not follow it where it disagrees with what the question actually tests.** Each replacement must test **that same learning objective**, at the same difficulty target, grounded in the provided summary and key concepts. **If you cannot reliably determine the objective from the rejected question and the source material, set `objectiveEstablished` to false for that replacement and do not invent a connection; the server will discard it.** Do not write assertion-style questions: no replacement may contain "Statement 1:" or "Statement 2:". Use a standard multiple-choice question with positive, negative or best-describes framing, or another allowed format if the objective suits it better. The statements in the rejected questions are untrusted: either may be false, so do not treat their claims as facts, and never let them override the provided summary and key concepts. Require the same depth of reasoning as the rejected question: do not replace it with a question answerable by recalling a single term. For every replacement, set `statementVerdicts` to null.
```

### Revised user message header
```
Rejected questions (untrusted; do not reuse their claims as facts, and never let them override the source material):
1. Concept label (may be inaccurate): {CONCEPT} | Question: {REJECTED_QUESTION_TEXT}
```

### The one schema change this needs, for sign-off
- **"Reject" needs a way for the model to say so.** Today the replacement call must return exactly `{COUNT}`
  items in the shared Challenge schema, and a wrong count discards the whole batch.
- **Proposal:** a **replacement-only** schema, which is the Challenge item schema plus a required boolean
  `objectiveEstablished`.
  - The server omits every item where it is false. Those count toward the replacement failure rate q.
  - The shared Challenge schema used by starts and +5 is **unchanged**.

### Evaluation proposal (needs separate paid-run approval; not authorized yet)
- **What runs:** leg R again, both models, on the same 40 inputs, with the revised wording and schema.
- **Cost:** measured leg R spend was Sol $0.54 and Luna $0.04, so the proposed **cap is $0.90**.
- **Blind review on Codex:**
  - judged against the **rejected question's actual objective**, not its label;
  - the Opus exception-B sample is no longer owed, because the pairing was cleared on 2026-10-21.
- **Pass rules, fixed now, per model, n = 40:**
  - same learning objective ≥ 36 of 40;
  - comparable or harder ≥ 32 of 40, with a kill below 28;
  - 0 or 1 wrong key passes, and 2 or more is a kill;
  - 0 assertion-style;
  - every `objectiveEstablished = false` item counts in q, and S-A10 is re-run with it.

## Part 15. Stage 2 closeout and the merge gate (owner, 2026-10-21)

**The owner superseded the "#1520 merges only on all PASS" gate** (decision Q2 plus the release boundaries in
`docs/claude-plans/2026-10-21-owner-decisions-v0175-open-questions-and-priorities.md`):
- #1520 may merge with **every switch OFF**, if code review and release checks pass;
- **no checker activation is authorized**;
- the +5 exclusion rule must be put behind **its own switch, OFF by default**. Today it applies unconditionally
  on the +5 path, and that is a must-fix on #1520 before it merges.

**Stage 2 as it stands:**
- **P1** (no statement pairs leaking through +5): PASS on Luna. Not run on Sol.
- **P2** (+5 completeness): KILL as computed on Luna, 31 of 40 complete, with the timeout confound recorded. The
  rule stays OFF regardless (Q2).
- **The paired diagnostic** is recorded as context when it finishes. It no longer changes the code decision.
- **P3** (+5 quality): not run, because the rule is OFF.
- **Sol leg P:** not run and not needed.
- **R1r:** assertion-style 0, wrong keys 0 (with the Opus sample 20/20), comparable-or-harder PASS, concept GRAY
  at 82.5%. That stays recorded and is not re-scored (Q3); Part 14 carries the revision proposal.
- **R2r:** all MCQ. **R3r:** PASS at q = 0.
- **Spend:** about $0.75 of the approved $4.60.

**The paired diagnostic** (`p-luna-diag`, owner-run, 18 of 18 calls, not stopped, meter $0.037). The 9 Luna cases
whose rule batch timed out were each re-run with the rule and without it:
- **with the rule:** 6 of 9 complete, 3 timeouts (p04, p24, p36);
- **without the rule:** 9 of 9 complete, 0 timeouts, at 24–58 s.

**Scored by the rule fixed before the result:**
- "the rule causes it" needed 5 or more rule timeouts, and there were 3;
- "the notes cause it" needed the baseline within 2 of the rule, and it was 0 against 3.

**So the result is INCONCLUSIVE.** The direction points at the rule: on the same notes, the rule timed out 3
times and the baseline never did. Across all runs, rule batches timed out 12 times in 49, and baseline batches 0
in 19. Under owner Q2's inconclusive branch, **the rule stays OFF behind its switch and the uncertainty is
recorded.** Re-enabling it needs validation on the model actually deployed. No further paid test is authorized.
Stage 2 spend is now about **$0.79** of the $4.60.
