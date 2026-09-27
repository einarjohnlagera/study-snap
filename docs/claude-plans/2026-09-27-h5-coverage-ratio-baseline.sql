-- H5 (v0.162.0) coverage-ratio baseline — read-only, run against production 2026-09-27.
-- Purpose: freeze the PRE-H5 baseline for the coverage-ratio metric RELEASES.md v0.162.0 Phase A
-- "Measurement" names as the real instrument (the raw H4 retry/omit count is explicitly NOT this metric).
-- Run the IDENTICAL query at signoff with `created_at >= '<H5 deploy timestamp>'` in place of the
-- 2026-09-22 (H4 deploy) floor, to get the POST-H5 number to compare against the numbers below.
--
-- Caveat, stated plainly rather than assumed: this is an APPROXIMATION, not a re-implementation of
-- QuizValidationUtils.isAnswerExplanationInternallyInconsistent. It uses a case-insensitive substring
-- match (`LIKE '%value%'`), not that method's normalized/rounded matcher
-- (normalizeConsistencyText + roundedChoiceOccursInEvidence). Only compare this query's own before/after
-- runs against each other -- do not read it as "H4's own predicate" or as a proxy for H4's actual
-- retry/omit behavior.
--
-- Minimum-n clause: if the post-H5 numeric-MCQ sample is well under ~80 items at signoff (this
-- baseline window's own count), record "not yet measurable" and re-date the checkpoint rather than
-- reading a thin sample as a verdict.

-- Query 1: total MCQ-shaped items in the window (denominator context: how rare is the numeric-literal
-- subset H5 can actually affect, per H4's own gate). NOTE: H4 treats a null questionFormat as MCQ
-- (QuizValidationUtils.java:194, `if (questionFormat != null && !MCQ_FORMAT.equals(questionFormat))`),
-- so this includes null format, not only the literal string "MCQ".
WITH items AS (
  SELECT sp.id AS pack_id, sp.created_at, elem
  FROM study_packs sp, jsonb_array_elements(sp.quiz) elem
  WHERE sp.quiz IS NOT NULL AND sp.created_at >= '2026-09-22'  -- H4 deploy (v0.155.0)
),
mcq_like AS (
  SELECT pack_id, created_at, elem
  FROM items
  WHERE (elem->>'questionFormat' IS NULL OR elem->>'questionFormat' = 'MCQ')
    AND elem->'correctIndex' IS NOT NULL
    AND jsonb_typeof(elem->'choices') = 'array'
),
numeric_mcq AS (
  -- H4's own isNumericUnitLiteral predicate: every choice <=20 chars and contains a digit.
  SELECT pack_id, created_at, elem
  FROM mcq_like
  WHERE NOT EXISTS (
    SELECT 1 FROM jsonb_array_elements_text(elem->'choices') c
    WHERE length(c) > 20 OR c !~ '[0-9]'
  )
)
SELECT
  (SELECT count(*) FROM mcq_like) AS total_mcq_like,
  (SELECT count(*) FROM numeric_mcq) AS numeric_mcq_total,
  -- Query 2: of the numeric-literal MCQs, how many already have the explanation/workingSolution
  -- state the correct choice's exact text (the PRE-H5 coverage ratio numerator).
  (SELECT count(*) FROM numeric_mcq
     WHERE lower(coalesce(elem->>'explanation','') || ' ' || coalesce(elem->>'workingSolution',''))
           LIKE '%' || lower(elem->'choices'->>((elem->>'correctIndex')::int)) || '%'
  ) AS value_stated_count,
  -- Query 3: masking-risk baseline. QuizValidationUtils:207-209 short-circuits to "consistent" the
  -- moment the CORRECT choice's value is found in evidence, before ever checking whether a WRONG
  -- choice's value is also present. This counts numeric MCQs whose evidence already contains BOTH the
  -- correct value AND at least one distractor's value -- cases H4 already cannot catch today, pre-H5.
  -- Watch this count post-H5: if it rises much faster than value_stated_count, that is a signal of
  -- either mis-keyed answers being masked, or of models violating the "do not discuss the other
  -- choices' values" clause -- both worth a look, not proof of either on their own.
  (SELECT count(*) FROM numeric_mcq nm
     WHERE lower(coalesce(nm.elem->>'explanation','') || ' ' || coalesce(nm.elem->>'workingSolution',''))
           LIKE '%' || lower(nm.elem->'choices'->>((nm.elem->>'correctIndex')::int)) || '%'
     AND EXISTS (
       SELECT 1 FROM jsonb_array_elements_text(nm.elem->'choices') WITH ORDINALITY AS c(val, idx)
       WHERE idx - 1 <> (nm.elem->>'correctIndex')::int
         AND lower(coalesce(nm.elem->>'explanation','') || ' ' || coalesce(nm.elem->>'workingSolution','')) LIKE '%' || lower(val) || '%'
     )
  ) AS both_correct_and_distractor_mentioned;

-- RESULT, read 2026-09-27 (pre-H5 baseline, H4-deploy window 2026-09-22 through 2026-09-27):
--   total_mcq_like = 5110
--   numeric_mcq_total = 88            (1.7% of MCQ-shaped items -- the entire population H5 can affect
--                                       under H4's own gate; the other 98.3% are prose/non-numeric MCQs
--                                       H4 never evaluates regardless of what H5 does to them)
--   value_stated_count = 60            (68.2% of the 88 already state the correct value verbatim, even
--                                       under the CURRENT "don't restate" instruction -- the ban is
--                                       imperfectly followed today, this is not evidence H5 has shipped)
--   both_correct_and_distractor_mentioned = 10  (16.7% of the 60 that state the correct value also
--                                       already mention a distractor's value -- pre-existing, not
--                                       introduced by H5)
--
-- Also confirmed separately (not part of the WITH-query above): no in-place regeneration occurred in
-- this window -- `SELECT count(*) FILTER (WHERE updated_at > created_at + interval '1 minute') FROM
-- study_packs WHERE created_at >= '2026-09-22'` returned 0 of 1057 -- so created_at is a clean partition
-- point for a signoff-time before/after read, PROVIDED regeneration still does not bump updated_at
-- without also bumping created_at at signoff time; re-verify this assumption then rather than reusing
-- it from this comment uncritically.
