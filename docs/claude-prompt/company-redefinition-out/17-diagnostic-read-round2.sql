-- Diagnostic Read — ROUND 2. Run against production Postgres, read-only.
-- Companion to 08-diagnostic-read-methodology.md and 08-diagnostic-read-queries.sql (Round 1).
-- Written 2026-08-07. Round 1 ran 2026-07-24/25 and was inconclusive BY CONSTRUCTION: the surge
-- cohort's 14-day W2 window had not closed yet.
--
-- ============================================================================
-- WHY THIS IS TIME-CRITICAL, AND WHAT BREAKS IF IT IS RUN LATE
-- ============================================================================
-- Query 4 is the reason this cannot wait. It compares create-first against practice-first
-- retention ON THE SAME COVERED TRACKS -- i.e. only programs where a qualifying Official Review
-- Set actually existed, so both paths were genuinely available and the comparison is fair.
--
-- v0.71.0 slice 5 (the Onboarding Intent Router) OPENS practice-first to every profile type,
-- where today it is gated to BOARD_EXAM. The moment that deploys, "users who could have adopted
-- but authored instead" stops being a coherent group, because the choice itself changes shape.
-- The comparison group does not degrade gradually -- it ceases to exist.
--
-- So: run Query 4 BEFORE v0.71.0 reaches production, or accept that this comparison is
-- permanently unavailable and record that decision in ROADMAP.md. There is no later.
--
-- Queries 1-3 are not time-critical and can be re-run any time.
--
-- ============================================================================
-- WHAT CHANGED SINCE ROUND 1 — read before interpreting anything
-- ============================================================================
-- 1. The 14-day windows have closed for the surge cohort, so the eligible denominator is real
--    now rather than "not yet measurable".
-- 2. The target-habit definition was RATIFIED 2026-07-28 (ROADMAP.md, "Target-habit definition")
--    and it retires the single blended W1->W2 boolean as the universal yardstick. Round 2 must
--    re-cut by segment rather than report one percentage -- that is Query 3, and it is the whole
--    point of re-running rather than repeating Round 1 verbatim.
-- 3. Segment by whether users.exam_date IS SET -- explicitly NOT by profile_type, which is a
--    coarser proxy for the same thing. Some non-BOARD_EXAM accounts legitimately set a real
--    exam date, and v0.71.0 stopped completeOnboarding from nulling those.
--
-- ============================================================================
-- THE SMALL-N TRAP — the same one Round 1 caught itself in
-- ============================================================================
-- The scored exam-bound group (exam date already PASSED) is likely to be very small: recent
-- signups, PRC-clustered exam dates. A near-single-digit denominator means "not yet
-- measurable", NOT a verdict. Every query below reports its denominator alongside its
-- percentage for exactly this reason. If a denominator is under ~20, report the raw counts and
-- say the percentage is not yet meaningful -- do not let a 1-of-3 become "33% retention".
--
-- Local dev is NOT a substitute for any of this. The v0.71.0 course/program query measured 60%
-- locally against 1.17% in production -- an inversion, not a rounding difference.

-- ============================================================================
-- QUERY 1 — Eligibility: is there enough closed-window data to read at all?
-- Run this FIRST. If eligible_signups is tiny, stop and re-read later.
-- ============================================================================
SELECT
    COUNT(*)                                                             AS signups_since_ramp,
    COUNT(*) FILTER (WHERE created_at <= now() - INTERVAL '14 days')     AS eligible_signups,
    COUNT(*) FILTER (WHERE created_at >  now() - INTERVAL '14 days')     AS still_in_flight,
    MIN(created_at)                                                      AS earliest_signup,
    MAX(created_at)                                                      AS latest_signup
FROM users
WHERE created_at >= '2026-07-01'::timestamptz;   -- RAMP_START, matching Round 1

-- ============================================================================
-- QUERY 2 — The blended W1->W2 read, UNCHANGED from Round 1.
-- Kept verbatim for comparability with the 2.4%/127 historical figure. It is no longer the
-- headline number -- Query 3 is -- but changing its definition and its window at the same time
-- would make the movement uninterpretable.
-- ============================================================================
WITH cohort AS (
    SELECT id AS user_id, created_at
    FROM users
    WHERE created_at >= '2026-07-01'::timestamptz
),
eligible AS (
    SELECT user_id, created_at FROM cohort
    WHERE created_at <= now() - INTERVAL '14 days'
),
returned_any_event AS (
    SELECT e.user_id FROM eligible e
    WHERE EXISTS (
        SELECT 1 FROM analytics_events ae
        WHERE ae.user_id = e.user_id
          AND ae.created_at >  e.created_at + INTERVAL '7 days'
          AND ae.created_at <= e.created_at + INTERVAL '14 days'
    )
),
returned_meaningful_study AS (
    SELECT e.user_id FROM eligible e
    WHERE EXISTS (
        SELECT 1 FROM user_activity_events uae
        WHERE uae.user_id = e.user_id
          AND uae.activity_type <> 'OPENED_STUDY_PACK'
          AND uae.created_at >  e.created_at + INTERVAL '7 days'
          AND uae.created_at <= e.created_at + INTERVAL '14 days'
    )
)
SELECT
    COUNT(*)                                                    AS eligible_signups,
    (SELECT COUNT(*) FROM returned_any_event)                   AS returned_any_event,
    (SELECT COUNT(*) FROM returned_meaningful_study)            AS returned_meaningful_study,
    ROUND(100.0 * (SELECT COUNT(*) FROM returned_any_event) / NULLIF(COUNT(*), 0), 2)
        AS w1_to_w2_pct_any_event,
    ROUND(100.0 * (SELECT COUNT(*) FROM returned_meaningful_study) / NULLIF(COUNT(*), 0), 2)
        AS w1_to_w2_pct_meaningful_study
FROM eligible;

-- ============================================================================
-- QUERY 3 — THE HEADLINE. The ratified target-habit segmentation.
--
-- Exam-bound learners (exam_date set): the natural arc is signup -> sustained practice -> sit
-- the exam -> legitimately stop. Going quiet AFTER your own exam date is completion, not churn.
-- So the scored question is: in the 7 days immediately BEFORE their exam date, did they study?
-- Users whose exam date has not arrived are `in_flight` and are excluded from the scored
-- denominator entirely -- neither success nor failure.
--
-- Open-ended learners (no exam date): no terminal event exists, so the windowed W1->W2 frame
-- remains reasonable here specifically.
-- ============================================================================
WITH exam_bound AS (
    SELECT
        u.id AS user_id,
        u.exam_date,
        (u.exam_date <  CURRENT_DATE) AS scoreable,
        EXISTS (
            SELECT 1 FROM user_activity_events uae
            WHERE uae.user_id = u.id
              AND uae.activity_type <> 'OPENED_STUDY_PACK'
              AND uae.created_at >= (u.exam_date - INTERVAL '7 days')
              AND uae.created_at <  (u.exam_date + INTERVAL '1 day')
        ) AS studied_in_final_week
    FROM users u
    WHERE u.exam_date IS NOT NULL
      AND u.created_at >= '2026-07-01'::timestamptz
),
open_ended AS (
    SELECT
        u.id AS user_id,
        (u.created_at <= now() - INTERVAL '14 days') AS scoreable,
        EXISTS (
            SELECT 1 FROM user_activity_events uae
            WHERE uae.user_id = u.id
              AND uae.activity_type <> 'OPENED_STUDY_PACK'
              AND uae.created_at >  u.created_at + INTERVAL '7 days'
              AND uae.created_at <= u.created_at + INTERVAL '14 days'
        ) AS returned_w2
    FROM users u
    WHERE u.exam_date IS NULL
      AND u.created_at >= '2026-07-01'::timestamptz
)
SELECT
    'exam_bound (scored: exam date already passed)'                       AS segment,
    COUNT(*) FILTER (WHERE scoreable)                                     AS scored_denominator,
    COUNT(*) FILTER (WHERE NOT scoreable)                                 AS in_flight_excluded,
    COUNT(*) FILTER (WHERE scoreable AND studied_in_final_week)           AS succeeded,
    ROUND(100.0 * COUNT(*) FILTER (WHERE scoreable AND studied_in_final_week)
          / NULLIF(COUNT(*) FILTER (WHERE scoreable), 0), 2)              AS pct
FROM exam_bound
UNION ALL
SELECT
    'open_ended (W1->W2 meaningful study)',
    COUNT(*) FILTER (WHERE scoreable),
    COUNT(*) FILTER (WHERE NOT scoreable),
    COUNT(*) FILTER (WHERE scoreable AND returned_w2),
    ROUND(100.0 * COUNT(*) FILTER (WHERE scoreable AND returned_w2)
          / NULLIF(COUNT(*) FILTER (WHERE scoreable), 0), 2)
FROM open_ended;

-- ============================================================================
-- QUERY 4 — TIME-CRITICAL. Create-first vs practice-first, on covered tracks only.
--
-- THIS IS THE ONE THAT EXPIRES. Run it before v0.71.0 deploys.
--
-- Restricted to programs where a qualifying Official Review Set existed, so both paths were
-- genuinely available to everyone counted. Comparing across uncovered programs would score
-- practice-first against users who were never offered it.
--
-- Path is derived from analytics rather than guessed: a practice-first adopter fires
-- ONBOARDING_V2_PRACTICE_FIRST_PLAN_ADOPTED. Everyone else who completed onboarding took the
-- create-first path. Note the header warning in Round 1's file: practice-first adopters get a
-- COPIED study pack and never fire STUDY_PACK_GENERATED, so anchoring on that event would make
-- them invisible rather than merely ineligible.
--
-- EDIT THIS LIST if the set of covered programs has changed. As of the 2026-08-06 production
-- audit the four published Official Review Sets were tagged with exact catalog names.
-- ============================================================================
WITH covered AS (
    SELECT unnest(ARRAY['Accountancy', 'Architecture', 'Education', 'Nursing']) AS course_program
),
cohort AS (
    SELECT
        u.id AS user_id,
        u.created_at,
        u.course_program,
        EXISTS (
            SELECT 1 FROM analytics_events ae
            WHERE ae.user_id = u.id
              AND ae.event_type = 'ONBOARDING_V2_PRACTICE_FIRST_PLAN_ADOPTED'
        ) AS took_practice_first,
        EXISTS (
            SELECT 1 FROM analytics_events ae
            WHERE ae.user_id = u.id
              AND ae.event_type = 'ONBOARDING_V2_COMPLETED'
        ) AS completed_onboarding
    FROM users u
    JOIN covered c ON c.course_program = u.course_program
    WHERE u.created_at >= '2026-07-01'::timestamptz
      AND u.created_at <= now() - INTERVAL '14 days'   -- window must have closed
),
scored AS (
    SELECT
        user_id,
        CASE WHEN took_practice_first THEN 'practice_first' ELSE 'create_first' END AS path,
        EXISTS (
            SELECT 1 FROM user_activity_events uae
            WHERE uae.user_id = cohort.user_id
              AND uae.activity_type <> 'OPENED_STUDY_PACK'
              AND uae.created_at >  cohort.created_at + INTERVAL '7 days'
              AND uae.created_at <= cohort.created_at + INTERVAL '14 days'
        ) AS returned_w2
    FROM cohort
    WHERE completed_onboarding      -- only users who actually finished onboarding are comparable
)
SELECT
    path,
    COUNT(*)                                                          AS scored_denominator,
    COUNT(*) FILTER (WHERE returned_w2)                               AS returned_w2,
    ROUND(100.0 * COUNT(*) FILTER (WHERE returned_w2) / NULLIF(COUNT(*), 0), 2) AS pct
FROM scored
GROUP BY path
ORDER BY path;

-- ============================================================================
-- HOW TO READ THE RESULT, AND WHAT NOT TO CONCLUDE
-- ============================================================================
-- Query 1 denominator small          -> stop. "Not yet measurable" is the finding. Re-read later.
--
-- Query 3, exam_bound denominator <20 -> report raw counts, not a percentage. A 1-of-3 is not
--                                       "33% retention", and the ratified definition says so.
--
-- Query 3 vs the historical 0/41      -> the reframe does NOT excuse that finding. 0/41 measured
--                                       disengagement BEFORE the goal, which is a real problem
--                                       under either frame. The reframe only stops penalising
--                                       expected disengagement AFTER the exam date.
--
-- Query 4 with either arm under ~20   -> directional at best. Say so explicitly. Do not let a
--                                       small-n difference justify a roadmap decision; the whole
--                                       reason for pulling it now is that it becomes unavailable
--                                       later, not that it is decisive today.
--
-- Query 4 unrunnable or empty         -> record in ROADMAP.md that the comparison window closed
--                                       unmeasured. That is a legitimate outcome; silently
--                                       dropping it is not.
--
-- Record the results in a dated file next to this one, the way
-- 25-query-a-production-results.md did for the v0.71.0 catalog read, and update the ROADMAP's
-- Phase 1 section in the same commit -- that section's own rule requires it.
-- ============================================================================
