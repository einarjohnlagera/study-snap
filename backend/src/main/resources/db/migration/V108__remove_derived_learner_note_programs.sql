-- V108 -- remove the note_course_program rows V107 mechanically derived from learner-authored notes.
--
-- RATIFIED DOCTRINE (owner ruling 2026-08-06, recorded in ADR-001 -> "Representation authority: what may
-- author an Applicable Program row"), quoted verbatim:
--
--   "A learner's personal free-text Course / Program must not be mechanically materialized into a catalog
--    Applicable Program row."
--
-- This is deliberately NARROWER than "learner-owned notes cannot carry join rows", and that broader rule is
-- wrong: it would contradict copy inheritance and the standing ruling that ownership must not change the
-- semantics of the metadata model. Three canonical shapes must all survive this migration:
--
--   * Learner-authored personal note -- the program lives in notes.course_program; NO mechanically derived
--     join rows. These are the rows this migration removes.
--   * Curator-authored note          -- notes.course_program is null; one or more authored join rows. KEPT.
--   * Curated note copied by learner -- inherited join rows, authored metadata. KEPT.
--
-- Read semantics are identical for all three and never consult ownership: joined programs first when joined
-- programs exist, otherwise the personal program string. A learner note served by the personal-string
-- fallback is a canonical, fully supported shape -- not a degraded one.
--
-- WHY A NEW MIGRATION RATHER THAN EDITING V107.
-- V107 has already run in local and other non-production environments. Editing its SQL would change its
-- Flyway checksum and break startup there, and AGENTS.md holds migrations append-only. V108 therefore runs
-- immediately after V107 in the same deploy, closing the window before production ever sees the table.
--
-- WHY CONDITION 2 IS EXACT RATHER THAN A created_at HEURISTIC.
-- Join rows reach a learner-owned note through exactly one legitimate route: inheritance via
-- NoteService.copyNote, which copies the source note's rows onto the copy. Every other write is
-- curator-gated -- all four noteCourseProgramRepository.replace call sites are either the curator branch of
-- note create/update or the curator-gated NoteApplicableProgramsService -- and
-- NoteApplicableProgramsMaintenanceService was removed in slice 4. No runtime path derives join rows from
-- the personal string. So "not a copy" is not a proxy for provenance; it is provenance: a non-copy
-- learner-owned note has no legitimate route to a join row at all.
--
-- In production at V108 time the predicate is unambiguous anyway -- V107 has just run, the app has served no
-- traffic, and copy-inheritance of join rows is itself new in this release, so every row present came from
-- V107's backfill. Condition 2 earns its place in NON-PRODUCTION environments, where V107 already ran and the
-- app has served traffic: local dev holds ~8 genuinely inherited rows, and without condition 2 this migration
-- would destroy them.
--
-- ACCEPTED LIMITATION, documented rather than solved: an ADMIN can set programs on another user's note
-- through the shared Applicable Programs endpoint. If an admin curated a non-copy learner-owned note before
-- this migration runs, its rows are removed here. This cannot occur in production (no traffic between V107
-- and V108) and is vanishingly unlikely elsewhere. A `source` provenance column was considered and rejected
-- by the owner -- this rule keeps that population empty.
--
-- ORPHANED OWNER BEHAVIOUR: the owner test is an EXISTS against users, not an outer join, so a note whose
-- owner_user_id has no users row is NOT treated as learner-owned and its rows are KEPT. Deletion is
-- destructive, so absence of evidence must not authorize it. The case is unreachable in practice --
-- notes.owner_user_id is NOT NULL REFERENCES users(id) -- and the form is chosen to make the behaviour
-- explicit rather than incidental.
--
-- IDEMPOTENT BY CONSTRUCTION: the DELETE removes every row matching the predicate, so a second run matches
-- nothing and deletes nothing. A fresh database matches nothing and succeeds, deleting zero.
--
-- The DELETE is deliberately kept OUTSIDE the PL/pgSQL reporting block below. CourseProgramCatalogMigrationTest
-- strips a migration from the start of that block onward, because H2 cannot parse PL/pgSQL -- V107's own
-- backfill sits inside such a block and is untested for exactly that reason. Keeping the DELETE above the
-- block is what makes V108's test genuine. Do not move it.

DELETE FROM note_course_program
WHERE note_id IN (
    SELECT notes.id
    FROM notes
    WHERE notes.copied_from_note_id IS NULL
      AND notes.source_note_id IS NULL
      AND EXISTS (
          SELECT 1
          FROM users
          WHERE users.id = notes.owner_user_id
            AND users.role <> 'ADMIN'
            AND users.profile_type IS DISTINCT FROM 'TEACHER'
      )
);

DO $$
DECLARE
    remaining_rows INTEGER;
    residual_derived_notes INTEGER;
BEGIN
    -- Informational, never fatal. ROW_COUNT is not reported here on purpose: GET DIAGNOSTICS reflects the
    -- last statement executed INSIDE this block, and the DELETE above deliberately sits outside it, so a
    -- deleted-row count taken here would be wrong. The residual invariant is the stronger post-condition
    -- anyway -- residual_derived_notes must be 0, and any other value means the predicate did not hold.
    SELECT count(*) INTO remaining_rows FROM note_course_program;
    SELECT count(DISTINCT notes.id)
      INTO residual_derived_notes
      FROM notes
      JOIN note_course_program ON note_course_program.note_id = notes.id
     WHERE notes.copied_from_note_id IS NULL
       AND notes.source_note_id IS NULL
       AND EXISTS (
           SELECT 1
             FROM users
            WHERE users.id = notes.owner_user_id
              AND users.role <> 'ADMIN'
              AND users.profile_type IS DISTINCT FROM 'TEACHER'
       );

    RAISE NOTICE 'V108: % note_course_program rows remain (curator-authored and copy-inherited); % learner-owned non-copy notes still carry rows',
        remaining_rows, residual_derived_notes;

    IF residual_derived_notes > 0 THEN
        RAISE NOTICE 'V108: expected 0 learner-owned non-copy notes with join rows after the delete -- investigate before anything reads note_course_program';
    END IF;
END $$;
