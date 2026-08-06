-- ============================================================================
-- CLOSED 2026-08-06 — NO LONGER A GATE. Retained only as a post-deploy check.
--
-- This gate closed BY CONSTRUCTION rather than by being run: production never received
-- note_course_program (V107 ships unreleased; production stopped at V106), so the affected count is 0 --
-- the owner's pre-set "prevent future divergence, no UI" threshold. The decision is Option 7, prevent the
-- mechanical derivation, ratified in ADR-001 -> "Representation authority" and shipped as
-- V108__remove_derived_learner_note_programs.sql, which deletes join rows on learner-owned NON-COPY notes
-- and preserves curator-authored and copy-inherited rows.
--
-- Still useful AFTER deploying V107+V108 together: Query 1's `backfill_derived` must read 0. Any other
-- value means V108 did not do its job. Note that the queries below describe the pre-V108 world, so their
-- framing ("this is the decision gate", the thresholds) is historical -- do not re-derive a decision from it.
-- ============================================================================
--
-- B2 / Decision A — sizing the V107 migration artifact. RUN AGAINST PRODUCTION.
--
-- This is the DECISION GATE. Owner ruling 2026-08-06: do not choose a fix before knowing the size.
--
-- Framing (owner, 2026-08-06):
--   "The core problem is that the migration created metadata that no learner intentionally authored.
--    That is a migration problem before it is a UX problem. The important discriminator is PROVENANCE
--    rather than ownership. Curated rows, inherited rows, and migration-generated rows are fundamentally
--    different kinds of data. Migration-generated rows are DERIVED data, not user-authored data.
--    Keep the learner experience simple. Move complexity into curation."
--
-- Decision thresholds the owner set in advance:
--   already_diverged = 0        -> prevent future divergence + document the migration behaviour. No UI.
--   a handful diverged          -> corrective migration, not permanent UI complexity.
--   meaningfully large          -> provenance becomes the real architectural distinction; Option 5
--                                  (a `source` column on note_course_program) deserves serious consideration.
--
-- Background: V107 created note_course_program and backfilled one row per note whose course_program string
-- matched the catalog. It had NO owner filter, so learner-owned notes got rows nobody authored. Reads are
-- join-first, and a learner update deliberately does not touch join rows, so a learner who edits their program
-- has an edit that is permanently inert. The endpoint that edits Applicable Programs is curator-gated.
--
-- PROVENANCE PROXY, and its limits. There is no `source` column on note_course_program. This script infers:
--   * INHERITED    = the note is a copy (copied_from_note_id or source_note_id is set) -> rows came from
--                    copy-inheritance in NoteService.copyNote, i.e. a curator authored them originally.
--   * BACKFILL-ISH = the note is NOT a copy and is learner-owned -> a learner cannot author join rows through
--                    any UI, so these rows are almost certainly V107's.
-- The proxy is imperfect in one direction: an ADMIN can set programs on ANY user's note via the shared
-- endpoint, so a non-copy learner note could in principle carry admin-curated rows. Query 3 cross-checks that
-- against row created_at clustering.


-- ============================================================================
-- Query 1 — THE GATE. Learner-owned notes carrying join rows, split by provenance.
-- The number that decides the outcome is `backfill_derived_AND_diverged` — notes broken TODAY by the
-- migration artifact specifically, as opposed to by legitimate copy-inheritance.
-- ============================================================================
SELECT
  COUNT(*)                                                    AS learner_notes_with_join_rows,
  COUNT(*) FILTER (WHERE is_copy)                             AS inherited_from_copy,
  COUNT(*) FILTER (WHERE NOT is_copy)                         AS backfill_derived,
  COUNT(*) FILTER (WHERE diverged)                            AS diverged_total,
  COUNT(*) FILTER (WHERE NOT is_copy AND diverged)            AS backfill_derived_AND_diverged,
  COUNT(*) FILTER (WHERE is_copy AND diverged)                AS inherited_AND_diverged
FROM (
  SELECT n.id,
    (n.copied_from_note_id IS NOT NULL OR n.source_note_id IS NOT NULL) AS is_copy,
    (n.course_program IS NOT NULL AND TRIM(n.course_program) <> ''
       AND NOT EXISTS (SELECT 1 FROM note_course_program x
                       JOIN course_programs c ON c.id = x.course_program_id
                       WHERE x.note_id = n.id AND c.name = n.course_program)) AS diverged
  FROM notes n JOIN users u ON u.id = n.owner_user_id
  WHERE EXISTS (SELECT 1 FROM note_course_program ncp WHERE ncp.note_id = n.id)
    AND u.role <> 'ADMIN' AND u.profile_type IS DISTINCT FROM 'TEACHER'
) t;


-- ============================================================================
-- Query 2 — The diverged notes in detail. Eyeball these; if the count is small this is the
-- corrective-migration work list. Shows what the note claims vs what discovery/generation actually use.
-- ============================================================================
SELECT
  n.id,
  n.course_program                              AS note_says,
  string_agg(c.name, ', ' ORDER BY c.name)      AS join_rows_say,
  (n.copied_from_note_id IS NOT NULL OR n.source_note_id IS NOT NULL) AS is_copy,
  n.created_at                                  AS note_created,
  MIN(ncp.created_at)                           AS earliest_join_row
FROM notes n
JOIN users u             ON u.id = n.owner_user_id
JOIN note_course_program ncp ON ncp.note_id = n.id
JOIN course_programs c   ON c.id = ncp.course_program_id
WHERE u.role <> 'ADMIN' AND u.profile_type IS DISTINCT FROM 'TEACHER'
  AND n.course_program IS NOT NULL AND TRIM(n.course_program) <> ''
GROUP BY n.id, n.course_program, n.copied_from_note_id, n.source_note_id, n.created_at
HAVING NOT bool_or(c.name = n.course_program)
ORDER BY MIN(ncp.created_at);


-- ============================================================================
-- Query 3 — Provenance cross-check. V107's rows should cluster tightly at migration time.
-- A distinct early cluster confirms the backfill; rows spread over time are curator/copy activity.
-- Use this to sanity-check the is_copy proxy in Query 1 before trusting it.
-- ============================================================================
SELECT
  DATE_TRUNC('minute', ncp.created_at)   AS created_minute,
  COUNT(*)                               AS rows_created,
  COUNT(DISTINCT ncp.note_id)            AS notes_touched,
  COUNT(*) FILTER (WHERE u.role <> 'ADMIN' AND u.profile_type IS DISTINCT FROM 'TEACHER')
                                         AS on_learner_notes
FROM note_course_program ncp
JOIN notes n ON n.id = ncp.note_id
JOIN users u ON u.id = n.owner_user_id
GROUP BY DATE_TRUNC('minute', ncp.created_at)
ORDER BY created_minute
LIMIT 50;


-- ============================================================================
-- Query 4 — Future exposure. Learner notes carrying join rows whose string STILL agrees today.
-- These are not broken yet, but every one becomes broken the moment its owner edits their program.
-- This is the number that justifies "prevent future divergence" even when already_diverged = 0.
-- ============================================================================
SELECT
  COUNT(*)                                     AS learner_notes_at_risk,
  COUNT(*) FILTER (WHERE NOT is_copy)          AS at_risk_and_backfill_derived
FROM (
  SELECT n.id,
    (n.copied_from_note_id IS NOT NULL OR n.source_note_id IS NOT NULL) AS is_copy
  FROM notes n JOIN users u ON u.id = n.owner_user_id
  WHERE u.role <> 'ADMIN' AND u.profile_type IS DISTINCT FROM 'TEACHER'
    AND n.course_program IS NOT NULL AND TRIM(n.course_program) <> ''
    AND EXISTS (SELECT 1 FROM note_course_program x
                JOIN course_programs c ON c.id = x.course_program_id
                WHERE x.note_id = n.id AND c.name = n.course_program)
) t;


-- ----------------------------------------------------------------------------
-- LOCAL DEV BASELINE, for comparison only (run 2026-08-06, V107 applied):
--   Query 1 -> learner_notes_with_join_rows = 8
--              inherited_from_copy          = 8
--              backfill_derived             = 0
--              diverged_total               = 0
--              backfill_derived_AND_diverged= 0
-- The local corpus is admin-heavy (most notes are owned by the curator account), so it is NOT
-- representative. Production has 364 accounts with 179 learners concentrated on the four catalog
-- programs the backfill matched, so the backfill_derived count there should be materially higher.
-- ----------------------------------------------------------------------------
