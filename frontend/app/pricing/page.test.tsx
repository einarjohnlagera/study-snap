import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import PricingPage, { metadata } from "./page";

jest.mock("next/navigation", () => ({
  usePathname: () => "/pricing",
}));

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
  joinPremiumWaitlist: jest.fn().mockResolvedValue({
    message: "You're on the list! We'll notify you when Premium launches.",
  }),
  requestEmailVerification: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

describe("PricingPage", () => {
  it("renders the simplified Free vs Premium pricing layout", async () => {
    render(<PricingPage />);

    expect(screen.getAllByAltText("NoteLib")).not.toHaveLength(0);
    expect(screen.getByText("Simple plans for everyday study and serious review.")).toBeInTheDocument();
    expect(
      screen.getByText("Start free for core features. Upgrade when you need more quizzes, deeper practice, and higher limits."),
    ).toBeInTheDocument();
    expect(
      screen.getAllByText("Most users stay on Free while studying casually. Upgrade when your review gets serious.")[0],
    ).toBeInTheDocument();
    expect(await screen.findByText("First month ₱199, then ₱249/month")).toBeInTheDocument();
    expect(screen.getByText("₱1,999/year (Save ₱989)")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Free for everyday study. Premium for deeper exam prep." })).toBeInTheDocument();
    expect(screen.getByText("Free covers the full note-to-study-pack workflow. Premium adds higher limits and deeper practice tools.")).toBeInTheDocument();
    expect(screen.getByText("For everyday study")).toBeInTheDocument();
    expect(
      screen.getByText("Build notes, generate Study Packs, and review with quizzes without paying upfront."),
    ).toBeInTheDocument();
    expect(screen.getAllByText("10 Study Packs / month")).not.toHaveLength(0);
    expect(screen.getAllByText("5 Quizzes / month")).not.toHaveLength(0);
    expect(screen.getAllByText("Summary + Key Concepts")).not.toHaveLength(0);
    expect(screen.getAllByText("Weak Concepts tracking")).not.toHaveLength(0);
    expect(screen.getByText("Board Exam Mode (Premium)")).toBeInTheDocument();
    expect(screen.getByText("Adaptive Practice (Premium)")).toBeInTheDocument();
    expect(screen.getByText("Difficulty selection (Premium)")).toBeInTheDocument();
    expect(screen.getByText("For focused exam preparation")).toBeInTheDocument();
    expect(screen.getByText("10")).toBeInTheDocument();
    expect(screen.getAllByText("100")).not.toHaveLength(0);
    expect(screen.getByText("50 Quizzes / month")).toBeInTheDocument();
    expect(screen.getByText("30 Adaptive Practice sessions / month")).toBeInTheDocument();
    expect(screen.getAllByText("Difficulty selection")).not.toHaveLength(0);
    expect(screen.getAllByText("Board Exam Mode")).not.toHaveLength(0);
    expect(
      screen.getByText(
        "Premium pricing is shown now, but upgrades currently join the waitlist while checkout is still being prepared.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText("🇵🇭 Philippines pricing (PHP)")).toBeInTheDocument();
    expect(screen.getByText("🌍 International pricing")).toBeInTheDocument();
    expect(screen.getByText((_, element) => element?.textContent === "₱249/month")).toBeInTheDocument();
    expect(screen.getByText("₱2,499/year")).toBeInTheDocument();
    expect(screen.getByText((_, element) => element?.textContent === "$4.99/month")).toBeInTheDocument();
    expect(screen.getByText("$39.99/year")).toBeInTheDocument();
    expect(screen.getAllByText("Prices are shown for Philippines (PHP) and international (USD).")[0]).toBeInTheDocument();
    expect(screen.getByText("Plan comparison")).toBeInTheDocument();
    expect(
      screen.getByText("Free covers the core study loop. Premium expands limits and unlocks deeper quiz training."),
    ).toBeInTheDocument();
    expect(screen.getAllByLabelText("Not included")).toHaveLength(3);

    const comparisonTable = screen.getByRole("table");
    const pricingText = comparisonTable.textContent ?? "";
    expect(pricingText.indexOf("Study Packs / month")).toBeLessThan(pricingText.indexOf("Quizzes / month"));
    expect(pricingText.indexOf("Quizzes / month")).toBeLessThan(pricingText.indexOf("Summary + Key Concepts"));
    expect(pricingText.indexOf("Summary + Key Concepts")).toBeLessThan(pricingText.indexOf("Weak Concepts tracking"));
    expect(pricingText.indexOf("Weak Concepts tracking")).toBeLessThan(pricingText.indexOf("Adaptive Practice"));
    expect(pricingText.indexOf("Adaptive Practice")).toBeLessThan(pricingText.indexOf("Difficulty selection"));
    expect(pricingText.indexOf("Difficulty selection")).toBeLessThan(pricingText.indexOf("Board Exam Mode"));
    expect(pricingText).not.toContain("Free for limited time");
  });

  it("links signup CTA and opens the premium waitlist flow", async () => {
    render(<PricingPage />);

    expect((await screen.findAllByRole("link", { name: "Start for Free" }))[0]).toHaveAttribute("href", "/signup");

    fireEvent.click((await screen.findAllByRole("button", { name: "Upgrade to Premium" }))[0]);

    expect(await screen.findByText("Premium is coming soon")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Join Waitlist" }));
    await waitFor(() => {
      expect(screen.getByText("You're on the list! We'll notify you when Premium launches.")).toBeInTheDocument();
    });
  });

  it("exports pricing metadata with canonical and social preview fields", () => {
    expect(metadata).toMatchObject({
      title: "NoteLib Pricing — Free and Premium Plans",
      description: "Simple plans for everyday study and serious review. Start free, and upgrade when your review gets serious.",
      alternates: {
        canonical: "https://notelib.app/pricing",
      },
      openGraph: expect.objectContaining({
        type: "website",
        url: "https://notelib.app/pricing",
        siteName: "NoteLib",
        images: expect.arrayContaining([
          expect.objectContaining({ url: "https://notelib.app/og-image.png" }),
        ]),
      }),
      twitter: expect.objectContaining({
        card: "summary_large_image",
        images: ["https://notelib.app/og-image.png"],
      }),
    });
  });
});
