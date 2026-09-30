-- Official Review Set vocabulary audit — RUN AGAINST PRODUCTION
--
-- Purpose: decide the shape of the program-resolution layer for the onboarding Intent Router
-- (docs/claude-plans/onboarding-activation-and-intent-router.md). Ratified 2026-08-06: audit the real
-- data first; do not introduce normalization we may not need.
--
-- Context for whoever runs this:
--   * "Official Review Set" = note_collections row with visibility='PUBLIC' AND parent_collection_id IS NULL.
--     There is no is_official flag; official-ness is procedural (admin-owned + published).
--   * note_collections.course_program is a free VARCHAR(120) with NO catalog FK. V106 added
--     course_program_id to `notes` and `users` only.
--   * Matching today is exact and case-sensitive (NoteCollectionRepository.java:79 has no IgnoreCase),
--     with only dash/whitespace normalization applied beforehand.
--   * course_programs holds 21 seeded rows; exactly 4 carry an exam_goal_slug
--     (let→Education, ale→Architecture, pnle→Nursing, cpale→Accountancy).
--
-- THE DECISION THIS DRIVES:
--   Query 1 answers whether plans are tagged with catalog names, exam slugs, or a mixture.
--     - all catalog names  -> resolution layer needs case-insensitivity only (use the existing,
--                             currently-unused CourseProgramNormalizationUtils.normalizeForLookup).
--     - all exam slugs     -> needs slug->name resolution via ExamGoalCourseProgramProvider.
--     - MIXED              -> this is a LIVE MATCHING BUG, not a fixture artifact. Document it
--                             explicitly and design the resolution layer around both conventions.


-- ============================================================================
-- Query 1 — THE DECISIVE ONE. What vocabulary do published root collections use?
-- ============================================================================
SELECT
    c.course_program,
    COUNT(*)                                             AS plans,
    -- exact catalog-name match (what the code does today, case-sensitively)
    BOOL_OR(cp_exact.name IS NOT NULL)                   AS matches_catalog_name_exact,
    -- case-insensitive catalog-name match (what normalizeForLookup would buy us)
    BOOL_OR(cp_ci.name IS NOT NULL)                      AS matches_catalog_name_ci,
    -- is the tag actually an exam-goal slug rather than a program name?
    BOOL_OR(cp_slug.exam_goal_slug IS NOT NULL)          AS matches_exam_goal_slug,
    CASE
        WHEN BOOL_OR(cp_exact.name IS NOT NULL) THEN 'catalog name (exact)'
        WHEN BOOL_OR(cp_ci.name    IS NOT NULL) THEN 'catalog name (case differs)'
        WHEN BOOL_OR(cp_slug.exam_goal_slug IS NOT NULL) THEN 'exam goal slug'
        WHEN c.course_program IS NULL           THEN 'NULL — untagged'
        ELSE 'UNRECOGNISED — neither catalog name nor slug'
    END                                                  AS verdict
FROM note_collections c
LEFT JOIN course_programs cp_exact ON cp_exact.name = c.course_program
LEFT JOIN course_programs cp_ci    ON LOWER(cp_ci.name) = LOWER(TRIM(c.course_program))
LEFT JOIN course_programs cp_slug  ON LOWER(cp_slug.exam_goal_slug) = LOWER(TRIM(c.course_program))
WHERE c.visibility = 'PUBLIC'
  AND c.parent_collection_id IS NULL
GROUP BY c.course_program
ORDER BY plans DESC, c.course_program;


-- ============================================================================
-- Query 2 — Availability. How many published root plans actually QUALIFY?
-- Mirrors onboarding/page.tsx:730-737: itemCount > 0 AND readyCount > 0.
-- readyCount = items whose note has a generated Study Pack.
-- NOTE: counts roll up from child collections, so a Goal can qualify with zero DIRECT items.
--       This query counts direct + child items to match rollUpCounts().
-- ============================================================================
WITH plan_tree AS (
    SELECT c.id AS root_id, c.course_program, c.title, c.id AS node_id
    FROM note_collections c
    WHERE c.visibility = 'PUBLIC' AND c.parent_collection_id IS NULL
    UNION ALL
    SELECT p.id AS root_id, p.course_program, p.title, ch.id AS node_id
    FROM note_collections p
    JOIN note_collections ch ON ch.parent_collection_id = p.id
    WHERE p.visibility = 'PUBLIC' AND p.parent_collection_id IS NULL
)
SELECT
    t.course_program,
    t.title,
    COUNT(i.id)                                                    AS item_count,
    COUNT(sp.id)                                                   AS ready_count,
    (COUNT(i.id) > 0 AND COUNT(sp.id) > 0)                         AS qualifies_for_practice_first
FROM plan_tree t
LEFT JOIN note_collection_items i ON i.collection_id = t.node_id
LEFT JOIN study_packs sp          ON sp.note_id = i.note_id
GROUP BY t.root_id, t.course_program, t.title
ORDER BY qualifies_for_practice_first DESC, ready_count DESC;


-- ============================================================================
-- Query 3 — Coverage. Which catalog programs CAN be served by Branch A,
-- and which fall to the "Coming soon for {Program}" state?
-- This sizes how load-bearing the unsupported-program copy is.
-- ============================================================================
SELECT
    cp.name                                              AS catalog_program,
    cp.exam_goal_slug,
    COUNT(DISTINCT c.id)                                 AS published_root_plans,
    CASE WHEN COUNT(DISTINCT c.id) > 0
         THEN 'Branch A available'
         ELSE 'Coming soon state' END                    AS onboarding_branch
FROM course_programs cp
LEFT JOIN note_collections c
       ON c.visibility = 'PUBLIC'
      AND c.parent_collection_id IS NULL
      AND (
            c.course_program = cp.name
         OR LOWER(TRIM(c.course_program)) = LOWER(cp.name)
         OR LOWER(TRIM(c.course_program)) = LOWER(cp.exam_goal_slug)
      )
GROUP BY cp.name, cp.exam_goal_slug
ORDER BY published_root_plans DESC, cp.name;


-- ============================================================================
-- Query 4 — What program values do real USERS hold, and can they be served?
-- Sizes the gap between what onboarding collects and what the catalog/plans can represent.
-- Directly sizes pressure-test finding C8.
-- ============================================================================
SELECT
    u.course_program                                     AS user_program,
    COUNT(*)                                             AS users,
    BOOL_OR(cp.name IS NOT NULL)                         AS in_catalog,
    EXISTS (
        SELECT 1 FROM note_collections c
        WHERE c.visibility = 'PUBLIC' AND c.parent_collection_id IS NULL
          AND LOWER(TRIM(c.course_program)) = LOWER(TRIM(u.course_program))
    )                                                    AS has_published_plan
FROM users u
LEFT JOIN course_programs cp ON LOWER(cp.name) = LOWER(TRIM(u.course_program))
WHERE u.course_program IS NOT NULL AND TRIM(u.course_program) <> ''
GROUP BY u.course_program
ORDER BY users DESC;


-- ============================================================================
-- Query 5 — Onboarding completion health, for baseline before the redesign.
-- The ROADMAP records ~40% of accounts with profile_type still NULL. Re-read it here
-- so the redesign has a pre-change baseline to be measured against.
-- ============================================================================
SELECT
    COUNT(*)                                                          AS all_accounts,
    COUNT(*) FILTER (WHERE profile_type IS NULL)                      AS null_profile_type,
    ROUND(100.0 * COUNT(*) FILTER (WHERE profile_type IS NULL) / NULLIF(COUNT(*), 0), 1)
                                                                      AS pct_null_profile_type,
    COUNT(*) FILTER (WHERE onboarding_completed_at IS NULL)            AS never_completed,
    COUNT(*) FILTER (WHERE course_program IS NULL OR TRIM(course_program) = '')
                                                                      AS null_course_program,
    -- the fire-and-forget loss cohort: completed onboarding but context never landed
    COUNT(*) FILTER (WHERE onboarding_completed_at IS NOT NULL
                       AND (course_program IS NULL OR TRIM(course_program) = ''))
                                                                      AS completed_but_no_program,
    COUNT(*) FILTER (WHERE onboarding_completed_at IS NOT NULL AND learner_level IS NULL)
                                                                      AS completed_but_no_level
FROM users;
