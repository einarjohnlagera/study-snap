package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.FeedbackPromptContextResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeedbackService {
    private static final String SUCCESS_MESSAGE = "Thanks! Your feedback helps improve NoteLib.";
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";
    private static final String WEBP_CONTENT_TYPE = "image/webp";
    private static final Set<String> SUPPORTED_IMAGE_CONTENT_TYPES = Set.of(
            PNG_CONTENT_TYPE,
            JPEG_CONTENT_TYPE,
            WEBP_CONTENT_TYPE
    );
    private static final int MAX_IMAGE_SIZE_BYTES = 2 * 1024 * 1024;
    private static final String IMAGE_TOO_LARGE_MESSAGE = "Screenshot is too large. Upload an image up to 2 MB.";
    private static final String INVALID_IMAGE_TYPE_MESSAGE = "Upload a PNG, JPEG, or WebP screenshot.";
    private static final String IMAGE_NOTICE_HTML = "<p><strong>Screenshot:</strong> Attached — view in Admin → Recent Feedback.</p>";
    private static final String IMAGE_NOTICE_TEXT = "Screenshot: Attached — view in Admin → Recent Feedback.";

    private final FeedbackRepository feedbackRepository;
    private final FeedbackImageRepository feedbackImageRepository;
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
    public FeedbackSubmissionResult submitFeedback(
            UUID userId,
            String message,
            String pageUrl
    ) {
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

        // Sent unconditionally, before any optional image attachment is known, so a client-side or
        // network failure in the separate image-upload call can never silently suppress this email —
        // see FeedbackServiceTest for the regression this guards against.
        sendNotificationEmail(feedback, now, false);
        return new FeedbackSubmissionResult(feedback.getId(), SUCCESS_MESSAGE);
    }

    @Transactional
    public void uploadImage(UUID userId, UUID feedbackId, MultipartFile image) {
        FeedbackEntity feedback = feedbackRepository.findByIdAndUserId(feedbackId, userId)
                .orElseThrow(FeedbackNotFoundException::new);

        ValidatedImage validatedImage = validateImage(image);

        FeedbackImageEntity feedbackImage = new FeedbackImageEntity();
        feedbackImage.setFeedbackId(feedbackId);
        feedbackImage.setContentType(validatedImage.contentType());
        feedbackImage.setSizeBytes(validatedImage.bytes().length);
        feedbackImage.setImageBytes(validatedImage.bytes());
        feedbackImage.setCreatedAt(OffsetDateTime.now());
        feedbackImageRepository.saveAndFlush(feedbackImage);
    }

    @Transactional(readOnly = true)
    public FeedbackImageData getImageForAdmin(UUID feedbackId) {
        FeedbackImageEntity image = feedbackImageRepository.findById(feedbackId)
                .orElseThrow(FeedbackImageNotFoundException::new);
        return new FeedbackImageData(image.getContentType(), image.getImageBytes());
    }

    private ValidatedImage validateImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new InvalidFeedbackImageException("Please choose a screenshot to upload.");
        }
        if (image.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new InvalidFeedbackImageException(IMAGE_TOO_LARGE_MESSAGE);
        }

        byte[] bytes;
        try {
            bytes = image.getBytes();
        } catch (IOException ex) {
            throw new InvalidFeedbackImageException("Could not read this screenshot. Please choose another image.");
        }

        String detectedContentType = detectContentType(bytes);
        String declaredContentType = image.getContentType() == null
                ? ""
                : image.getContentType().trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_IMAGE_CONTENT_TYPES.contains(declaredContentType)
                || !declaredContentType.equals(detectedContentType)) {
            throw new InvalidFeedbackImageException(INVALID_IMAGE_TYPE_MESSAGE);
        }
        return new ValidatedImage(detectedContentType, bytes);
    }

    private String detectContentType(byte[] bytes) {
        if (hasPrefix(bytes, new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return PNG_CONTENT_TYPE;
        }
        if (hasPrefix(bytes, new int[]{0xFF, 0xD8, 0xFF})) {
            return JPEG_CONTENT_TYPE;
        }
        if (bytes.length >= 12
                && hasAsciiAt(bytes, 0, "RIFF")
                && hasAsciiAt(bytes, 8, "WEBP")) {
            return WEBP_CONTENT_TYPE;
        }
        return "";
    }

    private boolean hasPrefix(byte[] bytes, int[] signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if ((bytes[index] & 0xFF) != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private boolean hasAsciiAt(byte[] bytes, int offset, String value) {
        for (int index = 0; index < value.length(); index++) {
            if (bytes[offset + index] != (byte) value.charAt(index)) {
                return false;
            }
        }
        return true;
    }

    private void sendNotificationEmail(FeedbackEntity feedback, OffsetDateTime createdAt, boolean hasImage) {
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
                            "submittedAt", createdAt.toString(),
                            "imageNoticeHtml", hasImage ? IMAGE_NOTICE_HTML : "",
                            "imageNoticeText", hasImage ? IMAGE_NOTICE_TEXT : ""
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

    public record FeedbackSubmissionResult(UUID feedbackId, String message) {
    }

    public record FeedbackImageData(String contentType, byte[] bytes) {
    }

    private record ValidatedImage(String contentType, byte[] bytes) {
    }
}
