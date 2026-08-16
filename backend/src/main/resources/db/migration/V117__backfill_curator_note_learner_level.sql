-- Authored Depth is a curriculum floor for future regeneration, so this historical correction is
-- limited to ADMIN-owned curator notes. Learner-owned notes never made this authoring decision and
-- must not acquire one from Target Audience. Existing learner_level values are authored decisions
-- and are therefore never overwritten.
--
-- BOARD_TAKER and PROFESSIONAL have exact safe mappings to BOARD_EXAM_REVIEW and PROFESSIONAL.
-- STUDENT deliberately has no mapping because it spans grade school through college depth.
--
-- BOARD_TAKER is not self-certifying. The production audit found all nine Information Technology
-- BOARD_TAKER notes to be ordinary database/JavaScript coursework, not licensure-review material.
-- Check both program stores: curated applicability rows in note_course_program and the personal-note
-- free-text notes.course_program value. A note with any resolved Information Technology program stays
-- NULL for curator classification, even when it also has other catalog programs.
--
-- Do not generalise this exclusion through course_programs.exam_goal_slug. That column identifies the
-- four programs with Exam Hubs, not every Philippine licensure program; Civil Engineering alone has
-- 254 legitimate BOARD_TAKER notes and no exam_goal_slug.
--
-- This is non-destructive and naturally idempotent: after a mapped note receives a level it no longer
-- matches learner_level IS NULL. Target Audience remains unchanged and is not a runtime fallback.
UPDATE notes n
SET learner_level = CASE n.target_profile_type
    WHEN 'BOARD_TAKER' THEN 'BOARD_EXAM_REVIEW'
    WHEN 'PROFESSIONAL' THEN 'PROFESSIONAL'
END
FROM users u
WHERE u.id = n.owner_user_id
  AND u.role = 'ADMIN'
  AND n.learner_level IS NULL
  AND (
      n.target_profile_type = 'PROFESSIONAL'
      OR (
          n.target_profile_type = 'BOARD_TAKER'
          AND coalesce(lower(trim(n.course_program)), '') <> lower('Information Technology')
          AND NOT EXISTS (
              SELECT 1
              FROM note_course_program ncp
              JOIN course_programs cp ON cp.id = ncp.course_program_id
              WHERE ncp.note_id = n.id
                AND lower(trim(cp.name)) = lower('Information Technology')
          )
      )
  );
