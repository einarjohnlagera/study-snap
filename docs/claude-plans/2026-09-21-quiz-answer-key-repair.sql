-- ============================================================================
-- Quick Review Correctness Incident — Owner-Run Repair
-- Incident doc: docs/claude-findings/2026-09-19-quick-review-percentage-increase-correctness-incident.md
-- Owner decisions locked: 2026-09-21 (§Q.1 of that doc)
-- ============================================================================
-- ⚠️ CLAUDE DOES NOT EXECUTE THIS FILE. Per CLAUDE.md, production writes are
-- owner-only, with no exceptions. This file was prepared read-only: every
-- current-state value below was re-confirmed against production by SELECT
-- immediately before this file was written (2026-09-21), so there should be
-- no drift between what's stated here and what a fresh read shows — but if
-- a pre-check count below doesn't match, STOP and re-investigate rather than
-- forcing the UPDATE through.
--
-- ⚠️ CORRECTED 2026-09-22, discovered while the owner was running this file:
-- Section B's original "15 array elements, one pool contains 2 near-identical
-- duplicate defects" claim was FALSE. A live re-read of pool 2437d442 found
-- the "second duplicate" is a DIFFERENT question (different wording, DIFFERENT
-- choices array) that is already correctly keyed — not a duplicate of the
-- genuine defect. Real count: 14 pool rows, 14 array elements, not 15. The
-- B.1 UPDATE statement itself was always safe regardless of this comment
-- error, because it matches on the defective question's specific choices
-- array (which the correct question does not share) plus correctIndex — see
-- the corrected note in Section B below.
--
-- FOUR INDEPENDENT SECTIONS. Run them separately; each is self-contained and
-- safe to skip without affecting the others:
--   A. study_packs        — 12 rows  (6 confirmed defects × public+copy pack)
--   B. exam_question_pool — 14 pool rows, fixing 14 array elements
--   C. challenge_quiz_question_bank — 10 rows, all on the owner's own
--                            admin/test account, zero learner exposure
--   D. RETROACTIVE HISTORY CORRECTION — 2 rows. Owner-approved explicitly
--      under "do not silently rewrite history" — kept separate on purpose.
--      Run A–C without D if you want the key fixes now and want to decide
--      on D separately.
--
-- EVERY UPDATE's WHERE clause re-asserts the CURRENT WRONG value before
-- writing. This makes every statement idempotent: re-running this whole file
-- a second time is a safe no-op (nothing left to match), and a row someone
-- already hand-fixed will not be touched or re-broken.
--
-- EXPLICITLY EXCLUDED — do NOT attempt to "fix" these by extending this
-- file. Each is a distinct, separate finding, not a key/explanation
-- contradiction repairable by an index swap:
--   - exam_question_pool candidate `f654d0e6-…`: stored choice/explanation
--     text contains a literal Unicode control character (U+0002) where an
--     inequality symbol should be. This is a DATA CORRUPTION bug, not an
--     answer-key defect. Needs its own scan/finding, not a hand repair here.
--   - exam_question_pool candidate `e2dac890-…`: the explanation concludes a
--     value ($0 / "zero") that is NOT among the four offered choices at all.
--     Not an index-swap — either the choice set is incomplete or the
--     underlying formula is non-standard. Needs curator/domain review.
--   - exam_question_pool candidate `c916ba22-…` and challenge-bank
--     `b712ad0d-…`: genuinely ambiguous / self-contradictory generation,
--     flagged for curator review or deletion, not a determinable correct
--     index. See §T.3 of the incident doc for full detail on all four.
-- ============================================================================


-- ============================================================================
-- SECTION A — study_packs.quiz  (12 rows: 6 confirmed defects, each present
-- in a public source pack and its one private learner copy)
-- Expected rows affected: 12 (one array element per statement)
-- ============================================================================

-- A.0 Pre-check: expect 12 rows, each showing the CURRENT WRONG state below.
SELECT sp.id::text AS pack_id, elem->>'question' AS question,
       elem->'choices' AS choices, elem->>'correctIndex' AS current_correct_index
FROM study_packs sp, jsonb_array_elements(sp.quiz) AS elem
WHERE sp.id::text IN (
  '1ccb19f9-8542-47bf-992a-cf0a0fb3ce0a', 'c8dd3aa2-6067-4d84-b9bd-85dc1a583770',
  '2e89b22c-f382-48f2-a57f-c50bd3927593', 'b2f7e4f3-f564-4bd5-b565-84831a7dc7fb',
  '400c9315-d1e8-4b7d-a930-e045433ce3be', '70409a24-2d94-4fde-b943-dc860131f51f',
  '5e2e1633-5be0-4fb2-b1de-0dab91f3719a', '9756f274-43f9-4994-9dc3-5abe66679f72',
  '6a2d6712-f25a-4f9d-a3bd-b8f41f598bc8', '9b96153b-f65e-4cd1-b389-bc249725d950',
  '771a06a8-4bdc-4b03-99e6-1c7dbe087c28', 'ccabeefc-90a0-481f-8dfc-2677ab33410e'
)
AND elem->'choices' IN (
  '["15%", "30%", "25%", "10%"]'::jsonb,
  '["0.833 m³/s", "8.33 m³/s", "83.3 m³/s", "0.0833 m³/s"]'::jsonb,
  '["500 units", "333 units", "667 units", "250 units"]'::jsonb,
  '["216 kPa", "144 kPa", "194.4 kPa", "259.2 kPa"]'::jsonb,
  '["50 m", "75 m", "100 m", "86.6 m"]'::jsonb,
  '["9.18 m", "18.36 m", "4.59 m", "2.29 m"]'::jsonb
);
-- Expect exactly 12 rows.

-- A.1 — "Calculate the percentage increase when a quantity changes from 50 to 65."
--       Choices: 15% / 30% / 25% / 10%.  keyed idx2 (25%) → correct idx1 (30%).
--       This is the exact reported incident question (§B of the incident doc).
UPDATE study_packs SET quiz = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["15%", "30%", "25%", "10%"]'::jsonb
              AND (elem->>'correctIndex')::int = 2
         THEN jsonb_set(elem, '{correctIndex}', '1'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(quiz) AS elem)
WHERE id = '1ccb19f9-8542-47bf-992a-cf0a0fb3ce0a'
  AND quiz @> jsonb_build_array(jsonb_build_object('choices', '["15%", "30%", "25%", "10%"]'::jsonb, 'correctIndex', 2));

UPDATE study_packs SET quiz = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["15%", "30%", "25%", "10%"]'::jsonb
              AND (elem->>'correctIndex')::int = 2
         THEN jsonb_set(elem, '{correctIndex}', '1'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(quiz) AS elem)
WHERE id = 'c8dd3aa2-6067-4d84-b9bd-85dc1a583770'
  AND quiz @> jsonb_build_array(jsonb_build_object('choices', '["15%", "30%", "25%", "10%"]'::jsonb, 'correctIndex', 2));

-- A.2 — Rational Method culvert peak discharge (C=0.6, i=50mm/hr, A=10ha).
--       Q = CiA = 0.6×0.05×100,000 = 3,000 m³/hr ÷3600 = 0.833 m³/s.
--       keyed idx1 (8.33) → correct idx0 (0.833).
UPDATE study_packs SET quiz = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["0.833 m³/s", "8.33 m³/s", "83.3 m³/s", "0.0833 m³/s"]'::jsonb
              AND (elem->>'correctIndex')::int = 1
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(quiz) AS elem)
WHERE id = '2e89b22c-f382-48f2-a57f-c50bd3927593'
  AND quiz @> jsonb_build_array(jsonb_build_object('choices', '["0.833 m³/s", "8.33 m³/s", "83.3 m³/s", "0.0833 m³/s"]'::jsonb, 'correctIndex', 1));

UPDATE study_packs SET quiz = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["0.833 m³/s", "8.33 m³/s", "83.3 m³/s", "0.0833 m³/s"]'::jsonb
              AND (elem->>'correctIndex')::int = 1
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(quiz) AS elem)
WHERE id = 'b2f7e4f3-f564-4bd5-b565-84831a7dc7fb'
  AND quiz @> jsonb_build_array(jsonb_build_object('choices', '["0.833 m³/s", "8.33 m³/s", "83.3 m³/s", "0.0833 m³/s"]'::jsonb, 'correctIndex', 1));

-- A.3 — Break-even point (FC=$10,000, SP=$50, VC=$30).
--       BEP = FC/(SP-VC) = 10,000/20 = 500 units.
--       keyed idx2 (667) → correct idx0 (500).
UPDATE study_packs SET quiz = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["500 units", "333 units", "667 units", "250 units"]'::jsonb
              AND (elem->>'correctIndex')::int = 2
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(quiz) AS elem)
WHERE id = '400c9315-d1e8-4b7d-a930-e045433ce3be'
  AND quiz @> jsonb_build_array(jsonb_build_object('choices', '["500 units", "333 units", "667 units", "250 units"]'::jsonb, 'correctIndex', 2));

UPDATE study_packs SET quiz = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["500 units", "333 units", "667 units", "250 units"]'::jsonb
              AND (elem->>'correctIndex')::int = 2
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(quiz) AS elem)
WHERE id = '70409a24-2d94-4fde-b943-dc860131f51f'
  AND quiz @> jsonb_build_array(jsonb_build_object('choices', '["500 units", "333 units", "667 units", "250 units"]'::jsonb, 'correctIndex', 2));

-- A.4 — Rankine passive earth pressure (φ=30°, γ=18 kN/m³, z=4m).
--       p_p = K_p×γ×z = 3×18×4 = 216 kPa.
--       keyed idx3 (259.2) → correct idx0 (216).
UPDATE study_packs SET quiz = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["216 kPa", "144 kPa", "194.4 kPa", "259.2 kPa"]'::jsonb
              AND (elem->>'correctIndex')::int = 3
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(quiz) AS elem)
WHERE id = '5e2e1633-5be0-4fb2-b1de-0dab91f3719a'
  AND quiz @> jsonb_build_array(jsonb_build_object('choices', '["216 kPa", "144 kPa", "194.4 kPa", "259.2 kPa"]'::jsonb, 'correctIndex', 3));

UPDATE study_packs SET quiz = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["216 kPa", "144 kPa", "194.4 kPa", "259.2 kPa"]'::jsonb
              AND (elem->>'correctIndex')::int = 3
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(quiz) AS elem)
WHERE id = '9756f274-43f9-4994-9dc3-5abe66679f72'
  AND quiz @> jsonb_build_array(jsonb_build_object('choices', '["216 kPa", "144 kPa", "194.4 kPa", "259.2 kPa"]'::jsonb, 'correctIndex', 3));

-- A.5 — Traverse latitude (d=100m, θ=60°).
--       L = d·cosθ = 100×0.5 = 50 m.
--       keyed idx3 (86.6, = 100×sin60° — a sin/cos mixup) → correct idx0 (50).
UPDATE study_packs SET quiz = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["50 m", "75 m", "100 m", "86.6 m"]'::jsonb
              AND (elem->>'correctIndex')::int = 3
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(quiz) AS elem)
WHERE id = '6a2d6712-f25a-4f9d-a3bd-b8f41f598bc8'
  AND quiz @> jsonb_build_array(jsonb_build_object('choices', '["50 m", "75 m", "100 m", "86.6 m"]'::jsonb, 'correctIndex', 3));

UPDATE study_packs SET quiz = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["50 m", "75 m", "100 m", "86.6 m"]'::jsonb
              AND (elem->>'correctIndex')::int = 3
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(quiz) AS elem)
WHERE id = '9b96153b-f65e-4cd1-b389-bc249725d950'
  AND quiz @> jsonb_build_array(jsonb_build_object('choices', '["50 m", "75 m", "100 m", "86.6 m"]'::jsonb, 'correctIndex', 3));

-- A.6 — Darcy-Weisbach head loss (V=3m/s, L=50m, D=0.1m, f=0.02, g=9.81).
--       h_f = f(L/D)(V²/2g) = 0.02×500×(9/19.62) = 10×0.4587 ≈ 4.59 m.
--       keyed idx0 (9.18, exactly 2× correct) → correct idx2 (4.59).
--       The model's own explanation states "The correct answer is 4.59 m,
--       which is option C" while keying idx0 — the strongest single piece
--       of evidence for the root cause in the whole incident (§J.2).
UPDATE study_packs SET quiz = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["9.18 m", "18.36 m", "4.59 m", "2.29 m"]'::jsonb
              AND (elem->>'correctIndex')::int = 0
         THEN jsonb_set(elem, '{correctIndex}', '2'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(quiz) AS elem)
WHERE id = '771a06a8-4bdc-4b03-99e6-1c7dbe087c28'
  AND quiz @> jsonb_build_array(jsonb_build_object('choices', '["9.18 m", "18.36 m", "4.59 m", "2.29 m"]'::jsonb, 'correctIndex', 0));

UPDATE study_packs SET quiz = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["9.18 m", "18.36 m", "4.59 m", "2.29 m"]'::jsonb
              AND (elem->>'correctIndex')::int = 0
         THEN jsonb_set(elem, '{correctIndex}', '2'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(quiz) AS elem)
WHERE id = 'ccabeefc-90a0-481f-8dfc-2677ab33410e'
  AND quiz @> jsonb_build_array(jsonb_build_object('choices', '["9.18 m", "18.36 m", "4.59 m", "2.29 m"]'::jsonb, 'correctIndex', 0));

-- A.9 Post-check: expect 0 rows (every old-wrong-value match is now gone).
SELECT sp.id::text AS pack_id, elem->>'question' AS question, elem->>'correctIndex' AS still_wrong
FROM study_packs sp, jsonb_array_elements(sp.quiz) AS elem
WHERE sp.id::text IN (
  '1ccb19f9-8542-47bf-992a-cf0a0fb3ce0a', 'c8dd3aa2-6067-4d84-b9bd-85dc1a583770',
  '2e89b22c-f382-48f2-a57f-c50bd3927593', 'b2f7e4f3-f564-4bd5-b565-84831a7dc7fb',
  '400c9315-d1e8-4b7d-a930-e045433ce3be', '70409a24-2d94-4fde-b943-dc860131f51f',
  '5e2e1633-5be0-4fb2-b1de-0dab91f3719a', '9756f274-43f9-4994-9dc3-5abe66679f72',
  '6a2d6712-f25a-4f9d-a3bd-b8f41f598bc8', '9b96153b-f65e-4cd1-b389-bc249725d950',
  '771a06a8-4bdc-4b03-99e6-1c7dbe087c28', 'ccabeefc-90a0-481f-8dfc-2677ab33410e'
)
AND (
  (elem->'choices' = '["15%", "30%", "25%", "10%"]'::jsonb AND (elem->>'correctIndex')::int = 2) OR
  (elem->'choices' = '["0.833 m³/s", "8.33 m³/s", "83.3 m³/s", "0.0833 m³/s"]'::jsonb AND (elem->>'correctIndex')::int = 1) OR
  (elem->'choices' = '["500 units", "333 units", "667 units", "250 units"]'::jsonb AND (elem->>'correctIndex')::int = 2) OR
  (elem->'choices' = '["216 kPa", "144 kPa", "194.4 kPa", "259.2 kPa"]'::jsonb AND (elem->>'correctIndex')::int = 3) OR
  (elem->'choices' = '["50 m", "75 m", "100 m", "86.6 m"]'::jsonb AND (elem->>'correctIndex')::int = 3) OR
  (elem->'choices' = '["9.18 m", "18.36 m", "4.59 m", "2.29 m"]'::jsonb AND (elem->>'correctIndex')::int = 0)
);
-- Expect 0 rows. Then re-run the A.0 query (change nothing else) and confirm
-- all 12 `current_correct_index` values now read: 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 2, 2
-- (in pack order 1ccb19f9, c8dd3aa2, 2e89b22c, b2f7e4f3, 400c9315, 70409a24,
--  5e2e1633, 9756f274, 6a2d6712, 9b96153b, 771a06a8, ccabeefc).


-- ============================================================================
-- SECTION B — exam_question_pool.questions  (14 pool rows, fixing 14 array
-- elements.
-- ⚠️ CORRECTED 2026-09-22: this section originally claimed pool 2437d442
-- contained TWO near-identical duplicate defective questions with identical
-- choices/keyed-index. Live re-verification found this false — the pool
-- actually holds two DIFFERENTLY-worded, DIFFERENT-choices questions on a
-- similar topic; only one (B.1's target) is defective, the other is already
-- correctly keyed. The B.1 UPDATE below was always safe under this
-- correction, since it matches the defective question's specific choices
-- array plus correctIndex — a condition the correctly-keyed question never
-- satisfies, so it was never at risk of being touched.
-- Expected rows affected: 14 pool rows updated; 14 array elements corrected.
-- Zero learner exposure on all 14 pools — served_question_keys is empty on
-- every one (verified during the extended review, §T of the incident doc).
-- ============================================================================

-- B.0 Pre-check.
SELECT eqp.id::text AS pool_id, elem->>'question' AS question,
       elem->'choices' AS choices, elem->>'correctIndex' AS current_correct_index
FROM exam_question_pool eqp, jsonb_array_elements(eqp.questions) AS elem
WHERE eqp.id::text IN (
  '2437d442-5b95-4383-ad92-790fc58c4557','2b01c6df-116b-448c-8792-82c3a7cfe97c',
  '2f82877b-e70f-424b-9723-6ba71d60c1f8','385d69db-76a3-4620-8510-5bee4b5f6ca7',
  '4ef82a80-4907-492c-bf9c-0f5e50b65f03','571244ab-afa6-409c-b962-7e4c9086a3ed',
  '69a963fb-44bc-48d7-9c24-f8db9978d8f4','76cad170-9249-45f1-b256-c4cab6eb1a6a',
  'aa48d9eb-865d-4f48-acc1-07bbaf674159','cfda9a7b-e2f9-4398-a47c-7a8649d3038a',
  'd2d91c72-5735-4158-9941-dfaa61ad3596','d7e8622e-ecd0-4185-88b6-766fb91663ab',
  'df62fc13-ff98-485f-b8dd-e594b450319c','e1d9b7fc-da50-42d9-b6c8-ee86f42e54aa'
)
AND elem->'choices' IN (
  '["$135,000", "$145,000", "$125,000", "$140,000"]'::jsonb,
  '["$5.00", "$4.50", "$4.00", "$5.50"]'::jsonb,
  '["$2,500 Favorable", "$2,500 Unfavorable", "$2,500 Neutral", "$0 No Variance"]'::jsonb,
  '["0.084", "0.120", "0.140", "0.196"]'::jsonb,
  '["10000", "100000", "1000000", "10000000"]'::jsonb,
  '["$42 favorable", "$42 unfavorable", "$20 favorable", "$20 unfavorable"]'::jsonb,
  '["120 units", "80 units", "110 units", "140 units"]'::jsonb,
  '["P100,000", "P150,000", "P50,000", "P200,000"]'::jsonb,
  '["$680.58", "$735.03", "$867.30", "$500.00"]'::jsonb,
  '["$485,000", "$400,000", "$515,000", "$415,000"]'::jsonb,
  '["10 A", "17.32 A", "30 A", "51.96 A"]'::jsonb,
  '["$31,000", "$27,000", "$35,000", "$33,000"]'::jsonb,
  '["+200 kJ", "-200 kJ", "+100 kJ", "-100 kJ"]'::jsonb,
  '["$45,000", "$65,000", "$55,000", "$35,000"]'::jsonb
);
-- Expect exactly 14 rows (corrected 2026-09-22 — see the header note above;
-- pool 2437d442 contributes only 1, not 2).

-- B.1 — Net cash, indirect method (NI $120,000, dep $30k, AR+$10k, AP-$5k).
--       120,000+30,000-10,000-5,000 = 135,000. keyed idx2 → correct idx0.
--       NOTE, CORRECTED 2026-09-22: this pool ALSO contains a differently-
--       worded question on the same topic with a DIFFERENT choices array
--       (["$115,000","$125,000","$135,000","$155,000"]), already correctly
--       keyed to $135,000. It is NOT a duplicate of this defect and this
--       statement cannot touch it — the WHERE/CASE match on this exact
--       choices array plus correctIndex=2, which that question does not have.
UPDATE exam_question_pool SET questions = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["$135,000", "$145,000", "$125,000", "$140,000"]'::jsonb
              AND (elem->>'correctIndex')::int = 2
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(questions) AS elem)
WHERE id = '2437d442-5b95-4383-ad92-790fc58c4557'
  AND questions @> jsonb_build_array(jsonb_build_object('choices', '["$135,000", "$145,000", "$125,000", "$140,000"]'::jsonb, 'correctIndex', 2));

-- B.2 — Basic EPS ((5,000,000-500,000)/1,000,000 = 4.50). keyed idx2 → correct idx1.
UPDATE exam_question_pool SET questions = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["$5.00", "$4.50", "$4.00", "$5.50"]'::jsonb
              AND (elem->>'correctIndex')::int = 2
         THEN jsonb_set(elem, '{correctIndex}', '1'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(questions) AS elem)
WHERE id = '2b01c6df-116b-448c-8792-82c3a7cfe97c'
  AND questions @> jsonb_build_array(jsonb_build_object('choices', '["$5.00", "$4.50", "$4.00", "$5.50"]'::jsonb, 'correctIndex', 2));

-- B.3 — Material price variance (std $8, actual $7.50 × 5,000 units).
--       (8-7.50)×5,000 = 2,500; standard>actual ⇒ favorable. keyed idx2 → correct idx0.
UPDATE exam_question_pool SET questions = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["$2,500 Favorable", "$2,500 Unfavorable", "$2,500 Neutral", "$0 No Variance"]'::jsonb
              AND (elem->>'correctIndex')::int = 2
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(questions) AS elem)
WHERE id = '2f82877b-e70f-424b-9723-6ba71d60c1f8'
  AND questions @> jsonb_build_array(jsonb_build_object('choices', '["$2,500 Favorable", "$2,500 Unfavorable", "$2,500 Neutral", "$0 No Variance"]'::jsonb, 'correctIndex', 2));

-- B.4 — Audit risk (IR .70 × CR .60 × DR .20 = 0.084). keyed idx1 → correct idx0.
UPDATE exam_question_pool SET questions = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["0.084", "0.120", "0.140", "0.196"]'::jsonb
              AND (elem->>'correctIndex')::int = 1
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(questions) AS elem)
WHERE id = '385d69db-76a3-4620-8510-5bee4b5f6ca7'
  AND questions @> jsonb_build_array(jsonb_build_object('choices', '["0.084", "0.120", "0.140", "0.196"]'::jsonb, 'correctIndex', 1));

-- B.5 — B-tree leaf nodes (height 4, 100 ptr/node): 100^3 = 1,000,000. keyed idx3 → correct idx2.
UPDATE exam_question_pool SET questions = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["10000", "100000", "1000000", "10000000"]'::jsonb
              AND (elem->>'correctIndex')::int = 3
         THEN jsonb_set(elem, '{correctIndex}', '2'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(questions) AS elem)
WHERE id = '4ef82a80-4907-492c-bf9c-0f5e50b65f03'
  AND questions @> jsonb_build_array(jsonb_build_object('choices', '["10000", "100000", "1000000", "10000000"]'::jsonb, 'correctIndex', 3));

-- B.6 — Material price variance (std $5, actual $4.80 × 210). (5-4.80)×210=42; favorable. keyed idx1 → correct idx0.
UPDATE exam_question_pool SET questions = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["$42 favorable", "$42 unfavorable", "$20 favorable", "$20 unfavorable"]'::jsonb
              AND (elem->>'correctIndex')::int = 1
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(questions) AS elem)
WHERE id = '571244ab-afa6-409c-b962-7e4c9086a3ed'
  AND questions @> jsonb_build_array(jsonb_build_object('choices', '["$42 favorable", "$42 unfavorable", "$20 favorable", "$20 unfavorable"]'::jsonb, 'correctIndex', 1));

-- B.7 — Metabolic rate (+20% of 100, direct proportional): 100×1.20=120. keyed idx1 → correct idx0.
UPDATE exam_question_pool SET questions = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["120 units", "80 units", "110 units", "140 units"]'::jsonb
              AND (elem->>'correctIndex')::int = 1
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(questions) AS elem)
WHERE id = '69a963fb-44bc-48d7-9c24-f8db9978d8f4'
  AND questions @> jsonb_build_array(jsonb_build_object('choices', '["120 units", "80 units", "110 units", "140 units"]'::jsonb, 'correctIndex', 1));

-- B.8 — Impairment loss (carrying P1,000,000; FV-costs P850,000; VIU P900,000).
--       recoverable = max(850k,900k) = 900k; loss = 1,000,000-900,000 = 100,000. keyed idx1 → correct idx0.
UPDATE exam_question_pool SET questions = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["P100,000", "P150,000", "P50,000", "P200,000"]'::jsonb
              AND (elem->>'correctIndex')::int = 1
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(questions) AS elem)
WHERE id = '76cad170-9249-45f1-b256-c4cab6eb1a6a'
  AND questions @> jsonb_build_array(jsonb_build_object('choices', '["P100,000", "P150,000", "P50,000", "P200,000"]'::jsonb, 'correctIndex', 1));

-- B.9 — PV of $1,000 in 5yr @ 8%: 1000/(1.08)^5 = 680.58. keyed idx2 → correct idx0.
UPDATE exam_question_pool SET questions = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["$680.58", "$735.03", "$867.30", "$500.00"]'::jsonb
              AND (elem->>'correctIndex')::int = 2
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(questions) AS elem)
WHERE id = 'aa48d9eb-865d-4f48-acc1-07bbaf674159'
  AND questions @> jsonb_build_array(jsonb_build_object('choices', '["$680.58", "$735.03", "$867.30", "$500.00"]'::jsonb, 'correctIndex', 2));

-- B.10 — Equity-method carrying amount (400,000+100,000+15,000=515,000). keyed idx3 → correct idx2.
UPDATE exam_question_pool SET questions = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["$485,000", "$400,000", "$515,000", "$415,000"]'::jsonb
              AND (elem->>'correctIndex')::int = 3
         THEN jsonb_set(elem, '{correctIndex}', '2'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(questions) AS elem)
WHERE id = 'cfda9a7b-e2f9-4398-a47c-7a8649d3038a'
  AND questions @> jsonb_build_array(jsonb_build_object('choices', '["$485,000", "$400,000", "$515,000", "$415,000"]'::jsonb, 'correctIndex', 3));

-- B.11 — Delta-connection phase current (line current 30A): I_Ph=I_L/√3=30/1.732=17.32A. keyed idx2 → correct idx1.
UPDATE exam_question_pool SET questions = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["10 A", "17.32 A", "30 A", "51.96 A"]'::jsonb
              AND (elem->>'correctIndex')::int = 2
         THEN jsonb_set(elem, '{correctIndex}', '1'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(questions) AS elem)
WHERE id = 'd2d91c72-5735-4158-9941-dfaa61ad3596'
  AND questions @> jsonb_build_array(jsonb_build_object('choices', '["10 A", "17.32 A", "30 A", "51.96 A"]'::jsonb, 'correctIndex', 2));

-- B.12 — Net cash, indirect method (NI $30,000, dep $4k, AR+$2k, AP-$1k): 30,000+4,000-2,000-1,000=31,000. keyed idx1 → correct idx0.
UPDATE exam_question_pool SET questions = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["$31,000", "$27,000", "$35,000", "$33,000"]'::jsonb
              AND (elem->>'correctIndex')::int = 1
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(questions) AS elem)
WHERE id = 'd7e8622e-ecd0-4185-88b6-766fb91663ab'
  AND questions @> jsonb_build_array(jsonb_build_object('choices', '["$31,000", "$27,000", "$35,000", "$33,000"]'::jsonb, 'correctIndex', 1));

-- B.13 — Thermodynamics ΔU (rejects 150kJ heat, 50kJ work done ON system): ΔU=Q+W_on=-150+50=-100kJ. keyed idx2 → correct idx3.
UPDATE exam_question_pool SET questions = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["+200 kJ", "-200 kJ", "+100 kJ", "-100 kJ"]'::jsonb
              AND (elem->>'correctIndex')::int = 2
         THEN jsonb_set(elem, '{correctIndex}', '3'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(questions) AS elem)
WHERE id = 'df62fc13-ff98-485f-b8dd-e594b450319c'
  AND questions @> jsonb_build_array(jsonb_build_object('choices', '["+200 kJ", "-200 kJ", "+100 kJ", "-100 kJ"]'::jsonb, 'correctIndex', 2));

-- B.14 — Net cash, indirect method (NI $50,000, dep $5k, AR+$10k): 50,000+5,000-10,000=45,000. keyed idx2 → correct idx0.
UPDATE exam_question_pool SET questions = (
  SELECT jsonb_agg(
    CASE WHEN elem->'choices' = '["$45,000", "$65,000", "$55,000", "$35,000"]'::jsonb
              AND (elem->>'correctIndex')::int = 2
         THEN jsonb_set(elem, '{correctIndex}', '0'::jsonb)
         ELSE elem END)
  FROM jsonb_array_elements(questions) AS elem)
WHERE id = 'e1d9b7fc-da50-42d9-b6c8-ee86f42e54aa'
  AND questions @> jsonb_build_array(jsonb_build_object('choices', '["$45,000", "$65,000", "$55,000", "$35,000"]'::jsonb, 'correctIndex', 2));

-- B.99 Post-check: expect 0 rows.
SELECT eqp.id::text AS pool_id, elem->>'question' AS question, elem->>'correctIndex' AS still_wrong
FROM exam_question_pool eqp, jsonb_array_elements(eqp.questions) AS elem
WHERE eqp.id::text IN (
  '2437d442-5b95-4383-ad92-790fc58c4557','2b01c6df-116b-448c-8792-82c3a7cfe97c',
  '2f82877b-e70f-424b-9723-6ba71d60c1f8','385d69db-76a3-4620-8510-5bee4b5f6ca7',
  '4ef82a80-4907-492c-bf9c-0f5e50b65f03','571244ab-afa6-409c-b962-7e4c9086a3ed',
  '69a963fb-44bc-48d7-9c24-f8db9978d8f4','76cad170-9249-45f1-b256-c4cab6eb1a6a',
  'aa48d9eb-865d-4f48-acc1-07bbaf674159','cfda9a7b-e2f9-4398-a47c-7a8649d3038a',
  'd2d91c72-5735-4158-9941-dfaa61ad3596','d7e8622e-ecd0-4185-88b6-766fb91663ab',
  'df62fc13-ff98-485f-b8dd-e594b450319c','e1d9b7fc-da50-42d9-b6c8-ee86f42e54aa'
)
AND (
  (elem->'choices' = '["$135,000", "$145,000", "$125,000", "$140,000"]'::jsonb AND (elem->>'correctIndex')::int = 2) OR
  (elem->'choices' = '["$5.00", "$4.50", "$4.00", "$5.50"]'::jsonb AND (elem->>'correctIndex')::int = 2) OR
  (elem->'choices' = '["$2,500 Favorable", "$2,500 Unfavorable", "$2,500 Neutral", "$0 No Variance"]'::jsonb AND (elem->>'correctIndex')::int = 2) OR
  (elem->'choices' = '["0.084", "0.120", "0.140", "0.196"]'::jsonb AND (elem->>'correctIndex')::int = 1) OR
  (elem->'choices' = '["10000", "100000", "1000000", "10000000"]'::jsonb AND (elem->>'correctIndex')::int = 3) OR
  (elem->'choices' = '["$42 favorable", "$42 unfavorable", "$20 favorable", "$20 unfavorable"]'::jsonb AND (elem->>'correctIndex')::int = 1) OR
  (elem->'choices' = '["120 units", "80 units", "110 units", "140 units"]'::jsonb AND (elem->>'correctIndex')::int = 1) OR
  (elem->'choices' = '["P100,000", "P150,000", "P50,000", "P200,000"]'::jsonb AND (elem->>'correctIndex')::int = 1) OR
  (elem->'choices' = '["$680.58", "$735.03", "$867.30", "$500.00"]'::jsonb AND (elem->>'correctIndex')::int = 2) OR
  (elem->'choices' = '["$485,000", "$400,000", "$515,000", "$415,000"]'::jsonb AND (elem->>'correctIndex')::int = 3) OR
  (elem->'choices' = '["10 A", "17.32 A", "30 A", "51.96 A"]'::jsonb AND (elem->>'correctIndex')::int = 2) OR
  (elem->'choices' = '["$31,000", "$27,000", "$35,000", "$33,000"]'::jsonb AND (elem->>'correctIndex')::int = 1) OR
  (elem->'choices' = '["+200 kJ", "-200 kJ", "+100 kJ", "-100 kJ"]'::jsonb AND (elem->>'correctIndex')::int = 2) OR
  (elem->'choices' = '["$45,000", "$65,000", "$55,000", "$35,000"]'::jsonb AND (elem->>'correctIndex')::int = 2)
);
-- Expect 0 rows.


-- ============================================================================
-- SECTION C — challenge_quiz_question_bank.question  (10 rows, one question
-- per row — NOT an array, unlike A/B). ALL 10 belong to the owner's own
-- admin/test account (dee4225c-e460-4f89-a6e5-cd43f6dd1972), all
-- last_known_outcome = 'UNANSWERED' — zero real learner exposure, verified
-- fresh during this file's preparation.
-- Expected rows affected: 10.
-- ============================================================================

-- C.0 Pre-check.
SELECT id::text AS bank_id, user_id::text AS owner_user_id, last_known_outcome,
       question->>'question' AS question, question->'choices' AS choices,
       question->>'correctIndex' AS current_correct_index
FROM challenge_quiz_question_bank
WHERE id::text IN (
  '1bb93537-b34f-4278-83d2-5ef528dccbf3','50b57537-732b-4ecc-bb48-c25e042d2a1a',
  '58f2c1f9-d943-4a1a-b5af-d80347821fda','5915864d-7cc3-4251-8dd8-314d5102f0c1',
  '72e0ea1a-da18-4c47-b45e-5784261e5bf6','818814ac-70d9-448c-9b10-f11a9315b9e5',
  '87e6db69-c7f3-41b7-89d0-5153e181bf44','aa684263-c426-405c-bbe4-b48d09855168',
  'e1c1110e-2e1b-42ce-96cc-3b2cf46faf4a','e73e4b66-ec30-4b7a-8613-1de343599d9d'
);
-- Expect 10 rows, all owner_user_id = dee4225c-e460-4f89-a6e5-cd43f6dd1972,
-- all last_known_outcome = 'UNANSWERED'. If ANY row shows a different
-- user_id or an outcome other than UNANSWERED, STOP — that would mean a
-- real learner has since interacted with a bank entry this repair assumed
-- was untouched, and the repair-vs-communication tradeoff in §L of the
-- incident doc needs revisiting before proceeding.

UPDATE challenge_quiz_question_bank
SET question = jsonb_set(question, '{correctIndex}', '0'::jsonb)
WHERE id = '1bb93537-b34f-4278-83d2-5ef528dccbf3'
  AND question->'choices' = '["130.8 m", "261.8 m", "75.4 m", "150.0 m"]'::jsonb
  AND (question->>'correctIndex')::int = 1;
-- Circular curve length R=300m, θ=25°: L=Rθ=300×(25π/180)=130.8m.

UPDATE challenge_quiz_question_bank
SET question = jsonb_set(question, '{correctIndex}', '0'::jsonb)
WHERE id = '50b57537-732b-4ecc-bb48-c25e042d2a1a'
  AND question->'choices' = '["0.3", "-0.3", "0.12", "-0.12"]'::jsonb
  AND (question->>'correctIndex')::int = 1;
-- Poisson's ratio (axial .002, transverse -.0006): ν=0.0006/0.002=0.3.
-- The question's own explanation text says "the positive value of 0.3"
-- while its OWN key pointed at -0.3 — self-contradictory in its own prose.

UPDATE challenge_quiz_question_bank
SET question = jsonb_set(question, '{correctIndex}', '0'::jsonb)
WHERE id = '58f2c1f9-d943-4a1a-b5af-d80347821fda'
  AND question->'choices' = '["$1,000 unfavorable", "$1,000 favorable", "$5,000 unfavorable", "$5,000 favorable"]'::jsonb
  AND (question->>'correctIndex')::int = 1;
-- Materials price variance (std $5, actual $6 × 1,000): (6-5)×1,000=1,000;
-- actual>standard ⇒ unfavorable.

UPDATE challenge_quiz_question_bank
SET question = jsonb_set(question, '{correctIndex}', '2'::jsonb)
WHERE id = '5915864d-7cc3-4251-8dd8-314d5102f0c1'
  AND question->'choices' = '["150 kPa", "300 kPa", "450 kPa", "600 kPa"]'::jsonb
  AND (question->>'correctIndex')::int = 1;
-- Required bearing capacity: 1800kN / 4m² = 450 kPa.

UPDATE challenge_quiz_question_bank
SET question = jsonb_set(question, '{correctIndex}', '0'::jsonb)
WHERE id = '72e0ea1a-da18-4c47-b45e-5784261e5bf6'
  AND question->'choices' = '["0.0747", "0.1877", "0.2639", "0.4012"]'::jsonb
  AND (question->>'correctIndex')::int = 2;
-- Binomial P(X=2), n=10, p=0.05: C(10,2)(0.05)²(0.95)^8 = 45×0.0025×0.6634 ≈ 0.0747.

UPDATE challenge_quiz_question_bank
SET question = jsonb_set(question, '{correctIndex}', '0'::jsonb)
WHERE id = '818814ac-70d9-448c-9b10-f11a9315b9e5'
  AND question->'choices' = '["$570,000", "$600,000", "$530,000", "$470,000"]'::jsonb
  AND (question->>'correctIndex')::int = 1;
-- Joint-venture carrying amount: 500,000+100,000-30,000=570,000.

UPDATE challenge_quiz_question_bank
SET question = jsonb_set(question, '{correctIndex}', '0'::jsonb)
WHERE id = '87e6db69-c7f3-41b7-89d0-5153e181bf44'
  AND question->'choices' = '["$81,400", "$79,000", "$86,400", "$75,000"]'::jsonb
  AND (question->>'correctIndex')::int = 2;
-- Liability carrying amount (initial $80,000, 8% interest, $5,000 repayment):
-- 80,000+6,400-5,000=81,400.

UPDATE challenge_quiz_question_bank
SET question = jsonb_set(question, '{correctIndex}', '0'::jsonb)
WHERE id = 'aa684263-c426-405c-bbe4-b48d09855168'
  AND question->'choices' = '["11", "7", "9", "14"]'::jsonb
  AND (question->>'correctIndex')::int = 1;
-- GCS total (eye3+verbal4+motor4): 3+4+4=11.

UPDATE challenge_quiz_question_bank
SET question = jsonb_set(question, '{correctIndex}', '1'::jsonb)
WHERE id = 'e1c1110e-2e1b-42ce-96cc-3b2cf46faf4a'
  AND question->'choices' = '["$1,250 favorable", "$1,250 unfavorable", "$21,250 favorable", "$21,250 unfavorable"]'::jsonb
  AND (question->>'correctIndex')::int = 3;
-- Labor efficiency variance (900-850 hrs)×$25 = $1,250; actual>standard hours
-- ⇒ unfavorable = idx1. Keyed idx3 has an inserted extra digit ("21,250").

UPDATE challenge_quiz_question_bank
SET question = jsonb_set(question, '{correctIndex}', '0'::jsonb)
WHERE id = 'e73e4b66-ec30-4b7a-8613-1de343599d9d'
  AND question->'choices' = '["533 veh/h", "480 veh/h", "600 veh/h", "720 veh/h"]'::jsonb
  AND (question->>'correctIndex')::int = 1;
-- Signal capacity: 1600×30/90=533.3 veh/h.

-- C.99 Post-check: expect 0 rows.
SELECT id::text AS bank_id, question->>'correctIndex' AS still_wrong
FROM challenge_quiz_question_bank
WHERE id::text IN (
  '1bb93537-b34f-4278-83d2-5ef528dccbf3','50b57537-732b-4ecc-bb48-c25e042d2a1a',
  '58f2c1f9-d943-4a1a-b5af-d80347821fda','5915864d-7cc3-4251-8dd8-314d5102f0c1',
  '72e0ea1a-da18-4c47-b45e-5784261e5bf6','818814ac-70d9-448c-9b10-f11a9315b9e5',
  '87e6db69-c7f3-41b7-89d0-5153e181bf44','aa684263-c426-405c-bbe4-b48d09855168',
  'e1c1110e-2e1b-42ce-96cc-3b2cf46faf4a','e73e4b66-ec30-4b7a-8613-1de343599d9d'
)
AND (
  (question->'choices' = '["130.8 m", "261.8 m", "75.4 m", "150.0 m"]'::jsonb AND (question->>'correctIndex')::int = 1) OR
  (question->'choices' = '["0.3", "-0.3", "0.12", "-0.12"]'::jsonb AND (question->>'correctIndex')::int = 1) OR
  (question->'choices' = '["$1,000 unfavorable", "$1,000 favorable", "$5,000 unfavorable", "$5,000 favorable"]'::jsonb AND (question->>'correctIndex')::int = 1) OR
  (question->'choices' = '["150 kPa", "300 kPa", "450 kPa", "600 kPa"]'::jsonb AND (question->>'correctIndex')::int = 1) OR
  (question->'choices' = '["0.0747", "0.1877", "0.2639", "0.4012"]'::jsonb AND (question->>'correctIndex')::int = 2) OR
  (question->'choices' = '["$570,000", "$600,000", "$530,000", "$470,000"]'::jsonb AND (question->>'correctIndex')::int = 1) OR
  (question->'choices' = '["$81,400", "$79,000", "$86,400", "$75,000"]'::jsonb AND (question->>'correctIndex')::int = 2) OR
  (question->'choices' = '["11", "7", "9", "14"]'::jsonb AND (question->>'correctIndex')::int = 1) OR
  (question->'choices' = '["$1,250 favorable", "$1,250 unfavorable", "$21,250 favorable", "$21,250 unfavorable"]'::jsonb AND (question->>'correctIndex')::int = 3) OR
  (question->'choices' = '["533 veh/h", "480 veh/h", "600 veh/h", "720 veh/h"]'::jsonb AND (question->>'correctIndex')::int = 1)
);
-- Expect 0 rows.


-- ============================================================================
-- SECTION D — RETROACTIVE HISTORY CORRECTION (2 rows)
-- ⚠️ RUN THIS SECTION ONLY IF YOU WANT THE ONE-TIME GOODWILL CORRECTION.
-- Owner decision (§Q.1 item 5, 2026-09-21): approved, EXPLICITLY on the
-- condition "we should not silently rewrite history" — that is why this is
-- its own separately-labeled section with its own before/after values and
-- its own verification, not folded into Section A. This is a documented,
-- one-time exception for this incident and does NOT establish a general
-- retroactive-correction policy for future incidents.
--
-- Affected: the one reporting learner's session, which recorded 80% because
-- it read the same wrong answer key Section A just corrected — their actual
-- selection (30%) was mathematically correct the whole time.
-- ============================================================================

-- D.0 Pre-check: expect exactly these current (wrong) values.
SELECT id::text, correct_answers, verified_correct_answers, score_percentage, total_questions
FROM quick_review_sessions WHERE id = '1e78a11d-d76a-4e06-adb1-75f52386248a';
-- Expect: correct_answers=4, verified_correct_answers=4, score_percentage=80.00, total_questions=5.

SELECT id::text, concept, last_correct_at, last_incorrect_at, incorrect_streak
FROM concept_health WHERE id = '9733ae06-8e0c-49b9-8040-68962912a206';
-- Expect: concept='Percentage change', last_incorrect_at='2026-09-16T08:54:27.687751Z'.

-- D.1 — Correct the session score: the learner's stored selection for
-- question 5 (index 1 = "30%") was mathematically correct; only the stored
-- answer KEY was wrong (now fixed by Section A). 4/5 → 5/5, 80.00% → 100.00%.
UPDATE quick_review_sessions
SET correct_answers = 5, verified_correct_answers = 5, score_percentage = 100.00
WHERE id = '1e78a11d-d76a-4e06-adb1-75f52386248a'
  AND correct_answers = 4 AND verified_correct_answers = 4 AND score_percentage = 80.00;

-- D.2 — Clear the false "incorrect" mark this session wrote to ConceptHealth.
-- Leaves last_correct_at untouched (it already reflects the learner's later,
-- correctly-scored session) and does not touch incorrect_streak (already 0).
UPDATE concept_health
SET last_incorrect_at = NULL
WHERE id = '9733ae06-8e0c-49b9-8040-68962912a206'
  AND last_incorrect_at = '2026-09-16T08:54:27.687751Z';

-- D.99 Post-check.
SELECT id::text, correct_answers, verified_correct_answers, score_percentage
FROM quick_review_sessions WHERE id = '1e78a11d-d76a-4e06-adb1-75f52386248a';
-- Expect: correct_answers=5, verified_correct_answers=5, score_percentage=100.00.

SELECT id::text, last_correct_at, last_incorrect_at
FROM concept_health WHERE id = '9733ae06-8e0c-49b9-8040-68962912a206';
-- Expect: last_incorrect_at IS NULL.


-- ============================================================================
-- FINAL SUMMARY — run after A, B, and C (D is independent, check separately)
-- ============================================================================
SELECT
  (SELECT count(*) FROM study_packs sp, jsonb_array_elements(sp.quiz) e
     WHERE sp.id::text IN ('1ccb19f9-8542-47bf-992a-cf0a0fb3ce0a','c8dd3aa2-6067-4d84-b9bd-85dc1a583770',
       '2e89b22c-f382-48f2-a57f-c50bd3927593','b2f7e4f3-f564-4bd5-b565-84831a7dc7fb',
       '400c9315-d1e8-4b7d-a930-e045433ce3be','70409a24-2d94-4fde-b943-dc860131f51f',
       '5e2e1633-5be0-4fb2-b1de-0dab91f3719a','9756f274-43f9-4994-9dc3-5abe66679f72',
       '6a2d6712-f25a-4f9d-a3bd-b8f41f598bc8','9b96153b-f65e-4cd1-b389-bc249725d950',
       '771a06a8-4bdc-4b03-99e6-1c7dbe087c28','ccabeefc-90a0-481f-8dfc-2677ab33410e')) AS section_a_elements_present_expect_12,
  (SELECT count(*) FROM exam_question_pool eqp, jsonb_array_elements(eqp.questions) e
     WHERE eqp.id::text IN ('2437d442-5b95-4383-ad92-790fc58c4557','2b01c6df-116b-448c-8792-82c3a7cfe97c',
       '2f82877b-e70f-424b-9723-6ba71d60c1f8','385d69db-76a3-4620-8510-5bee4b5f6ca7',
       '4ef82a80-4907-492c-bf9c-0f5e50b65f03','571244ab-afa6-409c-b962-7e4c9086a3ed',
       '69a963fb-44bc-48d7-9c24-f8db9978d8f4','76cad170-9249-45f1-b256-c4cab6eb1a6a',
       'aa48d9eb-865d-4f48-acc1-07bbaf674159','cfda9a7b-e2f9-4398-a47c-7a8649d3038a',
       'd2d91c72-5735-4158-9941-dfaa61ad3596','d7e8622e-ecd0-4185-88b6-766fb91663ab',
       'df62fc13-ff98-485f-b8dd-e594b450319c','e1d9b7fc-da50-42d9-b6c8-ee86f42e54aa')
     AND e->'choices' IN (
       '["$135,000", "$145,000", "$125,000", "$140,000"]'::jsonb,'["$5.00", "$4.50", "$4.00", "$5.50"]'::jsonb,
       '["$2,500 Favorable", "$2,500 Unfavorable", "$2,500 Neutral", "$0 No Variance"]'::jsonb,'["0.084", "0.120", "0.140", "0.196"]'::jsonb,
       '["10000", "100000", "1000000", "10000000"]'::jsonb,'["$42 favorable", "$42 unfavorable", "$20 favorable", "$20 unfavorable"]'::jsonb,
       '["120 units", "80 units", "110 units", "140 units"]'::jsonb,'["P100,000", "P150,000", "P50,000", "P200,000"]'::jsonb,
       '["$680.58", "$735.03", "$867.30", "$500.00"]'::jsonb,'["$485,000", "$400,000", "$515,000", "$415,000"]'::jsonb,
       '["10 A", "17.32 A", "30 A", "51.96 A"]'::jsonb,'["$31,000", "$27,000", "$35,000", "$33,000"]'::jsonb,
       '["+200 kJ", "-200 kJ", "+100 kJ", "-100 kJ"]'::jsonb,'["$45,000", "$65,000", "$55,000", "$35,000"]'::jsonb
     )) AS section_b_elements_present_expect_15,
  (SELECT count(*) FROM challenge_quiz_question_bank
     WHERE id::text IN ('1bb93537-b34f-4278-83d2-5ef528dccbf3','50b57537-732b-4ecc-bb48-c25e042d2a1a',
       '58f2c1f9-d943-4a1a-b5af-d80347821fda','5915864d-7cc3-4251-8dd8-314d5102f0c1',
       '72e0ea1a-da18-4c47-b45e-5784261e5bf6','818814ac-70d9-448c-9b10-f11a9315b9e5',
       '87e6db69-c7f3-41b7-89d0-5153e181bf44','aa684263-c426-405c-bbe4-b48d09855168',
       'e1c1110e-2e1b-42ce-96cc-3b2cf46faf4a','e73e4b66-ec30-4b7a-8613-1de343599d9d')) AS section_c_rows_present_expect_10;
-- These three counts should read 12 / 15 / 10 both BEFORE and AFTER running
-- the repair (the rows don't disappear, only their correctIndex values
-- change) — this query alone does not prove the repair worked; use the
-- per-section post-checks above for that. This is only a final row-presence
-- sanity check that nothing was accidentally deleted.
