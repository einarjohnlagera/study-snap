-- PR 5 input — the exact program vocabulary to seed `course_programs` from.
-- Written 2026-08-03. Run against PRODUCTION. Read-only: no INSERT, UPDATE, DELETE, or DDL.
--
-- ============================================================================
-- WHY THIS EXISTS
-- ============================================================================
-- `05-vocabulary-results.md` established the shape of the vocabulary — 27 note-side values,
-- 16 user-side, 32 in the union, zero character-level collisions — and classified most of them
-- by kind. What it does NOT contain is a seedable list:
--
--   * the 5 user-side-only values are counted but never named;
--   * values are quoted in prose, so exact bytes (trailing spaces, en dash vs hyphen, case)
--     are not recoverable from the doc.
--
-- A catalog seed needs exact strings. Guessing one wrong produces a row that matches nothing,
-- and because PR 5's FK is nullable and nothing reads it, that failure is silent until Release B.
-- Hence this query rather than transcribing `05`.
--
-- ============================================================================
-- WHAT PR 5 DOES WITH THE ANSWER (context for the judgment calls below)
-- ============================================================================
-- PR 5 creates `course_programs` + `program_families`, adds a nullable FK on `notes` and
-- `users` alongside the existing string columns, and seeds the catalog. **Nothing reads the FK
-- yet.** Unmappable values keep a NULL FK and their original string rather than being guessed.
--
-- Semantic calls already settled in `08`, restated so the output can be read against them:
--   `Bsed`                          -> Education (BSEd)
--   Computer Science / Information Technology / Software Engineering -> all three survive as
--                                      distinct programs; do not collapse
--   `Engineering`                   -> a program FAMILY, not a program
--   `Biology`                       -> a subject, not a program
--   `Civil Service`                 -> a goal/activity, not a program
--   `Professional / Board Exam Review` -> a goal/activity, not a program
--   `Self Study / Personal Learning`   -> a goal/activity, not a program
--
-- **Open, deferred here from PR 4 (ADR-001 second corollary):** whether `Grade School`,
-- `Junior High`, `High School`, and the three `Senior High` strands become catalog programs at
-- all. They are learner levels wearing a program label. PR 4 deliberately did NOT clear them,
-- on the grounds that "what counts as a program" is precisely this PR's question. Query D
-- below is the read that answers it.

-- ============================================================================
-- QUERY 0 — schema/state check
-- ============================================================================
-- Confirms which migrations production has, so Query D's columns are known to exist and its
-- results can be read correctly (V104 backfilled Grade School / Junior High).

SELECT version, description, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 4;

-- If the max version is < 104, Query D still runs but every `domain_context` will be NULL.
-- If it is < 102, drop `n.domain_context` / `n.learner_level` from Query D entirely.

-- ============================================================================
-- QUERY A — the seed list (this is the one PR 5 consumes)
-- ============================================================================
-- Every distinct program value across BOTH sides, with exact bytes made visible.
--
-- Read the columns in this order:
--   * `quoted`      — the value wrapped in quotes, so leading/trailing whitespace is visible.
--   * `char_len`    — compare against the visible length; a mismatch means hidden whitespace.
--   * `has_non_ascii` — TRUE marks the en-dash values. Copy those from `quoted` verbatim into
--                     the seed; do not retype them, and never normalize an en dash to a hyphen.
--   * `note_count` / `user_count` — a value with `user_count > 0` and `note_count = 0` is one
--                     of the 5 user-side-only values `05` never named.

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
)
SELECT
    quote_literal(COALESCE(n.value, u.value))            AS quoted,
    length(COALESCE(n.value, u.value))                   AS char_len,
    COALESCE(n.value, u.value) ~ '[^\x20-\x7E]'          AS has_non_ascii,
    COALESCE(n.note_count, 0)                            AS note_count,
    COALESCE(u.user_count, 0)                            AS user_count,
    CASE
        WHEN n.value IS NULL THEN 'user-side only'
        WHEN u.value IS NULL THEN 'note-side only'
        ELSE 'both'
    END                                                  AS side
FROM note_side n
FULL OUTER JOIN user_side u ON u.value = n.value
ORDER BY note_count DESC, user_count DESC, quoted;

-- Expected: 32 rows (audit, 2026-08-03), of which 5 are `user-side only`.
-- If the count has moved, say so — `StudyPackService:746` lets an LLM suggestion write into this
-- field, so new values can appear between the audit and the seed without anyone typing one.

-- ============================================================================
-- QUERY B — case-insensitive collision check
-- ============================================================================
-- The audit found ZERO character-level collisions, which is why PR 5 is a cheap curated seed
-- rather than a reconciliation project. That finding is load-bearing, and it is now weeks old.
-- Re-confirm it immediately before seeding: any group here with count > 1 means two spellings
-- of the same program exist and the seed must pick one and map the other.

WITH all_values AS (
    SELECT course_program AS value FROM notes WHERE course_program IS NOT NULL
    UNION ALL
    SELECT course_program FROM users WHERE course_program IS NOT NULL
)
SELECT
    lower(btrim(regexp_replace(value, '[‐-―]', '-', 'g'))) AS normalized,
    count(DISTINCT value)                                            AS distinct_spellings,
    string_agg(DISTINCT quote_literal(value), ' | ')                 AS spellings
FROM all_values
WHERE btrim(value) <> ''
GROUP BY normalized
HAVING count(DISTINCT value) > 1
ORDER BY distinct_spellings DESC;

-- Expected: ZERO rows. Any row is a genuine finding that changes PR 5's scope from "seed a
-- catalog" to "seed a catalog and reconcile N variants" — flag it rather than quietly picking.

-- ============================================================================
-- QUERY C — what each value is attached to, for the judgment calls
-- ============================================================================
-- For the values whose classification is a judgment call rather than obvious, the deciding
-- evidence is what kind of content sits under them. A "program" with three notes all in one
-- subject is usually a subject or a goal wearing a program label.

SELECT
    quote_literal(n.course_program)       AS quoted,
    count(*)                              AS notes,
    count(DISTINCT n.subject)             AS distinct_subjects,
    count(*) FILTER (WHERE n.visibility = 'PUBLIC') AS public_notes,
    string_agg(DISTINCT n.subject, ', ' ORDER BY n.subject) FILTER (WHERE n.subject IS NOT NULL) AS subjects
FROM notes n
WHERE n.course_program IN (
        'Engineering',                       -- family, not a program (settled)
        'Biology',                           -- subject, not a program (settled)
        'Civil Service',                     -- goal (settled)
        'Professional / Board Exam Review',  -- goal (settled)
        'Self Study / Personal Learning',    -- goal (settled)
        'Computer Science',
        'Information Technology',
        'Software Engineering'
      )
GROUP BY n.course_program
ORDER BY notes DESC;

-- The three computing values are here because `08` kept all three as distinct programs; this is
-- the read that confirms they carry genuinely different subject mixes rather than being three
-- names for one thing. If their subject lists are near-identical, reopen that call.

-- ============================================================================
-- QUERY D — the deferred call: are the K-12 level labels programs?
-- ============================================================================
-- PR 4 set domain_context + learner_level on the Grade School / Junior High notes and
-- deliberately left course_program in place, deferring "is this a program?" to PR 5.
-- This shows the current post-V104 state of all six level labels at once.

SELECT
    quote_literal(n.course_program) AS quoted,
    n.learner_level,
    n.domain_context,
    count(*)                        AS notes,
    count(*) FILTER (WHERE n.visibility = 'PUBLIC') AS public_notes
FROM notes n
WHERE n.course_program ILIKE '%grade school%'
   OR n.course_program ILIKE '%junior high%'
   OR n.course_program ILIKE '%high school%'
   OR n.course_program ILIKE '%senior high%'
GROUP BY n.course_program, n.learner_level, n.domain_context
ORDER BY quoted, notes DESC;

-- Expected post-V104: Grade School (3) and Junior High (24) now carry their level plus
-- GENERAL_EDUCATION; High School (11) and the three Senior High strands still carry neither.
--
-- The judgment this feeds:
--   * `Grade School` / `Junior High` — now fully described by learner_level + domain_context.
--     Nothing is lost by excluding them from the catalog, which is the natural conclusion.
--   * `Senior High – STEM / – ABM / – HUMSS` — genuine curriculum STRANDS, not bare levels.
--     They plausibly earn catalog rows even though the other four do not. Note the en dash.
--   * `High School` — do not decide here. It is PR 4b's classification, and a note that stays
--     unclassifiable keeps this label as its only classification (ADR-001 Legacy-data rule 1).
--     Excluding it from the catalog is fine; CLEARING it is not, and is not PR 5's to do either.

-- ============================================================================
-- HANDING THE ANSWER BACK
-- ============================================================================
-- Paste Query A's full output (all 32 rows) and Query B's row count. Those two are what the
-- seed is written from. Queries C and D inform the classification of individual values and can
-- be summarised rather than pasted in full.
--
-- Values classified as NOT programs (families, subjects, goals, bare levels) still need to be
-- listed explicitly in the PR 5 prompt as deliberate exclusions -- otherwise the next session
-- reading the seed cannot tell a considered omission from an oversight.
