-- A distinct terminal timestamp for EXPIRED, so retention stops depending on the deadline.
--
-- WHY. findVisibleForUser retains terminal rows on coalesce(revoked_at, expires_at) — the DEADLINE,
-- not the moment the row became terminal. Under a daily sweep those coincide, which is why it
-- shipped. But v0.98.0 added a 500-row batch bound and a pause hook, and both create backlogs: a
-- request swept more than request-ttl-days after its deadline becomes EXPIRED with a timestamp
-- ALREADY outside the retention window, so it vanishes from both parties' lists instantly and is
-- never shown as expired at all. Sweep paused 40 days, resumed: "pending, overdue" one day, nothing
-- the next — which defeats the exact purpose retention exists for.
--
-- ⚠️ expires_at IS NOT TOUCHED, AND THAT IS THE WHOLE POINT OF A SEPARATE COLUMN. It means THE
-- DEADLINE, for every status. v0.97.0 got this area wrong twice: once by overwriting expires_at with
-- the sweep time, once by backfilling it onto inherited rows and merely delaying a defect by 30
-- days. The lesson that survived both: a NULL expires_at is MEANINGFUL — it says the row is not on
-- the expiry clock at all, and that NULL is the entire mechanism protecting a consent-paused
-- relationship from being expired.
--
-- ⚠️ THE BACKFILL IS THE TRAP, and its shape was decided at kickoff rather than here.
-- EXPIRED ROWS ONLY, from expires_at. Those rows did expire at approximately their deadline, because
-- no backlog could exist before this release: the sweep ran daily and unbounded. So this is honest,
-- and it preserves their current retention behaviour exactly rather than changing it.
--
-- ⚠️ NOTHING is written for any other status. Both of v0.97.0's wrong attempts came from writing a
-- timestamp onto rows that should have had none, and a PENDING row must never carry an expired_at.
--
-- ⚠️ This is the THIRD amendment to v0.95.0's prohibition on adding a column to this table, raised
-- explicitly at kickoff. It ADDS A NEW FACT rather than reinterpreting an existing one — which is
-- precisely why a separate column was chosen over re-keying — and none of the three dated reads is
-- affected: 2026-09-19 groups by status, 2026-09-26 counts provisional rows plus PENDING/EXPIRED
-- relationships, and 2026-10-13 reads the carrier clock.
ALTER TABLE linked_learner_relationships
    ADD COLUMN expired_at timestamptz;

UPDATE linked_learner_relationships
   SET expired_at = expires_at
 WHERE status = 'EXPIRED'
   AND expires_at IS NOT NULL;

-- Retention reads this per terminal status, so index it alongside the status it belongs to.
CREATE INDEX idx_linked_learner_terminal_visibility
    ON linked_learner_relationships (status, expired_at, revoked_at);
