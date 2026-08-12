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

-- Query 2 — only if Query 1 returns anything other than a single row of 5.
-- How many of the off-spec packs are actually reachable by a learner today?
SELECT COALESCE(jsonb_array_length(sp.quiz), 0) AS question_count,
       COUNT(*)                                 AS study_packs,
       COUNT(DISTINCT n.user_id)                AS owners,
       MIN(sp.created_at)                       AS oldest,
       MAX(sp.created_at)                       AS newest
FROM study_packs sp
JOIN notes n ON n.id = sp.note_id
WHERE COALESCE(jsonb_array_length(sp.quiz), 0) <> 5
GROUP BY 1
ORDER BY 1;
