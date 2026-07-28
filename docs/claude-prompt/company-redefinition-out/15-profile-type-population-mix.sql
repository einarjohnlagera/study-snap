-- Profile-type population mix — run against production Postgres, read-only.
-- Companion to docs/product/ROADMAP.md's "Prioritization Lens & Strategic Frame"
-- section and the "Profile-type population mix across the full user base" Backlog
-- Index row it added. Scoped 2026-07-28.
--
-- ============================================================================
-- WHAT THIS ANSWERS
-- ============================================================================
-- The 2026-07-28 reprioritization discussion floated "Review Set adoption is
-- displacing note-authoring as the primary populate-path" as evidence NoteLib's
-- center of gravity has shifted journey-centric. The only profile_type split known
-- at the time was the 2026-07-23 surge cohort (STUDENT 6.9% / BOARD_EXAM 58.6%,
-- from 13-item4-profiletype-surge-check.sql) — that's the surge only, not the base,
-- and practice-first adoption is BOARD_EXAM-only (Phase 1 gate), so the surge's own
-- skew doesn't tell us anything about the overall population. This query checks the
-- real profile_type composition across every account, not just the surge.
--
-- SCHEMA: users(id, created_at, profile_type, onboarding_completed_at) — confirmed
-- against 08-diagnostic-read-queries.sql / 13-item4-profiletype-surge-check.sql,
-- same table.
--
-- ============================================================================
-- QUERY A — profile_type composition, full user base, all time
-- ============================================================================
SELECT
    profile_type,
    COUNT(*) AS accounts,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2) AS pct_of_base
FROM users
GROUP BY profile_type
ORDER BY accounts DESC;

-- ============================================================================
-- QUERY B — profile_type composition, onboarded accounts only
-- ============================================================================
-- Narrows to accounts that actually completed onboarding (profile_type is set
-- deliberately, not a default/placeholder) — the cleaner read if Query A's NULL
-- bucket is large enough to distort the picture.
SELECT
    profile_type,
    COUNT(*) AS accounts,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2) AS pct_of_onboarded
FROM users
WHERE onboarding_completed_at IS NOT NULL
GROUP BY profile_type
ORDER BY accounts DESC;

-- ============================================================================
-- QUERY C — pre-surge vs. surge-and-after, same breakdown
-- ============================================================================
-- Checks whether the mix is shifting over time (surge pulling the base toward
-- BOARD_EXAM) or whether the surge is a blip on top of an otherwise-stable mix.
-- Surge day: 2026-07-23 (per 08-diagnostic-read-methodology.md "Results — Round 1").
SELECT
    CASE WHEN created_at < '2026-07-23'::timestamptz THEN 'pre_surge' ELSE 'surge_and_after' END AS cohort,
    profile_type,
    COUNT(*) AS accounts,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (PARTITION BY
        CASE WHEN created_at < '2026-07-23'::timestamptz THEN 'pre_surge' ELSE 'surge_and_after' END
    ), 2) AS pct_within_cohort
FROM users
GROUP BY cohort, profile_type
ORDER BY cohort, accounts DESC;
