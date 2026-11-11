Status: D2 check, 2026-11-06. **Read-only: production was read with `SELECT` only; no SQL file was prepared, nothing was written, no Study Pack was touched, nothing is committed.** Answers the owner's D-20 condition: "approve a minimal wording fix only if one quiz item can be edited without regenerating the pack or invalidating review records; otherwise document for separate maintenance." Labels: **FACT** (read in code or production), **INFERENCE**, **PROPOSAL**.

# D2: *Financial Mathematics in Everyday Life*, quiz item 3 (check)

## 1. The item (FACT, production 2026-11-06)

Note `fd5727d4-b16a-4990-9989-3efd0e72d7eb`, PUBLIC, in 3 collection items. Study Pack `8efc275e-312e-4e0d-a97a-b6672585eb5d`, status DONE, five items, `quiz_stamp` 0, quiz digest `e7f206b08cbd9c6f352ffcc331838350`, model `gpt-4.1-mini`.

| Field | Value |
|---|---|
| Question (item 3, zero-based index 2) | "When budgeting, which step is LEAST important?" |
| Choices | Listing income / Listing expenses / **Ignoring future financial goals** / Planning savings |
| Key | index 2, "Ignoring future financial goals" |
| Explanation | "Ignoring future financial goals can lead to ineffective budgeting, as budgeting aims to meet such goals through proper expense and savings planning." |
| Format / type / concept | `MCQ` / `CONCEPTUAL` / "Budgeting" |

**The defect:** the key is not a step at all, so "LEAST important step" is a poor frame for it. The key is unambiguous and the explanation is sound; only the stem is poor (low severity). **The body supports a better stem (FACT):** "Budgeting involves listing income and expenses to plan spending and saving effectively to achieve financial goals." Listing income, listing expenses and planning savings are all parts of budgeting by that sentence; ignoring goals is what the sentence says budgeting is meant to avoid.

## 2. Can one item be edited without regenerating the pack? (FACT from code)

- **Through the product: no.** There is no endpoint that edits a single quiz item. `StudyPackController` offers create, read, delete, tags, metadata, memorization and activity; `AdminStudyPackController` offers `regenerate-summaries`, `repair-malformed-quizzes` and the template seed. Both quiz-replacing paths (`StudyPackService` generation, the malformed-quiz repair at `AdminStudyPackTransactionHelper.java:140`) **replace the whole quiz with a fresh LLM generation** and bump `quiz_stamp`. Neither can change one stem.
- **Through SQL: yes.** `study_packs.quiz` is `jsonb`, so one stem can be replaced in place (`jsonb_set` on the path `{2,question}`) without touching the other four items or any other field of item 3. That is an **owner-run production write to a generated artifact**, outside any product path; Claude does not run it.

## 3. Would a wording-only edit invalidate review records? (FACT from code and production)

| Dependent | Finding | Effect of editing the canonical stem |
|---|---|---|
| **`quiz_stamp`** | Bumped only on regeneration or repair. Quick Review mastery counts a completed session only if its stamp equals the pack's (`QuickReviewSessionRepository.findQuizMasteredAt`); a session whose stamp differs is "stale" and forfeited (`QuickReviewSessionService.isStale`). | **Leave the stamp unchanged**: the key and meaning are unchanged, so no one's mastery should be revoked. (Bumping it would be the invalidating choice.) |
| Sessions on the canonical pack | **0** `quick_review_sessions`, 0 `quiz_sessions`, 0 `quiz_questions`, 0 `concept_health`, 0 `memorization_cards`, 0 share links, 0 exam pool rows | Nothing to invalidate. |
| Session state | A session stores its own question list at creation (`QuizSessionStateUtils`), so existing sessions would keep the old wording | No effect on past records. |
| Shuffle | `QuizVersionShuffleUtils` (DOCX export) seeds from the quiz id and indices, not the question text. **CORRECTION 2026-11-06 (section 9): the LIVE quiz and answer review order the choices with `buildDeterministicChoiceOrder(question, choices)` in `frontend/lib/quiz.ts`, a deterministic shuffle seeded on the question text plus the choices.** | The export is unaffected. For the live quiz, a reworded stem changes that item's displayed choice order once. Harmless here (0 sessions on the canonical pack), but the earlier "no effect" was incomplete |
| Text consumers | `LongExamService:1158`, `QuickReviewAdaptivePracticeService:243,514` read quiz question texts only as a "do not repeat these" list for later generation | A reworded stem simply replaces the old text in that list. |
| Challenge Quiz bank | 20 `challenge_quiz_question_bank` rows for the pack, keyed to the pack's `generation_stamp` (0, unchanged) | Unaffected. |
| Activity | 1 `user_activity_events` row for the pack | Unaffected. |
| The QA record | The sample check recorded "keys correct; one awkward item (D2)" | Editing the stem resolves the recorded defect; the key does not change, so the "keys correct" finding stands. |

## 4. The part a canonical-only edit does NOT reach (FACT, production)

Seven other users copied this public Note. **Each copy has its own Study Pack with the same quiz** (a public-note copy includes the linked Study Pack, the documented exception: `NoteService.java:455`). All seven are **PRIVATE and owned by other users**, with identical quiz digests, `quiz_stamp` 0, and **one of them has 2 Quick Review sessions**.

- A canonical edit leaves those seven on the old wording. That is how copies behave by design (they are snapshots).
- **They are other learners' private content and must not be edited by this fix.** Nothing in D2 touches them; a SQL targeting by note title or by quiz digest would hit them, so any script must be guarded on the canonical pack id and note id.
- Result: after a canonical edit, the canonical Note and all future copies show the new stem; the seven existing copies show the old one. Low impact (a wording flaw, key unaffected).

## 5. Verdict against the owner's condition

**The D-20 condition is met by SQL, not by the product.** One item can be edited without regenerating the pack, and with no review record invalidated, provided the `quiz_stamp` is left alone and the write is limited to the canonical pack. It is a direct write to a generated artifact that no product path performs, and it does not reach the seven copies.

**PROPOSAL (owner decides):**
- **Option A, fix the canonical item (recommended).** New stem: **"Which of the following is NOT part of effective budgeting?"** Choices, key (index 2), explanation, format and type unchanged. A guarded, owner-run SQL in the same single-session, dry-run-by-default design as D1: guards on the note id, pack id, pack `updated_at` (`2026-09-09 01:36:56.523087+00`), the current quiz digest, the exact current stem, `quiz_stamp` = 0 and zero sessions; post-checks that only item 3's `question` changed (the other four items and every other field of item 3 byte-identical), the stamp unchanged, the Note and its metadata unchanged, and the seven copies unchanged; an exact rollback statement. **I have not written it; say so and I will.**
- **Option B, document only.** Record it as a known limitation for separate quiz-quality maintenance, together with D1's all-keys-position-A pattern. Costs nothing and risks nothing; the awkward stem stays.

**Neither option blocks publication of the BSIS journey or creation of the twelve FIS Notes.** The item is low severity and the key is correct. This is the same maintenance class as D1's answer-position pattern, which is already recorded and deliberately not fixed here.

## 6. Read-only evidence queries used (SELECT only)

The item and pack: `SELECT s.id, s.status, s.quiz_stamp, s.generation_stamp, md5(s.quiz::text), s.quiz::jsonb -> 2 FROM study_packs s WHERE s.note_id = 'fd5727d4-b16a-4990-9989-3efd0e72d7eb'`. Dependents: counts by `study_pack_id` over `quick_review_sessions`, `quiz_sessions`, `quiz_questions`, `exam_question_pool`, `challenge_quiz_question_bank`, `concept_health`, `memorization_cards`, `share_links`, `user_activity_events`. Copies: `notes` where `copied_from_note_id` or `source_note_id` equals the Note id, joined to `study_packs`. Production-state claims here are a snapshot of 2026-11-06; re-read before relying on them.

## 7. Option A chosen; SQL prepared (2026-11-06)

Owner chose **Option A**. `bsis-year1-d2-financial-mathematics-quiz-item-fix.sql` (owner-run, **NOT run**) replaces the one stem (`quiz -> element 3 -> "question"`) with **"Which of the following is NOT part of effective budgeting?"** and nothing else.

**One correction to section 5 found while writing it (FACT):** the script deliberately does **not** bump `study_packs.updated_at`, which section 5 left open. This pack's owner is an ADMIN account, and `StudyPackRepository.sumAdminGenerationTokensByModel` sums the tokens of ADMIN-owned DONE packs by `updated_at` month. Touching `updated_at` would add this pack's original generation (3,722 in / 880 out tokens) to the current month's curation usage. Raw SQL fires no `@PreUpdate` and `study_packs` has no database triggers, so leaving it alone is the default; the script guards on the current value and asserts it is unchanged. `quiz_stamp` is likewise unchanged by design.

Guards and post-checks: note and pack ids; pack status DONE, stamp 0, `updated_at` as read; the whole-quiz digest (`e7f206b0...`, which pins every item) and the exact old stem; no sessions, pool rows, cards or share links on the pack; exactly 7 copies. After the update: exactly one row; the other four items and every other field of item 3 identical; putting the old stem back reproduces the original quiz exactly; stamp, status and `updated_at` unchanged; the Note and the seven copies (digest over their id, quiz, stamp and `updated_at`) unchanged. Tested locally in dry, hold and commit modes with a re-run, rollback restore and ten deliberate failures; see the script header for the list and the limits.

## 8. Applied and verified (2026-11-06)

The owner ran the script; Claude re-read production read-only: new stem in place, choices and key unchanged, other four items unchanged, `quiz_stamp` 0, pack `updated_at` unchanged, old-stem restore reproduces the original digest, 7 copies still on the old stem, 0 sessions. See readiness report section 9.

## 9. Correction found during the spot-checks (2026-11-06)

Section 3 said the shuffle does not depend on the question text. That was true only of the DOCX export (`QuizVersionShuffleUtils`). The live quiz and the answer review (`getDisplayedQuizChoices`, `frontend/lib/quiz.ts`) shuffle the choices with a hash of the question text and the choices, so **editing the stem changed the displayed choice order of item 3 for the canonical Note.** Consequences (FACT/INFERENCE): the key, the grading (it tracks the canonical index) and every record are unaffected; no learner is mid-session (0 sessions on the canonical pack, verified before and after); the seven private copies keep the old stem and therefore the old order; and the only visible effect is that item 3's choices may appear in a different order than before. The D2 script's safety assertions were unaffected. Nothing needs undoing.
