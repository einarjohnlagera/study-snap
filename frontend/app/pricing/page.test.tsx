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
  trackAnalyticsEvent: jest.fn(),
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
    expect(screen.getByText("Start studying for free.")).toBeInTheDocument();
    expect(screen.getByText("Unlock adaptive practice and advanced quizzes.")).toBeInTheDocument();
    expect(screen.getByText("10 AI Study Packs / month")).toBeInTheDocument();
    expect(screen.getByText("5 Challenge Quizzes / month")).toBeInTheDocument();
    expect(screen.getAllByText("Weak Concepts Insights")).not.toHaveLength(0);
    expect(screen.getAllByText("File Uploads (PDF, DOCX, TXT)")).not.toHaveLength(0);
    expect(screen.getByText("Image to Text (OCR) - Limited")).toBeInTheDocument();
    expect(screen.getByText("Higher OCR Limits")).toBeInTheDocument();
    expect(screen.getAllByText("Choose Quiz Difficulty")).not.toHaveLength(0);
    expect(screen.getByText("More practice. Better results.")).toBeInTheDocument();
    expect(screen.getAllByLabelText("Not included")).toHaveLength(3);
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
      title: "NoteLib Pricing – Upgrade to Premium Study Tools",
      description: "Unlock Challenge Quiz, Adaptive Practice, and higher monthly limits with NoteLib Premium.",
      alternates: {
        canonical: "https://www.notelib.app/pricing",
      },
      openGraph: expect.objectContaining({
        type: "website",
        url: "https://www.notelib.app/pricing",
        siteName: "NoteLib",
        images: expect.arrayContaining([
          expect.objectContaining({ url: "https://www.notelib.app/og-image.png" }),
        ]),
      }),
      twitter: expect.objectContaining({
        card: "summary_large_image",
        images: ["https://www.notelib.app/og-image.png"],
      }),
    });
  });
});
