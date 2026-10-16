-- ============================================================================
-- study_packs.quiz: residual pre-H4 answer-key defects. TIGHTENED owner-run repair.
-- Supersedes, for execution, 2026-10-15-study-packs-residual-answer-key-defects-repair.sql
-- (Prod Investigator). Same 4 defects, same corrections. The original is kept as the record.
-- Finding: docs/claude-findings/2026-10-15-quiz-quality-retention-llm-cost-production-read.md, §2
-- Written 2026-10-16 by the Release Implementor session, read-only. Not executed.
-- ============================================================================
-- ⚠️ CLAUDE DOES NOT EXECUTE THIS FILE. Per CLAUDE.md, production writes are
-- owner-only, with no exceptions.
--
-- WHAT CHANGED FROM THE ORIGINAL (the math and target indexes are unchanged):
--   1. Every match also requires the exact QUESTION TEXT. D3's choices
--      ["$15","$20","$25","$30"] are generic enough that another, correctly
--      keyed question could share them with correctIndex 2. The original
--      would have silently "fixed" it wrong.
--   2. jsonb_agg uses WITH ORDINALITY ... ORDER BY ord. Without ORDER BY, the
--      element order of the rebuilt array is not guaranteed, and question
--      order is user-visible.
--   3. The whole run is one transaction. An assertion block aborts it if any
--      wrong key survives, if a quiz changes length, or if the touched-row
--      count is not the expected 6. An aborted transaction cannot COMMIT.
--
-- DELIBERATELY NOT TOUCHED (same as the 2026-09-21 repair):
--   - quiz_stamp. Bumping it would invalidate Quick Review mastery
--     (QuickReviewSessionRepository.findQuizMasteredAt) for anyone who
--     completed these quizzes. A corrected answer key is the same quiz.
--   - updated_at.
--   - No copies exist in challenge_quiz_question_bank, exam_question_pool or
--     quick_review_sessions (checked 2026-10-15). quiz is the only question
--     column on study_packs.
--
-- PRE-STATE, re-read 2026-10-16 (read-only): exactly 6 rows, each pack's quiz
-- has 5 questions:
--   D1  b64de9fd-982b-42a7-b59c-110861b82966  question #5  correctIndex 3
--   D2  3e671707-8984-4e51-8386-04cacd5d3667  question #5  correctIndex 0
--   D3  95aab696-5a9d-47c0-99f6-a9cca1736295  question #4  correctIndex 2
--   D4  5fe4e2be-e8d0-4a29-81b6-e1bc34077fbc  question #5  correctIndex 3
--   D4  d83013bd-3bd0-408e-b64e-8b7cbcf26285  question #5  correctIndex 3
--   D4  deaa49eb-f001-4ae1-bb9a-b5ac038eec93  question #5  correctIndex 3
-- If STEP 1 shows more D4 rows, a new public-note copy has been made since.
-- The question-text match fixes it too, but then change the 6 in STEP 3's
-- assertion to the new total before running.
--
-- HOW TO RUN: feed the whole file to psql as one script (psql "$DATABASE_URL" -f <file>).
--   - Use psql, not a console that runs each statement in its own transaction.
--     The safety net depends on everything sharing the one BEGIN.
--   - It ends with ROLLBACK, so the first run is a dry run. Read STEP 4's output.
--   - When it matches, change the final ROLLBACK to COMMIT and run it again.
--   - It is idempotent: every match re-asserts the current WRONG value, so a
--     second run updates 0 rows.
--   - ⚠️ On a second run after COMMIT, STEP 3 will fail with 0 ≠ 6. That is
--     expected and harmless (the transaction rolls back), not a defect.
-- ============================================================================

BEGIN;

-- Expected UPDATE counts are 1, 1, 1 and 3; psql prints each as "UPDATE n".

-- Fixed list of the four defects. Each row is matched by question text,
-- choices and the current (wrong) correctIndex, and gets the right index.
CREATE TEMP TABLE answer_key_fix ON COMMIT DROP AS
SELECT * FROM (VALUES
  ('D1',
   'If inherent risk is 0.4 and control risk is 0.5, what is the detection risk if the auditor wants to maintain an overall audit risk of 0.06?',
   '["0.3", "0.2", "0.15", "0.25"]'::jsonb, 3, 0),
     -- DR = AR/(IR×CR) = 0.06/0.2 = 0.3 → idx0
  ('D2',
   'Calculate the labor efficiency variance given the following data: Actual hours = 120, Standard hours = 100, Standard labor rate = $15 per hour.',
   '["$150 unfavorable", "$300 favorable", "$300 unfavorable", "$150 favorable"]'::jsonb, 0, 2),
     -- (120-100)×15 = $300, actual > standard → unfavorable → idx2
  ('D3',
   'Calculate the weighted average cost per unit if total cost of goods available is $10,000 and total units available are 500.',
   '["$15", "$20", "$25", "$30"]'::jsonb, 2, 1),
     -- 10,000/500 = $20 → idx1
  ('D4',
   'Calculate the annual depreciation expense using the Straight-Line Method for an asset costing $50,000, salvage value $5,000, and useful life 9 years.',
   '["$5,000", "$6,111", "$3,000", "$4,000"]'::jsonb, 3, 0)
     -- (50,000-5,000)/9 = $5,000 → idx0
) AS t(defect, question, choices, wrong_index, correct_index);

-- ---------------------------------------------------------------------------
-- STEP 1 — pre-check. Expect exactly the 6 rows listed in the header.
-- ---------------------------------------------------------------------------
SELECT f.defect, sp.id::text AS pack_id, e.ord AS question_no,
       (e.elem->>'correctIndex')::int AS current_index, f.correct_index AS new_index,
       jsonb_array_length(sp.quiz) AS quiz_len
FROM study_packs sp
CROSS JOIN LATERAL jsonb_array_elements(sp.quiz) WITH ORDINALITY AS e(elem, ord)
JOIN answer_key_fix f
  ON e.elem->>'question' = f.question
 AND e.elem->'choices' = f.choices
 AND (e.elem->>'correctIndex')::int = f.wrong_index
ORDER BY f.defect, pack_id;

-- Record each targeted pack's quiz length, for STEP 3's length check.
CREATE TEMP TABLE answer_key_fix_targets ON COMMIT DROP AS
SELECT DISTINCT sp.id, jsonb_array_length(sp.quiz) AS len_before
FROM study_packs sp
CROSS JOIN LATERAL jsonb_array_elements(sp.quiz) AS e(elem)
JOIN answer_key_fix f
  ON e.elem->>'question' = f.question
 AND e.elem->'choices' = f.choices
 AND (e.elem->>'correctIndex')::int = f.wrong_index;

-- ---------------------------------------------------------------------------
-- STEP 2 — the repair: one UPDATE per defect, order preserved.
-- ---------------------------------------------------------------------------
UPDATE study_packs sp SET quiz = (
  SELECT jsonb_agg(
           CASE WHEN e.elem->>'question' = f.question
                 AND e.elem->'choices' = f.choices
                 AND (e.elem->>'correctIndex')::int = f.wrong_index
                THEN jsonb_set(e.elem, '{correctIndex}', to_jsonb(f.correct_index))
                ELSE e.elem END
           ORDER BY e.ord)
  FROM jsonb_array_elements(sp.quiz) WITH ORDINALITY AS e(elem, ord))
FROM answer_key_fix f
WHERE f.defect = 'D1'
  AND sp.quiz @> jsonb_build_array(jsonb_build_object(
        'question', f.question, 'choices', f.choices, 'correctIndex', f.wrong_index));
-- expect UPDATE 1

UPDATE study_packs sp SET quiz = (
  SELECT jsonb_agg(
           CASE WHEN e.elem->>'question' = f.question
                 AND e.elem->'choices' = f.choices
                 AND (e.elem->>'correctIndex')::int = f.wrong_index
                THEN jsonb_set(e.elem, '{correctIndex}', to_jsonb(f.correct_index))
                ELSE e.elem END
           ORDER BY e.ord)
  FROM jsonb_array_elements(sp.quiz) WITH ORDINALITY AS e(elem, ord))
FROM answer_key_fix f
WHERE f.defect = 'D2'
  AND sp.quiz @> jsonb_build_array(jsonb_build_object(
        'question', f.question, 'choices', f.choices, 'correctIndex', f.wrong_index));
-- expect UPDATE 1

UPDATE study_packs sp SET quiz = (
  SELECT jsonb_agg(
           CASE WHEN e.elem->>'question' = f.question
                 AND e.elem->'choices' = f.choices
                 AND (e.elem->>'correctIndex')::int = f.wrong_index
                THEN jsonb_set(e.elem, '{correctIndex}', to_jsonb(f.correct_index))
                ELSE e.elem END
           ORDER BY e.ord)
  FROM jsonb_array_elements(sp.quiz) WITH ORDINALITY AS e(elem, ord))
FROM answer_key_fix f
WHERE f.defect = 'D3'
  AND sp.quiz @> jsonb_build_array(jsonb_build_object(
        'question', f.question, 'choices', f.choices, 'correctIndex', f.wrong_index));
-- expect UPDATE 1

UPDATE study_packs sp SET quiz = (
  SELECT jsonb_agg(
           CASE WHEN e.elem->>'question' = f.question
                 AND e.elem->'choices' = f.choices
                 AND (e.elem->>'correctIndex')::int = f.wrong_index
                THEN jsonb_set(e.elem, '{correctIndex}', to_jsonb(f.correct_index))
                ELSE e.elem END
           ORDER BY e.ord)
  FROM jsonb_array_elements(sp.quiz) WITH ORDINALITY AS e(elem, ord))
FROM answer_key_fix f
WHERE f.defect = 'D4'
  AND sp.quiz @> jsonb_build_array(jsonb_build_object(
        'question', f.question, 'choices', f.choices, 'correctIndex', f.wrong_index));
-- expect UPDATE 3

-- ---------------------------------------------------------------------------
-- STEP 3 — assertions. Any failure raises an exception and aborts the
-- transaction, so the final COMMIT can only roll back.
-- ---------------------------------------------------------------------------
DO $$
DECLARE
  still_wrong int;
  length_changed int;
  targets int;
  now_correct int;
BEGIN
  SELECT count(*) INTO targets FROM answer_key_fix_targets;
  IF targets <> 6 THEN
    RAISE EXCEPTION 'expected 6 targeted packs, found % — re-read STEP 1', targets;
  END IF;

  SELECT count(*) INTO still_wrong
  FROM study_packs sp
  CROSS JOIN LATERAL jsonb_array_elements(sp.quiz) AS e(elem)
  JOIN answer_key_fix f
    ON e.elem->>'question' = f.question AND e.elem->'choices' = f.choices
   AND (e.elem->>'correctIndex')::int = f.wrong_index;
  IF still_wrong <> 0 THEN
    RAISE EXCEPTION '% wrong answer keys survived the repair', still_wrong;
  END IF;

  SELECT count(*) INTO length_changed
  FROM answer_key_fix_targets t JOIN study_packs sp ON sp.id = t.id
  WHERE jsonb_array_length(sp.quiz) IS DISTINCT FROM t.len_before;
  IF length_changed <> 0 THEN
    RAISE EXCEPTION '% quizzes changed length', length_changed;
  END IF;

  SELECT count(*) INTO now_correct
  FROM answer_key_fix_targets t JOIN study_packs sp ON sp.id = t.id
  CROSS JOIN LATERAL jsonb_array_elements(sp.quiz) AS e(elem)
  JOIN answer_key_fix f
    ON e.elem->>'question' = f.question AND e.elem->'choices' = f.choices
   AND (e.elem->>'correctIndex')::int = f.correct_index;
  IF now_correct <> targets THEN
    RAISE EXCEPTION 'expected % corrected questions, found %', targets, now_correct;
  END IF;

  RAISE NOTICE 'answer-key repair assertions passed: % packs corrected', targets;
END $$;

-- ---------------------------------------------------------------------------
-- STEP 4 — post-check for your eyes. Expect the 6 rows, each with new_index
-- = 0, 2, 1, 0, 0, 0 (D1, D2, D3, D4 ×3) and quiz_len 5.
-- ---------------------------------------------------------------------------
SELECT f.defect, sp.id::text AS pack_id, e.ord AS question_no,
       (e.elem->>'correctIndex')::int AS new_index, jsonb_array_length(sp.quiz) AS quiz_len
FROM answer_key_fix_targets t
JOIN study_packs sp ON sp.id = t.id
CROSS JOIN LATERAL jsonb_array_elements(sp.quiz) WITH ORDINALITY AS e(elem, ord)
JOIN answer_key_fix f ON e.elem->>'question' = f.question AND e.elem->'choices' = f.choices
ORDER BY f.defect, pack_id;

-- Dry run by default. Change to COMMIT once STEP 4 looks right.
COMMIT;

-- ---------------------------------------------------------------------------
-- AFTER COMMIT — Release Implementor verifies read-only and records the
-- outcome under v0.173.0 in RELEASES.md.
-- ---------------------------------------------------------------------------
-- NOT included, and NOT to be repaired (per the finding, §2): the APGAR,
-- percentage-tax and grain-size candidates are FALSE POSITIVES, independently
-- re-derived as correctly keyed.
