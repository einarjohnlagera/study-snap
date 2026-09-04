-- v0.112.0 — Connection Pool Integrity: §5 falsification read for the 2026-09-04 outage
-- READ-ONLY. Run against PRODUCTION. Paste all five result blocks back verbatim.
-- Source: docs/claude-findings/2026-09-04-prod-outage-hikari-pool-exhaustion.md §5
--
-- ⚠️ THIS DECIDES WHETHER §3'S ROOT-CAUSE CLASS IS CONFIRMED OR STILL A HYPOTHESIS, AND PHASE 3 IS
-- SIZED ON THE ANSWER. Phase 3 restructures transaction boundaries on six services and re-opens quota
-- semantics on twelve charge sites. That is the correct response IF concurrent LLM generations
-- exhausted the pool. It is a large, risky change aimed at the wrong target if something else did.
--
-- ⚠️ WHAT THE LOG CANNOT TELL US, so nobody re-derives it from the log instead of running this (§4):
-- the log carries 39 level-bearing lines out of 763, contains ZERO INFO lines before the restart, and
-- is WARN/ERROR-filtered over a ~90-second window. NO `study-pack-generation-` or `llm-parallel-`
-- thread appears anywhere in it. "No application error precedes the exhaustion" is NOT established.
-- Absence of generation-thread errors in that log is ABSENCE OF EVIDENCE, not evidence of absence.
--
-- ⚠️ HOW TO READ THE RESULT — decided BEFORE the read, so the answer is not fitted to the hypothesis:
--   * A cluster of CHALLENGE rows in Q1                 -> CONFIRMS the hypothesis. Phase 3 as scoped.
--   * ZERO rows across Q1, Q2 AND Q4 together           -> REFUTES it. Redirect to a slow query or lock
--     on a large table (admin dashboard, funnel, account export), a long document import, or a
--     Render-side database event — and RE-SCOPE PHASE 3 BEFORE BUILDING IT.
--   * Anything in between                               -> still a hypothesis. Say so; do not round up.
--
-- ⚠️ CRON TIMING DOES NOT LINE UP AND THAT IS ALREADY CHECKED — do not re-derive it.
-- GenerationRecoveryJob fired 05:50:00 and BulkGenerationResultCleanupJob 05:45:00; everything else
-- runs 01:15–03:30 UTC. Saturation began ~05:54:43, back-dated from the 23,672 ms health check logged
-- at 05:55:07. GenerationRecoveryService is explicitly NOT @Transactional (:27) and sweeps row-by-row.
--
-- ⚠️ ALL TIMESTAMPS ARE UTC. The window is deliberately wider than the incident (05:45–05:56) so the
-- run-up is visible, not just the failure.

-- Q1 — THE DECIDING QUERY. Quiz sessions started in the window, by mode and status.
-- A cluster of CHALLENGE rows is the signature of concurrent synchronous generations, which is the
-- hypothesis: ChallengeQuizService is class-level @Transactional (:84) with the LLM call INSIDE it
-- (:449), so each concurrent start holds a pool connection for the whole generation.
SELECT
    session_mode,
    status,
    COUNT(*) AS sessions
FROM quick_review_sessions
WHERE created_at BETWEEN TIMESTAMPTZ '2026-09-04 05:45:00+00'
                     AND TIMESTAMPTZ '2026-09-04 05:56:00+00'
GROUP BY 1, 2
ORDER BY sessions DESC;

-- Q2 — What learners were actually doing in the window. Independent of Q1's table, so it corroborates
-- rather than repeats: if Q1 is empty but Q2 is busy, the load was real and came from somewhere else.
SELECT
    event_type,
    COUNT(*) AS events
FROM analytics_events
WHERE created_at BETWEEN TIMESTAMPTZ '2026-09-04 05:45:00+00'
                     AND TIMESTAMPTZ '2026-09-04 05:56:00+00'
GROUP BY 1
ORDER BY events DESC;

-- Q3 — Tests the GenerationRecoveryJob path specifically. It fired at 05:50:00, four minutes before
-- saturation. If it marked pools FAILED, a later sampleQuestions() call triggers refreshPool() on each,
-- which fills the generation executor — a plausible alternative route to the same exhaustion.
-- ⚠️ generation_status_at is the right clock here, NOT created_at: pool rows are REUSED, so created_at
-- does not move when a pool is regenerated (recorded in v0.86.0).
SELECT
    generation_status,
    COUNT(*) AS pools
FROM exam_question_pool
WHERE generation_status_at BETWEEN TIMESTAMPTZ '2026-09-04 05:45:00+00'
                               AND TIMESTAMPTZ '2026-09-04 05:56:00+00'
GROUP BY 1
ORDER BY pools DESC;

-- Q4 — Study Pack generations in flight. StudyPackService is one of the fourteen paths that hold a
-- connection across an OpenAI call, and this is the highest-volume of them.
-- ⚠️ THE FINDING'S §5 QUERY 4 NAMES `notes.generation_status_at`, WHICH DOES NOT EXIST AND WOULD HAVE
-- ERRORED. Verified against V118 and NoteEntity:92 at Phase 2: V118 adds `generation_status_at` to
-- exam_question_pool (used by Q3 above) but adds `generation_enqueued_at` to notes — two different
-- clocks, deliberately, because pool rows are REUSED and note rows are not. Corrected here.
SELECT COUNT(*) AS notes_generating
FROM notes
WHERE status = 'GENERATING'
  AND generation_enqueued_at BETWEEN TIMESTAMPTZ '2026-09-04 05:45:00+00'
                                 AND TIMESTAMPTZ '2026-09-04 05:56:00+00';

-- Q5 — The document-import path. Distinct from the LLM paths and much smaller (CPU-bound seconds, not
-- LLM minutes), but it holds the same class-level transaction across PDDocument.load and
-- PDFTextStripper.getText over up to 30 pages, on 0.5 CPU.
-- ⚠️ THE OCR PATH IS NOT WHAT THIS TESTS AND MUST NOT BE READ AS SUCH — §6 RETRACTED that finding:
-- Vision is DISABLED in production (OCR_ENABLED=false) and extractFromPdfViaOcr:144-146 throws
-- immediately, before the quota check and before any Vision call.
SELECT COUNT(*) AS drafts_created
FROM study_pack_drafts
WHERE created_at BETWEEN TIMESTAMPTZ '2026-09-04 05:45:00+00'
                     AND TIMESTAMPTZ '2026-09-04 05:56:00+00';
