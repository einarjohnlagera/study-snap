# v0.175.0 R1 — statement-pair answer-key guard: exact wording for owner sign-off

**Status: SIGNED OFF by the owner 2026-10-20.** Nothing is built. This file is the source for the R1 Codex
brief (`docs/skills/codex-prompt-generator.md`).

## Owner decisions (2026-10-20)

1. **Approved:** the Challenge-only prompt paragraph, the `statementVerdicts` field, the correction retry
   message and the server-side validation in §§3–4.
2. **Approved: field order (b),** `question` → `choices` → `statementVerdicts` → `answer` → `explanation`.
   Every other quiz mode keeps its existing order.
3. **Approved in principle: the paid acceptance run (§6), hard cap $1.00.** It runs only after the
   implementation passes local automated tests, and only after the owner confirms at that point. No paid
   calls before then.

Additional requirements:

- **A. No new or worse silent underfill.** Report requested, accepted, rejected, retried, omitted and
  delivered counts. Verify the existing minimum-question and recovery behaviour. If the current code allows
  fewer questions than requested, record it as a known issue and propose a safe correction, rather than
  widening R1.
- **B. The acceptance review independently checks both statement verdicts and the final answer against the
  source material.** Matching the model's own verdicts shows consistency, not factual correctness.
- **C. Strict Challenge-only scope.** Board Exam, Long Exam, Adaptive, teacher quizzes, Study Pack
  generation and stored questions stay unaffected.
- **D. The retry path keeps the new field, causes no duplicate quota charge, and handles a failed
  replacement safely.**
- **E. Zero wrong delivered statement-pair keys in the small acceptance sample is a smoke-test pass, not
  statistical proof** that the problem is gone.

**Underfill decision (owner, 2026-10-20): option (a), narrow.**
- **Rule:** a learner must never lose a quiz attempt or quota because the system failed to prepare a usable
  quiz.
- **Single-note start:** the owner's wording was "4 of 5". That rested on the release implementor's wrong
  statement that starts request 5; they request 10, 12 or 15 (`ChallengeQuizService:161-163`). **The owner chose
  "at most one short" (2026-10-20): 9/10, 11/12, 14/15.** The 80% reading was declined.
  - **Production read (read-only, 2026-10-20):** since 2026-08-01, 53 single-note Challenge starts that
    opened all had a full count (12 or 15; the 17 and 20 counts are +5 growth). None opened short, so
    neither floor would have failed any of them before R1. 3 starts since July are FAILED with 0 questions.
- **Frontend:** a FAILED start already shows "We couldn't generate the Challenge Quiz this time. Try again."
  and returns to the pre-start screen (`challenge-quiz/page.tsx:714-730`).
- **Multi-note start:** zero fails.
- **When either check fails:** the start goes through the existing failure path, with no quota charge and
  claims released, and tests prove it.
- **No new generation call to fill a shortfall.** The existing one-retry guard is kept, and its latency is
  reported.
- **Multi-note underfill (1–17 of 18) is a high-priority issue for v0.175.0 item 2 and item 5,** which own the
  minimum and the shortfall recovery for both kinds of start.
- **Stop rule:** if the transaction structure cannot guarantee no charge on a failed start, the work stops and
  is reported.

Implementation, deployment, production data changes and paid API calls each need separate approval.

Written 2026-10-20 by Release Implementor for `RELEASES.md` v0.175.0 item 1.
- Evidence: `docs/claude-findings/2026-10-19-v0175-c7-statement-pair-answer-key-comparison.md` and
  `docs/claude-findings/2026-10-19-v0175-r2-statement-pair-answer-key-scan.md`.
- Scope: the Challenge generation path only (`generateChallengeQuizWithSchema`,
  `OpenAiLlmStudyPackService.java:2305`), which covers Challenge start (single and multi-note), +5 and the
  Official template seed, on every model.

## 1. What the data says (new, from R2 data already on disk; no new queries, no OpenAI calls)

**The Challenge schema makes the model choose the answer letter before it writes the explanation or the
choices.**
- Strict structured output emits keys in schema order.
- The Challenge schema (`buildGeneratedQuizSchema`, `OpenAiLlmStudyPackService.java:1102`) declares `question`,
  `answer`, `explanation`, … with `choices` **last**. A saved mini Challenge response confirms that order.
- The Study Pack schema (`prompts/study-pack-v1/schema.json`) declares `question`, `choices`, `answer`,
  `explanation`. Study Pack quizzes had 0 wrong statement-pair keys on every model (C7).
- Sol is a reasoning model that thinks before writing. It had 0 of 42 wrong.

**The errors cluster on one true answer.** Across the 302 key fixes in R2 handoffs 1 and 3 (handoff 2's 13
re-checked held rows are left out; all three handoffs total 315):
- 275 have the true answer "Only Statement 1 is correct". The key was "Both" (156) or "Only Statement 2"
  (117).
- In the Codex-checked set (1,119 items, minus the 43 deleted, so 1,076), the wrong-key rate by true answer is:

  | True answer | Items | Wrong keys | Rate |
  |---|---|---|---|
  | Only Statement 1 | 397 | 126 | 32% |
  | Neither | 18 | 3 | 17% |
  | Only Statement 2 | 535 | 10 | 2% |
  | Both | 126 | 3 | 2% |

- **A shuffled-choices explanation is ruled out:** 136 of the 142 wrong keys in that set had the choices in
  the standard order (Both / Only 1 / Only 2 / Neither).
- **This points to the key being committed before the model has judged the statements.** That fits
  "explanation right, key wrong" in most errors. It is a strong pattern, not a proof. The acceptance run in §6
  is what tests it.

## 2. Decision for the owner: emit order

| | (a) Verdicts before answer | (b) Recommended: (a), plus `choices` before `answer` |
|---|---|---|
| Challenge schema order | `question`, `statementVerdicts`, `answer`, `explanation`, … `choices` last | `question`, `choices`, `statementVerdicts`, `answer`, `explanation`, … |
| What changes for non-statement items | Nothing | They also see their choices before choosing a letter, the order Study Pack already uses |
| Risk | Lowest change | Touches emit order for every Challenge item. Study Pack's clean record is evidence it is safe, not proof for Challenge |

- **A verdict field placed after `answer` would be useless**: it would be a label written after the choice
  was made.
- Either option keeps the server-side check (§4). The order is what should make it fire rarely.
- **What the check guarantees, and what it does not:** it guarantees that the key matches the model's own
  verdicts. A wrong verdict with a matching key still passes, and so does an explanation that contradicts both
  (the `2a48c6af` case in R2).

## 3. Exact changes

### 3.1 Prompt: `prompts/study-pack-v1/challenge-quiz-developer.txt` only

Append to the end of the existing assertion-style bullet (line 46), after "never TRUE_FALSE and never
["True", "False"].":

> For every assertion-style item, fill `statementVerdicts` before you choose the answer: judge Statement 1 on
> its own and Statement 2 on its own, each correct (`true`) or incorrect (`false`). Use exactly these four
> choice texts. The answer must be the choice that matches both verdicts: both true → "Both statements are
> correct"; only Statement 1 true → "Only Statement 1 is correct"; only Statement 2 true → "Only Statement 2
> is correct"; both false → "Neither statement is correct". Begin the explanation by saying whether each
> statement is correct, and why. For every other item, set `statementVerdicts` to null.

- **Not** in the hardcoded rule at `OpenAiLlmStudyPackService.java:172-176`, which other modes share.
- "Begin the explanation by saying whether each statement is correct" is learner-facing. It gives the learner
  the per-statement reasoning, which about half of today's explanations omit. Customer copy still names no
  model.

### 3.2 Schema: the Challenge schema only

New property on each item, in `required` (strict mode) and nullable, like `correctIndices`:

```json
"statementVerdicts": {
  "anyOf": [
    {
      "type": "object",
      "additionalProperties": false,
      "required": ["statement1Correct", "statement2Correct"],
      "properties": {
        "statement1Correct": { "type": "boolean" },
        "statement2Correct": { "type": "boolean" }
      }
    },
    { "type": "null" }
  ]
}
```

- **Emitted only for the Challenge schema**, through a new builder parameter derived solely from
  `CHALLENGE_QUIZ_SCHEMA_NAME`. It must not reuse the Identification predicate, which Long Exam shares: the
  comment at `OpenAiLlmStudyPackService.java:2363-2368` records the bug that sharing caused.
- **Under option (b)**, the Challenge schema builds its properties in the order `question`, `choices`,
  `statementVerdicts`, `answer`, `explanation`, then the rest unchanged. Other schemas keep today's order.
- The new flag is carried in `ConsistencyRetryContext` (`:3127`), so the one-question retry schema keeps the
  field. Without it, the retry would silently drop the field.
- The field is added to `PromptGeneratedQuizItem` (`:3094`). **It is not stored**: `QuizItem`, the bank and
  session JSON are unchanged.

### 3.3 Retry message (new; statement-pair rejections only)

> Correction retry: return exactly one replacement quiz question under the response schema. The prior
> question was rejected because its keyed answer did not match its own statement verdicts. Generate a
> different internally consistent question from the same source material. If it is assertion-style, judge
> each statement on its own, fill statementVerdicts, and key the choice that matches both verdicts.

The existing numeric-mismatch message stays as it is for its own case.

## 4. Validator rule (plain words)

Runs beside the existing H4 check in `buildQuizItemOrRetry` (`:2447`), **only when the new Challenge-only flag
is set** (carried in `ConsistencyRetryContext`). That method serves every mode, and Board and Long Exam
statement-pair items never get the field: without the gate they would all be rejected and omitted.

1. **Statement-pair detection is from the question text, not the field.** An item counts as statement-pair
   when its question contains both "Statement 1:" and "Statement 2:" (case-insensitive; also "Statement
   I:"/"Statement II:").
2. **Each choice is classified case-insensitively, after trimming, with these patterns:**
   - both true: `^([a-d][:.)]\s*)?both\b`
   - only 1: `\bonly statement\s*(1|i)\b`
   - only 2: `\bonly statement\s*(2|ii)\b`
   - neither: `^([a-d][:.)]\s*)?neither\b`
   - **A choice containing `\b(incorrect|false|wrong|not)\b` is unclassified** ("Only Statement 1 is
     incorrect" would otherwise read as its opposite).
   - Tested on all 1,119 R2 items: every one mapped one-to-one, none had a negated choice, none matched two
     patterns, and every mapped item carried both "Statement 1:" and "Statement 2:" in its stem, so the stem
     rule misses none of them.
3. **It is rejected when any of these holds:**
   - `statementVerdicts` is null;
   - the four choices do not map one-to-one to the four patterns;
   - the keyed choice is not the one matching the two verdicts.
4. **On rejection:** one retry with the §3.3 message, then omission if the replacement fails again. This is
   the same path H4 uses.
   - Log `action=quiz_statement_pair_consistency outcome=retrying|omitted reason=missing_verdicts|unclassified_choices|key_mismatch`.
   - No question text in the log, as today.
5. **Items that are not statement-pair:** verdicts are ignored, whatever their value.

**What omission does, read from code:**
- **Start** accepts fewer questions than asked (`ChallengeQuizService.java:479-483`; only the upper bound
  throws).
- **+5** needs at least 3 new questions (`MIN_NEW_QUESTIONS_AFTER_DEDUP`, `:185`, `:1194`).
- **The template seed** stores whatever comes back, up to 20 (`OfficialChallengeQuizTemplateService:301-320`).

Rough size: statement-pair is about 15% of items by prompt, and today's mis-key rate on them is about 12% (mini,
C7) to 27% (the stored templates, R2). So about 2–4% of items would take a retry, and about 1% or less would
end omitted, before any effect of the emit-order change.

## 5. Tests the brief must require

- **Through the real generation path:**
  - a wrong-keyed statement-pair response triggers the retry, then omission;
  - a null-verdict statement-pair item is rejected;
  - a correctly keyed item passes;
  - a non-statement item with verdicts set passes;
  - **a Board Exam or Long Exam statement-pair item with no verdicts passes unchanged** (the gate in §4).
- **Choice order:** a correctly keyed item with its choices in a non-standard order passes.
- **Schema scoping:**
  - the Challenge schema contains `statementVerdicts` (and, under (b), the new order);
  - the Board Exam, Long Exam, Adaptive and teacher schemas do not, and keep today's order;
  - the retry schema keeps the field.

## 6. Acceptance run (optional, needs owner approval of spend)

- **Run:** re-run the C7 mini Challenge arm on the same packs with the new schema, then a blind key check,
  the same method as C7.
- **Pass rule, stated before the run:** 0 wrong statement-pair keys in the blind check of the delivered items.
  The logged `key_mismatch` count is reported beside it.
- **What the run cannot show:** at this size (C7 had 17 mini statement-pair items) it cannot measure whether
  the emit-order change lowers the raw rate. Measuring that needs roughly ten times the items; that is a
  separate decision.
- **Cost:** about the size of B7 Phase 1 (about $0.40; hard stop $1.00), estimated exactly in the brief.

## 7. Not in this change

- Board Exam, Long Exam, Adaptive, teacher quiz and Study Pack generation. Extending the guard to them is a
  separate owner question.
- Stored questions (repaired in R2).
- `QuizItem` and its storage.
- Any other prompt rule.
