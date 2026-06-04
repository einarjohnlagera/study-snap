package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.NextStepResponse;
import com.studysnap.backend.dto.TodayFocusType;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.exception.StudyPackNotFoundException;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostSessionNextStepService {
    private static final int CONCEPT_LIMIT = 5;
    private static final int FIRST_PAGE = 0;
    private static final String SESSION_METADATA_WEAK_CONCEPTS = "weakConcepts";
    private static final String ADAPTIVE_PRACTICE_PATH = "/notes/%s/adaptive-practice";
    private static final String QUICK_REVIEW_PATH = "/notes/%s/quick-review";
    private static final String CHALLENGE_QUIZ_PATH = "/notes/%s/challenge-quiz";
    private static final String FALLBACK_PATH = "/library";

    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final ConceptHealthService conceptHealthService;
    private final SubscriptionService subscriptionService;
    private final UserUsageService userUsageService;
    private final StudySnapProperties properties;

    @Transactional(readOnly = true)
    public NextStepResponse getNextStep(UUID userId, UUID studyPackId) {
        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(StudyPackNotFoundException::new);

        PlanType planType = resolvePlan(userId);
        AdaptivePracticeQuota adaptivePracticeQuota = resolveAdaptivePracticeQuota(userId, planType);
        try {
            return resolveNextStep(userId, studyPack, adaptivePracticeQuota);
        } catch (RuntimeException ex) {
            log.warn(
                    "post_session_next_step_fallback userId={} studyPackId={} reason={}",
                    userId,
                    studyPackId,
                    ex.getMessage()
            );
            return reviewPackResponse(studyPack, adaptivePracticeQuota);
        }
    }

    private NextStepResponse resolveNextStep(
            UUID userId,
            StudyPackEntity studyPack,
            AdaptivePracticeQuota adaptivePracticeQuota
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<String> dueConcepts = capConcepts(conceptHealthService.getDueConcepts(
                userId,
                studyPack.getId(),
                getKeyConcepts(studyPack),
                now
        ));
        if (!dueConcepts.isEmpty()) {
            return practiceWeakConceptResponse(studyPack, dueConcepts, adaptivePracticeQuota);
        }

        QuickReviewSessionEntity latestCompletedSession = findLatestCompletedSession(userId, studyPack.getId());
        List<String> weakConcepts = capConcepts(extractWeakConcepts(latestCompletedSession));
        if (!weakConcepts.isEmpty()
                && latestCompletedSession != null
                && latestCompletedSession.getSessionMode() == QuickReviewSessionMode.QUICK_REVIEW) {
            return retryReviewResponse(studyPack, weakConcepts, adaptivePracticeQuota);
        }

        return reviewPackResponse(studyPack, adaptivePracticeQuota);
    }

    private QuickReviewSessionEntity findLatestCompletedSession(UUID userId, UUID studyPackId) {
        return quickReviewSessionRepository.findByUserIdAndStudyPackIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                        userId,
                        studyPackId,
                        PageRequest.of(FIRST_PAGE, 1)
                )
                .stream()
                .findFirst()
                .orElse(null);
    }

    private NextStepResponse practiceWeakConceptResponse(
            StudyPackEntity studyPack,
            List<String> dueConcepts,
            AdaptivePracticeQuota adaptivePracticeQuota
    ) {
        int conceptCount = dueConcepts.size();
        String conceptLabel = conceptCount == 1 ? "concept is" : "concepts are";
        return new NextStepResponse(
                TodayFocusType.PRACTICE_WEAK_CONCEPT,
                studyPack.getId().toString(),
                stringify(studyPack.getNoteId()),
                studyPack.getTitle(),
                conceptCount + " " + conceptLabel + " due for review. Practice them while they are fresh.",
                "Practice Weak Concepts",
                pathOrFallback(studyPack.getNoteId(), ADAPTIVE_PRACTICE_PATH),
                dueConcepts,
                adaptivePracticeQuota.available(),
                adaptivePracticeQuota.remaining()
        );
    }

    private NextStepResponse retryReviewResponse(
            StudyPackEntity studyPack,
            List<String> weakConcepts,
            AdaptivePracticeQuota adaptivePracticeQuota
    ) {
        return new NextStepResponse(
                TodayFocusType.RETRY_REVIEW,
                studyPack.getId().toString(),
                stringify(studyPack.getNoteId()),
                studyPack.getTitle(),
                "You have missed questions from this Quick Review ready to retry.",
                "Retry Incorrect Questions",
                pathOrFallback(studyPack.getNoteId(), QUICK_REVIEW_PATH),
                weakConcepts,
                adaptivePracticeQuota.available(),
                adaptivePracticeQuota.remaining()
        );
    }

    private NextStepResponse reviewPackResponse(
            StudyPackEntity studyPack,
            AdaptivePracticeQuota adaptivePracticeQuota
    ) {
        UUID noteId = studyPack.getNoteId();
        return new NextStepResponse(
                TodayFocusType.REVIEW_PACK,
                studyPack.getId().toString(),
                stringify(noteId),
                studyPack.getTitle(),
                "You are in good shape here. Step up with a challenge or review the note when ready.",
                noteId == null ? "Review Note" : "Take a Challenge",
                noteId == null ? FALLBACK_PATH : String.format(CHALLENGE_QUIZ_PATH, noteId),
                List.of(),
                adaptivePracticeQuota.available(),
                adaptivePracticeQuota.remaining()
        );
    }

    private AdaptivePracticeQuota resolveAdaptivePracticeQuota(UUID userId, PlanType planType) {
        int monthlyLimit = properties.getPricing().resolveMonthlyAdaptivePracticeLimit(planType);
        boolean available = monthlyLimit > 0;
        if (planType == PlanType.PRO) {
            return new AdaptivePracticeQuota(available, null);
        }
        int used = userUsageService.getMonthlyUsage(userId, OffsetDateTime.now(ZoneOffset.UTC)).adaptiveQuizGenerations();
        return new AdaptivePracticeQuota(available, Math.max(0, monthlyLimit - used));
    }

    private PlanType resolvePlan(UUID userId) {
        PlanType planType = subscriptionService.resolvePlan(userId);
        return planType == null ? PlanType.FREE : planType;
    }

    private List<String> extractWeakConcepts(QuickReviewSessionEntity session) {
        if (session == null || session.getSessionMetadata() == null) {
            return List.of();
        }
        Object weakConceptsRaw = session.getSessionMetadata().get(SESSION_METADATA_WEAK_CONCEPTS);
        if (!(weakConceptsRaw instanceof List<?> weakConceptsList)) {
            return List.of();
        }

        List<String> weakConcepts = new ArrayList<>();
        for (Object value : weakConceptsList) {
            if (!(value instanceof String concept)) {
                continue;
            }
            String normalized = concept.trim();
            if (!normalized.isBlank()) {
                weakConcepts.add(normalized);
            }
        }
        return weakConcepts;
    }

    private List<String> capConcepts(List<String> concepts) {
        if (concepts == null || concepts.isEmpty()) {
            return List.of();
        }
        return concepts.stream()
                .filter(concept -> concept != null && !concept.isBlank())
                .map(String::trim)
                .limit(CONCEPT_LIMIT)
                .toList();
    }

    private List<String> getKeyConcepts(StudyPackEntity studyPack) {
        return studyPack.getKeyConcepts() == null ? List.of() : studyPack.getKeyConcepts();
    }

    private String pathOrFallback(UUID noteId, String pattern) {
        return noteId == null ? FALLBACK_PATH : String.format(pattern, noteId);
    }

    private String stringify(UUID value) {
        return value == null ? null : value.toString();
    }

    private record AdaptivePracticeQuota(boolean available, Integer remaining) {
    }
}
