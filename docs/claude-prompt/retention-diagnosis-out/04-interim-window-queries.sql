-- Interim-window analytics pulls (2026-07-15 strategy checkpoint)
-- Schema-verified against the actual entities/migrations, not assumed.
-- Run these against production. All three are answerable with zero code changes.
--
-- NOT included here — confirmed to need real instrumentation first, not just a query:
--   - UTM/referral/acquisition-source tracking (no column/table exists anywhere)
--   - Offline-fallback-page hit rate (sw.js/offline.html serve the page but report nothing back)
--   - Public catalog "browse" tracking (published-plans-page-client.tsx fires zero analytics events;
--     EXAM_HUB_VIEWED is a different funnel step — public-note discovery, not the Review Set catalog —
--     do not substitute it)
-- See docs/claude-prompt/retention-diagnosis-session-plan.md's "Strategy checkpoint" for what to do
-- about each of those instead of a query.

-- ============================================================
-- Query 1: Device mix (mobile vs. desktop), approximate
-- ============================================================
-- No clean device_type column exists. This proxies off refresh_tokens.user_agent
-- (populated on every login/signup/refresh), classified by a pattern match.
-- Caveat: completeness depends on refresh_tokens retention — if expired/revoked
-- rows are pruned, a long look-back window may undercount older sessions. Check
-- for a cleanup job before trusting anything beyond a recent window.

SELECT
    CASE
        WHEN user_agent ~* 'Mobile|Android|iPhone|iPod' THEN 'mobile'
        WHEN user_agent ~* 'iPad|Tablet' THEN 'tablet'
        WHEN user_agent IS NULL THEN 'unknown'
        ELSE 'desktop'
    END AS device_class,
    COUNT(*) AS token_count,
    COUNT(DISTINCT user_id) AS distinct_users
FROM refresh_tokens
GROUP BY device_class
ORDER BY token_count DESC;

-- ============================================================
-- Query 2: PDF export volume per user
-- ============================================================
-- Covers only the Quiz Session Review PDF export path (QUIZ_REVIEW_EXPORTED).
-- Does NOT cover DOCX export (/quizzes/*/export-docx fires no analytics event at
-- all today — that gap needs its own instrumentation if DOCX volume matters too).

SELECT
    user_id,
    COUNT(*) AS pdf_export_count,
    MIN(created_at) AS first_export,
    MAX(created_at) AS last_export
FROM analytics_events
WHERE event_type = 'QUIZ_REVIEW_EXPORTED'
GROUP BY user_id
ORDER BY pdf_export_count DESC;

-- Aggregate version, if per-user detail isn't needed:
-- SELECT COUNT(*) AS total_pdf_exports, COUNT(DISTINCT user_id) AS distinct_exporters
-- FROM analytics_events WHERE event_type = 'QUIZ_REVIEW_EXPORTED';

-- ============================================================
-- Query 3: Official Review Set coverage per courseProgram bucket
-- ============================================================
-- "Official" = admin-published root collections (visibility = PUBLIC, no parent).
-- No isOfficial flag exists; this is the correct derived definition per
-- docs/features/collections.md. Accounts for the Goal/Subject hierarchy wrinkle:
-- a Goal holds zero items directly — its notes live on child Subject plans.

WITH official_roots AS (
    SELECT id, course_program
    FROM note_collections
    WHERE visibility = 'PUBLIC' AND parent_collection_id IS NULL
)
SELECT
    r.course_program,
    COUNT(DISTINCT r.id) AS published_review_set_count,
    COUNT(nci.id) AS note_count
FROM official_roots r
LEFT JOIN note_collections child
    ON child.parent_collection_id = r.id
LEFT JOIN note_collection_items nci
    ON nci.collection_id = r.id OR nci.collection_id = child.id
GROUP BY r.course_program
ORDER BY r.course_program;

-- Run this first, before the interviews — if ALE/PNLE/LET already show thin
-- coverage (few sets, few notes), that's your prior for the content-gap
-- hypothesis before a single interview lands.

-- ============================================================
-- Query 4: Adoption activity for the 35 exam-dated churned users (weaker signal)
-- ============================================================
-- This is NOT the full "browse-without-adopt" metric — the browse side doesn't
-- exist yet (see note at top). This only tells you whether these users adopted
-- ANYTHING, not whether they looked and found nothing. Treat as a partial signal,
-- not a substitute for the real metric once catalog-view tracking exists.
-- Reuses the same exam-dated-churned cohort definition as the existing
-- retention-diagnosis Query 2 (03-data-pull-queries.sql) — has_exam_date = true,
-- not returned in the week-2 window.

-- Fill in the same cohort CTE from 03-data-pull-queries.sql's Query 2, then:
SELECT u.id, COUNT(ae.id) AS adopt_events
FROM <exam_dated_churned_cohort> u
LEFT JOIN analytics_events ae
    ON ae.user_id = u.id AND ae.event_type = 'STUDY_PLAN_ADOPTED'
GROUP BY u.id;
