package com.studysnap.backend.repository;

import java.util.UUID;

public interface NoteCopyCountProjection {
    UUID getNoteId();
    long getCopyCount();
}
