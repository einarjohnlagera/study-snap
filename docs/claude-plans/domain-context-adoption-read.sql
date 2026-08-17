-- Domain Context adoption read — settles the open question in
-- docs/claude-plans/domain-context-catalog-assessment.md §8.
--
-- ⚠️ RUN AGAINST PRODUCTION. Read-only: five SELECTs, no writes, no DDL.
-- No psql meta-commands, so this pastes into DBeaver / JetBrains / pgAdmin as-is.
--
-- WHY THIS DECIDES SOMETHING: the assessment recommends NOT building the Domain Context
-- Catalog, on the grounds that the wall hit while authoring Engineering Economics is a
-- LEGIBILITY problem (nobody documented what belongs in each domain) rather than an
-- EXTENSIBILITY one. Query 1 and 2 can overturn that. Local dev holds 121 NULL / 2 set,
-- which is a fixture and evidence of nothing.


-- ===========================================================================
-- QUERY 1 — Is the vocabulary actually in use on public notes?
--
-- This is the single most decision-relevant number.
--   Overwhelmingly NULL          -> the vocabulary is barely used; a catalog is premature
--                                   regardless of anything else. Ship descriptions only.
--   All eight values in real use -> adoption is real; promotion pressure becomes plausible
--                                   and the catalog's trigger is closer than assessed.
-- ===========================================================================
SELECT coalesce(domain_context, '(none)') AS domain_context,
       count(*) AS public_notes
FROM notes
WHERE visibility = 'PUBLIC'
GROUP BY 1
ORDER BY 2 DESC;


-- ===========================================================================
-- QUERY 2 — Promotion pressure: which programs are still on the program-name
-- fallback past the governance floor of ~10 notes?
--
-- ADR-001's floor is ~10 notes whose treatment CANNOT be represented by an existing
-- value. Clearing 10 is necessary, not sufficient — the second clause decides. So read
-- this as "candidates to examine", never as "domains to create".
--
-- ⚠️ Several rows here are DELIBERATELY unclassified and must NOT be promoted:
-- ADR-001 is explicit that `High School`, `Grade School` and the Senior High strands
-- appear in this query by design. Expect them; do not treat them as demand.
-- ===========================================================================
SELECT coalesce(course_program, '(none)') AS course_program,
       count(*) AS null_context_public_notes
FROM notes
WHERE domain_context IS NULL
  AND visibility = 'PUBLIC'
GROUP BY 1
HAVING count(*) >= 10
ORDER BY 2 DESC;


-- ===========================================================================
-- QUERY 3 — Split the NULL population by owner role.
--
-- The assessment found `domain_context IS NULL` is overloaded FOUR ways: not-yet-promoted
-- thin program, deliberately-declined classification, learner personal notes (which must
-- never carry a value), and single-program curator notes where the catalog name already
-- suffices. Only the curator-owned public slice is even a candidate for promotion.
--
-- This is the query that tells you how much of the NULL count is real signal versus noise.
-- ===========================================================================
SELECT u.role,
       n.visibility,
       count(*) FILTER (WHERE n.domain_context IS NULL) AS null_context,
       count(*) FILTER (WHERE n.domain_context IS NOT NULL) AS has_context,
       count(*) AS total
FROM notes n
JOIN users u ON u.id = n.owner_user_id
GROUP BY 1, 2
ORDER BY total DESC;


-- ===========================================================================
-- QUERY 4 — How many curators are there really?
--
-- Domain Categories (the GPT suggestion for grouping the catalog) is justified entirely by
-- "keeping the admin experience manageable". ADR-001's query C recorded that every curated
-- note in production is admin-owned. If that still holds, the feature organises one
-- person's dropdown — which is the assessment's reason for declining it.
-- ===========================================================================
SELECT count(DISTINCT owner_user_id) AS distinct_public_note_authors
FROM notes
WHERE visibility = 'PUBLIC';

SELECT role,
       profile_type,
       count(*) AS accounts
FROM users
WHERE role = 'ADMIN' OR profile_type = 'TEACHER'
GROUP BY 1, 2
ORDER BY 3 DESC;


-- ===========================================================================
-- QUERY 5 — Engineering Economics: does it already exist, and how is it classified?
--
-- The concrete trigger. If notes already exist under a domain, that is a stronger signal
-- about where curators have actually been putting this material than any taxonomy argument.
-- ===========================================================================
SELECT coalesce(n.domain_context, '(none)') AS domain_context,
       coalesce(n.course_program, '(none)') AS course_program,
       n.subject,
       count(*) AS notes
FROM notes n
WHERE n.subject ILIKE '%econom%'
   OR n.title ILIKE '%econom%'
GROUP BY 1, 2, 3
ORDER BY notes DESC;


-- ===========================================================================
-- HOW TO READ THE RESULT AS A DECISION
--
-- Query 1 mostly '(none)'  AND  query 3 shows curator-owned public NULLs are few
--   -> Adoption is thin. Build descriptions beside the existing select (~40 LOC, no
--      schema) and stop. Revisit only when adoption exists.
--
-- Query 1 shows all eight in use  AND  query 2 lists programs past 10 that are NOT on the
-- deliberately-declined list
--   -> Promotion pressure is real. The catalog's trigger is closer than the assessment
--      concluded; re-read its Option B/C section before deciding.
--
-- Query 4 returns 1 author / 1 admin
--   -> Domain Categories organises one person's dropdown. Decline it, as assessed.
--
-- ⚠️ Whatever the numbers say, two code-level findings stand and are NOT settled by data,
-- because they are properties of the implementation rather than of adoption:
--   • `isQuantitativeContext` substring-matches the domain LABEL against ~49 keywords
--     across 8 call sites, so a curator-typed name that matches none silently turns
--     computation guidance OFF.
--   • `effectiveAuthoringDomain` returns `getLabel()`, so the display label IS the prompt
--     payload — renaming retroactively changes what the model was told.
-- Any catalog must address both regardless of what these queries return.
