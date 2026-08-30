-- Heal grant rows left live on relationships that were terminated before v0.97.0.
--
-- WHY. Until v0.97.0 revoke() did not cut grant rows, and the terminal early-return that now heals
-- them only fires if someone calls revoke AGAIN — which the UI stops offering on a terminal row. So a
-- relationship revoked before that release keeps live grants and reports *SharedByMe: true forever.
--
-- ⚠️ DISPLAY-ONLY, AND IT MUST STAY DESCRIBED THAT WAY. No access was open at any point:
-- LinkedLearnerGrantAuthorizationService requires the relationship to be ACCEPTED, and every other
-- reader (note shares, the progress read) re-checks the same thing. What was broken is the
-- connection list asserting a sharing act on a relationship that no longer exists.
--
-- ⚠️ TERMINAL STATUSES ONLY — NEVER THE CONSENT PAUSE. This is the single most important line here.
-- A v0.89.1 birth-year correction returns an ACCEPTED relationship to PENDING, and v0.93.0 made the
-- grant row SURVIVE that pause BY DESIGN: *SharedByMe reflects the row, so it reports the learner's
-- own standing act of sharing and what resumes on re-acceptance. Cutting a paused relationship's
-- grants would make a learner's own toggle read OFF while they never touched it, and sharing would
-- silently fail to resume. PENDING is therefore absent from the status list below, deliberately.
--
-- ⚠️ Idempotent via `revoked_at IS NULL`, so re-running changes nothing.
--
-- ⚠️ ONE-TIME REPAIR OF HISTORY, not a mechanism. Going forward the runtime rule in
-- LinkedLearnerService.revoke and LinkedLearnerRequestExpiryWorker does this at the moment of
-- transition; this statement exists only because those rows predate it.
UPDATE linked_learner_grants g
   SET revoked_at = now()
  FROM linked_learner_relationships r
 WHERE r.id = g.relationship_id
   AND g.revoked_at IS NULL
   AND r.status IN ('REVOKED', 'EXPIRED');
