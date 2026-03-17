package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ChallengeQuizCompleteRequest;
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
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Transactional
@RequiredArgsConstructor
public class ChallengeQuizService {
    private static final int MIN_QUESTION_COUNT = 10;
    private static final int MAX_QUESTION_COUNT = 20;
    private static final int DEFAULT_TIME_LIMIT_SECONDS = 600;

    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
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

        int usedThisMonth = assertChallengeQuizQuotaAvailable(userId);
        List<QuizItem> challengeQuiz = buildChallengeQuiz(studyPack.getQuiz());

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
        session.setSessionState(Map.of("timeLimitSeconds", DEFAULT_TIME_LIMIT_SECONDS));
        session.setCreatedAt(OffsetDateTime.now());
        session.setCompletedAt(null);

        QuickReviewSessionEntity saved = quickReviewSessionRepository.save(session);
        int limit = properties.getPricing().getPremiumMonthlyChallengeQuizLimit();
        return new ChallengeQuizStartResponse(
                saved.getId().toString(),
                studyPack.getId().toString(),
                studyPack.getTitle(),
                challengeQuiz.size(),
                DEFAULT_TIME_LIMIT_SECONDS,
                usedThisMonth + 1,
                limit,
                challengeQuiz
        );
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
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(request.totalQuestions());
        session.setCorrectAnswers(request.correctAnswers());
        session.setScorePercentage(scorePercentage);
        session.setRetryCount(0);
        session.setDurationSeconds(request.durationSeconds());
        session.setCompletedAt(OffsetDateTime.now());

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
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime monthStart = now.withDayOfMonth(1).toLocalDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime nextMonthStart = monthStart.plusMonths(1);

        int monthlyLimit = properties.getPricing().getPremiumMonthlyChallengeQuizLimit();
        long usedThisMonth = quickReviewSessionRepository.countByUserIdAndSessionModeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId,
                QuickReviewSessionMode.CHALLENGE,
                monthStart,
                nextMonthStart
        );
        if (usedThisMonth < monthlyLimit) {
            return (int) usedThisMonth;
        }

        throw new AppException(
                "MONTHLY_CHALLENGE_QUIZ_LIMIT_REACHED",
                "You've reached your monthly Challenge Quiz limit.",
                HttpStatus.FORBIDDEN
        );
    }

    private List<QuizItem> buildChallengeQuiz(List<QuizItem> sourceQuiz) {
        List<QuizItem> normalizedSource = normalizeSourceQuiz(sourceQuiz);
        if (normalizedSource.isEmpty()) {
            throw new AppException(
                    "CHALLENGE_QUIZ_NOT_AVAILABLE",
                    "This Study Pack does not have quiz questions yet.",
                    HttpStatus.BAD_REQUEST
            );
        }

        int targetCount = Math.min(MAX_QUESTION_COUNT, Math.max(MIN_QUESTION_COUNT, normalizedSource.size()));
        List<QuizItem> generated = new ArrayList<>(targetCount);

        while (generated.size() < targetCount) {
            List<QuizItem> cycle = new ArrayList<>(normalizedSource);
            Collections.shuffle(cycle, ThreadLocalRandom.current());
            for (QuizItem quizItem : cycle) {
                generated.add(withShuffledChoices(quizItem));
                if (generated.size() >= targetCount) {
                    break;
                }
            }
        }

        return generated;
    }

    private List<QuizItem> normalizeSourceQuiz(List<QuizItem> sourceQuiz) {
        if (sourceQuiz == null || sourceQuiz.isEmpty()) {
            return List.of();
        }

        List<QuizItem> normalized = new ArrayList<>();
        for (QuizItem item : sourceQuiz) {
            if (item == null) {
                continue;
            }
            String question = normalizeString(item.question());
            String answer = normalizeString(item.answer());
            String explanation = normalizeString(item.explanation());
            if (question == null || answer == null || explanation == null) {
                continue;
            }

            List<String> choices = new ArrayList<>();
            if (item.choices() != null) {
                for (String choice : item.choices()) {
                    String normalizedChoice = normalizeString(choice);
                    if (normalizedChoice != null && !choices.contains(normalizedChoice)) {
                        choices.add(normalizedChoice);
                    }
                }
            }
            if (!choices.contains(answer)) {
                choices.add(answer);
            }
            if (choices.size() < 2) {
                continue;
            }

            normalized.add(new QuizItem(question, choices, answer, normalizeString(item.concept()), explanation));
        }
        return normalized;
    }

    private QuizItem withShuffledChoices(QuizItem quizItem) {
        List<String> shuffledChoices = new ArrayList<>(quizItem.choices());
        Collections.shuffle(shuffledChoices, ThreadLocalRandom.current());
        return new QuizItem(
                quizItem.question(),
                shuffledChoices,
                quizItem.answer(),
                quizItem.concept(),
                quizItem.explanation()
        );
    }

    private String normalizeString(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
