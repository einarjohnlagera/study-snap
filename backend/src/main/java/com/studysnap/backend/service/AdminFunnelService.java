package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.AdminFunnelMetricsResponse;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.entity.UserUsageEntity;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.SubscriptionRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.repository.UserUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminFunnelService {
    private static final int STUCK_GENERATION_DAYS = 7;
    private static final double PERCENT_MULTIPLIER = 100.0;
    private static final double ONE_DECIMAL_PLACE = 10.0;

    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final AnalyticsEventRepository analyticsEventRepository;
    private final UserUsageRepository userUsageRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final StudySnapProperties properties;

    public AdminFunnelMetricsResponse getMetrics() {
        OffsetDateTime now = OffsetDateTime.now();

        AdminFunnelMetricsResponse.ActivationMetrics activation = getActivationMetrics();
        AdminFunnelMetricsResponse.StuckUsersMetrics stuckUsers = getStuckUsersMetrics(now);
        AdminFunnelMetricsResponse.QuotaHitMetrics quotaHit = getQuotaHitMetrics(now);
        AdminFunnelMetricsResponse.PaywallConversionMetrics paywallConversion = getPaywallConversionMetrics();
        AdminFunnelMetricsResponse.ValueLoopMetrics valueLoop = getValueLoopMetrics();

        return new AdminFunnelMetricsResponse(
                activation,
                stuckUsers,
                quotaHit,
                paywallConversion,
                valueLoop
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
        Set<UUID> paidUserIds = subscriptionRepository.findCurrentlyActiveByPlanTypeInAndStatus(
                        List.of(PlanType.PLUS, PlanType.PRO),
                        SubscriptionStatus.ACTIVE,
                        now
                ).stream()
                .map(SubscriptionEntity::getUser)
                .map(user -> user == null ? null : user.getId())
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        List<UserUsageEntity> currentPeriodUsage = userUsageRepository
                .findByPeriodStartLessThanEqualAndPeriodEndGreaterThanEqual(now, now);
        int freeMonthlyStudyPackLimit = properties.getPricing().getFreeMonthlyStudyPackLimit();

        long totalFreeUsers = 0;
        long freeUsersHitQuota = 0;
        for (UserUsageEntity usage : currentPeriodUsage) {
            if (usage.getUserId() == null || paidUserIds.contains(usage.getUserId())) {
                continue;
            }
            totalFreeUsers++;
            if (usage.getStudyPackGenerations() != null
                    && usage.getStudyPackGenerations() >= freeMonthlyStudyPackLimit) {
                freeUsersHitQuota++;
            }
        }

        return new AdminFunnelMetricsResponse.QuotaHitMetrics(
                freeUsersHitQuota,
                totalFreeUsers,
                ratePercent(freeUsersHitQuota, totalFreeUsers)
        );
    }

    private AdminFunnelMetricsResponse.PaywallConversionMetrics getPaywallConversionMetrics() {
        long usersSeenPaywall = analyticsEventRepository.countDistinctUsersByEventType(AnalyticsEventType.PAYWALL_VIEWED);
        long usersUpgradedAfterPaywall = analyticsEventRepository.countUsersUpgradedAfterPaywall(
                AnalyticsEventType.PAYWALL_VIEWED,
                AnalyticsEventType.SUBSCRIPTION_STARTED
        );
        return new AdminFunnelMetricsResponse.PaywallConversionMetrics(
                usersSeenPaywall,
                usersUpgradedAfterPaywall,
                ratePercent(usersUpgradedAfterPaywall, usersSeenPaywall)
        );
    }

    private AdminFunnelMetricsResponse.ValueLoopMetrics getValueLoopMetrics() {
        long usersGeneratedPack = analyticsEventRepository.countDistinctUsersByEventType(AnalyticsEventType.STUDY_PACK_GENERATED);
        long usersStartedQuizWithin7Days = analyticsEventRepository.countUsersStartedQuizWithin7DaysOfFirstGeneratedPack();
        return new AdminFunnelMetricsResponse.ValueLoopMetrics(
                usersGeneratedPack,
                usersStartedQuizWithin7Days,
                ratePercent(usersStartedQuizWithin7Days, usersGeneratedPack)
        );
    }

    private double ratePercent(long numerator, long denominator) {
        if (denominator == 0) {
            return 0.0;
        }
        double rate = numerator * PERCENT_MULTIPLIER / denominator;
        return Math.round(rate * ONE_DECIMAL_PLACE) / ONE_DECIMAL_PLACE;
    }
}
