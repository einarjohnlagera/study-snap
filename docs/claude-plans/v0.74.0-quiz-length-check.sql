-- v0.74.0 — does any stored Study Pack quiz have a length other than 5?
--
-- Why this matters: the v0.74.0 gate is "perfect 5/5 on Quick Review unlocks the Quiz tab".
-- Quick Review administers the WHOLE saved quiz with no slicing
-- (QuickReviewSessionService.java:98 sets totalQuestions from studyPack.getQuiz().size()),
-- so the gate's shape is whatever is in the row -- not whatever the current validator enforces.
--
-- Current code pins generation to exactly 5 (schema.json minItems/maxItems: 5;
-- STUDY_PACK_QUIZ_QUESTION_COUNT = 5; generation REJECTED at a different count,
-- OpenAiLlmStudyPackService.java:432). But that validation landed 2026-03-18 (c78ee9f1).
-- Packs generated before it, and any remix/copy descended from one
-- (ShareService.java:90, NoteService.java:401), are NOT covered by it.
--
-- Expected result if the retraction in the v0.74.0 brief holds: exactly one row, 5.
-- Any other row means "perfect 5/5" copy is wrong for those learners and the gate
-- needs a length-agnostic phrasing ("answer every question correctly") instead.

-- Query 1 — distribution of stored quiz lengths.
SELECT COALESCE(jsonb_array_length(sp.quiz), 0) AS question_count,
       COUNT(*)                                 AS study_packs
FROM study_packs sp
GROUP BY 1
ORDER BY 1;

-- Query 1b — ADDED 2026-08-12 while scoping item 1. Same question as Query 1
-- ("what is actually in the stored rows"), different column.
--
-- Why: Quick Review scores CLIENT-SIDE, and its score memo passes a choice INDEX to
-- isQuizSelectionCorrect (frontend/lib/quiz.ts:137) for every format except
-- MULTI_SELECT. That function returns false for IDENTIFICATION unless it receives a
-- string, and false for ENUMERATION unless it receives a string[]. Quick Review has no
-- text-input UI at all. So a stored IDENTIFICATION or ENUMERATION question can NEVER be
-- marked correct -- which under the v0.74.0 gate means that pack's Quiz tab can never
-- unlock, and Redo Mistakes cannot rescue it either. That is exactly the dead end the
-- owner's "Redo Mistakes counts" ruling exists to prevent.
--
-- Why this is probably NOT live, and is a check rather than an alarm: the study-pack
-- developer prompt only ever offers MCQ | TRUE_FALSE | MULTI_SELECT | MATCHING | null
-- (developer.txt:16), and Quick Review handles all four -- MATCHING via question groups,
-- the rest via index comparison. schema.json's enum is WIDER than the prompt
-- (it also permits IDENTIFICATION and ENUMERATION), so nothing structurally prevents an
-- older or hand-authored row from holding one. Same residual population as Query 2:
-- packs predating the 2026-03-18 validation, and remixes/copies descended from them.
--
-- Expected: only MCQ / TRUE_FALSE / MULTI_SELECT / MATCHING / null.
-- Any IDENTIFICATION or ENUMERATION row -> those packs are ungateable as built. Either
-- exempt them from the lock or make Quick Review able to score them; do not ship a gate
-- that a learner cannot pass.
SELECT COALESCE(q->>'questionFormat', '(null)') AS question_format,
       COUNT(*)                                 AS questions,
       COUNT(DISTINCT sp.id)                    AS study_packs
FROM study_packs sp,
     LATERAL jsonb_array_elements(sp.quiz) q
GROUP BY 1
ORDER BY 2 DESC;


-- Query 2 — only if Query 1 returns anything other than a single row of 5.
-- How many of the off-spec packs are actually reachable by a learner today?
SELECT COALESCE(jsonb_array_length(sp.quiz), 0) AS question_count,
       COUNT(*)                                 AS study_packs,
       COUNT(DISTINCT n.owner_user_id)          AS owners,
       MIN(sp.created_at)                       AS oldest,
       MAX(sp.created_at)                       AS newest
FROM study_packs sp
JOIN notes n ON n.id = sp.note_id
WHERE COALESCE(jsonb_array_length(sp.quiz), 0) <> 5
GROUP BY 1
ORDER BY 1;
