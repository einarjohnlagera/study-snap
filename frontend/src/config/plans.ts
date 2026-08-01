import type { PaidPlanType, PlanType, ProfileType } from "@/lib/api";
import { pricingConfig } from "@/lib/pricing-config";

export type AppPlanType = Extract<PlanType, "FREE" | "PLUS" | "PRO">;

export type PlanFeature = {
  label: string;
  helper?: string;
};

export type ComparisonValue = "check" | string | null;

export type PlanComparisonRow = {
  label: string;
  values: Record<AppPlanType, ComparisonValue>;
};

const EXPORT_HELPER = "PDF/DOCX for offline or classroom use";
export const TEACHER_PLUS_EXPORT_CALLOUT = "Teachers get unlimited quiz exports on Plus.";

// One-time pass framing — paid plans are time-boxed passes, not recurring subscriptions.
export const PASS_MODEL_TAGLINE = "One-time payment · never auto-charged";
export const PASS_QUOTA_REFRESH_NOTE = "Usage limits refresh each month during your pass.";
export const PASS_DATA_PERMANENCE_NOTE = "Keep your notes, Study Packs, and progress after your pass ends.";
export const PASS_ALL_ACCESS_NOTE = "Full access on desktop and mobile web.";
export const PASS_NO_AUTO_CHARGE_FOOTER = "We never auto-charge — grab a pass again only when your next exam is near.";

export const PLAN_ORDER: AppPlanType[] = ["FREE", "PLUS", "PRO"];

export const PLANS: Record<AppPlanType, {
  name: string;
  title: string;
  description: string;
  ctaLabel: string;
  eyebrow?: string;
  adaptivePracticeMessage?: string;
  features: PlanFeature[];
  upgradeHighlights?: string[];
}> = {
  FREE: {
    name: "Free",
    title: "For getting started",
    description: "Create notes, generate Study Packs, and review basic concepts.",
    ctaLabel: "Get Started Free",
    adaptivePracticeMessage: `Taste Adaptive Practice — ${pricingConfig.free.adaptivePracticePerMonth} sessions / month`,
    features: [
      { label: `${pricingConfig.free.studyPacksPerMonth} Study Packs / month` },
      { label: `${pricingConfig.free.challengeQuizzesPerMonth} Quizzes / month` },
      { label: `Adaptive Practice (${pricingConfig.free.adaptivePracticePerMonth} sessions / month)` },
      { label: `${pricingConfig.free.docxExportsPerMonth} exports / month`, helper: EXPORT_HELPER },
      { label: `${pricingConfig.free.quizShareLinksPerMonth} shareable quiz links / month` },
      { label: "Summary + Key Concepts" },
    ],
    upgradeHighlights: [
      `Adaptive Practice (${pricingConfig.free.adaptivePracticePerMonth} free sessions / month)`,
      "Board Exam Mode",
      "Higher topic note limits",
    ],
  },
  PLUS: {
    name: "Plus",
    title: "Guided learning built around your notes",
    description: "Consistent, guided study with more room to keep going — always know what to review next.",
    ctaLabel: "Get Plus",
    adaptivePracticeMessage: "Train on weak areas (limited sessions)",
    features: [
      { label: `Keep building with ${pricingConfig.plus.studyPacksPerMonth} Study Packs each month` },
      { label: `Practice more often with ${pricingConfig.plus.challengeQuizzesPerMonth} quizzes each month` },
      { label: `Take ${pricingConfig.plus.docxExportsPerMonth} study resources offline each month`, helper: EXPORT_HELPER },
      { label: `Share ${pricingConfig.plus.quizShareLinksPerMonth} quizzes with classmates each month` },
      { label: `Target weak areas with ${pricingConfig.plus.adaptivePracticePerMonth} Adaptive Practice sessions each month` },
      { label: `Ask questions grounded in your Review Set Companion (${pricingConfig.plus.askCompanionPerMonth} sessions / month)` },
      { label: "Create more notes when your study load grows" },
    ],
  },
  PRO: {
    name: "Pro",
    title: "Your complete learning system",
    description: "The complete system for serious, sustained study — plan what's next, practice with confidence, and track real progress.",
    ctaLabel: "Get Pro",
    eyebrow: "Most popular",
    adaptivePracticeMessage: "Train on weak areas until you master them",
    features: [
      { label: `Build a deep library with ${pricingConfig.pro.studyPacksPerMonth} Study Packs each month` },
      { label: `Stay exam-ready with ${pricingConfig.pro.challengeQuizzesPerMonth} quizzes each month` },
      { label: "Export every study resource you need", helper: EXPORT_HELPER },
      { label: "Share unlimited quizzes with classmates" },
      { label: `Keep improving weak areas with ${pricingConfig.pro.adaptivePracticePerMonth} Adaptive Practice sessions each month` },
      { label: `Rehearse real answers with ${pricingConfig.pro.interviewPracticePerMonth} Interview Practice sessions each month` },
      { label: `Ask questions grounded in your Review Set Companion (${pricingConfig.pro.askCompanionPerMonth} sessions / month)` },
      { label: `Build stamina with ${pricingConfig.pro.longExamPerMonth} Long Exam sessions each month` },
      { label: `Prepare for boards with ${pricingConfig.pro.boardExamPerMonth} Board Exam sessions each month` },
    ],
  },
};

export const PLAN_COMPARISON_ROWS: PlanComparisonRow[] = [
  {
    label: "Best for",
    values: {
      FREE: "Light review and trying the core study loop",
      PLUS: "Regular study with more room to keep going",
      PRO: "Focused exam preparation and mastery practice",
    },
  },
  {
    label: "Study Packs / month",
    values: {
      FREE: String(pricingConfig.free.studyPacksPerMonth),
      PLUS: String(pricingConfig.plus.studyPacksPerMonth),
      PRO: String(pricingConfig.pro.studyPacksPerMonth),
    },
  },
  {
    label: "Quizzes / month",
    values: {
      FREE: String(pricingConfig.free.challengeQuizzesPerMonth),
      PLUS: String(pricingConfig.plus.challengeQuizzesPerMonth),
      PRO: String(pricingConfig.pro.challengeQuizzesPerMonth),
    },
  },
  {
    label: "Exports / month",
    values: {
      FREE: String(pricingConfig.free.docxExportsPerMonth),
      PLUS: String(pricingConfig.plus.docxExportsPerMonth),
      PRO: "Unlimited",
    },
  },
  {
    label: "Shareable quiz links / month",
    values: {
      FREE: String(pricingConfig.free.quizShareLinksPerMonth),
      PLUS: String(pricingConfig.plus.quizShareLinksPerMonth),
      PRO: "Unlimited",
    },
  },
  {
    label: "Summary + Key Concepts",
    values: {
      FREE: "check",
      PLUS: "check",
      PRO: "check",
    },
  },
  {
    label: "Topic notes",
    values: {
      FREE: "Limited",
      PLUS: "Higher",
      PRO: "Highest",
    },
  },
  {
    label: "Adaptive Practice",
    values: {
      FREE: `${pricingConfig.free.adaptivePracticePerMonth} sessions`,
      PLUS: `${pricingConfig.plus.adaptivePracticePerMonth} sessions`,
      PRO: `${pricingConfig.pro.adaptivePracticePerMonth} sessions`,
    },
  },
  {
    label: "Ask Companion",
    values: {
      FREE: null,
      PLUS: `${pricingConfig.plus.askCompanionPerMonth} sessions`,
      PRO: `${pricingConfig.pro.askCompanionPerMonth} sessions`,
    },
  },
  {
    label: "Interview Practice",
    values: {
      FREE: null,
      PLUS: null,
      PRO: `${pricingConfig.pro.interviewPracticePerMonth} sessions`,
    },
  },
  {
    label: "Board Exam Mode",
    values: {
      FREE: null,
      PLUS: null,
      PRO: `${pricingConfig.pro.boardExamPerMonth} sessions`,
    },
  },
  {
    label: "Long Exam Mode",
    values: {
      FREE: null,
      PLUS: null,
      PRO: `${pricingConfig.pro.longExamPerMonth} sessions`,
    },
  },
];

export function getPlanConfig(planType: AppPlanType) {
  return PLANS[planType];
}

export function getPaidPlanCtaLabel(planType: PaidPlanType) {
  return PLANS[planType].ctaLabel;
}

export type UpgradeCta = {
  label: string;
  targetPlan: PaidPlanType;
};

export type UpgradeCtaSet = {
  primary: UpgradeCta | null;
  secondary: UpgradeCta | null;
};

export type UpgradeCtaContext =
  | "study-pack-limit"
  | "note-generation-limit"
  | "adaptive-practice"
  | "interview-practice"
  | "ask-companion"
  | "board-exam-mode"
  | "long-exam-mode"
  | "concept-review-timing"
  | "teacher-quiz-limit"
  | "teacher-export-limit"
  | "teacher-quiz-question-count"
  | "teacher-exam-versions"
  | "pass-renewal"
  | "general";

export type UpgradeCtaOptions = {
  context?: UpgradeCtaContext;
  profileType?: ProfileType | null;
};

function isTeacherUpgradeContext(context: UpgradeCtaContext | undefined): boolean {
  return context === "teacher-quiz-limit"
    || context === "teacher-export-limit"
    || context === "teacher-quiz-question-count"
    || context === "teacher-exam-versions";
}

function resolveUpgradeCtaOptions(contextOrOptions?: UpgradeCtaContext | UpgradeCtaOptions): UpgradeCtaOptions {
  if (typeof contextOrOptions === "string") {
    return { context: contextOrOptions };
  }
  return contextOrOptions ?? {};
}

export function getUpgradeCtas(
  currentPlan: AppPlanType,
  contextOrOptions?: UpgradeCtaContext | UpgradeCtaOptions,
): UpgradeCtaSet {
  const { context, profileType } = resolveUpgradeCtaOptions(contextOrOptions);
  const isTeacherUpgrade = profileType === "TEACHER" || isTeacherUpgradeContext(context);
  if (currentPlan === "FREE") {
    if (context === "teacher-quiz-question-count") {
      return {
        primary: { label: "Unlock 20- and 30-question quizzes", targetPlan: "PLUS" },
        secondary: { label: "Go Pro", targetPlan: "PRO" },
      };
    }
    if (context === "teacher-exam-versions") {
      return {
        primary: { label: "Unlock multiple exam versions", targetPlan: "PLUS" },
        secondary: { label: "Go Pro", targetPlan: "PRO" },
      };
    }
    if (isTeacherUpgrade) {
      return {
        primary: { label: "Unlock more exports — get Plus", targetPlan: "PLUS" },
        secondary: { label: "Go Pro", targetPlan: "PRO" },
      };
    }
    if (context === "adaptive-practice") {
      return {
        primary: { label: "Get More Adaptive Practice", targetPlan: "PLUS" },
        secondary: { label: "Go Pro", targetPlan: "PRO" },
      };
    }
    if (context === "concept-review-timing") {
      return {
        primary: { label: "See review timing — get Plus", targetPlan: "PLUS" },
        secondary: { label: "Go Pro", targetPlan: "PRO" },
      };
    }
    if (context === "interview-practice") {
      return {
        primary: { label: "Unlock Interview Practice", targetPlan: "PRO" },
        secondary: null,
      };
    }
    if (context === "ask-companion") {
      return {
        primary: { label: "Unlock Ask Companion — get Plus", targetPlan: "PLUS" },
        secondary: { label: "Go Pro", targetPlan: "PRO" },
      };
    }
    if (context === "board-exam-mode") {
      return {
        primary: { label: "Unlock Board Exam Mode", targetPlan: "PRO" },
        secondary: null,
      };
    }
    if (context === "long-exam-mode") {
      return {
        primary: { label: "Unlock the Long Exam", targetPlan: "PRO" },
        secondary: null,
      };
    }
    if (context === "study-pack-limit") {
      return {
        primary: { label: "Get More Study Packs", targetPlan: "PLUS" },
        secondary: null,
      };
    }
    return {
      primary: { label: "Upgrade to Plus", targetPlan: "PLUS" },
      secondary: { label: "Upgrade to Pro", targetPlan: "PRO" },
    };
  }
  if (currentPlan === "PLUS") {
    if (context === "pass-renewal") {
      return {
        primary: { label: "Get another Plus pass", targetPlan: "PLUS" },
        secondary: null,
      };
    }
    if (isTeacherUpgrade) {
      return {
        primary: { label: "Get more Study Packs & quiz generations with Pro", targetPlan: "PRO" },
        secondary: null,
      };
    }
    if (context === "interview-practice") {
      return {
        primary: { label: "Unlock Interview Practice", targetPlan: "PRO" },
        secondary: null,
      };
    }
    if (context === "board-exam-mode") {
      return {
        primary: { label: "Unlock Board Exam Mode", targetPlan: "PRO" },
        secondary: null,
      };
    }
    if (context === "long-exam-mode") {
      return {
        primary: { label: "Unlock the Long Exam", targetPlan: "PRO" },
        secondary: null,
      };
    }
    if (context === "study-pack-limit") {
      return {
        primary: { label: "Upgrade to Pro", targetPlan: "PRO" },
        secondary: null,
      };
    }
    if (context === "note-generation-limit") {
      return {
        primary: null,
        secondary: null,
      };
    }
    return {
      primary: { label: "Upgrade to Pro", targetPlan: "PRO" },
      secondary: null,
    };
  }
  if (context === "pass-renewal") {
    return {
      primary: { label: "Get another Pro pass", targetPlan: "PRO" },
      secondary: null,
    };
  }
  return { primary: null, secondary: null };
}
