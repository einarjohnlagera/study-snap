import { render, screen } from "@testing-library/react";
import { PricingPlansSection } from "./pricing-plans-section";
import { useBillingPricing } from "@/hooks/use-billing-pricing";
import { PASS_NO_AUTO_CHARGE_FOOTER } from "@/src/config/plans";

jest.mock("@/hooks/use-billing-pricing", () => ({
  useBillingPricing: jest.fn(),
}));

jest.mock("@/components/billing/premium-upgrade-button", () => ({
  PremiumUpgradeButton: ({ label, source }: { label: string; source: string }) => (
    <button type="button" data-source={source}>{label}</button>
  ),
}));

const useBillingPricingMock = useBillingPricing as jest.Mock;

describe("PricingPlansSection", () => {
  beforeEach(() => {
    useBillingPricingMock.mockReset();
  });

  it("renders the Pro exam-cycle checkout option when live pricing marks it available", () => {
    useBillingPricingMock.mockReturnValue({
      billingPricing: {
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
      },
    });

    render(<PricingPlansSection />);

    expect(screen.getByText("₱599 / 3 months")).toBeInTheDocument();
    expect(screen.getByText("Prices shown for your detected region (Philippines).")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Get Pro — ₱599 / 3 months" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Get Plus" })).toHaveAttribute("data-source", "pricing_plans_section_plus_monthly");
    expect(screen.getByRole("button", { name: "Get Pro — ₱599 / 3 months" })).toHaveAttribute("data-source", "pricing_plans_section_pro_exam_cycle");
    expect(screen.getByRole("button", { name: /^1 month/ })).toHaveAttribute("data-source", "pricing_plans_section_pro_monthly");
    expect(screen.getByRole("button", { name: /^1 year/ })).toHaveAttribute("data-source", "pricing_plans_section_pro_yearly");
    expect(screen.getAllByText(PASS_NO_AUTO_CHARGE_FOOTER)).toHaveLength(2);
  });

  it("does not render the exam-cycle checkout option for Plus or unavailable Pro pricing", () => {
    useBillingPricingMock.mockReturnValue({
      billingPricing: {
        region: "US",
        currency: "USD",
        plus: {
          planType: "PLUS",
          monthly: { amount: 3.99, durationDays: 30, introAmount: null, introEligible: false, available: true },
          yearly: { amount: null, durationDays: null, introAmount: null, introEligible: false, available: false },
          examCycle: { amount: null, durationDays: null, introAmount: null, introEligible: false, available: false },
        },
        pro: {
          planType: "PRO",
          monthly: { amount: 4.99, durationDays: 30, introAmount: null, introEligible: false, available: true },
          yearly: { amount: 39.99, durationDays: 365, introAmount: null, introEligible: false, available: true },
          examCycle: { amount: null, durationDays: null, introAmount: null, introEligible: false, available: false },
        },
      },
    });

    render(<PricingPlansSection />);

    expect(screen.queryByRole("button", { name: /\/ 3 months/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/3 months/)).not.toBeInTheDocument();
    expect(screen.getByText("Prices shown for your detected region (all other regions).")).toBeInTheDocument();
  });
});
