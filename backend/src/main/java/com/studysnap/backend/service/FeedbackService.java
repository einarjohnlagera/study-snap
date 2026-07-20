package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.FeedbackPromptContextResponse;
import com.studysnap.backend.entity.FeedbackEntity;
import com.studysnap.backend.entity.FeedbackStatus;
import com.studysnap.backend.entity.QuickReviewSessionStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.FeedbackRepository;
import com.studysnap.backend.repository.QuickReviewSessionRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {
    private static final String SUCCESS_MESSAGE = "Thanks! Your feedback helps improve NoteLib.";

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final EmailTemplateService emailTemplateService;
    private final EmailService emailService;
    private final StudySnapProperties studySnapProperties;
    private final RetentionService retentionService;
    private final QuickReviewSessionRepository quickReviewSessionRepository;

    @Transactional(readOnly = true)
    public FeedbackPromptContextResponse getPromptContext(UUID userId) {
        return new FeedbackPromptContextResponse(
                retentionService.isReturningAfterInactivity(userId),
                quickReviewSessionRepository.existsByUserIdAndStatusAndCompletedAtIsNotNull(
                        userId,
                        QuickReviewSessionStatus.COMPLETED
                )
        );
    }

    @Transactional
    public String submitFeedback(UUID userId, String message, String pageUrl) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        OffsetDateTime now = OffsetDateTime.now();
        FeedbackEntity feedback = new FeedbackEntity();
        feedback.setId(UUID.randomUUID());
        feedback.setUserId(userId);
        feedback.setEmail(user.getEmail());
        feedback.setMessage(message.trim());
        feedback.setPageUrl(normalizePageUrl(pageUrl));
        feedback.setStatus(FeedbackStatus.NEW);
        feedback.setCreatedAt(now);
        feedbackRepository.save(feedback);

        sendNotificationEmail(feedback, now);
        return SUCCESS_MESSAGE;
    }

    private void sendNotificationEmail(FeedbackEntity feedback, OffsetDateTime createdAt) {
        String supportEmail = studySnapProperties.getEmail().getSupport();
        if (supportEmail == null || supportEmail.isBlank()) {
            return;
        }

        try {
            EmailTemplateService.RenderedEmailTemplate rendered = emailTemplateService.render(
                    "feedback-notification",
                    Map.of(
                            "userEmail", feedback.getEmail(),
                            "message", feedback.getMessage(),
                            "pageUrl", feedback.getPageUrl() == null ? "Not provided" : feedback.getPageUrl(),
                            "submittedAt", createdAt.toString()
                    )
            );
            emailService.sendEmail(new EmailMessage(
                    supportEmail,
                    rendered.subject(),
                    rendered.htmlBody(),
                    rendered.textBody()
            ));
        } catch (RuntimeException ex) {
            log.warn("feedback.notification.email failed userId={} message={}", feedback.getUserId(), ex.getMessage());
        }
    }

    private String normalizePageUrl(String pageUrl) {
        if (pageUrl == null) {
            return null;
        }
        String trimmed = pageUrl.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
