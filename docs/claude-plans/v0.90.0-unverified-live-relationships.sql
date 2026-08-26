-- v0.90.0 — does the cross-user progress read need a verified-email gate?
--
-- Written 2026-08-26 during the second cold audit of `feat/invitation-integrity`. UNRUN.
-- Run this BEFORE signoff; it decides one open question and nothing else.
--
-- THE QUESTION
-- v0.90.0 gates every path that GRANTS or WIDENS access on a verified email, so any relationship
-- reaching ACCEPTED from now on implies a verified supporter. `getProgress` itself is deliberately
-- left ungated (see the v0.90.0 Known limitations in RELEASES.md).
--
-- The residue is historical: v0.89.x `invite` resolved an email to any ACTIVE account WITHOUT
-- requiring that invitee to be verified, and wrote a PENDING relationship row directly. If such a
-- row was accepted by an account that never verified, that supporter can read learner progress
-- today without ever having proved inbox control.
--
-- ⚠️ DECISION RULE, pre-committed before the read:
--   0 rows      -> leave `getProgress` ungated. Gating it would break working connections to close
--                  a window that does not exist. Record the number and move on.
--   >0 rows     -> add `authService.requireEmailVerified(callerUserId)` to
--                  LinkedLearnerProgressService.getProgress (or the shared authorization helper),
--                  and treat the affected supporters as a migration question, not a code question.
--
-- ⚠️ This is NOT attacker-arrangeable either way: the address was chosen by the inviter, so the
-- account holder is almost certainly the intended person who simply never clicked the verify link.
-- Severity is "unproven identity holds a live read", not "anyone can take one".

-- 1) THE LOAD-BEARING READ. Live relationships whose SUPPORTER has never verified an email.
select r.status,
       count(*)                                  as relationships,
       count(distinct r.supporter_user_id)       as unverified_supporters,
       min(r.created_at)                         as oldest,
       max(r.accepted_at)                        as newest_acceptance
from linked_learner_relationships r
join users u on u.id = r.supporter_user_id
where r.status in ('PENDING', 'ACCEPTED')
  and u.email_verified_at is null
group by r.status
order by r.status;

-- 2) The same for LEARNERS, for completeness. A learner is not granted a cross-user read, so this
--    does not gate the decision above -- but an unverified learner on a live link is worth seeing,
--    because the learner is who declared the birth year the consent gate keys on.
select r.status,
       count(*)                            as relationships,
       count(distinct r.learner_user_id)   as unverified_learners
from linked_learner_relationships r
join users u on u.id = r.learner_user_id
where r.status in ('PENDING', 'ACCEPTED')
  and u.email_verified_at is null
group by r.status
order by r.status;

-- 3) TOTAL live relationships, as the denominator. If read 1 returns 3 of 4, that is a different
--    situation from 3 of 400 -- the fix is the same, the migration conversation is not.
select status, count(*) as relationships
from linked_learner_relationships
group by status
order by status;
