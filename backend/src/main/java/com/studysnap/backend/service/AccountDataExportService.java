package com.studysnap.backend.service;

import com.studysnap.backend.dto.DataExportResponse;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.NoteCollectionItemEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.LinkedLearnerProvisionalBirthYearRepository;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountDataExportService {
    private static final String SCHEMA_VERSION = "1.2";
    private static final int SESSION_EXPORT_BATCH_SIZE = 100;

    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final NoteCollectionRepository noteCollectionRepository;
    private final NoteCollectionItemRepository noteCollectionItemRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final LinkedLearnerProvisionalBirthYearRepository provisionalBirthYearRepository;

    @Transactional(readOnly = true)
    public DataExportResponse exportForUser(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        List<NoteEntity> notes = noteRepository.findByOwnerUserIdOrderByUpdatedAtDesc(userId);
        Map<UUID, NoteEntity> notesById = notes.stream()
                .collect(Collectors.toMap(NoteEntity::getId, Function.identity()));
        List<StudyPackEntity> studyPacks = studyPackRepository.findByOwnerUserId(userId);
        List<NoteCollectionEntity> collections = noteCollectionRepository.findByOwnerUserId(userId);
        DataExportResponse.PracticeSummary practiceSummary = buildPracticeSummary(userId);

        return new DataExportResponse(
                new DataExportResponse.Meta(OffsetDateTime.now(), SCHEMA_VERSION),
                toAccount(user, provisionalBirthYearRepository.findAllDeclaredByLearner(userId)),
                notes.stream().map(this::toNote).toList(),
                studyPacks.stream().map(this::toStudyPack).toList(),
                collections.stream()
                        .map(collection -> toCollection(collection, notesById))
                        .toList(),
                practiceSummary
        );
    }

    private DataExportResponse.Account toAccount(
            UserEntity user,
            List<com.studysnap.backend.entity.LinkedLearnerProvisionalBirthYearEntity> provisional
    ) {
        return new DataExportResponse.Account(
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getDisplayName(),
                user.getUsername(),
                user.getProfileType(),
                user.getLearnerLevel(),
                user.getCourseProgram(),
                user.getStudyGoal(),
                toList(user.getFocusSubjects()),
                user.getExamDate(),
                user.getBirthYear(),
                provisional.stream()
                        .map(row -> new DataExportResponse.ProvisionalBirthYear(
                                row.getRelationshipId(), row.getBirthYear(), row.getDeclaredAt()))
                        .toList(),
                user.getCreatedAt()
        );
    }

    private DataExportResponse.Note toNote(NoteEntity note) {
        return new DataExportResponse.Note(
                note.getId(),
                note.getTitle(),
                note.getSubject(),
                note.getDomainContext(),
                note.getLearnerLevel(),
                note.getContent(),
                note.getVisibility(),
                note.getCopiedFromTitle(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }

    private DataExportResponse.StudyPack toStudyPack(StudyPackEntity studyPack) {
        return new DataExportResponse.StudyPack(
                studyPack.getNoteId(),
                studyPack.getTitle(),
                studyPack.getSummary(),
                studyPack.getKeyConcepts(),
                studyPack.getQuiz()
        );
    }

    private DataExportResponse.Collection toCollection(
            NoteCollectionEntity collection,
            Map<UUID, NoteEntity> notesById
    ) {
        List<DataExportResponse.CollectionNoteReference> noteReferences = noteCollectionItemRepository
                .findByCollectionIdOrderByPositionAsc(collection.getId())
                .stream()
                .map(NoteCollectionItemEntity::getNoteId)
                .filter(notesById::containsKey)
                .map(noteId -> toCollectionNoteReference(noteId, notesById.get(noteId)))
                .toList();
        return new DataExportResponse.Collection(collection.getTitle(), noteReferences);
    }

    private DataExportResponse.CollectionNoteReference toCollectionNoteReference(UUID noteId, NoteEntity note) {
        return new DataExportResponse.CollectionNoteReference(
                noteId,
                note == null ? null : note.getTitle()
        );
    }

    private DataExportResponse.PracticeSummary buildPracticeSummary(UUID userId) {
        Map<QuickReviewSessionMode, Long> completedSessionsByMode = new EnumMap<>(QuickReviewSessionMode.class);
        OffsetDateTime lastCompletedAt = null;
        long totalCompletedSessions = 0;
        int page = 0;
        List<QuickReviewSessionEntity> batch;
        do {
            batch = quickReviewSessionRepository.findByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                    userId,
                    PageRequest.of(page, SESSION_EXPORT_BATCH_SIZE)
            );
            for (QuickReviewSessionEntity session : batch) {
                if (lastCompletedAt == null) {
                    lastCompletedAt = session.getCompletedAt();
                }
                completedSessionsByMode.merge(session.getSessionMode(), 1L, Long::sum);
                totalCompletedSessions++;
            }
            page++;
        } while (batch.size() == SESSION_EXPORT_BATCH_SIZE);

        return new DataExportResponse.PracticeSummary(
                totalCompletedSessions,
                completedSessionsByMode,
                lastCompletedAt
        );
    }

    private List<String> toList(String[] values) {
        if (values == null || values.length == 0) {
            return List.of();
        }
        return Arrays.stream(values).toList();
    }
}
