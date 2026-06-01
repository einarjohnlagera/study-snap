package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.AdminFunnelMetricsResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.UserUsageEntity;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.SubscriptionRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.repository.UserUsageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFunnelServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private StudyPackRepository studyPackRepository;
    @Mock
    private AnalyticsEventRepository analyticsEventRepository;
    @Mock
    private UserUsageRepository userUsageRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;

    private StudySnapProperties properties;
    private AdminFunnelService adminFunnelService;

    @BeforeEach
    void setUp() {
        properties = new StudySnapProperties();
        properties.getPricing().setFreeMonthlyStudyPackLimit(10);
        adminFunnelService = new AdminFunnelService(
                userRepository,
                noteRepository,
                studyPackRepository,
                analyticsEventRepository,
                userUsageRepository,
                subscriptionRepository,
                properties
        );
    }

    @Test
    void getMetrics_returnsZeroRates_whenNoData() {
        stubBaseMetrics(0, 0, null, 0, 0, 0, 0);
        when(subscriptionRepository.findCurrentlyActiveByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of());

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.activation().totalVerifiedUsers()).isZero();
        assertThat(response.activation().activatedUsers()).isZero();
        assertThat(response.activation().activationRatePercent()).isEqualTo(0.0);
        assertThat(response.activation().medianDaysToFirstPack()).isNull();
        assertThat(response.stuckUsers().stuckUsersCount()).isZero();
        assertThat(response.quotaHit().ratePercent()).isEqualTo(0.0);
        assertThat(response.paywallConversion().ratePercent()).isEqualTo(0.0);
        assertThat(response.valueLoop().ratePercent()).isEqualTo(0.0);
    }

    @Test
    void getMetrics_activationRate_computedCorrectly() {
        stubBaseMetrics(10, 4, 2.5, 0, 0, 0, 0);
        when(subscriptionRepository.findCurrentlyActiveByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of());

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.activation().activationRatePercent()).isEqualTo(40.0);
        assertThat(response.activation().medianDaysToFirstPack()).isEqualTo(2.5);
    }

    @Test
    void getMetrics_stuckUsers_excludesUsersWithStudyPacks() {
        stubBaseMetrics(2, 1, null, 1, 0, 0, 0);
        when(subscriptionRepository.findCurrentlyActiveByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of());

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.stuckUsers().stuckUsersCount()).isEqualTo(1);
    }

    @Test
    void getMetrics_stuckUsers_excludesRecentNotes() {
        stubBaseMetrics(1, 0, null, 0, 0, 0, 0);
        when(subscriptionRepository.findCurrentlyActiveByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of());

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.stuckUsers().stuckUsersCount()).isZero();
    }

    @Test
    void getMetrics_quotaHitRate_excludesPaidUsers() {
        UUID freeUserId = UUID.randomUUID();
        UUID paidUserId = UUID.randomUUID();
        stubBaseMetrics(0, 0, null, 0, 0, 0, 0);
        when(subscriptionRepository.findCurrentlyActiveByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of(buildSubscription(paidUserId)));
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of(
                        buildUsage(freeUserId, 10),
                        buildUsage(paidUserId, 10)
                ));

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.quotaHit().totalFreeUsers()).isEqualTo(1);
        assertThat(response.quotaHit().freeUsersHitQuota()).isEqualTo(1);
        assertThat(response.quotaHit().ratePercent()).isEqualTo(100.0);
    }

    @Test
    void getMetrics_paywallConversion_onlyCountsUsersWhoUpgradedAfterPaywall() {
        stubBaseMetrics(0, 0, null, 0, 3, 2, 0);
        when(subscriptionRepository.findCurrentlyActiveByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of());

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.paywallConversion().usersSeenPaywall()).isEqualTo(3);
        assertThat(response.paywallConversion().usersUpgradedAfterPaywall()).isEqualTo(2);
        assertThat(response.paywallConversion().ratePercent()).isEqualTo(66.7);
    }

    private void stubBaseMetrics(
            long verifiedUsers,
            long activatedUsers,
            Double medianDaysToFirstPack,
            long stuckUsers,
            long usersSeenPaywall,
            long usersUpgradedAfterPaywall,
            long usersStartedQuizWithin7Days
    ) {
        when(userRepository.countByEmailVerifiedAtIsNotNull()).thenReturn(verifiedUsers);
        when(studyPackRepository.countDistinctOwnerUserIds()).thenReturn(activatedUsers);
        when(studyPackRepository.findMedianDaysFromVerifiedSignupToFirstPack()).thenReturn(medianDaysToFirstPack);
        when(noteRepository.countVerifiedUsersWithNotesBeforeAndNoStudyPacks(any())).thenReturn(stuckUsers);
        when(analyticsEventRepository.countDistinctUsersByEventType(any())).thenAnswer(invocation -> {
            AnalyticsEventType eventType = invocation.getArgument(0, AnalyticsEventType.class);
            return switch (eventType) {
                case PAYWALL_VIEWED -> usersSeenPaywall;
                case STUDY_PACK_GENERATED -> activatedUsers;
                default -> 0L;
            };
        });
        when(analyticsEventRepository.countUsersUpgradedAfterPaywall(
                AnalyticsEventType.PAYWALL_VIEWED,
                AnalyticsEventType.SUBSCRIPTION_STARTED
        )).thenReturn(usersUpgradedAfterPaywall);
        when(analyticsEventRepository.countUsersStartedQuizWithin7DaysOfFirstGeneratedPack())
                .thenReturn(usersStartedQuizWithin7Days);
    }

    private UserUsageEntity buildUsage(UUID userId, int studyPackGenerations) {
        UserUsageEntity usage = new UserUsageEntity();
        usage.setId(UUID.randomUUID());
        usage.setUserId(userId);
        usage.setStudyPackGenerations(studyPackGenerations);
        usage.setPeriodStart(OffsetDateTime.now().minusDays(1));
        usage.setPeriodEnd(OffsetDateTime.now().plusDays(1));
        return usage;
    }

    private SubscriptionEntity buildSubscription(UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);

        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        subscription.setUser(user);
        subscription.setPlanType(PlanType.PRO);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        return subscription;
    }
}
