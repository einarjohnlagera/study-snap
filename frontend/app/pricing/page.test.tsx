import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import PricingPage, { metadata } from "./page";
import { createPremiumCheckoutSession, trackAnalyticsEvent } from "@/lib/api";
import { redirectToCheckoutUrl } from "@/lib/checkout-redirect";

const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  usePathname: () => "/pricing",
  useRouter: () => ({
    push: pushMock,
  }),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: () => ({ id: "user-1", emailVerifiedAt: "2026-03-20T00:00:00Z" }),
  getCurrentPathWithQuery: () => `${window.location.pathname}${window.location.search}`,
  getSafeRedirectPath: (path: string | null | undefined) => (
    path && path.startsWith("/") && !path.startsWith("//") ? path : null
  ),
}));

jest.mock("@/lib/checkout-redirect", () => ({
  redirectToCheckoutUrl: jest.fn(),
}));

jest.mock("@/lib/api", () => ({
  createPremiumCheckoutSession: jest.fn().mockResolvedValue({
    checkoutUrl: "https://checkout.xendit.test/invoice_123",
  }),
  getBillingPricing: jest.fn().mockResolvedValue({
    region: "PH",
    currency: "PHP",
    plus: {
      planType: "PLUS",
      monthly: { amount: 179, durationDays: 30, introAmount: 149, introEligible: true, available: true },
      yearly: { amount: null, durationDays: null, introAmount: null, introEligible: false, available: false },
    },
    pro: {
      planType: "PRO",
      monthly: { amount: 249, durationDays: 30, introAmount: 199, introEligible: true, available: true },
      yearly: { amount: 1999, durationDays: 365, introAmount: null, introEligible: false, available: true },
    },
  }),
  isEmailNotVerifiedError: () => false,
  requestEmailVerification: jest.fn(),
  trackAnalyticsEvent: jest.fn(),
}));

describe("PricingPage", () => {
  beforeEach(() => {
    window.history.replaceState(null, "", "/pricing");
    pushMock.mockReset();
    (createPremiumCheckoutSession as jest.Mock).mockClear();
    (trackAnalyticsEvent as jest.Mock).mockClear();
    (redirectToCheckoutUrl as jest.Mock).mockReset();
  });

  it("renders the Free, Plus, and Pro pricing layout", async () => {
    render(<PricingPage />);

    expect(screen.getAllByAltText("NoteLib")).not.toHaveLength(0);
    expect(screen.getByText("Simple plans for everyday study and serious review.")).toBeInTheDocument();
    expect(
      screen.getByText("Start free for core features. Move to Plus for higher limits, or choose Pro for deeper practice and exam-ready tools."),
    ).toBeInTheDocument();
    expect(
      screen.getAllByText("Most users stay on Free while studying casually. Plus fits regular review, and Pro is built for serious exam prep.")[0],
    ).toBeInTheDocument();
    expect(await screen.findAllByText(/Intro offer:/i)).not.toHaveLength(0);
    expect(screen.getByRole("heading", { name: "Free for everyday study. Plus for regular review. Pro for deeper exam prep." })).toBeInTheDocument();
    expect(screen.getByText("Free covers the core note-to-study-pack workflow. Plus expands your monthly limits. Pro adds the highest limits and advanced practice tools.")).toBeInTheDocument();
    expect(screen.getByText("For getting started")).toBeInTheDocument();
    expect(screen.getByText("For regular study")).toBeInTheDocument();
    expect(screen.getByText("Best for exam prep")).toBeInTheDocument();
    expect(screen.getByText("Most popular")).toBeInTheDocument();
    expect(screen.getAllByText((_, element) => (element?.textContent ?? "").includes("₱249 after"))).not.toHaveLength(0);
    expect(screen.getAllByText((_, element) => (element?.textContent ?? "").includes("₱1,999 / 1 year"))).not.toHaveLength(0);
    expect(screen.getAllByText((_, element) => (element?.textContent ?? "").includes("₱179 after"))).not.toHaveLength(0);
    expect(screen.getAllByText((_, element) => (element?.textContent ?? "").includes("₱149"))).not.toHaveLength(0);
    expect(screen.getAllByText((_, element) => (element?.textContent ?? "").includes("₱199"))).not.toHaveLength(0);
    expect(screen.getAllByText("10 Study Packs / month")).not.toHaveLength(0);
    expect(screen.getByText("Keep building with 50 Study Packs each month")).toBeInTheDocument();
    expect(screen.getByText("Build a deep library with 100 Study Packs each month")).toBeInTheDocument();
    expect(screen.getAllByText("5 Quizzes / month")).not.toHaveLength(0);
    expect(screen.getByText("Practice more often with 25 quizzes each month")).toBeInTheDocument();
    expect(screen.getByText("Stay exam-ready with 50 quizzes each month")).toBeInTheDocument();
    expect(screen.getByText("2 exports / month")).toBeInTheDocument();
    expect(screen.getByText("Take 15 study resources offline each month")).toBeInTheDocument();
    expect(screen.getAllByText("Export every study resource you need")).not.toHaveLength(0);
    expect(screen.getByText("Teachers get unlimited quiz exports on Plus.")).toBeInTheDocument();
    expect(screen.getAllByText("Summary + Key Concepts")).not.toHaveLength(0);
    expect(screen.getAllByText("Higher note generation limits")).not.toHaveLength(0);
    expect(screen.getAllByText("Adaptive Practice")).not.toHaveLength(0);
    expect(screen.getAllByText("Difficulty selection")).not.toHaveLength(0);
    expect(screen.getAllByText("Board Exam Mode")).not.toHaveLength(0);
    expect(screen.getByRole("button", { name: "Get Plus" })).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Get Pro" })).not.toHaveLength(0);
    expect(screen.getByRole("button", { name: /^1 year — ₱1,999 \/ 1 year/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Get Plus Yearly/ })).not.toBeInTheDocument();
    expect(screen.getByText("🇵🇭 Philippines pricing (PHP)")).toBeInTheDocument();
    expect(screen.getByText("🌍 International pricing")).toBeInTheDocument();
    expect(screen.getByText("Plus: ₱179 / 1-month pass")).toBeInTheDocument();
    expect(screen.getByText("Pro: ₱249 / 1-month pass")).toBeInTheDocument();
    expect(screen.getByText("Pro 1-year pass: ₱1,999")).toBeInTheDocument();
    expect(screen.getByText("Pro: $4.99 / 1-month pass")).toBeInTheDocument();
    expect(screen.getByText("Pro 1-year pass: $39.99")).toBeInTheDocument();
    expect(screen.getByText("Prices are shown for Philippines (PHP) and international (USD) using backend pricing data when available.")).toBeInTheDocument();
    expect(screen.getByText("Plan comparison")).toBeInTheDocument();
    expect(screen.getByText("Best for")).toBeInTheDocument();
    expect(screen.getByText("Focused exam preparation and mastery practice")).toBeInTheDocument();
    expect(screen.getByText("How do I renew a pass?")).toBeInTheDocument();
    expect(screen.getByText("Can I get a refund?")).toBeInTheDocument();
    expect(
      screen.getByText("Free covers the core study loop. Plus expands your limits. Pro adds the full exam-prep toolkit."),
    ).toBeInTheDocument();
    expect(screen.getAllByLabelText("Not included")).not.toHaveLength(0);
  });

  it("links signup CTA and starts Pro checkout for authenticated users", async () => {
    render(<PricingPage />);

    expect((await screen.findAllByRole("link", { name: "Get Started Free" }))[0]).toHaveAttribute("href", "/signup");

    fireEvent.click((await screen.findAllByRole("button", { name: "Get Pro" }))[0]);

    await waitFor(() => {
      expect(createPremiumCheckoutSession).toHaveBeenCalledWith({ planType: "PRO", billingCycle: null, returnUrl: "/pricing" });
      expect(redirectToCheckoutUrl).toHaveBeenCalledWith("https://checkout.xendit.test/invoice_123");
      expect(trackAnalyticsEvent).toHaveBeenCalledWith({
        eventType: "UPGRADE_CLICKED",
        metadata: {
          source: "pricing_hero",
          feature: null,
          path: "/pricing",
          planType: "PRO",
          target: "xendit_checkout",
        },
      });
    });
  });

  it("starts yearly Pro checkout from the pricing card", async () => {
    render(<PricingPage />);

    fireEvent.click(await screen.findByRole("button", { name: /^1 year — ₱1,999 \/ 1 year/ }));

    await waitFor(() => {
      expect(createPremiumCheckoutSession).toHaveBeenCalledWith({ planType: "PRO", billingCycle: "YEARLY", returnUrl: "/pricing" });
      expect(redirectToCheckoutUrl).toHaveBeenCalledWith("https://checkout.xendit.test/invoice_123");
    });
  });

  it("exports pricing metadata with canonical and social preview fields", () => {
    expect(metadata).toMatchObject({
      title: "NoteLib Pricing — Free, Plus, and Pro Plans",
      description: "Simple plans for everyday study and serious review. Start free, move to Plus for higher limits, or choose Pro for exam prep.",
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
