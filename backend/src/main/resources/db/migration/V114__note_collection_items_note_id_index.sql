-- v0.78.0 -- index note_collection_items(note_id).
--
-- V72 indexed only (collection_id), because every read until now started from a collection and
-- walked to its items. The post-mastery next step reverses that direction: it starts from the note
-- the learner just mastered and asks which collections contain it
-- (NoteCollectionItemRepository.findContainingCollectionIdsByNoteIdAndOwnerUserIdOrderByUpdatedAtDesc).
-- That predicate is note_id, so it sequential-scanned the table on a request fired after every
-- completed session.
--
-- The UNIQUE (collection_id, note_id) constraint does not help: its leading column is collection_id,
-- and a filter on note_id alone cannot use it.
CREATE INDEX IF NOT EXISTS idx_note_collection_items_note_id
    ON note_collection_items(note_id);
