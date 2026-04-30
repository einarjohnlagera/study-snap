package com.studysnap.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@NoArgsConstructor
public class PaymentTransactionEntity {
    @Id
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne
    @JoinColumn(name = "subscription_id")
    private SubscriptionEntity subscription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BillingProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "billing_type", nullable = false, length = 32)
    private BillingType billingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false, length = 32)
    private PlanType planType;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 16)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentTransactionStatus status;

    @Column(name = "provider_reference_id", nullable = false, length = 191)
    private String providerReferenceId;

    @Column(name = "checkout_url", length = 1000)
    private String checkoutUrl;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
