package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.BillingHistoryResponse;
import com.studysnap.backend.dto.BillingHistoryItemResponse;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.PaymentTransactionStatus;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.SubscriptionStatus;
import com.studysnap.backend.repository.PaymentTransactionRepository;
import com.studysnap.backend.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingHistoryServiceTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private SubscriptionService subscriptionService;

    @Test
    void getHistory_returnsSummaryAndSortedTransactions() {
        StudySnapProperties properties = new StudySnapProperties();
        StudySnapProperties.RegionPricing regionPricing = new StudySnapProperties.RegionPricing();
        regionPricing.setCurrency("USD");
        regionPricing.setMonthlyPrice(new BigDecimal("4.99"));
        regionPricing.setYearlyPrice(new BigDecimal("39.99"));
        properties.getBilling().getPricingRegions().put("US", regionPricing);
        BillingHistoryService service = new BillingHistoryService(
                paymentTransactionRepository,
                subscriptionRepository,
                subscriptionService,
                properties,
                Clock.fixed(Instant.parse("2026-03-25T00:00:00Z"), ZoneOffset.UTC)
        );

        UUID userId = UUID.randomUUID();
        OffsetDateTime startAt = OffsetDateTime.parse("2026-03-01T00:00:00Z");
        OffsetDateTime endAt = OffsetDateTime.parse("2026-04-01T00:00:00Z");
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        subscription.setPlanType(PlanType.PREMIUM);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setStartAt(startAt);
        subscription.setEndAt(endAt);
        subscription.setCancelAtPeriodEnd(true);

        PaymentTransactionEntity failedTransaction = buildTransaction(
                "evt_failed",
                new BigDecimal("4.99"),
                PaymentTransactionStatus.FAILED,
                OffsetDateTime.parse("2026-03-22T00:00:00Z")
        );
        PaymentTransactionEntity renewalTransaction = buildTransaction(
                "evt_renewal",
                new BigDecimal("4.99"),
                PaymentTransactionStatus.SUCCESS,
                OffsetDateTime.parse("2026-03-15T00:00:00Z")
        );
        PaymentTransactionEntity initialTransaction = buildTransaction(
                "evt_initial",
                new BigDecimal("4.99"),
                PaymentTransactionStatus.SUCCESS,
                OffsetDateTime.parse("2026-03-01T00:00:00Z")
        );

        when(subscriptionService.getPlanSnapshot(userId)).thenReturn(
                new SubscriptionService.PlanSnapshot(
                        PlanType.PREMIUM,
                        true,
                        endAt,
                        OffsetDateTime.parse("2026-03-20T00:00:00Z")
                )
        );
        when(subscriptionRepository.findByUser_IdAndPlanTypeAndStatusOrderByUpdatedAtDesc(
                userId,
                PlanType.PREMIUM,
                SubscriptionStatus.ACTIVE
        )).thenReturn(List.of(subscription));
        when(paymentTransactionRepository.findByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(List.of(
                failedTransaction,
                renewalTransaction,
                initialTransaction
        ));

        BillingHistoryResponse history = service.getHistory(userId);

        assertThat(history.currentPlan()).isEqualTo(PlanType.PREMIUM);
        assertThat(history.subscriptionStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(history.billingType()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(history.currentPeriodStart()).isEqualTo(startAt);
        assertThat(history.currentPeriodEnd()).isEqualTo(endAt);
        assertThat(history.cancelAtPeriodEnd()).isTrue();
        assertThat(history.cancellationEffectiveAt()).isEqualTo(endAt);
        assertThat(history.transactions()).extracting(BillingHistoryItemResponse::description)
                .containsExactly("Failed payment", "Subscription Renewal", "Premium Monthly");
        assertThat(history.transactions()).extracting(BillingHistoryItemResponse::providerReferenceId)
                .containsExactly("evt_failed", "evt_renewal", "evt_initial");
    }

    @Test
    void getHistory_returnsFreeSummaryWhenThereAreNoTransactions() {
        StudySnapProperties properties = new StudySnapProperties();
        BillingHistoryService service = new BillingHistoryService(
                paymentTransactionRepository,
                subscriptionRepository,
                subscriptionService,
                properties,
                Clock.fixed(Instant.parse("2026-03-25T00:00:00Z"), ZoneOffset.UTC)
        );

        UUID userId = UUID.randomUUID();
        when(subscriptionService.getPlanSnapshot(userId)).thenReturn(
                new SubscriptionService.PlanSnapshot(PlanType.FREE, false, null, null)
        );
        when(subscriptionRepository.findByUser_IdAndPlanTypeAndStatusOrderByUpdatedAtDesc(
                userId,
                PlanType.PREMIUM,
                SubscriptionStatus.ACTIVE
        )).thenReturn(List.of());
        when(paymentTransactionRepository.findByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(List.of());

        BillingHistoryResponse history = service.getHistory(userId);

        assertThat(history.currentPlan()).isEqualTo(PlanType.FREE);
        assertThat(history.subscriptionStatus()).isNull();
        assertThat(history.billingType()).isNull();
        assertThat(history.currentPeriodStart()).isNull();
        assertThat(history.currentPeriodEnd()).isNull();
        assertThat(history.cancelAtPeriodEnd()).isFalse();
        assertThat(history.transactions()).isEmpty();
    }

    private PaymentTransactionEntity buildTransaction(
            String referenceId,
            BigDecimal amount,
            PaymentTransactionStatus status,
            OffsetDateTime createdAt
    ) {
        PaymentTransactionEntity transaction = new PaymentTransactionEntity();
        transaction.setId(UUID.randomUUID());
        transaction.setProvider(BillingProvider.XENDIT);
        transaction.setBillingType(BillingType.SUBSCRIPTION);
        transaction.setPlanType(PlanType.PREMIUM);
        transaction.setAmount(amount);
        transaction.setCurrency("USD");
        transaction.setStatus(status);
        transaction.setProviderReferenceId(referenceId);
        transaction.setCreatedAt(createdAt);
        return transaction;
    }
}
