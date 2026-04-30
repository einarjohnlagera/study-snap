import { render, screen } from "@testing-library/react";
import BillingFailedPage from "./page";

describe("BillingFailedPage", () => {
  it("uses the returnUrl CTA when one is provided", async () => {
    render(await BillingFailedPage({
      searchParams: Promise.resolve({ returnUrl: "/notes/new?restoreDraft=1" }),
    }));

    expect(screen.getByRole("link", { name: "Try Again" })).toHaveAttribute("href", "/notes/new?restoreDraft=1");
    expect(screen.getByRole("link", { name: "Go to Dashboard" })).toHaveAttribute("href", "/dashboard");
  });

  it("falls back to pricing when returnUrl is missing", async () => {
    render(await BillingFailedPage({}));

    expect(screen.getByRole("link", { name: "Back to Pricing" })).toHaveAttribute("href", "/pricing");
  });
});
