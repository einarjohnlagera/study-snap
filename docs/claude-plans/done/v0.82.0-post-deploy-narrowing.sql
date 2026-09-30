-- V117 post-deploy narrowing — step 2 of docs/claude-plans/v0.82.0-curator-depth-backfill-reversal.md
--
-- RUN THIS IMMEDIATELY. v0.82.0 merged and deployed 2026-08-17. Every hour this waits
-- widens the "known hole, minutes wide" the reversal doc names: a curator authoring a
-- depth between the merge and this query is wrongly counted as migrated.
--
-- ⚠️ RUN AGAINST PRODUCTION, from the repo root. `\copy` resolves its paths client-side,
-- so the relative paths below only work from the repo root. Run against a local
-- study_snap by mistake and the temp table still loads, the join returns nothing, and
-- written_expect_819 reads 0 — recoverable, but confusing enough to waste the window.
--
-- Input:  docs/claude-plans/v0.82.0-curator-depth-backfill-population.csv
--         828 note ids, the pre-merge superset. No header, one uuid per line.
-- Output: docs/claude-plans/v0.82.0-curator-depth-backfill-written.csv
--         the exact rows V117 wrote. THIS is the reversal key and the
--         [CHECKPOINT — due 2026-09-16] denominator. The superset is neither.
--
-- EXPECTED: 819 rows. 828 captured minus the 9 Information Technology notes the
-- denylist excludes. Anything else is a finding — see the verification block below.

\set ON_ERROR_STOP on

-- ---------------------------------------------------------------------------
-- Load the pre-merge superset.
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE v117_captured (id uuid PRIMARY KEY);

\copy v117_captured (id) FROM 'docs/claude-plans/v0.82.0-curator-depth-backfill-population.csv' WITH (FORMAT csv)

-- Guard: the capture must be the 828-row file, not a stale or truncated copy.
-- A real assertion, not a printed count. A silently short load would produce a key
-- missing rows, which is the reversal failing open.
DO $$
DECLARE n int;
BEGIN
    SELECT count(*) INTO n FROM v117_captured;
    IF n <> 828 THEN
        RAISE EXCEPTION 'capture loaded % rows, expected 828 - wrong or truncated file, STOP', n;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- Step 2 — narrow the superset to what V117 actually wrote.
--
-- Safe because V117 only ever touches rows with learner_level IS NULL, and nothing
-- else moves that column on its own. Every captured row was NULL at capture time by
-- construction of the step-1 predicate, so a non-NULL depth now means V117 wrote it.
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE v117_written AS
SELECT n.id
FROM notes n
JOIN v117_captured c ON c.id = n.id
WHERE n.learner_level IS NOT NULL
ORDER BY n.id;

SELECT count(*) AS written_expect_819 FROM v117_written;

-- ---------------------------------------------------------------------------
-- Verification — an identity check, not just a count check.
--
-- The 9 rows left NULL must be exactly the 9 Information Technology notes the
-- audit named. A bare count of 819 could also be produced by the denylist
-- excluding the WRONG 9 rows; this cannot.
-- ---------------------------------------------------------------------------
SELECT c.id,
       n.course_program,
       n.target_profile_type,
       (substring(c.id::text, 1, 8) IN (
            '24a98a93','3b1b76be','4d2b6ed1','7f8566bd','8840b0e6',
            '9c884c1d','c291ad2f','c8aec353','fa80777f'
       )) AS is_expected_it_exclusion
FROM v117_captured c
JOIN notes n ON n.id = c.id
WHERE n.learner_level IS NULL
ORDER BY is_expected_it_exclusion DESC, c.id;
-- EXPECT: exactly 9 rows, every is_expected_it_exclusion = true.
--   A false row  -> the denylist excluded something it should have written.
--   A missing id -> an IT note was written a licensure curriculum floor. Investigate
--                   BEFORE trusting the key; that is the failure V117's exclusion exists to stop.

-- Distribution of what was actually written. PROFESSIONAL is expected to be 0 —
-- the capture found no PROFESSIONAL note in production, so V117 has one live mapping.
SELECT n.learner_level, count(*)
FROM notes n JOIN v117_written w ON w.id = n.id
GROUP BY n.learner_level ORDER BY 2 DESC;
-- EXPECT: BOARD_EXAM_REVIEW = 819, and no other row.
--
-- ⚠️ This check is load-bearing, not cosmetic. A result like BOARD_EXAM_REVIEW = 818
-- plus COLLEGE = 1 means a curator hand-authored a depth on a captured note between the
-- capture and the merge. V117 SKIPPED that row (learner_level was no longer NULL), so it
-- is NOT part of the written set and MUST be removed from written.csv before that file
-- becomes the reversal key — otherwise the reversal NULLs a human authoring decision,
-- the precise failure this key exists to prevent. Any level other than
-- BOARD_EXAM_REVIEW/PROFESSIONAL is such a row. Delete those ids from written.csv.

-- ---------------------------------------------------------------------------
-- Persist the key. Without this the narrowed set dies with the psql session and
-- September has nothing to read.
-- ---------------------------------------------------------------------------
\copy (SELECT id FROM v117_written ORDER BY id) TO 'docs/claude-plans/v0.82.0-curator-depth-backfill-written.csv' WITH (FORMAT csv)

-- Then COMMIT both csv files. Until they are committed the reversal key exists only
-- in one working tree and one `git clean` removes it.
--
-- Residual hole this cannot close: a row hand-authored to BOARD_EXAM_REVIEW itself
-- between capture and merge is indistinguishable from one V117 wrote. Nothing recorded
-- can separate them. It is minutes wide and strictly smaller than the alternative.


-- ===========================================================================
-- SIZING THE PHASE 2 REGRESSION — run in the same session, production is open.
--
-- NOT a V118 proposal. V118 was audited and killed on zero eligible notes; this
-- re-proposes nothing and writes nothing. It sizes a product decision for v0.83.0:
-- removing the audience filter leaves student-level material filterable only by
-- Authored Depth, and STUDENT depth was deliberately left NULL by V117.
--
-- ⚠️ Run it because the repo contradicts itself on whether that regression is real:
--   - RELEASES.md says the V118 audit found ZERO eligible notes "because curators had
--     already authored those depths by hand" -> curator STUDENT notes DO carry depth,
--     and the depth filter is NOT empty for student material.
--   - The reversal doc says "the 6 curator High School NULL-depth notes are all STUDENT"
--     -> at least 6 such notes have no depth.
-- Both cannot be true. Decide on the number, not on either sentence.
-- ===========================================================================
SELECT n.learner_level IS NULL AS depth_missing, count(*)
FROM notes n
JOIN users u ON u.id = n.owner_user_id
WHERE u.role = 'ADMIN'
  AND n.visibility = 'PUBLIC'
  AND n.target_profile_type = 'STUDENT'
GROUP BY 1;
-- depth_missing = false dominates -> no real regression; record that and remove the
--   audience filter cleanly.
-- depth_missing is large (~120) -> the regression is real. Accept it explicitly with a
--   dated curator-classification follow-up, or classify before removing the chips.

-- The depths curators actually used, for the same cohort. Tells you whether a
-- replacement depth filter would offer a usable spread or collapse to one value.
SELECT n.learner_level, count(*)
FROM notes n
JOIN users u ON u.id = n.owner_user_id
WHERE u.role = 'ADMIN'
  AND n.visibility = 'PUBLIC'
  AND n.target_profile_type = 'STUDENT'
GROUP BY 1 ORDER BY 2 DESC;
