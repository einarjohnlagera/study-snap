import { render, screen } from "@testing-library/react";
import { SimplePricingSection } from "./pricing-plans-section";
import { getBillingPricing } from "@/lib/api";
import { PASS_MODEL_TAGLINE } from "@/src/config/plans";

jest.mock("@/lib/api", () => ({ getBillingPricing: jest.fn() }));

const cycle = (amount: number | null, introAmount: number | null = null, introEligible = false, available = true) => ({
  amount, introAmount, introEligible, available, durationDays: 30,
});
const pricing = {
  region: "PH", currency: "PHP",
  plus: { planType: "PLUS", monthly: cycle(99), yearly: cycle(null, null, false, false), examCycle: cycle(null, null, false, false) },
  pro: { planType: "PRO", monthly: cycle(149), yearly: cycle(null, null, false, false), examCycle: cycle(null, null, false, false) },
};

beforeEach(() => (getBillingPricing as jest.Mock).mockReset());

test("loading state shows one price shape per plan and keeps the pass tagline", () => {
  (getBillingPricing as jest.Mock).mockReturnValue(new Promise(() => {}));
  const { container } = render(<SimplePricingSection />);
  expect(container.querySelectorAll('[aria-hidden="true"].animate-pulse')).toHaveLength(2);
  expect(screen.getAllByText(PASS_MODEL_TAGLINE)).toHaveLength(2);
  expect(screen.queryByText(/₱\d|£\d/)).not.toBeInTheDocument();
});

test("shows backend PH monthly prices without a promotional label", async () => {
  (getBillingPricing as jest.Mock).mockResolvedValue(pricing);
  render(<SimplePricingSection />);
  expect(await screen.findByText("₱99 / 1 month")).toBeInTheDocument();
  expect(screen.getByText("₱149 / 1 month")).toBeInTheDocument();
  expect(screen.queryByText(/intro|first 1-month pass/i)).not.toBeInTheDocument();
});

test("shows backend PH base prices with no intro, even when eligible", async () => {
  (getBillingPricing as jest.Mock).mockResolvedValue({
    ...pricing, plus: { ...pricing.plus, monthly: cycle(99, 79, true) },
    pro: { ...pricing.pro, monthly: cycle(149, 119, true) },
  });
  render(<SimplePricingSection />);
  expect(await screen.findByText("₱99 / 1 month")).toBeInTheDocument();
  expect(screen.getByText("₱149 / 1 month")).toBeInTheDocument();
  expect(screen.queryByText(/intro|first 1-month pass/i)).not.toBeInTheDocument();
});

test("uses a non-PH response currency", async () => {
  (getBillingPricing as jest.Mock).mockResolvedValue({ ...pricing, region: "GB", currency: "GBP", plus: { ...pricing.plus, monthly: cycle(3.5) } });
  render(<SimplePricingSection />);
  const price = await screen.findByText(/3[.,]5(?:0)?\s*\/\s*1 month/);
  expect(price.textContent).toMatch(/£|GBP/);
});

test("failed or unusable prices link to current prices without an amount", async () => {
  (getBillingPricing as jest.Mock).mockRejectedValue(new Error("offline"));
  render(<SimplePricingSection />);
  const links = await screen.findAllByRole("link", { name: "See current prices" });
  expect(links).toHaveLength(2);
  links.forEach((link) => expect(link).toHaveAttribute("href", "/pricing"));
  expect(screen.queryByText(/₱\d/)).not.toBeInTheDocument();
});

test("an unavailable monthly cycle never becomes zero", async () => {
  (getBillingPricing as jest.Mock).mockResolvedValue({ ...pricing, plus: { ...pricing.plus, monthly: cycle(null, null, false, false) } });
  render(<SimplePricingSection />);
  expect(await screen.findByRole("link", { name: "See current prices" })).toHaveAttribute("href", "/pricing");
  expect(screen.queryByText("₱0 / 1 month")).not.toBeInTheDocument();
});
