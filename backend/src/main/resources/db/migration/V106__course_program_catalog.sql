CREATE TABLE program_families (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_program_families_name UNIQUE (name)
);

CREATE TABLE course_programs (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    program_family_id UUID,
    exam_goal_slug VARCHAR(32),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_course_programs_name UNIQUE (name),
    CONSTRAINT ck_course_programs_exam_goal_slug
        CHECK (exam_goal_slug IS NULL OR exam_goal_slug IN ('ale', 'pnle', 'let', 'cpale')),
    CONSTRAINT fk_course_programs_program_family
        FOREIGN KEY (program_family_id) REFERENCES program_families (id)
);

INSERT INTO program_families (id, name)
VALUES ('10000000-0000-0000-0000-000000000001', 'Engineering');

INSERT INTO course_programs (id, name, program_family_id, exam_goal_slug)
VALUES
    ('20000000-0000-0000-0000-000000000001', 'Education', NULL, 'let'),
    ('20000000-0000-0000-0000-000000000002', 'Architecture', NULL, 'ale'),
    ('20000000-0000-0000-0000-000000000003', 'Nursing', NULL, 'pnle'),
    ('20000000-0000-0000-0000-000000000004', 'Accountancy', NULL, 'cpale'),
    ('20000000-0000-0000-0000-000000000005', 'Civil Engineering', '10000000-0000-0000-0000-000000000001', NULL),
    ('20000000-0000-0000-0000-000000000006', 'Information Technology', NULL, NULL),
    ('20000000-0000-0000-0000-000000000007', 'Pharmacy', NULL, NULL),
    ('20000000-0000-0000-0000-000000000008', 'Electrical Engineering', '10000000-0000-0000-0000-000000000001', NULL),
    ('20000000-0000-0000-0000-000000000009', 'Mechanical Engineering', '10000000-0000-0000-0000-000000000001', NULL),
    ('20000000-0000-0000-0000-000000000010', 'Physical Therapy', NULL, NULL),
    ('20000000-0000-0000-0000-000000000011', 'Senior High – ABM', NULL, NULL),
    ('20000000-0000-0000-0000-000000000012', 'Senior High – STEM', NULL, NULL),
    ('20000000-0000-0000-0000-000000000013', 'Senior High – HUMSS', NULL, NULL),
    ('20000000-0000-0000-0000-000000000014', 'Medicine', NULL, NULL),
    ('20000000-0000-0000-0000-000000000015', 'Criminology', NULL, NULL),
    ('20000000-0000-0000-0000-000000000016', 'Law', NULL, NULL),
    ('20000000-0000-0000-0000-000000000017', 'Aviation', NULL, NULL),
    ('20000000-0000-0000-0000-000000000018', 'Business Administration', NULL, NULL),
    ('20000000-0000-0000-0000-000000000019', 'Psychology', NULL, NULL),
    ('20000000-0000-0000-0000-000000000020', 'Radiologic Technology', NULL, NULL),
    ('20000000-0000-0000-0000-000000000021', 'Special Needs Education – Generalist', NULL, NULL);

ALTER TABLE notes
    ADD COLUMN course_program_id UUID;

ALTER TABLE notes
    ADD CONSTRAINT fk_notes_course_program
    FOREIGN KEY (course_program_id) REFERENCES course_programs (id);

ALTER TABLE users
    ADD COLUMN course_program_id UUID;

ALTER TABLE users
    ADD CONSTRAINT fk_users_course_program
    FOREIGN KEY (course_program_id) REFERENCES course_programs (id);

UPDATE notes
SET course_program_id = course_programs.id
FROM course_programs
WHERE course_programs.name = notes.course_program;

UPDATE users
SET course_program_id = course_programs.id
FROM course_programs
WHERE course_programs.name = users.course_program;

-- Bsed is the only sanctioned non-exact alias. Keep the stored string unchanged.
-- Applied to users only, and that is correct by construction rather than an oversight: the production
-- vocabulary read (15-vocabulary-and-impact-results.md) measured 'Bsed' at 0 notes and 1 user.
UPDATE users
SET course_program_id = (
    SELECT id
    FROM course_programs
    WHERE name = 'Education'
)
WHERE course_program = 'Bsed'
  AND course_program_id IS NULL;

DO $$
DECLARE
    seeded_programs INTEGER;
    backfilled_notes INTEGER;
    backfilled_users INTEGER;
BEGIN
    -- Informational, never fatal. The backfill matches on exact string equality, so a value whose dash
    -- or casing drifted from the audited vocabulary simply misses and leaves course_program_id NULL --
    -- and with nothing reading either FK yet, a zero-match backfill would otherwise report success and
    -- surface only to whoever later writes the PR that starts reading it. V104 names this same silent
    -- no-op mode. These counts put the evidence in the deploy log instead.
    SELECT count(*) INTO seeded_programs FROM course_programs;
    SELECT count(*) INTO backfilled_notes FROM notes WHERE course_program_id IS NOT NULL;
    SELECT count(*) INTO backfilled_users FROM users WHERE course_program_id IS NOT NULL;

    RAISE NOTICE 'V106: seeded % course_programs; backfilled % notes and % users',
        seeded_programs, backfilled_notes, backfilled_users;

    IF backfilled_notes = 0 AND backfilled_users = 0 THEN
        RAISE NOTICE 'V106: no rows matched the catalog by exact name -- verify the seeded vocabulary against production before anything reads course_program_id';
    END IF;
END $$;
