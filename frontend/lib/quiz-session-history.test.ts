import type { RecentQuizSessionHistoryResponse } from "@/lib/api";
import {
  buildRecentQuizSessionHistory,
  getQuizSessionModeLabel,
} from "@/lib/quiz-session-history";

function makeSession(
  overrides: Partial<RecentQuizSessionHistoryResponse> & { sessionId: string },
): RecentQuizSessionHistoryResponse {
  return {
    sessionMode: "QUICK_REVIEW",
    totalQuestions: 10,
    correctAnswers: 8,
    scorePercentage: 80,
    retryCount: 1,
    performanceLevel: null,
    weakConcepts: [],
    participatingNoteCount: 1,
    createdAt: "2026-04-10T10:00:00Z",
    completedAt: "2026-04-10T10:05:00Z",
    ...overrides,
  };
}

describe("buildRecentQuizSessionHistory", () => {
  it("orders sessions by most recent completion first across quiz modes", () => {
    const sessions = [
      makeSession({
        sessionId: "quick-older",
        completedAt: "2026-04-09T10:00:00Z",
      }),
      makeSession({
        sessionId: "quick-newer",
        completedAt: "2026-04-11T10:00:00Z",
      }),
      makeSession({
        sessionId: "challenge-middle",
        sessionMode: "CHALLENGE",
        completedAt: "2026-04-10T10:00:00Z",
      }),
    ];

    expect(buildRecentQuizSessionHistory(sessions).map((session) => session.sessionId)).toEqual([
      "quick-newer",
      "challenge-middle",
      "quick-older",
    ]);
  });

  it("preserves retry count for Quick Review and performance level for Challenge Quiz", () => {
    const [quickReview, challenge] = buildRecentQuizSessionHistory([
      makeSession({ sessionId: "quick", retryCount: 2 }),
      makeSession({
        sessionId: "challenge",
        sessionMode: "CHALLENGE",
        performanceLevel: "Excellent",
      }),
    ]);

    expect(quickReview.sessionMode).toBe("QUICK_REVIEW");
    expect(quickReview.retryCount).toBe(2);
    expect(quickReview.performanceLevel).toBeNull();
    expect(challenge.sessionMode).toBe("CHALLENGE");
    expect(challenge.performanceLevel).toBe("Excellent");
  });
});

describe("getQuizSessionModeLabel", () => {
  it("returns the user-facing label for supported session modes", () => {
    expect(getQuizSessionModeLabel("QUICK_REVIEW")).toBe("Quick Review");
    expect(getQuizSessionModeLabel("CHALLENGE")).toBe("Challenge Quiz");
    expect(getQuizSessionModeLabel("ADAPTIVE")).toBe("Adaptive Practice");
    expect(getQuizSessionModeLabel("LONG_EXAM")).toBe("Long Exam");
    expect(getQuizSessionModeLabel("BOARD_EXAM")).toBe("Board Exam");
    expect(getQuizSessionModeLabel("INTERVIEW_PRACTICE")).toBe("Interview Practice");
  });
});
