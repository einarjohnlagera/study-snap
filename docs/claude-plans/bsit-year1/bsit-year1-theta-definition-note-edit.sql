-- ============================================================================
-- BSIT Year 1: define Theta notation in the Note "Algorithms and Their Properties"
-- Status: PREPARED, NOT RUN. Prepared 2026-11-05 by the Note Strategist session.
-- THE OWNER EXECUTES THIS. (CLAUDE.md: production writes are owner-only.)
--
-- WHY (owner decision 2026-11-05, option b)
--   Big-O is defined exactly once in the corpus, in this Note's Quick Recall (Computer Programming 1,
--   early in the plan). Theta (a tight bound) is defined nowhere. Four Notes use it:
--   Nested Loops, Recursive Problem Solving, Elementary Sorting, and Sequences and Summations (Discrete
--   Mathematics). Three have quiz items that depend on it. Adding one Quick Recall line here fixes all
--   four without touching them. An independent review (GPT, 2026-11-05) recommended HOLD on Nested Loops
--   and Recursive Problem Solving for exactly this reason.
--
-- WHAT IT DOES
--   Appends ONE bullet to the end of notes.content (and bumps updated_at) on ONE existing Note:
--       - Theta notation — An asymptotic tight bound on resource growth; $\Theta(n)$ denotes growth
--         bounded above and below by constant multiples of the input size $n$.
--   Nothing else is touched: no Subject, Domain Context, Authored Depth, Applicable Programs,
--   visibility, collection or Study Pack. The Study Pack is NOT regenerated and must not be
--   auto-regenerated (versioning rule). Its quiz does not use Theta, so it stays correct.
--
-- WHO IS AFFECTED
--   This is a canonical Note shared with the BS Computer Science Year 1 (and the BSIT plan). It currently
--   sits in 1 collection. The change adds a definition; it removes and rewords nothing.
--
-- GUARDS (read from production 2026-11-05; re-run STEP 0)
--   The UPDATE matches only if id, title, Domain Context, the exact md5 of the CURRENT content and the exact
--   current updated_at all still match. If anything changed since, it updates 0 rows instead of
--   overwriting something unexpected.
--     id          583c82dc-fc5b-4088-ae21-dd474a787be3
--     title       Algorithms and Their Properties        domain_context COMPUTING
--     length 2304 md5 023dd66424d42c3297f72ce6bcaaa89c   updated_at 2026-10-29 08:22:49.083596+00
--
-- HOW TO RUN (use psql, not an IDE console)
--   Dry run: psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f docs/claude-plans/bsit-year1/bsit-year1-theta-definition-note-edit.sql
--   Apply:   the same command with -c "COMMIT;" appended.
--   Save the output of STEP 0b: it is your rollback copy of the old body.
-- ============================================================================

-- STEP 0a (read-only): must return guard_ok = true.
SELECT n.id, n.title, n.domain_context, length(n.content) AS len, md5(n.content) AS md5, n.updated_at,
       (n.title = 'Algorithms and Their Properties'
        AND n.domain_context = 'COMPUTING'
        AND md5(n.content) = '023dd66424d42c3297f72ce6bcaaa89c'
        AND n.updated_at = '2026-10-29 08:22:49.083596+00'::timestamptz) AS guard_ok
FROM notes n WHERE n.id = '583c82dc-fc5b-4088-ae21-dd474a787be3';

-- STEP 0b (read-only): save this output. It is the old body.
SELECT content FROM notes WHERE id = '583c82dc-fc5b-4088-ae21-dd474a787be3';

BEGIN;

-- STEP 1: expect UPDATE 1.
UPDATE notes
SET content = content || E'\n- Theta notation — An asymptotic tight bound on resource growth; $\\Theta(n)$ denotes growth bounded above and below by constant multiples of the input size $n$.',
    updated_at = now()
WHERE id = '583c82dc-fc5b-4088-ae21-dd474a787be3'
  AND title = 'Algorithms and Their Properties'
  AND domain_context = 'COMPUTING'
  AND md5(content) = '023dd66424d42c3297f72ce6bcaaa89c'
  AND updated_at = '2026-10-29 08:22:49.083596+00'::timestamptz;

-- STEP 2 (inside the transaction): expect len = old length + the new bullet, and the last two lines to be
-- the Big-O bullet followed by the Theta bullet.
SELECT length(content) AS new_len,
       right(content, 330) AS tail
FROM notes WHERE id = '583c82dc-fc5b-4088-ae21-dd474a787be3';

-- COMMIT;   -- supplied by the -c "COMMIT;" above. If STEP 1 returned UPDATE 0, ROLLBACK, do not
--           -- COMMIT, and tell Claude what STEP 0a showed.

-- ============================================================================
-- ROLLBACK (only after a COMMIT you want to undo)
-- ============================================================================
-- BEGIN;
-- UPDATE notes
-- SET content = replace(content, E'\n- Theta notation — An asymptotic tight bound on resource growth; $\\Theta(n)$ denotes growth bounded above and below by constant multiples of the input size $n$.', ''),
--     updated_at = now()
-- WHERE id = '583c82dc-fc5b-4088-ae21-dd474a787be3';    -- expect UPDATE 1; md5(content) returns to 023dd664...
-- COMMIT;
--
-- AFTER THE SQL (in the application, not SQL)
--   * Do NOT use "Regenerate note" or "Bulk regenerate" on this Note: that regenerates the BODY from the
--     title and would overwrite the text. No Study Pack regeneration is needed.
