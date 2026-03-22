package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.BillingHistoryItemResponse;
import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.BillingType;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.PaymentTransactionStatus;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingHistoryServiceTest {

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Test
    void getHistory_mapsMonthlyAndYearlyDescriptions() {
        StudySnapProperties properties = new StudySnapProperties();
        properties.getBilling().getPaymongo().setMonthlyAmount(new BigDecimal("4.99"));
        properties.getBilling().getPaymongo().setYearlyAmount(new BigDecimal("39.99"));
        BillingHistoryService service = new BillingHistoryService(paymentTransactionRepository, properties);

        UUID userId = UUID.randomUUID();
        when(paymentTransactionRepository.findByUser_IdOrderByCreatedAtDesc(userId)).thenReturn(List.of(
                buildTransaction("evt_monthly", new BigDecimal("4.99"), PaymentTransactionStatus.SUCCESS),
                buildTransaction("evt_yearly", new BigDecimal("39.99"), PaymentTransactionStatus.SUCCESS)
        ));

        List<BillingHistoryItemResponse> history = service.getHistory(userId);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).description()).isEqualTo("Premium Monthly");
        assertThat(history.get(1).description()).isEqualTo("Premium Yearly");
    }

    private PaymentTransactionEntity buildTransaction(String referenceId, BigDecimal amount, PaymentTransactionStatus status) {
        PaymentTransactionEntity transaction = new PaymentTransactionEntity();
        transaction.setId(UUID.randomUUID());
        transaction.setProvider(BillingProvider.PAYMONGO);
        transaction.setBillingType(BillingType.SUBSCRIPTION);
        transaction.setPlanType(PlanType.PREMIUM);
        transaction.setAmount(amount);
        transaction.setCurrency("USD");
        transaction.setStatus(status);
        transaction.setProviderReferenceId(referenceId);
        transaction.setCreatedAt(OffsetDateTime.now());
        return transaction;
    }
}
