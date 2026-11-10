# Giveaway-only Challenge prompt: wording draft for the owner (v0.177.0 item 2)

Status: **DECIDED 2026-10-28: Variant G+ approved in principle (record: `2026-10-28-v0177-item2-gplus-decision.md`). Variant G is not used.** The text below is kept as the record of what was weighed; the resource file is built and hash-pinned by the Codex item, and frozen in the pre-registration before any evaluation. Written 2026-10-28 by the Release Implementor. The production file `challenge-quiz-developer.txt` is **not touched**; the variant would live only as a new test resource used by the evaluation arms B2 and B3. It authorizes no paid call and no production prompt change. The owner chooses a variant and approves the exact text before any Codex brief is written.

## 1. What is being changed, relative to the production prompt

`v0.176.0` tested three edits together (its Changes A, B, C), so the result could not say which one made questions easier. This release removes the likeliest cause:

| `v0.176.0` change | Where | `v0.177.0` |
|---|---|---|
| **A** cognitive-demand paragraph replacing `- Difficulty target: {DIFFICULTY}.` | line 6 | **Dropped.** The `{DIFFICULTY}` line stays as in production. |
| **B** five choice-quality bullets after `- Exactly 1 choice must be correct.` | after line 21 | **Variant G keeps the form-only part; Variant G+ keeps all five.** |
| **C** `- Explanation is required for every question and must be accurate and grounded in the notes.` | line 47 | **Dropped** (it is not a giveaway lever). |

Every sentence below is taken **verbatim from the wording the owner approved on 2026-10-27** (or is an unaltered whole sentence of it). Where a variant uses only part of an approved bullet, the cut is marked, and that cut is the only new wording to approve.

## 2. Variant G (giveaway-only, as scoped): form-only bullets

Insert after `- Exactly 1 choice must be correct.` and before the `MULTI_SELECT` line, nothing else changes:

```
- Do not use "all of the above" or "none of the above".
- For standard MCQ and MULTI_SELECT questions, keep the choices parallel in grammar, specificity and format, so that no choice stands out by length, detail or qualification, whether correct or incorrect. Avoid absolute words such as "always" or "never" in incorrect choices unless the concept genuinely requires them. This does not apply to assertion-style items, which use the four fixed statement choices, or to MATCHING blocks, which share one option set.
- No two choices may mean the same thing.
```

- Line 1 is the second sentence of approved bullet 3 (the first sentence, "Do not use absurd, unrelated, joke, or obviously false choices", is a plausibility rule and is left out). **Cut to approve.**
- Line 2 is approved bullet 4 **verbatim**.
- Line 3 is the first clause of approved bullet 5 (the clause about two questions testing the same fact is left out: it concerns repeated questions, not choices). **Cut to approve.**
- These three map to the structural giveaway types the signed rubric's G4 names: length, absolute words, grammar, odd-one-out, duplicates, "all/none of the above".

## 3. Variant G+ (all five approved choice bullets, still no A and no C)

Insert after `- Exactly 1 choice must be correct.`, the five approved Change B bullets **verbatim and in order** (bullet 1 realistic-misconception, bullet 2 clearly-wrong-but-defensible, bullet 3 no absurd/joke/obvious choices and no all/none, bullet 4 parallel and absolute words, bullet 5 no duplicate choices or questions). No new wording to approve at all: the file equals production plus the exact approved insert.

## 4. How to choose, stated plainly

- **Variant G isolates the giveaway fix** as the evaluation report proposed ("test only the giveaway fix without Change A"), so a result can be attributed. **Its limit:** it is form-only. G2 (distractor plausibility) is a semantic judgement and needs a mean of at least 4.0 **and** at least +0.3 over the same model's current arm; Luna's current mean is 3.23, so Luna needs about +0.77. A form-only change is unlikely to move a semantic score that far, so under G the likely Luna result is a G4 improvement with G2 still failing, which the owner should expect before paying for the run.
- **Variant G+ keeps the plausibility instructions (bullets 1 and 2)**, the only part aimed at G2, at the price of attribution: it can tell us whether Change B without A and C works, but not whether the form rules or the plausibility rules did it.
- **What `v0.176.0` can and cannot say about either:** Sol's result came from A, B and C together, and Luna's proposed arm was deliberately left uninterpreted (Amendment 4b), so neither variant has a measured prior on Luna.
- **My recommendation: Variant G+**, because the stated goal of this release is Luna's distractor quality, and Variant G cannot plausibly reach G2 on its own. If attribution matters more than reaching the gate, choose G. This is the owner's call; I have not looked at Luna's proposed-arm results and will not.

## 5. What happens after the owner decides

1. The owner picks G or G+ and approves the text (including the two cuts if G).
2. One Codex brief (test scope, Tier N): add the new resource file `backend/src/test/resources/benchmark/challenge-quality/challenge-quiz-developer.giveaway-only.txt` (the pinned `challenge-quiz-developer.proposed.txt` is **not edited**), and a guard test that fails unless the file equals production plus exactly the approved insert and that production still hashes to `5bf4a8e9…` (the same drift-guard pattern as `ChallengeQualityPromptGuardTest`). The harness already takes `-Dllm.benchmark.challengePromptFile`.
3. The resource file's git blob hash goes into the pre-registration before the freeze.
