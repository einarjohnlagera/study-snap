package com.studysnap.backend.model;

import com.studysnap.backend.entity.StudyPackStatus;

import java.util.List;
import java.util.UUID;

public interface StudyPackProgressView {
    UUID getId();

    UUID getNoteId();

    UUID getOwnerUserId();

    String getSubject();

    List<String> getKeyConcepts();

    StudyPackStatus getStatus();
}
