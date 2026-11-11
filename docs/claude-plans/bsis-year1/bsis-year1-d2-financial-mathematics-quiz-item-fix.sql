-- ============================================================================
-- D2 (Option A): reword one quiz stem in "Financial Mathematics in Everyday Life" (item 3) without regenerating the pack
-- Status: RUN by the owner on 2026-11-06 and VERIFIED read-only by Claude the same day (the script writes no timestamp by design, so the run time is not recorded here):
--   item 3 stem is the new wording; choices, key and the other four items unchanged (other-items digest 5e7e4d3a...); quiz_stamp 0; pack updated_at 2026-09-09 01:37:12.386427+00
--   unchanged; putting the old stem back reproduces the original digest e7f206b0...; the 7 copies unchanged (still the old stem); 0 sessions.
--   KEPT AS A RECORD; DO NOT RE-RUN (its preconditions now stop it at P4). Originally PREPARED 2026-11-06 for OWNER INSPECTION. Option A was chosen by the owner; EXECUTION IS NOT
--   approved until the owner separately runs it.
-- THE OWNER EXECUTED THIS (CLAUDE.md: production writes are owner-only). Before the run it WAS exercised against a throwaway local PostgreSQL (not production); see "TESTED".
--
-- WHY
--   Item 3 asks "When budgeting, which step is LEAST important?" but its key, "Ignoring future financial goals", is not a
--   step at all. The key and the explanation are right; only the stem is poor. The Note's body supports the new stem:
--   "Budgeting involves listing income and expenses to plan spending and saving effectively to achieve financial goals."
--
-- THE EXACT CHANGE (one string in one jsonb element; nothing else)
--   study_packs.quiz -> element 3 (zero-based index 2) -> key "question"
--   OLD: When budgeting, which step is LEAST important?
--   NEW: Which of the following is NOT part of effective budgeting?
--   Choices, the key (index 2), the explanation, the format and type, and the other four items are NOT changed.
--
-- THINGS THIS SCRIPT DELIBERATELY DOES NOT DO
--   * It does NOT change quiz_stamp. The key and meaning are unchanged, so no learner's mastery record is revoked
--     (bumping the stamp is what would invalidate them). The pack has no sessions today; the script re-checks that.
--   * It does NOT change study_packs.updated_at. This pack's owner is an ADMIN account, and the admin curation-token
--     report sums input/output tokens of ADMIN-owned DONE packs by updated_at month
--     (StudyPackRepository.sumAdminGenerationTokensByModel). Bumping updated_at would count this pack's original
--     generation tokens (3722 in / 880 out) in the CURRENT month. Raw SQL fires no @PreUpdate, and study_packs has no
--     database triggers (read 2026-11-06), so leaving updated_at alone is the default.
--   * It does NOT touch the Note, its metadata, or any other Study Pack. SEVEN other users copied this Note; each copy
--     has its own Study Pack with the OLD wording (PRIVATE, owned by other users). They are deliberately left as they
--     are, and the script asserts that they are unchanged.
--   * It does NOT regenerate anything. Do NOT use "Regenerate note", "Bulk regenerate" or any Study Pack regeneration
--     afterwards: regeneration would replace the whole quiz and bump the stamp.
--
-- GUARDS (read from production 2026-11-06; the script re-checks them and stops if any differs)
--   note         fd5727d4-b16a-4990-9989-3efd0e72d7eb   Financial Mathematics in Everyday Life   PUBLIC
--   Study Pack   8efc275e-312e-4e0d-a97a-b6672585eb5d   status DONE   quiz_stamp 0   updated_at 2026-09-09 01:37:12.386427+00
--   quiz digest  md5(quiz::text) = e7f206b08cbd9c6f352ffcc331838350   (pins every item, choice, key and explanation)
--   item 3 stem  exactly "When budgeting, which step is LEAST important?"   five items
--   Sessions / pools on the pack: none (quick_review_sessions, quiz_sessions, quiz_questions, concept_health,
--   memorization_cards, share_links, exam_question_pool all 0).   Seven copies of the Note, each with the same quiz.
--
-- HOW TO RUN (psql 10 or later; ONE command per mode, ONE connection each; never a separate `-c "COMMIT;"`)
--   1) DRY RUN (default; everything runs, then ROLLBACK):
--        psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f docs/claude-plans/bsis-year1/bsis-year1-d2-financial-mathematics-quiz-item-fix.sql | tee d2-dryrun.log
--   2) APPLY (explicit owner action; needs BOTH variables; preconditions run again inside the transaction):
--        psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v mode=commit -v confirm=owner-approved \
--             -f docs/claude-plans/bsis-year1/bsis-year1-d2-financial-mathematics-quiz-item-fix.sql | tee d2-apply.log
--   OPTIONAL interactive: `psql "$DATABASE_URL"`, `\set mode hold`, `\i <this file>`; the transaction stays OPEN;
--      type COMMIT; or ROLLBACK;. Closing psql rolls it back.
--   Modes: dry (default) | hold | commit. The script never commits by default.
--
-- AFTER A COMMIT
--   * Open the Note's Quiz once in the app: item 3 should show the new stem with the same four choices.
--   * Tell Claude; it re-reads the pack read-only (same SELECTs as the check) and updates the records.
--
-- ROLLBACK (only after a COMMIT you want to undo): the run prints an exact statement; it restores the old stem only,
--   guarded on the new stem. Expect UPDATE 1; afterwards md5(quiz::text) must be e7f206b08cbd9c6f352ffcc331838350 again.
--
-- TESTED (2026-11-06, throwaway local PostgreSQL, NOT production, with the real item 3 object read from production and
--   four stand-in items): dry run (nothing kept), hold via -f (rolled back on exit), commit, a second run after the
--   commit (stopped at P4), the printed rollback statement (quiz restored byte-for-byte), a bad mode, commit without
--   confirmation and with a wrong confirmation (also without the ON_ERROR_STOP flag), and deliberate failures for a
--   changed stem, a changed other item, a changed quiz_stamp, an appeared session or pool row, a changed pack updated_at, a changed
--   pack status, a Note no longer public, a missing pack and a missing copy of the Note: every one stopped with nothing written. LIMITS: the
--   local schema had only the columns the script reads; the production quiz digest cannot be reproduced locally, so the
--   test copy substitutes the local digest. The script's own post-update checks and the owner's dry run against
--   production are the real guard.
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
\echo 'D2 quiz stem fix run, mode =' :mode

BEGIN;

CREATE TEMP TABLE d2_before ON COMMIT DROP AS
SELECT s.id AS pack_id, s.note_id, s.status, s.quiz_stamp, s.updated_at AS pack_updated_at, s.quiz,
       md5(s.quiz::text) AS quiz_md5, jsonb_array_length(s.quiz) AS items,
       s.quiz -> 2 ->> 'question' AS stem,
       md5((s.quiz - 2)::text) AS other_items_md5,
       md5(((s.quiz -> 2) - 'question')::text) AS item3_rest_md5,
       n.title, n.visibility, n.updated_at AS note_updated_at,
       (SELECT count(*) FROM study_packs c JOIN notes cn ON cn.id = c.note_id WHERE cn.copied_from_note_id = n.id) AS copies,
       (SELECT md5(coalesce(string_agg(c.id::text || md5(c.quiz::text) || c.quiz_stamp::text || c.updated_at::text, ',' ORDER BY c.id), ''))
          FROM study_packs c JOIN notes cn ON cn.id = c.note_id WHERE cn.copied_from_note_id = n.id) AS copies_digest,
       (SELECT count(*) FROM quick_review_sessions x WHERE x.study_pack_id = s.id)
        + (SELECT count(*) FROM quiz_sessions x WHERE x.study_pack_id = s.id)
        + (SELECT count(*) FROM quiz_questions x WHERE x.study_pack_id = s.id)
        + (SELECT count(*) FROM concept_health x WHERE x.study_pack_id = s.id)
        + (SELECT count(*) FROM memorization_cards x WHERE x.study_pack_id = s.id)
        + (SELECT count(*) FROM share_links x WHERE x.study_pack_id = s.id)
        + (SELECT count(*) FROM exam_question_pool x WHERE x.study_pack_id = s.id) AS dependents
FROM study_packs s JOIN notes n ON n.id = s.note_id
WHERE s.id = '8efc275e-312e-4e0d-a97a-b6672585eb5d';

DO $pre$
DECLARE b record;
BEGIN
  SELECT * INTO b FROM d2_before;
  IF b.pack_id IS NULL THEN RAISE EXCEPTION 'P1 failed: the Study Pack does not exist'; END IF;
  IF b.note_id IS DISTINCT FROM 'fd5727d4-b16a-4990-9989-3efd0e72d7eb'::uuid OR b.title <> 'Financial Mathematics in Everyday Life' OR b.visibility IS DISTINCT FROM 'PUBLIC' THEN
    RAISE EXCEPTION 'P2 failed: the pack belongs to note % "%" (%), expected fd5727d4-b16a-4990-9989-3efd0e72d7eb', b.note_id, b.title, b.visibility; END IF;
  IF b.status <> 'DONE' OR b.quiz_stamp <> 0 THEN
    RAISE EXCEPTION 'P3 failed: status % / quiz_stamp % (expected DONE / 0)', b.status, b.quiz_stamp; END IF;
  IF b.pack_updated_at <> '2026-09-09 01:37:12.386427+00'::timestamptz THEN
    RAISE EXCEPTION 'P4 failed: the pack changed (updated_at %, expected 2026-09-09 01:37:12.386427+00)', b.pack_updated_at; END IF;
  IF b.quiz_md5 <> 'e7f206b08cbd9c6f352ffcc331838350' OR b.items <> 5 THEN
    RAISE EXCEPTION 'P4 failed: the quiz changed since it was read (md5 %, % items)', b.quiz_md5, b.items; END IF;
  IF b.stem IS DISTINCT FROM 'When budgeting, which step is LEAST important?' THEN
    RAISE EXCEPTION 'P5 failed: item 3 stem is "%", expected the old wording', b.stem; END IF;
  IF b.dependents <> 0 THEN
    RAISE EXCEPTION 'P6 failed: % session/pool/card/share rows now reference this pack; re-assess before editing', b.dependents; END IF;
  IF b.copies <> 7 THEN
    RAISE EXCEPTION 'P7 failed: expected 7 copies of the Note, found %; re-assess', b.copies; END IF;
END
$pre$;
\echo 'Preconditions P1-P7 passed.'

\echo 'BEFORE (item 3):'
SELECT quiz -> 2 ->> 'question' AS before_stem, quiz -> 2 -> 'choices' AS choices, quiz -> 2 -> 'correctIndex' AS correct_index FROM d2_before;

CREATE TEMP TABLE d2_updated (id uuid) ON COMMIT DROP;
WITH u AS (
  UPDATE study_packs
     SET quiz = jsonb_set(quiz, '{2,question}', to_jsonb('Which of the following is NOT part of effective budgeting?'::text), false)
   WHERE id = '8efc275e-312e-4e0d-a97a-b6672585eb5d'
     AND note_id = 'fd5727d4-b16a-4990-9989-3efd0e72d7eb'
     AND md5(quiz::text) = 'e7f206b08cbd9c6f352ffcc331838350'
     AND updated_at = '2026-09-09 01:37:12.386427+00'::timestamptz
     AND quiz_stamp = 0
  RETURNING id
)
INSERT INTO d2_updated SELECT * FROM u;

DO $post$
DECLARE b record; a record;
BEGIN
  IF (SELECT count(*) FROM d2_updated) <> 1 THEN RAISE EXCEPTION 'post failed: expected exactly 1 row updated, got %', (SELECT count(*) FROM d2_updated); END IF;
  SELECT * INTO b FROM d2_before;
  SELECT s.quiz, s.status, s.quiz_stamp, s.updated_at AS pack_updated_at, jsonb_array_length(s.quiz) AS items,
         s.quiz -> 2 ->> 'question' AS stem,
         md5((s.quiz - 2)::text) AS other_items_md5,
         md5(((s.quiz -> 2) - 'question')::text) AS item3_rest_md5,
         n.title, n.visibility, n.updated_at AS note_updated_at,
         (SELECT md5(coalesce(string_agg(c.id::text || md5(c.quiz::text) || c.quiz_stamp::text || c.updated_at::text, ',' ORDER BY c.id), ''))
            FROM study_packs c JOIN notes cn ON cn.id = c.note_id WHERE cn.copied_from_note_id = n.id) AS copies_digest
    INTO a FROM study_packs s JOIN notes n ON n.id = s.note_id WHERE s.id = b.pack_id;
  IF a.stem IS DISTINCT FROM 'Which of the following is NOT part of effective budgeting?' THEN RAISE EXCEPTION 'post failed: the new stem is "%"', a.stem; END IF;
  IF a.items <> 5 THEN RAISE EXCEPTION 'post failed: item count changed'; END IF;
  IF a.other_items_md5 <> b.other_items_md5 THEN RAISE EXCEPTION 'post failed: another item changed'; END IF;
  IF a.item3_rest_md5 <> b.item3_rest_md5 THEN RAISE EXCEPTION 'post failed: another field of item 3 changed (choices, key, explanation, format or type)'; END IF;
  -- the change is exactly one string: putting the old stem back reproduces the original quiz byte for byte
  IF jsonb_set(a.quiz, '{2,question}', to_jsonb(b.stem), false) IS DISTINCT FROM b.quiz THEN RAISE EXCEPTION 'post failed: more than the stem changed'; END IF;
  IF a.quiz_stamp <> b.quiz_stamp OR a.status <> b.status OR a.pack_updated_at <> b.pack_updated_at THEN
    RAISE EXCEPTION 'post failed: quiz_stamp, status or updated_at changed'; END IF;
  IF a.title IS DISTINCT FROM b.title OR a.visibility IS DISTINCT FROM b.visibility OR a.note_updated_at IS DISTINCT FROM b.note_updated_at THEN
    RAISE EXCEPTION 'post failed: the Note changed'; END IF;
  IF a.copies_digest IS DISTINCT FROM b.copies_digest THEN RAISE EXCEPTION 'post failed: a copy of the Note changed'; END IF;
END
$post$;
\echo 'Post-update assertions passed (1 row; only the item 3 stem changed; other items, choices, key, explanation, quiz_stamp, status, updated_at, the Note and the 7 copies unchanged).'

\echo 'AFTER (item 3):'
SELECT quiz -> 2 ->> 'question' AS after_stem, quiz -> 2 -> 'choices' AS choices, quiz -> 2 -> 'correctIndex' AS correct_index, quiz_stamp, updated_at
FROM study_packs WHERE id = '8efc275e-312e-4e0d-a97a-b6672585eb5d';

\echo 'ROLLBACK STATEMENT (SAVE THIS; use ONLY after a COMMIT you want to undo; expect UPDATE 1; afterwards md5(quiz::text) must be e7f206b08cbd9c6f352ffcc331838350):'
SELECT 'UPDATE study_packs SET quiz = jsonb_set(quiz, ''{2,question}'', to_jsonb(' || quote_literal('When budgeting, which step is LEAST important?') || '::text), false) WHERE id = ''8efc275e-312e-4e0d-a97a-b6672585eb5d'' AND quiz -> 2 ->> ''question'' = ' || quote_literal('Which of the following is NOT part of effective budgeting?') || ';' AS rollback_statement;

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
