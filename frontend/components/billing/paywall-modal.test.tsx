import { render, screen } from "@testing-library/react";
import { PaywallModal } from "./paywall-modal";

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
  trackAnalyticsEvent: jest.fn(),
}));

jest.mock("next/navigation", () => ({
  usePathname: () => "/notes/note-1",
}));

describe("PaywallModal", () => {
  it("renders Challenge Quiz messaging for exam mode", async () => {
    render(
      <PaywallModal
        isOpen
        variant="challenge-quiz"
        onClose={jest.fn()}
        onUpgrade={jest.fn()}
      />,
    );

    expect(await screen.findByText("Unlock Exam Mode")).toBeInTheDocument();
    expect(
      screen.getByText(/Challenge Quiz simulates a real exam and helps you test your knowledge without seeing answers immediately/i),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Maybe Later" })).toBeInTheDocument();
    expect(await screen.findByText("First month ₱199, then ₱249/month")).toBeInTheDocument();
  });

  it("renders Adaptive Practice messaging for weak-topic review", async () => {
    render(
      <PaywallModal
        isOpen
        variant="adaptive-practice"
        onClose={jest.fn()}
        onUpgrade={jest.fn()}
      />,
    );

    expect(await screen.findByText("Focus on Your Weak Topics")).toBeInTheDocument();
    expect(
      screen.getByText(/Adaptive Practice creates quizzes based on the topics you got wrong/i),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Maybe Later" })).toBeInTheDocument();
  });

  it("renders Study Pack limit messaging with OK dismiss action", async () => {
    render(
      <PaywallModal
        isOpen
        variant="study-pack-limit"
        onClose={jest.fn()}
        onUpgrade={jest.fn()}
      />,
    );

    expect(await screen.findByText("You've reached your monthly limit")).toBeInTheDocument();
    expect(
      screen.getByText(/Free plan includes 5 Study Pack generations per month/i),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "OK" })).toBeInTheDocument();
  });
});
