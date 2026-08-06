-- C1 sizing: do any notes already violate the multi-program => Domain Context invariant?
--
-- ============================================================================================
-- STATUS 2026-08-06: THIS GATE IS CLOSED BY CONSTRUCTION. DO NOT RUN AGAINST PRODUCTION YET --
-- THE TABLE DOES NOT EXIST THERE.
--
-- Production has not received `note_course_program` at all: the whole of v0.71.0, slices 1
-- through 4, deploys as one unit when the release merges. That closes the window this file was
-- written to measure:
--
--   * V107's backfill creates AT MOST ONE row per note -- it joins `course_programs.name`,
--     which is unique, on a single `notes.course_program` value (plus the one `Bsed` alias).
--     So no note can leave the migration multi-program.
--   * The slice 4 invariant ships in the SAME deploy, so both curator write paths
--     (`NoteService` and `NoteApplicableProgramsService.replace`) enforce it from the first
--     request.
--   * C1 closes the learner path in that same deploy.
--   * `copyNote` inherits join rows and `domain_context` together, so a copy of a legal note
--     is legal.
--
-- The pre-existing violating population this file was meant to size therefore cannot exist,
-- and no corrective migration or Known Limitation is owed. C1 is fully resolved.
--
-- KEEP THIS FILE as a POST-DEPLOY verification, not a pre-signoff gate. Run query 2 after
-- v0.71.0 reaches production and any time the invariant's enforcement changes; a non-zero
-- result then means a write path regressed, which is a live bug rather than legacy data.
-- ============================================================================================
--
-- WHY THIS EXISTS (original framing, retained for the reasoning)
-- C1 makes NoteService.update validate the *stored* join rows for learner owners, so a learner can no
-- longer clear domainContext on a copied multi-program note. That is the fix. The open question is
-- what it does to notes that are ALREADY in the violating state.
--
-- The Domain Context control is curator-only (frontend/components/notes/note-editor-form.tsx:469 renders
-- it behind showAuthoringMetadataFields). So a learner who owns a note with >1 program rows and a NULL
-- domain_context would be blocked from EVERY subsequent save -- including an unrelated title fix -- with
-- no in-product way to repair it.
--
-- CAN SUCH ROWS EXIST? There is a window. NoteApplicableProgramsService.replace gained the invariant in
-- slice 4 (595dfa62); the slice 1 version (1d168048) had no such check. Any curator who set several
-- programs on a NULL-domain note through PUT /notes/{id}/applicable-programs between those two deploys
-- created one. Whether that window was ever open in production is what this measures.
--
-- Local dev is NOT representative -- query A established that on a course/program distribution that was
-- off by enough to invert its conclusion (60% local vs 1.17% production). Run this against production.

-- =====================================================================================
-- Query 1 -- THE GATE. Notes violating the invariant, split by whether the owner can repair it.
-- Expected: 0 rows. Any row is a note whose owner may be locked out of editing.
-- =====================================================================================
SELECT
    n.id                AS note_id,
    n.owner_user_id,
    u.profile_type,
    u.role,
    count(*)            AS program_rows,
    -- Curators can repair via the Applicable Programs surface; learners cannot reach it at all.
    CASE
        WHEN u.role = 'ADMIN' OR u.profile_type = 'TEACHER' THEN 'curator - can repair'
        ELSE 'LEARNER - LOCKED OUT'
    END                 AS repairability
FROM note_course_program ncp
JOIN notes n ON n.id = ncp.note_id
JOIN users u ON u.id = n.owner_user_id
WHERE n.domain_context IS NULL
GROUP BY n.id, n.owner_user_id, u.profile_type, u.role
HAVING count(*) > 1
ORDER BY program_rows DESC;

-- =====================================================================================
-- Query 2 -- One-line summary for the RELEASES.md note.
-- =====================================================================================
SELECT
    count(*)                                          AS violating_notes,
    count(*) FILTER (
        WHERE NOT (role = 'ADMIN' OR profile_type = 'TEACHER')
    )                                                 AS locked_out_learner_notes,
    coalesce(max(program_rows), 0)                    AS widest_violation
FROM (
    SELECT n.id, u.profile_type, u.role, count(*) AS program_rows
    FROM note_course_program ncp
    JOIN notes n ON n.id = ncp.note_id
    JOIN users u ON u.id = n.owner_user_id
    WHERE n.domain_context IS NULL
    GROUP BY n.id, u.profile_type, u.role
    HAVING count(*) > 1
) violations;

-- =====================================================================================
-- HOW TO READ THE RESULT
--
--   0 violating notes
--       No lockout population exists. C1 is fully resolved; delete this gate from RELEASES.md.
--
--   >0, all curator-owned
--       No lockout -- curators repair through the Applicable Programs surface. Worth a Known
--       Limitation line noting the state exists and how it is repaired.
--
--   >0 with locked_out_learner_notes > 0
--       Real lockout. C1's fix still correctly blocks every NEW violation and should ship regardless,
--       but this population needs a corrective migration (backfill domain_context, or trim the extra
--       join rows) before signoff -- or an explicit Known Limitation naming the affected note count.
--       Do NOT solve it by exposing Domain Context to learners: ADR-001 and the 2026-08-06 ruling both
--       keep curation complexity out of the learner authoring surface.
-- =====================================================================================
