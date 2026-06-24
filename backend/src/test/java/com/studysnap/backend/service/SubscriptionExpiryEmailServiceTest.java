package com.studysnap.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.EmailLogEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.RetentionEmailType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserRole;
import com.studysnap.backend.entity.UserStatus;
import com.studysnap.backend.repository.EmailLogRepository;
import com.studysnap.backend.repository.SubscriptionRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionExpiryEmailServiceTest {
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-28T03:00:00Z");
    private static final String APP_BASE_URL = "https://notelib.app";
    private static final String RENEW_URL = APP_BASE_URL + "/settings?tab=billing";

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private EmailLogRepository emailLogRepository;
    @Mock
    private EmailTemplateService emailTemplateService;
    @Mock
    private EmailService emailService;

    private SubscriptionExpiryEmailService service;

    @BeforeEach
    void setUp() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getEmail().setAppBaseUrl(APP_BASE_URL);
        service = new SubscriptionExpiryEmailService(
                properties,
                subscriptionRepository,
                emailLogRepository,
                emailTemplateService,
                emailService
        );
        lenient().when(emailService.sendEmail(any(EmailMessage.class))).thenReturn(true);
    }

    @Test
    void sendExpiryNotificationEmails_sendsSevenDayEmailForQualifyingPlusSubscription() {
        UserEntity user = verifiedUser("Ada", "Ada Lovelace");
        SubscriptionEntity subscription = subscription(
                user,
                PlanType.PLUS,
                SubscriptionStatus.ACTIVE,
                NOW.plusDays(7)
        );
        stubWindows(List.of(subscription), List.of(), List.of());
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                user.getId(),
                RetentionEmailType.SUBSCRIPTION_EXPIRY_7_DAY,
                NOW.minusDays(14)
        )).thenReturn(false);
        when(emailTemplateService.render(eq("subscription-expiry-7-day"), any()))
                .thenReturn(rendered("Your Plus plan expires in 7 days"));

        SubscriptionExpiryEmailService.SubscriptionExpiryEmailDispatchSummary summary =
                service.sendExpiryNotificationEmails(NOW);

        assertThat(summary.sevenDaySent()).isEqualTo(1);
        assertThat(summary.oneDaySent()).isZero();
        assertThat(summary.postExpirySent()).isZero();
        verify(emailService).sendEmail(any(EmailMessage.class));
        assertLoggedEmail(user.getId(), RetentionEmailType.SUBSCRIPTION_EXPIRY_7_DAY);
        assertRenderedParameters("subscription-expiry-7-day", Map.of(
                "firstName", "Ada",
                "planName", "Plus",
                "expiryDate", "June 4, 2026",
                "renewUrl", RENEW_URL
        ));
    }

    @Test
    void sendExpiryNotificationEmails_skipsSevenDayEmailWhenCooldownExists() {
        UserEntity user = verifiedUser("Ada", "Ada Lovelace");
        SubscriptionEntity subscription = subscription(user, PlanType.PLUS, SubscriptionStatus.ACTIVE, NOW.plusDays(7));
        stubWindows(List.of(subscription), List.of(), List.of());
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                user.getId(),
                RetentionEmailType.SUBSCRIPTION_EXPIRY_7_DAY,
                NOW.minusDays(14)
        )).thenReturn(true);

        SubscriptionExpiryEmailService.SubscriptionExpiryEmailDispatchSummary summary =
                service.sendExpiryNotificationEmails(NOW);

        assertThat(summary.sevenDaySent()).isZero();
        verify(emailTemplateService, never()).render(eq("subscription-expiry-7-day"), any());
        verify(emailLogRepository, never()).save(any(EmailLogEntity.class));
    }

    @Test
    void sendExpiryNotificationEmails_skipsSevenDayEmailForUnverifiedUser() {
        UserEntity user = unverifiedUser();
        SubscriptionEntity subscription = subscription(user, PlanType.PLUS, SubscriptionStatus.ACTIVE, NOW.plusDays(7));
        stubWindows(List.of(subscription), List.of(), List.of());

        SubscriptionExpiryEmailService.SubscriptionExpiryEmailDispatchSummary summary =
                service.sendExpiryNotificationEmails(NOW);

        assertThat(summary.sevenDaySent()).isZero();
        verify(emailTemplateService, never()).render(eq("subscription-expiry-7-day"), any());
        verify(emailLogRepository, never()).save(any(EmailLogEntity.class));
    }

    @Test
    void sendExpiryNotificationEmails_sendsOneDayEmailForQualifyingProSubscription() {
        UserEntity user = verifiedUser("Grace", "Grace Hopper");
        SubscriptionEntity subscription = subscription(user, PlanType.PRO, SubscriptionStatus.ACTIVE, NOW.plusDays(1));
        stubWindows(List.of(), List.of(subscription), List.of());
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                user.getId(),
                RetentionEmailType.SUBSCRIPTION_EXPIRY_1_DAY,
                NOW.minusDays(3)
        )).thenReturn(false);
        when(emailTemplateService.render(eq("subscription-expiry-1-day"), any()))
                .thenReturn(rendered("Your Pro plan expires tomorrow"));

        SubscriptionExpiryEmailService.SubscriptionExpiryEmailDispatchSummary summary =
                service.sendExpiryNotificationEmails(NOW);

        assertThat(summary.oneDaySent()).isEqualTo(1);
        assertLoggedEmail(user.getId(), RetentionEmailType.SUBSCRIPTION_EXPIRY_1_DAY);
        assertRenderedParameters("subscription-expiry-1-day", Map.of(
                "firstName", "Grace",
                "planName", "Pro",
                "expiryDate", "May 29, 2026",
                "renewUrl", RENEW_URL
        ));
    }

    @Test
    void sendExpiryNotificationEmails_skipsOneDayEmailWhenCooldownExists() {
        UserEntity user = verifiedUser("Grace", "Grace Hopper");
        SubscriptionEntity subscription = subscription(user, PlanType.PRO, SubscriptionStatus.ACTIVE, NOW.plusDays(1));
        stubWindows(List.of(), List.of(subscription), List.of());
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                user.getId(),
                RetentionEmailType.SUBSCRIPTION_EXPIRY_1_DAY,
                NOW.minusDays(3)
        )).thenReturn(true);

        SubscriptionExpiryEmailService.SubscriptionExpiryEmailDispatchSummary summary =
                service.sendExpiryNotificationEmails(NOW);

        assertThat(summary.oneDaySent()).isZero();
        verify(emailTemplateService, never()).render(eq("subscription-expiry-1-day"), any());
        verify(emailLogRepository, never()).save(any(EmailLogEntity.class));
    }

    @Test
    void sendExpiryNotificationEmails_sendsPostExpiryEmailForRecentlyExpiredSubscription() {
        UserEntity user = verifiedUser("Linus", "Linus");
        SubscriptionEntity subscription = subscription(user, PlanType.PLUS, SubscriptionStatus.EXPIRED, NOW.minusHours(1));
        stubWindows(List.of(), List.of(), List.of(subscription));
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                user.getId(),
                RetentionEmailType.SUBSCRIPTION_EXPIRED,
                NOW.minusDays(30)
        )).thenReturn(false);
        when(emailTemplateService.render(eq("subscription-expired"), any()))
                .thenReturn(rendered("Your Plus plan has ended"));

        SubscriptionExpiryEmailService.SubscriptionExpiryEmailDispatchSummary summary =
                service.sendExpiryNotificationEmails(NOW);

        assertThat(summary.postExpirySent()).isEqualTo(1);
        assertLoggedEmail(user.getId(), RetentionEmailType.SUBSCRIPTION_EXPIRED);
        assertRenderedParameters("subscription-expired", Map.of(
                "firstName", "Linus",
                "planName", "Plus",
                "expiryDate", "May 28, 2026",
                "renewUrl", RENEW_URL
        ));
    }

    @Test
    void sendExpiryNotificationEmails_skipsPostExpiryEmailWhenCooldownExists() {
        UserEntity user = verifiedUser("Linus", "Linus");
        SubscriptionEntity subscription = subscription(user, PlanType.PLUS, SubscriptionStatus.EXPIRED, NOW.minusHours(1));
        stubWindows(List.of(), List.of(), List.of(subscription));
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                user.getId(),
                RetentionEmailType.SUBSCRIPTION_EXPIRED,
                NOW.minusDays(30)
        )).thenReturn(true);

        SubscriptionExpiryEmailService.SubscriptionExpiryEmailDispatchSummary summary =
                service.sendExpiryNotificationEmails(NOW);

        assertThat(summary.postExpirySent()).isZero();
        verify(emailTemplateService, never()).render(eq("subscription-expired"), any());
        verify(emailLogRepository, never()).save(any(EmailLogEntity.class));
    }

    @Test
    void sendExpiryNotificationEmails_continuesWhenOneSendFails() {
        UserEntity failingUser = verifiedUser("Fail", "Fail");
        UserEntity successfulUser = verifiedUser("Ok", "Ok");
        SubscriptionEntity failingSubscription = subscription(
                failingUser,
                PlanType.PLUS,
                SubscriptionStatus.ACTIVE,
                NOW.plusDays(7)
        );
        SubscriptionEntity successfulSubscription = subscription(
                successfulUser,
                PlanType.PLUS,
                SubscriptionStatus.ACTIVE,
                NOW.plusDays(7).plusHours(1)
        );
        stubWindows(List.of(failingSubscription, successfulSubscription), List.of(), List.of());
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                any(UUID.class),
                eq(RetentionEmailType.SUBSCRIPTION_EXPIRY_7_DAY),
                eq(NOW.minusDays(14))
        )).thenReturn(false);
        when(emailTemplateService.render(eq("subscription-expiry-7-day"), any()))
                .thenReturn(rendered("Your Plus plan expires in 7 days"));
        when(emailService.sendEmail(any(EmailMessage.class)))
                .thenThrow(new RuntimeException("Resend unavailable"))
                .thenReturn(true);

        SubscriptionExpiryEmailService.SubscriptionExpiryEmailDispatchSummary summary =
                service.sendExpiryNotificationEmails(NOW);

        assertThat(summary.sevenDaySent()).isEqualTo(1);
        assertLoggedEmail(successfulUser.getId(), RetentionEmailType.SUBSCRIPTION_EXPIRY_7_DAY);
    }

    @Test
    void sendExpiryNotificationEmails_returnsSummaryCountsForAllWindows() {
        UserEntity sevenDayUser = verifiedUser("Seven", "Seven");
        UserEntity oneDayUser = verifiedUser("One", "One");
        UserEntity expiredUser = verifiedUser("Expired", "Expired");
        stubWindows(
                List.of(subscription(sevenDayUser, PlanType.PLUS, SubscriptionStatus.ACTIVE, NOW.plusDays(7))),
                List.of(subscription(oneDayUser, PlanType.PRO, SubscriptionStatus.ACTIVE, NOW.plusDays(1))),
                List.of(subscription(expiredUser, PlanType.PRO, SubscriptionStatus.EXPIRED, NOW.minusHours(1)))
        );
        when(emailLogRepository.existsByUserIdAndEmailTypeAndSentAtAfter(
                any(UUID.class),
                any(RetentionEmailType.class),
                any(OffsetDateTime.class)
        )).thenReturn(false);
        when(emailTemplateService.render(any(), any())).thenReturn(rendered("Subject"));

        SubscriptionExpiryEmailService.SubscriptionExpiryEmailDispatchSummary summary =
                service.sendExpiryNotificationEmails(NOW);

        assertThat(summary.sevenDaySent()).isEqualTo(1);
        assertThat(summary.oneDaySent()).isEqualTo(1);
        assertThat(summary.postExpirySent()).isEqualTo(1);
    }

    private void stubWindows(
            List<SubscriptionEntity> sevenDaySubscriptions,
            List<SubscriptionEntity> oneDaySubscriptions,
            List<SubscriptionEntity> expiredSubscriptions
    ) {
        when(subscriptionRepository.findByPlanTypeInAndStatusAndEndAtBetween(
                anyCollection(),
                eq(SubscriptionStatus.ACTIVE),
                eq(NOW.plusDays(6)),
                eq(NOW.plusDays(8))
        )).thenReturn(sevenDaySubscriptions);
        when(subscriptionRepository.findByPlanTypeInAndStatusAndEndAtBetween(
                anyCollection(),
                eq(SubscriptionStatus.ACTIVE),
                eq(NOW),
                eq(NOW.plusHours(36))
        )).thenReturn(oneDaySubscriptions);
        when(subscriptionRepository.findByPlanTypeInAndStatusAndEndAtBetween(
                anyCollection(),
                eq(SubscriptionStatus.EXPIRED),
                eq(NOW.minusHours(36)),
                eq(NOW)
        )).thenReturn(expiredSubscriptions);
    }

    private SubscriptionEntity subscription(
            UserEntity user,
            PlanType planType,
            SubscriptionStatus status,
            OffsetDateTime endAt
    ) {
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(user);
        subscription.setPlanType(planType);
        subscription.setStatus(status);
        subscription.setStartAt(endAt.minusDays(30));
        subscription.setEndAt(endAt);
        subscription.setBillingType(BillingType.PREPAID);
        subscription.setProvider(BillingProvider.XENDIT);
        subscription.setCreatedAt(endAt.minusDays(30));
        subscription.setUpdatedAt(endAt.minusDays(30));
        return subscription;
    }

    private UserEntity verifiedUser(String firstName, String displayName) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("user-" + user.getId() + "@notelib.test");
        user.setFirstName(firstName);
        user.setDisplayName(displayName);
        user.setUsername("user" + user.getId().toString().replace("-", "").substring(0, 8));
        user.setStatus(UserStatus.ACTIVE);
        user.setRole(UserRole.USER);
        user.setEmailVerifiedAt(NOW.minusDays(10));
        return user;
    }

    private UserEntity unverifiedUser() {
        UserEntity user = verifiedUser("Unverified", "Unverified");
        user.setEmailVerifiedAt(null);
        return user;
    }

    private EmailTemplateService.RenderedEmailTemplate rendered(String subject) {
        return new EmailTemplateService.RenderedEmailTemplate(subject, "<p>Body</p>", "Body");
    }

    private void assertLoggedEmail(UUID userId, RetentionEmailType emailType) {
        ArgumentCaptor<EmailLogEntity> logCaptor = ArgumentCaptor.forClass(EmailLogEntity.class);
        verify(emailLogRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getUserId()).isEqualTo(userId);
        assertThat(logCaptor.getValue().getEmailType()).isEqualTo(emailType);
        assertThat(logCaptor.getValue().getSentAt()).isEqualTo(NOW);
    }

    @SuppressWarnings("unchecked")
    private void assertRenderedParameters(String templateName, Map<String, String> expectedParameters) {
        ArgumentCaptor<Map<String, String>> parametersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(emailTemplateService).render(eq(templateName), parametersCaptor.capture());
        assertThat(parametersCaptor.getValue()).containsAllEntriesOf(expectedParameters);
    }
}
