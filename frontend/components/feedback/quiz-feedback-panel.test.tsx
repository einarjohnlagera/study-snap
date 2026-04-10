import { fireEvent, render, screen } from "@testing-library/react";
import { QuizFeedbackPanel } from "./quiz-feedback-panel";

describe("QuizFeedbackPanel", () => {
  it("renders the helpfulness prompt on quiz result sections", () => {
    render(
      <QuizFeedbackPanel
        quizLabel="Challenge Quiz"
        noteTitle="Cardio Notes"
        section="results"
      />,
    );

    expect(screen.getByText("Was this quiz helpful?")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Yes" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Give Feedback" })).toBeInTheDocument();
  });

  it("shows a lightweight thank-you state after positive helpfulness feedback", () => {
    render(
      <QuizFeedbackPanel
        quizLabel="Challenge Quiz"
        noteTitle="Cardio Notes"
        section="results"
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Yes" }));

    expect(screen.getByText("Thanks. Your response helps us improve future quizzes.")).toBeInTheDocument();
  });

  it("opens the feedback modal with contextual quiz details from results", () => {
    render(
      <QuizFeedbackPanel
        quizLabel="Board Exam Mode"
        noteTitle="Cardio Notes"
        section="results"
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Give Feedback" }));

    expect(screen.getByRole("dialog", { name: "Was this quiz helpful?" })).toBeInTheDocument();
    expect(screen.getByLabelText("Message")).toHaveValue(
      "Feedback type: Quiz Feedback\nQuiz: Board Exam Mode\nContext: Quiz Results\nNote: Cardio Notes\n\nWhat happened?",
    );
  });

  it("keeps the issue-reporting panel for answer review sections", () => {
    render(
      <QuizFeedbackPanel
        quizLabel="Adaptive Practice"
        noteTitle="Cardio Notes"
        section="review"
      />,
    );

    expect(screen.getByText("Help improve this quiz")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Report Question" })).toBeInTheDocument();
  });
});
