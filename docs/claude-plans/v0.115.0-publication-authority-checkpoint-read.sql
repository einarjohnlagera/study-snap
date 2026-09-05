-- =============================================================================
-- v0.115.0 — Learner Publication Authority
-- [CHECKPOINT — due 2026-10-05]  (deploy 2026-09-05 + 30 days)
--
-- ⚠️ READ-ONLY. Every statement here is a SELECT. Nothing in this file writes.
--
-- WHAT THIS ANSWERS, AND WHY IT IS OWED
-- -----------------------------------------------------------------------------
-- v0.115.0 claims it TRANSFERS program-classification authority to the learner
-- rather than merely revoking the curator's. That claim is a BOOTSTRAP ARGUMENT
-- FROM MECHANISM, not a measured outcome: clearing the copied rows un-shadows the
-- learner's own Course / Program field, the editor renders it with a required
-- marker (note-editor-form.tsx:427-433), and we infer the learner will therefore
-- state their own classification.
--
-- If they publish and never classify, the release traded "shelved under the
-- curator's programs" for "not shelved at all" — a discovery REGRESSION wearing
-- the shape of a correctness fix. That is the thing this read exists to falsify.
--
-- KILL CRITERION, stated BEFORE the read
-- -----------------------------------------------------------------------------
-- If a MAJORITY of post-deploy learner publications remain UNSHELVED (zero join
-- rows AND null course_program) at the due date, the un-shadowing did not transfer
-- authority — it removed representation. Remedy in that case is a PUBLISH-TIME
-- classification step (ask before the note goes public), NOT reverting the
-- clearing, which would restore the curator-authority defect.
--
-- ⚠️ DENOMINATOR CLAUSE. Zero learner copies were public on 2026-09-05, so this
-- cohort starts EMPTY. "Too small to read" is itself a finding, not grounds to
-- extend: it would mean learners essentially never publish their copies, which
-- makes the closed hazard more theoretical than the 536-row count suggested.
-- Record that outcome and close; do not silently re-date.
-- =============================================================================


-- Q1 — THE HEADLINE. Post-deploy learner publications, split by whether the
--      learner classified. Curators are excluded: the clearing never touched them.
SELECT
    COUNT(*)                                                   AS learner_publications,
    COUNT(*) FILTER (WHERE ncp.row_count = 0
                       AND n.course_program IS NULL)           AS unshelved,
    COUNT(*) FILTER (WHERE n.course_program IS NOT NULL)       AS stated_own_program,
    COUNT(*) FILTER (WHERE ncp.row_count > 0)                  AS has_join_rows,
    ROUND(100.0 * COUNT(*) FILTER (WHERE ncp.row_count = 0
                                     AND n.course_program IS NULL)
          / NULLIF(COUNT(*), 0), 1)                            AS unshelved_pct
FROM notes n
JOIN users u ON u.id = n.owner_user_id
JOIN LATERAL (
    SELECT COUNT(*) AS row_count
    FROM note_course_program x
    WHERE x.note_id = n.id
) ncp ON TRUE
WHERE n.visibility = 'PUBLIC'
  AND n.copied_from_note_id IS NOT NULL
  AND u.role <> 'ADMIN'
  AND u.profile_type <> 'TEACHER'
  AND EXISTS (
      SELECT 1 FROM analytics_events e
      WHERE e.entity_id = n.id
        AND e.event_type = 'PUBLIC_NOTE_PUBLISHED'
        AND e.created_at >= TIMESTAMPTZ '2026-09-05 00:00:00+00'
  );


-- Q2 — TIME TO CLASSIFY. Of those that DID get a program, how long after
--      publication? A long tail means the required marker works but late, which
--      argues for a publish-time prompt rather than against the design.
SELECT
    width_bucket(
        EXTRACT(EPOCH FROM (n.updated_at - e.published_at)) / 3600.0,
        0, 168, 7
    )                                        AS hours_bucket_0_to_168,
    COUNT(*)                                 AS notes
FROM notes n
JOIN users u ON u.id = n.owner_user_id
JOIN LATERAL (
    SELECT MIN(created_at) AS published_at
    FROM analytics_events e2
    WHERE e2.entity_id = n.id
      AND e2.event_type = 'PUBLIC_NOTE_PUBLISHED'
      AND e2.created_at >= TIMESTAMPTZ '2026-09-05 00:00:00+00'
) e ON e.published_at IS NOT NULL
WHERE n.visibility = 'PUBLIC'
  AND n.copied_from_note_id IS NOT NULL
  AND n.course_program IS NOT NULL
  AND u.role <> 'ADMIN'
  AND u.profile_type <> 'TEACHER'
GROUP BY 1
ORDER BY 1;


-- Q3 — THE CONTROL, AND IT IS THE ONE THAT MAKES Q1 INTERPRETABLE.
--      Learner-owned public notes that were NEVER copied (authored from scratch).
--      They were never shadowed, so their classification rate is the BASELINE for
--      "how often does a learner fill this field at all?" A low Q1 number matched
--      by an equally low Q3 means the finding is about the FIELD, not about this
--      release — do not attribute a pre-existing habit to the clearing.
SELECT
    COUNT(*)                                             AS learner_authored_public,
    COUNT(*) FILTER (WHERE n.course_program IS NOT NULL) AS stated_own_program,
    ROUND(100.0 * COUNT(*) FILTER (WHERE n.course_program IS NOT NULL)
          / NULLIF(COUNT(*), 0), 1)                      AS stated_pct
FROM notes n
JOIN users u ON u.id = n.owner_user_id
WHERE n.visibility = 'PUBLIC'
  AND n.copied_from_note_id IS NULL
  AND u.role <> 'ADMIN'
  AND u.profile_type <> 'TEACHER';


-- Q4 — BLAST-RADIUS SANITY. The curated catalog must be UNTOUCHED. Curator public
--      notes carrying join rows should not have moved at all. A drop here means the
--      curator exclusion failed in production and is a P0, not a checkpoint finding.
SELECT
    COUNT(*)                                  AS curator_public_notes,
    COUNT(*) FILTER (WHERE ncp.row_count > 0) AS with_join_rows,
    SUM(ncp.row_count)                        AS total_join_rows
FROM notes n
JOIN users u ON u.id = n.owner_user_id
JOIN LATERAL (
    SELECT COUNT(*) AS row_count
    FROM note_course_program x
    WHERE x.note_id = n.id
) ncp ON TRUE
WHERE n.visibility = 'PUBLIC'
  AND (u.role = 'ADMIN' OR u.profile_type = 'TEACHER');
