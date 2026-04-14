import type {
  ChallengeQuizSessionSummaryResponse,
  QuickReviewSessionSummaryResponse,
  QuizSessionMode,
} from "@/lib/api";

export type RecentQuizSessionHistoryItem = {
  sessionId: string;
  sessionMode: Extract<QuizSessionMode, "QUICK_REVIEW" | "CHALLENGE">;
  totalQuestions: number;
  correctAnswers: number;
  scorePercentage: number;
  retryCount: number;
  performanceLevel: string | null;
  weakConcepts: string[];
  createdAt: string;
  completedAt: string | null;
};

function compareCompletedAtDesc(left: string | null, right: string | null): number {
  return new Date(right ?? 0).getTime() - new Date(left ?? 0).getTime();
}

export function buildRecentQuizSessionHistory(
  quickReviewSessions: QuickReviewSessionSummaryResponse[],
  challengeSessions: ChallengeQuizSessionSummaryResponse[],
): RecentQuizSessionHistoryItem[] {
  const quickSessions = quickReviewSessions.map((session) => ({
    sessionId: session.id,
    sessionMode: "QUICK_REVIEW" as const,
    totalQuestions: session.totalQuestions,
    correctAnswers: session.correctAnswers,
    scorePercentage: session.scorePercentage,
    retryCount: session.retryCount,
    performanceLevel: null,
    weakConcepts: session.weakConcepts ?? [],
    createdAt: session.createdAt,
    completedAt: session.completedAt,
  }));
  const challengeQuizItems = challengeSessions.map((session) => ({
    sessionId: session.sessionId,
    sessionMode: "CHALLENGE" as const,
    totalQuestions: session.totalQuestions,
    correctAnswers: session.correctAnswers,
    scorePercentage: session.scorePercentage,
    retryCount: 0,
    performanceLevel: session.performanceLevel,
    weakConcepts: session.weakConcepts,
    createdAt: session.createdAt,
    completedAt: session.completedAt,
  }));

  return [...quickSessions, ...challengeQuizItems].sort((left, right) => {
    const completedAtDiff = compareCompletedAtDesc(left.completedAt, right.completedAt);
    if (completedAtDiff !== 0) {
      return completedAtDiff;
    }
    return compareCompletedAtDesc(left.createdAt, right.createdAt);
  });
}

export function getQuizSessionModeLabel(sessionMode: RecentQuizSessionHistoryItem["sessionMode"]): string {
  return sessionMode === "CHALLENGE" ? "Challenge Quiz" : "Quick Review";
}
