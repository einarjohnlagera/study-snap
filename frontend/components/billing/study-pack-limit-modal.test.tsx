import { fireEvent, render, screen } from "@testing-library/react";
import { StudyPackLimitModal } from "./study-pack-limit-modal";

const pushMock = jest.fn();

jest.mock("next/navigation", () => ({
  useRouter: () => ({
    push: pushMock,
  }),
}));

describe("StudyPackLimitModal", () => {
  beforeEach(() => {
    pushMock.mockReset();
  });

  it("renders the free-plan limit copy and actions", () => {
    render(
      <StudyPackLimitModal
        isOpen
        planType="FREE"
        resetDateLabel="April 15"
        onClose={jest.fn()}
      />,
    );

    expect(screen.getByText("Free Plan Limit Reached")).toBeInTheDocument();
    expect(screen.getByText(/You’ve reached your Study Pack limit for this month on the Free plan\./)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Upgrade to Premium" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Maybe Later" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "View My Plan" })).toBeInTheDocument();
  });

  it("routes free-plan actions to pricing and plan pages", () => {
    const onClose = jest.fn();
    render(
      <StudyPackLimitModal
        isOpen
        planType="FREE"
        resetDateLabel="April 15"
        onClose={onClose}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Upgrade to Premium" }));
    expect(pushMock).toHaveBeenCalledWith("/pricing");

    pushMock.mockReset();
    fireEvent.click(screen.getByRole("button", { name: "View My Plan" }));
    expect(pushMock).toHaveBeenCalledWith("/settings#plan-billing");
  });

  it("renders premium-limit copy and actions", () => {
    render(
      <StudyPackLimitModal
        isOpen
        planType="PREMIUM"
        resetDateLabel="April 20"
        onClose={jest.fn()}
      />,
    );

    expect(screen.getByText("Monthly Limit Reached")).toBeInTheDocument();
    expect(screen.getByText(/You’ve used all your Study Packs for this month\./)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Upgrade Plan" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Get More Study Packs" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Maybe Later" })).toBeInTheDocument();
  });
});
