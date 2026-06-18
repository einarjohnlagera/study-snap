ALTER TABLE note_collections
    ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN course_program VARCHAR(120) NULL,
    ADD COLUMN source_plan_id UUID NULL;

CREATE INDEX idx_note_collections_visibility_course_program
    ON note_collections(visibility, course_program);

CREATE UNIQUE INDEX idx_note_collections_owner_source_plan
    ON note_collections(owner_user_id, source_plan_id)
    WHERE source_plan_id IS NOT NULL;
