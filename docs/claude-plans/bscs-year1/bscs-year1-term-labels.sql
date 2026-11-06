-- ============================================================================
-- BSCS Year 1: set Academic Term on the ten Subject Plans
-- Status: RUN by the owner and VERIFIED read-only (SELECT) on 2026-11-05. Kept as a RECORD; DO NOT RE-RUN
--   (the guard requires term_label to be null, so it would now match 0 rows). Committed 2026-11-05 10:45:16Z
--   (updated_at of the ten plans). Verified: First Semester / 1 on Introduction to Computing, Computer
--   Programming 1, Mathematics in the Modern World, The Contemporary World and Art Appreciation; Second Semester / 2
--   on Computer Programming 2, Discrete Structures I, Understanding the Self, Readings in Philippine History and
--   Purposive Communication. Root, visibility, titles and publication stamps unchanged.
--   Prepared 2026-11-05 by the Note Strategist session.
-- THE OWNER EXECUTES THIS. (CLAUDE.md: production writes are owner-only.)
--
-- WHY A SQL FILE AND NOT THE UI
--   The application REFUSES a term change here ("This Subject Plan is already published, so its academic
--   term can no longer change"). The rule is: a Subject Plan's term is frozen when its Year's root has ever
--   been published (root.last_update_published_at not null) AND the plan has published_at set
--   (NoteCollectionService.isTermLocked). Read 2026-11-05: the BSCS root was published 2026-11-02
--   15:57:31Z and later set back to PRIVATE, but the stamp persists, so all ten plans are locked.
--   The rule exists so a term edited after publication does not reach adopters inconsistently (adoption
--   copies the term, and Official update is additive only). THAT RISK DOES NOT APPLY HERE:
--   read 2026-11-05, there are ZERO adopted copies of the root and ZERO of its ten plans
--   (note_collections.source_plan_id), so no adopter can hold a stale term.
--
-- WHAT IT DOES
--   Sets term_label and term_order on exactly TEN rows (the ten Subject Plans of the BSCS Year root
--   967c3844-7383-4c8b-8aa0-ad6cd5567a32), matched by id AND title AND parent AND "term currently null".
--   First Semester / 1 for five plans; Second Semester / 2 for five (the same arrangement as the shaping
--   TSV). Setting all ten keeps the "every plan of a Year or none" coherence rule satisfied.
--   Nothing else is touched: not visibility, publication stamps, descriptions, notes or items.
--
-- EXPECTED RESULT: UPDATE 10.
--
-- HOW TO RUN (use psql, not an IDE console)
--   Dry run: psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f docs/claude-plans/bscs-year1/bscs-year1-term-labels.sql
--   Apply:   the same command with -c "COMMIT;" appended.
-- ============================================================================

-- STEP 0 (read-only): expect 10 rows, every guard_ok = true, term_label null, and 0 adopted copies.
SELECT c.title, c.visibility, c.term_label, c.term_order,
       (c.parent_collection_id = '967c3844-7383-4c8b-8aa0-ad6cd5567a32' AND c.source_plan_id IS NULL
        AND c.term_label IS NULL AND c.term_order IS NULL) AS guard_ok
FROM note_collections c
WHERE c.parent_collection_id = '967c3844-7383-4c8b-8aa0-ad6cd5567a32'
ORDER BY c.title;

-- 0b: adopters (expect 0 for root and children).
SELECT count(*) AS adopted_copies
FROM note_collections
WHERE source_plan_id = '967c3844-7383-4c8b-8aa0-ad6cd5567a32'
   OR source_plan_id IN (SELECT id FROM note_collections WHERE parent_collection_id = '967c3844-7383-4c8b-8aa0-ad6cd5567a32');

BEGIN;

-- STEP 1: expect UPDATE 10.
WITH t(id, title, label, ord) AS (VALUES
    ('573827e0-0182-46dc-8f68-6d980b77d51b'::uuid, '💻 Introduction to Computing',          'First Semester',  1),
    ('2cb61a8d-9417-4286-8b4b-ddbe7f9b672a'::uuid, '⌨️ Computer Programming 1',            'First Semester',  1),
    ('e934a474-b19d-402f-9e5b-391cd610bbe2'::uuid, '📐 Mathematics in the Modern World',   'First Semester',  1),
    ('0866568b-7ab9-4de0-aa16-c2f82ebd5cba'::uuid, '🌏 The Contemporary World',            'First Semester',  1),
    ('2a1a7b22-7797-44db-9135-5d2932f84ae2'::uuid, '🎨 Art Appreciation',                  'First Semester',  1),
    ('dcfaf040-a656-4b34-85be-68d5d74424a2'::uuid, '🧠 Computer Programming 2',            'Second Semester', 2),
    ('4d8952af-f716-49b2-9efa-7e82ef1dbfd4'::uuid, '🧮 Discrete Structures I',             'Second Semester', 2),
    ('2c3d2799-af08-49d0-885f-88136269e158'::uuid, '🪞 Understanding the Self',            'Second Semester', 2),
    ('f5d3f967-21cd-44cc-89c9-8ccc2c617541'::uuid, '📜 Readings in Philippine History',    'Second Semester', 2),
    ('9ead2477-7a8d-4fe9-8e3b-79763dd82a20'::uuid, '💬 Purposive Communication',           'Second Semester', 2)
)
UPDATE note_collections c
SET term_label = t.label, term_order = t.ord, updated_at = now()
FROM t
WHERE c.id = t.id
  AND c.title = t.title
  AND c.parent_collection_id = '967c3844-7383-4c8b-8aa0-ad6cd5567a32'
  AND c.source_plan_id IS NULL
  AND c.term_label IS NULL AND c.term_order IS NULL;

-- STEP 2 (inside the transaction): expect 5 plans First Semester/1 and 5 plans Second Semester/2.
SELECT term_label, term_order, count(*) AS plans, string_agg(title, ' | ' ORDER BY title) AS titles
FROM note_collections
WHERE parent_collection_id = '967c3844-7383-4c8b-8aa0-ad6cd5567a32'
GROUP BY term_label, term_order ORDER BY term_order;

-- COMMIT;   -- supplied by the -c "COMMIT;" above. If STEP 1 is not UPDATE 10, ROLLBACK and tell Claude what
--           -- STEP 0 showed.

-- ============================================================================
-- ROLLBACK (only after a COMMIT you want to undo)
-- ============================================================================
-- BEGIN;
-- UPDATE note_collections SET term_label = NULL, term_order = NULL, updated_at = now()
-- WHERE parent_collection_id = '967c3844-7383-4c8b-8aa0-ad6cd5567a32' AND term_label IN ('First Semester', 'Second Semester');
-- COMMIT;    -- expect UPDATE 10
