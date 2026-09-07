-- v0.130.0 pressure test — two READ-ONLY production checks handed to the owner.
--
-- WHY THIS FILE EXISTS: the v0.129.0 pressure-test agent's production tool call was blocked, so its
-- index claim is confirmed IN-REPO ONLY. Rather than write it up as verified, the two SELECTs it wanted
-- are recorded here. Both are pure SELECTs and carry no writes; they are handed over rather than run
-- only because the agent could not reach production.
--
-- Run against production, read the output, and paste it back if anything looks wrong.

-- Q1 — Does the v0.129.0 adoption-count index (V138) actually exist on the deployed database, and is it
--      the partial index the release claims? Expect one row whose indexdef carries
--      "WHERE (source_plan_id IS NOT NULL)".
SELECT indexname,
       indexdef
FROM pg_indexes
WHERE tablename = 'note_collections'
ORDER BY indexname;

-- Q2 — Can one owner hold TWO collections adopted from the same source plan? The adoption count is a
--      COUNT(id) with no DISTINCT, so a duplicate here would over-count that source. Expect ZERO rows.
--      Any row returned means the count on that source plan is inflated and the no-DISTINCT decision
--      needs revisiting.
SELECT owner_user_id,
       source_plan_id,
       count(*) AS copies
FROM note_collections
WHERE source_plan_id IS NOT NULL
GROUP BY owner_user_id, source_plan_id
HAVING count(*) > 1
ORDER BY copies DESC;

-- Q3 — Are the children of PUBLISHED Goals actually PUBLIC in production?
--
--      WHY: the v0.130.0 fix added `applyingSourceUpdateCreatesAnAdoptionOfANEWLYAddedChildSubjectPlan`,
--      which is the guard anchoring a correction to the already-released v0.129.0. Its fixture builds the
--      new child as PRIVATE (the test helper's default), and `applySourceUpdate` copied it anyway. Two
--      possibilities, and only production can separate them:
--
--        (a) Goal children are PUBLIC in production -> the fixture is a state the real path does not
--            produce, which is the "fixture no code path can produce" anti-pattern. The guard still kills
--            its mutation, but it should be rebuilt on a PUBLIC child.
--        (b) PRIVATE children genuinely exist under PUBLISHED Goals and get copied into an adopter's
--            library -> a collection shell crosses an ownership boundary on the update path, which
--            deserves its own look. (Note the NOTES inside are separately gated: applyPlacementAddition
--            calls isPublicSourceNote and throws for a non-public note. This asks only about the
--            collection shell.)
--
--      Expect: if every row comes back PUBLIC, that is (a). Any PRIVATE row is (b).
SELECT visibility,
       count(*) AS children
FROM note_collections
WHERE parent_collection_id IN (
    SELECT id
    FROM note_collections
    WHERE visibility = 'PUBLIC'
      AND parent_collection_id IS NULL
)
GROUP BY visibility
ORDER BY visibility;
