-- Hydraulics → Fluid Mechanics consolidation (curator catalog cleanup).
--
-- CONTEXT. Hydraulics was authored 2026-08-02 (12 notes, all Civil Engineering). A more
-- comprehensive Fluid Mechanics set was authored 2026-08-29 (12 notes) beside one older
-- 2026-05-24 note. Coverage was compared title by title: 10 of the 12 Hydraulics notes are
-- now duplicated, 2 are NOT.
--
-- ⚠️ FINDING A — FIX BEFORE DELETING ANYTHING. The 12 new Fluid Mechanics notes carry NO
-- course/program; the Hydraulics notes they replace all carry Civil Engineering.
-- NoteService.publicLibraryPrograms falls back to `courseProgram` only when
-- `applicablePrograms` is empty, and returns List.of() when both are — a note with neither
-- matches NO program filter at all. So deleting Hydraulics before backfilling the programs
-- would remove this entire topic area from Civil Engineering discovery. Step 2 fixes it.
--
-- ⚠️ FINDING B — THE 2 UNCOVERED NOTES ARE NOT OVERSIGHTS, THEY ARE THE REAL HYDRAULICS.
--   • Fundamentals of Open Channel Flow in Hydraulics
--   • Fundamentals of Hydraulic Structures in Civil Engineering
-- Neither has a Fluid Mechanics counterpart, and in a CE board curriculum neither belongs
-- in one: Fluid Mechanics carries the fundamentals, Hydraulics carries open channel flow,
-- hydraulic structures and water systems. So Hydraulics is not deleted as a subject — it
-- sheds the 10 duplicated fundamentals and keeps its own topics. Do not re-subject these two.
--
-- STEPS 0–1 and 6 ARE READ-ONLY. STEPS 2–5 WRITE. No psql meta-commands.
--
-- TRAP 1 — `notes.subject` is free text (V11:5) and IS copied onto learner rows
-- (NoteService:350; NoteCollectionService.adopt copies source notes). Every statement is
-- scoped to the curator id. Do not remove that predicate.
--
-- TRAP 2 — deleting `notes` does NOT remove the Study Pack. `fk_study_packs_note_id` is
-- ON DELETE SET NULL (V17:14), not CASCADE. Packs first, notes second — exactly what
-- NoteService.deleteById:471-473 does in Java.

-- ---------------------------------------------------------------------------
-- STEP 0. Curator user id. Confirm one row; use it as <CURATOR_UUID> below.
-- ---------------------------------------------------------------------------
SELECT id, email, role FROM users WHERE role = 'ADMIN';

-- ---------------------------------------------------------------------------
-- STEP 1. Confirm finding A before acting on it. `applicable_programs` is the
-- note_course_program join; the pasted blank column was notes.course_program only.
-- A row here with programs = 0 AND course_program NULL is invisible to every
-- course/program filter on the Public Library.
-- ---------------------------------------------------------------------------
SELECT n.subject,
       n.title,
       n.course_program,
       count(ncp.id) AS applicable_programs
FROM notes n
LEFT JOIN note_course_program ncp ON ncp.note_id = n.id
WHERE n.owner_user_id = 'dee4225c-e460-4f89-a6e5-cd43f6dd1972'
  AND n.subject IN ('Fluid Mechanics', 'Hydraulics')
GROUP BY n.subject, n.title, n.course_program
ORDER BY n.subject, n.title;

-- ---------------------------------------------------------------------------
-- STEP 2. ⚠️ WRITES. Give the Fluid Mechanics replacements the program their
-- predecessors carried, so the content stays reachable under Civil Engineering.
-- Run this BEFORE step 4. Both halves are needed: the join drives the filter, the
-- scalar is the fallback and what the authoring UI shows.
-- ---------------------------------------------------------------------------
BEGIN;

UPDATE notes
SET course_program = 'Civil Engineering',
    updated_at = now()
WHERE owner_user_id = 'dee4225c-e460-4f89-a6e5-cd43f6dd1972'
  AND subject = 'Fluid Mechanics'
  AND course_program IS NULL;

INSERT INTO note_course_program (id, note_id, course_program_id)
SELECT gen_random_uuid(), n.id, cp.id
FROM notes n
JOIN course_programs cp ON cp.name = 'Civil Engineering'
WHERE n.owner_user_id = 'dee4225c-e460-4f89-a6e5-cd43f6dd1972'
  AND n.subject = 'Fluid Mechanics'
ON CONFLICT (note_id, course_program_id) DO NOTHING;

-- Expect 0 rows: every Fluid Mechanics note now reachable under a program filter.
SELECT count(*) AS unreachable_fluid_notes
FROM notes n
WHERE n.owner_user_id = '<CURATOR_UUID>'
  AND n.subject = 'Fluid Mechanics'
  AND n.course_program IS NULL
  AND NOT EXISTS (SELECT 1 FROM note_course_program x WHERE x.note_id = n.id);

-- COMMIT;
-- ROLLBACK;

-- ---------------------------------------------------------------------------
-- STEP 3. ⚠️ WRITES, FULLY REVERSIBLE. Hide the 10 confirmed duplicates. They leave
-- Explore and the public filters immediately; every row survives for a rollback window.
-- Live with this for a week, then run step 4. If something turns out to be missing,
-- flipping visibility back is the whole undo.
--
-- COVERAGE MAP (Hydraulics note → Fluid Mechanics replacement):
--   Fundamental Fluid Properties in Hydraulics            → Fundamental Properties of Fluids in Fluid Mechanics
--   Fundamentals of Hydrostatics in Civil Engineering     → Fundamentals of Hydrostatics in Fluid Mechanics
--   Fundamentals of Fluid Kinematics in Hydraulics        → Fundamentals of Fluid Kinematics
--   Fundamentals of Fluid Dynamics in Civil Eng. Hydr.    → Fundamental Principles and Equations of Fluid Dynamics
--   Bernoulli Equation in Fluid Mechanics  [exact title]  → Bernoulli Equation in Fluid Mechanics
--   Fundamentals of Pipe Flow in Hydraulic Engineering    → Fundamentals and Analysis of Pipe Flow in Fluid Mechanics
--   Hydraulic Energy and Head Losses in Fluid Flow        → Analysis of Major and Minor Head Losses in Fluid Flow
--                                                           (+ Energy Equation in Fluid Mechanics)
--   Dimensional Analysis in Hydraulics Engineering        → Dimensional Analysis in Fluid Mechanics
--   Fundamentals and Types of Pumps in Hydraulic Eng.     → Fundamentals and Performance Characteristics of Pumps…
--   Hydraulic Turbines in Civil Engineering               → Fundamentals of Turbines in Fluid Mechanics
--
-- Fluid Mechanics additionally adds Continuity Equation and Energy Equation, which
-- Hydraulics never had — the replacement set is a strict superset of the overlap.
-- ---------------------------------------------------------------------------
UPDATE notes
SET visibility = 'PRIVATE',
    updated_at = now()
WHERE owner_user_id = 'dee4225c-e460-4f89-a6e5-cd43f6dd1972'
  AND subject = 'Hydraulics'
  AND title IN (
      'Fundamental Fluid Properties in Hydraulics',
      'Fundamentals of Hydrostatics in Civil Engineering',
      'Fundamentals of Fluid Kinematics in Hydraulics',
      'Fundamentals of Fluid Dynamics in Civil Engineering Hydraulics',
      'Bernoulli Equation in Fluid Mechanics',
      'Fundamentals of Pipe Flow in Hydraulic Engineering',
      'Hydraulic Energy and Head Losses in Fluid Flow',
      'Dimensional Analysis in Hydraulics Engineering',
      'Fundamentals and Types of Pumps in Hydraulic Engineering',
      'Hydraulic Turbines in Civil Engineering'
  );
-- Expect exactly 10 rows updated. Fewer means a title has drifted — stop and re-read step 1.

-- ---------------------------------------------------------------------------
-- STEP 4. ⚠️ DESTRUCTIVE. The same 10, deleted. Packs first (trap 2), owner-scoped
-- (trap 1), one transaction. The two keepers from finding B are excluded by title.
-- ---------------------------------------------------------------------------
BEGIN;

DELETE FROM study_packs
WHERE owner_user_id = '<CURATOR_UUID>'
  AND note_id IN (
      SELECT id FROM notes
      WHERE owner_user_id = '<CURATOR_UUID>'
        AND subject = 'Hydraulics'
        AND title NOT IN (
            'Fundamentals of Open Channel Flow in Hydraulics',
            'Fundamentals of Hydraulic Structures in Civil Engineering'
        )
  );

DELETE FROM notes
WHERE owner_user_id = '<CURATOR_UUID>'
  AND subject = 'Hydraulics'
  AND title NOT IN (
      'Fundamentals of Open Channel Flow in Hydraulics',
      'Fundamentals of Hydraulic Structures in Civil Engineering'
  );

-- Must return 0. Non-zero means a pack outlived its note (trap 2).
SELECT count(*) AS orphaned_packs
FROM study_packs
WHERE owner_user_id = '<CURATOR_UUID>' AND note_id IS NULL;

-- Must return exactly the 2 keepers.
SELECT title FROM notes
WHERE owner_user_id = '<CURATOR_UUID>' AND subject = 'Hydraulics' ORDER BY title;

-- COMMIT;
-- ROLLBACK;

-- ---------------------------------------------------------------------------
-- STEP 5. ⚠️ SEPARATE DECISION — a third duplicate, inside Fluid Mechanics.
-- 'Fluid Mechanics: Pressure, Flow Dynamics, and Bernoulli's Principle' (2026-05-24) is
-- an orphan from an earlier effort: it spans hydrostatics + fluid dynamics + Bernoulli,
-- all three now covered by dedicated notes, and it is the only Fluid Mechanics note
-- carrying a program — Mechanical Engineering, not Civil. Step 2 does not touch it
-- (its course_program is not NULL), so it stays a Mechanical Engineering note unless
-- decided otherwise. Read it before choosing; it is not part of the 10.
-- ---------------------------------------------------------------------------
SELECT n.id, n.title, n.course_program, n.visibility, n.created_at::date,
       length(sp.summary) AS summary_len
FROM notes n
LEFT JOIN study_packs sp ON sp.note_id = n.id
WHERE n.owner_user_id = '<CURATOR_UUID>'
  AND n.subject = 'Fluid Mechanics'
  AND n.created_at < '2026-08-01';

-- ---------------------------------------------------------------------------
-- STEP 6. Plan membership and adoption exposure. Run before step 4.
--
-- ⚠️ note_collection_items is ON DELETE CASCADE (V72:16): deleting a note silently drops
-- it from every Study Plan holding it. Read the two counts together — a plan already
-- holding the Fluid Mechanics replacements loses only clutter; a plan with hydraulics
-- items and no fluid items loses real coverage, so add the replacements there first.
-- ⚠️ NoteCollectionService.adopt() returns early on an existing source_plan_id, so anyone
-- who already adopted a plan listed here keeps the Hydraulics copies permanently and can
-- never receive the replacements. Finish this cleanup before promoting the CE plan.
-- ---------------------------------------------------------------------------
SELECT c.id     AS collection_id,
       c.title  AS plan_title,
       count(*) FILTER (WHERE n.subject = 'Hydraulics')      AS hydraulics_items,
       count(*) FILTER (WHERE n.subject = 'Fluid Mechanics') AS fluid_mechanics_items,
       (SELECT count(*) FROM note_collections a WHERE a.source_plan_id = c.id) AS times_adopted
FROM note_collection_items i
JOIN note_collections c ON c.id = i.collection_id
JOIN notes n ON n.id = i.note_id
WHERE n.owner_user_id = '<CURATOR_UUID>'
  AND n.subject IN ('Hydraulics', 'Fluid Mechanics')
GROUP BY c.id, c.title
HAVING count(*) FILTER (WHERE n.subject = 'Hydraulics') > 0
ORDER BY hydraulics_items DESC;
