package com.studysnap.backend.service;

import com.studysnap.backend.dto.BulkGenerateNoteGroupRequest;
import com.studysnap.backend.dto.BulkGenerateNotesRequest;
import com.studysnap.backend.dto.BulkGenerateNotesResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.LearnerLevel;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.exception.InvalidBulkGenerationRequestException;
import com.studysnap.backend.service.model.StudyPackGenerationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteBulkGenerationServiceTest {
    private static final String COURSE_PROGRAM = "Nursing";
    private static final String FIRST_SUBJECT = "Maternal Health";
    private static final String SECOND_SUBJECT = "Community Health";

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
                50,
                0
        );
    }

    @Test
    void queueBatch_createsNotesWithBatchMetadataAndStartsPublicStudyPackGeneration() {
        UUID userId = UUID.randomUUID();
        BulkGenerateNotesRequest request = requestWithTitles(List.of("Prenatal Care", "Labor Stages"), List.of("Epidemiology"));
        StudyPackGenerationContext firstContext = context(FIRST_SUBJECT);
        StudyPackGenerationContext secondContext = context(SECOND_SUBJECT);
        when(generationContextResolver.resolveForBulkGeneration(userId, LearnerLevel.BOARD_EXAM_REVIEW, COURSE_PROGRAM, FIRST_SUBJECT))
                .thenReturn(firstContext);
        when(generationContextResolver.resolveForBulkGeneration(userId, LearnerLevel.BOARD_EXAM_REVIEW, COURSE_PROGRAM, SECOND_SUBJECT))
                .thenReturn(secondContext);
        when(llmStudyPackService.generateNoteFromTopic(anyString(), any(StudyPackGenerationContext.class)))
                .thenAnswer(invocation -> "Generated content for " + invocation.getArgument(0));
        AtomicInteger noteSequence = new AtomicInteger();
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId)))
                .thenAnswer(invocation -> noteResponse("note-" + noteSequence.incrementAndGet()));

        BulkGenerateNotesResponse response = service.queueBatch(request, userId, false);

        assertThat(response).isEqualTo(new BulkGenerateNotesResponse(3, 3, 2, 0));
        ArgumentCaptor<UpsertNoteRequest> noteRequestCaptor = ArgumentCaptor.forClass(UpsertNoteRequest.class);
        verify(noteService, org.mockito.Mockito.times(3)).create(noteRequestCaptor.capture(), eq(userId));
        assertThat(noteRequestCaptor.getAllValues())
                .extracting(UpsertNoteRequest::subject)
                .containsExactly(FIRST_SUBJECT, FIRST_SUBJECT, SECOND_SUBJECT);
        assertThat(noteRequestCaptor.getAllValues())
                .extracting(UpsertNoteRequest::courseProgram)
                .containsOnly(COURSE_PROGRAM);
        assertThat(noteRequestCaptor.getAllValues())
                .extracting(UpsertNoteRequest::targetProfileType)
                .containsOnly("BOARD_TAKER");
        verify(noteService, org.mockito.Mockito.times(3))
                .updateVisibility(anyString(), eq(NoteVisibility.PUBLIC.name()), eq(userId));
        verify(studyPackService, org.mockito.Mockito.times(2)).startAsyncGenerationFromNote(
                anyString(), eq(userId), eq(false), eq(false), eq(firstContext), eq(FIRST_SUBJECT)
        );
        verify(studyPackService).startAsyncGenerationFromNote(
                anyString(), eq(userId), eq(false), eq(false), eq(secondContext), eq(SECOND_SUBJECT)
        );
        verify(noteGenerationService, never()).generateFromTopic(any(), any());
    }

    @Test
    void queueBatch_isolatesContentGenerationFailures() {
        UUID userId = UUID.randomUUID();
        BulkGenerateNotesRequest request = requestWithTitles(List.of("Rejected Topic", "Healthy Topic"), List.of());
        StudyPackGenerationContext context = context(FIRST_SUBJECT);
        when(generationContextResolver.resolveForBulkGeneration(userId, LearnerLevel.BOARD_EXAM_REVIEW, COURSE_PROGRAM, FIRST_SUBJECT))
                .thenReturn(context);
        when(llmStudyPackService.generateNoteFromTopic("Rejected Topic", context))
                .thenThrow(new RuntimeException("moderation rejected"));
        when(llmStudyPackService.generateNoteFromTopic("Healthy Topic", context)).thenReturn("Healthy content");
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId))).thenReturn(noteResponse("note-healthy"));

        BulkGenerateNotesResponse response = service.queueBatch(request, userId, false);

        assertThat(response.queuedTitles()).isEqualTo(2);
        ArgumentCaptor<UpsertNoteRequest> noteRequestCaptor = ArgumentCaptor.forClass(UpsertNoteRequest.class);
        verify(noteService).create(noteRequestCaptor.capture(), eq(userId));
        assertThat(noteRequestCaptor.getValue().title()).isEqualTo("Healthy Topic");
        verify(studyPackService).startAsyncGenerationFromNote(
                "note-healthy", userId, false, false, context, FIRST_SUBJECT
        );
    }

    @Test
    void queueBatch_rejectsEmptyAndOverCapRequests() {
        UUID userId = UUID.randomUUID();
        BulkGenerateNotesRequest emptyRequest = new BulkGenerateNotesRequest(
                COURSE_PROGRAM,
                LearnerLevel.COLLEGE,
                false,
                List.of()
        );
        List<String> tooManyTitles = java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(index -> "Title " + index)
                .toList();
        BulkGenerateNotesRequest overCapRequest = new BulkGenerateNotesRequest(
                COURSE_PROGRAM,
                LearnerLevel.COLLEGE,
                false,
                List.of(new BulkGenerateNoteGroupRequest(FIRST_SUBJECT, tooManyTitles))
        );

        assertThatThrownBy(() -> service.queueBatch(emptyRequest, userId, false))
                .isInstanceOf(InvalidBulkGenerationRequestException.class)
                .hasMessage("Add at least one title under a Subject: heading.");
        assertThatThrownBy(() -> service.queueBatch(overCapRequest, userId, false))
                .isInstanceOf(InvalidBulkGenerationRequestException.class)
                .hasMessage("You can bulk generate up to 50 titles at once.");
    }

    private BulkGenerateNotesRequest requestWithTitles(List<String> firstTitles, List<String> secondTitles) {
        List<BulkGenerateNoteGroupRequest> groups = new java.util.ArrayList<>();
        groups.add(new BulkGenerateNoteGroupRequest(FIRST_SUBJECT, firstTitles));
        if (!secondTitles.isEmpty()) {
            groups.add(new BulkGenerateNoteGroupRequest(SECOND_SUBJECT, secondTitles));
        }
        return new BulkGenerateNotesRequest(
                COURSE_PROGRAM,
                LearnerLevel.BOARD_EXAM_REVIEW,
                true,
                groups
        );
    }

    private StudyPackGenerationContext context(String subject) {
        return new StudyPackGenerationContext(
                LearnerLevel.BOARD_EXAM_REVIEW,
                COURSE_PROGRAM,
                subject,
                List.of()
        );
    }

    private NoteResponse noteResponse(String id) {
        return new NoteResponse(
                id,
                "Title",
                FIRST_SUBJECT,
                COURSE_PROGRAM,
                "BOARD_TAKER",
                List.of(),
                "Content",
                "PRIVATE",
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
