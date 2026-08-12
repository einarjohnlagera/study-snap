-- ============================================================================
-- Challenge Quiz adoption -- reads (a) and (b)
-- [CHECKPOINT -- due 2026-09-30], Backlog Index row "Challenge Quiz adoption --
-- the three validation reads deferred in June 2026"
--
-- *** RUN THIS AGAINST PRODUCTION BEFORE v0.74.0 DEPLOYS. IT IS A DEPLOY BLOCKER. ***
--
-- WHY THE DEADLINE MOVED FROM 2026-09-30 TO "BEFORE DEPLOY":
-- v0.74.0 item 4 moves the post-session promotion threshold back from >= 4/5 to
-- mastery (PostSessionNextStepService, STRONG_QUICK_REVIEW_MAX_MISSES). That is the
-- very change this checkpoint measures, so deploying closes the after-window.
-- The owner ruled 2026-08-12 to ship item 4 rather than park it; running these reads
-- pre-deploy is how that cost gets paid instead of silently absorbed.
--
-- Nothing is lost by moving the date up. The Backlog Index already records read (a)
-- as MEASURABLE NOW and states it "does not improve by waiting, it only accumulates
-- denominator" -- the before-window is fixed and closed. Waiting until 2026-09-30
-- would have bought a slightly larger after-window and then destroyed the read anyway.
--
-- WHAT IS BEING TESTED: the load-bearing claim that low Challenge Quiz adoption is a
-- value-is-unclear (MOTIVATION) problem, not a button-PLACEMENT problem. That framing
-- is what ruled out moving or enlarging the entry point, and it has never been tested.
--
-- KILL CRITERION: if Quick Review -> Challenge conversion has NOT improved measurably
-- against the pre-2026-06-16 baseline, the 5/5 -> 4/5 promotion is judged ineffective
-- and "motivation, not placement" reverts to UNCONFIRMED. Reopen the framing rather
-- than shipping further promotion tweaks on top of it.
--
-- DENOMINATOR CLAUSE: if the sample is too small to read, THAT IS ITSELF THE FINDING
-- (the lever is not reachable at current scale) and must be recorded as such. It is
-- not grounds to silently extend the date.
--
-- IF THESE GO UNRUN BEFORE DEPLOY: the kill criterion can never be evaluated, and
-- "motivation, not placement" must be recorded as PERMANENTLY UNCONFIRMED -- not left
-- sitting open as though it were still answerable.
--
-- READ (c) IS NOT HERE, AND THAT IS NOT AN OMISSION. It compares Challenge CTA
-- impressions vs clicks -- the only read that can separate seen-and-ignored from
-- never-reached, and therefore the only one that can actually falsify the framing.
-- It was NOT MEASURABLE because no impression or click event existed for the
-- post-session Challenge CTA. v0.74.0 item 7 ships both, which unblocks it. Read (c)
-- runs AFTER v0.74.0 has been live long enough to accumulate events.
-- DO NOT CLOSE THE CHECKPOINT ROW ON (a) AND (b) ALONE -- they measure whether the
-- fix worked; only (c) tests why.
-- ============================================================================

-- Boundary: becc70ba, 2026-06-16 -- "feat: promote Challenge at strong-majority
-- Quick Review (5/5 -> 4/5)". Adjust only if the production deploy of that commit
-- differs from its commit date; if so, use the DEPLOY date, not the commit date.
--
-- Event availability bounds the before-window and cannot be widened:
--   CHALLENGE_QUIZ_STARTED   added 2026-03-23
--   QUICK_REVIEW_COMPLETED   added 2026-05-05  <-- the binding constraint
-- So "before" is 2026-05-05 .. 2026-06-16 (~6 weeks). Report the window lengths
-- alongside the rates; an uneven comparison that is not disclosed is worse than a
-- short one that is.


-- ---------------------------------------------------------------------------
-- READ (a) -- Quick Review -> Challenge conversion, before vs after.
--
-- Conversion is defined per COMPLETED QUICK REVIEW, not per user: the promotion is
-- shown at the end of a session, so the session is the unit of exposure. A user with
-- ten Quick Reviews had ten chances to be promoted.
-- Attribution window is 24h -- the CTA is immediate, so a conversion days later is
-- more likely re-engagement than promotion response. Query (a2) re-runs at 7d to
-- confirm the direction is not an artifact of that choice.
-- ---------------------------------------------------------------------------
WITH boundary AS (
    SELECT TIMESTAMPTZ '2026-06-16 00:00:00+08' AS changed_at,
           TIMESTAMPTZ '2026-05-05 00:00:00+08' AS events_start
),
qr AS (
    SELECT ae.user_id,
           ae.created_at,
           CASE WHEN ae.created_at < b.changed_at THEN 'before_4of5' ELSE 'after_4of5' END AS cohort
    FROM analytics_events ae
    CROSS JOIN boundary b
    WHERE ae.event_type = 'QUICK_REVIEW_COMPLETED'
      AND ae.created_at >= b.events_start
      AND ae.user_id IS NOT NULL
)
SELECT qr.cohort,
       COUNT(*)                                            AS quick_reviews_completed,
       COUNT(DISTINCT qr.user_id)                          AS distinct_learners,
       COUNT(*) FILTER (WHERE EXISTS (
           SELECT 1
           FROM analytics_events cq
           WHERE cq.event_type = 'CHALLENGE_QUIZ_STARTED'
             AND cq.user_id = qr.user_id
             AND cq.created_at >  qr.created_at
             AND cq.created_at <= qr.created_at + INTERVAL '24 hours'
       ))                                                  AS converted_within_24h,
       ROUND(100.0 * COUNT(*) FILTER (WHERE EXISTS (
           SELECT 1
           FROM analytics_events cq
           WHERE cq.event_type = 'CHALLENGE_QUIZ_STARTED'
             AND cq.user_id = qr.user_id
             AND cq.created_at >  qr.created_at
             AND cq.created_at <= qr.created_at + INTERVAL '24 hours'
       )) / NULLIF(COUNT(*), 0), 1)                        AS conversion_pct,
       MIN(qr.created_at)::date                            AS window_start,
       MAX(qr.created_at)::date                            AS window_end,
       (MAX(qr.created_at)::date - MIN(qr.created_at)::date) AS window_days
FROM qr
GROUP BY qr.cohort
ORDER BY qr.cohort DESC;  -- before_4of5 first


-- ---------------------------------------------------------------------------
-- (a2) -- same read at a 7-day attribution window.
-- If (a) and (a2) point in OPPOSITE directions, neither is a finding: say so and
-- treat the read as inconclusive rather than picking the flattering one.
-- ---------------------------------------------------------------------------
WITH boundary AS (
    SELECT TIMESTAMPTZ '2026-06-16 00:00:00+08' AS changed_at,
           TIMESTAMPTZ '2026-05-05 00:00:00+08' AS events_start
),
qr AS (
    SELECT ae.user_id,
           ae.created_at,
           CASE WHEN ae.created_at < b.changed_at THEN 'before_4of5' ELSE 'after_4of5' END AS cohort
    FROM analytics_events ae
    CROSS JOIN boundary b
    WHERE ae.event_type = 'QUICK_REVIEW_COMPLETED'
      AND ae.created_at >= b.events_start
      AND ae.user_id IS NOT NULL
)
SELECT qr.cohort,
       COUNT(*) AS quick_reviews_completed,
       COUNT(*) FILTER (WHERE EXISTS (
           SELECT 1
           FROM analytics_events cq
           WHERE cq.event_type = 'CHALLENGE_QUIZ_STARTED'
             AND cq.user_id = qr.user_id
             AND cq.created_at >  qr.created_at
             AND cq.created_at <= qr.created_at + INTERVAL '7 days'
       )) AS converted_within_7d,
       ROUND(100.0 * COUNT(*) FILTER (WHERE EXISTS (
           SELECT 1
           FROM analytics_events cq
           WHERE cq.event_type = 'CHALLENGE_QUIZ_STARTED'
             AND cq.user_id = qr.user_id
             AND cq.created_at >  qr.created_at
             AND cq.created_at <= qr.created_at + INTERVAL '7 days'
       )) / NULLIF(COUNT(*), 0), 1) AS conversion_pct_7d
FROM qr
GROUP BY qr.cohort
ORDER BY qr.cohort DESC;


-- ---------------------------------------------------------------------------
-- (a3) -- SANITY CHECK. Run this and read it BEFORE trusting (a).
--
-- The >= 4/5 promotion can only fire for sessions actually scoring >= 4/5, so if the
-- score mix shifted between cohorts, (a) moves for reasons unrelated to the change.
-- This also reports how many completed sessions even REACH the promotion threshold --
-- if that count is tiny, the denominator clause above has already been triggered and
-- (a) cannot be read regardless of what number it returns.
-- ---------------------------------------------------------------------------
SELECT CASE WHEN s.completed_at < TIMESTAMPTZ '2026-06-16 00:00:00+08'
            THEN 'before_4of5' ELSE 'after_4of5' END       AS cohort,
       COUNT(*)                                            AS completed_quick_reviews,
       COUNT(DISTINCT s.user_id)                           AS distinct_learners,
       ROUND(AVG(s.score_percentage), 1)                   AS avg_score_pct,
       COUNT(*) FILTER (WHERE s.total_questions - s.correct_answers <= 1) AS scored_4of5_or_better,
       COUNT(*) FILTER (WHERE s.correct_answers = s.total_questions)      AS scored_perfect,
       ROUND(100.0 * COUNT(*) FILTER (WHERE s.total_questions - s.correct_answers <= 1)
             / NULLIF(COUNT(*), 0), 1)                     AS pct_reaching_promotion
FROM quick_review_sessions s
WHERE s.session_mode = 'QUICK_REVIEW'
  AND s.completed_at IS NOT NULL
  AND s.completed_at >= TIMESTAMPTZ '2026-05-05 00:00:00+08'
GROUP BY 1
ORDER BY 1 DESC;


-- ---------------------------------------------------------------------------
-- READ (b) -- return rate of converted users.
--
-- Does converting to Challenge Quiz actually retain anyone? A conversion lift that
-- retains nobody does not support "motivation, not placement" -- it just means the
-- button moved. The Backlog Index suggested deriving this from PUBLIC_NOTE_COPY_CLICKED
-- plus session data; session data alone is the sounder signal, since copying a public
-- note is a discovery action rather than a return-to-study one.
--
-- "Returned" = any completed quiz session, ANY mode, on a LATER CALENDAR DAY than the
-- conversion. Same-day continuation is one study sitting, not a return.
-- ---------------------------------------------------------------------------
WITH first_challenge AS (
    SELECT ae.user_id,
           MIN(ae.created_at) AS converted_at
    FROM analytics_events ae
    WHERE ae.event_type = 'CHALLENGE_QUIZ_STARTED'
      AND ae.user_id IS NOT NULL
      AND ae.created_at >= TIMESTAMPTZ '2026-05-05 00:00:00+08'
    GROUP BY ae.user_id
)
SELECT CASE WHEN fc.converted_at < TIMESTAMPTZ '2026-06-16 00:00:00+08'
            THEN 'before_4of5' ELSE 'after_4of5' END AS cohort,
       COUNT(*)                                      AS converted_learners,
       COUNT(*) FILTER (WHERE EXISTS (
           SELECT 1
           FROM quick_review_sessions s
           WHERE s.user_id = fc.user_id
             AND s.completed_at IS NOT NULL
             AND s.completed_at::date > fc.converted_at::date
       ))                                            AS returned_on_a_later_day,
       ROUND(100.0 * COUNT(*) FILTER (WHERE EXISTS (
           SELECT 1
           FROM quick_review_sessions s
           WHERE s.user_id = fc.user_id
             AND s.completed_at IS NOT NULL
             AND s.completed_at::date > fc.converted_at::date
       )) / NULLIF(COUNT(*), 0), 1)                  AS return_rate_pct
FROM first_challenge fc
GROUP BY 1
ORDER BY 1 DESC;


-- ---------------------------------------------------------------------------
-- (b2) -- guard against a short-tail illusion in (b).
--
-- Learners who converted recently have had less calendar time to return, which drags
-- the after_4of5 rate down for a reason that has nothing to do with the change.
-- This restricts both cohorts to conversions with at least 14 days of observable
-- follow-up. If (b) and (b2) disagree, TRUST (b2).
-- ---------------------------------------------------------------------------
WITH first_challenge AS (
    SELECT ae.user_id,
           MIN(ae.created_at) AS converted_at
    FROM analytics_events ae
    WHERE ae.event_type = 'CHALLENGE_QUIZ_STARTED'
      AND ae.user_id IS NOT NULL
      AND ae.created_at >= TIMESTAMPTZ '2026-05-05 00:00:00+08'
    GROUP BY ae.user_id
)
SELECT CASE WHEN fc.converted_at < TIMESTAMPTZ '2026-06-16 00:00:00+08'
            THEN 'before_4of5' ELSE 'after_4of5' END AS cohort,
       COUNT(*)                                      AS converted_learners_14d_observable,
       COUNT(*) FILTER (WHERE EXISTS (
           SELECT 1
           FROM quick_review_sessions s
           WHERE s.user_id = fc.user_id
             AND s.completed_at IS NOT NULL
             AND s.completed_at::date >  fc.converted_at::date
             AND s.completed_at      <= fc.converted_at + INTERVAL '14 days'
       ))                                            AS returned_within_14d,
       ROUND(100.0 * COUNT(*) FILTER (WHERE EXISTS (
           SELECT 1
           FROM quick_review_sessions s
           WHERE s.user_id = fc.user_id
             AND s.completed_at IS NOT NULL
             AND s.completed_at::date >  fc.converted_at::date
             AND s.completed_at      <= fc.converted_at + INTERVAL '14 days'
       )) / NULLIF(COUNT(*), 0), 1)                  AS return_rate_pct_14d
FROM first_challenge fc
WHERE fc.converted_at <= NOW() - INTERVAL '14 days'
GROUP BY 1
ORDER BY 1 DESC;


-- ---------------------------------------------------------------------------
-- RECORDING THE RESULT
--
-- Write the numbers into the "Challenge Quiz adoption" Backlog Index row in
-- docs/product/ROADMAP.md, with the date they were run. A read that happens and is
-- not written down is indistinguishable from one that never happened -- which is the
-- exact failure that row was opened to record.
--
-- State plainly which of the three outcomes occurred:
--   1. Conversion measurably improved   -> the promotion worked; "motivation, not
--      placement" survives (a) and (b), still awaiting (c) to be tested properly.
--   2. Conversion did not improve       -> KILL CRITERION MET. "motivation, not
--      placement" reverts to UNCONFIRMED. Reopen the framing.
--   3. Sample too small to read         -> that IS the finding: the lever is not
--      reachable at current scale. Record it; do not extend the date.
-- ---------------------------------------------------------------------------
