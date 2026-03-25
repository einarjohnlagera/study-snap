package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.FeedbackEntity;
import com.studysnap.backend.entity.FeedbackStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.FeedbackRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailTemplateService emailTemplateService;
    @Mock
    private EmailService emailService;

    private StudySnapProperties studySnapProperties;
    private FeedbackService feedbackService;

    @BeforeEach
    void setUp() {
        studySnapProperties = new StudySnapProperties();
        studySnapProperties.getEmail().setSupport("support@mail.notelib.app");
        feedbackService = new FeedbackService(
                feedbackRepository,
                userRepository,
                emailTemplateService,
                emailService,
                studySnapProperties
        );
    }

    @Test
    void submitFeedback_savesFeedbackAndSendsNotification() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId, "[email protected]");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(emailTemplateService.render(eq("feedback-notification"), any(Map.class)))
                .thenReturn(new EmailTemplateService.RenderedEmailTemplate("subject", "<p>body</p>", "body"));

        String message = feedbackService.submitFeedback(userId, "The quiz button is confusing.", "https://www.notelib.app/dashboard");

        assertThat(message).isEqualTo("Thanks! Your feedback helps improve NoteLib.");
        ArgumentCaptor<FeedbackEntity> captor = ArgumentCaptor.forClass(FeedbackEntity.class);
        verify(feedbackRepository).save(captor.capture());
        FeedbackEntity saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getEmail()).isEqualTo("[email protected]");
        assertThat(saved.getMessage()).isEqualTo("The quiz button is confusing.");
        assertThat(saved.getPageUrl()).isEqualTo("https://www.notelib.app/dashboard");
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

    private UserEntity buildUser(UUID userId, String email) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail(email);
        user.setCreatedAt(OffsetDateTime.now());
        user.setUpdatedAt(OffsetDateTime.now());
        return user;
    }
}
