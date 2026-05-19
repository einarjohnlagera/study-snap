import { fireEvent, render, screen } from "@testing-library/react";
import { QuestionNavigator } from "./question-navigator";

describe("QuestionNavigator", () => {
  const baseProps = {
    total: 5,
    currentIndex: 1,
    isAnswered: (index: number) => index === 0,
    onSelect: jest.fn(),
    summary: "Question 2 of 5 · 1 answered",
    defaultCollapsed: false,
  };

  beforeEach(() => {
    baseProps.onSelect = jest.fn();
  });

  it("renders the summary and a button per question", () => {
    render(<QuestionNavigator {...baseProps} />);

    expect(screen.getByText("Question 2 of 5 · 1 answered")).toBeInTheDocument();
    for (let n = 1; n <= 5; n++) {
      expect(screen.getByRole("button", { name: new RegExp(`Go to question ${n}`) })).toBeInTheDocument();
    }
  });

  it("marks the current question with aria-current=step", () => {
    render(<QuestionNavigator {...baseProps} />);

    expect(screen.getByRole("button", { name: /Go to question 2/ })).toHaveAttribute("aria-current", "step");
    expect(screen.getByRole("button", { name: /Go to question 1/ })).not.toHaveAttribute("aria-current");
  });

  it("labels answered vs unanswered questions", () => {
    render(<QuestionNavigator {...baseProps} />);

    expect(screen.getByRole("button", { name: "Go to question 1 (answered)" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Go to question 2 (unanswered)" })).toBeInTheDocument();
  });

  it("calls onSelect with the clicked index", () => {
    render(<QuestionNavigator {...baseProps} />);

    fireEvent.click(screen.getByRole("button", { name: /Go to question 4/ }));

    expect(baseProps.onSelect).toHaveBeenCalledWith(3);
  });

  it("starts collapsed when defaultCollapsed is true and expands when toggled", () => {
    render(
      <QuestionNavigator
        {...baseProps}
        defaultCollapsed
        disclosureTestId="nav-disclosure"
      />,
    );

    expect(screen.getByTestId("nav-disclosure")).toHaveAttribute("data-state", "collapsed");

    fireEvent.click(screen.getByRole("button", { expanded: false }));

    expect(screen.getByTestId("nav-disclosure")).toHaveAttribute("data-state", "expanded");
  });

  it("disables question buttons when disabled is true", () => {
    render(<QuestionNavigator {...baseProps} disabled />);

    expect(screen.getByRole("button", { name: /Go to question 3/ })).toBeDisabled();
  });

  it("exposes the tone via data-tone for downstream styling", () => {
    render(<QuestionNavigator {...baseProps} tone="board-exam" testId="nav" />);

    expect(screen.getByTestId("nav")).toHaveAttribute("data-tone", "board-exam");
  });
});
