package com.studysnap.backend.repository;

import java.util.UUID;

public interface PublicNoteEventCountProjection {
    UUID getNoteId();
    long getTotalCount();
}
