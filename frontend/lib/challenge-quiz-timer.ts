export const DEFAULT_CHALLENGE_QUIZ_TIME_LIMIT_SECONDS = 600;
export const BOARD_EXAM_TIMER_WARNING_THRESHOLD_SECONDS = 180;
export const BOARD_EXAM_TIMER_URGENT_THRESHOLD_SECONDS = 60;

export type ChallengeQuizSessionTimerState = {
  timerStartedAtEpochSeconds?: number;
};

export type BoardExamTimerState = "normal" | "warning" | "urgent" | "expired";

export function resolveTimerStartedAtEpochSeconds(
  sessionState: ChallengeQuizSessionTimerState | null | undefined,
  nowEpochSeconds: number,
): number {
  if (typeof sessionState?.timerStartedAtEpochSeconds === "number") {
    return sessionState.timerStartedAtEpochSeconds;
  }
  return nowEpochSeconds;
}

export function resolveDeadlineEpochSeconds(
  timeLimitSeconds: number,
  sessionState: ChallengeQuizSessionTimerState | null | undefined,
  nowEpochSeconds: number,
): number {
  const startedAtEpochSeconds = resolveTimerStartedAtEpochSeconds(sessionState, nowEpochSeconds);
  return startedAtEpochSeconds + timeLimitSeconds;
}

export function resolveRemainingSecondsFromDeadline(
  deadlineEpochSeconds: number,
  nowEpochSeconds: number,
): number {
  return Math.max(0, deadlineEpochSeconds - nowEpochSeconds);
}

export function resolveBoardExamTimerState(remainingSeconds: number): BoardExamTimerState {
  if (remainingSeconds <= 0) {
    return "expired";
  }
  if (remainingSeconds <= BOARD_EXAM_TIMER_URGENT_THRESHOLD_SECONDS) {
    return "urgent";
  }
  if (remainingSeconds <= BOARD_EXAM_TIMER_WARNING_THRESHOLD_SECONDS) {
    return "warning";
  }
  return "normal";
}
