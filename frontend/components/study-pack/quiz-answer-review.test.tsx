import { fireEvent, render, screen } from "@testing-library/react";
import { QuizAnswerReview } from "./quiz-answer-review";

const reviewQuiz = [
  {
    question: "What powers the cell?",
    choices: ["Mitochondria", "Nucleus", "Ribosome", "Cell wall"],
    correctIndex: 0,
    concept: "Cell organelles",
    explanation: "Mitochondria produce ATP for the cell.",
  },
  {
    question: "What carries genetic instructions?",
    choices: ["DNA", "ATP", "Glucose", "Oxygen"],
    correctIndex: 0,
    concept: "Genetics",
    explanation: "DNA stores hereditary information.",
  },
];

describe("QuizAnswerReview", () => {
  // C3. This component is the shared answer review behind five call sites — quick-review,
  // adaptive-practice, challenge-quiz (x2) and session-history review — and its explanation rendered raw
  // while the question directly above it rendered correctly, so it read as a rendering bug rather than
  // bad content. The rule in docs/features/quiz.md: any surface showing a question, option, or
  // explanation routes through renderMathText, and a raw {item.explanation} is the bug.
  it("renders LaTeX in the explanation, not just in the question", () => {
    const mathQuiz = [{
      question: "Which expression is equivalent?",
      choices: ["A", "B", "C", "D"],
      correctIndex: 0,
      concept: "Algebra",
      explanation: "Because \\(x^2 + 2x\\) factors to \\(x(x + 2)\\).",
    }];

    const { container } = render(<QuizAnswerReview quiz={mathQuiz} selectedChoices={{ 0: 0 }} />);

    expect(container.querySelector(".katex")).toBeInTheDocument();
    expect(screen.queryByText(/\\\(/)).not.toBeInTheDocument();
    expect(screen.queryByText(/x\^2/)).not.toBeInTheDocument();
  });

  it("shows the selected answer, correct answer, explanation, and concept", () => {
    render(<QuizAnswerReview quiz={reviewQuiz} selectedChoices={{ 0: 1, 1: 0 }} />);

    expect(screen.getByLabelText("Answer review")).toHaveClass("motion-fade-enter");
    expect(screen.getByTestId("quiz-answer-review-current-item")).toHaveClass("motion-fade-enter");
    expect(screen.getByText("Correct")).toBeInTheDocument();
    expect(screen.getByText("Total Questions")).toBeInTheDocument();
    expect(screen.getByText("Percentage")).toBeInTheDocument();
    expect(screen.getByText("Performance")).toBeInTheDocument();
    expect(screen.getByText("Fair")).toBeInTheDocument();
    expect(screen.getByText("Weak Concepts")).toBeInTheDocument();
    expect(screen.getByText("What powers the cell?")).toBeInTheDocument();
    expect(screen.getByText("Concept: Cell organelles")).toBeInTheDocument();
    expect(screen.getByText("Your Answer")).toBeInTheDocument();
    expect(screen.getByLabelText("Answer review")).toHaveTextContent(/Your Answer[\s\S]*Nucleus/);
    expect(screen.getByText("Correct Answer")).toBeInTheDocument();
    expect(screen.getByLabelText("Answer review")).toHaveTextContent(/Correct Answer[\s\S]*Mitochondria/);
    expect(screen.getByText("Nucleus")).toBeInTheDocument();
    expect(screen.getByText("Mitochondria")).toBeInTheDocument();
    expect(screen.getByText("Your Answer")).toBeInTheDocument();
    expect(screen.getByText("Correct Answer")).toBeInTheDocument();
    expect(screen.getByText("Why this is correct")).toBeInTheDocument();
    expect(screen.getByTestId("quiz-answer-review-explanation")).toHaveAttribute("data-state", "expanded");
    expect(screen.getByText("Mitochondria produce ATP for the cell.")).toBeInTheDocument();
  });

  it("moves through reviewed questions without losing answer state", () => {
    render(<QuizAnswerReview quiz={reviewQuiz} selectedChoices={{ 0: 1, 1: 0 }} />);

    fireEvent.click(screen.getByRole("button", { name: "Next Question" }));

    expect(screen.getByText("What carries genetic instructions?")).toBeInTheDocument();
    expect(screen.getByText("Concept: Genetics")).toBeInTheDocument();
    expect(screen.getByText("DNA stores hereditary information.")).toBeInTheDocument();
    expect(screen.getByText("DNA")).toBeInTheDocument();
    expect(screen.getByText("Your Answer")).toBeInTheDocument();
    expect(screen.getByText("Correct Answer")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "Previous Question" }));

    expect(screen.getByText("What powers the cell?")).toBeInTheDocument();
    expect(screen.getByText("Mitochondria produce ATP for the cell.")).toBeInTheDocument();
  });

  it("can focus the review on incorrect answers only", () => {
    render(<QuizAnswerReview quiz={reviewQuiz} selectedChoices={{ 0: 1, 1: 0 }} />);

    fireEvent.click(screen.getByRole("button", { name: "Incorrect Only" }));

    expect(screen.getByText("What powers the cell?")).toBeInTheDocument();
    expect(screen.queryByText("What carries genetic instructions?")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Next Question" })).toBeDisabled();
  });

  it("supports per-question explanation collapse and global expand or collapse controls", () => {
    render(<QuizAnswerReview quiz={reviewQuiz} selectedChoices={{ 0: 1, 1: 0 }} />);

    fireEvent.click(screen.getByRole("button", { name: "Collapse Explanation" }));
    expect(screen.getByTestId("quiz-answer-review-explanation")).toHaveAttribute("data-state", "collapsed");

    fireEvent.click(screen.getByRole("button", { name: "Expand All" }));
    expect(screen.getByTestId("quiz-answer-review-explanation")).toHaveAttribute("data-state", "expanded");

    fireEvent.click(screen.getByRole("button", { name: "Collapse All" }));
    expect(screen.getByTestId("quiz-answer-review-explanation")).toHaveAttribute("data-state", "collapsed");

    fireEvent.click(screen.getByRole("button", { name: "Next Question" }));
    expect(screen.getByTestId("quiz-answer-review-explanation")).toHaveAttribute("data-state", "collapsed");
  });

  it("renders optional continue-learning footer actions", () => {
    render(
      <QuizAnswerReview
        quiz={reviewQuiz}
        selectedChoices={{ 0: 1, 1: 0 }}
        footer={<button type="button">Review Study Pack</button>}
      />,
    );

    expect(screen.getByText("Continue learning")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Review Study Pack" })).toBeInTheDocument();
  });

  it("keeps review navigation controls mobile-stackable", () => {
    render(<QuizAnswerReview quiz={reviewQuiz} selectedChoices={{ 0: 1, 1: 0 }} />);

    expect(screen.getByRole("button", { name: "Previous Question" })).toHaveClass("w-full", "sm:w-auto");
    expect(screen.getByRole("button", { name: "Next Question" })).toHaveClass("w-full", "sm:w-auto");
  });

  it("keeps long question and explanation text readable on small screens", () => {
    render(
      <QuizAnswerReview
        quiz={[{
          question: "What does pneumonoultramicroscopicsilicovolcanoconiosis indicate in a respiratory system review session?",
          choices: ["A", "B", "C", "D"],
          correctIndex: 0,
          concept: "Respiratory system",
          explanation: "Supercalifragilisticexpialidocious-style terminology should still wrap instead of forcing horizontal scrolling in the review page.",
        }]}
        selectedChoices={{ 0: 1 }}
      />,
    );

    expect(screen.getByText(/pneumonoultramicroscopicsilicovolcanoconiosis/i)).toHaveClass("break-words");
    expect(screen.getByText(/Supercalifragilisticexpialidocious-style terminology/i)).toHaveClass("break-words");
  });

  it("renders identification answers with accepted answer reveal", () => {
    render(
      <QuizAnswerReview
        quiz={[{
          question: "Identify the law that relates voltage, current, and resistance.",
          choices: [],
          correctIndex: null,
          questionFormat: "IDENTIFICATION",
          acceptableAnswers: ["Ohm's Law", "Ohms law"],
          concept: "Circuit laws",
          explanation: "Ohm's Law relates voltage, current, and resistance.",
        }]}
        selectedChoices={{}}
        selectedIdentificationAnswers={{ 0: "  OHM'S   LAW " }}
      />,
    );

    expect(screen.getByLabelText("Identification answer")).toHaveValue("  OHM'S   LAW ");
    expect(screen.getByText("Accepted answers: Ohm's Law; Ohms law")).toBeInTheDocument();
    expect(screen.getByLabelText("Answer review")).toHaveTextContent(/Your Answer[\s\S]*OHM'S\s+LAW/);
    expect(screen.getByLabelText("Answer review")).toHaveTextContent(/Correct Answer[\s\S]*Ohm's Law; Ohms law/);
  });

  it("renders enumeration answers with per-item accepted answer reveal", () => {
    render(
      <QuizAnswerReview
        quiz={[{
          question: "Name the three branches of government.",
          choices: [],
          correctIndex: null,
          questionFormat: "ENUMERATION",
          acceptableAnswerGroups: [["Legislative"], ["Executive"], ["Judicial", "Judiciary"]],
          concept: "Government",
          explanation: "The three branches are legislative, executive, and judicial.",
        }]}
        selectedChoices={{}}
        selectedEnumerationAnswers={{ 0: ["Legislative", "Executive", "Judiciary"] }}
      />,
    );

    expect(screen.getByLabelText("Enumeration answer 1 of 3")).toHaveValue("Legislative");
    expect(screen.getByLabelText("Enumeration answer 2 of 3")).toHaveValue("Executive");
    expect(screen.getByLabelText("Enumeration answer 3 of 3")).toHaveValue("Judiciary");
    expect(screen.getByText("Accepted answers: Legislative; Executive; Judicial / Judiciary")).toBeInTheDocument();
    expect(screen.getByLabelText("Answer review")).toHaveTextContent(/Your Answer[\s\S]*Legislative; Executive; Judiciary/);
  });
});
