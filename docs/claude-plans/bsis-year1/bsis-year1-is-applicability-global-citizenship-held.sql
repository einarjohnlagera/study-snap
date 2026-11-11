-- ============================================================================
-- BSIS Year 1: add 'Information Systems' to "Global Citizenship and Global Civil Society" (the HELD sixth Note)
-- Status: RUN by the owner on 2026-11-06 and VERIFIED read-only by Claude the same day (see bsis-year1-fis-preproduction-readiness.md section 11). KEPT AS A RECORD; DO NOT RE-RUN (its preconditions now stop it). Ran after the RA 10364 correction (its content gate passed). Verified: Global Citizenship carries Information Systems (one row). Originally PREPARED 2026-11-06, HELD, for OWNER INSPECTION.
--
-- THIS SCRIPT REFUSES TO RUN until the RA 10364 correction is in place, by CONTENT, not by hash: the body must contain
-- "expanded by Republic Act No. 10364 in 2013;" exactly once and must contain no "in 2012". That works whether the
-- correction was made with bsis-year1-global-citizenship-ra10364-correction.sql or in the application's note editor
-- (an editor can normalize whitespace or line endings, which would change an md5 and wrongly lock this script out).
-- It adds one row to note_course_program and touches nothing else. It also asserts that the body and updated_at do
-- not change during the run.
--
--   id 45133a32-4440-4277-8b50-931eaec3788f   Global Citizenship and Global Civil Society
--   GENERAL_EDUCATION / COLLEGE / PUBLIC; current programs (read 2026-11-06): Computer Science, Information Technology
--   proposed: the same plus Information Systems (3 programs)
--   catalog: course_programs 'Information Systems' = 338d3e66-fee4-4400-afa7-0af0931444c8 (exactly one row)
--   Explicit Domain Context, so no binding conflict. Discovery only; no Study Pack change.
--
-- HOW TO RUN: identical to bsis-year1-is-applicability.sql. ONE command per mode, ONE connection each.
--   DRY RUN (default, ROLLBACK): psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f <this file> | tee gc-tag-dryrun.log
--   APPLY (explicit): psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v mode=commit -v confirm=owner-approved -f <this file> | tee gc-tag-apply.log
--   Modes: dry (default) | hold (interactive, leave open) | commit (needs confirm=owner-approved). Never commits by default.
-- TESTED on a throwaway local PostgreSQL 16 (not production): refuses on the uncorrected body; accepts a body corrected
--   either by the SQL script or by a whitespace-normalized edit; dry, commit and the id-exact rollback behave as described.
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
\echo 'Global Citizenship IS-tag run, mode =' :mode

BEGIN;

CREATE TEMP TABLE gct_scalar ON COMMIT DROP AS
SELECT (SELECT count(*) FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id
         WHERE cp.name = 'Information Systems') AS is_before,
       (SELECT md5(n.content) || '/' || n.updated_at::text FROM notes n WHERE n.id = '45133a32-4440-4277-8b50-931eaec3788f') AS body_state_before;

DO $pre$
DECLARE
  nid constant uuid := '45133a32-4440-4277-8b50-931eaec3788f';
  is_id constant uuid := '338d3e66-fee4-4400-afa7-0af0931444c8';
  n record; actual text;
BEGIN
  SELECT * INTO n FROM notes WHERE id = nid;
  IF n.id IS NULL THEN RAISE EXCEPTION 'P1 failed: the Note does not exist'; END IF;
  IF n.title <> 'Global Citizenship and Global Civil Society' THEN RAISE EXCEPTION 'P2 failed: title is "%"', n.title; END IF;
  IF n.domain_context IS DISTINCT FROM 'GENERAL_EDUCATION' OR n.learner_level IS DISTINCT FROM 'COLLEGE' OR n.visibility IS DISTINCT FROM 'PUBLIC' THEN
    RAISE EXCEPTION 'P3 failed: Domain Context / level / visibility are %/%/%', n.domain_context, n.learner_level, n.visibility; END IF;
  IF (length(n.content) - length(replace(n.content, 'expanded by Republic Act No. 10364 in 2013;', '')))
       / length('expanded by Republic Act No. 10364 in 2013;') <> 1
     OR position('in 2012' in n.content) > 0 THEN
    RAISE EXCEPTION 'HOLD: the RA 10364 correction is not in place (the body must contain "expanded by Republic Act No. 10364 in 2013;" exactly once and no "in 2012"). Correct the Note first.'; END IF;
  IF EXISTS (SELECT 1 FROM note_course_program x WHERE x.note_id = nid AND x.course_program_id = is_id) THEN
    RAISE EXCEPTION 'P4 failed: Information Systems is already associated'; END IF;
  SELECT string_agg(cp.name, '|' ORDER BY cp.name COLLATE "C") INTO actual
    FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id WHERE x.note_id = nid;
  IF actual IS DISTINCT FROM 'Computer Science|Information Technology' THEN
    RAISE EXCEPTION 'P6 failed: programs are [%], expected [Computer Science|Information Technology]', actual; END IF;
  IF (SELECT count(*) FROM course_programs WHERE name = 'Information Systems') <> 1
     OR NOT EXISTS (SELECT 1 FROM course_programs WHERE name = 'Information Systems' AND id = is_id) THEN
    RAISE EXCEPTION 'P7 failed: catalog "Information Systems" is missing, duplicated, or not id %', is_id; END IF;
END
$pre$;
\echo 'Preconditions passed (corrected body in place).'

CREATE TEMP TABLE gct_inserted (id uuid, note_id uuid, course_program_id uuid, created_at timestamptz) ON COMMIT DROP;
WITH ins AS (
  INSERT INTO note_course_program (id, note_id, course_program_id)
  SELECT gen_random_uuid(), '45133a32-4440-4277-8b50-931eaec3788f'::uuid, cp.id
  FROM course_programs cp WHERE cp.name = 'Information Systems'
  RETURNING id, note_id, course_program_id, created_at
)
INSERT INTO gct_inserted SELECT * FROM ins;
\echo 'INSERTED ASSOCIATION (exact id; expect 1 row):'
SELECT * FROM gct_inserted;

DO $post$
DECLARE actual text;
BEGIN
  IF (SELECT count(*) FROM gct_inserted) <> 1 THEN RAISE EXCEPTION 'post failed: expected 1 inserted row'; END IF;
  IF (SELECT count(*) FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id WHERE cp.name = 'Information Systems')
     <> (SELECT is_before FROM gct_scalar) + 1 THEN RAISE EXCEPTION 'post failed: global Information Systems count did not rise by exactly 1'; END IF;
  SELECT string_agg(cp.name, '|' ORDER BY cp.name COLLATE "C") INTO actual
    FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id
   WHERE x.note_id = '45133a32-4440-4277-8b50-931eaec3788f';
  IF actual <> 'Computer Science|Information Systems|Information Technology' THEN RAISE EXCEPTION 'post failed: programs are [%]', actual; END IF;
  IF (SELECT md5(n.content) || '/' || n.updated_at::text FROM notes n WHERE n.id = '45133a32-4440-4277-8b50-931eaec3788f')
     <> (SELECT body_state_before FROM gct_scalar) THEN RAISE EXCEPTION 'post failed: the body or updated_at changed'; END IF;
END
$post$;
\echo 'Post-insert assertions passed.'

\echo 'ROLLBACK STATEMENT, id-exact (SAVE THIS; only after a COMMIT you want to undo; expect DELETE 1):'
SELECT format('DELETE FROM note_course_program WHERE id = %L::uuid AND note_id = %L::uuid AND course_program_id = %L::uuid;', id, note_id, course_program_id)
       AS rollback_statement FROM gct_inserted;

SELECT (:'mode' = 'commit' AND :'confirm' = 'owner-approved') AS do_commit, (:'mode' = 'hold') AS do_hold \gset
\if :do_commit
  COMMIT;
  \echo 'COMMITTED.'
\elif :do_hold
  \echo 'HOLD: the transaction is still OPEN on this connection. Inspect, then type COMMIT; or ROLLBACK;.'
\else
  ROLLBACK;
  \echo 'DRY RUN: rolled back. Nothing was written.'
\endif
