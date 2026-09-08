-- v0.132.0 Publication Boundary — checkpoint read
--
-- ⚠️ READ-ONLY. Every statement here is a SELECT. Run against production.
-- Due: 14 days after V141 is DEPLOYED (not merged). If deploy slips, the due date slips with it —
-- re-date the ROADMAP row rather than reading early, because the clock starts when curators can
-- actually reach the boundary.
--
-- WHY THIS CHECKPOINT EXISTS
-- v0.132.0 made every curator edit invisible to learners until an explicit `Publish update`. That is
-- the intended behaviour, but it ships on an ASSUMPTION that was not evidenced: that a curator will
-- find and press the control. F5 of the pressure test sharpens the risk — the control and the
-- `Unpublished changes` indicator live ONLY on the root collection detail page, while every topic is
-- actually added in the Builder on a child Subject Plan, which says nothing about publication.
--
-- KILL CRITERION, stated before the read:
--   If Q1 returns ANY Official Review Set holding unpublished source rows older than 14 days while
--   Q2 shows that set has never been published since deploy, the boundary is STRANDING CURRICULUM.
--   The response is to build F5 (a publication surface in the Builder), not to widen the boundary.
--   If Q1 returns zero rows, the boundary is working and F5 stays a recorded limitation.
--
-- ⚠️ THIS IS A STATE CHECK, NOT A RATE, WHICH IS WHY IT IS SINGLE-TIER DESPITE A TINY DENOMINATOR.
-- The usual small-denominator problem is statistical power on a proportion. Here ONE stranded set is
-- a complete answer, so a handful of Review Sets and effectively one curator does not underpower it.

-- Q1 — Official Review Sets holding unpublished source rows, and how stale those rows are.
--      Source-side only: source_plan_id IS NULL excludes every adopted learner copy, whose
--      published_at is meaningless because nobody publishes their own copy.
SELECT root.id                                            AS review_set_id,
       root.title,
       root.visibility,
       root.last_update_published_at,
       COUNT(*) FILTER (WHERE item.published_at IS NULL)  AS unpublished_topics,
       (SELECT COUNT(*)
          FROM note_collections child
         WHERE child.parent_collection_id = root.id
           AND child.source_plan_id IS NULL
           AND child.published_at IS NULL)                AS unpublished_subject_plans,
       MIN(item.created_at) FILTER (WHERE item.published_at IS NULL) AS oldest_unpublished_row,
       now() - MIN(item.created_at) FILTER (WHERE item.published_at IS NULL) AS stranded_for
  FROM note_collections root
  LEFT JOIN note_collections child
         ON child.parent_collection_id = root.id
        AND child.source_plan_id IS NULL
  LEFT JOIN note_collection_items item
         ON item.collection_id IN (root.id, child.id)
 WHERE root.source_plan_id IS NULL
   AND root.parent_collection_id IS NULL
   AND root.visibility = 'PUBLIC'
 GROUP BY root.id, root.title, root.visibility, root.last_update_published_at
HAVING COUNT(*) FILTER (WHERE item.published_at IS NULL) > 0
    OR (SELECT COUNT(*)
          FROM note_collections child2
         WHERE child2.parent_collection_id = root.id
           AND child2.source_plan_id IS NULL
           AND child2.published_at IS NULL) > 0
 ORDER BY oldest_unpublished_row NULLS LAST;

-- Q2 — Has each public Review Set ever been published since the boundary shipped?
--      A last_update_published_at equal to created_at is the V141 BACKFILL, not a real publish, so it
--      is reported separately: treating a backfilled stamp as evidence of curator activity is the
--      single easiest way to misread this checkpoint.
SELECT id,
       title,
       created_at,
       last_update_published_at,
       CASE
         WHEN last_update_published_at IS NULL                 THEN 'never published'
         WHEN last_update_published_at = created_at            THEN 'V141 backfill only — no real publish'
         ELSE 'published after deploy'
       END AS publication_state
  FROM note_collections
 WHERE source_plan_id IS NULL
   AND parent_collection_id IS NULL
   AND visibility = 'PUBLIC'
 ORDER BY last_update_published_at NULLS FIRST;

-- Q3 — Denominator, so the read is interpretable rather than just a row count.
--      If total_public_review_sets is 0 the checkpoint is NOT a pass; it is "not yet measurable",
--      and the correct action is to re-date, exactly as the two-tier rule requires elsewhere.
SELECT COUNT(*)                                                          AS total_public_review_sets,
       COUNT(*) FILTER (WHERE last_update_published_at IS NOT NULL
                          AND last_update_published_at <> created_at)    AS sets_published_since_deploy,
       (SELECT COUNT(DISTINCT owner_user_id)
          FROM note_collections
         WHERE source_plan_id IS NULL
           AND parent_collection_id IS NULL
           AND visibility = 'PUBLIC')                                    AS distinct_curators
  FROM note_collections
 WHERE source_plan_id IS NULL
   AND parent_collection_id IS NULL
   AND visibility = 'PUBLIC';
