import { render, screen } from "@testing-library/react";
import BillingSuccessPage from "./page";

describe("BillingSuccessPage", () => {
  it("uses the returnUrl CTA when one is provided", async () => {
    render(await BillingSuccessPage({
      searchParams: Promise.resolve({ returnUrl: "/notes/note-1/edit" }),
    }));

    expect(screen.getByRole("link", { name: "Continue where you left off" })).toHaveAttribute("href", "/notes/note-1/edit");
    expect(screen.getByRole("link", { name: "Go to Dashboard" })).toHaveAttribute("href", "/dashboard");
  });

  it("falls back to the dashboard CTA when returnUrl is missing", async () => {
    render(await BillingSuccessPage({}));

    expect(screen.getByRole("link", { name: "Go to Dashboard" })).toHaveAttribute("href", "/dashboard");
  });
});
