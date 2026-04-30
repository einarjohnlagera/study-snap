import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PaywallModal } from "./paywall-modal";
import { createPremiumCheckoutSession } from "@/lib/api";
import { redirectToCheckoutUrl } from "@/lib/checkout-redirect";

const pushMock = jest.fn();
const requestEmailVerificationMock = jest.fn();
const getAuthUserMock = jest.fn();

jest.mock("@/lib/api", () => ({
  createPremiumCheckoutSession: jest.fn().mockResolvedValue({
    checkoutUrl: "https://checkout.xendit.test/invoice_123",
  }),
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

describe("PaywallModal", () => {
  beforeEach(() => {
    window.history.replaceState(null, "", "/notes/note-1");
    pushMock.mockReset();
    (redirectToCheckoutUrl as jest.Mock).mockReset();
    requestEmailVerificationMock.mockReset();
    getAuthUserMock.mockReset();
    getAuthUserMock.mockReturnValue({
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      profileType: "STUDENT",
    });
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it("renders the adaptive practice paywall copy", async () => {
    render(
      <PaywallModal
        isOpen
        variant="adaptive-practice"
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    expect(await screen.findByText("Adaptive Practice is a Pro feature")).toBeInTheDocument();
    expect(screen.getByText(/Adaptive Practice focuses on your weak concepts/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Maybe Later" })).toBeInTheDocument();
  });

  it("starts checkout when Go Pro is clicked", async () => {
    render(
      <PaywallModal
        isOpen
        variant="adaptive-practice"
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    fireEvent.click(await screen.findByRole("button", { name: "Go Pro" }));

    await waitFor(() => {
      expect(createPremiumCheckoutSession).toHaveBeenCalledWith({ planType: "PRO", returnUrl: "/notes/note-1" });
      expect(redirectToCheckoutUrl).toHaveBeenCalledWith("https://checkout.xendit.test/invoice_123");
    });
  });

  it("reopens after dismissal when the user triggers the same gated action again", async () => {
    const onClose = jest.fn();
    const { rerender } = render(
      <PaywallModal
        isOpen
        variant="difficulty-selection"
        source="test_source"
        onClose={onClose}
      />,
    );

    fireEvent.click(await screen.findByRole("button", { name: "Maybe Later" }));

    rerender(
      <PaywallModal
        isOpen
        variant="difficulty-selection"
        source="test_source"
        onClose={onClose}
      />,
    );

    expect(await screen.findByText("Difficulty Selection is a Pro feature")).toBeInTheDocument();
  });

  it("uses student-specific quiz limit copy", async () => {
    render(
      <PaywallModal
        isOpen
        variant="challenge-quiz-limit"
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    expect(await screen.findByText("You’ve reached your quiz limit")).toBeInTheDocument();
    expect(
      screen.getByText("You’ve used all your quizzes for this month. Choose Plus or go Pro to continue practicing and unlock higher limits."),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Maybe Later" })).toBeInTheDocument();
  });

  it("uses board taker-specific quiz limit copy", async () => {
    getAuthUserMock.mockReturnValue({
      emailVerifiedAt: "2026-03-24T00:00:00Z",
      profileType: "BOARD_EXAM",
    });

    render(
      <PaywallModal
        isOpen
        variant="challenge-quiz-limit"
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    expect(
      await screen.findByText("You’ve used all your quizzes for this month. Go Pro to continue practicing and access Board Exam mode."),
    ).toBeInTheDocument();
  });

  it("uses teacher-specific quiz generation copy", async () => {
    getAuthUserMock.mockReturnValue({
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

    expect(
      await screen.findByText("You’ve used all your quiz generations for this month. Choose Plus or go Pro to generate more quizzes and export materials for your class."),
    ).toBeInTheDocument();
  });

  it("shows the verification modal instead of routing when the user is unverified", async () => {
    getAuthUserMock.mockReturnValue({
      emailVerifiedAt: null,
      profileType: "STUDENT",
    });
    requestEmailVerificationMock.mockResolvedValue({
      message: "Verification email sent. Please check your inbox.",
    });

    render(
      <PaywallModal
        isOpen
        variant="adaptive-practice"
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    fireEvent.click(await screen.findByRole("button", { name: "Go Pro" }));

    expect(await screen.findByText("Verify your email first")).toBeInTheDocument();
    expect(pushMock).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Resend verification email" }));

    await waitFor(() => {
      expect(requestEmailVerificationMock).toHaveBeenCalled();
    });
  });
});
