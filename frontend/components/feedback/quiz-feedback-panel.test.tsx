import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QuizFeedbackPanel } from "./quiz-feedback-panel";
import { submitFeedback } from "@/lib/api";
import { markEarlyLifecycleFeedbackSignalShownThisSession } from "@/lib/early-lifecycle-feedback-signals";

jest.mock("@/lib/api", () => ({
  submitFeedback: jest.fn(),
  uploadFeedbackImage: jest.fn(),
}));

describe("QuizFeedbackPanel", () => {
  beforeEach(() => {
    globalThis.localStorage.clear();
    globalThis.sessionStorage.clear();
    (submitFeedback as jest.Mock).mockReset();
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

  it("shows the second-quiz difficulty and pacing ask only for the second completed session", () => {
    render(
      <QuizFeedbackPanel
        quizLabel="Challenge Quiz"
        noteTitle="Cardio Notes"
        section="results"
        isSecondCompletedSessionEver
        userId="user-2"
      />,
    );

    expect(screen.getByText("How did that quiz feel?")).toBeInTheDocument();
    expect(screen.queryByText("Was this quiz helpful?")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Felt right" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Too easy" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Too hard" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Too repetitive" })).toBeInTheDocument();
  });

  it("keeps the first-quiz ask ahead of the second-quiz ask if both flags are present", () => {
    render(
      <QuizFeedbackPanel
        quizLabel="Challenge Quiz"
        noteTitle="Cardio Notes"
        section="results"
        isFirstCompletedSessionEver
        isSecondCompletedSessionEver
        userId="user-3"
      />,
    );

    expect(screen.getByText("How did your first quiz go?")).toBeInTheDocument();
    expect(screen.queryByText("How did that quiz feel?")).not.toBeInTheDocument();
  });

  it("uses the generic panel when there is no second-session signal or another early-lifecycle prompt already fired", () => {
    const withoutSignal = render(
      <QuizFeedbackPanel
        quizLabel="Challenge Quiz"
        noteTitle="Cardio Notes"
        section="results"
        userId="user-4"
      />,
    );

    expect(screen.getByText("Was this quiz helpful?")).toBeInTheDocument();
    withoutSignal.unmount();
    markEarlyLifecycleFeedbackSignalShownThisSession();

    render(
      <QuizFeedbackPanel
        quizLabel="Challenge Quiz"
        noteTitle="Cardio Notes"
        section="results"
        isSecondCompletedSessionEver
        userId="user-5"
      />,
    );

    expect(screen.queryByText("How did that quiz feel?")).not.toBeInTheDocument();
    expect(screen.getByText("Was this quiz helpful?")).toBeInTheDocument();
  });

  it("marks the second-quiz ask seen after dismissal", async () => {
    const view = render(
      <QuizFeedbackPanel
        quizLabel="Challenge Quiz"
        noteTitle="Cardio Notes"
        section="results"
        isSecondCompletedSessionEver
        userId="user-6"
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Dismiss feedback prompt" }));
    expect(screen.queryByText("How did that quiz feel?")).not.toBeInTheDocument();

    view.unmount();
    globalThis.sessionStorage.clear();
    render(
      <QuizFeedbackPanel
        quizLabel="Challenge Quiz"
        noteTitle="Cardio Notes"
        section="results"
        isSecondCompletedSessionEver
        userId="user-6"
      />,
    );

    await waitFor(() => {
      expect(screen.queryByText("How did that quiz feel?")).not.toBeInTheDocument();
    });
    expect(screen.getByText("Was this quiz helpful?")).toBeInTheDocument();
  });

  it("dismisses Felt right without submitting feedback", () => {
    render(
      <QuizFeedbackPanel
        quizLabel="Challenge Quiz"
        noteTitle="Cardio Notes"
        section="results"
        isSecondCompletedSessionEver
        userId="user-7"
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: "Felt right" }));

    expect(screen.queryByText("How did that quiz feel?")).not.toBeInTheDocument();
    expect(submitFeedback).not.toHaveBeenCalled();
  });

  it.each(["Too easy", "Too hard", "Too repetitive"])("submits the %s feedback template", async (feedbackType) => {
    (submitFeedback as jest.Mock).mockResolvedValue({ message: "Thanks! Your feedback helps improve NoteLib." });
    render(
      <QuizFeedbackPanel
        quizLabel="Challenge Quiz"
        noteTitle="Cardio Notes"
        section="results"
        isSecondCompletedSessionEver
        userId={`user-${feedbackType}`}
      />,
    );

    fireEvent.click(screen.getByRole("button", { name: feedbackType }));
    fireEvent.click(screen.getByRole("button", { name: "Send Feedback" }));

    await waitFor(() => {
      expect(submitFeedback).toHaveBeenCalledWith(
        {
          message: [
            "Feedback type: Quiz Difficulty and Pacing",
            "Quiz: Challenge Quiz",
            "Context: Quiz Results",
            "Note: Cardio Notes",
            `Difficulty or pacing: ${feedbackType}`,
            "",
            "What felt off?",
          ].join("\n"),
        },
        "http://localhost/",
      );
    });
  });
});
