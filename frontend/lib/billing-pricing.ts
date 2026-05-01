"use client";

import type {
  BillingCycle,
  BillingPlanPricingResponse,
  BillingPricingCycleResponse,
  BillingPricingResponse,
  PaidPlanType,
} from "@/lib/api";

function buildFormatter(currency: string) {
  return new Intl.NumberFormat(undefined, {
    style: "currency",
    currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  });
}

export function formatBillingAmount(amount: number, currency: string) {
  return buildFormatter(currency).format(amount);
}

export function resolvePlanPricing(
  pricing: BillingPricingResponse | null,
  planType: PaidPlanType,
): BillingPlanPricingResponse | null {
  if (!pricing) {
    return null;
  }
  return planType === "PLUS" ? pricing.plus : pricing.pro;
}

export function resolveCyclePricing(
  pricing: BillingPricingResponse | null,
  planType: PaidPlanType,
  billingCycle: BillingCycle,
): BillingPricingCycleResponse | null {
  const planPricing = resolvePlanPricing(pricing, planType);
  if (!planPricing) {
    return null;
  }
  return billingCycle === "YEARLY" ? planPricing.yearly : planPricing.monthly;
}

export function getYearlySavings(
  pricing: BillingPricingResponse | null,
  planType: PaidPlanType,
) {
  const planPricing = resolvePlanPricing(pricing, planType);
  if (!planPricing || !planPricing.monthly.available || !planPricing.yearly.available) {
    return 0;
  }
  const monthlyAmount = planPricing.monthly.amount ?? 0;
  const yearlyAmount = planPricing.yearly.amount ?? 0;
  const savings = monthlyAmount * 12 - yearlyAmount;
  return Math.max(savings, 0);
}

export function getBillingCyclePriceLabel(
  pricing: BillingPricingResponse | null,
  planType: PaidPlanType,
  billingCycle: BillingCycle,
) {
  const cyclePricing = resolveCyclePricing(pricing, planType, billingCycle);
  const currency = pricing?.currency ?? "PHP";
  if (!cyclePricing || !cyclePricing.available || cyclePricing.amount === null) {
    return billingCycle === "MONTHLY" ? "Monthly pricing unavailable" : "Yearly pricing unavailable";
  }

  if (billingCycle === "MONTHLY") {
    const monthlyAmount = formatBillingAmount(cyclePricing.amount, currency);
    if (cyclePricing.introEligible && cyclePricing.introAmount !== null) {
      const introAmount = formatBillingAmount(cyclePricing.introAmount, currency);
      return `${introAmount} first month, then ${monthlyAmount}/month`;
    }
    return `${monthlyAmount}/month`;
  }

  const yearlyAmount = formatBillingAmount(cyclePricing.amount, currency);
  const yearlySavings = getYearlySavings(pricing, planType);
  if (yearlySavings > 0) {
    return `${yearlyAmount}/year (Save ${formatBillingAmount(yearlySavings, currency)})`;
  }
  return `${yearlyAmount}/year`;
}
