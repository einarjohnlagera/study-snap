package com.studysnap.backend.controller;

import com.studysnap.backend.config.FeedbackHeaders;
import com.studysnap.backend.dto.FeedbackPromptContextResponse;
import com.studysnap.backend.dto.SimpleMessageResponse;
import com.studysnap.backend.dto.SubmitFeedbackRequest;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.security.AuthenticatedUser;
import com.studysnap.backend.service.FeedbackService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackControllerTest {

    private static final String SETTINGS_URL = "https://www.notelib.app/settings";

    @Mock
    private FeedbackService feedbackService;

    @Test
    void controller_requiresAuthenticatedUserRole() throws NoSuchMethodException {
        PreAuthorize annotation = FeedbackController.class
                .getMethod(
                        "submitFeedback",
                        AuthenticatedUser.class,
                        SubmitFeedbackRequest.class,
                        String.class
                )
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('USER','ADMIN')");
    }

    @Test
    void submitFeedback_returnsSuccessMessage() {
        FeedbackController controller = new FeedbackController(feedbackService);
        UUID userId = UUID.randomUUID();
        UUID feedbackId = UUID.randomUUID();
        when(feedbackService.submitFeedback(userId, "The page was confusing.", SETTINGS_URL))
                .thenReturn(new FeedbackService.FeedbackSubmissionResult(
                        feedbackId,
                        "Thanks! Your feedback helps improve NoteLib."
                ));

        ResponseEntity<SimpleMessageResponse> response = controller.submitFeedback(
                new AuthenticatedUser(userId, UserRole.USER, true, 0),
                new SubmitFeedbackRequest("The page was confusing."),
                SETTINGS_URL
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Thanks! Your feedback helps improve NoteLib.");
        assertThat(response.getHeaders().getFirst(FeedbackHeaders.FEEDBACK_ID)).isEqualTo(feedbackId.toString());
        verify(feedbackService).submitFeedback(userId, "The page was confusing.", SETTINGS_URL);
    }

    @Test
    void getPromptContext_returnsTheAuthenticatedUsersSignals() {
        FeedbackController controller = new FeedbackController(feedbackService);
        UUID userId = UUID.randomUUID();
        FeedbackPromptContextResponse expected = new FeedbackPromptContextResponse(true, true);
        when(feedbackService.getPromptContext(userId)).thenReturn(expected);

        FeedbackPromptContextResponse response = controller.getPromptContext(
                new AuthenticatedUser(userId, UserRole.USER, true, 0)
        );

        assertThat(response).isEqualTo(expected);
        verify(feedbackService).getPromptContext(userId);
    }

    @Test
    void uploadImage_usesTheAuthenticatedOwner() {
        FeedbackController controller = new FeedbackController(feedbackService);
        UUID userId = UUID.randomUUID();
        UUID feedbackId = UUID.randomUUID();
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "screen.png",
                "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}
        );

        ResponseEntity<Void> response = controller.uploadImage(
                new AuthenticatedUser(userId, UserRole.USER, true, 0),
                feedbackId,
                image
        );

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(feedbackService).uploadImage(userId, feedbackId, image);
    }
}
