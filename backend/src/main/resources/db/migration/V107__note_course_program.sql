CREATE TABLE note_course_program (
    id UUID PRIMARY KEY,
    note_id UUID NOT NULL,
    course_program_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_note_course_program_note_program UNIQUE (note_id, course_program_id),
    CONSTRAINT fk_note_course_program_note
        FOREIGN KEY (note_id) REFERENCES notes (id) ON DELETE CASCADE,
    CONSTRAINT fk_note_course_program_course_program
        FOREIGN KEY (course_program_id) REFERENCES course_programs (id)
);

CREATE INDEX idx_note_course_program_note_id
    ON note_course_program (note_id);

CREATE INDEX idx_note_course_program_course_program_id
    ON note_course_program (course_program_id);

DO $$
DECLARE
    notes_total INTEGER;
    matched_notes INTEGER;
    inserted_rows INTEGER;
    unmatched_notes INTEGER;
    fk_disagreements INTEGER;
BEGIN
    INSERT INTO note_course_program (id, note_id, course_program_id)
    SELECT gen_random_uuid(), notes.id, course_programs.id
    FROM notes
    JOIN course_programs
      ON course_programs.name = CASE
          WHEN notes.course_program = 'Bsed' THEN 'Education'
          ELSE notes.course_program
      END
    ON CONFLICT (note_id, course_program_id) DO NOTHING;

    GET DIAGNOSTICS inserted_rows = ROW_COUNT;

    SELECT count(*) INTO notes_total FROM notes;
    SELECT count(DISTINCT notes.id)
      INTO matched_notes
      FROM notes
      JOIN course_programs
        ON course_programs.name = CASE
            WHEN notes.course_program = 'Bsed' THEN 'Education'
            ELSE notes.course_program
        END;
    SELECT count(*)
      INTO unmatched_notes
      FROM notes
     WHERE notes.course_program IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
             FROM note_course_program
            WHERE note_course_program.note_id = notes.id
       );
    SELECT count(*)
      INTO fk_disagreements
      FROM notes
     WHERE notes.course_program_id IS NOT NULL
       AND NOT EXISTS (
           SELECT 1
             FROM note_course_program
            WHERE note_course_program.note_id = notes.id
              AND note_course_program.course_program_id = notes.course_program_id
       );

    RAISE NOTICE 'V107: % notes total; inserted % note_course_program rows; % notes with a non-null course_program remain unmatched',
        notes_total, inserted_rows, unmatched_notes;

    IF matched_notes = 0 THEN
        RAISE NOTICE 'V107: no notes matched the catalog by exact name or the Bsed alias -- verify the seeded vocabulary before Slice 2 reads note_course_program';
    END IF;

    -- Informational, never fatal. notes.course_program_id is written by nothing in application code, so it
    -- has been frozen at whatever V106 resolved since v0.70.0 deployed while notes.course_program stayed
    -- editable. A note whose course/program was edited from one catalog value to another in that window
    -- therefore disagrees with its own stale FK by construction -- that is expected staleness, not catalog
    -- resolution drift, and failing the migration on it would block the deploy on ordinary user edits.
    -- The count is still worth surfacing: a large number would indicate genuine drift worth investigating
    -- before Slice 2 starts reading this join.
    IF fk_disagreements > 0 THEN
        RAISE NOTICE 'V107: % notes disagree with their stale notes.course_program_id (expected where course_program was edited after v0.70.0); review before Slice 2 if unexpectedly large',
            fk_disagreements;
    END IF;
END $$;
