package com.studysnap.backend.service;

import com.studysnap.backend.dto.CreatorImpactPageResponse;
import com.studysnap.backend.dto.CreatorImpactSummaryResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.CreatorImpactNoteProjection;
import com.studysnap.backend.repository.NoteCopyCountProjection;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PublicNoteEventCountProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CreatorImpactService {
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final NoteRepository noteRepository;
    private final AnalyticsEventRepository analyticsEventRepository;

    public CreatorImpactPageResponse getMine(UUID creatorUserId, boolean impacted, int page, int requestedSize) {
        int size = Math.clamp(requestedSize, 1, MAX_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(Math.max(0, page), size);
        List<CreatorImpactNoteProjection> rankedNotes = impacted
                ? noteRepository.findImpactedCreatorNotes(creatorUserId, pageRequest)
                : noteRepository.findZeroImpactCreatorNotes(creatorUserId, pageRequest);

        long totalImpacted = noteRepository.countImpactedNotesByCreatorUserId(creatorUserId);
        long publicNoteCount = noteRepository.countByOwnerUserIdAndVisibility(creatorUserId, NoteVisibility.PUBLIC);
        long totalZeroImpact = Math.max(0, publicNoteCount - totalImpacted);

        if (rankedNotes.isEmpty()) {
            return new CreatorImpactPageResponse(List.of(), page, size, totalImpacted, totalZeroImpact);
        }

        List<UUID> pageNoteIds = rankedNotes.stream()
                .map(CreatorImpactNoteProjection::getNoteId)
                .toList();
        Map<UUID, Long> viewsByNoteId = loadViewCounts(pageNoteIds);
        Map<UUID, Long> copiesByNoteId = loadCopyCounts(pageNoteIds);

        return new CreatorImpactPageResponse(
                rankedNotes.stream()
                        .map(note -> new CreatorImpactPageResponse.NoteImpact(
                                note.getNoteId().toString(),
                                note.getTitle(),
                                note.getLearnerCount(),
                                viewsByNoteId.getOrDefault(note.getNoteId(), 0L),
                                copiesByNoteId.getOrDefault(note.getNoteId(), 0L)
                        ))
                        .toList(),
                page,
                size,
                totalImpacted,
                totalZeroImpact
        );
    }

    public CreatorImpactSummaryResponse getSummary(UUID creatorUserId) {
        return new CreatorImpactSummaryResponse(
                noteRepository.countDistinctLearnersHelpedByCreatorUserId(creatorUserId),
                noteRepository.countByOwnerUserIdAndVisibility(creatorUserId, NoteVisibility.PUBLIC)
        );
    }

    private Map<UUID, Long> loadViewCounts(List<UUID> noteIds) {
        Map<UUID, Long> countsByNoteId = new HashMap<>();
        for (PublicNoteEventCountProjection projection
                : analyticsEventRepository.countPublicNoteEventsByTypeAndNoteIds(
                        AnalyticsEventType.PUBLIC_NOTE_VIEWED,
                        noteIds
                )) {
            if (projection.getNoteId() != null) {
                countsByNoteId.put(projection.getNoteId(), projection.getTotalCount());
            }
        }
        return countsByNoteId;
    }

    private Map<UUID, Long> loadCopyCounts(List<UUID> noteIds) {
        Map<UUID, Long> countsByNoteId = new HashMap<>();
        for (NoteCopyCountProjection projection : noteRepository.countCopiedPublicNotesBySourceNoteIds(noteIds)) {
            if (projection.getNoteId() != null) {
                countsByNoteId.put(projection.getNoteId(), projection.getCopyCount());
            }
        }
        return countsByNoteId;
    }
}
