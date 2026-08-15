-- September 2026 checkpoint reads — written 2026-08-15, BEFORE the due dates.
--
-- Why this file exists: twice in one week a metric turned out to be unanswerable only after the
-- release that depended on it had shipped. v0.78.0's checkpoint asked whether a named recommendation
-- converts "better than the generic pointer" — unanswerable, because the pointer had emitted nothing
-- before it was replaced. v0.79.0's first baseline query counted curator notes and would have read
-- ~0.1% regardless of learner behaviour. Both were caught by review rather than by design.
--
-- Every query below was written against the real schema (V25: event_type, entity_id, metadata_json
-- JSONB, created_at, user_id) and the real emitted metadata keys, so each September metric is
-- confirmed COMPUTABLE. Anything that could not be expressed here is a hole to fix now, not in
-- September. None was found.
--
-- Deploy dates that split these windows: v0.74.0 2026-08-13 · v0.78.0 2026-08-15 ·
-- v0.79.0 2026-08-15 · v0.80.0 (analytics delivery fixes) — fill in on merge: ____________

-- =====================================================================================
-- 0. DELIVERY HEALTH — did v0.80.0 actually recover events? Read this FIRST.
-- Every other number below is conditioned on delivery. If volume did not change, the
-- token-expiry and shutdown-drain fixes bought nothing and the reads carry the old bias.
-- =====================================================================================
SELECT date_trunc('day', created_at)::date AS day,
       count(*)                            AS events,
       count(DISTINCT user_id)             AS learners
FROM analytics_events
WHERE created_at >= now() - interval '30 days'
GROUP BY 1 ORDER BY 1;

-- =====================================================================================
-- 1. [CHECKPOINT — due 2026-09-14] v0.78.0 — does a NAMED plan recommendation convert?
-- PROXIMAL arm, kill criterion lives here.
-- ⚠️ The two recommendationType arms are NOT a controlled comparison. They never render to
-- the same learner (named needs a program match; the pointer renders when there is none), so
-- the pointer arm is the no-program / no-coverage cohort. Directional floor, not a control.
-- ⚠️ CLICKED fires BEFORE the adopt attempt, so retries after a failed adopt count again.
-- Read it as clicks, not conversions. CTR can exceed 100%.
-- =====================================================================================
SELECT metadata_json ->> 'surface'            AS surface,
       metadata_json ->> 'recommendationType' AS arm,
       count(*) FILTER (WHERE event_type = 'STUDY_PLAN_RECOMMENDATION_IMPRESSION') AS impressions,
       count(*) FILTER (WHERE event_type = 'STUDY_PLAN_RECOMMENDATION_CLICKED')    AS clicks,
       round(100.0 * count(*) FILTER (WHERE event_type = 'STUDY_PLAN_RECOMMENDATION_CLICKED')
             / nullif(count(*) FILTER (WHERE event_type = 'STUDY_PLAN_RECOMMENDATION_IMPRESSION'), 0), 1) AS ctr_pct
FROM analytics_events
WHERE event_type IN ('STUDY_PLAN_RECOMMENDATION_IMPRESSION', 'STUDY_PLAN_RECOMMENDATION_CLICKED')
  AND created_at >= DATE '2026-08-15'
GROUP BY 1, 2 ORDER BY 1, 2;

-- DISTAL (2026-10-15): did adoption volume actually move, not just the click?
SELECT date_trunc('week', created_at)::date AS week,
       count(*)                             AS study_plan_adoptions
FROM analytics_events
WHERE event_type = 'STUDY_PLAN_ADOPTED'
GROUP BY 1 ORDER BY 1 DESC LIMIT 12;

-- =====================================================================================
-- 2. [CHECKPOINT — due 2026-09-14] v0.79.0 — does catalog-first ordering change picks?
-- Kill criterion: if matchedCatalog is not clearly high AND volume is too small to read,
-- catalog-first ordering is not the lever and ONBOARDING is the whole intervention.
-- ⚠️ Volume is expected to be small: the event fires only on a CHANGED value, on surfaces
-- where an existing value is edited. Too small to read is the finding, not a reason to extend.
-- =====================================================================================
SELECT metadata_json ->> 'surface' AS surface,
       count(*)                                                              AS selections,
       count(*) FILTER (WHERE metadata_json ->> 'matchedCatalog' = 'true')   AS matched_catalog,
       round(100.0 * count(*) FILTER (WHERE metadata_json ->> 'matchedCatalog' = 'true')
             / nullif(count(*), 0), 1)                                       AS matched_pct
FROM analytics_events
WHERE event_type = 'COURSE_PROGRAM_VALUE_SELECTED'
  AND created_at >= DATE '2026-08-15'
GROUP BY 1 ORDER BY selections DESC;

-- GUARD: the learner-note off-catalog rate must not rise above its 0.6% pre-deploy floor.
-- Learner-scoped on purpose — an unscoped version counts curator notes and reads ~0.1% always.
SELECT date_trunc('month', n.created_at)::date AS month,
       count(*)                                AS learner_notes_with_program,
       count(*) FILTER (
         WHERE NOT EXISTS (SELECT 1 FROM course_programs cp
                           WHERE lower(cp.name) = lower(trim(n.course_program)))
       )                                       AS off_catalog
FROM notes n
JOIN users u ON u.id = n.owner_user_id
WHERE n.course_program IS NOT NULL AND trim(n.course_program) <> ''
  AND u.role = 'USER' AND (u.profile_type IS NULL OR u.profile_type <> 'TEACHER')
GROUP BY 1 ORDER BY 1 DESC LIMIT 6;

-- =====================================================================================
-- 3. v0.78.0 leg (a) — is the "Next in your plan" suggestion actually used?
-- Instrumented at the v0.78.0 signoff after the checkpoint gate found it emitted nothing.
-- Reach is capped at ~31% of packed-note learners by construction; see that release's record.
-- =====================================================================================
SELECT count(*) FILTER (WHERE event_type = 'POST_SESSION_NEXT_PLAN_ITEM_IMPRESSION') AS impressions,
       count(*) FILTER (WHERE event_type = 'POST_SESSION_NEXT_PLAN_ITEM_CLICKED')    AS clicks
FROM analytics_events
WHERE event_type IN ('POST_SESSION_NEXT_PLAN_ITEM_IMPRESSION', 'POST_SESSION_NEXT_PLAN_ITEM_CLICKED')
  AND created_at >= DATE '2026-08-15';

-- =====================================================================================
-- 4. [CHECKPOINT — due 2026-09-12] v0.74.0 — did removing the Quick Review route cost
-- Adaptive Practice adoption? PRIMARY metric is BACKEND-fired, so it is unaffected by the
-- frontend delivery bug v0.80.0 fixed.
-- ⚠️ The SECONDARY metric below is frontend-fired and its window opened 2026-08-13, so
-- roughly 3 of 30 days ran on the pre-v0.80.0 delivery behaviour.
-- =====================================================================================
SELECT metadata_json ->> 'entry' AS entry_point,
       count(*)                  AS starts,
       count(DISTINCT user_id)   AS learners
FROM analytics_events
WHERE event_type = 'ADAPTIVE_PRACTICE_STARTED'
  AND created_at >= DATE '2026-08-13'
GROUP BY 1 ORDER BY starts DESC;

-- SECONDARY: does an unlocked learner ever open the Quiz tab?
SELECT count(DISTINCT user_id) FILTER (WHERE event_type = 'STUDY_PACK_QUIZ_UNLOCKED')                  AS unlocked_learners,
       count(DISTINCT user_id) FILTER (WHERE event_type = 'STUDY_PACK_QUIZ_TAB_OPENED_AFTER_UNLOCK')   AS opened_learners
FROM analytics_events
WHERE event_type IN ('STUDY_PACK_QUIZ_UNLOCKED', 'STUDY_PACK_QUIZ_TAB_OPENED_AFTER_UNLOCK')
  AND created_at >= DATE '2026-08-13';
