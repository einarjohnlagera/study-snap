package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ChallengeQuizCompleteRequest;
import com.studysnap.backend.dto.ChallengeQuizProgressRequest;
import com.studysnap.backend.dto.ChallengeQuizSessionResponse;
import com.studysnap.backend.dto.ChallengeQuizStartResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.AppException;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class ChallengeQuizService {
    private static final String SESSION_STATE_TIME_LIMIT_SECONDS = "timeLimitSeconds";
    private static final String SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS = "timerStartedAtEpochSeconds";
    private static final String SESSION_STATE_SELECTED_CHOICES = "selectedChoices";
    private static final String SESSION_STATE_COMPLETED = "completed";
    private static final int LOW_SCORE_QUESTION_COUNT = 10;
    private static final int MID_SCORE_QUESTION_COUNT = 12;
    private static final int HIGH_SCORE_QUESTION_COUNT = 15;
    private static final int DEFAULT_TIME_LIMIT_SECONDS = 600;

    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final LlmStudyPackService llmStudyPackService;
    private final FeatureGateService featureGateService;
    private final StudySnapProperties properties;

    public ChallengeQuizStartResponse startSession(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                studyPackIdRaw,
                "STUDY_PACK_NOT_FOUND",
                "Study pack not found.",
                HttpStatus.NOT_FOUND
        );
        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));
        featureGateService.checkFeatureAccess(userId, Feature.CHALLENGE_QUIZ);

        QuickReviewSessionEntity existing = quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.CHALLENGE,
                        QuickReviewSessionStatus.IN_PROGRESS
                )
                .orElse(null);
        if (existing != null) {
            List<QuizItem> existingQuiz = QuizSessionStateUtils.extractQuiz(existing.getSessionState());
            if (!existingQuiz.isEmpty()) {
                int usedThisMonth = (int) countChallengeQuizUsedThisMonth(userId);
                return toStartResponse(existing, studyPack, usedThisMonth);
            }
            existing.setStatus(QuickReviewSessionStatus.COMPLETED);
            existing.setCompletedAt(OffsetDateTime.now());
            quickReviewSessionRepository.save(existing);
        }

        int usedThisMonth = assertChallengeQuizQuotaAvailable(userId);
        ChallengeGenerationProfile profile = resolveGenerationProfile(userId, studyPackId);
        List<String> disallowedQuestions = extractQuestionTexts(studyPack.getQuiz());
        List<QuizItem> generatedQuiz = llmStudyPackService.generateChallengeQuiz(
                studyPack.getTitle(),
                studyPack.getSummary(),
                studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts(),
                disallowedQuestions,
                profile.questionCount(),
                profile.difficulty()
        );
        List<QuizItem> challengeQuiz = QuizDeduplicationUtils.uniqueQuestions(
                generatedQuiz,
                QuizDeduplicationUtils.toNormalizedQuestionSetFromStrings(disallowedQuestions)
        );
        if (challengeQuiz.size() != profile.questionCount()) {
            throw new AppException(
                    "CHALLENGE_QUIZ_GENERATION_FAILED",
                    "Could not generate enough unique challenge questions. Please try again.",
                    HttpStatus.BAD_GATEWAY
            );
        }

        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPackId);
        session.setSessionMode(QuickReviewSessionMode.CHALLENGE);
        session.setStatus(QuickReviewSessionStatus.IN_PROGRESS);
        session.setCurrentQuestionIndex(0);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(challengeQuiz.size());
        session.setCorrectAnswers(0);
        session.setScorePercentage(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        session.setRetryCount(0);
        session.setSessionMetadata(null);
        session.setSessionState(QuizSessionStateUtils.withQuiz(
                challengeQuiz,
                buildInitialSessionState(profile.difficulty())
        ));
        session.setCreatedAt(OffsetDateTime.now());
        session.setCompletedAt(null);

        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        return toStartResponse(saved, studyPack, usedThisMonth + 1);
    }

    @Transactional(readOnly = true)
    public ChallengeQuizStartResponse getInProgressSession(String studyPackIdRaw, UUID userId) {
        UUID studyPackId = UuidParsingUtils.parseUuidOrThrow(
                studyPackIdRaw,
                "STUDY_PACK_NOT_FOUND",
                "Study pack not found.",
                HttpStatus.NOT_FOUND
        );
        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));

        int usedThisMonth = (int) countChallengeQuizUsedThisMonth(userId);
        return quickReviewSessionRepository
                .findTopByUserIdAndStudyPackIdAndSessionModeAndStatusOrderByCreatedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.CHALLENGE,
                        QuickReviewSessionStatus.IN_PROGRESS
                )
                .map(session -> toStartResponse(session, studyPack, usedThisMonth))
                .orElse(new ChallengeQuizStartResponse(
                        null,
                        studyPack.getId().toString(),
                        studyPack.getTitle(),
                        0,
                        DEFAULT_TIME_LIMIT_SECONDS,
                        usedThisMonth,
                        properties.getPricing().getPremiumMonthlyChallengeQuizLimit(),
                        List.of(),
                        0,
                        null
                ));
    }

    public ChallengeQuizStartResponse updateSessionProgress(
            String sessionIdRaw,
            UUID userId,
            ChallengeQuizProgressRequest request
    ) {
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(
                sessionIdRaw,
                "SESSION_NOT_FOUND",
                "Challenge Quiz session not found.",
                HttpStatus.NOT_FOUND
        );
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.CHALLENGE
                )
                .orElseThrow(() -> new AppException("SESSION_NOT_FOUND", "Challenge Quiz session not found.", HttpStatus.NOT_FOUND));

        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            throw new AppException(
                    "SESSION_NOT_IN_PROGRESS",
                    "Challenge Quiz session is already completed.",
                    HttpStatus.BAD_REQUEST
            );
        }

        int totalQuestions = session.getTotalQuestions() == null ? 0 : session.getTotalQuestions();
        int normalizedIndex = Math.max(0, Math.min(request.currentQuestionIndex(), Math.max(0, totalQuestions - 1)));
        session.setCurrentQuestionIndex(normalizedIndex);
        session.setSessionState(mergeSessionState(session.getSessionState(), request.sessionState()));
        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);

        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserId(saved.getStudyPackId(), userId)
                .orElseThrow(() -> new AppException("STUDY_PACK_NOT_FOUND", "Study pack not found.", HttpStatus.NOT_FOUND));
        int usedThisMonth = (int) countChallengeQuizUsedThisMonth(userId);
        return toStartResponse(saved, studyPack, usedThisMonth);
    }

    public ChallengeQuizSessionResponse completeSession(String sessionIdRaw, UUID userId, ChallengeQuizCompleteRequest request) {
        UUID sessionId = UuidParsingUtils.parseUuidOrThrow(
                sessionIdRaw,
                "SESSION_NOT_FOUND",
                "Challenge Quiz session not found.",
                HttpStatus.NOT_FOUND
        );
        QuickReviewSessionEntity session = quickReviewSessionRepository.findByIdAndUserIdAndSessionMode(
                        sessionId,
                        userId,
                        QuickReviewSessionMode.CHALLENGE
                )
                .orElseThrow(() -> new AppException("SESSION_NOT_FOUND", "Challenge Quiz session not found.", HttpStatus.NOT_FOUND));

        if (session.getStatus() != QuickReviewSessionStatus.IN_PROGRESS) {
            throw new AppException(
                    "SESSION_NOT_IN_PROGRESS",
                    "Challenge Quiz session is already completed.",
                    HttpStatus.BAD_REQUEST
            );
        }
        int totalQuestions = session.getTotalQuestions() == null ? request.totalQuestions() : session.getTotalQuestions();
        if (request.correctAnswers() > totalQuestions) {
            throw new AppException(
                    "INVALID_SESSION_RESULT",
                    "Correct answers cannot exceed total questions.",
                    HttpStatus.BAD_REQUEST
            );
        }

        BigDecimal scorePercentage = BigDecimal.valueOf(request.correctAnswers())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalQuestions), 2, RoundingMode.HALF_UP);

        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(totalQuestions);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(totalQuestions);
        session.setCorrectAnswers(request.correctAnswers());
        session.setScorePercentage(scorePercentage);
        session.setRetryCount(0);
        session.setDurationSeconds(request.durationSeconds());
        session.setCompletedAt(OffsetDateTime.now());
        session.setSessionState(markSessionStateCompleted(session.getSessionState()));

        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        return new ChallengeQuizSessionResponse(
                saved.getId().toString(),
                saved.getStudyPackId().toString(),
                saved.getStatus(),
                saved.getTotalQuestions() == null ? 0 : saved.getTotalQuestions(),
                saved.getCorrectAnswers() == null ? 0 : saved.getCorrectAnswers(),
                saved.getScorePercentage() == null ? BigDecimal.ZERO : saved.getScorePercentage(),
                saved.getDurationSeconds(),
                saved.getCreatedAt(),
                saved.getCompletedAt()
        );
    }

    private int assertChallengeQuizQuotaAvailable(UUID userId) {
        long usedThisMonth = countChallengeQuizUsedThisMonth(userId);
        int monthlyLimit = properties.getPricing().getPremiumMonthlyChallengeQuizLimit();
        if (usedThisMonth < monthlyLimit) {
            return (int) usedThisMonth;
        }

        throw new AppException(
                "MONTHLY_CHALLENGE_QUIZ_LIMIT_REACHED",
                "You've reached your monthly Challenge Quiz limit.",
                HttpStatus.FORBIDDEN
        );
    }

    private long countChallengeQuizUsedThisMonth(UUID userId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime monthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime nextMonthStart = monthStart.plusMonths(1);
        return quickReviewSessionRepository.countByUserIdAndSessionModeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                QuickReviewSessionMode.CHALLENGE,
                monthStart,
                nextMonthStart
        );
    }

    private ChallengeGenerationProfile resolveGenerationProfile(UUID userId, UUID studyPackId) {
        QuickReviewSessionEntity latestQuickReview = quickReviewSessionRepository
                .findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        PageRequest.of(0, 1)
                )
                .stream()
                .findFirst()
                .orElse(null);
        BigDecimal previousScore = latestQuickReview == null ? null : latestQuickReview.getScorePercentage();
        if (previousScore == null) {
            return new ChallengeGenerationProfile(MID_SCORE_QUESTION_COUNT, "medium");
        }

        if (previousScore.compareTo(BigDecimal.valueOf(50)) < 0) {
            return new ChallengeGenerationProfile(LOW_SCORE_QUESTION_COUNT, "easy-medium");
        }
        if (previousScore.compareTo(BigDecimal.valueOf(80)) < 0) {
            return new ChallengeGenerationProfile(MID_SCORE_QUESTION_COUNT, "medium");
        }
        return new ChallengeGenerationProfile(HIGH_SCORE_QUESTION_COUNT, "medium-hard");
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

    private ChallengeQuizStartResponse toStartResponse(QuickReviewSessionEntity session, StudyPackEntity studyPack, int usedThisMonth) {
        List<QuizItem> quiz = QuizSessionStateUtils.extractQuiz(session.getSessionState());
        if (quiz.isEmpty()) {
            throw new AppException(
                    "CHALLENGE_QUIZ_NOT_AVAILABLE",
                    "Challenge Quiz session is not available. Please start again.",
                    HttpStatus.BAD_REQUEST
            );
        }
        int timeLimitSeconds = extractTimeLimitSeconds(session.getSessionState());
        int limit = properties.getPricing().getPremiumMonthlyChallengeQuizLimit();
        return new ChallengeQuizStartResponse(
                session.getId().toString(),
                studyPack.getId().toString(),
                studyPack.getTitle(),
                quiz.size(),
                timeLimitSeconds,
                usedThisMonth,
                limit,
                quiz,
                session.getCurrentQuestionIndex() == null ? 0 : session.getCurrentQuestionIndex(),
                sanitizeSessionStateForClient(session.getSessionState())
        );
    }

    private Map<String, Object> buildInitialSessionState(String difficulty) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put(SESSION_STATE_TIME_LIMIT_SECONDS, DEFAULT_TIME_LIMIT_SECONDS);
        state.put(SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS, OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond());
        state.put(SESSION_STATE_SELECTED_CHOICES, Map.of());
        state.put(SESSION_STATE_COMPLETED, false);
        state.put("difficulty", difficulty);
        return state;
    }

    private int extractTimeLimitSeconds(Map<String, Object> sessionState) {
        if (sessionState == null) {
            return DEFAULT_TIME_LIMIT_SECONDS;
        }
        Object raw = sessionState.get(SESSION_STATE_TIME_LIMIT_SECONDS);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        return DEFAULT_TIME_LIMIT_SECONDS;
    }

    private Map<String, Object> sanitizeSessionStateForClient(Map<String, Object> sessionState) {
        if (sessionState == null || sessionState.isEmpty()) {
            return null;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>(sessionState);
        sanitized.remove("quiz");
        return sanitized;
    }

    private Map<String, Object> mergeSessionState(
            Map<String, Object> existingState,
            Map<String, Object> incomingState
    ) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (existingState != null && !existingState.isEmpty()) {
            merged.putAll(existingState);
        }
        if (incomingState != null && !incomingState.isEmpty()) {
            Object selectedChoices = incomingState.get(SESSION_STATE_SELECTED_CHOICES);
            if (selectedChoices instanceof Map<?, ?>) {
                merged.put(SESSION_STATE_SELECTED_CHOICES, selectedChoices);
            }
        }
        if (!merged.containsKey(SESSION_STATE_SELECTED_CHOICES)) {
            merged.put(SESSION_STATE_SELECTED_CHOICES, Map.of());
        }
        if (!merged.containsKey(SESSION_STATE_TIME_LIMIT_SECONDS)) {
            merged.put(SESSION_STATE_TIME_LIMIT_SECONDS, DEFAULT_TIME_LIMIT_SECONDS);
        }
        if (!merged.containsKey(SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS)) {
            merged.put(SESSION_STATE_TIMER_STARTED_AT_EPOCH_SECONDS, OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond());
        }
        if (!merged.containsKey(SESSION_STATE_COMPLETED)) {
            merged.put(SESSION_STATE_COMPLETED, false);
        }
        return merged;
    }

    private Map<String, Object> markSessionStateCompleted(Map<String, Object> existingState) {
        Map<String, Object> nextState = new LinkedHashMap<>();
        if (existingState != null && !existingState.isEmpty()) {
            nextState.putAll(existingState);
        }
        nextState.put(SESSION_STATE_COMPLETED, true);
        return nextState;
    }

    private record ChallengeGenerationProfile(int questionCount, String difficulty) {
    }
}
