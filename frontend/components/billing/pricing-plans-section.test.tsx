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

const basePricing = {
  region: "PH", currency: "PHP",
  plus: { planType: "PLUS", monthly: { amount: 99, durationDays: 30, introAmount: 79, introEligible: false, available: true }, yearly: { amount: null, durationDays: null, introAmount: null, introEligible: false, available: false }, examCycle: { amount: null, durationDays: null, introAmount: null, introEligible: false, available: false } },
  pro: { planType: "PRO", monthly: { amount: 149, durationDays: 30, introAmount: 119, introEligible: false, available: true }, yearly: { amount: null, durationDays: null, introAmount: null, introEligible: false, available: false }, examCycle: { amount: null, durationDays: null, introAmount: null, introEligible: false, available: false } },
};

describe("PricingPlansSection", () => {
  beforeEach(() => {
    useBillingPricingMock.mockReset();
  });

  it("keeps price slots empty while loading", () => {
    useBillingPricingMock.mockReturnValue({ billingPricing: null, pricingLoaded: false });
    const { container } = render(<PricingPlansSection />);
    expect(container.querySelectorAll(".animate-pulse")).toHaveLength(2);
    expect(screen.queryByText("Monthly pricing unavailable")).not.toBeInTheDocument();
  });

  it("does not show an intro after a successful ineligible response", () => {
    useBillingPricingMock.mockReturnValue({ billingPricing: basePricing, pricingLoaded: true });
    render(<PricingPlansSection />);
    expect(screen.getByText("₱99 / 1 month")).toBeInTheDocument();
    expect(screen.queryByText(/Intro offer:/)).not.toBeInTheDocument();
    expect(screen.queryByText("Regional Pricing")).not.toBeInTheDocument();
  });

  it("shows only an eligible backend intro", () => {
    useBillingPricingMock.mockReturnValue({ billingPricing: { ...basePricing, plus: { ...basePricing.plus, monthly: { ...basePricing.plus.monthly, introEligible: true } } }, pricingLoaded: true });
    render(<PricingPlansSection />);
    expect(screen.getByText("Intro offer: ₱79 on your first 1-month pass.")).toBeInTheDocument();
    expect(screen.queryByText("Intro offer: ₱119 on your first 1-month pass.")).not.toBeInTheDocument();
  });

  it("shows unavailable labels after a failed response", () => {
    useBillingPricingMock.mockReturnValue({ billingPricing: null, pricingLoaded: true });
    render(<PricingPlansSection />);
    expect(screen.getAllByText("Monthly pricing unavailable")).toHaveLength(2);
    expect(screen.queryByText(/Intro offer:/)).not.toBeInTheDocument();
  });

  it("hides a cycle whose response has no amount even if marked available", () => {
    useBillingPricingMock.mockReturnValue({ billingPricing: { ...basePricing, pro: { ...basePricing.pro, yearly: { ...basePricing.pro.yearly, available: true } } }, pricingLoaded: true });
    render(<PricingPlansSection />);
    expect(screen.queryByRole("button", { name: /^1 year/ })).not.toBeInTheDocument();
    expect(screen.queryByText("Yearly pricing unavailable")).not.toBeInTheDocument();
  });

  it("renders the Pro exam-cycle checkout option when live pricing marks it available", () => {
    useBillingPricingMock.mockReturnValue({
      pricingLoaded: true,
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
      pricingLoaded: true,
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
