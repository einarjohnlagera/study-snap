-- Two verification queries flowing from the next-priority-new-user-focus session (2026-07-21).
-- Run both against production, read-only. Neither should trigger a build on its own —
-- see docs/claude-prompt/next-priority-new-user-focus-out/01-next-priority-new-user-focus.md
-- for how each result should be acted on.

-- ============================================================================
-- QUERY 1 — H1+H5 decision gate: v0.48.0 cohort re-read
--
-- IMPORTANT TIMING CORRECTION (catch this before running / reading the result):
-- v0.48.0 (open-loop ending + digest default-on for new signups) merged 2026-07-15.
-- The pre-committed decision rule needs the W1->W2 window CLOSED for a cohort that
-- actually experienced the shipped changes — i.e. first_pack_at >= 2026-07-15 AND
-- first_pack_at <= now() - 14 days. Those two conditions cannot both hold until
-- 2026-07-29. Running this before then will return an empty or near-empty eligible
-- set for the "post-v0.48.0" cohort — that is not a negative signal, it's "not yet
-- measurable." Don't read a small/zero eligible count as a bad result before then.
--
-- Until 2026-07-29, this query still gives you the CURRENT overall/baseline number
-- (same shape as the existing 2.4%/127 Admin dashboard figure and 03-data-pull-
-- queries.sql Query 2) — useful as a fresh sanity check, but it is not the gated
-- post-v0.48.0 read itself, because it's dominated by users who signed up before
-- the shipped changes existed.
-- ============================================================================

WITH first_pack AS (
    SELECT user_id, MIN(created_at) AS first_pack_at
    FROM analytics_events
    WHERE event_type = 'STUDY_PACK_GENERATED'
      AND user_id IS NOT NULL
    GROUP BY user_id
),
eligible AS (
    SELECT
        fp.user_id,
        fp.first_pack_at,
        (fp.first_pack_at >= '2026-07-15'::timestamptz) AS experienced_v0_48_0
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
          AND ae.created_at > e.first_pack_at + INTERVAL '7 days'
          AND ae.created_at <= e.first_pack_at + INTERVAL '14 days'
    )
)
SELECT
    e.experienced_v0_48_0,
    COUNT(*) AS eligible_activated_users,
    COUNT(r.user_id) AS returned_in_week2,
    ROUND(100.0 * COUNT(r.user_id) / NULLIF(COUNT(*), 0), 2) AS w1_to_w2_retention_pct
FROM eligible e
LEFT JOIN returned r ON r.user_id = e.user_id
GROUP BY e.experienced_v0_48_0
ORDER BY e.experienced_v0_48_0 DESC;

-- Read it this way:
--   experienced_v0_48_0 = true  -> the actual gated cohort. Before 2026-07-29 this
--                                   row will be small/absent by construction (see above).
--   experienced_v0_48_0 = false -> the pre-v0.48.0 baseline, should land near the
--                                   existing 2.4%/127 Admin dashboard figure as a
--                                   sanity check that nothing else drifted.

-- ============================================================================
-- QUERY 2 — CPALE (Accountancy) Wave-2 exam hub depth check
--
-- No course_program bucket for Accountancy exists yet (unlike ALE/PNLE/LET, which
-- already have a curated courseProgram value per docs/features/exam-hub.md). This
-- checks whether public notes tagged with accountancy-adjacent SUBJECT strings would
-- clear the same ~25-30-note bar the original three hubs cleared, the way PNLE
-- combines "Nursing" + "Medical – Surgical Nursing" into one hub.
-- ============================================================================

SELECT
    subject,
    course_program,
    COUNT(*) AS note_count
FROM notes
WHERE visibility = 'PUBLIC'
  AND (
      subject ILIKE '%account%'
      OR subject ILIKE '%audit%'
      OR subject ILIKE '%taxation%'
      OR subject ILIKE '%financial management%'
      OR subject ILIKE '%CPALE%'
      OR course_program ILIKE '%account%'
  )
GROUP BY subject, course_program
ORDER BY note_count DESC;

-- Then sum note_count across every row above that would plausibly fall under one
-- "Accountancy" hub bucket (the way this session's brief described it) and compare
-- against the ~25-30 threshold. Eyeball the subject/course_program strings for
-- near-duplicates (e.g. "Accounting" vs "Financial Accounting") the same way N1/N2's
-- subject-depth inventory did for the general subject-page gate.
