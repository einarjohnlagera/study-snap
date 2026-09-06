package com.studysnap.backend.repository;

import com.studysnap.backend.model.NoteListItemProjection;
import com.studysnap.backend.model.PublicLibrarySort;

import java.time.OffsetDateTime;
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

    /**
     * Counts the rows a ranked page draws from, including the sort's own eligibility filter.
     *
     * <p>{@code FEATURED}, {@code POPULAR} and {@code COPIED} drop ineligible notes before ordering,
     * so their total is smaller than {@link #countPublicLibraryMatches(PublicLibraryFilterCriteria)}.
     * Every other sort ranks the full match set and the two agree. A {@code null} sort means the
     * legacy unpaginated ordering, which filters nothing.
     */
    long countPublicLibraryRankedMatches(PublicLibraryFilterCriteria criteria, PublicLibrarySort sort);

    /**
     * Returns ONE bounded page of note ids in ranked order, ordered and limited by the database.
     *
     * <p>⚠️ This replaced {@code findPublicLibraryCandidates}, which loaded every matching row so the
     * ranking could run in Java. That unbounded load — on an anonymous {@code permitAll} endpoint,
     * inside a read-only transaction holding a pooled JDBC connection — is the root cause of the
     * 2026-09-05 production outage. Do not reintroduce a repository method that returns the whole
     * candidate set.
     *
     * @param sort            {@code null} selects the legacy unpaginated ordering (updated_at desc)
     * @param rankedAt        the clock the age-decay factor is measured against; affects ORDER only,
     *                        never membership, because no eligibility rule is time-dependent
     * @param excludedNoteIds ids to leave out, used by the discovery sections to keep the three
     *                        sections mutually exclusive
     */
    List<UUID> findPublicLibraryRankedPageIds(
            PublicLibraryFilterCriteria criteria,
            PublicLibrarySort sort,
            OffsetDateTime rankedAt,
            Collection<UUID> excludedNoteIds,
            int offset,
            int limit
    );

    List<NoteListItemProjection> findPublicLibraryListItemProjectionsByIdIn(Collection<UUID> noteIds);
}
