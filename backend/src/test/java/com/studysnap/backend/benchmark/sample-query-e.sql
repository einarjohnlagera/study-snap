-- Leg E input. READ ONLY: one SELECT. Run from the repository root:
-- mkdir -p backend/target/llm-benchmark
-- psql "<External Database URL>" -At -f backend/src/test/java/com/studysnap/backend/benchmark/sample-query-e.sql \
--   > backend/target/llm-benchmark/e.jsonl
-- Contains source note text; never commit the output.
WITH base AS (
  SELECT n.id, n.subject, n.tags, n.domain_context, n.learner_level AS note_level,
         u.learner_level AS owner_level, n.course_program,
         regexp_replace(btrim(n.content), '\s+', ' ', 'g') AS note_text,
         sp.title AS sp_title, sp.summary AS sp_summary, sp.key_concepts, sp.quiz,
         (SELECT CASE WHEN count(*) = 1 THEN min(cp.name) END
            FROM note_course_program ncp JOIN course_programs cp ON cp.id = ncp.course_program_id
           WHERE ncp.note_id = n.id) AS joined_program,
         coalesce(n.domain_context, n.subject, 'none') AS stratum,
         md5(n.id::text || 'v0174-leg-e') AS ord
  FROM notes n
  JOIN users u ON u.id = n.owner_user_id AND u.deleted_at IS NULL
  JOIN study_packs sp ON sp.note_id = n.id
  WHERE n.visibility = 'PUBLIC' AND n.copied_from_note_id IS NULL
    AND length(regexp_replace(btrim(n.content), '\s+', ' ', 'g')) BETWEEN 400 AND 12000
    AND jsonb_typeof(sp.key_concepts) = 'array' AND jsonb_array_length(sp.key_concepts) >= 2
),
ranked AS (
  SELECT b.*, row_number() OVER (ORDER BY rn_in_stratum, ord) AS pick
  FROM (SELECT base.*, row_number() OVER (PARTITION BY stratum ORDER BY ord) AS rn_in_stratum FROM base) b
),
source_rows AS (
  SELECT r.*, jsonb_build_object(
    'studyPackTitle', sp_title, 'studyPackSummary', sp_summary,
    'keyConcepts', key_concepts,
    'disallowedQuestions', coalesce((SELECT jsonb_agg(q->>'question') FROM jsonb_array_elements(r.quiz) q), '[]'::jsonb),
    'sourceNoteText', note_text,
    'tags', coalesce(to_jsonb(tags), '[]'::jsonb), 'learnerLevel', owner_level,
    'noteLearnerLevel', note_level, 'domainContext', domain_context,
    'subject', subject, 'courseProgram', coalesce(joined_program, course_program)) AS source
  FROM ranked r
),
singles AS (SELECT pick AS run, source FROM source_rows WHERE pick <= 10),
subject_pool AS (
  SELECT s.*, row_number() OVER (PARTITION BY subject ORDER BY ord) AS within_subject
  FROM source_rows s WHERE pick > 10 AND subject IS NOT NULL
),
eligible_subjects AS (
  SELECT subject, min(ord) AS subject_order
  FROM subject_pool GROUP BY subject HAVING count(*) >= 6
),
subject_groups AS (
  SELECT subject, row_number() OVER (ORDER BY subject_order, subject) AS run
  FROM eligible_subjects ORDER BY subject_order, subject LIMIT 6
),
group_sources AS (
  SELECT g.run, p.within_subject, p.source
  FROM subject_groups g JOIN subject_pool p ON p.subject = g.subject
  WHERE p.within_subject <= 6
),
m6 AS (
  SELECT run, jsonb_agg(source ORDER BY within_subject) AS sources
  FROM group_sources GROUP BY run
),
m3 AS (
  SELECT run, jsonb_agg(source ORDER BY within_subject) AS sources
  FROM group_sources WHERE within_subject <= 3 GROUP BY run
),
lines AS (
  SELECT 1 AS priority, 'S15' AS cell, run, jsonb_build_array(source) AS sources FROM singles
  UNION ALL SELECT 2, 'M6x3', run, sources FROM m6
  UNION ALL SELECT 3, 'S10', run, jsonb_build_array(source) FROM singles
  UNION ALL SELECT 4, 'S12', run, jsonb_build_array(source) FROM singles
  UNION ALL SELECT 5, 'M3x6', run, sources FROM m3
  UNION ALL SELECT 6, 'VLOW', run, jsonb_build_array(source) FROM singles
)
SELECT (jsonb_build_object('id', 'E-' || cell || '-' || run, 'leg', 'E',
                          'cell', cell, 'run', run, 'sources', sources))::text
FROM lines ORDER BY priority, run;
