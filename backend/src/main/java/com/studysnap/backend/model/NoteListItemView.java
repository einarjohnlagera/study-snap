package com.studysnap.backend.model;

import com.studysnap.backend.entity.NoteStatus;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface NoteListItemView {
    UUID getId();

    UUID getOwnerUserId();

    String getTitle();

    String getCourseProgram();

    NoteTargetProfileType getTargetProfileType();

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
