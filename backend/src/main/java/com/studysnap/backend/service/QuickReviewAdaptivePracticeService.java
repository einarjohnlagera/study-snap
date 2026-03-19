package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuickReviewAdaptiveQuizResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.util.QuizDeduplicationUtils;
import com.studysnap.backend.util.QuizSessionStateUtils;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@Transactional
@RequiredArgsConstructor
public class QuickReviewAdaptivePracticeService {
    private static final int BASE_QUESTION_COUNT = 5;
    private static final int MID_QUESTION_COUNT = 7;
    private static final int HIGH_QUESTION_COUNT = 10;

    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final ActivityEventRepository activityEventRepository;
    private final LlmStudyPackService llmStudyPackService;
    private final ActivityTrackingService activityTrackingService;
    private final FeatureGateService featureGateService;
    private final StudySnapProperties properties;

    public QuickReviewAdaptiveQuizResponse generateAdaptiveQuiz(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                studyPackIdRaw,
                "STUDY_PACK_NOT_FOUND",
                "Study pack not found.",
                HttpStatus.NOT_FOUND
        );
        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));
        featureGateService.checkFeatureAccess(userId, Feature.ADAPTIVE_QUIZ);

        QuickReviewSessionEntity existing = quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.ADAPTIVE,
                        QuickReviewSessionStatus.IN_PROGRESS
                )
                .orElse(null);
        if (existing != null) {
            List<QuizItem> existingQuiz = QuizSessionStateUtils.extractQuiz(existing.getSessionState());
            if (!existingQuiz.isEmpty()) {
                return new QuickReviewAdaptiveQuizResponse(
                        existing.getId().toString(),
                        studyPack.getId().toString(),
                        studyPack.getTitle(),
                        extractWeakConcepts(existing),
                        existingQuiz,
                        "Focusing on concepts you need to improve."
                );
            }
            existing.setStatus(QuickReviewSessionStatus.COMPLETED);
            existing.setCompletedAt(OffsetDateTime.now());
            quickReviewSessionRepository.save(existing);
        }

        QuickReviewSessionEntity latestCompletedSession = resolveLatestAdaptiveSourceSession(userId, studyPackId);

        if (latestCompletedSession == null) {
            return new QuickReviewAdaptiveQuizResponse(
                    null,
                    studyPack.getId().toString(),
                    studyPack.getTitle(),
                    List.of(),
                    List.of(),
                    "Complete a Quick Review or Challenge Quiz first to unlock adaptive practice."
            );
        }

        List<String> weakConcepts = extractWeakConcepts(latestCompletedSession);
        if (weakConcepts.isEmpty()) {
            return new QuickReviewAdaptiveQuizResponse(
                    null,
                    studyPack.getId().toString(),
                    studyPack.getTitle(),
                    List.of(),
                    List.of(),
                    "No weak concepts found from your latest review."
            );
        }

        assertAdaptivePracticeQuotaAvailable(userId);
        int questionCount = resolveAdaptiveQuestionCount(weakConcepts.size());
        List<String> disallowedQuestions = extractQuestionTexts(studyPack.getQuiz());
        List<QuizItem> generatedQuiz = llmStudyPackService.generateAdaptivePracticeQuiz(
                studyPack.getTitle(),
                studyPack.getSummary(),
                studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts(),
                weakConcepts,
                disallowedQuestions,
                questionCount
        );
        List<QuizItem> adaptiveQuiz = QuizDeduplicationUtils.uniqueQuestions(
                generatedQuiz,
                QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(disallowedQuestions)
        );
        if (adaptiveQuiz.size() != questionCount) {
            throw new AppException(
                    "ADAPTIVE_QUIZ_GENERATION_FAILED",
                    "Could not generate enough unique adaptive questions. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        }

        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setSessionMode(QuickReviewSessionMode.ADAPTIVE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(adaptiveQuiz.size());
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        session.setRetryCount(0);
        session.setDurationSeconds(null);
        session.setSessionMetadata(java.util.Map.of("weakConcepts", weakConcepts));
        session.setSessionState(QuizSessionStateUtils.withQuiz(adaptiveQuiz, null));
        session.setCreatedAt(OffsetDateTime.now());
        session.setCompletedAt(null);
        QuickReviewSessionEntity savedSession = quickReviewSessionRepository.save(session);

        activityTrackingService.recordActivity(userId, ActivityType.STARTED_ADAPTIVE_PRACTICE, studyPackId);

        return new QuickReviewAdaptiveQuizResponse(
                savedSession.getId().toString(),
                studyPack.getId().toString(),
                studyPack.getTitle(),
                weakConcepts,
                adaptiveQuiz,
                "Focusing on concepts you need to improve."
        );
    }

    public SimpleMessageResponse completeAdaptiveSession(
            String sessionIdRaw,
            UUID userId,
            Integer correctAnswers,
            Integer totalQuestions,
            Integer durationSeconds
    ) {
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(
                sessionIdRaw,
                "SESSION_NOT_FOUND",
                "Adaptive Practice session not found.",
                HttpStatus.NOT_FOUND
        );
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.ADAPTIVE
                )
                .orElseThrow(() -> new AppException("SESSION_NOT_FOUND", "Adaptive Practice session not found.", HttpStatus.NOT_FOUND));

        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            return new SimpleMessageResponse("Adaptive Practice session already completed.");
        }

        int safeTotalQuestions = session.getTotalQuestions() == null ? (totalQuestions == null ? 0 : totalQuestions) : session.getTotalQuestions();
        int safeCorrectAnswers = correctAnswers == null ? 0 : Math.max(0, correctAnswers);
        if (safeCorrectAnswers > safeTotalQuestions) {
            throw new AppException(
                    "INVALID_SESSION_RESULT",
                    "Correct answers cannot exceed total questions.",
                    HttpStatus.BAD_REQUEST
            );
        }

        BigDecimal scorePercentage = safeTotalQuestions == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(safeCorrectAnswers)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(safeTotalQuestions), 2, RoundingMode.HALF_UP);
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(safeTotalQuestions);
        session.setTotalQuestions(safeTotalQuestions);
        session.setCorrectAnswers(safeCorrectAnswers);
        session.setScorePercentage(scorePercentage);
        session.setDurationSeconds(durationSeconds);
        session.setCompletedAt(OffsetDateTime.now());
        quickReviewSessionRepository.save(session);
        return new SimpleMessageResponse("Adaptive Practice session completed.");
    }

    private List<String> extractWeakConcepts(QuickReviewSessionEntity session) {
        if (session.getSessionMetadata() == null) {
            return List.of();
        }
        Object weakConceptsRaw = session.getSessionMetadata().get("weakConcepts");
        if (!(weakConceptsRaw instanceof List<?> rawList)) {
            return List.of();
        }

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (Object raw : rawList) {
            if (!(raw instanceof String value)) {
                continue;
            }
            String concept = value.trim();
            if (!concept.isBlank()) {
                normalized.add(concept);
            }
        }
        return new ArrayList<>(normalized);
    }

    private int resolveAdaptiveQuestionCount(int weakConceptCount) {
        if (weakConceptCount <= 2) {
            return BASE_QUESTION_COUNT;
        }
        if (weakConceptCount <= 4) {
            return MID_QUESTION_COUNT;
        }
        return HIGH_QUESTION_COUNT;
    }

    private List<String> extractQuestionTexts(List<QuizItem> quiz) {
        if (quiz == null || quiz.isEmpty()) {
            return List.of();
        }
        return quiz.stream()
                .map(QuizItem::question)
                .filter(question -> question != null && !question.isBlank())
                .toList();
    }

    private void assertAdaptivePracticeQuotaAvailable(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime monthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime nextMonthStart = monthStart.plusMonths(1);
        int monthlyLimit = properties.getPricing().getPremiumMonthlyAdaptivePracticeLimit();

        long usedThisMonth = activityEventRepository.countByUserIdAndActivityTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                ActivityType.STARTED_ADAPTIVE_PRACTICE,
                monthStart,
                nextMonthStart
        );
        if (usedThisMonth < monthlyLimit) {
            return;
        }

        throw new AppException(
                "MONTHLY_ADAPTIVE_PRACTICE_LIMIT_REACHED",
                "You've reached your monthly Adaptive Practice limit.",
                HttpStatus.FORBIDDEN
        );
    }

    private QuickReviewSessionEntity resolveLatestAdaptiveSourceSession(UUID userId, UUID studyPackId) {
        QuickReviewSessionEntity latestQuickReview = fetchLatestCompletedSession(
                userId,
                studyPackId,
                QuickReviewSessionMode.QUICK_REVIEW
        );
        QuickReviewSessionEntity latestChallenge = fetchLatestCompletedSession(
                userId,
                studyPackId,
                QuickReviewSessionMode.CHALLENGE
        );

        if (latestQuickReview == null) {
            return latestChallenge;
        }
        if (latestChallenge == null) {
            return latestQuickReview;
        }

        OffsetDateTime quickReviewCompletedAt = latestQuickReview.getCompletedAt();
        OffsetDateTime challengeCompletedAt = latestChallenge.getCompletedAt();
        if (quickReviewCompletedAt == null) {
            return latestChallenge;
        }
        if (challengeCompletedAt == null) {
            return latestQuickReview;
        }

        return challengeCompletedAt.isAfter(quickReviewCompletedAt) ? latestChallenge : latestQuickReview;
    }

    private QuickReviewSessionEntity fetchLatestCompletedSession(
            UUID userId,
            UUID studyPackId,
            QuickReviewSessionMode sessionMode
    ) {
        return quickReviewSessionRepository
                .findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        sessionMode,
                        PageRequest.of(0, 1)
                ).stream()
                .findFirst()
                .orElse(null);
    }
}
