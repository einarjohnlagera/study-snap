package com.studysnap.backend.repository;

import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.model.PublicLibrarySource;

import java.util.List;
import java.util.UUID;

public record PublicLibraryFilterCriteria(
        UUID viewerUserId,
        UUID deletedUserId,
        String searchPattern,
        String subjectSlug,
        List<String> tagSlugs,
        String courseProgramSlug,
        String creator,
        NoteTargetProfileType targetProfileType,
        boolean readyOnly,
        List<PublicLibrarySource> sources
) {
}
