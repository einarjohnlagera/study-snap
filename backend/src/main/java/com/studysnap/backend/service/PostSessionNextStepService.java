package com.studysnap.backend.service;

import com.studysnap.backend.config.ExamGoalConfig;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ConceptHealthEntryResponse;
import com.studysnap.backend.dto.GoalNudgeResponse;
import com.studysnap.backend.dto.NextStepResponse;
import com.studysnap.backend.dto.NextStepSecondaryActionResponse;
import com.studysnap.backend.dto.TodayFocusType;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.StudyPackNotFoundException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
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
    private static final String TAKE_CHALLENGE_LABEL = "Take a Challenge";
    private static final String PRACTICE_WEAK_CONCEPTS_LABEL = "Practice Weak Concepts";

    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final ConceptHealthService conceptHealthService;
    private final SubscriptionService subscriptionService;
    private final UserUsageService userUsageService;
    private final StudySnapProperties properties;
    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final ProgressReportService progressReportService;

    @Transactional(readOnly = true)
    public NextStepResponse getNextStep(UUID userId, UUID studyPackId) {
        StudyPackEntity studyPack = studyPackRepository.findByIdAndOwnerUserId(studyPackId, userId)
                .orElseThrow(StudyPackNotFoundException::new);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        PlanType planType = resolvePlan(userId);
        AdaptivePracticeQuota adaptivePracticeQuota = resolveAdaptivePracticeQuota(userId, planType);
        GoalNudgeResponse goalNudge = resolveGoalNudge(user, studyPack);
        try {
            return resolveNextStep(userId, studyPack, adaptivePracticeQuota, goalNudge);
        } catch (RuntimeException ex) {
            log.warn(
                    "post_session_next_step_fallback userId={} studyPackId={} reason={}",
                    userId,
                    studyPackId,
                    ex.getMessage()
            );
            return reviewPackResponse(studyPack, adaptivePracticeQuota, null);
        }
    }

    private NextStepResponse resolveNextStep(
            UUID userId,
            StudyPackEntity studyPack,
            AdaptivePracticeQuota adaptivePracticeQuota,
            GoalNudgeResponse goalNudge
    ) {
        QuickReviewSessionEntity latestCompletedSession = findLatestCompletedSession(userId, studyPack.getId());
        if (latestCompletedSession == null) {
            return reviewPackResponse(studyPack, adaptivePracticeQuota, goalNudge);
        }

        List<ConceptHealthEntryResponse> conceptHealth = conceptHealthService.getConceptHealth(
                userId,
                studyPack.getId(),
                getKeyConcepts(studyPack),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        List<String> sessionMisses = capConcepts(extractWeakConcepts(latestCompletedSession));
        List<String> genuineWeakConcepts = resolveGenuineWeakConcepts(conceptHealth, latestCompletedSession);

        return switch (latestCompletedSession.getSessionMode()) {
            case QUICK_REVIEW -> resolveQuickReviewNextStep(
                    studyPack,
                    sessionMisses,
                    genuineWeakConcepts,
                    adaptivePracticeQuota,
                    goalNudge
            );
            case CHALLENGE -> genuineWeakConcepts.isEmpty()
                    ? reviewPackResponse(studyPack, adaptivePracticeQuota, goalNudge)
                    : practiceWeakConceptResponse(
                            studyPack,
                            genuineWeakConcepts,
                            adaptivePracticeQuota,
                            goalNudge
                    );
            case ADAPTIVE -> reviewPackResponse(
                    studyPack,
                    adaptivePracticeQuota,
                    goalNudge,
                    genuineWeakConcepts,
                    "Targeted practice complete. Step up with a Challenge when you are ready.",
                    null
            );
            default -> reviewPackResponse(studyPack, adaptivePracticeQuota, goalNudge);
        };
    }

    private NextStepResponse resolveQuickReviewNextStep(
            StudyPackEntity studyPack,
            List<String> sessionMisses,
            List<String> genuineWeakConcepts,
            AdaptivePracticeQuota adaptivePracticeQuota,
            GoalNudgeResponse goalNudge
    ) {
        if (!sessionMisses.isEmpty()) {
            return retryReviewResponse(
                    studyPack,
                    sessionMisses,
                    adaptivePracticeQuota,
                    goalNudge,
                    challengeSecondaryAction(studyPack)
            );
        }

        NextStepSecondaryActionResponse secondaryAction = genuineWeakConcepts.isEmpty()
                ? null
                : adaptivePracticeSecondaryAction(studyPack);
        return reviewPackResponse(
                studyPack,
                adaptivePracticeQuota,
                goalNudge,
                genuineWeakConcepts,
                genuineWeakConcepts.isEmpty()
                        ? "Strong Quick Review. Step up with a Challenge next."
                        : "Strong Quick Review. Step up with a Challenge, with targeted review still available below.",
                secondaryAction
        );
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
            AdaptivePracticeQuota adaptivePracticeQuota,
            GoalNudgeResponse goalNudge
    ) {
        int conceptCount = dueConcepts.size();
        String conceptLabel = conceptCount == 1 ? "concept is" : "concepts are";
        return new NextStepResponse(
                TodayFocusType.PRACTICE_WEAK_CONCEPT,
                studyPack.getId().toString(),
                stringify(studyPack.getNoteId()),
                studyPack.getTitle(),
                conceptCount + " " + conceptLabel + " due for review. Practice them while they are fresh.",
                PRACTICE_WEAK_CONCEPTS_LABEL,
                pathOrFallback(studyPack.getNoteId(), ADAPTIVE_PRACTICE_PATH),
                dueConcepts,
                adaptivePracticeQuota.available(),
                adaptivePracticeQuota.remaining(),
                goalNudge,
                null
        );
    }

    private NextStepResponse retryReviewResponse(
            StudyPackEntity studyPack,
            List<String> weakConcepts,
            AdaptivePracticeQuota adaptivePracticeQuota,
            GoalNudgeResponse goalNudge,
            NextStepSecondaryActionResponse secondaryAction
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
                adaptivePracticeQuota.remaining(),
                goalNudge,
                secondaryAction
        );
    }

    private NextStepResponse reviewPackResponse(
            StudyPackEntity studyPack,
            AdaptivePracticeQuota adaptivePracticeQuota,
            GoalNudgeResponse goalNudge
    ) {
        return reviewPackResponse(
                studyPack,
                adaptivePracticeQuota,
                goalNudge,
                List.of(),
                "You are in good shape here. Step up with a challenge or review the note when ready.",
                null
        );
    }

    private NextStepResponse reviewPackResponse(
            StudyPackEntity studyPack,
            AdaptivePracticeQuota adaptivePracticeQuota,
            GoalNudgeResponse goalNudge,
            List<String> concepts,
            String message,
            NextStepSecondaryActionResponse secondaryAction
    ) {
        UUID noteId = studyPack.getNoteId();
        return new NextStepResponse(
                TodayFocusType.REVIEW_PACK,
                studyPack.getId().toString(),
                stringify(noteId),
                studyPack.getTitle(),
                message,
                noteId == null ? "Review Note" : TAKE_CHALLENGE_LABEL,
                noteId == null ? FALLBACK_PATH : String.format(CHALLENGE_QUIZ_PATH, noteId),
                concepts,
                adaptivePracticeQuota.available(),
                adaptivePracticeQuota.remaining(),
                goalNudge,
                secondaryAction
        );
    }

    List<String> resolveGenuineWeakConcepts(
            List<ConceptHealthEntryResponse> conceptHealth,
            QuickReviewSessionEntity latestCompletedSession
    ) {
        LinkedHashSet<String> genuineWeakConcepts = new LinkedHashSet<>();
        if (conceptHealth != null) {
            conceptHealth.stream()
                    .filter(ConceptHealthEntryResponse::isDue)
                    .filter(entry -> entry.lastCorrectAt() != null)
                    .map(ConceptHealthEntryResponse::concept)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(concept -> !concept.isBlank())
                    .forEach(genuineWeakConcepts::add);
        }
        genuineWeakConcepts.addAll(extractWeakConcepts(latestCompletedSession));
        return capConcepts(List.copyOf(genuineWeakConcepts));
    }

    private NextStepSecondaryActionResponse challengeSecondaryAction(StudyPackEntity studyPack) {
        return new NextStepSecondaryActionResponse(
                TAKE_CHALLENGE_LABEL,
                pathOrFallback(studyPack.getNoteId(), CHALLENGE_QUIZ_PATH),
                false
        );
    }

    private NextStepSecondaryActionResponse adaptivePracticeSecondaryAction(StudyPackEntity studyPack) {
        return new NextStepSecondaryActionResponse(
                PRACTICE_WEAK_CONCEPTS_LABEL,
                pathOrFallback(studyPack.getNoteId(), ADAPTIVE_PRACTICE_PATH),
                true
        );
    }

    private GoalNudgeResponse resolveGoalNudge(UserEntity user, StudyPackEntity studyPack) {
        String studyGoal = user.getStudyGoal();
        if (studyGoal == null || studyGoal.isBlank() || studyPack.getNoteId() == null) {
            return null;
        }

        try {
            NoteEntity note = noteRepository.findByIdAndOwnerUserId(studyPack.getNoteId(), user.getId())
                    .orElse(null);
            if (note == null || note.getCourseProgram() == null || note.getCourseProgram().isBlank()) {
                return null;
            }
            if (isCurrentNoteInGoal(studyGoal, note.getCourseProgram())) {
                return null;
            }
            return progressReportService.buildGoalNudge(user.getId(), studyGoal, OffsetDateTime.now(ZoneOffset.UTC));
        } catch (RuntimeException ex) {
            log.warn(
                    "goal_nudge_unavailable userId={} studyPackId={} studyGoal={} reason={}",
                    user.getId(),
                    studyPack.getId(),
                    studyGoal,
                    ex.getMessage()
            );
            return null;
        }
    }

    private boolean isCurrentNoteInGoal(String studyGoal, String noteCourseProgram) {
        if (ExamGoalConfig.isValidSlug(studyGoal)) {
            return ExamGoalConfig.getCoursePrograms(studyGoal).stream()
                    .anyMatch(courseProgram -> Objects.equals(courseProgram, noteCourseProgram));
        }
        return Objects.equals(studyGoal, noteCourseProgram);
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
