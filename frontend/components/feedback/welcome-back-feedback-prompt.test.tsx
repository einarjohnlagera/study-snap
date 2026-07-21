import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QuizFeedbackPanel } from "./quiz-feedback-panel";
import { WelcomeBackFeedbackPrompt } from "./welcome-back-feedback-prompt";

describe("WelcomeBackFeedbackPrompt", () => {
  beforeEach(() => {
    globalThis.localStorage.clear();
    globalThis.sessionStorage.clear();
  });

  it("renders only for a returning user with prior quiz history", async () => {
    const view = render(
      <WelcomeBackFeedbackPrompt
        userId="user-1"
        returningAfterInactivity={false}
        hasCompletedQuizSession
      />,
    );
    expect(screen.queryByText("Welcome back — what got in the way?")).not.toBeInTheDocument();

    view.unmount();
    render(
      <WelcomeBackFeedbackPrompt
        userId="user-1"
        returningAfterInactivity
        hasCompletedQuizSession
      />,
    );
    expect(await screen.findByText("Welcome back — what got in the way?")).toBeInTheDocument();
  });

  it("marks dismissal seen and does not show again after reload", async () => {
    const view = render(
      <WelcomeBackFeedbackPrompt
        userId="user-1"
        returningAfterInactivity
        hasCompletedQuizSession
      />,
    );
    expect(await screen.findByText("Welcome back — what got in the way?")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Dismiss feedback prompt" }));
    view.unmount();

    render(
      <WelcomeBackFeedbackPrompt
        userId="user-1"
        returningAfterInactivity
        hasCompletedQuizSession
      />,
    );
    await waitFor(() => {
      expect(screen.queryByText("Welcome back — what got in the way?")).not.toBeInTheDocument();
    });
  });

  it("prioritizes the first-quiz ask when both trigger conditions occur on one return", async () => {
    const firstQuizView = render(
      <QuizFeedbackPanel
        quizLabel="Quick Review"
        section="results"
        isFirstCompletedSessionEver
        userId="user-1"
      />,
    );
    expect(await screen.findByText("How did your first quiz go?")).toBeInTheDocument();
    firstQuizView.unmount();

    render(
      <WelcomeBackFeedbackPrompt
        userId="user-1"
        returningAfterInactivity
        hasCompletedQuizSession
      />,
    );
    expect(screen.queryByText("Welcome back — what got in the way?")).not.toBeInTheDocument();
  });
});
