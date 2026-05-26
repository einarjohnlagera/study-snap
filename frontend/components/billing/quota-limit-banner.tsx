"use client";

import { AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  getUpgradeCtas,
  type AppPlanType,
  type UpgradeCtaContext,
} from "@/src/config/plans";

type QuotaLimitBannerProps = {
  title: string;
  resetDateLabel?: string;
  plan: AppPlanType;
  ctaContext: UpgradeCtaContext;
  onUpgrade?: () => void;
  className?: string;
};

export function QuotaLimitBanner({
  title,
  resetDateLabel,
  plan,
  ctaContext,
  onUpgrade,
  className,
}: Readonly<QuotaLimitBannerProps>) {
  const primaryCta = getUpgradeCtas(plan, ctaContext).primary;
  const shouldShowUpgrade = Boolean(onUpgrade && primaryCta);

  return (
    <div
      role="status"
      className={`rounded-xl border border-red-500/30 bg-red-500/10 p-4 text-foreground/85 ${className ?? ""}`}
    >
      <div className="flex items-start gap-2">
        <AlertCircle className="mt-0.5 h-4 w-4 shrink-0 text-red-500" aria-hidden="true" />
        <div className="space-y-1">
          <p className="text-sm font-medium">{title}</p>
          {resetDateLabel ? (
            <p className="text-xs text-foreground/65">
              Resets on {resetDateLabel}.
            </p>
          ) : null}
        </div>
      </div>
      {shouldShowUpgrade ? (
        <Button
          type="button"
          variant="outline"
          size="sm"
          className="mt-3 w-full sm:w-fit"
          onClick={onUpgrade}
        >
          {primaryCta?.label}
        </Button>
      ) : null}
    </div>
  );
}
