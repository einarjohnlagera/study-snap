package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.QuickReviewAdaptiveQuizResponse;
import com.studysnap.backend.dto.QuizItem;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.Feature;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.util.UuidParsingUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private static final int MIN_QUESTION_COUNT = 3;
    private static final int MAX_QUESTION_COUNT = 5;

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
        assertAdaptivePracticeQuotaAvailable(userId);

        QuickReviewSessionEntity latestCompletedSession = quickReviewSessionRepository
                .findByUserIdAndStudyPackIdAndSessionModeAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        QuickReviewSessionMode.QUICK_REVIEW,
                        PageRequest.of(0, 1)
                ).stream()
                .findFirst()
                .orElse(null);

        if (latestCompletedSession == null) {
            return new QuickReviewAdaptiveQuizResponse(
                    studyPack.getId().toString(),
                    studyPack.getTitle(),
                    List.of(),
                    List.of(),
                    "Complete a Quick Review first to unlock adaptive practice."
            );
        }

        List<String> weakConcepts = extractWeakConcepts(latestCompletedSession);
        if (weakConcepts.isEmpty()) {
            return new QuickReviewAdaptiveQuizResponse(
                    studyPack.getId().toString(),
                    studyPack.getTitle(),
                    List.of(),
                    List.of(),
                    "No weak concepts found from your latest Quick Review."
            );
        }

        int questionCount = Math.max(MIN_QUESTION_COUNT, Math.min(MAX_QUESTION_COUNT, weakConcepts.size()));
        List<QuizItem> adaptiveQuiz = llmStudyPackService.generateAdaptivePracticeQuiz(
                studyPack.getTitle(),
                studyPack.getSummary(),
                studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts(),
                weakConcepts,
                questionCount
        );
        activityTrackingService.recordActivity(userId, ActivityType.STARTED_ADAPTIVE_PRACTICE, studyPackId);

        return new QuickReviewAdaptiveQuizResponse(
                studyPack.getId().toString(),
                studyPack.getTitle(),
                weakConcepts,
                adaptiveQuiz,
                "Generated from weak concepts in your latest Quick Review."
        );
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
}
