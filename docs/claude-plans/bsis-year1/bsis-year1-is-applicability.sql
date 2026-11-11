-- ============================================================================
-- BSIS Year 1: add 'Information Systems' to Applicable Programs on 5 existing GE Notes (Q7)
-- REVISION 2 (2026-11-06), replaces revision 1. Status: RUN by the owner on 2026-11-06 and VERIFIED read-only by Claude the same day (see bsis-year1-fis-preproduction-readiness.md section 11). KEPT AS A RECORD; DO NOT RE-RUN (its preconditions now stop it). Verified: all five Notes now carry Information Systems (one row each); programs equal the snapshot plus Information Systems; bodies, updated_at and Study Packs unchanged. Originally PREPARED for OWNER INSPECTION.
-- THE OWNER EXECUTES THIS (CLAUDE.md: production writes are owner-only). Nothing in this file has been executed
-- against production. It WAS exercised against a throwaway local PostgreSQL 16 (not production) in all three modes
-- and against deliberate precondition failures; see "TESTED" below.
--
-- WHAT CHANGED FROM REVISION 1 (Note Strategist review, 2026-11-06)
--   A. ONE SESSION. Revision 1 told the owner to run the file and then a separate `psql -c "COMMIT;"`. Two psql
--      invocations are two connections: the BEGIN of the first is rolled back when it exits, so the COMMIT of the
--      second commits nothing (or the wrong thing). This revision does BEGIN, preconditions, INSERT, verification
--      and COMMIT-or-ROLLBACK in ONE psql run on ONE connection, chosen by a psql variable. Any error stops the run
--      (ON_ERROR_STOP) and the open transaction is rolled back when psql exits.
--   B. HARD PRECONDITIONS. Failures now RAISE EXCEPTION (the run stops); they are not informational SELECTs.
--   C. EXACT AUDIT. The insert uses INSERT ... RETURNING and prints the exact inserted association ids plus a
--      ready-to-save, id-exact rollback statement. `created_at = now()` is NOT used to identify rows. The global
--      count of Information Systems associations is compared before and after instead.
--
-- WHAT IT DOES
--   Inserts exactly FIVE rows into note_course_program: ('Information Systems') x 5 Notes, identified by exact id.
--   It touches nothing else: not Subject, Domain Context, Authored Depth, content, visibility, Study Packs or
--   collections, and it preserves every existing program association. Applicable Programs is discovery only and never
--   reaches a prompt (ADR-001), so no Study Pack changes.
--
-- THE HELD NOTE IS NOT HERE. "Global Citizenship and Global Civil Society"
--   (45133a32-4440-4277-8b50-931eaec3788f) stays on HOLD until its RA 10364 wording correction is applied and
--   verified (see bsis-year1-global-citizenship-ra10364-correction.sql). Its tag has its own script,
--   bsis-year1-is-applicability-global-citizenship-held.sql, which REFUSES to run until the corrected body is in
--   place. THIS script also fails if the held Note is in the target list or has already been tagged.
--
-- THE FIVE NOTES (read from production 2026-11-06). Programs in C collation order.
--   5e2a6baa-791f-4bfb-9b1b-b4317623cd0b  Emerging Technologies and Their Social Impacts
--   556e7dbe-c43f-41f2-8d3f-fd2c4434b9e1  Spanish Colonization and Transformation of Philippine Society
--   769cd008-4a48-4ac0-b47d-620cbc23ffc0  Philippine Revolution and the First Philippine Republic
--       each: Computer Science, Early Childhood Education, Elementary Education, Information Technology,
--             Physical Education, Secondary Education, Special Needs Education, Technical-Vocational Teacher Education
--   3b53ce07-08db-4fac-b4fe-8525462d74a5  Film and Moving-Image Art
--   6256e54b-1501-44cf-abf3-88b2b4d67a0e  Art, Popular Culture and Media in Contemporary Society
--       each: Computer Science, Information Technology
--   Every Note: PUBLIC, GENERAL_EDUCATION, COLLEGE, exactly one Study Pack, 'Information Systems' not yet present.
--   Proposed values after the run: the existing list plus 'Information Systems' (9 and 3 programs).
--   Catalog: course_programs 'Information Systems' = 338d3e66-fee4-4400-afa7-0af0931444c8 (exactly one row).
--   note_course_program: id, note_id, course_program_id, created_at (default now()); UNIQUE (note_id, course_program_id).
--
-- NO DOMAIN CONTEXT CONFLICT. All five have an explicit Domain Context, so the rule "unset is legal only with one
--   program" does not apply.
--
-- THE PRECONDITIONS (any failure stops the run before anything is written)
--   P1 exactly 5 of the target Notes exist            P5 learner level is COLLEGE and visibility PUBLIC
--   P2 each title matches                              P6 current programs equal the expected snapshot above
--   P3 Domain Context is GENERAL_EDUCATION             P7 the catalog has exactly one 'Information Systems' with the expected id
--   P4 no target already has 'Information Systems'     P8 the held Note is not a target and is not already tagged
--   After the INSERT, the run also asserts: exactly 5 rows returned; each target now has the snapshot plus
--   'Information Systems' and nothing else; the global 'Information Systems' association count rose by exactly 5;
--   and notes content, updated_at and Study Pack counts are unchanged.
--
-- HOW TO RUN (psql 10 or later, not an IDE console; ONE command per mode, ONE connection each)
--   1) DRY RUN (default; everything executes, then ROLLBACK; nothing is kept). Save the output:
--        psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f docs/claude-plans/bsis-year1/bsis-year1-is-applicability.sql | tee bsis-is-dryrun.log
--   2) INSPECT the output: five "inserted" rows and the rollback statement are printed even in a dry run.
--   3) APPLY (explicit owner action; the commit happens at the end of THE SAME run and only with BOTH variables):
--        psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v mode=commit -v confirm=owner-approved \
--             -f docs/claude-plans/bsis-year1/bsis-year1-is-applicability.sql | tee bsis-is-apply.log
--      The preconditions run again inside the apply transaction, so a change between the dry run and the apply
--      stops it instead of being overwritten.
--   OPTIONAL interactive inspection before committing (still ONE connection): start `psql "$DATABASE_URL"`,
--      then `\set ON_ERROR_STOP on`, `\set mode hold`, `\i docs/claude-plans/bsis-year1/bsis-year1-is-applicability.sql`.
--      The transaction is left OPEN at the end; type COMMIT; or ROLLBACK; yourself. Closing psql without
--      either rolls it back.
--   Modes: dry (default, ROLLBACK) | hold (leave open, interactive only) | commit (COMMIT, needs confirm=owner-approved).
--   Anything else is refused. The script never commits by default.
--
-- TESTED (2026-11-06, throwaway local PostgreSQL 16.2, NOT production): dry, hold-style and commit modes;
--   a missing Note, a changed title, a wrong Domain Context, a wrong program snapshot, a pre-existing association,
--   a duplicated catalog row, the held Note in the target list, a bad mode, and commit without confirmation each
--   stopped the run with nothing written. The local schema was minimal (the columns this script reads), so a
--   production-only trigger or constraint would not have been exercised; the script's own post-insert checks
--   (global count, content and updated_at unchanged) are the guard for that.
-- ============================================================================

\set ON_ERROR_STOP on
\if :{?mode} \else \set mode dry \endif
\if :{?confirm} \else \set confirm none \endif
SELECT (:'mode' IN ('dry','hold','commit')) AS mode_ok,
       (:'mode' <> 'commit' OR :'confirm' = 'owner-approved') AS confirm_ok \gset
\if :mode_ok \else
  DO $refuse$ BEGIN RAISE EXCEPTION 'REFUSED: mode must be dry, hold or commit.'; END $refuse$;
\endif
\if :confirm_ok \else
  DO $refuse$ BEGIN RAISE EXCEPTION 'REFUSED: mode=commit also needs -v confirm=owner-approved.'; END $refuse$;
\endif
\echo 'BSIS IS-applicability run, mode =' :mode

BEGIN;

-- Expected snapshot (the five targets) and the held Note.
CREATE TEMP TABLE bsis_expected (note_id uuid PRIMARY KEY, title text NOT NULL, programs text NOT NULL) ON COMMIT DROP;
INSERT INTO bsis_expected VALUES
 ('5e2a6baa-791f-4bfb-9b1b-b4317623cd0b', 'Emerging Technologies and Their Social Impacts',
  'Computer Science|Early Childhood Education|Elementary Education|Information Technology|Physical Education|Secondary Education|Special Needs Education|Technical-Vocational Teacher Education'),
 ('556e7dbe-c43f-41f2-8d3f-fd2c4434b9e1', 'Spanish Colonization and Transformation of Philippine Society',
  'Computer Science|Early Childhood Education|Elementary Education|Information Technology|Physical Education|Secondary Education|Special Needs Education|Technical-Vocational Teacher Education'),
 ('769cd008-4a48-4ac0-b47d-620cbc23ffc0', 'Philippine Revolution and the First Philippine Republic',
  'Computer Science|Early Childhood Education|Elementary Education|Information Technology|Physical Education|Secondary Education|Special Needs Education|Technical-Vocational Teacher Education'),
 ('3b53ce07-08db-4fac-b4fe-8525462d74a5', 'Film and Moving-Image Art', 'Computer Science|Information Technology'),
 ('6256e54b-1501-44cf-abf3-88b2b4d67a0e', 'Art, Popular Culture and Media in Contemporary Society', 'Computer Science|Information Technology');

-- Before-state: content hash, updated_at and Study Pack count of every target, and the global IS association count.
CREATE TEMP TABLE bsis_before ON COMMIT DROP AS
SELECT n.id AS note_id, md5(n.content) AS content_md5, n.updated_at,
       (SELECT count(*) FROM study_packs s WHERE s.note_id = n.id) AS packs
FROM notes n JOIN bsis_expected e ON e.note_id = n.id;
CREATE TEMP TABLE bsis_scalar ON COMMIT DROP AS
SELECT (SELECT count(*) FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id
         WHERE cp.name = 'Information Systems') AS is_before;

-- PRECONDITIONS. Any failure raises and stops the run (ON_ERROR_STOP); the open transaction then rolls back.
DO $pre$
DECLARE
  held constant uuid := '45133a32-4440-4277-8b50-931eaec3788f';
  is_id constant uuid := '338d3e66-fee4-4400-afa7-0af0931444c8';
  r record;
  actual text;
BEGIN
  IF (SELECT count(*) FROM notes n JOIN bsis_expected e ON e.note_id = n.id) <> 5 THEN
    RAISE EXCEPTION 'P1 failed: expected exactly 5 target Notes, found %',
      (SELECT count(*) FROM notes n JOIN bsis_expected e ON e.note_id = n.id);
  END IF;
  FOR r IN SELECT e.note_id, e.title AS exp_title, e.programs AS exp_programs, n.title, n.domain_context,
                  n.learner_level, n.visibility
           FROM bsis_expected e JOIN notes n ON n.id = e.note_id ORDER BY e.title
  LOOP
    IF r.title IS DISTINCT FROM r.exp_title THEN
      RAISE EXCEPTION 'P2 failed for %: title is "%", expected "%"', r.note_id, r.title, r.exp_title; END IF;
    IF r.domain_context IS DISTINCT FROM 'GENERAL_EDUCATION' THEN
      RAISE EXCEPTION 'P3 failed for %: Domain Context is %, expected GENERAL_EDUCATION', r.note_id, r.domain_context; END IF;
    IF r.learner_level IS DISTINCT FROM 'COLLEGE' OR r.visibility IS DISTINCT FROM 'PUBLIC' THEN
      RAISE EXCEPTION 'P5 failed for %: level % / visibility %, expected COLLEGE / PUBLIC', r.note_id, r.learner_level, r.visibility; END IF;
    IF EXISTS (SELECT 1 FROM note_course_program x WHERE x.note_id = r.note_id AND x.course_program_id = is_id) THEN
      RAISE EXCEPTION 'P4 failed for %: Information Systems is already associated', r.note_id; END IF;
    SELECT string_agg(cp.name, '|' ORDER BY cp.name COLLATE "C") INTO actual
      FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id WHERE x.note_id = r.note_id;
    IF actual IS DISTINCT FROM r.exp_programs THEN
      RAISE EXCEPTION 'P6 failed for %: programs are [%], expected [%]', r.note_id, actual, r.exp_programs; END IF;
  END LOOP;
  IF (SELECT count(*) FROM course_programs WHERE name = 'Information Systems') <> 1
     OR NOT EXISTS (SELECT 1 FROM course_programs WHERE name = 'Information Systems' AND id = is_id) THEN
    RAISE EXCEPTION 'P7 failed: catalog "Information Systems" is missing, duplicated, or not id %', is_id; END IF;
  IF EXISTS (SELECT 1 FROM bsis_expected WHERE note_id = held) THEN
    RAISE EXCEPTION 'P8 failed: the HELD Note is in the target list'; END IF;
  IF EXISTS (SELECT 1 FROM note_course_program x WHERE x.note_id = held AND x.course_program_id = is_id) THEN
    RAISE EXCEPTION 'P8 failed: the HELD Note is already tagged Information Systems'; END IF;
END
$pre$;
\echo 'Preconditions P1-P8 passed.'

-- INSERT ... RETURNING: exact inserted association ids are captured in bsis_inserted and printed.
CREATE TEMP TABLE bsis_inserted (id uuid, note_id uuid, course_program_id uuid, created_at timestamptz) ON COMMIT DROP;
WITH ins AS (
  INSERT INTO note_course_program (id, note_id, course_program_id)
  SELECT gen_random_uuid(), e.note_id, cp.id
  FROM bsis_expected e CROSS JOIN course_programs cp
  WHERE cp.name = 'Information Systems'
  RETURNING id, note_id, course_program_id, created_at
)
INSERT INTO bsis_inserted SELECT * FROM ins;

\echo 'INSERTED ASSOCIATIONS (exact ids; expect 5 rows):'
SELECT i.id AS association_id, e.title, i.note_id, i.course_program_id, i.created_at
FROM bsis_inserted i JOIN bsis_expected e ON e.note_id = i.note_id ORDER BY e.title;

-- POST-INSERT ASSERTIONS. Any failure raises and stops the run.
DO $post$
DECLARE
  is_id constant uuid := '338d3e66-fee4-4400-afa7-0af0931444c8';
  r record;
  actual text;
  expected text;
  is_after bigint;
BEGIN
  IF (SELECT count(*) FROM bsis_inserted) <> 5 THEN
    RAISE EXCEPTION 'post failed: expected 5 inserted rows, got %', (SELECT count(*) FROM bsis_inserted); END IF;
  IF (SELECT count(DISTINCT note_id) FROM bsis_inserted) <> 5 THEN
    RAISE EXCEPTION 'post failed: the 5 rows do not cover 5 distinct Notes'; END IF;
  SELECT count(*) INTO is_after FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id
   WHERE cp.name = 'Information Systems';
  IF is_after <> (SELECT is_before FROM bsis_scalar) + 5 THEN
    RAISE EXCEPTION 'post failed: global Information Systems associations went % -> %, expected +5',
      (SELECT is_before FROM bsis_scalar), is_after; END IF;
  FOR r IN SELECT e.note_id, e.programs FROM bsis_expected e LOOP
    SELECT string_agg(cp.name, '|' ORDER BY cp.name COLLATE "C") INTO actual
      FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id WHERE x.note_id = r.note_id;
    SELECT string_agg(p, '|' ORDER BY p COLLATE "C") INTO expected
      FROM unnest(array_append(string_to_array(r.programs, '|'), 'Information Systems'::text)) AS p;
    IF actual IS DISTINCT FROM expected THEN
      RAISE EXCEPTION 'post failed for %: programs are [%], expected [%]', r.note_id, actual, expected; END IF;
  END LOOP;
  IF EXISTS (SELECT 1 FROM bsis_before b JOIN notes n ON n.id = b.note_id
              WHERE md5(n.content) <> b.content_md5 OR n.updated_at <> b.updated_at
                 OR (SELECT count(*) FROM study_packs s WHERE s.note_id = n.id) <> b.packs) THEN
    RAISE EXCEPTION 'post failed: a target Note body, updated_at or Study Pack count changed'; END IF;
  IF EXISTS (SELECT 1 FROM note_course_program x WHERE x.note_id = '45133a32-4440-4277-8b50-931eaec3788f'
               AND x.course_program_id = is_id) THEN
    RAISE EXCEPTION 'post failed: the HELD Note was tagged'; END IF;
END
$post$;
\echo 'Post-insert assertions passed (5 rows; programs = snapshot + Information Systems; global count +5; bodies, updated_at and Study Packs unchanged; held Note untouched).'

\echo 'RESULT PER NOTE:'
SELECT n.title, count(*) AS program_count, string_agg(cp.name, ', ' ORDER BY cp.name) AS programs
FROM notes n JOIN bsis_expected e ON e.note_id = n.id
JOIN note_course_program x ON x.note_id = n.id JOIN course_programs cp ON cp.id = x.course_program_id
GROUP BY n.title ORDER BY n.title;

\echo 'ROLLBACK STATEMENT, id-exact (SAVE THIS; use it ONLY after a COMMIT you want to undo; expect DELETE 5):'
SELECT format('DELETE FROM note_course_program WHERE id IN (%s) AND course_program_id = %L AND note_id IN (%s);',
              string_agg(quote_literal(id::text) || '::uuid', ', ' ORDER BY id),
              course_program_id, string_agg(quote_literal(note_id::text) || '::uuid', ', ' ORDER BY id))
       AS rollback_statement
FROM bsis_inserted GROUP BY course_program_id;

-- END OF RUN: one explicit action, same connection.
SELECT (:'mode' = 'commit' AND :'confirm' = 'owner-approved') AS do_commit, (:'mode' = 'hold') AS do_hold \gset
\if :do_commit
  COMMIT;
  \echo 'COMMITTED. Verify with the read-only queries below, and keep the log and the rollback statement above.'
\elif :do_hold
  \echo 'HOLD: the transaction is still OPEN on this connection. Inspect, then type COMMIT; or ROLLBACK;. Closing psql rolls it back.'
\else
  ROLLBACK;
  \echo 'DRY RUN: rolled back. Nothing was written.'
\endif

-- ============================================================================
-- AFTER A COMMIT (read-only verification, run separately; expect 5 rows, each with the new program):
--   SELECT n.title, count(*) AS program_count, string_agg(cp.name, ', ' ORDER BY cp.name) AS programs
--   FROM notes n JOIN note_course_program x ON x.note_id = n.id JOIN course_programs cp ON cp.id = x.course_program_id
--   WHERE n.id IN ('5e2a6baa-791f-4bfb-9b1b-b4317623cd0b','556e7dbe-c43f-41f2-8d3f-fd2c4434b9e1',
--                  '769cd008-4a48-4ac0-b47d-620cbc23ffc0','3b53ce07-08db-4fac-b4fe-8525462d74a5',
--                  '6256e54b-1501-44cf-abf3-88b2b4d67a0e')
--   GROUP BY n.title ORDER BY n.title;          -- expect 9, 9, 9, 3, 3
-- ROLLBACK: only the id-exact statement printed by the run (it deletes by primary key, notes and program).
--   Never use a broad DELETE by program name.
-- ============================================================================
