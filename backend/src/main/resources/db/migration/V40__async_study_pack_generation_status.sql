ALTER TABLE notes
    DROP CONSTRAINT IF EXISTS ck_notes_status;

ALTER TABLE notes
    ADD CONSTRAINT ck_notes_status
        CHECK (status IN ('DRAFT', 'GENERATING', 'FAILED', 'GENERATED'));
