-- PR 4 step 1 — the read-only review pass over the `High School` notes.
-- Written 2026-08-03. Run against PRODUCTION. Read-only: no INSERT, UPDATE, DELETE, or DDL.
--
-- ============================================================================
-- WHY THIS EXISTS
-- ============================================================================
-- ADR-001 "Legacy-data policy" rule 1 forbids a blanket mapping for
-- `notes.course_program = 'High School'`. `LearnerLevel` already separates JUNIOR_HIGH from
-- SENIOR_HIGH, so the legacy label is strictly LESS precise than the taxonomy replacing it,
-- and no HIGH_SCHOOL enum value may be added to preserve the ambiguity.
--
-- That makes PR 4 three steps, not one SQL UPDATE
-- (`09-release-a-pr-sequence.md`, PR 4):
--
--   1. THIS FILE — list the notes for review.
--   2. The owner/curator classifies each one as JUNIOR_HIGH or SENIOR_HIGH from its ACTUAL
--      curriculum and content, or marks it unclassifiable. Never from the old label.
--   3. The V104 migration applies that explicit note-ID → level mapping.
--
-- The classification cannot be done from this repo: the notes live in production, and the
-- local dev database is a different dataset (118 notes, at V101, no `High School` rows at
-- all). Query A's output is the input to step 2.
--
-- ============================================================================
-- WHAT THE MIGRATION DOES WITH EACH ANSWER (read before classifying)
-- ============================================================================
-- Classified (JUNIOR_HIGH or SENIOR_HIGH):
--     learner_level  := the chosen level
--     domain_context := GENERAL_EDUCATION
--     course_program := cleared (the level has moved out of it)
--
-- Unclassifiable:
--     learner_level  := stays NULL
--     domain_context := stays NULL          <-- see the correction below
--     course_program := RETAINED ('High School')
--
-- CORRECTION, 2026-08-03 — why an unclassifiable note keeps `domain_context` NULL as well.
-- `09`'s summary table gives the whole `High School` row `domain_context = GENERAL_EDUCATION`,
-- which is only correct for the notes that get classified. Post-PR 2,
-- `StudyPackGenerationContextResolver:122-140`:
--
--     effectiveAuthoringDomain  = domainContext ?? courseProgram
--     effectiveCurriculumLevel  = noteLearnerLevel ?? userLearnerLevel ?? COLLEGE
--                                  (it never reads courseProgram)
--
-- So writing GENERAL_EDUCATION onto an UNCLASSIFIABLE note evicts 'High School' from the
-- "Domain:" line (OpenAiLlmStudyPackService:1561-1566) — domain_context wins that fallback —
-- and nothing replaces it. Static content reads noteLearnerLevel DIRECTLY, with no reader
-- fallback (:1554-1556, deliberate: a Grade School reader must not lower a College note), so a
-- NULL level emits no "Curriculum level:" line at all. Quizzes and exams do fall back, and land
-- on the reader's level — COLLEGE by default.
--
-- Net effect on those notes: today they emit a wrong-axis but real grade-level signal; after a
-- blanket GENERAL_EDUCATION backfill they emit NO level signal for static content and COLLEGE
-- curriculum for quizzes. That is a regression, on exactly the notes ADR-001 was most careful
-- about.
--
-- Leaving both columns NULL keeps `course_program` flowing through the fallback chain, which
-- is what ADR-001 already intends in prose: an unclassified note "retains its existing
-- course_program, so it is never left with no classification at all — the fallback chain
-- still resolves it," and `domain_context IS NULL` *is* the marker of "not yet promoted."
-- No ratified decision is reopened; `09`'s table cell assumed all 11 get classified.
--
-- DO NOT flip `visibility`. All of these are live official public notes. ADR-001 reads the
-- ratifying instruction's "unpublished" conservatively as *leave unclassified* and explicitly
-- does not authorize withdrawing published content.

-- ============================================================================
-- QUERY 0 — RUN THIS FIRST: does production have V102 yet?
-- ============================================================================
-- PRs 1-3 merged into `releases/v0.69.0`, NOT into `main`, so whether production has
-- `notes.domain_context` / `notes.learner_level` depends on what is deployed — it is not
-- implied by the branch history. The local dev DB is at V101 and fails Queries A and B
-- outright ("column learner_level does not exist"), so check before assuming.

SELECT version, description, installed_on
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 3;

-- If the max version is >= 102: run everything below unchanged.
-- If it is < 102: the columns do not exist yet. Drop from Query A's select list:
--       n.learner_level  AS current_learner_level,
--       n.domain_context AS current_domain_context,
--   and from Query B:
--       count(n.learner_level)  AS already_has_level,
--       count(n.domain_context) AS already_has_context,
--   Nothing else changes, and their expected values are NULL / 0 either way — they are here
--   to catch a surprise backfill, not because the classification needs them.

-- ============================================================================
-- QUERY A — the review list (this is the one that needs your judgment)
-- ============================================================================
-- One row per `High School` note, with enough content to classify it without opening the app.
--
-- What to look for, in rough order of usefulness:
--   * SENIOR_HIGH signals — a named strand (STEM / ABM / HUMSS / GAS / TVL), Grade 11-12
--     subjects (General Mathematics, Pre-Calculus, Earth and Life Science, General Biology,
--     General Chemistry/Physics, Statistics and Probability, Practical Research,
--     Disciplines and Ideas, Organization and Management, Business Math, Empowerment
--     Technologies), or college-preparatory framing.
--   * JUNIOR_HIGH signals — Grade 7-10 spiral-curriculum topics (Integrated Science,
--     Araling Panlipunan, TLE, Grade 7-10 Math strands), or explicit grade references.
--   * NEITHER, confidently -> mark it unclassifiable. That is a legitimate answer and the
--     policy explicitly prefers it over a guess. Do not stretch a signal to avoid a NULL.
--
-- `content_excerpt` is the first 800 characters. If a note is genuinely borderline, open it
-- in the app rather than widening the excerpt here — a longer excerpt rarely decides it.

SELECT
    n.id,
    n.title,
    n.subject,
    n.tags,
    n.target_profile_type,
    n.visibility,
    n.learner_level                        AS current_learner_level,   -- expected NULL (V102 backfilled nothing)
    n.domain_context                       AS current_domain_context,  -- expected NULL
    n.created_at,
    n.updated_at,
    length(n.content)                      AS content_chars,
    EXISTS (SELECT 1 FROM study_packs sp WHERE sp.note_id = n.id) AS has_study_pack,
    (SELECT count(*) FROM note_collection_items nci WHERE nci.note_id = n.id) AS collection_memberships,
    left(regexp_replace(n.content, '\s+', ' ', 'g'), 800) AS content_excerpt
FROM notes n
WHERE n.course_program = 'High School'
ORDER BY n.subject NULLS LAST, n.title;

-- Expected: 11 rows (audited 2026-08-03). If the count has moved, say so before the migration
-- is written — the V104 mapping is keyed by note ID, so a note added since the audit would
-- otherwise be silently skipped and left with a level in its program field.

-- ============================================================================
-- QUERY B — drift check on all six level-in-program values
-- ============================================================================
-- Confirms the 38/11 split still holds and that nothing has been backfilled since V102 shipped.
-- Any non-zero `already_has_level` / `already_has_context` is a surprise worth explaining
-- before writing a migration that assumes it is backfilling from NULL.

SELECT
    n.course_program,
    count(*)                        AS notes,
    count(n.learner_level)          AS already_has_level,
    count(n.domain_context)         AS already_has_context,
    count(*) FILTER (WHERE n.visibility = 'PUBLIC') AS public_notes
FROM notes n
WHERE n.course_program IN (
        'Grade School',
        'Junior High',
        'High School',
        'Senior High – STEM',    -- EN DASH (U+2013), not a hyphen. See ExamGoalConfig's
        'Senior High – ABM',     -- documented en-dash fragility.
        'Senior High – HUMSS'
      )
GROUP BY n.course_program
ORDER BY notes DESC;

-- Expected, from the 2026-08-03 audit:
--   Junior High 24 | High School 11 | Senior High – STEM 4 | – ABM 4 | – HUMSS 3 | Grade School 3
--   = 49 total, all already_has_level = 0 and already_has_context = 0.

-- ============================================================================
-- QUERY B2 — en-dash guard
-- ============================================================================
-- If Query B's Senior High rows come back short, a hyphen variant exists that the audit's
-- character-collision check would have caught but a later hand-authored note might not honour.
-- This catches any level-ish program value the IN-list above misses.

SELECT
    n.course_program,
    count(*) AS notes
FROM notes n
WHERE n.course_program ILIKE '%senior high%'
   OR n.course_program ILIKE '%junior high%'
   OR n.course_program ILIKE '%high school%'
   OR n.course_program ILIKE '%grade school%'
GROUP BY n.course_program
ORDER BY notes DESC;

-- Any value here that is NOT in Query B's IN-list is a new case PR 4 must decide on
-- explicitly — it does not get folded into an existing mapping by resemblance.

-- ============================================================================
-- QUERY C — blast radius
-- ============================================================================
-- `09` states all 49 are in zero collections, so clearing `course_program` cannot disturb a
-- Review Set. This re-verifies that immediately before the migration rather than trusting a
-- figure taken days earlier.

SELECT
    n.course_program,
    count(DISTINCT n.id)              AS notes,
    count(DISTINCT nci.collection_id) AS distinct_collections,
    count(nci.id)                     AS membership_rows
FROM notes n
LEFT JOIN note_collection_items nci ON nci.note_id = n.id
WHERE n.course_program IN (
        'Grade School', 'Junior High', 'High School',
        'Senior High – STEM', 'Senior High – ABM', 'Senior High – HUMSS'
      )
GROUP BY n.course_program
ORDER BY notes DESC;

-- Expected: distinct_collections = 0 and membership_rows = 0 on every row.
-- If any row is non-zero, PR 4 is no longer zero-blast-radius and the affected Review Set
-- needs to be looked at before `course_program` is cleared.

-- ============================================================================
-- QUERY D — the Senior High strands (an OPEN question, not part of the classification)
-- ============================================================================
-- The same eviction mechanism that forced the correction above also applies to the 11 strand
-- notes, and `09` never examined it. Those notes KEEP `course_program` and gain
-- `domain_context = GENERAL_EDUCATION`, so their prompt Domain line moves:
--
--       Domain: Senior High – STEM   ->   Domain: General Education
--
-- The level is NOT lost here (SENIOR_HIGH gets set), so this is not the High School
-- regression. What is lost is the STRAND: a Pre-Calculus note and a Disciplines-and-Ideas
-- note collapse to one domain constraint. That is risk R4 exactly — a broader domain label
-- replacing a narrower one — on 11 live notes.
--
-- This query is here so the question can be answered by looking at the actual notes rather
-- than in the abstract. It needs no classification pass; it is a read for the R4 check.

SELECT
    n.course_program,
    n.subject,
    n.title,
    left(regexp_replace(n.content, '\s+', ' ', 'g'), 300) AS content_excerpt
FROM notes n
WHERE n.course_program IN ('Senior High – STEM', 'Senior High – ABM', 'Senior High – HUMSS')
ORDER BY n.course_program, n.subject NULLS LAST, n.title;

-- The judgment: does `subject` already carry enough of the discriminating signal that
-- GENERAL_EDUCATION is a sufficient domain constraint, or does the strand need to keep
-- flowing through `effectiveAuthoringDomain` (i.e. these 11 also keep `domain_context` NULL)?
--
-- A strand note is the CHEAPEST available R4 subject — one regenerate-and-diff answers it
-- empirically, which is the tie-break ADR-001 itself prescribes ("generate the note under both
-- candidate values and compare the output"). Fold it into the owed R4 pass rather than
-- adjudicating it on definitions.

-- ============================================================================
-- HANDING THE ANSWER BACK
-- ============================================================================
-- Paste Query A's classification back in this shape. The migration consumes it verbatim, so
-- every one of the 11 IDs must appear exactly once, including the unclassifiable ones —
-- an omitted ID is indistinguishable from an oversight.
--
--   <uuid>  JUNIOR_HIGH     -- <title>
--   <uuid>  SENIOR_HIGH     -- <title>
--   <uuid>  UNCLASSIFIABLE  -- <title>, <one line on why it could not be decided>
--
-- Record the same table in `RELEASES.md` under v0.69.0 when PR 4 ships, so the classification
-- rationale survives the migration file. A future session reading only V104 would otherwise
-- see an unexplained ID list and have no way to tell a judgment call from a typo.
