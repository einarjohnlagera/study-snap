package com.studysnap.backend.service;

import com.studysnap.backend.dto.RecentQuizSessionHistoryResponse;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.repository.NoteLatestCompletionProjection;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.util.QuizSessionStateUtils;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class QuizSessionHistoryService {
    private static final String SESSION_STATE_MODE = "mode";
    private static final String SESSION_STATE_SOURCE_NOTE_REFS = "sourceNoteRefs";
    private static final String SOURCE_NOTE_ID_KEY = "noteId";
    private static final String SESSION_METADATA_WEAK_CONCEPTS = "weakConcepts";
    private static final String MODE_BOARD_EXAM = "board_exam";
    private static final String SUB_MODE_INTERVIEW = "INTERVIEW";
    private static final String HISTORY_MODE_BOARD_EXAM = "BOARD_EXAM";
    private static final String HISTORY_MODE_INTERVIEW_PRACTICE = "INTERVIEW_PRACTICE";
    private static final String PERFORMANCE_LEVEL_EXCELLENT = "Excellent";
    private static final String PERFORMANCE_LEVEL_GOOD = "Good";
    private static final String PERFORMANCE_LEVEL_FAIR = "Fair";
    private static final String PERFORMANCE_LEVEL_NEEDS_IMPROVEMENT = "Needs Improvement";
    private static final int FIRST_PAGE_LIMIT = 1;
    private static final int MAX_RECENT_SESSION_LIMIT = 10;
    private static final int EXCELLENT_SCORE_THRESHOLD = 90;
    private static final int GOOD_SCORE_THRESHOLD = 75;
    private static final int FAIR_SCORE_THRESHOLD = 50;
    private static final List<QuickReviewSessionMode> MULTI_NOTE_SESSION_MODES = List.of(
            QuickReviewSessionMode.LONG_EXAM,
            QuickReviewSessionMode.CHALLENGE
    );

    private final QuickReviewSessionRepository quickReviewSessionRepository;

    public Map<UUID, OffsetDateTime> findLatestSessionCompletedAtByNoteIds(UUID userId, Collection<UUID> noteIds) {
        if (userId == null || noteIds == null || noteIds.isEmpty()) {
            return Map.of();
        }

        Set<UUID> requestedNoteIds = noteIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (requestedNoteIds.isEmpty()) {
            return Map.of();
        }

        Map<UUID, OffsetDateTime> latestByNoteId = new HashMap<>();
        for (NoteLatestCompletionProjection projection : quickReviewSessionRepository.findLatestCompletedAtByUserIdAndNoteIdIn(
                userId,
                QuickReviewSessionStatus.COMPLETED,
                requestedNoteIds
        )) {
            latestByNoteId.put(projection.noteId(), projection.completedAt());
        }
        for (QuickReviewSessionEntity session : findCompletedMultiNoteSessionCandidates(userId)) {
            for (UUID noteId : findParticipatingNoteIds(session)) {
                if (requestedNoteIds.contains(noteId)) {
                    latestByNoteId.merge(noteId, session.getCompletedAt(), this::latest);
                }
            }
        }
        return latestByNoteId;
    }

    public List<RecentQuizSessionHistoryResponse> listRecentSessions(String noteIdRaw, UUID userId, int limit) {
        UUID noteId = UuidParsingUtils.parseUuidOrThrow(noteIdRaw, NoteNotFoundException::new);
        int normalizedLimit = Math.clamp(limit, FIRST_PAGE_LIMIT, MAX_RECENT_SESSION_LIMIT);
        return quickReviewSessionRepository.findRecentHistoryCandidatesByUserIdAndNoteIdOrderByCompletedAtDesc(
                        userId,
                        noteId,
                        QuickReviewSessionStatus.COMPLETED,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        MULTI_NOTE_SESSION_MODES
                ).stream()
                .filter(session -> findParticipatingNoteIds(session).contains(noteId))
                .limit(normalizedLimit)
                .map(this::toResponse)
                .toList();
    }

    private List<QuickReviewSessionEntity> findCompletedMultiNoteSessionCandidates(UUID userId) {
        return quickReviewSessionRepository.findByUserIdAndStatusAndSessionModeInAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                userId,
                QuickReviewSessionStatus.COMPLETED,
                MULTI_NOTE_SESSION_MODES
        );
    }

    private RecentQuizSessionHistoryResponse toResponse(QuickReviewSessionEntity session) {
        String historyMode = resolveHistoryMode(session);
        return new RecentQuizSessionHistoryResponse(
                session.getId().toString(),
                historyMode,
                safeInt(session.getTotalQuestions()),
                safeInt(session.getCorrectAnswers()),
                session.getScorePercentage() == null ? BigDecimal.ZERO : session.getScorePercentage(),
                safeInt(session.getRetryCount()),
                resolvePerformanceLevel(historyMode, session.getScorePercentage()),
                extractWeakConcepts(session.getSessionMetadata()),
                findParticipatingNoteIds(session).size(),
                session.getCreatedAt(),
                session.getCompletedAt()
        );
    }

    private Set<UUID> findParticipatingNoteIds(QuickReviewSessionEntity session) {
        Set<UUID> noteIds = new HashSet<>();
        if (session.getNoteId() != null) {
            noteIds.add(session.getNoteId());
        }
        boolean isMultiNoteMode = session.getSessionMode() == QuickReviewSessionMode.LONG_EXAM
                || (session.getSessionMode() == QuickReviewSessionMode.CHALLENGE && isBoardExam(session.getSessionState()));
        if (!isMultiNoteMode) {
            return noteIds;
        }
        Object rawSourceRefs = session.getSessionState() == null
                ? null
                : session.getSessionState().get(SESSION_STATE_SOURCE_NOTE_REFS);
        if (!(rawSourceRefs instanceof List<?> sourceRefs)) {
            return noteIds;
        }
        for (Object sourceRef : sourceRefs) {
            UUID sourceNoteId = readSourceNoteId(sourceRef);
            if (sourceNoteId != null) {
                noteIds.add(sourceNoteId);
            }
        }
        return noteIds;
    }

    private UUID readSourceNoteId(Object sourceRef) {
        if (!(sourceRef instanceof Map<?, ?> sourceRefMap)) {
            return null;
        }
        Object rawNoteId = sourceRefMap.get(SOURCE_NOTE_ID_KEY);
        if (!(rawNoteId instanceof String noteId) || noteId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(noteId);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String resolveHistoryMode(QuickReviewSessionEntity session) {
        if (session.getSessionMode() == QuickReviewSessionMode.CHALLENGE && isBoardExam(session.getSessionState())) {
            return HISTORY_MODE_BOARD_EXAM;
        }
        if (session.getSessionMode() == QuickReviewSessionMode.ADAPTIVE && isInterviewPractice(session.getSessionState())) {
            return HISTORY_MODE_INTERVIEW_PRACTICE;
        }
        return session.getSessionMode().name();
    }

    private boolean isBoardExam(Map<String, Object> sessionState) {
        if (sessionState == null) {
            return false;
        }
        Object rawMode = sessionState.get(SESSION_STATE_MODE);
        return rawMode instanceof String mode && MODE_BOARD_EXAM.equals(mode);
    }

    private boolean isInterviewPractice(Map<String, Object> sessionState) {
        return SUB_MODE_INTERVIEW.equals(QuizSessionStateUtils.extractSubMode(sessionState));
    }

    private String resolvePerformanceLevel(String historyMode, BigDecimal scorePercentage) {
        if (scorePercentage == null
                || (!QuickReviewSessionMode.CHALLENGE.name().equals(historyMode)
                && !HISTORY_MODE_BOARD_EXAM.equals(historyMode))) {
            return null;
        }
        if (scorePercentage.compareTo(BigDecimal.valueOf(EXCELLENT_SCORE_THRESHOLD)) >= 0) {
            return PERFORMANCE_LEVEL_EXCELLENT;
        }
        if (scorePercentage.compareTo(BigDecimal.valueOf(GOOD_SCORE_THRESHOLD)) >= 0) {
            return PERFORMANCE_LEVEL_GOOD;
        }
        if (scorePercentage.compareTo(BigDecimal.valueOf(FAIR_SCORE_THRESHOLD)) >= 0) {
            return PERFORMANCE_LEVEL_FAIR;
        }
        return PERFORMANCE_LEVEL_NEEDS_IMPROVEMENT;
    }

    private List<String> extractWeakConcepts(Map<String, Object> sessionMetadata) {
        if (sessionMetadata == null) {
            return List.of();
        }
        Object rawWeakConcepts = sessionMetadata.get(SESSION_METADATA_WEAK_CONCEPTS);
        if (!(rawWeakConcepts instanceof List<?> weakConcepts)) {
            return List.of();
        }
        return weakConcepts.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(concept -> !concept.isBlank())
                .toList();
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private OffsetDateTime latest(OffsetDateTime left, OffsetDateTime right) {
        return left.isAfter(right) ? left : right;
    }
}
