package com.studysnap.backend.repository;

import com.studysnap.backend.model.NoteLibrarySort;
import com.studysnap.backend.model.NoteListItemProjection;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    /**
     * Owner-scoped list for {@code GET /notes}, newest first. Shares the native select and join with
     * {@link #findLibraryPage}, which is the point: the JPQL projection this replaced never selected
     * {@code applicablePrograms}, so the endpoint advertised a field it always returned empty (M2).
     *
     * <p>⚠️ {@code searchPattern} EXISTS SO A CALLER CAN BOUND {@code limit} WITHOUT NARROWING WHAT
     * IT CAN REACH. The Study Plan builder's note picker used to fetch the whole library and filter
     * it in the browser; a bound alone would have made every note past the limit unaddable, which is
     * why {@code v0.123.0} declined the bound outright. The two ship together.
     *
     * @param searchPattern an already-escaped, already-lowercased {@code %…%} LIKE pattern, or
     *                      {@code null} for no search. Matches title, subject, course program AND
     *                      tags — the same four fields the client filter it replaces matched.
     * @param limit {@code null} for unbounded — several callers list the whole library.
     */
    List<NoteListItemProjection> findListItemProjectionsByOwnerUserId(
            UUID ownerUserId,
            String searchPattern,
            Integer limit
    );

    List<NoteLibrarySubjectProjection> findAllLibrarySubjectCandidates(UUID ownerUserId);

    List<NoteLibraryValueCountProjection> countLibraryCoursePrograms(UUID ownerUserId);

    List<NoteLibraryValueCountProjection> countLibraryTags(UUID ownerUserId);

    boolean existsOwnedNoteWithQuizQuestions(UUID ownerUserId);

    Optional<UUID> findMostRecentlyUpdatedStudyPackReadyNoteId(UUID ownerUserId);
}
