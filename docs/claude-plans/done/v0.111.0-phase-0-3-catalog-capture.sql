-- v0.111.0 Phase 0.3 — capture the LIVE course-program catalog.
-- Read-only. Run against PRODUCTION. Owner-executed; nothing in the repo can answer it.
--
-- WHY THIS IS LOAD-BEARING, NOT BOOKKEEPING
-- ADR-001's failure condition -- reviewed at EVERY kickoff -- is a RATIO: Domain Context values
-- against course programs. The recorded denominator is 21, taken from V106's seed. That figure is
-- STALE: the catalog is admin-manageable at runtime, and 'Architectural Engineering' is a live chip
-- that appears in NO migration. So production already holds at least 22.
--
-- ⚠️ Computing the ratio from 21 repeats the EXACT error ADR-001 was corrected for on 2026-08-31,
-- when the denominator turned out to be the pre-catalog free-text spread rather than catalog rows.
-- A larger denominator makes the ratio move LESS, and the v0.111.0 amendment must be argued against
-- the real number.
--
-- ⚠️ Q1 returns the NAMES, not just a count, on purpose. The amendment restates the failure condition
-- in terms of the naming rule ("no value may equal a catalog program name unless it is a board
-- subject-area name"), which cannot be argued from a bare integer.
--
-- PHASE 0 STATUS
--   0.1  Is 'Architectural Engineering' in the production catalog?  ANSWERED YES (owner screenshot;
--        absent from db/migration/, so added at runtime). Q3 below re-confirms it and finds any others.
--   0.2  Is Medicine already seeded?  ANSWERED YES, verified in the repo 2026-09-04 --
--        V106__course_program_catalog.sql, id 20000000-0000-0000-0000-000000000014. It is part of the
--        current design problem, not a horizon item.
--   0.3  Capture the live catalog.  THIS FILE.
--
-- Domain Context ADOPTION (the zero-usage question for NURSING / ACCOUNTANCY / PROFESSIONAL_EDUCATION)
-- is a SEPARATE Phase 2 input and already has its own query: docs/claude-plans/domain-context-adoption-read.sql.
-- Do not fold it in here.

-- ---------------------------------------------------------------------------
-- Q1. The live catalog, in full. This is the denominator AND the naming evidence.
-- ---------------------------------------------------------------------------
SELECT
    cp.name                              AS program_name,
    pf.name                              AS program_family,
    cp.exam_goal_slug,
    cp.created_at
FROM course_programs cp
LEFT JOIN program_families pf ON pf.id = cp.program_family_id
ORDER BY cp.created_at, cp.name;

-- ---------------------------------------------------------------------------
-- Q2. The headline denominator, and the ratio arithmetic stated explicitly.
--     8 is today's Domain Context count (DomainContext.java, verified 2026-09-04).
-- ---------------------------------------------------------------------------
SELECT
    COUNT(*)                                            AS live_course_programs,
    21                                                  AS v106_seeded_count,
    COUNT(*) - 21                                       AS added_at_runtime,
    8                                                   AS domain_context_values_today,
    ROUND(8.0 / COUNT(*), 3)                            AS ratio_today,
    ROUND(9.0 / COUNT(*), 3)                            AS ratio_if_one_value_added,
    ROUND(10.0 / COUNT(*), 3)                           AS ratio_if_two_values_added,
    ROUND(11.0 / COUNT(*), 3)                           AS ratio_if_three_values_added,
    ROUND(12.0 / COUNT(*), 3)                           AS ratio_if_four_values_added
FROM course_programs;

-- ---------------------------------------------------------------------------
-- Q3. Which programs were added AFTER V106's seed -- i.e. through the admin-manageable
--     catalog at runtime. This is the set the stale denominator misses.
--     Expected to include 'Architectural Engineering'.
-- ---------------------------------------------------------------------------
SELECT
    cp.name AS program_added_at_runtime,
    cp.exam_goal_slug,
    cp.created_at
FROM course_programs cp
WHERE cp.name NOT IN (
        'Education',
        'Architecture',
        'Nursing',
        'Accountancy',
        'Civil Engineering',
        'Information Technology',
        'Pharmacy',
        'Electrical Engineering',
        'Mechanical Engineering',
        'Physical Therapy',
        'Senior High – ABM',
        'Senior High – STEM',
        'Senior High – HUMSS',
        'Medicine',
        'Criminology',
        'Law',
        'Aviation',
        'Business Administration',
        'Psychology',
        'Radiologic Technology',
        'Special Needs Education – Generalist'
    )
ORDER BY cp.created_at, cp.name;
