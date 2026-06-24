package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.EmailLogEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.RetentionEmailType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.repository.EmailLogRepository;
import com.studysnap.backend.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExpiryEmailService {
    private static final List<PlanType> PAID_PLAN_TYPES = List.of(PlanType.PLUS, PlanType.PRO);
    private static final ZoneId EXPIRY_DATE_ZONE = ZoneId.of("Asia/Manila");
    private static final DateTimeFormatter EXPIRY_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US);
    private static final String TEMPLATE_EXPIRY_7_DAY = "subscription-expiry-7-day";
    private static final String TEMPLATE_EXPIRY_1_DAY = "subscription-expiry-1-day";
    private static final String TEMPLATE_EXPIRED = "subscription-expired";
    private static final String RENEW_PATH = "/settings?tab=billing";
    private static final String PARAM_FIRST_NAME = "firstName";
    private static final String PARAM_PLAN_NAME = "planName";
    private static final String PARAM_EXPIRY_DATE = "expiryDate";
    private static final String PARAM_RENEW_URL = "renewUrl";
    private static final int SEVEN_DAY_WINDOW_START_DAYS = 6;
    private static final int SEVEN_DAY_WINDOW_END_DAYS = 8;
    private static final int ONE_DAY_WINDOW_HOURS = 36;
    private static final int POST_EXPIRY_WINDOW_HOURS = 36;
    private static final int SEVEN_DAY_COOLDOWN_DAYS = 14;
    private static final int ONE_DAY_COOLDOWN_DAYS = 3;
    private static final int POST_EXPIRY_COOLDOWN_DAYS = 30;

    private final StudySnapProperties properties;
    private final SubscriptionRepository subscriptionRepository;
    private final EmailLogRepository emailLogRepository;
    private final EmailTemplateService emailTemplateService;
    private final EmailService emailService;

    @Transactional
    public SubscriptionExpiryEmailDispatchSummary sendExpiryNotificationEmails() {
        return sendExpiryNotificationEmails(OffsetDateTime.now(ZoneOffset.UTC));
    }

    SubscriptionExpiryEmailDispatchSummary sendExpiryNotificationEmails(OffsetDateTime now) {
        int sevenDaySent = dispatchEmails(
                findPreExpirySubscriptions(
                        now.plusDays(SEVEN_DAY_WINDOW_START_DAYS),
                        now.plusDays(SEVEN_DAY_WINDOW_END_DAYS)
                ),
                RetentionEmailType.SUBSCRIPTION_EXPIRY_7_DAY,
                TEMPLATE_EXPIRY_7_DAY,
                now.minusDays(SEVEN_DAY_COOLDOWN_DAYS),
                now
        );
        int oneDaySent = dispatchEmails(
                findPreExpirySubscriptions(now, now.plusHours(ONE_DAY_WINDOW_HOURS)),
                RetentionEmailType.SUBSCRIPTION_EXPIRY_1_DAY,
                TEMPLATE_EXPIRY_1_DAY,
                now.minusDays(ONE_DAY_COOLDOWN_DAYS),
                now
        );
        int postExpirySent = dispatchEmails(
                subscriptionRepository.findByPlanTypeInAndStatusAndEndAtBetween(
                        PAID_PLAN_TYPES,
                        SubscriptionStatus.EXPIRED,
                        now.minusHours(POST_EXPIRY_WINDOW_HOURS),
                        now
                ),
                RetentionEmailType.SUBSCRIPTION_EXPIRED,
                TEMPLATE_EXPIRED,
                now.minusDays(POST_EXPIRY_COOLDOWN_DAYS),
                now
        );
        return new SubscriptionExpiryEmailDispatchSummary(sevenDaySent, oneDaySent, postExpirySent);
    }

    private List<SubscriptionEntity> findPreExpirySubscriptions(OffsetDateTime windowStart, OffsetDateTime windowEnd) {
        return subscriptionRepository.findByPlanTypeInAndStatusAndEndAtBetween(
                PAID_PLAN_TYPES,
                SubscriptionStatus.ACTIVE,
                windowStart,
                windowEnd
        );
    }

    private int dispatchEmails(
            List<SubscriptionEntity> subscriptions,
            RetentionEmailType emailType,
            String templateName,
            OffsetDateTime cooldownCutoff,
            OffsetDateTime now
    ) {
        int sent = 0;
        for (SubscriptionEntity subscription : subscriptions) {
            if (sendSubscriptionExpiryEmail(subscription, emailType, templateName, cooldownCutoff, now)) {
                sent += 1;
            }
        }
        return sent;
    }

    private boolean sendSubscriptionExpiryEmail(
            SubscriptionEntity subscription,
            RetentionEmailType emailType,
            String templateName,
            OffsetDateTime cooldownCutoff,
            OffsetDateTime now
    ) {
        UserEntity user = subscription.getUser();
        if (subscription.getEndAt() == null) {
            return false;
        }
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            log.warn(
                    "billing.expiry.email.skipped missingRecipient subscriptionId={} emailType={}",
                    subscription.getId(),
                    emailType
            );
            return false;
        }
        if (user.getEmailVerifiedAt() == null) {
            return false;
        }
        if (emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(user.getId(), emailType, cooldownCutoff)) {
            return false;
        }
        try {
            EmailTemplateService.RenderedEmailTemplate rendered = emailTemplateService.render(
                    templateName,
                    buildTemplateParameters(subscription, user)
            );
            boolean sent = emailService.sendEmail(new EmailMessage(
                    user.getEmail(),
                    rendered.subject(),
                    rendered.htmlBody(),
                    rendered.textBody()
            ));
            if (!sent) {
                return false;
            }
            logEmailSent(user.getId(), emailType, now);
            return true;
        } catch (RuntimeException ex) {
            log.warn(
                    "billing.expiry.email.send.failed userId={} emailType={} message={}",
                    user.getId(),
                    emailType,
                    ex.getMessage()
            );
            return false;
        }
    }

    private Map<String, String> buildTemplateParameters(SubscriptionEntity subscription, UserEntity user) {
        return Map.of(
                PARAM_FIRST_NAME, resolveFirstName(user),
                PARAM_PLAN_NAME, subscription.getPlanType().getDisplayName(),
                PARAM_EXPIRY_DATE, formatExpiryDate(subscription.getEndAt()),
                PARAM_RENEW_URL, buildAbsoluteUrl(RENEW_PATH)
        );
    }

    private String formatExpiryDate(OffsetDateTime endAt) {
        return endAt.atZoneSameInstant(EXPIRY_DATE_ZONE).format(EXPIRY_DATE_FORMATTER);
    }

    private String resolveFirstName(UserEntity user) {
        if (StringUtils.hasText(user.getFirstName())) {
            return user.getFirstName().trim();
        }
        if (StringUtils.hasText(user.getDisplayName())) {
            return user.getDisplayName().trim();
        }
        return "there";
    }

    private String buildAbsoluteUrl(String path) {
        String baseUrl = properties.getEmail().getAppBaseUrl();
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return normalizedBase + path;
    }

    private void logEmailSent(UUID userId, RetentionEmailType emailType, OffsetDateTime now) {
        EmailLogEntity entity = new EmailLogEntity();
        entity.setId(UUID.randomUUID());
        entity.setUserId(userId);
        entity.setEmailType(emailType);
        entity.setSentAt(now);
        emailLogRepository.save(entity);
    }

    public record SubscriptionExpiryEmailDispatchSummary(
            int sevenDaySent,
            int oneDaySent,
            int postExpirySent
    ) {
    }
}
