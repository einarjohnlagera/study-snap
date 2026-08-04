-- PR 6a input — how big is the re-level refresh wave?
-- Written 2026-08-03. Run against PRODUCTION. Read-only: no INSERT, UPDATE, DELETE, or DDL.
--
-- ============================================================================
-- WHY THIS EXISTS
-- ============================================================================
-- PR 6 moves `exam_question_pool.learner_level` and `challenge_quiz_question_bank.learner_level`
-- off the READER's level and onto the NOTE's curriculum level. The write side is a small change
-- (pass `StudyPackGenerationContextResolver.effectiveCurriculumLevel(context)` instead of
-- `context.learnerLevel()`), but the read side has a consequence that is not small:
--
--   ExamQuestionPoolService:101 compares the stored level against the current user's level and,
--   on mismatch, calls refreshPool(:102) -- which REGENERATES the pool via the LLM.
--
-- Change the semantics and every existing row stamped with a reader's level can mismatch on its
-- next access, triggering a lazy regeneration wave paid for in LLM spend and driven by ordinary
-- user traffic. ADR-001 Legacy-data rule 2 says "no destructive regeneration and no bulk
-- retirement," and whether a lazy per-pool refresh counts is genuinely ambiguous in that text.
--
-- Do not settle it by interpretation. Settle it by size:
--
--   * SMALL  -> ship 6a as one PR, note the one-time refresh cost in RELEASES.md.
--   * LARGE  -> 6a needs a migration that stamps existing rows with the source note's level
--               BEFORE the comparison changes, so the semantic change and the refresh wave do
--               not land together (otherwise a bug and expected churn are indistinguishable).
--
-- This cannot be answered locally: the dev database has 118 notes and a near-empty pool table.

-- ============================================================================
-- QUERY 0 — RUN THIS FIRST: which variant applies?
-- ============================================================================
-- Queries A, B and C below join on `notes.learner_level`, which is a V102 column. Production
-- was confirmed at **V101 on 2026-08-03**, i.e. nothing from this release is deployed, so as
-- written they fail with "column n.learner_level does not exist". Re-check rather than assuming
-- the state has not moved.

SELECT version, description, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 4;

-- >= 104  -> run A, B, C as written. This is the ONLY variant that answers the real question,
--            because only then do notes actually carry the level PR 6 will compare against.
-- 102-103 -> run A, B, C as written, but `source_note_has_level` will be 0 or near it (V104 has
--            not backfilled), so `at_risk` is still mostly reader drift. Treat as a lower bound.
-- < 102   -> use the V101 VARIANT below instead. Do NOT edit A/B/C by hand: dropping the
--            `notes` join changes what the number means, and the variant says so explicitly.
--
-- **Recommended: run this file AFTER the release deploys.** Its whole purpose is sizing a wave
-- caused by comparing against the NOTE's level, and at V101 no note has one. The variant below
-- is worth running now only to establish the floor -- see its note on why that floor is useful.

-- ============================================================================
-- V101 VARIANT — reader-level drift only (a genuine LOWER BOUND on `at_risk`)
-- ============================================================================
-- At V101, `effectiveCurriculumLevel` would resolve to COALESCE(owner level, 'COLLEGE') for
-- every note, because no note has an authored level. So this measures only the component of the
-- refresh wave caused by a pool being stamped with one level and the owner now sitting at
-- another -- users who changed their learner level after the pool was generated.
--
-- Why the floor is still worth having: this component does not shrink when V104 lands. V104 can
-- only ADD divergence, by giving 27 notes a level that may differ from their owner's. So a large
-- number here already decides the PR's shape -- 6a needs the pre-stamping migration -- without
-- waiting for deployment. A small number decides nothing and must be re-run after deploy.

SELECT
    count(*)                                                        AS ready_pools,
    count(*) FILTER (WHERE p.learner_level IS NULL)                 AS unstamped,
    count(*) FILTER (
        WHERE p.learner_level IS NOT NULL
          AND p.learner_level IS DISTINCT FROM COALESCE(u.learner_level, 'COLLEGE')
    )                                                               AS at_risk_floor
FROM exam_question_pool p
JOIN study_packs sp ON sp.id = p.study_pack_id
JOIN users u        ON u.id = sp.owner_user_id
WHERE p.generation_status = 'READY';

SELECT
    count(*)                                                        AS bank_rows,
    count(DISTINCT b.user_id)                                       AS affected_users,
    count(*) FILTER (WHERE b.learner_level IS NULL)                 AS unstamped,
    count(*) FILTER (
        WHERE b.learner_level IS NOT NULL
          AND b.learner_level IS DISTINCT FROM COALESCE(u.learner_level, 'COLLEGE')
    )                                                               AS at_risk_floor,
    count(*) FILTER (WHERE b.last_known_outcome = 'INCORRECT')      AS incorrect_rows
FROM challenge_quiz_question_bank b
JOIN users u ON u.id = b.user_id
WHERE b.claimed_session_id IS NULL;

-- Query C has NO V101 equivalent and must wait for deployment. It measures whether the note's
-- level and the reader's level disagree -- which is definitionally zero when no note has a
-- level. Running a stripped version would return 0 and read as "no divergence", which is the
-- opposite of the truth: the divergence simply cannot exist yet.

-- ============================================================================
-- QUERY A — the headline number
-- ============================================================================
-- `at_risk` is the count that decides the PR's shape: READY pools whose stored level differs
-- from the level PR 6 will start comparing against (the source note's, falling back to the
-- owner's, then COLLEGE -- mirroring effectiveCurriculumLevel).

SELECT
    count(*)                                                              AS ready_pools,
    count(*) FILTER (WHERE p.learner_level IS NULL)                       AS unstamped,
    count(*) FILTER (
        WHERE p.learner_level IS NOT NULL
          AND p.learner_level IS DISTINCT FROM COALESCE(n.learner_level, u.learner_level, 'COLLEGE')
    )                                                                     AS at_risk,
    count(*) FILTER (WHERE n.learner_level IS NOT NULL)                   AS source_note_has_level
FROM exam_question_pool p
JOIN study_packs sp ON sp.id = p.study_pack_id
JOIN notes n        ON n.id = sp.note_id
JOIN users u        ON u.id = sp.owner_user_id
WHERE p.generation_status = 'READY';

-- `unstamped` rows are already treated as a mismatch today, so they are not new exposure --
-- but count them separately rather than folding them into `at_risk`, or the number reads worse
-- than it is and pushes the PR toward a migration it may not need.

-- ============================================================================
-- QUERY B — the same question for the question bank
-- ============================================================================
-- The bank has no refresh path, so a mismatch here does not regenerate. It makes rows
-- invisible to claims instead -- cheaper, but it silently shrinks Redo Missed Questions.

SELECT
    count(*)                                                              AS bank_rows,
    count(DISTINCT b.user_id)                                             AS affected_users,
    count(*) FILTER (WHERE b.learner_level IS NULL)                       AS unstamped,
    count(*) FILTER (
        WHERE b.learner_level IS NOT NULL
          AND b.learner_level IS DISTINCT FROM COALESCE(n.learner_level, u.learner_level, 'COLLEGE')
    )                                                                     AS at_risk,
    count(*) FILTER (WHERE b.last_known_outcome = 'INCORRECT')            AS incorrect_rows
FROM challenge_quiz_question_bank b
JOIN study_packs sp ON sp.id = b.study_pack_id
JOIN notes n        ON n.id = sp.note_id
JOIN users u        ON u.id = b.user_id
WHERE b.claimed_session_id IS NULL;

-- `incorrect_rows` matters on its own: those are the rows behind Redo Missed Questions. If a
-- large share of them is `at_risk`, learners lose a feature they can see, which is worse than
-- an invisible regeneration.

-- ============================================================================
-- QUERY C — the read/write divergence PR 6a must not create
-- ============================================================================
-- PostSessionNextStepService:80 passes `user.getLearnerLevel()` -- the READER's level -- into
-- countEligibleIncorrectQuestions, and that service never touches the resolver. If PR 6a fixes
-- the five ChallengeQuizService write sites and leaves this read site alone, the availability
-- check and the claim query use DIFFERENT levels: the Redo Missed Questions CTA appears when
-- nothing is claimable, or hides when questions exist.
--
-- This counts the users for whom those two levels already disagree -- i.e. how many learners
-- would see that divergence the day PR 6a ships if this call site is not threaded too.

SELECT
    count(*)                        AS packs_with_incorrect_rows,
    count(DISTINCT sp.owner_user_id) AS distinct_owners,
    count(*) FILTER (
        WHERE COALESCE(n.learner_level, u.learner_level, 'COLLEGE') IS DISTINCT FROM COALESCE(u.learner_level, 'COLLEGE')
    )                               AS reader_level_differs_from_note_level
FROM study_packs sp
JOIN notes n ON n.id = sp.note_id
JOIN users u ON u.id = sp.owner_user_id
WHERE EXISTS (
    SELECT 1 FROM challenge_quiz_question_bank b
    WHERE b.study_pack_id = sp.id AND b.last_known_outcome = 'INCORRECT'
);

-- Any non-zero `reader_level_differs_from_note_level` means the divergence is live, not
-- theoretical, and PostSessionNextStepService must be threaded in the SAME PR as the write
-- sites. Expect this to be zero today only if no note carries an authored level yet -- V104
-- just gave 27 notes one, so it will not stay zero.

-- ============================================================================
-- HANDING THE ANSWER BACK
-- ============================================================================
-- Paste all three result rows, and say which variant you ran. The decisions they drive:
--
--   Query A `at_risk`  -> does PR 6a carry a pre-stamping migration, or just a RELEASES.md note?
--   Query B `at_risk` + `incorrect_rows` -> is the bank impact learner-visible?
--   Query C            -> is PostSessionNextStepService in scope for 6a? (Almost certainly yes.)
--
-- If you ran the V101 variant, say so explicitly -- `at_risk_floor` is not `at_risk`, and the
-- two must not be recorded as the same number. A large floor decides the PR shape on its own;
-- a small floor decides nothing and the file must be re-run after deployment.
--
-- Sanity check on the variant, from a local run 2026-08-03: on a database where no note has an
-- authored level, `at_risk_floor` and the full Query A `at_risk` returned the SAME value
-- (35 of 46 READY pools). That is the expected relationship and is a cheap way to confirm the
-- variant is measuring what it claims -- if they ever differ on a pre-V104 database, one of the
-- two queries is wrong.
--
-- None of these reopen an ADR-001 decision. They size a consequence the ADR left to the
-- implementing PR, which is what `09` PR 6 means by "state the staleness behavior explicitly."
