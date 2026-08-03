-- Backfills the 27 pure-level legacy notes with General Education and their authored learner level.
-- course_program is deliberately retained; clearing it is cosmetic once domain_context is set.
-- The 11 'High School' rows and 11 strand-labelled legacy rows are deliberately excluded; their decisions live in ADR-001's Legacy-data policy and PR 4b.
-- Inverse legacy mapping: 'GRADE_SCHOOL' -> 'Grade School'; 'JUNIOR_HIGH' -> 'Junior High'.
-- The guard below catches the case that would otherwise fail silently: rows exist but the
-- UPDATE predicate missed them (a mistyped label). It matches the same (NULL, NULL) precondition
-- both UPDATEs require, so a row a curator has already partly classified through the PR-3
-- authoring UI -- domain_context set, learner_level left blank -- is skipped by the UPDATE and
-- by the guard alike, rather than raising and blocking the deploy over a state the curator chose.
--
-- Not covered by KnowledgeImpactDigestPreferenceMigrationTest's pattern (load the file, run it
-- on H2 in PostgreSQL mode): H2 cannot parse a PL/pgSQL DO block, so that test could only run a
-- mutilated copy of this file with the guard stripped -- i.e. it would cover everything except
-- the part most likely to be wrong. Verified instead against real Postgres in BEGIN/ROLLBACK:
-- happy path, the partial-curator-edit case above, and a deliberately mistyped label to confirm
-- the guard fires. Do not add a Java test that runs only the UPDATEs.

UPDATE notes
SET learner_level = 'GRADE_SCHOOL',
    domain_context = 'GENERAL_EDUCATION'
WHERE course_program = 'Grade School'
  AND learner_level IS NULL
  AND domain_context IS NULL;

UPDATE notes
SET learner_level = 'JUNIOR_HIGH',
    domain_context = 'GENERAL_EDUCATION'
WHERE course_program = 'Junior High'
  AND learner_level IS NULL
  AND domain_context IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM notes
        WHERE course_program IN ('Grade School', 'Junior High')
          AND learner_level IS NULL
          AND domain_context IS NULL
    ) THEN
        RAISE EXCEPTION 'V104 backfill left Grade School or Junior High notes without a learner level';
    END IF;
END
$$;
