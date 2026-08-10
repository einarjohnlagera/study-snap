-- Follow-up reads after 03-course-program-vocabulary.sql ran against production 2026-08-03.
-- (03 was removed 2026-08-06 once Release A shipped; its results live in 05-vocabulary-results.md.)
-- Read-only. Three questions the first pass raised or answered badly.
--
-- ============================================================================
-- WHY THIS EXISTS
-- ============================================================================
-- 03's results were clean and mostly good news (27 note-side values, ZERO character-level
-- collisions, 0.00% measured duplication). But two of its queries measured the wrong thing,
-- and one result raises a question that changes sequencing:
--
--   (1) Query G counted DIRECT note membership only (note_collection_items), while
--       note_collections has had parent_collection_id since V83. The four exam-level
--       "Comprehensive Review" sets returned 0 notes each — almost certainly because their
--       notes live in child collections. So "avg 8.6 notes per published Review Set" is the
--       per-SUBJECT figure, not the per-comprehensive-Review-Set figure the initiative's
--       claim is actually about. Query H fixes the unit.
--
--   (2) Query E matched normalized (title, subject) exactly, found 0 duplicate groups, and
--       that is being read as "no duplication exists." It is not that strong a claim.
--       Query F simultaneously found 11 subjects spanning 2+ programs — including
--       Pharmacology with 109 notes across {Nursing, Pharmacy} and Strength of Materials
--       across {Civil Engineering, Mechanical Engineering}. Exact-title matching cannot
--       distinguish "genuinely different topics per program" (benign) from "same topics,
--       differently worded titles" (duplication Baseline A is blind to). Query J surfaces
--       the titles so this can be judged by eye. This is the discriminating question for
--       whether Step 3 is worth building.
--
--   (3) Civil Engineering has 197 official public notes — the LARGEST official public
--       program in the library — and appears in ZERO published Review Sets. Query I checks
--       whether those notes are assembled anywhere at all. If authoring is done and
--       assembly is the real gap, that is a different near-term priority than schema work.

-- ============================================================================
-- QUERY H — hierarchy-aware Review Set size (corrects §9 Baseline B)
-- ============================================================================
-- Counts, for every published root Review Set (no parent), the notes reachable through the
-- full parent→child hierarchy, not just direct membership. Compare `direct_notes` against
-- `rollup_notes` — a large gap confirms the four exam-level sets are assembly shells.
WITH RECURSIVE roots AS (
    SELECT id, title, course_program, owner_user_id
    FROM note_collections
    WHERE visibility = 'PUBLIC'
      AND parent_collection_id IS NULL
),
descendants AS (
    SELECT r.id AS root_id, r.id AS collection_id
    FROM roots r
    UNION ALL
    SELECT d.root_id, c.id
    FROM descendants d
    JOIN note_collections c ON c.parent_collection_id = d.collection_id
)
SELECT
    r.title,
    r.course_program,
    (SELECT COUNT(*) FROM note_collection_items i WHERE i.collection_id = r.id)        AS direct_notes,
    COUNT(DISTINCT i.note_id)                                                          AS rollup_notes,
    COUNT(DISTINCT d.collection_id) - 1                                                AS child_collections
FROM roots r
JOIN descendants d ON d.root_id = r.id
LEFT JOIN note_collection_items i ON i.collection_id = d.collection_id
GROUP BY r.id, r.title, r.course_program
ORDER BY rollup_notes DESC;

-- ============================================================================
-- QUERY I — the Civil Engineering assembly gap
-- ============================================================================
-- 197 official public Civil Engineering notes, 0 published Review Sets. Where are they?
-- Splits them by whether they belong to any collection at all, and if so, that collection's
-- visibility — distinguishing "authored but never assembled" from "assembled but unpublished."
SELECT
    CASE
        WHEN c.id IS NULL           THEN 'in no collection'
        WHEN c.visibility = 'PUBLIC' THEN 'in a PUBLIC collection'
        ELSE                             'in a PRIVATE collection'
    END                        AS assembly_state,
    COUNT(DISTINCT n.id)       AS notes,
    COUNT(DISTINCT c.id)       AS collections
FROM notes n
LEFT JOIN note_collection_items i ON i.note_id = n.id
LEFT JOIN note_collections c      ON c.id = i.collection_id
WHERE n.course_program = 'Civil Engineering'
  AND n.visibility = 'PUBLIC'
  AND EXISTS (
      SELECT 1 FROM users official_user
      WHERE official_user.id = n.owner_user_id
        AND (official_user.role = 'ADMIN'
             OR lower(official_user.email) = lower('einar.lagera@gmail.com'))
        AND official_user.id <> '00000000-0000-0000-0000-00000000d1ed'::uuid
  )
GROUP BY assembly_state
ORDER BY notes DESC;

-- Same question for every official public program, as context — is Civil Engineering
-- unusual, or is "authored but unassembled" the normal state of the library?
SELECT
    n.course_program,
    COUNT(DISTINCT n.id)                                        AS official_public_notes,
    COUNT(DISTINCT n.id) FILTER (WHERE i.note_id IS NOT NULL)   AS in_some_collection,
    COUNT(DISTINCT n.id) FILTER (WHERE i.note_id IS NULL)       AS in_no_collection
FROM notes n
LEFT JOIN note_collection_items i ON i.note_id = n.id
WHERE n.visibility = 'PUBLIC'
  AND n.course_program IS NOT NULL AND trim(n.course_program) <> ''
  AND EXISTS (
      SELECT 1 FROM users official_user
      WHERE official_user.id = n.owner_user_id
        AND (official_user.role = 'ADMIN'
             OR lower(official_user.email) = lower('einar.lagera@gmail.com'))
        AND official_user.id <> '00000000-0000-0000-0000-00000000d1ed'::uuid
  )
GROUP BY n.course_program
ORDER BY official_public_notes DESC;

-- ============================================================================
-- QUERY J — is the cross-program sharing real duplication or genuinely different content?
-- ============================================================================
-- The discriminating read. For each subject that spans 2+ programs, lists titles grouped by
-- program so overlap can be judged by eye. Restricted to the concentrations that matter:
-- Pharmacology (109 notes, {Nursing, Pharmacy}) and Strength of Materials
-- (11 notes, {Civil Engineering, Mechanical Engineering}) — the latter being the
-- Civil-Engineering-expansion case already present in miniature.
--
-- READING IT: if Nursing-Pharmacology and Pharmacy-Pharmacology cover DIFFERENT topics,
-- the current single-program model is working and Step 3's value is discovery-only. If they
-- cover the SAME topics with different titles, duplication already exists at scale,
-- Baseline A's 0.00% is an artifact of exact-title matching, and Step 3 is worth more than
-- the plan currently credits it.
SELECT
    n.subject,
    n.course_program,
    n.title
FROM notes n
WHERE n.subject IN ('Pharmacology', 'Strength of Materials', 'Computer Science', 'Biology')
  AND n.course_program IS NOT NULL AND trim(n.course_program) <> ''
  AND n.visibility = 'PUBLIC'
ORDER BY n.subject, n.course_program, n.title;

-- Compact form if the above is too long to scan: per subject+program, how many notes and
-- how many share a normalized title with a note under a DIFFERENT program for that subject.
WITH public_notes AS (
    SELECT
        n.subject,
        n.course_program,
        regexp_replace(lower(trim(coalesce(n.title, ''))), '\s+', ' ', 'g') AS norm_title
    FROM notes n
    WHERE n.visibility = 'PUBLIC'
      AND n.subject IS NOT NULL AND trim(n.subject) <> ''
      AND n.course_program IS NOT NULL AND trim(n.course_program) <> ''
)
SELECT
    a.subject,
    a.course_program,
    COUNT(*)                                                     AS notes,
    COUNT(*) FILTER (WHERE EXISTS (
        SELECT 1 FROM public_notes b
        WHERE b.subject = a.subject
          AND b.course_program <> a.course_program
          AND b.norm_title = a.norm_title
    ))                                                           AS exact_title_shared_with_other_program
FROM public_notes a
GROUP BY a.subject, a.course_program
HAVING (SELECT COUNT(DISTINCT c.course_program) FROM public_notes c WHERE c.subject = a.subject) > 1
ORDER BY a.subject, notes DESC;
