-- Review Set reshape — the read pack for the Note Strategist.
--
-- ⚠️ THIS IS A TEMPLATE. Replace the four values in the PARAMETERS block below, then run the
-- whole file. Everything else is generic — it works for any Review Set.
--
-- PURPOSE. Produce everything a strategist needs to propose a NEW shape for a Review Set: what
-- the target set has today, what a comprehensive set looks like as the benchmark, what material
-- already exists and is applicable to the target program but unused, and where the gaps are.
--
-- ⚠️ EVERY QUERY IS READ-ONLY. No writes, no DDL. Safe to run against production.
-- No psql meta-commands, so it pastes into DBeaver / JetBrains / pgAdmin as-is.
--
-- =====================  PARAMETERS — find and replace these four  =====================
--
--   <CURATOR_ID>        the owning curator's user id.
--                       ⚠️ CHICKEN-AND-EGG: Q0 filters on it, so if you do not know it yet, run
--                       Q0 once with the whole predicate line deleted, read the id off the owning
--                       row, then put it back. Do NOT leave Q0 unfiltered for the other queries.
--
--   <TARGET_ROOT_ID>    the Review Set being reshaped (root note_collections row).
--                       Q0 lists the candidates with their ids.
--
--   <BENCHMARK_MATCH>   a title fragment matching ONE root set to use as the depth benchmark.
--                       'civil' is the usual choice — it is the deepest set built.
--
--   <TARGET_PROGRAMS>   the catalog program name(s) the target set serves, as a SQL list.
--                       LET  = 'Education', 'Special Needs Education – Generalist'
--                       ALE  = 'Architecture', 'Architectural Engineering'
--                       ⚠️ Run Q6 FIRST if unsure — the names must match the catalog exactly,
--                       including the en dash in 'Senior High – STEM'-style values.
--
--   KNOWN VALUES, for reference only — these are a lookup table, NOT defaults. Nothing below
--   reads them; every query uses the placeholders above.
--     ALE  root = b0db3648-c520-40a5-8e0b-f8ebfcdef102   programs = 'Architecture', 'Architectural Engineering'
--     LET  root = d84dcf18-f4aa-409c-91e9-36c02d6c7580   programs = 'Education', 'Special Needs Education – Generalist'
--
-- ⚠️ THIS FILE MUST STAY PROGRAM-AGNOSTIC. It shipped once half-templated — three queries kept a
-- hardcoded root id while others used the placeholder — so a find-and-replace for a DIFFERENT set
-- left those three silently reading the old one, mixing two trees with no error. That is the exact
-- failure Q0(b) warns about, and it survived verification because the pack was validated against
-- the very set whose id was baked in. If you add a query, use the placeholders.
--
-- ======================================================================================
--
-- ⚠️ ADDING A NOTE TO A REVIEW SET DOES NOT COPY IT. `note_collection_items` is a join, so one
-- canonical note can sit in several Review Sets at once. The strategist should propose REUSE,
-- not duplication — duplicating shared knowledge per program is the exact failure the
-- Applicable Programs axis exists to prevent.
--
-- ⚠️ MORE THAN ONE PROGRAM MAY BE RELEVANT. Architecture needed both `Architecture` and
-- `Architectural Engineering`; Education may need `Special Needs Education – Generalist`
-- alongside `Education`. Deciding whether those are one audience or two is a strategist
-- question, so Q4 and Q5 take the whole list.
--
-- ⚠️ STRUCTURE. A Review Set is a root `note_collections` row (`parent_collection_id IS NULL`).
-- Its children are subject plans. Notes hang off the LEAF collections via `note_collection_items`,
-- where `label` is the section name and `position` is the order. There is no "kind" column —
-- depth is the only thing distinguishing a Review Set from a subject plan.

-- ---------------------------------------------------------------------------
-- Q0. Identify the review sets, and confirm two things before trusting anything below:
--   (a) <TARGET_ROOT_ID> is the set you mean, and
--   (b) EXACTLY ONE root matches <BENCHMARK_MATCH>. Q3 and Q5 resolve the benchmark by that
--       title predicate, so two matches would silently union both trees and inflate every count.
-- Q0 also shows the owning user id for <CURATOR_ID>.
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
WHERE c.owner_user_id = '<CURATOR_ID>'
  AND c.parent_collection_id IS NULL
ORDER BY notes_in_tree DESC;

-- ---------------------------------------------------------------------------
-- Q1. THE TARGET SET TODAY — the full tree, note by note. This is "current shape".
-- ---------------------------------------------------------------------------
WITH RECURSIVE tree AS (
    SELECT id, title, parent_collection_id, 0 AS depth
    FROM note_collections
    WHERE id = '<TARGET_ROOT_ID>'
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
-- Q2. THE TARGET SET TODAY — compact summary, so the strategist sees the skeleton
-- without reading every title.
-- ---------------------------------------------------------------------------
WITH RECURSIVE tree AS (
    SELECT id, title, parent_collection_id FROM note_collections
    WHERE id = '<TARGET_ROOT_ID>'
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
-- Q3. THE BENCHMARK SET — the comprehensive shape, summary level.
-- This is what "comprehensive" looks like. The root resolves itself from Q0's title match.
-- ---------------------------------------------------------------------------
WITH RECURSIVE tree AS (
    -- Benchmark root, resolved by title. Q0 must show exactly one match.
    SELECT id, title, parent_collection_id FROM note_collections
    WHERE owner_user_id = '<CURATOR_ID>'
      AND parent_collection_id IS NULL
      AND title ILIKE '%<BENCHMARK_MATCH>%'
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
-- Curator notes already marked applicable to the target program(s) that are NOT yet in the
-- target Review Set. These need no authoring — only placement.
--
-- ⚠️ CORRECTED 2026-08-29. The first version joined `note_course_program` and used count(*),
-- so a note tagged with TWO of the target programs was counted TWICE
-- and its title appeared twice in the list. Verified against Q5: Surveying read 28 here but 14
-- in Q5; Construction Materials and Testing read 21 here but 13 in Q5. The note is now
-- de-duplicated before counting, so `available_notes` is a true note count.
-- ---------------------------------------------------------------------------
WITH RECURSIVE tree AS (
    SELECT id, parent_collection_id FROM note_collections
    WHERE id = '<TARGET_ROOT_ID>'
    UNION ALL
    SELECT c.id, c.parent_collection_id
    FROM note_collections c JOIN tree t ON c.parent_collection_id = t.id
), in_set AS (
    SELECT DISTINCT i.note_id FROM note_collection_items i JOIN tree t ON t.id = i.collection_id
), candidate AS (
    -- one row per NOTE, regardless of how many of the two programs it carries
    SELECT DISTINCT n.id, n.subject, n.title
    FROM notes n
    WHERE n.owner_user_id = '<CURATOR_ID>'
      AND n.id NOT IN (SELECT note_id FROM in_set)
      AND EXISTS (
          SELECT 1 FROM note_course_program ncp
            JOIN course_programs cp ON cp.id = ncp.course_program_id
           WHERE ncp.note_id = n.id
             AND cp.name IN (<TARGET_PROGRAMS>)
      )
)
SELECT subject,
       count(*)                                    AS available_notes,
       string_agg(title, ' | ' ORDER BY title)     AS titles
FROM candidate
GROUP BY subject
ORDER BY available_notes DESC, subject;

-- ---------------------------------------------------------------------------
-- Q5. THE OVERLAP QUESTION — subjects in the BENCHMARK set, and whether that material is
-- already applicable to the target program(s).
--
-- Read as: "CE teaches this; is it tagged for Architecture, and is it in the Archi set?"
-- `arch_tagged = 0` with a high `ce_notes` means the material exists but nobody has decided
-- it applies to Architecture — that is an Applicable Programs decision, not an authoring one.
-- ---------------------------------------------------------------------------
WITH RECURSIVE ce_tree AS (
    -- Benchmark root, resolved by title. Q0 must show exactly one match.
    SELECT id, parent_collection_id FROM note_collections
    WHERE owner_user_id = '<CURATOR_ID>'
      AND parent_collection_id IS NULL
      AND title ILIKE '%<BENCHMARK_MATCH>%'
    UNION ALL
    SELECT c.id, c.parent_collection_id FROM note_collections c JOIN ce_tree t ON c.parent_collection_id = t.id
), arch_tree AS (
    SELECT id, parent_collection_id FROM note_collections WHERE id = '<TARGET_ROOT_ID>'
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
            WHERE x.note_id = cn.id AND c2.name IN (<TARGET_PROGRAMS>)
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

-- ---------------------------------------------------------------------------
-- Q7. RECONCILIATION — fill in a proposed shape's `status` column.
--
-- WHEN: a strategist has proposed a target shape for a set whose notes ALREADY EXIST (a
-- reshape rather than a build). Their rows land as `status = Unmapped`, which is honest but
-- not actionable. This produces the list to match them against.
--
-- ⚠️ Match on KNOWLEDGE, not on string equality. A strategist proposes knowledge-first titles
-- ("Normal Stress"); existing notes often carry the older suffixed form ("Normal Stress in
-- Strength of Materials"). Those are the SAME note and the existing one should be reused —
-- with its title normalised on touch, per the canonical title policy. A string join would
-- score them as different and manufacture hundreds of phantom "New" rows.
--
-- Set the collection id, then match subject block by subject block.
-- ---------------------------------------------------------------------------
SELECT n.subject,
       count(*)                                     AS existing_notes,
       string_agg(n.title, ' | ' ORDER BY n.title)  AS existing_titles
FROM note_collection_items i
JOIN notes n ON n.id = i.note_id
WHERE i.collection_id IN (
        WITH RECURSIVE t AS (
            SELECT id, parent_collection_id FROM note_collections WHERE id = '<TARGET_ROOT_ID>'
            UNION ALL
            SELECT c.id, c.parent_collection_id FROM note_collections c JOIN t ON c.parent_collection_id = t.id
        ) SELECT id FROM t)
GROUP BY n.subject
ORDER BY existing_notes DESC, n.subject;
