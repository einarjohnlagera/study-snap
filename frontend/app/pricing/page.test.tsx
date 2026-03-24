import { render, screen } from "@testing-library/react";
import PricingPage, { metadata } from "./page";

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
