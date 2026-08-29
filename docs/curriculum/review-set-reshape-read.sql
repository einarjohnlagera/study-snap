-- Architecture Review Set reshape — the read pack for the Note Strategist.
--
-- PURPOSE. The Architecture Review Set (`b0db3648-c520-40a5-8e0b-f8ebfcdef102`) is thinner than
-- the Civil Engineering set, and the two overlap heavily. These queries produce everything a
-- strategist needs to propose the NEW shape: what Architecture has today, what CE has as the
-- comprehensiveness benchmark, what material already exists and is Architecture-applicable but
-- unused, and where the gaps are.
--
-- ⚠️ EVERY QUERY IS READ-ONLY. No writes, no DDL. Safe to run against production.
-- No psql meta-commands — pastes into DBeaver / JetBrains / pgAdmin as-is.
--
-- ⚠️ ADDING A NOTE TO A REVIEW SET DOES NOT COPY IT. `note_collection_items` is a join, so one
-- canonical note can sit in the Architecture set AND the Civil Engineering set at once. The
-- strategist should propose REUSE, not duplication — duplicating shared knowledge per program
-- is the exact failure the Applicable Programs axis exists to prevent.
--
-- ⚠️ TWO PROGRAMS ARE RELEVANT, NOT ONE. The catalog carries both `Architecture` and
-- `Architectural Engineering`, and they are distinct rows. Queries 4 and 5 include both;
-- deciding whether they are one audience or two is a strategist question.
--
-- ⚠️ STRUCTURE. A Review Set is a root `note_collections` row (`parent_collection_id IS NULL`).
-- Its children are subject plans. Notes hang off the LEAF collections via `note_collection_items`,
-- where `label` is the section name and `position` is the order. There is no "kind" column —
-- depth is the only thing distinguishing a Review Set from a subject plan.

-- ---------------------------------------------------------------------------
-- Q0. Identify the review sets. Confirm the Architecture id, and CONFIRM THAT EXACTLY ONE
-- root collection matches `title ILIKE '%civil%'` — Q3 and Q5 resolve the Civil Engineering
-- root by that predicate rather than by a pasted id. If Q0 shows two matching roots (an old
-- and a rebuilt set, say), replace the predicate in Q3/Q5 with the explicit id instead.
-- ---------------------------------------------------------------------------
SELECT c.id,
       c.title,
       c.visibility,
       c.course_program,
       c.learner_level,
       (SELECT count(*) FROM note_collections k WHERE k.parent_collection_id = c.id) AS child_plans,
       (SELECT count(*) FROM note_collection_items i
          JOIN note_collections k ON k.id = i.collection_id
         WHERE k.id = c.id OR k.parent_collection_id = c.id)                          AS notes_in_tree
FROM note_collections c
WHERE c.owner_user_id = 'dee4225c-e460-4f89-a6e5-cd43f6dd1972'
  AND c.parent_collection_id IS NULL
ORDER BY notes_in_tree DESC;

-- ---------------------------------------------------------------------------
-- Q1. ARCHITECTURE TODAY — the full tree, note by note. This is "current shape".
-- ---------------------------------------------------------------------------
WITH RECURSIVE tree AS (
    SELECT id, title, parent_collection_id, 0 AS depth
    FROM note_collections
    WHERE id = 'b0db3648-c520-40a5-8e0b-f8ebfcdef102'
    UNION ALL
    SELECT c.id, c.title, c.parent_collection_id, t.depth + 1
    FROM note_collections c JOIN tree t ON c.parent_collection_id = t.id
)
SELECT t.title              AS subject_plan,
       i.label              AS section,
       i.position,
       n.subject            AS note_subject,
       n.title              AS note_title,
       coalesce(n.domain_context, '(none)') AS domain_context,
       n.learner_level
FROM tree t
JOIN note_collection_items i ON i.collection_id = t.id
JOIN notes n                 ON n.id = i.note_id
ORDER BY t.title, i.position;

-- ---------------------------------------------------------------------------
-- Q2. ARCHITECTURE TODAY — compact summary, so the strategist sees the skeleton
-- without reading every title.
-- ---------------------------------------------------------------------------
WITH RECURSIVE tree AS (
    SELECT id, title, parent_collection_id FROM note_collections
    WHERE id = 'b0db3648-c520-40a5-8e0b-f8ebfcdef102'
    UNION ALL
    SELECT c.id, c.title, c.parent_collection_id
    FROM note_collections c JOIN tree t ON c.parent_collection_id = t.id
)
SELECT t.title AS subject_plan,
       coalesce(i.label, '(no section)') AS section,
       count(*)                          AS notes,
       string_agg(DISTINCT n.subject, ', ' ORDER BY n.subject) AS subjects
FROM tree t
JOIN note_collection_items i ON i.collection_id = t.id
JOIN notes n                 ON n.id = i.note_id
GROUP BY t.title, i.label
ORDER BY t.title, section;

-- ---------------------------------------------------------------------------
-- Q3. CIVIL ENGINEERING — the benchmark shape, summary level.
-- This is what "comprehensive" looks like. The root resolves itself from Q0's title match.
-- ---------------------------------------------------------------------------
WITH RECURSIVE tree AS (
    -- Civil Engineering root, resolved by title. Q0 must show exactly one match.
    SELECT id, title, parent_collection_id FROM note_collections
    WHERE owner_user_id = 'dee4225c-e460-4f89-a6e5-cd43f6dd1972'
      AND parent_collection_id IS NULL
      AND title ILIKE '%civil%'
    UNION ALL
    SELECT c.id, c.title, c.parent_collection_id
    FROM note_collections c JOIN tree t ON c.parent_collection_id = t.id
)
SELECT t.title AS subject_plan,
       coalesce(i.label, '(no section)') AS section,
       count(*)                          AS notes,
       string_agg(DISTINCT n.subject, ', ' ORDER BY n.subject) AS subjects
FROM tree t
JOIN note_collection_items i ON i.collection_id = t.id
JOIN notes n                 ON n.id = i.note_id
GROUP BY t.title, i.label
ORDER BY t.title, section;

-- ---------------------------------------------------------------------------
-- Q4. ⚠️ THE READY-TO-ADD POOL — the most decision-relevant query.
-- Curator notes already marked applicable to Architecture (or Architectural Engineering)
-- that are NOT yet in the Architecture Review Set. These need no authoring — only placement.
--
-- ⚠️ CORRECTED 2026-08-29. The first version joined `note_course_program` and used count(*),
-- so a note tagged with BOTH 'Architecture' AND 'Architectural Engineering' was counted TWICE
-- and its title appeared twice in the list. Verified against Q5: Surveying read 28 here but 14
-- in Q5; Construction Materials and Testing read 21 here but 13 in Q5. The note is now
-- de-duplicated before counting, so `available_notes` is a true note count.
-- ---------------------------------------------------------------------------
WITH RECURSIVE tree AS (
    SELECT id, parent_collection_id FROM note_collections
    WHERE id = 'b0db3648-c520-40a5-8e0b-f8ebfcdef102'
    UNION ALL
    SELECT c.id, c.parent_collection_id
    FROM note_collections c JOIN tree t ON c.parent_collection_id = t.id
), in_set AS (
    SELECT DISTINCT i.note_id FROM note_collection_items i JOIN tree t ON t.id = i.collection_id
), candidate AS (
    -- one row per NOTE, regardless of how many of the two programs it carries
    SELECT DISTINCT n.id, n.subject, n.title
    FROM notes n
    WHERE n.owner_user_id = 'dee4225c-e460-4f89-a6e5-cd43f6dd1972'
      AND n.id NOT IN (SELECT note_id FROM in_set)
      AND EXISTS (
          SELECT 1 FROM note_course_program ncp
            JOIN course_programs cp ON cp.id = ncp.course_program_id
           WHERE ncp.note_id = n.id
             AND cp.name IN ('Architecture', 'Architectural Engineering')
      )
)
SELECT subject,
       count(*)                                    AS available_notes,
       string_agg(title, ' | ' ORDER BY title)     AS titles
FROM candidate
GROUP BY subject
ORDER BY available_notes DESC, subject;

-- ---------------------------------------------------------------------------
-- Q5. THE OVERLAP QUESTION — subjects in the CE Review Set, and whether that
-- material is already Architecture-applicable.
--
-- Read as: "CE teaches this; is it tagged for Architecture, and is it in the Archi set?"
-- `arch_tagged = 0` with a high `ce_notes` means the material exists but nobody has decided
-- it applies to Architecture — that is an Applicable Programs decision, not an authoring one.
-- ---------------------------------------------------------------------------
WITH RECURSIVE ce_tree AS (
    -- Civil Engineering root, resolved by title. Q0 must show exactly one match.
    SELECT id, parent_collection_id FROM note_collections
    WHERE owner_user_id = 'dee4225c-e460-4f89-a6e5-cd43f6dd1972'
      AND parent_collection_id IS NULL
      AND title ILIKE '%civil%'
    UNION ALL
    SELECT c.id, c.parent_collection_id FROM note_collections c JOIN ce_tree t ON c.parent_collection_id = t.id
), arch_tree AS (
    SELECT id, parent_collection_id FROM note_collections WHERE id = 'b0db3648-c520-40a5-8e0b-f8ebfcdef102'
    UNION ALL
    SELECT c.id, c.parent_collection_id FROM note_collections c JOIN arch_tree t ON c.parent_collection_id = t.id
), ce_notes AS (
    SELECT DISTINCT n.id, n.subject
    FROM note_collection_items i JOIN ce_tree t ON t.id = i.collection_id JOIN notes n ON n.id = i.note_id
), arch_in_set AS (
    SELECT DISTINCT i.note_id FROM note_collection_items i JOIN arch_tree t ON t.id = i.collection_id
)
SELECT cn.subject,
       count(*)                                                        AS ce_notes,
       count(*) FILTER (WHERE EXISTS (
           SELECT 1 FROM note_course_program x JOIN course_programs c2 ON c2.id = x.course_program_id
            WHERE x.note_id = cn.id AND c2.name IN ('Architecture', 'Architectural Engineering')
       ))                                                              AS arch_tagged,
       count(*) FILTER (WHERE cn.id IN (SELECT note_id FROM arch_in_set)) AS already_in_arch_set
FROM ce_notes cn
GROUP BY cn.subject
ORDER BY ce_notes DESC;

-- ---------------------------------------------------------------------------
-- Q6. Catalog sanity — the exact program names, so the strategist proposes real ones.
-- ---------------------------------------------------------------------------
SELECT cp.name,
       count(ncp.note_id) AS notes_tagged
FROM course_programs cp
LEFT JOIN note_course_program ncp ON ncp.course_program_id = cp.id
GROUP BY cp.name
ORDER BY notes_tagged DESC, cp.name;
