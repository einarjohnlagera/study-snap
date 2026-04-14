import { fireEvent, render, screen } from "@testing-library/react";
import { QuizSessionHistory } from "./quiz-session-history";
import type { QuizSessionReviewResponse } from "@/lib/api";
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
    createdAt: "2026-04-10T10:00:00Z",
    completedAt: "2026-04-10T10:12:00Z",
  },
];

const activeReview: QuizSessionReviewResponse = {
  sessionId: "quick-1",
  studyPackId: "study-pack-1",
  sessionMode: "QUICK_REVIEW",
  status: "COMPLETED",
  totalQuestions: 10,
  correctAnswers: 8,
  scorePercentage: 80,
  retryCount: 1,
  durationSeconds: 120,
  weakConcepts: ["Cells"],
  conceptBreakdown: [
    {
      concept: "Cells",
      correctAnswers: 1,
      totalQuestions: 2,
      accuracyPercentage: 50,
    },
  ],
  quiz: [
    {
      question: "What powers the cell?",
      choices: ["Mitochondria", "Nucleus", "Ribosome", "Cell wall"],
      correctIndex: 0,
      concept: "Cells",
      explanation: "Mitochondria produce ATP for the cell.",
    },
  ],
  selectedChoices: { "0": 1 },
  createdAt: "2026-04-11T10:00:00Z",
  completedAt: "2026-04-11T10:05:00Z",
};

describe("QuizSessionHistory", () => {
  it("shows the empty state when no completed sessions exist", () => {
    render(
      <QuizSessionHistory
        sessions={[]}
        activeSessionId={null}
        activeReview={null}
        loadingReview={false}
        reviewError={null}
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
        activeSessionId={null}
        activeReview={null}
        loadingReview={false}
        reviewError={null}
        onSelectSession={onSelectSession}
      />,
    );

    expect(screen.getByText("Quick Review")).toBeInTheDocument();
    expect(screen.getByText("Challenge Quiz")).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: /Quick Review/i }));

    expect(onSelectSession).toHaveBeenCalledWith(historyItems[0]);
  });

  it("renders the active session review with answer correctness and concept breakdown", () => {
    render(
      <QuizSessionHistory
        sessions={historyItems}
        activeSessionId="quick-1"
        activeReview={activeReview}
        loadingReview={false}
        reviewError={null}
        onSelectSession={jest.fn()}
      />,
    );

    expect(screen.getByText("Session Review")).toBeInTheDocument();
    expect(screen.getByText("What powers the cell?")).toBeInTheDocument();
    expect(screen.getByText("Concept: Cells")).toBeInTheDocument();
    expect(screen.getAllByText("Incorrect")).toHaveLength(2);
    expect(screen.getByText("Weak Concepts")).toBeInTheDocument();
  });

  it("degrades gracefully when a legacy session lacks quiz detail", () => {
    render(
      <QuizSessionHistory
        sessions={historyItems}
        activeSessionId="challenge-1"
        activeReview={{
          ...activeReview,
          sessionId: "challenge-1",
          sessionMode: "CHALLENGE",
          quiz: [],
        }}
        loadingReview={false}
        reviewError={null}
        onSelectSession={jest.fn()}
      />,
    );

    expect(screen.getByText("Detailed answer review is unavailable for this session.")).toBeInTheDocument();
    expect(screen.getByText("Concept Breakdown")).toBeInTheDocument();
  });
});
