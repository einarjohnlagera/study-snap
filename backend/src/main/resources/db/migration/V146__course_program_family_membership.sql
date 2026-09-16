CREATE TABLE course_program_family (
    id UUID PRIMARY KEY,
    course_program_id UUID NOT NULL,
    program_family_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_course_program_family_program_family UNIQUE (course_program_id, program_family_id),
    CONSTRAINT fk_course_program_family_course_program FOREIGN KEY (course_program_id) REFERENCES course_programs (id) ON DELETE CASCADE,
    CONSTRAINT fk_course_program_family_program_family FOREIGN KEY (program_family_id) REFERENCES program_families (id) ON DELETE CASCADE
);

CREATE INDEX idx_course_program_family_program_id ON course_program_family (course_program_id);
CREATE INDEX idx_course_program_family_family_id ON course_program_family (program_family_id);

INSERT INTO course_program_family (id, course_program_id, program_family_id)
SELECT gen_random_uuid(), course_programs.id, course_programs.program_family_id
FROM course_programs
WHERE course_programs.program_family_id IS NOT NULL
ON CONFLICT (course_program_id, program_family_id) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM course_programs
        WHERE course_programs.program_family_id IS NOT NULL
          AND NOT EXISTS (
              SELECT 1 FROM course_program_family
              WHERE course_program_family.course_program_id = course_programs.id
                AND course_program_family.program_family_id = course_programs.program_family_id
          )
    ) THEN
        RAISE EXCEPTION 'V146: one or more legacy Course / Program family relationships were not copied';
    END IF;
END $$;
