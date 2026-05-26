"use client";

import type { PlanType } from "@/lib/api";
import { QuotaLimitBanner } from "@/components/billing/quota-limit-banner";
import type { AppPlanType } from "@/src/config/plans";

type NearLimitBannerProps = {
  className?: string;
  planType: PlanType;
  remainingCredits?: number | null;
  resetDateLabel?: string;
  onUpgrade?: () => void;
};

export function resolveAppPlan(planType: PlanType): AppPlanType {
  if (planType === "PLUS" || planType === "PRO") {
    return planType;
  }
  return "FREE";
}

export function NearLimitBanner({
  className,
  planType,
  remainingCredits = null,
  resetDateLabel = "your reset date",
  onUpgrade,
}: Readonly<NearLimitBannerProps>) {
  const normalizedRemaining = typeof remainingCredits === "number"
    ? Math.max(0, remainingCredits)
    : null;
  const isLimitReached = normalizedRemaining !== null && normalizedRemaining <= 0;
  if (isLimitReached) {
    const title = planType === "FREE"
      ? "You’ve reached your Free plan limit for this month."
      : planType === "PLUS"
        ? "You’ve used all your Study Packs this month on Plus."
        : "You’ve used all your Study Packs this month.";
    return (
      <QuotaLimitBanner
        className={className}
        title={title}
        resetDateLabel={resetDateLabel}
        plan={resolveAppPlan(planType)}
        ctaContext="study-pack-limit"
        onUpgrade={onUpgrade}
      />
    );
  }

  const message = planType === "FREE"
    ? `You have ${normalizedRemaining} Study Pack${normalizedRemaining === 1 ? "" : "s"} left this month on the Free plan.`
    : planType === "PLUS"
      ? `You have ${normalizedRemaining} Study Pack${normalizedRemaining === 1 ? "" : "s"} left this month on Plus.`
      : `You have ${normalizedRemaining} Study Pack${normalizedRemaining === 1 ? "" : "s"} left this month.`;

  return (
    <div
      role="status"
      className={`rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 text-sm text-foreground/85 ${className ?? ""}`}
    >
      <p>{message}</p>
    </div>
  );
}
