ALTER TABLE notes
    ADD COLUMN target_profile_type VARCHAR(16);

UPDATE notes n
SET target_profile_type = CASE
    WHEN u.profile_type = 'BOARD_EXAM' THEN 'BOARD_TAKER'
    WHEN u.profile_type = 'TEACHER' THEN 'TEACHER'
    ELSE 'STUDENT'
END
FROM users u
WHERE u.id = n.owner_user_id;

UPDATE notes
SET target_profile_type = 'STUDENT'
WHERE target_profile_type IS NULL;

ALTER TABLE notes
    ALTER COLUMN target_profile_type SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_notes_visibility_target_profile_updated_at
    ON notes (visibility, target_profile_type, updated_at DESC);
