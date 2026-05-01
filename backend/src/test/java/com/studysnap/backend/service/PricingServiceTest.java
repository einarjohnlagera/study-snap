package com.studysnap.backend.service;

import com.studysnap.backend.config.StudySnapProperties;
import com.studysnap.backend.dto.BillingPricingResponse;
import com.studysnap.backend.entity.BillingCycle;
import com.studysnap.backend.entity.DiscountVoucherEntity;
import com.studysnap.backend.entity.PaymentTransactionEntity;
import com.studysnap.backend.entity.PlanType;
import com.studysnap.backend.entity.SubscriptionEntity;
import com.studysnap.backend.entity.UserEntity;
import com.studysnap.backend.entity.VoucherBillingCycleScope;
import com.studysnap.backend.entity.VoucherDiscountType;
import com.studysnap.backend.entity.VoucherPlanScope;
import com.studysnap.backend.entity.VoucherRedemptionEntity;
import com.studysnap.backend.repository.DiscountVoucherRepository;
import com.studysnap.backend.repository.SubscriptionRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.repository.VoucherRedemptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PricingServiceTest {

    @Mock
    private PricingRegionResolver pricingRegionResolver;
    @Mock
    private UserRepository userRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private DiscountVoucherRepository discountVoucherRepository;
    @Mock
    private VoucherRedemptionRepository voucherRedemptionRepository;

    private PricingService pricingService;

    @BeforeEach
    void setUp() {
        StudySnapProperties properties = new StudySnapProperties();
        StudySnapProperties.RegionPricing phPricing = new StudySnapProperties.RegionPricing();
        phPricing.setCurrency("PHP");
        phPricing.setActive(true);
        phPricing.getPlus().getMonthly().setAmount(new BigDecimal("179.00"));
        phPricing.getPlus().getMonthly().setDurationDays(30);
        phPricing.getPlus().getYearly().setActive(false);
        phPricing.getPro().getMonthly().setAmount(new BigDecimal("249.00"));
        phPricing.getPro().getMonthly().setDurationDays(30);
        phPricing.getPro().getYearly().setAmount(new BigDecimal("1999.00"));
        phPricing.getPro().getYearly().setDurationDays(365);
        properties.getBilling().setPricingRegions(Map.of("PH", phPricing));

        pricingService = new PricingService(
                properties,
                pricingRegionResolver,
                userRepository,
                subscriptionRepository,
                discountVoucherRepository,
                voucherRedemptionRepository
        );
    }

    @Test
    void getPricing_returnsPlanScopedAmountsAndIntroPricing() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        DiscountVoucherEntity plusIntroVoucher = buildVoucher(
                "INTRO-PH-PLUS-MONTHLY",
                VoucherDiscountType.OVERRIDE_PRICE,
                new BigDecimal("149.00"),
                VoucherPlanScope.PLUS,
                VoucherBillingCycleScope.MONTHLY,
                true,
                true
        );
        DiscountVoucherEntity proIntroVoucher = buildVoucher(
                "INTRO-PH-PRO-MONTHLY",
                VoucherDiscountType.OVERRIDE_PRICE,
                new BigDecimal("199.00"),
                VoucherPlanScope.PRO,
                VoucherBillingCycleScope.MONTHLY,
                true,
                true
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pricingRegionResolver.normalizeCountryCode("PH")).thenReturn("PH");
        when(pricingRegionResolver.resolveRegion("PH")).thenReturn("PH");
        when(discountVoucherRepository.findByIsActiveTrue()).thenReturn(List.of(plusIntroVoucher, proIntroVoucher));
        when(subscriptionRepository.existsByUser_IdAndPlanTypeIn(eq(userId), any())).thenReturn(false);
        when(voucherRedemptionRepository.existsByVoucher_IdAndUser_Id(any(UUID.class), eq(userId))).thenReturn(false);

        BillingPricingResponse response = pricingService.getPricing(userId, "PH");

        assertThat(response.region()).isEqualTo("PH");
        assertThat(response.currency()).isEqualTo("PHP");
        assertThat(response.plus().planType()).isEqualTo(PlanType.PLUS);
        assertThat(response.plus().monthly().amount()).isEqualByComparingTo("179.00");
        assertThat(response.plus().monthly().introAmount()).isEqualByComparingTo("149.00");
        assertThat(response.plus().monthly().introEligible()).isTrue();
        assertThat(response.plus().yearly().available()).isFalse();
        assertThat(response.pro().planType()).isEqualTo(PlanType.PRO);
        assertThat(response.pro().monthly().amount()).isEqualByComparingTo("249.00");
        assertThat(response.pro().monthly().introAmount()).isEqualByComparingTo("199.00");
        assertThat(response.pro().yearly().amount()).isEqualByComparingTo("1999.00");
        assertThat(response.pro().yearly().durationDays()).isEqualTo(365);
        assertThat(user.getCountryCode()).isEqualTo("PH");
    }

    @Test
    void resolveCheckoutSelection_appliesAutomaticPlusIntroForEligibleNewSubscriber() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        DiscountVoucherEntity plusIntroVoucher = buildVoucher(
                "INTRO-PH-PLUS-MONTHLY",
                VoucherDiscountType.OVERRIDE_PRICE,
                new BigDecimal("149.00"),
                VoucherPlanScope.PLUS,
                VoucherBillingCycleScope.MONTHLY,
                true,
                true
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pricingRegionResolver.normalizeCountryCode("PH")).thenReturn("PH");
        when(pricingRegionResolver.resolveRegion("PH")).thenReturn("PH");
        when(discountVoucherRepository.findByIsActiveTrue()).thenReturn(List.of(plusIntroVoucher));
        when(subscriptionRepository.existsByUser_IdAndPlanTypeIn(eq(userId), any())).thenReturn(false);
        when(voucherRedemptionRepository.existsByVoucher_IdAndUser_Id(plusIntroVoucher.getId(), userId)).thenReturn(false);

        PricingService.CheckoutSelection selection = pricingService.resolveCheckoutSelection(
                userId,
                PlanType.PLUS,
                BillingCycle.MONTHLY,
                null,
                "PH"
        );

        assertThat(selection.planType()).isEqualTo(PlanType.PLUS);
        assertThat(selection.planDisplayName()).isEqualTo("Plus");
        assertThat(selection.billingCycle()).isEqualTo(BillingCycle.MONTHLY);
        assertThat(selection.accessDurationDays()).isEqualTo(30);
        assertThat(selection.basePrice()).isEqualByComparingTo("179.00");
        assertThat(selection.discountAmount()).isEqualByComparingTo("30.00");
        assertThat(selection.voucherId()).isEqualTo(plusIntroVoucher.getId());
        assertThat(selection.voucherCode()).isEqualTo("INTRO-PH-PLUS-MONTHLY");
        assertThat(selection.effectivePrice()).isEqualByComparingTo("149.00");
    }

    @Test
    void resolveCheckoutSelection_skipsNewSubscriberIntroWhenUserAlreadyHasPaidHistory() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        DiscountVoucherEntity proIntroVoucher = buildVoucher(
                "INTRO-PH-PRO-MONTHLY",
                VoucherDiscountType.OVERRIDE_PRICE,
                new BigDecimal("199.00"),
                VoucherPlanScope.PRO,
                VoucherBillingCycleScope.MONTHLY,
                true,
                true
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pricingRegionResolver.normalizeCountryCode("PH")).thenReturn("PH");
        when(pricingRegionResolver.resolveRegion("PH")).thenReturn("PH");
        when(discountVoucherRepository.findByIsActiveTrue()).thenReturn(List.of(proIntroVoucher));
        when(subscriptionRepository.existsByUser_IdAndPlanTypeIn(eq(userId), any())).thenReturn(true);

        PricingService.CheckoutSelection selection = pricingService.resolveCheckoutSelection(
                userId,
                PlanType.PRO,
                BillingCycle.MONTHLY,
                null,
                "PH"
        );

        assertThat(selection.planType()).isEqualTo(PlanType.PRO);
        assertThat(selection.basePrice()).isEqualByComparingTo("249.00");
        assertThat(selection.discountAmount()).isEqualByComparingTo("0.00");
        assertThat(selection.voucherId()).isNull();
        assertThat(selection.voucherCode()).isNull();
        assertThat(selection.effectivePrice()).isEqualByComparingTo("249.00");
    }

    @Test
    void resolveCheckoutSelection_appliesExplicitPercentageVoucherForProYearlyCheckout() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        DiscountVoucherEntity yearlyVoucher = buildVoucher(
                "SAVE10",
                VoucherDiscountType.PERCENTAGE,
                new BigDecimal("10.00"),
                VoucherPlanScope.PRO,
                VoucherBillingCycleScope.YEARLY,
                true,
                false
        );
        yearlyVoucher.setRegionScope("ANY");
        yearlyVoucher.setRequiresCode(true);
        yearlyVoucher.setNewSubscribersOnly(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pricingRegionResolver.normalizeCountryCode("PH")).thenReturn("PH");
        when(pricingRegionResolver.resolveRegion("PH")).thenReturn("PH");
        when(discountVoucherRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(yearlyVoucher));

        PricingService.CheckoutSelection selection = pricingService.resolveCheckoutSelection(
                userId,
                PlanType.PRO,
                BillingCycle.YEARLY,
                "SAVE10",
                "PH"
        );

        assertThat(selection.planType()).isEqualTo(PlanType.PRO);
        assertThat(selection.billingCycle()).isEqualTo(BillingCycle.YEARLY);
        assertThat(selection.accessDurationDays()).isEqualTo(365);
        assertThat(selection.basePrice()).isEqualByComparingTo("1999.00");
        assertThat(selection.discountAmount()).isEqualByComparingTo("199.90");
        assertThat(selection.effectivePrice()).isEqualByComparingTo("1799.10");
    }

    @Test
    void resolveCheckoutSelection_capsFixedAmountDiscountAtZero() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        DiscountVoucherEntity fixedAmountVoucher = buildVoucher(
                "SAVE500",
                VoucherDiscountType.FIXED_AMOUNT,
                new BigDecimal("500.00"),
                VoucherPlanScope.PRO,
                VoucherBillingCycleScope.MONTHLY,
                true,
                false
        );
        fixedAmountVoucher.setRegionScope("ANY");
        fixedAmountVoucher.setRequiresCode(true);
        fixedAmountVoucher.setNewSubscribersOnly(false);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pricingRegionResolver.normalizeCountryCode("PH")).thenReturn("PH");
        when(pricingRegionResolver.resolveRegion("PH")).thenReturn("PH");
        when(discountVoucherRepository.findByCodeIgnoreCase("SAVE500")).thenReturn(Optional.of(fixedAmountVoucher));

        PricingService.CheckoutSelection selection = pricingService.resolveCheckoutSelection(
                userId,
                PlanType.PRO,
                BillingCycle.MONTHLY,
                "SAVE500",
                "PH"
        );

        assertThat(selection.basePrice()).isEqualByComparingTo("249.00");
        assertThat(selection.discountAmount()).isEqualByComparingTo("249.00");
        assertThat(selection.effectivePrice()).isEqualByComparingTo("0.00");
    }

    @Test
    void recordVoucherRedemption_savesDiscountAmountOnlyOncePerPaymentTransaction() {
        UUID userId = UUID.randomUUID();
        DiscountVoucherEntity voucher = buildVoucher(
                "INTRO-PH-PRO-MONTHLY",
                VoucherDiscountType.OVERRIDE_PRICE,
                new BigDecimal("199.00"),
                VoucherPlanScope.PRO,
                VoucherBillingCycleScope.MONTHLY,
                false,
                true
        );
        UserEntity user = buildUser(userId);
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        PaymentTransactionEntity paymentTransaction = new PaymentTransactionEntity();
        paymentTransaction.setId(UUID.randomUUID());
        paymentTransaction.setUser(user);
        paymentTransaction.setVoucher(voucher);
        paymentTransaction.setDiscountAmount(new BigDecimal("50.00"));
        paymentTransaction.setAmount(new BigDecimal("199.00"));
        paymentTransaction.setCurrency("PHP");

        when(voucherRedemptionRepository.existsByPaymentTransaction_Id(paymentTransaction.getId())).thenReturn(false);

        pricingService.recordVoucherRedemption(subscription, paymentTransaction);

        ArgumentCaptor<VoucherRedemptionEntity> redemptionCaptor = ArgumentCaptor.forClass(VoucherRedemptionEntity.class);
        verify(voucherRedemptionRepository).save(redemptionCaptor.capture());
        VoucherRedemptionEntity saved = redemptionCaptor.getValue();
        assertThat(saved.getVoucher()).isEqualTo(voucher);
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getSubscription()).isEqualTo(subscription);
        assertThat(saved.getPaymentTransaction()).isEqualTo(paymentTransaction);
        assertThat(saved.getAppliedAmount()).isEqualByComparingTo("50.00");
        assertThat(saved.getCurrency()).isEqualTo("PHP");
    }

    @Test
    void recordVoucherRedemption_skipsDuplicateRedemptionForSamePayment() {
        PaymentTransactionEntity paymentTransaction = new PaymentTransactionEntity();
        paymentTransaction.setId(UUID.randomUUID());
        paymentTransaction.setVoucher(buildVoucher(
                "INTRO-PH-PRO-MONTHLY",
                VoucherDiscountType.OVERRIDE_PRICE,
                new BigDecimal("199.00"),
                VoucherPlanScope.PRO,
                VoucherBillingCycleScope.MONTHLY,
                false,
                true
        ));
        paymentTransaction.setDiscountAmount(new BigDecimal("50.00"));

        when(voucherRedemptionRepository.existsByPaymentTransaction_Id(paymentTransaction.getId())).thenReturn(true);

        pricingService.recordVoucherRedemption(new SubscriptionEntity(), paymentTransaction);

        verify(voucherRedemptionRepository, never()).save(any());
    }

    private UserEntity buildUser(UUID userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("user@example.com");
        user.setCountryCode("US");
        user.setUpdatedAt(OffsetDateTime.parse("2026-03-23T00:00:00Z"));
        return user;
    }

    private DiscountVoucherEntity buildVoucher(
            String code,
            VoucherDiscountType discountType,
            BigDecimal discountValue,
            VoucherPlanScope planScope,
            VoucherBillingCycleScope billingCycleScope,
            boolean active,
            boolean newSubscribersOnly
    ) {
        DiscountVoucherEntity voucher = new DiscountVoucherEntity();
        voucher.setId(UUID.randomUUID());
        voucher.setCode(code);
        voucher.setName(code);
        voucher.setDescription("Promo");
        voucher.setDiscountType(discountType);
        voucher.setDiscountValue(discountValue);
        voucher.setCurrency("PHP");
        voucher.setBillingCycleScope(billingCycleScope);
        voucher.setPlanScope(planScope);
        voucher.setRegionScope("PH");
        voucher.setNewSubscribersOnly(newSubscribersOnly);
        voucher.setRequiresCode(false);
        voucher.setActive(active);
        voucher.setValidFrom(OffsetDateTime.now().minusDays(1));
        voucher.setValidUntil(OffsetDateTime.now().plusDays(30));
        voucher.setCreatedAt(OffsetDateTime.now().minusDays(2));
        voucher.setUpdatedAt(OffsetDateTime.now().minusDays(1));
        return voucher;
    }
}
