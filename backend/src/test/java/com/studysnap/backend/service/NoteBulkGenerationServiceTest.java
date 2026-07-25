package com.studysnap.backend.service;

import com.studysnap.backend.dto.BulkGenerateNotesRequest;
import com.studysnap.backend.dto.BulkGenerateNotesResponse;
import com.studysnap.backend.dto.GenerateNoteFromTopicRequest;
import com.studysnap.backend.dto.GenerateNoteFromTopicResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.BulkNoteGenerationQuotaExceededException;
import com.studysnap.backend.exception.InvalidBulkGenerationRequestException;
import com.studysnap.backend.exception.MonthlyNoteGenerationLimitReachedException;
import com.studysnap.backend.exception.ProfileSetupRequiredException;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
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
    private OnboardingGuardService onboardingGuardService;
    @Mock
    private BulkGenerationResultService bulkGenerationResultService;
    @Mock
    private MePlanService mePlanService;

    private NoteBulkGenerationService service;
    private StudyPackGenerationTaskDispatcher taskDispatcher;

    @BeforeEach
    void setUp() {
        taskDispatcher = spy(new StudyPackGenerationTaskDispatcher(Runnable::run));
        service = new NoteBulkGenerationService(
                noteGenerationService,
                noteService,
                studyPackService,
                llmStudyPackService,
                contentModerationService,
                generationContextResolver,
                taskDispatcher,
                userRepository,
                onboardingGuardService,
                bulkGenerationResultService,
                mePlanService,
                50,
                0
        );
    }

    @Test
    void queueBatch_rejectsMissingProfileTypeBeforeLoadingOwnerOrQueueingWork() {
        UUID userId = UUID.randomUUID();
        ProfileSetupRequiredException exception = new ProfileSetupRequiredException();
        doThrow(exception).when(onboardingGuardService).assertProfileComplete(userId);
        BulkGenerateNotesRequest request = request(
                List.of("Prenatal Care"),
                COURSE_PROGRAM,
                NoteTargetProfileType.BOARD_TAKER,
                true
        );

        assertThatThrownBy(() -> service.queueBatch(request, userId, false))
                .isSameAs(exception);

        verify(userRepository, never()).findById(userId);
        verify(noteService, never()).create(any(UpsertNoteRequest.class), any(UUID.class));
    }

    @Test
    void queueBatch_adminHonorsExplicitMetadataAndBypassesUsagePaths() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, "Profile Course");
        BulkGenerateNotesRequest request = request(
                List.of("Prenatal Care", "Labor Stages"),
                COURSE_PROGRAM,
                NoteTargetProfileType.BOARD_TAKER,
                true
        );
        StudyPackGenerationContext context = context(
                LearnerLevel.COLLEGE,
                COURSE_PROGRAM
        );
        when(generationContextResolver.resolveForBulkGeneration(
                userId,
                COURSE_PROGRAM,
                SUBJECT
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
            assertThat(noteRequest.courseProgram()).isEqualTo(COURSE_PROGRAM);
            assertThat(noteRequest.targetProfileType()).isEqualTo(NoteTargetProfileType.BOARD_TAKER.name());
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
                eq(NoteTargetProfileType.BOARD_TAKER.name()),
                eq(true),
                eq(2),
                eq(2),
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
                NoteTargetProfileType.STUDENT,
                false
        );

        assertThatThrownBy(() -> service.queueBatch(request, userId, true))
                .isInstanceOf(BulkNoteGenerationQuotaExceededException.class)
                .hasMessage("You have 2 note generation(s) left this cycle. Remove 1 topic(s) to continue.")
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
                anyString(),
                anyBoolean(),
                anyInt(),
                anyInt(),
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
                NoteTargetProfileType.STUDENT,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(userId, COURSE_PROGRAM, SUBJECT))
                .thenReturn(context);
        when(noteGenerationService.generateFromTopic(any(GenerateNoteFromTopicRequest.class), eq(userId)))
                .thenReturn(new GenerateNoteFromTopicResponse("Generated content"));
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId)))
                .thenReturn(noteResponse("note-1"), noteResponse("note-2"));

        BulkGenerateNotesResponse response = service.queueBatch(request, userId, true);

        assertThat(response.queuedTopics()).isEqualTo(2);
        verify(taskDispatcher).execute(any(Runnable.class));
        verify(noteGenerationService, times(2)).generateFromTopic(any(GenerateNoteFromTopicRequest.class), eq(userId));
        verify(bulkGenerationResultService).recordResult(
                eq(response.resultId()),
                eq(userId),
                eq(SUBJECT),
                eq(COURSE_PROGRAM),
                eq(NoteTargetProfileType.STUDENT.name()),
                eq(false),
                eq(2),
                eq(2),
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
                NoteTargetProfileType.STUDENT,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(userId, COURSE_PROGRAM, SUBJECT))
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
    void queueBatch_teacherUsesExplicitCategorizationAndProfileLearnerLevel() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.USER, ProfileType.TEACHER, LearnerLevel.SENIOR_HIGH, PROFILE_COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(
                List.of("Classroom Assessment"),
                COURSE_PROGRAM,
                NoteTargetProfileType.STUDENT,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.SENIOR_HIGH, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                userId,
                COURSE_PROGRAM,
                SUBJECT
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic("Classroom Assessment", context)).thenReturn("Content");
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId))).thenReturn(noteResponse("note-1"));

        service.queueBatch(request, userId, false);

        ArgumentCaptor<UpsertNoteRequest> captor = ArgumentCaptor.forClass(UpsertNoteRequest.class);
        verify(noteService).create(captor.capture(), eq(userId));
        assertThat(captor.getValue().courseProgram()).isEqualTo(COURSE_PROGRAM);
        assertThat(captor.getValue().targetProfileType()).isEqualTo(NoteTargetProfileType.STUDENT.name());
    }

    @Test
    void queueBatch_nonTeacherIgnoresClientMetadataAndUsesProfile() {
        UUID userId = UUID.randomUUID();
        mockUser(
                userId,
                UserRole.USER,
                ProfileType.BOARD_EXAM,
                LearnerLevel.BOARD_EXAM_REVIEW,
                PROFILE_COURSE_PROGRAM
        );
        BulkGenerateNotesRequest request = request(
                List.of("Licensure Review"),
                "Ignored Course",
                NoteTargetProfileType.PROFESSIONAL,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.BOARD_EXAM_REVIEW, PROFILE_COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                userId,
                PROFILE_COURSE_PROGRAM,
                SUBJECT
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic("Licensure Review", context)).thenReturn("Content");
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId))).thenReturn(noteResponse("note-1"));

        service.queueBatch(request, userId, false);

        ArgumentCaptor<UpsertNoteRequest> captor = ArgumentCaptor.forClass(UpsertNoteRequest.class);
        verify(noteService).create(captor.capture(), eq(userId));
        assertThat(captor.getValue().courseProgram()).isEqualTo(PROFILE_COURSE_PROGRAM);
        assertThat(captor.getValue().targetProfileType()).isEqualTo(NoteTargetProfileType.BOARD_TAKER.name());
    }

    @Test
    void queueBatch_isolatesContentGenerationFailuresAndRecordsFailedTopics() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(
                List.of("Rejected Topic", "Healthy Topic"),
                COURSE_PROGRAM,
                NoteTargetProfileType.STUDENT,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                userId,
                COURSE_PROGRAM,
                SUBJECT
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
                eq(NoteTargetProfileType.STUDENT.name()),
                eq(false),
                eq(2),
                eq(1),
                eq(List.of("Rejected Topic")),
                eq(List.of())
        );
    }

    @Test
    void queueBatch_classifiesQuotaAndGenerationFailuresForNonAdmins() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.USER, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(
                List.of("Healthy Topic", "Over Limit Topic", "Broken Topic"),
                COURSE_PROGRAM,
                NoteTargetProfileType.STUDENT,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(mePlanService.getNoteGenerationsRemaining(userId)).thenReturn(3);
        when(generationContextResolver.resolveForBulkGeneration(userId, COURSE_PROGRAM, SUBJECT))
                .thenReturn(context);
        when(noteGenerationService.generateFromTopic(any(GenerateNoteFromTopicRequest.class), eq(userId)))
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
                eq(NoteTargetProfileType.STUDENT.name()),
                eq(false),
                eq(3),
                eq(1),
                eq(List.of("Broken Topic")),
                eq(List.of("Over Limit Topic"))
        );
    }

    @Test
    void queueBatch_studyPackFailureAfterNoteCreateDoesNotRecordFailedTopic() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(
                List.of("Healthy Topic"),
                COURSE_PROGRAM,
                NoteTargetProfileType.STUDENT,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(userId, COURSE_PROGRAM, SUBJECT))
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
                eq(NoteTargetProfileType.STUDENT.name()),
                eq(false),
                eq(1),
                eq(1),
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
                NoteTargetProfileType.STUDENT,
                false
        );
        when(generationContextResolver.resolveForBulkGeneration(userId, COURSE_PROGRAM, SUBJECT))
                .thenThrow(new RuntimeException("context unavailable"));

        BulkGenerateNotesResponse response = service.queueBatch(request, userId, false);

        verify(noteService, never()).create(any(UpsertNoteRequest.class), any(UUID.class));
        verify(bulkGenerationResultService).recordResult(
                eq(response.resultId()),
                eq(userId),
                eq(SUBJECT),
                eq(COURSE_PROGRAM),
                eq(NoteTargetProfileType.STUDENT.name()),
                eq(false),
                eq(2),
                eq(0),
                eq(List.of("Topic One", "Topic Two")),
                eq(List.of())
        );
    }

    @Test
    void queueBatch_rejectsMissingSubjectEmptyTopicsAndOverCap() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest missingSubject = new BulkGenerateNotesRequest(
                " ", List.of("Topic"), false, COURSE_PROGRAM, NoteTargetProfileType.STUDENT
        );
        BulkGenerateNotesRequest emptyTopics = request(
                List.of(), COURSE_PROGRAM, NoteTargetProfileType.STUDENT, false
        );
        List<String> tooManyTopics = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(index -> "Topic " + index)
                .toList();
        BulkGenerateNotesRequest overCap = request(
                tooManyTopics, COURSE_PROGRAM, NoteTargetProfileType.STUDENT, false
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
    void queueBatch_rejectsTeacherMissingRequiredMetadata() {
        UUID teacherId = UUID.randomUUID();
        mockUser(teacherId, UserRole.USER, ProfileType.TEACHER, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest missingCourse = request(
                List.of("Topic"), " ", NoteTargetProfileType.STUDENT, false
        );
        BulkGenerateNotesRequest missingTarget = request(
                List.of("Topic"), COURSE_PROGRAM, null, false
        );

        assertThatThrownBy(() -> service.queueBatch(missingCourse, teacherId, false))
                .isInstanceOf(InvalidBulkGenerationRequestException.class)
                .hasMessage("Course/program is required.");
        assertThatThrownBy(() -> service.queueBatch(missingTarget, teacherId, false))
                .isInstanceOf(InvalidBulkGenerationRequestException.class)
                .hasMessage("Target audience is required for teachers and admins.");
    }

    @Test
    void queueBatch_adminAllowsMissingProfileLearnerLevel() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, null, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(
                List.of("Topic"), COURSE_PROGRAM, NoteTargetProfileType.STUDENT, false
        );
        StudyPackGenerationContext context = context(null, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(userId, COURSE_PROGRAM, SUBJECT))
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
                NoteTargetProfileType.STUDENT,
                false
        );

        assertThatThrownBy(() -> limitedService.queueBatch(request, userId, false))
                .isInstanceOf(InvalidBulkGenerationRequestException.class)
                .hasMessage("You can bulk generate up to 1 topics at once.");
    }

    private BulkGenerateNotesRequest request(
            List<String> topics,
            String courseProgram,
            NoteTargetProfileType targetProfileType,
            boolean makePublic
    ) {
        return new BulkGenerateNotesRequest(
                SUBJECT,
                topics,
                makePublic,
                courseProgram,
                targetProfileType
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
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    }

    private StudyPackGenerationContext context(LearnerLevel learnerLevel, String courseProgram) {
        return new StudyPackGenerationContext(learnerLevel, courseProgram, SUBJECT, List.of());
    }

    private NoteResponse noteResponse(String id) {
        return new NoteResponse(
                id,
                "Title",
                SUBJECT,
                COURSE_PROGRAM,
                NoteTargetProfileType.STUDENT.name(),
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
}
