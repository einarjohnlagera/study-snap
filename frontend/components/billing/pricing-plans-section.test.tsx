import { render, screen } from "@testing-library/react";
import { PricingPlansSection } from "./pricing-plans-section";
import { useBillingPricing } from "@/hooks/use-billing-pricing";

jest.mock("@/hooks/use-billing-pricing", () => ({
  useBillingPricing: jest.fn(),
}));

jest.mock("@/components/billing/premium-upgrade-button", () => ({
  PremiumUpgradeButton: ({ label }: { label: string }) => <button type="button">{label}</button>,
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

    render(<PricingPlansSection showHeading={false} />);

    expect(screen.getByText("₱599 for 90 days")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Go Pro — 90-Day Exam Pass" })).toBeInTheDocument();
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

    render(<PricingPlansSection showHeading={false} />);

    expect(screen.queryByRole("button", { name: "Go Pro — 90-Day Exam Pass" })).not.toBeInTheDocument();
    expect(screen.queryByText(/for 90 days/)).not.toBeInTheDocument();
  });
});
