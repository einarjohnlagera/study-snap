package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.ActivityType;
import com.studysnap.backend.entity.EmailLogEntity;
import com.studysnap.backend.entity.ProfileType;
import com.studysnap.backend.entity.RetentionEmailType;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.repository.ActivityEventRepository;
import com.studysnap.backend.repository.EmailLogRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReEngagementCampaignService {

    // Resend free tier: 100 emails/day. Cap each invocation so the admin re-clicks Send for remaining users.
    private static final int SEND_BATCH_LIMIT = 100;
    private static final int INACTIVITY_DAYS = 30;

    private final UserRepository userRepository;
    private final ActivityEventRepository activityEventRepository;
    private final EmailLogRepository emailLogRepository;
    private final EmailTemplateService emailTemplateService;
    private final EmailService emailService;
    private final StudySnapProperties properties;
    private final EmailUnsubscribeLinkService emailUnsubscribeLinkService;

    @Transactional(readOnly = true)
    public int countEligible() {
        return countEligible(OffsetDateTime.now(ZoneOffset.UTC));
    }

    int countEligible(OffsetDateTime now) {
        return (int) buildEligibleStream(now).count();
    }

    @Transactional
    public ReEngagementSendResult send() {
        return send(OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Transactional
    ReEngagementSendResult send(OffsetDateTime now) {
        List<UserEntity> batch = buildEligibleStream(now)
                .limit(SEND_BATCH_LIMIT)
                .toList();

        int sent = 0;
        int skipped = 0;

        for (UserEntity user : batch) {
            if (sendEmail(user, now)) {
                sent++;
            } else {
                skipped++;
            }
        }

        return new ReEngagementSendResult(sent, skipped);
    }

    private java.util.stream.Stream<UserEntity> buildEligibleStream(OffsetDateTime now) {
        OffsetDateTime activityCutoff = now.minusDays(INACTIVITY_DAYS);
        return userRepository.findByStatusAndEmailVerifiedAtIsNotNullAndMarketingEmailsEnabledTrue(UserStatus.ACTIVE).stream()
                .filter(user -> !alreadySent(user.getId()))
                .filter(user -> isInactive(user.getId(), activityCutoff));
    }

    private boolean sendEmail(UserEntity user, OffsetDateTime now) {
        try {
            String templateName = resolveTemplateName(user.getProfileType());
            EmailUnsubscribeLinkService.OptionalEmailUnsubscribeContext unsubscribeContext = buildUnsubscribeContext(user);
            EmailTemplateService.RenderedEmailTemplate rendered = emailTemplateService.render(
                    templateName,
                    Map.of(
                            "firstName", resolveFirstName(user),
                            "dashboardUrl", buildAbsoluteUrl("/dashboard"),
                            "unsubscribeUrl", unsubscribeContext.unsubscribeUrl(),
                            "unsubscribeFooterHtml", unsubscribeContext.htmlFooter(),
                            "unsubscribeFooterText", unsubscribeContext.textFooter()
                    )
            );
            emailService.sendEmail(new EmailMessage(
                    user.getEmail(),
                    rendered.subject(),
                    rendered.htmlBody(),
                    rendered.textBody(),
                    unsubscribeContext.headers()
            ));
            logEmailSent(user.getId(), now);
            return true;
        } catch (RuntimeException ex) {
            log.warn("re-engagement.send failed userId={} message={}", user.getId(), ex.getMessage());
            return false;
        }
    }

    private EmailUnsubscribeLinkService.OptionalEmailUnsubscribeContext buildUnsubscribeContext(UserEntity user) {
        try {
            return emailUnsubscribeLinkService.buildContext(user.getId(), UnsubscribeCategory.MARKETING);
        } catch (RuntimeException ex) {
            log.warn("re-engagement.unsubscribe_link failed userId={} message={}", user.getId(), ex.getMessage());
            return EmailUnsubscribeLinkService.OptionalEmailUnsubscribeContext.empty();
        }
    }

    private boolean alreadySent(UUID userId) {
        return emailLogRepository.existsByUserIdAndEmailType(userId, RetentionEmailType.RE_ENGAGEMENT_2025);
    }

    private boolean isInactive(UUID userId, OffsetDateTime cutoff) {
        return !activityEventRepository.existsByUserIdAndActivityTypeInAndCreatedAtGreaterThanEqual(
                userId, ActivityType.MEANINGFUL_STUDY_ACTIVITIES, cutoff);
    }

    private void logEmailSent(UUID userId, OffsetDateTime now) {
        EmailLogEntity entity = new EmailLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setEmailType(RetentionEmailType.RE_ENGAGEMENT_2025);
        entity.setSentAt(now);
        emailLogRepository.save(entity);
    }

    private String resolveTemplateName(ProfileType profileType) {
        if (profileType == ProfileType.PROFESSIONAL) return "re-engagement-professional";
        if (profileType == ProfileType.TEACHER) return "re-engagement-teacher";
        return "re-engagement-student";
    }

    private String resolveFirstName(UserEntity user) {
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) {
            return user.getFirstName().trim();
        }
        return "there";
    }

    private String buildAbsoluteUrl(String path) {
        String baseUrl = properties.getEmail().getAppBaseUrl();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBase + path;
    }

    public record ReEngagementSendResult(int sent, int skipped) {}
}
