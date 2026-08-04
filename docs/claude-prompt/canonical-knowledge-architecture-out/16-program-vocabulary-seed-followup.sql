-- PR 5 input, round 2 — the seed list itself, plus the third source nobody read.
-- Written 2026-08-05. Run against PRODUCTION. Read-only: no INSERT, UPDATE, DELETE, or DDL.
--
-- ============================================================================
-- WHY THIS EXISTS, WHEN `11` ALREADY RAN
-- ============================================================================
-- `11-program-vocabulary-seed.sql` ran against production 2026-08-04 and its results are in
-- `15-vocabulary-and-impact-results.md`. But `15` recorded the *findings* from Query A, not
-- Query A's **output**. `11`'s own closing section says "Paste Query A's full output (all 32
-- rows)"; what `15` actually contains is ~13 named values — the 5 user-side-only ones, the
-- three computing values, the four settled judgment calls, and the level labels from Query D.
--
-- The ~19 remaining values are the ones the catalog is mostly made of, including the largest
-- programs in the library (Architecture ~837 notes, Education, Nursing, Accountancy, Civil
-- Engineering, Pharmacy, Law, Medicine, Criminology, Psychology, Aviation, Business
-- Administration, Physical Therapy, Electrical/Mechanical Engineering). Their exact bytes exist
-- nowhere in the repo. `05-vocabulary-results.md` lists some in prose, which `11` already ruled
-- insufficient: "prose loses exact bytes (trailing spaces, en dash vs hyphen, case)".
--
-- Severity, stated honestly: a value missing from the seed degrades to a **NULL FK**, which is
-- already the documented behavior for unmappable values. Nothing breaks and nothing is lost.
-- But a silently-absent Architecture would not surface until Release B reads the FK, so this is
-- worth one minute now.
--
-- ============================================================================
-- THE NEW FINDING — `note_collections.course_program` was never in scope
-- ============================================================================
-- `11` unioned `notes` and `users`. It did **not** read `note_collections.course_program`,
-- added by `V76__collection_visibility_and_plan_source.sql` and indexed on
-- `(visibility, course_program)`.
--
-- That matters because of a FIFTH en-dash value no vocabulary document has ever mentioned:
-- `Medical - Surgical Nursing` (with U+2013, not the hyphen written here). It is hardcoded in
-- `ExamGoalConfig:15` and `frontend/lib/exam-hub-config.ts:26` as PNLE's second course/program,
-- and both files carry the comment "CourseProgram values must match production DB values
-- exactly". `RELEASES.md:253` describes Exam Hub enrichment as a published-**set** lookup per
-- configured course/program — i.e. it matches collections, not notes. So the most likely
-- explanation is that this value lives in `note_collections` and nowhere else, which is exactly
-- why every prior audit missed it.
--
-- `15`'s FINDING 1 said `Special Needs Education - Generalist` was the fourth en-dash value and
-- that no audit had been in range of it. This is the fifth, and the same is true again.
--
-- **This query deliberately does not type that string.** Retyping an en dash to probe for an
-- en-dash bug is how the trap catches the person setting it. Query B enumerates every non-ASCII
-- value across all three tables instead, which is robust by construction and would have found
-- both the fourth and the fifth without anyone knowing to look.

-- ============================================================================
-- QUERY 0 — schema/state check
-- ============================================================================

SELECT version, description, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 4;

-- Expected max: V105. If it is higher, say so — something shipped that this scoping did not see.

-- ============================================================================
-- QUERY A — THE SEED LIST (three-way). This is the one PR 5 consumes.
-- ============================================================================
-- Every distinct program value across notes, users, AND collections, with exact bytes visible.
--
-- Read the columns in this order:
--   * `quoted`        — quote_literal output. Copy the seed strings from THIS column, verbatim.
--                       Never retype a value that has has_non_ascii = TRUE.
--   * `char_len`      — compare against visible length; a mismatch means hidden whitespace.
--   * `has_non_ascii` — TRUE marks en-dash (and any other non-ASCII) values.
--   * `note_count` / `user_count` / `collection_count` — where the value is actually used.
--   * `sources`       — which tables carry it. A row reading only `collections` is a value that
--                       every prior audit was structurally unable to see.

WITH note_side AS (
    SELECT course_program AS value, count(*) AS note_count
    FROM notes
    WHERE course_program IS NOT NULL AND btrim(course_program) <> ''
    GROUP BY course_program
),
user_side AS (
    SELECT course_program AS value, count(*) AS user_count
    FROM users
    WHERE course_program IS NOT NULL AND btrim(course_program) <> ''
    GROUP BY course_program
),
collection_side AS (
    SELECT course_program AS value, count(*) AS collection_count
    FROM note_collections
    WHERE course_program IS NOT NULL AND btrim(course_program) <> ''
    GROUP BY course_program
),
all_values AS (
    SELECT value FROM note_side
    UNION
    SELECT value FROM user_side
    UNION
    SELECT value FROM collection_side
)
SELECT
    quote_literal(a.value)                      AS quoted,
    length(a.value)                             AS char_len,
    a.value ~ '[^\x20-\x7E]'                    AS has_non_ascii,
    COALESCE(n.note_count, 0)                   AS note_count,
    COALESCE(u.user_count, 0)                   AS user_count,
    COALESCE(c.collection_count, 0)             AS collection_count,
    concat_ws(
        '+',
        CASE WHEN n.value IS NOT NULL THEN 'notes' END,
        CASE WHEN u.value IS NOT NULL THEN 'users' END,
        CASE WHEN c.value IS NOT NULL THEN 'collections' END
    )                                           AS sources
FROM all_values a
LEFT JOIN note_side n       ON n.value = a.value
LEFT JOIN user_side u       ON u.value = a.value
LEFT JOIN collection_side c ON c.value = a.value
ORDER BY note_count DESC, user_count DESC, collection_count DESC, quoted;

-- Expected: >= 32 rows. The 2026-08-04 audit found 32 across notes+users; any additional rows
-- here are collection-side values that were never in range of a prior audit.
--
-- **Paste this output in full.** It is the seed list. Summarising it reintroduces exactly the
-- exact-bytes problem this query exists to solve.

-- ============================================================================
-- QUERY B — enumerate every non-ASCII value across all three tables
-- ============================================================================
-- No literal is typed here on purpose. Prior probes searched for '%senior high%' and friends and
-- were therefore blind to any en-dash value whose name nobody had guessed. This finds them all.

WITH all_values AS (
    SELECT course_program AS value, 'notes' AS source FROM notes WHERE course_program IS NOT NULL
    UNION ALL
    SELECT course_program, 'users' FROM users WHERE course_program IS NOT NULL
    UNION ALL
    SELECT course_program, 'collections' FROM note_collections WHERE course_program IS NOT NULL
)
SELECT
    quote_literal(value)                        AS quoted,
    length(value)                               AS char_len,
    string_agg(DISTINCT source, '+' ORDER BY source) AS sources,
    count(*)                                    AS row_count
FROM all_values
WHERE btrim(value) <> ''
  AND value ~ '[^\x20-\x7E]'
GROUP BY value
ORDER BY quoted;

-- Expected: the three `Senior High - ...` strand values (U+2013), `Special Needs Education -
-- Generalist` (U+2013), and — the open question — whether a Medical/Surgical Nursing value
-- appears here at all, and if so from which source.
--
-- Three outcomes, each leading somewhere different:
--   * collections only  -> the catalog seed must be three-way, and PR 5's ExamGoalConfig
--                          retirement genuinely depends on it. Expected outcome.
--   * notes and/or users -> `15`'s write-up was incomplete rather than its query being narrow.
--                          Same seed fix; no new source.
--   * absent entirely   -> PNLE's second course/program has been matching zero rows in
--                          production. Pre-existing Exam Hub defect. LOG IT, do not fix it here,
--                          and do not let it expand PR 5.

-- ============================================================================
-- QUERY C — collision re-check across all three sources
-- ============================================================================
-- `11`'s Query B found zero case/punctuation collisions across notes+users, and that finding is
-- load-bearing: it is why PR 5 is a curated seed and not a reconciliation project. Adding a third
-- source can only add collisions, so re-confirm over the wider union.

WITH all_values AS (
    SELECT course_program AS value FROM notes WHERE course_program IS NOT NULL
    UNION ALL
    SELECT course_program FROM users WHERE course_program IS NOT NULL
    UNION ALL
    SELECT course_program FROM note_collections WHERE course_program IS NOT NULL
)
SELECT
    lower(btrim(regexp_replace(value, '[‐-―]', '-', 'g'))) AS normalized,
    count(DISTINCT value)                                           AS distinct_spellings,
    string_agg(DISTINCT quote_literal(value), ' | ')                AS spellings
FROM all_values
WHERE btrim(value) <> ''
GROUP BY normalized
HAVING count(DISTINCT value) > 1
ORDER BY distinct_spellings DESC;

-- Expected: ZERO rows. Any row means two spellings of one program exist and the seed must pick
-- one and map the other — flag it rather than quietly choosing.

-- ============================================================================
-- QUERY D — collection-side detail, for the ExamGoalConfig decision
-- ============================================================================
-- If PR 5 (or a later item) retires ExamGoalConfig's hand-synced lists in favour of the catalog,
-- the catalog has to cover whatever the Exam Hub currently matches on. This shows what that is.

SELECT
    quote_literal(c.course_program) AS quoted,
    c.visibility,
    count(*)                        AS collections
FROM note_collections c
WHERE c.course_program IS NOT NULL AND btrim(c.course_program) <> ''
GROUP BY c.course_program, c.visibility
ORDER BY collections DESC, quoted;

-- A published (PUBLIC) collection under a course/program that the catalog would not contain is
-- precisely the row that silently disappears from an Exam Hub if the hardcoded list is retired
-- before the catalog covers it.

-- ============================================================================
-- HANDING THE ANSWER BACK
-- ============================================================================
-- Paste Query A in full and Query B in full. Queries C and D can be summarised — C is a row
-- count (expected 0), D only matters for values Query A shows as collection-only.
--
-- Record the result in `15-vocabulary-and-impact-results.md` (or a new `17-*.md`) rather than
-- only in a chat reply. `15` recording findings-but-not-output is the reason this second read
-- was needed at all, and that lesson is worth not repeating.
