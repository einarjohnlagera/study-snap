-- Provenance for the v0.157.0 Stage 2 sample-size bound and the dispatch-order fix.
-- READ-ONLY (SELECT only). Run against notelib-db-prod on 2026-09-23 via the Render MCP server.
-- A finished, one-off sizing read: it is a release artifact of v0.157.0, not planning output, so it needs
-- no Backlog row of its own. Re-run before relying on any number here; volumes decay.

-- 1) Per-type sends, last 90 days.
-- Result 2026-09-23: INACTIVITY 4387 sends / 234 recipients (2026-06-26..2026-09-22);
--                    DUE_CONCEPTS_DIGEST 773 sends / 121 recipients (from 2026-07-19).
--                    WEAK_CONCEPT, WEEKLY_SUMMARY, KNOWLEDGE_IMPACT_DIGEST: no rows.
SELECT email_type,
       COUNT(*)                 AS sent_last_90d,
       COUNT(DISTINCT user_id)  AS distinct_recipients,
       MIN(sent_at)             AS earliest_sent_at,
       MAX(sent_at)             AS latest_sent_at
FROM email_log
WHERE email_type IN ('INACTIVITY','WEAK_CONCEPT','WEEKLY_SUMMARY','DUE_CONCEPTS_DIGEST','KNOWLEDGE_IMPACT_DIGEST')
  AND sent_at >= now() - interval '90 days'
GROUP BY email_type
ORDER BY sent_last_90d DESC;

-- 2) Opt-in counts among active, verified users.
-- Result 2026-09-23: 398 active verified; inactivity 395, due_concepts_digest 152, weak_concept 2,
--                    weekly_summary 1, knowledge_impact_digest 0.
SELECT count(*) AS active_verified_users,
       count(*) FILTER (WHERE inactivity_reminders_enabled)            AS inactivity_opted_in,
       count(*) FILTER (WHERE weak_concept_reminders_enabled)          AS weak_concept_opted_in,
       count(*) FILTER (WHERE weekly_summary_reminders_enabled)        AS weekly_summary_opted_in,
       count(*) FILTER (WHERE due_concepts_digest_reminders_enabled)   AS due_concepts_digest_opted_in,
       count(*) FILTER (WHERE knowledge_impact_digest_reminders_enabled) AS knowledge_impact_digest_opted_in
FROM users
WHERE status = 'ACTIVE' AND email_verified_at IS NOT NULL;

-- 3) Recent rates (the basis for the bound; the 90-day totals are NOT, DUE_CONCEPTS_DIGEST ramped up in July).
-- Result 2026-09-23: DUE_CONCEPTS_DIGEST 156 in 14d, 377 in 28d, 120 recipients/28d;
--                    INACTIVITY 807 in 14d, 1586 in 28d, 231 recipients/28d.
SELECT email_type,
       COUNT(*) FILTER (WHERE sent_at >= now() - interval '14 days') AS sent_14d,
       COUNT(*) FILTER (WHERE sent_at >= now() - interval '28 days') AS sent_28d,
       COUNT(DISTINCT user_id) FILTER (WHERE sent_at >= now() - interval '28 days') AS recipients_28d
FROM email_log
WHERE email_type IN ('INACTIVITY','DUE_CONCEPTS_DIGEST')
GROUP BY email_type
ORDER BY email_type;

-- 4) Per Manila day, last 14 days: this is what showed INACTIVITY pinned at the 60/day budget cap.
-- Result 2026-09-23 (manila_day: inactivity / due_digest / transactional):
--   09-10: 60/2/0   09-11: 60/0/1   09-12: 60/9/0   09-13: 48/0/1   09-14: 60/5/0   09-15: 59/2/0   09-16: 60/21/0
--   09-17: 60/19/0  09-18: 60/13/0  09-19: 49/14/0  09-20: 60/16/0  09-21: 60/20/0  09-22: 60/13/0  09-23: 51/22/0 (partial day)
SELECT (sent_at AT TIME ZONE 'Asia/Manila')::date AS manila_day,
       COUNT(*) FILTER (WHERE email_type = 'INACTIVITY')           AS inactivity,
       COUNT(*) FILTER (WHERE email_type = 'DUE_CONCEPTS_DIGEST')  AS due_digest,
       COUNT(*) FILTER (WHERE email_type IN ('EMAIL_VERIFICATION','PASSWORD_RESET','SUBSCRIPTION_EXPIRY_7_DAY',
                                             'SUBSCRIPTION_EXPIRY_1_DAY','SUBSCRIPTION_EXPIRED')) AS transactional,
       COUNT(*) AS all_types
FROM email_log
WHERE sent_at >= now() - interval '14 days'
GROUP BY 1
ORDER BY 1;
