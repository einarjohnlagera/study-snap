-- Course/Program vocabulary audit + canonical-knowledge baselines.
-- Run against production Postgres, read-only. Scoped 2026-08-03.
--
-- Companion to 01-architecture-critique-and-migration-plan.md §1.2 (the vocabulary
-- queries that gate Step 2) and §9 (the success-metric baselines that must be taken
-- BEFORE Step 1 ships, because they measure the world without Domain Context in it).
--
-- ============================================================================
-- WHAT THIS ANSWERS
-- ============================================================================
-- The proposal assumed `note_course_program(note_id, course_program_id)` — i.e. that a
-- `course_programs` catalog exists to point at. It does not. `notes.course_program` and
-- `users.course_program` are plain free-text VARCHAR(120) columns (V38, V39), the write
-- path has no validation (`UpsertNoteRequest.courseProgram`), and an LLM suggestion can
-- write into the field (`StudyPackService:746`). So the backfill is a vocabulary
-- reconciliation project of genuinely unknown size.
--
-- Queries A–D size that reconciliation. Queries E–G take the §9 baselines while they are
-- still takeable. F is additionally the most useful single input to the Domain Context
-- taxonomy decision: it names the subjects that ALREADY span multiple programs, which are
-- exactly the notes that should become canonical shared notes first.
--
-- SCHEMA (confirmed by direct entity read, not assumed):
--   notes(id, owner_user_id, title, subject, course_program, visibility, status,
--         target_profile_type, created_at, updated_at)   -- NoteEntity
--   users(id, email, role, course_program, learner_level, profile_type)  -- UserEntity
--   note_collections(id, owner_user_id, title, visibility, course_program)  -- NoteCollectionEntity
--   note_collection_items(id, collection_id, note_id, position)  -- NoteCollectionItemEntity
--   visibility enums are 'PRIVATE' | 'PUBLIC' for both notes and collections.
--
-- OFFICIAL-AUTHOR PREDICATE: copied verbatim from PublicLibraryRepositoryImpl's
-- officialAuthorPredicate() (admin role OR the official email, excluding the deleted-user
-- sentinel seeded by V81). Use this exact form — the narrower admin-role-only variant that
-- predated the v0.62.0 fix mis-buckets the owner's personally-authored notes as community.

-- ============================================================================
-- QUERY A — note-side vocabulary: every distinct value, by weight
-- ============================================================================
-- The headline number is the ROW COUNT of this result. Under ~20 rows and the catalog is
-- an afternoon; over ~60 and Step 2 needs its own release with curator time budgeted.
SELECT
    n.course_program,
    COUNT(*)                                                        AS notes_total,
    COUNT(*) FILTER (WHERE n.visibility = 'PUBLIC')                 AS public_notes,
    COUNT(*) FILTER (WHERE n.visibility = 'PUBLIC' AND EXISTS (
        SELECT 1 FROM users official_user
        WHERE official_user.id = n.owner_user_id
          AND (official_user.role = 'ADMIN'
               OR lower(official_user.email) = lower('einar.lagera@gmail.com'))
          AND official_user.id <> '00000000-0000-0000-0000-00000000d1ed'::uuid
    ))                                                              AS official_public_notes,
    COUNT(DISTINCT n.owner_user_id)                                 AS distinct_owners,
    COUNT(DISTINCT n.subject)                                       AS distinct_subjects
FROM notes n
WHERE n.course_program IS NOT NULL
  AND trim(n.course_program) <> ''
GROUP BY n.course_program
ORDER BY notes_total DESC;

-- ============================================================================
-- QUERY B — user-side vocabulary
-- ============================================================================
-- Drives personalization, the resolver's note→user fallback chain
-- (StudyPackGenerationContextResolver:31, :71), and Exam Hub resolution
-- (getExamSlugForCourseProgram). Expect this vocabulary to be dirtier than A's, because
-- it is typed by learners during onboarding with no suggestions list. If it is, do NOT
-- make users.course_program a hard catalog FK — see 01 §2.8.
SELECT
    u.course_program,
    COUNT(*) AS users
FROM users u
WHERE u.course_program IS NOT NULL
  AND trim(u.course_program) <> ''
GROUP BY u.course_program
ORDER BY users DESC;

-- ============================================================================
-- QUERY C — near-duplicate collision groups (THE reconciliation-cost measure)
-- ============================================================================
-- Collapses case, whitespace, and ALL punctuation (hyphen, en-dash, ampersand, period),
-- then reports any normalized key with more than one raw spelling. Every row here is a
-- manual merge decision during Step 2's backfill, and every one missed silently drops
-- notes out of a filter. Covers notes and users together, since the catalog must serve both.
WITH raw_values AS (
    SELECT 'note' AS side, course_program AS value FROM notes
    WHERE course_program IS NOT NULL AND trim(course_program) <> ''
    UNION ALL
    SELECT 'user' AS side, course_program AS value FROM users
    WHERE course_program IS NOT NULL AND trim(course_program) <> ''
)
SELECT
    regexp_replace(lower(value), '[^a-z0-9]', '', 'g') AS normalized_key,
    COUNT(DISTINCT value)                              AS distinct_spellings,
    array_agg(DISTINCT value ORDER BY value)           AS raw_spellings,
    COUNT(*)                                           AS total_rows,
    array_agg(DISTINCT side ORDER BY side)             AS appears_on
FROM raw_values
GROUP BY normalized_key
HAVING COUNT(DISTINCT value) > 1
ORDER BY distinct_spellings DESC, total_rows DESC;

-- ============================================================================
-- QUERY D — non-ASCII character audit (the documented en-dash landmine)
-- ============================================================================
-- ExamGoalConfig:52-55 and frontend/lib/exam-hub-config.ts:12-13 both carry a hand-written
-- warning that "Medical – Surgical Nursing" uses U+2013, not a hyphen, and that the two
-- files must be kept in sync by hand. This finds every value carrying a character that
-- would break naive exact matching, so the catalog can normalize them once and retire
-- that warning.
SELECT
    'note' AS side, course_program AS value, COUNT(*) AS rows_affected
FROM notes
WHERE course_program ~ '[^\x20-\x7E]'
GROUP BY course_program
UNION ALL
SELECT
    'user' AS side, course_program AS value, COUNT(*) AS rows_affected
FROM users
WHERE course_program ~ '[^\x20-\x7E]'
GROUP BY course_program
ORDER BY rows_affected DESC;

-- ============================================================================
-- QUERY E — §9 BASELINE A: duplicate-content ratio (take BEFORE Step 1)
-- ============================================================================
-- Official public notes whose normalized (title, subject) already appears under two or
-- more distinct course_program values — i.e. knowledge that has already been duplicated
-- to satisfy the single-program constraint.
--
-- EXPECTED ANSWER TODAY: near zero. That is the point. The proposal itself says there are
-- "no significant duplicate Official Notes," and 01 §2.10 is explicit that the duplication
-- cost is forward-looking rather than measured. This is the baseline that would climb if
-- Civil Engineering shipped WITHOUT this architecture — so it only has meaning if it is
-- recorded now, before Domain Context exists.
WITH official_public_notes AS (
    SELECT
        n.id,
        regexp_replace(lower(trim(coalesce(n.title, ''))),   '\s+', ' ', 'g') AS norm_title,
        regexp_replace(lower(trim(coalesce(n.subject, ''))), '\s+', ' ', 'g') AS norm_subject,
        n.course_program
    FROM notes n
    WHERE n.visibility = 'PUBLIC'
      AND EXISTS (
          SELECT 1 FROM users official_user
          WHERE official_user.id = n.owner_user_id
            AND (official_user.role = 'ADMIN'
                 OR lower(official_user.email) = lower('einar.lagera@gmail.com'))
            AND official_user.id <> '00000000-0000-0000-0000-00000000d1ed'::uuid
      )
),
duplicate_groups AS (
    SELECT
        norm_title,
        norm_subject,
        COUNT(DISTINCT course_program)           AS distinct_programs,
        COUNT(*)                                 AS note_copies,
        array_agg(DISTINCT course_program)       AS programs
    FROM official_public_notes
    WHERE norm_title <> ''
    GROUP BY norm_title, norm_subject
    HAVING COUNT(DISTINCT course_program) > 1
)
SELECT
    (SELECT COUNT(*) FROM official_public_notes)                              AS official_public_notes,
    (SELECT COUNT(*) FROM duplicate_groups)                                   AS duplicated_knowledge_groups,
    (SELECT coalesce(SUM(note_copies), 0) FROM duplicate_groups)              AS notes_in_duplicate_groups,
    ROUND(100.0 * (SELECT coalesce(SUM(note_copies), 0) FROM duplicate_groups)
          / NULLIF((SELECT COUNT(*) FROM official_public_notes), 0), 2)       AS pct_duplicated;

-- Detail rows behind the ratio above (empty result is the expected, healthy answer today).
WITH official_public_notes AS (
    SELECT
        regexp_replace(lower(trim(coalesce(n.title, ''))),   '\s+', ' ', 'g') AS norm_title,
        regexp_replace(lower(trim(coalesce(n.subject, ''))), '\s+', ' ', 'g') AS norm_subject,
        n.course_program
    FROM notes n
    WHERE n.visibility = 'PUBLIC'
      AND EXISTS (
          SELECT 1 FROM users official_user
          WHERE official_user.id = n.owner_user_id
            AND (official_user.role = 'ADMIN'
                 OR lower(official_user.email) = lower('einar.lagera@gmail.com'))
            AND official_user.id <> '00000000-0000-0000-0000-00000000d1ed'::uuid
      )
)
SELECT
    norm_title,
    norm_subject,
    COUNT(DISTINCT course_program)     AS distinct_programs,
    array_agg(DISTINCT course_program) AS programs
FROM official_public_notes
WHERE norm_title <> ''
GROUP BY norm_title, norm_subject
HAVING COUNT(DISTINCT course_program) > 1
ORDER BY distinct_programs DESC;

-- ============================================================================
-- QUERY F — subjects already spanning multiple programs (TAXONOMY INPUT)
-- ============================================================================
-- The most actionable single query for the Domain Context decision. Any subject appearing
-- under two or more course_program values is knowledge the library is ALREADY treating as
-- shared — those are the first notes that should become canonical, and their groupings
-- suggest the Domain Context values themselves ("Engineering Foundation" is the right
-- value if Algebra/Physics/Statistics cluster across engineering programs here).
--
-- Unlike Query E this is not restricted to official notes: community notes reveal how
-- learners themselves group subjects across programs, which is useful taxonomy evidence
-- even though those notes are not curation targets.
SELECT
    n.subject,
    COUNT(DISTINCT n.course_program)           AS distinct_programs,
    COUNT(*)                                   AS notes_total,
    array_agg(DISTINCT n.course_program
              ORDER BY n.course_program)       AS programs
FROM notes n
WHERE n.subject IS NOT NULL AND trim(n.subject) <> ''
  AND n.course_program IS NOT NULL AND trim(n.course_program) <> ''
GROUP BY n.subject
HAVING COUNT(DISTINCT n.course_program) > 1
ORDER BY distinct_programs DESC, notes_total DESC;

-- ============================================================================
-- QUERY G — §9 BASELINE B: notes per published Review Set (take BEFORE Step 1)
-- ============================================================================
-- The proposal's premise is that Official Review Sets are small ("only a handful of Notes")
-- against a target of several hundred. This records the actual current distribution, which
-- is the headline before/after number for the whole initiative.
SELECT
    c.id,
    c.title,
    c.course_program,
    CASE WHEN EXISTS (
        SELECT 1 FROM users official_user
        WHERE official_user.id = c.owner_user_id
          AND (official_user.role = 'ADMIN'
               OR lower(official_user.email) = lower('einar.lagera@gmail.com'))
          AND official_user.id <> '00000000-0000-0000-0000-00000000d1ed'::uuid
    ) THEN 'official' ELSE 'community' END      AS author_kind,
    COUNT(i.id)                                 AS note_count
FROM note_collections c
LEFT JOIN note_collection_items i ON i.collection_id = c.id
WHERE c.visibility = 'PUBLIC'
GROUP BY c.id, c.title, c.course_program, c.owner_user_id
ORDER BY note_count DESC;

-- Summary form of the same baseline.
SELECT
    CASE WHEN EXISTS (
        SELECT 1 FROM users official_user
        WHERE official_user.id = c.owner_user_id
          AND (official_user.role = 'ADMIN'
               OR lower(official_user.email) = lower('einar.lagera@gmail.com'))
          AND official_user.id <> '00000000-0000-0000-0000-00000000d1ed'::uuid
    ) THEN 'official' ELSE 'community' END      AS author_kind,
    COUNT(DISTINCT c.id)                        AS published_review_sets,
    ROUND(AVG(sub.note_count), 1)               AS avg_notes,
    MAX(sub.note_count)                         AS max_notes,
    MIN(sub.note_count)                         AS min_notes
FROM note_collections c
JOIN LATERAL (
    SELECT COUNT(*) AS note_count
    FROM note_collection_items i
    WHERE i.collection_id = c.id
) sub ON true
WHERE c.visibility = 'PUBLIC'
GROUP BY author_kind;
