-- 2026-09-06 — fix an accidentally-pasted section name in review set 85078db3-c7ea-4ee1-9c62-291543234663
--
-- Target: rename that section to 'Housing and Human Settlements'.
--
-- ⚠️ OWNER RUNS THIS. Claude does not execute writes against production (CLAUDE.md, owner rule
--    2026-09-04). Steps 1-2 are SELECTs and are safe to run now; step 3 is the write.
--
-- ⚠️ RUN STEP 1 AND STEP 2 FIRST AND READ THE OUTPUT. The literal you pasted is 137 codepoints /
--    138 UTF-16 units, and note_collection_items.label is VARCHAR(120) (V72:17) with the service
--    REJECTING anything longer (NoteCollectionService.validateOptionalLabel:2804-2809 — it rejects,
--    it does not truncate). So the value you pasted CANNOT be what is stored, and an exact-equality
--    UPDATE written against it would match zero rows. Step 2 shows the real stored literal.
--
-- Background facts, verified in code rather than assumed:
--   * A "section" is not a table. It is derived from note_collection_items.label — every note in a
--     section carries the same label string. Renaming a section = updating N item rows.
--   * label is trimmed and blank-to-null normalised on write (normalizeOptionalText:2812-2818), so
--     the stored value has no leading or trailing whitespace. Internal runs of spaces ARE preserved,
--     which is why the pasted value has that long gap in the middle.
--   * A Goal collection cannot contain notes directly (GOAL_CANNOT_ACCEPT_NOTES_MESSAGE), so if
--     85078db3… is a Goal, the section lives on a CHILD Subject Plan and not on the Goal itself.
--     Step 1 settles which, and step 2 searches the Goal AND its children for that reason.

-- =============================================================================================
-- STEP 1 (SELECT) — what is 85078db3…: a Goal with children, or a Subject Plan holding notes?
-- =============================================================================================
SELECT c.id,
       c.title,
       c.parent_collection_id,
       (c.parent_collection_id IS NULL) AS is_top_level,
       (SELECT count(*) FROM note_collections ch WHERE ch.parent_collection_id = c.id) AS child_plans,
       (SELECT count(*) FROM note_collection_items i WHERE i.collection_id = c.id)     AS direct_notes
FROM note_collections c
WHERE c.id = '85078db3-c7ea-4ee1-9c62-291543234663'
   OR c.parent_collection_id = '85078db3-c7ea-4ee1-9c62-291543234663'
ORDER BY is_top_level DESC, c.title;

-- =============================================================================================
-- STEP 2 (SELECT) — every section label in that review set and its children.
-- ⚠️ THIS IS THE STEP THAT MATTERS. Copy the exact `label` value of the bad row from here.
-- ⚠️ Also check whether 'Housing and Human Settlements' ALREADY exists below. If it does, the
--    rename MERGES two sections into one — which may be what you want, but it is not a rename,
--    and it is irreversible without knowing which notes came from which side.
-- =============================================================================================
SELECT i.collection_id,
       c.title                       AS plan_title,
       i.label,
       char_length(i.label)          AS label_chars,
       count(*)                      AS notes_in_section,
       min(i.position)               AS first_position,
       max(i.position)               AS last_position
FROM note_collection_items i
JOIN note_collections c ON c.id = i.collection_id
WHERE i.collection_id = '85078db3-c7ea-4ee1-9c62-291543234663'
   OR i.collection_id IN (
        SELECT ch.id FROM note_collections ch
        WHERE ch.parent_collection_id = '85078db3-c7ea-4ee1-9c62-291543234663'
      )
GROUP BY i.collection_id, c.title, i.label
ORDER BY c.title, first_position;

-- =============================================================================================
-- STEP 3 (WRITE — owner executes) — the rename.
--
-- Matches on an ANCHORED PREFIX rather than full equality, deliberately: the exact stored literal
-- is unknown to the author (see the header), and a prefix match on the distinctive opening is both
-- robust to the trailing truncation and narrow enough to hit one section. It is additionally scoped
-- to this review set, so it cannot touch another plan that happens to use similar wording.
--
-- ⚠️ EXPECTED ROW COUNT: the `notes_in_section` value shown for the bad label in step 2. If the
--    UPDATE reports a different number, ROLLBACK — something else matched.
-- ⚠️ Run inside the transaction as written. Do not COMMIT until the count matches and the SELECT
--    inside the transaction looks right.
-- =============================================================================================
BEGIN;

UPDATE note_collection_items
   SET label = 'Housing and Human Settlements'
 WHERE label LIKE '🌳 Planning and Site Development%'
   AND (
         collection_id = '85078db3-c7ea-4ee1-9c62-291543234663'
      OR collection_id IN (
           SELECT ch.id FROM note_collections ch
           WHERE ch.parent_collection_id = '85078db3-c7ea-4ee1-9c62-291543234663'
         )
       );

-- Verify BEFORE committing: the bad label must be gone and the new one present with the row count
-- step 2 predicted.
SELECT label, char_length(label) AS label_chars, count(*) AS notes_in_section
FROM note_collection_items
WHERE collection_id = '85078db3-c7ea-4ee1-9c62-291543234663'
   OR collection_id IN (
        SELECT ch.id FROM note_collections ch
        WHERE ch.parent_collection_id = '85078db3-c7ea-4ee1-9c62-291543234663'
      )
GROUP BY label
ORDER BY label;

COMMIT;    -- uncomment and run once the row count and labels above are correct
-- ROLLBACK;  -- otherwise

-- =============================================================================================
-- NOT CHANGED, deliberately
-- =============================================================================================
-- * source_label_at_sync is left alone. V134 added it to record the label the note had AT SYNC on
--   the upstream source; it is provenance, not the learner-facing section name, and rewriting it
--   would falsify the record of what was actually synced.
-- * position is untouched — this is a rename, so section membership and note order do not move.
-- * No note, Study Pack, quiz or session history is touched.

-- =============================================================================================
-- STEP 2b (SELECT) — WHY THE BUILDER PAGE KEEPS REFRESHING. Run this; it is the same root cause.
--
-- A section label whose stored form differs from its WHITESPACE-COLLAPSED form puts
-- /collections/{id}/builder into an unbounded write -> refresh loop. Proven in code:
--
--   1. LeafSortableNoteCard seeds `labelValue` from item.label, then every render computes
--      `nextLabel = labelValue.trim().replaceAll(/\s+/g, " ")` and compares it to the RAW
--      item.label (study-plan-builder-page-client.tsx:445-455). A label containing a run of
--      internal spaces is never equal to its own collapsed form, so the guard never returns
--      early and it schedules onLabelChange 500ms later.
--   2. handleLeafLabelChange (:1646-1657) then snaps the requested name back to an existing
--      section by comparing `normalizeSectionValue(existing) === normalizeSectionValue(requested)`.
--      normalizeSectionValue ALSO collapses whitespace (lib/collection-labels.ts:137-139), so the
--      RAW label matches the collapsed request and `exactExistingName` wins — the write sends the
--      raw, still-uncollapsed label BACK.
--   3. persistLeafItems writes it and then `await refreshBuilder()` (:1541), which refetches the
--      collection AND listNotes() — the user's ENTIRE note list.
--   4. item.label returns unchanged, so step 1 fires again. Forever.
--
-- ⚠️ The two halves fight each other: the card normalises whitespace, the case-snap immediately
--    un-normalises it. Neither is wrong alone. There is no tick cap, no error and no toast, so it
--    presents purely as a page that will not stop reloading.
-- ⚠️ CONSEQUENCE: renaming this label to 'Housing and Human Settlements' — which has no double
--    spaces — ENDS THE LOOP as a side effect. The two reported problems are one.
-- =============================================================================================
SELECT i.collection_id,
       c.title                  AS plan_title,
       i.label,
       char_length(i.label)     AS label_chars,
       count(*)                 AS notes_affected
FROM note_collection_items i
JOIN note_collections c ON c.id = i.collection_id
WHERE i.label IS NOT NULL
  AND i.label <> regexp_replace(btrim(i.label), '\s+', ' ', 'g')   -- differs from its collapsed form
GROUP BY i.collection_id, c.title, i.label
ORDER BY notes_affected DESC, c.title;

-- ⚠️ Rows returned here are ALL loop triggers, not just yours. Every curator who opens the builder
--    for one of those collections gets the same runaway refresh. Worth handing to the implementing
--    session as the real fix; the UPDATE below only clears the one instance you hit.
