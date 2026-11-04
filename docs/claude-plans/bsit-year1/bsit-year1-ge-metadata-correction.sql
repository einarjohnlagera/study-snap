-- ============================================================================
-- BSIT Year 1: Applicable Programs correction for 3 GE Notes (Q3)
-- Status: PREPARED, NOT RUN. Prepared 2026-11-03 by the Note Strategist session.
-- THE OWNER EXECUTES THIS. (CLAUDE.md: production writes are owner-only.)
--
-- WHAT IT DOES
--   Adds two Applicable Programs ('Computer Science', 'Information Technology') to each of exactly
--   THREE existing Notes, identified by exact production id. Six rows into note_course_program at most.
--   It touches nothing else: not Subject, Domain Context, Authored Depth, content, visibility, Study
--   Packs or collections. It preserves every existing association (the six Education programs).
--
-- THE THREE NOTES (ids are the copies that sit in the BSCS Year 1 plans; read 2026-11-03)
--   5e2a6baa-791f-4bfb-9b1b-b4317623cd0b  Emerging Technologies and Their Social Impacts
--   556e7dbe-c43f-41f2-8d3f-fd2c4434b9e1  Spanish Colonization and Transformation of Philippine Society
--   769cd008-4a48-4ac0-b47d-620cbc23ffc0  Philippine Revolution and the First Philippine Republic
--   Each is also a member of the LET "General Education" plan. Adding programs only widens discovery;
--   it never reaches a prompt (ADR-001), so no Study Pack changes. Eight title-matching copies exist
--   for each title in production; THIS FILE DELIBERATELY TOUCHES ONLY THE THREE IDS ABOVE.
--
-- STATE READ 2026-11-03 (re-check with STEP 0 before running)
--   Each of the three carried only: Early Childhood Education, Elementary Education, Physical
--   Education, Secondary Education, Special Needs Education, Technical-Vocational Teacher Education.
--   Bodies read in full: program-neutral. Each has exactly one Study Pack.
--
-- EXPECTED RESULT
--   STEP 1 inserts 6 rows (3 notes x 2 programs) if STEP 0 shows no existing CS/IT association.
--   If STEP 0 shows some already present, ON CONFLICT DO NOTHING skips them (idempotent) and the
--   insert count is lower; that is fine, but then the ROLLBACK section below no longer applies
--   verbatim: delete only the rows STEP 1 reported inserting (see ROLLBACK).
--
-- HOW TO RUN (use psql, not an IDE console)
--   Dry run (the file BEGINs and does not COMMIT, so exiting rolls back):
--     psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f docs/claude-plans/bsit-year1/bsit-year1-ge-metadata-correction.sql
--   Apply: the same command with -c "COMMIT;" appended.
--     psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f docs/claude-plans/bsit-year1/bsit-year1-ge-metadata-correction.sql -c "COMMIT;"
-- ============================================================================

-- STEP 0 (read-only): guards. 0a must return 3 rows, each guard_ok = true.
SELECT n.id, n.title, n.domain_context, n.learner_level,
       (n.domain_context = 'GENERAL_EDUCATION') AS guard_ok,
       (SELECT string_agg(cp.name, ', ' ORDER BY cp.name)
          FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id
         WHERE x.note_id = n.id) AS current_programs
FROM notes n
WHERE n.id IN ('5e2a6baa-791f-4bfb-9b1b-b4317623cd0b',
               '556e7dbe-c43f-41f2-8d3f-fd2c4434b9e1',
               '769cd008-4a48-4ac0-b47d-620cbc23ffc0')
ORDER BY n.title;

-- 0b: both catalog programs must resolve to exactly one row each.
SELECT id, name FROM course_programs WHERE name IN ('Computer Science', 'Information Technology') ORDER BY name;

BEGIN;

-- STEP 1: idempotent, narrowly scoped insert.
INSERT INTO note_course_program (id, note_id, course_program_id)
SELECT gen_random_uuid(), n.id, cp.id
FROM notes n
CROSS JOIN course_programs cp
WHERE n.id IN ('5e2a6baa-791f-4bfb-9b1b-b4317623cd0b',
               '556e7dbe-c43f-41f2-8d3f-fd2c4434b9e1',
               '769cd008-4a48-4ac0-b47d-620cbc23ffc0')
  AND n.domain_context = 'GENERAL_EDUCATION'
  AND cp.name IN ('Computer Science', 'Information Technology')
ON CONFLICT (note_id, course_program_id) DO NOTHING;

-- STEP 2 (inside the transaction): verify before COMMIT. Expect 3 rows, each with 8 programs.
SELECT n.title, count(*) AS program_count,
       string_agg(cp.name, ', ' ORDER BY cp.name) AS programs
FROM notes n
JOIN note_course_program x ON x.note_id = n.id
JOIN course_programs cp ON cp.id = x.course_program_id
WHERE n.id IN ('5e2a6baa-791f-4bfb-9b1b-b4317623cd0b',
               '556e7dbe-c43f-41f2-8d3f-fd2c4434b9e1',
               '769cd008-4a48-4ac0-b47d-620cbc23ffc0')
GROUP BY n.title ORDER BY n.title;

-- COMMIT;   -- supplied by the -c "COMMIT;" above; if STEP 1 inserted other than 6 rows, or STEP 2
--           -- does not show 8 programs per note, ROLLBACK and report what STEP 0 showed.

-- ============================================================================
-- ROLLBACK (only after a COMMIT you want to undo; valid when STEP 0 showed NO existing CS/IT rows)
-- ============================================================================
-- BEGIN;
-- DELETE FROM note_course_program x
-- USING course_programs cp
-- WHERE cp.id = x.course_program_id
--   AND cp.name IN ('Computer Science', 'Information Technology')
--   AND x.note_id IN ('5e2a6baa-791f-4bfb-9b1b-b4317623cd0b',
--                     '556e7dbe-c43f-41f2-8d3f-fd2c4434b9e1',
--                     '769cd008-4a48-4ac0-b47d-620cbc23ffc0');   -- expect DELETE 6
-- COMMIT;
-- If STEP 0 showed some CS/IT rows already present, delete only the rows STEP 1 actually inserted
-- (those with created_at at or after the run time), never the pre-existing ones.
