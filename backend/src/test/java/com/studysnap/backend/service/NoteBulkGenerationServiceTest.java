package com.studysnap.backend.service;

import com.studysnap.backend.dto.BulkGenerateNotesRequest;
import com.studysnap.backend.dto.BulkGenerateNotesResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.InvalidBulkGenerationRequestException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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

    private NoteBulkGenerationService service;

    @BeforeEach
    void setUp() {
        service = new NoteBulkGenerationService(
                noteGenerationService,
                noteService,
                studyPackService,
                llmStudyPackService,
                contentModerationService,
                generationContextResolver,
                new StudyPackGenerationTaskDispatcher(Runnable::run),
                userRepository,
                50,
                0
        );
    }

    @Test
    void queueBatch_adminHonorsExplicitMetadataAndBypassesUsagePaths() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, "Profile Course");
        BulkGenerateNotesRequest request = request(
                List.of("Prenatal Care", "Labor Stages"),
                COURSE_PROGRAM,
                NoteTargetProfileType.BOARD_TAKER,
                LearnerLevel.BOARD_EXAM_REVIEW,
                true
        );
        StudyPackGenerationContext context = context(
                LearnerLevel.BOARD_EXAM_REVIEW,
                COURSE_PROGRAM
        );
        when(generationContextResolver.resolveForBulkGeneration(
                userId,
                LearnerLevel.BOARD_EXAM_REVIEW,
                COURSE_PROGRAM,
                SUBJECT
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic(anyString(), eq(context)))
                .thenAnswer(invocation -> "Generated content for " + invocation.getArgument(0));
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId)))
                .thenReturn(noteResponse("note-1"), noteResponse("note-2"));

        BulkGenerateNotesResponse response = service.queueBatch(request, userId, false);

        assertThat(response).isEqualTo(new BulkGenerateNotesResponse(2, 2, 1, 0));
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
    }

    @Test
    void queueBatch_teacherUsesExplicitCategorizationAndProfileLearnerLevel() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.USER, ProfileType.TEACHER, LearnerLevel.SENIOR_HIGH, PROFILE_COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(
                List.of("Classroom Assessment"),
                COURSE_PROGRAM,
                NoteTargetProfileType.STUDENT,
                LearnerLevel.PROFESSIONAL,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.SENIOR_HIGH, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                userId,
                LearnerLevel.SENIOR_HIGH,
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
                LearnerLevel.GRADE_SCHOOL,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.BOARD_EXAM_REVIEW, PROFILE_COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                userId,
                LearnerLevel.BOARD_EXAM_REVIEW,
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
    void queueBatch_isolatesContentGenerationFailures() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest request = request(
                List.of("Rejected Topic", "Healthy Topic"),
                COURSE_PROGRAM,
                NoteTargetProfileType.STUDENT,
                LearnerLevel.COLLEGE,
                false
        );
        StudyPackGenerationContext context = context(LearnerLevel.COLLEGE, COURSE_PROGRAM);
        when(generationContextResolver.resolveForBulkGeneration(
                userId,
                LearnerLevel.COLLEGE,
                COURSE_PROGRAM,
                SUBJECT
        )).thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic("Rejected Topic", context))
                .thenThrow(new RuntimeException("moderation rejected"));
        when(llmStudyPackService.generateNoteFromTopic("Healthy Topic", context)).thenReturn("Healthy content");
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId))).thenReturn(noteResponse("note-healthy"));

        BulkGenerateNotesResponse response = service.queueBatch(request, userId, false);

        assertThat(response.queuedTitles()).isEqualTo(2);
        ArgumentCaptor<UpsertNoteRequest> captor = ArgumentCaptor.forClass(UpsertNoteRequest.class);
        verify(noteService).create(captor.capture(), eq(userId));
        assertThat(captor.getValue().title()).isEqualTo("Healthy Topic");
    }

    @Test
    void queueBatch_rejectsMissingSubjectEmptyTitlesAndOverCap() {
        UUID userId = UUID.randomUUID();
        mockUser(userId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest missingSubject = new BulkGenerateNotesRequest(
                " ", List.of("Title"), false, COURSE_PROGRAM, NoteTargetProfileType.STUDENT, LearnerLevel.COLLEGE
        );
        BulkGenerateNotesRequest emptyTitles = request(
                List.of(), COURSE_PROGRAM, NoteTargetProfileType.STUDENT, LearnerLevel.COLLEGE, false
        );
        List<String> tooManyTitles = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(index -> "Title " + index)
                .toList();
        BulkGenerateNotesRequest overCap = request(
                tooManyTitles, COURSE_PROGRAM, NoteTargetProfileType.STUDENT, LearnerLevel.COLLEGE, false
        );

        assertThatThrownBy(() -> service.queueBatch(missingSubject, userId, false))
                .isInstanceOf(InvalidBulkGenerationRequestException.class)
                .hasMessage("Subject is required.");
        assertThatThrownBy(() -> service.queueBatch(emptyTitles, userId, false))
                .isInstanceOf(InvalidBulkGenerationRequestException.class)
                .hasMessage("Add at least one title.");
        assertThatThrownBy(() -> service.queueBatch(overCap, userId, false))
                .isInstanceOf(InvalidBulkGenerationRequestException.class)
                .hasMessage("You can bulk generate up to 50 titles at once.");
    }

    @Test
    void queueBatch_rejectsTeacherMissingRequiredMetadataAndAdminMissingLearnerLevel() {
        UUID teacherId = UUID.randomUUID();
        mockUser(teacherId, UserRole.USER, ProfileType.TEACHER, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest missingCourse = request(
                List.of("Title"), " ", NoteTargetProfileType.STUDENT, LearnerLevel.COLLEGE, false
        );
        BulkGenerateNotesRequest missingTarget = request(
                List.of("Title"), COURSE_PROGRAM, null, LearnerLevel.COLLEGE, false
        );
        UUID adminId = UUID.randomUUID();
        mockUser(adminId, UserRole.ADMIN, ProfileType.STUDENT, LearnerLevel.COLLEGE, COURSE_PROGRAM);
        BulkGenerateNotesRequest missingLearner = request(
                List.of("Title"), COURSE_PROGRAM, NoteTargetProfileType.STUDENT, null, false
        );

        assertThatThrownBy(() -> service.queueBatch(missingCourse, teacherId, false))
                .isInstanceOf(InvalidBulkGenerationRequestException.class)
                .hasMessage("Course/program is required.");
        assertThatThrownBy(() -> service.queueBatch(missingTarget, teacherId, false))
                .isInstanceOf(InvalidBulkGenerationRequestException.class)
                .hasMessage("Target audience is required for teachers and admins.");
        assertThatThrownBy(() -> service.queueBatch(missingLearner, adminId, false))
                .isInstanceOf(InvalidBulkGenerationRequestException.class)
                .hasMessage("Learner level is required for admins.");
    }

    private BulkGenerateNotesRequest request(
            List<String> titles,
            String courseProgram,
            NoteTargetProfileType targetProfileType,
            LearnerLevel learnerLevel,
            boolean makePublic
    ) {
        return new BulkGenerateNotesRequest(
                SUBJECT,
                titles,
                makePublic,
                courseProgram,
                targetProfileType,
                learnerLevel
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
                false,
                false
        );
    }
}
