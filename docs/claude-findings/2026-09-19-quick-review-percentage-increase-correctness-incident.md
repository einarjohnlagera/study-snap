# Quick Review Correctness Integrity Incident — Root Cause, Blast Radius, Prevention & Repair Audit

**Date:** 2026-09-19
**Status:** OWNER DECISIONS LOCKED (2026-09-21) — see §Q.1. **Repair SQL EXECUTED by the owner, 2026-09-22** — `docs/claude-plans/2026-09-21-quiz-answer-key-repair.sql`, all four sections (A/B/C/D) run, every per-section post-check (A.9, B.99, C.99, D.99) returned clean (0 rows / corrected values as expected), covering all 38 confirmed repairs (12 `study_packs` + 14 `exam_question_pool` array elements + 10 `challenge_quiz_question_bank` + 2 retroactive-history rows). The H4 validator and its H1/H2/H3/H3b hardening shipped separately via Codex, merged PR #1421 into `releases/v0.155.0`. Both halves of this release are now complete.

**⚠️⚠️ CORRECTED 2026-09-22 — the "duplicate defect" correction below was ITSELF WRONG, found by the owner mid-repair.** While running Section B, the owner's own pre-check returned 14 rows against this document's claimed 15 — investigating (a live re-read of pool `2437d442`) found its second "near-identical" question actually carries a **different** choices array (`["$115,000","$125,000","$135,000","$155,000"]` vs the defective question's `["$135,000","$145,000","$125,000","$140,000"]`) and is **already correctly keyed** to `$135,000`. It is a distinct, differently-worded question on the same topic — not a duplicate of the confirmed defect at all. **The true confirmed defect count is 30, not 31** (14 in `exam_question_pool`, not 15/16 as the superseded paragraph below claims). The repair file's B.1 statement was always safe regardless of this documentation error, because it matches on the defective question's specific choices array plus `correctIndex`, a condition the correct question never satisfies. **Lesson for this document's own methodology:** the "duplicate" claim below was accepted on matching *question wording* ("near-identical worded") without re-verifying the *choices arrays* were actually identical — exactly the kind of claim this document's own N2 false-positive discipline (§H) says must be checked against stated inputs, not prose similarity.

**Superseded, kept for the record — this paragraph's count is wrong, corrected above:** ~~One correction discovered while preparing the repair file, not caught in §T: pool `2437d442-…` (Net cash, indirect method, NI $120,000) contains **two** near-identical worded questions with identical choices and identical wrong key — a duplicate defect, not one. §T.1 listed it once; the repair file's single statement for that pool fixes both (matched on choices+correctIndex rather than question wording, so it correctly catches both without needing separate entries). Confirmed defect count is therefore 31, not 30 (16 in `exam_question_pool`, not 14) — §I/§T's scale figures should be read with that correction. All 39 repair-file entries (now including this second element) were independently re-derived and cross-checked against a fresh production read on 2026-09-21, immediately before the file was written — no further drift found on any of them.~~
**Repo state at audit:** `main`, v0.154.0 (Released)
**Production access:** READ-ONLY (`SELECT` only) via Render MCP, instance `dpg-d6tvb8fkijhs73fda4m0-a`. **No writes were performed.**
**PII:** All learners referenced by UUID only. Question text / choices / explanations are LLM-generated study content and are quoted verbatim as evidence.

---

## A. Executive verdict

**A single generated MCQ was persisted with an answer key that contradicts its own explanation, and the defect was present in the LLM's response payload before any NoteLib code touched it.** The stored artifact for the reported question carries `correctIndex = 2` (`"25%"`) while its own `explanation` and `workingSolution` both derive and state `30%` (`choices[1]`). This is **failure class A — generation inconsistency**, not a parsing, shuffle, persistence, assembly, evaluation, or rendering defect. Confidence: **high**, on direct production artifact evidence plus a complete code trace showing the answer identity is a single canonical zero-based `correctIndex` that is never re-derived, re-mapped, or text-matched anywhere after parse.

Scope is **GENERATION_PIPELINE** (shared by all five quiz modes, all of which consume the same `QuizItem` shape and the same int-vs-int grader), but **measured learner blast radius is very small**: across **115,333 production questions in four stores**, deterministic scanning finds **zero answer-key structural defects** (six checks) and **71** internally-contradictory candidates, of which only **17** sit in `study_packs` reachable by Quick Review, and only **one learner (the reporter) has ever run a session against any of them** — two sessions, both on the reported pack. The 25 exam-pool candidates were never served; the 29 challenge-bank candidates belong to the owner's own admin/test account and are all `UNANSWERED`.

Severity: **low in realized learner harm, high in trust and in what it reveals about the generation contract.** The system has no check — at any layer — that a generated question's answer key agrees with its own explanation. The defect class is silent, durable, and it *did* corrupt a real learner's score and ConceptHealth record.

---

## B. Exact incident trace

### Stage 0 — Learner report (`feedback`)

| Field | Value |
|---|---|
| Feedback id | `24d63431-ab2f-43cf-a1cb-9e4ea19046ca` |
| Learner (user id) | `68c5ff32-88af-4bcd-81bb-b7b6a7a213f9` |
| Created | `2026-09-16T08:55:26.736Z` |
| Page URL | `/notes/4389e11d-…/quick-review?sessionId=7ad230c1-…` |
| Message (structured prefix) | `Feedback type: Quiz Feedback / Quiz: Quick Review / Context: Quiz Results / Note: Fractions, Decimals, Percentages, and Ratios` + `one question gave the wrong correct answer` |
| Status | `NEW` |

The page URL carried both the Note id and the session id. That is the only reason this trace was possible — see §R/18.

### Stage 1 — Generation / source artifact (FIRST INCORRECT STATE)

Source public Note `e0811772-23be-4a79-892d-676ead6e8823` (PUBLIC, created `2026-09-09T01:35:38Z`) → Study Pack `1ccb19f9-8542-47bf-992a-cf0a0fb3ce0a`.
Learner's copy Note `4389e11d-2a03-4423-b8d6-f5b418f0fe49` (PRIVATE, `copied_from_note_id = e0811772…`, `copied_from_public = true`, copied `2026-09-16T05:50:17Z`) → Study Pack `c8dd3aa2-6067-4d84-b9bd-85dc1a583770`.

`study_packs.quiz[4]` — **byte-identical in both packs**:

```json
{
  "question": "Calculate the percentage increase when a quantity changes from 50 to 65.",
  "choices": ["15%", "30%", "25%", "10%"],
  "correctIndex": 2,
  "questionFormat": "MCQ",
  "questionType": "COMPUTATIONAL",
  "concept": "Percentage change",
  "explanation": "Percentage increase is calculated as $(\\text{Change} / \\text{Original}) \\times 100\\%$. Here, change is $65 - 50 = 15$. So percentage increase = $(15 / 50) \\times 100\\% = 30\\%$. The correct choice reflecting this calculation is 30%.",
  "workingSolution": "Change = 65 - 50 = 15\nPercentage increase = $\\frac{15}{50} \\times 100\\% = 30\\%$",
  "correctIndices": [], "acceptableAnswers": [], "acceptableAnswerGroups": [],
  "questionGroup": null, "keyConcept": null, "sourceStudyPackId": null
}
```

`correctIndex = 2` → `choices[2]` = `"25%"`. Explanation and workingSolution both conclude `30%` = `choices[1]`. **The artifact is self-contradictory at rest.** This is the first incorrect state.

The other four questions in the same pack are all correctly keyed (verified individually: `correctIndex` 0, 0, 1, 0 — each agrees with its explanation). **There is no systematic off-by-one**; a parser indexing bug would have moved all five.

### Stage 2 — Persistence

No defect. The value written is the value the pipeline computed. `study_packs.quiz` is the live store; `quiz_questions` (the `QuizQuestionEntity` table) holds **0 rows in production** and is orphaned dead code (see §C).

### Stage 3 — Copy propagation

The learner's pack is an exact copy made under the documented public-copy contract (public-note copies include the linked StudyPack). No mutation occurred during copy. Copy fan-out for this source note: **1**.

### Stage 4 — Quick Review session construction

`quick_review_sessions.session_state` stores **only learner selections as canonical indexes** — it does *not* snapshot the questions:

```json
{"selectedChoices":{...},"roundSelections":{...},"activeQuestionIndexes":[...],"retryQuestionIndexes":[...]}
```

The quiz is read live from the Study Pack at review time (`QuickReviewSessionService.java:427-429`). No transformation of answer identity occurs.

### Stage 5 — Evaluation (two sessions, and this is the important part)

**Session 1 — `1e78a11d-d76a-4e06-adb1-75f52386248a`** (`08:52:58Z` → `08:54:27Z`)

```
selectedChoices      : {0:0, 1:0, 2:1, 3:0, 4:1}
retryQuestionIndexes : [3, 4]
roundSelections      : {3:0, 4:1}      ← retry-round answers
correct_answers      : 4      verified_correct_answers: 4      score: 80.00%
```

Retry mechanics (`frontend/app/study-packs/[id]/quick-review/page.tsx:972-979`): at the end of the initial round the client computes `incorrectIndexes` via `isQuizSelectionCorrect`, sets those as `retryQuestionIndexes`/`activeQuestionIndexes`, clears `roundSelections`, and re-asks only those. `selectedChoices` is then *overwritten* by the retry answers. So: the learner got Q3 and Q4 wrong in the initial round; on retry they answered Q3 = index 0 (correct, counted) and **Q4 = index 1 = `"30%"` — the mathematically correct answer — which was graded INCORRECT** against `correctIndex = 2`.

Final stored state therefore records the learner choosing the right answer and scoring **80% instead of 100%**.

**Session 2 — `7ad230c1-0e92-48d6-91ff-ec96cfbe82bd`** (`08:54:38Z` → `08:55:06Z`)

```
selectedChoices : {0:0, 1:0, 2:1, 3:0, 4:2}
correct_answers : 5   verified_correct_answers: 5   score: 100.00%
```

The learner re-ran the quiz and selected index **2** (`"25%"`) — the keyed-but-wrong answer — to score 100%. **20 seconds later they filed the feedback report.** The behavioural signature is unmistakable: answered correctly, was told they were wrong, reverse-engineered the key, then reported the bug.

### Stage 6 — Results rendering

No defect. `quiz-choice-list.tsx:57` marks `✓ Correct` via `choice.canonicalIndex === correctIndex`. The frontend does apply a deterministic display shuffle (`lib/quiz.ts:420-434`) but it is **display-only and canonical-index-stable** — selections record `canonicalIndex`, so grading and highlighting agree. The UI faithfully rendered a wrong artifact.

### First incorrect state — verdict

> **The LLM response payload for this question, before `resolveAnswerIndex` converted it.** The model emitted answer letter `"C"` while its explanation computed `30%` (letter `B`). Every NoteLib layer downstream propagated that faithfully.

---

## C. Root cause

**Class A — generation inconsistency. The model selected the wrong answer *letter* while producing a correct worked solution, and nothing in the pipeline cross-checks the two.**

### Evidence 1 — the answer identity is a letter at the LLM boundary

`backend/src/main/resources/prompts/study-pack-v1/developer.txt:90-91`:

```
- standard MCQ answers must be exactly one of: A, B, C, D
- exactly one choice must be correct
```

JSON shape block: `"answer": "A" | "B" | "C" | "D"`.

The model is never asked for the answer *text* or the *index*. It must hold the choice array in working memory and emit a positional letter. **That indirection is the proximate design cause**: the model computed `30%` correctly and then mis-mapped it to a letter.

### Evidence 2 — the schema does not constrain the letter

`backend/src/main/resources/prompts/study-pack-v1/schema.json`:

```json
"answer": { "type": ["string", "null"] }
```

No `enum`, no `pattern`. Structured-output `strict: true` is set (`OpenAiLlmStudyPackService.java:373-376`) but strict mode only enforces required keys + `additionalProperties:false`. Letter-ness is enforced in Java only.

### Evidence 3 — the parser is deterministic and cannot mis-map

`OpenAiLlmStudyPackService.java:1467-1492`:

```java
return switch (normalizedAnswer.toUpperCase()) {
    case "A" -> 0;
    case "B" -> 1;
    case "C" -> { if (choiceCount != 4) { throw invalidOutput(invalidMessage); } yield 2; }
    case "D" -> { if (choiceCount != 4) { throw invalidOutput(invalidMessage); } yield 3; }
    default -> throw invalidOutput(invalidMessage);
};
```

Anything that is not a bare letter **hard-fails generation**; it is never silently coerced. `legacyAnswer` is explicitly persisted as `null` (`OpenAiLlmStudyPackService.java:502-518`), so production is index-only end to end. `C → 2 → "25%"` is exactly what we observe. **Classes B, D are excluded.**

### Evidence 4 — the model was visibly struggling with letter mapping

A second defective question found by the corpus scan (Study Pack `5e2e1633-5be0-4fb2-b1de-0dab91f3719a`, Note `353f891b-…`) contains the model's own leaked reasoning:

> "Then compute $p_p = K_p \times \gamma \times z = 3 \times 18 \times 4 = 216 \text{ kPa}$. **Note the calculation for this example yields 216 kPa, but since 216 kPa is among choices, it is correct. Double check the choices for the correct letter accordingly.**"

Keyed answer: `correctIndex = 3` = `"259.2 kPa"`. Correct answer per its own working: `216 kPa` = `choices[0]`. This is direct, in-corpus evidence that the failure is *letter selection at generation time*, and it corroborates Evidence 1.

### Evidence 5 — no reordering can lose answer identity

- `QuizVersionShuffleUtils.java:115-128` (DOCX export) correctly remaps `correctIndex` **and** `correctIndices`.
- `frontend/lib/quiz.ts:349-366` shuffles for display only; `quiz-choice-list.tsx:82` emits `choice.canonicalIndex`.
- **Latent hazard, not this bug:** `QuizValidationUtils.randomizeChoices` (`:178-183`) reorders choices **without remapping `correctIndex`**. It is dead code — its only caller is `QuizValidationUtilsTest.java:202-216` — but it is a loaded gun. Recommend deletion (§H).

**Classes C, E, F, G, H excluded. Class A confirmed.**

### Corrected premise — `QuizQuestionEntity` is dead code

The orientation for this audit pointed at `QuizQuestionEntity.answer`. That field is **not the live answer identity**:

- `grep -rn "setAnswer\|getAnswer" backend/src` → **zero hits**.
- `QuizQuestionRepository` is injected nowhere.
- `quiz_questions` holds **0 rows in production**; it was populated once by `V20__quiz_questions_and_usage_tracking.sql:42` as a snapshot and never written since.

The live identity is `StudyPackEntity.quiz` → `List<QuizItem>` JSONB with `Integer correctIndex` (`StudyPackEntity.java:66-68`). `QuizItem.answer()` is a `@JsonIgnore` *derived* accessor (`QuizItem.java:323-329`) returning `choices.get(correctIndex)` — never persisted, never sent to the client.

---

## D. Blast radius

### Corpus scale (production, measured)

| Store | Rows | Questions |
|---|---:|---:|
| `study_packs.quiz` | 8,276 | 41,380 |
| `exam_question_pool.questions` | 1,316 | 44,952 |
| `challenge_quiz_question_bank.question` | 28,901 | 28,901 |
| `generated_quizzes.questions` | 8 | 100 |
| **Total** | | **115,333** |

Question-format mix in `study_packs`: MCQ 38,364 · `null` (legacy, treated MCQ) 1,630 · MULTI_SELECT 993 · TRUE_FALSE 393.
**All four stores use an identical `QuizItem` JSON shape** (verified via `jsonb_object_keys`) — confirming shared infrastructure.

### Structural integrity scan — clean on answer-key integrity, 4 defects in MATCHING block integrity

Across **all 115,333 questions in all four stores**:

| Check | Violations |
|---|---:|
| `correctIndex` null on MCQ / TRUE_FALSE / MATCHING | **0** |
| `correctIndex` out of range (`<0` or `>= len(choices)`) | **0** |
| Duplicate choices (case-insensitive, trimmed) | **0** |
| MULTI_SELECT with empty or out-of-range `correctIndices` | **0** |
| MULTI_SELECT `correctIndex` contradicting `correctIndices[0]` (2,470 rows carry both) | **0** |
| MULTI_SELECT with *every* choice keyed correct | **0** |

> **No structural check would have detected the reported defect, or any defect of its class.** This is the single most important quantitative result in this audit: it is why §H recommends internal-consistency validation and *not* a structural answer-key validator.

**⚠️ Scope-of-claim correction.** "Zero structural defects" is true **for the six answer-key checks listed above** and must not be stated more broadly. A seventh and eighth check — **MATCHING block integrity** — do find violations:

| MATCHING check | Result |
|---|---|
| Total MATCHING blocks in the corpus | **50** (all in `exam_question_pool`; `study_packs` has none) |
| Blocks violating the 2–4 size rule (`developer.txt`, `long-exam-developer.txt:30`) | **4** — three size-1 singletons, one size-6 |
| Blocks whose items do **not** share an identical `choices` array (prompt marks this **CRITICAL**) | **1** (the size-6 block) |
| Valid blocks | **46 / 50 (92%)** |

The three **size-1 singletons** are the exact hazard noted in §S.1: `groupQuizItems` (`lib/quiz.ts:35`) only forms a group when `length > 1`, so a lone MATCHING item renders through the **shuffled** `QuizChoiceList` instead of the **unshuffled** `QuizMatchingGroup`. `OpenAiLlmStudyPackService.java:589-591` demotes size-1 groups to MCQ at generation, but only inside `normalizeMatchingGroups` — these rows escaped it.

**Learner exposure of these 4 blocks is nil**: they are all in `exam_question_pool`, and see the session evidence below. They are recorded here for completeness and folded into §H as a cheap structural check worth adding — unlike the answer-key checks, this one has a non-zero measured yield.

### Internal-consistency scan — the actual defect signature

Detector: MCQ, all choices contain a digit and are ≤ 20 chars (numeric/unit literal answers), normalised case-insensitively with `$`/`,`/whitespace stripped. Flag when the **keyed choice string does not appear anywhere in `explanation` + `workingSolution`, while some other choice string does.**

| Store | Numeric-literal MCQs scanned | Candidates | Rate |
|---|---:|---:|---:|
| `study_packs` | 924 | **17** | 1.8% |
| `exam_question_pool` | 2,607 | **25** | 1.0% |
| `challenge_quiz_question_bank` | 1,912 | **29** | 1.5% |
| **Total** | **5,443** | **71** | **1.3%** |

**⚠️ Precision was measured on ONE store only — do not read it as applying to all 71.** **6/6 on manual review** of the distinct `study_packs`-store defects under the stricter first-pass filter (6 distinct questions, drawn from the 17 raw `study_packs` candidates in the table above). The 25 `exam_question_pool` and 29 `challenge_quiz_question_bank` candidates (54 of the 71 total) have **not** been manually reviewed in this pass — their true precision is unmeasured, not assumed equal to the `study_packs` figure. §I's "71 candidates, precision 6/6" framing must be read as: **71 raw candidates across 4 stores; 6/6 precision confirmed on the 6 distinct questions manually checked, all of which happen to be in `study_packs`.** Every one of the six is a genuine key/explanation contradiction:

| Study Pack | Question (abbrev.) | Choices | Keyed | Explanation concludes |
|---|---|---|---|---|
| `c8dd3aa2-…` / `1ccb19f9-…` | % increase 50 → 65 | `15%, 30%, 25%, 10%` | `25%` (idx 2) | **30%** (idx 1) |
| `2e89b22c-…` / `b2f7e4f3-…` | Rational Method culvert Q | `0.833, 8.33, 83.3, 0.0833 m³/s` | `8.33` (idx 1) | **0.833** (idx 0) |
| `400c9315-…` / `70409a24-…` | Break-even point | `500, 333, 667, 250 units` | `667` (idx 2) | **500** (idx 0) |
| `5e2e1633-…` / `9756f274-…` | Rankine passive pressure | `216, 144, 194.4, 259.2 kPa` | `259.2` (idx 3) | **216** (idx 0) |
| `6a2d6712-…` / `9b96153b-…` | Traverse latitude | `50, 75, 100, 86.6 m` | `86.6` (idx 3) | **50** (idx 0) |
| `771a06a8-…` / `ccabeefc-…` | Darcy-Weisbach head loss | `9.18, 18.36, 4.59, 2.29 m` | `9.18` (idx 0) | **4.59** (idx 2) |

**⚠️ This 71 is a measured LOWER BOUND, not an estimate of the true population.** Two stated exclusions:

1. **The detector only sees numeric-literal answers — 5,443 of 115,333 questions (4.7%).** Prose-choice MCQs are structurally invisible to it.
2. **The generation prompt actively suppresses the signal it depends on.** `developer.txt:105`: *"explanation must explain WHY the answer is correct — **do not repeat, list, or reference answer choices by letter (A, B, C, D) or by choice text**"*. Computational questions leak the answer only because a *separate* rule overrides it — `developer.txt:106`: *"for computation questions, explanation must show a short step-by-step solution"*. This precisely explains why every hit is computational, and it means the detector's recall on prose questions is near zero **by prompt design**, not by chance.

Do **not** extrapolate 1.3% across 115,333 to claim ~1,500 defects. The keying-error rate is plausibly format-dependent (computational questions require an extra derive-then-map step that conceptual questions do not) and we have no measurement for prose items. **The honest statement is: 71 confirmed-class candidates found; the prose-choice population is unmeasured and not deterministically measurable under the current prompt contract.**

### Confirmed affected

- **6 distinct defective questions**, each present in exactly 2 Study Packs (1 public source + 1 private learner copy) = **12 packs** under the strict filter; **17 packs** (10 public notes, 7 private copies) under the relaxed filter.
- **1 learner** (`68c5ff32-…`), **2 Quick Review sessions**, both on Study Pack `c8dd3aa2-…`.

  **Session-coverage verification (this number is load-bearing for §L, so it was checked rather than assumed):** `quick_review_sessions` is the shared table for **all** quiz modes per the shared-session model, and its live mode distribution is `QUICK_REVIEW 616 · CHALLENGE 213 · ADAPTIVE 67 · LONG_EXAM 5` = **901 rows**. The separate `quiz_sessions` table holds **exactly 901 rows with exactly the same ids and the same mode split** — it is a 1:1 shadow, not an additional population. **There has never been a single `BOARD_EXAM` session in production.** The `study_pack_id` join therefore covers every quiz session that exists, and "1 learner, 2 sessions" is complete, not a lower bound.
- **1 learner score corrupted** (session `1e78a11d-…`: 80% recorded, 100% actual).
- **1 ConceptHealth row contaminated** (`9733ae06-…`, concept `Percentage change`).
- Copy fan-out per affected public note: **1 each** (verified by `GROUP BY copied_from_note_id`) — no wider propagation.

### Potentially affected

- **25 `exam_question_pool` candidates** (11 BOARD_EXAM, 14 LONG_EXAM across 24 packs). **All sit in pools whose `served_question_keys` is empty — never served to any exam session.** Zero learner exposure.
- **29 `challenge_quiz_question_bank` candidates.** All belong to user `dee4225c-e460-4f89-a6e5-cd43f6dd1972` — the same account that filed the `Test`/`Testing` feedback from `/admin`, i.e. the owner's own admin/test account — and **all 29 are `last_known_outcome = 'UNANSWERED'`**. Zero learner exposure.
- The **unmeasured prose-choice population** across all stores.

### Not affected

- Structural integrity of the corpus (0 violations, 115,333 questions).
- Parsing, persistence, session snapshotting, grading, and results rendering for MCQ — all index-pure and verified correct.
- MULTI_SELECT / TRUE_FALSE / IDENTIFICATION / ENUMERATION answer integrity (no structural violations; no evidence of this defect class).
- `generated_quizzes` (100 questions, 8 rows, no candidates).
- Learner copies did **not** receive any silent propagation — the copy predates the incident and has not been re-synced.

---

## E. Underlying Note / Study Pack correctness

**Classification: `NOTE_CORRECT_QUIZ_WRONG`.**

Source Note `e0811772-…` content states the rule **correctly**:

> "Percentage increase/decrease is calculated by $\frac{\text{Change}}{\text{Original}} \times 100\%$."

Study Pack `c8dd3aa2-…` `summary` states it **correctly**:

> "Percentage increase or decrease can be calculated using $\frac{\text{Change}}{\text{Original}} \times 100\%$."

`key_concepts` are correct and include `Percentage change`. The quiz explanation and `workingSolution` are also correct. **Exactly one scalar is wrong: `quiz[4].correctIndex`.**

| Question | Answer |
|---|---|
| Does this learner need **Note + Study Pack** regeneration? | **NO** |
| Does this learner need **Study Pack-only** regeneration? | **NO** — regeneration is a non-deterministic re-roll of all five questions that would discard correct content and could reintroduce the same class of defect. A targeted single-field correction is strictly safer. |
| Neither? | **Correct — neither.** One field repair on one question, in two packs. |

This finding directly overrides any default instinct to regenerate. Per the versioning doctrine (never auto-regenerate; explicit confirmation; in-place update to preserve session linkage), regeneration here would be both unnecessary and destructive of correct material.

---

## F. Learning-record impact

**⚠️ Durable learning evidence WAS contaminated. This is not display-only.**

### Doctrine correction

The prompt for this audit carried the claim *"Quick Review historically should NOT write ConceptHealth."* **That claim is false against current truth, and the repo already knows it.** Verified from both directions:

- Code: `QuickReviewSessionService.java:84` injects `ConceptHealthService`; `:309` calls `recordCorrectAnswers(...)`; `:313` calls `recordIncorrectAnswers(...)`.
- Doc: `docs/features/quick-review.md:123` — *"**Quick Review DOES record to `ConceptHealth`.** Corrected 2026-08-12 — this section had described the opposite for a month and was wrong."*

The feature doc is current and correct. No doc change needed on this axis.

### Measured contamination

`concept_health` rows for user `68c5ff32-…` / Study Pack `c8dd3aa2-…`, all created `08:54:27.687Z` (session 1 completion) and updated `08:55:06.428Z` (session 2 completion):

| id | concept | last_correct_at | last_incorrect_at | incorrect_streak |
|---|---|---|---|---|
| `29fcd483-…` | Fraction definition | 08:55:06 | `null` | 0 |
| `cc9b57af-…` | Conversion methods | 08:55:06 | `null` | 0 |
| `2f81389d-…` | Percentage meaning | 08:55:06 | `null` | 0 |
| `067b472b-…` | Ratio comparison | 08:55:06 | `null` | 0 |
| **`9733ae06-…`** | **Percentage change** | 08:55:06 | **`2026-09-16T08:54:27Z`** | 0 |

`Percentage change` is the **only** concept carrying a `last_incorrect_at`, and its timestamp is exactly session 1's completion. **A learner who answered correctly was recorded as having failed the concept.**

### Signals affected

| Signal | Affected? | Evidence |
|---|---|---|
| Session score | **YES** | `1e78a11d-…`: `correct_answers = 4`, `score_percentage = 80.00` — should be 5 / 100.00 |
| Server-verified score | **YES** | `verified_correct_answers = 4` — the server re-derivation agrees with the client because *both* read the same wrong key (`QuizSessionReviewUtils.java:430-462`, int-vs-int) |
| Activity history | **YES** (indirectly) | a COMPLETED session with a depressed score is recorded |
| **ConceptHealth** | **YES** | `9733ae06-…` `last_incorrect_at` set — durable |
| Weak concepts / twice-missed | **Likely** | `Percentage change` was missed in both the initial and retry rounds of session 1; `recordIncorrectAnswers` returns `twiceMissedConcepts`. Current row shows `incorrect_streak = 0` because session 2 reset it. |
| Readiness / recommendations / Companion | **Downstream of ConceptHealth** — `quick-review.md:128` states weak-area recommendations read shared ConceptHealth fed by Quick Review alongside all other modes. Exact numeric effect not quantified; the input was corrupted. |
| Streak | **NO** | both sessions completed; streak counts completion, not correctness |

**Self-healing note:** session 2 set `last_correct_at = 08:55:06` and reset `incorrect_streak` to 0, so the *current* ConceptHealth state for this learner is benign. The residual corruption is (a) the historical `last_incorrect_at` timestamp and (b) session 1's permanently recorded 80%.

---

## G. Existing validation map

| Boundary | Structural guarantee today | Internal-consistency guarantee | Semantic guarantee | Gap |
|---|---|---|---|---|
| **A. LLM structured output** (`schema.json`, `strict:true`) | Required keys, `additionalProperties:false`. `"answer": ["string","null"]` — **no `enum`, no `pattern`** | none | none | **Letter-ness unenforced at the schema boundary** |
| **B. Parsing** (`resolveAnswerIndex`, `OpenAiLlmStudyPackService:1467`) | Hard-fails on non-`A/B/C/D`; enforces `choiceCount ∈ {2,4}`; `C`/`D` rejected when 2 choices | none | none | **No check that the key agrees with the explanation** |
| **C. Generation service** (`normalizeMatchingGroups`, choice/explanation normalisation) | Matching-block size 2–4 enforced; choice-label stripping; explanation required | none | none | Same |
| **D. Persistence** (`QuizItem` canonical ctor, `QuizItem.java:227-263`) | `correctIndex` derived, immutable, `final`; precedence ladder `correctIndex > answerIndex > correctAnswerIndex > correctIndices[0] > exact text > letter` (`:458-493`) | none | none | Same |
| **E. Session construction** (`QuizSessionStateUtils.serializeQuiz:592-619`) | Writes `correctIndex`/`correctIndices` only; `ANSWER_KEY` declared `:23` but never written | none | none | Snapshot trusts the stored key |
| **F. Quiz start** | — | none | none | — |
| **G. Answer submission / scoring** (`QuizSessionReviewUtils:430-462`) | Int-vs-int on `correctIndex`; MULTI_SELECT sorted-set equality; IDENTIFICATION/ENUMERATION string-match against `acceptableAnswers` | none | none | Grades faithfully against a wrong key |
| **H. Result rendering** (`quiz-choice-list.tsx:57`) | `canonicalIndex === correctIndex`; display shuffle is canonical-stable | none | none | Renders a wrong key faithfully |

**Summary of gaps:**

1. **No layer, anywhere, compares the answer key against the explanation.** The defect passes all eight boundaries untouched.
2. **The `answer` field is unconstrained in the JSON schema** — the one place the letter contract could be enforced declaratively.
3. **Structural answer-key validation is already effectively perfect** (0 violations on six checks across 115,333 questions) and therefore has **zero marginal detection value for this defect class**. Adding a structural answer-key validator would be pure ceremony. **MATCHING block integrity is the one exception — 4/50 blocks violate it (§D) and it is worth a cheap check (H3b).**
4. **`QuizValidationUtils.randomizeChoices:178-183`** reorders choices without remapping `correctIndex` — dead code, latent hazard.
5. **The prompt forbids explanations from naming the answer** (`developer.txt:105`), which suppresses the only deterministic consistency signal available.

---

## H. Prevention recommendation

**Smallest robust fix: an internal-consistency gate at the parse boundary (B), plus a schema tightening at (A). Nothing else.**

Keep the three tiers strictly separate — the spec's §24 warning applies directly here.

### Tier 1 — STRUCTURAL validation (answer-key checks already satisfied; MATCHING block integrity is not)

- **H1. Constrain `answer` in `schema.json`** to `{"type":["string","null"],"enum":["A","B","C","D",null]}`. Zero runtime cost, moves an invariant from Java into the model's decoding constraints. *This does not prevent the bug* (the model emitted a valid letter — just the wrong one) but it closes the schema/Java divergence.
- **H2. Delete `QuizValidationUtils.randomizeChoices` and its test.** Dead code that silently breaks answer identity if ever wired in.
- **H3. Delete or explicitly tombstone `QuizQuestionEntity` + `QuizQuestionRepository` + the `quiz_questions` table.** 0 rows, 0 references, and it actively misled the opening of this very investigation. (Table drop = owner-run migration; see §J.)
- **H3b. Add a MATCHING block-integrity check at generation** — enforce block size 2–4 and an identical `choices` array across all items in a block, demoting to MCQ otherwise. **Unlike the answer-key checks this one has measured yield: 4 of 50 blocks (8%) violate it today** (§D), and the 3 singletons cause a real frontend rendering inconsistency (§S.1). `normalizeMatchingGroups` (`OpenAiLlmStudyPackService.java:589-591`) already implements the demotion — the rows that escaped indicate it is not reached on every path. **Cheap, and it is the only structural check with a non-zero base rate.**
- Do **not** add correctness checks for index-in-range / duplicate choices / exactly-one-key / MULTI_SELECT key agreement as new runtime validators expecting yield. Measured violations: **0 of 115,333 on all six answer-key checks**. If added at all, add them as cheap assertions, and do not report them as the fix.

### Tier 2 — INTERNAL-CONSISTENCY validation ← **this is the actual fix**

**H4. Add an answer↔explanation consistency check at generation, applied only where it is deterministic and high-precision.**

Placement: in the generation service immediately after `resolveAnswerIndex`, before `QuizItem` construction — i.e. **before persistence**, at boundary (B)/(C).

Rule (deliberately narrow, mirrors the validated detector from §D):

> For an MCQ whose choices are **all numeric/unit literals** (every choice contains a digit, ≤ 20 chars) — normalise case, whitespace, `$`, `,` — if the **keyed** choice string does not occur in `explanation + workingSolution` **and some other choice string does**, the question is internally inconsistent.

Measured properties on the live corpus: **precision 6/6 manually verified; 71/5,443 flag rate (1.3%)**; deterministic; zero model cost.

**H5. Pair it with a prompt change, or H4's recall stays near zero on prose questions.**
`developer.txt:105` currently forbids the explanation from naming the answer by text. **Relax that rule to require the explanation to state the correct answer's value/meaning explicitly** (while still forbidding *letter* references, which are meaningless after the display shuffle). This is a one-line prompt edit that converts H4 from "catches computational questions only" into a general-purpose detector — and it independently improves explanation quality for learners. **This is the highest-leverage single change in this document.** It must be treated as a generation-behaviour change and re-verified on a sample.

**H6. Consider replacing the letter contract with the answer text.** The root cause is positional indirection: the model derives `30%`, then must map it to a letter. Asking for `"correctAnswerText": "30%"` and resolving by exact match against `choices` (hard-fail on no-match/ambiguous-match) removes the mapping step entirely and makes the key self-validating. **Higher risk** (changes every mode's prompt + parser; exact-match brittleness on LaTeX/unit formatting; needs the legacy ladder retained for existing rows), so: **flag for owner decision, do not bundle into the incident fix.**

### Tier 3 — SEMANTIC verification

**Not recommended for this incident. Explicitly out of scope.**

> **⚠️ A question passing H4 is NOT verified correct.** H4 proves only that the answer key and the explanation agree. It proves nothing about whether the explanation is factually right, whether the stem is well-posed, or whether more than one option is defensible. **Do not label H4's output "verified", do not surface any learner-facing correctness badge from it, and do not let it close the follow-up Question Quality audit.** An explanation that merely rationalises a wrongly-keyed answer would pass H4 cleanly.

### Where the future single-best-answer / ambiguity gate belongs (spec §24.3)

Architecturally it is a **separate, later stage than H4**: H4 sits at the *parse* boundary and is deterministic and synchronous; an ambiguity gate is a *semantic* judgement that requires either a model call or human curation and therefore belongs **out of the synchronous generation path** — as an asynchronous post-generation review queue over high-value (public / curator / official) material, feeding a curator surface rather than blocking a learner's generation. Building it inside H4 would make an internally-consistent-but-ambiguous question look "verified", which is exactly the failure §24.4 warns about.

### Spec §24.5 — does the current prompt require exactly one clearly defensible best answer?

**NO.** `developer.txt:91` says only *"exactly one choice must be correct"* — a **uniqueness/structural** instruction, not a defensibility or non-ambiguity requirement. There is no instruction anywhere in `study-pack-v1/` requiring the best answer to be unambiguous, or forbidding stems where multiple options are defensible under different interpretations. The nearest rule pushes the *opposite* way: `developer.txt` "Quiz quality" — *"keep distractors plausible and same-topic."*

**Per spec §24.6 this is flagged for the follow-up Question Quality audit and is NOT actioned here.** (It is precisely the gap the cement/hydraulic-activity incident sits in.)

---

## I. Historical sanitation recommendation

### **DETERMINISTIC_SCAN**

**Rationale:**

- A deterministic scan **already succeeded** — it found all 6 distinct defective questions including the reported one, at 6/6 precision and zero model cost. The work is done; what remains is productionising it.
- `FULL_CORPUS` is unjustifiable: 115,333 questions, 0 answer-key structural defects, and a measured learner exposure of **one learner, two sessions**. Mass regeneration would destroy large volumes of correct, curator-reviewed material to chase a defect with no measured victim beyond one report — and would re-roll every question through the same generator that produced the defect.
- `TARGETED_VERIFICATION` is **premature but the right next step if the owner wants prose coverage** — and it should be gated behind H5. Running model verification over prose questions *today* is expensive and low-yield; running it *after* H5 makes explanations name their answers is unnecessary, because H4 then covers them deterministically for free.
- `NONE` is not defensible: 71 candidates exist, 6 are confirmed wrong, and two of them are in **public curator material** on board-exam subjects (Rankine passive pressure, Darcy-Weisbach, traverse latitude, Rational Method) where a wrong key is materially harmful to an exam candidate.

### Scale and cost

| Item | Value |
|---|---|
| Questions scanned | 115,333 (all 4 stores) |
| Model calls required | **0** |
| Wall-clock | Seconds (all scans in this audit ran as single SQL statements) |
| Candidates surfaced | 71 raw candidates (17 `study_packs`, 25 `exam_question_pool`, 29 `challenge_quiz_question_bank`) — **precision (6/6) was measured on the `study_packs` subset only; the 54 exam-pool/challenge-bank candidates remain unreviewed** |
| Confirmed defects requiring repair | **6 distinct questions across 12 Study Packs**, all independently hand-verified (§J.1–J.2) (relaxed filter: up to 17 packs — the extra 5 need the same manual confirmation) |
| Human review burden | ~71 questions, ~1–2 min each ≈ **2 hours one-off** |
| Quota impact | **None** — no generation calls |
| Operational risk | **None for the scan** (read-only). Risk lives entirely in the repair step (§J), which is owner-executed. |

**Recommended shape:** a one-off read-only scan + a manually-reviewed repair list, **not** a scheduled job. Once H4 is in the generation path, new defects of this class cannot be persisted, so a recurring scan has a decaying denominator and does not earn its keep.

### §12 — public vs private standards

The **prevention** (H4) applies uniformly to all generated assessments — there is no public/private branch and there must not be one. Only the **remediation priority** differs, and the evidence supports this order:

1. **Public curator material** (10 public notes here) — highest blast radius per defect, since each is a copy source, and board-exam subjects carry the most learner harm.
2. **Private learner copies of affected public notes** (7 here) — already-materialised independent Notes; **each must be repaired on its own row.**
3. **Exam pool / challenge bank entries** — none served; lowest urgency.
4. Other historical private artifacts — unmeasured, lowest priority.

**⚠️ Learner copies are independent Notes. Repairing the public source must NOT propagate into the copies** — there is no such contract, and creating one here would be a silent-content-replacement violation. Each affected copy is repaired as its own artifact or left for the owner to decide, per §J.

---

## J. Repair recommendation

**No production writes have been made, and none are proposed to be run by Claude.** Per CLAUDE.md, every statement below is for the **owner** to execute. If the owner approves, the exact SQL belongs in a `docs/claude-plans/*.sql` file with expected row counts and verification queries — **not run and then reported.**

### 1. The exact reported artifact

**Recommended: targeted single-field key correction in place. Not regeneration.**

Change `quiz[4].correctIndex` from `2` → `1` in **two** Study Packs:

| Study Pack | Note | Visibility |
|---|---|---|
| `1ccb19f9-8542-47bf-992a-cf0a0fb3ce0a` | `e0811772-23be-4a79-892d-676ead6e8823` | PUBLIC (source) |
| `c8dd3aa2-6067-4d84-b9bd-85dc1a583770` | `4389e11d-2a03-4423-b8d6-f5b418f0fe49` | PRIVATE (learner copy) |

Expected rows affected: **2** (one statement each, or one statement with an `IN` list).
Verification: re-read `quiz->4->>'correctIndex'` = `1` and confirm `choices[1] = "30%"` on both.

Why not regeneration: the Note, the summary, the key concepts, the explanation and the working solution are **all correct** (§E). Regeneration re-rolls all five questions non-deterministically, discards verified-correct content, breaks nothing usefully, and can reintroduce the same defect class. A single-scalar fix is strictly safer and preserves session/history linkage per the in-place versioning doctrine.

**Also recommended:** a light sanity edit to the explanation's trailing sentence (*"The correct choice reflecting this calculation is 30%."*) is **not** needed — it is correct and, post-fix, it agrees with the key. Leave it.

### 2. Other confirmed affected artifacts

The remaining 5 distinct defective questions across 10 Study Packs, same shape (one `correctIndex` per question):

| Study Packs | Question | `correctIndex` now → should be | Independently hand-verified? |
|---|---|---|---|
| `2e89b22c-…`, `b2f7e4f3-…` | Rational Method culvert | `1` → **`0`** (`0.833 m³/s`) | ✅ **YES, this pass.** Given `C=0.6, i=50` mm/hr `=0.05` m/hr, `A=10` ha `=100,000` m²: `Q=CiA=0.6×0.05×100,000=3,000` m³/hr `÷3600=0.833` m³/s. Re-derived from the stored `question`/`workingSolution`'s own stated inputs, not merely re-read from its narrative. |
| `400c9315-…`, `70409a24-…` | Break-even point | `2` → **`0`** (`500 units`) | ✅ **YES, this pass.** Given `FC=$10,000, SP=$50, VC=$30`: `BEP=FC/(SP−VC)=10,000/20=500` units. |
| `5e2e1633-…`, `9756f274-…` | Rankine passive pressure | `3` → **`0`** (`216 kPa`) | ✅ Hand-re-derived during the audit itself (§C Evidence 4): `p_p=K_p×γ×z=3×18×4=216` kPa. |
| `6a2d6712-…`, `9b96153b-…` | Traverse latitude | `3` → **`0`** (`50 m`) | ✅ **YES, this pass.** Given `d=100` m, `θ=60°`: `L=d·cosθ=100×0.5=50` m. Stored key pointed at `86.6 m = 100×sin60°` — a sin/cos mixup, not a typo. |
| `771a06a8-…`, `ccabeefc-…` | Darcy-Weisbach head loss | `0` → **`2`** (`4.59 m`) | ✅ Hand-re-derived during the audit itself (§J.2 below): `0.02×(50/0.1)×(3²/(2×9.81))=10×0.4587≈4.587` m. |

**All five are now independently hand-verified from each question's own stated numeric inputs** (not from trusting the model's prose explanation, which §C establishes is the artifact under suspicion) — the culvert, break-even, and traverse-latitude rows were re-derived in a follow-up pass specifically because the first pass had only re-checked Rankine and Darcy-Weisbach.

The Darcy-Weisbach item is the most clear-cut of the five and worth quoting, because the model states the right answer *and its letter* in prose while keying the wrong index:

> "…$= 10 \times 0.4587 = 4.587 m.$$ **The correct answer is 4.59 m, which is option C.**"

Stored `correctIndex = 0` = `"9.18 m"`. `choices[2] = "4.59 m"` = option C. Hand-recomputed: `0.02 × (50/0.1) × (3²/(2×9.81)) = 10 × 0.4587 = 4.587 m`. **The model named the correct letter in its explanation and still emitted the wrong one in the `answer` field** — the single strongest piece of evidence in this audit for class A and for H6.

Expected rows affected: **10**.
**All five corrected values are now independently hand-verified** (table above) from each question's own stated numeric inputs — not from its narrative explanation. **A curator eye is still warranted before running**, since this is public board-exam material and a second independent check on generated content costs little, but the "trust the suspect artifact's own prose" gap flagged in an earlier pass of this audit is closed.

The additional 5 candidates surfaced only by the relaxed filter need the same manual pass before inclusion.

**Not recommended for repair:** the 25 exam-pool and 29 challenge-bank candidates. Both sets have **zero learner exposure** (never served / owner test account, all `UNANSWERED`). Exam pools are regenerated on their own cadence and challenge-bank rows are per-user ephemera; repairing them is effort against no audience. Record the finding, leave the rows.

### 3. Historical completed sessions

**Recommended: do NOT rewrite. Record as a separate product decision.**

Affected: session `1e78a11d-…` (80% recorded, 100% actual) and ConceptHealth row `9733ae06-…` (`last_incorrect_at = 2026-09-16T08:54:27Z`).

Reasons not to silently rewrite:

- There is **no existing product contract for retroactive score correction**. Creating one inside an incident fix is exactly the "silent learner-content replacement" the spec forbids (§22).
- The contamination is **already largely self-healed**: session 2 reset `incorrect_streak` to 0 and set `last_correct_at`. Current-state ConceptHealth for this learner is benign.
- Rewriting a completed session's score changes visible history a learner may have seen, with no notification path.

**What to do instead:** tell the learner directly (§K) that their 80% was our error, and let the owner decide separately whether NoteLib wants a retroactive-correction contract at all. If the owner *does* want this one corrected as a goodwill gesture, it is 2 rows (`quick_review_sessions.correct_answers/verified_correct_answers/score_percentage` and `concept_health.last_incorrect_at`) — owner-run, and it should be a conscious, documented exception rather than a side effect of the bug fix.

---

## K. Learner workaround

**What the code and data actually prove, and therefore what is safe to say:**

- The bug is **in the stored answer key**, not in rendering and not in the session. (§B, §C)
- **Retrying Quick Review will not help** — every session reads the same wrong `correctIndex` live from the Study Pack (`QuickReviewSessionService.java:427-429`). The learner already proved this empirically across two sessions.
- **Regenerating the Study Pack is not recommended** and may not help: it re-rolls all five questions non-deterministically through the same generator. It would probably remove *this* question but could produce a different defect, and it discards four correct questions plus correct teaching content. (§E)
- **Regenerating the Note is definitively unnecessary** — the Note and the Study Pack summary both state the percentage-increase rule correctly. (§E)
- The learner's **understanding is correct**: `(65 − 50) / 50 × 100 = 30%`.

**Exact message we can safely send today (before repair).** ⚠️ The repair is **not yet approved** — it is owner decision #1 in §Q — so the draft below promises intent, not a completed or scheduled fix. Do not send a version that states the fix is already done.

> Thanks for reporting this — you were right, and you found a real bug.
>
> For "Calculate the percentage increase when a quantity changes from 50 to 65", the correct answer is **30%** — exactly as the explanation on that question shows. Our stored answer key for that one question was wrong, so Quick Review marked 25% as correct. The explanation you read was correct; the key was not.
>
> A couple of things worth knowing:
> - **Please don't regenerate the note or the study pack** — the note itself is correct, and regenerating would rebuild all five questions unnecessarily. The answer key is what needs correcting.
> - **Retrying the quiz won't change it** in the meantime, so there's nothing you need to do on your side.
> - Your earlier attempt scored 80% when it should have been 100%. That was our error, not yours.
>
> We're correcting this question and adding a check so that a question whose answer key disagrees with its own explanation can't be published again.

**After repair**, append:

> This is now fixed — the question shows 30% as correct. Your next Quick Review on this note will use the corrected answer.

---

## L. Communication recommendation

### **NO_BROAD_ANNOUNCEMENT** — with a **targeted reply to the reporting learner** (which is already covered by §K).

**Blast-radius evidence supporting this:**

- **Exactly one learner has ever encountered any affected question.** 2 sessions, 1 distinct user, on 1 of the 17 affected packs. All other affected artifacts have never been served.
- The 25 exam-pool candidates sit in pools with **empty `served_question_keys`** — never delivered.
- The 29 challenge-bank candidates are on the **owner's own admin/test account**, all `UNANSWERED`.
- **Zero answer-key structural defects across 115,333 questions** (six checks) — there is no systemic breakage to disclose. The 4 MATCHING block-integrity defects are in never-served exam pools and are cosmetic/rendering, not correctness.
- The confirmed defect population is **6 questions**, and the reporting learner is the only person known to have been affected by any of them.

A general announcement would tell thousands of learners that quiz answers may be wrong, on the evidence of one report and six questions with a single known victim — which damages trust far more than it protects it, and is exactly what the spec warns against.

**However — two honest caveats the owner should weigh, and they pull toward "revisit later", not toward announcing now:**

1. The deterministic detector's **recall on prose-choice questions is near zero by prompt design** (§D). "Only 6 confirmed" is a floor. It is **not** evidence that only 6 exist.
2. **Four of the six confirmed defects are in public board-exam engineering material** (Rankine, Darcy-Weisbach, traverse latitude, Rational Method). Nobody has run them yet, but they are exactly the content where a wrong key does real damage to a candidate. Fix these with priority even though no announcement is warranted.

**Revisit the communication decision if** H5 ships and a re-scan with prose coverage surfaces a materially larger confirmed population, or if a second independent learner report arrives.

---

## M. Observability

Minimal, and scoped to the new gate. Reuse the existing generation-failure logging pattern rather than inventing one.

**M1. Structured log on every H4 internal-consistency rejection**, at WARN:

| Field | Value |
|---|---|
| `event` | `quiz_question_integrity_rejected` |
| `reason` | `KEY_EXPLANATION_CONTRADICTION` (enumerated, extensible) |
| `questionFormat` | `MCQ` / … |
| `questionType` | `COMPUTATIONAL` / `CONCEPTUAL` |
| `studyPackId` / `noteId` | artifact IDs |
| `questionIndex` | position in the pack |
| `keyedIndex`, `impliedIndex` | the disagreement, as indexes |
| `model`, `modelTier` | generation context already captured on `study_packs` |
| `retryOutcome` | `RETRIED_OK` / `RETRIED_FAILED` / `OMITTED` / `NOT_RETRIED` |

**M2. Do NOT log:** learner identity, full Note content, source text, raw choice/explanation strings beyond the two disagreeing index values, or secrets. Indexes and IDs are sufficient to re-fetch the artifact under read-only access — the artifact itself is already in the DB and does not need duplicating into logs.

**M3. One counter** — rejections per generation, so a spike in the rate is visible without reading logs. If the rate materially exceeds the measured **1.3%** historical base rate, that is a model-behaviour regression signal.

**M4. Feed §18's structured feedback context** (below) into the same trace, so a future learner report lands next to the generation record.

---

## N. Tests

All new tests belong in the existing backend suite; the generation path is Java-side, so this is mostly `OpenAiLlmStudyPackService` / `QuizItem` / validator coverage. **⚠️ Per CLAUDE.md, a behaviour change with no test that executes it is an unverified change** — every item below must actually run the path it claims to cover.

### N1. Headline regression — the reported scenario, verbatim

```
choices     = ["15%", "30%", "25%", "10%"]
explanation = "...(15 / 50) × 100% = 30%..."
answer      = "C"        // → correctIndex 2 → "25%"
```

**Must be rejected by the internal-consistency validator.** The test must assert that the system **cannot** accept a payload whose canonical answer is `25%` while its explanation concludes `30%`. Add the mirror case (`answer = "B"` → accepted) so the test cannot pass by rejecting everything.

### N2. Validator false-positive guards — **at least as important as N1**

These must all **pass** validation. A validator that rejects valid questions is worse than no validator.

- Explanation legitimately mentions a distractor while explaining why it is wrong (`"a common error is to compute 15/65 = 23%"`).
- Keyed value appears only in `workingSolution`, not in `explanation`.
- LaTeX-formatted key (`$30\%$`) vs plain choice (`30%`) — normalisation must match.
- Thousands separators (`10,000` vs `10000`), currency (`$500` vs `500`), units (`m³/s`), leading `≈`.
- Prose choices where no choice string appears in the explanation at all → **not flagged** (out of detector scope).
- TRUE_FALSE, MULTI_SELECT, MATCHING, IDENTIFICATION, ENUMERATION → **not flagged** (out of scope).
- A question where two choices are substrings of one another (`"5%"` vs `"25%"`) — **substring matching must not produce a false agreement.** This is a real hazard in the current detector and needs an explicit boundary-aware match.

### N3. Answer-identity invariants (shared infrastructure)

- MCQ answer identity survives generation → `QuizItem` → JSONB → API read-back (`A/B/C/D` → `0/1/2/3`, all four).
- `resolveAnswerIndex` boundaries: `"a"` lowercase accepted; `"C"`/`"D"` rejected when `choiceCount == 2`; `choiceCount ∉ {2,4}` rejected; `null`, `""`, `"30%"`, `"2"`, `"E"` all rejected.
- Malformed / absent key rejected rather than defaulted.
- Answer absent from choices (legacy text ladder) — pin current behaviour at `QuizItem.java:475-492`.
- Duplicate choices — pin current behaviour (0 in corpus, but the ladder's exact-match path is order-dependent).

### N4. Ordering / remapping

- `QuizVersionShuffleUtils` remaps `correctIndex` **and** `correctIndices`; TRUE_FALSE exempt; a MATCHING block shares one order.
- Frontend `getDisplayedQuizChoices` — `canonicalIndex` is preserved under shuffle; live session and review screen produce the **same** order for the same question (the `quiz-answer-review.tsx:71-76` regression).
- **Regression guard for the deleted `randomizeChoices`** — or simply delete the function and its test (H2).

### N5. Session / scoring

- Quick Review session snapshot preserves answer identity as `correctIndex` (`QuizSessionStateUtils.serializeQuiz`).
- Result rendering highlights the stored canonical answer.
- Client-reported `correctAnswers` and server-side `verified_correct_answers` agree on the same canonical identity (`QuickReviewSessionService.java:222` vs `QuizSessionReviewUtils:430-462`).
- **Retry-round semantics**: a question answered wrong initially and right on retry counts correct; `selectedChoices` reflects the retry answer. (This audit had to reverse-engineer it from production state — it is untested.)
- Regeneration does not cross-wire old/new questions into an existing session's stored indexes.

### N6. Other modes (shared infrastructure)

Challenge Quiz, Adaptive Practice, Long Exam, Board Exam, Interview Practice and the teacher Generate Quiz path all consume the same `QuizItem` and the same grader. **The validator must be applied at the shared generation boundary, and a test per mode must prove it is reached** — not just a test on the study-pack path. (`v0.116.0`/`v0.117.0` both shipped silent no-ops for exactly this reason.)

### N7. Failure behaviour

- Retry-once-then-omit (or whatever §O settles on) is exercised end to end.
- Quota accounting under a retry.
- A whole-pack generation does **not** fail because one question was rejected.

---

## O. Implementation plan

**⚠️ ROUTING (CLAUDE.md): this is a backend change to the generation pipeline, touching a service, a validator, prompts and tests across more than 3 files — it is a `Codex` item, not inline Claude Code work.** Per the task instruction and CLAUDE.md, **this plan STOPS here. No Codex prompt has been written, and none should be written until the owner approves the scope below.**

Verification tier, per the CLAUDE.md gate: this changes **generated-content semantics** for every quiz mode → **one scoped cold agent framed as falsification**, plus the standard `advisor()` call before the Codex prompt and again on the diff. Not the full three-agent pressure test — this is not a permission substrate, a cross-user read, or a money/quota change.

### Dependency-ordered slices

**Slice 0 — Owner decisions (blocking).** Resolve the open questions in §Q before anything is built.

**Slice 1 — Repair the confirmed artifacts (owner-run SQL, no code).**
Independent of all code work and the only learner-visible urgency. Produce `docs/claude-plans/<date>-quiz-answer-key-repair.sql` with the 12 `correctIndex` updates (§J.1, §J.2), expected row counts, and read-only verification queries. **Owner executes.** Reply to the learner per §K.

**Slice 2 — Internal-consistency validator (Codex).**
Pure function + unit tests first, no wiring. Implements the H4 rule with N2's false-positive guards (especially substring-boundary safety). Deliverable is a tested, unwired validator — it can be reviewed against the 71 known candidates as a fixture set before it can break anything.

**Slice 3 — Wire the validator into the generation boundary (Codex).**
Apply after `resolveAnswerIndex`, before `QuizItem` construction, on the **shared** path so all modes inherit it. Includes the failure-handling behaviour chosen in Slice 0, the M1 structured logging, and per-mode tests proving the gate is reached (N6).

**Slice 4 — Schema + dead-code hardening (Codex, small).**
H1 (`answer` enum in `schema.json`), H2 (delete `randomizeChoices`), H3 (tombstone `QuizQuestionEntity`/`QuizQuestionRepository`; the `quiz_questions` table drop is an owner-run migration).

**Slice 5 — Prompt change H5 (separate, and explicitly gated).**
Relax `developer.txt:105` to require the explanation to state the correct answer's value while still forbidding letter references. **This changes generation behaviour for every study pack** and must ship with a before/after sample review, not bundled silently into Slice 3. Re-run the §D scan afterwards to measure the new recall.

**Slice 6 — Re-scan and close (read-only).**
Re-run the deterministic scan post-H5 to measure the newly-visible population, and re-take the §L communication decision on that evidence.

**Explicitly out of scope for this incident** (§22, §24.6): the single-best-answer / ambiguity gate, the cement/hydraulic-activity incident, any semantic model-based verification, any historical score rewriting, and H6 (answer-text contract) unless the owner elects it in Slice 0.

---

## P. Documentation impact

| Doc | Change | Why |
|---|---|---|
| `docs/features/study-pack-generation.md` | **Required** — document the new internal-consistency gate: what it checks, what it rejects, what happens on rejection, and **explicitly that passing it does not mean semantically verified**. | Behavioural change to generation; per CLAUDE.md a shipped behaviour change owes its feature doc. |
| `docs/features/quick-review.md` | **Only if** the answer-key contract statement changes. **No ConceptHealth change needed** — `:123` is already correct and this audit re-verified it against code. | Anti-drift; avoid re-editing a correct section. |
| `docs/features/<challenge-quiz / adaptive-practice / long-exam / board-exam>.md` | **Required if the gate is shared** (it should be) — each doc that describes generated-question guarantees needs the gate named. | §5 scope is shared infrastructure; per-PR doc updates are exactly where this drifts. |
| `RELEASES.md` | **Required** — bullet under the shipping version, including the Known Limitation that prose-choice recall is near zero pre-H5. | Standing rule. |
| `docs/product/ROADMAP.md` Backlog Index | **Required** — a row for the **follow-up Question Quality / single-best-answer audit** (§24.6), and a row for H6 (answer-text contract) if deferred. Also a row for this findings file itself, per kickoff step 8. | `docs/claude-findings/` files must be indexed; and an un-indexed follow-up is the documented way a real item silently dies. |
| `docs/architecture/ADR-*` | **NOT required.** | This is a validation gate at an existing boundary, not a new architectural axis. It does not change the Note metadata model, the quiz mode contract, or any system boundary. Creating an ADR here would be ceremony. **If H6 (replacing the letter contract with answer text) is adopted, that *would* warrant an ADR** — it changes the canonical answer-identity representation across every mode. |
| `docs/product/EXAM_MODES.md` | **No change.** | Mode hierarchy untouched. |

---

## Q. Owner checkpoint

```
QUICK REVIEW CORRECTNESS INCIDENT

Root cause:
Failure class A — generation inconsistency. The LLM emitted answer letter "C"
(→ correctIndex 2 → "25%") while its own explanation and workingSolution
correctly derived 30% (choices[1]). The prompt asks the model for a positional
LETTER (developer.txt:90) rather than the answer value, and schema.json leaves
"answer" as an unconstrained string; nothing at any of the eight validation
boundaries compares the answer key against the explanation. NoteLib's parser,
persistence, session snapshot, grader and renderer all propagated the wrong key
faithfully — they are index-pure and were verified correct.

First incorrect layer:
The LLM response payload, before resolveAnswerIndex (OpenAiLlmStudyPackService
:1467). First incorrect state observable in our system:
study_packs.quiz[4].correctIndex = 2 on Study Pack 1ccb19f9-… (public source,
generated 2026-09-09) and its copy c8dd3aa2-… .

Underlying Note correct:
YES — note e0811772-… states "Percentage increase/decrease is calculated by
Change/Original × 100%". Correct.

Study Pack teaching content correct:
YES — summary and key_concepts both state the rule correctly. The quiz
explanation and workingSolution are also correct. Exactly one scalar is wrong.

Reported question requires repair:
YES — a single-field fix: quiz[4].correctIndex 2 → 1, in TWO study packs
(1ccb19f9-… public source, c8dd3aa2-… learner copy). Owner-run. No regeneration.

Note + Study Pack regeneration needed:
NO

Study Pack-only regeneration needed:
NO — regeneration would re-roll five questions non-deterministically, discard
correct content, and could reintroduce the same defect class.

Quick Review scoring affected:
YES — session 1e78a11d-… recorded correct_answers 4 / score 80.00% when the
learner's stored selection (index 1 = "30%") was mathematically correct.
verified_correct_answers also 4, because the server re-derivation reads the same
wrong key.

Durable learning evidence affected:
YES. Quick Review DOES write ConceptHealth (QuickReviewSessionService:309/:313;
docs/features/quick-review.md:123 — the "Quick Review does not write
ConceptHealth" doctrine is STALE and the repo already corrected it on 2026-08-12).
concept_health row 9733ae06-… (concept "Percentage change") carries
last_incorrect_at = 2026-09-16T08:54:27Z from that session. Largely self-healed
by the learner's second session (incorrect_streak back to 0, last_correct_at set);
the residual is the historical timestamp and the permanently recorded 80%.

Other quiz modes affected:
SHARED_QUIZ_INFRASTRUCTURE — all four question stores use an identical QuizItem
shape and the same int-vs-int grader, so the generation-pipeline root cause
reaches Quick Review, Challenge Quiz, Adaptive Practice, Long Exam and Board Exam.
MEASURED LEARNER EXPOSURE OUTSIDE QUICK REVIEW IS ZERO: the 25 exam_question_pool
candidates sit in pools with empty served_question_keys (never served); the 29
challenge_quiz_question_bank candidates are all on the owner's own admin/test
account (dee4225c-…) and all UNANSWERED.

Historical sanitation:
DETERMINISTIC_SCAN

Estimated sanitation cost:
ZERO model calls. 115,333 questions across 4 stores scanned in single SQL
statements. 0 ANSWER-KEY structural defects found across six checks (a seventh
check, MATCHING block integrity, DOES find 4 violations in 50 blocks — all in
never-served exam pools; folded into H3b). 71 RAW internal-consistency candidates
(17 study_packs / 25 exam pool / 29 challenge bank) from 5,443 numeric-literal
MCQs (1.3%). Precision 6/6 was measured ONLY on the 6 distinct study_packs-store
defects manually reviewed and independently hand-verified from each question's
own stated inputs (§J.1–J.2) — the 54 exam-pool/challenge-bank candidates are
UNREVIEWED, not assumed clean. ~2 hours one-off human review for the reviewed
subset; reviewing the remaining 54 would add roughly proportional time if the
owner wants that coverage (see OWNER DECISIONS #6). No quota impact.
⚠️ 71 is a MEASURED LOWER BOUND: the detector only covers numeric-literal answers
(4.7% of the corpus), and developer.txt:105 FORBIDS explanations from naming the
answer by text — so prose-question recall is near zero BY PROMPT DESIGN, not by
chance. Do not read 6 confirmed defects as "only 6 exist".

Future prevention:
Smallest robust fix = an internal-consistency gate at the parse boundary:
reject an MCQ whose keyed choice never appears in its explanation/workingSolution
while another choice does (deterministic, 0 model cost, 6/6 precision measured).
Plus H1 (constrain "answer" to an A–D enum in schema.json), H2 (delete the dead
QuizValidationUtils.randomizeChoices, which reorders choices WITHOUT remapping
correctIndex), H3 (tombstone the orphaned quiz_questions table / QuizQuestionEntity
— 0 rows, 0 references, and it misdirected the opening of this audit).
⚠️ HIGHEST-LEVERAGE SINGLE CHANGE IS H5: relax developer.txt:105 so explanations
must state the correct answer's VALUE (while still forbidding letter references,
which are meaningless after the display shuffle). Without it the validator only
ever catches computational questions.
⚠️ STRUCTURAL ANSWER-KEY VALIDATION ADDS NOTHING: 0 violations on six checks
across 115,333 questions. The ONE structural check with real yield is MATCHING
block integrity (4 of 50 blocks bad) — H3b, cheap, unrelated to this incident.
⚠️ PASSING THIS GATE IS NOT SEMANTIC VERIFICATION. It proves the key agrees with
the explanation and nothing else. An explanation that merely rationalises a
wrongly-keyed answer passes cleanly. Do not surface it as a "verified" badge.

Learner workaround:
Tell them they were right: the answer is 30%, our stored key was wrong, and their
80% should have been 100%. Tell them explicitly NOT to regenerate the note or the
study pack (both are correct) and that retrying will not help until we push the
fix — every session reads the same key live from the study pack
(QuickReviewSessionService:427-429). Full message drafted in §K.

Communication:
NO_BROAD_ANNOUNCEMENT — with a targeted reply to the one reporting learner.
Exactly one learner has ever run a session against any affected artifact (2
sessions, 1 pack). All other affected artifacts were never served. Zero structural
defects corpus-wide. Revisit if H5 ships and a re-scan surfaces a materially
larger confirmed population, or if a second independent report arrives.

Implementation routing:
CODEX — backend generation-pipeline change across a service, a validator, prompts
and tests (> 3 files, anti-drift rules apply across them). Per CLAUDE.md and the
task instruction, THIS PLAN STOPS HERE: no Codex prompt has been written and none
should be until the owner approves the scope. Verification tier: one scoped cold
agent framed as FALSIFICATION (generated-content semantics change across all quiz
modes), plus advisor() before the prompt and again on the diff. Slice 1 (the
12-row artifact repair) is owner-run SQL and needs no Codex at all.

OWNER DECISIONS REQUIRED:
1. Approve Slice 1 — the 12 correctIndex repairs (6 questions × 2 packs each).
   Please independently confirm the 5 non-percentage engineering values before
   running; they are public board-exam material and were re-derived from the
   model's own working, which is the artifact under suspicion.
2. On validator rejection, what should happen? (a) retry that one question once,
   (b) omit it and generate N-1, (c) fail the whole pack. Recommendation: (a) then
   (b) — never (c). This also decides quota accounting on the retry.
3. Approve or defer H5 (the developer.txt:105 prompt relaxation). It is the single
   highest-leverage change here but it alters generation behaviour for every study
   pack and should ship as its own slice with a before/after sample review.
4. Approve or defer H6 — replacing the A/B/C/D letter contract with the answer
   TEXT, resolved by exact match against choices. Removes the root-cause
   indirection entirely, but touches every mode's prompt and parser and would
   warrant an ADR. Recommendation: defer, decide separately.
5. Retroactive correction of session 1e78a11d-… (80% → 100%) and concept_health
   9733ae06-… : there is NO existing product contract for this. Recommendation:
   do not rewrite; tell the learner instead. If you want it corrected as a goodwill
   exception, it should be a conscious documented decision, not a side effect.
6. Confirm the 25 exam-pool and 29 challenge-bank candidates are left unrepaired
   AND unreviewed for now (zero learner exposure, independent of the fact that
   their precision hasn't been manually checked — those are two separate
   reasons, either alone would justify deferring) — recommended.

DO NOT IMPLEMENT YET.
```

### Q.1 — Owner decisions, locked 2026-09-21

```
1. APPROVED — the 12-row repair for the 6 confirmed study_packs defects.
   All 5 previously-unverified corrections were independently hand-derived
   from each question's own stated numeric inputs before this approval
   (see the ✅ column in §J.2's table) — not just re-read from the model's
   own suspect prose.

2. LOCKED — "never fail the whole pack." Adopted mechanism: retry the
   rejected question once; if still invalid, OMIT it (generate N-1) rather
   than fail the pack. This was the explicit recommendation offered; the
   owner's answer confirms the non-negotiable tail (never (c)) and does not
   contradict the retry-then-omit chain, so that chain is the decided
   behavior — to be stated explicitly, not re-litigated, when the Codex
   prompt is written.

3. APPROVED — H5 (relax developer.txt:105 so explanations must state the
   answer's VALUE, still forbidding letter references). Ships as its own
   slice with a before/after sample review, per the original recommendation.

4. APPROVED — H6 (replace the A/B/C/D letter contract with answer TEXT,
   resolved by exact match against choices). ⚠️ THIS CHANGES SCOPE: §H6 and
   §P both said this "would warrant an ADR" if adopted, because it replaces
   the canonical answer-identity representation across every quiz mode's
   prompt and parser — not a validation-gate addition like H1–H5, but a
   change to what "the answer" IS at the generation boundary. Consequences
   of this approval, not yet executed:
     - An ADR (or an amendment to whichever ADR governs generation
       contracts, if one already does — recheck at implementation time
       since ADR-001 covers Note metadata axes, not the quiz-question
       schema itself, and this may need a new ADR rather than an amendment)
       must be drafted and ratified BEFORE the Codex prompt is written for
       this piece, consistent with this repo's established Codex-routing
       precedent (architectural changes get an ADR step ahead of the
       implementation prompt, not bundled into it).
     - The legacy answer-index ladder (`QuizItem.java:458-493`) must be
       RETAINED for all existing rows — H6 changes future generation only;
       it does not migrate 115,333 existing questions.
     - H6 and H4/H5 are sequenced, not simultaneous: H4 (the consistency
       gate) ships first against the CURRENT letter contract, because it is
       the incident fix and has known 6/6 precision measured against it. H6
       is a follow-on architecture change that should be scoped and ADR'd
       as its own release, not folded into the incident-fix Codex prompt.
     - **Drafted 2026-09-21:** `docs/architecture/ADR-002-quiz-answer-identity-by-text.md`.
       Status **PROPOSED**, not yet Accepted — the owner approved H6's
       concept in this decision, not this specific text; ratification is a
       separate, deliberate act (change the ADR's Status line when it
       happens). Confirms this is a new ADR, not an ADR-001 amendment
       (different system entirely — quiz answer identity, not Note
       metadata), scopes the change to MCQ/TRUE_FALSE only (explicitly not
       MULTI_SELECT/MATCHING/IDENTIFICATION/ENUMERATION), specifies exact
       verbatim-text-match resolution (no fuzzy matching, burden of
       exactness on the prompt contract not the parser), confirms H4 is
       retained afterward as a distinct semantic-adjacent check, and
       resolved by direct grep that the letter contract lives in six
       independently-maintained prompt files, not a shared include.

5. APPROVED, WITH THE EXPLICIT CONSTRAINT "do not silently rewrite
   history." Decision: YES, retroactively correct session `1e78a11d-…`
   (`correct_answers`/`verified_correct_answers`/`score_percentage`: 4/4/80.00
   → 5/5/100.00) and `concept_health` row `9733ae06-…`
   (`last_incorrect_at`: `2026-09-16T08:54:27Z` → `null`). This is now a
   THIRD repair-SQL item, folded into the same owner-run repair file as the
   12 answer-key corrections — NOT a silent side effect of the bug fix, but
   a named, separately-labeled statement with its own before/after values
   and its own verification query, exactly because the owner's condition
   was that it must not be silent. This does not establish a general
   retroactive-correction policy — it is this incident's own explicit,
   documented exception, and should not be cited as precedent for a future
   incident without a fresh owner decision.

6. "Can't we repair them? If we can, we can still repair them" — decision:
   EXTEND THE REVIEW to the 54 previously-unreviewed candidates (25
   exam_question_pool + 29 challenge_quiz_question_bank), using the same
   independent-hand-verification methodology already applied to the 6
   confirmed study_packs defects (re-derive from each question's own stated
   inputs, not from its own explanation's prose; apply the N2 false-positive
   guards — distractor-explained-away, LaTeX/formatting variance, substring
   collisions like "5%" vs "25%"). Any genuine defect found among the 54 is
   added to the same repair file, regardless of the original zero-exposure
   argument — zero learner exposure was never a reason NOT to fix a
   confirmed defect, only a reason it wasn't URGENT. This review is being
   run as a follow-up pass; results will be appended to this document and
   folded into the repair SQL once complete.
```

---

## R. Appendix — §18 Feedback traceability (small, high-value)

**What is persisted structurally today** (`FeedbackEntity`): `id`, `user_id`, `email`, `message` (text), `page_url` (text), `status`, `created_at`. **That is all.**

**Everything else is embedded in free text or in the URL:**

- Quiz type, context, and Note title are a **newline-prefixed preamble inside `message`**:
  `Feedback type: Quiz Feedback / Quiz: Quick Review / Context: Quiz Results / Note: …`
- Note id and session id exist **only as URL path/query parameters** in `page_url`.
- **The question is not captured at all** — not its index, not its id, not its text.

**Assessment:** this trace only succeeded because `page_url` happened to carry both `noteId` and `sessionId`. Had the learner submitted from a different surface, the report would have been untraceable. And the single most useful field — *which question* — required inferring it from the learner's own prose plus a corpus scan.

**Minimal, materially useful improvements (not the Campaign Feedback feature):**

1. **Persist `note_id` and `session_id` as real nullable columns**, populated from context rather than parsed out of a URL. Removes the dependency on URL shape.
2. **Persist `question_index` (and `question_format`) when feedback is submitted from a Quiz Results context.** The results screen already knows which question is on screen. This is the single highest-value field for quiz-bug diagnosis and it is currently absent.
3. **Persist the `Feedback type` / `Quiz` / `Context` preamble as enum columns** instead of a text prefix — they are already structured, just stringified.

These are additive nullable columns plus a slightly richer submit payload. They should be **scoped separately** from the correctness fix and are **not** required to close this incident.

---

## S. Appendix — Adjacent defects discovered (NOT this incident; do not bundle)

Recorded so they are not lost. **None is the root cause of the reported bug, and none should be fixed as part of this incident's scope without separate owner approval.**

1. **MATCHING results-screen letter mismatch (real, live, untested).**
   `quiz-answer-review.tsx:77` computes summary letters via `getDisplayedQuizChoices`, which exempts only `TRUE_FALSE` (`lib/quiz.ts:350`) — so a `MATCHING` item **is** shuffled for the summary. But the grid below is rendered by `quiz-matching-group.tsx`, which applies **no shuffle** and prints canonical letters (`:92`). Net effect: on the results screen a MATCHING question's "Correct Answer" box can read `C. …` while the option marked ✓ Correct below is labelled `A`. **Text is correct in both places and grading is correct** (everything is `correctIndex`), so this is a display-letter defect only. `quiz-answer-review.test.tsx` has **zero** MATCHING cases. This is the same regression class the `quiz-answer-review.tsx:71-76` comment claims was closed — the fix reached `QuizChoiceList` and never reached the component that never adopted the shuffle.

   **Measured:** 3 singleton MATCHING blocks exist in production today (all in `exam_question_pool`), plus 1 size-6 block whose items do not share a choices array — 4 of 50 blocks (§D). All in never-served pools, so no learner has hit this. Covered as H3b.

2. **`QuizValidationUtils.randomizeChoices` (`:178-183`) reorders choices without remapping `correctIndex`.** Dead code (only caller is its own test), but it would silently corrupt answer identity if ever wired in. Covered as H2.

3. **`QuizQuestionEntity` / `QuizQuestionRepository` / `quiz_questions` are fully orphaned** — 0 rows in production, 0 code references, last written by a one-time backfill in `V20__quiz_questions_and_usage_tracking.sql:42`. Covered as H3. Worth removing on the evidence that it actively misdirected the start of this investigation.

4. **Quick Review scoring is client-reported.** `QuickReviewSessionService.java:222` stores `request.correctAnswers()` verbatim. `verified_correct_answers` is a separate server-side re-derivation, and in this incident both agreed — because both read the same wrong key. Not a defect here, but worth noting that the server-side value is a re-derivation from stored selections rather than an independent authority.

5. **Retry-round semantics are untested.** The `roundSelections` / `retryQuestionIndexes` / `activeQuestionIndexes` interaction had to be reverse-engineered from production state and source during this audit. Covered as N5.

6. **Single-best-answer / ambiguity is not required by any prompt** (§H, spec §24.5). `developer.txt:91` requires only that *"exactly one choice must be correct"*. **Flagged for the follow-up Question Quality audit per §24.6 — explicitly NOT actioned here.**

---

## T. Appendix — §Q.1 item 6 follow-up: extended review of the previously-unreviewed exam-pool/challenge-bank candidates

**Owner decision, 2026-09-21:** *"Can't we repair them? If we can, we can still repair them."* This section extends the §D/§J.2 manual-verification methodology (independently re-derive the correct answer from each question's own stated numeric inputs, never from trusting its own explanation) to the 54 candidates the original 2026-09-19 pass left unreviewed.

### Scope note — the candidate count grew, and here is exactly why

Re-running the exact detector logic against current production data returned **31** `exam_question_pool` candidates and **40** `challenge_quiz_question_bank` candidates (**71**, not 54) — a real increase of 17 over the 2026-09-19 count in these two stores. This is **corpus drift, not a detector discrepancy**: the same normalization (numeric/unit-literal choices, `$`/`,`/whitespace-stripped substring match) was re-run verbatim. New content was generated into these tables between 2026-09-19 and 2026-09-21. **This total is reported as observed, not reconciled against the original 54** — the point of this pass is repairing what exists now, not explaining a two-day-old count.

### Results

| Store | Reviewed | CONFIRMED | FALSE POSITIVE | UNRESOLVED |
|---|---:|---:|---:|---:|
| `exam_question_pool` | 31 | **14** | 14 | 3 |
| `challenge_quiz_question_bank` | 40 | **10** | 29 | 1 |
| **Total** | **71** | **24** | **43** | **4** |

**⚠️ This roughly doubles the incident's confirmed-defect count** — from 6 (§D/§J.2, all `study_packs`) to **30** total. All 24 new confirmed defects were independently re-derived from each question's own stated inputs this pass, the same standard applied to the original 6 (e.g. the culvert/break-even/traverse-latitude corrections earlier this session) — not read off the suspect explanation text alone.

**Exposure, checked for every confirmed defect: ZERO real learner exposure in either store**, consistent with the original audit's finding for these two stores specifically (this does not change §L's communication recommendation, which already accounted for these stores being unserved/test-account-only):
- All 14 `exam_question_pool` confirmed defects: `served_question_keys` is an **empty array** (`served_count: 0`) — never served to any exam session, verified per-pool this pass.
- All 10 `challenge_quiz_question_bank` confirmed defects (and the 1 unresolved item): `user_id = dee4225c-e460-4f89-a6e5-cd43f6dd1972` (the owner's own admin/test account, same account identified in the original audit) and `last_known_outcome = UNANSWERED`.

### T.1 — CONFIRMED defects (24), ready for repair SQL

**`exam_question_pool` (14):**

| Pool id | Question (abbrev.) | Choices | Keyed (wrong) | Corrected index | Independent derivation |
|---|---|---|---|---|---|
| `2437d442-…` | Net cash, indirect method (NI 120k) | `$135,000 / $145,000 / $125,000 / $140,000` | idx2 `$125,000` | **idx0** `$135,000` | `120,000+30,000−10,000−5,000=135,000` |
| `2b01c6df-…` | Basic EPS | `$5.00 / $4.50 / $4.00 / $5.50` | idx2 `$4.00` | **idx1** `$4.50` | `(5,000,000−500,000)/1,000,000=4.50` |
| `2f82877b-…` | Material price variance (std $8, actual $7.50×5,000) | `$2,500 Fav / $2,500 Unfav / $2,500 Neutral / $0` | idx2 `$2,500 Neutral` | **idx0** `$2,500 Favorable` | `(8−7.50)×5,000=2,500`; standard>actual ⇒ favorable |
| `385d69db-…` | Audit risk (IR .70, CR .60, DR .20) | `0.084 / 0.120 / 0.140 / 0.196` | idx1 `0.120` | **idx0** `0.084` | `0.70×0.60×0.20=0.084` |
| `4ef82a80-…` | B-tree leaf nodes (height 4, 100 ptr) | `10000 / 100000 / 1000000 / 10000000` | idx3 `10000000` | **idx2** `1000000` | `100^(4−1)=100^3=1,000,000` |
| `571244ab-…` | Material price variance (std $5, actual $4.80×210) | `$42 fav / $42 unfav / $20 fav / $20 unfav` | idx1 `$42 unfavorable` | **idx0** `$42 favorable` | `(5−4.80)×210=42`; standard>actual ⇒ favorable |
| `69a963fb-…` | Metabolic rate (+20% of 100, direct proportional) | `120 / 80 / 110 / 140 units` | idx1 `80 units` | **idx0** `120 units` | `100×1.20=120` |
| `76cad170-…` | Impairment loss (carrying 1,000,000; recoverable 900,000) | `P100,000 / P150,000 / P50,000 / P200,000` | idx1 `P150,000` | **idx0** `P100,000` | `max(850k,900k)=900k`; `1,000,000−900,000=100,000` |
| `aa48d9eb-…` | PV of $1,000 in 5yr @8% | `$680.58 / $735.03 / $867.30 / $500.00` | idx2 `$867.30` | **idx0** `$680.58` | `1000/(1.08)^5=680.58` |
| `cfda9a7b-…` | Equity-method carrying amount | `$485,000 / $400,000 / $515,000 / $415,000` | idx3 `$415,000` | **idx2** `$515,000` | `400,000+100,000+15,000=515,000` |
| `d2d91c72-…` | Delta-connection phase current (line 30A) | `10 A / 17.32 A / 30 A / 51.96 A` | idx2 `30 A` (the *given* line current, not the derived phase current) | **idx1** `17.32 A` | `I_Ph=I_L/√3=30/1.732=17.32` |
| `d7e8622e-…` | Net cash, indirect method (NI 30k) | `$31,000 / $27,000 / $35,000 / $33,000` | idx1 `$27,000` | **idx0** `$31,000` | `30,000+4,000−2,000−1,000=31,000` |
| `df62fc13-…` | Thermodynamics ΔU (rejects 150kJ, 50kJ done on system) | `+200 / −200 / +100 / −100 kJ` | idx2 `+100 kJ` | **idx3** `−100 kJ` | `ΔU=Q+W_on=−150+50=−100`; explanation's own `(−150)−(−50)=−100` agrees |
| `e1d9b7fc-…` | Net cash, indirect method (NI 50k) | `$45,000 / $65,000 / $55,000 / $35,000` | idx2 `$55,000` | **idx0** `$45,000` | `50,000+5,000−10,000=45,000` |

**`challenge_quiz_question_bank` (10, all bank entry ids, all zero-exposure per above):**

| Bank id | Question (abbrev.) | Choices | Keyed (wrong) | Corrected index | Independent derivation |
|---|---|---|---|---|---|
| `1bb93537-…` | Circular curve length (R=300m, 25°) | `130.8 / 261.8 / 75.4 / 150.0 m` | idx1 `261.8 m` (exactly 2× correct — likely a diameter/radius confusion in the KEY only, not the math) | **idx0** `130.8 m` | `L=Rθ=300×(25π/180)=130.8` |
| `50b57537-…` | Poisson's ratio (axial .002, transverse −.0006) | `0.3 / −0.3 / 0.12 / −0.12` | idx1 `−0.3` | **idx0** `0.3` | `ν=−(−0.0006)/0.002=+0.3`; explanation's own text says *"the **positive** value of 0.3"*, contradicting its own negative key |
| `58f2c1f9-…` | Materials price variance (std $5, actual $6×1,000) | `$1,000 unfav / $1,000 fav / $5,000 unfav / $5,000 fav` | idx1 `$1,000 favorable` | **idx0** `$1,000 unfavorable` | `(6−5)×1,000=1,000`; actual>standard ⇒ unfavorable — explanation's own text says *"unfavorable"* and keys favorable |
| `5915864d-…` | Required bearing capacity (1,800kN / 4m²) | `150 / 300 / 450 / 600 kPa` | idx1 `300 kPa` | **idx2** `450 kPa` | `1800/4=450` |
| `72e0ea1a-…` | Binomial P(X=2), n=10, p=0.05 | `0.0747 / 0.1877 / 0.2639 / 0.4012` | idx2 `0.2639` | **idx0** `0.0747` | `C(10,2)(0.05)²(0.95)^8=45×0.0025×0.6634=0.0747` |
| `818814ac-…` | Joint-venture carrying amount | `$570,000 / $600,000 / $530,000 / $470,000` | idx1 `$600,000` | **idx0** `$570,000` | `500,000+100,000−30,000=570,000` |
| `87e6db69-…` | Liability carrying amount (interest 8%, repay $5,000) | `$81,400 / $79,000 / $86,400 / $75,000` | idx2 `$86,400` | **idx0** `$81,400` | `80,000+6,400−5,000=81,400` |
| `aa684263-…` | GCS total (eye3+verbal4+motor4) | `11 / 7 / 9 / 14` | idx1 `7` | **idx0** `11` | `3+4+4=11` |
| `e1c1110e-…` | Labor efficiency variance (900−850hrs)×$25 | `$1,250 fav / $1,250 unfav / $21,250 fav / $21,250 unfav` | idx3 `$21,250 unfavorable` | **idx1** `$1,250 unfavorable` | `(900−850)×25=1,250`; explanation's own conclusion is `$1,250 unfavorable`, key is off by an inserted digit |
| `e73e4b66-…` | Signal capacity (1,600×30/90) | `533 / 480 / 600 / 720 veh/h` | idx1 `480 veh/h` | **idx0** `533 veh/h` | `1600×30/90=533.3` |

### T.2 — FALSE POSITIVE patterns (43, not itemized individually — grouped by cause)

Every one of these 43 was independently re-derived and confirmed **correctly keyed**; the detector's plain substring match failed to see the agreement for a specific, identifiable reason. This list is valuable input for N2 (validator false-positive guards) beyond what the original 6-item review surfaced:

- **LaTeX inline-math delimiter mismatch** (`\(...\)` or bare `$...$` wrapping the choice but not the same span in the explanation) — the single largest category, ~14 instances (e.g. `5d779f49-…` ×3, `0486e819-…`, `0e4da05f-…`, `13a16326-…`, `2dd8fbbe-…`, `888d4d36-…`, `9dde4864-…`, `a6e8d317-…`, `bdad02cc-…`, `c958d307-…`, `d62d9ef6-…`, `e5dd7e3d-…`).
- **Decimal-rounding precision mismatch** (choice states a rounded value, explanation/workingSolution states an unrounded or differently-rounded intermediate) — ~11 instances (e.g. `38cf9f46-…` ₱13,393 vs ₱13,392.86; `6dd4e4e1-…` 14.3% vs 14.29%; `715e7c89-…`, `80d7d375-…`, `ece9117e-…`×2, `a0a55e05-…`, `a3890c00-…`, `cbc1e87b-…`, `dee01743-…`, `958587e3-…`, `e30be57f-…`).
- **Word-order / phrasing mismatch** ("gain of $500" vs "$500 gain") — ~4 instances (`166d2927-…`, `3da0bdb7-…`, `68805d4b-…` "exceeds" vs "more than", `abc3510b-…`).
- **`\text{}` / unit-wrapper or tilde-spacing breaking adjacency** (`10 \text{ mL}`, `3464~N`) — ~4 instances (`da3bf569-…`, `2c8eea17-…`, `3b2d0bf3-…`, `b8f20dec-…`).
- **Value stated in words, not numerals** ("no tax due" vs "₱0") — 1 instance (`9f494998-…`).
- **General-rule explanation that doesn't restate the specific value** ("must be between 0 and 1" vs "1.2") — 1 instance (`45988e3c-…`).
- **VAT-payable stated as a negative/credit rather than the keyed "0"** — 1 instance (`f624f317-…`).
- **Tilde-prefixed approximation glyph** (`~984 N` vs `984 N`) — 1 instance (`341b1d4a-…`).

**None of these represents a new detector-tuning requirement severe enough to block H4** — every pattern above is exactly the class N2 already anticipated (LaTeX/formatting normalization, rounding, distractor-explained-away). They do, however, argue for a **more thorough normalization pass** in the actual H4 implementation than the ad-hoc regex used for this incident's detection queries — strip LaTeX delimiters (`\(`, `\)`, inline `$`), collapse `\text{...}` wrappers, and tolerate a small numeric-rounding window, or the shipped validator will itself generate a high false-positive/reject rate against **correctly-keyed** questions.

### T.3 — UNRESOLVED (4), flagged for curator review, not classified either way

- **`c916ba22-…`** (exam pool) — "square rotated 90° CW then reflected vertically" orientation question. Composing a rotation with a reflection is not equivalent to a pure rotation in general, and for an unlabeled square the visual "orientation" is arguably indistinguishable across 90° multiples regardless. This is a geometric-reasoning question, not a direct-computation one — outside what I'm confident asserting a single correct answer for without risking the same overconfidence this whole incident is about. **Likely an instance of the §24 ambiguity class** (a question that can be internally consistent while still ill-posed), not a key/explanation contradiction — flag for the follow-up Question Quality audit rather than this incident's repair list.
- **`e2dac890-…`** (exam pool) — federal estate tax question whose own explanation concludes "taxable estate is zero," but **zero is not among the four offered choices** (`$8,000,000 / $2,510,000 / $2,000,000 / $5,490,000`). This is not a simple index-swap: either the question's own choice set is incomplete, or the underlying formula the explanation applies (subtracting the exclusion directly from the taxable estate figure, rather than treating the exclusion as a credit against tax owed) is itself non-standard. Flag for curator/domain review — repairing this by picking the "closest" choice would be exactly the kind of unverified guess this audit is trying to eliminate.
- **`f654d0e6-…`** (exam pool) — inequality-sign-flip question. The choice text and explanation both contain a **literal non-printable control character** (`U+0002`) where an inequality symbol (`≥`/`≤`) should be — a **separate, distinct data-corruption bug** (likely a Unicode escape that broke somewhere in the generation/storage pipeline), not this incident's defect class. I cannot confidently determine the intended symbols from the corrupted text, so I will not guess a correctIndex. **Worth a narrow follow-up of its own**: grep `study_pack_id`/pool/bank JSON columns for `` or other C0 control characters as a distinct, cheap, deterministic corruption scan — unrelated to answer-key correctness but the same "cheap deterministic check, real yield" pattern as this whole incident.
- **`b712ad0d-…`** (challenge bank, owner test account) — petty-cash "cash over" question whose own explanation is **visibly self-contradictory and confused mid-derivation** (it computes $200, calls that "unrealistic," and then states *"for simplicity, the cash over is $20"* with no derivation for that number). This is the same generation-confusion signature as the original incident's Evidence 4 and 5, but here the underlying arithmetic doesn't resolve to any clean value from the given numbers — possibly a genuinely malformed question (given values don't reconcile under the standard petty-cash formula). Zero learner exposure (owner test account, unanswered). Flag for curator review or deletion rather than a key correction.

### T.4 — What this changes and doesn't change

- **Repair scope: 30 confirmed defects total** (6 original + 24 here), not 6. All should go into the same owner-run repair SQL file per §Q.1 item 6's approval.
- **Communication recommendation (§L) is UNCHANGED**: `NO_BROAD_ANNOUNCEMENT` still holds — every new confirmed defect has zero real learner exposure, same as the original 6's exam-pool/challenge-bank candidates already accounted for.
- **Historical sanitation classification (§I) is UNCHANGED**: still `DETERMINISTIC_SCAN` — this section IS that recommendation being executed on the remaining scope, at the cost the original estimate anticipated (zero model calls, a few hours of review), not evidence for escalating to `TARGETED_VERIFICATION` or `FULL_CORPUS`.
- **New input for H4/N2**: the false-positive pattern catalog in T.2 should be read into the actual validator implementation's normalization logic, not just the original 6-item review's narrower pattern set.
- **New, narrow follow-up identified**: a Unicode/control-character corruption scan (T.3, `f654d0e6-…`) — unrelated to this incident's root cause, cheap, deterministic, not actioned here.
