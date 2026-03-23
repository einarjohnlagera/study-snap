import type { PlanType } from "./api";

export const PLAN_BILLING_SECTION_ID = "plan-billing";
export const PLAN_BILLING_PATH = `/settings#${PLAN_BILLING_SECTION_ID}` as const;
export const PRICING_PAGE_PATH = "/pricing" as const;
const NEAR_LIMIT_THRESHOLD = 0.8;

const MONTHLY_STUDY_PACK_LIMITS: Record<PlanType, number> = {
  FREE: 5,
  PREMIUM: 100,
};

type StudyPackDateLike = {
  createdAt: string;
};

export function getMonthlyStudyPackLimit(planType: PlanType): number {
  return MONTHLY_STUDY_PACK_LIMITS[planType];
}

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

export function hasReachedUsageLimit(used: number, limit: number): boolean {
  return limit > 0 && used >= limit;
}

export function isNearUsageLimit(used: number, limit: number): boolean {
  if (limit <= 0 || hasReachedUsageLimit(used, limit)) {
    return false;
  }
  return used / limit >= NEAR_LIMIT_THRESHOLD;
}

export function shouldShowNearStudyPackLimitBanner(
  planType: PlanType,
  used: number,
  limit: number,
): boolean {
  return planType === "FREE" && isNearUsageLimit(used, limit);
}

export function isStudyPackLimitReachedMessage(message: string): boolean {
  const normalized = message.toLowerCase();
  return (
    (normalized.includes("free plan limit") && normalized.includes("study pack"))
    || (normalized.includes("study pack limit") && normalized.includes("upgrade to premium"))
    || normalized.includes("study pack generations per month")
  );
}
