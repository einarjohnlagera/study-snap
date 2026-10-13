-- v0.170.0 Item 2 (multi-goal exam support) -- V155__user_exam_goals.sql verification
-- All queries below are READ-ONLY SELECTs, safe to run against notelib-db-prod at any time.
-- Written by Claude at v0.170.0 signoff, 2026-10-13, before releases/v0.170.0 merges to main.
-- V155 has NOT yet run against production as of this writing -- it ships with this release
-- and will apply automatically on the next deploy to main (Flyway, backend boot).

-- ============================================================
-- PRE-DEPLOY BASELINE (captured 2026-10-13, read-only, before V155 has run against prod)
-- ============================================================
-- Expected INSERT row count: 16 users will be migrated into user_exam_goals.
-- Expected UPDATE row count: the same 16 users will have study_goal set to NULL.
-- Of those 16, 0 also have focus_subjects set -- no ambiguous dual-state case exists
-- in current data (confirmed below), though UserExamGoalService's own precedence
-- rules still need to hold for any FUTURE user who reaches that combination.
SELECT
  count(*) FILTER (WHERE lower(trim(study_goal)) IN ('ale','pnle','let','cpale','ce')) AS expected_migrated,
  count(*) FILTER (WHERE lower(trim(study_goal)) IN ('ale','pnle','let','cpale','ce')
    AND cardinality(focus_subjects) > 0) AS matched_with_focus_subjects_also_set,
  count(*) AS total_users
FROM users WHERE role = 'USER';
-- Result when run 2026-10-13: expected_migrated=16, matched_with_focus_subjects_also_set=0, total_users=426

-- ============================================================
-- POST-DEPLOY VERIFICATION (run after v0.170.0 deploys to prod and V155 has applied)
-- ============================================================

-- 1. Confirm V155 is recorded as applied.
SELECT version, description, success, installed_on
FROM flyway_schema_history WHERE version = '155';
-- Expect: exactly one row, success = true.

-- 2. Confirm exactly 16 rows landed in user_exam_goals, one per migrated user, all active.
SELECT count(*) AS migrated_rows, count(*) FILTER (WHERE is_active) AS active_rows
FROM user_exam_goals;
-- Expect: migrated_rows = 16, active_rows = 16 (no second goal adopted yet at deploy instant).

-- 3. Confirm the matched users' studyGoal was actually cleared (no dual-storage of the same fact).
SELECT count(*) AS still_has_old_slug
FROM users
WHERE id IN (SELECT user_id FROM user_exam_goals)
  AND lower(trim(study_goal)) IN ('ale','pnle','let','cpale','ce');
-- Expect: still_has_old_slug = 0.

-- 4. Confirm the partial unique index exists (defends the exactly-one-active-goal invariant).
SELECT indexname, indexdef FROM pg_indexes
WHERE tablename = 'user_exam_goals' AND indexname = 'ux_user_exam_goals_one_active';
-- Expect: one row, indexdef containing "WHERE is_active".

-- ============================================================
-- DISTAL CHECKPOINT MEASUREMENT (due deploy + 60 days -- ROADMAP.md Backlog Index)
-- ============================================================
-- Direct measurement, now that user_exam_goals exists -- prefer this over the old
-- adopted-collections proxy query, which only ever approximated this population
-- before learners could explicitly adopt a second goal.
SELECT count(*) AS users_with_2plus_goals
FROM (
  SELECT user_id FROM user_exam_goals GROUP BY user_id HAVING count(*) >= 2
) x;
-- Baseline at deploy instant: expect 0 (migration only ever inserts one row per user).
-- Compare at +60 days against the pre-existing 2-of-426 adopted-collections proxy baseline.

-- ============================================================
-- PROXIMAL CHECKPOINT MEASUREMENT (due deploy + 14 days -- ROADMAP.md Backlog Index)
-- ============================================================
-- Does EXAM_GOAL_ADDED fire at all in production once the picker is live?
SELECT count(*) AS exam_goal_added_events
FROM analytics_events
WHERE event_type = 'EXAM_GOAL_ADDED' AND created_at > now() - interval '14 days';
-- Zero in 14 days with the picker live is a signal, not a kill criterion on its own.
