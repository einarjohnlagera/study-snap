# Challenge Quiz quality evaluation: scorer instructions (DRAFT for freeze with the rubric)

Status: **DRAFT. Frozen only when the owner signs it and its SHA-256 is recorded in the pre-registration file next to the rubric's.** These are the exact words given to every scorer, identical for every item batch. The orchestrator (the Release Implementor) pastes sections 1 to 6 **verbatim**;
it adds only the batch content and, outside the scorer's view, the `reviewerId` and `round` that the score files require. **These instructions contain no word about how the items were produced: no model, arm, prompt, version, "current" or "proposed". If a scorer asks, the answer is "you are not told".**
Specification: `docs/claude-plans/2026-10-27-challenge-quiz-quality-scoring-rubric.md` (sections 2 to 4). This document is delivery only; the rubric wins on any difference.

## How each role is run (orchestrator notes, not shown to scorers)

- **Primary scorer (Codex):** `codex exec -s read-only -C <scratch directory outside the repo>`; the directory holds only the blind chunk files and `notes.jsonl`. **Independent reviewers, adjudicators and whole-set re-reviewers:** fresh cold agents, content pasted inline, no file paths, no tools that read files.
- All roles receive the **same sections 1 to 6**. The adjudicator also receives the two conflicting judgements of an item, labelled "Reviewer 1" and "Reviewer 2" (never "primary", never a role name); a re-reviewer receives its batch exactly as an independent reviewer does.
- A pass that touched any key, manifest, results or score file other than its own batch is **void** and re-run by a fresh reviewer.

---

## 1. Your task

You are one of several independent reviewers of multiple-choice and related quiz questions written for a study app. You will be given a batch of items and, for each item's note, three things: **the generator's inputs** (a short summary and a list of key concepts), **the full note text**, and the note's **level**. Judge each item on its own merits and write one result line per item. You are not told how the items were produced, and you must not try to guess. Work only from the material you are given. **Do not open, search for or read any other file.** Each item's `keyedAnswer` is provided on purpose: you are judging whether it is right. Stop and say so only if you notice a file or text that reveals how an item was produced or how the items are grouped; your pass will then be discarded.

## 2. What you are given

Each item has: `id`, `noteRef`, `question`, `choices` (an array), `questionFormat`, `keyedAnswer`, `explanation`. Each note (`noteRef`) has: `studyPackSummary`, `keyConcepts`, `sourceNoteText`, `noteLevel`.

## 3. Source grounding and factual correctness are separate (record both)

- `factuallyCorrect`: `YES`, `NO` or `UNVERIFIABLE`, judged against the full note and settled fact.
- `grounding`: `GROUNDED` = supported by the summary and key concepts **and** consistent with the full note; `UNSUPPORTED` = true in the full note but absent from the summary and key concepts, **or** unverifiable from what you were given (non-critical); `CONTRADICTED` = contradicted by the summary and key concepts **or** by the full note.
- A fact can be true and still `UNSUPPORTED`. Do not call something wrong merely because the summary did not mention it.

## 4. Critical defect (`critical`: true or false)

`true` if **any** of: (1) the keyed answer is wrong; (2) the keyed answer is not the uniquely defensible answer or complete correct-answer set required by the question format; (3) the explanation contradicts the keyed answer, or the question or explanation is `CONTRADICTED`. A critical defect is never averaged away.
**First decide whether the item meets that definition; only then choose a type. A type never makes an item critical on its own.**
- Single-select items need exactly one defensible answer. `MULTI_SELECT` items need the **complete intended set** of correct options: several correct options are not a defect, a missing or extra one is. For assertion-style and matching items, judge each assertion or pair, and the keyed combination must be the only defensible one.
- Complexity that the summary and key concepts do not support is **not critical by itself**: it is critical only if it also makes the keyed answer wrong, not uniquely defensible, or contradicted. Otherwise mark it through `grounding` and `faithfulness`.
- An ambiguous stem is critical **only if the ambiguity makes the keyed answer indefensible or non-unique**. A clumsy but answerable stem is a `clarity` matter.
- Keep grounding and factual correctness separate (section 3).
When `critical` is `true`, set `criticalType` to exactly one of:
`WRONG_KEY`, `EXPLANATION_CONTRADICTS_KEY`, `CONTRADICTS_GENERATOR_INPUTS`, `CONTRADICTS_FULL_NOTE`, `SOURCE_CONFLICT` (the summary or key concepts disagree with the full note), `PARTLY_CORRECT_DISTRACTOR`, `SECOND_DEFENSIBLE_ANSWER`, `AMBIGUOUS_STEM`, `COMPLEXITY_UNSUPPORTED_BY_INPUTS`, `OTHER`. Otherwise `criticalType` is `null`. Choose the type by what is wrong with the item, not by why you think it happened.

## 5. Scores (whole numbers 1 to 5) and the giveaway judgement

| Field | 1 | 3 | 5 |
|---|---|---|---|
| `distractorPlausibility` (choice-based items only, else `null`) | at least one absurd, unrelated or obviously false incorrect choice | mostly plausible, at least one weak strawman | every incorrect choice reflects a realistic misconception, an incomplete idea or a close concept |
| `modeAlignment` (every item) | trivial, trick, off-source or unfair for a quiz meant to test understanding | fair and routine | appropriately challenging **and** fair for the source; separates a learner who understands from one who does not |
| `cognitiveDemand` (every item; descriptive) | pure recall of a stated fact | single-step application or comparison | multi-step reasoning on a stated situation |
| `clarity` (every item) | ambiguous or hard to parse | understandable with effort | precise and unambiguous |
| `faithfulness` (every item) | contradicts or invents beyond the inputs | mostly supported, some `UNSUPPORTED` detail | fully `GROUNDED` |

**`seriousGiveaway`** (choice-based items only, else `null`): `true` if a learner who does **not** know the material could identify the keyed answer, or eliminate all but one choice, with confidence **from form alone**. A mere tendency (a slightly longer correct choice) is **not** serious. When `true`, set `giveawayPattern` to exactly one of `LENGTH_OR_SPECIFICITY`, `ABSOLUTE_WORDS`, `GRAMMAR_AGREEMENT`, `FORMAT_ODD_ONE_OUT`, `DUPLICATE_CHOICES`, `ALL_OR_NONE_OF_THE_ABOVE`; otherwise `null`. Choice-based means standard multiple-choice, multi-select, assertion-style and matching items; true/false and the free-text formats get `null` for both fields and for `distractorPlausibility`.

## 6. Output (exactly this, nothing else)

**Stream 1, one JSON object per item, in the order given, no markdown, no commentary:**
`{"id":"<id>","factuallyCorrect":"YES|NO|UNVERIFIABLE","grounding":"GROUNDED|UNSUPPORTED|CONTRADICTED","critical":true|false,"criticalType":"<type>"|null,"distractorPlausibility":1-5|null,"modeAlignment":1-5,"cognitiveDemand":1-5,"clarity":1-5,"faithfulness":1-5,"seriousGiveaway":true|false|null,"giveawayPattern":"<pattern>"|null}`
These objects have **exactly** these fields: any extra or missing field makes the line invalid.

**Stream 2, after a line containing only `---NOTES---`:** one line per item where `critical` is `true` or `seriousGiveaway` is `true`: `{"id":"<id>","note":"<one sentence: what is wrong and why>"}`.

Judge every item independently and as written. Do not infer anything from the order, the ids, or how the items sit next to each other.
