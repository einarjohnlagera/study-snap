-- ============================================================================
-- BSIT Year 1: add Information Technology to the last 2 retained Discrete Mathematics Notes
-- Status: RUN by the owner and VERIFIED read-only (SELECT) on 2026-11-05. Kept as a RECORD; idempotent, but DO NOT RE-RUN.
--   Committed 2026-11-05 08:16:31Z (2 rows, Information Technology: Boolean Algebra and Digital Logic;
--   Mathematical Induction). Verified: both now carry 3 programs. Prepared 2026-11-05 by the Note Strategist session.
-- THE OWNER EXECUTES THIS. (CLAUDE.md: production writes are owner-only.)
--
-- WHY THIS FILE EXISTS
--   bsit-year1-dm-it-applicability.sql tagged 29 Notes (run and verified 2026-11-05). Two retained
--   Notes were read in full earlier and were not in that file: Boolean Algebra and Digital Logic, and
--   Mathematical Induction. The BSIT shaping TSV already lists them as Computer Engineering, Computer
--   Science, Information Technology. This file makes production match.
--   The 3 held-out Notes (Strong Induction; Proving Program Correctness; Logic in Programming and
--   Software Specifications) are NOT tagged and are not touched.
--
-- WHAT IT DOES
--   Adds ONE Applicable Program, 'Information Technology', to 2 existing Notes by exact id AND title.
--   At most 2 rows into note_course_program. Nothing else is touched. Existing associations are kept.
--
-- STATE READ 2026-11-05: both Notes carry Computer Engineering, Computer Science only.
--
-- HOW TO RUN (use psql, not an IDE console)
--   Dry run: psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f docs/claude-plans/bsit-year1/bsit-year1-dm-it-applicability-2-notes.sql
--   Apply:   the same command with -c "COMMIT;" appended.
-- ============================================================================

-- STEP 0 (read-only): expect 2 rows, guard_ok = true, has_it = false.
SELECT n.id, n.title, n.domain_context, n.subject,
       (n.domain_context = 'COMPUTING' AND n.subject = 'Discrete Mathematics') AS guard_ok,
       EXISTS (SELECT 1 FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id
                WHERE x.note_id = n.id AND cp.name = 'Information Technology') AS has_it
FROM notes n
WHERE n.id IN ('b0fe093f-58ff-4031-8253-3710a85522eb', 'a5e4e21d-c7ce-4cc9-9190-6935743795a2')
ORDER BY n.title;

BEGIN;

-- STEP 1: expect INSERT 0 2.
INSERT INTO note_course_program (id, note_id, course_program_id)
SELECT gen_random_uuid(), n.id, cp.id
FROM notes n CROSS JOIN course_programs cp
WHERE ((n.id = 'b0fe093f-58ff-4031-8253-3710a85522eb' AND n.title = 'Boolean Algebra and Digital Logic')
    OR (n.id = 'a5e4e21d-c7ce-4cc9-9190-6935743795a2' AND n.title = 'Mathematical Induction'))
  AND n.domain_context = 'COMPUTING' AND n.subject = 'Discrete Mathematics'
  AND cp.name = 'Information Technology'
ON CONFLICT (note_id, course_program_id) DO NOTHING;

-- STEP 2 (inside the transaction): expect 2 rows, each with 3 programs.
SELECT n.title, count(*) AS program_count,
       string_agg(cp.name, ', ' ORDER BY cp.name) AS programs
FROM notes n
JOIN note_course_program x ON x.note_id = n.id
JOIN course_programs cp ON cp.id = x.course_program_id
WHERE n.id IN ('b0fe093f-58ff-4031-8253-3710a85522eb', 'a5e4e21d-c7ce-4cc9-9190-6935743795a2')
GROUP BY n.title ORDER BY n.title;

-- COMMIT;   -- supplied by the -c "COMMIT;" above

-- ROLLBACK (only after a COMMIT you want to undo; valid when STEP 0 showed has_it = false)
-- BEGIN;
-- DELETE FROM note_course_program x USING course_programs cp
-- WHERE cp.id = x.course_program_id AND cp.name = 'Information Technology'
--   AND x.note_id IN ('b0fe093f-58ff-4031-8253-3710a85522eb', 'a5e4e21d-c7ce-4cc9-9190-6935743795a2');  -- expect DELETE 2
-- COMMIT;
