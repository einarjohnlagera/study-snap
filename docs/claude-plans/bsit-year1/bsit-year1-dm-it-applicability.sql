-- ============================================================================
-- BSIT Year 1: add Information Technology to 29 Discrete Mathematics Notes
-- Status: RUN by the owner and VERIFIED read-only (SELECT) on 2026-11-05. Kept as a RECORD; idempotent, but DO NOT RE-RUN.
--   Committed 2026-11-05 08:12:46Z (29 rows, Information Technology). Verified: all 29 Notes carry it;
--   Computer Science and Computer Engineering are preserved. Prepared 2026-11-05 by the Note Strategist session.
-- THE OWNER EXECUTES THIS. (CLAUDE.md: production writes are owner-only.)
--
-- AUTHORITY
--   Owner (curator) decision 2026-11-05: include the reviewed Discrete Mathematics Notes in the BSIT
--   plan and tag them, including the three the independent review recommended holding (14 Power Sets,
--   Cartesian Products and Cardinality; 18 Partial Orders; 22 Sequences and Summations), on the rule
--   "include them if they give value to the study plan". Note 9 (Inference with Quantified
--   Statements) is the closest call and is a separate statement below so you can strike it.
--
-- WHAT IT DOES
--   Adds ONE Applicable Program, 'Information Technology', to each of 29 existing Notes, by exact id AND
--   exact title. At most 29 rows into note_course_program. It touches nothing else: not Subject, Domain
--   Context, Authored Depth, content, visibility, Study Packs or collections. Existing associations
--   (Computer Science, Computer Engineering) are preserved. Applicable Programs never reach a prompt
--   (ADR-001), so no Study Pack changes.
--
-- KNOWN LIMITATIONS RECORDED BEFORE THE TAG (not fixed here)
--   * Note 22 Sequences and Summations: its Q5 uses Theta notation that the Note body never defines.
--   * Notes 14 and 18 carry intro-plus depth (Cantor's theorem; lattices). Notes 8, 24 and 9 carry
--     moderate extra depth (resolution; extended Euclid; natural-deduction rules).
--   Fixing any of these means editing a canonical Note that BSCS also uses; that is a separate owner
--   decision and is not part of this file.
--
-- STATE READ 2026-11-03 (re-check with STEP 0 before running)
--   None of the 29 carried Information Technology. Each is COMPUTING / Discrete Mathematics / COLLEGE.
--
-- EXPECTED RESULT
--   STEP 1a inserts 28 rows and STEP 1b inserts 1 row (Note 9), 29 in total, if STEP 0 shows no existing
--   IT association. ON CONFLICT DO NOTHING makes it idempotent.
--
-- HOW TO RUN (use psql, not an IDE console)
--   Dry run (the file BEGINs and does not COMMIT, so exiting rolls back):
--     psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f docs/claude-plans/bsit-year1/bsit-year1-dm-it-applicability.sql
--   Apply: the same command with -c "COMMIT;" appended.
--     psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f docs/claude-plans/bsit-year1/bsit-year1-dm-it-applicability.sql -c "COMMIT;"
-- ============================================================================

-- STEP 0 (read-only): 0a must return 29 rows, every guard_ok = true and has_it = false.
WITH t(id, title) AS (VALUES
    ('edeb5f50-6a28-4dd9-a622-8682d15cdae4', 'Propositions and Logical Connectives'),
    ('39de3b6f-f300-42d9-aa30-eed0f50f6259', 'Truth Tables and Compound Propositions'),
    ('45341aa4-ba24-4b65-a209-4b684c909a64', 'Logical Equivalence and the Laws of Logic'),
    ('e8db8246-dc06-4e4e-9af6-ad19d6b3cf5b', 'Conditional, Biconditional, Converse and Contrapositive'),
    ('6621091a-1f26-460b-81f5-054c50f47963', 'Predicates and Quantifiers'),
    ('a5ca3860-6aad-4bd8-a34b-67f0d2c42b56', 'Nested Quantifiers and Negation'),
    ('768897ae-28e9-4304-8f64-734852e2b5f8', 'Translating Between English and Logic'),
    ('e2861c9f-c355-42e9-87fa-bd9a324ebac6', 'Valid Arguments and Rules of Inference'),
    ('021b67ab-d365-4ce3-9604-15537ed018b2', 'Inference with Quantified Statements'),
    ('c908c48d-f0fb-4e13-804a-5d78fa033f1c', 'Common Logical Fallacies'),
    ('4e991264-1dd9-4770-bd06-ca15d69612d2', 'Sets and Set Notation'),
    ('6c231725-6719-4274-84d9-9e6a228704b4', 'Set Operations and Venn Diagrams'),
    ('c4a020e3-82cc-405d-a732-4a3013c7a194', 'Set Identities and Membership Proofs'),
    ('a4d585f7-ef99-48db-98c4-cc5f29bd7fe0', 'Power Sets, Cartesian Products and Cardinality'),
    ('17335256-d635-4b99-8eaf-2654198f13fc', 'Relations and Their Representations'),
    ('1c5d914e-02ce-4d2c-8c5a-9db57979d4fd', 'Properties of Relations'),
    ('0c49a755-d725-46e1-9061-5f4d482842f6', 'Equivalence Relations and Partitions'),
    ('fa405f1c-276a-46ab-80d2-64a46d643a77', 'Partial Orders'),
    ('85566327-9c2e-431a-9cf5-8bd84cf8513d', 'Functions: Definition and Notation'),
    ('f3933743-e340-4348-95fb-dc82e1049c53', 'One-to-One, Onto and Bijective Functions'),
    ('416ddf8e-c2d7-4dcb-94c3-07f6a5b41666', 'Composition and Inverse of Functions'),
    ('888adb83-d076-4565-aa9c-ebe405186bcf', 'Sequences and Summations'),
    ('f67d2237-996a-4d83-97e0-1aafbb7cfbba', 'Divisibility, Parity and the Division Algorithm'),
    ('26ccf35e-04d8-42a2-8e91-950947d3980d', 'Greatest Common Divisors and the Euclidean Algorithm'),
    ('c275a182-64a8-4717-bda3-05fa8a5b19b6', 'Introduction to Mathematical Proof'),
    ('3d36cf2b-1d4c-45cc-a539-b45e34604ecd', 'Direct Proof'),
    ('a214fd5c-bbbc-4e9c-b209-b650df393a7c', 'Proof by Contraposition'),
    ('4150f7b6-4cc6-4ada-92ea-34b8aaefeba9', 'Proof by Contradiction'),
    ('a9d41b2f-2781-4d79-8937-ee0e8afc9763', 'Proof by Cases and Counterexamples')
)
SELECT t.title,
       (n.id IS NOT NULL AND n.title = t.title AND n.domain_context = 'COMPUTING'
        AND n.subject = 'Discrete Mathematics') AS guard_ok,
       EXISTS (SELECT 1 FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id
                WHERE x.note_id = n.id AND cp.name = 'Information Technology') AS has_it
FROM t LEFT JOIN notes n ON n.id = t.id::uuid
ORDER BY t.title;

-- 0b: the catalog program must resolve to exactly one row.
SELECT id, name FROM course_programs WHERE name = 'Information Technology';

BEGIN;

-- STEP 1a: 28 Notes (all except Note 9).
WITH t(id, title) AS (VALUES
    ('edeb5f50-6a28-4dd9-a622-8682d15cdae4', 'Propositions and Logical Connectives'),
    ('39de3b6f-f300-42d9-aa30-eed0f50f6259', 'Truth Tables and Compound Propositions'),
    ('45341aa4-ba24-4b65-a209-4b684c909a64', 'Logical Equivalence and the Laws of Logic'),
    ('e8db8246-dc06-4e4e-9af6-ad19d6b3cf5b', 'Conditional, Biconditional, Converse and Contrapositive'),
    ('6621091a-1f26-460b-81f5-054c50f47963', 'Predicates and Quantifiers'),
    ('a5ca3860-6aad-4bd8-a34b-67f0d2c42b56', 'Nested Quantifiers and Negation'),
    ('768897ae-28e9-4304-8f64-734852e2b5f8', 'Translating Between English and Logic'),
    ('e2861c9f-c355-42e9-87fa-bd9a324ebac6', 'Valid Arguments and Rules of Inference'),
    ('021b67ab-d365-4ce3-9604-15537ed018b2', 'Inference with Quantified Statements'),
    ('c908c48d-f0fb-4e13-804a-5d78fa033f1c', 'Common Logical Fallacies'),
    ('4e991264-1dd9-4770-bd06-ca15d69612d2', 'Sets and Set Notation'),
    ('6c231725-6719-4274-84d9-9e6a228704b4', 'Set Operations and Venn Diagrams'),
    ('c4a020e3-82cc-405d-a732-4a3013c7a194', 'Set Identities and Membership Proofs'),
    ('a4d585f7-ef99-48db-98c4-cc5f29bd7fe0', 'Power Sets, Cartesian Products and Cardinality'),
    ('17335256-d635-4b99-8eaf-2654198f13fc', 'Relations and Their Representations'),
    ('1c5d914e-02ce-4d2c-8c5a-9db57979d4fd', 'Properties of Relations'),
    ('0c49a755-d725-46e1-9061-5f4d482842f6', 'Equivalence Relations and Partitions'),
    ('fa405f1c-276a-46ab-80d2-64a46d643a77', 'Partial Orders'),
    ('85566327-9c2e-431a-9cf5-8bd84cf8513d', 'Functions: Definition and Notation'),
    ('f3933743-e340-4348-95fb-dc82e1049c53', 'One-to-One, Onto and Bijective Functions'),
    ('416ddf8e-c2d7-4dcb-94c3-07f6a5b41666', 'Composition and Inverse of Functions'),
    ('888adb83-d076-4565-aa9c-ebe405186bcf', 'Sequences and Summations'),
    ('f67d2237-996a-4d83-97e0-1aafbb7cfbba', 'Divisibility, Parity and the Division Algorithm'),
    ('26ccf35e-04d8-42a2-8e91-950947d3980d', 'Greatest Common Divisors and the Euclidean Algorithm'),
    ('c275a182-64a8-4717-bda3-05fa8a5b19b6', 'Introduction to Mathematical Proof'),
    ('3d36cf2b-1d4c-45cc-a539-b45e34604ecd', 'Direct Proof'),
    ('a214fd5c-bbbc-4e9c-b209-b650df393a7c', 'Proof by Contraposition'),
    ('4150f7b6-4cc6-4ada-92ea-34b8aaefeba9', 'Proof by Contradiction'),
    ('a9d41b2f-2781-4d79-8937-ee0e8afc9763', 'Proof by Cases and Counterexamples')
)
INSERT INTO note_course_program (id, note_id, course_program_id)
SELECT gen_random_uuid(), n.id, cp.id
FROM t
JOIN notes n ON n.id = t.id::uuid AND n.title = t.title
          AND n.domain_context = 'COMPUTING' AND n.subject = 'Discrete Mathematics'
CROSS JOIN course_programs cp
WHERE cp.name = 'Information Technology'
  AND t.title <> 'Inference with Quantified Statements'
ON CONFLICT (note_id, course_program_id) DO NOTHING;

-- STEP 1b: Note 9 on its own so it can be struck. Delete this statement to leave Note 9 untagged.
INSERT INTO note_course_program (id, note_id, course_program_id)
SELECT gen_random_uuid(), n.id, cp.id
FROM notes n CROSS JOIN course_programs cp
WHERE n.id = '021b67ab-d365-4ce3-9604-15537ed018b2'
  AND n.title = 'Inference with Quantified Statements'
  AND n.domain_context = 'COMPUTING' AND n.subject = 'Discrete Mathematics'
  AND cp.name = 'Information Technology'
ON CONFLICT (note_id, course_program_id) DO NOTHING;

-- STEP 2 (inside the transaction): verify before COMMIT. Expect 29 rows (28 if Note 9 was struck).
SELECT count(*) AS it_tagged_of_the_29
FROM note_course_program x
JOIN course_programs cp ON cp.id = x.course_program_id AND cp.name = 'Information Technology'
WHERE x.note_id IN ('edeb5f50-6a28-4dd9-a622-8682d15cdae4', '39de3b6f-f300-42d9-aa30-eed0f50f6259', '45341aa4-ba24-4b65-a209-4b684c909a64', 'e8db8246-dc06-4e4e-9af6-ad19d6b3cf5b', '6621091a-1f26-460b-81f5-054c50f47963', 'a5ca3860-6aad-4bd8-a34b-67f0d2c42b56', '768897ae-28e9-4304-8f64-734852e2b5f8', 'e2861c9f-c355-42e9-87fa-bd9a324ebac6', '021b67ab-d365-4ce3-9604-15537ed018b2', 'c908c48d-f0fb-4e13-804a-5d78fa033f1c', '4e991264-1dd9-4770-bd06-ca15d69612d2', '6c231725-6719-4274-84d9-9e6a228704b4', 'c4a020e3-82cc-405d-a732-4a3013c7a194', 'a4d585f7-ef99-48db-98c4-cc5f29bd7fe0', '17335256-d635-4b99-8eaf-2654198f13fc', '1c5d914e-02ce-4d2c-8c5a-9db57979d4fd', '0c49a755-d725-46e1-9061-5f4d482842f6', 'fa405f1c-276a-46ab-80d2-64a46d643a77', '85566327-9c2e-431a-9cf5-8bd84cf8513d', 'f3933743-e340-4348-95fb-dc82e1049c53', '416ddf8e-c2d7-4dcb-94c3-07f6a5b41666', '888adb83-d076-4565-aa9c-ebe405186bcf', 'f67d2237-996a-4d83-97e0-1aafbb7cfbba', '26ccf35e-04d8-42a2-8e91-950947d3980d', 'c275a182-64a8-4717-bda3-05fa8a5b19b6', '3d36cf2b-1d4c-45cc-a539-b45e34604ecd', 'a214fd5c-bbbc-4e9c-b209-b650df393a7c', '4150f7b6-4cc6-4ada-92ea-34b8aaefeba9', 'a9d41b2f-2781-4d79-8937-ee0e8afc9763');

-- COMMIT;   -- supplied by the -c "COMMIT;" above. If STEP 1a/1b inserted other than 28 + 1, or STEP 2
--           -- does not show 29 (28), ROLLBACK and report what STEP 0 showed.

-- ============================================================================
-- ROLLBACK (only after a COMMIT you want to undo; valid when STEP 0 showed NO existing IT rows)
-- ============================================================================
-- BEGIN;
-- DELETE FROM note_course_program x
-- USING course_programs cp
-- WHERE cp.id = x.course_program_id AND cp.name = 'Information Technology'
--   AND x.note_id IN ('edeb5f50-6a28-4dd9-a622-8682d15cdae4', '39de3b6f-f300-42d9-aa30-eed0f50f6259', '45341aa4-ba24-4b65-a209-4b684c909a64', 'e8db8246-dc06-4e4e-9af6-ad19d6b3cf5b', '6621091a-1f26-460b-81f5-054c50f47963', 'a5ca3860-6aad-4bd8-a34b-67f0d2c42b56', '768897ae-28e9-4304-8f64-734852e2b5f8', 'e2861c9f-c355-42e9-87fa-bd9a324ebac6', '021b67ab-d365-4ce3-9604-15537ed018b2', 'c908c48d-f0fb-4e13-804a-5d78fa033f1c', '4e991264-1dd9-4770-bd06-ca15d69612d2', '6c231725-6719-4274-84d9-9e6a228704b4', 'c4a020e3-82cc-405d-a732-4a3013c7a194', 'a4d585f7-ef99-48db-98c4-cc5f29bd7fe0', '17335256-d635-4b99-8eaf-2654198f13fc', '1c5d914e-02ce-4d2c-8c5a-9db57979d4fd', '0c49a755-d725-46e1-9061-5f4d482842f6', 'fa405f1c-276a-46ab-80d2-64a46d643a77', '85566327-9c2e-431a-9cf5-8bd84cf8513d', 'f3933743-e340-4348-95fb-dc82e1049c53', '416ddf8e-c2d7-4dcb-94c3-07f6a5b41666', '888adb83-d076-4565-aa9c-ebe405186bcf', 'f67d2237-996a-4d83-97e0-1aafbb7cfbba', '26ccf35e-04d8-42a2-8e91-950947d3980d', 'c275a182-64a8-4717-bda3-05fa8a5b19b6', '3d36cf2b-1d4c-45cc-a539-b45e34604ecd', 'a214fd5c-bbbc-4e9c-b209-b650df393a7c', '4150f7b6-4cc6-4ada-92ea-34b8aaefeba9', 'a9d41b2f-2781-4d79-8937-ee0e8afc9763');   -- expect DELETE 29 (28)
-- COMMIT;
-- If STEP 0 showed some IT rows already present, delete only the rows STEP 1 actually inserted.
