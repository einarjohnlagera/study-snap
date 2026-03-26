import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PaywallModal } from "./paywall-modal";

const pushMock = jest.fn();

jest.mock("@/lib/api", () => ({
  trackAnalyticsEvent: jest.fn(),
}));

jest.mock("next/navigation", () => ({
  usePathname: () => "/notes/note-1",
  useRouter: () => ({
    push: pushMock,
  }),
}));

describe("PaywallModal", () => {
  beforeEach(() => {
    pushMock.mockReset();
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
        variant="study-pack-limit"
        source="test_source"
        onClose={jest.fn()}
      />,
    );

    fireEvent.click(await screen.findByRole("button", { name: "Upgrade to Premium" }));

    await waitFor(() => {
      expect(pushMock).toHaveBeenCalledWith("/settings#plan-billing");
    });
  });

  it("does not reopen after dismissal in the same session", async () => {
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

    await waitFor(() => {
      expect(screen.queryByText("Difficulty Selection is a Premium feature")).not.toBeInTheDocument();
    });
  });
});
