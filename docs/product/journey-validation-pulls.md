# Journey Validation Pulls

Two SQL pulls to run **before** building the "Journey" direction (goal-first, curated-plan-matched study experience with goal-scoped readiness). They answer the only two questions that decide whether Journey is worth building and whether it can serve real learners on day one.

- **Pull 1 — Adopt → retention funnel:** do users who adopted a curated plan return more than those who didn't? If yes, the bundle hypothesis has legs. If adopters *also* don't return, the bundle is not the retention lever and Journey would polish the wrong thing.
- **Pull 2 — Curated-plan inventory:** how many goals actually have a complete, Study-Pack-ready plan a reviewer could trust? This caps how many learners Journey can serve on day one — the risk that moved when we chose "match a curated plan" over AI synthesis.

Schema notes (source of truth):

- "Who adopted" = `note_collections.source_plan_id IS NOT NULL` — a durable row, not telemetry (analytics had a drop bug; do not rely on `STUDY_PLAN_ADOPTED` events for counts).
- "Study-Pack-ready" = the `study_packs` join the app itself uses for readiness (`study_packs.note_id`), not the note status enum.
- All quiz modes share `quick_review_sessions`, so a "session" there captures Quick Review / Challenge / Adaptive / Long Exam / Board Exam return activity.

---

## Pull 1 — Adopt → retention funnel

```sql
-- Run this first so day-boundaries are PH-correct (created_at is timestamptz):
SET TIME ZONE 'Asia/Manila';

-- 1a. HEADLINE: return rate, adopters vs non-adopters (among activated users)
-- "activated" = has >=1 quiz session ever (any mode — they share quick_review_sessions)
-- "returned"  = practiced on >=2 distinct calendar days
WITH adopters AS (
    SELECT owner_user_id AS user_id, MIN(created_at) AS adopted_at
    FROM note_collections
    WHERE source_plan_id IS NOT NULL          -- adopted from a curated source plan
    GROUP BY owner_user_id
),
user_activity AS (
    SELECT user_id, COUNT(DISTINCT created_at::date) AS active_days
    FROM quick_review_sessions
    GROUP BY user_id
)
SELECT
    CASE WHEN a.user_id IS NOT NULL THEN 'adopter' ELSE 'non_adopter' END AS cohort,
    COUNT(*)                                                      AS activated_users,
    COUNT(*) FILTER (WHERE ua.active_days >= 2)                   AS returned_users,
    ROUND(100.0 * COUNT(*) FILTER (WHERE ua.active_days >= 2)
          / NULLIF(COUNT(*), 0), 1)                              AS return_rate_pct
FROM user_activity ua
LEFT JOIN adopters a ON a.user_id = ua.user_id
GROUP BY 1
ORDER BY 1;
```

```sql
-- 1b. TIGHTER (causal-ish): of users who adopted, how many practiced AFTER adopt day?
WITH adopters AS (
    SELECT owner_user_id AS user_id, MIN(created_at) AS adopted_at
    FROM note_collections
    WHERE source_plan_id IS NOT NULL
    GROUP BY owner_user_id
)
SELECT
    COUNT(*) AS adopters,
    COUNT(*) FILTER (WHERE EXISTS (
        SELECT 1 FROM quick_review_sessions q
        WHERE q.user_id = a.user_id
          AND q.created_at::date > a.adopted_at::date   -- a later day, not the adopt-day burst
    )) AS returned_after_adopt,
    ROUND(100.0 * COUNT(*) FILTER (WHERE EXISTS (
        SELECT 1 FROM quick_review_sessions q
        WHERE q.user_id = a.user_id
          AND q.created_at::date > a.adopted_at::date
    )) / NULLIF(COUNT(*), 0), 1) AS pct_returned_after_adopt
FROM adopters a;
```

**Decision rule:** if the `adopter` return rate in 1a is meaningfully above `non_adopter` (and 1b is not ~0), bundling earns the Journey build. If they are equal, stop — Journey would polish the wrong thing.

---

## Pull 2 — Curated-plan inventory (coverage & quality)

```sql
-- Per public (adoptable) plan: completeness + how often it's actually adopted.
SELECT
    c.id                                                         AS source_plan_id,
    u.role                                                       AS owner_role,        -- ADMIN = curated; USER = self-published
    c.course_program,
    c.title,
    COUNT(DISTINCT i.note_id)                                    AS total_notes,
    COUNT(DISTINCT sp.note_id)                                   AS notes_with_pack,
    COUNT(DISTINCT sp.note_id) FILTER (
        WHERE jsonb_array_length(sp.key_concepts) > 0)           AS packs_with_concepts, -- readiness needs concepts
    (COUNT(DISTINCT i.note_id) = COUNT(DISTINCT sp.note_id)
        AND COUNT(DISTINCT i.note_id) > 0)                       AS fully_ready,
    (SELECT COUNT(*)            FROM note_collections ac
       WHERE ac.source_plan_id = c.id)                           AS times_adopted,
    (SELECT COUNT(DISTINCT ac.owner_user_id) FROM note_collections ac
       WHERE ac.source_plan_id = c.id)                           AS distinct_adopters
FROM note_collections c
JOIN users u                  ON u.id = c.owner_user_id
LEFT JOIN note_collection_items i ON i.collection_id = c.id
LEFT JOIN study_packs sp          ON sp.note_id = i.note_id
WHERE c.visibility = 'PUBLIC'
GROUP BY c.id, u.role, c.course_program, c.title
ORDER BY times_adopted DESC, c.course_program;
```

```sql
-- One-number rollup: how many goals (course/programs) have >=1 fully-ready adoptable plan
SELECT COUNT(DISTINCT c.course_program) AS goals_with_a_ready_plan
FROM note_collections c
WHERE c.visibility = 'PUBLIC'
  AND c.course_program IS NOT NULL
  AND NOT EXISTS (                          -- no item missing a study pack
        SELECT 1 FROM note_collection_items i
        LEFT JOIN study_packs sp ON sp.note_id = i.note_id
        WHERE i.collection_id = c.id AND sp.note_id IS NULL
  )
  AND EXISTS (SELECT 1 FROM note_collection_items i WHERE i.collection_id = c.id);  -- non-empty
```

---

## Follow-up — assemble vs. seed (run after the first results)

The first run (2026-06) showed only 1 goal (Accountancy) with a ready plan. This decides whether fixing coverage is cheap (assemble plans from notes you already have) or expensive (seed content first).

```sql
-- Adoptable raw material per goal, vs how much is already assembled into a public plan
SELECT
    n.course_program,
    COUNT(DISTINCT n.id)        AS public_notes,
    COUNT(DISTINCT sp.note_id)  AS public_notes_with_pack,
    COUNT(DISTINCT ci.note_id)  AS notes_already_in_a_public_plan
FROM notes n
LEFT JOIN study_packs sp ON sp.note_id = n.id
LEFT JOIN note_collection_items ci ON ci.note_id = n.id
    AND ci.collection_id IN (SELECT id FROM note_collections WHERE visibility = 'PUBLIC')
WHERE n.visibility = 'PUBLIC'
GROUP BY n.course_program
ORDER BY public_notes_with_pack DESC;
```

- Goals with **many `public_notes_with_pack` but ~0 `notes_already_in_a_public_plan`** → assembly/curation ops (cheap, days). Build plans from existing content, then re-test adoption.
- Goals with **few public notes** → content seeding (Bulk Generation) is the prerequisite (weeks).
- Also tells you **where your public learners actually are** — build coverage for those goals, not assumed ones.

---

## Caveats (read the numbers honestly)

- **Small-n.** The whole base is ~153; the adopter cohort is a slice of that. Treat 1a/1b as *directional*, not statistically significant — pair with 2–3 interviews of actual adopters.
- **One instrumentation gap.** There is no "recommended-plan *seen*" event (`STUDY_PLAN_ADOPTED` fires on adopt; nothing fires on impression). So adoptions and post-adopt retention are measurable, but the saw→adopted conversion is not. If Pull 1 shows adopters retain but adoptions are low, instrument a recommended-card impression event next.
- **"Returned" = practiced again,** not "opened the app." A user who came back only to read a note (no session) will not count — deliberate; practice-return is the signal that matters for readiness.
- `study_packs` ↔ `note` is 1:1 in the model, so the `DISTINCT`s are just inflation-proofing.

## Tables referenced

- `note_collections` (`owner_user_id`, `visibility`, `course_program`, `source_plan_id`, `created_at`)
- `note_collection_items` (`collection_id`, `note_id`, `position`)
- `study_packs` (`note_id`, `owner_user_id`, `key_concepts` jsonb)
- `quick_review_sessions` (`user_id`, `created_at`, `completed_at`) — shared across all quiz modes
- `users` (`id`, `role`)
