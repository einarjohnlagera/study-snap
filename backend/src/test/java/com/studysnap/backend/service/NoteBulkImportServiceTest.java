package com.studysnap.backend.service;

import com.studysnap.backend.dto.BulkImportResultResponse;
import com.studysnap.backend.dto.ExtractedNoteTextResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.UpsertNoteRequest;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.InvalidBulkImportRequestException;
import com.studysnap.backend.exception.ProfileSetupRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteBulkImportServiceTest {
    private static final int MAX_BATCH_FILES = 3;
    private static final String DRAFT_STATUS = "DRAFT";
    private static final String PRIVATE_VISIBILITY = "PRIVATE";
    private static final String BIOLOGY_CONTENT = "Biology extracted content";
    private static final String CHEMISTRY_CONTENT = "Chemistry extracted content";
    private static final String PHYSICS_CONTENT = "Physics extracted content";
    private static final String NOTE_IMPORT_FAILED_CODE = "NOTE_IMPORT_FAILED";
    private static final String NOTE_IMPORT_FAILED_MESSAGE = "We could not read this file.";
    private static final String NO_READABLE_TEXT_CODE = "NO_READABLE_TEXT";
    private static final String NO_READABLE_TEXT_MESSAGE = "No readable text was found in this file.";
    private static final String CREATED_COUNT_KEY = "createdCount";
    private static final String FAILED_COUNT_KEY = "failedCount";
    private static final String MULTIPART_FIELD_NAME = "files";
    private static final String TEXT_PLAIN_CONTENT_TYPE = "text/plain";

    @Mock
    private AuthService authService;
    @Mock
    private NoteTextExtractionService noteTextExtractionService;
    @Mock
    private NoteService noteService;
    @Mock
    private AnalyticsService analyticsService;
    @Mock
    private OnboardingGuardService onboardingGuardService;

    private NoteBulkImportService service;

    @BeforeEach
    void setUp() {
        service = new NoteBulkImportService(
                authService,
                noteTextExtractionService,
                noteService,
                analyticsService,
                onboardingGuardService,
                MAX_BATCH_FILES
        );
    }

    @Test
    void importBatch_rejectsMissingProfileTypeBeforeReadingFiles() {
        UUID userId = UUID.randomUUID();
        ProfileSetupRequiredException exception = new ProfileSetupRequiredException();
        doThrow(exception).when(onboardingGuardService).assertProfileComplete(userId);
        MockMultipartFile file = textFile("biology.txt");

        assertThatThrownBy(() -> service.importBatch(userId, List.of(file)))
                .isSameAs(exception);

        verify(authService).requireEmailVerified(userId);
        verify(noteTextExtractionService, never()).extractText(any(), any(UUID.class));
        verify(noteService, never()).create(any(UpsertNoteRequest.class), any(UUID.class));
    }

    @Test
    void importBatch_createsDraftNotesForValidFiles() {
        UUID userId = UUID.randomUUID();
        MockMultipartFile first = textFile("biology unit.pdf");
        MockMultipartFile second = textFile("chemistry-notes.txt");
        MockMultipartFile third = textFile("physics.docx");
        UUID firstNoteId = UUID.randomUUID();
        UUID secondNoteId = UUID.randomUUID();
        UUID thirdNoteId = UUID.randomUUID();
        when(noteTextExtractionService.extractText(first, userId)).thenReturn(extracted(BIOLOGY_CONTENT, false));
        when(noteTextExtractionService.extractText(second, userId)).thenReturn(extracted(CHEMISTRY_CONTENT, true));
        when(noteTextExtractionService.extractText(third, userId)).thenReturn(extracted(PHYSICS_CONTENT, false));
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId)))
                .thenReturn(note(firstNoteId, "biology unit", BIOLOGY_CONTENT))
                .thenReturn(note(secondNoteId, "chemistry-notes", CHEMISTRY_CONTENT))
                .thenReturn(note(thirdNoteId, "physics", PHYSICS_CONTENT));

        BulkImportResultResponse result = service.importBatch(userId, List.of(first, second, third));

        assertThat(result.created()).hasSize(3);
        assertThat(result.failed()).isEmpty();
        assertThat(result.created()).extracting(BulkImportResultResponse.ImportedNoteResult::noteId)
                .containsExactly(firstNoteId, secondNoteId, thirdNoteId);
        assertThat(result.created()).extracting(BulkImportResultResponse.ImportedNoteResult::title)
                .containsExactly("biology unit", "chemistry-notes", "physics");
        assertThat(result.created()).extracting(BulkImportResultResponse.ImportedNoteResult::lowConfidence)
                .containsExactly(false, true, false);

        ArgumentCaptor<UpsertNoteRequest> requestCaptor = ArgumentCaptor.forClass(UpsertNoteRequest.class);
        verify(noteService, times(3)).create(requestCaptor.capture(), eq(userId));
        assertThat(requestCaptor.getAllValues()).extracting(UpsertNoteRequest::content)
                .containsExactly(BIOLOGY_CONTENT, CHEMISTRY_CONTENT, PHYSICS_CONTENT);
        assertThat(requestCaptor.getAllValues()).allSatisfy(request -> {
            assertThat(request.subject()).isNull();
            assertThat(request.courseProgram()).isNull();
            assertThat(request.tags()).isEmpty();
            assertThat(request.targetProfileType()).isNull();
        });
        verify(analyticsService).trackEvent(
                eq(userId),
                eq(AnalyticsEventType.NOTES_BULK_IMPORTED),
                isNull(),
                org.mockito.ArgumentMatchers.argThat(metadata -> hasBulkImportCounts(metadata, 3, 0))
        );
    }

    @Test
    void importBatch_recordsMiddleFailureAndPersistsOtherNotes() {
        UUID userId = UUID.randomUUID();
        MockMultipartFile first = textFile("good one.txt");
        MockMultipartFile second = textFile("bad image.png");
        MockMultipartFile third = textFile("good two.txt");
        UUID firstNoteId = UUID.randomUUID();
        UUID thirdNoteId = UUID.randomUUID();
        when(noteTextExtractionService.extractText(first, userId)).thenReturn(extracted(BIOLOGY_CONTENT, false));
        when(noteTextExtractionService.extractText(second, userId)).thenThrow(importFailedException());
        when(noteTextExtractionService.extractText(third, userId)).thenReturn(extracted(PHYSICS_CONTENT, false));
        when(noteService.create(any(UpsertNoteRequest.class), eq(userId)))
                .thenReturn(note(firstNoteId, "good one", BIOLOGY_CONTENT))
                .thenReturn(note(thirdNoteId, "good two", PHYSICS_CONTENT));

        BulkImportResultResponse result = service.importBatch(userId, List.of(first, second, third));

        assertThat(result.created()).extracting(BulkImportResultResponse.ImportedNoteResult::noteId)
                .containsExactly(firstNoteId, thirdNoteId);
        assertThat(result.failed()).hasSize(1);
        assertThat(result.failed().getFirst().fileName()).isEqualTo("bad image.png");
        assertThat(result.failed().getFirst().errorCode()).isEqualTo(NOTE_IMPORT_FAILED_CODE);
        assertThat(result.failed().getFirst().message()).isEqualTo(NOTE_IMPORT_FAILED_MESSAGE);
        verify(noteService, times(2)).create(any(UpsertNoteRequest.class), eq(userId));
        verify(analyticsService).trackEvent(
                eq(userId),
                eq(AnalyticsEventType.NOTES_BULK_IMPORTED),
                isNull(),
                org.mockito.ArgumentMatchers.argThat(metadata -> hasBulkImportCounts(metadata, 2, 1))
        );
    }

    @Test
    void importBatch_recordsBlankExtractedTextAsFailure() {
        UUID userId = UUID.randomUUID();
        MockMultipartFile file = textFile("blank.pdf");
        when(noteTextExtractionService.extractText(file, userId)).thenReturn(extracted("   ", false));

        BulkImportResultResponse result = service.importBatch(userId, List.of(file));

        assertThat(result.created()).isEmpty();
        assertThat(result.failed()).hasSize(1);
        assertThat(result.failed().getFirst().errorCode()).isEqualTo(NO_READABLE_TEXT_CODE);
        assertThat(result.failed().getFirst().message()).isEqualTo(NO_READABLE_TEXT_MESSAGE);
        verify(noteService, never()).create(any(UpsertNoteRequest.class), eq(userId));
        verify(analyticsService, never()).trackEvent(any(), any(), any(), any());
    }

    @Test
    void importBatch_rejectsMoreThanConfiguredMaxFiles() {
        UUID userId = UUID.randomUUID();
        List<MultipartFile> files = List.of(
                textFile("one.txt"),
                textFile("two.txt"),
                textFile("three.txt"),
                textFile("four.txt")
        );

        assertThatThrownBy(() -> service.importBatch(userId, files))
                .isInstanceOf(InvalidBulkImportRequestException.class)
                .hasMessage("You can import up to 3 files at once.");
    }

    @Test
    void importBatch_rejectsEmptyFilesList() {
        UUID userId = UUID.randomUUID();
        List<MultipartFile> files = List.of();

        assertThatThrownBy(() -> service.importBatch(userId, files))
                .isInstanceOf(InvalidBulkImportRequestException.class)
                .hasMessage("Please upload at least one file.");
    }

    @Test
    void importBatch_rejectsAllEmptyFiles() {
        UUID userId = UUID.randomUUID();
        List<MultipartFile> files = List.of(new MockMultipartFile(
                MULTIPART_FIELD_NAME,
                "empty.txt",
                TEXT_PLAIN_CONTENT_TYPE,
                new byte[0]
        ));

        assertThatThrownBy(() -> service.importBatch(userId, files))
                .isInstanceOf(InvalidBulkImportRequestException.class)
                .hasMessage("Please upload at least one file.");
    }

    @Test
    void importBatch_unverifiedUserFailsBeforeExtraction() {
        UUID userId = UUID.randomUUID();
        List<MultipartFile> files = List.of(textFile("biology.txt"));
        AppException verificationException = new AppException(
                "EMAIL_NOT_VERIFIED",
                "Verify your email before using this feature.",
                HttpStatus.FORBIDDEN
        );
        doThrow(verificationException).when(authService).requireEmailVerified(userId);

        assertThatThrownBy(() -> service.importBatch(userId, files))
                .isSameAs(verificationException);
        verify(noteTextExtractionService, never()).extractText(any(), any());
    }

    @Test
    void importBatch_isNotTransactional() throws NoSuchMethodException {
        Method method = NoteBulkImportService.class.getMethod("importBatch", UUID.class, List.class);

        assertThat(NoteBulkImportService.class.getAnnotation(Transactional.class)).isNull();
        assertThat(method.getAnnotation(Transactional.class)).isNull();
    }

    private MockMultipartFile textFile(String fileName) {
        return new MockMultipartFile(MULTIPART_FIELD_NAME, fileName, TEXT_PLAIN_CONTENT_TYPE, "content".getBytes());
    }

    private ExtractedNoteTextResponse extracted(String content, boolean lowConfidence) {
        return new ExtractedNoteTextResponse(
                "TEXT",
                content,
                new ExtractedNoteTextResponse.ExtractionMeta(0.92, lowConfidence)
        );
    }

    private NoteResponse note(UUID noteId, String title, String content) {
        return new NoteResponse(
                noteId.toString(),
                title,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                content,
                PRIVATE_VISIBILITY,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                null,
                null,
                false,
                null,
                null,
                DRAFT_STATUS,
                null,
                List.of(),
                List.of(),
                null,
                0,
                false,
                false,
                false
        );
    }

    private AppException importFailedException() {
        return new AppException(NOTE_IMPORT_FAILED_CODE, NOTE_IMPORT_FAILED_MESSAGE, HttpStatus.BAD_REQUEST);
    }

    private boolean hasBulkImportCounts(Map<String, Object> metadata, int createdCount, int failedCount) {
        return metadata != null
                && metadata.get(CREATED_COUNT_KEY).equals(createdCount)
                && metadata.get(FAILED_COUNT_KEY).equals(failedCount);
    }
}
