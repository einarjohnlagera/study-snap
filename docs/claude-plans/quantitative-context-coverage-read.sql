-- Quantitative-context coverage read — sizes the defect found while assessing the
-- Domain Context Catalog proposal (docs/claude-plans/domain-context-catalog-assessment.md).
--
-- ⚠️ RUN AGAINST PRODUCTION. Read-only: three SELECTs, no writes, no DDL.
-- No psql meta-commands, so it pastes into DBeaver / JetBrains / pgAdmin as-is.
--
-- THE DEFECT. `OpenAiLlmStudyPackService.isQuantitativeContext` decides whether computation
-- guidance enters the prompt by lowercasing a haystack and substring-matching it against 49
-- keywords. The haystack is: the Domain Context's DISPLAY LABEL (or `course_program` when no
-- domain is set) + subject + tags + concept hints + summary. At the Quick Review
-- developer-prompt call site the last two are empty, so the haystack is only
-- domain-or-program + subject + tags.
--
-- Five of the eight labels trip NO keyword — verified directly:
--     Engineering Mathematics  YES (engineering, math, mathematics)
--     Engineering Sciences     YES (engineering)
--     Civil Engineering        YES (engineering)
--     Professional Practice & Regulation   ** NO **
--     General Education                    ** NO **
--     Professional Education               ** NO **
--     Nursing                              ** NO **
--     Accountancy                          ** NO **
--
-- `Accountancy` is the sharpest case: the keyword list contains "accounting", but
-- `'accounting' NOT IN 'accountancy'` as a substring, so the CPA licensure domain — full of
-- amortization, cash flow, interest and ratios, all of which ARE keywords — does not match on
-- its own name.
--
-- WHAT THIS READ DECIDES. Those notes may still be rescued by their subject or tags. This
-- measures how often they are.
--     Rescued most of the time  -> latent gap; fix it cheaply and move on.
--     Rescued rarely            -> computation guidance is OFF today across the two largest
--                                  unclassified programs, which is a live quality defect and
--                                  the release's headline item.


-- The 49 keywords, verbatim from QUANTITATIVE_KEYWORDS.
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
-- Rebuild the exact haystack the Quick Review developer prompt sees: the domain's display
-- label (NOT the stored enum name) falling back to course_program, plus subject and tags.
hay AS (
    SELECT n.id,
           coalesce(n.course_program, '(none)') AS course_program,
           coalesce(n.domain_context, '(none)') AS domain_context,
           n.subject,
           lower(concat_ws(' ',
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
           )) AS haystack
    FROM notes n
    JOIN users u ON u.id = n.owner_user_id
    WHERE u.role = 'ADMIN'
      AND n.visibility = 'PUBLIC'
),
scored AS (
    SELECT h.*,
           EXISTS (SELECT 1 FROM kw WHERE h.haystack LIKE '%' || kw.word || '%') AS quantitative
    FROM hay h
)

-- ===========================================================================
-- QUERY 1 — Per program: how many curator public notes get computation guidance?
--
-- This is the headline. Read the `missing_guidance` column for Accountancy and Nursing.
-- ===========================================================================
SELECT course_program,
       count(*) AS notes,
       count(*) FILTER (WHERE quantitative) AS gets_guidance,
       count(*) FILTER (WHERE NOT quantitative) AS missing_guidance,
       round(100.0 * count(*) FILTER (WHERE NOT quantitative) / count(*), 1) AS pct_missing
FROM scored
GROUP BY 1
ORDER BY missing_guidance DESC;


-- ===========================================================================
-- QUERY 2 — Which subjects fail, in the programs that matter most?
--
-- Names the specific material generating quizzes without computation guidance, so the fix can
-- be checked against real cases rather than assumed. If these subjects look computational to
-- you and appear here, the defect is real and user-visible.
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
hay AS (
    SELECT n.id,
           coalesce(n.course_program, '(none)') AS course_program,
           n.subject,
           lower(concat_ws(' ',
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
           )) AS haystack
    FROM notes n
    JOIN users u ON u.id = n.owner_user_id
    WHERE u.role = 'ADMIN'
      AND n.visibility = 'PUBLIC'
      AND coalesce(n.course_program, '') IN ('Accountancy', 'Nursing', 'Education', 'Architecture')
)
SELECT course_program,
       coalesce(subject, '(none)') AS subject,
       count(*) AS notes_missing_guidance
FROM hay
WHERE NOT EXISTS (SELECT 1 FROM kw WHERE hay.haystack LIKE '%' || kw.word || '%')
GROUP BY 1, 2
ORDER BY notes_missing_guidance DESC, 1, 2;


-- ===========================================================================
-- QUERY 3 — Sanity check: does the haystack ever depend on tags alone?
--
-- If tags are what rescue most notes, the fix is more fragile than it looks — tags are
-- free-text and per-note, so coverage would vary note by note within one subject.
-- ===========================================================================
WITH kw(word) AS (
    VALUES ('accounting'),('algebra'),('amortization'),('analysis'),('anatomy'),('balance'),
           ('calculus'),('cash flow'),('chemistry'),('computation'),('compute'),('equation'),
           ('finance'),('formula'),('geometry'),('interest'),('integral'),('math'),('mathematics'),
           ('numerical'),('probability'),('ratio'),('solve'),('statistics'),('units'),('variance')
)
SELECT count(*) FILTER (WHERE by_subject) AS rescued_by_subject,
       count(*) FILTER (WHERE NOT by_subject AND by_tags) AS rescued_only_by_tags,
       count(*) AS notes_considered
FROM (
    SELECT EXISTS (SELECT 1 FROM kw WHERE lower(coalesce(n.subject, '')) LIKE '%' || kw.word || '%') AS by_subject,
           EXISTS (SELECT 1 FROM kw WHERE lower(array_to_string(n.tags, ' ')) LIKE '%' || kw.word || '%') AS by_tags
    FROM notes n
    JOIN users u ON u.id = n.owner_user_id
    WHERE u.role = 'ADMIN'
      AND n.visibility = 'PUBLIC'
      AND coalesce(n.course_program, '') IN ('Accountancy', 'Nursing')
) t;


-- ===========================================================================
-- HOW TO READ IT
--
-- Query 1 `pct_missing` low for Accountancy/Nursing
--   -> subjects rescue it. Latent gap: fix the mechanism, no urgency, fold into the
--      descriptions release.
--
-- Query 1 `pct_missing` high for Accountancy/Nursing
--   -> computation guidance is OFF today on the CPA and nursing catalogs. That is a live
--      generation-quality defect on the two largest unclassified programs, and it becomes the
--      release's headline rather than a fold.
--
-- Query 3 shows most rescues come from TAGS rather than subject
--   -> coverage is per-note and accidental, so even a "low pct_missing" is unstable. Treat as
--      the high case.
--
-- ⚠️ Either way the mechanism is wrong for domain labels: substring-matching a curated,
-- closed vocabulary against 49 English keywords is guessing at something that could simply be
-- declared per value. That conclusion does not depend on these numbers.
