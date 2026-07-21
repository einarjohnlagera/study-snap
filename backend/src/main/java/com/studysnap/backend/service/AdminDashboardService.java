package com.studysnap.backend.service;

import com.studysnap.backend.dto.AdminDashboardRecentEventsResponse;
import com.studysnap.backend.dto.AdminDashboardSummaryResponse;
import com.studysnap.backend.dto.AdminDashboardTopContentResponse;
import com.studysnap.backend.dto.AdminOrganicLandingsResponse;
import com.studysnap.backend.dto.AdminRecentFeedbackItemResponse;
import com.studysnap.backend.dto.AdminRecentFailedPaymentItemResponse;
import com.studysnap.backend.dto.AdminRecentUpgradeItemResponse;
import com.studysnap.backend.dto.AdminSubjectMetricItemResponse;
import com.studysnap.backend.entity.AnalyticsEventEntity;
import com.studysnap.backend.entity.AnalyticsEventType;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.FeedbackEntity;
import com.studysnap.backend.entity.NoteVisibility;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.PaymentTransactionStatus;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.repository.FeedbackImageRepository;
import com.studysnap.backend.repository.AnalyticsEventRepository;
import com.studysnap.backend.repository.ExamHubOrganicClickThroughProjection;
import com.studysnap.backend.repository.FeedbackRepository;
import com.studysnap.backend.repository.NoteRepository;
import com.studysnap.backend.repository.PaymentTransactionRepository;
import com.studysnap.backend.repository.OrganicLandingProjection;
import com.studysnap.backend.repository.PremiumWaitlistRepository;
import com.studysnap.backend.repository.StudyPackRepository;
import com.studysnap.backend.repository.SubscriptionRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AdminDashboardService {
    private static final int TOP_CONTENT_LIMIT = 5;
    private static final int RECENT_EVENT_LIMIT = 5;
    private static final int ORGANIC_LANDINGS_LOOKBACK_WEEKS = 8;
    private static final int ORGANIC_CLICK_THROUGH_RATIO_SCALE = 4;
    private static final long YEARLY_SUBSCRIPTION_DAYS_THRESHOLD = 300L;

    private final AnalyticsEventRepository analyticsEventRepository;
    private final UserRepository userRepository;
    private final NoteRepository noteRepository;
    private final StudyPackRepository studyPackRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PremiumWaitlistRepository premiumWaitlistRepository;
    private final FeedbackRepository feedbackRepository;
    private final FeedbackImageRepository feedbackImageRepository;

    public AdminDashboardSummaryResponse getSummary() {
        OffsetDateTime now = OffsetDateTime.now();
        List<SubscriptionEntity> activePremiumSubscriptions = subscriptionRepository.findCurrentlyActiveByPlanTypeInAndStatus(
                List.of(PlanType.PLUS, PlanType.PRO),
                SubscriptionStatus.ACTIVE,
                now
        );

        long monthlySubscriptions = activePremiumSubscriptions.stream()
                .filter(subscription -> subscription.getBillingType() == BillingType.SUBSCRIPTION)
                .filter(subscription -> inferBillingCycle(subscription) == BillingCycleLabel.MONTHLY)
                .count();
        long yearlySubscriptions = activePremiumSubscriptions.stream()
                .filter(subscription -> subscription.getBillingType() == BillingType.SUBSCRIPTION)
                .filter(subscription -> inferBillingCycle(subscription) == BillingCycleLabel.YEARLY)
                .count();
        long cancelAtPeriodEndSubscriptions = activePremiumSubscriptions.stream()
                .filter(SubscriptionEntity::isCancelAtPeriodEnd)
                .count();
        long premiumUsers = activePremiumSubscriptions.stream()
                .map(subscription -> subscription.getUser().getId())
                .distinct()
                .count();

        RevenueEstimate revenueEstimate = estimateRevenue(activePremiumSubscriptions);

        return new AdminDashboardSummaryResponse(
                new AdminDashboardSummaryResponse.Overview(
                        userRepository.count(),
                        userRepository.countByEmailVerifiedAtIsNotNull(),
                        premiumUsers,
                        premiumWaitlistRepository.count(),
                        noteRepository.count(),
                        studyPackRepository.count(),
                        noteRepository.countByVisibility(NoteVisibility.PUBLIC),
                        analyticsEventRepository.countByEventType(AnalyticsEventType.PUBLIC_NOTE_VIEWED),
                        analyticsEventRepository.countByEventType(AnalyticsEventType.PUBLIC_NOTE_COPIED),
                        analyticsEventRepository.countByEventType(AnalyticsEventType.SUBSCRIPTION_STARTED)
                ),
                new AdminDashboardSummaryResponse.Billing(
                        activePremiumSubscriptions.size(),
                        monthlySubscriptions,
                        yearlySubscriptions,
                        cancelAtPeriodEndSubscriptions,
                        paymentTransactionRepository.countByStatus(PaymentTransactionStatus.FAILED),
                        revenueEstimate.estimatedMrr(),
                        revenueEstimate.estimatedArr()
                ),
                new AdminDashboardSummaryResponse.Engagement(
                        studyPackRepository.countByCreatedAtGreaterThanEqual(now.minusDays(7)),
                        analyticsEventRepository.countByEventType(AnalyticsEventType.QUICK_REVIEW_STARTED),
                        analyticsEventRepository.countByEventType(AnalyticsEventType.CHALLENGE_QUIZ_STARTED),
                        analyticsEventRepository.countByEventType(AnalyticsEventType.ADAPTIVE_PRACTICE_STARTED),
                        analyticsEventRepository.countByEventType(AnalyticsEventType.LONG_EXAM_STARTED),
                        analyticsEventRepository.countByEventType(AnalyticsEventType.INTERVIEW_PRACTICE_STARTED),
                        analyticsEventRepository.countByEventType(AnalyticsEventType.PAYWALL_VIEWED),
                        analyticsEventRepository.countByEventType(AnalyticsEventType.UPGRADE_CLICKED),
                        analyticsEventRepository.countByEventType(AnalyticsEventType.SIGNUP_COMPLETED),
                        analyticsEventRepository.countByEventType(AnalyticsEventType.EMAIL_VERIFIED)
                )
        );
    }

    public AdminDashboardTopContentResponse getTopContent() {
        return new AdminDashboardTopContentResponse(
                analyticsEventRepository.findTopPublicNotesByEventType(
                        AnalyticsEventType.PUBLIC_NOTE_VIEWED,
                        PageRequest.of(0, TOP_CONTENT_LIMIT)
                ),
                analyticsEventRepository.findTopPublicNotesByEventType(
                        AnalyticsEventType.PUBLIC_NOTE_COPIED,
                        PageRequest.of(0, TOP_CONTENT_LIMIT)
                ),
                normalizeTopSubjects(
                        studyPackRepository.findTopSubjectsByStudyPackCount(PageRequest.of(0, TOP_CONTENT_LIMIT * 2))
                )
        );
    }

    public AdminDashboardRecentEventsResponse getRecentEvents() {
        List<AnalyticsEventEntity> recentUpgradeEvents = analyticsEventRepository.findByEventTypeOrderByCreatedAtDesc(
                AnalyticsEventType.SUBSCRIPTION_STARTED,
                PageRequest.of(0, RECENT_EVENT_LIMIT)
        );
        Map<UUID, SubscriptionEntity> subscriptionsById = subscriptionRepository.findAllById(
                        recentUpgradeEvents.stream()
                                .map(AnalyticsEventEntity::getEntityId)
                                .filter(java.util.Objects::nonNull)
                                .collect(Collectors.toCollection(LinkedHashSet::new))
                ).stream()
                .collect(Collectors.toMap(SubscriptionEntity::getId, Function.identity()));
        Map<UUID, PaymentTransactionEntity> latestSuccessfulTransactionBySubscriptionId = new LinkedHashMap<>();
        List<UUID> subscriptionIds = subscriptionsById.keySet().stream().toList();
        if (!subscriptionIds.isEmpty()) {
            for (PaymentTransactionEntity transaction : paymentTransactionRepository.findBySubscription_IdInAndStatusOrderByCreatedAtDesc(
                    subscriptionIds,
                    PaymentTransactionStatus.SUCCESS
            )) {
                if (transaction.getSubscription() == null) {
                    continue;
                }
                latestSuccessfulTransactionBySubscriptionId.putIfAbsent(transaction.getSubscription().getId(), transaction);
            }
        }

        List<AdminRecentUpgradeItemResponse> recentPremiumUpgrades = recentUpgradeEvents.stream()
                .map(event -> {
                    SubscriptionEntity subscription = event.getEntityId() == null ? null : subscriptionsById.get(event.getEntityId());
                    if (subscription == null || subscription.getUser() == null) {
                        return null;
                    }
                    PaymentTransactionEntity transaction = latestSuccessfulTransactionBySubscriptionId.get(subscription.getId());
                    return new AdminRecentUpgradeItemResponse(
                            subscription.getId(),
                            subscription.getUser().getEmail(),
                            inferBillingCycle(subscription).name(),
                            subscription.getProvider(),
                            transaction == null ? null : transaction.getId().toString(),
                            transaction == null ? null : transaction.getAmount(),
                            transaction == null ? null : transaction.getCurrency(),
                            subscription.isCancelAtPeriodEnd(),
                            event.getCreatedAt()
                    );
                })
                .filter(java.util.Objects::nonNull)
                .toList();

        List<AdminRecentFailedPaymentItemResponse> recentFailedPayments = paymentTransactionRepository
                .findByStatusOrderByCreatedAtDesc(PaymentTransactionStatus.FAILED, PageRequest.of(0, RECENT_EVENT_LIMIT))
                .stream()
                .map(transaction -> new AdminRecentFailedPaymentItemResponse(
                        transaction.getId(),
                        transaction.getUser().getEmail(),
                        transaction.getAmount(),
                        transaction.getCurrency(),
                        transaction.getProvider(),
                        transaction.getCreatedAt()
                ))
                .toList();

        List<FeedbackEntity> recentFeedbackEntities = feedbackRepository
                .findAllByOrderByCreatedAtDesc(PageRequest.of(0, RECENT_EVENT_LIMIT));
        List<UUID> recentFeedbackIds = recentFeedbackEntities.stream()
                .map(FeedbackEntity::getId)
                .toList();
        Set<UUID> feedbackIdsWithImages = recentFeedbackIds.isEmpty()
                ? Set.of()
                : Set.copyOf(feedbackImageRepository.findExistingFeedbackIds(recentFeedbackIds));
        List<AdminRecentFeedbackItemResponse> recentFeedback = recentFeedbackEntities
                .stream()
                .map(feedback -> new AdminRecentFeedbackItemResponse(
                        feedback.getId(),
                        feedback.getEmail(),
                        feedback.getMessage(),
                        feedback.getPageUrl(),
                        feedback.getStatus(),
                        feedback.getCreatedAt(),
                        feedbackIdsWithImages.contains(feedback.getId())
                ))
                .toList();

        return new AdminDashboardRecentEventsResponse(recentPremiumUpgrades, recentFailedPayments, recentFeedback);
    }

    public AdminOrganicLandingsResponse getOrganicLandings() {
        return getOrganicLandingsSince(OffsetDateTime.now().minusWeeks(ORGANIC_LANDINGS_LOOKBACK_WEEKS));
    }

    AdminOrganicLandingsResponse getOrganicLandingsSince(OffsetDateTime since) {
        List<AdminOrganicLandingsResponse.Landing> landings = analyticsEventRepository.findOrganicLandingsSince(since)
                .stream()
                .map(this::toOrganicLanding)
                .toList();
        ExamHubOrganicClickThroughProjection clickThrough = analyticsEventRepository
                .findExamHubOrganicClickThroughSince(since);
        long googleExamHubViews = clickThrough == null ? 0L : clickThrough.getGoogleExamHubViews();
        long examHubCtaClicks = clickThrough == null ? 0L : clickThrough.getExamHubCtaClicks();

        return new AdminOrganicLandingsResponse(
                landings,
                googleExamHubViews,
                examHubCtaClicks,
                calculateOrganicClickThroughRatio(examHubCtaClicks, googleExamHubViews)
        );
    }

    private List<AdminSubjectMetricItemResponse> normalizeTopSubjects(List<AdminSubjectMetricItemResponse> rawSubjects) {
        Map<String, Long> merged = new LinkedHashMap<>();
        for (AdminSubjectMetricItemResponse item : rawSubjects) {
            String label = normalizeSubject(item.subject());
            merged.merge(label, item.studyPackCount(), Long::sum);
        }
        return merged.entrySet().stream()
                .map(entry -> new AdminSubjectMetricItemResponse(entry.getKey(), entry.getValue()))
                .sorted((left, right) -> Long.compare(right.studyPackCount(), left.studyPackCount()))
                .limit(TOP_CONTENT_LIMIT)
                .toList();
    }

    private AdminOrganicLandingsResponse.Landing toOrganicLanding(OrganicLandingProjection projection) {
        return new AdminOrganicLandingsResponse.Landing(
                projection.getWeekStart(),
                projection.getEventType(),
                projection.getReferrerSource(),
                projection.getTotalCount()
        );
    }

    private BigDecimal calculateOrganicClickThroughRatio(long clicks, long views) {
        if (views == 0L) {
            return null;
        }
        return BigDecimal.valueOf(clicks)
                .divide(BigDecimal.valueOf(views), ORGANIC_CLICK_THROUGH_RATIO_SCALE, RoundingMode.HALF_UP);
    }

    private RevenueEstimate estimateRevenue(List<SubscriptionEntity> activePremiumSubscriptions) {
        List<UUID> recurringSubscriberIds = activePremiumSubscriptions.stream()
                .filter(subscription -> subscription.getBillingType() == BillingType.SUBSCRIPTION)
                .map(subscription -> subscription.getUser().getId())
                .distinct()
                .toList();
        if (recurringSubscriberIds.isEmpty()) {
            return new RevenueEstimate(zeroAmount(), zeroAmount());
        }

        Map<UUID, PaymentTransactionEntity> latestSuccessfulTransactionByUserId = new LinkedHashMap<>();
        for (PaymentTransactionEntity transaction : paymentTransactionRepository.findByUser_IdInAndStatusOrderByCreatedAtDesc(
                recurringSubscriberIds,
                PaymentTransactionStatus.SUCCESS
        )) {
            latestSuccessfulTransactionByUserId.putIfAbsent(transaction.getUser().getId(), transaction);
        }

        BigDecimal estimatedMrr = zeroAmount();
        BigDecimal estimatedArr = zeroAmount();
        for (SubscriptionEntity subscription : activePremiumSubscriptions) {
            if (subscription.getBillingType() != BillingType.SUBSCRIPTION) {
                continue;
            }
            PaymentTransactionEntity latestPayment = latestSuccessfulTransactionByUserId.get(subscription.getUser().getId());
            if (latestPayment == null || latestPayment.getAmount() == null) {
                continue;
            }
            if (inferBillingCycle(subscription) == BillingCycleLabel.YEARLY) {
                estimatedArr = estimatedArr.add(latestPayment.getAmount());
            } else {
                estimatedMrr = estimatedMrr.add(latestPayment.getAmount());
            }
        }

        return new RevenueEstimate(scaleCurrency(estimatedMrr), scaleCurrency(estimatedArr));
    }

    private BillingCycleLabel inferBillingCycle(SubscriptionEntity subscription) {
        if (subscription == null || subscription.getStartAt() == null || subscription.getEndAt() == null) {
            return BillingCycleLabel.MONTHLY;
        }
        long durationDays = Duration.between(subscription.getStartAt(), subscription.getEndAt()).toDays();
        return durationDays >= YEARLY_SUBSCRIPTION_DAYS_THRESHOLD
                ? BillingCycleLabel.YEARLY
                : BillingCycleLabel.MONTHLY;
    }

    private String normalizeSubject(String subject) {
        if (subject == null) {
            return "Uncategorized";
        }
        String trimmed = subject.trim();
        return trimmed.isEmpty() ? "Uncategorized" : trimmed;
    }

    private BigDecimal zeroAmount() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal scaleCurrency(BigDecimal value) {
        return value == null ? zeroAmount() : value.setScale(2, RoundingMode.HALF_UP);
    }

    private record RevenueEstimate(BigDecimal estimatedMrr, BigDecimal estimatedArr) {
    }

    private enum BillingCycleLabel {
        MONTHLY,
        YEARLY
    }
}
