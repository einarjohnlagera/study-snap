package com.studysnap.backend.service;

import com.studysnap.backend.dto.BillingHistoryItemResponse;
import com.studysnap.backend.dto.BillingHistoryResponse;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.PaymentTransactionStatus;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.repository.PaymentTransactionRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingHistoryService {
    private static final long YEARLY_SUBSCRIPTION_DAYS_THRESHOLD = 330;
    private static final String PREMIUM_MONTHLY_DESCRIPTION = "Premium Monthly";
    private static final String PREMIUM_ANNUAL_DESCRIPTION = "Premium Annual";
    private static final String PREMIUM_MONTHLY_DISCOUNT_DESCRIPTION = "Premium Monthly (Discount applied)";
    private static final String PREMIUM_ANNUAL_DISCOUNT_DESCRIPTION = "Premium Annual (Discount applied)";
    private static final String PREMIUM_RENEWAL_DESCRIPTION = "Premium Renewal";
    private static final String PREMIUM_RENEWAL_DISCOUNT_DESCRIPTION = "Premium Renewal (Discount applied)";
    private static final String FAILED_PREMIUM_UPGRADE_DESCRIPTION = "Failed Premium upgrade";
    private static final String PENDING_PREMIUM_UPGRADE_DESCRIPTION = "Pending Premium upgrade";
    private static final String FAILED_PAYMENT_DESCRIPTION = "Failed payment";
    private static final String PENDING_PAYMENT_DESCRIPTION = "Pending payment";

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SubscriptionService subscriptionService;
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
                return FAILED_PREMIUM_UPGRADE_DESCRIPTION;
            }
            if (transaction.getStatus() == PaymentTransactionStatus.PENDING) {
                return PENDING_PREMIUM_UPGRADE_DESCRIPTION;
            }
            return resolveSuccessfulPremiumDescription(transaction, firstSuccessfulTransactionId);
        }
        if (transaction.getStatus() == PaymentTransactionStatus.FAILED) {
            return FAILED_PAYMENT_DESCRIPTION;
        }
        if (transaction.getStatus() == PaymentTransactionStatus.PENDING) {
            return PENDING_PAYMENT_DESCRIPTION;
        }
        return resolveSuccessfulPremiumDescription(transaction, firstSuccessfulTransactionId);
    }

    private String resolveSuccessfulPremiumDescription(
            PaymentTransactionEntity transaction,
            UUID firstSuccessfulTransactionId
    ) {
        BillingCycle billingCycle = resolveTransactionBillingCycle(transaction);
        boolean discounted = transaction.getDiscountAmount() != null
                && transaction.getDiscountAmount().signum() > 0;
        if (firstSuccessfulTransactionId != null && firstSuccessfulTransactionId.equals(transaction.getId())) {
            if (discounted) {
                return billingCycle == BillingCycle.YEARLY
                        ? PREMIUM_ANNUAL_DISCOUNT_DESCRIPTION
                        : PREMIUM_MONTHLY_DISCOUNT_DESCRIPTION;
            }
            return billingCycle == BillingCycle.YEARLY
                    ? PREMIUM_ANNUAL_DESCRIPTION
                    : PREMIUM_MONTHLY_DESCRIPTION;
        }
        if (discounted) {
            return PREMIUM_RENEWAL_DISCOUNT_DESCRIPTION;
        }
        return PREMIUM_RENEWAL_DESCRIPTION;
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

    private BillingCycle resolveTransactionBillingCycle(PaymentTransactionEntity transaction) {
        if (transaction == null || transaction.getBillingCycle() == null) {
            return BillingCycle.MONTHLY;
        }
        return transaction.getBillingCycle();
    }
}
