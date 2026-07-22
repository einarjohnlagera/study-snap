-- Retention diagnosis data pulls — run against production Postgres.
-- Companion to docs/claude-prompt/retention-diagnosis-session-plan.md.
-- All three queries reuse the exact activation/eligibility/return definitions
-- already used by AdminFunnelService's W1->W2 retention read, so results are
-- directly comparable to the 2.4% (3/127) number already on the Admin dashboard.
--
-- Activation = a user's first STUDY_PACK_GENERATED row in analytics_events.
-- Eligible   = that first event happened >= 14 days ago (so the W2 window has closed).
-- Returned   = any analytics_events row (any type) in (first_pack_at + 7d, first_pack_at + 14d].
--
-- Table/column names confirmed against migrations (legacy "studysnap" naming):
--   users(id, email_verified_at, exam_date, profile_type, course_program)
--   notes(id, owner_user_id, status)              -- ready state is 'GENERATED', not 'STUDY_PACK_READY'
--   study_packs(id, owner_user_id, note_id, created_at)
--   quick_review_sessions(id, user_id, session_mode, status, created_at, completed_at)
--     status: GENERATING, FAILED, IN_PROGRESS, PAUSED, COMPLETED, FORFEITED
--   analytics_events(user_id, event_type, created_at, metadata_json)

-- ============================================================================
-- QUERY 1 — Week-1 depth: of eligible activated users, how many take a real
-- second study action (a COMPLETED quiz session) more than 24h after their
-- first pack, but still inside week 1 (before the W2 boundary)?
--
-- Why 24h: excludes same-session follow-through (viewing the pack, taking the
-- first quiz right after generating) so this isolates a genuine return visit,
-- not the tail of session 1.
--
-- Read this against the 2.4% W2 number: if W1 depth here is healthy but W2
-- collapses anyway, the problem is the cross-week trigger, not session-1 value.
-- If W1 depth is ALSO near zero, session-1 value itself is suspect.
-- ============================================================================

WITH first_pack AS (
    SELECT user_id, MIN(created_at) AS first_pack_at
    FROM analytics_events
    WHERE event_type = 'STUDY_PACK_GENERATED'
      AND user_id IS NOT NULL
    GROUP BY user_id
),
eligible AS (
    SELECT user_id, first_pack_at
    FROM first_pack
    WHERE first_pack_at <= now() - INTERVAL '14 days'
),
week1_return AS (
    SELECT e.user_id
    FROM eligible e
    WHERE EXISTS (
        SELECT 1
        FROM quick_review_sessions qrs
        WHERE qrs.user_id = e.user_id
          AND qrs.status = 'COMPLETED'
          AND qrs.completed_at > e.first_pack_at + INTERVAL '24 hours'
          AND qrs.completed_at <= e.first_pack_at + INTERVAL '7 days'
    )
)
SELECT
    COUNT(*) AS eligible_activated_users,
    (SELECT COUNT(*) FROM week1_return) AS returned_within_week1_after_24h,
    ROUND(
        100.0 * (SELECT COUNT(*) FROM week1_return) / NULLIF(COUNT(*), 0),
        2
    ) AS week1_depth_pct
FROM eligible;

-- ============================================================================
-- QUERY 2 — Exam-date natural experiment: does W1->W2 retention differ for
-- users who set an exam date vs. those who didn't, using the identical
-- cohort/eligibility/return definition as the existing Admin dashboard read?
--
-- This is the cheapest test of the "exam date is the missing commitment
-- device" hypothesis — no code change, just a segmented read of data you
-- already have.
-- ============================================================================

WITH first_pack AS (
    SELECT user_id, MIN(created_at) AS first_pack_at
    FROM analytics_events
    WHERE event_type = 'STUDY_PACK_GENERATED'
      AND user_id IS NOT NULL
    GROUP BY user_id
),
eligible AS (
    SELECT fp.user_id, fp.first_pack_at, (u.exam_date IS NOT NULL) AS has_exam_date
    FROM first_pack fp
    JOIN users u ON u.id = fp.user_id
    WHERE fp.first_pack_at <= now() - INTERVAL '14 days'
),
returned AS (
    SELECT e.user_id, e.has_exam_date
    FROM eligible e
    WHERE EXISTS (
        SELECT 1
        FROM analytics_events ae
        WHERE ae.user_id = e.user_id
          AND ae.created_at > e.first_pack_at + INTERVAL '7 days'
          AND ae.created_at <= e.first_pack_at + INTERVAL '14 days'
    )
)
SELECT
    e.has_exam_date,
    COUNT(*) AS eligible_activated_users,
    COUNT(r.user_id) AS returned_in_week2,
    ROUND(100.0 * COUNT(r.user_id) / NULLIF(COUNT(*), 0), 2) AS w1_to_w2_retention_pct
FROM eligible e
LEFT JOIN returned r ON r.user_id = e.user_id
GROUP BY e.has_exam_date
ORDER BY e.has_exam_date DESC;

-- ============================================================================
-- QUERY 3 — Acquisition-source segmentation: NOT CLEANLY AVAILABLE.
--
-- There is no UTM/referral/channel column on `users`, and no dedicated
-- signup-source table. The closest signal is analytics_events.metadata_json
-- on SIGNUP / SIGNUP_STARTED / SIGNUP_COMPLETED / LANDING_PAGE_VIEWED /
-- LANDING_CTA_CLICKED rows, but there's no guaranteed structured field in
-- that payload — whatever the frontend happened to log, if anything.
--
-- Run this first to see what's actually in there before trusting any
-- segmentation built on top of it:

SELECT event_type, metadata_json, created_at
FROM analytics_events
WHERE event_type IN ('SIGNUP', 'SIGNUP_STARTED', 'SIGNUP_COMPLETED', 'LANDING_PAGE_VIEWED', 'LANDING_CTA_CLICKED')
ORDER BY created_at DESC
LIMIT 50;

-- If that payload contains something usable (a referral/source/utm field),
-- write the real segmentation query from what you actually see there rather
-- than assuming a field name. If it's empty or unstructured, acquisition-
-- source segmentation isn't answerable from existing data — it would need
-- new instrumentation (out of scope for this diagnosis pass) before it can
-- be tested at all.
