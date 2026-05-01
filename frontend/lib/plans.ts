import type { PlanType } from "./api";

export const PLAN_BILLING_SECTION_ID = "plan-billing";
export const PLAN_BILLING_PATH = `/settings#${PLAN_BILLING_SECTION_ID}` as const;
export const PRICING_PAGE_PATH = "/pricing" as const;
type StudyPackDateLike = {
  createdAt: string;
};

export function getCurrentMonthStudyPackUsage<T extends StudyPackDateLike>(
  studyPacks: T[],
  referenceDate: Date = new Date(),
): number {
  const year = referenceDate.getFullYear();
  const month = referenceDate.getMonth();

  return studyPacks.reduce((count, studyPack) => {
    const createdAt = new Date(studyPack.createdAt);
    if (Number.isNaN(createdAt.getTime())) {
      return count;
    }
    if (createdAt.getFullYear() !== year || createdAt.getMonth() !== month) {
      return count;
    }
    return count + 1;
  }, 0);
}

export function getUsageProgressPercent(used: number, limit: number): number {
  if (limit <= 0) {
    return 0;
  }
  const ratio = (used / limit) * 100;
  return Math.max(0, Math.min(100, Math.round(ratio)));
}

export function resolveRemainingUsageCredits(
  used: number,
  limit: number,
  remainingCredits: number | null | undefined,
): number {
  if (typeof remainingCredits === "number") {
    return Math.max(0, remainingCredits);
  }
  return Math.max(0, limit - used);
}

export function hasReachedUsageLimit(used: number, limit: number): boolean {
  return limit > 0 && used >= limit;
}

export function isNearUsageLimit(used: number, limit: number): boolean {
  if (limit <= 0 || hasReachedUsageLimit(used, limit)) {
    return false;
  }
  return Math.max(0, limit - used) <= 2;
}

export function isStudyPackLimitReached(remainingCredits: number | null | undefined): boolean {
  return typeof remainingCredits === "number" && remainingCredits <= 0;
}

export function shouldShowNearStudyPackLimitBanner(
  planType: PlanType,
  remainingCredits: number | null | undefined,
): boolean {
  return (planType === "FREE" || planType === "PLUS" || planType === "PRO")
    && typeof remainingCredits === "number"
    && remainingCredits <= 2;
}

export function isPaidPlanType(planType: PlanType | null | undefined): planType is "PLUS" | "PRO" {
  return planType === "PLUS" || planType === "PRO";
}

export function isProPlanType(planType: PlanType | null | undefined): planType is "PRO" {
  return planType === "PRO";
}

export function getPlanDisplayName(planType: PlanType | null | undefined): string {
  switch (planType) {
    case "PLUS":
      return "Plus";
    case "PRO":
      return "Pro";
    default:
      return "Free";
  }
}

export function formatStudyPackResetDate(rawDate: string | null | undefined): string {
  if (!rawDate) {
    return "your reset date";
  }
  const value = new Date(rawDate);
  if (Number.isNaN(value.getTime())) {
    return rawDate;
  }
  return value.toLocaleDateString(undefined, { month: "long", day: "numeric" });
}

export function isStudyPackLimitReachedMessage(message: string): boolean {
  const normalized = message.toLowerCase();
  return (
    (normalized.includes("free plan limit") && normalized.includes("study pack"))
    || (normalized.includes("study pack limit") && normalized.includes("upgrade"))
    || normalized.includes("study pack generations per month")
  );
}

export function isQuizLimitReachedMessage(message: string): boolean {
  const normalized = message.toLowerCase();
  return (
    (normalized.includes("quiz") && normalized.includes("limit"))
    || normalized.includes("monthly quiz credit limit")
    || normalized.includes("challenge quizzes for this month")
  );
}
