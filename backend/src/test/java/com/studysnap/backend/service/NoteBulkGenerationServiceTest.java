package com.studysnap.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.studysnap.backend.dto.BulkGenerateNotesRequest;
import com.studysnap.backend.dto.BulkGenerateNotesResponse;
import com.studysnap.backend.dto.BulkGenerationFailureReason;
import com.studysnap.backend.dto.AddNoteCollectionItemsRequest;
import com.studysnap.backend.dto.GenerateNoteFromTopicRequest;
import com.studysnap.backend.dto.GenerateNoteFromTopicResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.DomainContext;
import com.studysnap.backend.entity.BulkGenerationResultEntity;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.BulkNoteGenerationQuotaExceededException;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.InvalidBulkGenerationRequestException;
import com.studysnap.backend.exception.CollectionNotFoundException;
import com.studysnap.backend.exception.InvalidCollectionRequestException;
import com.studysnap.backend.exception.InvalidDomainContextException;
import com.studysnap.backend.exception.InvalidNoteLearnerLevelException;
import com.studysnap.backend.exception.MonthlyNoteGenerationLimitReachedException;
import com.studysnap.backend.exception.MultiProgramDomainContextRequiredException;
import com.studysnap.backend.exception.ProfileSetupRequiredException;
import com.studysnap.backend.exception.SubjectTooLongException;
import com.studysnap.backend.repository.BulkGenerationResultRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.repository.CourseProgramCatalogRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteBulkGenerationServiceTest {
    private static final String COURSE_PROGRAM = "Nursing";
    private static final String PROFILE_COURSE_PROGRAM = "Secondary Education";
    private static final String SUBJECT = "Maternal Health";
    private static final String ENGINEERING_ALGEBRA_TOPIC = "Engineering Algebra";
    private static final String INVALID_OVERVIEW_MESSAGE = "Generated note has an invalid overview.";
    private static final UUID CATALOG_PROGRAM_ID = UUID.randomUUID();

    @Mock
    private NoteGenerationService noteGenerationService;
    @Mock
    private NoteService noteService;
    @Mock
    private StudyPackService studyPackService;
    @Mock
    private LlmStudyPackService llmStudyPackService;
    @Mock
    private ContentModerationService contentModerationService;
    @Mock
    private StudyPackGenerationContextResolver generationContextResolver;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CourseProgramCatalogRepository courseProgramCatalogRepository;
    @Mock
    private OnboardingGuardService onboardingGuardService;
    private BulkGenerationResultService bulkGenerationResultService;
    @Mock
    private BulkGenerationResultRepository bulkGenerationResultRepository;
    @Mock
    private MePlanService mePlanService;
    @Mock
    private NoteCollectionService noteCollectionService;

    private NoteBulkGenerationService service;
    private StudyPackGenerationTaskDispatcher taskDispatcher;
    private BulkGenerationFailureReasonNormalizer failureReasonNormalizer;

    @BeforeEach
    void setUp() {
        taskDispatcher = spy(new StudyPackGenerationTaskDispatcher(Runnable::run));
        bulkGenerationResultService = spy(new BulkGenerationResultService(bulkGenerationResultRepository));
        failureReasonNormalizer = spy(new BulkGenerationFailureReasonNormalizer());
        service = new NoteBulkGenerationService(
                noteGenerationService,
                noteService,
                studyPackService,
                llmStudyPackService,
                contentModerationService,
                generationContextResolver,
                taskDispatcher,
                userRepository,
                courseProgramCatalogRepository,
                onboardingGuardService,
                bulkGenerationResultService,
                failureReasonNormalizer,
                mePlanService,
                noteCollectionService,
                50,
                0
        );
        org.mockito.Mockito.lenient().when(courseProgramCatalogRepository.findExistingIds(org.mockito.ArgumentMatchers.any(Collection.class)))
                .thenAnswer(invocation -> List.copyOf(invocation.getArgument(0)));
    }

    @Test
    void queueBatch_rejectsMissingProfileTypeBeforeLoadingOwnerOrQueueingWork() {
        UUID userId = UUID.randomUUID();
        ProfileSetupRequiredException exception = new ProfileSetupRequiredException();
        doThrow(exception).when(onboardingGuardService).assertProfileComplete(userId);
        BulkGenerateNotesRequest request = request(
                List.of("Prenatal Care"),
                COURSE_PROGRAM,
                true
        );

        assertThatThrownBy(() -> service.queueBatch(request, userId, false))
                .isSameAs(exception);

        verify(userRepository, never()).findById(userId);
        verify(noteService, never()).create(any(UpsertNoteRequest.class), any(UUID.class));
    }

    @Test
    void queueBatch_rejectsSubjectExpandedPastStorageBeforeDispatchOrQuotaUse() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        String rawSubject = "x".repeat(61) + "-y";
        BulkGenerateNotesRequest request = new BulkGenerateNotesRequest(
                rawSubject,
                List.of("Prenatal Care"),
                false,
                List.of(CATALOG_PROGRAM_ID),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.queueBatch(request, userId, true))
                .isInstanceOf(SubjectTooLongException.class);

        verify(taskDispatcher, never()).execute(any(Runnable.class));
        verify(noteService, never()).create(any(UpsertNoteRequest.class), any(UUID.class));
        verify(mePlanService, never()).getNoteGenerationsRemaining(any(UUID.class));
    }

    @Test
    void queueBatch_rejectsForeignOrMissingCollectionBeforeDispatch() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = requestWithCollection(collectionId, List.of("Topic"));
        doThrow(new CollectionNotFoundException())
                .when(noteCollectionService).validateNoteAcceptingCollection(collectionId, userId);

        assertThatThrownBy(() -> service.queueBatch(request, userId, false))
                .isInstanceOf(CollectionNotFoundException.class);
        verify(taskDispatcher, never()).execute(any(Runnable.class));
    }

    @Test
    void queueBatch_rejectsGoalCollectionBeforeDispatch() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = requestWithCollection(collectionId, List.of("Topic"));
        doThrow(new InvalidCollectionRequestException("A goal collection cannot contain direct notes."))
                .when(noteCollectionService).validateNoteAcceptingCollection(collectionId, userId);

        assertThatThrownBy(() -> service.queueBatch(request, userId, false))
                .isInstanceOf(InvalidCollectionRequestException.class)
                .hasMessageContaining("goal collection");
        verify(taskDispatcher, never()).execute(any(Runnable.class));
    }

    @Test
    void queueBatch_addsOnlySuccessfullyCreatedNotesToSelectedCollectionInOrder() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID firstNoteId = UUID.randomUUID();
        UUID thirdNoteId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = requestWithCollection(
                collectionId,
                List.of("First Topic", "Failed Topic", "Third Topic")
        );
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                userId, List.of(CATALOG_PROGRAM_ID), null, SUBJECT, null, null
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic(anyString(), eq(context)))
                .thenReturn("First content")
                .thenThrow(new RuntimeException("generation failed"))
                .thenReturn("Third content");
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId)))
                .thenReturn(noteResponse(firstNoteId.toString()), noteResponse(thirdNoteId.toString()));

        service.queueBatch(request, userId, false);

        verify(noteCollectionService).addGeneratedItems(
                collectionId,
                userId,
                List.of(firstNoteId, thirdNoteId),
                null
        );
    }

    @Test
    void queueBatch_threadsNormalizedSectionToCollectionMembership() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = requestWithCollection(collectionId, List.of("Topic"), "  Algebra  ");
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                userId, List.of(CATALOG_PROGRAM_ID), null, SUBJECT, null, null
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic("Topic", context)).thenReturn("Content");
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId))).thenReturn(noteResponse(noteId.toString()));

        service.queueBatch(request, userId, false);

        verify(noteCollectionService).addGeneratedItems(collectionId, userId, List.of(noteId), "Algebra");
    }

    @Test
    void queueBatch_normalizesBlankSectionToNull() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = requestWithCollection(collectionId, List.of("Topic"), "   ");
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                userId, List.of(CATALOG_PROGRAM_ID), null, SUBJECT, null, null
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic("Topic", context)).thenReturn("Content");
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId))).thenReturn(noteResponse(noteId.toString()));

        service.queueBatch(request, userId, false);

        verify(noteCollectionService).addGeneratedItems(collectionId, userId, List.of(noteId), null);
    }

    @Test
    void queueBatch_membershipFailureDoesNotEscapeOrRetryGeneration() {
        UUID userId = UUID.randomUUID();
        UUID collectionId = UUID.randomUUID();
        UUID noteId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = requestWithCollection(collectionId, List.of("Topic"), "Algebra");
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                userId, List.of(CATALOG_PROGRAM_ID), null, SUBJECT, null, null
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic("Topic", context)).thenReturn("Content");
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId))).thenReturn(noteResponse(noteId.toString()));
        doThrow(new CollectionNotFoundException()).when(noteCollectionService).addGeneratedItems(
                collectionId,
                userId,
                List.of(noteId),
                "Algebra"
        );

        BulkGenerateNotesResponse response = service.queueBatch(request, userId, false);

        assertThat(response.queuedTopics()).isEqualTo(1);
        verify(noteService, times(1)).create(any(UpsertNoteRequest.class), eq(userId));
        verify(noteCollectionService, times(1)).addGeneratedItems(any(), any(), any(), any());
        verify(bulkGenerationResultService).recordResult(
                any(), eq(userId), eq(SUBJECT), any(), any(), any(), any(), any(), eq(false), eq(1), eq(1),
                any(), any(), any()
        );
    }

    @Test
    void queueBatch_adminHonorsAuthoringMetadataAndPersistsOwnerDerivedTargetProfileType() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, "Profile Course");
        BulkGenerateNotesRequest request = new BulkGenerateNotesRequest(
                SUBJECT,
                List.of("Prenatal Care", "Labor Stages"),
                true,
                List.of(CATALOG_PROGRAM_ID),
                null,
                DomainContext.NURSING.name(),
                LearnerLevel.BOARD_EXAM_REVIEW.name()
        );
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE,
                COURSE_PROGRAM,
                SUBJECT,
                List.of(),
                DomainContext.NURSING,
                LearnerLevel.BOARD_EXAM_REVIEW
        );
        when(generationContextResolver.resolveForBulkGeneration(
                userId,
                List.of(CATALOG_PROGRAM_ID),
                null,
                SUBJECT,
                DomainContext.NURSING,
                LearnerLevel.BOARD_EXAM_REVIEW
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic(anyString(), eq(context)))
                .thenAnswer(invocation -> "Generated content for " + invocation.getArgument(0));
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId)))
                .thenReturn(noteResponse("note-1"), noteResponse("note-2"));

        BulkGenerateNotesResponse response = service.queueBatch(request, userId, false);

        assertThat(response.resultId()).isNotNull();
        assertThat(response.acceptedTopics()).isEqualTo(2);
        assertThat(response.queuedTopics()).isEqualTo(2);
        assertThat(response.rejectedTopics()).isZero();
        ArgumentCaptor<UpsertNoteRequest> captor = ArgumentCaptor.forClass(UpsertNoteRequest.class);
        verify(noteService, times(2)).create(captor.capture(), eq(userId));
        assertThat(captor.getAllValues()).allSatisfy(noteRequest -> {
            assertThat(noteRequest.subject()).isEqualTo(SUBJECT);
            assertThat(noteRequest.courseProgramIds()).containsExactly(CATALOG_PROGRAM_ID);
            assertThat(noteRequest.courseProgramText()).isNull();
            assertThat(noteRequest.domainContext()).isEqualTo(DomainContext.NURSING.name());
            assertThat(noteRequest.learnerLevel()).isEqualTo(LearnerLevel.BOARD_EXAM_REVIEW.name());
        });
        verify(noteService, times(2))
                .updateVisibility(anyString(), eq(NoteVisibility.PUBLIC.name()), eq(userId));
        verify(studyPackService, times(2)).startAsyncGenerationFromNote(
                anyString(), eq(userId), eq(false), eq(false), eq(context), eq(SUBJECT)
        );
        verify(noteGenerationService, never()).generateFromTopic(any(), any());
        verify(bulkGenerationResultService).recordResult(
                eq(response.resultId()),
                eq(userId),
                eq(SUBJECT),
                eq(COURSE_PROGRAM),
                eq(DomainContext.NURSING),
                eq(LearnerLevel.BOARD_EXAM_REVIEW),
                eq(NoteTargetProfileType.STUDENT.name()),
                any(),
                eq(true),
                eq(2),
                eq(2),
                eq(List.of()),
                eq(List.of()),
                eq(List.of())
        );
    }

    @Test
    void queueBatch_rejectsNonAdminWhenRequestedTopicsExceedRemainingNoteGenerations() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.USER, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(mePlanService.getNoteGenerationsRemaining(userId)).thenReturn(2);
        BulkGenerateNotesRequest request = request(
                List.of("Topic One", "Topic Two", "Topic Three"),
                COURSE_PROGRAM,
                false
        );

        assertThatThrownBy(() -> service.queueBatch(request, userId, true))
                .isInstanceOf(BulkNoteGenerationQuotaExceededException.class)
                .hasMessage("You have 2 topic notes left this cycle. Remove 1 topic to continue.")
                .satisfies(exception -> {
                    BulkNoteGenerationQuotaExceededException quotaException =
                            (BulkNoteGenerationQuotaExceededException) exception;
                    assertThat(quotaException.getRemaining()).isEqualTo(2);
                    assertThat(quotaException.getRequestedCount()).isEqualTo(3);
                });

        verify(taskDispatcher, never()).execute(any(Runnable.class));
        verify(noteGenerationService, never()).generateFromTopic(any(), any());
        verify(noteService, never()).create(any(UpsertNoteRequest.class), any(UUID.class));
        verify(bulkGenerationResultService, never()).recordResult(
                any(UUID.class),
                any(UUID.class),
                anyString(),
                anyString(),
                any(DomainContext.class),
                any(LearnerLevel.class),
                anyString(),
                any(),
                anyBoolean(),
                anyInt(),
                anyInt(),
                any(),
                any(),
                any()
        );
    }

    @Test
    void queueBatch_allowsNonAdminWhenRequestedTopicsEqualRemainingNoteGenerations() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.USER, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(mePlanService.getNoteGenerationsRemaining(userId)).thenReturn(2);
        BulkGenerateNotesRequest request = request(
                List.of("Topic One", "Topic Two"),
                COURSE_PROGRAM,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(userId, COURSE_PROGRAM, SUBJECT, null, null))
                .thenReturn(context);
        when(noteGenerationService.generateFromTopic(
                any(GenerateNoteFromTopicRequest.class), eq(userId), eq(context)
        ))
                .thenReturn(new GenerateNoteFromTopicResponse("Generated content"));
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId)))
                .thenReturn(noteResponse("note-1"), noteResponse("note-2"));

        BulkGenerateNotesResponse response = service.queueBatch(request, userId, true);

        assertThat(response.queuedTopics()).isEqualTo(2);
        verify(taskDispatcher).execute(any(Runnable.class));
        verify(noteGenerationService, times(2)).generateFromTopic(
                any(GenerateNoteFromTopicRequest.class), eq(userId), eq(context)
        );
        verify(bulkGenerationResultService).recordResult(
                eq(response.resultId()),
                eq(userId),
                eq(SUBJECT),
                eq(COURSE_PROGRAM),
                eq(null),
                eq(null),
                eq(NoteTargetProfileType.STUDENT.name()),
                any(),
                eq(false),
                eq(2),
                eq(2),
                eq(List.of()),
                eq(List.of()),
                eq(List.of())
        );
    }

    @Test
    void queueBatch_adminSkipsSubmitTimeNoteGenerationQuotaGate() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(
                List.of("Topic One", "Topic Two", "Topic Three"),
                COURSE_PROGRAM,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(userId, List.of(CATALOG_PROGRAM_ID), null, SUBJECT, null, null))
                .thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic(anyString(), eq(context)))
                .thenReturn("Generated content");
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId)))
                .thenReturn(noteResponse("note-1"), noteResponse("note-2"), noteResponse("note-3"));

        BulkGenerateNotesResponse response = service.queueBatch(request, userId, false);

        assertThat(response.queuedTopics()).isEqualTo(3);
        verify(mePlanService, never()).getNoteGenerationsRemaining(userId);
        verify(taskDispatcher).execute(any(Runnable.class));
        verify(noteGenerationService, never()).generateFromTopic(any(), any());
        verify(noteService, times(3)).create(any(UpsertNoteRequest.class), eq(userId));
    }

    @Test
    void queueBatch_teacherUsesProfileDerivedStorageValueAndProfileLearnerLevel() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.USER, ProfileType.TEACHER, LearnerLevel.SENIOR_HIGH, PROFILE_COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(
                List.of("Classroom Assessment"),
                COURSE_PROGRAM,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.SENIOR_HIGH, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                userId,
                List.of(CATALOG_PROGRAM_ID),
                null,
                SUBJECT,
                null,
                null
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic("Classroom Assessment", context)).thenReturn("Content");
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId))).thenReturn(noteResponse("note-1"));

        service.queueBatch(request, userId, false);

        ArgumentCaptor<UpsertNoteRequest> captor = ArgumentCaptor.forClass(UpsertNoteRequest.class);
        verify(noteService).create(captor.capture(), eq(userId));
        assertThat(captor.getValue().courseProgramIds()).containsExactly(CATALOG_PROGRAM_ID);
        assertThat(captor.getValue().courseProgramText()).isNull();
    }

    @Test
    void queueBatch_nonTeacherAcceptsAuthoringAxesWithoutClientCategorization() {
        UUID userId = UUID.randomUUID();
        mockUser(
                userId,
                UserRole.USER,
                ProfileType.BOARD_EXAM,
                LearnerLevel.BOARD_EXAM_REVIEW,
                PROFILE_COURSE_PROGRAM
        );
        BulkGenerateNotesRequest request = new BulkGenerateNotesRequest(
                SUBJECT,
                List.of("Licensure Review"),
                false,
                List.of(),
                "Ignored Course",
                DomainContext.PROFESSIONAL_PRACTICE_AND_REGULATION.name(),
                LearnerLevel.BOARD_EXAM_REVIEW.name()
        );
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.BOARD_EXAM_REVIEW,
                PROFILE_COURSE_PROGRAM,
                SUBJECT,
                List.of(),
                DomainContext.PROFESSIONAL_PRACTICE_AND_REGULATION,
                LearnerLevel.BOARD_EXAM_REVIEW
        );
        when(generationContextResolver.resolveForBulkGeneration(
                userId,
                "Ignored Course",
                SUBJECT,
                DomainContext.PROFESSIONAL_PRACTICE_AND_REGULATION,
                LearnerLevel.BOARD_EXAM_REVIEW
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic("Licensure Review", context)).thenReturn("Content");
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId))).thenReturn(noteResponse("note-1"));

        service.queueBatch(request, userId, false);

        ArgumentCaptor<UpsertNoteRequest> captor = ArgumentCaptor.forClass(UpsertNoteRequest.class);
        verify(noteService).create(captor.capture(), eq(userId));
        assertThat(captor.getValue().courseProgramIds()).isEmpty();
        assertThat(captor.getValue().courseProgramText()).isEqualTo("Ignored Course");
        assertThat(captor.getValue().domainContext())
                .isEqualTo(DomainContext.PROFESSIONAL_PRACTICE_AND_REGULATION.name());
        assertThat(captor.getValue().learnerLevel()).isEqualTo(LearnerLevel.BOARD_EXAM_REVIEW.name());
    }

    @Test
    void queueBatch_rejectsUnknownDomainContextBeforeDispatch() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = new BulkGenerateNotesRequest(
                SUBJECT,
                List.of(ENGINEERING_ALGEBRA_TOPIC),
                false,
                List.of(CATALOG_PROGRAM_ID),
                null,
                "engineering_math",
                null
        );

        assertThatThrownBy(() -> service.queueBatch(request, userId, false))
                .isInstanceOf(InvalidDomainContextException.class)
                .hasMessageContaining("domainContext");

        verify(taskDispatcher, never()).execute(any(Runnable.class));
    }

    @Test
    void queueBatch_rejectsUnknownLearnerLevelBeforeDispatch() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = new BulkGenerateNotesRequest(
                SUBJECT,
                List.of(ENGINEERING_ALGEBRA_TOPIC),
                false,
                List.of(CATALOG_PROGRAM_ID),
                null,
                DomainContext.ENGINEERING_MATHEMATICS.name(),
                "university"
        );

        assertThatThrownBy(() -> service.queueBatch(request, userId, false))
                .isInstanceOf(InvalidNoteLearnerLevelException.class)
                .hasMessageContaining("learnerLevel");

        verify(taskDispatcher, never()).execute(any(Runnable.class));
    }

    @Test
    void queueBatch_isolatesContentGenerationFailuresAndRecordsFailedTopics() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(
                List.of("Rejected Topic", "Healthy Topic"),
                COURSE_PROGRAM,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                userId,
                List.of(CATALOG_PROGRAM_ID),
                null,
                SUBJECT,
                null,
                null
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic("Rejected Topic", context))
                .thenThrow(new RuntimeException("moderation rejected"));
        when(llmStudyPackService.generateNoteFromTopic("Healthy Topic", context)).thenReturn("Healthy content");
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId))).thenReturn(noteResponse("note-healthy"));

        BulkGenerateNotesResponse response = service.queueBatch(request, userId, false);

        assertThat(response.queuedTopics()).isEqualTo(2);
        ArgumentCaptor<UpsertNoteRequest> captor = ArgumentCaptor.forClass(UpsertNoteRequest.class);
        verify(noteService).create(captor.capture(), eq(userId));
        assertThat(captor.getValue().title()).isEqualTo("Healthy Topic");
        verify(bulkGenerationResultService).recordResult(
                eq(response.resultId()),
                eq(userId),
                eq(SUBJECT),
                eq(COURSE_PROGRAM),
                eq(null),
                eq(null),
                eq(NoteTargetProfileType.STUDENT.name()),
                any(),
                eq(false),
                eq(2),
                eq(1),
                eq(List.of("Rejected Topic")),
                any(),
                eq(List.of())
        );
    }

    @Test
    void queueBatch_persistsAppExceptionCodeAndMessageThroughProcessBatch() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(List.of("Invalid Overview"), COURSE_PROGRAM, false);
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                userId, List.of(CATALOG_PROGRAM_ID), null, SUBJECT, null, null
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic("Invalid Overview", context))
                .thenThrow(new TestLlmInvalidOutputException());

        service.queueBatch(request, userId, false);

        BulkGenerationResultEntity receipt = savedReceipt();
        assertThat(receipt.getFailedTopicReasons()).containsExactly(
                new BulkGenerationFailureReason(
                        "Invalid Overview",
                        TestLlmInvalidOutputException.CODE,
                        INVALID_OVERVIEW_MESSAGE
                )
        );
    }

    @Test
    void queueBatch_persistsNamedSubjectBoundReasonInsteadOfUnexpectedError() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(List.of("Bounded Topic"), COURSE_PROGRAM, false);
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                userId, List.of(CATALOG_PROGRAM_ID), null, SUBJECT, null, null
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic("Bounded Topic", context)).thenReturn("Generated content");
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId))).thenThrow(new SubjectTooLongException());

        service.queueBatch(request, userId, false);

        assertThat(savedReceipt().getFailedTopicReasons()).containsExactly(
                new BulkGenerationFailureReason(
                        "Bounded Topic",
                        "SUBJECT_TOO_LONG",
                        "Subject must be 64 characters or less."
                )
        );
    }

    @Test
    void queueBatch_persistsGenericReasonWithoutNonAppExceptionMessage() {
        UUID userId = UUID.randomUUID();
        String sensitiveMessage = "provider response contained secret payload";
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(List.of("Provider Failure"), COURSE_PROGRAM, false);
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                userId, List.of(CATALOG_PROGRAM_ID), null, SUBJECT, null, null
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic("Provider Failure", context))
                .thenThrow(new IllegalStateException(sensitiveMessage));

        service.queueBatch(request, userId, false);

        BulkGenerationFailureReason reason = savedReceipt().getFailedTopicReasons().getFirst();
        assertThat(reason.code()).isEqualTo(BulkGenerationFailureReasonNormalizer.UNEXPECTED_ERROR_CODE);
        assertThat(reason.reason())
                .contains(IllegalStateException.class.getSimpleName())
                .doesNotContain(sensitiveMessage);
    }

    @Test
    void queueBatch_preservesFailedTopicsJsonShapeContentAndOrder() throws Exception {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(
                List.of("First Failed Topic", "Second Failed Topic"),
                COURSE_PROGRAM,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                userId, List.of(CATALOG_PROGRAM_ID), null, SUBJECT, null, null
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic(anyString(), eq(context)))
                .thenThrow(new IllegalStateException("first"), new IllegalArgumentException("second"));

        service.queueBatch(request, userId, false);

        String persistedJson = new ObjectMapper().writeValueAsString(savedReceipt().getFailedTopics());
        assertThat(persistedJson).isEqualTo("[\"First Failed Topic\",\"Second Failed Topic\"]");
    }

    @Test
    void queueBatch_classifiesQuotaAndGenerationFailuresForNonAdmins() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.USER, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(
                List.of("Healthy Topic", "Over Limit Topic", "Broken Topic"),
                COURSE_PROGRAM,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(mePlanService.getNoteGenerationsRemaining(userId)).thenReturn(3);
        when(generationContextResolver.resolveForBulkGeneration(userId, COURSE_PROGRAM, SUBJECT, null, null))
                .thenReturn(context);
        when(noteGenerationService.generateFromTopic(
                any(GenerateNoteFromTopicRequest.class), eq(userId), eq(context)
        ))
                .thenAnswer(invocation -> {
                    GenerateNoteFromTopicRequest generationRequest = invocation.getArgument(0);
                    if ("Over Limit Topic".equals(generationRequest.topic())) {
                        throw new MonthlyNoteGenerationLimitReachedException();
                    }
                    if ("Broken Topic".equals(generationRequest.topic())) {
                        throw new RuntimeException("generation failed");
                    }
                    return new GenerateNoteFromTopicResponse("Healthy content");
                });
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId))).thenReturn(noteResponse("note-healthy"));

        BulkGenerateNotesResponse response = service.queueBatch(request, userId, true);

        verify(noteService).create(any(UpsertNoteRequest.class), eq(userId));
        verify(bulkGenerationResultService).recordResult(
                eq(response.resultId()),
                eq(userId),
                eq(SUBJECT),
                eq(COURSE_PROGRAM),
                eq(null),
                eq(null),
                eq(NoteTargetProfileType.STUDENT.name()),
                any(),
                eq(false),
                eq(3),
                eq(1),
                eq(List.of("Broken Topic")),
                any(),
                eq(List.of("Over Limit Topic"))
        );
        assertThat(savedReceipt().getFailedTopicReasons())
                .extracting(BulkGenerationFailureReason::topic)
                .containsExactly("Broken Topic")
                .doesNotContain("Over Limit Topic");
    }

    @Test
    void queueBatch_fallsBackForOneNormalizationFailureAndRecordsTheRest() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(
                List.of("Fallback Topic", "App Topic"),
                COURSE_PROGRAM,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                userId, List.of(CATALOG_PROGRAM_ID), null, SUBJECT, null, null
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic(anyString(), eq(context)))
                .thenThrow(new IllegalStateException("original failure"), new TestLlmInvalidOutputException());
        doThrow(new IllegalStateException("normalizer failed"))
                .doCallRealMethod()
                .when(failureReasonNormalizer)
                .normalize(anyString(), any(RuntimeException.class));

        service.queueBatch(request, userId, false);

        BulkGenerationResultEntity receipt = savedReceipt();
        assertThat(receipt.getFailedTopicReasons()).containsExactly(
                BulkGenerationFailureReasonNormalizer.unexpected(
                        "Fallback Topic",
                        new IllegalStateException()
                ),
                new BulkGenerationFailureReason(
                        "App Topic",
                        TestLlmInvalidOutputException.CODE,
                        INVALID_OVERVIEW_MESSAGE
                )
        );
    }

    @Test
    void queueBatch_studyPackFailureAfterNoteCreateDoesNotRecordFailedTopic() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(
                List.of("Healthy Topic"),
                COURSE_PROGRAM,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(userId, List.of(CATALOG_PROGRAM_ID), null, SUBJECT, null, null))
                .thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic("Healthy Topic", context)).thenReturn("Healthy content");
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId))).thenReturn(noteResponse("note-healthy"));
        doThrow(new RuntimeException("pack generation failed")).when(studyPackService)
                .startAsyncGenerationFromNote("note-healthy", userId, false, false, context, SUBJECT);

        BulkGenerateNotesResponse response = service.queueBatch(request, userId, false);

        verify(noteService).create(any(UpsertNoteRequest.class), eq(userId));
        verify(bulkGenerationResultService).recordResult(
                eq(response.resultId()),
                eq(userId),
                eq(SUBJECT),
                eq(COURSE_PROGRAM),
                eq(null),
                eq(null),
                eq(NoteTargetProfileType.STUDENT.name()),
                any(),
                eq(false),
                eq(1),
                eq(1),
                eq(List.of()),
                eq(List.of()),
                eq(List.of())
        );
    }

    @Test
    void queueBatch_recordsAllTopicsWhenBatchFailsBeforeLoop() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(
                List.of("Topic One", "Topic Two"),
                COURSE_PROGRAM,
                false
        );
        when(generationContextResolver.resolveForBulkGeneration(userId, List.of(CATALOG_PROGRAM_ID), null, SUBJECT, null, null))
                .thenThrow(new RuntimeException("context unavailable"));

        BulkGenerateNotesResponse response = service.queueBatch(request, userId, false);

        verify(noteService, never()).create(any(UpsertNoteRequest.class), any(UUID.class));
        verify(bulkGenerationResultService).recordResult(
                eq(response.resultId()),
                eq(userId),
                eq(SUBJECT),
                eq(null),
                eq(null),
                eq(null),
                eq(NoteTargetProfileType.STUDENT.name()),
                any(),
                eq(false),
                eq(2),
                eq(0),
                eq(List.of("Topic One", "Topic Two")),
                any(),
                eq(List.of())
        );
    }

    @Test
    void queueBatch_rejectsMissingSubjectEmptyTopicsAndOverCap() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest missingSubject = new BulkGenerateNotesRequest(
                " ", List.of("Topic"), false, List.of(CATALOG_PROGRAM_ID), null, null, null
        );
        BulkGenerateNotesRequest emptyTopics = request(
                List.of(), COURSE_PROGRAM, false
        );
        List<String> tooManyTopics = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(index -> "Topic " + index)
                .toList();
        BulkGenerateNotesRequest overCap = request(
                tooManyTopics, COURSE_PROGRAM, false
        );

        assertThatThrownBy(() -> service.queueBatch(missingSubject, userId, false))
                .isInstanceOf(InvalidBulkGenerationRequestException.class)
                .hasMessage("Subject is required.");
        assertThatThrownBy(() -> service.queueBatch(emptyTopics, userId, false))
                .isInstanceOf(InvalidBulkGenerationRequestException.class)
                .hasMessage("Add at least one topic.");
        assertThatThrownBy(() -> service.queueBatch(overCap, userId, false))
                .isInstanceOf(InvalidBulkGenerationRequestException.class)
                .hasMessage("You can bulk generate up to 50 topics at once.");
    }

    @Test
    void queueBatch_rejectsTeacherMissingRequiredCourseMetadata() {
        UUID teacherId = UUID.randomUUID();
        mockUser(teacherId, UserRole.USER, ProfileType.TEACHER, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest missingCourse = new BulkGenerateNotesRequest(
                SUBJECT, List.of("Topic"), false, List.of(), null, null, null
        );

        assertThatThrownBy(() -> service.queueBatch(missingCourse, teacherId, false))
                .isInstanceOf(com.studysnap.backend.exception.CourseProgramSelectionRequiredException.class)
                .hasMessage("Choose at least one course or program.");
    }

    @Test
    void queueBatch_curatorMultiProgramWithoutDomainContextStillRejects() {
        UUID adminId = UUID.randomUUID();
        UUID secondProgramId = UUID.randomUUID();
        mockUser(adminId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = new BulkGenerateNotesRequest(
                SUBJECT,
                List.of("Topic"),
                false,
                List.of(CATALOG_PROGRAM_ID, secondProgramId),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> service.queueBatch(request, adminId, false))
                .isInstanceOf(MultiProgramDomainContextRequiredException.class);

        verify(taskDispatcher, never()).execute(any(Runnable.class));
        verify(noteService, never()).create(any(UpsertNoteRequest.class), any(UUID.class));
    }

    @Test
    void queueBatch_curatorRoleMidOnboardingTakesTheLearnerBranch() {
        // The v0.71.0 fix in adfa797f: nobody curates during onboarding. A curator-role account that
        // reaches an authoring path before completing onboarding must NOT be asked for courseProgramIds,
        // because no onboarding screen can supply them -- that is what made onboarding uncompletable for
        // every ADMIN account. It takes the learner branch and its free-text program instead.
        UUID adminId = UUID.randomUUID();
        mockMidOnboardingUser(adminId, UserRole.ADMIN, ProfileType.STUDENT);
        BulkGenerateNotesRequest requestWithoutCatalogIds = new BulkGenerateNotesRequest(
                SUBJECT, List.of("Topic"), false, List.of(), COURSE_PROGRAM, null, null, null, null
        );
        StudyPackGenerationContext context = new StudyPackGenerationContext(
                LearnerLevel.COLLEGE, COURSE_PROGRAM, SUBJECT, List.of(), null, null
        );
        when(generationContextResolver.resolveForBulkGeneration(
                adminId, COURSE_PROGRAM, SUBJECT, null, null
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic("Topic", context)).thenReturn("Content");
        when(noteService.create(any(UpsertNoteRequest.class), eq(adminId))).thenReturn(noteResponse("note-1"));

        service.queueBatch(requestWithoutCatalogIds, adminId, false);

        ArgumentCaptor<UpsertNoteRequest> captor = ArgumentCaptor.forClass(UpsertNoteRequest.class);
        verify(noteService).create(captor.capture(), eq(adminId));
        assertThat(captor.getValue().courseProgramIds()).isEmpty();
        assertThat(captor.getValue().courseProgramText()).isEqualTo(COURSE_PROGRAM);
    }

    @Test
    void queueBatch_onboardedCuratorStillCurates() {
        // Scope guard: the onboarding check must not demote a genuine curator, mirroring the two
        // assertions adfa797f added for the paths it corrected.
        UUID adminId = UUID.randomUUID();
        mockUser(adminId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(
                List.of("Topic"), COURSE_PROGRAM, false
        );
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                adminId, List.of(CATALOG_PROGRAM_ID), null, SUBJECT, null, null
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic("Topic", context)).thenReturn("Content");
        when(noteService.create(any(UpsertNoteRequest.class), eq(adminId))).thenReturn(noteResponse("note-1"));

        service.queueBatch(request, adminId, false);

        ArgumentCaptor<UpsertNoteRequest> captor = ArgumentCaptor.forClass(UpsertNoteRequest.class);
        verify(noteService).create(captor.capture(), eq(adminId));
        assertThat(captor.getValue().courseProgramIds()).containsExactly(CATALOG_PROGRAM_ID);
        assertThat(captor.getValue().courseProgramText()).isNull();
    }

    @Test
    void queueBatch_adminAllowsMissingProfileLearnerLevel() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, null, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(
                List.of("Topic"), COURSE_PROGRAM, false
        );
        StudyPackGenerationContext context = context(null, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(userId, List.of(CATALOG_PROGRAM_ID), null, SUBJECT, null, null))
                .thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic("Topic", context)).thenReturn("Content");
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId))).thenReturn(noteResponse("note-1"));

        service.queueBatch(request, userId, false);

        verify(studyPackService).startAsyncGenerationFromNote(
                "note-1", userId, false, false, context, SUBJECT
        );
        assertThat(context.learnerLevel()).isNull();
    }

    @Test
    void queueBatch_honorsConfiguredTopicCap() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        NoteBulkGenerationService limitedService = new NoteBulkGenerationService(
                noteGenerationService,
                noteService,
                studyPackService,
                llmStudyPackService,
                contentModerationService,
                generationContextResolver,
                new StudyPackGenerationTaskDispatcher(Runnable::run),
                userRepository,
                onboardingGuardService,
                bulkGenerationResultService,
                mePlanService,
                1,
                0
        );
        BulkGenerateNotesRequest request = request(
                List.of("Topic One", "Topic Two"),
                COURSE_PROGRAM,
                false
        );

        assertThatThrownBy(() -> limitedService.queueBatch(request, userId, false))
                .isInstanceOf(InvalidBulkGenerationRequestException.class)
                .hasMessage("You can bulk generate up to 1 topics at once.");
    }

    private BulkGenerateNotesRequest request(
            List<String> topics,
            String courseProgram,
            boolean makePublic
    ) {
        return new BulkGenerateNotesRequest(
                SUBJECT,
                topics,
                makePublic,
                List.of(CATALOG_PROGRAM_ID),
                null,
                null,
                null
        );
    }

    private BulkGenerateNotesRequest requestWithCollection(UUID collectionId, List<String> topics) {
        return requestWithCollection(collectionId, topics, null);
    }

    private BulkGenerateNotesRequest requestWithCollection(
            UUID collectionId,
            List<String> topics,
            String sectionLabel
    ) {
        return new BulkGenerateNotesRequest(
                SUBJECT,
                topics,
                false,
                List.of(CATALOG_PROGRAM_ID),
                null,
                null,
                null,
                collectionId,
                sectionLabel
        );
    }

    private void mockUser(
            UUID userId,
            UserRole role,
            ProfileType profileType,
            LearnerLevel learnerLevel,
            String courseProgram
    ) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setRole(role);
        user.setProfileType(profileType);
        user.setLearnerLevel(learnerLevel);
        user.setCourseProgram(courseProgram);
        // Onboarded by default: bulk generate is unreachable until onboarding completes
        // (requireAuthenticatedOnboardedUser), so a null value here would model a state the surface
        // cannot be in -- and would silently route every curator test down the learner branch.
        user.setOnboardingCompletedAt(OffsetDateTime.now());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private void mockMidOnboardingUser(UUID userId, UserRole role, ProfileType profileType) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setRole(role);
        user.setProfileType(profileType);
        user.setLearnerLevel(LearnerLevel.COLLEGE);
        user.setCourseProgram(COURSE_PROGRAM);
        user.setOnboardingCompletedAt(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private StudyPackGenerationContext context(LearnerLevel learnerLevel, String courseProgram) {
        return new StudyPackGenerationContext(learnerLevel, courseProgram, SUBJECT, List.of());
    }

    private BulkGenerationResultEntity savedReceipt() {
        ArgumentCaptor<BulkGenerationResultEntity> captor =
                ArgumentCaptor.forClass(BulkGenerationResultEntity.class);
        verify(bulkGenerationResultRepository).save(captor.capture());
        return captor.getValue();
    }

    private NoteResponse noteResponse(String id) {
        return new NoteResponse(
                id,
                "Title",
                SUBJECT,
                COURSE_PROGRAM,
                null,
                null,
                List.of(),
                "Content",
                NoteVisibility.PRIVATE.name(),
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                null,
                null,
                false,
                null,
                null,
                "DRAFT",
                null,
                List.of(),
                List.of(),
                null,
                null,
                0,
                false,
                false,
                false
        );
    }

    private static final class TestLlmInvalidOutputException extends AppException {
        private static final String CODE = "LLM_INVALID_OUTPUT";

        private TestLlmInvalidOutputException() {
            super(CODE, INVALID_OVERVIEW_MESSAGE, HttpStatus.BAD_GATEWAY);
        }
    }
}
