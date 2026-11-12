import { render, screen } from "@testing-library/react";
import { PricingPlansSection } from "./pricing-plans-section";
import { getBillingPricing } from "@/lib/api";
import { PASS_NO_AUTO_CHARGE_FOOTER } from "@/src/config/plans";

jest.mock("@/lib/api", () => ({ getBillingPricing: jest.fn() }));

jest.mock("@/components/billing/premium-upgrade-button", () => ({
  PremiumUpgradeButton: ({ label, source }: { label: string; source: string }) => (
    <button type="button" data-source={source}>{label}</button>
  ),
}));

const getBillingPricingMock = getBillingPricing as jest.Mock;

const basePricing = {
  region: "PH", currency: "PHP",
  plus: { planType: "PLUS", monthly: { amount: 99, durationDays: 30, introAmount: 79, introEligible: false, available: true }, yearly: { amount: null, durationDays: null, introAmount: null, introEligible: false, available: false }, examCycle: { amount: null, durationDays: null, introAmount: null, introEligible: false, available: false } },
  pro: { planType: "PRO", monthly: { amount: 149, durationDays: 30, introAmount: 119, introEligible: false, available: true }, yearly: { amount: null, durationDays: null, introAmount: null, introEligible: false, available: false }, examCycle: { amount: null, durationDays: null, introAmount: null, introEligible: false, available: false } },
};

describe("PricingPlansSection", () => {
  beforeEach(() => {
    getBillingPricingMock.mockReset();
  });

  it("keeps price slots empty while loading", () => {
    getBillingPricingMock.mockReturnValue(new Promise(() => {}));
    const { container } = render(<PricingPlansSection />);
    expect(container.querySelectorAll(".animate-pulse")).toHaveLength(2);
    expect(screen.queryByText("Monthly pricing unavailable")).not.toBeInTheDocument();
  });

  it("does not show an intro after a successful ineligible response", async () => {
    getBillingPricingMock.mockResolvedValue(basePricing);
    render(<PricingPlansSection />);
    expect(await screen.findByText("₱99 / 1 month")).toBeInTheDocument();
    expect(screen.queryByText(/Intro offer:/)).not.toBeInTheDocument();
    expect(screen.queryByText("Regional Pricing")).not.toBeInTheDocument();
  });

  it("shows only an eligible backend intro", async () => {
    getBillingPricingMock.mockResolvedValue({ ...basePricing, plus: { ...basePricing.plus, monthly: { ...basePricing.plus.monthly, introEligible: true } } });
    render(<PricingPlansSection />);
    expect(await screen.findByText("Intro offer: ₱79 on your first 1-month pass.")).toBeInTheDocument();
    expect(screen.queryByText("Intro offer: ₱119 on your first 1-month pass.")).not.toBeInTheDocument();
  });

  it("shows unavailable labels after a failed response", async () => {
    getBillingPricingMock.mockRejectedValue(new Error("pricing unavailable"));
    render(<PricingPlansSection />);
    expect(await screen.findAllByText("Monthly pricing unavailable")).toHaveLength(2);
    expect(screen.queryByText(/Intro offer:/)).not.toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/[₱$£€]\s*\d/);
  });

  it("hides a cycle whose response has no amount even if marked available", async () => {
    getBillingPricingMock.mockResolvedValue({ ...basePricing, pro: { ...basePricing.pro, yearly: { ...basePricing.pro.yearly, available: true } } });
    render(<PricingPlansSection />);
    expect(await screen.findByText("₱99 / 1 month")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^1 year/ })).not.toBeInTheDocument();
    expect(screen.queryByText("Yearly pricing unavailable")).not.toBeInTheDocument();
  });

  it("renders the Pro exam-cycle checkout option when live pricing marks it available", async () => {
    getBillingPricingMock.mockResolvedValue({
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
    });

    render(<PricingPlansSection />);

    expect(await screen.findByText("₱599 / 3 months")).toBeInTheDocument();
    expect(screen.getByText("Prices shown for your detected region (Philippines).")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Get Pro — ₱599 / 3 months" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Get Plus" })).toHaveAttribute("data-source", "pricing_plans_section_plus_monthly");
    expect(screen.getByRole("button", { name: "Get Pro — ₱599 / 3 months" })).toHaveAttribute("data-source", "pricing_plans_section_pro_exam_cycle");
    expect(screen.getByRole("button", { name: /^1 month/ })).toHaveAttribute("data-source", "pricing_plans_section_pro_monthly");
    expect(screen.getByRole("button", { name: /^1 year/ })).toHaveAttribute("data-source", "pricing_plans_section_pro_yearly");
    expect(screen.getAllByText(PASS_NO_AUTO_CHARGE_FOOTER)).toHaveLength(2);
  });

  it("does not render the exam-cycle checkout option for Plus or unavailable Pro pricing", async () => {
    getBillingPricingMock.mockResolvedValue({
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
    });

    render(<PricingPlansSection />);

    expect(await screen.findByRole("button", { name: /^1 year/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /\/ 3 months/ })).not.toBeInTheDocument();
    expect(screen.queryByText(/3 months/)).not.toBeInTheDocument();
    expect(screen.getByText("Prices shown for your detected region (all other regions).")).toBeInTheDocument();
  });
});

describe("PricingPlansSection with the real billing-pricing hook", () => {
  beforeEach(() => getBillingPricingMock.mockReset());

  it("shows skeleton price slots while the pricing request is pending", () => {
    getBillingPricingMock.mockReturnValue(new Promise(() => {}));
    const { container } = render(<PricingPlansSection />);
    expect(container.querySelectorAll('[aria-hidden="true"].animate-pulse')).toHaveLength(2);
    expect(document.body.textContent).not.toMatch(/[₱$£€]\s*\d/);
  });

  it("shows backend PH amounts and the eligible intro after pricing resolves", async () => {
    getBillingPricingMock.mockResolvedValue({
      ...basePricing,
      plus: { ...basePricing.plus, monthly: { ...basePricing.plus.monthly, introEligible: true } },
    });
    render(<PricingPlansSection />);
    expect(await screen.findByText("₱79 for your first 1-month pass · ₱99 after")).toBeInTheDocument();
    expect(screen.getByText("₱149 / 1 month")).toBeInTheDocument();
    expect(screen.getByText("Intro offer: ₱79 on your first 1-month pass.")).toBeInTheDocument();
  });

  it("shows unavailable labels without an invented amount after pricing rejects", async () => {
    getBillingPricingMock.mockRejectedValue(new Error("pricing unavailable"));
    render(<PricingPlansSection />);
    expect(await screen.findAllByText("Monthly pricing unavailable")).toHaveLength(2);
    expect(document.body.textContent).not.toMatch(/[₱$£€]\s*\d/);
  });
});
