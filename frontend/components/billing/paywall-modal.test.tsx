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

const proOnlyPaywallContexts = [
  { type: "BOARD_EXAM_MODE_LOCKED" as const, ctaLabel: "Unlock Board Exam Mode" },
  { type: "LONG_EXAM_MODE_LOCKED" as const, ctaLabel: "Unlock the Long Exam" },
  { type: "DIFFICULTY_SELECTION_LOCKED" as const, ctaLabel: "Unlock Difficulty Selection" },
  { type: "INTERVIEW_PRACTICE_LOCKED" as const, ctaLabel: "Unlock Interview Practice" },
];

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

    expect(await screen.findByText("You've used your free Adaptive Practice sessions")).toBeInTheDocument();
    expect(screen.getByText("Free includes a small monthly taste of targeted weak-area practice. Upgrade for more sessions and keep closing your learning loop.")).toBeInTheDocument();
    expect(screen.getByText("Plus")).toBeInTheDocument();
    expect(screen.getByText("Pro")).toBeInTheDocument();
    expect(screen.getByText("Most popular")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Get More Adaptive Practice" })).toBeInTheDocument();
    expect(screen.getByText("Access activates immediately after payment")).toBeInTheDocument();
    expect(screen.getByText("No automatic charges. You control renewals.")).toBeInTheDocument();
  });

  it("saves the paywall upgrade context and starts checkout for the plan-aware Study Pack upgrade", async () => {
    render(
      <PaywallModal
        isOpen
        context={{ type: "GENERATE_STUDY_PACK_LIMIT", noteId: "note-1" }}
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    fireEvent.click(await screen.findByRole("button", { name: "Get More Study Packs" }));

    await waitFor(() => {
      expect(createPremiumCheckoutSession).toHaveBeenCalledWith({ planType: "PLUS", returnUrl: "/notes/note-1" });
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

  it.each(proOnlyPaywallContexts)(
    "keeps Plus visible but non-selectable for %s",
    async ({ type, ctaLabel }) => {
      render(
        <PaywallModal
          isOpen
          context={{ type }}
          source="test_source"
          onClose={jest.fn()}
        />,
      );

      expect(await screen.findByRole("button", { name: ctaLabel })).toBeInTheDocument();
      expect(screen.getByText("Plus").closest("[role='button']")).toBeNull();
      expect(screen.getByText("Pro").closest("[role='button']")).toBeInTheDocument();

      fireEvent.click(screen.getByText("Plus"));

      expect(screen.getByRole("button", { name: ctaLabel })).toBeInTheDocument();
      expect(createPremiumCheckoutSession).not.toHaveBeenCalled();
    },
  );

  it("starts Pro checkout from a Pro-only paywall context", async () => {
    render(
      <PaywallModal
        isOpen
        context={{ type: "BOARD_EXAM_MODE_LOCKED" }}
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    fireEvent.click(await screen.findByRole("button", { name: "Unlock Board Exam Mode" }));

    await waitFor(() => {
      expect(createPremiumCheckoutSession).toHaveBeenCalledWith({ planType: "PRO", returnUrl: "/notes/note-1" });
      expect(redirectToCheckoutUrl).toHaveBeenCalledWith("https://checkout.xendit.test/invoice_123");
    });
  });

  it("keeps Plus selectable and checkoutable for Plus-eligible adaptive practice paywalls", async () => {
    render(
      <PaywallModal
        isOpen
        context={{ type: "ADAPTIVE_PRACTICE_LOCKED" }}
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    expect(await screen.findByRole("button", { name: "Get More Adaptive Practice" })).toBeInTheDocument();
    expect(screen.getByText("Plus").closest("[role='button']")).toBeInTheDocument();

    fireEvent.click(screen.getByText("Pro"));
    expect(await screen.findByRole("button", { name: "Go Pro" })).toBeInTheDocument();

    fireEvent.click(screen.getByText("Plus"));
    fireEvent.click(await screen.findByRole("button", { name: "Get More Adaptive Practice" }));

    await waitFor(() => {
      expect(createPremiumCheckoutSession).toHaveBeenCalledWith({ planType: "PLUS", returnUrl: "/notes/note-1" });
      expect(redirectToCheckoutUrl).toHaveBeenCalledWith("https://checkout.xendit.test/invoice_123");
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
    expect(await screen.findByRole("button", { name: "Unlock more exports — get Plus" })).toBeInTheDocument();
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
      screen.getByText("Move up for more DOCX quiz exports so you can keep printing exams for your class."),
    ).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Get more Study Packs & quiz generations with Pro" })).toBeInTheDocument();
    expect(screen.queryByText(/unlimited quiz exports/i)).not.toBeInTheDocument();
  });

  it("explains the longer teacher quiz question-count gate", async () => {
    getAuthUserMock.mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      profileType: "TEACHER",
    });

    render(
      <PaywallModal
        isOpen
        variant="teacher-quiz-question-count"
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    expect(await screen.findByText("Unlock longer teacher quizzes")).toBeInTheDocument();
    expect(
      screen.getByText("Plus unlocks 20- and 30-question quizzes so you can match chapter quizzes and longer unit assessments."),
    ).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Unlock 20- and 30-question quizzes" })).toBeInTheDocument();
  });

  it("explains the multiple teacher exam versions gate", async () => {
    getAuthUserMock.mockReturnValue({
      id: "user-1",
      planType: "FREE",
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      profileType: "TEACHER",
    });

    render(
      <PaywallModal
        isOpen
        variant="teacher-exam-versions"
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    expect(await screen.findByRole("heading", { name: "Unlock multiple exam versions" })).toBeInTheDocument();
    expect(screen.getByText("Plus unlocks multiple exam versions for anti-cheating.")).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Unlock multiple exam versions" })).toBeInTheDocument();
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

    fireEvent.click(await screen.findByRole("button", { name: "Get More Adaptive Practice" }));

    expect(await screen.findByText("Verify your email first")).toBeInTheDocument();
    expect(createPremiumCheckoutSession).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Resend verification email" }));

    await waitFor(() => {
      expect(requestEmailVerificationMock).toHaveBeenCalled();
    });
  });
});
