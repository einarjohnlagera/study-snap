-- 2026-09-05 — READ-ONLY triage: generation failures in the last 15 minutes
--
-- Written by the Prod Investigator session because the Render MCP server disconnected
-- (getaddrinfo ENOTFOUND mcp.render.com), so live logs and the MCP SQL path were unavailable.
-- ⚠️ EVERY STATEMENT HERE IS A SELECT. Nothing writes. Safe to run against production.
--
-- "Generation" spans five surfaces in this codebase and they fail independently, so Q1 is the
-- triage step: run it FIRST and let its counts decide which of Q2-Q6 is worth reading.
-- Column names verified against the migration set at v0.117.0 (V73, V118, V119).

-- ---------------------------------------------------------------------------------------------
-- Q1 — WHICH SURFACE IS FAILING. One row per surface; expect zeros on the healthy ones.
-- ⚠️ notes has NO generation_status_at column (V118 gave it generation_enqueued_at instead), so a
--    FAILED note is dated by updated_at. That is an upper bound on when it failed, not exact.
-- ---------------------------------------------------------------------------------------------
SELECT 'notes.FAILED' AS surface, count(*) AS rows_last_15m
FROM notes
WHERE status = 'FAILED' AND updated_at >= now() - interval '15 minutes'
UNION ALL
SELECT 'notes.GENERATING (still in flight)', count(*)
FROM notes
WHERE status = 'GENERATING' AND generation_enqueued_at >= now() - interval '15 minutes'
UNION ALL
SELECT 'notes.GENERATING (STUCK >15m)', count(*)
FROM notes
WHERE status = 'GENERATING' AND generation_enqueued_at < now() - interval '15 minutes'
UNION ALL
SELECT 'quick_review_sessions.FAILED', count(*)
FROM quick_review_sessions
WHERE status = 'FAILED' AND created_at >= now() - interval '15 minutes'
UNION ALL
SELECT 'quick_review_sessions.GENERATING (stuck)', count(*)
FROM quick_review_sessions
WHERE status = 'GENERATING' AND created_at < now() - interval '15 minutes'
UNION ALL
SELECT 'exam_question_pool non-terminal', count(*)
FROM exam_question_pool
WHERE generation_status IN ('PENDING', 'GENERATING')
UNION ALL
SELECT 'bulk_generation_result with failures', count(*)
FROM bulk_generation_result
WHERE created_at >= now() - interval '15 minutes' AND jsonb_array_length(failed_topics) > 0
ORDER BY 1;

-- ---------------------------------------------------------------------------------------------
-- Q2 — THE FAILED NOTES THEMSELVES. Who, what, and whether they cluster on one owner or subject.
-- ⚠️ Deliberately selects no note content — this is triage, not a data pull.
-- ---------------------------------------------------------------------------------------------
SELECT n.id,
       n.owner_user_id,
       n.subject,
       n.course_program,
       n.domain_context,
       n.learner_level,
       n.visibility,
       n.generation_enqueued_at,
       n.updated_at,
       n.updated_at - n.generation_enqueued_at AS time_to_failure
FROM notes n
WHERE n.status = 'FAILED'
  AND n.updated_at >= now() - interval '15 minutes'
ORDER BY n.updated_at DESC
LIMIT 50;

-- ---------------------------------------------------------------------------------------------
-- Q3 — DO THEY CLUSTER? One owner, one subject, or one domain context implicates authoring input;
--      spread across all three implicates the pipeline (LLM, pool, connection).
-- ---------------------------------------------------------------------------------------------
SELECT coalesce(subject, '(null)')        AS subject,
       coalesce(course_program, '(null)') AS course_program,
       coalesce(domain_context, '(null)') AS domain_context,
       count(*)                           AS failures,
       count(DISTINCT owner_user_id)      AS distinct_owners
FROM notes
WHERE status = 'FAILED'
  AND updated_at >= now() - interval '15 minutes'
GROUP BY 1, 2, 3
ORDER BY failures DESC;

-- ---------------------------------------------------------------------------------------------
-- Q4 — WHY, when the failure came through BULK generation. This is the only surface that persists
--      a machine-readable reason: v0.87.0 shipped failed_topic_reasons (V119) precisely because
--      "which topic" without "why" had already cost two investigations.
-- ⚠️ failed_topic_reasons is NULLABLE — rows written before V119 deployed carry only failed_topics.
-- ---------------------------------------------------------------------------------------------
SELECT b.id,
       b.owner_user_id,
       b.subject,
       b.course_program,
       b.requested_count,
       b.created_count,
       jsonb_array_length(b.failed_topics) AS failed_count,
       b.failed_topics,
       b.failed_topic_reasons,
       b.created_at
FROM bulk_generation_result b
WHERE b.created_at >= now() - interval '15 minutes'
  AND jsonb_array_length(b.failed_topics) > 0
ORDER BY b.created_at DESC;

-- ---------------------------------------------------------------------------------------------
-- Q5 — IS THIS THE OUTAGE, OR SOMETHING NEW? A generation that could not obtain a JDBC connection
--      is the SAME defect as the 11:45 UTC outage, not a separate one. Non-terminal pool rows with
--      an old clock are the recovery sweeper's own backlog.
--      See docs/claude-findings/2026-09-05-prod-outage-public-catalog-unbounded-read.md
-- ---------------------------------------------------------------------------------------------
SELECT generation_status,
       count(*)                     AS rows,
       min(generation_status_at)    AS oldest,
       max(generation_status_at)    AS newest
FROM exam_question_pool
WHERE generation_status IN ('PENDING', 'GENERATING')
GROUP BY generation_status
ORDER BY 1;

-- ---------------------------------------------------------------------------------------------
-- Q6 — BASELINE. Without this the counts above are unreadable: a handful of failures may be the
--      normal rate rather than an incident. Compare the last 15 minutes against the prior 24 h.
-- ---------------------------------------------------------------------------------------------
SELECT date_trunc('hour', updated_at) AS hour_utc,
       count(*)                       AS failed_notes
FROM notes
WHERE status = 'FAILED'
  AND updated_at >= now() - interval '24 hours'
GROUP BY 1
ORDER BY 1 DESC;

-- ==============================================================================================
-- ADDENDUM — note REGENERATION specifically (added after the owner clarified: ~14 "Site Planning"
-- notes regenerated, 3-4 failed).
--
-- ⚠️ READ THIS BEFORE RUNNING: the failure REASON is not in the database at all. The async worker
-- catches every exception, calls markNoteGenerationFailed, and logs it as
--     action=complete_async_studyPack_generation ... outcome=failed
-- with the stack trace (StudyPackService:884-897). Only `notes.status = 'FAILED'` is persisted.
-- v0.87.0 added failed_topic_reasons (V119) for BULK generation and single-note regeneration was
-- never given the equivalent — so these queries establish WHICH and WHEN, and the WHY needs logs.
-- ==============================================================================================

-- Q7 — the Site Planning cohort: what regenerated, what failed, and how they differ.
-- ⚠️ Adjust the subject/title filter to match how the cohort is actually labelled.
SELECT n.id,
       n.title,
       n.subject,
       n.status,
       n.course_program,
       n.domain_context,
       n.learner_level,
       length(n.content)                        AS content_len,
       (sp.id IS NOT NULL)                      AS has_study_pack,
       length(n.title)                          AS title_len,
       array_length(regexp_split_to_array(trim(n.title), '\s+'), 1) AS title_words,
       n.generation_enqueued_at,
       n.updated_at,
       n.updated_at - n.generation_enqueued_at  AS elapsed
FROM notes n
LEFT JOIN study_packs sp ON sp.note_id = n.id
WHERE (n.subject ILIKE '%site planning%' OR n.title ILIKE '%site planning%')
ORDER BY n.status, n.updated_at DESC;

-- Q8 — the three synchronous preconditions that reject a regeneration BEFORE any LLM call.
-- ⚠️ These produce an immediate HTTP error and leave status UNCHANGED — they never mark a note
--    FAILED. If the failed notes show FAILED in the UI, the cause is NOT here and Q8 will be empty
--    of explanations; that is itself informative, because it rules out three of seven modes.
--    (StudyPackService.startAsyncNoteAndStudyPackRegeneration: no pack -> 409, blank title -> 409,
--     multi-program with null domain_context -> MultiProgramDomainContextRequiredException.)
SELECT n.id,
       n.title,
       n.status,
       (sp.id IS NULL)                                   AS blocked_no_study_pack,
       (n.title IS NULL OR trim(n.title) = '')           AS blocked_blank_title,
       (cnt.program_count >= 2 AND n.domain_context IS NULL) AS blocked_multi_program_no_domain,
       cnt.program_count
FROM notes n
LEFT JOIN study_packs sp ON sp.note_id = n.id
LEFT JOIN LATERAL (
    SELECT count(*) AS program_count
    FROM note_course_program ncp
    WHERE ncp.note_id = n.id
) cnt ON TRUE
WHERE (n.subject ILIKE '%site planning%' OR n.title ILIKE '%site planning%')
ORDER BY n.title;

-- Q9 — is the pool starved again? A regeneration that cannot obtain a JDBC connection fails exactly
-- like one that failed on its own merits. If this returns non-trivial numbers the cause is the
-- 2026-09-05 outage defect, not the content pipeline.
SELECT count(*) FILTER (WHERE status = 'GENERATING')                                  AS notes_generating_now,
       count(*) FILTER (WHERE status = 'GENERATING'
                          AND generation_enqueued_at < now() - interval '15 minutes') AS notes_stuck_generating
FROM notes;
