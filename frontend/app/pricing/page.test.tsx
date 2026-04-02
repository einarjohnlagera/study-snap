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
  it("renders localized pricing and upgrade messaging", async () => {
    render(<PricingPage />);

    expect(screen.getByText("Study smarter. Pass exams faster.")).toBeInTheDocument();
    expect(
      screen.getByText("Turn your notes into reviewers, practice questions, and better exam prep."),
    ).toBeInTheDocument();
    expect(await screen.findByText("First month ₱199, then ₱249/month")).toBeInTheDocument();
    expect(screen.getByText("₱1,999/year (Save ₱989)")).toBeInTheDocument();
    expect(screen.queryByText("Included")).not.toBeInTheDocument();
    expect(screen.queryByText("Not included")).not.toBeInTheDocument();
    expect(screen.getByText("Start studying for free.")).toBeInTheDocument();
    expect(screen.getByText("Unlock adaptive practice and deeper quiz training.")).toBeInTheDocument();
    expect(screen.getByText("Most students upgrade during exam weeks.")).toBeInTheDocument();
    expect(screen.getByText("10 Study Packs per month")).toBeInTheDocument();
    expect(screen.getByText("5 Challenge Quizzes per month")).toBeInTheDocument();
    expect(screen.getAllByText("Weak Concepts Tracking")).not.toHaveLength(0);
    expect(screen.getAllByText("Quick Review")).not.toHaveLength(0);
    expect(screen.getByText("Everything in Free")).toBeInTheDocument();
    expect(screen.getByText("More Study Packs and Quizzes")).toBeInTheDocument();
    expect(screen.getByText("Adaptive Practice for weak topics")).toBeInTheDocument();
    expect(screen.getAllByText("Choose Quiz Difficulty")).not.toHaveLength(0);
    expect(screen.getAllByText("Future premium features")).not.toHaveLength(0);
    expect(screen.getByText("More practice. Better results.")).toBeInTheDocument();
    expect(screen.getAllByLabelText("Not included")).toHaveLength(3);
    expect(
      screen.getByText("Free helps you study consistently. Premium unlocks deeper practice and higher limits when exams get serious."),
    ).toBeInTheDocument();
    expect(screen.getByText("Included in Free")).toBeInTheDocument();
    expect(screen.getByText("Monthly Limits")).toBeInTheDocument();
    expect(screen.getByText("Premium Features")).toBeInTheDocument();
    expect(screen.getByText("10")).toBeInTheDocument();
    expect(screen.getByText("50")).toBeInTheDocument();
    expect(screen.getByText("30")).toBeInTheDocument();
    expect(screen.queryByText("Public Library Access")).not.toBeInTheDocument();
    expect(screen.queryByText("File Uploads (PDF, DOCX, TXT)")).not.toBeInTheDocument();
    expect(screen.queryByText("Image to Text (OCR)")).not.toBeInTheDocument();

    const comparisonTable = screen.getByRole("table");
    const pricingText = comparisonTable.textContent ?? "";
    expect(pricingText.indexOf("Quick Review")).toBeLessThan(pricingText.indexOf("Weak Concepts Tracking"));
    expect(pricingText.indexOf("Weak Concepts Tracking")).toBeLessThan(pricingText.indexOf("AI Study Packs / month"));
    expect(pricingText.indexOf("AI Study Packs / month")).toBeLessThan(pricingText.indexOf("Challenge Quizzes / month"));
    expect(pricingText.indexOf("Challenge Quizzes / month")).toBeLessThan(pricingText.indexOf("Adaptive Practice"));
    expect(pricingText.indexOf("Adaptive Practice")).toBeLessThan(pricingText.indexOf("Choose Quiz Difficulty"));
    expect(pricingText.indexOf("Choose Quiz Difficulty")).toBeLessThan(pricingText.indexOf("Future Premium Features"));
  });

  it("links signup CTA and opens the premium waitlist flow", async () => {
    render(<PricingPage />);

    expect((await screen.findAllByRole("link", { name: "Start for Free" }))[0]).toHaveAttribute("href", "/auth");

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
      description:
        "Choose between Free and Premium plans for turning notes into reviewers, practice questions, and better exam prep.",
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
