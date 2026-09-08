-- v0.133.0 Education Family — POST-DEPLOY read
--
-- ⚠️ READ-ONLY. Every statement is a SELECT. Run AFTER `V142` has been applied in production.
--
-- ⚠️ WHY THIS FILE EXISTS. `V142` is a DATA migration, and this release's headline claim —
-- "the existing UI renders 'Add all 8 Education programs' with no code change" — is UNVERIFIED IN
-- PRODUCTION until the migration runs. `EducationProgramFamilyMigrationTest` proves the SQL does what
-- it says against H2 on a V106-shaped schema; it cannot prove that production's 44-row catalog, 23 rows
-- of which were created through `POST /course-programs` with random UUIDs, contains no row that
-- collides by NAME. The precondition read checked that on 2026-09-08 and found none — this read
-- confirms the outcome rather than the plan.
--
-- This is the instrument for `[CHECKPOINT — due deploy + 1 day]` in ROADMAP.md's Backlog Index.
-- ⚠️ KILL CRITERION IS ON Q2: anything other than exactly 8 rows means STOP and correct the catalog
-- BEFORE any curator authors against it. A wrong catalog row is visible to every curator immediately
-- and is awkward to withdraw once Notes reference it.

-- Q1 — The family exists exactly once.  EXPECT: 1 row, name 'Education'.
SELECT id, name
  FROM program_families
 WHERE name = 'Education';

-- Q2 — ⚠️ THE KILL-CRITERION QUERY. The family's membership.
--      EXPECT EXACTLY 8 ROWS, every one with exam_goal_slug = 'let':
--        Early Childhood Education, Education, Elementary Education, Physical Education,
--        Secondary Education, Special Needs Education, Teacher Certification,
--        Technical-Vocational Teacher Education
--      FEWER than 8  -> an INSERT silently failed.
--      MORE  than 8  -> a duplicate was created, or an unrelated row was swept into the family.
--      A NULL slug   -> the row is invisible under the LET exam goal while its siblings are not.
SELECT cp.name, cp.exam_goal_slug, cp.id
  FROM course_programs cp
  JOIN program_families pf ON pf.id = cp.program_family_id
 WHERE pf.name = 'Education'
 ORDER BY cp.name;

-- Q3 — The rename landed and left no sibling behind.
--      EXPECT: exactly 1 row, 'Special Needs Education', keeping id '20000000-...-000000000021'.
--      ⚠️ TWO ROWS means the migration inserted rather than renamed — the exact failure it was
--      written to avoid.
SELECT name, id, program_family_id, exam_goal_slug
  FROM course_programs
 WHERE name ILIKE 'Special Needs Education%'
 ORDER BY name;

-- Q4 — ⚠️ THE REGRESSION GUARD. Assigning a family to the existing Education row must not have
--      touched Engineering. EXPECT: Engineering still 18, Education 8.
SELECT pf.name AS family, COUNT(cp.id) AS programs
  FROM program_families pf
  LEFT JOIN course_programs cp ON cp.program_family_id = pf.id
 GROUP BY pf.name
 ORDER BY pf.name;

-- Q5 — No Note lost its link through the rename. `note_course_program` joins by id, so this should be
--      unchanged. EXPECT: whatever it was before (the 2026-09-08 read found ZERO), never fewer.
SELECT COUNT(*) AS notes_linked_to_renamed_row
  FROM note_course_program ncp
 WHERE ncp.course_program_id = '20000000-0000-0000-0000-000000000021';

-- Q6 — The owner-run profile write (docs/claude-plans/v0.133.0-owner-profile-string-update.sql).
--      EXPECT after it runs: old_name 0, new_name 1. If old_name is still 1, that write has not run.
SELECT
  COUNT(*) FILTER (WHERE course_program = 'Special Needs Education – Generalist') AS old_name,
  COUNT(*) FILTER (WHERE course_program = 'Special Needs Education')              AS new_name
  FROM users;

-- Q7 — DISTAL TIER, run later, not at deploy. Do the six NEW programs ever receive a Note?
--      ⚠️ DENOMINATOR CLAUSE: the curator population is effectively one person. A zero here is
--      "not yet measurable", NOT a verdict that the programs were the wrong ones — re-date rather
--      than conclude. It becomes readable only once real LET curriculum authoring has run.
SELECT cp.name, COUNT(ncp.note_id) AS notes
  FROM course_programs cp
  LEFT JOIN note_course_program ncp ON ncp.course_program_id = cp.id
 WHERE cp.id IN ('20000000-0000-0000-0000-000000000022',
                 '20000000-0000-0000-0000-000000000023',
                 '20000000-0000-0000-0000-000000000024',
                 '20000000-0000-0000-0000-000000000025',
                 '20000000-0000-0000-0000-000000000026',
                 '20000000-0000-0000-0000-000000000027')
 GROUP BY cp.name
 ORDER BY notes DESC, cp.name;
