-- Diagnostic Read data pulls — run against production Postgres, read-only.
-- Companion to docs/claude-prompt/company-redefinition-out/08-diagnostic-read-methodology.md
-- and 07-reprioritization.md. Scoped 2026-07-24 in response to a signup surge
-- (~15 signups in one evening) plus a product/UX "quizzes as reusable assets"
-- realization — this is the diagnostic step that comes before either is acted on.
--
-- ============================================================================
-- WHY THE EXISTING W1->W2 DEFINITION HAD TO CHANGE (read this before anything else)
-- ============================================================================
-- The existing Admin-dashboard read (the 2.4%/127, exam-dated 0%/41 figures) anchors
-- "activated" on a user's first STUDY_PACK_GENERATED row in analytics_events. That
-- was a correct proxy for "got real value" when every onboarding path ended in
-- note-authoring + LLM generation. v0.57.0's practice-first branch breaks that
-- proxy: a BOARD_EXAM learner who adopts an Official Review Set gets a COPIED
-- Study Pack (NoteService.copySourceStudyPack(), no LLM call) — STUDY_PACK_GENERATED
-- never fires for them. Under the old definition, every practice-first-onboarded
-- learner is INVISIBLE to the read, not "ineligible" — they never enter the cohort
-- at all, regardless of how they actually retain. Since the surge is exactly the
-- population this release targets, queries below use TWO anchors so nothing is lost:
--   (A) signup-anchored (users.created_at)      -- primary, path-agnostic, NEW
--   (B) activation-anchored, WIDENED             -- historical comparability, FIXED
--       "activated" = first(STUDY_PACK_GENERATED OR ONBOARDING_V2_COMPLETED)
--       (ONBOARDING_V2_COMPLETED fires for both the create-first and practice-first
--       paths — confirmed at frontend/app/onboarding/page.tsx:622,775 — so this
--       union no longer excludes practice-first adopters.)
--
-- ============================================================================
-- TIMING WARNING — do not misread an empty/small result (same trap V0.48.0's
-- re-check query flagged; repeating it here on purpose)
-- ============================================================================
-- A user is only ELIGIBLE for a completed W1->W2 read once their anchor date is
-- >= 14 days in the past (so the W2 window has actually closed). The signup surge
-- happened in the last ~24-48h as of this writing (2026-07-24) — that specific
-- batch will not be eligible until roughly 14 days after it happened. Running
-- these queries now will correctly EXCLUDE that freshest batch from the eligible
-- cohort; that is "not yet measurable," not a bad result. The queries below
-- therefore read the whole recent trailing window (Query 1 shows you its real
-- shape), which already contains plenty of already-eligible users from just
-- before the spike, so a directional number exists today — re-run once the
-- newest batch's 14-day window closes to add it in.
--
-- ============================================================================
-- SCHEMA REFERENCE (confirmed directly against entities/migrations, not assumed)
-- ============================================================================
--   users(id, created_at, email_verified_at, onboarding_completed_at, profile_type,
--         course_program, exam_date)
--   analytics_events(user_id [nullable, no FK], event_type, entity_id [polymorphic —
--         meaning depends on event_type, do not join it generically], metadata_json,
--         created_at)
--   user_activity_events(user_id, study_pack_id, activity_type, created_at)
--       activity_type values: CREATED_STUDY_PACK, OPENED_STUDY_PACK,
--         STARTED_QUICK_REVIEW, STARTED_ADAPTIVE_PRACTICE, COMPLETED_QUICK_REVIEW,
--         COMPLETED_CHALLENGE_QUIZ, COMPLETED_ADAPTIVE_QUIZ. "Meaningful" subset
--         (ActivityType.MEANINGFUL_STUDY_ACTIVITIES) excludes only OPENED_STUDY_PACK.
--   user_usage(user_id, month, year, period_start, period_end, study_pack_generations,
--         challenge_quiz_generations, adaptive_quiz_generations,
--         interview_practice_used_this_month, long_exam_used_this_month,
--         board_exam_used_this_month, ocr_extractions, note_generations,
--         docx_exports_count, pdf_exports_count, quiz_share_links_created)
--       IMPORTANT: period_start/period_end is a ROLLING PER-USER BILLING CYCLE
--       anchored to that user's own signup day-of-month — NOT a calendar month.
--       Filter by period_start/period_end overlap with your date range, never by
--       month/year alone, or usage will be misattributed across users with
--       different signup dates.
--   study_packs(id, model_tier, model_used, input_tokens, output_tokens,
--         cached_input_tokens, estimated_cost) -- estimated_cost is always NULL in
--       practice; the only entity with real token grounding at all.

-- ============================================================================
-- QUERY 1 — Signup trend: is this a one-evening spike, or a step-change?
-- ============================================================================
-- Run this FIRST. It tells you the real shape of the ramp (Facebook/LET-driven,
-- per the reprioritization doc) and defines what "the surge cohort" actually means
-- for the queries below — do not hardcode a single evening's date without looking
-- at this output first.

SELECT
    date_trunc('day', created_at) AS signup_day,
    COUNT(*) AS signups
FROM users
WHERE created_at >= now() - INTERVAL '30 days'
GROUP BY signup_day
ORDER BY signup_day;

-- Eyeball this before continuing. If elevated volume goes back further than the
-- one evening that prompted attention, widen RAMP_START below to match — the
-- read needs enough eligible (14-day-closed) users to clear the ~30/arm
-- directional floor / ~75+/arm decision-grade floor already used for the Phase 1
-- read (docs/product/ROADMAP.md, Phase 1 section).

-- ============================================================================
-- Shared parameter — set this from Query 1's output before running Queries 2-6.
-- ============================================================================
-- RAMP_START: the date the elevated signup volume actually begins (not
-- necessarily just the single most-visible evening). Placeholder below;
-- replace '2026-07-01' with the real value read off Query 1.

-- ============================================================================
-- QUERY 2 — Signup-anchored W1->W2 (PRIMARY, new, path-agnostic)
-- ============================================================================
-- Anchor = users.created_at. Catches practice-first adopters the old
-- activation-anchored definition misses entirely (see header). Computes BOTH
-- return-signal variants side by side:
--   returned_any_event        = matches historical precedent (broadest signal)
--   returned_meaningful_study = stricter: actual study behavior, not just any
--                               app telemetry (user_activity_events, "meaningful"
--                               subset). Divergence between the two is itself a
--                               finding — high any_event / low meaningful_study
--                               means people are "returning" without studying.

WITH cohort AS (
    SELECT id AS user_id, created_at
    FROM users
    WHERE created_at >= '2026-07-01'::timestamptz  -- RAMP_START, see above
),
eligible AS (
    SELECT user_id, created_at
    FROM cohort
    WHERE created_at <= now() - INTERVAL '14 days'
),
returned_any_event AS (
    SELECT e.user_id
    FROM eligible e
    WHERE EXISTS (
        SELECT 1 FROM analytics_events ae
        WHERE ae.user_id = e.user_id
          AND ae.created_at > e.created_at + INTERVAL '7 days'
          AND ae.created_at <= e.created_at + INTERVAL '14 days'
    )
),
returned_meaningful_study AS (
    SELECT e.user_id
    FROM eligible e
    WHERE EXISTS (
        SELECT 1 FROM user_activity_events uae
        WHERE uae.user_id = e.user_id
          AND uae.activity_type <> 'OPENED_STUDY_PACK'
          AND uae.created_at > e.created_at + INTERVAL '7 days'
          AND uae.created_at <= e.created_at + INTERVAL '14 days'
    )
)
SELECT
    COUNT(*) AS eligible_signups,
    (SELECT COUNT(*) FROM returned_any_event) AS returned_any_event,
    (SELECT COUNT(*) FROM returned_meaningful_study) AS returned_meaningful_study,
    ROUND(100.0 * (SELECT COUNT(*) FROM returned_any_event) / NULLIF(COUNT(*), 0), 2)
        AS w1_to_w2_retention_pct_any_event,
    ROUND(100.0 * (SELECT COUNT(*) FROM returned_meaningful_study) / NULLIF(COUNT(*), 0), 2)
        AS w1_to_w2_retention_pct_meaningful_study
FROM eligible;

-- ============================================================================
-- QUERY 3 — Activation-anchored W1->W2 (historical comparability, FIXED)
-- ============================================================================
-- Same shape as the existing Admin-dashboard read, but reports BOTH the old
-- (STUDY_PACK_GENERATED only) and widened (+ ONBOARDING_V2_COMPLETED) activation
-- definitions side by side, so the widening's effect on the count is itself
-- visible. The old-definition row should roughly reproduce the existing
-- 2.4%/127 baseline when run over a comparable historical window — if it
-- doesn't, treat that as a bug to fix before trusting anything below.

WITH first_pack_old AS (
    SELECT user_id, MIN(created_at) AS activated_at
    FROM analytics_events
    WHERE event_type = 'STUDY_PACK_GENERATED'
      AND user_id IS NOT NULL
    GROUP BY user_id
),
first_pack_widened AS (
    SELECT user_id, MIN(created_at) AS activated_at
    FROM analytics_events
    WHERE event_type IN ('STUDY_PACK_GENERATED', 'ONBOARDING_V2_COMPLETED')
      AND user_id IS NOT NULL
    GROUP BY user_id
),
eligible AS (
    SELECT 'old_definition' AS definition, user_id, activated_at
    FROM first_pack_old
    WHERE activated_at <= now() - INTERVAL '14 days'
    UNION ALL
    SELECT 'widened_definition' AS definition, user_id, activated_at
    FROM first_pack_widened
    WHERE activated_at <= now() - INTERVAL '14 days'
),
returned AS (
    SELECT e.definition, e.user_id
    FROM eligible e
    WHERE EXISTS (
        SELECT 1 FROM analytics_events ae
        WHERE ae.user_id = e.user_id
          AND ae.created_at > e.activated_at + INTERVAL '7 days'
          AND ae.created_at <= e.activated_at + INTERVAL '14 days'
    )
)
SELECT
    e.definition,
    COUNT(*) AS eligible_activated_users,
    COUNT(r.user_id) AS returned_in_week2,
    ROUND(100.0 * COUNT(r.user_id) / NULLIF(COUNT(*), 0), 2) AS w1_to_w2_retention_pct
FROM eligible e
LEFT JOIN returned r ON r.user_id = e.user_id AND r.definition = e.definition
GROUP BY e.definition
ORDER BY e.definition;

-- ============================================================================
-- QUERY 4 — Exam-date-proximity buckets (tests the lifecycle-mismatch hypothesis)
-- ============================================================================
-- Buckets by days-until-exam_date AT SIGNUP TIME, not presence/absence alone.
-- Starting cuts below are a reasonable guess, not measured — sanity-check the
-- bucket sizes against the real exam_date distribution and adjust if e.g.
-- almost everyone lands in one bucket. Uses the same signup-anchored cohort
-- as Query 2. If retention is flat across proximity buckets, that argues
-- AGAINST a pure lifecycle-mismatch story (consistent with the existing
-- 0/41 exam-dated finding — retaining at 0% even BELOW their own exam date).

WITH cohort AS (
    SELECT
        u.id AS user_id,
        u.created_at,
        u.exam_date,
        CASE
            WHEN u.exam_date IS NULL THEN 'no_exam_date'
            WHEN u.exam_date - u.created_at::date < 30 THEN 'under_30d'
            WHEN u.exam_date - u.created_at::date < 90 THEN '30_to_90d'
            ELSE 'over_90d'
        END AS exam_proximity_bucket
    FROM users u
    WHERE u.created_at >= '2026-07-01'::timestamptz  -- same RAMP_START as Query 2
),
eligible AS (
    SELECT user_id, created_at, exam_proximity_bucket
    FROM cohort
    WHERE created_at <= now() - INTERVAL '14 days'
),
returned AS (
    SELECT e.user_id
    FROM eligible e
    WHERE EXISTS (
        SELECT 1 FROM analytics_events ae
        WHERE ae.user_id = e.user_id
          AND ae.created_at > e.created_at + INTERVAL '7 days'
          AND ae.created_at <= e.created_at + INTERVAL '14 days'
    )
)
SELECT
    e.exam_proximity_bucket,
    COUNT(*) AS eligible_signups,
    COUNT(r.user_id) AS returned_in_week2,
    ROUND(100.0 * COUNT(r.user_id) / NULLIF(COUNT(*), 0), 2) AS w1_to_w2_retention_pct
FROM eligible e
LEFT JOIN returned r ON r.user_id = e.user_id
GROUP BY e.exam_proximity_bucket
ORDER BY e.exam_proximity_bucket;

-- ============================================================================
-- QUERY 5 — LET / Education concentration check
-- ============================================================================
-- Confirms whether the surge is actually concentrated on the LET channel (per
-- the reprioritization doc's "LET is currently the strongest acquisition
-- channel" claim) and reads that sub-cohort's retention specifically. Same
-- ILIKE-bucket style as the existing CPALE depth-check precedent
-- (next-priority-new-user-focus-out/02-h1-h5-cohort-recheck-and-cpale-depth.sql
-- Query 2). Ties to the reprioritization doc's "under-investing in LET/Education
-- content depth" finding — thin content on the exact channel bringing in the
-- surge risks starving the whole reuse flywheel.

WITH cohort AS (
    SELECT
        id AS user_id,
        created_at,
        (course_program ILIKE '%LET%' OR course_program ILIKE '%education%'
         OR course_program ILIKE '%teacher%') AS is_let_education
    FROM users
    WHERE created_at >= '2026-07-01'::timestamptz  -- same RAMP_START as Query 2
),
eligible AS (
    SELECT user_id, created_at, is_let_education
    FROM cohort
    WHERE created_at <= now() - INTERVAL '14 days'
),
returned AS (
    SELECT e.user_id
    FROM eligible e
    WHERE EXISTS (
        SELECT 1 FROM analytics_events ae
        WHERE ae.user_id = e.user_id
          AND ae.created_at > e.created_at + INTERVAL '7 days'
          AND ae.created_at <= e.created_at + INTERVAL '14 days'
    )
)
SELECT
    e.is_let_education,
    COUNT(*) AS eligible_signups,
    COUNT(r.user_id) AS returned_in_week2,
    ROUND(100.0 * COUNT(r.user_id) / NULLIF(COUNT(*), 0), 2) AS w1_to_w2_retention_pct
FROM eligible e
LEFT JOIN returned r ON r.user_id = e.user_id
GROUP BY e.is_let_education
ORDER BY e.is_let_education DESC;

-- Also worth a raw count of signups by course_program over the same window, to
-- see the channel mix directly rather than only the LET/non-LET split:
SELECT course_program, COUNT(*) AS signups
FROM users
WHERE created_at >= '2026-07-01'::timestamptz  -- same RAMP_START as Query 2
GROUP BY course_program
ORDER BY signups DESC;

-- ============================================================================
-- QUERY 6 — Crude cost-per-active-user (no token accounting needed — directional
-- only, every non-Study-Pack unit cost below is an explicitly-labeled ASSUMPTION,
-- not a measurement)
-- ============================================================================
-- "Active" = distinct users with >=1 user_activity_events row in the window
-- (NOT users.last_login_at — refresh tokens persist up to 30 days without a
-- fresh login, which would badly undercount real activity).
--
-- Step A: grounded average $/Study-Pack-generation from REAL token counts.
-- VERIFY the per-1K-token rates below against current OpenAI pricing before
-- trusting the output — they are placeholders, not fetched live.

WITH model_pricing AS (
    -- $ per 1K tokens — REPLACE with current published OpenAI pricing for
    -- whatever LLM_MODEL_FREE / LLM_MODEL_PREMIUM actually resolve to in prod
    -- (see application.yaml; defaults gpt-4.1-mini / gpt-4.1 per CLAUDE.md).
    SELECT * FROM (VALUES
        ('gpt-4.1-mini', 0.00040::numeric, 0.00160::numeric),
        ('gpt-4.1',      0.00200::numeric, 0.00800::numeric)
    ) AS t(model_used, price_per_1k_input, price_per_1k_output)
),
study_pack_unit_cost AS (
    SELECT
        AVG(
            (COALESCE(sp.input_tokens, 0) / 1000.0) * mp.price_per_1k_input
            + (COALESCE(sp.output_tokens, 0) / 1000.0) * mp.price_per_1k_output
        ) AS avg_cost_per_study_pack
    FROM study_packs sp
    JOIN model_pricing mp ON mp.model_used = sp.model_used
    WHERE sp.created_at >= '2026-07-01'::timestamptz  -- same RAMP_START as Query 2
),
-- Step B: assumed multipliers relative to one Study Pack generation's grounded
-- cost, for the generation types with NO token grounding at all. These are
-- rough proxies based on relative output size (long/board exam fan out to
-- dozens of questions vs. a Study Pack's fixed quiz) — refine later with real
-- token capture if this number ends up mattering.
weighted_usage AS (
    SELECT
        uu.user_id,
        uu.study_pack_generations * 1.0
            + uu.challenge_quiz_generations * 0.5
            + uu.adaptive_quiz_generations * 0.5
            + uu.interview_practice_used_this_month * 1.0
            + uu.long_exam_used_this_month * 3.0
            + uu.board_exam_used_this_month * 2.0
            + uu.note_generations * 0.5
            AS weighted_generation_units
    FROM user_usage uu
    WHERE uu.period_start <= now()
      AND uu.period_end >= '2026-07-01'::timestamptz  -- same RAMP_START as Query 2
      -- overlap filter, NOT month/year — periods are rolling per-user, see header
),
active_users AS (
    SELECT DISTINCT user_id
    FROM user_activity_events
    WHERE created_at >= '2026-07-01'::timestamptz  -- same RAMP_START as Query 2
)
SELECT
    (SELECT avg_cost_per_study_pack FROM study_pack_unit_cost) AS grounded_cost_per_study_pack,
    COUNT(DISTINCT au.user_id) AS active_users,
    COALESCE(SUM(wu.weighted_generation_units), 0) AS total_weighted_generation_units,
    ROUND(
        (COALESCE(SUM(wu.weighted_generation_units), 0)
            * (SELECT avg_cost_per_study_pack FROM study_pack_unit_cost))
        / NULLIF(COUNT(DISTINCT au.user_id), 0),
        4
    ) AS crude_cost_per_active_user
FROM active_users au
LEFT JOIN weighted_usage wu ON wu.user_id = au.user_id;

-- Read the assumed multipliers (0.5x / 1.0x / 2.0x / 3.0x above) as clearly
-- labeled guesses, not measurements — the point is directional ("is this
-- roughly a cent or roughly a dollar per active user"), not a billing figure.
