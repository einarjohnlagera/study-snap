package com.studysnap.backend.repository;

import java.util.UUID;

public interface NoteCollectionAdoptionCountProjection {
    UUID getCollectionId();

    long getAdoptionCount();
}
