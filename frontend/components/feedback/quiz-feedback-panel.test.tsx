import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QuizFeedbackPanel } from "./quiz-feedback-panel";

describe("QuizFeedbackPanel", () => {
  beforeEach(() => {
    globalThis.localStorage.clear();
  });

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
    expect(screen.queryByRole("button", { name: "Send Feedback" })).not.toBeInTheDocument();
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

  it("shows the first-quiz ask once and suppresses it after dismissal", async () => {
    const view = render(
      <QuizFeedbackPanel
        quizLabel="Long Exam"
        noteTitle="Cardio Notes"
        section="results"
        isFirstCompletedSessionEver
        userId="user-1"
      />,
    );

    expect(await screen.findByText("How did your first quiz go?")).toBeInTheDocument();
    expect(screen.queryByText("Was this quiz helpful?")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Dismiss feedback prompt" }));
    expect(screen.queryByText("How did your first quiz go?")).not.toBeInTheDocument();

    view.unmount();
    render(
      <QuizFeedbackPanel
        quizLabel="Long Exam"
        noteTitle="Cardio Notes"
        section="results"
        isFirstCompletedSessionEver
        userId="user-1"
      />,
    );

    await waitFor(() => {
      expect(screen.queryByText("How did your first quiz go?")).not.toBeInTheDocument();
    });
    expect(screen.getByText("Was this quiz helpful?")).toBeInTheDocument();
  });
});
