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
    private static final List<PlanType> PAID_PLANS = List.of(PlanType.PLUS, PlanType.PRO);
    private static final String ERROR_REGION_PRICING_INACTIVE = "REGION_PRICING_INACTIVE";
    private static final String ERROR_REGION_PRICING_NOT_CONFIGURED = "REGION_PRICING_NOT_CONFIGURED";
    private static final String ERROR_CHECKOUT_PLAN_NOT_SUPPORTED = "CHECKOUT_PLAN_NOT_SUPPORTED";
    private static final String ERROR_CHECKOUT_BILLING_CYCLE_UNAVAILABLE = "CHECKOUT_BILLING_CYCLE_UNAVAILABLE";
    private static final String MESSAGE_REGION_PRICING_UNAVAILABLE = "Paid plan pricing is not available in your region right now.";
    private static final String MESSAGE_CHECKOUT_PLAN_NOT_SUPPORTED = "This plan is not available for checkout.";
    private static final String MESSAGE_CHECKOUT_BILLING_CYCLE_UNAVAILABLE = "That billing cycle is not available for the selected plan.";

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
        return new BillingPricingResponse(
                context.region(),
                context.regionPricing().getCurrency(),
                buildPlanPricing(user, context, PlanType.PLUS),
                buildPlanPricing(user, context, PlanType.PRO)
        );
    }

    @Transactional
    public CheckoutSelection resolveCheckoutSelection(
            UUID userId,
            PlanType requestedPlanType,
            BillingCycle requestedBillingCycle,
            String voucherCode,
            String cfIpCountry
    ) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        PlanType planType = normalizeRequestedPlan(requestedPlanType);
        BillingCycle billingCycle = requestedBillingCycle == null ? BillingCycle.MONTHLY : requestedBillingCycle;
        ResolvedPricingContext context = resolvePricingContext(user, cfIpCountry);
        StudySnapProperties.BillingCyclePricing cyclePricing = resolveCyclePricing(
                context.regionPricing(),
                planType,
                billingCycle
        );
        BigDecimal basePrice = normalizeAmount(cyclePricing.getAmount());
        AppliedVoucher appliedVoucher = findBestEligibleVoucher(
                user,
                context.region(),
                context.regionPricing().getCurrency(),
                billingCycle,
                planType,
                basePrice,
                voucherCode
        ).orElse(null);
        BigDecimal effectivePrice = appliedVoucher == null
                ? basePrice
                : appliedVoucher.effectivePrice();
        BigDecimal discountAmount = appliedVoucher == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : appliedVoucher.discountAmount();

        return new CheckoutSelection(
                context.region(),
                context.countryCode(),
                context.regionPricing().getCurrency(),
                planType,
                planType.getDisplayName(),
                billingCycle,
                cyclePricing.getDurationDays(),
                basePrice,
                discountAmount,
                appliedVoucher == null ? null : appliedVoucher.voucher().getId(),
                appliedVoucher == null ? null : appliedVoucher.voucher().getCode(),
                effectivePrice
        );
    }

    public void recordVoucherRedemption(
            SubscriptionEntity subscription,
            PaymentTransactionEntity paymentTransaction
    ) {
        if (paymentTransaction == null || paymentTransaction.getVoucher() == null) {
            return;
        }
        if (paymentTransaction.getDiscountAmount() == null
                || paymentTransaction.getDiscountAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        if (voucherRedemptionRepository.existsByPaymentTransaction_Id(paymentTransaction.getId())) {
            return;
        }
        UserEntity user = paymentTransaction.getUser();
        if (user == null) {
            throw new UserNotFoundException();
        }

        VoucherRedemptionEntity redemption = new VoucherRedemptionEntity();
        redemption.setId(UUID.randomUUID());
        redemption.setVoucher(paymentTransaction.getVoucher());
        redemption.setUser(user);
        redemption.setSubscription(subscription);
        redemption.setPaymentTransaction(paymentTransaction);
        redemption.setRedeemedAt(OffsetDateTime.now());
        redemption.setAppliedAmount(paymentTransaction.getDiscountAmount());
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
                    ERROR_REGION_PRICING_INACTIVE,
                    MESSAGE_REGION_PRICING_UNAVAILABLE,
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        return new ResolvedPricingContext(region, effectiveCountryCode, regionPricing);
    }

    private BillingPricingResponse.PlanPricing buildPlanPricing(
            UserEntity user,
            ResolvedPricingContext context,
            PlanType planType
    ) {
        StudySnapProperties.PaidPlanPricing paidPlanPricing = context.regionPricing().resolvePlanPricing(planType);
        return new BillingPricingResponse.PlanPricing(
                planType,
                toCyclePricing(user, context, planType, BillingCycle.MONTHLY, paidPlanPricing.getMonthly()),
                toCyclePricing(user, context, planType, BillingCycle.YEARLY, paidPlanPricing.getYearly()),
                toCyclePricing(user, context, planType, BillingCycle.EXAM_CYCLE, paidPlanPricing.getExamCycle())
        );
    }

    private BillingPricingResponse.CyclePricing toCyclePricing(
            UserEntity user,
            ResolvedPricingContext context,
            PlanType planType,
            BillingCycle billingCycle,
            StudySnapProperties.BillingCyclePricing cyclePricing
    ) {
        if (cyclePricing == null || !cyclePricing.isActive()) {
            return new BillingPricingResponse.CyclePricing(null, null, null, false, false);
        }

        BigDecimal basePrice = normalizeAmount(cyclePricing.getAmount());
        Optional<AppliedVoucher> introVoucher = findBestEligibleVoucher(
                user,
                context.region(),
                context.regionPricing().getCurrency(),
                billingCycle,
                planType,
                basePrice,
                null
        );

        return new BillingPricingResponse.CyclePricing(
                basePrice,
                cyclePricing.getDurationDays(),
                introVoucher.map(AppliedVoucher::effectivePrice).orElse(null),
                introVoucher.isPresent(),
                basePrice.compareTo(BigDecimal.ZERO) > 0
        );
    }

    Optional<AppliedVoucher> findBestEligibleVoucher(
            UserEntity user,
            String region,
            String currency,
            BillingCycle billingCycle,
            PlanType planType,
            BigDecimal basePrice,
            String voucherCode
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        List<DiscountVoucherEntity> candidates = voucherCode == null || voucherCode.isBlank()
                ? discountVoucherRepository.findByIsActiveTrue().stream()
                .filter(voucher -> !voucher.isRequiresCode())
                .toList()
                : discountVoucherRepository.findByCodeIgnoreCase(voucherCode.trim()).stream().toList();

        return candidates.stream()
                .filter(voucher -> isEligible(voucher, user, region, currency, billingCycle, planType, now))
                .map(voucher -> applyVoucher(voucher, basePrice))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .max(Comparator.comparing(AppliedVoucher::discountAmount));
    }

    private boolean isEligible(
            DiscountVoucherEntity voucher,
            UserEntity user,
            String region,
            String currency,
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
        if (!voucher.getCurrency().equalsIgnoreCase(currency)) {
            return false;
        }
        if (!voucher.isNewSubscribersOnly()) {
            return true;
        }
        if (user == null) {
            return true;
        }
        boolean hasPriorPaidSubscription = subscriptionRepository.existsByUser_IdAndPlanTypeIn(user.getId(), PAID_PLANS);
        if (hasPriorPaidSubscription) {
            return false;
        }
        return !voucherRedemptionRepository.existsByVoucher_IdAndUser_Id(voucher.getId(), user.getId());
    }

    private Optional<AppliedVoucher> applyVoucher(
            DiscountVoucherEntity voucher,
            BigDecimal basePrice
    ) {
        BigDecimal normalizedBasePrice = normalizeAmount(basePrice);
        BigDecimal effectivePrice;
        if (voucher.getDiscountType() == VoucherDiscountType.OVERRIDE_PRICE) {
            effectivePrice = voucher.getDiscountValue();
        } else if (voucher.getDiscountType() == VoucherDiscountType.FIXED_AMOUNT) {
            effectivePrice = normalizedBasePrice.subtract(voucher.getDiscountValue());
        } else {
            BigDecimal percentage = voucher.getDiscountValue().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
            effectivePrice = normalizedBasePrice.multiply(BigDecimal.ONE.subtract(percentage));
        }
        effectivePrice = effectivePrice.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = normalizedBasePrice.subtract(effectivePrice)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        if (discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        return Optional.of(new AppliedVoucher(voucher, effectivePrice, discountAmount));
    }

    private StudySnapProperties.BillingCyclePricing resolveCyclePricing(
            StudySnapProperties.RegionPricing regionPricing,
            PlanType planType,
            BillingCycle billingCycle
    ) {
        PlanType normalizedPlanType = normalizeRequestedPlan(planType);
        StudySnapProperties.PaidPlanPricing paidPlanPricing = regionPricing.resolvePlanPricing(normalizedPlanType);
        StudySnapProperties.BillingCyclePricing cyclePricing = switch (billingCycle) {
            case YEARLY -> paidPlanPricing.getYearly();
            case EXAM_CYCLE -> paidPlanPricing.getExamCycle();
            case MONTHLY -> paidPlanPricing.getMonthly();
        };
        if (cyclePricing == null || !cyclePricing.isActive() || normalizeAmount(cyclePricing.getAmount()).compareTo(BigDecimal.ZERO) <= 0) {
            throw new AppException(
                    ERROR_CHECKOUT_BILLING_CYCLE_UNAVAILABLE,
                    MESSAGE_CHECKOUT_BILLING_CYCLE_UNAVAILABLE,
                    HttpStatus.BAD_REQUEST
            );
        }
        return cyclePricing;
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
                ERROR_REGION_PRICING_NOT_CONFIGURED,
                MESSAGE_REGION_PRICING_UNAVAILABLE,
                HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    private boolean matchesRegion(String regionScope, String region) {
        return "ANY".equalsIgnoreCase(regionScope) || region.equalsIgnoreCase(regionScope);
    }

    private boolean matchesCycle(VoucherBillingCycleScope scope, BillingCycle billingCycle) {
        return scope == VoucherBillingCycleScope.ANY
                || (scope == VoucherBillingCycleScope.MONTHLY && billingCycle == BillingCycle.MONTHLY)
                || (scope == VoucherBillingCycleScope.YEARLY && billingCycle == BillingCycle.YEARLY)
                || (scope == VoucherBillingCycleScope.EXAM_CYCLE && billingCycle == BillingCycle.EXAM_CYCLE);
    }

    private boolean matchesPlan(VoucherPlanScope scope, PlanType planType) {
        return scope == VoucherPlanScope.ANY
                || (scope == VoucherPlanScope.FREE && planType == PlanType.FREE)
                || (scope == VoucherPlanScope.PLUS && planType == PlanType.PLUS)
                || (scope == VoucherPlanScope.PRO && planType == PlanType.PRO);
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        BigDecimal normalized = amount == null ? BigDecimal.ZERO : amount;
        return normalized.setScale(2, RoundingMode.HALF_UP);
    }

    private PlanType normalizeRequestedPlan(PlanType requestedPlanType) {
        PlanType planType = requestedPlanType == null ? PlanType.PRO : requestedPlanType;
        if (!planType.isPaid()) {
            throw new AppException(
                    ERROR_CHECKOUT_PLAN_NOT_SUPPORTED,
                    MESSAGE_CHECKOUT_PLAN_NOT_SUPPORTED,
                    HttpStatus.BAD_REQUEST
            );
        }
        return planType;
    }

    public record CheckoutSelection(
            String region,
            String countryCode,
            String currency,
            PlanType planType,
            String planDisplayName,
            BillingCycle billingCycle,
            int accessDurationDays,
            BigDecimal basePrice,
            BigDecimal discountAmount,
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
