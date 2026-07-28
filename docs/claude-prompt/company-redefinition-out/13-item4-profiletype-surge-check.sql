-- v0.60.3 Item 4 kickoff gate — run against production Postgres, read-only.
-- Companion to docs/product/ROADMAP.md's "Item 4 — Onboarding coverage-gap capture"
-- sequencing-risk note and to 08-diagnostic-read-queries.sql (same Diagnostic Read
-- this gate protects). Scoped 2026-07-28.
--
-- ============================================================================
-- WHAT THIS ANSWERS
-- ============================================================================
-- Item 4 adds a STUDENT-scoped onboarding coverage-gap capture prompt. The Diagnostic
-- Read (docs/product/ROADMAP.md, "Diagnostic Read" section) is mid-measurement on the
-- 2026-07-23 signup surge and its own text warns that reorganizing onboarding mid-surge
-- would pollute that read's funnel data. The surge cohort skews Board-Exam/exam-dated,
-- but "skews toward" isn't "excludes" — this query checks the real profile_type
-- composition instead of proceeding on the skew assumption.
--
-- PASS BAR (per ROADMAP, not a judgment call): the cohort must show EFFECTIVELY ZERO
-- STUDENT-profile accounts for Item 4 to proceed now. "Mostly Board Exam" or "skews
-- Board Exam" is NOT a pass — any meaningful STUDENT presence means Item 4 waits
-- unconditionally until the Diagnostic Read closes (~2026-08-06), regardless of how
-- small that presence looks.
--
-- SCHEMA: users(id, created_at, profile_type, onboarding_completed_at) — confirmed
-- against 08-diagnostic-read-queries.sql's schema reference, same table.
--
-- ============================================================================
-- QUERY A — profile_type composition of the open measurement window
-- ============================================================================
-- Everyone signed up on or after the surge day (2026-07-23) through now — this is
-- the population still inside its 14-day W1->W2 window, i.e. what the Diagnostic
-- Read's re-read (after 2026-08-06) will actually score. This is the primary answer
-- to "is the measurement cohort STUDENT-free."

SELECT
    profile_type,
    COUNT(*) AS signups,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2) AS pct_of_window
FROM users
WHERE created_at >= '2026-07-23'::timestamptz
GROUP BY profile_type
ORDER BY signups DESC;

-- ============================================================================
-- QUERY B — profile_type composition of the surge day itself (tighter check)
-- ============================================================================
-- Same breakdown, narrowed to exactly the spike day Round 1 identified (29 signups,
-- per 08-diagnostic-read-methodology.md "Results — Round 1"). Confirms Query A's
-- read isn't being diluted or skewed by ordinary daily volume in the days since.

SELECT
    profile_type,
    COUNT(*) AS signups,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2) AS pct_of_day
FROM users
WHERE created_at::date = '2026-07-23'
GROUP BY profile_type
ORDER BY signups DESC;

-- ============================================================================
-- QUERY C — raw NULL check
-- ============================================================================
-- profile_type is set during onboarding (see onboarding/page.tsx); a signup that
-- never completed onboarding has no profile_type yet and won't show up as a labeled
-- STUDENT row in A/B even if they would have chosen STUDENT. Since the chronic ~50%
-- onboarding non-completion rate is an already-confirmed, separate finding (Round 1),
-- don't read a low STUDENT count in A/B as automatically clean without checking how
-- many rows in the same window are still NULL here.

SELECT
    COUNT(*) AS window_signups,
    COUNT(profile_type) AS has_profile_type,
    COUNT(*) - COUNT(profile_type) AS null_profile_type
FROM users
WHERE created_at >= '2026-07-23'::timestamptz;
