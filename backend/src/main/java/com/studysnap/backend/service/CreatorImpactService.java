package com.studysnap.backend.service;

import com.studysnap.backend.dto.CreatorImpactResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.NoteCopyCountProjection;
import com.studysnap.backend.repository.NoteLearnersHelpedProjection;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PublicNoteEventCountProjection;
import lombok.RequiredArgsConstructor;
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
    private final NoteRepository noteRepository;
    private final AnalyticsEventRepository analyticsEventRepository;

    public CreatorImpactResponse getMine(UUID creatorUserId) {
        List<NoteEntity> publicNotes = noteRepository.findByOwnerUserIdAndVisibilityOrderByUpdatedAtDesc(
                creatorUserId,
                NoteVisibility.PUBLIC
        );
        if (publicNotes.isEmpty()) {
            return new CreatorImpactResponse(0, List.of());
        }

        List<UUID> noteIds = publicNotes.stream()
                .map(NoteEntity::getId)
                .toList();
        Map<UUID, Long> learnersByNoteId = loadLearnerCounts(noteIds);
        Map<UUID, Long> viewsByNoteId = loadViewCounts(noteIds);
        Map<UUID, Long> copiesByNoteId = loadCopyCounts(noteIds);
        long distinctLearnersHelped = noteRepository.countDistinctLearnersHelpedByCreatorUserId(creatorUserId);

        return new CreatorImpactResponse(
                distinctLearnersHelped,
                publicNotes.stream()
                        .map(note -> new CreatorImpactResponse.NoteImpact(
                                note.getId().toString(),
                                note.getTitle(),
                                learnersByNoteId.getOrDefault(note.getId(), 0L),
                                viewsByNoteId.getOrDefault(note.getId(), 0L),
                                copiesByNoteId.getOrDefault(note.getId(), 0L)
                        ))
                        .toList()
        );
    }

    private Map<UUID, Long> loadLearnerCounts(List<UUID> noteIds) {
        Map<UUID, Long> countsByNoteId = new HashMap<>();
        for (NoteLearnersHelpedProjection projection
                : noteRepository.countDistinctLearnersHelpedBySourceNoteIds(noteIds)) {
            if (projection.getNoteId() != null) {
                countsByNoteId.put(projection.getNoteId(), projection.getLearnerCount());
            }
        }
        return countsByNoteId;
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
