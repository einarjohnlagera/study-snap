# C7 — wrong answer keys on "which statements are true" items: mini vs Luna vs Sol

**Status: COMPLETE.** Owner-approved C7 (consolidated plan, Part G). **No OpenAI calls.** Items, key and
checks are local and git-ignored, because they contain note text (`backend/target/llm-benchmark/c7/`; the
key is kept outside it). Written 2026-10-19 by Release Implementor.

## Why

B7 Phase 1 found Luna wrong-keying 6 of 26 statement-pair items in Challenge starts
(`docs/claude-findings/2026-10-19-v0175-b7-low-effort-quality-review-phase1.md`). The open question before
the v0.174.0 switch was whether this is Luna-specific, and whether it affects what the switch actually moves
to Luna: Study Pack quizzes.

## Method

- **Items:** every statement-pair item from the first benchmark (`full-*`): mini 51, Luna 75, Sol 86.
  All three models answered the **same 81 inputs**, so the comparison is paired.
- **Blinding:** labels removed, items shuffled, and grouped by source note across three parts. A whole-word
  check confirmed no model id appears in the text.
- **Checking:** three fresh Opus agents judged each statement, derived the correct choice, and compared it
  with the key. A fourth fresh agent independently re-checked all 9 flagged items. It confirmed **7 wrong
  keys, all harmful**, and 1 that is ambiguous by jurisdiction (an impairment reversal: wrong under
  IFRS/PFRS, right under US GAAP and the source note).

## Results (un-blinded)

| | Study Pack quizzes (legs A and D) | Quiz-mode generation (leg B: Challenge, Board, Adaptive, Long Exam) | Total |
|---|---|---|---|
| `gpt-4.1-mini` (**production today**) | 0 / 34 | **2 / 17** (12%), plus 1 IFRS-ambiguous | 2 / 51 |
| `gpt-6-luna` | **0 / 43** | **5 / 32** (16%) | 5 / 75 |
| `gpt-6.1-sol` | 0 / 44 | **0 / 42** | 0 / 86 |

**Combined with B7 Phase 1** (Luna Challenge starts, 6 of 26), **Luna's quiz-mode statement-pair wrong-key
rate is 11 of 58 (about 19%)**. In every confirmed case the item's own explanation is right and only the
keyed index is wrong.

## What this means

1. **The v0.174.0 switch (FREE Study Packs → Luna) is not affected.** Study Pack quizzes had **0 wrong keys
   on every model** (0/43 on Luna).
2. **The defect lives in quiz-mode generation, and today's production model already has it:** mini 2/17.
   In v0.174.0 every quiz mode stays pinned to mini, so v0.174.0 neither introduces nor worsens it.
3. **Routing FREE Challenge to Luna in v0.175.0 (item 17) would worsen it:** about 19% against about 12%,
   on small samples. **Sol shows none** (0/42 in quiz mode).
4. **The existing consistency check** (`isAnswerExplanationInternallyInconsistent`, H4) does not catch this
   pattern.

## Recommendations

- **R1. Before v0.175.0 routes FREE Challenge to Luna, add a statement-pair key guard.** Detect when a
  statement-pair item's keyed option contradicts its own explanation's per-statement verdicts, then retry.
  It could be generation-path validation alone, or validation plus a schema field carrying per-statement
  verdicts. **A schema or prompt change would need the owner's sign-off** under the "no prompt-content
  change" anti-drift.
- **R2. Production already serves this defect in quiz modes, on mini.** Saved Challenge bank and template
  questions may carry wrong statement-pair keys. A read-only scan is a follow-up item: find statement-pair
  items in `challenge_quiz_question_bank` and in Study Pack quizzes, then blind-check a sample. Any repair
  goes to the owner and is never automatic.
- **R3. Source-note errors found incidentally,** for the owner and the Note Strategist:
  - a source note states Graves disease causes **hypothyroidism**; it causes hyperthyroidism;
  - a CPA-track note states impairment losses are **irreversible**, which is wrong under IFRS/PFRS for
    assets other than goodwill.

  Both come from the benchmark's sample of public original notes (mostly Official authors). Located in the
  local input (benchmark ids are hashed, so a read-only `SELECT` on the passage resolves the production note
  id at handoff):
  - Anatomy note opening "HYPERTHYROIDISM" (input `A-5dd2234d`): its "GRAVE'S DISEASE" section lists
    "(1) Hypothyroidism";
  - Accountancy note "Depreciation Methods and Asset Impairment" (input `B-bd71a90f`): "Impairment losses are
    irreversible".

## Owner decisions (2026-10-19): all approved as recommended

The owner replied "approve all recommended".
- **R1: approved into v0.175.0 scope.** The statement-pair key guard ships before, or with, FREE Challenge
  routing to Luna (item 17). Validation alone is the default design. **If the brief needs a prompt or schema
  change, the exact change still goes to the owner for sign-off first:** approving R1 does not pre-approve
  prompt content.
- **R2: approved.** A read-only scan (`SELECT` only) of statement-pair items in the Challenge bank, templates
  and Study Pack quizzes, plus a blind check of a sample, at no OpenAI cost. Findings go to the owner; any
  repair is the owner's write.
- **R3: approved.** The Note Strategist drafts the two corrections; the owner applies them, because editing a
  public Official note is a production content change. The Release Implementor resolves the two note ids by
  read-only `SELECT` for the handoff.

## Limits

Small n: 17 to 58 items per model in quiz mode, so the rates are directional, not precise. One checker per
item plus an independent re-check of the flags.
