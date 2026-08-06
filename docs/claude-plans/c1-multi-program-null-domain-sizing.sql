-- C1 sizing: do any notes already violate the multi-program => Domain Context invariant?
--
-- WHY THIS EXISTS
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
