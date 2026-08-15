-- v0.81.0 -- stamp bank rows that are permanently unclaimable.
--
-- `learner_level` was added nullable by V96, so rows written before it was populated carry NULL.
-- Bank reads gate on `sameLearnerLevel(row.learner_level, effectiveCurriculumLevel)`, and
-- `effectiveCurriculumLevel` never returns null (note level -> reader level -> COLLEGE). A NULL row
-- therefore matches nothing and stays unclaimable for its owner forever. Question pools recover
-- after one lazy refresh; bank rows have no such path -- recorded as a v0.70.0 Known Limitation
-- ("Null-by-null pool/bank cohort", ~15 rows) with the note that it will not resolve itself.
--
-- The stamp resolves the way the application does: the pack's note level first, COLLEGE only as the
-- terminal fallback. A blanket COLLEGE stamp would be wrong -- these rows are NULL because pre-v0.70.0
-- code passed the reader's raw nullable level, NOT because the note lacked one, so a note carrying
-- e.g. JUNIOR_HIGH would stay just as unclaimable if stamped COLLEGE.
--
-- NON-DESTRUCTIVE. Nothing is deleted -- ADR-001 rule 2 holds. The NOT EXISTS guard is defensive
-- only: the pre-V115 key was unique on (user_id, study_pack_id, question_key) with all three NOT
-- NULL, so at most one row can exist per triple and a NULL row cannot have a levelled twin today.
-- It is kept so the migration stays safe if it is ever re-run against data written after V115.
UPDATE challenge_quiz_question_bank a
SET learner_level = resolved.learner_level
FROM (
    SELECT b.id,
           coalesce(
               (SELECT n.learner_level
                FROM study_packs sp
                JOIN notes n ON n.id = sp.note_id
                WHERE sp.id = b.study_pack_id),
               'COLLEGE'
           ) AS learner_level
    FROM challenge_quiz_question_bank b
    WHERE b.learner_level IS NULL
) AS resolved
WHERE a.id = resolved.id
  AND NOT EXISTS (
      SELECT 1
      FROM challenge_quiz_question_bank c
      WHERE c.user_id = a.user_id
        AND c.study_pack_id = a.study_pack_id
        AND c.question_key = a.question_key
        AND c.learner_level = resolved.learner_level
  );
