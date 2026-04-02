"use client";

import { PremiumWaitlistButton } from "@/components/billing/premium-waitlist-button";

type NearLimitBannerProps = {
  className?: string;
  remainingCredits?: number | null;
};

export function NearLimitBanner({ className, remainingCredits = null }: Readonly<NearLimitBannerProps>) {
  const normalizedRemaining = typeof remainingCredits === "number"
    ? Math.max(0, remainingCredits)
    : null;
  const message = normalizedRemaining === 1
    ? "You have 1 Study Pack left this billing cycle. Upgrade to Premium to keep generating Study Packs and unlock Challenge Quiz and Adaptive Practice."
    : normalizedRemaining && normalizedRemaining > 1
      ? `You have ${normalizedRemaining} Study Packs left this billing cycle. Upgrade to Premium to keep generating Study Packs and unlock Challenge Quiz and Adaptive Practice.`
      : "You’re almost at your monthly limit. Upgrade to Premium to continue generating Study Packs and unlock Challenge Quiz and Adaptive Practice.";
  return (
    <div
      role="status"
      className={`rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 text-sm text-foreground/85 ${className ?? ""}`}
    >
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <p>{message}</p>
        <PremiumWaitlistButton
          label="Upgrade to Premium"
          source="near_limit_banner"
          variant="outline"
          size="sm"
          className="w-full sm:w-auto"
        />
      </div>
    </div>
  );
}
