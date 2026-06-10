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
    private static final long EXAM_CYCLE_SUBSCRIPTION_DAYS = 90;
    private static final long EXAM_CYCLE_SUBSCRIPTION_DAYS_TOLERANCE = 1;
    private static final long YEARLY_SUBSCRIPTION_DAYS_THRESHOLD = 330;
    private static final String DISCOUNT_SUFFIX = " (Discount applied)";
    private static final String RENEWAL_SUFFIX = " Renewal";
    private static final String FAILED_UPGRADE_PREFIX = "Failed ";
    private static final String PENDING_UPGRADE_PREFIX = "Pending ";
    private static final String UPGRADE_SUFFIX = " upgrade";
    private static final String FAILED_PAYMENT_DESCRIPTION = "Failed payment";
    private static final String PENDING_PAYMENT_DESCRIPTION = "Pending payment";

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final SubscriptionService subscriptionService;
    private final Clock clock;

    public BillingHistoryResponse getHistory(UUID userId) {
        SubscriptionService.PlanSnapshot planSnapshot = subscriptionService.getPlanSnapshot(userId);
        SubscriptionEntity currentPaidSubscription = planSnapshot.planType().isPaid()
                ? subscriptionService.findActiveSubscription(userId, planSnapshot.planType()).orElse(null)
                : null;
        List<PaymentTransactionEntity> transactions = paymentTransactionRepository.findByUser_IdOrderByCreatedAtDesc(userId);
        UUID firstSuccessfulTransactionId = findFirstSuccessfulTransactionId(transactions);

        return new BillingHistoryResponse(
                planSnapshot.planType(),
                currentPaidSubscription == null ? null : currentPaidSubscription.getStatus(),
                resolveBillingCycle(currentPaidSubscription),
                currentPaidSubscription == null ? null : currentPaidSubscription.getStartAt(),
                currentPaidSubscription == null ? null : currentPaidSubscription.getEndAt(),
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
        PlanType transactionPlanType = transaction.getPlanType() == null ? PlanType.PRO : transaction.getPlanType();
        if (transaction.getBillingType() == BillingType.PREPAID) {
            if (transaction.getStatus() == PaymentTransactionStatus.FAILED) {
                return FAILED_UPGRADE_PREFIX + transactionPlanType.getDisplayName() + UPGRADE_SUFFIX;
            }
            if (transaction.getStatus() == PaymentTransactionStatus.PENDING) {
                return PENDING_UPGRADE_PREFIX + transactionPlanType.getDisplayName() + UPGRADE_SUFFIX;
            }
            return resolveSuccessfulPaidDescription(transaction, firstSuccessfulTransactionId);
        }
        if (transaction.getStatus() == PaymentTransactionStatus.FAILED) {
            return FAILED_PAYMENT_DESCRIPTION;
        }
        if (transaction.getStatus() == PaymentTransactionStatus.PENDING) {
            return PENDING_PAYMENT_DESCRIPTION;
        }
        return resolveSuccessfulPaidDescription(transaction, firstSuccessfulTransactionId);
    }

    private String resolveSuccessfulPaidDescription(
            PaymentTransactionEntity transaction,
            UUID firstSuccessfulTransactionId
    ) {
        BillingCycle billingCycle = resolveTransactionBillingCycle(transaction);
        boolean discounted = transaction.getDiscountAmount() != null
                && transaction.getDiscountAmount().signum() > 0;
        String planLabel = (transaction.getPlanType() == null ? PlanType.PRO : transaction.getPlanType()).getDisplayName();
        String cycleLabel = switch (billingCycle) {
            case YEARLY -> "Annual";
            case EXAM_CYCLE -> "90-Day Exam Pass";
            case MONTHLY -> "Monthly";
        };
        if (firstSuccessfulTransactionId != null && firstSuccessfulTransactionId.equals(transaction.getId())) {
            return discounted
                    ? planLabel + " " + cycleLabel + DISCOUNT_SUFFIX
                    : planLabel + " " + cycleLabel;
        }
        return discounted
                ? planLabel + RENEWAL_SUFFIX + DISCOUNT_SUFFIX
                : planLabel + RENEWAL_SUFFIX;
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
        if (Math.abs(durationDays - EXAM_CYCLE_SUBSCRIPTION_DAYS) <= EXAM_CYCLE_SUBSCRIPTION_DAYS_TOLERANCE) {
            return BillingCycle.EXAM_CYCLE;
        }
        return durationDays >= YEARLY_SUBSCRIPTION_DAYS_THRESHOLD ? BillingCycle.YEARLY : BillingCycle.MONTHLY;
    }

    private BillingCycle resolveTransactionBillingCycle(PaymentTransactionEntity transaction) {
        if (transaction == null || transaction.getBillingCycle() == null) {
            return BillingCycle.MONTHLY;
        }
        return transaction.getBillingCycle();
    }
}
