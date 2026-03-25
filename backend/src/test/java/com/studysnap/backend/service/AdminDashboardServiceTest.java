package com.studysnap.backend.service;

import com.studysnap.backend.dto.AdminDashboardRecentEventsResponse;
import com.studysnap.backend.dto.AdminDashboardSummaryResponse;
import com.studysnap.backend.dto.AdminDashboardTopContentResponse;
import com.studysnap.backend.dto.AdminPublicNoteMetricItemResponse;
import com.studysnap.backend.dto.AdminSubjectMetricItemResponse;
import com.studysnap.backend.entity.AnalyticsEventEntity;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.PaymentTransactionStatus;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PaymentTransactionRepository;
import com.studysnap.backend.repository.PremiumWaitlistRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.SubscriptionRepository;
import com.studysnap.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private AnalyticsEventRepository analyticsEventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private PremiumWaitlistRepository premiumWaitlistRepository;

    private AdminDashboardService adminDashboardService;

    @BeforeEach
    void setUp() {
        adminDashboardService = new AdminDashboardService(
                analyticsEventRepository,
                userRepository,
                noteRepository,
                studyPackRepository,
                subscriptionRepository,
                paymentTransactionRepository,
                premiumWaitlistRepository
        );
    }

    @Test
    void getSummary_aggregatesOverviewBillingAndEngagementMetrics() {
        UserEntity monthlyUser = buildUser("[email protected]");
        UserEntity yearlyUser = buildUser("[email protected]");
        SubscriptionEntity monthlySubscription = buildSubscription(
                monthlyUser,
                OffsetDateTime.parse("2026-03-01T00:00:00Z"),
                OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                false
        );
        SubscriptionEntity yearlySubscription = buildSubscription(
                yearlyUser,
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                OffsetDateTime.parse("2027-01-01T00:00:00Z"),
                true
        );

        when(subscriptionRepository.findCurrentlyActiveByPlanTypeAndStatus(eq(PlanType.PREMIUM), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of(monthlySubscription, yearlySubscription));
        when(paymentTransactionRepository.findByUser_IdInAndStatusOrderByCreatedAtDesc(any(), eq(PaymentTransactionStatus.SUCCESS)))
                .thenReturn(List.of(
                        buildTransaction(monthlyUser, new BigDecimal("249.00"), "PHP", PaymentTransactionStatus.SUCCESS),
                        buildTransaction(yearlyUser, new BigDecimal("1999.00"), "PHP", PaymentTransactionStatus.SUCCESS)
                ));
        when(userRepository.count()).thenReturn(120L);
        when(userRepository.countByEmailVerifiedAtIsNotNull()).thenReturn(90L);
        when(premiumWaitlistRepository.count()).thenReturn(41L);
        when(noteRepository.count()).thenReturn(420L);
        when(noteRepository.countByVisibility(any())).thenReturn(44L);
        when(studyPackRepository.count()).thenReturn(275L);
        when(studyPackRepository.countByCreatedAtGreaterThanEqual(any())).thenReturn(38L);
        when(paymentTransactionRepository.countByStatus(PaymentTransactionStatus.FAILED)).thenReturn(3L);
        when(analyticsEventRepository.countByEventType(any())).thenAnswer(invocation -> switch (invocation.getArgument(0, AnalyticsEventType.class)) {
            case PUBLIC_NOTE_VIEWED -> 830L;
            case PUBLIC_NOTE_COPIED -> 67L;
            case SUBSCRIPTION_STARTED -> 18L;
            case QUICK_REVIEW_STARTED -> 240L;
            case CHALLENGE_QUIZ_STARTED -> 81L;
            case ADAPTIVE_PRACTICE_STARTED -> 44L;
            case PAYWALL_VIEWED -> 130L;
            case UPGRADE_CLICKED -> 27L;
            case SIGNUP_COMPLETED -> 52L;
            case EMAIL_VERIFIED -> 31L;
            default -> 0L;
        });

        AdminDashboardSummaryResponse response = adminDashboardService.getSummary();

        assertThat(response.overview().totalUsers()).isEqualTo(120);
        assertThat(response.overview().premiumUsers()).isEqualTo(2);
        assertThat(response.overview().premiumWaitlistCount()).isEqualTo(41);
        assertThat(response.billing().monthlySubscriptions()).isEqualTo(1);
        assertThat(response.billing().yearlySubscriptions()).isEqualTo(1);
        assertThat(response.billing().cancelAtPeriodEndSubscriptions()).isEqualTo(1);
        assertThat(response.billing().estimatedMrr()).isEqualByComparingTo("249.00");
        assertThat(response.billing().estimatedArr()).isEqualByComparingTo("1999.00");
        assertThat(response.engagement().studyPacksGeneratedThisWeek()).isEqualTo(38);
        assertThat(response.engagement().verifiedAccounts()).isEqualTo(31);
    }

    @Test
    void getTopContent_normalizesBlankSubjects() {
        when(analyticsEventRepository.findTopPublicNotesByEventType(eq(AnalyticsEventType.PUBLIC_NOTE_VIEWED), any(Pageable.class)))
                .thenReturn(List.of(new AdminPublicNoteMetricItemResponse(UUID.randomUUID(), "Cell Structure", "Science", 12)));
        when(analyticsEventRepository.findTopPublicNotesByEventType(eq(AnalyticsEventType.PUBLIC_NOTE_COPIED), any(Pageable.class)))
                .thenReturn(List.of(new AdminPublicNoteMetricItemResponse(UUID.randomUUID(), "World War 1 Causes", "History", 4)));
        when(studyPackRepository.findTopSubjectsByStudyPackCount(any(Pageable.class)))
                .thenReturn(List.of(
                        new AdminSubjectMetricItemResponse("", 3),
                        new AdminSubjectMetricItemResponse(null, 2),
                        new AdminSubjectMetricItemResponse("Biology", 5)
                ));

        AdminDashboardTopContentResponse response = adminDashboardService.getTopContent();

        assertThat(response.mostViewedPublicNotes()).hasSize(1);
        assertThat(response.mostCopiedPublicNotes()).hasSize(1);
        assertThat(response.topSubjectsByStudyPackGeneration())
                .extracting(AdminSubjectMetricItemResponse::subject, AdminSubjectMetricItemResponse::studyPackCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("Biology", 5L),
                        org.assertj.core.groups.Tuple.tuple("Uncategorized", 5L)
                );
    }

    @Test
    void getRecentEvents_returnsUpgradesAndFailedPayments() {
        UserEntity user = buildUser("[email protected]");
        SubscriptionEntity subscription = buildSubscription(
                user,
                OffsetDateTime.parse("2026-03-01T00:00:00Z"),
                OffsetDateTime.parse("2026-04-01T00:00:00Z"),
                false
        );
        subscription.setId(UUID.randomUUID());

        AnalyticsEventEntity event = new AnalyticsEventEntity();
        event.setId(UUID.randomUUID());
        event.setEntityId(subscription.getId());
        event.setCreatedAt(OffsetDateTime.parse("2026-03-20T00:00:00Z"));

        PaymentTransactionEntity failedPayment = buildTransaction(
                user,
                new BigDecimal("249.00"),
                "PHP",
                PaymentTransactionStatus.FAILED
        );
        failedPayment.setCreatedAt(OffsetDateTime.parse("2026-03-21T00:00:00Z"));

        when(analyticsEventRepository.findByEventTypeOrderByCreatedAtDesc(eq(AnalyticsEventType.SUBSCRIPTION_STARTED), any(Pageable.class)))
                .thenReturn(List.of(event));
        when(subscriptionRepository.findAllById(any())).thenReturn(List.of(subscription));
        when(paymentTransactionRepository.findByStatusOrderByCreatedAtDesc(eq(PaymentTransactionStatus.FAILED), any(Pageable.class)))
                .thenReturn(List.of(failedPayment));

        AdminDashboardRecentEventsResponse response = adminDashboardService.getRecentEvents();

        assertThat(response.recentPremiumUpgrades()).hasSize(1);
        assertThat(response.recentPremiumUpgrades().getFirst().userEmail()).isEqualTo("[email protected]");
        assertThat(response.recentFailedPayments()).hasSize(1);
        assertThat(response.recentFailedPayments().getFirst().currency()).isEqualTo("PHP");
    }

    private UserEntity buildUser(String email) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        return user;
    }

    private SubscriptionEntity buildSubscription(
            UserEntity user,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            boolean cancelAtPeriodEnd
    ) {
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(user);
        subscription.setPlanType(PlanType.PREMIUM);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setBillingType(BillingType.SUBSCRIPTION);
        subscription.setProvider(BillingProvider.PAYMONGO);
        subscription.setStartAt(startAt);
        subscription.setEndAt(endAt);
        subscription.setCancelAtPeriodEnd(cancelAtPeriodEnd);
        return subscription;
    }

    private PaymentTransactionEntity buildTransaction(
            UserEntity user,
            BigDecimal amount,
            String currency,
            PaymentTransactionStatus status
    ) {
        PaymentTransactionEntity transaction = new PaymentTransactionEntity();
        transaction.setId(UUID.randomUUID());
        transaction.setUser(user);
        transaction.setProvider(BillingProvider.PAYMONGO);
        transaction.setBillingType(BillingType.SUBSCRIPTION);
        transaction.setPlanType(PlanType.PREMIUM);
        transaction.setAmount(amount);
        transaction.setCurrency(currency);
        transaction.setStatus(status);
        transaction.setCreatedAt(OffsetDateTime.now());
        return transaction;
    }
}
