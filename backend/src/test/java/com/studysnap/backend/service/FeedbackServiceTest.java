package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.FeedbackEntity;
import com.studysnap.backend.entity.FeedbackImageEntity;
import com.studysnap.backend.entity.FeedbackStatus;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.FeedbackImageNotFoundException;
import com.studysnap.backend.exception.FeedbackNotFoundException;
import com.studysnap.backend.exception.InvalidFeedbackImageException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.FeedbackImageRepository;
import com.studysnap.backend.repository.FeedbackRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";
    private static final String DASHBOARD_URL = "https://www.notelib.app/dashboard";

    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private FeedbackImageRepository feedbackImageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailTemplateService emailTemplateService;
    @Mock
    private EmailService emailService;
    @Mock
    private RetentionService retentionService;
    @Mock
    private QuickReviewSessionRepository quickReviewSessionRepository;

    private StudySnapProperties studySnapProperties;
    private FeedbackService feedbackService;

    @BeforeEach
    void setUp() {
        studySnapProperties = new StudySnapProperties();
        studySnapProperties.getEmail().setSupport("support@mail.notelib.app");
        feedbackService = new FeedbackService(
                feedbackRepository,
                feedbackImageRepository,
                userRepository,
                emailTemplateService,
                emailService,
                studySnapProperties,
                retentionService,
                quickReviewSessionRepository
        );
    }

    @Test
    void submitFeedback_savesFeedbackAndSendsNotification() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId, "[email protected]");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(emailTemplateService.render(eq("feedback-notification"), any(Map.class)))
                .thenReturn(new EmailTemplateService.RenderedEmailTemplate("subject", "<p>body</p>", "body"));

        FeedbackService.FeedbackSubmissionResult result = feedbackService.submitFeedback(
                userId,
                "The quiz button is confusing.",
                DASHBOARD_URL
        );

        assertThat(result.message()).isEqualTo("Thanks! Your feedback helps improve NoteLib.");
        assertThat(result.feedbackId()).isNotNull();
        ArgumentCaptor<FeedbackEntity> captor = ArgumentCaptor.forClass(FeedbackEntity.class);
        verify(feedbackRepository).save(captor.capture());
        FeedbackEntity saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getEmail()).isEqualTo("[email protected]");
        assertThat(saved.getMessage()).isEqualTo("The quiz button is confusing.");
        assertThat(saved.getPageUrl()).isEqualTo(DASHBOARD_URL);
        assertThat(saved.getStatus()).isEqualTo(FeedbackStatus.NEW);
        assertThat(saved.getCreatedAt()).isNotNull();
        verify(emailService).sendEmail(any(EmailMessage.class));
    }

    @Test
    void submitFeedback_throwsWhenUserDoesNotExist() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> feedbackService.submitFeedback(userId, "Message", "/dashboard"))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void submitFeedback_sendsOneNotificationImmediatelyEvenWhenAnImageWillFollow() {
        // The notification must never depend on the separate, best-effort image upload
        // succeeding or even arriving — otherwise a network failure on the optional image
        // could silently suppress the notification for an already-saved feedback submission.
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId, "learner@notelib.app");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(emailTemplateService.render(eq("feedback-notification"), any(Map.class)))
                .thenReturn(new EmailTemplateService.RenderedEmailTemplate("subject", "<p>body</p>", "body"));

        FeedbackService.FeedbackSubmissionResult submission = feedbackService.submitFeedback(
                userId,
                "The card is clipped.",
                "/dashboard"
        );

        assertThat(submission.feedbackId()).isNotNull();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> templateParameters = ArgumentCaptor.forClass(Map.class);
        verify(emailTemplateService).render(eq("feedback-notification"), templateParameters.capture());
        assertThat(templateParameters.getValue().get("imageNoticeHtml")).isEmpty();
        assertThat(templateParameters.getValue().get("imageNoticeText")).isEmpty();
        verify(emailService, times(1)).sendEmail(any(EmailMessage.class));
    }

    @Test
    void uploadImage_doesNotSendASecondNotification() {
        UUID userId = UUID.randomUUID();
        FeedbackEntity feedback = buildFeedback(userId);
        when(feedbackRepository.findByIdAndUserId(feedback.getId(), userId)).thenReturn(Optional.of(feedback));

        feedbackService.uploadImage(userId, feedback.getId(), image("screen.png", PNG_CONTENT_TYPE, new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        }));

        verify(emailService, never()).sendEmail(any());
        verify(emailTemplateService, never()).render(eq("feedback-notification"), any(Map.class));
    }

    @Test
    void uploadImage_acceptsPngJpegAndWebpMagicBytes() {
        UUID userId = UUID.randomUUID();
        FeedbackEntity feedback = buildFeedback(userId);
        when(feedbackRepository.findByIdAndUserId(feedback.getId(), userId)).thenReturn(Optional.of(feedback));

        feedbackService.uploadImage(userId, feedback.getId(), image("screen.png", PNG_CONTENT_TYPE, new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        }));
        feedbackService.uploadImage(userId, feedback.getId(), image("screen.jpg", JPEG_CONTENT_TYPE, new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01
        }));
        feedbackService.uploadImage(userId, feedback.getId(), image("screen.webp", "image/webp", new byte[]{
                'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'
        }));

        ArgumentCaptor<FeedbackImageEntity> imageCaptor = ArgumentCaptor.forClass(FeedbackImageEntity.class);
        verify(feedbackImageRepository, times(3)).saveAndFlush(imageCaptor.capture());
        assertThat(imageCaptor.getAllValues())
                .extracting(FeedbackImageEntity::getContentType)
                .containsExactly(PNG_CONTENT_TYPE, JPEG_CONTENT_TYPE, "image/webp");
    }

    @Test
    void uploadImage_rejectsUnsupportedOrMismatchedContent() {
        studySnapProperties.getEmail().setSupport("");
        UUID userId = UUID.randomUUID();
        FeedbackEntity feedback = buildFeedback(userId);
        when(feedbackRepository.findByIdAndUserId(feedback.getId(), userId)).thenReturn(Optional.of(feedback));
        MockMultipartFile invalidImage = image("screen.txt", "text/plain", "not-an-image".getBytes());

        assertThatThrownBy(() -> feedbackService.uploadImage(userId, feedback.getId(), invalidImage))
                .isInstanceOf(InvalidFeedbackImageException.class)
                .hasMessage("Upload a PNG, JPEG, or WebP screenshot.");
        verify(feedbackImageRepository, never()).saveAndFlush(any());
    }

    @Test
    void uploadImage_rejectsImageOverTwoMegabytes() {
        studySnapProperties.getEmail().setSupport("");
        UUID userId = UUID.randomUUID();
        FeedbackEntity feedback = buildFeedback(userId);
        when(feedbackRepository.findByIdAndUserId(feedback.getId(), userId)).thenReturn(Optional.of(feedback));
        MockMultipartFile oversizedImage = image("screen.png", PNG_CONTENT_TYPE, new byte[(2 * 1024 * 1024) + 1]);

        assertThatThrownBy(() -> feedbackService.uploadImage(userId, feedback.getId(), oversizedImage))
                .isInstanceOf(InvalidFeedbackImageException.class)
                .hasMessage("Screenshot is too large. Upload an image up to 2 MB.");
        verify(feedbackImageRepository, never()).saveAndFlush(any());
    }

    @Test
    void uploadImage_hidesFeedbackOwnedByAnotherUser() {
        UUID userId = UUID.randomUUID();
        UUID feedbackId = UUID.randomUUID();
        when(feedbackRepository.findByIdAndUserId(feedbackId, userId)).thenReturn(Optional.empty());
        MockMultipartFile png = image("screen.png", PNG_CONTENT_TYPE, new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        });

        assertThatThrownBy(() -> feedbackService.uploadImage(userId, feedbackId, png))
                .isInstanceOf(FeedbackNotFoundException.class);
        verify(feedbackImageRepository, never()).saveAndFlush(any());
    }

    @Test
    void getImageForAdmin_returnsBytesAndContentTypeOrNotFound() {
        UUID feedbackId = UUID.randomUUID();
        byte[] bytes = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        FeedbackImageEntity image = new FeedbackImageEntity();
        image.setFeedbackId(feedbackId);
        image.setContentType(JPEG_CONTENT_TYPE);
        image.setImageBytes(bytes);
        when(feedbackImageRepository.findById(feedbackId)).thenReturn(Optional.of(image));

        FeedbackService.FeedbackImageData result = feedbackService.getImageForAdmin(feedbackId);

        assertThat(result.contentType()).isEqualTo(JPEG_CONTENT_TYPE);
        assertThat(result.bytes()).containsExactly(bytes);

        UUID missingFeedbackId = UUID.randomUUID();
        when(feedbackImageRepository.findById(missingFeedbackId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> feedbackService.getImageForAdmin(missingFeedbackId))
                .isInstanceOf(FeedbackImageNotFoundException.class);
    }

    @Test
    void getPromptContext_combinesRetentionAndCompletedQuizSignals() {
        UUID userId = UUID.randomUUID();
        when(retentionService.isReturningAfterInactivity(userId)).thenReturn(true);
        when(quickReviewSessionRepository.existsByUserIdAndStatusAndCompletedAtIsNotNull(
                userId,
                QuickReviewSessionStatus.COMPLETED
        )).thenReturn(true);

        var response = feedbackService.getPromptContext(userId);

        assertThat(response.returningAfterInactivity()).isTrue();
        assertThat(response.hasCompletedQuizSession()).isTrue();
    }

    private UserEntity buildUser(UUID userId, String email) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail(email);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return user;
    }

    private FeedbackEntity buildFeedback(UUID userId) {
        FeedbackEntity feedback = new FeedbackEntity();
        feedback.setId(UUID.randomUUID());
        feedback.setUserId(userId);
        feedback.setEmail("learner@notelib.app");
        feedback.setMessage("The layout broke on mobile.");
        feedback.setPageUrl(DASHBOARD_URL);
        feedback.setStatus(FeedbackStatus.NEW);
        feedback.setCreatedAt(OffsetDateTime.now());
        return feedback;
    }

    private MockMultipartFile image(String fileName, String contentType, byte[] bytes) {
        return new MockMultipartFile("image", fileName, contentType, bytes);
    }
}
