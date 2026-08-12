package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.AdminFunnelMetricsResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.entity.UserUsageEntity;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.OnboardingCompletionProjection;
import com.studysnap.backend.repository.OnboardingStepUserCountProjection;
import com.studysnap.backend.repository.OfficialStudyPlanDemandProjection;
import com.studysnap.backend.repository.OfficialStudyPlanWishlistRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.SubscriptionRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.repository.UserUsageRepository;
import com.studysnap.backend.repository.WeeklyRetentionCohortProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminFunnelServiceTest {
    private static final String ONBOARDING_STEP_PROFILE = "profile";
    private static final String ONBOARDING_STEP_COURSE_PROGRAM = "course-program";
    private static final String ONBOARDING_STEP_LEARNING_CONTEXT = "learning-context";
    private static final String ONBOARDING_STEP_LEGACY = "legacy";
    private static final String NURSING = "Nursing";
    private static final String ACCOUNTANCY = "Accountancy";

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
    @Mock
    private OfficialStudyPlanWishlistRepository officialStudyPlanWishlistRepository;

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
                officialStudyPlanWishlistRepository,
                properties
        );
    }

    @Test
    void getMetrics_returnsZeroRates_whenNoData() {
        stubBaseMetrics(0, 0, null, 0, 0, 0, 0);
        when(subscriptionRepository.findActiveUserIdsByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of());

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.windowDays()).isNull();
        assertThat(response.windowStartedAt()).isNull();
        assertThat(response.onboarding().totalSignups()).isZero();
        assertThat(response.onboarding().onboardingCompletedUsers()).isZero();
        assertThat(response.onboarding().completionRatePercent()).isEqualTo(0.0);
        assertThat(response.onboarding().steps()).hasSize(9).allSatisfy(step -> {
            assertThat(step.userCount()).isZero();
        });
        assertThat(response.onboarding().legacyStep().userCount()).isZero();
        assertThat(response.activation().totalVerifiedUsers()).isZero();
        assertThat(response.activation().activatedUsers()).isZero();
        assertThat(response.activation().activationRatePercent()).isEqualTo(0.0);
        assertThat(response.activation().medianDaysToFirstPack()).isNull();
        assertThat(response.stuckUsers().stuckUsersCount()).isZero();
        assertThat(response.quotaHit().ratePercent()).isEqualTo(0.0);
        assertThat(response.quotaHit().quotaTypes()).hasSize(6);
        assertThat(response.paywallConversion().ratePercent()).isEqualTo(0.0);
        assertThat(response.valueLoop().ratePercent()).isEqualTo(0.0);
        assertThat(response.retentionCohort().ratePercent()).isEqualTo(0.0);
        assertThat(response.retentionCohort().wideRetention().eligibleActivatedUsers()).isZero();
        assertThat(response.retentionCohort().wideRetention().afterDay7RatePercent()).isEqualTo(0.0);
        assertThat(response.retentionCohort().wideRetention().days2To30RatePercent()).isEqualTo(0.0);
        assertThat(response.retentionCohort().wideRetention().afterDay1RatePercent()).isEqualTo(0.0);
        assertThat(response.retentionCohort().weeklyCohorts()).isEmpty();
        assertThat(response.checkoutConversion().clickToCheckoutRatePercent()).isEqualTo(0.0);
        assertThat(response.checkoutConversion().checkoutToPaidRatePercent()).isEqualTo(0.0);
        assertThat(response.checkoutConversion().clickToPaidRatePercent()).isEqualTo(0.0);
    }

    @Test
    void getMetrics_onboardingOrdersCurrentStepsFillsMissingAndBucketsLegacyRows() {
        stubBaseMetrics(0, 0, null, 0, 0, 0, 0);
        when(subscriptionRepository.findActiveUserIdsByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of());
        when(analyticsEventRepository.findOnboardingCompletion()).thenReturn(onboardingCompletion(375, 234));
        when(analyticsEventRepository.findOnboardingStepUserCounts()).thenReturn(List.of(
                onboardingStep("completion", 11),
                onboardingStep(ONBOARDING_STEP_LEARNING_CONTEXT, 7),
                onboardingStep(ONBOARDING_STEP_PROFILE, 20),
                onboardingStep("future-renamed-step", 3),
                onboardingStep(ONBOARDING_STEP_COURSE_PROGRAM, 15)
        ));

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.onboarding().totalSignups()).isEqualTo(375);
        assertThat(response.onboarding().onboardingCompletedUsers()).isEqualTo(234);
        assertThat(response.onboarding().completionRatePercent()).isEqualTo(62.4);
        assertThat(response.onboarding().steps())
                .extracting(AdminFunnelMetricsResponse.OnboardingStepMetrics::stepName)
                .containsExactly(
                        ONBOARDING_STEP_PROFILE,
                        ONBOARDING_STEP_COURSE_PROGRAM,
                        "learner-level",
                        "first-intent",
                        "input-method",
                        "note",
                        "generating",
                        "completion",
                        "confirm-practice"
                );
        assertThat(response.onboarding().steps().get(0).userCount()).isEqualTo(20);
        assertThat(response.onboarding().steps().get(1).userCount()).isEqualTo(15);
        assertThat(response.onboarding().steps().get(1).dropOffFromPrevious()).isEqualTo(5);
        assertThat(response.onboarding().steps().get(2).userCount()).isZero();
        assertThat(response.onboarding().steps().get(7).userCount()).isEqualTo(11);
        assertThat(response.onboarding().legacyStep().stepName()).isEqualTo(ONBOARDING_STEP_LEGACY);
        assertThat(response.onboarding().legacyStep().userCount()).isEqualTo(10);
    }

    @Test
    void getMetrics_onboardingIncludesRequestedProgramsInRepositoryOrder() {
        stubBaseMetrics(0, 0, null, 0, 0, 0, 0);
        when(subscriptionRepository.findActiveUserIdsByPlanTypeInAndStatus(
                eq(List.of(PlanType.PLUS, PlanType.PRO)),
                eq(SubscriptionStatus.ACTIVE),
                any()
        )).thenReturn(List.of());
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of());
        when(officialStudyPlanWishlistRepository.findProgramDemand()).thenReturn(List.of(
                officialPlanDemand(NURSING, 8, 8),
                officialPlanDemand(ACCOUNTANCY, 3, 3)
        ));

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.onboarding().requestedPrograms())
                .extracting(AdminFunnelMetricsResponse.RequestedProgramMetrics::courseProgram)
                .containsExactly(NURSING, ACCOUNTANCY);
        assertThat(response.onboarding().requestedPrograms().getFirst().requestCount()).isEqualTo(8);
        assertThat(response.onboarding().requestedPrograms().getFirst().distinctLearners()).isEqualTo(8);
    }

    @Test
    void onboardingMetrics_treatsAMissingStepNameAsLegacyRatherThanFailing() {
        // metadata_json defaults to '{}', so jsonb_extract_path_text can legitimately return NULL for a
        // STEP_VIEWED row. The step-name set is an immutable Set, whose contains(null) throws NPE -- which
        // would 500 the WHOLE admin funnel endpoint, not just this section, over one missing metadata key.
        stubBaseMetrics(0, 0, null, 0, 0, 0, 0);
        when(subscriptionRepository.findActiveUserIdsByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of());
        when(analyticsEventRepository.findOnboardingCompletion()).thenReturn(onboardingCompletion(30, 12));
        when(analyticsEventRepository.findOnboardingStepUserCounts())
                .thenReturn(List.of(onboardingStep(null, 4)));

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.onboarding().legacyStep().userCount()).isEqualTo(4);
        assertThat(response.onboarding().steps()).allSatisfy(step -> assertThat(step.userCount()).isZero());
    }

    @Test
    void getMetrics_onboardingKnownStepNeverEntersLegacyBucket() {
        stubBaseMetrics(0, 0, null, 0, 0, 0, 0);
        when(subscriptionRepository.findActiveUserIdsByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of());
        when(analyticsEventRepository.findOnboardingStepUserCounts()).thenReturn(List.of(
                onboardingStep(ONBOARDING_STEP_PROFILE, 12)
        ));

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.onboarding().steps().getFirst().userCount()).isEqualTo(12);
        assertThat(response.onboarding().legacyStep().userCount()).isZero();
    }

    @Test
    void getMetrics_onboardingOnlyLegacyRowsKeepsEveryCurrentStepAtZero() {
        stubBaseMetrics(0, 0, null, 0, 0, 0, 0);
        when(subscriptionRepository.findActiveUserIdsByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of());
        when(analyticsEventRepository.findOnboardingStepUserCounts()).thenReturn(List.of(
                onboardingStep(ONBOARDING_STEP_LEARNING_CONTEXT, 9),
                onboardingStep("input", 8),
                onboardingStep("study-pack", 6)
        ));

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.onboarding().steps()).hasSize(9).allSatisfy(step -> {
            assertThat(step.userCount()).isZero();
        });
        assertThat(response.onboarding().legacyStep().userCount()).isEqualTo(23);
    }

    @Test
    void getMetrics_activationRate_computedCorrectly() {
        stubBaseMetrics(10, 4, 2.5, 0, 0, 0, 0);
        when(subscriptionRepository.findActiveUserIdsByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
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
        when(subscriptionRepository.findActiveUserIdsByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of());

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.stuckUsers().stuckUsersCount()).isEqualTo(1);
    }

    @Test
    void getMetrics_stuckUsers_excludesRecentNotes() {
        stubBaseMetrics(1, 0, null, 0, 0, 0, 0);
        when(subscriptionRepository.findActiveUserIdsByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
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
        when(subscriptionRepository.findActiveUserIdsByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of(paidUserId));
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of(
                        buildUsage(freeUserId, 10),
                        buildUsage(paidUserId, 10)
                ));

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.quotaHit().totalFreeUsers()).isEqualTo(1);
        assertThat(response.quotaHit().freeUsersHitQuota()).isEqualTo(1);
        assertThat(response.quotaHit().ratePercent()).isEqualTo(100.0);
        assertThat(quotaType(response, "study_pack").usersHitQuota()).isEqualTo(1);
        assertThat(quotaType(response, "study_pack").applicableFreeUsers()).isEqualTo(1);
        assertThat(quotaType(response, "long_exam").applicable()).isFalse();
        assertThat(quotaType(response, "long_exam").applicableFreeUsers()).isZero();
    }

    @Test
    void getMetrics_quotaHitBreakdown_countsQuizHitAndAnyQuotaHit() {
        UUID freeUserId = UUID.randomUUID();
        stubBaseMetrics(0, 0, null, 0, 0, 0, 0);
        when(subscriptionRepository.findActiveUserIdsByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of());
        UserUsageEntity usage = buildUsage(freeUserId, 0);
        usage.setChallengeQuizGenerations(20);
        usage.setAdaptiveQuizGenerations(0);
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of(usage));

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.quotaHit().totalFreeUsers()).isEqualTo(1);
        assertThat(response.quotaHit().freeUsersHitQuota()).isEqualTo(1);
        assertThat(response.quotaHit().ratePercent()).isEqualTo(100.0);
        assertThat(quotaType(response, "study_pack").usersHitQuota()).isZero();
        assertThat(quotaType(response, "quiz").usersHitQuota()).isEqualTo(1);
        assertThat(quotaType(response, "quiz").ratePercent()).isEqualTo(100.0);
        assertThat(quotaType(response, "interview").applicable()).isFalse();
        assertThat(quotaType(response, "interview").applicableFreeUsers()).isZero();
    }

    @Test
    void getMetrics_paywallConversion_onlyCountsUsersWhoUpgradedAfterPaywall() {
        stubBaseMetrics(0, 0, null, 0, 3, 2, 0);
        when(subscriptionRepository.findActiveUserIdsByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of());

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.paywallConversion().usersSeenPaywall()).isEqualTo(3);
        assertThat(response.paywallConversion().usersUpgradedAfterPaywall()).isEqualTo(2);
        assertThat(response.paywallConversion().ratePercent()).isEqualTo(66.7);
    }

    @Test
    void getMetrics_retentionCohort_computesHeadlineAndWeeklyRates() {
        stubBaseMetrics(0, 0, null, 0, 0, 0, 0);
        when(subscriptionRepository.findActiveUserIdsByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of());
        when(analyticsEventRepository.countEligibleActivatedUsersForWeek2Retention(any())).thenReturn(12L);
        when(analyticsEventRepository.countReturnedWeek2Users(any())).thenReturn(5L);
        when(analyticsEventRepository.countEligibleActivatedUsersForWideRetention(any())).thenReturn(10L);
        when(analyticsEventRepository.countReturnedAfterDay7Users(any())).thenReturn(4L);
        when(analyticsEventRepository.countReturnedDays2To30Users(any())).thenReturn(6L);
        when(analyticsEventRepository.countReturnedAfterDay1Users(any())).thenReturn(7L);
        when(analyticsEventRepository.findWeeklyRetentionCohorts(any())).thenReturn(List.of(
                weeklyCohort(LocalDate.parse("2026-05-04"), 6, 3, 4),
                weeklyCohort(LocalDate.parse("2026-04-27"), 6, 2, 3)
        ));

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.retentionCohort().eligibleActivatedUsers()).isEqualTo(12);
        assertThat(response.retentionCohort().returnedWeek2Users()).isEqualTo(5);
        assertThat(response.retentionCohort().ratePercent()).isEqualTo(41.7);
        assertThat(response.retentionCohort().wideRetention().eligibleActivatedUsers()).isEqualTo(10);
        assertThat(response.retentionCohort().wideRetention().returnedAfterDay7Users()).isEqualTo(4);
        assertThat(response.retentionCohort().wideRetention().afterDay7RatePercent()).isEqualTo(40.0);
        assertThat(response.retentionCohort().wideRetention().returnedDays2To30Users()).isEqualTo(6);
        assertThat(response.retentionCohort().wideRetention().days2To30RatePercent()).isEqualTo(60.0);
        assertThat(response.retentionCohort().wideRetention().returnedAfterDay1Users()).isEqualTo(7);
        assertThat(response.retentionCohort().wideRetention().afterDay1RatePercent()).isEqualTo(70.0);
        assertThat(response.retentionCohort().weeklyCohorts()).hasSize(2);
        assertThat(response.retentionCohort().weeklyCohorts().getFirst().weekStart()).isEqualTo("2026-05-04");
        assertThat(response.retentionCohort().weeklyCohorts().getFirst().ratePercent()).isEqualTo(50.0);
        assertThat(response.retentionCohort().weeklyCohorts().getFirst().returnedAfterDay7Count()).isEqualTo(4);
        assertThat(response.retentionCohort().weeklyCohorts().getFirst().afterDay7RatePercent()).isEqualTo(66.7);
    }

    @Test
    void getMetrics_checkoutConversion_computesStepwiseRates() {
        stubBaseMetrics(0, 0, null, 0, 0, 0, 0);
        when(subscriptionRepository.findActiveUserIdsByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of());
        when(analyticsEventRepository.countDistinctUsersByEventType(AnalyticsEventType.UPGRADE_CLICKED)).thenReturn(10L);
        when(analyticsEventRepository.countDistinctUsersWithEventAfterEvent(
                AnalyticsEventType.UPGRADE_CLICKED,
                AnalyticsEventType.CHECKOUT_INITIATED
        )).thenReturn(4L);
        when(analyticsEventRepository.countDistinctUsersWithEventAfterEvent(
                AnalyticsEventType.CHECKOUT_INITIATED,
                AnalyticsEventType.SUBSCRIPTION_STARTED
        )).thenReturn(1L);

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics();

        assertThat(response.checkoutConversion().usersClickedUpgrade()).isEqualTo(10);
        assertThat(response.checkoutConversion().usersInitiatedCheckout()).isEqualTo(4);
        assertThat(response.checkoutConversion().usersSubscribed()).isEqualTo(1);
        assertThat(response.checkoutConversion().clickToCheckoutRatePercent()).isEqualTo(40.0);
        assertThat(response.checkoutConversion().checkoutToPaidRatePercent()).isEqualTo(25.0);
        assertThat(response.checkoutConversion().clickToPaidRatePercent()).isEqualTo(10.0);
    }

    @Test
    void getMetrics_withWindowDays_usesWindowedEventQueriesOnlyForEventStages() {
        stubCumulativeMetrics(20, 8, 2.5, 1);
        when(subscriptionRepository.findActiveUserIdsByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of());
        when(analyticsEventRepository.countDistinctUsersByEventTypeSince(eq(AnalyticsEventType.PAYWALL_VIEWED), any()))
                .thenReturn(5L);
        when(analyticsEventRepository.countUsersUpgradedAfterPaywallSince(
                eq(AnalyticsEventType.PAYWALL_VIEWED),
                eq(AnalyticsEventType.SUBSCRIPTION_STARTED),
                any()
        )).thenReturn(2L);
        when(analyticsEventRepository.countDistinctUsersByEventTypeSince(eq(AnalyticsEventType.STUDY_PACK_GENERATED), any()))
                .thenReturn(9L);
        when(analyticsEventRepository.countUsersStartedQuizWithin7DaysOfFirstGeneratedPackSince(any()))
                .thenReturn(3L);
        when(analyticsEventRepository.countDistinctUsersByEventTypeSince(eq(AnalyticsEventType.UPGRADE_CLICKED), any()))
                .thenReturn(4L);
        when(analyticsEventRepository.countDistinctUsersWithEventAfterEventSince(
                eq(AnalyticsEventType.UPGRADE_CLICKED),
                eq(AnalyticsEventType.CHECKOUT_INITIATED),
                any()
        )).thenReturn(1L);
        when(analyticsEventRepository.countDistinctUsersWithEventAfterEventSince(
                eq(AnalyticsEventType.CHECKOUT_INITIATED),
                eq(AnalyticsEventType.SUBSCRIPTION_STARTED),
                any()
        )).thenReturn(0L);

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics(30);

        assertThat(response.windowDays()).isEqualTo(30);
        assertThat(response.windowStartedAt()).isNotNull();
        assertThat(response.paywallConversion().usersSeenPaywall()).isEqualTo(5);
        assertThat(response.paywallConversion().usersUpgradedAfterPaywall()).isEqualTo(2);
        assertThat(response.valueLoop().usersGeneratedPack()).isEqualTo(9);
        assertThat(response.valueLoop().usersStartedQuizWithin7Days()).isEqualTo(3);
        assertThat(response.checkoutConversion().usersClickedUpgrade()).isEqualTo(4);
        assertThat(response.checkoutConversion().usersInitiatedCheckout()).isEqualTo(1);
        assertThat(response.checkoutConversion().usersSubscribed()).isZero();
        verify(analyticsEventRepository, never()).countDistinctUsersByEventType(any());
        verify(analyticsEventRepository, never()).countUsersUpgradedAfterPaywall(any(), any());
        verify(analyticsEventRepository, never()).countUsersStartedQuizWithin7DaysOfFirstGeneratedPack();
        verify(analyticsEventRepository, never()).countDistinctUsersWithEventAfterEvent(any(), any());
    }

    @Test
    void getMetrics_nonPositiveWindowDays_usesAllTimeEventQueries() {
        stubBaseMetrics(0, 0, null, 0, 3, 1, 2);
        when(subscriptionRepository.findActiveUserIdsByPlanTypeInAndStatus(eq(List.of(PlanType.PLUS, PlanType.PRO)), eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of());
        when(userUsageRepository.findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(any(), any()))
                .thenReturn(List.of());

        AdminFunnelMetricsResponse response = adminFunnelService.getMetrics(0);

        assertThat(response.windowDays()).isNull();
        assertThat(response.windowStartedAt()).isNull();
        assertThat(response.paywallConversion().usersSeenPaywall()).isEqualTo(3);
        assertThat(response.valueLoop().usersStartedQuizWithin7Days()).isEqualTo(2);
        verify(analyticsEventRepository, never()).countDistinctUsersByEventTypeSince(any(), any());
        verify(analyticsEventRepository, never()).countUsersUpgradedAfterPaywallSince(any(), any(), any());
        verify(analyticsEventRepository, never()).countUsersStartedQuizWithin7DaysOfFirstGeneratedPackSince(any());
        verify(analyticsEventRepository, never()).countDistinctUsersWithEventAfterEventSince(any(), any(), any());
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
        stubCumulativeMetrics(verifiedUsers, activatedUsers, medianDaysToFirstPack, stuckUsers);
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
        when(analyticsEventRepository.countDistinctUsersWithEventAfterEvent(any(), any())).thenReturn(0L);
    }

    private void stubCumulativeMetrics(
            long verifiedUsers,
            long activatedUsers,
            Double medianDaysToFirstPack,
            long stuckUsers
    ) {
        when(userRepository.countByEmailVerifiedAtIsNotNull()).thenReturn(verifiedUsers);
        when(studyPackRepository.countDistinctOwnerUserIds()).thenReturn(activatedUsers);
        when(studyPackRepository.findMedianDaysFromVerifiedSignupToFirstPack()).thenReturn(medianDaysToFirstPack);
        when(noteRepository.countVerifiedUsersWithNotesBeforeAndNoStudyPacks(any())).thenReturn(stuckUsers);
        when(analyticsEventRepository.findOnboardingCompletion()).thenReturn(onboardingCompletion(0, 0));
        when(analyticsEventRepository.findOnboardingStepUserCounts()).thenReturn(List.of());
        when(analyticsEventRepository.countEligibleActivatedUsersForWeek2Retention(any())).thenReturn(0L);
        when(analyticsEventRepository.countReturnedWeek2Users(any())).thenReturn(0L);
        when(analyticsEventRepository.countEligibleActivatedUsersForWideRetention(any())).thenReturn(0L);
        when(analyticsEventRepository.countReturnedAfterDay7Users(any())).thenReturn(0L);
        when(analyticsEventRepository.countReturnedDays2To30Users(any())).thenReturn(0L);
        when(analyticsEventRepository.countReturnedAfterDay1Users(any())).thenReturn(0L);
        when(analyticsEventRepository.findWeeklyRetentionCohorts(any())).thenReturn(List.of());
    }

    private OnboardingCompletionProjection onboardingCompletion(long totalSignups, long completedUsers) {
        return new OnboardingCompletionProjection() {
            @Override
            public long getTotalSignups() {
                return totalSignups;
            }

            @Override
            public long getOnboardingCompletedUsers() {
                return completedUsers;
            }
        };
    }

    private OnboardingStepUserCountProjection onboardingStep(String stepName, long userCount) {
        return new OnboardingStepUserCountProjection() {
            @Override
            public String getStepName() {
                return stepName;
            }

            @Override
            public long getUserCount() {
                return userCount;
            }
        };
    }

    private OfficialStudyPlanDemandProjection officialPlanDemand(
            String courseProgram,
            long requestCount,
            long distinctLearners
    ) {
        return new OfficialStudyPlanDemandProjection() {
            @Override
            public String getCourseProgram() {
                return courseProgram;
            }

            @Override
            public long getRequestCount() {
                return requestCount;
            }

            @Override
            public long getDistinctLearners() {
                return distinctLearners;
            }
        };
    }

    private WeeklyRetentionCohortProjection weeklyCohort(
            LocalDate weekStart,
            long cohortSize,
            long returnedCount,
            long returnedAfterDay7Count
    ) {
        return new WeeklyRetentionCohortProjection() {
            @Override
            public LocalDate getWeekStart() {
                return weekStart;
            }

            @Override
            public long getCohortSize() {
                return cohortSize;
            }

            @Override
            public long getReturnedCount() {
                return returnedCount;
            }

            @Override
            public long getReturnedAfterDay7Count() {
                return returnedAfterDay7Count;
            }
        };
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

    private AdminFunnelMetricsResponse.QuotaTypeHitMetrics quotaType(
            AdminFunnelMetricsResponse response,
            String quotaType
    ) {
        return response.quotaHit().quotaTypes()
                .stream()
                .filter(metrics -> metrics.quotaType().equals(quotaType))
                .findFirst()
                .orElseThrow();
    }

}
