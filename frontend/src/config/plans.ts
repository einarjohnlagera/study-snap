import type { PaidPlanType, PlanType } from "@/lib/api";
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
    features: [
      { label: `${pricingConfig.free.studyPacksPerMonth} Study Packs / month` },
      { label: `${pricingConfig.free.challengeQuizzesPerMonth} Quizzes / month` },
      { label: `${pricingConfig.free.exportsPerMonth} exports / month`, helper: EXPORT_HELPER },
      { label: "Summary + Key Concepts" },
    ],
    upgradeHighlights: [
      `Adaptive Practice (${pricingConfig.plus.adaptivePracticePerMonth} sessions / month)`,
      "Difficulty selection",
      "Board Exam Mode",
      "Higher note generation limits",
    ],
  },
  PLUS: {
    name: "Plus",
    title: "For regular study",
    description: "Perfect for students who want consistent review and better retention.",
    ctaLabel: "Choose Plus",
    adaptivePracticeMessage: "Train on weak areas (limited sessions)",
    features: [
      { label: `${pricingConfig.plus.studyPacksPerMonth} Study Packs / month` },
      { label: `${pricingConfig.plus.challengeQuizzesPerMonth} Quizzes / month` },
      { label: `${pricingConfig.plus.exportsPerMonth} exports / month`, helper: EXPORT_HELPER },
      { label: `Adaptive Practice (${pricingConfig.plus.adaptivePracticePerMonth} sessions / month)` },
      { label: "Higher note generation limits" },
    ],
  },
  PRO: {
    name: "Pro",
    title: "Best for exam prep",
    description: "Designed for serious learners preparing for board and entrance exams.",
    ctaLabel: "Go Pro",
    eyebrow: "Most popular",
    adaptivePracticeMessage: "Train on weak areas until you master them",
    features: [
      { label: `${pricingConfig.pro.studyPacksPerMonth} Study Packs / month` },
      { label: `${pricingConfig.pro.challengeQuizzesPerMonth} Quizzes / month` },
      { label: "Unlimited exports", helper: EXPORT_HELPER },
      { label: `Adaptive Practice (${pricingConfig.pro.adaptivePracticePerMonth} sessions / month)` },
      { label: "Difficulty selection" },
      { label: "Board Exam Mode" },
    ],
  },
};

export const PLAN_COMPARISON_ROWS: PlanComparisonRow[] = [
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
      FREE: String(pricingConfig.free.exportsPerMonth),
      PLUS: String(pricingConfig.plus.exportsPerMonth),
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
    label: "Topic note generation",
    values: {
      FREE: "Limited",
      PLUS: "Higher",
      PRO: "Highest",
    },
  },
  {
    label: "Adaptive Practice",
    values: {
      FREE: null,
      PLUS: `${pricingConfig.plus.adaptivePracticePerMonth} sessions`,
      PRO: `${pricingConfig.pro.adaptivePracticePerMonth} sessions`,
    },
  },
  {
    label: "Difficulty selection",
    values: {
      FREE: null,
      PLUS: null,
      PRO: "check",
    },
  },
  {
    label: "Board Exam Mode",
    values: {
      FREE: null,
      PLUS: null,
      PRO: "check",
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

export function getUpgradeCtas(currentPlan: AppPlanType): UpgradeCtaSet {
  if (currentPlan === "FREE") {
    return {
      primary: { label: "Upgrade to Plus", targetPlan: "PLUS" },
      secondary: { label: "Go Pro", targetPlan: "PRO" },
    };
  }
  if (currentPlan === "PLUS") {
    return {
      primary: { label: "Upgrade to Pro", targetPlan: "PRO" },
      secondary: null,
    };
  }
  return { primary: null, secondary: null };
}
