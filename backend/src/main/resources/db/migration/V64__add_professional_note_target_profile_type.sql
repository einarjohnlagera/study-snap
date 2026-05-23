ALTER TABLE notes
    DROP CONSTRAINT IF EXISTS chk_notes_target_profile_type;

ALTER TABLE notes
    ADD CONSTRAINT chk_notes_target_profile_type
    CHECK (target_profile_type IN ('STUDENT', 'BOARD_TAKER', 'PROFESSIONAL'));
