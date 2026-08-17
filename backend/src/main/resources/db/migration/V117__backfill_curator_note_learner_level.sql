-- Authored Depth is a curriculum floor for future regeneration, so this historical correction is
-- limited to ADMIN-owned curator notes. Learner-owned notes never made this authoring decision and
-- must not acquire one from Target Audience. Existing learner_level values are authored decisions
-- and are therefore never overwritten.
--
-- BOARD_TAKER and PROFESSIONAL have exact safe mappings to BOARD_EXAM_REVIEW and PROFESSIONAL.
-- STUDENT deliberately has no mapping because it spans grade school through college depth.
--
-- BOARD_TAKER is not self-certifying, and it has now failed in two separate programs. The production
-- audit found all nine Information Technology BOARD_TAKER notes to be ordinary database/JavaScript
-- coursework. A second audit of academic-level program values then found a public curator note tagged
-- BOARD_TAKER whose program is High School and whose depth the same curator authored as JUNIOR_HIGH --
-- there is no junior-high licensure board, so the audience tag contradicts the curator's own depth.
--
-- The exclusion is therefore a denylist of program values known NOT to be licensure programs, not a
-- single named program. Both stores are checked: curated applicability rows in note_course_program and
-- the personal-note free-text notes.course_program value. A note stays NULL for curator classification
-- if ANY of its resolved programs is on the denylist, even when it also carries legitimate ones.
--
-- The denylist gates BOTH mappings, not just BOARD_TAKER. It excludes zero PROFESSIONAL rows today --
-- production carries no PROFESSIONAL note at all -- but the only reason that mapping exists is that a
-- note could acquire the audience later, and an arm kept for tomorrow must be guarded tomorrow. A
-- 'Grade School' note tagged PROFESSIONAL is mis-tagged for exactly the reason the BOARD_TAKER ones are.
--
-- The academic-level values are on it because they are levels wearing a program's clothing -- the
-- Authored Depth axis leaking into the program axis. They currently exclude zero rows (no curator note
-- is both BOARD_TAKER and NULL-depth with such a program), so this is defence in depth: a migration
-- runs once, at merge time, against whatever the data looks like then.
--
-- 'junior high%' and 'senior high%' are prefix matches: the Senior High strand suffixes vary
-- (STEM / HUMSS / ABM) behind an en dash that must not become load-bearing, and both take a
-- '... School' suffix in ordinary usage. The audit query searched for those longer spellings and the
-- data contained none, so the prefixes close a gap that is not currently live -- kept because they
-- cost the same as the exact match and make the denylist match the net the audit actually cast.
--
-- The remaining entries are exact matches, so the denylist is not spelling-proof: a free-text 'IT'
-- or 'HS' would still pass. It is also a denylist, so it protects only against the non-licensure
-- programs somebody has already audited. Widening it further needs evidence, not guesses.
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
  AND n.target_profile_type IN ('BOARD_TAKER', 'PROFESSIONAL')
  AND NOT (
      coalesce(lower(trim(n.course_program)), '') IN (
          'information technology', 'grade school', 'high school'
      )
      OR coalesce(lower(trim(n.course_program)), '') LIKE 'junior high%'
      OR coalesce(lower(trim(n.course_program)), '') LIKE 'senior high%'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM note_course_program ncp
      JOIN course_programs cp ON cp.id = ncp.course_program_id
      WHERE ncp.note_id = n.id
        AND (
            lower(trim(cp.name)) IN (
                'information technology', 'grade school', 'high school'
            )
            OR lower(trim(cp.name)) LIKE 'junior high%'
            OR lower(trim(cp.name)) LIKE 'senior high%'
        )
  );
