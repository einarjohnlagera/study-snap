import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PaywallModal } from "./paywall-modal";

const pushMock = jest.fn();
const requestEmailVerificationMock = jest.fn();
const getAuthUserMock = jest.fn();

jest.mock("@/lib/api", () => ({
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
}));

describe("PaywallModal", () => {
  beforeEach(() => {
    pushMock.mockReset();
    requestEmailVerificationMock.mockReset();
    getAuthUserMock.mockReset();
    getAuthUserMock.mockReturnValue({
      emailVerifiedAt: "2026-03-24T00:00:00Z",
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

    expect(await screen.findByText("Adaptive Practice is a Premium feature")).toBeInTheDocument();
    expect(screen.getByText(/Adaptive Practice focuses on your weak concepts/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Maybe Later" })).toBeInTheDocument();
  });

  it("routes to Settings plan when Upgrade to Premium is clicked", async () => {
    render(
      <PaywallModal
        isOpen
        variant="adaptive-practice"
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    fireEvent.click(await screen.findByRole("button", { name: "Upgrade to Premium" }));

    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith("/settings#plan-billing");
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

    expect(await screen.findByText("Difficulty Selection is a Premium feature")).toBeInTheDocument();
  });

  it("shows the verification modal instead of routing when the user is unverified", async () => {
    getAuthUserMock.mockReturnValue({
      emailVerifiedAt: null,
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

    fireEvent.click(await screen.findByRole("button", { name: "Upgrade to Premium" }));

    expect(await screen.findByText("Verify your email first")).toBeInTheDocument();
    expect(pushMock).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "Resend verification email" }));

    await waitFor(() => {
      expect(requestEmailVerificationMock).toHaveBeenCalled();
    });
  });
});
