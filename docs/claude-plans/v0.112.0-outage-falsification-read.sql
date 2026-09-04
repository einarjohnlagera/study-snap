-- ⚠️⚠️ RUN 2026-09-04 (v0.114.0). RESULTS AND VERDICT: see §12 of the finding named below.
-- ⚠️ DO NOT RE-RUN THIS AS IF IT WERE STILL OPEN, AND DO NOT TRUST §11's "Refuted" LIST WITHOUT §12.
-- Q1, Q3, Q4, Q5 and Q7 all returned ZERO — and Q7's zero means Q1's zero is the NORMAL state for that
-- clock hour, so the window was widened (05:00-05:56 sessions, 04:00-05:56 enqueued notes) and stayed
-- zero. **Q2 is the one that matters**, and ONLY once the RAW TIMESTAMPS are pulled — the minute bucket
-- misleads. It is not 12 visitors: it is ONE note hit SIXTEEN times in 78 seconds (twelve of them in
-- eight), on a page otherwise viewed 1-2 times an HOUR across eleven days, starting ~15 s BEFORE the
-- saturation signature. That REOPENS the read-burst hypothesis §11 refuted from a metric §11 itself
-- recorded as unreliable here. ⚠️ DIRECTION IS NOT SETTLED — symptom-vs-cause is argued both ways in §12.
-- VERDICT: "anything in between" — still a hypothesis, NOT rounded up. §3's cause is now positively
-- unsupported, which matters because Phase 3 is built to address it.
--
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
-- ⚠️⚠️ THE REFUTATION BRANCH ABOVE HAS A HOLE, FOUND BY A COLD RE-READ OF THE LOG ON 2026-09-04 AND
-- VERIFIED AGAINST CODE. READ THIS BEFORE CONCLUDING ANYTHING FROM ZERO ROWS.
-- Q1-Q5 all read GENERATION, SESSION, POOL and DRAFT tables — every one of them is a record of a
-- WRITE. **None of them can detect an anonymous READ burst**, because reads leave no row anywhere.
-- So "zero rows across Q1, Q2 and Q4" does NOT refute a read-burst cause. IT FAILS TO SEE IT.
-- ⚠️⚠️ CORRECTED 2026-09-04 (v0.114.0), AND THE CORRECTION IS WHY THIS READ WAS WORTH RUNNING: THE
-- SENTENCE ABOVE IS WRONG ABOUT Q2. `analytics_events` is NOT a write-record of a learner action — it
-- records PAGE VIEWS, including anonymous ones, and it DID see the read activity. Q2 is therefore the
-- LOAD-BEARING query in this file, not a corroborating one. A later session reading only the banner
-- above would discount §12's finding, which is the opposite of what this banner was written to prevent.
-- ⚠️ THE LIMIT THAT DOES HOLD: an ISR revalidation, a crawler or any non-browser client runs no
-- client-side JavaScript and leaves no row, so Q2 is a FLOOR on read activity and never a total.
--
-- ⚠️ THIS IS NOT A HYPOTHETICAL ALTERNATIVE, AND THREE FACTS MAKE IT LIVE:
--   1. THE ONLY PATH THE LOG ACTUALLY NAMES AS STARVED IS SUCH AN ENDPOINT. The one stack trace in
--      the log (`CannotCreateTransactionException`, log L381-L391) is
--      `NoteCollectionController.listPublic` -> `NoteCollectionService.listPublic:206`, which is
--      `@Transactional(readOnly = true)`, runs several unpaginated repository calls per invocation,
--      and is `permitAll` (`SecurityConfig:60`, alongside `/notes/public/**` :50 and `/public/**` :49).
--      The log names a VICTIM and names ZERO holders.
--   2. THIS REPO HAS ALREADY HAD PUBLIC-ENDPOINT FETCHES SATURATE THE BACKEND. See
--      `docs/claude-findings/2026-09-01-prod-frontend-build-failure-public-notes-2mb.md` and CLAUDE.md:
--      "~250 static pages each issue their own 2.5 MB request in one build UNTIL THE BACKEND SATURATES."
--      ⚠️ That specific pathology WAS FIXED in v0.100.0 — verified in code, not assumed:
--      `generateStaticParams` now reads `/subjects?scope=public` and `getServerPublicNotes()` paginates.
--      It is cited as PRECEDENT for the class, not as a live defect.
--   3. THESE ENDPOINTS ARE RE-FETCHED ON A SCHEDULE, INDEPENDENT OF DEPLOYS.
--      `app/public/library/[subject]/page.tsx:28` sets `export const revalidate = 300` and
--      `lib/server-public-notes.ts:57,189` fetch with `next: { revalidate: 300 }`. So public-endpoint
--      load recurs every ~5 minutes as pages go stale — it does NOT require a deploy or a build.
--
-- ⚠️ A DEPLOY-TRIGGERED BUILD IS RULED OUT FOR THIS INCIDENT, so do not spend time on it: the last
-- merge to `main` before the outage was `e513f867` at 2026-09-04 **03:07:21 UTC**, two hours 48 minutes
-- earlier, and the restarted JVM reports the SAME version (`v0.110.2`) with no deploy line in the log.
--
-- ⚠️ THE DATABASE CANNOT SETTLE THIS. Q6 below is a WEAK PROXY only. The decisive evidence is
-- REQUEST-RATE DATA, which lives in Render, not Postgres: pull request logs for `/collections/public`,
-- `/notes/public` and `/public/**` over 05:45-05:56 UTC, plus datastore connection counts. If those
-- show a burst, the cause is a read burst on unpaginated public endpoints and **Phase 3 — which
-- restructures six services' transaction boundaries and twelve quota sites — is aimed at the wrong
-- target.**
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

-- Q6 — WEAK PROXY for anonymous traffic in the window. ⚠️ READ THE LIMITS BEFORE USING IT.
-- `AnalyticsController` records `user_id` as NULL for anonymous callers, so this counts anonymous
-- BROWSER activity. It does NOT see the traffic most likely to matter here: a server-side ISR
-- revalidation, a crawler, or any non-browser client fetches the API directly and runs no client-side
-- JavaScript, so it fires NO analytics event and leaves NO row.
-- ⚠️ THEREFORE: a non-zero result is informative; A ZERO RESULT REFUTES NOTHING AT ALL.
SELECT
    COUNT(*)                                   AS anonymous_events,
    COUNT(DISTINCT event_type)                 AS distinct_event_types
FROM analytics_events
WHERE user_id IS NULL
  AND created_at BETWEEN TIMESTAMPTZ '2026-09-04 05:45:00+00'
                     AND TIMESTAMPTZ '2026-09-04 05:56:00+00';

-- Q7 — Baseline for Q1-Q6. ⚠️ WITHOUT THIS, "12 sessions" MEANS NOTHING — it could be a quiet Tuesday
-- or ten times normal. Same clock-hour on the seven preceding days, so the incident window can be
-- compared against its own normal rather than against an intuition.
SELECT
    date_trunc('day', created_at)::date        AS day,
    COUNT(*)                                   AS sessions_0545_0556
FROM quick_review_sessions
WHERE created_at >= TIMESTAMPTZ '2026-08-28 00:00:00+00'
  AND created_at <  TIMESTAMPTZ '2026-09-05 00:00:00+00'
  AND created_at::time BETWEEN TIME '05:45' AND TIME '05:56'
GROUP BY 1
ORDER BY 1;
