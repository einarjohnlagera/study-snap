ALTER TABLE user_usage
    ADD COLUMN docx_exports_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN pdf_exports_count INTEGER NOT NULL DEFAULT 0;

UPDATE user_usage
SET docx_exports_count = exports_count
WHERE user_id IN (
    SELECT id
    FROM users
    WHERE profile_type = 'TEACHER'
       OR role = 'ADMIN'
);

UPDATE user_usage
SET pdf_exports_count = exports_count
WHERE user_id IN (
    SELECT id
    FROM users
    WHERE (profile_type IS NULL OR profile_type <> 'TEACHER')
      AND (role IS NULL OR role <> 'ADMIN')
);

ALTER TABLE user_usage
    DROP COLUMN exports_count;
