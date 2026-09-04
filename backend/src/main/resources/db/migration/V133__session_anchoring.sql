-- v0.113.0 Session Anchoring.
--
-- ⚠️ THE NOT VALID / VALIDATE SPLIT BELOW DOES NOT SHORTEN THE LOCK WINDOW IN THIS FILE, and is kept
-- for intent and for any future re-run on a larger table -- not because it buys anything here.
-- Flyway runs one script in one transaction (no group/mixed flag is set), and the ALTER TABLE
-- immediately below already takes ACCESS EXCLUSIVE and holds it to COMMIT, which subsumes
-- VALIDATE's weaker SHARE UPDATE EXCLUSIVE. Measured with pg_locks, not assumed.
--
-- What actually makes this safe is the table's SIZE: an owner-run read on 2026-09-04 returned
-- 698 rows / 2496 kB, with ZERO rows failing the new CHECK, so the whole migration is milliseconds.
-- ⚠️ If quick_review_sessions ever grows large, this file must move OUT of the transaction
-- (-- flyway:executeInTransaction=false) or split, or the ACCESS EXCLUSIVE lock will block READS on
-- the one table every quiz mode shares while the old instance is still serving traffic.

ALTER TABLE quick_review_sessions
    ALTER COLUMN study_pack_id DROP NOT NULL,
    ALTER COLUMN note_id DROP NOT NULL,
    -- ⚠️ SET NULL, NOT CASCADE. A cascade here would make deleting a Study Plan -- an ORGANISATIONAL
    -- act, where "Remove never means canonical note deletion" is standing doctrine -- hard-delete the
    -- learner's COMPLETED quiz history for that plan, while the ConceptHealth rows those sessions
    -- wrote survive (that table is keyed per pack and has no collection FK). Evidence would persist
    -- while the history explaining it vanished.
    -- Account deletion does NOT depend on this: quick_review_sessions.user_id has cascaded since
    -- V4:3, so orphan cleanup was already covered and the cascade added only a second, user-facing
    -- deletion trigger.
    ADD COLUMN source_collection_id UUID
        REFERENCES note_collections(id) ON DELETE SET NULL;

-- An ACTIVE session must be reachable, so it must carry an anchor. A TERMINAL one is history: it is
-- surfaced through session_state.sourceNoteRefs, which credits every note the session sampled, so it
-- stays reachable after ON DELETE SET NULL has cleared its collection. Without the terminal branch
-- that SET NULL would violate this constraint and deleting a plan would fail outright.
ALTER TABLE quick_review_sessions
    ADD CONSTRAINT chk_quick_review_sessions_anchor
        CHECK (
            (study_pack_id IS NOT NULL AND note_id IS NOT NULL)
            OR source_collection_id IS NOT NULL
            OR status IN ('COMPLETED', 'FORFEITED')
        ) NOT VALID;

ALTER TABLE quick_review_sessions
    VALIDATE CONSTRAINT chk_quick_review_sessions_anchor;

DROP INDEX IF EXISTS idx_quick_review_sessions_one_active_generation;
DROP INDEX IF EXISTS idx_quick_review_sessions_one_active_generation_note;

CREATE UNIQUE INDEX idx_quick_review_sessions_one_active_generation
    ON quick_review_sessions(user_id, study_pack_id, session_mode)
    WHERE status IN ('GENERATING', 'IN_PROGRESS')
      AND study_pack_id IS NOT NULL;

CREATE UNIQUE INDEX idx_quick_review_sessions_one_active_generation_note
    ON quick_review_sessions(user_id, note_id, session_mode)
    WHERE status IN ('GENERATING', 'IN_PROGRESS')
      AND note_id IS NOT NULL;

CREATE UNIQUE INDEX idx_quick_review_sessions_one_active_generation_collection
    ON quick_review_sessions(user_id, source_collection_id, session_mode)
    WHERE status IN ('GENERATING', 'IN_PROGRESS')
      AND source_collection_id IS NOT NULL;

CREATE INDEX idx_quick_review_sessions_source_collection
    ON quick_review_sessions(source_collection_id);
