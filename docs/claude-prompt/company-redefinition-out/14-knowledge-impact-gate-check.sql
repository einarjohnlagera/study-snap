-- Knowledge Impact un-park gate — run against production Postgres, read-only.
-- Companion to docs/claude-prompt/company-redefinition-out/09-knowledge-impact.md,
-- "## The gate (a one-time query pair nobody has run)". Scoped 2026-07-28, following
-- the same request pattern as 13-item4-profiletype-surge-check.sql.
--
-- ============================================================================
-- WHAT THIS ANSWERS
-- ============================================================================
-- Knowledge Impact (creator-recognition dashboard, "your notes helped N learners")
-- is Parked, data-gated: the CTO evaluation in 09 found ~232 of ~235 public notes are
-- official/admin-curated, only ~3 ever published by non-official users — but that
-- 232:3 ratio was a code-level inference, never an actual query. This runs the real
-- two-query gate 09 specifies, un-run until now.
--
-- UN-PARK THRESHOLD (per 09, not a judgment call): a real, meaningful count of
-- non-official creators (order-of-magnitude 20-30+, echoing this repo's existing bar
-- for "enough depth to justify building for this") AND/OR visible upward movement in
-- the raw community-publish rate. Track both — 09 explicitly does not want the count
-- alone treated as the whole answer, since a rising rate could matter even at low
-- absolute volume.
--
-- ============================================================================
-- RESOLVED INCONSISTENCY — read before trusting any COMMUNITY/OFFICIAL split below
-- ============================================================================
-- 09 flags a shipped inconsistency: the Java-level official-author classifier
-- (PublicProfileService.isOfficialAuthor) checks `email = OFFICIAL_AUTHOR_EMAIL OR
-- role = 'ADMIN'`; the SQL-level classifier (PublicLibraryRepositoryImpl's
-- officialAuthorPredicate, used to filter the public library's OFFICIAL/COMMUNITY
-- toggle) checks ONLY `role = 'ADMIN'` — it silently misses the email-based check.
-- OFFICIAL_AUTHOR_EMAIL is a hardcoded constant in PublicProfileService.java
-- ('einar.lagera@gmail.com' as of this writing — confirm it hasn't changed before
-- running). The queries below use the WIDER Java-level definition (email OR role),
-- since that's the one actually surfaced to users on public profile pages. This is a
-- real, separate finding worth fixing in the app code (not done here, out of scope
-- for a read-only gate check) — the public library's own OFFICIAL/COMMUNITY filter
-- currently under-counts official notes by this same gap.
--
-- SCHEMA REFERENCE (confirmed directly against entities, not assumed):
--   notes(id, owner_user_id, visibility)         -- visibility stored as STRING enum, 'PUBLIC'
--   users(id, email, role)                        -- role stored as STRING enum, 'ADMIN'
--   analytics_events(entity_id, event_type, created_at)  -- entity_id -> notes.id for these event types
--   Event types (confirmed against AdminDashboardService.getSummary(), the source of
--   the cited 12,211/2,094 aggregate): PUBLIC_NOTE_VIEWED, PUBLIC_NOTE_COPIED.

-- ============================================================================
-- QUERY 1 — distinct non-official creator count (the primary un-park number)
-- ============================================================================

SELECT
    COUNT(DISTINCT n.owner_user_id) AS distinct_community_creators
FROM notes n
JOIN users u ON u.id = n.owner_user_id
WHERE n.visibility = 'PUBLIC'
  AND NOT (u.role = 'ADMIN' OR u.email ILIKE 'einar.lagera@gmail.com');

-- ============================================================================
-- QUERY 2 — raw public-note count, official vs. community (sanity-checks the
-- cited 232:3 / ~98.7% ratio against a real query instead of an inference)
-- ============================================================================

SELECT
    (u.role = 'ADMIN' OR u.email ILIKE 'einar.lagera@gmail.com') AS is_official_author,
    COUNT(*) AS public_notes
FROM notes n
JOIN users u ON u.id = n.owner_user_id
WHERE n.visibility = 'PUBLIC'
GROUP BY is_official_author
ORDER BY is_official_author DESC;

-- ============================================================================
-- QUERY 3 — attribute the views/copies aggregate to official vs. community notes
-- ============================================================================
-- Reproduces AdminDashboardService.getSummary()'s 12,211 views / 2,094 copies global
-- aggregate, split by authorship. If this doesn't roughly sum to the current
-- admin-dashboard totals, treat that as a bug to investigate before trusting the split.

SELECT
    (u.role = 'ADMIN' OR u.email ILIKE 'einar.lagera@gmail.com') AS is_official_author,
    COUNT(*) FILTER (WHERE ae.event_type = 'PUBLIC_NOTE_VIEWED') AS views,
    COUNT(*) FILTER (WHERE ae.event_type = 'PUBLIC_NOTE_COPIED') AS copies
FROM analytics_events ae
JOIN notes n ON n.id = ae.entity_id
JOIN users u ON u.id = n.owner_user_id
WHERE n.visibility = 'PUBLIC'
  AND ae.event_type IN ('PUBLIC_NOTE_VIEWED', 'PUBLIC_NOTE_COPIED')
GROUP BY is_official_author
ORDER BY is_official_author DESC;

-- ============================================================================
-- QUERY 4 — community-publish rate over time (the "and/or rising rate" half of
-- the un-park threshold — a flat ~3-ever count could still show a recent uptick)
-- ============================================================================

SELECT
    date_trunc('month', n.created_at) AS publish_month,
    COUNT(*) AS community_public_notes_published
FROM notes n
JOIN users u ON u.id = n.owner_user_id
WHERE n.visibility = 'PUBLIC'
  AND NOT (u.role = 'ADMIN' OR u.email ILIKE 'einar.lagera@gmail.com')
GROUP BY publish_month
ORDER BY publish_month;
