-- N2: per-subject public-note depth inventory (2026-07-17 SEO expansion follow-up)
-- Schema-verified against NoteEntity (table: notes, columns: subject varchar(64), visibility enum-as-
-- string). Run against production, read-only.
--
-- Purpose: sets N1's noindex/sitemap-filter threshold honestly, shows how many subject pages the gate
-- will affect, and surfaces near-duplicate subject strings ("Bio" vs "Biology") as a side effect.
--
-- IMPORTANT: grouping by the raw `subject` column undercounts real per-page depth. The actual subject
-- landing page (frontend/lib/public-note-path.ts's getPublicSubjectSlug + frontend/lib/server-public-
-- notes.ts's getServerPublicNotesBySubjectSlug) groups notes by a SLUGIFIED subject (lowercased, trimmed,
-- non-alphanumeric runs collapsed to a single "-", leading/trailing "-" stripped, empty -> "general") —
-- so "Biology", "biology", and " Biology " all render on the SAME /public/library/biology page. Query 1
-- below groups by that same slug logic so the counts match what a real page will actually show.

-- ============================================================
-- Query 1: Public note count per subject SLUG (matches actual page grouping — use this for N1's threshold)
-- ============================================================

WITH slugged AS (
    SELECT
        id,
        COALESCE(
            NULLIF(
                regexp_replace(
                    regexp_replace(lower(trim(subject)), '[^a-z0-9]+', '-', 'g'),
                    '(^-+)|(-+$)', '', 'g'
                ),
                ''
            ),
            'general'
        ) AS subject_slug,
        subject AS raw_subject
    FROM notes
    WHERE visibility = 'PUBLIC'
)
SELECT
    subject_slug,
    COUNT(*) AS note_count,
    COUNT(DISTINCT raw_subject) AS distinct_raw_strings_collapsed,
    -- sample of the raw strings collapsing into this slug, so you can eyeball whether the collapse is
    -- correct normalization ("Biology"/"biology") vs. an accidental over-merge
    array_agg(DISTINCT raw_subject ORDER BY raw_subject) FILTER (WHERE raw_subject IS NOT NULL) AS raw_subject_variants
FROM slugged
GROUP BY subject_slug
ORDER BY note_count DESC;

-- ============================================================
-- Query 2: How many subject pages fall below candidate thresholds (sanity-check before picking N1's number)
-- ============================================================
-- Re-run with different threshold values once Query 1's distribution is visible. Starting candidates
-- bracket Fable's suggested "~4-6 notes" range.

WITH slugged AS (
    SELECT
        id,
        COALESCE(
            NULLIF(
                regexp_replace(
                    regexp_replace(lower(trim(subject)), '[^a-z0-9]+', '-', 'g'),
                    '(^-+)|(-+$)', '', 'g'
                ),
                ''
            ),
            'general'
        ) AS subject_slug
    FROM notes
    WHERE visibility = 'PUBLIC'
),
per_subject AS (
    SELECT subject_slug, COUNT(*) AS note_count
    FROM slugged
    GROUP BY subject_slug
)
SELECT
    COUNT(*) FILTER (WHERE note_count = 0)               AS subjects_with_zero_notes, -- won't actually appear here (a subject only exists if >=1 note), included for clarity
    COUNT(*) FILTER (WHERE note_count < 4)                AS subjects_below_4,
    COUNT(*) FILTER (WHERE note_count < 5)                AS subjects_below_5,
    COUNT(*) FILTER (WHERE note_count < 6)                AS subjects_below_6,
    COUNT(*) FILTER (WHERE note_count >= 6)               AS subjects_at_or_above_6,
    COUNT(*)                                              AS total_subject_pages
FROM per_subject;

-- ============================================================
-- Query 3 (bonus, not required for N1): Exam-hub-adjacent subject pages specifically
-- ============================================================
-- Cross-checks Fable's L1 point — that exam-adjacent subject pages (Med Surg, Fundamentals of Nursing,
-- etc.) already have real depth via the exam curation effort, distinct from the general long tail.
-- courseProgram values must match frontend/lib/exam-hub-config.ts exactly (kept in sync per its own
-- comment) — "Medical – Surgical Nursing" uses U+2013 (en-dash), not a hyphen.

SELECT
    subject,
    course_program,
    COUNT(*) AS note_count
FROM notes
WHERE visibility = 'PUBLIC'
  AND course_program IN ('Architecture', 'Nursing', 'Medical – Surgical Nursing', 'Education')
GROUP BY subject, course_program
ORDER BY course_program, note_count DESC;
