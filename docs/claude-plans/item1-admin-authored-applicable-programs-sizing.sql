-- v0.71.1 group 1 item 1 — sizing the admin-authored Applicable Programs class.
--
-- Context: NoteApplicableProgramsService.replace lets an ADMIN set catalog programs on ANY note,
-- including another user's. A learner update never clears them, so this recreates the V108 class
-- (rows a learner cannot reach on a note that is theirs) by ordinary admin action rather than by
-- migration. ADR-001 -> "Representation authority" is the ratified rule this tests against.
--
-- Why run it before deciding: ADR-001:153 ratified that "zero affected users strengthens the case
-- for prevention rather than weakening it" — B2 was decided exactly this way. A zero here makes
-- prevention cheap and the decision much easier.
--
-- Run against PRODUCTION. Local (2026-08-11) returned 0 for query A, with every learner-owned note
-- carrying join rows being a copy — the shape the architecture intends.

-- ============================================================================
-- A. THE DECIDING NUMBER — rows only reachable by an admin write.
--    Learner-owned (not admin, not teacher), not a copy of anything, yet carrying join rows.
--    Copy inheritance is the only *legitimate* route to a join row on a learner-owned note,
--    so anything here was authored by an admin onto someone else's note.
-- ============================================================================
SELECT count(*) AS admin_authored_rows,
       count(DISTINCT n.id) AS affected_notes,
       count(DISTINCT n.owner_user_id) AS affected_owners
FROM note_course_program ncp
JOIN notes n ON n.id = ncp.note_id
JOIN users u ON u.id = n.owner_user_id
WHERE u.role <> 'ADMIN'
  AND u.profile_type IS DISTINCT FROM 'TEACHER'
  AND n.copied_from_note_id IS NULL
  AND n.source_note_id IS NULL;

-- ============================================================================
-- B. THE FLIP HOLE — sizes the option that gates on visibility at write time.
--    A visibility-scoped rule permits curating a PUBLIC note; the learner can then flip it back to
--    PRIVATE (canManageVisibility = isEmailVerified || isPublic), landing in the V108 class through
--    two individually-permitted steps. These are learner-owned PRIVATE notes carrying join rows.
--    Non-copies among them are already-realised instances of the hole.
-- ============================================================================
SELECT (n.copied_from_note_id IS NOT NULL OR n.source_note_id IS NOT NULL) AS arrived_by_copy,
       count(DISTINCT n.id) AS notes,
       count(*) AS rows_
FROM note_course_program ncp
JOIN notes n ON n.id = ncp.note_id
JOIN users u ON u.id = n.owner_user_id
WHERE u.role <> 'ADMIN'
  AND u.profile_type IS DISTINCT FROM 'TEACHER'
  AND n.visibility = 'PRIVATE'
GROUP BY 1;

-- ============================================================================
-- C. CURATION REACH — what an owner-only restriction would actually cost.
--    Notes carrying join rows that an ADMIN does NOT own. Under "admin curates only what it owns"
--    these become uncurateable. A small number means the restriction costs little in practice.
-- ============================================================================
SELECT n.visibility,
       CASE WHEN u.profile_type = 'TEACHER' THEN 'teacher' ELSE 'learner' END AS owner_kind,
       count(DISTINCT n.id) AS notes
FROM note_course_program ncp
JOIN notes n ON n.id = ncp.note_id
JOIN users u ON u.id = n.owner_user_id
WHERE u.role <> 'ADMIN'
GROUP BY 1, 2
ORDER BY 1, 2;

-- ============================================================================
-- D. BLAST RADIUS OF THE ADMIN PAGE — how many notes the curator view lists but,
--    under any restriction, could no longer write. getAdminPage uses findAll() with no
--    visibility or ownership filter, so it currently lists every private learner note.
--    Whichever option ships must filter or mark these, or an admin gets "not found"
--    on a row the page just rendered.
-- ============================================================================
SELECT count(*) FILTER (WHERE u.role = 'ADMIN') AS admin_owned,
       count(*) FILTER (WHERE u.role <> 'ADMIN' AND n.visibility = 'PUBLIC') AS other_public,
       count(*) FILTER (WHERE u.role <> 'ADMIN' AND n.visibility = 'PRIVATE') AS other_private,
       count(*) AS total_listed
FROM notes n
JOIN users u ON u.id = n.owner_user_id;
