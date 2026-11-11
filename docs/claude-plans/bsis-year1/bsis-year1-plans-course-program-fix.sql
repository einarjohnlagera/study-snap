-- ============================================================================
-- BSIS Year 1: repair the Subject Plans' course/program after a wrong cascade
-- Status: RUN by the owner on 2026-11-09 (committed 14:20:10Z, target = information_systems; 9 rows) and VERIFIED read-only by Claude the same day:
--   the root and all ten plans read Information Systems; titles, terms, level, visibility, descriptions and item counts unchanged. KEPT AS A
--   RECORD; DO NOT RE-RUN (its preconditions now stop it at P3). Originally PREPARED 2026-11-09 for OWNER INSPECTION.
--   THE OWNER EXECUTES THIS (CLAUDE.md: production writes are owner-only). It WAS exercised against a throwaway local PostgreSQL (not production); see "TESTED".
--
-- WHAT HAPPENED (read 2026-11-09)
--   Re-saving the Year root's course/program in the app copies the saved value onto every plan whose course/program is BLANK
--   (NoteCollectionService.updateMetadata -> cascadeCourseProgramToBlankChildren). A temporary value, "Accountancy", was saved on the
--   root; it cascaded to the 8 plans that were still blank. Then plans 1 and 2 were set to Information Systems by hand and the root's
--   program was cleared. Now: root = NULL; plans 1 and 2 = Information Systems; plans 3 to 10 = Accountancy; 0 adopters.
--
-- THE CHANGE (target = information_systems, the DEFAULT; recommended)
--   * The 8 plans under the root whose course_program is exactly 'Accountancy' -> 'Information Systems'.
--   * The root, whose course_program is NULL -> 'Information Systems' (its original, approved value).
--   * updated_at = now() on those 9 rows, as the app does. Nothing else: not titles, descriptions, terms, level, visibility, items,
--     membership, or any Note.
-- ALTERNATIVE (-v target=blank): set the 8 plans to NULL and leave the root alone, if you prefer to clear them and then re-save the
--   root's program in the app (that cascade would then fill the blank plans). Not recommended: it needs one more app step.
--
-- GUARDS (read 2026-11-09; the script re-checks them and stops if any differs)
--   root 27788999-e07f-469b-9c4c-8ddb6487a6cb, title "🗄️ BS Information Systems — 1st Year", PUBLIC, course_program NULL, level COLLEGE
--   exactly 10 child plans: 2 = 'Information Systems', 8 = 'Accountancy', none other; all PUBLIC
--   no collection adopted the root or any plan (source_plan_id); the catalog has exactly one 'Information Systems' program row
--
-- HOW TO RUN (psql 10 or later, not an IDE console; ONE command per mode, ONE connection each)
--   1) DRY RUN (default; everything runs, then ROLLBACK):
--        psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f docs/claude-plans/bsis-year1/bsis-year1-plans-course-program-fix.sql | tee plans-cp-dryrun.log
--   2) APPLY (needs BOTH variables; preconditions run again inside the transaction):
--        psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v mode=commit -v confirm=owner-approved \
--             -f docs/claude-plans/bsis-year1/bsis-year1-plans-course-program-fix.sql | tee plans-cp-apply.log
--   Modes: dry (default) | hold (leave open, interactive only) | commit. Option: -v target=blank (see ALTERNATIVE).
--
-- AFTER A COMMIT: do NOT re-save the root's course/program in the app unless the ten plans are already non-blank (the cascade only
--   fills blank plans, so it would then do nothing, but do not rely on that). Tell Claude; it re-reads the plans read-only.
-- ROLLBACK (only after a COMMIT you want to undo): the run prints an id-exact statement. Expect UPDATE 9 (or 8 with target=blank).
--
-- TESTED (2026-11-09, throwaway local PostgreSQL, NOT production, with the exact current shape: root NULL, 2 + 8 plans, 0 adopters):
--   dry run, hold, commit, a re-run after commit (stops), the printed rollback, target=blank, a bad mode, a bad target, commit
--   without and with a wrong confirmation, and deliberate failures for: a third program value on a plan, a plan count of 9, a PRIVATE
--   plan, a non-NULL root, a changed root title, an adopter, and a missing catalog row. Each stopped with nothing written.
--   LIMITS: the local schema had only the columns the script reads.
-- ============================================================================

\set ON_ERROR_STOP on
\if :{?mode} \else \set mode dry \endif
\if :{?confirm} \else \set confirm none \endif
\if :{?target} \else \set target information_systems \endif
SELECT (:'mode' IN ('dry','hold','commit')) AS mode_ok,
       (:'target' IN ('information_systems','blank')) AS target_ok,
       (:'mode' <> 'commit' OR :'confirm' = 'owner-approved') AS confirm_ok \gset
\if :mode_ok \else
  DO $refuse$ BEGIN RAISE EXCEPTION 'REFUSED: mode must be dry, hold or commit.'; END $refuse$;
\endif
\if :target_ok \else
  DO $refuse$ BEGIN RAISE EXCEPTION 'REFUSED: target must be information_systems or blank.'; END $refuse$;
\endif
\if :confirm_ok \else
  DO $refuse$ BEGIN RAISE EXCEPTION 'REFUSED: mode=commit also needs -v confirm=owner-approved.'; END $refuse$;
\endif
\echo 'BSIS plans course/program repair, mode =' :mode ', target =' :target

BEGIN;

CREATE TEMP TABLE cpf_params ON COMMIT DROP AS SELECT :'target'::text AS target;

CREATE TEMP TABLE cpf_before ON COMMIT DROP AS
SELECT c.id, c.parent_collection_id, c.title, c.visibility, c.course_program, c.learner_level, c.term_label, c.term_order,
       c.sibling_position, md5(coalesce(c.description,'')) AS desc_md5,
       (SELECT count(*) FROM note_collection_items i WHERE i.collection_id = c.id) AS items
FROM note_collections c
WHERE c.id = '27788999-e07f-469b-9c4c-8ddb6487a6cb' OR c.parent_collection_id = '27788999-e07f-469b-9c4c-8ddb6487a6cb';

DO $pre$
DECLARE r record;
BEGIN
  SELECT * INTO r FROM cpf_before WHERE id = '27788999-e07f-469b-9c4c-8ddb6487a6cb';
  IF r.id IS NULL THEN RAISE EXCEPTION 'P1 failed: the root does not exist'; END IF;
  IF r.title <> E'\U0001F5C4️ BS Information Systems — 1st Year' OR r.parent_collection_id IS NOT NULL THEN
    RAISE EXCEPTION 'P1 failed: the root title is "%" (or it has a parent)', r.title; END IF;
  IF r.visibility::text <> 'PUBLIC' OR r.learner_level::text IS DISTINCT FROM 'COLLEGE' THEN
    RAISE EXCEPTION 'P2 failed: root visibility/level are %/%', r.visibility, r.learner_level; END IF;
  IF r.course_program IS NOT NULL THEN
    RAISE EXCEPTION 'P3 failed: the root course_program is "%", expected NULL; re-read production before continuing', r.course_program; END IF;
  IF (SELECT count(*) FROM cpf_before WHERE parent_collection_id IS NOT NULL) <> 10 THEN
    RAISE EXCEPTION 'P4 failed: expected exactly 10 child plans, found %', (SELECT count(*) FROM cpf_before WHERE parent_collection_id IS NOT NULL); END IF;
  IF (SELECT count(*) FROM cpf_before WHERE parent_collection_id IS NOT NULL AND course_program = 'Accountancy') <> 8
     OR (SELECT count(*) FROM cpf_before WHERE parent_collection_id IS NOT NULL AND course_program = 'Information Systems') <> 2 THEN
    RAISE EXCEPTION 'P5 failed: expected 8 plans = Accountancy and 2 = Information Systems; found: %',
      (SELECT string_agg(coalesce(course_program,'NULL') || ' x' || n, ', ') FROM (SELECT course_program, count(*) n FROM cpf_before WHERE parent_collection_id IS NOT NULL GROUP BY course_program) q); END IF;
  IF (SELECT count(*) FROM cpf_before WHERE parent_collection_id IS NOT NULL AND visibility::text <> 'PUBLIC') > 0 THEN
    RAISE EXCEPTION 'P6 failed: a plan is not PUBLIC'; END IF;
  IF (SELECT count(*) FROM note_collections a WHERE a.source_plan_id IN (SELECT id FROM cpf_before)) > 0 THEN
    RAISE EXCEPTION 'P7 failed: % collection(s) adopted the Year or a plan; re-assess before changing a published plan', (SELECT count(*) FROM note_collections a WHERE a.source_plan_id IN (SELECT id FROM cpf_before)); END IF;
  IF (SELECT count(*) FROM course_programs WHERE name = 'Information Systems') <> 1 THEN
    RAISE EXCEPTION 'P8 failed: the catalog must hold exactly one Information Systems program'; END IF;
END
$pre$;
\echo 'Preconditions P1-P8 passed.'

\echo 'BEFORE:'
SELECT coalesce(course_program,'NULL') AS course_program, count(*) AS rows, (parent_collection_id IS NULL) AS is_root FROM cpf_before GROUP BY course_program, (parent_collection_id IS NULL) ORDER BY is_root DESC, course_program;

CREATE TEMP TABLE cpf_changed (id uuid, title text, old_program text) ON COMMIT DROP;
WITH u AS (
  UPDATE note_collections c
     SET course_program = CASE WHEN :'target' = 'blank' THEN NULL ELSE 'Information Systems' END,
         updated_at = now()
   WHERE (c.parent_collection_id = '27788999-e07f-469b-9c4c-8ddb6487a6cb' AND c.course_program = 'Accountancy')
      OR (:'target' = 'information_systems' AND c.id = '27788999-e07f-469b-9c4c-8ddb6487a6cb' AND c.course_program IS NULL)
  RETURNING c.id, c.title, CASE WHEN c.parent_collection_id IS NULL THEN NULL ELSE 'Accountancy' END AS old_program
)
INSERT INTO cpf_changed SELECT * FROM u;

DO $post$
DECLARE tgt text := (SELECT target FROM cpf_params); expected_rows int := CASE WHEN (SELECT target FROM cpf_params) = 'blank' THEN 8 ELSE 9 END;
BEGIN
  IF (SELECT count(*) FROM cpf_changed) <> expected_rows THEN
    RAISE EXCEPTION 'post failed: expected % rows updated, got %', expected_rows, (SELECT count(*) FROM cpf_changed); END IF;
  IF tgt = 'information_systems' THEN
    IF (SELECT count(*) FROM note_collections WHERE (id = '27788999-e07f-469b-9c4c-8ddb6487a6cb' OR parent_collection_id = '27788999-e07f-469b-9c4c-8ddb6487a6cb') AND course_program = 'Information Systems') <> 11 THEN
      RAISE EXCEPTION 'post failed: the root and all ten plans must now read Information Systems'; END IF;
  ELSE
    IF (SELECT count(*) FROM note_collections WHERE parent_collection_id = '27788999-e07f-469b-9c4c-8ddb6487a6cb' AND course_program IS NULL) <> 8 THEN
      RAISE EXCEPTION 'post failed: eight plans must now be blank'; END IF;
  END IF;
  -- nothing else changed: every other column of the 11 rows, and every item count
  IF EXISTS (
    SELECT 1 FROM cpf_before b2 JOIN note_collections c ON c.id = b2.id
     WHERE c.title IS DISTINCT FROM b2.title OR c.visibility IS DISTINCT FROM b2.visibility OR c.learner_level IS DISTINCT FROM b2.learner_level
        OR c.term_label IS DISTINCT FROM b2.term_label OR c.term_order IS DISTINCT FROM b2.term_order OR c.sibling_position IS DISTINCT FROM b2.sibling_position
        OR md5(coalesce(c.description,'')) <> b2.desc_md5 OR c.parent_collection_id IS DISTINCT FROM b2.parent_collection_id
        OR (SELECT count(*) FROM note_collection_items i WHERE i.collection_id = c.id) <> b2.items) THEN
    RAISE EXCEPTION 'post failed: something other than course_program changed'; END IF;
END
$post$;
\echo 'Post-update assertions passed (only course_program and updated_at changed; titles, terms, level, visibility, descriptions, items and membership untouched; the UPDATE is limited to the root and its own plans).'

\echo 'CHANGED ROWS (exact ids):'
SELECT id::text, title, coalesce(old_program, 'NULL (root)') AS old_program FROM cpf_changed ORDER BY title;

\echo 'AFTER:'
SELECT coalesce(course_program,'NULL') AS course_program, count(*) AS rows, (parent_collection_id IS NULL) AS is_root
FROM note_collections WHERE id = '27788999-e07f-469b-9c4c-8ddb6487a6cb' OR parent_collection_id = '27788999-e07f-469b-9c4c-8ddb6487a6cb'
GROUP BY course_program, (parent_collection_id IS NULL) ORDER BY is_root DESC, course_program;

\echo 'ROLLBACK STATEMENT, id-exact (SAVE THIS; only after a COMMIT you want to undo):'
SELECT 'UPDATE note_collections SET course_program = ' ||
       CASE WHEN count(*) FILTER (WHERE old_program IS NULL) > 0 THEN 'CASE WHEN id = ''27788999-e07f-469b-9c4c-8ddb6487a6cb'' THEN NULL ELSE ''Accountancy'' END' ELSE '''Accountancy''' END ||
       ', updated_at = now() WHERE id IN (' || string_agg('''' || id::text || '''', ', ' ORDER BY id::text) || ');' AS rollback_statement
FROM cpf_changed;

SELECT (:'mode' = 'commit' AND :'confirm' = 'owner-approved') AS do_commit, (:'mode' = 'hold') AS do_hold \gset
\if :do_commit
  COMMIT;
  \echo 'COMMITTED. Keep this log. Tell Claude to re-read the plans.'
\elif :do_hold
  \echo 'HOLD: the transaction is still OPEN on this connection. Inspect, then type COMMIT; or ROLLBACK;. Closing psql rolls it back.'
\else
  ROLLBACK;
  \echo 'DRY RUN: rolled back. Nothing was written.'
\endif
