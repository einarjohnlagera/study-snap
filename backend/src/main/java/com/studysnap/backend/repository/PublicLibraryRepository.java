package com.studysnap.backend.repository;

import com.studysnap.backend.model.NoteListItemProjection;
import com.studysnap.backend.model.PublicLibrarySort;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PublicLibraryRepository {
    List<String> findDistinctPublicTags();

    List<NoteListItemProjection> findPublicLibraryPage(
            PublicLibraryFilterCriteria criteria,
            PublicLibrarySort sort,
            int offset,
            int limit
    );

    long countPublicLibraryMatches(PublicLibraryFilterCriteria criteria);

    List<PublicLibraryCandidateProjection> findPublicLibraryCandidates(PublicLibraryFilterCriteria criteria);

    List<NoteListItemProjection> findPublicLibraryListItemProjectionsByIdIn(Collection<UUID> noteIds);
}
