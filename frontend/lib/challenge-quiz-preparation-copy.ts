/** Owner-approved wording: Q4 in the owner decisions doc and background +5 in
 * docs/claude-plans/2026-10-21-v0175-plus5-slower-models-proposal.md. */
export const CHALLENGE_PREPARATION_COPY = {
  continueEyebrow: "CONTINUE WHERE YOU LEFT OFF",
  readyTitle: "All set! Your quiz is ready.",
  preparedCardTitle: "Your Challenge Quiz is ready",
  generatingCardTitle: "We're getting your Challenge Quiz ready",
  startedCardTitle: "You have a Challenge Quiz in progress",
  redoPreparedCardTitle: "Your missed-question review is ready!",
  redoGeneratingCardTitle: "We're getting your missed-question review ready…",
  openPrepared: "Open prepared quiz",
  viewPreparation: "View preparation",
  resumeQuiz: "Resume quiz",
  startQuiz: "Start quiz",
  continueReviewing: "Continue reviewing",
  differentNotes: "Want to study different notes?",
  readyReminder: "Your timer and monthly attempt start only when you press Start quiz.",
  redoReadyReminder: "Your timer starts only when you press Start quiz. Redo Missed Questions doesn't count toward your monthly quiz limit.",
  questionCount: (count: number) => `${count} questions`,
  readyFacts: (count?: number, minutes?: number) => [
    count ? `${count} questions` : null, minutes ? `${minutes} minutes` : null,
  ].filter(Boolean).join(" · "),
  singleCardMeta: (count?: number, minutes?: number, title?: string) => [
    count ? `${count} questions` : null, minutes ? `${minutes} minutes` : null, title || null,
  ].filter(Boolean).join(" · "),
  multiCardMeta: (count: number | undefined, notes: number) => count
    ? `${count} questions from ${notes} notes, including this one` : `${notes} notes, including this one`,
  multiTitle: (title: string, additional: number) => `${title} + ${additional} more notes`,
  noteCount: (count: number) => `${count} notes`,
  collisionTitle: "You already have a quiz ready",
  collisionGeneratingTitle: "Your quiz is still being prepared",
  collisionBody: "You can return to your quiz or start something else. If you start something else, your unstarted quiz will be discarded without using a monthly attempt.",
  collisionResume: "Resume your prepared quiz",
  collisionReplace: "Start something else",
  dashboardLabel: "Open your prepared quiz",
  monthlyLimit: "This quiz counts toward your monthly limit only once you start it. Leaving before you start won't use an attempt.",
  underfilledTitle: (delivered: number, requested: number) => `Your quiz is ready with ${delivered} of ${requested} questions.`,
  // Owner-approved 2026-10-22: docs/claude-plans/2026-10-21-owner-decisions-v0175-open-questions-and-priorities.md.
  coverageLine: (contributing: number, selected: number) => `From ${contributing} of ${selected} notes`,
  moreAdding: "Adding questions…",
  moreStillAdding: "Still adding questions…",
  moreStop: "Stop adding",
  moreAdded: (count: number) => `Added ${count} more questions.`,
  moreFailed: "We couldn't add more questions. Your answers are safe.",
  moreRetry: "Try again",
  moreFinishingPrevious: "Finishing up your last set of questions…",
  redoMissedMonthlyLimit: "Redo Missed Questions doesn't count toward your monthly quiz limit.",
} as const;

export const challengeQuizMinutes = (seconds: number) => Math.ceil(seconds / 60);
