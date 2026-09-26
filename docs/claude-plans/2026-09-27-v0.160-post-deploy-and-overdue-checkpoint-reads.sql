-- READ-ONLY. Every statement below is a SELECT. Nothing here writes, locks or changes state.
-- Written 2026-09-27 at the v0.161.0 kickoff, for the owner to run in their DB console, because the
-- Render MCP server was unreachable (ENOTFOUND mcp.render.com) in the session that wrote it.
-- Paste each result back so it can be recorded in RELEASES.md / ROADMAP.md. Table and column names
-- were read from the Flyway migrations, not remembered. This file is a sizing/read artifact.

-- ============================================================================================
-- A. v0.160.0 POST-DEPLOY (records the two reads RELEASES.md v0.160.0 lists as owed)
-- ============================================================================================

-- A0. Did V150 run? Expect one row, success = true. Zero rows means the migration has not deployed.
SELECT version, description, success, installed_on
FROM flyway_schema_history
WHERE version = '150';

-- A1. How many collections carry an academic term? Expect 0 (no curator has termed a Year yet).
--     If this errors with "column term_label does not exist", V150 has not deployed.
SELECT count(*) AS collections_with_term
FROM note_collections
WHERE term_label IS NOT NULL;

-- A2. Non-admin-owned roots that V141 (or a later publish) stamped as published. Their children are
--     frozen for term edits. Expected count: 0 or a handful, unknown until read. Any non-zero row is a candidate
--     permanent lock to review, not by itself a defect.
SELECT count(*) AS non_admin_stamped_roots
FROM note_collections c
JOIN users u ON u.id = c.owner_user_id
WHERE c.last_update_published_at IS NOT NULL
  AND u.role <> 'ADMIN';

-- ============================================================================================
-- B. [CHECKPOINT due 2026-09-27] retention click/open tracking: is it EMITTING?
--    Kill criterion (stated in ROADMAP): zero of BOTH means tracking or the webhook subscription
--    is off: fix it and restart the clock, do not read an empty result as "nobody clicks".
--    State at v0.158.0 signoff, 2026-09-25: clicked_at IS NOT NULL = 0; opens = 6 (09-23), 3 (09-24).
-- ============================================================================================
SELECT count(*) AS clicked_rows FROM email_log WHERE clicked_at IS NOT NULL;

SELECT event_date, open_count
FROM email_open_daily_counts
ORDER BY event_date DESC
LIMIT 10;

-- ============================================================================================
-- C. [CHECKPOINTS due 2026-09-26] v0.91.0 / v0.92.0 / v0.93.0 / v0.94.0 / v0.95.0 (Learning Connections)
--    READ THE DENOMINATOR FIRST. If C1 shows no ACCEPTED relationships, all five of these were
--    never asked: the ROADMAP rows say that is a RE-DATE, not a verdict.
-- ============================================================================================

-- C1. Relationships by status (the denominator for every row below).
SELECT status, count(*) AS n
FROM linked_learner_relationships
GROUP BY status
ORDER BY status;

-- C2. v0.94.0: invitation links created, and how many were redeemed.
SELECT
    count(*)                                        AS links_created,
    count(*) FILTER (WHERE redeemed_at IS NOT NULL) AS links_redeemed,
    count(*) FILTER (WHERE revoked_at IS NOT NULL)  AS links_revoked
FROM linked_learner_invitation_links;

-- C3. v0.95.0: is a pile of unconfirmed redemptions accumulating? The expiry sweep DELETES the
--     provisional row, so a swept pile reads as empty: read BOTH numbers together (ROADMAP re-specified
--     read: count PENDING plus EXPIRED). KILL CRITERION: provisional rows that accumulate.
SELECT
    (SELECT count(*) FROM linked_learner_provisional_birth_years)                           AS provisional_rows_now,
    (SELECT count(*) FROM linked_learner_relationships WHERE status = 'PENDING')            AS relationships_pending,
    (SELECT count(*) FROM linked_learner_relationships WHERE status = 'EXPIRED')            AS relationships_expired;

-- C4. v0.92.0 / v0.93.0: grants ever made and currently live, by scope.
SELECT scope,
       count(*)                                    AS grants_ever,
       count(*) FILTER (WHERE revoked_at IS NULL)  AS grants_live
FROM linked_learner_grants
GROUP BY scope
ORDER BY scope;

-- C5. v0.91.0 / v0.92.0 / v0.93.0: the analytics events the checkpoints name. Check that EACH of the
--     five v0.91.0 events has a non-zero count before reading any ratio between them (the ROADMAP row
--     warns a refactor could stop one emitting).
SELECT event_type, count(*) AS n, min(created_at) AS first_seen, max(created_at) AS last_seen
FROM analytics_events
WHERE event_type IN (
    'NOTE_SHARED_WITH_CONNECTION', 'NOTE_SHARE_REVOKED', 'SHARED_NOTE_OPENED',
    'SHARED_STUDY_PACK_OPENED', 'SHARED_NOTE_COPIED',
    'CONNECTION_ACTIVITY_SHARED', 'CONNECTION_ACTIVITY_SHARE_REVOKED', 'CONNECTION_ACTIVITY_VIEWED',
    'CONNECTION_PROGRESS_SHARED', 'CONNECTION_PROGRESS_SHARE_REVOKED', 'CONNECTION_PROGRESS_VIEWED')
GROUP BY event_type
ORDER BY event_type;

-- ============================================================================================
-- D. [CHECKPOINT due 2026-09-26] v0.87.0 (did attribution make a failure diagnosable?)
--    NOT ANSWERABLE FROM THE DATABASE, by the row's own MEASUREMENT WARNING: the bulk-generation
--    receipt is deleted on read and TTL'd at 24 hours, so an empty table proves nothing. It is
--    owner-observed at the moment of a failure, with the server log as the durable backstop.
--    No query is provided on purpose.
-- ============================================================================================
