package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuickReviewSessionCompleteRequest;
import com.studysnap.backend.dto.QuickReviewSessionResponse;
import com.studysnap.backend.dto.QuickReviewSessionStartResponse;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class QuickReviewSessionService {
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final StudyPackRepository studyPackRepository;
    private final ActivityTrackingService activityTrackingService;

    public QuickReviewSessionStartResponse startSession(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = parseUuid(studyPackIdRaw, "STUDY_PACK_NOT_FOUND", "Study pack not found.");
        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));

        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setTotalQuestions(studyPack.getQuiz() == null ? 0 : studyPack.getQuiz().size());
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        session.setRetryCount(0);
        session.setCreatedAt(OffsetDateTime.now());
        session.setCompletedAt(null);
        quickReviewSessionRepository.save(session);

        activityTrackingService.recordActivity(userId, ActivityType.STARTED_QUICK_REVIEW, studyPackId);

        return new QuickReviewSessionStartResponse(session.getId().toString());
    }

    public QuickReviewSessionResponse completeSession(String sessionIdRaw, UUID userId, QuickReviewSessionCompleteRequest request) {
        UUID sessionId = parseUuid(sessionIdRaw, "SESSION_NOT_FOUND", "Quick Review session not found.");
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AppException("SESSION_NOT_FOUND", "Quick Review session not found.", HttpStatus.NOT_FOUND));

        if (request.correctAnswers() > request.totalQuestions()) {
            throw new AppException(
                    "INVALID_SESSION_RESULT",
                    "Correct answers cannot exceed total questions.",
                    HttpStatus.BAD_REQUEST
            );
        }

        BigDecimal scorePercentage = BigDecimal.valueOf(request.correctAnswers())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(request.totalQuestions()), 2, RoundingMode.HALF_UP);

        session.setTotalQuestions(request.totalQuestions());
        session.setCorrectAnswers(request.correctAnswers());
        session.setScorePercentage(scorePercentage);
        session.setRetryCount(request.retryCount());
        session.setDurationSeconds(request.durationSeconds());
        session.setSessionMetadata(request.sessionMetadata());
        session.setCompletedAt(OffsetDateTime.now());
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);

        activityTrackingService.recordActivity(userId, ActivityType.COMPLETED_QUICK_REVIEW, saved.getStudyPackId());

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<QuickReviewSessionResponse> listRecentSessions(String studyPackIdRaw, UUID userId, int limit) {
        UUID studyPackId = parseUuid(studyPackIdRaw, "STUDY_PACK_NOT_FOUND", "Study pack not found.");
        studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));

        int normalizedLimit = Math.max(1, Math.min(limit, 10));
        return quickReviewSessionRepository.findByUserIdAndStudyPackIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        PageRequest.of(0, normalizedLimit)
                ).stream()
                .map(this::toResponse)
                .toList();
    }

    private QuickReviewSessionResponse toResponse(QuickReviewSessionEntity session) {
        return new QuickReviewSessionResponse(
                session.getId().toString(),
                session.getStudyPackId().toString(),
                session.getTotalQuestions() == null ? 0 : session.getTotalQuestions(),
                session.getCorrectAnswers() == null ? 0 : session.getCorrectAnswers(),
                session.getScorePercentage() == null ? BigDecimal.ZERO : session.getScorePercentage(),
                session.getRetryCount() == null ? 0 : session.getRetryCount(),
                session.getDurationSeconds(),
                session.getCreatedAt(),
                session.getCompletedAt()
        );
    }

    private UUID parseUuid(String raw, String code, String message) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new AppException(code, message, HttpStatus.NOT_FOUND);
        }
    }
}
