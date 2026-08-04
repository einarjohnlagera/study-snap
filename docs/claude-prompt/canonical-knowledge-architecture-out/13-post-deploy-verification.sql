-- Post-deploy verification for v0.69.0. Run against PRODUCTION immediately after deploy.
-- Read-only: no INSERT, UPDATE, DELETE, or DDL.
--
-- ============================================================================
-- WHY THIS EXISTS
-- ============================================================================
-- A successful deploy proves the migrations RAN. It does not prove they MATCHED anything.
-- Three specific ways v0.69.0 can deploy green and still be wrong:
--
--   1. V104/V105's guards are derived from their own UPDATE predicates, so a label mistyped in
--      both places matches zero rows in both and the migration is a silent no-op that reports
--      success (recorded in V104's header).
--   2. V105's classification UPDATEs are keyed by NOTE ID from a curator review run on
--      2026-08-03. A `High School` note created between then and deploy was never in that list,
--      so it is silently skipped -- and the RAISE NOTICE only counts the IDs it knows about,
--      which cannot detect an addition.
--   3. The strand UPDATE matches `ILIKE 'Senior High%'`. A bare strand label (`STEM`, `ABM`,
--      `HUMSS`, `GAS`) has no such prefix and was never enumerated by any audit query.
--
-- None of these fail loudly. All three are cheap to check once, here.

-- ============================================================================
-- QUERY 0 — did the migrations actually apply?
-- ============================================================================

SELECT version, description, success, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 5;

-- Expected: 105, 104, 103, 102 all present with success = t. Anything less means the deploy did
-- not carry the release and NOTHING below is meaningful — stop and check what shipped.

-- ============================================================================
-- QUERY A — did V104 and V105 write what they claimed?
-- ============================================================================
-- One row per legacy label with its resulting axes. Compare against the expected table below.

SELECT
    n.course_program,
    n.learner_level,
    n.domain_context,
    count(*) AS notes
FROM notes n
WHERE n.course_program ILIKE '%grade school%'
   OR n.course_program ILIKE '%junior high%'
   OR n.course_program ILIKE '%high school%'
   OR n.course_program ILIKE '%senior high%'
GROUP BY 1, 2, 3
ORDER BY 1, 2 NULLS FIRST;

-- Expected after a correct deploy:
--   'Grade School'        GRADE_SCHOOL  GENERAL_EDUCATION   3
--   'Junior High'         JUNIOR_HIGH   GENERAL_EDUCATION  24
--   'High School'         JUNIOR_HIGH   GENERAL_EDUCATION   3
--   'High School'         SENIOR_HIGH   GENERAL_EDUCATION   1
--   'High School'         NULL          NULL                6   <-- deliberately unclassified
--   'Senior High – STEM'  SENIOR_HIGH   NULL                4
--   'Senior High – ABM'   SENIOR_HIGH   NULL                4
--   'Senior High – HUMSS' SENIOR_HIGH   NULL                3
--
-- Any 'Grade School'/'Junior High'/'Senior High%' row still showing NULL learner_level is a
-- MISS -- either a new note arrived after the audit, or a label does not match the literals.
-- A 'High School' row with NULL beyond the expected 6 is failure mode 2 above.

-- ============================================================================
-- QUERY B — did any new legacy-labelled note arrive after the audit?
-- ============================================================================
-- This is the one the migration's own guards structurally cannot catch: V105 is ID-keyed, so a
-- note created after 2026-08-03 with one of these labels is invisible to it.

SELECT
    n.id,
    n.title,
    n.course_program,
    n.learner_level,
    n.domain_context,
    n.created_at
FROM notes n
WHERE (n.course_program ILIKE '%grade school%'
       OR n.course_program ILIKE '%junior high%'
       OR n.course_program ILIKE '%high school%'
       OR n.course_program ILIKE '%senior high%')
  AND n.learner_level IS NULL
  AND n.id NOT IN (
      -- the six deliberately-unclassified High School notes
      '7411902d-1264-4fd0-9908-d422cdd9e862',
      'bdfaad4c-4341-4d61-abd8-3963a864f32f',
      '84d49a06-c46c-4fa3-aab8-a48aebe58f0f',
      '9844846d-c0d1-4ef3-8528-e441bacd9c10',
      'e33f27fb-827d-4b38-a9db-c31858abef3a',
      '2ec3253f-d51b-4541-87e7-873020bed467'
  )
ORDER BY n.created_at DESC;

-- Expected: ZERO rows. Any row is a note the backfill missed. Check `created_at`: after
-- 2026-08-03 means it arrived post-audit and needs classifying; before means a label the
-- literals did not match, which is the more worrying case.

-- ============================================================================
-- QUERY C — bare strand labels the prefix match cannot reach
-- ============================================================================
-- `ILIKE 'Senior High%'` requires the prefix. A note labelled just `STEM` or `ABM` was never
-- enumerated by any audit query and was never in scope for V105.

SELECT
    n.course_program,
    count(*)                        AS notes,
    count(n.learner_level)          AS has_level,
    count(n.domain_context)         AS has_context
FROM notes n
WHERE upper(btrim(n.course_program)) IN ('STEM', 'ABM', 'HUMSS', 'GAS', 'TVL')
GROUP BY 1
ORDER BY notes DESC;

-- Expected: ZERO rows. Any row is a genuine gap -- those notes still feed a strand label into
-- the authoring-domain line with no learner level, which is the defect this release set out to
-- remove. Not a deploy failure; a scope gap for v0.70.0.

-- ============================================================================
-- QUERY D — sanity: nothing outside the legacy set was touched
-- ============================================================================
-- V104/V105 should have written exactly 27 + 4 + 11 = 42 rows. If more notes carry the new axes
-- than that, either a curator has been authoring (fine, and expected once R4 passes) or a
-- migration over-matched (not fine).

SELECT
    count(*) FILTER (WHERE n.domain_context IS NOT NULL) AS notes_with_domain_context,
    count(*) FILTER (WHERE n.learner_level IS NOT NULL)  AS notes_with_learner_level,
    count(*) FILTER (WHERE n.domain_context = 'GENERAL_EDUCATION') AS general_education_notes
FROM notes n;

-- Expected immediately after deploy, before any authoring:
--   notes_with_domain_context = 31   (27 pure-level + 4 classified High School)
--   notes_with_learner_level  = 42   (31 above + 11 Senior High strands)
--   general_education_notes   = 31
--
-- Higher numbers with no authoring yet means over-match -- investigate before authoring on top.

-- ============================================================================
-- WHAT TO DO NEXT
-- ============================================================================
-- If Queries 0/A/D match and B/C return zero rows, the backfill landed correctly and the
-- release is verified in production.
--
-- Then, in this order:
--   1. R4's four steps (RELEASES.md v0.69.0, "Verification owed"). BULK AUTHORING MUST NOT
--      BEGIN UNTIL STEP 2 PASSES. Checkpoint due 2026-08-18.
--   2. 11-program-vocabulary-seed.sql  -> unblocks PR 5 in v0.70.0
--   3. 12-pool-bank-relevel-impact.sql -> decides whether PR 6a needs a pre-stamping migration.
--      Now that V104/V105 have applied, run the FULL Query A/B/C, not the V101 variant.
