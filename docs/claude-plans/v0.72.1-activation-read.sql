-- ============================================================================
-- v0.72.1 ACTIVATION READ — RUN AGAINST PRODUCTION, READ-ONLY
--
-- This is `v0.72.1 — Activation` scope item 1. It answers ONE question:
--
--     Is the binding constraint retention *rate* or activation *volume*?
--
-- WHERE THE RESULTS GO: the `v0.72.1` section of `RELEASES.md`. Record the
-- numbers, not just "the query was run." v0.72.0 wrote that rule for itself
-- after a pre-committed read sat unrun for 13 days and nearly took its
-- reasoning with it. A query cited as a mechanism whose result is never
-- written down is how this project has previously lost a decision.
--
-- INDEX STATUS: at the time of writing this file is UNRUN, so it is covered by
-- the `v0.72.1` Backlog Index row while the release is open. Per the artifact
-- exemption's own clause, if it is still unrun at signoff it stops being a
-- release artifact and needs its own Backlog Index row.
--
-- WHY THIS READ EXISTS. v0.72.0 shipped H1+H5 against W1->W2 retention and
-- recorded, in the same document, the observation that motivates this release:
-- only 185 users have ever generated a first Study Pack and are old enough to
-- measure, and 3 have ever returned in week 2. Moving retention from 2% to 4%
-- against a ~31-user monthly activated cohort is roughly +0.6 returning users
-- per month. That was deliberately NOT used to avoid the H1+H5 commitment then
-- — the rule was written when the answer was unknown. It is the right question
-- to ask now, on evidence.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- FOUR THINGS TO KNOW BEFORE READING ANY RESULT
-- ----------------------------------------------------------------------------
--
-- (1) ONBOARDING STEP EVENTS ARE ONLY TRUSTWORTHY FROM 2026-07-28 ONWARD.
--     `ONBOARDING_V2_ABANDONED` over-fired on nearly every step transition and
--     leaked without a matching `ONBOARDING_V2_STARTED`; the fix landed in
--     91d8d29f on 2026-07-28 (verified: `startedTrackedRef` /
--     `shouldTrackAbandonmentRef` are present in
--     frontend/app/onboarding/page.tsx today). Events before that date are
--     poisoned in BOTH directions, so `STARTED` also under-counts its own
--     population. Query 3b is therefore restricted to >= 2026-07-28 and will
--     have a small n. Every funnel stage in Queries 1, 2 and 3a is keyed on
--     `users` table state instead, which is durable and unaffected.
--
-- (2) `onboarding_completed_at IS NOT NULL` HAS KNOWN FALSE POSITIVES.
--     AuthService.java:481 documents accounts where it "was already set — the
--     user was never routed back to fix it," and the 2026-08-06 vocabulary
--     audit measured 5 completed with no course program and 4 with no learner
--     level. Against a ~364-account population that is not noise. Query 1
--     surfaces these as their own column rather than folding them into the
--     success bucket. Read `onboarding_completed` as an upper bound.
--
-- (3) `product_onboarding_completed_at` IS DELIBERATELY IGNORED.
--     It has no service-layer or controller reader — it appears only in
--     AuthResponse/MeResponse DTOs. `OnboardingGuardService` gates on
--     `onboarding_completed_at`, which is the signup-flow field this read
--     cares about.
--
-- (4) ACTIVATION IS DEFINED IDENTICALLY TO THE READ THIS RELEASE BUILDS ON.
--     `MIN(created_at)` over `event_type = 'STUDY_PACK_GENERATED'`, exactly as
--     in next-priority-new-user-focus-out/02-h1-h5-cohort-recheck-and-cpale-
--     depth.sql. Do not "improve" this definition here — if it drifts, the new
--     numbers stop reconciling with the 185 / 3 / +0.6-per-month figures that
--     are this release's entire premise, and the release ends up carrying two
--     incompatible definitions of the same word.


-- ============================================================================
-- QUERY 1 — THE PRIMARY READ. Signup -> activation funnel.
--
-- Two windows: all time (the whole population) and trailing 90 days (recent
-- behaviour). These are the numbers to quote. Query 2's monthly rows are for
-- trend only — at ~31 activated users a month, any single month is
-- underpowered and should not be quoted on its own.
--
-- HOW TO READ IT: the biggest absolute drop between two adjacent stages is the
-- volume answer. If that drop is small, or the stages are already tight, then
-- volume is not where the loss is and the constraint is rate after all.
-- ============================================================================

WITH first_pack AS (
    SELECT user_id, MIN(created_at) AS first_pack_at
    FROM analytics_events
    WHERE event_type = 'STUDY_PACK_GENERATED'
      AND user_id IS NOT NULL
    GROUP BY user_id
),
scoped AS (
    SELECT
        u.id,
        u.created_at,
        u.email_verified_at,
        u.onboarding_completed_at,
        u.profile_type,
        u.course_program,
        u.learner_level,
        fp.first_pack_at
    FROM users u
    LEFT JOIN first_pack fp ON fp.user_id = u.id
),
windows AS (
    SELECT 'all time'::text AS label, '-infinity'::timestamptz AS since, 1 AS ord
    UNION ALL
    SELECT 'trailing 90 days', now() - INTERVAL '90 days', 2
)
SELECT
    w.label                                                                    AS window,
    COUNT(*)                                                                   AS signups,
    COUNT(*) FILTER (WHERE s.email_verified_at IS NOT NULL)                    AS email_verified,
    COUNT(*) FILTER (WHERE s.profile_type IS NOT NULL)                         AS has_profile_type,
    COUNT(*) FILTER (WHERE s.onboarding_completed_at IS NOT NULL)              AS onboarding_completed,
    -- (2) above: completions that did not actually collect what onboarding collects
    COUNT(*) FILTER (
        WHERE s.onboarding_completed_at IS NOT NULL
          AND (s.profile_type IS NULL OR s.course_program IS NULL OR s.learner_level IS NULL)
    )                                                                          AS completed_but_missing_context,
    COUNT(*) FILTER (WHERE s.first_pack_at IS NOT NULL)                        AS activated_first_pack,
    -- conversion rates, each against signups
    ROUND(100.0 * COUNT(*) FILTER (WHERE s.email_verified_at IS NOT NULL)
          / NULLIF(COUNT(*), 0), 2)                                            AS pct_verified,
    ROUND(100.0 * COUNT(*) FILTER (WHERE s.onboarding_completed_at IS NOT NULL)
          / NULLIF(COUNT(*), 0), 2)                                            AS pct_onboarded,
    ROUND(100.0 * COUNT(*) FILTER (WHERE s.first_pack_at IS NOT NULL)
          / NULLIF(COUNT(*), 0), 2)                                            AS pct_activated,
    -- the conditional step: of those who finished onboarding, how many activated?
    -- NOTE: the numerator MUST be conditioned on onboarding too. The first run of
    -- this file (2026-08-11) divided ALL activated users by onboarded users and
    -- reported 83.33%, which is wrong — activation does not require completing
    -- onboarding, so the numerator included users absent from the denominator.
    -- Corrected here; the true first-run value is 73.1% (171/234).
    ROUND(100.0 * COUNT(*) FILTER (
              WHERE s.first_pack_at IS NOT NULL
                AND s.onboarding_completed_at IS NOT NULL
          )
          / NULLIF(COUNT(*) FILTER (WHERE s.onboarding_completed_at IS NOT NULL), 0), 2)
                                                                               AS pct_activated_of_onboarded
FROM windows w
JOIN scoped s ON s.created_at >= w.since
GROUP BY w.label, w.ord
ORDER BY w.ord;


-- ============================================================================
-- QUERY 2 — TREND ONLY. Same funnel by signup month.
--
-- DO NOT QUOTE A SINGLE MONTH. Each row is a small-n slice of Query 1; the
-- point is direction, and whether the 2026-07-24 surge distorts the all-time
-- figure. `activated_first_pack` for the most recent month or two is also
-- structurally incomplete — those users have not had their full chance to
-- activate yet.
-- ============================================================================

WITH first_pack AS (
    SELECT user_id, MIN(created_at) AS first_pack_at
    FROM analytics_events
    WHERE event_type = 'STUDY_PACK_GENERATED'
      AND user_id IS NOT NULL
    GROUP BY user_id
)
SELECT
    date_trunc('month', u.created_at)::date                                    AS signup_month,
    COUNT(*)                                                                   AS signups,
    COUNT(*) FILTER (WHERE u.email_verified_at IS NOT NULL)                    AS email_verified,
    COUNT(*) FILTER (WHERE u.onboarding_completed_at IS NOT NULL)              AS onboarding_completed,
    COUNT(*) FILTER (WHERE fp.first_pack_at IS NOT NULL)                       AS activated_first_pack,
    ROUND(100.0 * COUNT(*) FILTER (WHERE u.onboarding_completed_at IS NOT NULL)
          / NULLIF(COUNT(*), 0), 2)                                            AS pct_onboarded,
    ROUND(100.0 * COUNT(*) FILTER (WHERE fp.first_pack_at IS NOT NULL)
          / NULLIF(COUNT(*), 0), 2)                                            AS pct_activated,
    -- the two most recent months cannot be complete: a user who signed up
    -- recently has not yet had their full chance to activate
    (date_trunc('month', u.created_at) >= date_trunc('month', now() - INTERVAL '1 month'))
                                                                               AS incomplete_window
FROM users u
LEFT JOIN first_pack fp ON fp.user_id = u.id
GROUP BY date_trunc('month', u.created_at)
ORDER BY date_trunc('month', u.created_at);


-- ============================================================================
-- QUERY 3a — WHERE DO THE NON-ACTIVATED STOP? (durable state, not events)
--
-- Single furthest-stage attribution per user, so the buckets sum to the whole
-- population and nobody is counted twice. Immune to caveat (1): every stage is
-- read from `users` / `notes` state rather than from onboarding events.
--
-- This is the query that names the intervention. A pile-up at stage 2 or 3 is
-- an onboarding-flow problem — which is what the Onboarding Intent Router
-- residuals (C8/C9 + M13/M15/M16) already propose to fix. A pile-up at stage 4
-- or 5 is a different problem and would mean the residuals are the WRONG build
-- half for this release; say so rather than shipping them anyway.
-- ============================================================================

WITH first_pack AS (
    SELECT user_id, MIN(created_at) AS first_pack_at
    FROM analytics_events
    WHERE event_type = 'STUDY_PACK_GENERATED'
      AND user_id IS NOT NULL
    GROUP BY user_id
),
staged AS (
    SELECT
        u.id,
        CASE
            WHEN u.email_verified_at IS NULL
                THEN '1. signed up, never verified email'
            WHEN u.profile_type IS NULL AND u.onboarding_completed_at IS NULL
                THEN '2. verified, never reached a profile type'
            WHEN u.onboarding_completed_at IS NULL
                THEN '3. picked a profile type, never completed onboarding'
            WHEN NOT EXISTS (SELECT 1 FROM notes n WHERE n.owner_user_id = u.id)
                THEN '4. completed onboarding, never created a note'
            WHEN fp.first_pack_at IS NULL
                THEN '5. created a note, never generated a Study Pack'
            ELSE '6. ACTIVATED (generated a Study Pack)'
        END AS furthest_stage
    FROM users u
    LEFT JOIN first_pack fp ON fp.user_id = u.id
)
SELECT
    furthest_stage,
    COUNT(*)                                                       AS users,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2)             AS pct_of_all_signups
FROM staged
GROUP BY furthest_stage
ORDER BY furthest_stage;


-- ============================================================================
-- QUERY 3b — SECONDARY, SMALL-n. Step-level drop from onboarding events.
--
-- RESTRICTED TO >= 2026-07-28 per caveat (1). Before that date the event
-- stream cannot support this question at all. Expect a small denominator: this
-- is roughly two weeks of clean data as of 2026-08-11.
--
-- Treat this as colour on Query 3a, never as a substitute for it. If the two
-- disagree, 3a wins — it reads durable state.
-- ============================================================================

WITH clean_window AS (
    SELECT ae.user_id, ae.event_type, ae.created_at
    FROM analytics_events ae
    WHERE ae.created_at >= '2026-07-28'::timestamptz
      AND ae.user_id IS NOT NULL
      AND ae.event_type LIKE 'ONBOARDING_V2_%'
),
last_step AS (
    SELECT DISTINCT ON (cw.user_id)
        cw.user_id,
        cw.event_type AS furthest_event,
        cw.created_at
    FROM clean_window cw
    WHERE cw.event_type <> 'ONBOARDING_V2_ABANDONED'   -- an outcome, not a step
    ORDER BY cw.user_id, cw.created_at DESC
)
SELECT
    ls.furthest_event,
    COUNT(*)                                                                   AS users,
    COUNT(*) FILTER (WHERE u.onboarding_completed_at IS NOT NULL)              AS later_completed,
    COUNT(*) FILTER (WHERE u.onboarding_completed_at IS NULL)                  AS stalled_here
FROM last_step ls
JOIN users u ON u.id = ls.user_id
GROUP BY ls.furthest_event
ORDER BY stalled_here DESC, users DESC;


-- ============================================================================
-- QUERY 4 — THE DECISIVE ONE. Rate vs. volume, in returning users per month.
--
-- Puts both levers in the same unit so they can actually be compared, which
-- neither the 2.4% retention figure nor the onboarding percentages do on their
-- own.
--
-- ****  THE ASSUMPTION THIS QUERY CANNOT TEST, STATED PLAINLY  ****
-- The volume branch assumes recovered users retain at the SAME W1->W2 rate as
-- users who activate today. That is almost certainly false and optimistic:
-- people who stall during onboarding are lower-intent by selection, not
-- randomly blocked. SQL cannot settle this. That is why the output carries a
-- sensitivity row at HALF the current rate — read the pair as a range, and do
-- not quote the optimistic row alone. If the two branches only separate under
-- the optimistic assumption, the honest reading is "this read did not settle
-- it," which is a legitimate outcome and must be recorded as such.
--
-- Also note both branches are counterfactual ceilings: they assume a perfect
-- fix that recovers the entire gap. Real interventions recover a fraction.
-- ============================================================================

WITH first_pack AS (
    SELECT user_id, MIN(created_at) AS first_pack_at
    FROM analytics_events
    WHERE event_type = 'STUDY_PACK_GENERATED'
      AND user_id IS NOT NULL
    GROUP BY user_id
),
-- current W1->W2 retention, same definition as the v0.72.0 read
eligible AS (
    SELECT fp.user_id, fp.first_pack_at
    FROM first_pack fp
    WHERE fp.first_pack_at <= now() - INTERVAL '14 days'
),
returned AS (
    SELECT e.user_id
    FROM eligible e
    WHERE EXISTS (
        SELECT 1
        FROM analytics_events ae
        WHERE ae.user_id = e.user_id
          AND ae.created_at >  e.first_pack_at + INTERVAL '7 days'
          AND ae.created_at <= e.first_pack_at + INTERVAL '14 days'
    )
),
retention AS (
    SELECT
        COUNT(*)                                                   AS eligible_users,
        COUNT(r.user_id)                                           AS returned_users,
        COALESCE(COUNT(r.user_id)::numeric / NULLIF(COUNT(*), 0), 0) AS w2_rate
    FROM eligible e
    LEFT JOIN returned r ON r.user_id = e.user_id
),
-- current monthly throughput, measured over the trailing 90 days
throughput AS (
    SELECT
        COUNT(*) FILTER (WHERE u.created_at >= now() - INTERVAL '90 days')::numeric / 3.0
                                                                   AS signups_per_month,
        COUNT(*) FILTER (
            WHERE u.created_at >= now() - INTERVAL '90 days'
              AND fp.first_pack_at IS NOT NULL
        )::numeric / 3.0                                           AS activated_per_month,
        COUNT(*) FILTER (
            WHERE u.created_at >= now() - INTERVAL '90 days'
              AND u.onboarding_completed_at IS NOT NULL
        )::numeric / 3.0                                           AS onboarded_per_month
    FROM users u
    LEFT JOIN first_pack fp ON fp.user_id = u.id
),
scenarios AS (
    SELECT
        'A. baseline (today)'                                      AS scenario,
        t.activated_per_month                                      AS activated_per_month,
        r.w2_rate                                                  AS assumed_w2_rate,
        t.activated_per_month * r.w2_rate                           AS returning_users_per_month,
        r.eligible_users                                           AS eligible_users,
        r.returned_users                                           AS returned_users
    FROM throughput t CROSS JOIN retention r

    UNION ALL
    SELECT
        'B. RATE lever: W1->W2 doubles, volume unchanged',
        t.activated_per_month,
        r.w2_rate * 2,
        t.activated_per_month * r.w2_rate * 2,
        r.eligible_users,
        r.returned_users
    FROM throughput t CROSS JOIN retention r

    UNION ALL
    SELECT
        'C. VOLUME lever: every signup activates, rate unchanged (optimistic)',
        t.signups_per_month,
        r.w2_rate,
        t.signups_per_month * r.w2_rate,
        r.eligible_users,
        r.returned_users
    FROM throughput t CROSS JOIN retention r

    UNION ALL
    SELECT
        'D. VOLUME lever, sensitivity: recovered users retain at HALF the rate',
        t.signups_per_month,
        -- BLENDED effective rate, not the headline rate: today's cohort retains at
        -- w2_rate, the recovered cohort at half of it. Reported blended so that
        -- `returning = activated x assumed_rate` holds on every row of this table.
        ((t.activated_per_month * r.w2_rate)
            + ((t.signups_per_month - t.activated_per_month) * r.w2_rate * 0.5))
            / NULLIF(t.signups_per_month, 0),
        (t.activated_per_month * r.w2_rate)
            + ((t.signups_per_month - t.activated_per_month) * r.w2_rate * 0.5),
        r.eligible_users,
        r.returned_users
    FROM throughput t CROSS JOIN retention r
)
SELECT
    s.scenario,
    ROUND(s.activated_per_month, 1)                                AS activated_users_per_month,
    ROUND(100.0 * s.assumed_w2_rate, 2)                            AS assumed_w2_rate_pct,
    ROUND(s.returning_users_per_month, 2)                          AS returning_users_per_month,
    -- the numerator and denominator the whole table rests on. Always report
    -- these alongside the scenarios: a rate built on a handful of return
    -- events cannot carry an absolute forecast, even though the COMPARISON
    -- between scenarios is rate-independent (see the note below).
    s.eligible_users                                               AS w2_eligible_users,
    s.returned_users                                               AS w2_returned_users
FROM scenarios s
ORDER BY s.scenario;


-- ============================================================================
-- HOW TO DECIDE, WRITTEN BEFORE THE RESULT IS KNOWN
--
-- Stated up front on purpose — the same discipline as the pre-committed rule
-- that produced v0.72.0, and for the same reason: so the answer cannot be
-- fitted to whichever build was already appealing.
--
--   * If Query 3a shows the largest drop at stages 2-3 AND Query 4's volume
--     branch beats the rate branch EVEN AT the half-rate sensitivity (row D
--     above row B), the constraint is volume. Ship the Onboarding Intent
--     Router residuals as this release's build half.
--
--   * If the branches only separate under the optimistic row C, this read did
--     NOT settle it. Record that outcome plainly. Do not resolve it by
--     preferring the build already scoped.
--
--   * If the rate branch wins, or the funnel is already tight and the loss is
--     downstream of activation, then v0.72.1 rescopes — per its own recorded
--     rule, and per v0.72.0's precedent that an honestly-blocked release is an
--     acceptable outcome. Any fallback scope must be VERIFIED UNBUILT IN CODE
--     before being recorded: v0.72.0 named the CPALE Exam Hub as its fallback
--     and it had already shipped as v0.54.0.
--
--   * Denominator clause, applying to every branch: if the eligible cohort in
--     Query 4 is still ~31 with ~0-3 returns, the W2 rate underlying all four
--     scenarios is itself underpowered — P(zero | no change) was 54.3% at that
--     n. Say "not yet measurable" rather than reporting a scenario table built
--     on a rate the data cannot support.
-- ============================================================================


-- ============================================================================
-- QUERY 5 — THE RESCOPE READ. Is 2.4% a retention fact, or a window artifact?
--
-- ADDED 2026-08-11, AFTER Queries 1-4 were run and the verdict came back
-- "rescope". This is NOT part of the activation read and must not be used to
-- revisit its verdict — that verdict fired on its own pre-committed rule and
-- stands. This is the recommended rescope target, written because of what the
-- numbers turned out to be.
--
-- WHY. The W1->W2 metric this project has deferred to for months is a BOOLEAN
-- OVER ONE NARROW WINDOW: did the user emit any event in days 7-14 after their
-- first Study Pack? A learner who returned on day 3, on day 5, or on day 20
-- scores as churned. The first run measured 1.62% — 3 return events. Before
-- another release is spent moving that number, it is worth one read-only query
-- to find out whether it is measuring churn or measuring the window.
--
-- HOW TO READ IT:
--   * If the wider windows are also near-zero, the constraint is REAL and has
--     now been hardened rather than assumed. That is a genuinely useful
--     outcome — the number stops being an inherited assumption.
--   * If the wider windows are several times higher, then every roadmap
--     decision that deferred to "2.4% retention" was reading a narrow-window
--     artifact, and the target-habit definition (see the Backlog Index row of
--     that name, which already retired the blended W1->W2 boolean as the
--     universal yardstick) needs to be applied before any further retention
--     work is scoped.
--
-- NOTE ON THE SOURCE SIGNAL. `users.last_login_at` is deliberately NOT used as
-- a cross-check. LOGIN fires only on explicit login (AuthService.java:844) and
-- that same path sets `last_login_at`, so a `keepSignedIn` refresh-token return
-- updates neither — it shares the exact blind spot it would be checking. The
-- variable worth varying is the WINDOW, not the event source.
--
-- All windows below share ONE eligible set (first pack at least 30 days ago)
-- so the columns are directly comparable to each other.
-- ============================================================================

WITH first_pack AS (
    SELECT user_id, MIN(created_at) AS first_pack_at
    FROM analytics_events
    WHERE event_type = 'STUDY_PACK_GENERATED'
      AND user_id IS NOT NULL
    GROUP BY user_id
),
eligible AS (
    SELECT fp.user_id, fp.first_pack_at
    FROM first_pack fp
    WHERE fp.first_pack_at <= now() - INTERVAL '30 days'
),
flags AS (
    SELECT
        e.user_id,
        -- the current metric, exactly as defined today
        EXISTS (
            SELECT 1 FROM analytics_events ae
            WHERE ae.user_id = e.user_id
              AND ae.created_at >  e.first_pack_at + INTERVAL '7 days'
              AND ae.created_at <= e.first_pack_at + INTERVAL '14 days'
        ) AS w2_strict_days_7_14,
        -- same start, no upper bound: did they EVER come back after week 1?
        EXISTS (
            SELECT 1 FROM analytics_events ae
            WHERE ae.user_id = e.user_id
              AND ae.created_at >  e.first_pack_at + INTERVAL '7 days'
        ) AS returned_after_day_7_unbounded,
        -- a wider, more conventional early-retention window
        EXISTS (
            SELECT 1 FROM analytics_events ae
            WHERE ae.user_id = e.user_id
              AND ae.created_at >  e.first_pack_at + INTERVAL '1 day'
              AND ae.created_at <= e.first_pack_at + INTERVAL '30 days'
        ) AS returned_days_2_30,
        -- the loosest possible reading: came back on any later day at all
        EXISTS (
            SELECT 1 FROM analytics_events ae
            WHERE ae.user_id = e.user_id
              AND ae.created_at >  e.first_pack_at + INTERVAL '1 day'
        ) AS returned_after_day_1_unbounded
    FROM eligible e
)
SELECT
    COUNT(*)                                                                   AS eligible_users,
    COUNT(*) FILTER (WHERE w2_strict_days_7_14)                                AS returned_w2_strict,
    ROUND(100.0 * COUNT(*) FILTER (WHERE w2_strict_days_7_14)
          / NULLIF(COUNT(*), 0), 2)                                            AS pct_w2_strict,
    COUNT(*) FILTER (WHERE returned_after_day_7_unbounded)                     AS returned_after_d7,
    ROUND(100.0 * COUNT(*) FILTER (WHERE returned_after_day_7_unbounded)
          / NULLIF(COUNT(*), 0), 2)                                            AS pct_after_d7,
    COUNT(*) FILTER (WHERE returned_days_2_30)                                 AS returned_d2_30,
    ROUND(100.0 * COUNT(*) FILTER (WHERE returned_days_2_30)
          / NULLIF(COUNT(*), 0), 2)                                            AS pct_d2_30,
    COUNT(*) FILTER (WHERE returned_after_day_1_unbounded)                     AS returned_after_d1,
    ROUND(100.0 * COUNT(*) FILTER (WHERE returned_after_day_1_unbounded)
          / NULLIF(COUNT(*), 0), 2)                                            AS pct_after_d1
FROM flags;
