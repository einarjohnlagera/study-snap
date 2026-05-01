"use client";

import type { PlanType } from "@/lib/api";

type NearLimitBannerProps = {
  className?: string;
  planType: PlanType;
  remainingCredits?: number | null;
  resetDateLabel?: string;
};

export function NearLimitBanner({
  className,
  planType,
  remainingCredits = null,
  resetDateLabel = "your reset date",
}: Readonly<NearLimitBannerProps>) {
  const normalizedRemaining = typeof remainingCredits === "number"
    ? Math.max(0, remainingCredits)
    : null;
  const isLimitReached = normalizedRemaining !== null && normalizedRemaining <= 0;
  const message = planType === "FREE"
    ? isLimitReached
      ? `You’ve reached your Free plan limit for this month. You can generate more again on ${resetDateLabel}.`
      : `You have ${normalizedRemaining} Study Pack${normalizedRemaining === 1 ? "" : "s"} left this month on the Free plan.`
    : planType === "PLUS"
      ? isLimitReached
        ? `You’ve used all your Study Packs this month on Plus. Go Pro or wait until ${resetDateLabel} to generate more.`
        : `You have ${normalizedRemaining} Study Pack${normalizedRemaining === 1 ? "" : "s"} left this month on Plus.`
    : isLimitReached
      ? `You’ve used all your Study Packs this month. Limit resets on ${resetDateLabel}.`
      : `You have ${normalizedRemaining} Study Pack${normalizedRemaining === 1 ? "" : "s"} left this month.`;
  return (
    <div
      role="status"
      className={`rounded-xl border p-4 text-sm text-foreground/85 ${isLimitReached ? "border-red-500/30 bg-red-500/10" : "border-amber-500/30 bg-amber-500/10"} ${className ?? ""}`}
    >
      <p>{message}</p>
    </div>
  );
}
