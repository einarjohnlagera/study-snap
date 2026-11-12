# Structured distractor feedback for Study Pack quizzes: scoped proposal

**Status: PROPOSAL for Product UX review, 2026-11-09. Planning only: nothing implemented, no prompt or schema changed, no paid call, no production write, nothing committed.** Written by Feature Planner against the handoff `docs/claude-plans/2026-11-06-structured-distractor-feedback-feature-planner-handoff.md` (Option C, owner-approved 2026-11-06, a future release separate from `v0.179.0`). Code read from `origin/main` `cd6bd183` (read with `git show`, no branch switched); production reads were read-only aggregates.

**Summary.** The smallest safe version is **forward-only and narrow**: new Study Packs get one short rationale for each wrong choice on **plain single-answer MCQ items only** (about 80% of items), stored beside `explanation`, resolved by **verbatim choice text at parse time** (ADR-002's discipline) and stored **index-aligned**. The learner sees **only the selected option's rationale after a wrong answer, in Quick Review and answer review**. Everything else (Flashcards, Memorization, public pages, share links, exports, other modes) is unchanged. It ships behind a switch **OFF** and is enabled only after a small owner-run blind evaluation passes. Two things need the owner's attention (section 8): old and Official packs will not gain rationales without a separate backfill decision, and generation cost rises about 20%.

---

## 1. Verified facts, and corrections to the handoff

- **A Study Pack quiz is exactly 5 items** (`schema.json`: `quiz` `minItems` and `maxItems` 5; production average 5.00 items, 184 packs since 2026-10-19). Fields are `required` with `additionalProperties: false`; nullable fields use `["type","null"]`.
- **Format mix of the 2,000 most recent quiz items** (400 packs): plain MCQ 1,593 (79.7%), assertion-style "Statement 1 / Statement 2" MCQ 325 (16.3%), multi-select 55 (2.8%), numeric-choice MCQ 19 (1.0%), True/False 8 (0.4%). No identification, enumeration or matching items appear in Study Pack quizzes. So **about 4 of 5 items per pack are eligible** under the narrow scope.
- **Correction: `ShareService.copyQuizItem` is no longer field by field.** It is `item.withSourceStudyPackId(null)` (`ShareService.java:226-228`), a copy constructor; the handoff's claim is stale. What **does** drop a new field is the private canonical constructor, the `@JsonCreator` constructor, `fromStoredComponents`, the three `QuizItem` copy constructors (`QuizItem.java:437-492`), `QuizSessionStateUtils` serialization and parse (explicit key-by-key, `:984-996`, `:736-774`), `QuizVersionShuffleUtils`, and the stubs and mocks (`StubLlmStudyPackService`, `MockQuizGenerationUtils`, the Challenge candidate at `OpenAiLlmStudyPackService.java:2865`).
- **The reveal path is server-side and already unredacted.** `POST /quick-review/{sessionId}/answer` returns `QuickReviewAnswerResponse(questionIndex, QuizItem question)`, the full item with key and explanation, built after the answer is recorded (`QuickReviewSessionService.java:279-313`). The server knows the selected index there, so it can return **only the selected option's rationale**. In-progress, pause and resume responses use `QuizItem.withoutAnswerKey()` (`QuizItem.java:437-444`), which nulls `explanation`; **the new field must be nulled there too.**
- **Quick Review is the only mode that serves the saved Study Pack quiz as questions.** Challenge, Adaptive, Long Exam and the exam pool read it only as an exclusion list, and Combined Quiz copies from a generated quiz. So a Study Pack rationale surfaces only in Quick Review (and the review screens) by construction.
- **The model's own letter and position claims have failed before** (ADR-002: the Darcy-Weisbach incident, 115,333 questions, `resolveAnswerIndex` faithfully converting a wrong letter). Production is still **index-only** (ADR-002's H6 is not implemented).

## 2. Data shape and generation

**At the model boundary (new nullable field on each quiz item):** `distractorRationales`, either `null` or an array of exactly 3 objects `{ "choice": "<exact text of one incorrect choice>", "rationale": "<one sentence>" }`. This follows ADR-002: the association is carried by **verbatim text**, never by a letter or position the model must recall.

**At parse time (deterministic, free):**
1. Resolve each `choice` by exact match against the item's `choices` (no fuzzy matching). No match, or more than one match, rejects the rationales.
2. The three resolved choices must be **exactly the three choices other than the keyed correct one**. A rationale for the keyed choice, or a missing one, rejects the rationales. This is a **free cross-check on the answer key**: a rationale written for the keyed-correct choice is the same reason-right, index-wrong pattern R2 measured on templates. **Phase 1 only logs it, under its own rejection reason counted separately from format rejections** (no retry, omission or flag is shown to anyone); the evaluation reports its rate and its precision against the blind scorers' wrong-key verdicts. Whether to act on it (retry or omit, as the H4 consistency validator does) is a later owner decision.
3. Reject any rationale containing a choice letter reference (`option B`, `(C)`, `choice A`) or longer than a length cap (proposed 200 characters, about 25 words), or with unpaired `$` delimiters (the open defect at `docs/features/quiz.md:46`).
4. **On any rejection, drop the rationales for that item and keep the question** (it degrades to today's experience); count and log the rejection, no question text.

**Stored shape:** `QuizItem.distractorRationales` as a `List<String>` **aligned to the stored `choices` order**, `null` at the keyed index and for any unresolved entry; the whole field is `null` when the item is ineligible or rejected. The frontend already carries a `canonicalIndex` per displayed choice, so it reads `distractorRationales[canonicalIndex]` regardless of shuffle.

**Eligibility reuses the existing detectors, no new regex:** assertion-style is `QuizValidationUtils.isStatementPair`; numeric choices use the existing test behind the numeric-explanation rule (`QuizValidationUtils.isNumericUnitLiteral`, at most 20 characters with a digit; it is `private` today, so it needs a visibility change). The parser enforces eligibility **in both directions**: it drops rationales the model emits for an ineligible item, and counts eligible items where the model emitted `null`. **Schema:** the nullable array of objects needs the `anyOf` pattern `correctIndices` already uses, and the nested object needs its own `additionalProperties: false` and `required` list.

**Which generation paths:** the Study Pack quiz only (`generateStudyPack`, `developer.txt`, `schema.json`). The other modes generate their own items with separate prompts and schemas and are **out of this slice** (section 7). **Eligible items:** standard single-answer MCQ with four non-numeric choices that is not assertion-style. **Excluded:** True/False (the explanation already addresses it, 0.4% of items), multi-select, assertion-style (the explanation already states each statement's verdict), and numeric or unit choices (a rationale for a wrong numeric value restates the value, which conflicts with the existing numeric-explanation rule and its consistency gate).

**Prompt change (owner sign-off of the exact wording required first):** one new rule block in `developer.txt` for eligible items: three rationales, one per incorrect choice, each one short sentence saying why a learner might choose it and why it is wrong; `choice` copied verbatim; no letters; do not restate the correct answer; `null` for every other item. The existing explanation rule stays exactly as is (the explanation explains the correct answer and never discusses other choices), so this does not reopen the letter-leak defect.

## 3. Shuffle safety

Stored order is the stable key and rationales are stored in that order, so **no shuffle can mis-associate them**: the live quiz, answer review and export shuffles all keep a map back to the stored index (`getDisplayedQuizChoices` / `buildDeterministicChoiceOrder`, `canonicalIndex`; `QuizVersionShuffleUtils`). Phase 1 never renders rationales in an export. A deterministic test pins that, for every one of several shuffle seeds, the rationale shown for the selected displayed choice is the one stored for that choice's text.

## 4. Protecting explanation quality

| Risk | Control |
|---|---|
| Wrong rationale, invented reasons | Owner-run blind evaluation before enabling (section 6); the generator sees the summary and key concepts only, so rationales are judged against the source note |
| A distractor is arguably correct, so its rationale contradicts the key | Counted as a separate **defensible-alternative rate** and treated as a content defect (the BSIS review standard); the parse cross-check (step 2) also catches key-versus-rationale disagreement |
| Answer leak by elimination | Rationales are **not delivered before an answer** (section 5); the answer response returns only the selected option's rationale |
| Letter or text leakage | Schema-carried association plus the parse-time letter ban; no prose reliance |
| Math delimiters | Parse-time `$` pairing check |
| Tone | Short, calm, no scolding, under `docs/product/VOICE_AND_TONE.md`; copy for the label is below |
| Numeric and format fit | Excluded by design (section 2) |

## 5. UI behaviour and delivery

**Delivery rule (default-deny).** A rationale reaches a client only in two places: (a) the **Quick Review answer response**, as a single new field `selectedOptionRationale` (the selected option's text), with `distractorRationales` nulled in that copy; and (b) the **session review response**, which today carries the **whole** `List<QuizItem> quiz` plus `selectedChoices` (`QuizSessionReviewResponse`). Its mapper must **compute one rationale per wrongly answered question from the recorded selection** into a new map (for example `selectedOptionRationales`, keyed by question index) and **strip the array from every `QuizItem` in that response**; without that, Codex would pass the full list through and the posture would become "selected only, live" but "everything, on review". The review endpoints take the authenticated learner's id (`NoteController.java:556`, `:654`); I found no supporter or teacher path to another learner's review, but that should be re-verified when the slice is cut. Every other response that carries a `List<QuizItem>` must strip the field: note detail (`StudyPackResponse`), `PublicNoteDetailResponse`, `SharedStudyPackResponse`, share links, public mini preview, the teacher preview and exports, plus `withoutAnswerKey()` for in-progress state. Because `QuizItem` is persisted to JSONB by the same serializer, the field cannot simply be made serialization-ignored; **stripping happens at the response mappers, guarded by a reflection test that lists every response type containing a `QuizItem`** so a future endpoint cannot leak it by default.

| Surface | Change |
|---|---|
| Quick Review live (`quick-review/page.tsx:1583-1586`) | After a **wrong** answer, show the selected option's rationale under the existing explanation. After a correct answer: unchanged. Absent rationale: unchanged. |
| Answer review (`quiz-answer-review.tsx:436`, has `selectedChoices` and `canonicalIndex`) | Same rule, per question the learner got wrong. It is shared with Adaptive and Challenge, which carry no rationales, so they are unchanged. |
| `practice-quiz-card.tsx` (shows every explanation, no selection, mounted on anonymous `/p/[token]`), `public-mini-quiz-preview.tsx`, teacher preview | **No rationale.** No learner selection exists and these are public. |
| Flashcards, Memorization, study tips, exports, concept links | **Unchanged**, still `explanation` only. |

**Proposed label (for Product UX and the owner under `VOICE_AND_TONE.md`, sentence case):** "Why that choice doesn't fit". Packs without rationales render exactly as today.

## 6. Cost and evaluation (measured baseline, estimated delta, owner-run measurement)

- **Measured baseline** (production, `study_packs` token columns, since 2026-10-19): Sol 179 packs, mean **3,910 input and 1,564 output** tokens, 5.0 items; Luna 5 packs, 4,327 input and 2,110 output. `estimated_cost` is not populated, so dollars come from the v0.174.0 benchmark: **about $0.0225 per Sol Study Pack and $0.0013 per Luna pack**.
- **Estimate (not a measurement):** about 4 eligible items times 3 rationales is about 12 sentences, roughly 25 to 30 tokens each, so **about +300 to +360 output tokens, +19% to +23% on Sol (+14% to +17% on Luna)**, plus about 150 input tokens of instructions. If output tokens dominate cost, that is about **+$0.003 to +$0.005 per Sol pack and +$0.0002 per Luna pack**. The curation cost alert's default threshold is **$10 per month** (`application.yaml:620`, env-overridable; the production value is unread). Recent ADMIN volume is 179 Sol packs, about $4 at benchmark price, so +20% adds under $1 and does not materially move the alert unless curation volume rises.
- **Measurement to run before enabling (owner-run paid, small):** 20 Official notes, one pack each, current versus proposed prompt on Sol and Luna (80 generations, about **$1, cap $2**). It measures real token deltas, the **parse-validity rate**, and supplies about 80 eligible items and about 240 rationales per model for scoring. The alternate-prompt arm needs benchmark-harness support for Study Pack generation: test-scope work for Release Implementor (PR #1536 covers only Challenge).
- **Scoring:** blind, Codex scores all (D11), an Opus cold sample of 25% plus every flag, against the source notes. **Provisional gates:** zero rationales that state a distractor is correct or contradict the key or source (critical, listed individually); at least 95% of eligible items receive valid rationales (otherwise retries raise cost); defensible-alternative rate reported and not hidden; median length at most 25 words; 100% correct association under shuffle (deterministic test, not scored). Per-model verdicts, an inconclusive result is not a pass.

## 7. Backward compatibility and rollout

- **Forward-only.** Packs without the field render exactly as today; no migration, no automatic regeneration, no backfill. Older stored JSON and older sessions deserialize (the DTO already ignores unknown properties; confirm every reconstruction site in section 1, and sessions started before rollout degrade silently).
- **Switch.** Prompt, schema and parsing ship behind a config switch **default OFF**; enabling is an owner action after the evaluation passes and the exact wording is signed off. Rolling back is switching it off; already stored rationales are harmless (the response mappers still strip them wherever they are not wanted).
- **Slices (two PRs, backend first):** (S1) generation, parsing and validators, storage, constructors and copy paths, the response stripping plus its reflection guard, switch OFF; (S2) the answer-response field, the review response, and the two UI surfaces. The evaluation runs on S1 before anything is enabled.
- **Later, not now:** rationales for Challenge, Adaptive, Board and Long Exam (each has its own prompt, schema and answer-key redaction); rationales in exports and public pages; a backfill. Each is its own proposal.

## 8. What needs a decision (only the meaningful tradeoffs)

1. **Reach is narrower than "new packs benefit first".** Adopting a public note creates a **new `study_packs` row with a cloned quiz** (`NoteService.java:444-465`: `new ArrayList<>(sourceStudyPack.getQuiz())`). So regenerating an **Official** pack with rationales reaches **future adopters only**; every learner who already adopted holds their own copy, and regenerating that needs **that learner's own confirmation** (the versioning rule). A backfill of Official packs would not reach current learners. It matters: in the last 60 days **191 of 318 Quick Review sessions (60%, 16 learners) ran on adopted-copy packs** and 127 (40%, 7 learners) on non-copy packs (read-only, `notes.copied_from_note_id`). Rationales therefore reach learners through (a) packs they generate themselves after rollout and (b) Official packs regenerated **before** they are adopted. Deciding backfill is an owner-approved paid action; I recommend deciding it **after** the evaluation, with measured cost per pack, knowing it cannot reach existing adopters.
2. **Format scope.** Recommend the narrow scope (plain single-answer MCQ, about 80% of items) over covering assertion-style items. About 1 in 5 wrong answers will show no rationale, which is correct behaviour but worth Product UX knowing.
3. **Cost.** About +20% output tokens per Study Pack, an estimate to be measured; it touches generation cost, so the owner should see the measured figure before enabling.
4. **Prompt and schema wording sign-off** (owner), as with R1.

## 9. Size and verification tier

- **Size:** medium. Backend about 10 to 12 files plus tests (`QuizItem` and its constructors, `QuizSessionStateUtils`, `schema.json`, `developer.txt`, the parse and validator code, stubs and mocks, response mappers, `QuickReviewAnswerResponse`); frontend about 4 (`lib/api.ts` type, the Quick Review page, `quiz-answer-review.tsx`, tests). Two PRs.
- **Recommended verification tier:** **one scoped cold falsification agent** (triggers: an answer-key and public-exposure boundary moves, and the LLM output schema changes), plus a `MockMvc` real-request test of the answer and review responses and the reflection guard against leaking a `QuizItem` field. Not the full pressure test (no permission substrate, no cross-user read, no money or quota semantics beyond the cost note).

## 10. Proposed Backlog Index row (Release Implementor is the sole writer; not added by me)

`Structured distractor feedback for Study Pack quizzes (Option C)` | Evidence: this file, the handoff `docs/claude-plans/2026-11-06-structured-distractor-feedback-feature-planner-handoff.md`, decision `D-43` | Status: proposal returned to Product UX 2026-11-09; owner approved the direction 2026-11-06; **no implementation authorized** | Gate: Product UX review of this proposal, the owner's wording sign-off, a passing owner-run evaluation, and a release slot after `v0.179.0` | Last reviewed 2026-11-09. The handoff file and the `bsis-year1` folder still lack index rows.

## 11. Questions for Product UX

1. Is the narrow format scope acceptable, given about 1 in 5 items will show no rationale?
2. Is "Why that choice doesn't fit" the right label and tone, shown only after a wrong answer?
3. Is forward-only acceptable until a backfill decision, knowing adopted Official packs will not show rationales for now?
4. Is delivering only the selected option's rationale (and nothing on public pages, exports or flashcards) the right privacy posture?
