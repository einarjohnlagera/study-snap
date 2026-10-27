-- READ ONLY: one SELECT. From repo root, pass exactly the 20 pre-registered pack UUIDs:
-- psql "<External Database URL>" -v "pack_ids='uuid-1','uuid-2',...,'uuid-20'" -At \
--   -f backend/src/test/java/com/studysnap/backend/benchmark/sample-query-stock.sql \
--   > backend/target/llm-benchmark/quality-stock.jsonl
-- STDERR-STYLE WARNING: expect 100 JSONL lines (five per pack); stop if fewer.
\warn 'QUALITY STOCK: require 100 output lines, five from each pre-registered pack; stop if fewer.'
WITH ranked AS (
  SELECT bank.study_pack_id AS pack_id, bank.question, bank.generated_at,
         row_number() OVER (PARTITION BY bank.study_pack_id
                            ORDER BY md5(bank.question_key || 'v0176-stock'), bank.id) AS position
  FROM challenge_quiz_question_bank bank
  JOIN study_packs sp ON sp.id = bank.study_pack_id
  JOIN users u ON u.id = bank.user_id AND u.role = 'ADMIN'
  WHERE bank.study_pack_id IN (:pack_ids)
    AND bank.claimed_session_id IS NULL
    -- same rule as findClaimableForUpdate: NULL-stamp Official template rows are claimable
    AND (bank.generation_stamp IS NULL OR bank.generation_stamp = sp.generation_stamp)
)
SELECT jsonb_build_object('packId', pack_id, 'question', question, 'generatedAt', generated_at)::text
FROM ranked WHERE position <= 5 ORDER BY pack_id, position;
