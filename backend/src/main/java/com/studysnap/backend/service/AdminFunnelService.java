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
import com.studysnap.backend.repository.OfficialStudyPlanWishlistRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.SubscriptionRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.repository.UserUsageRepository;
import com.studysnap.backend.repository.WeeklyRetentionCohortProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminFunnelService {
    private static final int STUCK_GENERATION_DAYS = 7;
    private static final double PERCENT_MULTIPLIER = 100.0;
    private static final double ONE_DECIMAL_PLACE = 10.0;
    private static final String QUOTA_TYPE_STUDY_PACK = "study_pack";
    private static final String QUOTA_TYPE_CHALLENGE_QUIZ = "quiz";
    private static final String QUOTA_TYPE_ADAPTIVE_PRACTICE = "adaptive";
    private static final String QUOTA_TYPE_LONG_EXAM = "long_exam";
    private static final String QUOTA_TYPE_BOARD_EXAM = "board_exam";
    private static final String QUOTA_TYPE_INTERVIEW_PRACTICE = "interview";
    private static final String QUOTA_LABEL_STUDY_PACK = "Study Packs";
    private static final String QUOTA_LABEL_CHALLENGE_QUIZ = "Challenge Quiz";
    private static final String QUOTA_LABEL_ADAPTIVE_PRACTICE = "Adaptive Practice";
    private static final String QUOTA_LABEL_LONG_EXAM = "Long Exam";
    private static final String QUOTA_LABEL_BOARD_EXAM = "Board Exam";
    private static final String QUOTA_LABEL_INTERVIEW_PRACTICE = "Interview Practice";
    private static final String LEGACY_ONBOARDING_STEP_NAME = "legacy";
    private static final String LEGACY_ONBOARDING_STEP_LABEL = "Legacy / other step names";
    private static final List<OnboardingStepDefinition> ONBOARDING_STEPS = List.of(
            new OnboardingStepDefinition("profile", "Profile"),
            new OnboardingStepDefinition("course-program", "Course / Program"),
            new OnboardingStepDefinition("learner-level", "Learner Level"),
            new OnboardingStepDefinition("first-intent", "First Intent"),
            new OnboardingStepDefinition("input-method", "Input Method"),
            new OnboardingStepDefinition("note", "Note"),
            new OnboardingStepDefinition("generating", "Generating"),
            new OnboardingStepDefinition("completion", "Completion")
    );
    /**
     * Screens that are BRANCHES of Screen 5, not steps after it. They must not join the ordered walk:
     * a learner who adopts a Review Set exits from Screen 5 and never reaches `completion`, so ordering
     * `confirm-practice` after `completion` produced a drop-off of completion-minus-confirm-practice --
     * arithmetic with no meaning, and negative as soon as adoption is real. There is no ordering that
     * makes these a funnel with the others; they are siblings of `input-method`, not successors.
     */
    private static final List<OnboardingStepDefinition> ONBOARDING_BRANCH_STEPS = List.of(
            new OnboardingStepDefinition("confirm-practice", "Confirm & Practice (ready-made branch)"),
            new OnboardingStepDefinition("resolving-plan", "Checking for a plan (ready-made branch)"),
            new OnboardingStepDefinition("plan-unavailable", "No plan yet (ready-made branch)")
    );
    private static final Set<String> ONBOARDING_STEP_NAMES = Stream.concat(
                    ONBOARDING_STEPS.stream(), ONBOARDING_BRANCH_STEPS.stream())
            .map(OnboardingStepDefinition::stepName)
            .collect(Collectors.toUnmodifiableSet());

    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final UserUsageRepository userUsageRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final OfficialStudyPlanWishlistRepository officialStudyPlanWishlistRepository;
    private final StudySnapProperties properties;

    public AdminFunnelMetricsResponse getMetrics() {
        return getMetrics(null);
    }

    public AdminFunnelMetricsResponse getMetrics(Integer windowDays) {
        OffsetDateTime now = OffsetDateTime.now();
        Integer normalizedWindowDays = normalizeWindowDays(windowDays);
        OffsetDateTime since = normalizedWindowDays == null ? null : now.minusDays(normalizedWindowDays);

        AdminFunnelMetricsResponse.OnboardingMetrics onboarding = getOnboardingMetrics();
        AdminFunnelMetricsResponse.ActivationMetrics activation = getActivationMetrics();
        AdminFunnelMetricsResponse.StuckUsersMetrics stuckUsers = getStuckUsersMetrics(now);
        AdminFunnelMetricsResponse.QuotaHitMetrics quotaHit = getQuotaHitMetrics(now);
        AdminFunnelMetricsResponse.PaywallConversionMetrics paywallConversion = getPaywallConversionMetrics(since);
        AdminFunnelMetricsResponse.ValueLoopMetrics valueLoop = getValueLoopMetrics(since);
        AdminFunnelMetricsResponse.RetentionCohortMetrics retentionCohort = getRetentionCohortMetrics(now);
        AdminFunnelMetricsResponse.CheckoutConversionMetrics checkoutConversion = getCheckoutConversionMetrics(since);

        return new AdminFunnelMetricsResponse(
                normalizedWindowDays,
                since,
                onboarding,
                activation,
                stuckUsers,
                quotaHit,
                paywallConversion,
                valueLoop,
                retentionCohort,
                checkoutConversion
        );
    }

    private AdminFunnelMetricsResponse.OnboardingMetrics getOnboardingMetrics() {
        OnboardingCompletionProjection completion = analyticsEventRepository.findOnboardingCompletion();
        Map<String, Long> currentStepCounts = new HashMap<>();
        long legacyUserCount = 0;

        for (OnboardingStepUserCountProjection row : analyticsEventRepository.findOnboardingStepUserCounts()) {
            String stepName = row.getStepName();
            // A NULL step name is a real possibility, not a defensive nicety: metadata_json defaults to
            // '{}', so jsonb_extract_path_text returns NULL for any STEP_VIEWED row missing the key. The
            // step-name set is immutable, and contains(null) on those throws -- which would 500 the WHOLE
            // admin funnel endpoint over one absent metadata key. Unrecognised means legacy, including null.
            if (stepName != null && ONBOARDING_STEP_NAMES.contains(stepName)) {
                currentStepCounts.merge(stepName, row.getUserCount(), Long::sum);
            } else {
                legacyUserCount += row.getUserCount();
            }
        }

        List<AdminFunnelMetricsResponse.OnboardingStepMetrics> steps = new ArrayList<>();
        Long previousUserCount = null;
        for (OnboardingStepDefinition step : ONBOARDING_STEPS) {
            long userCount = currentStepCounts.getOrDefault(step.stepName(), 0L);
            Long dropOff = previousUserCount == null ? null : previousUserCount - userCount;
            steps.add(new AdminFunnelMetricsResponse.OnboardingStepMetrics(
                    step.stepName(),
                    step.label(),
                    userCount,
                    dropOff
            ));
            previousUserCount = userCount;
        }

        // Branch rows carry a null drop-off on purpose: there is no previous step to subtract from.
        List<AdminFunnelMetricsResponse.OnboardingStepMetrics> branchSteps = ONBOARDING_BRANCH_STEPS.stream()
                .map(step -> new AdminFunnelMetricsResponse.OnboardingStepMetrics(
                        step.stepName(),
                        step.label(),
                        currentStepCounts.getOrDefault(step.stepName(), 0L),
                        null
                ))
                .toList();

        return new AdminFunnelMetricsResponse.OnboardingMetrics(
                completion.getTotalSignups(),
                completion.getOnboardingCompletedUsers(),
                ratePercent(completion.getOnboardingCompletedUsers(), completion.getTotalSignups()),
                List.copyOf(steps),
                branchSteps,
                new AdminFunnelMetricsResponse.OnboardingStepMetrics(
                        LEGACY_ONBOARDING_STEP_NAME,
                        LEGACY_ONBOARDING_STEP_LABEL,
                        legacyUserCount,
                        null
                ),
                officialStudyPlanWishlistRepository.findProgramDemand().stream()
                        .map(row -> new AdminFunnelMetricsResponse.RequestedProgramMetrics(
                                row.getCourseProgram(),
                                row.getRequestCount()
                        ))
                        .toList()
        );
    }

    private AdminFunnelMetricsResponse.ActivationMetrics getActivationMetrics() {
        long totalVerifiedUsers = userRepository.countByEmailVerifiedAtIsNotNull();
        long activatedUsers = studyPackRepository.countDistinctOwnerUserIds();
        return new AdminFunnelMetricsResponse.ActivationMetrics(
                totalVerifiedUsers,
                activatedUsers,
                ratePercent(activatedUsers, totalVerifiedUsers),
                studyPackRepository.findMedianDaysFromVerifiedSignupToFirstPack()
        );
    }

    private AdminFunnelMetricsResponse.StuckUsersMetrics getStuckUsersMetrics(OffsetDateTime now) {
        long stuckUsersCount = noteRepository.countVerifiedUsersWithNotesBeforeAndNoStudyPacks(
                now.minusDays(STUCK_GENERATION_DAYS)
        );
        return new AdminFunnelMetricsResponse.StuckUsersMetrics(stuckUsersCount);
    }

    private AdminFunnelMetricsResponse.QuotaHitMetrics getQuotaHitMetrics(OffsetDateTime now) {
        Set<UUID> paidUserIds = new HashSet<>(subscriptionRepository.findActiveUserIdsByPlanTypeInAndStatus(
                List.of(PlanType.PLUS, PlanType.PRO),
                SubscriptionStatus.ACTIVE,
                now
        ));

        List<UserUsageEntity> currentPeriodUsage = userUsageRepository
                .findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(now, now);
        List<QuotaAccumulator> quotaAccumulators = List.of(
                new QuotaAccumulator(
                        QUOTA_TYPE_STUDY_PACK,
                        QUOTA_LABEL_STUDY_PACK,
                        properties.getPricing().resolveMonthlyStudyPackLimit(PlanType.FREE),
                        UserUsageEntity::getStudyPackGenerations
                ),
                new QuotaAccumulator(
                        QUOTA_TYPE_CHALLENGE_QUIZ,
                        QUOTA_LABEL_CHALLENGE_QUIZ,
                        properties.getPricing().resolveMonthlyChallengeQuizLimit(PlanType.FREE),
                        UserUsageEntity::getChallengeQuizGenerations
                ),
                new QuotaAccumulator(
                        QUOTA_TYPE_ADAPTIVE_PRACTICE,
                        QUOTA_LABEL_ADAPTIVE_PRACTICE,
                        properties.getPricing().resolveMonthlyAdaptivePracticeLimit(PlanType.FREE),
                        UserUsageEntity::getAdaptiveQuizGenerations
                ),
                new QuotaAccumulator(
                        QUOTA_TYPE_LONG_EXAM,
                        QUOTA_LABEL_LONG_EXAM,
                        properties.getPricing().resolveMonthlyLongExamLimit(PlanType.FREE),
                        UserUsageEntity::getLongExamUsedThisMonth
                ),
                new QuotaAccumulator(
                        QUOTA_TYPE_BOARD_EXAM,
                        QUOTA_LABEL_BOARD_EXAM,
                        properties.getPricing().resolveMonthlyBoardExamLimit(PlanType.FREE),
                        UserUsageEntity::getBoardExamUsedThisMonth
                ),
                new QuotaAccumulator(
                        QUOTA_TYPE_INTERVIEW_PRACTICE,
                        QUOTA_LABEL_INTERVIEW_PRACTICE,
                        properties.getPricing().resolveMonthlyInterviewPracticeLimit(PlanType.FREE),
                        UserUsageEntity::getInterviewPracticeUsedThisMonth
                )
        );

        long totalFreeUsers = 0;
        long freeUsersHitQuota = 0;
        for (UserUsageEntity usage : currentPeriodUsage) {
            if (usage.getUserId() == null || paidUserIds.contains(usage.getUserId())) {
                continue;
            }
            totalFreeUsers++;
            boolean hitAnyQuota = false;
            for (QuotaAccumulator quotaAccumulator : quotaAccumulators) {
                if (quotaAccumulator.record(usage)) {
                    hitAnyQuota = true;
                }
            }
            if (hitAnyQuota) {
                freeUsersHitQuota++;
            }
        }

        return new AdminFunnelMetricsResponse.QuotaHitMetrics(
                freeUsersHitQuota,
                totalFreeUsers,
                ratePercent(freeUsersHitQuota, totalFreeUsers),
                quotaAccumulators.stream()
                        .map(QuotaAccumulator::toMetrics)
                        .toList()
        );
    }

    private AdminFunnelMetricsResponse.PaywallConversionMetrics getPaywallConversionMetrics(OffsetDateTime since) {
        long usersSeenPaywall = countDistinctUsersByEventType(AnalyticsEventType.PAYWALL_VIEWED, since);
        long usersUpgradedAfterPaywall = since == null
                ? analyticsEventRepository.countUsersUpgradedAfterPaywall(
                        AnalyticsEventType.PAYWALL_VIEWED,
                        AnalyticsEventType.SUBSCRIPTION_STARTED
                )
                : analyticsEventRepository.countUsersUpgradedAfterPaywallSince(
                        AnalyticsEventType.PAYWALL_VIEWED,
                        AnalyticsEventType.SUBSCRIPTION_STARTED,
                        since
                );
        return new AdminFunnelMetricsResponse.PaywallConversionMetrics(
                usersSeenPaywall,
                usersUpgradedAfterPaywall,
                ratePercent(usersUpgradedAfterPaywall, usersSeenPaywall)
        );
    }

    private AdminFunnelMetricsResponse.ValueLoopMetrics getValueLoopMetrics(OffsetDateTime since) {
        long usersGeneratedPack = countDistinctUsersByEventType(AnalyticsEventType.STUDY_PACK_GENERATED, since);
        long usersStartedQuizWithin7Days = since == null
                ? analyticsEventRepository.countUsersStartedQuizWithin7DaysOfFirstGeneratedPack()
                : analyticsEventRepository.countUsersStartedQuizWithin7DaysOfFirstGeneratedPackSince(since);
        return new AdminFunnelMetricsResponse.ValueLoopMetrics(
                usersGeneratedPack,
                usersStartedQuizWithin7Days,
                ratePercent(usersStartedQuizWithin7Days, usersGeneratedPack)
        );
    }

    private AdminFunnelMetricsResponse.RetentionCohortMetrics getRetentionCohortMetrics(OffsetDateTime now) {
        long eligibleActivatedUsers = analyticsEventRepository.countEligibleActivatedUsersForWeek2Retention(now);
        long returnedWeek2Users = analyticsEventRepository.countReturnedWeek2Users(now);
        long wideEligibleActivatedUsers = analyticsEventRepository.countEligibleActivatedUsersForWideRetention(now);
        long returnedAfterDay7Users = analyticsEventRepository.countReturnedAfterDay7Users(now);
        long returnedDays2To30Users = analyticsEventRepository.countReturnedDays2To30Users(now);
        long returnedAfterDay1Users = analyticsEventRepository.countReturnedAfterDay1Users(now);
        List<AdminFunnelMetricsResponse.WeeklyRetentionCohortMetrics> weeklyCohorts = analyticsEventRepository
                .findWeeklyRetentionCohorts(now)
                .stream()
                .map(this::toWeeklyRetentionCohortMetrics)
                .toList();

        return new AdminFunnelMetricsResponse.RetentionCohortMetrics(
                eligibleActivatedUsers,
                returnedWeek2Users,
                ratePercent(returnedWeek2Users, eligibleActivatedUsers),
                new AdminFunnelMetricsResponse.WideRetentionMetrics(
                        wideEligibleActivatedUsers,
                        returnedAfterDay7Users,
                        ratePercent(returnedAfterDay7Users, wideEligibleActivatedUsers),
                        returnedDays2To30Users,
                        ratePercent(returnedDays2To30Users, wideEligibleActivatedUsers),
                        returnedAfterDay1Users,
                        ratePercent(returnedAfterDay1Users, wideEligibleActivatedUsers)
                ),
                weeklyCohorts
        );
    }

    private AdminFunnelMetricsResponse.WeeklyRetentionCohortMetrics toWeeklyRetentionCohortMetrics(
            WeeklyRetentionCohortProjection cohort
    ) {
        long cohortSize = cohort.getCohortSize();
        long returnedCount = cohort.getReturnedCount();
        long returnedAfterDay7Count = cohort.getReturnedAfterDay7Count();
        return new AdminFunnelMetricsResponse.WeeklyRetentionCohortMetrics(
                cohort.getWeekStart().toString(),
                cohortSize,
                returnedCount,
                ratePercent(returnedCount, cohortSize),
                returnedAfterDay7Count,
                ratePercent(returnedAfterDay7Count, cohortSize)
        );
    }

    private AdminFunnelMetricsResponse.CheckoutConversionMetrics getCheckoutConversionMetrics(OffsetDateTime since) {
        long usersClickedUpgrade = countDistinctUsersByEventType(AnalyticsEventType.UPGRADE_CLICKED, since);
        long usersInitiatedCheckout = countDistinctUsersWithEventAfterEvent(
                AnalyticsEventType.UPGRADE_CLICKED,
                AnalyticsEventType.CHECKOUT_INITIATED,
                since
        );
        long usersSubscribed = countDistinctUsersWithEventAfterEvent(
                AnalyticsEventType.CHECKOUT_INITIATED,
                AnalyticsEventType.SUBSCRIPTION_STARTED,
                since
        );

        return new AdminFunnelMetricsResponse.CheckoutConversionMetrics(
                usersClickedUpgrade,
                usersInitiatedCheckout,
                usersSubscribed,
                ratePercent(usersInitiatedCheckout, usersClickedUpgrade),
                ratePercent(usersSubscribed, usersInitiatedCheckout),
                ratePercent(usersSubscribed, usersClickedUpgrade)
        );
    }

    private long countDistinctUsersByEventType(AnalyticsEventType eventType, OffsetDateTime since) {
        if (since == null) {
            return analyticsEventRepository.countDistinctUsersByEventType(eventType);
        }
        return analyticsEventRepository.countDistinctUsersByEventTypeSince(eventType, since);
    }

    private long countDistinctUsersWithEventAfterEvent(
            AnalyticsEventType earlierEventType,
            AnalyticsEventType laterEventType,
            OffsetDateTime since
    ) {
        if (since == null) {
            return analyticsEventRepository.countDistinctUsersWithEventAfterEvent(earlierEventType, laterEventType);
        }
        return analyticsEventRepository.countDistinctUsersWithEventAfterEventSince(
                earlierEventType,
                laterEventType,
                since
        );
    }

    private Integer normalizeWindowDays(Integer windowDays) {
        if (windowDays == null || windowDays <= 0) {
            return null;
        }
        return windowDays;
    }

    private double ratePercent(long numerator, long denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        double rate = numerator * PERCENT_MULTIPLIER / denominator;
        return Math.round(rate * ONE_DECIMAL_PLACE) / ONE_DECIMAL_PLACE;
    }

    private final class QuotaAccumulator {
        private final String quotaType;
        private final String label;
        private final int monthlyLimit;
        private final Function<UserUsageEntity, Integer> usageReader;
        private long applicableFreeUsers;
        private long usersHitQuota;

        private QuotaAccumulator(
                String quotaType,
                String label,
                int monthlyLimit,
                Function<UserUsageEntity, Integer> usageReader
        ) {
            this.quotaType = quotaType;
            this.label = label;
            this.monthlyLimit = monthlyLimit;
            this.usageReader = usageReader;
        }

        private boolean record(UserUsageEntity usage) {
            if (monthlyLimit <= 0) {
                return false;
            }
            applicableFreeUsers++;
            Integer usedValue = usageReader.apply(usage);
            int used = usedValue == null ? 0 : usedValue;
            if (used >= monthlyLimit) {
                usersHitQuota++;
                return true;
            }
            return false;
        }

        private AdminFunnelMetricsResponse.QuotaTypeHitMetrics toMetrics() {
            return new AdminFunnelMetricsResponse.QuotaTypeHitMetrics(
                    quotaType,
                    label,
                    monthlyLimit,
                    usersHitQuota,
                    applicableFreeUsers,
                    ratePercent(usersHitQuota, applicableFreeUsers),
                    monthlyLimit > 0
            );
        }
    }

    private record OnboardingStepDefinition(String stepName, String label) {
    }
}
