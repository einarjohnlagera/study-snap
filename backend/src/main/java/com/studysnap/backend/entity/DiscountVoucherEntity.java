package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "discount_vouchers")
@Getter
@Setter
@NoArgsConstructor
public class DiscountVoucherEntity {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 32)
    private VoucherDiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal discountValue;

    @Column(nullable = false, length = 16)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle_scope", nullable = false, length = 16)
    private VoucherBillingCycleScope billingCycleScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_scope", nullable = false, length = 16)
    private VoucherPlanScope planScope;

    @Column(name = "region_scope", nullable = false, length = 16)
    private String regionScope;

    @Column(name = "new_subscribers_only", nullable = false)
    private boolean newSubscribersOnly;

    @Column(name = "requires_code", nullable = false)
    private boolean requiresCode;

    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(name = "valid_from")
    private OffsetDateTime validFrom;

    @Column(name = "valid_until")
    private OffsetDateTime validUntil;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
