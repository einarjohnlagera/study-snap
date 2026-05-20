import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PaywallModal } from "./paywall-modal";
import {
  createPremiumCheckoutSession,
  getBillingPricing,
} from "@/lib/api";
import { redirectToCheckoutUrl } from "@/lib/checkout-redirect";
import { loadPendingPaywallUpgradeContext } from "@/lib/paywall-upgrade-context";

const pushMock = jest.fn();
const requestEmailVerificationMock = jest.fn();
const getAuthUserMock = jest.fn();

jest.mock("@/lib/api", () => ({
  createPremiumCheckoutSession: jest.fn().mockResolvedValue({
    checkoutUrl: "https://checkout.xendit.test/invoice_123",
  }),
  getBillingPricing: jest.fn(),
  isEmailNotVerifiedError: (error: unknown) => error instanceof Error && error.message === "EMAIL_VERIFICATION_REQUIRED",
  trackAnalyticsEvent: jest.fn(),
  requestEmailVerification: (...args: unknown[]) => requestEmailVerificationMock(...args),
}));

jest.mock("next/navigation", () => ({
  usePathname: () => "/notes/note-1",
  useRouter: () => ({
    push: pushMock,
  }),
}));

jest.mock("@/lib/auth", () => ({
  getAuthUser: () => getAuthUserMock(),
  getCurrentPathWithQuery: () => `${window.location.pathname}${window.location.search}`,
  getSafeRedirectPath: (path: string | null | undefined) => (
    path && path.startsWith("/") && !path.startsWith("//") ? path : null
  ),
}));

jest.mock("@/lib/checkout-redirect", () => ({
  redirectToCheckoutUrl: jest.fn(),
}));

const billingPricingFixture = {
  region: "PH",
  currency: "PHP",
  plus: {
    planType: "PLUS" as const,
    monthly: {
      amount: 179,
      durationDays: 30,
      introAmount: 149,
      introEligible: true,
      available: true,
    },
    yearly: {
      amount: 1790,
      durationDays: 365,
      introAmount: null,
      introEligible: false,
      available: false,
    },
  },
  pro: {
    planType: "PRO" as const,
    monthly: {
      amount: 249,
      durationDays: 30,
      introAmount: 199,
      introEligible: true,
      available: true,
    },
    yearly: {
      amount: 2490,
      durationDays: 365,
      introAmount: null,
      introEligible: false,
      available: false,
    },
  },
};

describe("PaywallModal", () => {
  beforeEach(() => {
    window.history.replaceState(null, "", "/notes/note-1");
    pushMock.mockReset();
    requestEmailVerificationMock.mockReset();
    getAuthUserMock.mockReset();
    (redirectToCheckoutUrl as jest.Mock).mockReset();
    (createPremiumCheckoutSession as jest.Mock).mockReset();
    (getBillingPricing as jest.Mock).mockReset();
    (createPremiumCheckoutSession as jest.Mock).mockResolvedValue({
      checkoutUrl: "https://checkout.xendit.test/invoice_123",
    });
    (getBillingPricing as jest.Mock).mockResolvedValue(billingPricingFixture);
    getAuthUserMock.mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      profileType: "STUDENT",
    });
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it("renders context-aware adaptive practice copy with Plus and Pro plan cards", async () => {
    render(
      <PaywallModal
        isOpen
        context={{ type: "ADAPTIVE_PRACTICE_LOCKED" }}
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    expect(await screen.findByText("Unlock Adaptive Practice")).toBeInTheDocument();
    expect(screen.getByText("Train on your weak concepts and improve faster with targeted quizzes.")).toBeInTheDocument();
    expect(screen.getByText("Plus")).toBeInTheDocument();
    expect(screen.getByText("Pro")).toBeInTheDocument();
    expect(screen.getByText("Most popular")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Continue with Pro" })).toBeInTheDocument();
    expect(screen.getByText("Access activates immediately after payment")).toBeInTheDocument();
    expect(screen.getByText("No automatic charges. You control renewals.")).toBeInTheDocument();
  });

  it("saves the paywall upgrade context and starts checkout for Pro", async () => {
    render(
      <PaywallModal
        isOpen
        context={{ type: "GENERATE_STUDY_PACK_LIMIT", noteId: "note-1" }}
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    fireEvent.click(await screen.findByRole("button", { name: "Continue with Pro" }));

    await waitFor(() => {
      expect(createPremiumCheckoutSession).toHaveBeenCalledWith({ planType: "PRO", returnUrl: "/notes/note-1" });
      expect(redirectToCheckoutUrl).toHaveBeenCalledWith("https://checkout.xendit.test/invoice_123");
    });

    expect(loadPendingPaywallUpgradeContext("user-1")).toMatchObject({
      type: "GENERATE_STUDY_PACK_LIMIT",
      lastAction: "GENERATE_STUDY_PACK",
      noteId: "note-1",
      returnPath: "/notes/note-1",
      source: "test_source",
    });
  });

  it("disables the Plus CTA when the current plan is already Plus", async () => {
    getAuthUserMock.mockReturnValue({
      id: "user-1",
      planType: "PLUS",
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      profileType: "STUDENT",
    });

    render(
      <PaywallModal
        isOpen
        context={{ type: "GENERATE_NOTE_LIMIT" }}
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    expect(await screen.findByText("Current plan")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Continue with Pro" })).toBeEnabled();
  });

  it("uses teacher-specific quiz generation copy", async () => {
    getAuthUserMock.mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      profileType: "TEACHER",
    });

    render(
      <PaywallModal
        isOpen
        variant="quiz-generation-limit"
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    expect(await screen.findByText("You've reached your quiz generation limit")).toBeInTheDocument();
    expect(
      screen.getByText("Generate more quizzes and export-ready classroom materials without breaking your teaching flow."),
    ).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Unlock more quiz generations and exports" })).toBeInTheDocument();
  });

  it("uses teacher-specific export limit copy", async () => {
    getAuthUserMock.mockReturnValue({
      id: "user-1",
      planType: "PLUS",
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      profileType: "TEACHER",
    });

    render(
      <PaywallModal
        isOpen
        variant="export-limit"
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    expect(await screen.findByText("You've used all your exports")).toBeInTheDocument();
    expect(
      screen.getByText("Upgrade for unlimited quiz exports so you can keep printing DOCX exams for your class."),
    ).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Get higher Study Pack and quiz generation limits" })).toBeInTheDocument();
  });

  it("shows the verification modal instead of starting checkout for unverified users", async () => {
    getAuthUserMock.mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: null,
      profileType: "STUDENT",
    });
    requestEmailVerificationMock.mockResolvedValue({
      message: "Verification email sent. Please check your inbox.",
    });

    render(
      <PaywallModal
        isOpen
        context={{ type: "ADAPTIVE_PRACTICE_LOCKED" }}
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    fireEvent.click(await screen.findByRole("button", { name: "Continue with Pro" }));

    expect(await screen.findByText("Verify your email first")).toBeInTheDocument();
    expect(createPremiumCheckoutSession).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Resend verification email" }));

    await waitFor(() => {
      expect(requestEmailVerificationMock).toHaveBeenCalled();
    });
  });
});
