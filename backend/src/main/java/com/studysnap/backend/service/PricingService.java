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
import com.studysnap.backend.exception.AppException;
import com.studysnap.backend.exception.UserNotFoundException;
import com.studysnap.backend.repository.DiscountVoucherRepository;
import com.studysnap.backend.repository.SubscriptionRepository;
import com.studysnap.backend.repository.UserRepository;
import com.studysnap.backend.repository.VoucherRedemptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PricingService {
    private final StudySnapProperties properties;
    private final PricingRegionResolver pricingRegionResolver;
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final DiscountVoucherRepository discountVoucherRepository;
    private final VoucherRedemptionRepository voucherRedemptionRepository;

    @Transactional
    public BillingPricingResponse getPricing(UUID userId, String cfIpCountry) {
        UserEntity user = userId == null ? null : userRepository.findById(userId).orElse(null);
        ResolvedPricingContext context = resolvePricingContext(user, cfIpCountry);
        Optional<AppliedVoucher> introVoucher = findBestEligibleVoucher(
                user,
                context.region(),
                context.regionPricing(),
                BillingCycle.MONTHLY,
                PlanType.PREMIUM,
                null
        );
        return new BillingPricingResponse(
                context.region(),
                context.regionPricing().getCurrency(),
                context.regionPricing().getMonthlyPrice(),
                context.regionPricing().getYearlyPrice(),
                introVoucher.map(AppliedVoucher::effectivePrice).orElse(null),
                introVoucher.isPresent(),
                introVoucher.isPresent()
        );
    }

    @Transactional
    public CheckoutSelection resolveCheckoutSelection(
            UUID userId,
            BillingCycle billingCycle,
            String voucherCode,
            String cfIpCountry
    ) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        ResolvedPricingContext context = resolvePricingContext(user, cfIpCountry);
        BillingCycle normalizedCycle = billingCycle == null ? BillingCycle.MONTHLY : billingCycle;
        AppliedVoucher appliedVoucher = findBestEligibleVoucher(
                user,
                context.region(),
                context.regionPricing(),
                normalizedCycle,
                PlanType.PREMIUM,
                voucherCode
        ).orElse(null);

        String planId = resolvePlanId(context.regionPricing(), normalizedCycle, appliedVoucher);
        if (planId == null) {
            throw new AppException(
                    "PAYMONGO_PLAN_NOT_CONFIGURED",
                    "Premium pricing is not configured for your region yet.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        BigDecimal effectivePrice = appliedVoucher == null
                ? basePriceForCycle(context.regionPricing(), normalizedCycle)
                : appliedVoucher.effectivePrice();

        return new CheckoutSelection(
                context.region(),
                context.countryCode(),
                context.regionPricing().getCurrency(),
                normalizedCycle,
                planId,
                appliedVoucher == null ? null : appliedVoucher.voucher().getId(),
                appliedVoucher == null ? null : appliedVoucher.voucher().getCode(),
                effectivePrice
        );
    }

    public void recordVoucherRedemption(
            UUID voucherId,
            UUID userId,
            SubscriptionEntity subscription,
            PaymentTransactionEntity paymentTransaction
    ) {
        if (voucherId == null || paymentTransaction == null) {
            return;
        }
        if (voucherRedemptionRepository.existsByVoucher_IdAndUser_Id(voucherId, userId)) {
            return;
        }

        DiscountVoucherEntity voucher = discountVoucherRepository.findById(voucherId)
                .orElseThrow(() -> new AppException("VOUCHER_NOT_FOUND", "Voucher not found.", HttpStatus.NOT_FOUND));
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        VoucherRedemptionEntity redemption = new VoucherRedemptionEntity();
        redemption.setId(UUID.randomUUID());
        redemption.setVoucher(voucher);
        redemption.setUser(user);
        redemption.setSubscription(subscription);
        redemption.setPaymentTransaction(paymentTransaction);
        redemption.setRedeemedAt(OffsetDateTime.now());
        redemption.setAppliedAmount(paymentTransaction.getAmount());
        redemption.setCurrency(paymentTransaction.getCurrency());
        voucherRedemptionRepository.save(redemption);
    }

    ResolvedPricingContext resolvePricingContext(UserEntity user, String cfIpCountry) {
        String normalizedHeaderCountry = pricingRegionResolver.normalizeCountryCode(cfIpCountry);
        if (user != null && normalizedHeaderCountry != null && !normalizedHeaderCountry.equals(user.getCountryCode())) {
            user.setCountryCode(normalizedHeaderCountry);
            user.setUpdatedAt(OffsetDateTime.now());
        }

        String effectiveCountryCode = normalizedHeaderCountry != null
                ? normalizedHeaderCountry
                : user == null ? null : pricingRegionResolver.normalizeCountryCode(user.getCountryCode());
        String region = pricingRegionResolver.resolveRegion(effectiveCountryCode);
        StudySnapProperties.RegionPricing regionPricing = resolveRegionPricing(region);
        if (!regionPricing.isActive()) {
            throw new AppException(
                    "REGION_PRICING_INACTIVE",
                    "Premium pricing is not available in your region right now.",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        return new ResolvedPricingContext(region, effectiveCountryCode, regionPricing);
    }

    Optional<AppliedVoucher> findBestEligibleVoucher(
            UserEntity user,
            String region,
            StudySnapProperties.RegionPricing regionPricing,
            BillingCycle billingCycle,
            PlanType planType,
            String voucherCode
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        List<DiscountVoucherEntity> candidates = voucherCode == null || voucherCode.isBlank()
                ? discountVoucherRepository.findByIsActiveTrue().stream()
                .filter(voucher -> !voucher.isRequiresCode())
                .toList()
                : discountVoucherRepository.findByCodeIgnoreCase(voucherCode.trim()).stream().toList();

        return candidates.stream()
                .filter(voucher -> isEligible(voucher, user, region, regionPricing, billingCycle, planType, now))
                .map(voucher -> applyVoucher(voucher, regionPricing, billingCycle))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(Comparator.comparing(AppliedVoucher::discountAmount));
    }

    private boolean isEligible(
            DiscountVoucherEntity voucher,
            UserEntity user,
            String region,
            StudySnapProperties.RegionPricing regionPricing,
            BillingCycle billingCycle,
            PlanType planType,
            OffsetDateTime now
    ) {
        if (!voucher.isActive()) {
            return false;
        }
        if (voucher.getValidFrom() != null && now.isBefore(voucher.getValidFrom())) {
            return false;
        }
        if (voucher.getValidUntil() != null && now.isAfter(voucher.getValidUntil())) {
            return false;
        }
        if (voucher.getMaxRedemptions() != null
                && voucherRedemptionRepository.countByVoucher_Id(voucher.getId()) >= voucher.getMaxRedemptions()) {
            return false;
        }
        if (!matchesRegion(voucher.getRegionScope(), region)) {
            return false;
        }
        if (!matchesCycle(voucher.getBillingCycleScope(), billingCycle)) {
            return false;
        }
        if (!matchesPlan(voucher.getPlanScope(), planType)) {
            return false;
        }
        if (!voucher.getCurrency().equalsIgnoreCase(regionPricing.getCurrency())) {
            return false;
        }
        if (!voucher.isNewSubscribersOnly()) {
            return true;
        }
        if (user == null) {
            return true;
        }
        boolean hasPriorPremiumSubscription = subscriptionRepository.existsByUser_IdAndPlanType(user.getId(), PlanType.PREMIUM);
        if (hasPriorPremiumSubscription) {
            return false;
        }
        return !voucherRedemptionRepository.existsByVoucher_IdAndUser_Id(voucher.getId(), user.getId());
    }

    private Optional<AppliedVoucher> applyVoucher(
            DiscountVoucherEntity voucher,
            StudySnapProperties.RegionPricing regionPricing,
            BillingCycle billingCycle
    ) {
        BigDecimal basePrice = basePriceForCycle(regionPricing, billingCycle);
        BigDecimal effectivePrice;
        if (voucher.getDiscountType() == VoucherDiscountType.OVERRIDE_PRICE) {
            effectivePrice = voucher.getDiscountValue();
        } else if (voucher.getDiscountType() == VoucherDiscountType.FIXED_AMOUNT) {
            effectivePrice = basePrice.subtract(voucher.getDiscountValue());
        } else {
            BigDecimal percentage = voucher.getDiscountValue().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            effectivePrice = basePrice.multiply(BigDecimal.ONE.subtract(percentage));
        }
        effectivePrice = effectivePrice.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = basePrice.subtract(effectivePrice).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        return Optional.of(new AppliedVoucher(voucher, effectivePrice, discountAmount));
    }

    private BigDecimal basePriceForCycle(StudySnapProperties.RegionPricing regionPricing, BillingCycle billingCycle) {
        return billingCycle == BillingCycle.YEARLY ? regionPricing.getYearlyPrice() : regionPricing.getMonthlyPrice();
    }

    private String resolvePlanId(
            StudySnapProperties.RegionPricing regionPricing,
            BillingCycle billingCycle,
            AppliedVoucher appliedVoucher
    ) {
        if (billingCycle == BillingCycle.YEARLY) {
            if (appliedVoucher != null) {
                String introYearlyPlanId = normalizeText(regionPricing.getPaymongoIntroYearlyPlanId());
                if (introYearlyPlanId != null) {
                    return introYearlyPlanId;
                }
            }
            return normalizeText(regionPricing.getPaymongoYearlyPlanId());
        }
        if (appliedVoucher != null) {
            String introMonthlyPlanId = normalizeText(regionPricing.getPaymongoIntroMonthlyPlanId());
            if (introMonthlyPlanId != null) {
                return introMonthlyPlanId;
            }
        }
        return normalizeText(regionPricing.getPaymongoMonthlyPlanId());
    }

    private StudySnapProperties.RegionPricing resolveRegionPricing(String region) {
        Map<String, StudySnapProperties.RegionPricing> configuredRegions = properties.getBilling().getPricingRegions();
        StudySnapProperties.RegionPricing regionPricing = configuredRegions.get(region);
        if (regionPricing != null) {
            return regionPricing;
        }
        StudySnapProperties.RegionPricing upperCaseRegionPricing = configuredRegions.get(region.toUpperCase(Locale.ROOT));
        if (upperCaseRegionPricing != null) {
            return upperCaseRegionPricing;
        }
        throw new AppException(
                "REGION_PRICING_NOT_CONFIGURED",
                "Premium pricing is not configured for your region yet.",
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    private boolean matchesRegion(String regionScope, String region) {
        return "ANY".equalsIgnoreCase(regionScope) || region.equalsIgnoreCase(regionScope);
    }

    private boolean matchesCycle(VoucherBillingCycleScope scope, BillingCycle billingCycle) {
        return scope == VoucherBillingCycleScope.ANY
                || (scope == VoucherBillingCycleScope.MONTHLY && billingCycle == BillingCycle.MONTHLY)
                || (scope == VoucherBillingCycleScope.YEARLY && billingCycle == BillingCycle.YEARLY);
    }

    private boolean matchesPlan(VoucherPlanScope scope, PlanType planType) {
        return scope == VoucherPlanScope.ANY
                || (scope == VoucherPlanScope.PREMIUM && planType == PlanType.PREMIUM)
                || (scope == VoucherPlanScope.FREE && planType == PlanType.FREE);
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record CheckoutSelection(
            String region,
            String countryCode,
            String currency,
            BillingCycle billingCycle,
            String planId,
            UUID voucherId,
            String voucherCode,
            BigDecimal effectivePrice
    ) {
    }

    record AppliedVoucher(
            DiscountVoucherEntity voucher,
            BigDecimal effectivePrice,
            BigDecimal discountAmount
    ) {
    }

    record ResolvedPricingContext(
            String region,
            String countryCode,
            StudySnapProperties.RegionPricing regionPricing
    ) {
    }
}
