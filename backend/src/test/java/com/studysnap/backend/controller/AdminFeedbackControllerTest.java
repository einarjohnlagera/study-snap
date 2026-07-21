package com.studysnap.backend.controller;

import com.studysnap.backend.service.FeedbackService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFeedbackControllerTest {
    @Mock
    private FeedbackService feedbackService;

    @Test
    void controller_requiresAdminRole() {
        PreAuthorize annotation = AdminFeedbackController.class.getAnnotation(PreAuthorize.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void getImage_returnsStoredBytesAndContentType() {
        UUID feedbackId = UUID.randomUUID();
        byte[] bytes = new byte[]{1, 2, 3};
        when(feedbackService.getImageForAdmin(feedbackId))
                .thenReturn(new FeedbackService.FeedbackImageData("image/webp", bytes));
        AdminFeedbackController controller = new AdminFeedbackController(feedbackService);

        ResponseEntity<byte[]> response = controller.getImage(feedbackId);

        assertThat(response.getHeaders().getContentType()).hasToString("image/webp");
        assertThat(response.getBody()).containsExactly(bytes);
        verify(feedbackService).getImageForAdmin(feedbackId);
    }
}
