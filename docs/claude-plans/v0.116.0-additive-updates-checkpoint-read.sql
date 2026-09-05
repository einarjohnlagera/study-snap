-- =============================================================================
-- v0.116.0 — Additive Review Set Updates
-- [CHECKPOINT — due 2026-10-05]   (deploy 2026-09-05 + 30 days)
--
-- ⚠️ READ-ONLY. Every statement is a SELECT. Nothing here writes.
--
-- WHY THIS IS OWED
-- -----------------------------------------------------------------------------
-- The release shipped on a DEMAND INFERENCE, not a measurement: 10 no-op re-adopt
-- attempts by 4 distinct users were read as learners asking for updates. That is
-- suggestive, not the same as uptake — those presses may equally have been people
-- trying to RESET a plan, or mis-clicks on a button that appeared to do nothing.
--
-- ⚠️ AND THE SECOND QUESTION IS THE SHARPER ONE. An apply holds ONE pooled
-- connection for the whole request (DELAYED_ACQUISITION_AND_HOLD, open-in-view ON,
-- read from ConnectionLifetimeStartupLogger rather than framework defaults), and
-- inspectSourceUpdate is N+1 by plan and runs on EVERY page load of all 523
-- adopted collections. Pool is 20 with a 5 s acquisition timeout. This is a new
-- long-holding path added ONE RELEASE after the 2026-09-04 pool-exhaustion outage.
--
-- KILL CRITERIA, stated BEFORE the read
-- -----------------------------------------------------------------------------
-- (a) UPTAKE: if learners are OFFERED updates and essentially never apply them,
--     the additive-update mechanism is not what they wanted. Do NOT build Slices
--     4-5 (structural updates) on top of it; re-open what re-adoption presses
--     actually meant before extending the surface.
-- (b) LOAD: if 5xx or acquisition-timeout rates rise materially after deploy, the
--     apply path must move off a single request-scoped connection BEFORE any
--     further work lands on it — that is the outage mechanism, not a slow page.
--
-- ⚠️ DENOMINATOR CLAUSE. 92 learners hold adopted collections and only 28 have
-- pending additions today. "Too few to read" is a RE-DATE for (a) and a finding in
-- its own right — it would mean the stranded population is smaller than the 364
-- stranded placements implied. It is NEVER a re-date for (b): a load problem does
-- not need a large denominator to be real.
-- =============================================================================


-- Q1 — UPTAKE. Adopted collections whose source has grown, and whether the learner
--      has since taken the additions. source_synced_at moving past the adoption is
--      the only durable trace an apply leaves.
SELECT
    COUNT(*)                                                             AS adoptions_with_a_live_source,
    COUNT(*) FILTER (WHERE c.source_synced_at > c.created_at + INTERVAL '1 minute')
                                                                         AS applied_at_least_once,
    ROUND(100.0 * COUNT(*) FILTER (WHERE c.source_synced_at > c.created_at + INTERVAL '1 minute')
          / NULLIF(COUNT(*), 0), 1)                                      AS applied_pct
FROM note_collections c
JOIN note_collections src ON src.id = c.source_plan_id
WHERE c.source_plan_id IS NOT NULL;


-- Q2 — THE OFFER SIDE, which makes Q1 interpretable. How many adoptions actually
--      HAVE something to apply? A low Q1 against a low Q2 means nothing was ever
--      offered — that is not learner disinterest and must not be read as it.
SELECT
    COUNT(DISTINCT c.id)     AS adoptions_with_pending_additions,
    COUNT(*)                 AS pending_placements
FROM note_collections c
JOIN note_collection_items si ON si.collection_id = c.source_plan_id
WHERE c.source_plan_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM note_collection_items ai
      JOIN notes an ON an.id = ai.note_id
      WHERE ai.collection_id = c.id
        AND COALESCE(an.copied_from_note_id, an.source_note_id) = si.note_id
  );


-- Q3 — THE RESTRUCTURE POPULATION. The shape that caused the worst defect this
--      release had: a learner holding direct notes while the source has children.
--      Should report only MOVED and apply nothing. If any of these gained children,
--      the F1 fix regressed and that is a P0, not a checkpoint finding.
SELECT
    c.id                                   AS adopted_collection_id,
    COUNT(DISTINCT ai.id)                  AS learner_direct_notes,
    COUNT(DISTINCT kid.id)                 AS learner_children,
    COUNT(DISTINCT srckid.id)              AS source_children
FROM note_collections c
JOIN note_collections src        ON src.id = c.source_plan_id
LEFT JOIN note_collection_items ai ON ai.collection_id = c.id
LEFT JOIN note_collections kid     ON kid.parent_collection_id = c.id
LEFT JOIN note_collections srckid  ON srckid.parent_collection_id = src.id
WHERE c.source_plan_id IS NOT NULL
GROUP BY c.id
HAVING COUNT(DISTINCT ai.id) > 0 AND COUNT(DISTINCT srckid.id) > 0
ORDER BY learner_direct_notes DESC;


-- Q4 — ITEM 4 REACHABILITY. Adoption now stamps a Companion baseline, so the
--      staleness signal should stop being structurally unreachable. Before this
--      release: 82 adopted collections carried a Companion and ZERO a snapshot.
SELECT
    COUNT(*) FILTER (WHERE c.companion IS NOT NULL)                     AS adopted_with_companion,
    COUNT(*) FILTER (WHERE c.companion IS NOT NULL
                       AND c.companion_structure_snapshot IS NOT NULL)  AS with_a_baseline,
    COUNT(*) FILTER (WHERE c.companion IS NOT NULL
                       AND c.companion_structure_snapshot IS NULL)      AS still_unreachable
FROM note_collections c
WHERE c.source_plan_id IS NOT NULL;
