package com.studysnap.backend.controller;

import com.studysnap.backend.dto.ConfirmTextRequest;
import com.studysnap.backend.dto.CreateStudyPackRequest;
import com.studysnap.backend.dto.StudyPackMeta;
import com.studysnap.backend.dto.StudyPackResponse;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.AuthService;
import com.studysnap.backend.service.ConceptHealthService;
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
class StudyPackControllerTest {

    @Mock
    private AuthService authService;
    @Mock
    private StudyPackService studyPackService;
    @Mock
    private ConceptHealthService conceptHealthService;

    private StudyPackController studyPackController;

    @BeforeEach
    void setUp() {
        studyPackController = new StudyPackController(authService, studyPackService, conceptHealthService);
    }

    @Test
    void createFromImage_requiresEmailVerificationBeforeOcr() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "note.jpg",
                "image/jpeg",
                "fake-image".getBytes()
        );
        StudyPackResponse expected = new StudyPackResponse(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "IMAGE",
                "extracted",
                "Title",
                "Summary",
                "source",
                "Biology",
                List.of("Concept"),
                List.of("tag"),
                List.of(),
                OffsetDateTime.now(),
                new StudyPackMeta(0.92, 100L)
        );
        when(studyPackService.createFromImage(image, "Biology", userId)).thenReturn(expected);

        Object response = studyPackController.createFromImage(image, "Biology", user);

        verify(authService).requireEmailVerified(userId);
        verify(studyPackService).createFromImage(image, "Biology", userId);
        assertThat(response).isEqualTo(expected);
    }

    @Test
    void createFromImage_blocksUnverifiedUsers() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, false, 1);
        MockMultipartFile image = new MockMultipartFile("image", "note.jpg", "image/jpeg", "data".getBytes());
        AppException verificationError = new AppException(
                "EMAIL_VERIFICATION_REQUIRED",
                "Email verification required.",
                HttpStatus.FORBIDDEN
        );
        doThrow(verificationError).when(authService).requireEmailVerified(userId);

        assertThatThrownBy(() -> studyPackController.createFromImage(image, null, user))
                .isSameAs(verificationError);

        verify(studyPackService, never()).createFromImage(any(), any(), any(UUID.class));
    }

    @Test
    void confirmText_requiresEmailVerification() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        ConfirmTextRequest request = new ConfirmTextRequest("draft-id", "confirmed text");
        StudyPackResponse expected = new StudyPackResponse(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "IMAGE",
                "confirmed text",
                "Title",
                "Summary",
                "source",
                "Biology",
                List.of("Concept"),
                List.of("tag"),
                List.of(),
                OffsetDateTime.now(),
                new StudyPackMeta(0.82, 120L)
        );
        when(studyPackService.confirmExtractedText(request, userId)).thenReturn(expected);

        StudyPackResponse response = studyPackController.confirmText(request, user);

        verify(authService).requireEmailVerified(userId);
        verify(studyPackService).confirmExtractedText(request, userId);
        assertThat(response).isEqualTo(expected);
    }

    @Test
    void createFromText_requiresEmailVerification() {
        UUID userId = UUID.randomUUID();
        AuthenticatedUser user = new AuthenticatedUser(userId, UserRole.USER, true, 1);
        CreateStudyPackRequest request = new CreateStudyPackRequest("some notes", null);
        StudyPackResponse expected = new StudyPackResponse(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "TEXT",
                null,
                "Title",
                "Summary",
                "source",
                "Biology",
                List.of("Concept"),
                List.of("tag"),
                List.of(),
                OffsetDateTime.now(),
                new StudyPackMeta(null, 85L)
        );
        when(studyPackService.createFromText(request, userId)).thenReturn(expected);

        StudyPackResponse response = studyPackController.createFromText(request, user);

        verify(authService).requireEmailVerified(userId);
        verify(studyPackService).createFromText(request, userId);
        assertThat(response).isEqualTo(expected);
    }
}
