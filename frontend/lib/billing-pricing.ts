"use client";

import type { BillingCycle, BillingPricingResponse } from "@/lib/api";

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

export function getYearlySavings(pricing: BillingPricingResponse) {
  const savings = pricing.monthlyPrice * 12 - pricing.yearlyPrice;
  return savings > 0 ? savings : 0;
}

export function getBillingCyclePriceLabel(
  pricing: BillingPricingResponse | null,
  billingCycle: BillingCycle,
) {
  if (!pricing) {
    return billingCycle === "MONTHLY" ? "Monthly pricing unavailable" : "Yearly pricing unavailable";
  }

  if (billingCycle === "MONTHLY") {
    const monthlyAmount = formatBillingAmount(pricing.monthlyPrice, pricing.currency);
    if (pricing.introEligible && pricing.introMonthlyPrice !== null) {
      const introAmount = formatBillingAmount(pricing.introMonthlyPrice, pricing.currency);
      return `First month ${introAmount}, then ${monthlyAmount}/month`;
    }
    return `${monthlyAmount}/month`;
  }

  const yearlyAmount = formatBillingAmount(pricing.yearlyPrice, pricing.currency);
  const yearlySavings = getYearlySavings(pricing);
  if (yearlySavings > 0) {
    return `${yearlyAmount}/year (Save ${formatBillingAmount(yearlySavings, pricing.currency)})`;
  }
  return `${yearlyAmount}/year`;
}
