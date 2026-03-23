import { render, screen } from "@testing-library/react";
import PricingPage from "./page";

jest.mock("@/lib/api", () => ({
  getBillingPricing: jest.fn().mockResolvedValue({
    region: "PH",
    currency: "PHP",
    monthlyPrice: 249,
    yearlyPrice: 1999,
    introMonthlyPrice: 199,
    hasIntroPromo: true,
    introEligible: true,
  }),
}));

describe("PricingPage", () => {
  it("renders localized pricing and upgrade messaging", async () => {
    render(<PricingPage />);

    expect(screen.getByText("Study smarter. Pass exams faster.")).toBeInTheDocument();
    expect(
      screen.getByText("Turn your notes into summaries, quizzes, and reviewers in seconds."),
    ).toBeInTheDocument();
    expect(await screen.findByText("First month ₱199, then ₱249/month")).toBeInTheDocument();
    expect(screen.getByText("₱1,999/year (Save ₱989)")).toBeInTheDocument();
    expect(screen.queryByText("Included")).not.toBeInTheDocument();
    expect(screen.queryByText("Not included")).not.toBeInTheDocument();
    expect(screen.getAllByText("Challenge Quiz (Exam Mode)")).not.toHaveLength(0);
    expect(screen.getAllByText("Adaptive Practice")).not.toHaveLength(0);
    expect(screen.getAllByLabelText("Not included")).toHaveLength(3);
  });

  it("links hero and plan CTAs to signup and billing", async () => {
    render(<PricingPage />);

    expect((await screen.findAllByRole("link", { name: "Start Free" }))[0]).toHaveAttribute("href", "/auth");
    expect((await screen.findAllByRole("link", { name: "Upgrade to Premium" }))[0]).toHaveAttribute(
      "href",
      "/settings#plan-billing",
    );
  });
});
