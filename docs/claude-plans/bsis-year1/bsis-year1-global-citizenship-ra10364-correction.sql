-- ============================================================================
-- BSIS Year 1: correct one date in "Global Citizenship and Global Civil Society" (RA 10364 was approved in 2013)
-- Status: RUN by the owner on 2026-11-06 and VERIFIED read-only by Claude the same day (see bsis-year1-fis-preproduction-readiness.md section 11). KEPT AS A RECORD; DO NOT RE-RUN (its preconditions now stop it). Verified: body md5 9e3e316db280f70073ca1ce485abd01b, length 3516, 'in 2013;' present, 'in 2012' gone, Study Pack unchanged (updated_at 2026-11-05 12:46:46Z); note updated_at 2026-11-06 10:20:08Z. Originally PREPARED 2026-11-06 for OWNER INSPECTION. The correction wording is APPROVED by the owner
--   (2026-11-06); EXECUTION IS NOT approved until the owner separately runs it.
-- THE OWNER EXECUTES THIS (CLAUDE.md: production writes are owner-only). Nothing here has been executed against
--   production. It WAS exercised against a throwaway local PostgreSQL 16 (not production); see "TESTED".
--
-- WHY
--   The Note's fourth Core Details bullet says Republic Act No. 10364 expanded the trafficking law "in 2012".
--   RA 10364 (the "Expanded Anti-Trafficking in Persons Act of 2012") was APPROVED on 6 February 2013 (Lawphil
--   shows "Approved: FEB 06 2013"; the Senate legislative record lists 2/6/2013). "2012" is the year in the short
--   title, not the year it was approved. The same bullet says the UN protocol was "adopted in 2000"; that is
--   correct (General Assembly resolution 55/25, 15 November 2000), so it is NOT changed.
--
-- THE EXACT CHANGE (one token, same length; nothing else in the body changes)
--   OLD: ...expanded by Republic Act No. 10364 in 2012; civil society groups assist survivors and monitor enforcement.
--   NEW: ...expanded by Republic Act No. 10364 in 2013; civil society groups assist survivors and monitor enforcement.
--   Production state read 2026-11-06: the body contains "2012" exactly once and the phrase
--   "expanded by Republic Act No. 10364 in 2012;" exactly once. The only other years in the body are 2000 and 2003.
--   The change introduces no inconsistency: 2003 (RA 9208) < 2013 (RA 10364), and no other sentence cites the date.
--
-- WHAT IT DOES NOT TOUCH
--   Subject, Domain Context, Authored Depth, Applicable Programs, visibility, collections, and the Study Pack.
--   STUDY PACK: not regenerated and not edited, and it must not be auto-regenerated (versioning rule). It is
--   unaffected: its summary says only "the Philippines' Anti-Trafficking in Persons Act and its expansion" (no
--   year), its key concepts contain no year, and none of its 5 quiz items mentions trafficking. This script
--   asserts the pack is unchanged.
--
-- GUARDS (read from production 2026-11-06; the script re-checks them and stops if any differs)
--   id           45133a32-4440-4277-8b50-931eaec3788f
--   title        Global Citizenship and Global Civil Society     domain_context GENERAL_EDUCATION   level COLLEGE   PUBLIC
--   length 3516  md5 834de9de0e5296a782855611eed736f7            updated_at 2026-11-05 12:47:39.088493+00
--   Study Pack   cb8a8853-2a84-4fd2-adad-2dcadc75a71b            updated_at 2026-11-05 12:46:46.483572+00
--   expected md5 AFTER the change: 9e3e316db280f70073ca1ce485abd01b (length 3516)
--
-- HOW TO RUN (psql 10 or later; ONE command per mode, ONE connection each; never a separate `-c "COMMIT;"`)
--   1) DRY RUN (default; everything runs, then ROLLBACK):
--        psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f docs/claude-plans/bsis-year1/bsis-year1-global-citizenship-ra10364-correction.sql | tee gc-dryrun.log
--   2) APPLY (explicit owner action; needs BOTH variables; preconditions run again inside the transaction):
--        psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v mode=commit -v confirm=owner-approved \
--             -f docs/claude-plans/bsis-year1/bsis-year1-global-citizenship-ra10364-correction.sql | tee gc-apply.log
--   OPTIONAL interactive: `psql "$DATABASE_URL"`, `\set mode hold`, `\i <this file>`; the transaction stays OPEN;
--      type COMMIT; or ROLLBACK;. Closing psql rolls it back.
--   Modes: dry (default) | hold | commit. The script never commits by default.
--
-- AFTER A COMMIT (in the application, not SQL)
--   * Do NOT use "Regenerate note" or "Bulk regenerate" on this Note: that regenerates the BODY from the title and
--     would overwrite the text. No Study Pack regeneration is needed.
--   * Then the held tag script (bsis-year1-is-applicability-global-citizenship-held.sql) becomes runnable; it checks
--     that this correction is in place.
--
-- ROLLBACK (only after a COMMIT you want to undo): the run prints an exact statement; it reverses the one token
--   and is guarded on the corrected md5, so it cannot touch anything else. Expect UPDATE 1.
--
-- TESTED (2026-11-06, throwaway local PostgreSQL 16.2, NOT production, with the real body text): dry, commit,
--   re-run after commit, changed body, changed updated_at, changed title, changed Domain Context, a second
--   occurrence of the phrase, a changed Study Pack, a bad mode and commit without confirmation; rollback
--   restored md5 834de9de... exactly.
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
\echo 'RA 10364 correction run, mode =' :mode

BEGIN;

CREATE TEMP TABLE gc_before ON COMMIT DROP AS
SELECT n.id, n.title, n.subject, n.domain_context, n.learner_level, n.visibility, n.content, md5(n.content) AS md5,
       length(n.content) AS len, n.updated_at,
       (SELECT s.id FROM study_packs s WHERE s.note_id = n.id) AS pack_id,
       (SELECT s.updated_at FROM study_packs s WHERE s.note_id = n.id) AS pack_updated_at,
       (SELECT md5(coalesce(s.summary,'') || coalesce(s.key_concepts::text,'') || coalesce(s.quiz::text,'')) FROM study_packs s WHERE s.note_id = n.id) AS pack_md5,
       (SELECT count(*) FROM note_course_program x WHERE x.note_id = n.id) AS programs
FROM notes n WHERE n.id = '45133a32-4440-4277-8b50-931eaec3788f';

DO $pre$
DECLARE
  b record;
  old_phrase constant text := 'expanded by Republic Act No. 10364 in 2012;';
  new_phrase constant text := 'expanded by Republic Act No. 10364 in 2013;';
BEGIN
  SELECT * INTO b FROM gc_before;
  IF b.id IS NULL THEN RAISE EXCEPTION 'P1 failed: the Note does not exist'; END IF;
  IF b.title <> 'Global Citizenship and Global Civil Society' THEN RAISE EXCEPTION 'P2 failed: title is "%"', b.title; END IF;
  IF b.domain_context IS DISTINCT FROM 'GENERAL_EDUCATION' OR b.learner_level IS DISTINCT FROM 'COLLEGE' OR b.visibility IS DISTINCT FROM 'PUBLIC' THEN
    RAISE EXCEPTION 'P3 failed: Domain Context / level / visibility are %/%/%', b.domain_context, b.learner_level, b.visibility; END IF;
  IF b.md5 <> '834de9de0e5296a782855611eed736f7' OR b.len <> 3516 THEN
    RAISE EXCEPTION 'P4 failed: the body changed since it was read (md5 %, length %)', b.md5, b.len; END IF;
  IF b.updated_at <> '2026-11-05 12:47:39.088493+00'::timestamptz THEN
    RAISE EXCEPTION 'P5 failed: updated_at is %, expected 2026-11-05 12:47:39.088493+00', b.updated_at; END IF;
  IF (length(b.content) - length(replace(b.content, old_phrase, ''))) / length(old_phrase) <> 1 THEN
    RAISE EXCEPTION 'P6 failed: the exact phrase to change must occur exactly once'; END IF;
  IF position(new_phrase in b.content) > 0 THEN
    RAISE EXCEPTION 'P6 failed: the corrected phrase is already present'; END IF;
  IF (length(b.content) - length(replace(b.content, '2012', ''))) / 4 <> 1 THEN
    RAISE EXCEPTION 'P6 failed: "2012" must occur exactly once in the body'; END IF;
  IF b.pack_id IS DISTINCT FROM 'cb8a8853-2a84-4fd2-adad-2dcadc75a71b'::uuid
     OR b.pack_updated_at IS DISTINCT FROM '2026-11-05 12:46:46.483572+00'::timestamptz THEN
    RAISE EXCEPTION 'P7 failed: the Study Pack is not the expected one (id %, updated_at %)', b.pack_id, b.pack_updated_at; END IF;
  IF (SELECT count(*) FROM study_packs WHERE note_id = b.id) <> 1 THEN RAISE EXCEPTION 'P7 failed: expected exactly one Study Pack'; END IF;
END
$pre$;
\echo 'Preconditions P1-P7 passed.'

\echo 'BEFORE (the sentence being corrected):'
SELECT substr(content, position('expanded by Republic' in content) - 40, 160) AS before_text FROM gc_before;

CREATE TEMP TABLE gc_updated (id uuid, md5 text, len int, updated_at timestamptz) ON COMMIT DROP;
WITH u AS (
  UPDATE notes
     SET content = replace(content, 'expanded by Republic Act No. 10364 in 2012;', 'expanded by Republic Act No. 10364 in 2013;'),
         updated_at = now()
   WHERE id = '45133a32-4440-4277-8b50-931eaec3788f'
     AND title = 'Global Citizenship and Global Civil Society'
     AND md5(content) = '834de9de0e5296a782855611eed736f7'
     AND updated_at = '2026-11-05 12:47:39.088493+00'::timestamptz
  RETURNING id, md5(content) AS md5, length(content) AS len, updated_at
)
INSERT INTO gc_updated SELECT * FROM u;

DO $post$
DECLARE b record; a record;
BEGIN
  IF (SELECT count(*) FROM gc_updated) <> 1 THEN RAISE EXCEPTION 'post failed: expected exactly 1 row updated, got %', (SELECT count(*) FROM gc_updated); END IF;
  SELECT * INTO b FROM gc_before;
  SELECT n.title, n.subject, n.domain_context, n.learner_level, n.visibility, n.content, md5(n.content) AS md5, length(n.content) AS len,
         (SELECT s.id FROM study_packs s WHERE s.note_id = n.id) AS pack_id,
         (SELECT s.updated_at FROM study_packs s WHERE s.note_id = n.id) AS pack_updated_at,
         (SELECT md5(coalesce(s.summary,'') || coalesce(s.key_concepts::text,'') || coalesce(s.quiz::text,'')) FROM study_packs s WHERE s.note_id = n.id) AS pack_md5,
         (SELECT count(*) FROM note_course_program x WHERE x.note_id = n.id) AS programs
    INTO a FROM notes n WHERE n.id = b.id;
  IF a.md5 <> '9e3e316db280f70073ca1ce485abd01b' THEN RAISE EXCEPTION 'post failed: new md5 is %, expected 9e3e316db280f70073ca1ce485abd01b', a.md5; END IF;
  IF a.len <> b.len THEN RAISE EXCEPTION 'post failed: length changed % -> %', b.len, a.len; END IF;
  IF position('in 2012' in a.content) > 0 OR (length(a.content) - length(replace(a.content, 'in 2013;', ''))) / 8 <> 1 THEN
    RAISE EXCEPTION 'post failed: expected no "in 2012" and exactly one "in 2013;"'; END IF;
  IF a.title IS DISTINCT FROM b.title OR a.subject IS DISTINCT FROM b.subject OR a.domain_context IS DISTINCT FROM b.domain_context
     OR a.learner_level IS DISTINCT FROM b.learner_level OR a.visibility IS DISTINCT FROM b.visibility OR a.programs <> b.programs THEN
    RAISE EXCEPTION 'post failed: other Note metadata changed'; END IF;
  IF a.pack_id IS DISTINCT FROM b.pack_id OR a.pack_updated_at IS DISTINCT FROM b.pack_updated_at OR a.pack_md5 IS DISTINCT FROM b.pack_md5 THEN
    RAISE EXCEPTION 'post failed: the Study Pack changed'; END IF;
  -- the two other year claims in the bullet are untouched
  IF position('adopted in 2000' in a.content) = 0 OR position('Act of 2003 (Republic Act No. 9208)' in a.content) = 0 THEN
    RAISE EXCEPTION 'post failed: the 2000 or 2003 claims were disturbed'; END IF;
END
$post$;
\echo 'Post-update assertions passed (1 row; "in 2012" gone; one "in 2013;"; length 3516; new md5 9e3e316d...; metadata and Study Pack unchanged; 2000 and 2003 untouched).'

\echo 'AFTER:'
SELECT substr(content, position('expanded by Republic' in content) - 40, 160) AS after_text, length(content) AS len, md5(content) AS md5, updated_at
FROM notes WHERE id = '45133a32-4440-4277-8b50-931eaec3788f';

\echo 'ROLLBACK STATEMENT (SAVE THIS; use ONLY after a COMMIT you want to undo; expect UPDATE 1):'
\echo 'UPDATE notes SET content = replace(content, ''expanded by Republic Act No. 10364 in 2013;'', ''expanded by Republic Act No. 10364 in 2012;''), updated_at = now() WHERE id = ''45133a32-4440-4277-8b50-931eaec3788f'' AND md5(content) = ''9e3e316db280f70073ca1ce485abd01b'';  -- afterwards md5 must be 834de9de0e5296a782855611eed736f7'

SELECT (:'mode' = 'commit' AND :'confirm' = 'owner-approved') AS do_commit, (:'mode' = 'hold') AS do_hold \gset
\if :do_commit
  COMMIT;
  \echo 'COMMITTED. Keep this log. Do not regenerate the Note or its Study Pack.'
\elif :do_hold
  \echo 'HOLD: the transaction is still OPEN on this connection. Inspect, then type COMMIT; or ROLLBACK;. Closing psql rolls it back.'
\else
  ROLLBACK;
  \echo 'DRY RUN: rolled back. Nothing was written.'
\endif
