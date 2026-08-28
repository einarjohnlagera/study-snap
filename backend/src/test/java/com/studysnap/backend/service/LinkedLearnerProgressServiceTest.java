package com.studysnap.backend.service;

import com.studysnap.backend.dto.GoalSummaryResponse;
import com.studysnap.backend.dto.LinkedLearnerProgressResponse;
import com.studysnap.backend.dto.MasterySnapshotResponse;
import com.studysnap.backend.dto.NoteCollectionSummaryResponse;
import com.studysnap.backend.dto.ProgressReportResponse;
import com.studysnap.backend.dto.StudyEngagementResponse;
import com.studysnap.backend.dto.SubjectProgressEntry;
import com.studysnap.backend.entity.EngagementMode;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.mockito.Mockito.doThrow;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LinkedLearnerProgressServiceTest {
    @Mock private LinkedLearnerReadAuthorizationService authorizationService;
    @Mock private AuthService authService;
    @Mock private DashboardService dashboardService;
    @Mock private ProgressReportService progressReportService;
    @Mock private NoteCollectionService noteCollectionService;
    @Mock private UserRepository userRepository;
    @Mock private AnalyticsService analyticsService;

    private LinkedLearnerProgressService progressService;

    @BeforeEach
    void setUp() {
        progressService = new LinkedLearnerProgressService(
                authorizationService,
                authService,
                dashboardService,
                progressReportService,
                noteCollectionService,
                userRepository,
                analyticsService
        );
    }

    @Test
    void anUnverifiedCallerCannotReadLearnerProgress() {
        // ⚠️ Defence in depth, and it is FREE rather than merely cheap. The sizing query run
        // 2026-08-26 returned zero live relationships of any status, so nothing is broken by
        // adding this; and emailVerifiedAt is monotonic — no call site clears it — so no verified
        // supporter can ever lose access to a connection they already hold.
        UUID callerUserId = UUID.randomUUID();
        doThrow(new AppException("EMAIL_NOT_VERIFIED", "Verify your email.", HttpStatus.FORBIDDEN))
                .when(authService).requireEmailVerified(callerUserId);

        assertThatThrownBy(() -> progressService.getProgress(callerUserId, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("code", "EMAIL_NOT_VERIFIED");

        // The authorization helper is never even consulted, so the gate is a gate.
        verify(authorizationService, never()).requireAcceptedLearnerId(any(), any());
    }

    @Test
    void progressAuthorizedSupporterReadReturnsOnlyAggregateLearnerProgress() {
        UUID callerUserId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        stubAuthorizedRead(callerUserId, relationshipId, learnerUserId);
        when(dashboardService.getMasterySnapshot(learnerUserId))
                .thenReturn(new MasterySnapshotResponse(new BigDecimal("76.50"), new BigDecimal("90.00"), 3));
        when(dashboardService.getStudyEngagement(learnerUserId))
                .thenReturn(new StudyEngagementResponse(EngagementMode.CONSISTENCY, 4, 9, 3));
        when(progressReportService.getProgressReport(eq(learnerUserId), eq(null), any(OffsetDateTime.class)))
                .thenReturn(progressReport(List.of(
                        new SubjectProgressEntry("Private subject", 5, 2, 1, 2, 40),
                        new SubjectProgressEntry("Another private subject", 3, 1, 1, 1, 33)
                )));
        when(noteCollectionService.list(learnerUserId)).thenReturn(List.of(
                collection(4, 3, 2),
                collection(2, 1, 1)
        ));

        LinkedLearnerProgressResponse response = progressService.getProgress(callerUserId, relationshipId);

        assertThat(response.learnerDisplayName()).isEqualTo("Learner Name");
        assertThat(response.quizPerformance().averageRecentScore()).isEqualByComparingTo("76.50");
        assertThat(response.engagement().studyDaysThisWeek()).isEqualTo(3);
        assertThat(response.readiness()).isEqualTo(
                new LinkedLearnerProgressResponse.ReadinessCounts(8, 3, 2, 3, 38));
        assertThat(response.collectionProgress()).isEqualTo(
                new LinkedLearnerProgressResponse.CollectionProgressCounts(2, 6, 4, 3));
        assertThat(response.hasActivity()).isTrue();
        assertThat(Arrays.stream(LinkedLearnerProgressResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName))
                .containsExactly(
                        "relationshipId", "learnerDisplayName", "quizPerformance", "engagement",
                        "readiness", "collectionProgress", "hasActivity");
    }

    @Test
    void learnerWithNoActivityReturnsSuccessfulExplicitlyEmptyAggregates() {
        UUID callerUserId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        stubAuthorizedRead(callerUserId, relationshipId, learnerUserId);
        when(dashboardService.getMasterySnapshot(learnerUserId))
                .thenReturn(new MasterySnapshotResponse(null, null, 0));
        when(dashboardService.getStudyEngagement(learnerUserId))
                .thenReturn(new StudyEngagementResponse(EngagementMode.FOCUSED, 0, 0, 0));
        when(progressReportService.getProgressReport(eq(learnerUserId), eq(null), any(OffsetDateTime.class)))
                .thenReturn(progressReport(List.of()));
        when(noteCollectionService.list(learnerUserId)).thenReturn(List.of());

        LinkedLearnerProgressResponse response = progressService.getProgress(callerUserId, relationshipId);

        assertThat(response.hasActivity()).isFalse();
        assertThat(response.readiness().totalConcepts()).isZero();
        assertThat(response.collectionProgress().collectionCount()).isZero();
        assertThat(response.quizPerformance().averageRecentScore()).isNull();
    }

    @Test
    void supporterViewCreatesNoSessionAndUpdatesNoLearnerSignalsOrCounters() {
        UUID callerUserId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        stubAuthorizedRead(callerUserId, relationshipId, learnerUserId);
        when(dashboardService.getMasterySnapshot(learnerUserId))
                .thenReturn(new MasterySnapshotResponse(null, null, 0));
        when(dashboardService.getStudyEngagement(learnerUserId))
                .thenReturn(new StudyEngagementResponse(EngagementMode.FOCUSED, 0, 0, 0));
        when(progressReportService.getProgressReport(eq(learnerUserId), eq(null), any(OffsetDateTime.class)))
                .thenReturn(progressReport(List.of()));
        when(noteCollectionService.list(learnerUserId)).thenReturn(List.of());

        progressService.getProgress(callerUserId, relationshipId);

        // These are the only service boundaries that reach ConceptHealth, quiz sessions,
        // collection progress, and engagement state. The supporter projection invokes only
        // their established read APIs and performs no direct learner save.
        verify(authorizationService).requireAcceptedLearnerId(callerUserId, relationshipId);
        verify(userRepository).findById(learnerUserId);
        verify(userRepository, never()).save(any(UserEntity.class));
        verify(dashboardService).getMasterySnapshot(learnerUserId);
        verify(dashboardService).getStudyEngagement(learnerUserId);
        verify(progressReportService).getProgressReport(eq(learnerUserId), eq(null), any(OffsetDateTime.class));
        verify(noteCollectionService).list(learnerUserId);
        verify(analyticsService).trackEvent(
                callerUserId,
                com.studysnap.backend.entity.AnalyticsEventType.CONNECTION_PROGRESS_VIEWED,
                relationshipId,
                java.util.Map.of());
        verifyNoMoreInteractions(authorizationService, dashboardService, progressReportService,
                noteCollectionService, userRepository, analyticsService);
    }

    @Test
    void responseShapeContainsNoLearnerAuthoredTextFields() {
        Set<String> topLevelFields = Arrays.stream(LinkedLearnerProgressResponse.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .collect(Collectors.toSet());
        Set<String> readinessFields = Arrays.stream(
                        LinkedLearnerProgressResponse.ReadinessCounts.class.getRecordComponents())
                .map(component -> component.getName().toLowerCase())
                .collect(Collectors.toSet());

        assertThat(topLevelFields).doesNotContain(
                "concept", "concepts", "subject", "subjects", "title", "titles",
                "note", "notes", "content", "summary", "studypackid", "noteid");
        assertThat(readinessFields).doesNotContain("concept", "subject", "title", "note", "content");
    }

    @Test
    void analyticsFailureDoesNotFailAnAuthorizedProgressRead() {
        UUID callerUserId = UUID.randomUUID();
        UUID relationshipId = UUID.randomUUID();
        UUID learnerUserId = UUID.randomUUID();
        stubAuthorizedRead(callerUserId, relationshipId, learnerUserId);
        when(dashboardService.getMasterySnapshot(learnerUserId))
                .thenReturn(new MasterySnapshotResponse(null, null, 0));
        when(dashboardService.getStudyEngagement(learnerUserId))
                .thenReturn(new StudyEngagementResponse(EngagementMode.FOCUSED, 0, 0, 0));
        when(progressReportService.getProgressReport(eq(learnerUserId), eq(null), any(OffsetDateTime.class)))
                .thenReturn(progressReport(List.of()));
        when(noteCollectionService.list(learnerUserId)).thenReturn(List.of());
        doThrow(new IllegalStateException("analytics unavailable"))
                .when(analyticsService).trackEvent(any(), any(), any(), any());

        assertThatCode(() -> progressService.getProgress(callerUserId, relationshipId))
                .doesNotThrowAnyException();
    }

    private void stubAuthorizedRead(UUID callerUserId, UUID relationshipId, UUID learnerUserId) {
        UserEntity learner = new UserEntity();
        learner.setId(learnerUserId);
        learner.setEmail("learner@example.com");
        learner.setFirstName("Learner");
        learner.setLastName("Name");
        learner.setDisplayName("Learner Name");
        when(authorizationService.requireAcceptedLearnerId(callerUserId, relationshipId))
                .thenReturn(learnerUserId);
        when(userRepository.findById(learnerUserId)).thenReturn(Optional.of(learner));
    }

    private ProgressReportResponse progressReport(List<SubjectProgressEntry> subjects) {
        return new ProgressReportResponse(subjects, (GoalSummaryResponse) null, List.of(), null);
    }

    private NoteCollectionSummaryResponse collection(int items, int ready, int practiced) {
        return new NoteCollectionSummaryResponse(
                UUID.randomUUID(),
                "Private collection title",
                "Private description",
                "PRIVATE",
                null,
                null,
                null,
                items,
                ready,
                0,
                practiced,
                Instant.now(),
                Instant.now()
        );
    }
}
