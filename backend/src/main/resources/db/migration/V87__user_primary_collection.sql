ALTER TABLE users
    ADD COLUMN primary_collection_id UUID NULL REFERENCES note_collections(id) ON DELETE SET NULL;

CREATE INDEX idx_users_primary_collection_id
    ON users(primary_collection_id);
