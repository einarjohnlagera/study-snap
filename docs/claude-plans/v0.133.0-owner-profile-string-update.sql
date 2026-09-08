-- v0.133.0 — OWNER-RUN production write
--
-- ⚠️ THIS IS THE OWNER'S TO RUN. Claude does not execute writes against production, and this file
-- exists instead of an execution. It is handed over per the protocol in CLAUDE.md: the exact
-- statement, the expected row count, and how to verify it.
--
-- WHY THIS EXISTS
-- v0.133.0 renames the catalog row `Special Needs Education – Generalist` to `Special Needs Education`
-- (owner decision 1, settled 2026-09-08). That decision was made CONDITIONAL on the precondition read
-- returning zero hits across five free-text `course_program` columns.
--
-- ⚠️ THE CONDITION WAS NOT MET. The read ran against production on 2026-09-08 and returned:
--
--   notes.course_program                    0
--   note_collections.course_program         0
--   users.course_program                    1   <-- this row
--   bulk_generation_result.course_program   0
--   official_study_plan_wishlist            0   (both columns)
--
--   notes linked via note_course_program:   0
--
-- So exactly ONE user profile carries the literal string, and NO Note is linked to the row by id.
--
-- ⚠️ NOTHING BREAKS IF THIS IS NOT RUN. `users.course_program` is consumed by
-- StudyPackGenerationContextResolver as FREE TEXT (a fallback authoring domain passed straight
-- through), never resolved against the catalog by name. Leaving it stale means that one account's
-- profile program no longer corresponds to any catalog entry, which affects catalog-scoped discovery
-- and matching for that account only — not correctness.
--
-- THE OWNER'S THREE OPTIONS
--   (a) Run the UPDATE below, so the profile follows the rename.        <-- recommended
--   (b) Leave it stale. One account's profile stops matching the catalog.
--   (c) Do not rename at all; keep `Special Needs Education – Generalist` and drop the proposed
--       `Special Needs Education` from the insert list. This reopens owner decision 1.
--
-- ⚠️ IF (a): RUN IT IN THE SAME MAINTENANCE STEP AS THE MIGRATION, not before it. Running this first
-- leaves the profile pointing at a catalog name that does not exist yet.

-- ── 1. VERIFY BEFORE (expect exactly 1) ──────────────────────────────────────────────────────────
SELECT COUNT(*) AS expect_1
  FROM users
 WHERE course_program = 'Special Needs Education – Generalist';

-- ── 2. THE WRITE (expect: UPDATE 1) ──────────────────────────────────────────────────────────────
-- ⚠️ THE SEPARATOR IS AN EN DASH (–, U+2013), NOT A HYPHEN. Copy this line verbatim; retyping it
-- with a hyphen matches nothing and the statement silently updates 0 rows.
UPDATE users
   SET course_program = 'Special Needs Education'
 WHERE course_program = 'Special Needs Education – Generalist';

-- ── 3. VERIFY AFTER (expect old_name = 0 and new_name = 1) ───────────────────────────────────────
SELECT
  COUNT(*) FILTER (WHERE course_program = 'Special Needs Education – Generalist') AS old_name_expect_0,
  COUNT(*) FILTER (WHERE course_program = 'Special Needs Education')              AS new_name_expect_1
  FROM users;
