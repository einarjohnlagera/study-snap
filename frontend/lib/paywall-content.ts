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
  | "ADAPTIVE_PRACTICE"
  | "NOTE_GENERATION";

type PaywallProfileType =
  | "STUDENT"
  | "BOARD_EXAM"
  | "TEACHER"
  | "PARENT"
  | "PROFESSIONAL"
  | null
  | undefined;

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

const BASE_FREE_PAYWALL_CONTENT: Record<PaywallAction, FreePaywallContent> = {
  STUDY_PACK: {
    title: "You’ve reached your study pack limit",
    body: "You’ve used all your Study Packs for this month. Upgrade to Premium to create more study packs and continue turning your notes into summaries, key concepts, and quizzes.",
    feature: "study_pack_limit",
    dismissLabel: "Maybe Later",
  },
  QUIZ: {
    title: "You’ve reached your quiz limit",
    body: "You’ve used all your quizzes for this month. Upgrade to Premium to continue practicing with more quizzes and keep your review going.",
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
    title: "You’ve reached your quiz generation limit",
    body: "You’ve used all your quiz generations for this month. Upgrade to Premium to generate more quizzes and prepare materials for your class.",
    feature: "quiz_generation_limit",
    dismissLabel: "Maybe Later",
  },
  ADAPTIVE_PRACTICE: {
    title: "Adaptive Practice is a Premium feature",
    body: "Adaptive Practice focuses on your weak concepts. Upgrade to Premium to unlock targeted practice built around them.",
    feature: "adaptive",
    dismissLabel: "Maybe Later",
  },
  NOTE_GENERATION: {
    title: "You’ve reached your note generation limit",
    body: "You’ve used all your topic-based note generations for this month. Upgrade to Premium to generate more notes from topics.",
    feature: "note_generation_limit",
    dismissLabel: "Maybe Later",
  },
};

export const PREMIUM_EXHAUSTED_CONTENT: Record<PaywallAction, PremiumExhaustedContent> = {
  STUDY_PACK: {
    title: "You’ve reached your study pack limit for this month",
    body: "Your study pack limit resets on your next billing cycle.",
  },
  QUIZ: {
    title: "You’ve reached your quiz limit for this month",
    body: "Your quiz limit resets on your next billing cycle.",
  },
  BOARD_EXAM: {
    title: "You’ve reached your quiz limit for this month",
    body: "Board Exam Mode shares your quiz limit and will be available again after your next billing cycle.",
  },
  QUIZ_GENERATION: {
    title: "You’ve reached your quiz generation limit for this month",
    body: "Your quiz generation limit resets on your next billing cycle.",
  },
  ADAPTIVE_PRACTICE: {
    title: "You’ve reached your quiz limit for this month",
    body: "Adaptive Practice will be available again after your next billing cycle.",
  },
  NOTE_GENERATION: {
    title: "You’ve reached your note generation limit for this month",
    body: "Your topic-based note generation limit resets on your next billing cycle.",
  },
};

export const FREE_PAYWALL_CONTENT = BASE_FREE_PAYWALL_CONTENT;

export function resolveFreePaywallContent(
  action: PaywallAction,
  profileType: PaywallProfileType,
): FreePaywallContent {
  if (action === "QUIZ") {
    if (profileType === "BOARD_EXAM") {
      return {
        ...BASE_FREE_PAYWALL_CONTENT.QUIZ,
        body: "You’ve used all your quizzes for this month. Upgrade to Premium to continue practicing and access Board Exam mode.",
      };
    }
    return {
      ...BASE_FREE_PAYWALL_CONTENT.QUIZ,
      body: "You’ve used all your quizzes for this month. Upgrade to Premium to continue practicing and unlock Adaptive Practice.",
    };
  }
  if (action === "QUIZ_GENERATION") {
    return {
      ...BASE_FREE_PAYWALL_CONTENT.QUIZ_GENERATION,
      body: profileType === "TEACHER"
        ? "You’ve used all your quiz generations for this month. Upgrade to Premium to generate more quizzes and export materials for your class."
        : BASE_FREE_PAYWALL_CONTENT.QUIZ_GENERATION.body,
    };
  }
  return BASE_FREE_PAYWALL_CONTENT[action];
}
