-- v0.127.0 -- single-note generation failure attribution.
--
-- A production regeneration failure on 2026-09-05 left ZERO database trace. The account hit its
-- monthly note-generation limit partway through a burst; the requests that had already passed the
-- pre-dispatch check and hit the limit INSIDE the async worker were swallowed by
-- StudyPackService.generateStudyPackFromExistingNoteAsync's blanket catch, marked FAILED, and told
-- the owner nothing. The incident was reconstructable only because Render logs had not yet rotated.
-- This is v0.87.0 (Failure Attribution) repeating on the single-note surface: that release gave bulk
-- generation a reason per failed topic (V119, `failed_topic_reasons`) and single-note never got the
-- equivalent.
--
-- ⚠️ THE SHAPE IS v0.87.0'S, NOT A SECOND ONE: a CODE plus a SAFE reason string, normalized by
-- BulkGenerationFailureReasonNormalizer. An AppException contributes its own code and message; every
-- other exception contributes UNEXPECTED_ERROR and a template naming only the exception CLASS.
-- ⚠️ RAW EXCEPTION TEXT IS NEVER PERSISTED for non-AppException failures.
--
-- ⚠️ generation_failed_at IS NOT REDUNDANT WITH updated_at, AND IT IS WHAT MAKES THE OTHER TWO
-- READABLE. The reason is deliberately NOT cleared when a later attempt succeeds -- the owner's manual
-- retry is exactly what destroyed the evidence in production, so a retry must not be able to erase it
-- again. That makes these columns a LAST-FAILURE record rather than a description of the current
-- status, and updated_at cannot disambiguate the two because the retry bumps it. Only a timestamp
-- written at the moment of failure can say whether a reason describes the row's current FAILED state
-- or an earlier, since-recovered one.
--
-- ⚠️ NULLABLE, NO BACKFILL, NO DEFAULT. Every existing row genuinely has no recorded reason and
-- inventing one would be a fabricated finding; NULL honestly means "not recorded".
ALTER TABLE notes
    ADD COLUMN generation_failure_code text,
    ADD COLUMN generation_failure_reason text,
    ADD COLUMN generation_failed_at timestamptz;
