package com.studysnap.backend.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface SharedNoteListProjection {
    UUID getShareId();
    UUID getNoteId();
    String getTitle();
    String getSubject();
    String getOwnerDisplayName();
    String getOwnerFirstName();
    String getOwnerLastName();
    String getOwnerEmail();
    UUID getStudyPackId();
    String getStudyPackStatus();
    OffsetDateTime getSharedAt();
}
