-- ============================================================================
-- study_packs.quiz — residual pre-H4 answer-key defects, owner-run repair
-- Finding: docs/claude-findings/2026-10-15-quiz-quality-retention-llm-cost-production-read.md, §2
-- Written 2026-10-15 by the Prod Investigator session, read-only. Not implemented.
-- ============================================================================
-- ⚠️ CLAUDE DOES NOT EXECUTE THIS FILE. Per CLAUDE.md, production writes are
-- owner-only, with no exceptions.
--
-- WHAT THIS IS: 4 distinct questions, confirmed defective by independently
-- re-deriving the correct answer from each question's own stated numeric
-- inputs (same standard as the 2026-09-21/22 repair). All 4 PRE-DATE both
-- H4 (2026-09-22) and the original 2026-09-19 incident scan itself — they
-- are plausibly among that scan's own "17 raw study_packs candidates, only
-- 6 manually reviewed" gap, not new contamination. See the finding for why.
--
-- ⚠️ UNLIKE THE 2026-09-21 REPAIR FILE, THIS ONE MATCHES ON (choices,
-- correctIndex) RATHER THAN HARDCODED PACK IDS. One of these 4 defects
-- (the depreciation question) has already propagated via the ordinary
-- public-note copy mechanism to 2 additional packs since its source was
-- first generated (2026-08-18) — copies keep being made right up to this
-- file being written (the latest, 2026-10-04, belongs to a real learner who
-- has not yet quizzed on it). A pack-id list would miss any further copy
-- made between this file being written and the owner running it. The
-- choices+correctIndex match catches all of them, present and future,
-- without needing to re-run a scan first.
--
-- EXPOSURE: checked directly against quick_review_sessions by study_pack_id
-- for all 6 currently-matching packs. Exactly one session exists on any of
-- them (the audit-risk pack, 1 incomplete session, by the owner's own
-- dee4225c test account). The one real learner's copy of the depreciation
-- defect has zero sessions recorded — a live but not yet realized risk.
--
-- EVERY UPDATE's WHERE clause re-asserts the CURRENT WRONG value, so this
-- file is idempotent and safe to run more than once or on a partially-
-- patched row.
-- ============================================================================

-- ============================================================================
-- D.1 — Audit detection risk (IR=0.4, CR=0.5, target AR=0.06).
--       DR = AR/(IR×CR) = 0.06/0.2 = 0.3. keyed idx3 (0.25) -> correct idx0 (0.3).
--       Explanation/workingSolution both independently state 0.3.
-- ============================================================================

-- D.1.0 Pre-check: expect 1 row (as of 2026-10-15; re-run before applying in
-- case another copy has been made since this file was written).
SELECT sp.id::text AS pack_id, elem->>'question' AS question, elem->>'correctIndex' AS current_correct_index
FROM study_packs sp, jsonb_array_elements(sp.quiz) AS elem
WHERE elem->'choices' = '["0.3", "0.2", "0.15", "0.25"]'::jsonb
  AND (elem->>'correctIndex')::int = 3;

UPDATE study_packs SET quiz = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["0.3", "0.2", "0.15", "0.25"]'::jsonb
              AND (elem->>'correctIndex')::int = 3
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(quiz) AS elem)
WHERE quiz @> jsonb_build_array(jsonb_build_object('choices', '["0.3", "0.2", "0.15", "0.25"]'::jsonb, 'correctIndex', 3));

-- ============================================================================
-- D.2 — Labor efficiency variance (Actual=120hrs, Standard=100hrs, rate=$15/hr).
--       (120-100)x15 = $300; actual>standard hours -> unfavorable = idx2.
--       keyed idx0 ("$150 unfavorable") -> correct idx2 ("$300 unfavorable").
--       Wrong on both the magnitude and which choice pairs the right label.
-- ============================================================================

SELECT sp.id::text AS pack_id, elem->>'question' AS question, elem->>'correctIndex' AS current_correct_index
FROM study_packs sp, jsonb_array_elements(sp.quiz) AS elem
WHERE elem->'choices' = '["$150 unfavorable", "$300 favorable", "$300 unfavorable", "$150 favorable"]'::jsonb
  AND (elem->>'correctIndex')::int = 0;

UPDATE study_packs SET quiz = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["$150 unfavorable", "$300 favorable", "$300 unfavorable", "$150 favorable"]'::jsonb
              AND (elem->>'correctIndex')::int = 0
         THEN jsonb_set(elem, '{correctIndex}', '2'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(quiz) AS elem)
WHERE quiz @> jsonb_build_array(jsonb_build_object('choices', '["$150 unfavorable", "$300 favorable", "$300 unfavorable", "$150 favorable"]'::jsonb, 'correctIndex', 0));

-- ============================================================================
-- D.3 — Weighted average cost/unit (total cost $10,000, total units 500).
--       10,000/500 = $20. keyed idx2 ("$25") -> correct idx1 ("$20").
-- ============================================================================

SELECT sp.id::text AS pack_id, elem->>'question' AS question, elem->>'correctIndex' AS current_correct_index
FROM study_packs sp, jsonb_array_elements(sp.quiz) AS elem
WHERE elem->'choices' = '["$15", "$20", "$25", "$30"]'::jsonb
  AND (elem->>'correctIndex')::int = 2;

UPDATE study_packs SET quiz = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["$15", "$20", "$25", "$30"]'::jsonb
              AND (elem->>'correctIndex')::int = 2
         THEN jsonb_set(elem, '{correctIndex}', '1'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(quiz) AS elem)
WHERE quiz @> jsonb_build_array(jsonb_build_object('choices', '["$15", "$20", "$25", "$30"]'::jsonb, 'correctIndex', 2));

-- ============================================================================
-- D.4 — Straight-line depreciation (cost $50,000, salvage $5,000, life 9yr).
--       (50,000-5,000)/9 = $5,000. keyed idx3 ("$4,000") -> correct idx0 ("$5,000").
--       ⚠️ This one has 2 known copies as of 2026-10-15 (one belongs to a real
--       learner, d9743dfb-…, note PRIVATE) — the pack-agnostic match below
--       fixes the source AND every existing copy in one statement. Re-run the
--       pre-check immediately before applying in case a newer copy exists.
-- ============================================================================

SELECT sp.id::text AS pack_id, elem->>'question' AS question, elem->>'correctIndex' AS current_correct_index
FROM study_packs sp, jsonb_array_elements(sp.quiz) AS elem
WHERE elem->'choices' = '["$5,000", "$6,111", "$3,000", "$4,000"]'::jsonb
  AND (elem->>'correctIndex')::int = 3;
-- Expect 3 rows as of 2026-10-15 (1 source + 2 copies).

UPDATE study_packs SET quiz = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["$5,000", "$6,111", "$3,000", "$4,000"]'::jsonb
              AND (elem->>'correctIndex')::int = 3
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(quiz) AS elem)
WHERE quiz @> jsonb_build_array(jsonb_build_object('choices', '["$5,000", "$6,111", "$3,000", "$4,000"]'::jsonb, 'correctIndex', 3));

-- ============================================================================
-- POST-CHECK — expect 0 rows for all four patterns.
-- ============================================================================
SELECT
  (SELECT count(*) FROM study_packs sp, jsonb_array_elements(sp.quiz) e
     WHERE e->'choices' = '["0.3", "0.2", "0.15", "0.25"]'::jsonb AND (e->>'correctIndex')::int = 3) AS d1_still_wrong,
  (SELECT count(*) FROM study_packs sp, jsonb_array_elements(sp.quiz) e
     WHERE e->'choices' = '["$150 unfavorable", "$300 favorable", "$300 unfavorable", "$150 favorable"]'::jsonb AND (e->>'correctIndex')::int = 0) AS d2_still_wrong,
  (SELECT count(*) FROM study_packs sp, jsonb_array_elements(sp.quiz) e
     WHERE e->'choices' = '["$15", "$20", "$25", "$30"]'::jsonb AND (e->>'correctIndex')::int = 2) AS d3_still_wrong,
  (SELECT count(*) FROM study_packs sp, jsonb_array_elements(sp.quiz) e
     WHERE e->'choices' = '["$5,000", "$6,111", "$3,000", "$4,000"]'::jsonb AND (e->>'correctIndex')::int = 3) AS d4_still_wrong;
-- Expect all 4 columns = 0.

-- NOT included here, and NOT recommended for repair (per the finding, §2):
-- the APGAR, percentage-tax, and grain-size candidates this pass found are
-- all FALSE POSITIVES, independently re-derived as correctly keyed. Do not
-- "fix" them.
