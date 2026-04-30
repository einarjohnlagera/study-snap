package com.studysnap.backend.service;

import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.PaymentTransactionStatus;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.PaymentTransactionRepository;
import com.studysnap.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class PaymentTransactionService {
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Optional<PaymentTransactionEntity> findByProviderReferenceId(BillingProvider provider, String providerReferenceId) {
        return paymentTransactionRepository.findByProviderAndProviderReferenceId(provider, providerReferenceId);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentTransactionEntity> findLatestPendingTransaction(
            UUID userId,
            BillingProvider provider,
            PlanType planType
    ) {
        return paymentTransactionRepository.findFirstByUser_IdAndProviderAndPlanTypeAndStatusOrderByCreatedAtDesc(
                userId,
                provider,
                planType,
                PaymentTransactionStatus.PENDING
        );
    }

    public Optional<PaymentTransactionEntity> createPending(
            UUID userId,
            BillingProvider provider,
            BillingType billingType,
            PlanType planType,
            BigDecimal amount,
            String currency,
            String providerReferenceId,
            String checkoutUrl,
            OffsetDateTime expiresAt
    ) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        PaymentTransactionEntity transaction = new PaymentTransactionEntity();
        transaction.setId(UUID.randomUUID());
        transaction.setUser(user);
        transaction.setProvider(provider);
        transaction.setBillingType(billingType);
        transaction.setPlanType(planType);
        transaction.setAmount(amount == null ? BigDecimal.ZERO : amount);
        transaction.setCurrency(normalizeCurrency(currency));
        transaction.setStatus(PaymentTransactionStatus.PENDING);
        transaction.setProviderReferenceId(providerReferenceId);
        transaction.setCheckoutUrl(normalizeCheckoutUrl(checkoutUrl));
        transaction.setExpiresAt(expiresAt);
        transaction.setCreatedAt(OffsetDateTime.now());

        try {
            return Optional.of(paymentTransactionRepository.save(transaction));
        } catch (DataIntegrityViolationException ex) {
            return Optional.empty();
        }
    }

    public void markSuccess(UUID transactionId) {
        paymentTransactionRepository.findById(transactionId)
                .ifPresent(transaction -> {
                    transaction.setStatus(PaymentTransactionStatus.SUCCESS);
                    paymentTransactionRepository.save(transaction);
                });
    }

    public void attachSubscription(UUID transactionId, SubscriptionEntity subscription) {
        paymentTransactionRepository.findById(transactionId)
                .ifPresent(transaction -> {
                    transaction.setSubscription(subscription);
                    paymentTransactionRepository.save(transaction);
                });
    }

    public void markFailed(UUID transactionId) {
        paymentTransactionRepository.findById(transactionId)
                .ifPresent(transaction -> {
                    transaction.setStatus(PaymentTransactionStatus.FAILED);
                    paymentTransactionRepository.save(transaction);
                });
    }

    private String normalizeCurrency(String rawCurrency) {
        if (rawCurrency == null || rawCurrency.isBlank()) {
            return "USD";
        }
        return rawCurrency.trim().toUpperCase();
    }

    private String normalizeCheckoutUrl(String rawCheckoutUrl) {
        if (rawCheckoutUrl == null) {
            return null;
        }
        String normalized = rawCheckoutUrl.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
