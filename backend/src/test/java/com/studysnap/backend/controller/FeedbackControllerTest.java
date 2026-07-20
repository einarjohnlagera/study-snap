package com.studysnap.backend.controller;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackControllerTest {

    @Mock
    private FeedbackService feedbackService;

    @Test
    void controller_requiresAuthenticatedUserRole() throws NoSuchMethodException {
        PreAuthorize annotation = FeedbackController.class
                .getMethod("submitFeedback", AuthenticatedUser.class, SubmitFeedbackRequest.class, String.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasAnyRole('USER','ADMIN')");
    }

    @Test
    void submitFeedback_returnsSuccessMessage() {
        FeedbackController controller = new FeedbackController(feedbackService);
        UUID userId = UUID.randomUUID();
        when(feedbackService.submitFeedback(userId, "The page was confusing.", "https://www.notelib.app/settings"))
                .thenReturn("Thanks! Your feedback helps improve NoteLib.");

        SimpleMessageResponse response = controller.submitFeedback(
                new AuthenticatedUser(userId, UserRole.USER, true, 0),
                new SubmitFeedbackRequest("The page was confusing."),
                "https://www.notelib.app/settings"
        );

        assertThat(response.message()).isEqualTo("Thanks! Your feedback helps improve NoteLib.");
        verify(feedbackService).submitFeedback(userId, "The page was confusing.", "https://www.notelib.app/settings");
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
}
