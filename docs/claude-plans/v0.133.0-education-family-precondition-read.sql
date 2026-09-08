-- v0.133.0 Education Family — PRECONDITION read
--
-- ⚠️ READ-ONLY. Every statement is a SELECT. Run against production BEFORE the migration is written.
--
-- ⚠️⚠️ WHY THIS IS A PRECONDITION AND NOT A FORMALITY
-- V106 seeds THREE Engineering programs (Civil, Electrical, Mechanical). The brief reports EIGHTEEN in
-- production, and no later migration inserts any — V107, V108 and V117 touch course_programs for other
-- reasons. So the catalog has been extended through `POST /course-programs` in production, and
-- **a duplicate audit performed from the repo alone is UNSOUND.** An inserted duplicate is visible to
-- every curator immediately and is awkward to withdraw once Notes reference it.
--
-- Q1 and Q2 size the insert list. Q3 decides whether owner decision 1 (RENAME, settled 2026-09-08)
-- can proceed as a pure catalog edit.

-- Q1 — Every catalog entry that could collide with a proposed Education program.
--      ⚠️ Read the NAMING CONVENTION off the results, not off the brief: qualified suffixes use an
--      EN DASH (–), not a hyphen — "Senior High – ABM", "Special Needs Education – Generalist".
SELECT cp.name,
       pf.name AS family,
       cp.exam_goal_slug,
       cp.id
  FROM course_programs cp
  LEFT JOIN program_families pf ON pf.id = cp.program_family_id
 WHERE cp.name ILIKE '%educ%' OR cp.name ILIKE '%teach%' OR cp.name ILIKE '%BEEd%'
    OR cp.name ILIKE '%BSEd%' OR cp.name ILIKE '%BPEd%' OR cp.name ILIKE '%TVT%'
    OR cp.name ILIKE '%early child%' OR cp.name ILIKE '%special need%'
    OR cp.name ILIKE '%physical educ%' OR cp.name ILIKE '%certificat%'
 ORDER BY cp.name;

-- Q2 — Families and their current membership. Confirms Engineering's real size and that Education
--      does not already exist as a family.
SELECT pf.name AS family,
       COUNT(cp.id) AS programs
  FROM program_families pf
  LEFT JOIN course_programs cp ON cp.program_family_id = pf.id
 GROUP BY pf.name
 ORDER BY pf.name;

-- Q2b — Programs with no family at all. The existing `Education` row should appear here; anything else
--       unexpected is a sizing signal for a later release, NOT something to fix in this one.
SELECT name, exam_goal_slug
  FROM course_programs
 WHERE program_family_id IS NULL
 ORDER BY name;

-- Q3 — ⚠️ THE GATE ON THE RENAME. Five free-text course_program columns may hold the literal string
--      "Special Needs Education – Generalist". They are NOT updated by renaming the catalog row, and
--      no repository method resolves a catalog entry by name — so nothing BREAKS, but a stale string
--      stops matching the catalog, which affects discovery and the wishlist's normalized matching.
--
--      IF EVERY COUNT IS 0 the rename is a pure catalog edit and decision 1 proceeds as settled.
--      IF ANY IS NON-ZERO, what happens to those strings is a FURTHER OWNER DECISION and a
--      PRODUCTION WRITE — the owner's to run, never Claude's.
SELECT 'notes.course_program' AS source, COUNT(*) AS hits
  FROM notes WHERE course_program ILIKE '%Special Needs Education%'
UNION ALL
SELECT 'note_collections.course_program', COUNT(*)
  FROM note_collections WHERE course_program ILIKE '%Special Needs Education%'
UNION ALL
SELECT 'users.course_program', COUNT(*)
  FROM users WHERE course_program ILIKE '%Special Needs Education%'
UNION ALL
SELECT 'bulk_generation_result.course_program', COUNT(*)
  FROM bulk_generation_result WHERE course_program ILIKE '%Special Needs Education%'
UNION ALL
SELECT 'wishlist.course_program', COUNT(*)
  FROM official_study_plan_wishlist WHERE course_program ILIKE '%Special Needs Education%'
UNION ALL
SELECT 'wishlist.normalized_course_program', COUNT(*)
  FROM official_study_plan_wishlist WHERE normalized_course_program ILIKE '%Special Needs Education%';

-- Q4 — Notes currently linked to the row being renamed, by ID rather than by name. This is the
--      population the rename guard test must model: a Note linked BEFORE the rename must still be
--      linked after it and must render the NEW name. A fixture created after the rename passes
--      trivially and proves nothing.
SELECT COUNT(*) AS notes_linked_to_special_needs_generalist
  FROM note_course_program ncp
  JOIN course_programs cp ON cp.id = ncp.course_program_id
 WHERE cp.name ILIKE 'Special Needs Education%';
