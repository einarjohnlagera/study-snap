-- R2 second repair handoff: 13 more Official template key fixes, 1 learner-copy key fix,
-- and 12 Official template rows deleted. OWNER RUNS THIS. Claude has not run it and will not.
-- Source: docs/claude-findings/2026-10-19-v0175-r2-statement-pair-answer-key-scan.md (owner decisions 2 and 4).
-- Independent of the first handoff (2026-10-19-v0175-r2-template-answer-key-repair.sql); either order works.
--
-- Key fixes: confirmed wrong by an independent re-check, with an explanation that supports the correct
-- answer. Only question->'correctIndex' changes; correctIndices is an empty array on these rows and
-- QuizItem.resolveCorrectIndex takes a valid correctIndex first. Guarded as in the first handoff.
-- The learner row's last_known_outcome is left as history (owner decision 4).
--
-- Deletes: confirmed wrong (or self-contradictory) rows whose explanation is also wrong, so a key change
-- alone would not fix them. All 12 are ADMIN-owned template rows, unclaimed, with no learner copies
-- (checked 2026-10-19). No foreign key references challenge_quiz_question_bank. A template missing a row
-- copies fewer questions, and the shortfall is generated live.
--
-- EXPECTED: step 1 returns template_fix 13/13, learner_fix 1/1, delete_found 12.
-- Step 2 reports UPDATE 13, UPDATE 1, DELETE 12. Anything else: ROLLBACK and send the output back.

-- 1. Preview (read-only)
WITH fix(id, target, keyed) AS (VALUES
  ('1507f5b8-f411-4883-8284-6ef69a146b41'::uuid, 'TF', 'FT'),
  ('6a6a5926-acbe-47b7-b2ed-ecb3d1ea8e8c'::uuid, 'FT', 'FF'),
  ('7fe6d4f9-5032-4dc4-a467-15f4bc8a1a0f'::uuid, 'TF', 'TT'),
  ('82fab8e7-0144-42fd-96a2-ec2ad875291e'::uuid, 'FT', 'TT'),
  ('9126ff4a-29aa-4784-8b7a-cc82e9f57c92'::uuid, 'TF', 'TT'),
  ('944d9f2d-5112-41c2-a3f5-abcc876bc6c3'::uuid, 'TF', 'TT'),
  ('9687301a-03a7-418c-90ce-5532f36a55a6'::uuid, 'TF', 'FT'),
  ('99e8902f-9948-4ec0-b0a8-7e969170994c'::uuid, 'TF', 'FT'),
  ('9fee84e3-b774-4439-97a8-ab0bd9d215dd'::uuid, 'FT', 'TF'),
  ('a8478dbb-9582-4a58-b882-41e18cf40f2d'::uuid, 'TF', 'FT'),
  ('c6a4d14a-03d8-4def-9157-50d5ba9167f2'::uuid, 'TF', 'FT'),
  ('e8837e09-81c6-4222-bcd7-8777834bdc1f'::uuid, 'TF', 'FT'),
  ('f5da852d-cddc-40b2-9e16-5bd7ddd796a7'::uuid, 'TF', 'TT')
), t AS (
  SELECT f.id, (b.question->>'correctIndex')::int old_index,
    (SELECT (o.i - 1)::int FROM jsonb_array_elements_text(b.question->'choices') WITH ORDINALITY o(c, i) WHERE CASE f.target WHEN 'TT' THEN o.c ~* '^([a-d][:.)]\s*)?both' WHEN 'TF' THEN o.c ~* 'only statement\s*(1|i)\M'
                 WHEN 'FT' THEN o.c ~* 'only statement\s*(2|ii)\M' WHEN 'FF' THEN o.c ~* '^([a-d][:.)]\s*)?neither' END) new_index,
    (SELECT (o.i - 1)::int FROM jsonb_array_elements_text(b.question->'choices') WITH ORDINALITY o(c, i) WHERE CASE f.keyed WHEN 'TT' THEN o.c ~* '^([a-d][:.)]\s*)?both' WHEN 'TF' THEN o.c ~* 'only statement\s*(1|i)\M'
                 WHEN 'FT' THEN o.c ~* 'only statement\s*(2|ii)\M' WHEN 'FF' THEN o.c ~* '^([a-d][:.)]\s*)?neither' END) keyed_index
  FROM fix f JOIN challenge_quiz_question_bank b ON b.id = f.id JOIN users u ON u.id = b.user_id AND u.role = 'ADMIN'
)
SELECT 'template_fix' what, count(*) found, count(*) FILTER (WHERE new_index IS NOT NULL AND new_index <> old_index AND keyed_index = old_index) will_change FROM t;

WITH fix(id, target, keyed) AS (VALUES
  ('701891d3-dd62-4a52-af01-c2c806b69861'::uuid, 'TF', 'FT')
), t AS (
  SELECT f.id, (b.question->>'correctIndex')::int old_index,
    (SELECT (o.i - 1)::int FROM jsonb_array_elements_text(b.question->'choices') WITH ORDINALITY o(c, i) WHERE CASE f.target WHEN 'TT' THEN o.c ~* '^([a-d][:.)]\s*)?both' WHEN 'TF' THEN o.c ~* 'only statement\s*(1|i)\M'
                 WHEN 'FT' THEN o.c ~* 'only statement\s*(2|ii)\M' WHEN 'FF' THEN o.c ~* '^([a-d][:.)]\s*)?neither' END) new_index,
    (SELECT (o.i - 1)::int FROM jsonb_array_elements_text(b.question->'choices') WITH ORDINALITY o(c, i) WHERE CASE f.keyed WHEN 'TT' THEN o.c ~* '^([a-d][:.)]\s*)?both' WHEN 'TF' THEN o.c ~* 'only statement\s*(1|i)\M'
                 WHEN 'FT' THEN o.c ~* 'only statement\s*(2|ii)\M' WHEN 'FF' THEN o.c ~* '^([a-d][:.)]\s*)?neither' END) keyed_index
  FROM fix f JOIN challenge_quiz_question_bank b ON b.id = f.id JOIN users u ON u.id = b.user_id AND u.role <> 'ADMIN'
)
SELECT 'learner_fix' what, count(*) found, count(*) FILTER (WHERE new_index IS NOT NULL AND new_index <> old_index AND keyed_index = old_index) will_change FROM t;

WITH d(id) AS (VALUES
  ('2481bb98-662c-4092-b8a6-6142229c0ce5'::uuid),
  ('2cf31004-3b8e-4329-bc20-b762ecdb01f3'::uuid),
  ('4472ce58-424e-4581-808d-a544bc562460'::uuid),
  ('552b6290-52f8-446b-8576-9efdb435b3bc'::uuid),
  ('5966cd66-f57d-4cf2-8bb7-cf976739d571'::uuid),
  ('732df2dc-4473-47fd-a2e5-56a9e221846c'::uuid),
  ('90e34652-ac1d-4232-a066-aa3d05d92c8a'::uuid),
  ('983d6d40-f783-4535-a2e3-c31978356490'::uuid),
  ('9918c461-2dc4-419f-8d8b-21df49cb3537'::uuid),
  ('a6224208-d453-4e89-ab04-404e22ed237c'::uuid),
  ('c39cbef2-2e56-4c18-843e-ac179c3ee354'::uuid),
  ('ed97c036-e283-43d6-817a-002e138ed2ca'::uuid)
)
SELECT 'delete' what, count(*) found, count(*) FILTER (WHERE u.role = 'ADMIN' AND b.claimed_session_id IS NULL) deletable
FROM d JOIN challenge_quiz_question_bank b ON b.id = d.id JOIN users u ON u.id = b.user_id;

-- 2. Apply (owner only)
BEGIN;

WITH fix(id, target, keyed) AS (VALUES
  ('1507f5b8-f411-4883-8284-6ef69a146b41'::uuid, 'TF', 'FT'),
  ('6a6a5926-acbe-47b7-b2ed-ecb3d1ea8e8c'::uuid, 'FT', 'FF'),
  ('7fe6d4f9-5032-4dc4-a467-15f4bc8a1a0f'::uuid, 'TF', 'TT'),
  ('82fab8e7-0144-42fd-96a2-ec2ad875291e'::uuid, 'FT', 'TT'),
  ('9126ff4a-29aa-4784-8b7a-cc82e9f57c92'::uuid, 'TF', 'TT'),
  ('944d9f2d-5112-41c2-a3f5-abcc876bc6c3'::uuid, 'TF', 'TT'),
  ('9687301a-03a7-418c-90ce-5532f36a55a6'::uuid, 'TF', 'FT'),
  ('99e8902f-9948-4ec0-b0a8-7e969170994c'::uuid, 'TF', 'FT'),
  ('9fee84e3-b774-4439-97a8-ab0bd9d215dd'::uuid, 'FT', 'TF'),
  ('a8478dbb-9582-4a58-b882-41e18cf40f2d'::uuid, 'TF', 'FT'),
  ('c6a4d14a-03d8-4def-9157-50d5ba9167f2'::uuid, 'TF', 'FT'),
  ('e8837e09-81c6-4222-bcd7-8777834bdc1f'::uuid, 'TF', 'FT'),
  ('f5da852d-cddc-40b2-9e16-5bd7ddd796a7'::uuid, 'TF', 'TT')
), t AS (
  SELECT f.id, (b.question->>'correctIndex')::int old_index,
    (SELECT (o.i - 1)::int FROM jsonb_array_elements_text(b.question->'choices') WITH ORDINALITY o(c, i) WHERE CASE f.target WHEN 'TT' THEN o.c ~* '^([a-d][:.)]\s*)?both' WHEN 'TF' THEN o.c ~* 'only statement\s*(1|i)\M'
                 WHEN 'FT' THEN o.c ~* 'only statement\s*(2|ii)\M' WHEN 'FF' THEN o.c ~* '^([a-d][:.)]\s*)?neither' END) new_index,
    (SELECT (o.i - 1)::int FROM jsonb_array_elements_text(b.question->'choices') WITH ORDINALITY o(c, i) WHERE CASE f.keyed WHEN 'TT' THEN o.c ~* '^([a-d][:.)]\s*)?both' WHEN 'TF' THEN o.c ~* 'only statement\s*(1|i)\M'
                 WHEN 'FT' THEN o.c ~* 'only statement\s*(2|ii)\M' WHEN 'FF' THEN o.c ~* '^([a-d][:.)]\s*)?neither' END) keyed_index
  FROM fix f JOIN challenge_quiz_question_bank b ON b.id = f.id JOIN users u ON u.id = b.user_id AND u.role = 'ADMIN'
)
UPDATE challenge_quiz_question_bank q SET question = jsonb_set(q.question, '{correctIndex}', to_jsonb(t.new_index))
FROM t WHERE q.id = t.id AND t.new_index IS NOT NULL AND t.new_index <> t.old_index AND t.keyed_index = t.old_index;
-- Expect: UPDATE 13

WITH fix(id, target, keyed) AS (VALUES
  ('701891d3-dd62-4a52-af01-c2c806b69861'::uuid, 'TF', 'FT')
), t AS (
  SELECT f.id, (b.question->>'correctIndex')::int old_index,
    (SELECT (o.i - 1)::int FROM jsonb_array_elements_text(b.question->'choices') WITH ORDINALITY o(c, i) WHERE CASE f.target WHEN 'TT' THEN o.c ~* '^([a-d][:.)]\s*)?both' WHEN 'TF' THEN o.c ~* 'only statement\s*(1|i)\M'
                 WHEN 'FT' THEN o.c ~* 'only statement\s*(2|ii)\M' WHEN 'FF' THEN o.c ~* '^([a-d][:.)]\s*)?neither' END) new_index,
    (SELECT (o.i - 1)::int FROM jsonb_array_elements_text(b.question->'choices') WITH ORDINALITY o(c, i) WHERE CASE f.keyed WHEN 'TT' THEN o.c ~* '^([a-d][:.)]\s*)?both' WHEN 'TF' THEN o.c ~* 'only statement\s*(1|i)\M'
                 WHEN 'FT' THEN o.c ~* 'only statement\s*(2|ii)\M' WHEN 'FF' THEN o.c ~* '^([a-d][:.)]\s*)?neither' END) keyed_index
  FROM fix f JOIN challenge_quiz_question_bank b ON b.id = f.id JOIN users u ON u.id = b.user_id AND u.role <> 'ADMIN'
)
UPDATE challenge_quiz_question_bank q SET question = jsonb_set(q.question, '{correctIndex}', to_jsonb(t.new_index))
FROM t WHERE q.id = t.id AND t.new_index IS NOT NULL AND t.new_index <> t.old_index AND t.keyed_index = t.old_index;
-- Expect: UPDATE 1

DELETE FROM challenge_quiz_question_bank b USING users u
WHERE u.id = b.user_id AND u.role = 'ADMIN' AND b.claimed_session_id IS NULL AND b.id IN (
  '2481bb98-662c-4092-b8a6-6142229c0ce5'::uuid,
  '2cf31004-3b8e-4329-bc20-b762ecdb01f3'::uuid,
  '4472ce58-424e-4581-808d-a544bc562460'::uuid,
  '552b6290-52f8-446b-8576-9efdb435b3bc'::uuid,
  '5966cd66-f57d-4cf2-8bb7-cf976739d571'::uuid,
  '732df2dc-4473-47fd-a2e5-56a9e221846c'::uuid,
  '90e34652-ac1d-4232-a066-aa3d05d92c8a'::uuid,
  '983d6d40-f783-4535-a2e3-c31978356490'::uuid,
  '9918c461-2dc4-419f-8d8b-21df49cb3537'::uuid,
  'a6224208-d453-4e89-ab04-404e22ed237c'::uuid,
  'c39cbef2-2e56-4c18-843e-ac179c3ee354'::uuid,
  'ed97c036-e283-43d6-817a-002e138ed2ca'::uuid
);
-- Expect: DELETE 12. Then COMMIT; otherwise ROLLBACK.
-- COMMIT;

-- 3. Verify (read-only): re-run step 1; will_change must be 0 for both fixes and delete_found 0.
