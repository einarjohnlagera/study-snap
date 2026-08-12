-- v0.74.0 -- server-derived Quick Review score, used as the Quiz tab mastery gate.
--
-- From this migration onward, QuickReviewSessionService.completeSession derives
-- verified_correct_answers from the SAME server-side evaluation that feeds ConceptHealth
-- (QuizSessionReviewUtils.computeConceptBreakdownForStoredSelections), rather than from the
-- score the client reports. The gate and the integrity signal it protects must not be
-- computed from different inputs.
ALTER TABLE quick_review_sessions
    ADD COLUMN verified_correct_answers INTEGER;

-- Backfill: pre-deploy sessions are GRANDFATHERED from their reported score.
--
-- This is deliberate, and the alternative was tried and rejected. Re-deriving the score in
-- SQL means re-implementing answer resolution against the RAW jsonb, which bypasses
-- QuizItem's @JsonCreator -- and that constructor is where resolution actually happens
-- (QuizItem.resolveCorrectIndex: correctIndex -> answerIndex -> correctAnswerIndex ->
-- MULTI_SELECT correctIndices[0] -> sanitized answer text -> answer-as-LETTER).
--
-- That last step is not an edge case, it is the normal case: generated quizzes do not store
-- correctIndex at all (it is absent from prompts/study-pack-v1/schema.json), and "answer"
-- holds a LETTER -- developer.txt:15 pins it to "A" | "B" | "C" | "D". A SQL scorer matching
-- that letter against choice TEXT resolves nothing, scores every question wrong, and
-- backfills every existing learner as not-mastered -- locking them out of a Quiz tab they
-- have been using, on deploy day. That is the exact regression this backfill exists to
-- prevent.
--
-- Grandfathering keeps a fourth copy of answer resolution from existing. The cost is that a
-- pre-deploy row whose client over-reported is trusted; that is bounded, one-time, and
-- strictly better than locking out a real learner. Sessions completed AFTER this migration
-- are server-derived with no such allowance.
--
-- A null correct_answers stays null: a session with no recorded score is no evidence of
-- mastery, and null correctly reads as not-mastered.
UPDATE quick_review_sessions
SET verified_correct_answers = correct_answers
WHERE session_mode = 'QUICK_REVIEW'
  AND status = 'COMPLETED'
  AND correct_answers IS NOT NULL;
