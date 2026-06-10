import { getExamCyclePriceLabel, resolveCyclePricing } from "./billing-pricing";
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
    expect(getExamCyclePriceLabel(pricing, "PRO")).toBe("₱599 for 90 days");
  });
});
