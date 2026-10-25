-- R2 fourth repair handoff: delete 1 Official template row. OWNER RUNS THIS. Claude has not run it and will not.
-- Source: docs/claude-findings/2026-10-19-v0175-r2-statement-pair-answer-key-scan.md ("Post-repair check").
--
-- Row 2a48c6af-8837-4069-bb5d-7241f8eddc86 was key-fixed by handoff 3 to "Only Statement 1", which is correct.
-- Its explanation, however, opens with "Both statements are correct." and then argues the opposite. That is
-- the "explanation also wrong" case, which the rule says to delete. The re-check misfiled it as a key fix.
-- ADMIN-owned template row; the guard also requires it to be unclaimed and to still carry the corrected key.
--
-- HOW TO RUN (repo root):
--   dry run:  psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f docs/claude-plans/2026-10-20-v0175-r2-template-answer-key-repair-4.sql
--   apply:    the same command with  -c "COMMIT;"  added at the end
-- EXPECTED: the preview shows found 1 / deletable 1 / learner_copies 0; then DELETE 1.

-- 1. Preview (read-only)
SELECT count(*) found,
  count(*) FILTER (WHERE u.role = 'ADMIN' AND b.claimed_session_id IS NULL AND (b.question->>'correctIndex')::int = 1) deletable,
  (SELECT count(*) FROM challenge_quiz_question_bank lq JOIN study_packs lsp ON lsp.id = lq.study_pack_id JOIN notes ln ON ln.id = lsp.note_id
     JOIN study_packs osp ON osp.note_id = ln.copied_from_note_id JOIN challenge_quiz_question_bank b2 ON b2.study_pack_id = osp.id AND b2.question_key = lq.question_key
     WHERE b2.id = '2a48c6af-8837-4069-bb5d-7241f8eddc86' AND lq.id <> b2.id) learner_copies
FROM challenge_quiz_question_bank b JOIN users u ON u.id = b.user_id
WHERE b.id = '2a48c6af-8837-4069-bb5d-7241f8eddc86';

-- 2. Apply (owner only)
BEGIN;
DELETE FROM challenge_quiz_question_bank b USING users u
WHERE u.id = b.user_id AND u.role = 'ADMIN' AND b.claimed_session_id IS NULL
  AND (b.question->>'correctIndex')::int = 1
  AND b.id = '2a48c6af-8837-4069-bb5d-7241f8eddc86';
-- Expect: DELETE 1. Then COMMIT; otherwise ROLLBACK.
-- COMMIT;

-- 3. Verify (read-only): re-run step 1; found must be 0.
