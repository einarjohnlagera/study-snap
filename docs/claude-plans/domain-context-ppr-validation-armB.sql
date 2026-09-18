-- Domain Context Taxonomy Calibration — §A7 RFBT validation, Arm B
-- Owner-run only. Claude does not execute this — CLAUDE.md's production read-only rule:
-- approving the Stage 2 plan is not approval to execute the write it names.
--
-- Context: docs/claude-plans/domain-context-biomedical-business-calibration-stage2.md §A7.
-- Owner decision 3 (widen PROFESSIONAL_PRACTICE_AND_REGULATION's description to cover
-- Business Law / RFBT) is gated on this two-arm comparison. Arm A is the current behaviour
-- (domain_context IS NULL, single Accountancy program -> resolveCourseProgram emits
-- "Domain: Accountancy"). Arm B below sets domain_context so the same three notes instead
-- emit "Domain: Professional Practice & Regulation", then each must be regenerated and the
-- two outputs compared per §A7's pass/fail condition.
--
-- Verified 2026-09-14 (read-only SELECT) — all three notes exist, are canonical
-- (copied_from_note_id IS NULL), and currently carry domain_context IS NULL:
--   227540b8-a063-460f-9d90-d6755c71f3b9  Corporation Code in Philippine Business Law
--   a4fcaf79-7cae-42a0-97a4-3ee83eed4f35  Obligations and Contracts in Accountancy's Regulatory Framework
--   7a7aba06-4123-4798-8159-55d87d3697b9  Negotiable Instruments in Accountancy and Business Law

-- STEP 1 — Arm B write. Expected: UPDATE 3.
UPDATE notes
SET domain_context = 'PROFESSIONAL_PRACTICE_AND_REGULATION'
WHERE id IN (
    '227540b8-a063-460f-9d90-d6755c71f3b9',
    'a4fcaf79-7cae-42a0-97a4-3ee83eed4f35',
    '7a7aba06-4123-4798-8159-55d87d3697b9'
)
AND copied_from_note_id IS NULL
AND domain_context IS NULL;

-- STEP 2 — verify it landed. Expected: 3 rows, all domain_context = 'PROFESSIONAL_PRACTICE_AND_REGULATION'.
SELECT id, title, domain_context
FROM notes
WHERE id IN (
    '227540b8-a063-460f-9d90-d6755c71f3b9',
    'a4fcaf79-7cae-42a0-97a4-3ee83eed4f35',
    '7a7aba06-4123-4798-8159-55d87d3697b9'
);

-- STEP 3 — after verifying, regenerate each of the three notes' Study Packs from the app
-- (owner action, not a SQL step) and compare Arm B's output against Arm A's existing
-- Study Pack content, per §A7's pass condition: Arm B must preserve statutory citations
-- (RA numbers, PD 442), legal terminology and doctrinal structure, and must NOT import
-- engineering-contract or construction framing. Do NOT test an ACCOUNTANCY arm — it is
-- quantitative = true and would confound the comparison.

-- STEP 4 — revert to Arm A once the comparison is recorded (whether it passes or fails —
-- this is a validation read, not a permanent reclassification; a real classification of
-- these three notes is separate curator work, per the plan's §A11).
-- Expected: UPDATE 3.
UPDATE notes
SET domain_context = NULL
WHERE id IN (
    '227540b8-a063-460f-9d90-d6755c71f3b9',
    'a4fcaf79-7cae-42a0-97a4-3ee83eed4f35',
    '7a7aba06-4123-4798-8159-55d87d3697b9'
)
AND domain_context = 'PROFESSIONAL_PRACTICE_AND_REGULATION';
