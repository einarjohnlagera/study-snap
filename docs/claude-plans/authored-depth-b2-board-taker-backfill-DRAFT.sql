-- ============================================================================================
-- DRAFT ONLY — NOT A FLYWAY MIGRATION. NOT EXECUTED BY CLAUDE. PRODUCTION WRITE — OWNER RUNS THIS.
-- ============================================================================================
-- Drafted 2026-09-17 per docs/claude-plans/authored-depth-legacy-backfill-audit-and-plan.md (§C
-- Query 6, §N "B2 disposition") and the owner's decision on that audit: "Run the V117 rule for the
-- 193 eligible."
--
-- What this is: a re-run, verbatim, of the UPDATE statement already shipped and owner-ratified as
-- backend/src/main/resources/db/migration/V117__backfill_curator_note_learner_level.sql (v0.82.0).
-- It is not new logic and not a new inference rule — see that file's own extensive comment header
-- for the full rationale (STUDENT excluded because it spans four depths; the denylist rationale;
-- why this is Target Audience -> Depth, a different axis pair from the Course/Program -> Depth
-- inference the current Authored Depth decision separately locks out).
--
-- Why it needs to run again: V117 was a ONE-TIME migration, not a runtime rule (AGENTS.md:75,
-- docs/features/notes.md:100). 173 of today's 202 BOARD_TAKER/PROFESSIONAL NULL-depth curator public
-- notes were created AFTER v0.82.0 ran and therefore never received it. This statement is naturally
-- idempotent (V117's own header: "after a mapped note receives a level it no longer matches
-- learner_level IS NULL"), so re-running it only touches rows that are still NULL today — it cannot
-- re-touch or overwrite any note V117 or a curator already classified.
--
-- Expected result: 193 rows updated (verified read-only against production 2026-09-17 — see the
-- COUNT query below, which returned exactly 193 before this statement was drafted). The remaining
-- 9 of the 202 are excluded by the denylist (an academic-level or Information-Technology-shaped
-- program value) and require human classification alongside the original 80 — this statement
-- deliberately does not touch them, and it must not be widened to cover them without the same
-- denylist audit V117 itself required.
--
-- BEFORE YOU RUN THIS: re-run the COUNT query below first. If the number is not 193, the population
-- has moved since this file was drafted (new bulk-generate batches land ~6/day per the audit's
-- Vector 2) — that is expected and fine, but confirm the new number and the AFTER count reconciles
-- (AFTER = 0 eligible rows remaining) before treating this as done.
-- ============================================================================================

-- ---- BEFORE: expected 193 (production read-only, 2026-09-17) ----
select count(*) as v117_eligible_before
from notes n join users u on u.id = n.owner_user_id
where u.role = 'ADMIN' and n.visibility = 'PUBLIC' and n.learner_level is null
  and n.target_profile_type in ('BOARD_TAKER', 'PROFESSIONAL')
  and not (
    coalesce(lower(trim(n.course_program)), '') in ('information technology', 'grade school', 'high school')
    or coalesce(lower(trim(n.course_program)), '') like 'junior high%'
    or coalesce(lower(trim(n.course_program)), '') like 'senior high%'
    or exists (
      select 1 from note_course_program ncp join course_programs cp on cp.id = ncp.course_program_id
      where ncp.note_id = n.id and (
        lower(trim(cp.name)) in ('information technology', 'grade school', 'high school')
        or lower(trim(cp.name)) like 'junior high%'
        or lower(trim(cp.name)) like 'senior high%')
    )
  );
-- Expect: 193

-- ---- THE WRITE — verbatim from V117__backfill_curator_note_learner_level.sql ----
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
-- Expect: "UPDATE 193" (or whatever the BEFORE count read as, if the population moved)

-- ---- AFTER: verify 0 remain eligible ----
select count(*) as v117_eligible_after
from notes n join users u on u.id = n.owner_user_id
where u.role = 'ADMIN' and n.visibility = 'PUBLIC' and n.learner_level is null
  and n.target_profile_type in ('BOARD_TAKER', 'PROFESSIONAL')
  and not (
    coalesce(lower(trim(n.course_program)), '') in ('information technology', 'grade school', 'high school')
    or coalesce(lower(trim(n.course_program)), '') like 'junior high%'
    or coalesce(lower(trim(n.course_program)), '') like 'senior high%'
    or exists (
      select 1 from note_course_program ncp join course_programs cp on cp.id = ncp.course_program_id
      where ncp.note_id = n.id and (
        lower(trim(cp.name)) in ('information technology', 'grade school', 'high school')
        or lower(trim(cp.name)) like 'junior high%'
        or lower(trim(cp.name)) like 'senior high%')
    )
  );
-- Expect: 0

-- ---- SANITY: total BOARD_TAKER/PROFESSIONAL NULL-depth remaining should now equal only the
--             denylist-excluded rows (~9), which join the manual cleanup queue alongside the 80 ----
select count(*) as remaining_after_denylist_exclusion
from notes n join users u on u.id = n.owner_user_id
where u.role = 'ADMIN' and n.visibility = 'PUBLIC' and n.learner_level is null
  and n.target_profile_type in ('BOARD_TAKER', 'PROFESSIONAL');
-- Expect: ~9 (202 minus however many were actually updated above)
