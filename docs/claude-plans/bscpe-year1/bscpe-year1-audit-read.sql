-- BSCpE Year 1 canonical audit: the read-only queries behind bscpe-year1-canonical-audit.md.
-- Read 2026-11-12 against notelib-db-prod through the Render MCP (read-only transaction).
-- EVERY STATEMENT IS A SELECT. No writes, no DDL, no function that writes. Safe to re-run;
-- facts decay, so re-run before any figure reaches a plan.
-- "Canonical" here means: owner role ADMIN, PUBLIC, not a copy (copied_from_note_id IS NULL).
-- The tool prints uuids as text only when cast (id::text). Keep result sets small: the database is small.

-- Q1. Catalog programs of interest and how many canonical Notes each carries.
SELECT cp.name, cp.is_active,
       count(DISTINCT n.id) FILTER (WHERE n.visibility='PUBLIC' AND n.copied_from_note_id IS NULL) AS public_originals
FROM course_programs cp
LEFT JOIN note_course_program x ON x.course_program_id = cp.id
LEFT JOIN notes n ON n.id = x.note_id
WHERE cp.name ~* 'computer|engineering|electr|electron|information|physics|chemi|mathemat'
GROUP BY cp.name, cp.is_active ORDER BY cp.name;

-- Q2. What is tagged Computer Engineering today, by Subject / Domain Context / Authored Depth.
SELECT n.subject, coalesce(n.domain_context,'(unset)') AS dc, coalesce(n.learner_level,'(unset)') AS depth,
       u.role::text AS owner_role, count(*) AS notes,
       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM study_packs s WHERE s.note_id = n.id)) AS with_pack
FROM notes n
JOIN note_course_program x ON x.note_id = n.id
JOIN course_programs cp ON cp.id = x.course_program_id AND cp.name = 'Computer Engineering'
JOIN users u ON u.id = n.owner_user_id
WHERE n.visibility='PUBLIC' AND n.copied_from_note_id IS NULL
GROUP BY 1,2,3,4 ORDER BY notes DESC, 1;

-- Q3. Engineering-adjacent canonical subjects: size, Domain Context, depth, Computer Engineering tag.
SELECT n.subject, coalesce(n.domain_context,'(unset)') AS dc, count(*) AS notes,
       count(*) FILTER (WHERE n.learner_level IS NULL) AS depth_unset,
       count(*) FILTER (WHERE n.learner_level = 'BOARD_EXAM_REVIEW') AS board_depth,
       count(*) FILTER (WHERE EXISTS (SELECT 1 FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id
                                      WHERE x.note_id = n.id AND cp.name = 'Computer Engineering')) AS cpe_tagged
FROM notes n JOIN users u ON u.id = n.owner_user_id AND u.role::text = 'ADMIN'
WHERE n.visibility='PUBLIC' AND n.copied_from_note_id IS NULL
  AND n.subject ~* 'calcul|chem|physic|mechanic|circuit|electri|electron|draw|draft|cad|programming|algorithm|logic|engineering|digital|energy|thermo|material|safety|computing|computer'
GROUP BY 1,2 ORDER BY 1, 3 DESC;

-- Q4. Calculus Notes: ids, depth, body length, programs (ids are needed for the workbook).
SELECT n.id::text AS id, n.title, n.subject, coalesce(n.learner_level,'(unset)') AS depth, length(n.content) AS len,
       (SELECT count(*) FROM study_packs s WHERE s.note_id = n.id) AS packs,
       (SELECT string_agg(left(cp.name,14),'; ' ORDER BY cp.name)
          FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id WHERE x.note_id = n.id) AS programs
FROM notes n JOIN users u ON u.id = n.owner_user_id AND u.role::text = 'ADMIN'
WHERE n.visibility='PUBLIC' AND n.copied_from_note_id IS NULL
  AND n.subject IN ('Differential Calculus','Integral Calculus','Calculus')
ORDER BY n.subject, coalesce(n.learner_level,'0'), n.title;

-- Q5. Programs on Electrical, Electronics, Chemistry, Mechanical, Chemical Engineering and Mathematics:
--     shows that Electronics and Electrical carry only the common mathematics plus a handful of electrical Notes.
SELECT cp.name AS program, n.subject, coalesce(n.domain_context,'(unset)') AS dc, count(DISTINCT n.id) AS notes
FROM notes n
JOIN note_course_program x ON x.note_id = n.id
JOIN course_programs cp ON cp.id = x.course_program_id
     AND cp.name IN ('Electrical Engineering','Electronics Engineering','Chemistry','Mechanical Engineering','Chemical Engineering','Mathematics')
JOIN users u ON u.id = n.owner_user_id AND u.role::text = 'ADMIN'
WHERE n.visibility='PUBLIC' AND n.copied_from_note_id IS NULL
GROUP BY 1,2,3 ORDER BY 1, 4 DESC, 2;

-- Q6. Title search for circuits, digital logic, drafting, chemistry and physics terms (no hits for drafting or chemistry).
SELECT n.id::text AS id, n.title, n.subject, coalesce(n.domain_context,'(unset)') AS dc, coalesce(n.learner_level,'(unset)') AS depth,
       length(n.content) AS len
FROM notes n JOIN users u ON u.id = n.owner_user_id AND u.role::text = 'ADMIN'
WHERE n.visibility='PUBLIC' AND n.copied_from_note_id IS NULL
  AND n.title ~* '\y(circuits?|ohm|kirchhoff|resistors?|capacitors?|inductors?|thevenin|norton|semiconductors?|diodes?|transistors?|op-?amps?|logic gates?|flip-?flops?|digital logic|combinational|sequential logic|orthographic|isometric|engineering drawing|drafting|cad|lettering|dimensioning|stoichiometry|periodic table|chemical bonding|atomic structure|kinematics|projectile|newton|coulomb|electric fields?|magnetic fields?|electromagnet\w*|simple harmonic|waves?)\y'
ORDER BY n.subject, n.title LIMIT 80;

-- Q7. Mechanics, physics, circuit and thermodynamics subjects (titles, depth, length).
SELECT n.id::text AS id, n.title, coalesce(n.learner_level,'(unset)') AS depth, length(n.content) AS len
FROM notes n JOIN users u ON u.id = n.owner_user_id AND u.role::text = 'ADMIN'
WHERE n.visibility='PUBLIC' AND n.copied_from_note_id IS NULL
  AND n.subject IN ('Engineering Mechanics','Physics','Circuit Theory','Electrical Engineering','Electrical Machines','Power Systems','Thermodynamics')
ORDER BY n.subject, n.title;

-- Q8. Data structures, engineering professional practice, and a chemistry title sweep.
SELECT n.id::text AS id, n.title, n.subject, coalesce(n.domain_context,'(unset)') AS dc, coalesce(n.learner_level,'(unset)') AS depth,
       length(n.content) AS len,
       EXISTS (SELECT 1 FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id
               WHERE x.note_id = n.id AND cp.name = 'Computer Engineering') AS cpe
FROM notes n JOIN users u ON u.id = n.owner_user_id AND u.role::text = 'ADMIN'
WHERE n.visibility='PUBLIC' AND n.copied_from_note_id IS NULL
  AND (n.subject IN ('Data Structures and Algorithms','Engineering Laws, Ethics, and Professional Practice')
       OR (n.title ~* '\y(chemistry|chemical (reactions?|bonds?|bonding|equilibrium|kinetics)|atoms?|atomic|molecules?|molecular|periodic|stoichiometry|gas laws?|thermochemistry|electrochemistry|acids?|oxidation|redox)\y'
           AND n.subject !~* 'nursing|clinical|pharm|water|waste|air|soil|environment'))
ORDER BY n.subject, n.title LIMIT 60;

-- Q9. Notes with an UNSET Domain Context and how many programs each carries (ADR-001: a second program needs a Domain Context first).
SELECT n.subject, (SELECT count(*) FROM note_course_program x WHERE x.note_id = n.id) AS n_programs, count(*) AS notes
FROM notes n JOIN users u ON u.id = n.owner_user_id AND u.role::text = 'ADMIN'
WHERE n.visibility='PUBLIC' AND n.copied_from_note_id IS NULL AND n.domain_context IS NULL
  AND n.subject IN ('Database Systems','Circuit Theory','Electrical Engineering','Electrical Safety','Power Systems','Electrical Machines',
                    'Computer Networking','Advanced Programming','Object – Oriented Programming')
GROUP BY 1,2 ORDER BY 1,2;

-- Q10. The 198 shared-journey Notes (Introduction to Computing, Computer Programming 1 and 2, Mathematics in the Modern World,
--      The Contemporary World, Discrete Mathematics, Understanding the Self, Purposive Communication) verified by id.
--      The id array is generated from docs/claude-plans/bsit-year1/bsit-year1-reuse-id-manifest.tsv (plans 1,2,3,4,6,7,8,10), never typed:
--        SELECT n.subject, coalesce(n.domain_context,'(unset)'), coalesce(n.learner_level,'(unset)'), n.visibility,
--               EXISTS (SELECT 1 FROM note_course_program x JOIN course_programs cp ON cp.id = x.course_program_id
--                       WHERE x.note_id = n.id AND cp.name = 'Computer Engineering') AS cpe_tagged,
--               (SELECT count(*) FROM study_packs s WHERE s.note_id = n.id) AS packs, count(*)
--        FROM notes n WHERE n.id = ANY(ARRAY[ ...ids from the manifest... ]::uuid[]) GROUP BY 1,2,3,4,5,6 ORDER BY 1,5;
--      Result 2026-11-12: 198 of 198 exist, all PUBLIC, all COLLEGE, all with exactly one Study Pack; 91 tagged Computer Engineering, 107 not.

-- Q11. Which collections hold the calculus and mechanics candidates (root, plan, collection depth, Note depth).
--      Table names: note_collections (plans and roots), note_collection_items (membership).
SELECT coalesce(r.title, c.title) AS root_title, c.title AS plan_title, c.visibility::text AS coll_visibility,
       coalesce(c.learner_level,'(unset)') AS coll_depth, n.subject, coalesce(n.learner_level,'(unset)') AS note_depth,
       count(DISTINCT n.id) AS notes_held
FROM note_collection_items i
JOIN note_collections c ON c.id = i.collection_id
LEFT JOIN note_collections r ON r.id = c.parent_collection_id
JOIN notes n ON n.id = i.note_id
JOIN users u ON u.id = n.owner_user_id AND u.role::text = 'ADMIN'
WHERE n.visibility='PUBLIC' AND n.copied_from_note_id IS NULL
  AND n.subject IN ('Differential Calculus','Integral Calculus','Calculus','Engineering Mechanics','Circuit Theory')
  AND c.source_plan_id IS NULL
GROUP BY 1,2,3,4,5,6 ORDER BY 1,2,5,6;
--      Result 2026-11-12: all in the public CELE Comprehensive Review: Engineering Mathematics plan (calculus: 14 unset-depth, 5 board-depth)
--      and Engineering Mechanics plan (51 of 52); the collection depth is unset.

-- Q12. Computer Programming 1 body marker check (server-side, no content transferred). The 42 ids come from the manifest, plan 2.
--      CORRECTED 2026-11-12: the first pattern matched 'cin' and 'cout' as bare substrings and required a word boundary after 'C++',
--      so it was wrong in both directions. Correct pattern:
--        SELECT left(n.id::text,8) AS id8, n.title, (n.content ~ 'C\+\+') AS has_cpp,
--               (n.content ~* '(\ycout\y|\ycin\y|#include|std::|int main\()') AS has_cpp_code,
--               (n.content ~* '\y(python|java|javascript|c#)\y') AS has_other_lang, length(n.content) AS len
--        FROM notes n WHERE n.id = ANY(ARRAY[ ...42 ids from the manifest... ]::uuid[]) ORDER BY 3, 4, 2;
--      Result 2026-11-12: 22 Notes mention C++ in the body, exactly the 22 whose titles say C++ (12 also contain code); the other 20 have no C++ marker.
