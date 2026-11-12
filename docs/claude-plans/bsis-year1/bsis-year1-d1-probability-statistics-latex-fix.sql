-- ============================================================================
-- D1: wrap the raw LaTeX in "Probability and Basic Statistics" in $...$ so it renders as math
-- Status: RUN by the owner on 2026-11-06 (committed 09:04:40Z) and VERIFIED read-only by Claude the same day: body md5 ada1a3636c502fbc515fad7c942eca17, length 1928,
--   12 dollar signs, 7 backslashes, removing the dollar signs gives the original md5 3105b510...; Study Pack, programs and collection items unchanged.
--   KEPT AS A RECORD; DO NOT RE-RUN (its preconditions now stop it at P4). Originally PREPARED 2026-11-06 for OWNER INSPECTION. D1 was approved in principle by the owner (a guarded fix,
--   Study Pack NOT regenerated); EXECUTION IS NOT approved until the owner separately runs it.
-- THE OWNER EXECUTED THIS (CLAUDE.md: production writes are owner-only). Before the run it WAS exercised against a throwaway local PostgreSQL 16 (not production); see "TESTED".
--
-- WHY
--   The three Quick Recall lines below contain raw LaTeX with no delimiters, so they show as code-like text
--   (for example "\frac{\sum x_i}{n}"). NoteLib renders math with remark-math + KaTeX and the existing Notes
--   (for example Correlation and Simple Linear Regression) delimit inline math with single dollar signs. Wrapping
--   the math tokens in $...$ makes these lines render the same way.
--
-- THIS NOTE IS SHARED. It sits in 3 collection items today (read 2026-11-06) and is in the BSIS Year 1 journey.
--   The edit changes the visible text of a PUBLIC Note that other published journeys may include. It changes only
--   the three lines below and only by adding dollar signs (12 added characters); no word is changed.
--
-- THE EXACT CHANGE (nothing else in the body changes)
--   1) OLD: - Mean formula — \frac{\sum x_i}{n} where x_i are data points and n is number of points
--      NEW: - Mean formula — $\frac{\sum x_i}{n}$ where $x_i$ are data points and $n$ is number of points
--   2) OLD: - Variance formula — \frac{\sum (x_i - \bar{x})^2}{n} where \bar{x} is mean
--      NEW: - Variance formula — $\frac{\sum (x_i - \bar{x})^2}{n}$ where $\bar{x}$ is mean
--   3) OLD: - Multiplication rule — P(A and B) = P(A) \times P(B) for independent events
--      NEW: - Multiplication rule — P(A and B) = P(A) $\times$ P(B) for independent events
--   The addition rule line has no LaTeX and is not touched. Production state read 2026-11-06: the body has no
--   dollar sign at all, 7 backslashes (\frac x2, \sum x2, \bar x2, \times x1 = 7, all on these three lines), and each
--   OLD line occurs exactly once.
--
-- WHAT IT DOES NOT TOUCH
--   Title, Subject, Domain Context, level, visibility, Applicable Programs, collections, and the STUDY PACK.
--   The Study Pack is not regenerated and not edited (versioning rule: never auto-regenerate). Its answer-position
--   pattern (all five keys are option A; the keys themselves are correct) is a SEPARATE quality item recorded for
--   later maintenance and is deliberately not fixed here. This script asserts the pack is unchanged.
--
-- GUARDS (read from production 2026-11-06; the script re-checks them and stops if any differs)
--   id           404ee436-ff9e-4963-bcc2-7f1c395e19dd
--   title        Probability and Basic Statistics     Domain Context GENERAL_EDUCATION   level COLLEGE   PUBLIC   subject Mathematics
--   length 1916  md5 3105b51056c2c78d8d5ae657179bac07         updated_at 2026-09-25 14:57:10.415033+00
--   Study Pack   d218080e-87a5-4497-a536-62b9b1282d2b  status DONE  updated_at 2026-09-09 01:36:56.523087+00
--                content digest 6d8b7a188804915439dd1270dec68773 (md5 of summary || key_concepts || quiz)
--   Applicable Programs: 9 rows.   Collection items referencing the Note: 3.
--   expected md5 AFTER the change: ada1a3636c502fbc515fad7c942eca17 (length 1928)
--
-- HOW TO RUN (psql 10 or later; ONE command per mode, ONE connection each; never a separate `-c "COMMIT;"`)
--   1) DRY RUN (default; everything runs, then ROLLBACK):
--        psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f docs/claude-plans/bsis-year1/bsis-year1-d1-probability-statistics-latex-fix.sql | tee d1-dryrun.log
--   2) APPLY (explicit owner action; needs BOTH variables; preconditions run again inside the transaction):
--        psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v mode=commit -v confirm=owner-approved \
--             -f docs/claude-plans/bsis-year1/bsis-year1-d1-probability-statistics-latex-fix.sql | tee d1-apply.log
--   OPTIONAL interactive: `psql "$DATABASE_URL"`, `\set mode hold`, `\i <this file>`; the transaction stays OPEN;
--      type COMMIT; or ROLLBACK;. Closing psql rolls it back.
--   Modes: dry (default) | hold | commit. The script never commits by default.
--
-- AFTER A COMMIT
--   * Do NOT use "Regenerate note" or "Bulk regenerate" on this Note: that regenerates the BODY from the title and
--     would overwrite the text. No Study Pack regeneration is needed or wanted.
--   * Look at the Note in the app once: the three lines should render as formulas. If the app still shows raw text,
--     run the printed ROLLBACK STATEMENT and tell Claude.
--
-- ROLLBACK (only after a COMMIT you want to undo): the run prints an exact statement; it reverses only these three
--   lines and is guarded on the corrected md5. Expect UPDATE 1.
--
-- TESTED (2026-11-06, throwaway local PostgreSQL, NOT production, with the real body text read from production):
--   dry run (nothing kept), hold via -f (rolled back on exit), commit, a second run after the commit (stopped at P4),
--   the printed rollback statement (restored md5 3105b51056c2c78d8d5ae657179bac07 exactly), a bad mode, commit without confirmation and with a
--   wrong confirmation (also without the ON_ERROR_STOP flag), and deliberate failures for a changed body, a changed
--   updated_at, a changed title, a changed Domain Context, a non-public Note, a changed Study Pack (digest, then
--   updated_at), a second Study Pack, a body already containing a dollar sign, and a missing Note: every one stopped
--   with nothing written. LIMITS: the local schema had only the columns the script reads, and the local Study Pack
--   digest was substituted in the test copy (the production digest cannot be reproduced locally); the script's own
--   post-update checks (additive-only, metadata, memberships, Study Pack) and the owner's dry run against production
--   are the real guard.
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
\echo 'D1 LaTeX delimiter fix run, mode =' :mode

BEGIN;

CREATE TEMP TABLE d1_before ON COMMIT DROP AS
SELECT n.id, n.title, n.subject, n.domain_context, n.learner_level, n.visibility, n.content, md5(n.content) AS md5,
       length(n.content) AS len, n.updated_at,
       (SELECT s.id FROM study_packs s WHERE s.note_id = n.id) AS pack_id,
       (SELECT s.updated_at FROM study_packs s WHERE s.note_id = n.id) AS pack_updated_at,
       (SELECT md5(coalesce(s.summary,'') || coalesce(s.key_concepts::text,'') || coalesce(s.quiz::text,'')) FROM study_packs s WHERE s.note_id = n.id) AS pack_md5,
       (SELECT count(*) FROM note_course_program x WHERE x.note_id = n.id) AS programs,
       (SELECT count(*) FROM note_collection_items i WHERE i.note_id = n.id) AS items
FROM notes n WHERE n.id = '404ee436-ff9e-4963-bcc2-7f1c395e19dd';

DO $pre$
DECLARE
  b record;
  o1 constant text := '- Mean formula — \frac{\sum x_i}{n} where x_i are data points and n is number of points';
  o2 constant text := '- Variance formula — \frac{\sum (x_i - \bar{x})^2}{n} where \bar{x} is mean';
  o3 constant text := '- Multiplication rule — P(A and B) = P(A) \times P(B) for independent events';
BEGIN
  SELECT * INTO b FROM d1_before;
  IF b.id IS NULL THEN RAISE EXCEPTION 'P1 failed: the Note does not exist'; END IF;
  IF b.title <> 'Probability and Basic Statistics' OR b.subject IS DISTINCT FROM 'Mathematics' THEN
    RAISE EXCEPTION 'P2 failed: title/subject are "%"/"%"', b.title, b.subject; END IF;
  IF b.domain_context IS DISTINCT FROM 'GENERAL_EDUCATION' OR b.learner_level IS DISTINCT FROM 'COLLEGE' OR b.visibility IS DISTINCT FROM 'PUBLIC' THEN
    RAISE EXCEPTION 'P3 failed: Domain Context / level / visibility are %/%/%', b.domain_context, b.learner_level, b.visibility; END IF;
  IF b.md5 <> '3105b51056c2c78d8d5ae657179bac07' OR b.len <> 1916 THEN
    RAISE EXCEPTION 'P4 failed: the body changed since it was read (md5 %, length %)', b.md5, b.len; END IF;
  IF b.updated_at <> '2026-09-25 14:57:10.415033+00'::timestamptz THEN
    RAISE EXCEPTION 'P5 failed: updated_at is %, expected 2026-09-25 14:57:10.415033+00', b.updated_at; END IF;
  IF position('$' in b.content) > 0 THEN RAISE EXCEPTION 'P6 failed: the body already contains a dollar sign'; END IF;
  IF (length(b.content) - length(replace(b.content, o1, ''))) / length(o1) <> 1
     OR (length(b.content) - length(replace(b.content, o2, ''))) / length(o2) <> 1
     OR (length(b.content) - length(replace(b.content, o3, ''))) / length(o3) <> 1 THEN
    RAISE EXCEPTION 'P6 failed: each of the three lines to change must occur exactly once'; END IF;
  IF length(b.content) - length(replace(b.content, chr(92), '')) <> 7 THEN
    RAISE EXCEPTION 'P6 failed: expected exactly 7 backslashes in the body'; END IF;
  IF b.pack_id IS DISTINCT FROM 'd218080e-87a5-4497-a536-62b9b1282d2b'::uuid
     OR b.pack_updated_at IS DISTINCT FROM '2026-09-09 01:36:56.523087+00'::timestamptz
     OR b.pack_md5 IS DISTINCT FROM '6d8b7a188804915439dd1270dec68773' THEN
    RAISE EXCEPTION 'P7 failed: the Study Pack is not the expected one (id %, updated_at %, digest %)', b.pack_id, b.pack_updated_at, b.pack_md5; END IF;
  IF (SELECT count(*) FROM study_packs WHERE note_id = b.id) <> 1 THEN RAISE EXCEPTION 'P7 failed: expected exactly one Study Pack'; END IF;
END
$pre$;
\echo 'Preconditions P1-P7 passed.'

\echo 'BEFORE (the Quick Recall lines):'
SELECT l AS before_line FROM d1_before, regexp_split_to_table(content, E'\n') l WHERE l LIKE '%formula%' OR l LIKE 'Multiplication%' OR l LIKE '- Multiplication%';

CREATE TEMP TABLE d1_updated (id uuid, md5 text, len int, updated_at timestamptz) ON COMMIT DROP;
WITH u AS (
  UPDATE notes
     SET content = replace(replace(replace(content, '- Mean formula — \frac{\sum x_i}{n} where x_i are data points and n is number of points', '- Mean formula — $\frac{\sum x_i}{n}$ where $x_i$ are data points and $n$ is number of points'), '- Variance formula — \frac{\sum (x_i - \bar{x})^2}{n} where \bar{x} is mean', '- Variance formula — $\frac{\sum (x_i - \bar{x})^2}{n}$ where $\bar{x}$ is mean'), '- Multiplication rule — P(A and B) = P(A) \times P(B) for independent events', '- Multiplication rule — P(A and B) = P(A) $\times$ P(B) for independent events'),
         updated_at = now()
   WHERE id = '404ee436-ff9e-4963-bcc2-7f1c395e19dd'
     AND title = 'Probability and Basic Statistics'
     AND md5(content) = '3105b51056c2c78d8d5ae657179bac07'
     AND updated_at = '2026-09-25 14:57:10.415033+00'::timestamptz
  RETURNING id, md5(content) AS md5, length(content) AS len, updated_at
)
INSERT INTO d1_updated SELECT * FROM u;

DO $post$
DECLARE b record; a record;
BEGIN
  IF (SELECT count(*) FROM d1_updated) <> 1 THEN RAISE EXCEPTION 'post failed: expected exactly 1 row updated, got %', (SELECT count(*) FROM d1_updated); END IF;
  SELECT * INTO b FROM d1_before;
  SELECT n.title, n.subject, n.domain_context, n.learner_level, n.visibility, n.content, md5(n.content) AS md5, length(n.content) AS len,
         (SELECT s.id FROM study_packs s WHERE s.note_id = n.id) AS pack_id,
         (SELECT s.updated_at FROM study_packs s WHERE s.note_id = n.id) AS pack_updated_at,
         (SELECT md5(coalesce(s.summary,'') || coalesce(s.key_concepts::text,'') || coalesce(s.quiz::text,'')) FROM study_packs s WHERE s.note_id = n.id) AS pack_md5,
         (SELECT count(*) FROM note_course_program x WHERE x.note_id = n.id) AS programs,
         (SELECT count(*) FROM note_collection_items i WHERE i.note_id = n.id) AS items
    INTO a FROM notes n WHERE n.id = b.id;
  IF a.md5 <> 'ada1a3636c502fbc515fad7c942eca17' THEN RAISE EXCEPTION 'post failed: new md5 is %, expected ada1a3636c502fbc515fad7c942eca17', a.md5; END IF;
  IF a.len <> 1928 THEN RAISE EXCEPTION 'post failed: length is %, expected 1928', a.len; END IF;
  IF length(a.content) - length(replace(a.content, '$', '')) <> 12 THEN RAISE EXCEPTION 'post failed: expected exactly 12 dollar signs'; END IF;
  IF length(a.content) - length(replace(a.content, chr(92), '')) <> 7 THEN RAISE EXCEPTION 'post failed: the backslash count changed'; END IF;
  -- the change is purely additive: removing the 12 dollar signs gives back the original body exactly
  IF md5(replace(a.content, '$', '')) <> b.md5 THEN RAISE EXCEPTION 'post failed: more than dollar signs changed'; END IF;
  IF a.title IS DISTINCT FROM b.title OR a.subject IS DISTINCT FROM b.subject OR a.domain_context IS DISTINCT FROM b.domain_context
     OR a.learner_level IS DISTINCT FROM b.learner_level OR a.visibility IS DISTINCT FROM b.visibility
     OR a.programs <> b.programs OR a.items <> b.items THEN
    RAISE EXCEPTION 'post failed: other Note metadata or memberships changed'; END IF;
  IF a.pack_id IS DISTINCT FROM b.pack_id OR a.pack_updated_at IS DISTINCT FROM b.pack_updated_at OR a.pack_md5 IS DISTINCT FROM b.pack_md5 THEN
    RAISE EXCEPTION 'post failed: the Study Pack changed'; END IF;
END
$post$;
\echo 'Post-update assertions passed (1 row; 12 dollar signs added and nothing else; length 1928; new md5 ada1a363...; metadata, memberships and Study Pack unchanged).'

\echo 'AFTER:'
SELECT l AS after_line FROM notes n, regexp_split_to_table(n.content, E'\n') l WHERE n.id = '404ee436-ff9e-4963-bcc2-7f1c395e19dd' AND l LIKE '%$%';
SELECT length(content) AS len, md5(content) AS md5, updated_at FROM notes WHERE id = '404ee436-ff9e-4963-bcc2-7f1c395e19dd';

\echo 'ROLLBACK STATEMENT (SAVE THIS; use ONLY after a COMMIT you want to undo; expect UPDATE 1; afterwards the md5 must be 3105b51056c2c78d8d5ae657179bac07):'
SELECT 'UPDATE notes SET content = replace(replace(replace(content, ''- Mean formula — $\frac{\sum x_i}{n}$ where $x_i$ are data points and $n$ is number of points'', ''- Mean formula — \frac{\sum x_i}{n} where x_i are data points and n is number of points''), ''- Variance formula — $\frac{\sum (x_i - \bar{x})^2}{n}$ where $\bar{x}$ is mean'', ''- Variance formula — \frac{\sum (x_i - \bar{x})^2}{n} where \bar{x} is mean''), ''- Multiplication rule — P(A and B) = P(A) $\times$ P(B) for independent events'', ''- Multiplication rule — P(A and B) = P(A) \times P(B) for independent events''), updated_at = now() WHERE id = ''404ee436-ff9e-4963-bcc2-7f1c395e19dd'' AND md5(content) = ''ada1a3636c502fbc515fad7c942eca17'';' AS rollback_statement;

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
