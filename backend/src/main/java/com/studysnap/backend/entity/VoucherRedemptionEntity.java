package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "voucher_redemptions")
@Getter
@Setter
@NoArgsConstructor
public class VoucherRedemptionEntity {

    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "voucher_id", nullable = false)
    private DiscountVoucherEntity voucher;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne
    @JoinColumn(name = "subscription_id")
    private SubscriptionEntity subscription;

    @ManyToOne
    @JoinColumn(name = "payment_transaction_id")
    private PaymentTransactionEntity paymentTransaction;

    @Column(name = "redeemed_at", nullable = false)
    private OffsetDateTime redeemedAt;

    @Column(name = "applied_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal appliedAmount;

    @Column(nullable = false, length = 16)
    private String currency;
}
