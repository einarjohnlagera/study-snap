package com.studysnap.backend.repository;

import java.util.UUID;

public interface NoteCollectionItemNoteProjection {
    UUID getCollectionId();

    UUID getNoteId();
}
