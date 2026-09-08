-- v0.133.0 — Education program family.
--
-- ⚠️ THIS IS A DATA MIGRATION. It adds no column, table, constraint or index.
-- The Program Family primitive is already fully generic: `program_families` is a name plus a nullable
-- FK, and `applicable-programs-combobox.tsx` derives its families DYNAMICALLY from the catalog with no
-- Engineering literal anywhere in it. Seeding this family is the whole feature — the UI starts
-- rendering "Add all N Education programs" with no code change.
--
-- ⚠️ ID RANGE VERIFIED AGAINST PRODUCTION 2026-09-08, NOT ASSUMED. V106 seeded 21 programs as
-- '20000000-...-000000000001'..'021' and one family as '10000000-...-000000000001'. Production holds
-- 44 programs, of which EXACTLY 21 are in the seed range (highest '...021') -- the other 23 were
-- created through POST /course-programs and carry random UUIDs. So '...022'+ and family '...002' are
-- free, and this migration cannot collide on the primary key.

-- ── 1. The family ────────────────────────────────────────────────────────────────────────────────
INSERT INTO program_families (id, name)
VALUES ('10000000-0000-0000-0000-000000000002', 'Education');

-- ── 2. Assign the EXISTING Education program to it ───────────────────────────────────────────────
-- ⚠️ ASSIGN, NEVER DELETE OR RE-CREATE. This row is referenced by note_course_program and by user
-- profiles; replacing it would strand both. Its exam_goal_slug ('let') is deliberately untouched.
UPDATE course_programs
   SET program_family_id = '10000000-0000-0000-0000-000000000002'
 WHERE id = '20000000-0000-0000-0000-000000000001';

-- ── 3. Rename, do NOT duplicate ──────────────────────────────────────────────────────────────────
-- Owner decision, 2026-09-08: 'Special Needs Education – Generalist' becomes 'Special Needs
-- Education' and KEEPS ITS id, so every note_course_program row survives (that join is by
-- course_program_id, never by name).
--
-- ⚠️ THE SEPARATOR IN THE OLD NAME IS AN EN DASH (U+2013), NOT A HYPHEN. This statement matches on id
-- precisely so that the rename cannot silently miss.
--
-- ⚠️ ONE STALE FREE-TEXT STRING REMAINS AND IS NOT THIS MIGRATION'S TO FIX. The production read found
-- exactly one users.course_program row holding the old literal. That column is consumed by
-- StudyPackGenerationContextResolver as FREE TEXT and is never resolved against the catalog by name,
-- so nothing breaks -- but the profile stops corresponding to a catalog entry. It is handed to the
-- owner as docs/claude-plans/v0.133.0-owner-profile-string-update.sql, because a write to production
-- is the owner's to run, never Claude's.
--
-- ⚠️ THIS ROW ALSO GAINS exam_goal_slug = 'let', WHICH IS A USER-VISIBLE CHANGE BEYOND A RENAME AND IS
-- FLAGGED RATHER THAN BURIED. Special Needs Education graduates genuinely sit the LET, so the owner's
-- rule (2026-09-08) covers this row as much as the six new ones; leaving it NULL would make seven of
-- eight family members LET-discoverable and one silently not, which reads as a bug. The field is
-- one-to-MANY by design -- CourseProgramCatalogRepository.findNamesByExamGoalSlug returns a List and
-- PublicExamGoalCourseProgramController maps each slug to a list -- so this is the field being used as
-- intended, not overloaded. V106 seeded only four slugs because only four such programs existed then.
-- The visible effect: this program starts appearing under the LET exam goal on the public endpoint.
UPDATE course_programs
   SET name = 'Special Needs Education',
       program_family_id = '10000000-0000-0000-0000-000000000002',
       exam_goal_slug = 'let'
 WHERE id = '20000000-0000-0000-0000-000000000021';

-- ── 4. The new Education programs ────────────────────────────────────────────────────────────────
-- exam_goal_slug follows the owner's rule (2026-09-08): 'let' only where learners genuinely sit the
-- Licensure Examination for Teachers, never as a proxy for family membership. Every program below
-- leads to the LET -- BEEd, BSEd, BECEd and BTVTEd graduates all sit it, and the Teacher
-- Certification route exists precisely to make non-education graduates LET-eligible -- so the rule
-- happens to select all of them here. That is the rule being satisfied, not bypassed.
--
-- ⚠️ NAMING: no credential abbreviation as a canonical name (no BEEd/BSEd/BPEd/CPE). 'Teacher
-- Certification' is used rather than 'Professional Education', which already exists BOTH as a
-- DomainContext value and as a Subject and would collide on two axes.
INSERT INTO course_programs (id, name, program_family_id, exam_goal_slug)
VALUES
    ('20000000-0000-0000-0000-000000000022', 'Elementary Education',                  '10000000-0000-0000-0000-000000000002', 'let'),
    ('20000000-0000-0000-0000-000000000023', 'Secondary Education',                   '10000000-0000-0000-0000-000000000002', 'let'),
    ('20000000-0000-0000-0000-000000000024', 'Early Childhood Education',             '10000000-0000-0000-0000-000000000002', 'let'),
    ('20000000-0000-0000-0000-000000000025', 'Technical-Vocational Teacher Education','10000000-0000-0000-0000-000000000002', 'let'),
    ('20000000-0000-0000-0000-000000000026', 'Physical Education',                    '10000000-0000-0000-0000-000000000002', 'let'),
    ('20000000-0000-0000-0000-000000000027', 'Teacher Certification',                 '10000000-0000-0000-0000-000000000002', 'let');
