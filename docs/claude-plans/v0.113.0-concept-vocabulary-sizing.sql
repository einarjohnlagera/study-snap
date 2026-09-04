-- v0.113.0 — Canonical Concept Identity: vocabulary sizing read
-- (Deferred from v0.112.0, which was repointed to the 2026-09-04 production outage. Unchanged otherwise;
--  it is owner-executed and can run in parallel with v0.112.0.)
-- READ-ONLY. Run against PRODUCTION. Paste all six result blocks back verbatim.
--
-- ⚠️ WHY THIS IS LOAD-BEARING AND NOT BOOKKEEPING.
-- The recorded architecture finding says canonical concept identity is "the same problem ADR-001
-- solved for programs". ADR-001 solved it with a CURATED CATALOG of 41 rows. That solution only
-- transfers if the concept vocabulary is the same order of magnitude. If Q2 returns ~500 the
-- catalog pattern is buildable by a curator; if it returns ~50,000 it is not, and the minting rule
-- must be something else. THE MINTING DECISION IS DOWNSTREAM OF Q2 AND MUST NOT BE TAKEN BEFORE IT.
--
-- ⚠️ Q4 is the one that decides whether the release is worth building at all. If almost no concept
-- string is shared across packs, cross-pack identity has nothing to unify and the release should be
-- rescoped or dropped. Report the number even if it is small — especially if it is small.

-- Q1 — How much evidence exists at all. Sizes the backfill question.
SELECT
    COUNT(*)                                   AS concept_health_rows,
    COUNT(DISTINCT user_id)                    AS users_with_evidence,
    COUNT(DISTINCT study_pack_id)              AS packs_with_evidence,
    COUNT(DISTINCT concept)                    AS distinct_concept_strings,
    COUNT(DISTINCT lower(btrim(concept)))      AS distinct_normalized_strings
FROM concept_health;

-- Q2 — THE SIZING NUMBER. The full authored vocabulary, not just what has been assessed.
-- key_concepts is JSONB (V1:40), so this unnests the array across every study pack.
SELECT
    COUNT(*)                                        AS total_concept_mentions,
    COUNT(DISTINCT btrim(kc))                       AS distinct_concepts_exact,
    COUNT(DISTINCT lower(btrim(kc)))                AS distinct_concepts_normalized,
    ROUND(COUNT(*)::numeric
        / NULLIF(COUNT(DISTINCT lower(btrim(kc))), 0), 2) AS mentions_per_distinct_concept
FROM study_packs sp
CROSS JOIN LATERAL jsonb_array_elements_text(sp.key_concepts) AS kc
WHERE jsonb_typeof(sp.key_concepts) = 'array';

-- Q3 — Vocabulary size scoped by Domain Context. Tests whether the v0.111.0 taxonomy BOUNDS the
-- problem: if concepts partition cleanly by domain, a per-domain vocabulary is far smaller than a
-- global one and the catalog pattern may transfer after all.
SELECT
    COALESCE(n.domain_context, '(unset)')            AS domain_context,
    COUNT(DISTINCT sp.id)                            AS packs,
    COUNT(DISTINCT lower(btrim(kc)))                 AS distinct_concepts_normalized
FROM study_packs sp
JOIN notes n ON n.id = sp.note_id
CROSS JOIN LATERAL jsonb_array_elements_text(sp.key_concepts) AS kc
WHERE jsonb_typeof(sp.key_concepts) = 'array'
GROUP BY 1
ORDER BY 3 DESC;

-- Q4 — THE JUSTIFYING NUMBER. How often the SAME concept string already appears in more than one
-- pack. This is the population canonical identity would unify. Reported two ways because
-- normalizeConcept is trim()-only today, so the exact-match row is what the product sees now.
SELECT
    COUNT(*) FILTER (WHERE pack_count > 1)          AS concepts_in_multiple_packs,
    COUNT(*)                                        AS concepts_total,
    ROUND(100.0 * COUNT(*) FILTER (WHERE pack_count > 1)
        / NULLIF(COUNT(*), 0), 1)                   AS pct_shared,
    MAX(pack_count)                                 AS max_packs_one_concept
FROM (
    SELECT lower(btrim(kc)) AS c, COUNT(DISTINCT sp.id) AS pack_count
    FROM study_packs sp
    CROSS JOIN LATERAL jsonb_array_elements_text(sp.key_concepts) AS kc
    WHERE jsonb_typeof(sp.key_concepts) = 'array'
    GROUP BY 1
) t;

-- Q5 — The same question asked of REAL LEARNER EVIDENCE rather than authored vocabulary. A concept
-- a learner has been assessed on in two different packs is the exact case the recommendation engine
-- cannot currently see. This is the population the ONE converted read path would newly serve.
SELECT
    COUNT(*) FILTER (WHERE pack_count > 1)          AS user_concepts_across_packs,
    COUNT(*)                                        AS user_concepts_total,
    ROUND(100.0 * COUNT(*) FILTER (WHERE pack_count > 1)
        / NULLIF(COUNT(*), 0), 1)                   AS pct_cross_pack,
    COUNT(DISTINCT user_id) FILTER (WHERE pack_count > 1) AS users_affected
FROM (
    SELECT user_id, lower(btrim(concept)) AS c, COUNT(DISTINCT study_pack_id) AS pack_count
    FROM concept_health
    GROUP BY 1, 2
) t;

-- Q6 — The top shared concepts, as a hand-checkable sample. Q4/Q5 are counts; this is what makes
-- them believable or not. A list of genuinely identical concepts argues FOR identity; a list of
-- generic strings ("Introduction", "Overview", "Definitions") argues that the collisions are noise
-- and that identity would unify things that are not actually the same.
SELECT
    lower(btrim(kc))                 AS concept,
    COUNT(DISTINCT sp.id)            AS packs,
    COUNT(DISTINCT n.domain_context) AS distinct_domain_contexts,
    COUNT(DISTINCT n.subject)        AS distinct_subjects
FROM study_packs sp
JOIN notes n ON n.id = sp.note_id
CROSS JOIN LATERAL jsonb_array_elements_text(sp.key_concepts) AS kc
WHERE jsonb_typeof(sp.key_concepts) = 'array'
GROUP BY 1
HAVING COUNT(DISTINCT sp.id) > 1
ORDER BY 2 DESC
LIMIT 40;
