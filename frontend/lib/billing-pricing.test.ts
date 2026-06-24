import { formatPassDuration, getExamCyclePriceLabel, passSavingsPct, resolveCyclePricing } from "./billing-pricing";
import type { BillingPricingResponse } from "./api";

const pricing: BillingPricingResponse = {
  region: "PH",
  currency: "PHP",
  plus: {
    planType: "PLUS",
    monthly: { amount: 179, durationDays: 30, introAmount: 149, introEligible: true, available: true },
    yearly: { amount: null, durationDays: null, introAmount: null, introEligible: false, available: false },
    examCycle: { amount: null, durationDays: null, introAmount: null, introEligible: false, available: false },
  },
  pro: {
    planType: "PRO",
    monthly: { amount: 249, durationDays: 30, introAmount: 199, introEligible: true, available: true },
    yearly: { amount: 1999, durationDays: 365, introAmount: null, introEligible: false, available: true },
    examCycle: { amount: 599, durationDays: 90, introAmount: null, introEligible: false, available: true },
  },
};

describe("billing pricing helpers", () => {
  it("resolves EXAM_CYCLE pricing from the examCycle field", () => {
    expect(resolveCyclePricing(pricing, "PRO", "EXAM_CYCLE")).toEqual(pricing.pro.examCycle);
  });

  it("formats the exam cycle price with its access duration", () => {
    expect(getExamCyclePriceLabel(pricing, "PRO")).toBe("₱599 / 3 months");
  });

  it("expresses pass durations in whole months", () => {
    expect(formatPassDuration(30)).toBe("1 month");
    expect(formatPassDuration(90)).toBe("3 months");
    expect(formatPassDuration(365)).toBe("12 months");
  });

  it("computes pass savings vs. the equivalent regular monthly passes", () => {
    // 90-day pass ₱599 vs three ₱249 monthly passes (₱747) ≈ 20% off
    expect(passSavingsPct(599, 249, 3)).toBe(20);
    // 1-year pass ₱1,999 vs twelve ₱249 monthly passes (₱2,988) ≈ 33% off
    expect(passSavingsPct(1999, 249, 12)).toBe(33);
    // not cheaper / missing inputs → 0 (callers guard on > 0)
    expect(passSavingsPct(800, 249, 3)).toBe(0);
    expect(passSavingsPct(null, 249, 3)).toBe(0);
  });
});
