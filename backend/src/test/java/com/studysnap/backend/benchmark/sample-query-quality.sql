-- READ ONLY: one SELECT. Run from repo root (replace URL with the owner's read-only database URL):
-- mkdir -p backend/target/llm-benchmark
-- psql "<External Database URL>" -At -f backend/src/test/java/com/studysnap/backend/benchmark/sample-query-quality.sql > backend/target/llm-benchmark/quality-e.jsonl
-- The output contains Official note text; keep it under target and never commit it.
-- STDERR-STYLE WARNING: require 10 notes, 4 strata and 2 quantitative notes in EACH level.
-- A missed target emits zero lines; stop, do not relax eligibility.
\warn 'QUALITY SAMPLE: require 10 COLLEGE and 10 BOARD_EXAM_REVIEW notes, each with 4 strata and 2 quantitative; zero lines means a target was missed.'
WITH base AS (
  SELECT n.id, sp.id AS pack_id, n.subject, n.tags, n.domain_context,
         n.learner_level AS note_level, n.course_program,
         regexp_replace(btrim(n.content), '\s+', ' ', 'g') AS note_text,
         sp.title AS sp_title, sp.summary AS sp_summary, sp.key_concepts, sp.quiz,
         (SELECT CASE WHEN count(*) = 1 THEN min(cp.name) END
            FROM note_course_program ncp JOIN course_programs cp ON cp.id = ncp.course_program_id
           WHERE ncp.note_id = n.id) AS joined_program,
         coalesce(n.domain_context, n.subject, 'none') AS stratum,
         md5(n.id::text || 'v0176-quality-eval') AS ord
  FROM notes n
  JOIN users u ON u.id = n.owner_user_id AND u.role = 'ADMIN' AND u.deleted_at IS NULL
  JOIN study_packs sp ON sp.note_id = n.id
  WHERE n.visibility = 'PUBLIC' AND n.copied_from_note_id IS NULL
    AND n.learner_level IN ('COLLEGE', 'BOARD_EXAM_REVIEW')
    AND length(regexp_replace(btrim(n.content), '\s+', ' ', 'g')) BETWEEN 400 AND 12000
    AND jsonb_typeof(sp.key_concepts) = 'array' AND jsonb_array_length(sp.key_concepts) >= 2
    AND (SELECT count(*) FROM challenge_quiz_question_bank bank
         JOIN users bank_owner ON bank_owner.id = bank.user_id AND bank_owner.role = 'ADMIN'
         WHERE bank.study_pack_id = sp.id
           -- same rule as ChallengeQuizQuestionBankRepository.findClaimableForUpdate: Official template rows carry a NULL stamp and are claimable
           AND (bank.generation_stamp IS NULL OR bank.generation_stamp = sp.generation_stamp)) >= 20
), marked AS (
  SELECT b.*,
         ((length(note_text) - length(replace(note_text, '$', '')))
          + (length(note_text) - length(replace(note_text, '\(', ''))) / 2
          + (SELECT count(*) FROM regexp_matches(note_text,
              '\m(compute|calculate|equation|formula)\M', 'gi'))) >= 3 AS quantitative
  FROM base b
), one_per_stratum AS (
  SELECT m.*, row_number() OVER (PARTITION BY note_level, stratum ORDER BY ord, id) AS stratum_rank
  FROM marked m
), strata_first AS (
  SELECT * FROM (
    SELECT s.id, s.note_level, s.quantitative,
           row_number() OVER (PARTITION BY note_level ORDER BY ord, id) AS pick_rank
    FROM one_per_stratum s WHERE stratum_rank = 1
  ) ranked WHERE pick_rank <= 4
), quant_candidates AS (
  SELECT m.id, m.note_level,
         row_number() OVER (PARTITION BY m.note_level ORDER BY m.ord, m.id) AS pick_rank
  FROM marked m WHERE m.quantitative
    AND NOT EXISTS (SELECT 1 FROM strata_first s WHERE s.id = m.id)
), quant_fill AS (
  SELECT q.id, q.note_level FROM quant_candidates q
  WHERE q.pick_rank <= (SELECT greatest(0, 2 - count(*)::int)
                        FROM strata_first s WHERE s.note_level = q.note_level AND s.quantitative)
), first_picks AS (
  SELECT id, note_level FROM strata_first UNION ALL SELECT id, note_level FROM quant_fill
), rest_candidates AS (
  SELECT m.id, m.note_level,
         row_number() OVER (PARTITION BY m.note_level ORDER BY m.ord, m.id) AS pick_rank
  FROM marked m WHERE NOT EXISTS (SELECT 1 FROM first_picks f WHERE f.id = m.id)
), rest AS (
  SELECT r.id, r.note_level FROM rest_candidates r
  WHERE r.pick_rank <= (SELECT greatest(0, 10 - count(*)::int) FROM first_picks f WHERE f.note_level = r.note_level)
), picks AS (
  SELECT id, note_level FROM first_picks UNION ALL SELECT id, note_level FROM rest
), selected AS (
  SELECT m.*, row_number() OVER (ORDER BY m.ord, m.id) AS run FROM marked m JOIN picks p ON p.id = m.id
), valid_pool AS (
  SELECT count(*) = 20 AND count(DISTINCT stratum) >= 6
         AND count(*) FILTER (WHERE quantitative) >= 4
         AND (SELECT count(*) = 10 AND count(DISTINCT stratum) >= 4
                     AND count(*) FILTER (WHERE quantitative) >= 2
              FROM selected WHERE note_level = 'COLLEGE')
         AND (SELECT count(*) = 10 AND count(DISTINCT stratum) >= 4
                     AND count(*) FILTER (WHERE quantitative) >= 2
              FROM selected WHERE note_level = 'BOARD_EXAM_REVIEW') AS ok FROM selected
)
SELECT jsonb_build_object(
  'id', 'Q-E-' || s.run, 'leg', 'E', 'cell', 'S15', 'run', s.run,
  'officialOwner', true, 'quantitative', s.quantitative, 'stratum', s.stratum,
  'noteLevel', s.note_level, 'effectiveLevel', s.note_level,
  'packId', s.pack_id,
  'sources', jsonb_build_array(jsonb_build_object(
    'studyPackTitle', s.sp_title, 'studyPackSummary', s.sp_summary,
    'keyConcepts', s.key_concepts,
    'disallowedQuestions', coalesce((SELECT jsonb_agg(q->>'question') FROM jsonb_array_elements(s.quiz) q), '[]'::jsonb),
    'sourceNoteText', s.note_text, 'tags', coalesce(to_jsonb(s.tags), '[]'::jsonb),
    'learnerLevel', 'COLLEGE', 'noteLearnerLevel', s.note_level,
    'domainContext', s.domain_context, 'subject', s.subject,
    'courseProgram', coalesce(s.joined_program, s.course_program))))::text
FROM selected s CROSS JOIN valid_pool v WHERE v.ok ORDER BY s.run;
