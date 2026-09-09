package com.studysnap.backend.repository;

import java.util.UUID;

public interface CreatorImpactNoteProjection {
    UUID getNoteId();
    String getTitle();
    long getLearnerCount();
}
