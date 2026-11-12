/** @jest-environment node */
import { renderToString } from "react-dom/server";
import { SimplePricingSection } from "./pricing-plans-section";

jest.mock("@/lib/api", () => ({ getBillingPricing: jest.fn() }));

test("server HTML contains price placeholders and no amount", () => {
  const html = renderToString(<SimplePricingSection />);
  expect(html).toContain("animate-pulse");
  expect(html).not.toMatch(/[₱£€$]\s*\d/);
  expect(html).not.toContain("See current prices");
});
