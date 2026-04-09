import {
  BOARD_EXAM_TIMER_URGENT_THRESHOLD_SECONDS,
  BOARD_EXAM_TIMER_WARNING_THRESHOLD_SECONDS,
  resolveBoardExamTimerState,
  resolveDeadlineEpochSeconds,
  resolveRemainingSecondsFromDeadline,
  resolveTimerStartedAtEpochSeconds,
} from "@/lib/challenge-quiz-timer";

describe("challenge-quiz-timer", () => {
  it("uses the persisted timer start when available", () => {
    expect(resolveTimerStartedAtEpochSeconds({ timerStartedAtEpochSeconds: 1_720_000_000 }, 1_720_000_100)).toBe(1_720_000_000);
  });

  it("falls back to the current time when persisted timer start is missing", () => {
    expect(resolveTimerStartedAtEpochSeconds({}, 1_720_000_100)).toBe(1_720_000_100);
  });

  it("derives the deadline from the persisted start plus duration", () => {
    expect(resolveDeadlineEpochSeconds(600, { timerStartedAtEpochSeconds: 1_720_000_000 }, 1_720_000_100)).toBe(1_720_000_600);
  });

  it("derives remaining time from the deadline instead of local countdown state", () => {
    expect(resolveRemainingSecondsFromDeadline(1_720_000_600, 1_720_000_120)).toBe(480);
  });

  it("never returns negative remaining time", () => {
    expect(resolveRemainingSecondsFromDeadline(1_720_000_600, 1_720_000_999)).toBe(0);
  });

  it("returns normal state above the warning threshold", () => {
    expect(resolveBoardExamTimerState(BOARD_EXAM_TIMER_WARNING_THRESHOLD_SECONDS + 1)).toBe("normal");
  });

  it("returns warning state at and below the warning threshold", () => {
    expect(resolveBoardExamTimerState(BOARD_EXAM_TIMER_WARNING_THRESHOLD_SECONDS)).toBe("warning");
  });

  it("returns urgent state at and below the urgent threshold", () => {
    expect(resolveBoardExamTimerState(BOARD_EXAM_TIMER_URGENT_THRESHOLD_SECONDS)).toBe("urgent");
  });

  it("returns expired state when time is gone", () => {
    expect(resolveBoardExamTimerState(0)).toBe("expired");
  });
});
