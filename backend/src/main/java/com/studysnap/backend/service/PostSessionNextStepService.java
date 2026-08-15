package com.studysnap.backend.service;

import com.studysnap.backend.config.ExamGoalConfig;
import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ApplicableProgramResponse;
import com.studysnap.backend.dto.ConceptHealthEntryResponse;
import com.studysnap.backend.dto.GoalNudgeResponse;
import com.studysnap.backend.dto.NextStepResponse;
import com.studysnap.backend.dto.NextStepSecondaryActionResponse;
import com.studysnap.backend.dto.TodayFocusType;
import com.studysnap.backend.entity.CollectionVisibility;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.StudyPackNotFoundException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.NoteCourseProgramRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackQuizMastery;
import com.studysnap.backend.util.CourseProgramNormalizationUtils;
import com.studysnap.backend.util.NoteEffectivePrograms;
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
import java.util.Map;
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
    private static final String CHALLENGE_QUIZ_PATH = "/notes/%s/challenge-quiz";
    private static final String NOTE_DETAIL_PATH = "/notes/%s";
    private static final String EXPLORE_POST_MASTERY_PATH = "/explore?source=post-mastery";
    private static final String REDO_MISSED_CHALLENGE_QUIZ_PATH = "/notes/%s/challenge-quiz?entry=redo-missed";
    private static final String FALLBACK_PATH = "/library";
    private static final String TAKE_CHALLENGE_LABEL = "Take a Challenge";
    private static final String PRACTICE_WEAK_CONCEPTS_LABEL = "Practice Weak Concepts";
    private static final String REDO_MISSED_QUESTIONS_LABEL = "Redo Missed Questions";
    private static final String REVIEW_THE_NOTES_LABEL = "Review the Notes";
    private static final String NEXT_IN_YOUR_PLAN_LABEL = "Next in your plan";
    private static final String START_RECOMMENDED_PLAN_LABEL = "Start %s";

    private final StudyPackRepository studyPackRepository;
    private final QuickReviewSessionRepository quickReviewSessionRepository;
    private final ConceptHealthService conceptHealthService;
    private final SubscriptionService subscriptionService;
    private final UserUsageService userUsageService;
    private final StudySnapProperties properties;
    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final NoteCourseProgramRepository noteCourseProgramRepository;
    private final ProgressReportService progressReportService;
    private final ChallengeQuizQuestionBankService challengeQuizQuestionBankService;
    private final StudyPackGenerationContextResolver generationContextResolver;
    private final ExamGoalCourseProgramProvider examGoalCourseProgramProvider;
    private final StudyPackQuizMasteryService studyPackQuizMasteryService;
    private final NoteCollectionItemRepository noteCollectionItemRepository;
    private final QuizSessionHistoryService quizSessionHistoryService;
    private final NoteCollectionRepository noteCollectionRepository;

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
            LearnerLevel effectiveCurriculumLevel = StudyPackGenerationContextResolver.effectiveCurriculumLevel(
                    generationContextResolver.resolveForStudyPack(userId, studyPack)
            );
            return resolveNextStep(
                    userId,
                    user.getPrimaryCollectionId(),
                    user.getCourseProgram(),
                    effectiveCurriculumLevel,
                    studyPack,
                    adaptivePracticeQuota,
                    goalNudge
            );
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
            UUID primaryCollectionId,
            String courseProgram,
            LearnerLevel effectiveCurriculumLevel,
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
                    userId,
                    primaryCollectionId,
                    courseProgram,
                    studyPack,
                    sessionMisses,
                    adaptivePracticeQuota,
                    goalNudge
            );
            case CHALLENGE -> resolveChallengeNextStep(
                    userId,
                    effectiveCurriculumLevel,
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

    private NextStepResponse resolveChallengeNextStep(
            UUID userId,
            LearnerLevel effectiveCurriculumLevel,
            StudyPackEntity studyPack,
            List<String> genuineWeakConcepts,
            AdaptivePracticeQuota adaptivePracticeQuota,
            GoalNudgeResponse goalNudge
    ) {
        boolean hasRedoMissedQuestions = challengeQuizQuestionBankService.countEligibleIncorrectQuestions(
                userId,
                studyPack.getId(),
                effectiveCurriculumLevel
        ) >= ChallengeQuizQuestionBankService.MINIMUM_REDO_MISSED_QUESTIONS;
        if (!genuineWeakConcepts.isEmpty()) {
            return practiceWeakConceptResponse(
                    studyPack,
                    genuineWeakConcepts,
                    adaptivePracticeQuota,
                    goalNudge,
                    hasRedoMissedQuestions ? redoMissedQuestionsSecondaryAction(studyPack) : null
            );
        }
        if (hasRedoMissedQuestions) {
            return redoMissedQuestionsResponse(studyPack, adaptivePracticeQuota, goalNudge);
        }
        return reviewPackResponse(studyPack, adaptivePracticeQuota, goalNudge);
    }

    private NextStepResponse resolveQuickReviewNextStep(
            UUID userId,
            UUID primaryCollectionId,
            String courseProgram,
            StudyPackEntity studyPack,
            List<String> sessionMisses,
            AdaptivePracticeQuota adaptivePracticeQuota,
            GoalNudgeResponse goalNudge
    ) {
        StudyPackQuizMastery mastery = studyPackQuizMasteryService.resolve(userId, studyPack);
        if (!mastery.mastered()) {
            return reviewNotesResponse(
                    studyPack,
                    sessionMisses,
                    adaptivePracticeQuota,
                    goalNudge,
                    challengeSecondaryAction(studyPack)
            );
        }

        // The ACTION keys on mastery (ever), which is deliberate — the promotion and the Quiz-tab
        // unlock must read one signal. The COPY must key on THIS session, because mastery is sticky:
        // a learner who mastered the pack in August and scores 1/5 today is still routed to Challenge,
        // and telling them "Strong Quick Review" would congratulate them on a score the screen shows
        // as 1/5 directly above. The missed concepts are surfaced too, so a weak repeat session is not
        // left without remediation.
        boolean strongThisSession = sessionMisses.isEmpty();
        NextStepSecondaryActionResponse nextPlanItem = resolveNextPlanItem(
                userId,
                studyPack.getNoteId(),
                primaryCollectionId
        );
        return reviewPackResponse(
                studyPack,
                adaptivePracticeQuota,
                goalNudge,
                strongThisSession ? List.of() : sessionMisses,
                strongThisSession
                        ? "Strong Quick Review. Step up with a Challenge next."
                        : "You have already mastered this pack. Revisit the notes on these areas, or step up with a Challenge.",
                nextPlanItem != null ? nextPlanItem : resolveRecommendedPlan(userId, courseProgram)
        );
    }

    /**
     * The program-matched published plan for a learner whose mastered note is in no plan, or null.
     *
     * <p>Deliberately reads the repository directly rather than {@code NoteCollectionService.listPublic}
     * / {@code list}. Those build full summaries — item, ready, child and practiced counts across every
     * collection — and this path needs a title, an id and one adoption boolean. {@code list} in particular
     * resolves practiced counts through the multi-note session scan, which the merged plan-item branch
     * was restructured to run once per plan instead of once per page. That scan is still on this
     * request whenever the note is in a plan — it is the single definition of practiced — so the point
     * here is not to add a SECOND one for a title and an adoption boolean. The ordering matches {@code listPublic}
     * exactly (same repository method, same {@code updatedAt DESC}), so both surfaces pick the same plan.
     */
    private NextStepSecondaryActionResponse resolveRecommendedPlan(UUID userId, String courseProgram) {
        String normalizedCourseProgram = CourseProgramNormalizationUtils.normalizeForStorage(courseProgram);
        if (normalizedCourseProgram == null) {
            return null;
        }
        List<NoteCollectionEntity> publicPlans = noteCollectionRepository
                .findByVisibilityAndCourseProgramAndParentCollectionIdIsNullOrderByUpdatedAtDesc(
                        CollectionVisibility.PUBLIC,
                        normalizedCourseProgram
                );
        if (publicPlans.isEmpty()) {
            return null;
        }
        NoteCollectionEntity matchedPlan = publicPlans.getFirst();
        boolean alreadyAdopted = noteCollectionRepository
                .findByOwnerUserIdAndSourcePlanId(userId, matchedPlan.getId()).isPresent()
                || noteCollectionRepository.findByIdAndOwnerUserId(matchedPlan.getId(), userId).isPresent();
        if (alreadyAdopted) {
            return null;
        }
        // NOT /collections/{id}: that route is owner-scoped (NoteCollectionService.get ->
        // getOwnedCollectionOrThrow), so it 404s for exactly the learners this recommendation targets
        // — the adoption guard above guarantees we only reach here when they do NOT own the plan.
        // Explore's default tab is review-sets, which renders the published plans already filtered to
        // the learner's own course/program, so the named plan is the first thing they see, adoptable.
        return new NextStepSecondaryActionResponse(
                START_RECOMMENDED_PLAN_LABEL.formatted(matchedPlan.getTitle()),
                EXPLORE_POST_MASTERY_PATH,
                false,
                true,
                matchedPlan.getCourseProgram(),
                matchedPlan.getId().toString(),
                false
        );
    }

    private NextStepSecondaryActionResponse resolveNextPlanItem(
            UUID userId,
            UUID completedNoteId,
            UUID primaryCollectionId
    ) {
        if (completedNoteId == null) {
            return null;
        }
        List<UUID> containingCollectionIds = noteCollectionItemRepository
                .findContainingCollectionIdsByNoteIdAndOwnerUserIdOrderByUpdatedAtDesc(completedNoteId, userId);
        if (containingCollectionIds.isEmpty()) {
            return null;
        }
        UUID collectionId = primaryCollectionId != null && containingCollectionIds.contains(primaryCollectionId)
                ? primaryCollectionId
                : containingCollectionIds.getFirst();

        List<UUID> candidateNoteIds = noteCollectionItemRepository
                .findReadableNoteIdsByCollectionIdOrderByPositionAsc(collectionId, userId, completedNoteId);
        if (candidateNoteIds.isEmpty()) {
            return null;
        }
        // One practiced lookup for the whole plan, matching how NoteCollectionService already resolves
        // collection progress. It is deliberately the single authority on "practiced" — it counts
        // multi-note sessions, which a per-note session predicate cannot see.
        Map<UUID, OffsetDateTime> practicedAtByNoteId = quizSessionHistoryService
                .findLatestSessionCompletedAtByNoteIds(userId, candidateNoteIds);
        return candidateNoteIds.stream()
                .filter(noteId -> practicedAtByNoteId.get(noteId) == null)
                .findFirst()
                .map(nextNoteId -> new NextStepSecondaryActionResponse(
                        NEXT_IN_YOUR_PLAN_LABEL,
                        pathOrFallback(nextNoteId, NOTE_DETAIL_PATH),
                        false,
                        false,
                        null,
                        null,
                        true
                ))
                .orElse(null);
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
        return practiceWeakConceptResponse(studyPack, dueConcepts, adaptivePracticeQuota, goalNudge, null);
    }

    private NextStepResponse practiceWeakConceptResponse(
            StudyPackEntity studyPack,
            List<String> dueConcepts,
            AdaptivePracticeQuota adaptivePracticeQuota,
            GoalNudgeResponse goalNudge,
            NextStepSecondaryActionResponse secondaryAction
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
                secondaryAction
        );
    }

    private NextStepResponse redoMissedQuestionsResponse(
            StudyPackEntity studyPack,
            AdaptivePracticeQuota adaptivePracticeQuota,
            GoalNudgeResponse goalNudge
    ) {
        return new NextStepResponse(
                TodayFocusType.REDO_MISSED_QUESTIONS,
                studyPack.getId().toString(),
                stringify(studyPack.getNoteId()),
                studyPack.getTitle(),
                "Your missed Challenge Quiz questions are ready for another try.",
                REDO_MISSED_QUESTIONS_LABEL,
                pathOrFallback(studyPack.getNoteId(), REDO_MISSED_CHALLENGE_QUIZ_PATH),
                List.of(),
                adaptivePracticeQuota.available(),
                adaptivePracticeQuota.remaining(),
                goalNudge,
                null
        );
    }

    /**
     * The next step after a Quick Review the learner has not yet mastered: send them back to the
     * source note, with Challenge kept available as a secondary action.
     *
     * <p>This replaced a {@code RETRY_REVIEW} primary labelled "Retry Incorrect Questions", which was
     * wrong twice over. It pointed at the Quick Review path, so it restarted the <em>whole</em>
     * Quick Review rather than the missed questions — the genuine targeted retry only exists
     * mid-session. And it re-offered an action the learner had already declined one screen earlier,
     * as the primary CTA, while the thing they actually needed (re-reading the material) sat at the
     * bottom of the screen.
     */
    private NextStepResponse reviewNotesResponse(
            StudyPackEntity studyPack,
            List<String> weakConcepts,
            AdaptivePracticeQuota adaptivePracticeQuota,
            GoalNudgeResponse goalNudge,
            NextStepSecondaryActionResponse secondaryAction
    ) {
        return new NextStepResponse(
                TodayFocusType.REVIEW_PACK,
                studyPack.getId().toString(),
                stringify(studyPack.getNoteId()),
                studyPack.getTitle(),
                "Review the notes on these areas, then come back and try again.",
                REVIEW_THE_NOTES_LABEL,
                pathOrFallback(studyPack.getNoteId(), NOTE_DETAIL_PATH),
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

    private NextStepSecondaryActionResponse redoMissedQuestionsSecondaryAction(StudyPackEntity studyPack) {
        return new NextStepSecondaryActionResponse(
                REDO_MISSED_QUESTIONS_LABEL,
                pathOrFallback(studyPack.getNoteId(), REDO_MISSED_CHALLENGE_QUIZ_PATH),
                false
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
            if (note == null) {
                return null;
            }
            // M3: read the join first. Reading notes.course_program alone made every curated note look
            // programme-less -- ADR-001 defines a curator-authored note's string as null -- so the goal
            // nudge was skipped entirely for exactly the notes an Official Review Set is built from.
            List<String> notePrograms = NoteEffectivePrograms.resolve(
                    noteCourseProgramRepository.findByNoteId(note.getId()).stream()
                            .map(ApplicableProgramResponse::name)
                            .toList(),
                    note.getCourseProgram()
            );
            if (notePrograms.isEmpty()) {
                return null;
            }
            // Applicable to several programs means in-goal if ANY of them is: the note genuinely serves
            // that goal, so nudging the learner elsewhere would be wrong.
            if (notePrograms.stream().anyMatch(program -> isCurrentNoteInGoal(studyGoal, program))) {
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
            return examGoalCourseProgramProvider.getCoursePrograms(studyGoal).stream()
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
