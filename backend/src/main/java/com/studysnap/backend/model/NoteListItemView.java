package com.studysnap.backend.model;

import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.LearnerLevel;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface NoteListItemView {
    UUID getId();

    UUID getOwnerUserId();

    String getTitle();

    String getCourseProgram();

    DomainContext getDomainContext();

    LearnerLevel getLearnerLevel();

    String getSubject();

    String[] getTags();

    String getContent();

    NoteStatus getStatus();

    NoteVisibility getVisibility();

    OffsetDateTime getCreatedAt();

    OffsetDateTime getUpdatedAt();

    UUID getCopiedFromNoteId();

    Boolean getCopiedFromPublic();
}
