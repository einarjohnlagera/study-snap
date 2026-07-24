-- Phase 3a (Shared Official Pool Foundation, v0.61.0) adoption-concurrency gate check.
-- Run against production Postgres, read-only.
-- Companion to docs/product/ROADMAP.md's "Company Redefinition Roadmap — Phase Detail"
-- Phase 3 section. Requested 2026-07-24 in response to a fresh signup surge, to check
-- whether adoption telemetry now shows a shared Official Review Set with enough
-- concurrent adopters that duplicated per-owner pool generation is a measurable cost,
-- not hypothetical — the proposed (not owner-stated) gate for kicking off 3a.
--
-- ============================================================================
-- WHAT THIS DOES AND DOES NOT ANSWER
-- ============================================================================
-- This is diagnostic, not a pass/fail formula — the owner has not set a numeric
-- threshold. It answers "how many distinct users have adopted the same shared
-- Official Review Set" (all-time, and separately for the recent surge window), so
-- that number can inform a ratification decision, the same way the Diagnostic
-- Read informs v0.60.0's gate. A high count on one popular set is evidence FOR
-- kicking off 3a; a flat, spread-out distribution across many sets with low
-- per-set adopter counts is evidence the per-user pool duplication cost is still
-- hypothetical, regardless of how large the signup surge itself is.
--
-- Source of the adoption event: NoteCollectionService.trackStudyPlanAdopted() /
-- trackStudyGoalAdopted() write STUDY_PLAN_ADOPTED / STUDY_GOAL_ADOPTED rows to
-- analytics_events, with entity_id = the newly created PERSONAL copy's id and
-- metadata_json->>'sourcePlanId' = the shared Official (published) source plan's
-- id. adopt()/adoptGoal() only ever operate on PUBLIC-visibility collections, so
-- every sourcePlanId here is already Official/published content by construction
-- — no extra visibility filter is needed.
--
-- A single user can appear more than once against the same sourcePlanId (e.g. a
-- repeat "already adopted" resolution re-fires the tracking event) — every query
-- below counts DISTINCT user_id per sourcePlanId to avoid inflating the adopter
-- count from repeat/idempotent calls by the same person.

-- ============================================================================
-- Query 1 — all-time distinct adopters per shared source plan, ranked
-- ============================================================================
-- This is the primary number for the gate: does any single Official Review Set
-- have enough distinct adopters that its per-owner pool is being regenerated
-- redundantly many times over?
SELECT
    (ae.metadata_json ->> 'sourcePlanId')::uuid AS source_plan_id,
    nc.title AS source_plan_title,
    nc.course_program,
    ae.event_type,
    COUNT(DISTINCT ae.user_id) AS distinct_adopters,
    MIN(ae.created_at) AS first_adopted_at,
    MAX(ae.created_at) AS most_recent_adopted_at
FROM analytics_events ae
LEFT JOIN note_collections nc
    ON nc.id = (ae.metadata_json ->> 'sourcePlanId')::uuid
WHERE ae.event_type IN ('STUDY_PLAN_ADOPTED', 'STUDY_GOAL_ADOPTED')
GROUP BY 1, 2, 3, 4
ORDER BY distinct_adopters DESC
LIMIT 30;

-- ============================================================================
-- Query 2 — same, scoped to the last 14 days, to isolate the surge's own
-- contribution rather than all-time historical build-up
-- ============================================================================
SELECT
    (ae.metadata_json ->> 'sourcePlanId')::uuid AS source_plan_id,
    nc.title AS source_plan_title,
    nc.course_program,
    ae.event_type,
    COUNT(DISTINCT ae.user_id) AS distinct_adopters_last_14d,
    MIN(ae.created_at) AS first_adopted_at,
    MAX(ae.created_at) AS most_recent_adopted_at
FROM analytics_events ae
LEFT JOIN note_collections nc
    ON nc.id = (ae.metadata_json ->> 'sourcePlanId')::uuid
WHERE ae.event_type IN ('STUDY_PLAN_ADOPTED', 'STUDY_GOAL_ADOPTED')
    AND ae.created_at >= now() - interval '14 days'
GROUP BY 1, 2, 3, 4
ORDER BY distinct_adopters_last_14d DESC
LIMIT 30;

-- ============================================================================
-- Query 3 — denominator / shape context: how many distinct source plans have
-- ANY adopter at all, and how concentrated adoption is overall
-- ============================================================================
-- Helps read Query 1/2 in context: a top result of "27 adopters" means something
-- different if it's 1 of 3 ever-adopted plans vs. 1 of 300. This mirrors the
-- concentration shape already seen in the copy/adoption analytics discussed
-- 2026-07-24 (2,096 public-note copies across 701 public notes, with a handful
-- of titles well ahead of the rest) — this query checks whether Official
-- Review Set adoption (a different action from public-note copying) shows the
-- same concentrated shape.
SELECT
    ae.event_type,
    COUNT(DISTINCT (ae.metadata_json ->> 'sourcePlanId')) AS distinct_source_plans_with_any_adopter,
    COUNT(DISTINCT ae.user_id) AS distinct_adopting_users_overall,
    COUNT(*) AS total_adoption_events
FROM analytics_events ae
WHERE ae.event_type IN ('STUDY_PLAN_ADOPTED', 'STUDY_GOAL_ADOPTED')
GROUP BY ae.event_type;
