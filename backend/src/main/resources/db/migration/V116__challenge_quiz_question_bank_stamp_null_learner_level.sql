-- v0.81.0 -- stamp bank rows that are permanently unclaimable.
--
-- `learner_level` was added nullable by V96, so rows written before it was populated carry NULL.
-- Bank reads gate on `sameLearnerLevel(row.learner_level, effectiveCurriculumLevel)`, and
-- `effectiveCurriculumLevel` never returns null (note level -> reader level -> COLLEGE). A NULL row
-- therefore matches nothing and stays unclaimable for its owner forever. Question pools recover
-- after one lazy refresh; bank rows have no such path -- recorded as a v0.70.0 Known Limitation
-- ("Null-by-null pool/bank cohort", ~15 rows) with the note that it will not resolve itself.
--
-- COLLEGE is the correct stamp because it is exactly what the resolution chain yields when neither
-- the note nor the reader carries a level -- the same state these rows were written in.
--
-- NON-DESTRUCTIVE BY CONSTRUCTION. A row is stamped only when no claimable COLLEGE twin already
-- exists for the same (user, study pack, question). Rows left NULL are duplicates of a row the
-- learner can already receive, so nothing is lost and nothing is deleted -- ADR-001 rule 2 holds.
-- The guard also keeps this migration from violating V115's widened uniqueness key.
UPDATE challenge_quiz_question_bank a
SET learner_level = 'COLLEGE'
WHERE a.learner_level IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM challenge_quiz_question_bank b
      WHERE b.user_id = a.user_id
        AND b.study_pack_id = a.study_pack_id
        AND b.question_key = a.question_key
        AND b.learner_level = 'COLLEGE'
  );
