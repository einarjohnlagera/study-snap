package com.studysnap.backend.controller;

import com.studysnap.backend.dto.ExtractedNoteTextResponse;
import com.studysnap.backend.dto.GenerateNoteFromTopicRequest;
import com.studysnap.backend.dto.GenerateNoteFromTopicResponse;
import com.studysnap.backend.dto.NoteListItemResponse;
import com.studysnap.backend.dto.NoteResponse;
import com.studysnap.backend.dto.PublicNoteDetailResponse;
import com.studysnap.backend.dto.PublicNoteLikeResponse;
import com.studysnap.backend.dto.RecentQuizSessionHistoryResponse;
import com.studysnap.backend.dto.UpdateNoteVisibilityRequest;
import com.studysnap.backend.entity.NoteTargetProfileType;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.NoteNotFoundException;
import com.studysnap.backend.dto.CopyOnSignupRequest;
import com.studysnap.backend.dto.CopyOnSignupResponse;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.ChallengeQuizService;
import com.studysnap.backend.service.GeneratedQuizService;
import com.studysnap.backend.service.NoteService;
import com.studysnap.backend.service.NoteGenerationService;
import com.studysnap.backend.service.NoteTextExtractionService;
import com.studysnap.backend.service.QuickReviewAdaptivePracticeService;
import com.studysnap.backend.service.QuickReviewSessionService;
import com.studysnap.backend.service.QuickReviewStudyTipService;
import com.studysnap.backend.service.QuizSessionHistoryService;
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
    private NoteGenerationService noteGenerationService;
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
    @Mock
    private GeneratedQuizService generatedQuizService;
    @Mock
    private QuizSessionHistoryService quizSessionHistoryService;

    private NoteController noteController;

    @BeforeEach
    void setUp() {
        noteController = new NoteController(
                authService,
                noteService,
                noteGenerationService,
                noteTextExtractionService,
                studyPackService,
                quickReviewSessionService,
                quickReviewStudyTipService,
                challengeQuizService,
                quickReviewAdaptivePracticeService,
                generatedQuizService,
                quizSessionHistoryService
        );
    }

    @Test
    void generate_callsEmailVerificationBeforeGeneration() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        NoteResponse expected = new NoteResponse(
                "note-1",
                "Draft note",
                "Biology",
                "Nursing",
                "STUDENT",
                List.of("tag"),
                "content",
                "PRIVATE",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                null,
                null,
                false,
                null,
                null,
                "GENERATING",
                null,
                List.of(),
                List.of(),
                null,
                0,
                false,
                false,
                false,
                false
        );
        when(noteService.getById("note-1", userId)).thenReturn(expected);

        NoteResponse response = noteController.generate("note-1", false, user);

        verify(authService).requireEmailVerified(userId);
        verify(studyPackService).startAsyncGenerationFromNote("note-1", userId, false);
        verify(noteService).getById("note-1", userId);
        assertThat(response).isEqualTo(expected);
    }

    @Test
    void generate_passesAutoApplyMetadataWhenRequested() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        NoteResponse expected = new NoteResponse(
                "note-1",
                "Draft note",
                "Biology",
                "Nursing",
                "STUDENT",
                List.of("tag"),
                "content",
                "PRIVATE",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                null,
                null,
                false,
                null,
                null,
                "GENERATING",
                null,
                List.of(),
                List.of(),
                null,
                0,
                false,
                false,
                false,
                false
        );
        when(noteService.getById("note-1", userId)).thenReturn(expected);

        NoteResponse response = noteController.generate("note-1", true, user);

        verify(authService).requireEmailVerified(userId);
        verify(studyPackService).startAsyncGenerationFromNote("note-1", userId, true);
        verify(noteService).getById("note-1", userId);
        assertThat(response).isEqualTo(expected);
    }

    @Test
    void generateNoteFromTopic_callsEmailVerificationBeforeGeneration() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        GenerateNoteFromTopicRequest request = new GenerateNoteFromTopicRequest("Newton's Laws of Motion", null);
        GenerateNoteFromTopicResponse expected = new GenerateNoteFromTopicResponse("Generated note content");
        when(noteGenerationService.generateFromTopic(request, userId)).thenReturn(expected);

        GenerateNoteFromTopicResponse response = noteController.generateNoteFromTopic(request, user);

        verify(authService).requireEmailVerified(userId);
        verify(noteGenerationService).generateFromTopic(request, userId);
        assertThat(response).isEqualTo(expected);
    }

    @Test
    void listPublic_mapsAudienceQueryToTargetProfileFilter() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        when(noteService.listPublic(userId, "cinco", "recent", "history", List.of("mexican-history"), "nursing", NoteTargetProfileType.STUDENT))
                .thenReturn(List.of());

        List<NoteListItemResponse> response = noteController.listPublic(
                "cinco",
                "recent",
                "history",
                List.of("mexican-history"),
                "nursing",
                "student",
                null,
                user
        );

        verify(noteService).listPublic(
                userId,
                "cinco",
                "recent",
                "history",
                List.of("mexican-history"),
                "nursing",
                NoteTargetProfileType.STUDENT
        );
        assertThat(response).isEmpty();
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

        assertThatThrownBy(() -> noteController.generate("note-1", false, user))
                .isSameAs(verificationError);

        verify(studyPackService, never()).startAsyncGenerationFromNote("note-1", userId, false);
    }

    @Test
    void copyNoteOnSignup_copiesPublicNoteAndStartsGeneration() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        String publicNoteId = "public-note-1";
        String copiedNoteId = "copied-note-1";
        NoteResponse copied = buildNoteResponse(copiedNoteId, "DRAFT");
        when(noteService.copyPublicNoteForSignup(publicNoteId, userId)).thenReturn(copied);

        CopyOnSignupResponse response = noteController.copyNoteOnSignup(
                new CopyOnSignupRequest(publicNoteId),
                user
        );

        verify(authService).requireEmailVerified(userId);
        verify(noteService).copyPublicNoteForSignup(publicNoteId, userId);
        verify(studyPackService).startAsyncGenerationFromNote(copiedNoteId, userId);
        assertThat(response.noteId()).isEqualTo(copiedNoteId);
    }

    @Test
    void copyNoteOnSignup_rethrowsNoteNotFound() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        String missingNoteId = "missing-note";
        NoteNotFoundException notFound = new NoteNotFoundException();
        when(noteService.copyPublicNoteForSignup(missingNoteId, userId)).thenThrow(notFound);

        assertThatThrownBy(() -> noteController.copyNoteOnSignup(new CopyOnSignupRequest(missingNoteId), user))
                .isSameAs(notFound);

        verify(authService).requireEmailVerified(userId);
        verify(studyPackService, never()).startAsyncGenerationFromNote(missingNoteId, userId);
    }

    @Test
    void copyNoteOnSignup_returnsExistingCopyWithoutStartingDuplicateGeneration() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        String publicNoteId = "public-note-1";
        String existingNoteId = "existing-note-1";
        NoteResponse existingCopy = buildNoteResponse(existingNoteId, "GENERATING");
        when(noteService.copyPublicNoteForSignup(publicNoteId, userId)).thenReturn(existingCopy);

        CopyOnSignupResponse response = noteController.copyNoteOnSignup(
                new CopyOnSignupRequest(publicNoteId),
                user
        );

        verify(authService).requireEmailVerified(userId);
        verify(noteService).copyPublicNoteForSignup(publicNoteId, userId);
        verify(studyPackService, never()).startAsyncGenerationFromNote(existingNoteId, userId);
        assertThat(response.noteId()).isEqualTo(existingNoteId);
    }

    @Test
    void generateGeneratedQuiz_callsEmailVerificationBeforeGeneration() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        com.studysnap.backend.dto.GeneratedQuizResponse expected = new com.studysnap.backend.dto.GeneratedQuizResponse(
                UUID.randomUUID().toString(),
                "note-1",
                List.of(),
                OffsetDateTime.now()
        );
        com.studysnap.backend.dto.GenerateGeneratedQuizRequest request =
                new com.studysnap.backend.dto.GenerateGeneratedQuizRequest(20);
        when(generatedQuizService.generate("note-1", userId, 20, null)).thenReturn(expected);

        com.studysnap.backend.dto.GeneratedQuizResponse response = noteController.generateGeneratedQuiz("note-1", request, user);

        verify(authService).requireEmailVerified(userId);
        verify(generatedQuizService).generate("note-1", userId, 20, null);
        assertThat(response).isEqualTo(expected);
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
                "Full note content",
                "preview",
                "STUDY_PACK_READY",
                "Summary",
                List.of("Nucleus"),
                List.of(),
                "noteguru",
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
                        "Nursing",
                        "STUDENT",
                        "Biology",
                        List.of("cells"),
                        "preview",
                        "summary preview",
                        "PUBLIC",
                        null,
                        "STUDY_PACK_READY",
                        4,
                        2L,
                        0L,
                        1L,
                        5L,
                        "My Notes",
                        "mynotes",
                        false,
                        true,
                        OffsetDateTime.now(),
                        OffsetDateTime.now(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        false
                )
        );
        when(noteService.listPublic(userId, null, null, null, null, null, null)).thenReturn(expected);

        List<NoteListItemResponse> response = noteController.listPublic(null, null, null, null, null, null, null, user);

        assertThat(response).isEqualTo(expected);
        verify(noteService).listPublic(userId, null, null, null, null, null, null);
    }

    @Test
    void listRecentQuizSessions_delegatesAfterValidatingOwnedStudyPack() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        List<RecentQuizSessionHistoryResponse> expected = List.of(new RecentQuizSessionHistoryResponse(
                "session-1",
                "LONG_EXAM",
                20,
                16,
                java.math.BigDecimal.valueOf(80),
                0,
                null,
                List.of(),
                2,
                OffsetDateTime.now().minusMinutes(20),
                OffsetDateTime.now()
        ));
        when(noteService.getOwnedStudyPackIdOrThrow("note-1", userId)).thenReturn("study-pack-1");
        when(quizSessionHistoryService.listRecentSessions("note-1", userId, 5)).thenReturn(expected);

        List<RecentQuizSessionHistoryResponse> response = noteController.listRecentQuizSessions("note-1", 5, user);

        assertThat(response).isEqualTo(expected);
        verify(noteService).getOwnedStudyPackIdOrThrow("note-1", userId);
        verify(quizSessionHistoryService).listRecentSessions("note-1", userId, 5);
    }

    @Test
    void togglePublicNoteLike_delegatesToService() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        PublicNoteLikeResponse expected = new PublicNoteLikeResponse(true, 12L);
        when(noteService.togglePublicNoteLike("note-1", userId)).thenReturn(expected);

        PublicNoteLikeResponse response = noteController.togglePublicNoteLike("note-1", user);

        assertThat(response).isEqualTo(expected);
        verify(noteService).togglePublicNoteLike("note-1", userId);
    }

    private static NoteResponse buildNoteResponse(String id, String studyPackStatus) {
        return new NoteResponse(
                id,
                "Copied note",
                "Biology",
                "Nursing",
                "STUDENT",
                List.of("tag"),
                "content",
                "PRIVATE",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                null,
                null,
                null,
                false,
                null,
                null,
                studyPackStatus,
                null,
                List.of(),
                List.of(),
                null,
                0,
                false,
                false,
                false,
                false
        );
    }
}
