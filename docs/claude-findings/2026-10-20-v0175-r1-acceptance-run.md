# v0.175.0 R1 acceptance run: statement-pair guard on `gpt-4.1-mini`

**Result: the pre-stated pass rule FAILS.** Blind checkers judged 3 of 32 delivered statement-pair keys wrong: 2
clearly, 1 arguably.
These are not the error class R1 targets. In all three, the model's own verdicts, its explanation and its
key agree with each other, and the verdict itself is factually wrong. The wording doc (§2) and owner
requirement B anticipated exactly this.

Written 2026-10-20 by Release Implementor. The paid calls were run by the owner (cap $1.00). Raw items, the
blind key and the scores are local and not committed: `backend/target/llm-benchmark/r1-mini*/` and the
session scratchpad.

## Run

- **What ran:**
  - **Full run:** Leg E single-note cells S15, S12 and S10, 10 packs each, 30 starts.
  - **Earlier small run:** 3 starts (one per cell) from a mis-specified first command (`runs=10` selected
    only run 10). 37 of 37 questions were delivered, with 4 statement-pair items and no rejections. Those 4
    items are included in the blind check.
- **Model and path:** `gpt-4.1-mini`, through the real `generateChallengeQuiz` path with the R1 schema and
  check.
- **Cost:** about $0.18 in total ($0.169 + $0.013).

**Full-run generation counts** (`challenge_quiz_generation_counts`: 33 call lines, for 30 starts plus 3 failed
first attempts):

| | Value |
|---|---|
| Requested | 370 questions across the 30 starts (the 3 failed attempts add another 45) |
| Statement-pair rejections | **0** |
| H4 (numeric) rejections | 8, each retried once; 1 omitted after its replacement failed |
| Whole-call invalid-output retries | 3: invalid answer mapping, invalid choices, invalid multi-select; all succeeded on attempt 2 |
| Delivered | 369 of 370. One S12 start delivered 11 of 12, within the owner's minimum |
| Retry time | 2.0–3.8 s per correction call; median call 15.1 s, max 30.6 s |

The model filled `statementVerdicts` on every statement-pair item, and every item used the four standard
choices in standard order.

## Blind check (owner requirement B)

- **Method:**
  - Two cold Opus reviewers each checked 16 of the 32 statement-pair items (shuffled, seed 1513).
  - Each saw the item's source note, the question and the choices. Neither saw the key, the model's verdicts
    or its explanation.
  - Each judged both statements independently against the source, then picked the choice.
- **Agreement:** 29 of 32 matched the key. One of the 29 (an open-traverse accuracy item) was marked
  ambiguous on Statement 1, with the reviewer leaning to the key's answer.
- **Disagreements:** 3. In each the model keyed "Both statements are correct" with an explanation saying so,
  and one statement is false.
  - The release implementor re-read all three after seeing the reviewers' reasoning, so that re-read is not
    independent.
  - r1-04 is arguable: its reviewer noted that the author may have meant deflection angles. The other two
    are clear.

  | Item | Topic | Why one statement is false |
  |---|---|---|
  | r1-04 | Surveying, open traverse | Says bearings are measured relative to the prior line. Bearings are referenced to the meridian; angles from the prior line are deflection angles |
  | r1-06 | Architecture, Corinthian order | Says columns "always" have fluted shafts. Unfluted Corinthian shafts exist |
  | r1-07 | Steel connections | Says pretensioning "eliminates" slip and "ensures equal" load sharing. Both absolutes are false |

- **The pattern:** two of the three turn on an absolute word the model accepted as true.

## What it means

- **The delivered error rate has not been shown to drop.** 3 of 32 now, against 2 of 17 in C7 before R1, cannot
  be told apart at this size (requirement E).
- **What the data does suggest:** no delivered item had an explanation contradicting its key, which was the
  dominant pre-R1 error. At mini's earlier rate about 4 of 32 would be expected, and seeing none by chance is
  roughly a 2% event. That is suggestive, not proof. 17 of 32 keys are "Only Statement 1", the answer that was
  previously mis-keyed about a third of the time.
- **The server check fired zero times.** Any improvement came from the field order and the prompt, not from
  rejections. The check only catches a key that contradicts the model's own verdicts. With the verdicts
  written just before the answer, that rarely happens. **Nothing in production catches the factual-verdict
  errors, and they are what remains.**
- **The remaining errors are factual verdicts:** about 9% of statement-pair items here (3 of 32), or about 0.8%
  of all delivered questions. Learners would still be marked wrong for a right answer on these.
- **For comparison:** C7 found 2 of 17 wrong statement-pair keys on mini before R1 (the index class). Sol had 0
  of 42 in C7.

## Options for the owner (not decided)

1. **Accept R1 as merged** and record factual-verdict errors as a known limitation. Item 6 then moves Plus/Pro
   Challenge to Sol (0 of 42 in C7) **and FREE Challenge to Luna, which is untested with R1.** Luna had about
   19% wrong statement keys before R1, and R1 was approved as the gate for that switch.
   - **Proposed next check, whatever the choice below:** a Luna arm of this same run. Same three cells, fresh
     output folder, independent blind check, about $0.10. About $0.82 of the $1.00 cap remains. It needs the
     owner's go-ahead, because it is a different model from the approved mini run.
2. **Add a prompt rule:** when judging a statement, a standard exception to an absolute word ("always",
   "never", "only", "eliminates", "ensures") makes the statement false. This is a prompt change, so it needs
   owner sign-off on the exact text. A comparable paid re-run (about $0.18) would check it, with the same
   smoke-test framing.
3. **Add a verifier call** for statement-pair items. This adds latency and cost to a blocking start, against the
   owner's performance rule; it fits item 2's background preparation better.

**Nothing has reached learners.** R1 is merged into `releases/v0.175.0` only, which cannot reach `main` until
after v0.174.0's #1512.
