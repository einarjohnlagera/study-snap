ALTER TABLE users
    ADD COLUMN IF NOT EXISTS username VARCHAR(30);

WITH username_candidates AS (
    SELECT
        id,
        COALESCE(
            NULLIF(
                SUBSTRING(
                    TRIM(BOTH '-_' FROM REGEXP_REPLACE(
                        LOWER(COALESCE(NULLIF(display_name, ''), SPLIT_PART(email, '@', 1), 'user')),
                        '[^a-z0-9_-]+',
                        '',
                        'g'
                    ))
                    FOR 24
                ),
                ''
            ),
            'user'
        ) AS base_username
    FROM users
    WHERE username IS NULL OR username = ''
),
normalized_candidates AS (
    SELECT
        id,
        CASE
            WHEN LENGTH(base_username) < 3 THEN 'user'
            WHEN base_username IN ('admin', 'root', 'support', 'notelib', 'public', 'library', 'settings', 'login', 'signup', 'api') THEN 'user'
            ELSE base_username
        END AS base_username
    FROM username_candidates
),
deduped_candidates AS (
    SELECT
        id,
        base_username,
        ROW_NUMBER() OVER (PARTITION BY base_username ORDER BY id) AS duplicate_index
    FROM normalized_candidates
),
final_usernames AS (
    SELECT
        id,
        CASE
            WHEN duplicate_index = 1 THEN base_username
            ELSE SUBSTRING(base_username FOR (30 - LENGTH((duplicate_index - 1)::TEXT))) || (duplicate_index - 1)::TEXT
        END AS generated_username
    FROM deduped_candidates
)
UPDATE users u
SET username = f.generated_username
FROM final_usernames f
WHERE u.id = f.id;

ALTER TABLE users
    ALTER COLUMN username SET NOT NULL;

ALTER TABLE users
    ADD CONSTRAINT users_username_format_chk
    CHECK (username ~ '^[a-z0-9_-]{3,30}$');

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_username_lower
    ON users (LOWER(username));
