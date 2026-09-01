import type { RecentQuizSessionHistoryResponse } from "@/lib/api";
import {
  buildRecentQuizSessionHistory,
  getQuizSessionModeLabel,
} from "@/lib/quiz-session-history";
import {
  GENERATED_QUIZZES_PRICING_NOUN,
  QUIZ_GENERATIONS_USAGE_DESCRIPTION,
  QUIZ_GENERATIONS_USAGE_LABEL,
} from "@/lib/usage-labels";

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

  it("keeps the Challenge Quiz MODE name distinct from the quiz-generation QUOTA label", () => {
    // ⚠️ These two strings are deliberately different and must stay that way.
    // The quota meter is spent by Challenge Quiz, Board Exam AND "Quiz for someone", so it is named
    // for the metered act — but the practice MODE is still called Challenge Quiz everywhere it names
    // the mode. A global find-and-replace unifying them would destroy the distinction, which is the
    // failure this pins. Other tests cover the mode name incidentally; this one records the intent.
    expect(getQuizSessionModeLabel("CHALLENGE")).toBe("Challenge Quiz");
    expect(QUIZ_GENERATIONS_USAGE_LABEL).toBe("Quiz generations");
    expect(getQuizSessionModeLabel("CHALLENGE")).not.toBe(QUIZ_GENERATIONS_USAGE_LABEL);
  });

  it("keeps the meter label separate from the pricing noun, so a meter fix cannot rewrite pricing", () => {
    // ⚠️ These were ONE constant until v0.101.0, interpolated into both the Settings meter and four
    // pricing strings — so renaming the meter silently rewrote public pricing copy. They are split
    // deliberately: the meter names the metered ACT, pricing names a COUNT of things you get.
    // Re-merging them is the regression this pins.
    expect(QUIZ_GENERATIONS_USAGE_LABEL).not.toBe(GENERATED_QUIZZES_PRICING_NOUN);
    expect(GENERATED_QUIZZES_PRICING_NOUN).toBe("generated quizzes");
  });

  it("describes the quiz-generation meter without enumerating modes or allowances", () => {
    // ⚠️ Two independent failures are pinned here, both of which a plausible reword reintroduces.
    // (a) MODE-AGNOSTIC: a later multi-note session for Free and Plus rides the Challenge engine and
    //     spends this same meter, so naming today's modes goes stale the day that ships.
    // (b) NO ALLOWANCE LIST: Board Exam spends BOTH this meter and its own, and Settings renders a
    //     Board Exam row directly beneath this description — so any "X, Y and Z have their own
    //     allowances" list is falsified by a row the reader is already looking at. The description
    //     discloses the double-spend instead.
    expect(QUIZ_GENERATIONS_USAGE_DESCRIPTION).not.toMatch(/Challenge Quiz/);
    expect(QUIZ_GENERATIONS_USAGE_DESCRIPTION).not.toMatch(/have their own allowances/);
    expect(QUIZ_GENERATIONS_USAGE_DESCRIPTION).toMatch(/Board Exam sessions also count against their own allowance/);
  });
});
