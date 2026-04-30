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

    expect(screen.getByText("You’ve reached your study pack limit")).toBeInTheDocument();
    expect(screen.getByText(/Upgrade to Plus or Pro to create more study packs/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Upgrade to Plus" })).toBeInTheDocument();
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

    fireEvent.click(screen.getByRole("button", { name: "Upgrade to Plus" }));
    expect(pushMock).toHaveBeenCalledWith("/pricing");

    pushMock.mockReset();
    fireEvent.click(screen.getByRole("button", { name: "View My Plan" }));
    expect(pushMock).toHaveBeenCalledWith("/settings#plan-billing");
  });

  it("renders pro-limit copy and actions", () => {
    render(
      <StudyPackLimitModal
        isOpen
        planType="PRO"
        resetDateLabel="April 20"
        onClose={jest.fn()}
      />,
    );

    expect(screen.getByText("You’ve reached your study pack limit for this month")).toBeInTheDocument();
    expect(screen.getByText(/Your study pack limit resets on April 20\./)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "View Plans" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Get More Study Packs" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Maybe Later" })).toBeInTheDocument();
  });
});
