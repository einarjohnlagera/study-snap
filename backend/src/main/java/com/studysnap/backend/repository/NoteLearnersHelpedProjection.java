package com.studysnap.backend.repository;

import java.util.UUID;

public interface NoteLearnersHelpedProjection {
    UUID getNoteId();
    long getLearnerCount();
}
