-- Campaign Feedback ("STUDY_FRICTION_2026_09") — the read pack for reviewing responses.
--
-- PURPOSE. v1 has no admin UI by design (plan §I, Outcome D — sized on evidence: 405 active
-- users, a 1.8% click-through floor with no impression denominator, and no admin feedback list
-- endpoint anywhere in the repo to build on). This file is the v1 review mechanism: run it
-- whenever you want to check on the campaign, the same pattern docs/curriculum/review-set-reshape-read.sql
-- established for Review Set reshaping.
--
-- ⚠️ EVERY QUERY IS READ-ONLY. No writes, no DDL. Safe to run against production as-is.
-- No parameters to fill in — there is exactly one campaign instrument (CAMPAIGN_ID is a fixed
-- constant, not a table row), so every query below hardcodes 'STUDY_FRICTION_2026_09'.
-- No psql meta-commands, so it pastes into DBeaver / JetBrains / pgAdmin as-is.
--
-- Source: docs/claude-plans/actionable-announcements-campaign-feedback-stage1-plan.md §G, §I.
-- Feeds: ROADMAP.md's [CHECKPOINT — due 2026-10-08] row — its three-way kill criterion is
-- answered by Q0 (was it even published) and Q1 (response count).

-- =====================  Q0 — was the invitation ever published, and is it still live?  =====================
-- ⚠️ Keyed on cta_path, not the owner-typed title — a reworded or typo'd title would make a
-- title-matched query return zero rows and misreport "never published" (the exact scenario the
-- checkpoint's kill criterion exists to catch). If this returns zero rows, nothing downstream in
-- this file can produce a meaningful answer: the campaign has no invitation, so a response count
-- of zero means "not published," not "nobody wanted to respond."
SELECT id, status, published_at, expires_at, audience
FROM announcements
WHERE cta_path = '/feedback'
ORDER BY id DESC;

-- =====================  Q1 — response count and reach, against the active user base  =====================
-- Compare against Q0's audience: an EVERYONE announcement should be read against total active
-- users; a segmented audience should be read against that segment's size instead.
SELECT
    count(*)                                            AS total_responses,
    (SELECT count(*) FROM users WHERE status = 'ACTIVE') AS active_users,
    round(
        100.0 * count(*) / NULLIF((SELECT count(*) FROM users WHERE status = 'ACTIVE'), 0),
        2
    ) AS response_rate_pct
FROM campaign_feedback_responses
WHERE campaign_id = 'STUDY_FRICTION_2026_09';

-- =====================  Q1b — the rate Q1 CANNOT compute: read→responded, not delivered→responded  =====================
-- ⚠️ ADDED 2026-09-22, after the campaign's own announcement delivered 406 notifications in one
-- fan-out — 7x the entire historical notification volume the plan's "1.8% click-through floor"
-- figure was computed on (plan §A3). That figure was a floor with NO impression denominator
-- because none existed at the time; THIS campaign is the first one big enough to supply one.
-- Q1 answers "responses / all active users" (a reach-and-conversion blend). This answers the
-- narrower, more diagnostic question: of the learners who actually clicked the notification
-- through to the page, how many completed it? Low here + healthy Q1 read-rate below = a landing
-- page problem, not a discovery problem. Correlates by user_id, both sides scoped to this
-- campaign's one announcement (cta_path = '/feedback' is unique to it, confirmed by Q0).
WITH campaign_notified AS (
    SELECT recipient_user_id, read_at
    FROM notifications
    WHERE cta_path = '/feedback'
)
SELECT
    count(*)                                                        AS delivered,
    count(*) FILTER (WHERE read_at IS NOT NULL)                     AS read_by_recipient,
    round(100.0 * count(*) FILTER (WHERE read_at IS NOT NULL)
          / NULLIF(count(*), 0), 2)                                  AS read_rate_pct,
    (SELECT count(*) FROM campaign_feedback_responses
     WHERE campaign_id = 'STUDY_FRICTION_2026_09')                   AS total_responses,
    (SELECT count(DISTINCT r.user_id)
     FROM campaign_feedback_responses r
     JOIN campaign_notified n ON n.recipient_user_id = r.user_id AND n.read_at IS NOT NULL
     WHERE r.campaign_id = 'STUDY_FRICTION_2026_09')                 AS responded_among_readers,
    round(100.0 *
        (SELECT count(DISTINCT r.user_id)
         FROM campaign_feedback_responses r
         JOIN campaign_notified n ON n.recipient_user_id = r.user_id AND n.read_at IS NOT NULL
         WHERE r.campaign_id = 'STUDY_FRICTION_2026_09')
        / NULLIF(count(*) FILTER (WHERE read_at IS NOT NULL), 0), 2) AS read_to_responded_pct
FROM campaign_notified;

-- =====================  Q2 — primary blocker breakdown  =====================
-- The core diagnostic read: which axis of friction is most reported. TEXT[] unnest, not a join —
-- this table deliberately has no child table (plan §G).
SELECT unnest(primary_blockers) AS primary_blocker, count(*) AS responses
FROM campaign_feedback_responses
WHERE campaign_id = 'STUDY_FRICTION_2026_09'
GROUP BY 1
ORDER BY 2 DESC;

-- =====================  Q3 — quiz-issue breakdown (conditional on "quiz questions could be better")  =====================
-- Only rows that selected QUIZ_QUALITY as a primary blocker can carry these — a NULL/empty
-- quiz_issues array here for a QUIZ_QUALITY row means the learner selected the parent but
-- skipped its follow-up, which is a valid, optional submission shape (plan §E step 4).
SELECT unnest(quiz_issues) AS quiz_issue, count(*) AS responses
FROM campaign_feedback_responses
WHERE campaign_id = 'STUDY_FRICTION_2026_09'
  AND quiz_issues IS NOT NULL
GROUP BY 1
ORDER BY 2 DESC;

-- =====================  Q4 — plan-issue breakdown (conditional on "paid plans aren't right")  =====================
-- Single-select, so a plain GROUP BY, not unnest.
SELECT plan_issue, count(*) AS responses
FROM campaign_feedback_responses
WHERE campaign_id = 'STUDY_FRICTION_2026_09'
  AND plan_issue IS NOT NULL
GROUP BY 1
ORDER BY 2 DESC;

-- =====================  Q5 — missing-feature free text (conditional on "a feature I need is missing")  =====================
SELECT id, created_at, missing_feature_text
FROM campaign_feedback_responses
WHERE campaign_id = 'STUDY_FRICTION_2026_09'
  AND missing_feature_text IS NOT NULL
  AND missing_feature_text <> ''
ORDER BY created_at DESC;

-- =====================  Q6 — content-subject free text (conditional on "can't find enough content")  =====================
SELECT id, created_at, content_subject_text
FROM campaign_feedback_responses
WHERE campaign_id = 'STUDY_FRICTION_2026_09'
  AND content_subject_text IS NOT NULL
  AND content_subject_text <> ''
ORDER BY created_at DESC;

-- =====================  Q7 — the free-text field (always available, "Something else" relies on it)  =====================
-- Read this one manually, in full — it is the only unstructured signal in the table and is
-- usually the highest-information field for a small N. NOT joined to user identity in this
-- query by design; add "u.email" only if you specifically need to follow up with a respondent,
-- and treat that as a deliberate exception to the aggregate-only review this file otherwise does.
SELECT id, created_at, free_text
FROM campaign_feedback_responses
WHERE campaign_id = 'STUDY_FRICTION_2026_09'
  AND free_text IS NOT NULL
  AND free_text <> ''
ORDER BY created_at DESC;

-- =====================  Q8 — "Nothing major" vs genuine friction, as a share of respondents  =====================
-- NOTHING_MAJOR is mutually exclusive with every blocker (plan §F/§E), so a response containing
-- it never also contains a real blocker — this splits cleanly into two disjoint groups.
SELECT
    count(*) FILTER (WHERE 'NOTHING_MAJOR' = ANY(primary_blockers)) AS nothing_major_responses,
    count(*) FILTER (WHERE NOT ('NOTHING_MAJOR' = ANY(primary_blockers))
                      AND array_length(primary_blockers, 1) > 0)    AS reported_friction_responses,
    count(*) FILTER (WHERE array_length(primary_blockers, 1) IS NULL
                      OR array_length(primary_blockers, 1) = 0)     AS free_text_only_responses
FROM campaign_feedback_responses
WHERE campaign_id = 'STUDY_FRICTION_2026_09';
