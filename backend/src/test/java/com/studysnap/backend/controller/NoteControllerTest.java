package com.studysnap.backend.controller;

import com.studysnap.backend.dto.CreateStudyPackRequest;
import com.studysnap.backend.dto.ExtractedNoteTextResponse;
import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.PublicNoteDetailResponse;
import com.studysnap.backend.dto.StudyPackMeta;
import com.studysnap.backend.dto.StudyPackResponse;
import com.studysnap.backend.dto.UpdateNoteVisibilityRequest;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.ChallengeQuizService;
import com.studysnap.backend.service.NoteService;
import com.studysnap.backend.service.NoteTextExtractionService;
import com.studysnap.backend.service.QuickReviewAdaptivePracticeService;
import com.studysnap.backend.service.QuickReviewSessionService;
import com.studysnap.backend.service.QuickReviewStudyTipService;
import com.studysnap.backend.service.StudyPackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NoteControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private NoteService noteService;
    @Mock
    private NoteTextExtractionService noteTextExtractionService;
    @Mock
    private StudyPackService studyPackService;
    @Mock
    private QuickReviewSessionService quickReviewSessionService;
    @Mock
    private QuickReviewStudyTipService quickReviewStudyTipService;
    @Mock
    private ChallengeQuizService challengeQuizService;
    @Mock
    private QuickReviewAdaptivePracticeService quickReviewAdaptivePracticeService;

    private NoteController noteController;

    @BeforeEach
    void setUp() {
        noteController = new NoteController(
                authService,
                noteService,
                noteTextExtractionService,
                studyPackService,
                quickReviewSessionService,
                quickReviewStudyTipService,
                challengeQuizService,
                quickReviewAdaptivePracticeService
        );
    }

    @Test
    void generate_callsEmailVerificationBeforeGeneration() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        StudyPackResponse expected = new StudyPackResponse(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "TEXT",
                null,
                "Generated",
                "Summary",
                "Source",
                "Biology",
                List.of("Concept"),
                List.of("tag"),
                List.of(),
                OffsetDateTime.now(),
                new StudyPackMeta(null, null)
        );
        when(studyPackService.createFromText(any(CreateStudyPackRequest.class), any(UUID.class))).thenReturn(expected);

        StudyPackResponse response = noteController.generate("note-1", user);

        verify(authService).requireEmailVerified(userId);
        verify(studyPackService).createFromText(new CreateStudyPackRequest(null, "note-1"), userId);
        assertThat(response).isEqualTo(expected);
    }

    @Test
    void extractText_delegatesToExtractionService() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "Hello".getBytes());
        ExtractedNoteTextResponse expected = new ExtractedNoteTextResponse(
                "txt",
                "Hello",
                new ExtractedNoteTextResponse.ExtractionMeta(null, false)
        );
        when(noteTextExtractionService.extractText(file, userId)).thenReturn(expected);

        ExtractedNoteTextResponse response = noteController.extractText(file, user);

        assertThat(response).isEqualTo(expected);
        verify(noteTextExtractionService).extractText(file, userId);
    }

    @Test
    void generate_blocksUnverifiedUsers() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, false, 1);
        AppException verificationError = new AppException(
                "EMAIL_VERIFICATION_REQUIRED",
                "Email verification required.",
                HttpStatus.FORBIDDEN
        );
        doThrow(verificationError).when(authService).requireEmailVerified(userId);

        assertThatThrownBy(() -> noteController.generate("note-1", user))
                .isSameAs(verificationError);

        verify(studyPackService, never()).createFromText(any(CreateStudyPackRequest.class), any(UUID.class));
    }

    @Test
    void updateVisibility_publicRequiresEmailVerification() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, false, 1);
        NoteResponse updated = null;
        when(noteService.updateVisibility("note-1", "PUBLIC", userId)).thenReturn(updated);

        noteController.updateVisibility("note-1", new UpdateNoteVisibilityRequest("PUBLIC"), user);

        verify(authService).requireEmailVerified(userId);
        verify(noteService).updateVisibility("note-1", "PUBLIC", userId);
    }

    @Test
    void updateVisibility_privateDoesNotRequireEmailVerification() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, false, 1);
        NoteResponse updated = null;
        when(noteService.updateVisibility("note-1", "PRIVATE", userId)).thenReturn(updated);

        noteController.updateVisibility("note-1", new UpdateNoteVisibilityRequest("PRIVATE"), user);

        verify(authService, never()).requireEmailVerified(userId);
        verify(noteService).updateVisibility("note-1", "PRIVATE", userId);
    }

    @Test
    void getPublicBySeoPath_delegatesToService() {
        PublicNoteDetailResponse expected = new PublicNoteDetailResponse(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "Cell Structure",
                "Science",
                List.of("cells"),
                "preview",
                "STUDY_PACK_READY",
                "Summary",
                List.of("Nucleus"),
                List.of(),
                "noteguru",
                false,
                false,
                OffsetDateTime.now()
        );
        when(noteService.getPublicBySeoPath("science", "cell-structure", null)).thenReturn(expected);

        PublicNoteDetailResponse response = noteController.getPublicBySeoPath("science", "cell-structure", null);

        assertThat(response).isEqualTo(expected);
        verify(noteService).getPublicBySeoPath("science", "cell-structure", null);
    }

    @Test
    void listPublic_delegatesViewerIdentityWithoutExcludingOwnNotes() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        List<NoteListItemResponse> expected = List.of(
                new NoteListItemResponse(
                        UUID.randomUUID().toString(),
                        userId.toString(),
                        "My public note",
                        "Biology",
                        List.of("cells"),
                        "preview",
                        "PUBLIC",
                        null,
                        "STUDY_PACK_READY",
                        4,
                        "My Notes",
                        false,
                        true,
                        OffsetDateTime.now()
                )
        );
        when(noteService.listPublic(userId)).thenReturn(expected);

        List<NoteListItemResponse> response = noteController.listPublic(user);

        assertThat(response).isEqualTo(expected);
        verify(noteService).listPublic(userId);
    }
}
