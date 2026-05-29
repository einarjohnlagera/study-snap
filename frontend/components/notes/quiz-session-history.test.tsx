import { fireEvent, render, screen } from "@testing-library/react";
import { QuizSessionHistory } from "./quiz-session-history";
import type { RecentQuizSessionHistoryItem } from "@/lib/quiz-session-history";

const historyItems: RecentQuizSessionHistoryItem[] = [
  {
    sessionId: "quick-1",
    sessionMode: "QUICK_REVIEW",
    totalQuestions: 10,
    correctAnswers: 8,
    scorePercentage: 80,
    retryCount: 1,
    performanceLevel: null,
    weakConcepts: ["Cells"],
    participatingNoteCount: 1,
    createdAt: "2026-04-11T10:00:00Z",
    completedAt: "2026-04-11T10:05:00Z",
  },
  {
    sessionId: "challenge-1",
    sessionMode: "CHALLENGE",
    totalQuestions: 12,
    correctAnswers: 7,
    scorePercentage: 58,
    retryCount: 0,
    performanceLevel: "Fair",
    weakConcepts: ["Photosynthesis"],
    participatingNoteCount: 1,
    createdAt: "2026-04-10T10:00:00Z",
    completedAt: "2026-04-10T10:12:00Z",
  },
];

describe("QuizSessionHistory", () => {
  it("shows the empty state when no completed sessions exist", () => {
    render(
      <QuizSessionHistory
        sessions={[]}
        onSelectSession={jest.fn()}
      />,
    );

    expect(screen.getByText("No completed quiz sessions yet.")).toBeInTheDocument();
    expect(screen.getByText("Start a quiz to begin tracking your progress.")).toBeInTheDocument();
  });

  it("renders recent sessions and lets the user select one for review", () => {
    const onSelectSession = jest.fn();

    render(
      <QuizSessionHistory
        sessions={historyItems}
        onSelectSession={onSelectSession}
      />,
    );

    expect(screen.getByText("Quick Review")).toBeInTheDocument();
    expect(screen.getByText("Challenge Quiz")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Quick Review/i }));

    expect(onSelectSession).toHaveBeenCalledWith(historyItems[0]);
  });

  it("keeps the recent sessions list as the navigation entry point without inline review state", () => {
    render(
      <QuizSessionHistory
        sessions={historyItems}
        onSelectSession={jest.fn()}
      />,
    );

    expect(screen.queryByText("Currently reviewing")).not.toBeInTheDocument();
    expect(screen.queryByText("Loading session review...")).not.toBeInTheDocument();
    expect(screen.queryByText("Select a session to review answers and concept performance.")).not.toBeInTheDocument();
    expect(screen.getAllByText("Review session")).toHaveLength(2);
  });

  it("shows the note span for multi-note Long Exam history", () => {
    render(
      <QuizSessionHistory
        sessions={[{
          ...historyItems[0],
          sessionId: "long-exam-1",
          sessionMode: "LONG_EXAM",
          participatingNoteCount: 3,
        }]}
        onSelectSession={jest.fn()}
      />,
    );

    expect(screen.getByText("Long Exam")).toBeInTheDocument();
    expect(screen.getByText("Multi-note Long Exam · spans 3 notes")).toBeInTheDocument();
  });

  it("shows the note span for multi-note Board Exam history", () => {
    render(
      <QuizSessionHistory
        sessions={[{
          ...historyItems[0],
          sessionId: "board-exam-1",
          sessionMode: "BOARD_EXAM",
          participatingNoteCount: 2,
        }]}
        onSelectSession={jest.fn()}
      />,
    );

    expect(screen.getByText("Board Exam")).toBeInTheDocument();
    expect(screen.getByText("Multi-note Board Exam · spans 2 notes")).toBeInTheDocument();
  });
});
