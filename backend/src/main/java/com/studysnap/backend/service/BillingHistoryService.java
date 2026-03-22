package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.BillingHistoryItemResponse;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingHistoryService {
    private static final BigDecimal DESCRIPTION_AMOUNT_TOLERANCE = new BigDecimal("0.01");

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final StudySnapProperties properties;

    public List<BillingHistoryItemResponse> getHistory(UUID userId) {
        return paymentTransactionRepository.findByUser_IdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    private BillingHistoryItemResponse toResponse(PaymentTransactionEntity transaction) {
        return new BillingHistoryItemResponse(
                transaction.getCreatedAt(),
                resolveDescription(transaction),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getStatus(),
                transaction.getProvider(),
                transaction.getProviderReferenceId()
        );
    }

    private String resolveDescription(PaymentTransactionEntity transaction) {
        if (transaction.getAmount() == null) {
            return "Premium Subscription";
        }

        BigDecimal yearlyAmount = properties.getBilling().getPaymongo().getYearlyAmount();
        BigDecimal monthlyAmount = properties.getBilling().getPaymongo().getMonthlyAmount();
        if (yearlyAmount != null && isWithinTolerance(transaction.getAmount(), yearlyAmount)) {
            return "Premium Yearly";
        }
        if (monthlyAmount != null && isWithinTolerance(transaction.getAmount(), monthlyAmount)) {
            return "Premium Monthly";
        }
        return "Premium Subscription";
    }

    private boolean isWithinTolerance(BigDecimal left, BigDecimal right) {
        return left.subtract(right).abs().compareTo(DESCRIPTION_AMOUNT_TOLERANCE) <= 0;
    }
}
