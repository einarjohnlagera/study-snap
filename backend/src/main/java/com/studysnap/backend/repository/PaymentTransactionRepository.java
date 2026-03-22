package com.studysnap.backend.repository;

import com.studysnap.backend.entity.BillingProvider;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransactionEntity, UUID> {
    Optional<PaymentTransactionEntity> findByProviderAndProviderReferenceId(
            BillingProvider provider,
            String providerReferenceId
    );

    List<PaymentTransactionEntity> findByUser_IdOrderByCreatedAtDesc(UUID userId);
}
