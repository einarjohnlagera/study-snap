import { act, render, screen, fireEvent } from "@testing-library/react";
import { GuidanceTip } from "./guidance-tip";
import * as guidance from "@/lib/guidance";

jest.mock("@/lib/guidance", () => ({
  hasSeenTip: jest.fn(),
  markTipSeen: jest.fn(),
}));

describe("GuidanceTip", () => {
  beforeEach(() => {
    jest.useFakeTimers();
    (guidance.hasSeenTip as jest.Mock).mockReset();
    (guidance.markTipSeen as jest.Mock).mockReset();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it("renders message when tip has not been seen", () => {
    (guidance.hasSeenTip as jest.Mock).mockReturnValue(false);
    render(<GuidanceTip tipId="test-tip" message="Try the quiz feature." />);
    expect(screen.getByText("Try the quiz feature.")).toBeInTheDocument();
  });

  it("does not render when tip has already been seen", () => {
    (guidance.hasSeenTip as jest.Mock).mockReturnValue(true);
    render(<GuidanceTip tipId="test-tip" message="Try the quiz feature." />);
    expect(screen.queryByText("Try the quiz feature.")).not.toBeInTheDocument();
  });

  it("shows a dismiss button", () => {
    (guidance.hasSeenTip as jest.Mock).mockReturnValue(false);
    render(<GuidanceTip tipId="test-tip" message="Try the quiz feature." />);
    expect(screen.getByRole("button", { name: "Dismiss tip" })).toBeInTheDocument();
  });

  it("calls markTipSeen and hides after dismiss", () => {
    (guidance.hasSeenTip as jest.Mock).mockReturnValue(false);
    render(<GuidanceTip tipId="test-tip" message="Try the quiz feature." />);
    fireEvent.click(screen.getByRole("button", { name: "Dismiss tip" }));
    expect(guidance.markTipSeen).toHaveBeenCalledWith("test-tip");
    act(() => { jest.runAllTimers(); });
    expect(screen.queryByText("Try the quiz feature.")).not.toBeInTheDocument();
  });

  it("passes tipId to hasSeenTip", () => {
    (guidance.hasSeenTip as jest.Mock).mockReturnValue(false);
    render(<GuidanceTip tipId="unique-id-123" message="Some hint." />);
    expect(guidance.hasSeenTip).toHaveBeenCalledWith("unique-id-123");
  });
});
