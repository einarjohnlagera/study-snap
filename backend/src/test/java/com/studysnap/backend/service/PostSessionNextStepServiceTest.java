package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.ConceptHealthEntryResponse;
import com.studysnap.backend.dto.ConceptReadinessStatus;
import com.studysnap.backend.dto.GoalNudgeResponse;
import com.studysnap.backend.dto.NextStepResponse;
import com.studysnap.backend.dto.TodayFocusType;
import com.studysnap.backend.entity.NoteEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.QuickReviewRound;
import com.studysnap.backend.entity.QuickReviewSessionEntity;
import com.studysnap.backend.entity.QuickReviewSessionMode;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.StudyPackEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private ProgressReportService progressReportService;

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
                progressReportService
        );
    }

    @Test
    void getNextStep_returnsChallengeAfterPerfectQuickReviewWhenOnlyNeverReviewedConceptsAreDue() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        stubLatestSession(userId, studyPack, QuickReviewSessionMode.QUICK_REVIEW, List.of());
        stubConceptHealth(userId, studyPack, List.of(
                conceptHealth(FIRST_CONCEPT, null, true),
                conceptHealth(SECOND_CONCEPT, null, true)
        ));

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.actionLabel()).isEqualTo("Take a Challenge");
        assertThat(response.actionHref()).endsWith(CHALLENGE_PATH_SUFFIX);
        assertThat(response.concepts()).isEmpty();
        assertThat(response.secondaryAction()).isNull();
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

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.actionHref()).endsWith(CHALLENGE_PATH_SUFFIX);
        assertThat(response.concepts()).containsExactly(FIRST_CONCEPT);
        assertThat(response.secondaryAction()).isNotNull();
        assertThat(response.secondaryAction().actionHref()).endsWith(ADAPTIVE_PATH_SUFFIX);
        assertThat(response.secondaryAction().adaptivePractice()).isTrue();
    }

    @Test
    void getNextStep_promotesChallengeForSingleMissQuickReviewWithRetrySecondary() {
        UUID userId = UUID.randomUUID();
        StudyPackEntity studyPack = buildStudyPack(userId);
        stubOwnedStudyPack(userId, studyPack);
        stubPlanAndUsage(userId, PlanType.FREE, 0);
        stubLatestSession(userId, studyPack, QuickReviewSessionMode.QUICK_REVIEW, List.of(FIRST_CONCEPT));
        stubConceptHealth(userId, studyPack, List.of());

        NextStepResponse response = postSessionNextStepService.getNextStep(userId, studyPack.getId());

        assertThat(response.type()).isEqualTo(TodayFocusType.REVIEW_PACK);
        assertThat(response.actionHref()).endsWith(CHALLENGE_PATH_SUFFIX);
        assertThat(response.concepts()).containsExactly(FIRST_CONCEPT);
        assertThat(response.secondaryAction()).isNotNull();
        assertThat(response.secondaryAction().actionHref()).endsWith(QUICK_REVIEW_PATH_SUFFIX);
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

        assertThat(response.type()).isEqualTo(TodayFocusType.RETRY_REVIEW);
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

    private void stubNote(UUID userId, UUID noteId, String courseProgram) {
        NoteEntity note = new NoteEntity();
        note.setId(noteId);
        note.setOwnerUserId(userId);
        note.setCourseProgram(courseProgram);
        when(noteRepository.findByIdAndOwnerUserId(noteId, userId)).thenReturn(Optional.of(note));
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
