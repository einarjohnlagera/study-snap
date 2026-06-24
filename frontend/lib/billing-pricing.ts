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
  if (billingCycle === "EXAM_CYCLE") {
    return planPricing.examCycle;
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
    if (billingCycle === "EXAM_CYCLE") {
      return "Exam pass pricing unavailable";
    }
    return billingCycle === "MONTHLY" ? "Monthly pricing unavailable" : "Yearly pricing unavailable";
  }

  if (billingCycle === "MONTHLY") {
    const monthlyAmount = formatBillingAmount(cyclePricing.amount, currency);
    if (cyclePricing.introEligible && cyclePricing.introAmount !== null) {
      const introAmount = formatBillingAmount(cyclePricing.introAmount, currency);
      return `${introAmount} for your first 1-month pass · ${monthlyAmount} after`;
    }
    return `${monthlyAmount} / 1 month`;
  }

  if (billingCycle === "EXAM_CYCLE") {
    return getExamCyclePriceLabel(pricing, planType);
  }

  const yearlyAmount = formatBillingAmount(cyclePricing.amount, currency);
  const yearlySavings = getYearlySavings(pricing, planType);
  if (yearlySavings > 0) {
    return `${yearlyAmount} / 1 year · save ${formatBillingAmount(yearlySavings, currency)}`;
  }
  return `${yearlyAmount} / 1 year`;
}

export function getExamCyclePriceLabel(
  pricing: BillingPricingResponse | null,
  planType: PaidPlanType,
) {
  const cyclePricing = resolveCyclePricing(pricing, planType, "EXAM_CYCLE");
  const currency = pricing?.currency ?? "PHP";
  if (!cyclePricing || !cyclePricing.available || cyclePricing.amount === null) {
    return "Exam pass pricing unavailable";
  }
  const durationDays = cyclePricing.durationDays ?? 90;
  return `${formatBillingAmount(cyclePricing.amount, currency)} / ${formatPassDuration(durationDays)}`;
}

// Express a pass duration in whole months for consistent labelling across surfaces
// (e.g. a 90-day exam pass reads as "3 months", a 30-day pass as "1 month").
export function formatPassDuration(durationDays: number): string {
  const months = Math.max(1, Math.round(durationDays / 30));
  return `${months} month${months === 1 ? "" : "s"}`;
}

// Percentage saved by a multi-cycle pass vs. buying the equivalent number of
// regular 1-month passes. Returns 0 when not actually cheaper (callers guard on > 0).
export function passSavingsPct(passAmount: number | null, monthlyAmount: number | null, cycles: number): number {
  if (!passAmount || !monthlyAmount || cycles <= 0) {
    return 0;
  }
  return Math.max(0, Math.round((1 - passAmount / (monthlyAmount * cycles)) * 100));
}
