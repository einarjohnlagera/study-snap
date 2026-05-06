import type {
  ChallengeQuizSessionSummaryResponse,
  QuickReviewSessionSummaryResponse,
} from "@/lib/api";
import {
  buildRecentQuizSessionHistory,
  getQuizSessionModeLabel,
} from "@/lib/quiz-session-history";

function makeQuickReviewSession(
  overrides: Partial<QuickReviewSessionSummaryResponse> & { id: string },
): QuickReviewSessionSummaryResponse {
  return {
    studyPackId: "study-pack-1",
    totalQuestions: 10,
    correctAnswers: 8,
    scorePercentage: 80,
    retryCount: 1,
    durationSeconds: 120,
    createdAt: "2026-04-10T10:00:00Z",
    completedAt: "2026-04-10T10:05:00Z",
    ...overrides,
  };
}

function makeChallengeSession(
  overrides: Partial<ChallengeQuizSessionSummaryResponse> & { sessionId: string },
): ChallengeQuizSessionSummaryResponse {
  return {
    totalQuestions: 12,
    correctAnswers: 9,
    scorePercentage: 75,
    performanceLevel: "Good",
    conceptBreakdown: [],
    weakConcepts: [],
    createdAt: "2026-04-10T09:00:00Z",
    completedAt: "2026-04-10T09:10:00Z",
    ...overrides,
  };
}

describe("buildRecentQuizSessionHistory", () => {
  it("orders sessions by most recent completion first across quiz modes", () => {
    const quickReviewSessions = [
      makeQuickReviewSession({
        id: "quick-older",
        completedAt: "2026-04-09T10:00:00Z",
      }),
      makeQuickReviewSession({
        id: "quick-newer",
        completedAt: "2026-04-11T10:00:00Z",
      }),
    ];
    const challengeSessions = [
      makeChallengeSession({
        sessionId: "challenge-middle",
        completedAt: "2026-04-10T10:00:00Z",
      }),
    ];

    expect(buildRecentQuizSessionHistory(quickReviewSessions, challengeSessions).map((session) => session.sessionId)).toEqual([
      "quick-newer",
      "challenge-middle",
      "quick-older",
    ]);
  });

  it("preserves retry count for Quick Review and performance level for Challenge Quiz", () => {
    const [quickReview, challenge] = buildRecentQuizSessionHistory(
      [makeQuickReviewSession({ id: "quick", retryCount: 2 })],
      [makeChallengeSession({ sessionId: "challenge", performanceLevel: "Excellent" })],
    );

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
  });
});
