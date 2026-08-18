-- Domain Context classification worksheet — Accountancy and Nursing curator public notes.
--
-- ⚠️ RUN AGAINST PRODUCTION. Steps 0, 1 and 3 are read-only. STEP 2 WRITES.
-- No psql meta-commands, so it pastes into DBeaver / JetBrains / pgAdmin as-is.
--
-- WHY THIS EXISTS. v0.85.0 replaced the substring guess in `isQuantitativeContext` with a
-- property declared on the `DomainContext` enum. Verified after that landed: it changes the
-- prompt for ZERO notes today. The property fires only when `context.domainContext()` is
-- non-null, and `StudyPackGenerationContextResolver` populates that solely from the note's own
-- `domain_context` column — there is no program-to-enum map. Every enum value in production use
-- (CIVIL_ENGINEERING 54, ENGINEERING_MATHEMATICS 20, ENGINEERING_SCIENCES 16) already matched
-- the old keyword scan on its own label, and the four values that newly opt in — ACCOUNTANCY,
-- NURSING, PROFESSIONAL_PRACTICE_AND_REGULATION, PROFESSIONAL_EDUCATION — have no notes at all.
--
-- So the 463 notes missing computation guidance close by CLASSIFICATION, not by more code.
-- This is the worksheet for the two programs where it pays most: Accountancy (154 notes, 60
-- missing) and Nursing (130 notes, 106 missing) — the CPA and NLE catalogs, where arithmetic is
-- most of the exam.
--
--
-- ⚠️ READ THESE FOUR THINGS BEFORE RUNNING STEP 2.
--
-- (1) CLASSIFY BY SUBJECT, NEVER BY PROGRAM. This is the entire lesson of the
--     PROFESSIONAL_EDUCATION decision recorded in RELEASES.md. Of 136 Education notes missing
--     guidance only ~10% were computational, so declaring the whole value quantitative would
--     have pushed a false signal onto ~123 notes to rescue 13. `Nursing` is the same shape:
--     Pharmacology is dosage calculation, Community Health is not. Do not blanket-update a
--     program. Step 0 exists to make you look at the subjects first.
--
-- (2) FOR THESE TWO PROGRAMS, CLASSIFICATION DOES NOT CHANGE THE PROMPT PAYLOAD — which is what
--     makes this unusually safe. `effectiveAuthoringDomain` sends the Domain Context's DISPLAY
--     LABEL, falling back to `course_program`. The labels are literally `Accountancy` and
--     `Nursing`, identical to the `course_program` strings these notes already send. So the
--     domain line the model receives is byte-identical before and after; the ONLY thing that
--     changes is the quantitative flag. That is not true for other programs — see (3).
--
-- (3) DO NOT SET `GENERAL_EDUCATION` ON THE NON-MAJOR SUBJECTS. It is tempting for the Rizal /
--     Ethics / Communication rows, but it is a real payload change: the domain line would go
--     from `Accountancy` to `General Education`, stripping the framing that currently keeps
--     generation inside the CPA context. LEAVE THEM NULL. A null `domain_context` is also the
--     promotion-backlog marker (ADR-001, v0.75.0) — an observable "not yet decided" is worth
--     more than a wrong decision, and null already falls through to the unchanged keyword scan.
--
-- (4) ⚠️ EXISTING STUDY PACKS DO NOT CHANGE. Per `docs/features/notes.md`, correcting an
--     authoring axis on a `STUDY_PACK_READY` note affects only FUTURE generation; the existing
--     pack is untouched until someone explicitly regenerates. Study Packs are never
--     auto-regenerated (the versioning rule). So this backfill fixes what these notes generate
--     from now on — new Challenge questions, new Board Exam pools, any regeneration — but the
--     already-generated content stays as it is. Classify first, then decide separately and
--     deliberately which packs are worth regenerating. Do not treat the two as one step.
--
--
-- ===========================================================================
-- STEP 0 — THE WORKSHEET. Read-only. Run this first and actually read it.
--
-- Every subject in the two programs that has at least one unclassified curator public note,
-- with how many of those notes currently get computation guidance. Rows at the top are the
-- ones where classification would change something.
--
-- Decision rule, to apply per row BEFORE you look at the counts: would a competent examiner
-- expect this subject's questions to involve calculation? Pharmacology yes (dosage). Income Tax
-- yes. Nursing Research — only if it is the statistics half. Bioethics no.
-- ===========================================================================
WITH kw(word) AS (
    VALUES ('accounting'),('algebra'),('algorithm'),('algorithms'),('amortization'),('analysis'),
           ('anatomy'),('balance'),('calculus'),('cash flow'),('chemistry'),('circuit'),('circuits'),
           ('computation'),('compute'),('current'),('derivative'),('derivatives'),('differential'),
           ('electric'),('electrical'),('engineering'),('equation'),('equations'),('finance'),
           ('formula'),('formulas'),('geometry'),('interest'),('integral'),('kinematics'),
           ('laws of motion'),('math'),('mathematics'),('mechanics'),('numerical'),('ohm'),
           ('physics'),('probability'),('ratio'),('resistance'),('solve'),('statistics'),
           ('stoichiometry'),('thermodynamics'),('unit conversion'),('units'),('variance'),('voltage')
),
candidate AS (
    SELECT n.id,
           n.course_program,
           coalesce(n.subject, '(no subject)') AS subject,
           -- the haystack as it is TODAY: course_program (domain_context is null here)
           -- + subject + tags, exactly as the initial-generation call site builds it.
           lower(concat_ws(' ', n.course_program, n.subject, array_to_string(n.tags, ' '))) AS haystack
    FROM notes n
    JOIN users u ON u.id = n.owner_user_id
    WHERE u.role = 'ADMIN'
      AND n.visibility = 'PUBLIC'
      AND n.domain_context IS NULL
      AND n.course_program IN ('Accountancy', 'Nursing')
)
SELECT course_program,
       subject,
       count(*) AS unclassified_notes,
       count(*) FILTER (
           WHERE NOT EXISTS (SELECT 1 FROM kw WHERE candidate.haystack LIKE '%' || kw.word || '%')
       ) AS missing_guidance_today,
       count(*) FILTER (
           WHERE EXISTS (SELECT 1 FROM kw WHERE candidate.haystack LIKE '%' || kw.word || '%')
       ) AS already_rescued_by_keyword
FROM candidate
GROUP BY 1, 2
ORDER BY missing_guidance_today DESC, unclassified_notes DESC;


-- ===========================================================================
-- STEP 1 — REVERSAL KEY. Read-only. Run this BEFORE step 2 and KEEP THE OUTPUT.
--
-- v0.82.0 spent a release recovering a backfill key that was never exported. Export this to
-- CSV and commit it to docs/claude-plans/ before writing anything. Every row is currently
-- NULL, so the reversal is: set domain_context = NULL for exactly these ids.
--
-- Replace the subject list with the one YOU chose in step 0 — the same list you will paste
-- into step 2. If the two lists differ, the key does not describe the write.
-- ===========================================================================
SELECT n.id,
       n.course_program,
       n.subject,
       n.domain_context AS domain_context_before,   -- expect NULL on every row
       n.title
FROM notes n
JOIN users u ON u.id = n.owner_user_id
WHERE u.role = 'ADMIN'
  AND n.visibility = 'PUBLIC'
  AND n.domain_context IS NULL
  AND n.course_program IN ('Accountancy', 'Nursing')
  AND n.subject IN (
      -- ⚠️ PASTE YOUR CHOSEN SUBJECTS HERE. Examples from the coverage read, NOT a
      -- recommendation — confirm each against step 0 before including it:
      'Income Tax', 'Business Tax', 'Basic Taxation', 'Advanced Taxation',
      'Budgeting', 'Cash and Receivables', 'Investments', 'Financial Management', 'PPE',
      'Pharmacology'
  )
ORDER BY n.course_program, n.subject, n.id;


-- ===========================================================================
-- STEP 2 — ⚠️ THIS WRITES. Run inside a transaction and check the row count before COMMIT.
--
-- Two statements because the target value differs per program. Both are scoped to curator-owned
-- public notes that are currently unclassified, so they cannot touch learner-owned notes — the
-- constraint v0.82.0 settled on production data (4,645 of 5,550 affected notes were
-- learner-owned; writing authoring metadata there asserts a decision their author never made).
--
-- The `domain_context IS NULL` predicate also makes this idempotent: re-running it is a no-op,
-- and it can never overwrite a classification a curator already made by hand.
-- ===========================================================================
BEGIN;

UPDATE notes n
SET domain_context = 'ACCOUNTANCY'
FROM users u
WHERE u.id = n.owner_user_id
  AND u.role = 'ADMIN'
  AND n.visibility = 'PUBLIC'
  AND n.domain_context IS NULL
  AND n.course_program = 'Accountancy'
  AND n.subject IN (
      -- ⚠️ SAME LIST AS STEP 1, Accountancy rows only.
      'Income Tax', 'Business Tax', 'Basic Taxation', 'Advanced Taxation',
      'Budgeting', 'Cash and Receivables', 'Investments', 'Financial Management', 'PPE'
  );

UPDATE notes n
SET domain_context = 'NURSING'
FROM users u
WHERE u.id = n.owner_user_id
  AND u.role = 'ADMIN'
  AND n.visibility = 'PUBLIC'
  AND n.domain_context IS NULL
  AND n.course_program = 'Nursing'
  AND n.subject IN (
      -- ⚠️ SAME LIST AS STEP 1, Nursing rows only.
      'Pharmacology'
  );

-- Confirm the counts match step 1's row count, then:
--   COMMIT;
-- If anything looks wrong:
--   ROLLBACK;
COMMIT;


-- ===========================================================================
-- STEP 3 — VERIFICATION. Read-only. Run after COMMIT.
--
-- Rebuilds the coverage read with the DECLARED property applied, so it reports what generation
-- will actually do rather than what the old keyword scan did. `missing_guidance` for Accountancy
-- and Nursing should have dropped by exactly the number of rows step 2 updated.
--
-- This query is also the [CHECKPOINT — due 2026-09-17] read: its kill criterion is whether
-- classification moved off 12.7% and whether the 463 shrank.
-- ===========================================================================
WITH kw(word) AS (
    VALUES ('accounting'),('algebra'),('algorithm'),('algorithms'),('amortization'),('analysis'),
           ('anatomy'),('balance'),('calculus'),('cash flow'),('chemistry'),('circuit'),('circuits'),
           ('computation'),('compute'),('current'),('derivative'),('derivatives'),('differential'),
           ('electric'),('electrical'),('engineering'),('equation'),('equations'),('finance'),
           ('formula'),('formulas'),('geometry'),('interest'),('integral'),('kinematics'),
           ('laws of motion'),('math'),('mathematics'),('mechanics'),('numerical'),('ohm'),
           ('physics'),('probability'),('ratio'),('resistance'),('solve'),('statistics'),
           ('stoichiometry'),('thermodynamics'),('unit conversion'),('units'),('variance'),('voltage')
),
scored AS (
    SELECT coalesce(n.course_program, '(none)') AS course_program,
           n.domain_context,
           -- leg 1: the DECLARED property, mirroring DomainContext.isQuantitative()
           (n.domain_context IN (
                'ENGINEERING_MATHEMATICS', 'ENGINEERING_SCIENCES', 'CIVIL_ENGINEERING',
                'PROFESSIONAL_PRACTICE_AND_REGULATION', 'NURSING', 'ACCOUNTANCY'
            ))
           -- leg 2: the unchanged keyword scan, which still runs for everything else
           OR EXISTS (
                SELECT 1 FROM kw
                WHERE lower(concat_ws(' ',
                          CASE n.domain_context
                              WHEN 'ENGINEERING_MATHEMATICS' THEN 'Engineering Mathematics'
                              WHEN 'ENGINEERING_SCIENCES' THEN 'Engineering Sciences'
                              WHEN 'CIVIL_ENGINEERING' THEN 'Civil Engineering'
                              WHEN 'PROFESSIONAL_PRACTICE_AND_REGULATION' THEN 'Professional Practice & Regulation'
                              WHEN 'GENERAL_EDUCATION' THEN 'General Education'
                              WHEN 'PROFESSIONAL_EDUCATION' THEN 'Professional Education'
                              WHEN 'NURSING' THEN 'Nursing'
                              WHEN 'ACCOUNTANCY' THEN 'Accountancy'
                              ELSE n.course_program
                          END,
                          n.subject,
                          array_to_string(n.tags, ' ')
                      )) LIKE '%' || kw.word || '%'
           ) AS quantitative
    FROM notes n
    JOIN users u ON u.id = n.owner_user_id
    WHERE u.role = 'ADMIN'
      AND n.visibility = 'PUBLIC'
)
SELECT course_program,
       count(*) AS notes,
       count(*) FILTER (WHERE quantitative) AS gets_guidance,
       count(*) FILTER (WHERE NOT quantitative) AS missing_guidance,
       round(100.0 * count(*) FILTER (WHERE NOT quantitative) / count(*), 1) AS pct_missing,
       count(*) FILTER (WHERE domain_context IS NOT NULL) AS classified
FROM scored
GROUP BY 1
ORDER BY missing_guidance DESC;
