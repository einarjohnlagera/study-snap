package com.studysnap.backend.repository;

import java.util.UUID;

public interface NoteCollectionItemCountProjection {
    UUID getCollectionId();

    long getItemCount();
}
