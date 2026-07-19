package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.model.NoteLibraryReadiness;

import java.util.List;
import java.util.UUID;

public record NoteLibraryFilterCriteria(
        UUID ownerUserId,
        String searchPattern,
        NoteLibraryReadiness readiness,
        String courseProgram,
        List<String> tags,
        NoteVisibility visibility
) {
}
