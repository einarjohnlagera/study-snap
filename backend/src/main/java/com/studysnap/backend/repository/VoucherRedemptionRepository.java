package com.studysnap.backend.repository;

import com.studysnap.backend.entity.VoucherRedemptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemptionEntity, UUID> {
    long countByVoucher_Id(UUID voucherId);

    boolean existsByVoucher_IdAndUser_Id(UUID voucherId, UUID userId);

    boolean existsByPaymentTransaction_Id(UUID paymentTransactionId);

    List<VoucherRedemptionEntity> findByUser_Id(UUID userId);
}
