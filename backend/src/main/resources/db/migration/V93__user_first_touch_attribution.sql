ALTER TABLE users
    ADD COLUMN utm_source VARCHAR(255),
    ADD COLUMN utm_medium VARCHAR(255),
    ADD COLUMN utm_campaign VARCHAR(255),
    ADD COLUMN utm_content VARCHAR(255),
    ADD COLUMN utm_term VARCHAR(255),
    ADD COLUMN referrer VARCHAR(2048);
