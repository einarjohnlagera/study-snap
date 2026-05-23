import type { QuizSessionHistoryMode, RecentQuizSessionHistoryResponse } from "@/lib/api";

export type QuizSessionModeForHistory = QuizSessionHistoryMode;

export type RecentQuizSessionHistoryItem = RecentQuizSessionHistoryResponse;

function compareCompletedAtDesc(left: string | null, right: string | null): number {
  return new Date(right ?? 0).getTime() - new Date(left ?? 0).getTime();
}

export function buildRecentQuizSessionHistory(
  sessions: RecentQuizSessionHistoryResponse[],
): RecentQuizSessionHistoryItem[] {
  return [...sessions].sort((left, right) => {
    const completedAtDiff = compareCompletedAtDesc(left.completedAt, right.completedAt);
    if (completedAtDiff !== 0) {
      return completedAtDiff;
    }
    return compareCompletedAtDesc(left.createdAt, right.createdAt);
  });
}

export function getQuizSessionModeLabel(sessionMode: QuizSessionModeForHistory): string {
  switch (sessionMode) {
    case "QUICK_REVIEW":
      return "Quick Review";
    case "CHALLENGE":
      return "Challenge Quiz";
    case "ADAPTIVE":
      return "Adaptive Practice";
    case "LONG_EXAM":
      return "Long Exam";
    case "BOARD_EXAM":
      return "Board Exam";
    case "INTERVIEW_PRACTICE":
      return "Interview Practice";
  }
}
