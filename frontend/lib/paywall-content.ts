/**
 * Shared action-aware paywall content.
 *
 * Centralises copy for two distinct gating surfaces:
 *   FREE_PAYWALL_CONTENT      — shown to free users when an action is gated (upgrade prompt)
 *   PREMIUM_EXHAUSTED_CONTENT — shown to premium users when monthly usage is exhausted
 *
 * Use `PaywallAction` as the canonical key so call sites are decoupled from
 * the modal variant string.
 */

export type PaywallAction =
  | "STUDY_PACK"
  | "QUIZ"
  | "BOARD_EXAM"
  | "QUIZ_GENERATION"
  | "ADAPTIVE_PRACTICE";

export type FreePaywallContent = {
  title: string;
  body: string;
  /** Analytics feature identifier */
  feature: string;
  dismissLabel: string;
};

export type PremiumExhaustedContent = {
  title: string;
  body: string;
};

export const FREE_PAYWALL_CONTENT: Record<PaywallAction, FreePaywallContent> = {
  STUDY_PACK: {
    title: "You've reached your monthly study pack limit",
    body: "Upgrade to Premium to create more study packs and continue turning your notes into summaries, key concepts, and quizzes.",
    feature: "study_pack_limit",
    dismissLabel: "Maybe Later",
  },
  QUIZ: {
    title: "You've reached your monthly quiz limit",
    body: "Upgrade to Premium to continue practicing with more quizzes and keep your review going.",
    feature: "quiz_limit",
    dismissLabel: "Maybe Later",
  },
  BOARD_EXAM: {
    title: "Board Exam Mode is a Premium feature",
    body: "Upgrade to Premium to access stricter exam-style practice designed for focused prep.",
    feature: "board_exam",
    dismissLabel: "Maybe Later",
  },
  QUIZ_GENERATION: {
    title: "You've reached your monthly quiz generation limit",
    body: "Upgrade to Premium to generate more quizzes and prepare materials for your class.",
    feature: "quiz_generation_limit",
    dismissLabel: "Maybe Later",
  },
  ADAPTIVE_PRACTICE: {
    title: "Adaptive Practice is a Premium feature",
    body: "Upgrade to Premium to unlock targeted practice based on your weak concepts.",
    feature: "adaptive",
    dismissLabel: "Maybe Later",
  },
};

export const PREMIUM_EXHAUSTED_CONTENT: Record<PaywallAction, PremiumExhaustedContent> = {
  STUDY_PACK: {
    title: "You've used all your study pack usage for this month",
    body: "Your monthly study pack usage will reset on your next billing cycle.",
  },
  QUIZ: {
    title: "You've used all your quiz usage for this month",
    body: "Your monthly quiz usage will reset on your next billing cycle.",
  },
  BOARD_EXAM: {
    title: "You've used all your quiz usage for this month",
    body: "Board Exam Mode shares your monthly quiz usage and will be available again after your reset.",
  },
  QUIZ_GENERATION: {
    title: "You've used all your quiz generation usage for this month",
    body: "Your monthly quiz generation usage will reset on your next billing cycle.",
  },
  ADAPTIVE_PRACTICE: {
    title: "You've used all your quiz usage for this month",
    body: "Adaptive Practice will be available again after your monthly reset.",
  },
};
