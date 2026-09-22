# ADR-002 — Quiz answer identity is generated as the answer's text, resolved by exact match; never a letter position

**Status:** **PROPOSED** — drafted 2026-09-21, pending owner ratification. Not yet Accepted. Do not implement against this ADR until its status changes.
**Deciders:** Owner
**Second ADR in this repo.** Deliberately separate from `ADR-001-canonical-knowledge-architecture.md` rather than an amendment to it: ADR-001 governs the Note metadata axes (Subject, Domain Context, Note Learner Level, Course/Program, and Program Family as an authoring convenience over Course/Program). This decision governs a different system entirely — how a generated quiz question's *correct answer* is represented, from the LLM boundary through to grading. The two systems share no field, no table, and no resolver in common; folding this into ADR-001 would blur two unrelated boundaries the way `notes.course_program` originally blurred five.
**Motivating incident:** `docs/claude-findings/2026-09-19-quick-review-percentage-increase-correctness-incident.md` (owner decisions locked 2026-09-21, §Q.1 item 4 — "Approved" — this ADR is the required prerequisite named there before H6 may be implemented).
**H4 ships independently and is not gated on this ADR.** H4 (the incident's actual fix — an answer↔explanation internal-consistency validator, §H of the incident doc) ships first, against the *current* letter contract, whether or not this ADR is ever ratified. Conversely, **this ADR is not a prerequisite for closing the incident** — it is a separate, later, architectural change this ADR alone governs.

---

### Context

A production learner correctness report traced to a single confirmed root cause, independently re-derived and cross-checked against 71 candidate questions across all four question stores during the incident audit (**30 confirmed genuine defects** — corrected 2026-09-22 from an originally-claimed 31; one "duplicate defect" in pool `2437d442` was found on live re-read, while the owner was running the repair SQL, to be a different, already-correctly-keyed question with a different choices array, not a duplicate — all detail in the incident doc): **the model is asked to derive a correct answer, then separately asked to name which positional letter (`A`/`B`/`C`/`D`) that answer occupies, and nothing anywhere checks that the second step agrees with the first.**

Current mechanism, cited exactly so this ADR can be checked against code rather than trusted on its own authority:

- `backend/src/main/resources/prompts/study-pack-v1/developer.txt:90-91` — *"standard MCQ answers must be exactly one of: A, B, C, D"* / *"exactly one choice must be correct."* The model must hold the choice array in working memory and emit a **positional letter**, not the value it computed.
- `backend/src/main/resources/prompts/study-pack-v1/schema.json` — `"answer": {"type": ["string", "null"]}`, no `enum`, no `pattern`. The letter contract is enforced only in Java, not at the structured-output boundary.
- `OpenAiLlmStudyPackService.java:1467-1492` (`resolveAnswerIndex`) — a deterministic `switch` mapping `A/B/C/D` → `0/1/2/3`, hard-failing generation on anything else. This function cannot mis-map; it can only faithfully convert whatever letter the model chose, correct or not.
- `OpenAiLlmStudyPackService.java:502-518` — `legacyAnswer` (the free-text answer form) is explicitly persisted as `null` on every current write path. Production is **index-only** end to end today; no code currently exercises the text-matching fallback described below.
- `QuizItem.java:458-493` (`resolveCorrectIndex`) — the read-side precedence ladder is `correctIndex > answerIndex > correctAnswerIndex > correctIndices[0] > (legacy) exact-text match against choices > (legacy) letter`. **The exact-text-match rung already exists and is already exercised on historical rows** (`sanitizeChoiceText` comparison, `QuizItem.java` legacy path) — it is not new mechanism this ADR would introduce, only a rung this ADR promotes from a legacy fallback to the primary generation contract.

**The clearest evidence for the failure mode**, from the incident audit's independent hand-verification of the Darcy-Weisbach head-loss question (`docs/claude-findings/2026-09-19-…md`, §J.2): the model's own explanation stated, in its own words, *"The correct answer is 4.59 m, which is option C"* — and then emitted `answer: "A"` in the structured response, which the deterministic parser correctly converted to `correctIndex = 0` (`"9.18 m"`, exactly double the right value). **The model identified the correct choice, named its own letter correctly in prose, and still emitted the wrong letter in the field that is actually read.** This is not a computation error and not a parsing error — every downstream layer in NoteLib (parse, persist, session-snapshot, grade, render) is index-pure and was verified correct in the audit. The defect is the indirection step itself: *derive a value → recall which position holds it → name that position*, an extra, unnecessary, unchecked translation between what the model knows and what NoteLib stores.

**Scale, for calibrating how much this decision matters.** All 115,333 production questions across `study_packs.quiz`, `exam_question_pool.questions`, `challenge_quiz_question_bank.question`, and `generated_quizzes.questions` share one `QuizItem` JSON shape and one grader. Zero structural (index-out-of-range, duplicate-choice, etc.) defects exist across that corpus — the letter-mapping failure is invisible to structural validation by construction, because a wrong-but-in-range letter is a perfectly valid `QuizItem`. The incident's own H4 validator (deterministic answer↔explanation consistency check, shipping independently of this ADR) catches a measured subset — 30 confirmed defects at 1.3% flag rate among numeric-literal-answer questions specifically, with near-zero recall on prose answers **by prompt design**, because `developer.txt:105` currently forbids explanations from naming the answer. H4 is a detector bolted onto an architecture that still produces the defect; this ADR is the alternative — remove the architecture's capacity to produce it.

### Decision

**A single-correct-answer question's answer key is generated, transmitted, and initially resolved as the answer's literal text — copied verbatim from one of the `choices` entries — never as a letter or position the model must separately recall.**

```
"correctAnswerText": "<exact string, character-for-character identical to one
                       of the entries in this question's own choices array>"
```

replaces

```
"answer": "A" | "B" | "C" | "D"
```

at the LLM structured-output boundary, for every question whose correctness is expressed as a single `correctIndex` over a `choices` list — **that is MCQ and TRUE_FALSE only.** (Rationale for that exact boundary, and what is explicitly out of scope, below.)

**Resolution is exact match, not fuzzy match — the same no-fuzzy-matching discipline this repo already applies to the incident's V147-equivalent migration work.** The parser locates the `choices` entry that is byte-identical (after only the normalization already applied to every choice via `QuizValidationUtils.sanitizeChoiceText`/`normalizeChoiceTexts` — no new normalization surface) to `correctAnswerText`, and that entry's position becomes `correctIndex`. Two failure shapes, both structural and both deterministic:

1. **No choice matches.** The model failed to copy verbatim (paraphrased, reformatted, or picked a value not among the choices).
2. **More than one choice matches.** The choices contain a duplicate value (already a zero-occurrence case across the live corpus, per the incident audit's structural scan, but a real possibility for future content).

Both are **rejections at the parse boundary**, following the exact retry/failure chain the owner already locked for the incident fix (§Q.1 item 2 of the incident doc): **retry the question once; if still invalid, omit it (generate N−1); never fail the whole pack.**

**Why this closes the failure mode structurally, not just detects it more often.** A letter is a claim about position that can silently disagree with the value the model actually computed — that disagreement is exactly what happened in every one of the 30 confirmed defects. A verbatim-text answer **is** the value; there is no second translation step in which the two can drift apart. The failure mode this ADR removes is narrowly "the model correctly derived X and incorrectly reported that X is at position P" — it cannot occur if the model is never asked to report a position at all.

**What this decision explicitly does NOT claim, and must not be sold as claiming (carried forward from the incident doc's own Tier 3 discipline, §H and §24):** this is a **structural** fix to a **representation** defect. It does not, and cannot, prevent the model from confidently deriving the wrong value in the first place and then correctly copying that wrong value's text. A self-consistent-but-substantively-wrong answer (bad arithmetic, a mis-stated fact, an ambiguous stem with a defensible alternative) is a **semantic correctness** question, untouched by this ADR, and remains the responsibility of H4 (retained, see Consequences) and the future single-best-answer/ambiguity quality gate the incident doc deferred to a follow-up audit. Do not close that follow-up audit on the strength of this ADR shipping.

### Scope — MCQ and TRUE_FALSE only; everything else is explicitly out

| Question type | Answer representation | In scope? |
|---|---|---|
| MCQ | `correctIndex` over `choices` | **YES** — this is the incident's defect class |
| TRUE_FALSE | `correctIndex` over a 2-entry `choices` | **YES** — same representation, same failure mode, no reason to carve out an exception |
| MULTI_SELECT | `correctIndices` (plural) over `choices` | **NO.** A text contract here needs set-matching semantics (a list of verbatim strings resolved against the choices list, with its own duplicate/subset/ordering questions) rather than the single-value lookup this ADR specifies — a materially different design, not a trivial extension of it. The audit found zero defects of this failure class to justify designing that extension now: 0 violations across 115,333 questions on the specific check for `correctIndex` disagreeing with `correctIndices[0]`. **Deferred to a future, separately-ratified ADR amendment if evidence ever justifies it — not decided here, and not silently folded in.** |
| MATCHING | its own block structure | **NO.** Unrelated representation; the incident audit's only MATCHING finding (block-integrity, H3b) is a different defect class already scoped to the incident fix, not this ADR. |
| IDENTIFICATION / ENUMERATION | `acceptableAnswers` / `acceptableAnswerGroups`, text-based already | **NO.** These types have no positional answer key today — there is no letter-indirection failure mode for this ADR to remove. |

### Consequences

**Prompt changes, six separate files — confirmed by direct grep this pass, not assumed.** The `A, B, C, D` / *"exactly one of"* letter instruction is **not** centralized in a shared include; it is independently repeated, file by file, in `backend/src/main/resources/prompts/study-pack-v1/`:
`developer.txt`, `challenge-quiz-developer.txt`, `adaptive-practice-developer.txt`, `long-exam-developer.txt`, `teacher-quiz-developer.txt`, and `interview-practice-developer.txt`. **`board-exam-developer.txt` does not repeat that exact instruction line** — it carries only the sibling "do not reference choices by letter in the explanation" rule (the same rule H5 relaxes) — implying Board Exam may inherit the letter contract from `schema.json` alone rather than restating it; this must be confirmed, not assumed, when implementation begins, since an inherited contract updates for free while a restated one does not. **Every file above must instruct the model to emit the verbatim choice text,** with an explicit **"copy this string character-for-character from the choices array above; do not reformat, round, or restate it"** instruction — the burden of exactness is placed on the generation contract, not on a fuzzy resolver trying to guess equivalence after the fact. This is a deliberate, load-bearing design choice: the incident audit's own extended review (§T.2 of the incident doc) catalogued a wide range of *legitimate* formatting variance in generated text — LaTeX delimiter placement, decimal rounding, thousands-separator style, word-order phrasing — every one of which would become a **resolution failure** under this ADR if the model is allowed to restate the answer in its own words rather than copy it. **A resolver that tries to normalize away that variance is the wrong fix and reintroduces exactly the brittle pseudo-matching this repo's own incident audit explicitly warned against** ("we must NOT build a brittle pseudo-math parser that rejects valid questions incorrectly," incident doc §8/§22). The contract must make exactness cheap for the model (copy, don't recompute) rather than making equivalence hard for the parser to judge.

**Parser change, one function.** `resolveAnswerIndex` is replaced by an exact-match lookup against `choices`, reusing (not reinventing) the comparison already implemented for the legacy text-matching rung in `QuizItem.java`'s `resolveCorrectIndex`. This is a narrowing of an existing, already-exercised code path to a primary role, not new mechanism.

**The legacy precedence ladder is retained in full, unchanged, forever (or until a separate, explicit deprecation decision).** This ADR governs **future generation only**. It does not migrate, re-key, or touch any of the 115,333 existing questions — all of which are `correctIndex`-authoritative already and unaffected by a change to how *new* generation resolves its answer. `QuizItem.java:458-493`'s full ladder (`correctIndex > answerIndex > correctAnswerIndex > correctIndices[0] > exact-text > letter`) must not be pruned; new generation simply becomes the first rung in the ladder that legacy rows never populate.

**H4 (the incident's own fix) is retained after this ADR ships — not superseded, not made redundant.** H4 catches a materially different failure: a model that copies its OWN wrong choice verbatim (a real semantic error, self-consistently keyed) while its explanation states a different value. That is exactly the class this ADR does not and cannot address (see "What this decision explicitly does NOT claim" above). Removing H4 on the theory that this ADR "already fixed the incident" would reopen exactly the semantic-vs-structural conflation the incident doc spent an entire section (§24) warning against.

**Failure-rate risk, named rather than assumed away.** Requiring verbatim copy-fidelity is a new, stricter demand on the model than "pick a letter," and the retry-then-omit chain (§Q.1 item 2) means a higher rejection rate here translates directly into more omitted questions per generation — a cost, not a defect, but one that should be **measured on a real sample before this ships broadly**, not assumed benign. Implementation must include a before/after comparison of rejection/omission rates on a representative generation sample, the same discipline already required for H5's prompt relaxation.

**No migration, no backfill, no new column.** `study_packs.quiz`, `exam_question_pool.questions`, `challenge_quiz_question_bank.question`, and `generated_quizzes.questions` are all JSONB; `correctIndex` remains the stored, graded, rendered field for every row, old and new alike. Only the **generation-time resolution path** changes.

### Rejected alternatives, on the record

- **Constrain `answer` to an `A`–`D` enum in `schema.json` (H1) as a substitute for this ADR.** Already approved and scoped as part of the incident fix, independent of this decision — but it is explicitly **not** a substitute: an enum only guarantees the model picks *a* valid letter, not the *correct* one. It closes a schema/Java divergence, not the root cause. Ships regardless of this ADR's fate.
- **Ask for both a letter and the text, and cross-check them at parse time.** Rejected: this is H4's internal-consistency-check pattern applied to the key/text pair instead of the key/explanation pair, and it inherits the same weakness — it is a detector bolted onto an architecture still capable of producing the disagreement, not a removal of the disagreement's cause. It also doubles the model's output surface for no structural gain over asking for the text alone.
- **Fuzzy/normalized text matching in the resolver**, to tolerate the model restating rather than copying the answer. Rejected for the reason stated in Consequences above: the incident audit's own false-positive catalogue (§T.2) is a direct demonstration of how much legitimate formatting variance a "smart" matcher would have to correctly ignore, and getting that wrong in either direction either rejects valid questions or accepts invalid ones — the two failure modes this whole incident exists to avoid trading between.

### Sequencing

1. **H4 ships first, independently, against the current letter contract.** Not gated on this ADR.
2. **This ADR is ratified or rejected by the owner.**
3. **If ratified:** prompt + parser changes scoped as their own Codex slice, per the incident doc's routing note (§O), with a before/after sample review per Consequences above, and `advisor()` called before the prompt is written and again on the diff, per standing CLAUDE.md process.
4. **Re-run the incident's deterministic detector post-ship** (the same scan used throughout the incident, per its own §I recommendation for H5) to confirm the letter-mapping defect class stops appearing in newly-generated content.

### Open questions, not decided here

- Whether MULTI_SELECT should eventually receive an equivalent text-based contract — deferred, no evidence collected either way in this pass.
- Whether `board-exam-developer.txt` inherits its letter contract from `schema.json` alone (confirmed absent from that file directly, per the grep above) — resolve before writing the implementation prompt, since it determines whether Board Exam needs its own edit or updates automatically once `schema.json` changes.
