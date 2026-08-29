-- Construction subjects — delete + re-generate under the corrected Domain Context.
--
-- ⚠️ SCOPE CORRECTED 2026-08-29 AFTER THE STEP 1 READ — 'Construction Materials' IS NOT
-- AFFECTED AND HAS BEEN REMOVED FROM EVERY STATEMENT BELOW. It carries domain_context =
-- NULL (not PROFESSIONAL_PRACTICE_AND_REGULATION), learner_level BOARD_EXAM_REVIEW (not
-- COLLEGE) and was authored 2026-05-24 — a different batch three months before the mistake.
-- With a NULL domain its authoring domain fell back to the course program, so it was never
-- mis-calibrated. Deleting its 11 notes would be pure loss. Do not re-add it.
--
-- WHY. Three subjects in the Civil Engineering Review Set were bulk-generated with
-- domain_context = PROFESSIONAL_PRACTICE_AND_REGULATION when ENGINEERING_SCIENCES was
-- intended. The wrong value reached BOTH generation stages — the note body
-- (note-generation-developer.txt) and the Study Pack (developer.txt) — so per-note Study Pack
-- regeneration cannot fix it: it would re-read the wrong-domain note body. Delete + re-run
-- bulk generation is the clean repair, and it fixes body, title, tags and quiz together.
--
-- ⚠️ RUN STEPS 0-3 AND SAVE THEIR OUTPUT BEFORE STEP 4. Step 4 is irreversible.
--
-- ⚠️ TRAP 1 — THE TOPIC LIST IS NOT STORED ANYWHERE. `bulk_generation_result` keeps only
-- `failed_topics` and `quota_blocked_topics` (V73); topics that generated successfully are
-- never persisted, and the note's original typed topic was overwritten by the Study Pack
-- write-back (StudyPackService.applyBulkGeneratedMetadataToNote). **The current note titles
-- are the only surviving proxy for your topic list.** Step 2 exports them. Save that output
-- OUTSIDE the database before running step 4.
--
-- ⚠️ TRAP 2 — DELETING notes does NOT delete the Study Pack. `fk_study_packs_note_id` is
-- ON DELETE SET NULL (V17:14), not CASCADE. Packs first, notes second — the order
-- NoteService.deleteById:471-473 uses in Java.
--
-- ⚠️ TRAP 3 — `notes.subject` is free text and IS copied onto learner rows
-- (NoteService:350; NoteCollectionService.adopt). Every statement below is scoped to the
-- curator's user id. Do not remove that predicate.
--
-- ⚠️ TRAP 4 — Study Plan membership CASCADES away (`note_collection_items`, V72:16).
-- Step 3 captures which plan each note sits in, at which position, under which section
-- label, so the plan can be rebuilt after regeneration.

-- ---------------------------------------------------------------------------
-- STEP 0. Curator user id. Confirm one row; use it as <CURATOR_UUID> below.
-- ---------------------------------------------------------------------------
SELECT id, email, role FROM users WHERE role = 'ADMIN';

-- ---------------------------------------------------------------------------
-- STEP 1. Confirm the target set and its current classification before touching it.
-- Check that domain_context really is PROFESSIONAL_PRACTICE_AND_REGULATION on these
-- rows — if some are already correct, they do not need regenerating.
-- ---------------------------------------------------------------------------
SELECT n.subject,
       coalesce(n.domain_context, '(none)') AS domain_context,
       n.learner_level,
       count(*)                              AS notes,
       count(sp.id)                          AS with_study_pack,
       min(n.created_at)::date               AS first_authored
FROM notes n
LEFT JOIN study_packs sp ON sp.note_id = n.id
WHERE n.owner_user_id = 'dee4225c-e460-4f89-a6e5-cd43f6dd1972'
  AND n.subject IN (
      'Construction Management',
      'Construction Scheduling',
      'Construction Cost Engineering'
  )
GROUP BY n.subject, n.domain_context, n.learner_level
ORDER BY n.subject;

-- ---------------------------------------------------------------------------
-- STEP 2. ⚠️ THE REGENERATION INPUT — EXPORT AND SAVE THIS BEFORE STEP 4.
-- These titles are your topic list for the new bulk run (one batch per subject,
-- since bulk generation applies ONE subject to the whole batch).
--
-- Note the titles are LLM-written, not your original typed topics, and they were written
-- under the wrong domain. Read them as you paste — this is also the moment to apply the
-- knowledge-first title rule (drop "… in Civil Engineering"-style suffixes) since these
-- topics seed brand-new notes.
-- ---------------------------------------------------------------------------
SELECT n.subject,
       n.title,
       n.visibility,
       n.learner_level
FROM notes n
WHERE n.owner_user_id = 'dee4225c-e460-4f89-a6e5-cd43f6dd1972'
  AND n.subject IN (
      'Construction Management',
      'Construction Scheduling',
      'Construction Cost Engineering'
  )
ORDER BY n.subject, n.title;

-- ---------------------------------------------------------------------------
-- STEP 3. ⚠️ ALSO EXPORT — Study Plan placement, so the plan can be rebuilt.
-- `label` is the section name; `position` is the order within the plan. Both are lost
-- on delete. `times_adopted` > 0 means learners hold copies that will NOT be updated
-- (NoteCollectionService.adopt returns early on an existing source_plan_id).
-- ---------------------------------------------------------------------------
SELECT c.title                                   AS plan_title,
       c.id                                      AS collection_id,
       i.label                                   AS section_label,
       i.position,
       n.subject,
       n.title,
       (SELECT count(*) FROM note_collections a WHERE a.source_plan_id = c.id) AS times_adopted
FROM note_collection_items i
JOIN note_collections c ON c.id = i.collection_id
JOIN notes n            ON n.id = i.note_id
WHERE n.owner_user_id = 'dee4225c-e460-4f89-a6e5-cd43f6dd1972'
  AND n.subject IN (
      'Construction Management',
      'Construction Scheduling',
      'Construction Cost Engineering'
  )
ORDER BY c.title, i.position;

-- ---------------------------------------------------------------------------
-- STEP 4. ⚠️ DESTRUCTIVE AND IRREVERSIBLE. Run only after steps 2 and 3 are saved.
-- Packs first (trap 2), owner-scoped (trap 3), one transaction. Inspect the row counts
-- against step 1 before COMMIT; ROLLBACK if they disagree.
-- ---------------------------------------------------------------------------
BEGIN;

DELETE FROM study_packs
WHERE owner_user_id = 'dee4225c-e460-4f89-a6e5-cd43f6dd1972'
  AND note_id IN (
      SELECT id FROM notes
      WHERE owner_user_id = 'dee4225c-e460-4f89-a6e5-cd43f6dd1972'
        AND subject IN (
            'Construction Management',
            'Construction Scheduling',
            'Construction Cost Engineering'
        )
  );

DELETE FROM notes
WHERE owner_user_id = 'dee4225c-e460-4f89-a6e5-cd43f6dd1972'
  AND subject IN (
      'Construction Management',
      'Construction Scheduling',
      'Construction Cost Engineering'
  );

-- Must return 0. Non-zero means a pack outlived its note (trap 2).
SELECT count(*) AS orphaned_packs
FROM study_packs
WHERE owner_user_id = 'dee4225c-e460-4f89-a6e5-cd43f6dd1972' AND note_id IS NULL;

-- Must return 0 rows.
SELECT subject, count(*) FROM notes
WHERE owner_user_id = 'dee4225c-e460-4f89-a6e5-cd43f6dd1972'
  AND subject IN (
      'Construction Management',
      'Construction Scheduling',
      'Construction Cost Engineering'
  )
GROUP BY subject;

COMMIT;
-- ROLLBACK;

-- ---------------------------------------------------------------------------
-- AFTER — what the 2026-08-29 read says the rebuild actually costs.
--
-- 23 notes across three batches (Cost Engineering 8, Management 7, Scheduling 8), one
-- bulk batch per subject because a batch applies ONE subject to every topic in it.
-- Each note spends one Study Pack generation against the monthly quota.
--
-- PLAN REBUILD — only 15 of the 23 need re-adding, and all to one plan:
--   Plan `🏢 Construction Engineering and Management` (d0d95568-4cfd-486c-b07d-c0a21c479973)
--     section "Construction Management" — positions 0-6   (7 notes)
--     section "Construction Scheduling"  — positions 7-14  (8 notes)
--   The 8 Construction Cost Engineering notes are in NO plan today, so they cost nothing
--   to rebuild — add them wherever they belong when convenient.
--
-- times_adopted was 0 on every row, so no learner holds copies that would be stranded.
--
-- Nothing else needs cleaning: the old exam pools and Challenge bank rows are deleted by
-- cascade with their Study Packs.
-- ---------------------------------------------------------------------------
