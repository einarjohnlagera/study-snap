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
        phPricing.setMonthlyPrice(new BigDecimal("249.00"));
        phPricing.setYearlyPrice(new BigDecimal("1999.00"));
        phPricing.setPaymongoMonthlyPlanId("plan_ph_monthly");
        phPricing.setPaymongoYearlyPlanId("plan_ph_yearly");
        phPricing.setPaymongoIntroMonthlyPlanId("plan_ph_intro_monthly");
        phPricing.setActive(true);
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
    void getPricing_returnsIntroPromoForEligibleRegion() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        DiscountVoucherEntity introVoucher = buildVoucher("PH-INTRO", new BigDecimal("199.00"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pricingRegionResolver.normalizeCountryCode("PH")).thenReturn("PH");
        when(pricingRegionResolver.resolveRegion("PH")).thenReturn("PH");
        when(discountVoucherRepository.findByIsActiveTrue()).thenReturn(List.of(introVoucher));
        when(subscriptionRepository.existsByUser_IdAndPlanType(userId, PlanType.PREMIUM)).thenReturn(false);
        when(voucherRedemptionRepository.existsByVoucher_IdAndUser_Id(introVoucher.getId(), userId)).thenReturn(false);

        BillingPricingResponse response = pricingService.getPricing(userId, "PH");

        assertThat(response.region()).isEqualTo("PH");
        assertThat(response.currency()).isEqualTo("PHP");
        assertThat(response.monthlyPrice()).isEqualByComparingTo("249.00");
        assertThat(response.yearlyPrice()).isEqualByComparingTo("1999.00");
        assertThat(response.introMonthlyPrice()).isEqualByComparingTo("199.00");
        assertThat(response.hasIntroPromo()).isTrue();
        assertThat(response.introEligible()).isTrue();
        assertThat(user.getCountryCode()).isEqualTo("PH");
    }

    @Test
    void resolveCheckoutSelection_usesIntroPlanForEligibleNewSubscriber() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        DiscountVoucherEntity introVoucher = buildVoucher("PH-INTRO", new BigDecimal("199.00"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pricingRegionResolver.normalizeCountryCode("PH")).thenReturn("PH");
        when(pricingRegionResolver.resolveRegion("PH")).thenReturn("PH");
        when(discountVoucherRepository.findByIsActiveTrue()).thenReturn(List.of(introVoucher));
        when(subscriptionRepository.existsByUser_IdAndPlanType(userId, PlanType.PREMIUM)).thenReturn(false);
        when(voucherRedemptionRepository.existsByVoucher_IdAndUser_Id(introVoucher.getId(), userId)).thenReturn(false);

        PricingService.CheckoutSelection selection = pricingService.resolveCheckoutSelection(
                userId,
                BillingCycle.MONTHLY,
                null,
                "PH"
        );

        assertThat(selection.region()).isEqualTo("PH");
        assertThat(selection.countryCode()).isEqualTo("PH");
        assertThat(selection.currency()).isEqualTo("PHP");
        assertThat(selection.planId()).isEqualTo("plan_ph_intro_monthly");
        assertThat(selection.voucherId()).isEqualTo(introVoucher.getId());
        assertThat(selection.voucherCode()).isEqualTo("PH-INTRO");
        assertThat(selection.effectivePrice()).isEqualByComparingTo("199.00");
    }

    @Test
    void resolveCheckoutSelection_fallsBackToStandardPlanWhenIntroVoucherIsNotEligible() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        DiscountVoucherEntity introVoucher = buildVoucher("PH-INTRO", new BigDecimal("199.00"));

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pricingRegionResolver.normalizeCountryCode("PH")).thenReturn("PH");
        when(pricingRegionResolver.resolveRegion("PH")).thenReturn("PH");
        when(discountVoucherRepository.findByIsActiveTrue()).thenReturn(List.of(introVoucher));
        when(subscriptionRepository.existsByUser_IdAndPlanType(userId, PlanType.PREMIUM)).thenReturn(true);

        PricingService.CheckoutSelection selection = pricingService.resolveCheckoutSelection(
                userId,
                BillingCycle.MONTHLY,
                null,
                "PH"
        );

        assertThat(selection.planId()).isEqualTo("plan_ph_monthly");
        assertThat(selection.voucherId()).isNull();
        assertThat(selection.voucherCode()).isNull();
        assertThat(selection.effectivePrice()).isEqualByComparingTo("249.00");
    }

    @Test
    void resolveCheckoutSelection_usesExplicitVoucherCodeForYearlyPricing() {
        UUID userId = UUID.randomUUID();
        UserEntity user = buildUser(userId);
        DiscountVoucherEntity yearlyVoucher = buildVoucher("SAVE10", new BigDecimal("10.00"));
        yearlyVoucher.setDiscountType(VoucherDiscountType.PERCENTAGE);
        yearlyVoucher.setBillingCycleScope(VoucherBillingCycleScope.YEARLY);
        yearlyVoucher.setRegionScope("ANY");
        yearlyVoucher.setNewSubscribersOnly(false);
        yearlyVoucher.setRequiresCode(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(pricingRegionResolver.normalizeCountryCode("PH")).thenReturn("PH");
        when(pricingRegionResolver.resolveRegion("PH")).thenReturn("PH");
        when(discountVoucherRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(yearlyVoucher));

        PricingService.CheckoutSelection selection = pricingService.resolveCheckoutSelection(
                userId,
                BillingCycle.YEARLY,
                "SAVE10",
                "PH"
        );

        assertThat(selection.planId()).isEqualTo("plan_ph_yearly");
        assertThat(selection.voucherCode()).isEqualTo("SAVE10");
        assertThat(selection.effectivePrice()).isEqualByComparingTo("1799.10");
    }

    @Test
    void recordVoucherRedemption_savesOnceForSuccessfulPayment() {
        UUID userId = UUID.randomUUID();
        UUID voucherId = UUID.randomUUID();
        DiscountVoucherEntity voucher = buildVoucher("PH-INTRO", new BigDecimal("199.00"));
        voucher.setId(voucherId);
        UserEntity user = buildUser(userId);
        SubscriptionEntity subscription = new SubscriptionEntity();
        subscription.setId(UUID.randomUUID());
        PaymentTransactionEntity paymentTransaction = new PaymentTransactionEntity();
        paymentTransaction.setId(UUID.randomUUID());
        paymentTransaction.setAmount(new BigDecimal("199.00"));
        paymentTransaction.setCurrency("PHP");

        when(voucherRedemptionRepository.existsByVoucher_IdAndUser_Id(voucherId, userId)).thenReturn(false);
        when(discountVoucherRepository.findById(voucherId)).thenReturn(Optional.of(voucher));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        pricingService.recordVoucherRedemption(voucherId, userId, subscription, paymentTransaction);

        ArgumentCaptor<VoucherRedemptionEntity> redemptionCaptor = ArgumentCaptor.forClass(VoucherRedemptionEntity.class);
        verify(voucherRedemptionRepository).save(redemptionCaptor.capture());
        VoucherRedemptionEntity saved = redemptionCaptor.getValue();
        assertThat(saved.getVoucher()).isEqualTo(voucher);
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getSubscription()).isEqualTo(subscription);
        assertThat(saved.getPaymentTransaction()).isEqualTo(paymentTransaction);
        assertThat(saved.getAppliedAmount()).isEqualByComparingTo("199.00");
        assertThat(saved.getCurrency()).isEqualTo("PHP");
    }

    @Test
    void recordVoucherRedemption_skipsWhenTransactionIsMissing() {
        pricingService.recordVoucherRedemption(UUID.randomUUID(), UUID.randomUUID(), new SubscriptionEntity(), null);

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

    private DiscountVoucherEntity buildVoucher(String code, BigDecimal price) {
        DiscountVoucherEntity voucher = new DiscountVoucherEntity();
        voucher.setId(UUID.randomUUID());
        voucher.setCode(code);
        voucher.setName(code);
        voucher.setDescription("Promo");
        voucher.setDiscountType(VoucherDiscountType.OVERRIDE_PRICE);
        voucher.setDiscountValue(price);
        voucher.setCurrency("PHP");
        voucher.setBillingCycleScope(VoucherBillingCycleScope.MONTHLY);
        voucher.setPlanScope(VoucherPlanScope.PREMIUM);
        voucher.setRegionScope("PH");
        voucher.setNewSubscribersOnly(true);
        voucher.setRequiresCode(false);
        voucher.setActive(true);
        voucher.setValidFrom(OffsetDateTime.now().minusDays(1));
        voucher.setValidUntil(OffsetDateTime.now().plusDays(30));
        voucher.setCreatedAt(OffsetDateTime.now().minusDays(2));
        voucher.setUpdatedAt(OffsetDateTime.now().minusDays(1));
        return voucher;
    }
}
