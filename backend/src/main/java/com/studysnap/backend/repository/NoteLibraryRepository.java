package com.studysnap.backend.repository;

import com.studysnap.backend.model.NoteLibrarySort;
import com.studysnap.backend.model.NoteListItemProjection;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NoteLibraryRepository {
    List<NoteListItemProjection> findLibraryPage(
            NoteLibraryFilterCriteria criteria,
            NoteLibrarySort sort,
            int offset,
            int limit
    );

    long countLibraryMatches(NoteLibraryFilterCriteria criteria);

    List<NoteLibraryCandidateProjection> findLibraryCandidates(NoteLibraryFilterCriteria criteria);

    List<NoteLibrarySubjectProjection> findLibrarySubjectCandidates(NoteLibraryFilterCriteria criteria);

    List<NoteLibrarySubjectIdProjection> findLibrarySubjectIdCandidates(NoteLibraryFilterCriteria criteria);

    List<UUID> findLibraryMatchingIds(NoteLibraryFilterCriteria criteria, int limit);

    List<NoteListItemProjection> findLibraryListItemProjectionsByOwnerUserIdAndIdIn(
            UUID ownerUserId,
            Collection<UUID> noteIds
    );

    List<NoteLibrarySubjectProjection> findAllLibrarySubjectCandidates(UUID ownerUserId);

    List<NoteLibraryValueCountProjection> countLibraryCoursePrograms(UUID ownerUserId);

    List<NoteLibraryValueCountProjection> countLibraryTags(UUID ownerUserId);
}
