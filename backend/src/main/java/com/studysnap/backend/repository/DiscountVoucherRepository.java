package com.studysnap.backend.repository;

import com.studysnap.backend.entity.DiscountVoucherEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DiscountVoucherRepository extends JpaRepository<DiscountVoucherEntity, UUID> {
    Optional<DiscountVoucherEntity> findByCodeIgnoreCase(String code);

    List<DiscountVoucherEntity> findByIsActiveTrue();
}
