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
-- WHY THE PREDICATE MIRRORS V107'S OWN INSERT, AND NOT "IS THIS NOTE A COPY".
-- An earlier version of this migration deleted rows where the note was learner-owned AND not a copy
-- (copied_from_note_id IS NULL AND source_note_id IS NULL), reasoning that copy-inheritance is the only
-- legitimate route to a join row on a learner note. That reasoning conflated "is a copy" with "has
-- INHERITED rows", and it was wrong: NoteService.copyNote sets source_note_id on EVERY copy -- self-copy
-- and public copy alike -- and has done so since long before this release. So every PRE-EXISTING copy was
-- skipped by that predicate while still carrying the row V107 derived from its own course_program string.
-- The migration preserved precisely the rows it exists to delete. Measured on a dev database: 8 of 11
-- learner-owned rows on copies were derived, i.e. wrongly protected.
--
-- Copy-inheritance of join rows is itself NEW in this release, so at V108 time a pre-existing copy cannot
-- have inherited rows -- only derived ones. The copy check therefore protected nothing that existed yet.
--
-- The predicate below is not a heuristic and not a proxy: it is the exact INVERSE of V107's insert. V107
-- joined course_programs.name against the note's own course_program (with the single 'Bsed' -> 'Education'
-- alias); this deletes learner-owned rows where that same equality still holds. It therefore removes
-- exactly V107's output for learner notes and nothing else. Rows whose catalog name DIFFERS from the note's
-- string cannot have come from V107 and are preserved -- on the same dev database, the remaining 3.
--
-- Deletion is by note_course_program.id, not by note_id, so a note carrying both a derived and an
-- inherited row loses only the derived one.
--
-- KNOWN AMBIGUITY, accepted: a copy whose inherited row happens to carry the same catalog name as the
-- copy's own string is indistinguishable from a derived row, and is deleted. This cannot arise in
-- production, where no inherited rows exist at V108 time. Resolving it would need the `source` provenance
-- column the owner considered and rejected.
--
-- ACCEPTED LIMITATION, documented rather than solved: an ADMIN can set programs on another user's note
-- through the shared Applicable Programs endpoint. If an admin curated a learner-owned note with a program
-- matching that note's own string before this migration runs, the row is removed here. Unreachable in
-- production (no traffic between V107 and V108) and vanishingly unlikely elsewhere.
--
-- ORPHANED OWNER BEHAVIOUR: the users join is an INNER JOIN, so a note whose owner_user_id has no users row
-- does not match and its rows are KEPT. Deletion is destructive; absence of evidence must not authorize it.
-- Unreachable in practice (notes.owner_user_id is NOT NULL REFERENCES users(id)) and made explicit anyway.
--
-- IDEMPOTENT BY CONSTRUCTION: the DELETE removes every row matching the predicate, so a second run matches
-- nothing and deletes nothing. A fresh database matches nothing and succeeds, deleting zero.
--
-- The DELETE is deliberately kept OUTSIDE the PL/pgSQL reporting block below. CourseProgramCatalogMigrationTest
-- strips a migration from the start of that block onward, because H2 cannot parse PL/pgSQL -- V107's own
-- backfill sits inside such a block and is untested for exactly that reason. Keeping the DELETE above the
-- block is what makes V108's test genuine. Do not move it.

DELETE FROM note_course_program
WHERE id IN (
    SELECT ncp.id
    FROM note_course_program ncp
    JOIN notes n ON n.id = ncp.note_id
    JOIN course_programs cp ON cp.id = ncp.course_program_id
    JOIN users u ON u.id = n.owner_user_id
    WHERE u.role <> 'ADMIN'
      AND u.profile_type IS DISTINCT FROM 'TEACHER'
      AND cp.name = CASE
          WHEN n.course_program = 'Bsed' THEN 'Education'
          ELSE n.course_program
      END
);

DO $$
DECLARE
    remaining_rows INTEGER;
    remaining_learner_rows INTEGER;
BEGIN
    -- Informational, never fatal. ROW_COUNT is not reported: GET DIAGNOSTICS reflects the last statement
    -- executed INSIDE this block, and the DELETE deliberately sits outside it.
    --
    -- The check below is DELIBERATELY NOT the delete predicate re-run. The previous version of this file
    -- asserted "0 learner-owned non-copy notes still carry rows" using the same predicate the DELETE used,
    -- so it reported 0 by construction no matter what survived -- which is exactly why the predicate being
    -- wrong went unnoticed. Count something the DELETE did not target instead: ALL rows still attached to a
    -- learner-owned note. In production that must be 0, because copy-inheritance of join rows is new in
    -- this release and no traffic runs between V107 and V108, so every row present came from V107's
    -- backfill and every one of them matches the predicate. A non-zero value in production means a row
    -- survived that should not have. Elsewhere -- a dev database where V107 ran long ago and traffic has
    -- flowed since -- a small non-zero count is expected and correct: those are genuinely inherited rows
    -- whose catalog name differs from the note's own string, and they must be preserved.
    SELECT count(*) INTO remaining_rows FROM note_course_program;
    SELECT count(*)
      INTO remaining_learner_rows
      FROM note_course_program ncp
      JOIN notes n ON n.id = ncp.note_id
      JOIN users u ON u.id = n.owner_user_id
     WHERE u.role <> 'ADMIN'
       AND u.profile_type IS DISTINCT FROM 'TEACHER';

    RAISE NOTICE 'V108: % note_course_program rows remain in total; % of them sit on learner-owned notes (expected 0 in production, small and non-zero only where traffic ran between V107 and V108)',
        remaining_rows, remaining_learner_rows;
END $$;
