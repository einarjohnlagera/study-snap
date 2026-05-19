import { fireEvent, render, screen } from "@testing-library/react";
import { ExamTopBar } from "./exam-top-bar";

describe("ExamTopBar", () => {
  const baseProps = {
    modeLabel: "Long Exam",
    leaveLabel: "Leave Exam",
    onLeave: jest.fn(),
    remainingSeconds: 754,
    timerState: "normal" as const,
  };

  beforeEach(() => {
    baseProps.onLeave = jest.fn();
  });

  it("renders the leave button, mode label, and formatted timer", () => {
    render(<ExamTopBar {...baseProps} timerTestId="exam-timer" />);

    expect(screen.getByRole("button", { name: "Leave Exam" })).toBeInTheDocument();
    expect(screen.getByText("Long Exam")).toBeInTheDocument();
    expect(screen.getByTestId("exam-timer")).toHaveTextContent("12:34");
    expect(screen.getByTestId("exam-timer")).toHaveAttribute("data-timer-state", "normal");
  });

  it("invokes onLeave when the leave button is clicked", () => {
    render(<ExamTopBar {...baseProps} />);

    fireEvent.click(screen.getByRole("button", { name: "Leave Exam" }));

    expect(baseProps.onLeave).toHaveBeenCalledTimes(1);
  });

  it("disables the leave button when leaveDisabled is true", () => {
    render(<ExamTopBar {...baseProps} leaveDisabled />);

    expect(screen.getByRole("button", { name: "Leave Exam" })).toBeDisabled();
  });

  it("clamps negative remainingSeconds to 00:00", () => {
    render(<ExamTopBar {...baseProps} remainingSeconds={-30} timerTestId="exam-timer" />);

    expect(screen.getByTestId("exam-timer")).toHaveTextContent("00:00");
  });

  it("exposes timer state via data-timer-state for warning and urgent", () => {
    const { rerender } = render(
      <ExamTopBar {...baseProps} timerState="warning" timerTestId="exam-timer" />,
    );
    expect(screen.getByTestId("exam-timer")).toHaveAttribute("data-timer-state", "warning");

    rerender(<ExamTopBar {...baseProps} timerState="urgent" timerTestId="exam-timer" />);
    expect(screen.getByTestId("exam-timer")).toHaveAttribute("data-timer-state", "urgent");
  });
});
