package com.studysnap.backend.repository;

import java.util.UUID;

public interface NoteCollectionChildCountProjection {
    UUID getCollectionId();

    long getChildCount();
}
