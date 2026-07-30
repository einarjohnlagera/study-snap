import { render, screen } from "@testing-library/react";
import { ScoreReveal } from "./score-reveal";

describe("ScoreReveal", () => {
  it("renders the percentage, label, and supporting line", () => {
    render(
      <ScoreReveal
        percentage={78}
        label="Overall mastery"
        supportingLine="18 of 25 answered"
      />,
    );

    expect(screen.getByRole("heading", { name: /Overall mastery, 78 percent/i })).toHaveTextContent("78");
    expect(screen.getByText("Overall mastery")).toBeInTheDocument();
    expect(screen.getByText("18 of 25 answered")).toBeInTheDocument();
  });

  it("renders the performance pill when provided", () => {
    render(
      <ScoreReveal
        percentage={92}
        label="Score"
        performanceLevel="Excellent"
      />,
    );

    expect(screen.getByText("Excellent")).toBeInTheDocument();
  });

  it("omits the performance pill when not provided", () => {
    render(
      <ScoreReveal
        percentage={45}
        label="Score"
      />,
    );

    expect(screen.queryByText("Excellent")).not.toBeInTheDocument();
    expect(screen.queryByText("Needs Improvement")).not.toBeInTheDocument();
  });

  it("renders a subordinate secondary metric when provided", () => {
    render(
      <ScoreReveal
        percentage={80}
        label="Answered Accuracy"
        secondaryMetric={{
          label: "Overall Completion Score",
          percentage: 60,
          description: "6 correct of 10 total",
        }}
      />,
    );

    expect(screen.getByText("Overall Completion Score")).toBeInTheDocument();
    expect(screen.getByTestId("score-reveal-secondary-metric")).toBeInTheDocument();
    expect(screen.getByLabelText("Overall Completion Score, 60 percent")).toHaveTextContent("60%");
    expect(screen.getByText("6 correct of 10 total")).toBeInTheDocument();
  });

  it("renders no secondary metric markup when omitted", () => {
    render(<ScoreReveal percentage={80} label="Score" />);

    expect(screen.queryByText("Overall Completion Score")).not.toBeInTheDocument();
    expect(screen.queryByTestId("score-reveal-secondary-metric")).not.toBeInTheDocument();
  });

  it("clamps percentages outside 0-100", () => {
    const { rerender } = render(<ScoreReveal percentage={120} label="Score" />);
    expect(screen.getByRole("heading")).toHaveTextContent("100");

    rerender(<ScoreReveal percentage={-5} label="Score" />);
    expect(screen.getByRole("heading")).toHaveTextContent("0");
  });

  it("exposes the tone via data-tone for downstream styling", () => {
    render(<ScoreReveal percentage={50} label="Score" tone="board-exam" />);
    expect(screen.getByTestId("exam-score-reveal")).toHaveAttribute("data-tone", "board-exam");
  });

  it("supports the Challenge Quiz tone", () => {
    render(<ScoreReveal percentage={50} label="Score" tone="challenge-quiz" />);
    expect(screen.getByTestId("exam-score-reveal")).toHaveAttribute("data-tone", "challenge-quiz");
  });
});
