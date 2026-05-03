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

  it("renders the free-plan limit copy and dynamic upgrade CTAs", () => {
    render(
      <StudyPackLimitModal
        isOpen
        planType="FREE"
        resetDateLabel="April 15"
        onClose={jest.fn()}
      />,
    );

    expect(screen.getByText("You’ve reached your study pack limit")).toBeInTheDocument();
    expect(screen.getByText(/Upgrade for more Study Packs/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Upgrade to Plus" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Go Pro" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "View My Plan" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Maybe Later" })).toBeInTheDocument();
  });

  it("routes free-plan upgrade CTAs to /settings?section=plans and View My Plan to plan-billing hash", () => {
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
    expect(pushMock).toHaveBeenCalledWith("/settings?section=plans");

    pushMock.mockReset();
    fireEvent.click(screen.getByRole("button", { name: "View My Plan" }));
    expect(pushMock).toHaveBeenCalledWith("/settings#plan-billing");
  });

  it("renders the plus-plan limit copy with single Upgrade to Pro CTA", () => {
    render(
      <StudyPackLimitModal
        isOpen
        planType="PLUS"
        resetDateLabel="April 18"
        onClose={jest.fn()}
      />,
    );

    expect(screen.getByText("You’ve reached your study pack limit for Plus")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Upgrade to Pro" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Go Pro" })).not.toBeInTheDocument();
  });

  it("renders pro-limit copy with no upgrade CTAs (Pro is the top plan)", () => {
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
    expect(screen.queryByRole("button", { name: "Upgrade to Plus" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Upgrade to Pro" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Go Pro" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "View My Plan" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Maybe Later" })).toBeInTheDocument();
  });
});
