package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.BillingHistoryItemResponse;
import com.studysnap.backend.dto.BillingHistoryResponse;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.PaymentTransactionStatus;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.repository.PaymentTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingHistoryService {
    private static final BigDecimal DESCRIPTION_AMOUNT_TOLERANCE = new BigDecimal("0.01");
    private static final long YEARLY_SUBSCRIPTION_DAYS_THRESHOLD = 330;

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SubscriptionService subscriptionService;
    private final StudySnapProperties properties;
    private final Clock clock;

    public BillingHistoryResponse getHistory(UUID userId) {
        SubscriptionService.PlanSnapshot planSnapshot = subscriptionService.getPlanSnapshot(userId);
        SubscriptionEntity currentPremiumSubscription = subscriptionService
                .findActiveSubscription(userId, PlanType.PREMIUM)
                .orElse(null);
        List<PaymentTransactionEntity> transactions = paymentTransactionRepository.findByUser_IdOrderByCreatedAtDesc(userId);
        UUID firstSuccessfulTransactionId = findFirstSuccessfulTransactionId(transactions);

        return new BillingHistoryResponse(
                planSnapshot.planType(),
                currentPremiumSubscription == null ? null : currentPremiumSubscription.getStatus(),
                resolveBillingCycle(currentPremiumSubscription),
                currentPremiumSubscription == null ? null : currentPremiumSubscription.getStartAt(),
                currentPremiumSubscription == null ? null : currentPremiumSubscription.getEndAt(),
                planSnapshot.cancelAtPeriodEnd(),
                planSnapshot.premiumEndsAt(),
                transactions.stream()
                        .map(transaction -> toResponse(transaction, firstSuccessfulTransactionId))
                        .toList()
        );
    }

    private BillingHistoryItemResponse toResponse(PaymentTransactionEntity transaction, UUID firstSuccessfulTransactionId) {
        return new BillingHistoryItemResponse(
                transaction.getId(),
                transaction.getCreatedAt(),
                resolveDescription(transaction, firstSuccessfulTransactionId),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus(),
                transaction.getProvider(),
                transaction.getProviderReferenceId()
        );
    }

    private String resolveDescription(PaymentTransactionEntity transaction, UUID firstSuccessfulTransactionId) {
        if (transaction.getBillingType() == BillingType.PREPAID) {
            if (transaction.getStatus() == PaymentTransactionStatus.FAILED) {
                return "Failed Premium upgrade";
            }
            if (transaction.getStatus() == PaymentTransactionStatus.PENDING) {
                return "Pending Premium upgrade";
            }
            return "Premium Upgrade";
        }
        if (transaction.getStatus() == PaymentTransactionStatus.FAILED) {
            return "Failed payment";
        }
        if (transaction.getStatus() == PaymentTransactionStatus.PENDING) {
            return "Pending payment";
        }
        if (firstSuccessfulTransactionId != null && firstSuccessfulTransactionId.equals(transaction.getId())) {
            return inferTransactionBillingCycle(transaction) == BillingCycle.YEARLY
                    ? "Premium Yearly"
                    : "Premium Monthly";
        }
        return "Subscription Renewal";
    }

    private UUID findFirstSuccessfulTransactionId(List<PaymentTransactionEntity> transactions) {
        return transactions.stream()
                .filter(transaction -> transaction.getStatus() == PaymentTransactionStatus.SUCCESS)
                .reduce((first, second) -> second)
                .map(PaymentTransactionEntity::getId)
                .orElse(null);
    }

    private BillingCycle resolveBillingCycle(SubscriptionEntity subscription) {
        if (subscription == null || subscription.getStartAt() == null || subscription.getEndAt() == null) {
            return null;
        }
        long durationDays = Duration.between(subscription.getStartAt(), subscription.getEndAt()).toDays();
        return durationDays >= YEARLY_SUBSCRIPTION_DAYS_THRESHOLD ? BillingCycle.YEARLY : BillingCycle.MONTHLY;
    }

    private BillingCycle inferTransactionBillingCycle(PaymentTransactionEntity transaction) {
        if (transaction == null || transaction.getAmount() == null) {
            return BillingCycle.MONTHLY;
        }

        String currency = normalizeCurrency(transaction.getCurrency());
        for (Map.Entry<String, StudySnapProperties.RegionPricing> entry : properties.getBilling().getPricingRegions().entrySet()) {
            StudySnapProperties.RegionPricing regionPricing = entry.getValue();
            if (regionPricing == null || !currency.equals(normalizeCurrency(regionPricing.getCurrency()))) {
                continue;
            }
            if (regionPricing.getYearlyPrice() != null && isWithinTolerance(transaction.getAmount(), regionPricing.getYearlyPrice())) {
                return BillingCycle.YEARLY;
            }
            if (regionPricing.getMonthlyPrice() != null && isWithinTolerance(transaction.getAmount(), regionPricing.getMonthlyPrice())) {
                return BillingCycle.MONTHLY;
            }
        }

        return BillingCycle.MONTHLY;
    }

    private String normalizeCurrency(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private boolean isWithinTolerance(BigDecimal left, BigDecimal right) {
        return left.subtract(right).abs().compareTo(DESCRIPTION_AMOUNT_TOLERANCE) <= 0;
    }
}
