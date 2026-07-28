-- ONBOARDING_V2_ABANDONED leak-path discriminator — run against production Postgres, read-only.
-- Companion to docs/product/ROADMAP.md's "Diagnostic Read" section and
-- 08-diagnostic-read-methodology.md's "Results — Onboarding funnel re-check" section.
-- Scoped 2026-07-28, after an Opus-reviewed root-cause analysis of why
-- ONBOARDING_V2_ABANDONED's distinct-user count (69) exceeded ONBOARDING_V2_STARTED's (68)
-- in the same query window.
--
-- ============================================================================
-- WHAT THIS ANSWERS
-- ============================================================================
-- Two competing explanations were on the table:
--   (1) Window-boundary artifact — a session straddling the query's created_at cutoff
--       (STARTED just outside the window, ABANDONED just inside it) could produce a
--       1-user gap on its own, with no code bug involved.
--   (2) Leak paths in frontend/app/onboarding/page.tsx — several early-return redirects
--       (already-verified-email check, two separate already-completed-onboarding checks,
--       a getMe() rejection) fire ONBOARDING_V2_ABANDONED but never reach the
--       ONBOARDING_V2_STARTED tracking code at all, for any time window.
--
-- This query removes the time-window variable entirely: it checks, with NO bound on
-- STARTED's timestamp, whether any user has an in-window ABANDONED event but has
-- *never*, at any point, fired STARTED. If explanation (2) is real, this returns > 0
-- regardless of window boundaries. If it returns 0, the leak paths aren't actually
-- being hit in practice and the boundary-artifact explanation (1) is the better fit.
--
-- SCHEMA: analytics_events(id, user_id, event_type, created_at, metadata) — same table
-- as 08-diagnostic-read-queries.sql's Query 8.
--
-- ============================================================================
-- QUERY A — users with an in-window ABANDONED but no STARTED, ever
-- ============================================================================
SELECT COUNT(DISTINCT a.user_id) AS abandoned_never_started
FROM analytics_events a
WHERE a.event_type = 'ONBOARDING_V2_ABANDONED'
  AND a.created_at >= '2026-07-23'::timestamptz
  AND a.user_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM analytics_events s
    WHERE s.user_id = a.user_id
      AND s.event_type = 'ONBOARDING_V2_STARTED'   -- deliberately no time bound
  );

-- ============================================================================
-- QUERY B — corroborate the over-fire independently: events-per-user for ABANDONED,
-- and where in the flow (last_step) the over-fired events cluster
-- ============================================================================
-- If the over-fire bug (Bug 1: cleanup keyed on draft.currentStep) is real, expect
-- events-per-user well above 1 for at least some users, with mass concentrated at
-- low last_step values (people over-fire on ordinary early forward navigation, not
-- just once at their actual drop-off point).
SELECT
    metadata->>'last_step' AS last_step,
    COUNT(*) AS raw_events,
    COUNT(DISTINCT user_id) AS distinct_users,
    ROUND(COUNT(*)::numeric / NULLIF(COUNT(DISTINCT user_id), 0), 2) AS events_per_user
FROM analytics_events
WHERE event_type = 'ONBOARDING_V2_ABANDONED'
  AND created_at >= '2026-07-23'::timestamptz
GROUP BY last_step
ORDER BY raw_events DESC;

-- ============================================================================
-- HOW TO READ THE RESULTS
-- ============================================================================
-- Query A > 0  -> leak paths confirmed as at least part of the real cause; the
--                 window-boundary theory alone is insufficient.
-- Query A = 0  -> the 2026-07-28 window's specific gap may be a pure boundary
--                 artifact, and the leak paths are a latent bug not yet the observed
--                 cause -- still worth fixing, just not the explanation for this
--                 particular number.
-- Query B: events_per_user meaningfully > 1, with mass at low last_step -> confirms
--                 Bug 1 (the over-fire) independently of Query A's answer on Bug 2.
