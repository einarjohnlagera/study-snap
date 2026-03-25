import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { PaywallModal } from "./paywall-modal";

jest.mock("@/lib/api", () => ({
  joinPremiumWaitlist: jest.fn().mockResolvedValue({
    message: "You're on the list! We'll notify you when Premium launches.",
  }),
  trackAnalyticsEvent: jest.fn(),
}));

jest.mock("next/navigation", () => ({
  usePathname: () => "/notes/note-1",
}));

describe("PaywallModal", () => {
  it("renders the coming-soon waitlist modal for premium quiz features", async () => {
    render(
      <PaywallModal
        isOpen
        variant="challenge-quiz"
        onClose={jest.fn()}
      />,
    );

    expect(await screen.findByText("Premium is coming soon")).toBeInTheDocument();
    expect(screen.getByText(/Premium will include:/i)).toBeInTheDocument();
    expect(screen.getByText("Challenge Quiz")).toBeInTheDocument();
    expect(screen.getByText("Adaptive Practice")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Maybe Later" })).toBeInTheDocument();
  });

  it("lets the user join the premium waitlist", async () => {
    render(
      <PaywallModal
        isOpen
        variant="adaptive-practice"
        onClose={jest.fn()}
      />,
    );

    fireEvent.click(await screen.findByRole("button", { name: "Join Waitlist" }));

    await waitFor(() => {
      expect(screen.getByText("You're on the list! We'll notify you when Premium launches.")).toBeInTheDocument();
    });
  });
});
