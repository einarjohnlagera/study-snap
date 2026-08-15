package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ConceptHealthEntryResponse;
import com.studysnap.backend.dto.ConceptReadinessStatus;
import com.studysnap.backend.dto.GoalNudgeResponse;
import com.studysnap.backend.dto.NextStepResponse;
import com.studysnap.backend.dto.NoteCollectionSummaryResponse;
import com.studysnap.backend.dto.TodayFocusType;
import com.studysnap.backend.entity.ChallengeQuizQuestionBankEntity;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.repository.ChallengeQuizQuestionBankRepository;
import com.studysnap.backend.entity.CollectionVisibility;
import com.studysnap.backend.entity.NoteCollectionEntity;
import com.studysnap.backend.repository.NoteCollectionItemRepository;
import com.studysnap.backend.repository.NoteCollectionRepository;
import com.studysnap.backend.repository.NoteCourseProgramRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import com.studysnap.backend.service.model.StudyPackQuizMastery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostSessionNextStepServiceTest {
    private static final String FIRST_CONCEPT = "Cardiac Output";
    private static final String SECOND_CONCEPT = "Renal Clearance";
    private static final String CHALLENGE_PATH_SUFFIX = "/challenge-quiz";
    private static final String ADAPTIVE_PATH_SUFFIX = "/adaptive-practice";
    private static final String QUICK_REVIEW_PATH_SUFFIX = "/quick-review";
    private static final String NOTE_DETAIL_PATH_PREFIX = "/notes/";
    private static final String TAKE_CHALLENGE_LABEL = "Take a Challenge";
    private static final String NEXT_IN_YOUR_PLAN_LABEL = "Next in your plan";
    private static final String REVIEW_THE_NOTES_LABEL = "Review the Notes";
    private static final String REDO_MISSED_QUESTIONS_LABEL = "Redo Missed Questions";
    private static final String OUTCOME_INCORRECT = "INCORRECT";
    private static final OffsetDateTime NOW = OffsetDateTime.of(2026, 6, 4, 7, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private QuickReviewSessionRepository quickReviewSessionRepository;
    @Mock
    private ConceptHealthService conceptHealthService;
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private UserUsageService userUsageService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private NoteCourseProgramRepository noteCourseProgramRepository;
    @Mock
    private ProgressReportService progressReportService;
    @Mock
    private ChallengeQuizQuestionBankService challengeQuizQuestionBankService;
    @Mock
    private ChallengeQuizQuestionBankRepository challengeQuizQuestionBankRepository;
    @Mock
    private StudyPackGenerationContextResolver generationContextResolver;
    @Mock
    private ExamGoalCourseProgramProvider examGoalCourseProgramProvider;
    @Mock
    private StudyPackQuizMasteryService studyPackQuizMasteryService;
    @Mock
    private NoteCollectionItemRepository noteCollectionItemRepository;
    @Mock
    private QuizSessionHistoryService quizSessionHistoryService;
    @Mock
    private NoteCollectionRepository noteCollectionRepository;

    private StudySnapProperties properties;
    private PostSessionNextStepService postSessionNextStepService;

    @BeforeEach
    void setUp() {
        properties = new StudySnapProperties();
        postSessionNextStepService = new PostSessionNextStepService(
                studyPackRepository,
                quickReviewSessionRepository,
                conceptHealthService,
                subscriptionService,
                userUsageService,
                properties,
                userRepository,
                noteRepository,
                noteCourseProgramRepository,
                progressReportService,
                challengeQuizQuestionBankService,
                generationContextResolver,
                examGoalCourseProgramProvider,
                studyPackQuizMasteryService,
                noteCollectionItemRepository,
                quizSessionHistoryService,
                noteCollectionRepository
        );
        lenient().when(examGoalCourseProgramProvider.getCoursePrograms("pnle"))
                .thenReturn(List.of("Nursing"));
        lenient().when(studyPackQuizMasteryService.resolve(any(), any()))
                .thenReturn(StudyPackQuizMastery.notMastered());
    }

    @Test
    void getNextStep_returnsNoSecondaryActionWhenMasteredNoteIsInNoCollection() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        stubLatestSession(userId, studyPack, QuickReviewSessionMode.QUICK_REVIEW, List.of());
        stubConceptHealth(userId, studyPack, List.of(
                conceptHealth(FIRST_CONCEPT, null, true),
                conceptHealth(SECOND_CONCEPT, null, true)
        ));
        when(studyPackQuizMasteryService.resolve(userId, studyPack))
                .thenReturn(StudyPackQuizMastery.masteredAt(NOW));

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.actionLabel()).isEqualTo(TAKE_CHALLENGE_LABEL);
        assertThat(response.actionHref()).endsWith(CHALLENGE_PATH_SUFFIX);
        assertThat(response.concepts()).isEmpty();
        assertThat(response.secondaryAction()).isNull();
    }

    @Test
    void getNextStep_offersMatchingUnadoptedPublishedPlanWhenMasteredNoteHasNoPlan() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubMasteredQuickReviewWithCourseProgram(userId, studyPack, "Nursing");
        when(noteCollectionRepository.findByVisibilityAndCourseProgramAndParentCollectionIdIsNullOrderByUpdatedAtDesc(
                CollectionVisibility.PUBLIC, "Nursing"
        ))
                .thenReturn(List.of(collectionEntity(planId, "Nursing Board Review", "Nursing", null)));

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.secondaryAction()).isNotNull();
        assertThat(response.secondaryAction().actionLabel()).isEqualTo("Start Nursing Board Review");
        // NOT /collections/{id}: that route is owner-scoped and 404s for an unadopted plan, which is
        // the only case this recommendation fires in. Explore renders it program-filtered and adoptable.
        assertThat(response.secondaryAction().actionHref()).isEqualTo("/explore?source=post-mastery");
        assertThat(response.secondaryAction().recommendedPlanId()).isEqualTo(planId.toString());
        assertThat(response.secondaryAction().studyPlanRecommendation()).isTrue();
        assertThat(response.secondaryAction().courseProgram()).isEqualTo("Nursing");
        assertThat(response.secondaryAction().nextPlanItem()).isFalse();
    }

    @Test
    void getNextStep_keepsNextPlanItemAheadOfPublishedPlanRecommendation() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID nextNoteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubMasteredQuickReviewWithCourseProgram(userId, studyPack, "Nursing");
        when(noteCollectionItemRepository.findContainingCollectionIdsByNoteIdAndOwnerUserIdOrderByUpdatedAtDesc(
                studyPack.getNoteId(), userId
        )).thenReturn(List.of(collectionId));
        stubNextPlanCandidate(userId, studyPack, collectionId, nextNoteId);

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.secondaryAction().actionLabel()).isEqualTo(NEXT_IN_YOUR_PLAN_LABEL);
        verify(noteCollectionRepository, never())
                .findByVisibilityAndCourseProgramAndParentCollectionIdIsNullOrderByUpdatedAtDesc(any(), any());
    }

    @Test
    void getNextStep_returnsNoRecommendationWhenProgramHasNoPublishedPlan() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubMasteredQuickReviewWithCourseProgram(userId, studyPack, "Architecture");
        when(noteCollectionRepository.findByVisibilityAndCourseProgramAndParentCollectionIdIsNullOrderByUpdatedAtDesc(
                CollectionVisibility.PUBLIC, "Architecture"
        )).thenReturn(List.of());

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.secondaryAction()).isNull();
    }

    @Test
    void getNextStep_returnsNoRecommendationWhenMatchingPlanWasAlreadyAdopted() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubMasteredQuickReviewWithCourseProgram(userId, studyPack, "Education");
        when(noteCollectionRepository.findByVisibilityAndCourseProgramAndParentCollectionIdIsNullOrderByUpdatedAtDesc(
                CollectionVisibility.PUBLIC, "Education"
        ))
                .thenReturn(List.of(collectionEntity(planId, "LET Review", "Education", null)));
        when(noteCollectionRepository.findByOwnerUserIdAndSourcePlanId(userId, planId))
                .thenReturn(Optional.of(collectionEntity(UUID.randomUUID(), "My LET Review", "Education", planId)));

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.secondaryAction()).isNull();
    }

    @Test
    void getNextStep_fallsBackWithoutBrokenActionWhenPublishedPlanLookupThrows() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubMasteredQuickReviewWithCourseProgram(userId, studyPack, "Nursing");
        when(noteCollectionRepository.findByVisibilityAndCourseProgramAndParentCollectionIdIsNullOrderByUpdatedAtDesc(
                CollectionVisibility.PUBLIC, "Nursing"
        ))
                .thenThrow(new IllegalStateException("catalog unavailable"));

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.actionLabel()).isEqualTo(TAKE_CHALLENGE_LABEL);
        assertThat(response.secondaryAction()).isNull();
    }

    @Test
    void getNextStep_offersLowestPositionUnpracticedPlanItemAfterMastery() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID nextNoteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubMasteredQuickReview(userId, studyPack);
        when(noteCollectionItemRepository.findContainingCollectionIdsByNoteIdAndOwnerUserIdOrderByUpdatedAtDesc(
                studyPack.getNoteId(), userId
        )).thenReturn(List.of(collectionId));
        when(noteCollectionItemRepository.findReadableNoteIdsByCollectionIdOrderByPositionAsc(
                collectionId, userId, studyPack.getNoteId()
        )).thenReturn(List.of(nextNoteId));
        when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(userId, List.of(nextNoteId)))
                .thenReturn(Map.of());

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.actionLabel()).isEqualTo(TAKE_CHALLENGE_LABEL);
        assertThat(response.actionHref()).endsWith(CHALLENGE_PATH_SUFFIX);
        assertThat(response.secondaryAction()).isNotNull();
        assertThat(response.secondaryAction().actionLabel()).isEqualTo(NEXT_IN_YOUR_PLAN_LABEL);
        assertThat(response.secondaryAction().actionHref()).isEqualTo(NOTE_DETAIL_PATH_PREFIX + nextNoteId);
        assertThat(response.secondaryAction().adaptivePractice()).isFalse();
        // Drives POST_SESSION_NEXT_PLAN_ITEM_* — without it this action emits no analytics at all.
        assertThat(response.secondaryAction().nextPlanItem()).isTrue();
        assertThat(response.secondaryAction().studyPlanRecommendation()).isFalse();
    }

    @Test
    void getNextStep_skipsPracticedItemsAndOffersTheLowestPositionUnpracticedOne() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID practicedNoteId = UUID.randomUUID();
        UUID nextNoteId = UUID.randomUUID();
        UUID laterNoteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubMasteredQuickReview(userId, studyPack);
        when(noteCollectionItemRepository.findContainingCollectionIdsByNoteIdAndOwnerUserIdOrderByUpdatedAtDesc(
                studyPack.getNoteId(), userId
        )).thenReturn(List.of(collectionId));
        // The repository returns plan order without filtering practice state — that filter is the
        // service's single definition of practiced, so this test is what proves it is applied.
        List<UUID> planOrder = List.of(practicedNoteId, nextNoteId, laterNoteId);
        when(noteCollectionItemRepository.findReadableNoteIdsByCollectionIdOrderByPositionAsc(
                collectionId, userId, studyPack.getNoteId()
        )).thenReturn(planOrder);
        when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(userId, planOrder))
                .thenReturn(Map.of(practicedNoteId, NOW));

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.secondaryAction()).isNotNull();
        assertThat(response.secondaryAction().actionHref()).isEqualTo(NOTE_DETAIL_PATH_PREFIX + nextNoteId);
    }

    @Test
    void getNextStep_returnsNoPlanActionWhenAllCandidateItemsArePracticedThroughSharedHistory() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID candidateNoteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubMasteredQuickReview(userId, studyPack);
        when(noteCollectionItemRepository.findContainingCollectionIdsByNoteIdAndOwnerUserIdOrderByUpdatedAtDesc(
                studyPack.getNoteId(), userId
        )).thenReturn(List.of(collectionId));
        when(noteCollectionItemRepository.findReadableNoteIdsByCollectionIdOrderByPositionAsc(
                collectionId, userId, studyPack.getNoteId()
        )).thenReturn(List.of(candidateNoteId));
        when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(userId, List.of(candidateNoteId)))
                .thenReturn(Map.of(candidateNoteId, NOW));

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.secondaryAction()).isNull();
    }

    @Test
    void getNextStep_usesPrimaryContainingCollectionWhenNoteBelongsToSeveralPlans() {
        UUID userId = UUID.randomUUID();
        UUID primaryCollectionId = UUID.randomUUID();
        UUID recentCollectionId = UUID.randomUUID();
        UUID nextNoteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubMasteredQuickReview(userId, studyPack, primaryCollectionId);
        when(noteCollectionItemRepository.findContainingCollectionIdsByNoteIdAndOwnerUserIdOrderByUpdatedAtDesc(
                studyPack.getNoteId(), userId
        )).thenReturn(List.of(recentCollectionId, primaryCollectionId));
        when(noteCollectionItemRepository.findReadableNoteIdsByCollectionIdOrderByPositionAsc(
                primaryCollectionId, userId, studyPack.getNoteId()
        )).thenReturn(List.of(nextNoteId));
        when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(userId, List.of(nextNoteId)))
                .thenReturn(Map.of());

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.secondaryAction().actionHref()).isEqualTo(NOTE_DETAIL_PATH_PREFIX + nextNoteId);
        verify(noteCollectionItemRepository).findReadableNoteIdsByCollectionIdOrderByPositionAsc(
                primaryCollectionId, userId, studyPack.getNoteId()
        );
    }

    @Test
    void getNextStep_fallsBackToMostRecentContainingCollectionWhenPrimaryDoesNotContainNote() {
        UUID userId = UUID.randomUUID();
        UUID nonContainingPrimaryId = UUID.randomUUID();
        UUID recentCollectionId = UUID.randomUUID();
        UUID olderCollectionId = UUID.randomUUID();
        UUID nextNoteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubMasteredQuickReview(userId, studyPack, nonContainingPrimaryId);
        when(noteCollectionItemRepository.findContainingCollectionIdsByNoteIdAndOwnerUserIdOrderByUpdatedAtDesc(
                studyPack.getNoteId(), userId
        )).thenReturn(List.of(recentCollectionId, olderCollectionId));
        stubNextPlanCandidate(userId, studyPack, recentCollectionId, nextNoteId);

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.secondaryAction().actionHref()).isEqualTo(NOTE_DETAIL_PATH_PREFIX + nextNoteId);
    }

    @Test
    void getNextStep_fallsBackCleanlyWhenPrimaryCollectionIdIsStale() {
        UUID userId = UUID.randomUUID();
        UUID stalePrimaryId = UUID.randomUUID();
        UUID containingCollectionId = UUID.randomUUID();
        UUID nextNoteId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubMasteredQuickReview(userId, studyPack, stalePrimaryId);
        when(noteCollectionItemRepository.findContainingCollectionIdsByNoteIdAndOwnerUserIdOrderByUpdatedAtDesc(
                studyPack.getNoteId(), userId
        )).thenReturn(List.of(containingCollectionId));
        stubNextPlanCandidate(userId, studyPack, containingCollectionId, nextNoteId);

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.secondaryAction().actionHref()).isEqualTo(NOTE_DETAIL_PATH_PREFIX + nextNoteId);
    }

    @Test
    void getNextStep_keepsGenuineWeakPracticeSecondaryAfterPerfectQuickReview() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        stubLatestSession(userId, studyPack, QuickReviewSessionMode.QUICK_REVIEW, List.of());
        stubConceptHealth(userId, studyPack, List.of(
                conceptHealth(FIRST_CONCEPT, NOW.minusDays(5), true),
                conceptHealth(SECOND_CONCEPT, NOW.minusDays(1), false)
        ));
        when(studyPackQuizMasteryService.resolve(userId, studyPack))
                .thenReturn(StudyPackQuizMastery.masteredAt(NOW));

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        // Quick Review no longer routes into Adaptive Practice at all (EXAM_MODES.md amendment):
        // a static 5-question refresher should not spend quota-limited LLM remediation.
        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.actionHref()).endsWith(CHALLENGE_PATH_SUFFIX);
        assertThat(response.secondaryAction()).isNull();
    }

    @Test
    void getNextStep_returnsRetryReviewForSingleMissQuickReviewWithChallengeSecondary() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        stubLatestSession(userId, studyPack, QuickReviewSessionMode.QUICK_REVIEW, List.of(FIRST_CONCEPT));
        stubConceptHealth(userId, studyPack, List.of());
        when(studyPackQuizMasteryService.resolve(userId, studyPack))
                .thenReturn(StudyPackQuizMastery.notMastered());

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        // A non-mastered Quick Review now sends the learner back to the note. The old primary
        // ("Retry Incorrect Questions") pointed at the Quick Review path, so it restarted the whole
        // quiz rather than the missed questions, and re-offered what was already declined one
        // screen earlier.
        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.actionLabel()).isEqualTo(REVIEW_THE_NOTES_LABEL);
        assertThat(response.actionHref()).doesNotEndWith(QUICK_REVIEW_PATH_SUFFIX);
        assertThat(response.concepts()).containsExactly(FIRST_CONCEPT);
        assertThat(response.secondaryAction()).isNotNull();
        assertThat(response.secondaryAction().actionHref()).endsWith(CHALLENGE_PATH_SUFFIX);
        assertThat(response.secondaryAction().adaptivePractice()).isFalse();
    }

    @Test
    void getNextStep_returnsRetryReviewForMultipleQuickReviewMissesWithChallengeSecondary() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        stubLatestSession(userId, studyPack, QuickReviewSessionMode.QUICK_REVIEW, List.of(FIRST_CONCEPT, SECOND_CONCEPT));
        stubConceptHealth(userId, studyPack, List.of());

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.actionLabel()).isEqualTo(REVIEW_THE_NOTES_LABEL);
        assertThat(response.concepts()).containsExactly(FIRST_CONCEPT, SECOND_CONCEPT);
        assertThat(response.secondaryAction()).isNotNull();
        assertThat(response.secondaryAction().actionHref()).endsWith(CHALLENGE_PATH_SUFFIX);
        assertThat(response.secondaryAction().adaptivePractice()).isFalse();
    }

    @Test
    void getNextStep_returnsNoGoalNudgeWhenUserHasNoGoal() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                eq(studyPack.getId()),
                any(Pageable.class)
        )).thenReturn(List.of());

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.goalNudge()).isNull();
        verify(progressReportService, never()).buildGoalNudge(any(), any(), any());
    }

    @Test
    void getNextStep_returnsNoGoalNudgeWhenExamGoalMatchesCurrentNoteCourseProgram() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack, "pnle");
        stubNote(userId, studyPack.getNoteId(), "Nursing");
        stubReviewPackPath(userId, studyPack);

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.goalNudge()).isNull();
        verify(progressReportService, never()).buildGoalNudge(any(), any(), any());
    }

    @Test
    void getNextStep_returnsExamGoalNudgeWhenCurrentNoteIsOutsideExamGoal() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        GoalNudgeResponse nudge = new GoalNudgeResponse(
                "pnle",
                "EXAM",
                "PNLE",
                "Philippine Nurse Licensure Examination",
                42,
                8,
                null
        );
        stubOwnedStudyPack(userId, studyPack, "pnle");
        stubNote(userId, studyPack.getNoteId(), "Biochemistry");
        stubReviewPackPath(userId, studyPack);
        when(progressReportService.buildGoalNudge(eq(userId), eq("pnle"), any(OffsetDateTime.class))).thenReturn(nudge);

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.goalNudge()).isEqualTo(nudge);
        assertThat(response.goalNudge().goalName()).isEqualTo("PNLE");
        assertThat(response.goalNudge().goalLabel()).isEqualTo("Philippine Nurse Licensure Examination");
        assertThat(response.goalNudge().goalType()).isEqualTo("EXAM");
    }

    @Test
    void getNextStep_readsTheJoinWhenResolvingWhetherACuratedNoteIsInTheGoal() {
        // M3: reading notes.course_program alone made every curated note look programme-less -- ADR-001
        // defines a curator-authored note's string as null -- so the nudge was skipped for exactly the
        // notes an Official Review Set is built from. Here the note is outside the goal, so it nudges.
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        GoalNudgeResponse nudge = new GoalNudgeResponse(
                "pnle", "EXAM", "PNLE", "Philippine Nurse Licensure Examination", 42, 8, null
        );
        stubOwnedStudyPack(userId, studyPack, "pnle");
        stubCuratedNote(userId, studyPack.getNoteId(), "Civil Engineering");
        stubReviewPackPath(userId, studyPack);
        when(progressReportService.buildGoalNudge(eq(userId), eq("pnle"), any(OffsetDateTime.class))).thenReturn(nudge);

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.goalNudge()).isEqualTo(nudge);
    }

    @Test
    void getNextStep_suppressesTheNudgeWhenAnyJoinedProgramIsInsideTheGoal() {
        // Applicable to several programs means in-goal if ANY of them is: the note genuinely serves that
        // goal, so nudging the learner elsewhere would be wrong.
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack, "pnle");
        stubCuratedNote(userId, studyPack.getNoteId(), "Civil Engineering", "Nursing");
        stubReviewPackPath(userId, studyPack);

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.goalNudge()).isNull();
        verify(progressReportService, never()).buildGoalNudge(any(), any(), any());
    }

    @Test
    void getNextStep_returnsNoGoalNudgeWhenSubjectGoalMatchesCurrentNoteCourseProgram() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack, "Biochemistry");
        stubNote(userId, studyPack.getNoteId(), "Biochemistry");
        stubReviewPackPath(userId, studyPack);

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.goalNudge()).isNull();
        verify(progressReportService, never()).buildGoalNudge(any(), any(), any());
    }

    @Test
    void getNextStep_returnsSubjectGoalNudgeWhenCurrentNoteIsOutsideSubjectGoal() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        GoalNudgeResponse nudge = new GoalNudgeResponse(
                "Biochemistry",
                "SUBJECT",
                "Biochemistry",
                "Biochemistry",
                25,
                3,
                null
        );
        stubOwnedStudyPack(userId, studyPack, "Biochemistry");
        stubNote(userId, studyPack.getNoteId(), "Nursing");
        stubReviewPackPath(userId, studyPack);
        when(progressReportService.buildGoalNudge(eq(userId), eq("Biochemistry"), any(OffsetDateTime.class))).thenReturn(nudge);

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.goalNudge()).isEqualTo(nudge);
        assertThat(response.goalNudge().goalName()).isEqualTo("Biochemistry");
        assertThat(response.goalNudge().goalType()).isEqualTo("SUBJECT");
    }

    @Test
    void getNextStep_returnsNoGoalNudgeWhenStudyPackHasNoLinkedNote() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        studyPack.setNoteId(null);
        stubOwnedStudyPack(userId, studyPack, "pnle");
        stubReviewPackPath(userId, studyPack);

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.goalNudge()).isNull();
        verify(progressReportService, never()).buildGoalNudge(any(), any(), any());
    }

    @Test
    void getNextStep_returnsZeroGoalNudgeWhenGoalHasNoMatchingStudyPacks() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        GoalNudgeResponse nudge = new GoalNudgeResponse(
                "pnle",
                "EXAM",
                "PNLE",
                "Philippine Nurse Licensure Examination",
                0,
                0,
                null
        );
        stubOwnedStudyPack(userId, studyPack, "pnle");
        stubNote(userId, studyPack.getNoteId(), "Biochemistry");
        stubReviewPackPath(userId, studyPack);
        when(progressReportService.buildGoalNudge(eq(userId), eq("pnle"), any(OffsetDateTime.class))).thenReturn(nudge);

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.goalNudge()).isEqualTo(nudge);
        assertThat(response.goalNudge().masteryPercentage()).isZero();
        assertThat(response.goalNudge().dueConcepts()).isZero();
    }

    @Test
    void getNextStep_returnsNullGoalNudgeWhenGoalAggregationThrows() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack, "pnle");
        stubNote(userId, studyPack.getNoteId(), "Biochemistry");
        stubReviewPackPath(userId, studyPack);
        when(progressReportService.buildGoalNudge(eq(userId), eq("pnle"), any(OffsetDateTime.class)))
                .thenThrow(new IllegalStateException("progress unavailable"));

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.goalNudge()).isNull();
    }

    @Test
    void getNextStep_returnsPracticeWeakConceptsAfterChallengeWithGenuineWeakness() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        stubLatestSession(userId, studyPack, QuickReviewSessionMode.CHALLENGE, List.of(SECOND_CONCEPT));
        stubConceptHealth(userId, studyPack, List.of(
                conceptHealth(FIRST_CONCEPT, NOW.minusDays(7), true),
                conceptHealth(SECOND_CONCEPT, null, true)
        ));

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.type()).isEqualTo(TodayFocusType.PRACTICE_WEAK_CONCEPT);
        assertThat(response.actionHref()).endsWith(ADAPTIVE_PATH_SUFFIX);
        assertThat(response.concepts()).containsExactly(FIRST_CONCEPT, SECOND_CONCEPT);
    }

    @Test
    void getNextStep_offersRedoMissedQuestionsAsSecondaryAfterChallengeWithWeakConcepts() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        stubLatestSession(userId, studyPack, QuickReviewSessionMode.CHALLENGE, List.of(FIRST_CONCEPT));
        stubConceptHealth(userId, studyPack, List.of(conceptHealth(FIRST_CONCEPT, NOW.minusDays(7), true)));
        when(challengeQuizQuestionBankService.countEligibleIncorrectQuestions(userId, studyPack.getId(), LearnerLevel.COLLEGE))
                .thenReturn(3L);

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.type()).isEqualTo(TodayFocusType.PRACTICE_WEAK_CONCEPT);
        assertThat(response.secondaryAction()).isNotNull();
        assertThat(response.secondaryAction().actionLabel()).isEqualTo(REDO_MISSED_QUESTIONS_LABEL);
        assertThat(response.secondaryAction().actionHref()).endsWith("/challenge-quiz?entry=redo-missed");
    }

    @Test
    void getNextStep_promotesRedoMissedQuestionsWhenChallengeHasNoWeakConcepts() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        stubLatestSession(userId, studyPack, QuickReviewSessionMode.CHALLENGE, List.of());
        stubConceptHealth(userId, studyPack, List.of(conceptHealth(FIRST_CONCEPT, NOW.minusDays(1), false)));
        when(challengeQuizQuestionBankService.countEligibleIncorrectQuestions(userId, studyPack.getId(), LearnerLevel.COLLEGE))
                .thenReturn(3L);

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.type()).isEqualTo(TodayFocusType.REDO_MISSED_QUESTIONS);
        assertThat(response.actionLabel()).isEqualTo(REDO_MISSED_QUESTIONS_LABEL);
        assertThat(response.actionHref()).endsWith("/challenge-quiz?entry=redo-missed");
    }

    @Test
    void getNextStep_redoAvailabilityAndClaimUseTheSameEffectiveCurriculumLevel() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        UUID redoSessionId = UUID.randomUUID();
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE, null, null, List.of(), null, LearnerLevel.SENIOR_HIGH
        );
        List<ChallengeQuizQuestionBankEntity> missedQuestions = List.of(
                bankedQuestion("Missed one"),
                bankedQuestion("Missed two"),
                bankedQuestion("Missed three")
        );
        ChallengeQuizQuestionBankService realQuestionBankService =
                new ChallengeQuizQuestionBankService(challengeQuizQuestionBankRepository);
        PostSessionNextStepService serviceWithRealBank = new PostSessionNextStepService(
                studyPackRepository,
                quickReviewSessionRepository,
                conceptHealthService,
                subscriptionService,
                userUsageService,
                properties,
                userRepository,
                noteRepository,
                noteCourseProgramRepository,
                progressReportService,
                realQuestionBankService,
                generationContextResolver,
                examGoalCourseProgramProvider,
                studyPackQuizMasteryService,
                noteCollectionItemRepository,
                quizSessionHistoryService,
                noteCollectionRepository
        );
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        stubLatestSession(userId, studyPack, QuickReviewSessionMode.CHALLENGE, List.of());
        stubConceptHealth(userId, studyPack, List.of());
        when(generationContextResolver.resolveForStudyPack(userId, studyPack)).thenReturn(context);
        when(challengeQuizQuestionBankRepository.countIncorrectEligibleQuestions(
                userId,
                studyPack.getId(),
                LearnerLevel.SENIOR_HIGH.name(),
                OUTCOME_INCORRECT
        )).thenReturn((long) missedQuestions.size());
        when(challengeQuizQuestionBankRepository.findIncorrectClaimableForUpdate(
                userId,
                studyPack.getId(),
                LearnerLevel.SENIOR_HIGH.name(),
                OUTCOME_INCORRECT
        )).thenReturn(missedQuestions);

        NextStepResponse response = serviceWithRealBank.getNextStep(userId, studyPack.getId());
        long eligibleCount = realQuestionBankService.countEligibleIncorrectQuestions(
                userId,
                studyPack.getId(),
                StudyPackGenerationContextResolver.effectiveCurriculumLevel(context)
        );
        List<com.studysnap.backend.dto.QuizItem> claimed = realQuestionBankService.claimIncorrectQuestions(
                userId,
                studyPack.getId(),
                StudyPackGenerationContextResolver.effectiveCurriculumLevel(context),
                redoSessionId,
                5,
                ChallengeQuizQuestionBankService.MINIMUM_REDO_MISSED_QUESTIONS
        );

        assertThat(response.type()).isEqualTo(TodayFocusType.REDO_MISSED_QUESTIONS);
        assertThat(claimed).hasSize(Math.toIntExact(eligibleCount));
        verify(challengeQuizQuestionBankRepository, org.mockito.Mockito.times(2)).countIncorrectEligibleQuestions(
                userId,
                studyPack.getId(),
                LearnerLevel.SENIOR_HIGH.name(),
                OUTCOME_INCORRECT
        );
        verify(challengeQuizQuestionBankRepository).findIncorrectClaimableForUpdate(
                userId,
                studyPack.getId(),
                LearnerLevel.SENIOR_HIGH.name(),
                OUTCOME_INCORRECT
        );
    }

    @Test
    void getNextStep_returnsReviewPackFallbackWhenGenerationContextResolutionFails() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        when(generationContextResolver.resolveForStudyPack(userId, studyPack))
                .thenThrow(new IllegalStateException("note unavailable"));

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.actionHref()).endsWith(CHALLENGE_PATH_SUFFIX);
    }

    @Test
    void getNextStep_returnsChallengeAfterChallengeWhenNoGenuineWeaknessRemains() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        stubLatestSession(userId, studyPack, QuickReviewSessionMode.CHALLENGE, List.of());
        stubConceptHealth(userId, studyPack, List.of(
                conceptHealth(FIRST_CONCEPT, NOW.minusDays(1), false),
                conceptHealth(SECOND_CONCEPT, null, true)
        ));

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.actionHref()).endsWith(CHALLENGE_PATH_SUFFIX);
        assertThat(response.concepts()).isEmpty();
    }

    @Test
    void getNextStep_stepsUpToChallengeAfterAdaptivePracticeWithRemainingWeakness() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        stubLatestSession(userId, studyPack, QuickReviewSessionMode.ADAPTIVE, List.of(SECOND_CONCEPT));
        stubConceptHealth(userId, studyPack, List.of(
                conceptHealth(FIRST_CONCEPT, NOW.minusDays(5), true)
        ));

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.actionHref()).endsWith(CHALLENGE_PATH_SUFFIX);
        assertThat(response.concepts()).containsExactly(FIRST_CONCEPT, SECOND_CONCEPT);
        assertThat(response.secondaryAction()).isNull();
    }

    @Test
    void getNextStep_stepsUpToChallengeAfterAdaptivePracticeWhenWeaknessIsCleared() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        stubLatestSession(userId, studyPack, QuickReviewSessionMode.ADAPTIVE, List.of());
        stubConceptHealth(userId, studyPack, List.of(
                conceptHealth(FIRST_CONCEPT, NOW.minusDays(1), false)
        ));

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.actionHref()).endsWith(CHALLENGE_PATH_SUFFIX);
        assertThat(response.concepts()).isEmpty();
    }

    @Test
    void resolveGenuineWeakConcepts_excludesNeverReviewedAndCapsStableUnion() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        QuickReviewSessionEntity session = buildCompletedSession(
                userId,
                studyPack,
                QuickReviewSessionMode.CHALLENGE,
                List.of("Four", "Five", "Six", FIRST_CONCEPT)
        );

        List<String> genuineWeakConcepts = postSessionNextStepService.resolveGenuineWeakConcepts(
                List.of(
                        conceptHealth(FIRST_CONCEPT, NOW.minusDays(4), true),
                        conceptHealth(SECOND_CONCEPT, null, true),
                        conceptHealth("Three", NOW.minusDays(8), true)
                ),
                session
        );

        assertThat(genuineWeakConcepts).containsExactly(FIRST_CONCEPT, "Three", "Four", "Five", "Six");
    }

    @Test
    void getNextStep_returnsReviewPackFallbackWhenConceptHealthThrows() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        stubLatestSession(userId, studyPack, QuickReviewSessionMode.CHALLENGE, List.of());
        when(conceptHealthService.getConceptHealth(eq(userId), eq(studyPack.getId()), any(), any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.actionHref()).endsWith(CHALLENGE_PATH_SUFFIX);
    }

    @Test
    void getNextStep_returnsSafeProgressionWhenNoCompletedSessionExists() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubReviewPackPath(userId, studyPack);

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.actionHref()).endsWith(CHALLENGE_PATH_SUFFIX);
        verify(conceptHealthService, never()).getConceptHealth(any(), any(), any(), any());
    }

    @Test
    void getNextStep_preservesAdaptiveQuotaForWeakAreaRecommendation() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.FREE, 3);
        stubLatestSession(userId, studyPack, QuickReviewSessionMode.CHALLENGE, List.of(FIRST_CONCEPT));
        stubConceptHealth(userId, studyPack, List.of());

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.type()).isEqualTo(TodayFocusType.PRACTICE_WEAK_CONCEPT);
        assertThat(response.adaptivePracticeAvailable()).isTrue();
        assertThat(response.adaptivePracticeRemaining()).isZero();
    }

    @Test
    void getNextStep_returnsNullAdaptiveRemainingForPro() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.PRO, 12);
        stubLatestSession(userId, studyPack, QuickReviewSessionMode.CHALLENGE, List.of(FIRST_CONCEPT));
        stubConceptHealth(userId, studyPack, List.of());

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.adaptivePracticeAvailable()).isTrue();
        assertThat(response.adaptivePracticeRemaining()).isNull();
    }

    private void stubOwnedStudyPack(UUID userId, StudyPackEntity studyPack) {
        when(studyPackRepository.findByIdAndOwnerUserId(studyPack.getId(), userId)).thenReturn(Optional.of(studyPack));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, null)));
    }

    private void stubOwnedStudyPack(UUID userId, StudyPackEntity studyPack, String studyGoal) {
        when(studyPackRepository.findByIdAndOwnerUserId(studyPack.getId(), userId)).thenReturn(Optional.of(studyPack));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(userId, studyGoal)));
    }

    private void stubMasteredQuickReview(UUID userId, StudyPackEntity studyPack) {
        stubMasteredQuickReview(userId, studyPack, null);
    }

    private void stubMasteredQuickReview(UUID userId, StudyPackEntity studyPack, UUID primaryCollectionId) {
        when(studyPackRepository.findByIdAndOwnerUserId(studyPack.getId(), userId)).thenReturn(Optional.of(studyPack));
        UserEntity user = user(userId, null);
        user.setPrimaryCollectionId(primaryCollectionId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        stubLatestSession(userId, studyPack, QuickReviewSessionMode.QUICK_REVIEW, List.of());
        stubConceptHealth(userId, studyPack, List.of());
        when(studyPackQuizMasteryService.resolve(userId, studyPack))
                .thenReturn(StudyPackQuizMastery.masteredAt(NOW));
    }

    private void stubMasteredQuickReviewWithCourseProgram(
            UUID userId,
            StudyPackEntity studyPack,
            String courseProgram
    ) {
        stubMasteredQuickReview(userId, studyPack);
        UserEntity user = user(userId, null);
        user.setCourseProgram(courseProgram);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private NoteCollectionEntity collectionEntity(
            UUID id,
            String title,
            String courseProgram,
            UUID sourcePlanId
    ) {
        NoteCollectionEntity collection = new NoteCollectionEntity();
        collection.setId(id);
        collection.setTitle(title);
        collection.setCourseProgram(courseProgram);
        collection.setSourcePlanId(sourcePlanId);
        collection.setVisibility(CollectionVisibility.PUBLIC);
        return collection;
    }

    private void stubNextPlanCandidate(
            UUID userId,
            StudyPackEntity studyPack,
            UUID collectionId,
            UUID nextNoteId
    ) {
        when(noteCollectionItemRepository.findReadableNoteIdsByCollectionIdOrderByPositionAsc(
                collectionId, userId, studyPack.getNoteId()
        )).thenReturn(List.of(nextNoteId));
        when(quizSessionHistoryService.findLatestSessionCompletedAtByNoteIds(userId, List.of(nextNoteId)))
                .thenReturn(Map.of());
    }

    private void stubNote(UUID userId, UUID noteId, String courseProgram) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setOwnerUserId(userId);
        note.setCourseProgram(courseProgram);
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(note));
        lenient().when(noteCourseProgramRepository.findByNoteId(noteId)).thenReturn(List.of());
    }

    /** A curated note: null legacy string, applicability carried entirely by the join (M3). */
    private void stubCuratedNote(UUID userId, UUID noteId, String... joinedProgramNames) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setOwnerUserId(userId);
        note.setCourseProgram(null);
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(note));
        when(noteCourseProgramRepository.findByNoteId(noteId)).thenReturn(
                java.util.Arrays.stream(joinedProgramNames)
                        .map(name -> new com.studysnap.backend.dto.ApplicableProgramResponse(UUID.randomUUID(), name))
                        .toList()
        );
    }

    private void stubReviewPackPath(UUID userId, StudyPackEntity studyPack) {
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                eq(studyPack.getId()),
                any(Pageable.class)
        )).thenReturn(List.of());
    }

    private void stubLatestSession(
            UUID userId,
            StudyPackEntity studyPack,
            QuickReviewSessionMode mode,
            List<String> weakConcepts
    ) {
        when(quickReviewSessionRepository.findByUserIdAndStudyPackIdAndCompletedAtIsNotNullOrderByCompletedAtDesc(
                eq(userId),
                eq(studyPack.getId()),
                any(Pageable.class)
        )).thenReturn(List.of(buildCompletedSession(userId, studyPack, mode, weakConcepts)));
    }

    private void stubConceptHealth(
            UUID userId,
            StudyPackEntity studyPack,
            List<ConceptHealthEntryResponse> conceptHealth
    ) {
        when(conceptHealthService.getConceptHealth(eq(userId), eq(studyPack.getId()), any(), any()))
                .thenReturn(conceptHealth);
    }

    private ConceptHealthEntryResponse conceptHealth(
            String concept,
            OffsetDateTime lastCorrectAt,
            boolean due
    ) {
        Integer daysSinceReview = lastCorrectAt == null
                ? null
                : Math.toIntExact(java.time.temporal.ChronoUnit.DAYS.between(lastCorrectAt, NOW));
        ConceptReadinessStatus status = lastCorrectAt == null
                ? ConceptReadinessStatus.NOT_STARTED
                : due ? ConceptReadinessStatus.DUE : ConceptReadinessStatus.MASTERED;
        return new ConceptHealthEntryResponse(concept, status, lastCorrectAt, null, false, due, daysSinceReview);
    }

    private UserEntity user(UUID userId, String studyGoal) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setStudyGoal(studyGoal);
        user.setLearnerLevel(LearnerLevel.COLLEGE);
        return user;
    }

    private void stubPlanAndUsage(UUID userId, PlanType planType, int adaptiveUsed) {
        when(subscriptionService.resolvePlan(userId)).thenReturn(planType);
        if (planType != PlanType.PRO) {
            when(userUsageService.getMonthlyUsage(eq(userId), any(OffsetDateTime.class)))
                    .thenReturn(new UserUsageService.MonthlyUsage(
                            NOW.minusDays(4),
                            NOW.plusDays(26),
                            0,
                            0,
                            adaptiveUsed,
                            0,
                            0,
                            0,
                            0
                    ));
        }
    }

    private StudyPackEntity buildStudyPack(UUID userId) {
        StudyPackEntity studyPack = new StudyPackEntity();
        studyPack.setId(UUID.randomUUID());
        studyPack.setOwnerUserId(userId);
        studyPack.setNoteId(UUID.randomUUID());
        studyPack.setTitle("Physiology");
        studyPack.setKeyConcepts(List.of(FIRST_CONCEPT, SECOND_CONCEPT));
        return studyPack;
    }

    private ChallengeQuizQuestionBankEntity bankedQuestion(String text) {
        ChallengeQuizQuestionBankEntity entry = new ChallengeQuizQuestionBankEntity();
        entry.setQuestionKey(text.toLowerCase());
        entry.setQuestion(new com.studysnap.backend.dto.QuizItem(
                text,
                List.of("A", "B", "C", "D"),
                0,
                "Concept",
                "Explanation"
        ));
        return entry;
    }

    private QuickReviewSessionEntity buildCompletedSession(
            UUID userId,
            StudyPackEntity studyPack,
            QuickReviewSessionMode mode,
            List<String> weakConcepts
    ) {
        QuickReviewSessionEntity session = new QuickReviewSessionEntity();
        session.setId(UUID.randomUUID());
        session.setUserId(userId);
        session.setStudyPackId(studyPack.getId());
        session.setNoteId(studyPack.getNoteId());
        session.setSessionMode(mode);
        session.setStatus(QuickReviewSessionStatus.COMPLETED);
        session.setCurrentQuestionIndex(2);
        session.setCurrentRound(QuickReviewRound.INITIAL);
        session.setTotalQuestions(2);
        session.setCorrectAnswers(weakConcepts.isEmpty() ? 2 : 1);
        session.setRetryCount(0);
        session.setCreatedAt(NOW.minusMinutes(10));
        session.setCompletedAt(NOW);
        session.setSessionMetadata(Map.of("weakConcepts", weakConcepts));
        return session;
    }
}
