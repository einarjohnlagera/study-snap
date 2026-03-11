package com.studysnap.backend.service;

import com.studysnap.backend.dto.QuickReviewSessionCompleteRequest;
import com.studysnap.backend.dto.QuickReviewPerformanceSummaryResponse;
import com.studysnap.backend.dto.QuickReviewSessionProgressRequest;
import com.studysnap.backend.dto.QuickReviewSessionResponse;
import com.studysnap.backend.dto.QuickReviewSessionStartResponse;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.util.UuidParsingUtils;
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
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                studyPackIdRaw,
                "STUDY_PACK_NOT_FOUND",
                "Study pack not found.",
                HttpStatus.NOT_FOUND
        );
        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserIdForUpdate(studyPackId, userId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));

        QuickReviewSessionEntity existing = quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndStatusOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionStatus.IN_PROGRESS
                )
                .orElse(null);
        if (existing != null) {
            return toStartResponse(existing);
        }

        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(studyPack.getQuiz() == null ? 0 : studyPack.getQuiz().size());
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        session.setRetryCount(0);
        session.setSessionState(null);
        session.setCreatedAt(OffsetDateTime.now());
        session.setCompletedAt(null);
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);

        activityTrackingService.recordActivity(userId, ActivityType.STARTED_QUICK_REVIEW, studyPackId);

        return toStartResponse(saved);
    }

    @Transactional(readOnly = true)
    public QuickReviewSessionStartResponse getInProgressSession(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                studyPackIdRaw,
                "STUDY_PACK_NOT_FOUND",
                "Study pack not found.",
                HttpStatus.NOT_FOUND
        );
        studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));

        return quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndStatusOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionStatus.IN_PROGRESS
                )
                .map(this::toStartResponse)
                .orElse(new QuickReviewSessionStartResponse(null, null, 0, null, 0, null));
    }

    public QuickReviewSessionResponse updateSessionProgress(
            String sessionIdRaw,
            UUID userId,
            QuickReviewSessionProgressRequest request
    ) {
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(
                sessionIdRaw,
                "SESSION_NOT_FOUND",
                "Quick Review session not found.",
                HttpStatus.NOT_FOUND
        );
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new AppException("SESSION_NOT_FOUND", "Quick Review session not found.", HttpStatus.NOT_FOUND));

        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            throw new AppException(
                    "SESSION_NOT_IN_PROGRESS",
                    "Quick Review session is already completed.",
                    HttpStatus.BAD_REQUEST
            );
        }

        session.setCurrentQuestionIndex(request.currentQuestionIndex());
        session.setCurrentRound(request.currentRound());
        session.setRetryCount(request.retryCount());
        session.setSessionState(request.sessionState());
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);

        return toResponse(saved);
    }

    public QuickReviewSessionResponse completeSession(String sessionIdRaw, UUID userId, QuickReviewSessionCompleteRequest request) {
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(
                sessionIdRaw,
                "SESSION_NOT_FOUND",
                "Quick Review session not found.",
                HttpStatus.NOT_FOUND
        );
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

        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(request.totalQuestions());
        session.setCurrentRound(request.retryCount() > 0 ? QuickReviewRound.RETRY : QuickReviewRound.INITIAL);
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
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                studyPackIdRaw,
                "STUDY_PACK_NOT_FOUND",
                "Study pack not found.",
                HttpStatus.NOT_FOUND
        );
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

    @Transactional(readOnly = true)
    public QuickReviewPerformanceSummaryResponse getPerformanceSummary(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                studyPackIdRaw,
                "STUDY_PACK_NOT_FOUND",
                "Study pack not found.",
                HttpStatus.NOT_FOUND
        );
        studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));

        long attempts = quickReviewSessionRepository.countByUserIdAndStudyPackIdAndCompletedAtIsNotNull(userId, studyPackId);
        if (attempts == 0) {
            return new QuickReviewPerformanceSummaryResponse(null, 0L, null, null);
        }

        BigDecimal bestScore = quickReviewSessionRepository.findBestScorePercentageByUserIdAndStudyPackId(userId, studyPackId);
        QuickReviewSessionEntity latest = quickReviewSessionRepository.findByUserIdAndStudyPackIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        PageRequest.of(0, 1)
                ).stream()
                .findFirst()
                .orElse(null);

        return new QuickReviewPerformanceSummaryResponse(
                bestScore,
                attempts,
                latest == null ? null : latest.getScorePercentage(),
                latest == null ? null : latest.getCompletedAt()
        );
    }

    private QuickReviewSessionResponse toResponse(QuickReviewSessionEntity session) {
        return new QuickReviewSessionResponse(
                session.getId().toString(),
                session.getStudyPackId().toString(),
                session.getStatus(),
                session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex(),
                session.getCurrentRound(),
                session.getTotalQuestions() == null ? 0 : session.getTotalQuestions(),
                session.getCorrectAnswers() == null ? 0 : session.getCorrectAnswers(),
                session.getScorePercentage() == null ? BigDecimal.ZERO : session.getScorePercentage(),
                session.getRetryCount() == null ? 0 : session.getRetryCount(),
                session.getDurationSeconds(),
                session.getSessionState(),
                session.getCreatedAt(),
                session.getCompletedAt()
        );
    }

    private QuickReviewSessionStartResponse toStartResponse(QuickReviewSessionEntity session) {
        return new QuickReviewSessionStartResponse(
                session.getId().toString(),
                session.getStatus(),
                session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex(),
                session.getCurrentRound(),
                session.getRetryCount() == null ? 0 : session.getRetryCount(),
                session.getSessionState()
        );
    }
}
