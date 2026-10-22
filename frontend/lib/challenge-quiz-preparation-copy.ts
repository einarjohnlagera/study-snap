/** Owner-approved wording, 2026-10-21 (docs/claude-plans/2026-10-21-owner-decisions-v0175-open-questions-and-priorities.md, Q4). */
export const CHALLENGE_PREPARATION_COPY = {
  collisionTitle: "You already have a quiz ready",
  collisionGeneratingTitle: "Your quiz is still being prepared",
  collisionBody: "You can return to your quiz or start something else. If you start something else, your unstarted quiz will be discarded without using a monthly attempt.",
  collisionResume: "Resume your prepared quiz",
  collisionReplace: "Start something else",
  dashboardLabel: "Open your prepared quiz",
  monthlyLimit: "This quiz counts toward your monthly limit only when you press Start Quiz. Leaving before you start won't use an attempt.",
  underfilledTitle: (delivered: number, requested: number) => `Your quiz is ready with ${delivered} of ${requested} questions.`,
  // Provisional wording; pending owner sign-off.
  coverageLine: (contributing: number, selected: number) => `From ${contributing} of ${selected} notes`,
  redoMissedMonthlyLimit: "Redo Missed Questions doesn't count toward your monthly quiz limit.",
} as const;
