"use client";

import { useEffect, useRef } from "react";
import type { PlanType } from "@/lib/api";
import { QuotaLimitBanner } from "@/components/billing/quota-limit-banner";
import { trackAnalyticsEvent } from "@/lib/api";
import type { AppPlanType, UpgradeCtaContext } from "@/src/config/plans";

type NearLimitBannerProps = {
  className?: string;
  planType: PlanType;
  remainingCredits?: number | null;
  resetDateLabel?: string;
  onUpgrade?: () => void;
  /**
   * Singular noun for the metered credit. Defaults to the Study Pack copy used
   * by the Note editor. Pass a custom label (e.g. "note generation", "image scan")
   * to reuse the banner for other quotas.
   */
  creditLabel?: string;
  creditLabelPlural?: string;
  ctaContext?: UpgradeCtaContext;
  analyticsSource: string;
};

const NEAR_LIMIT_FEATURE = "near_limit";
const UPGRADE_SURFACE_TARGET = "upgrade_surface";

function getCurrentPathname() {
  return globalThis.location?.pathname ?? "";
}

function planSuffix(planType: PlanType): string {
  if (planType === "FREE") {
    return " on the Free plan";
  }
  if (planType === "PLUS") {
    return " on Plus";
  }
  return "";
}

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
  creditLabel,
  creditLabelPlural,
  ctaContext = "study-pack-limit",
  analyticsSource,
}: Readonly<NearLimitBannerProps>) {
  const pathname = getCurrentPathname();
  const hasTrackedViewRef = useRef(false);
  const normalizedRemaining = typeof remainingCredits === "number"
    ? Math.max(0, remainingCredits)
    : null;
  const isLimitReached = normalizedRemaining !== null && normalizedRemaining <= 0;
  const singular = creditLabel ?? "Study Pack";
  const plural = creditLabelPlural ?? `${singular}s`;

  useEffect(() => {
    if (hasTrackedViewRef.current) {
      return;
    }
    hasTrackedViewRef.current = true;
    void trackAnalyticsEvent({
      eventType: "PAYWALL_VIEWED",
      metadata: {
        source: analyticsSource,
        feature: NEAR_LIMIT_FEATURE,
        path: pathname,
        currentPlan: planType,
        remaining: normalizedRemaining,
        ctaContext,
      },
    });
  }, [analyticsSource, ctaContext, normalizedRemaining, pathname, planType]);

  const handleUpgrade = () => {
    void trackAnalyticsEvent({
      eventType: "UPGRADE_CLICKED",
      metadata: {
        source: analyticsSource,
        feature: NEAR_LIMIT_FEATURE,
        path: pathname,
        currentPlan: planType,
        remaining: normalizedRemaining,
        ctaContext,
        target: UPGRADE_SURFACE_TARGET,
      },
    });
    onUpgrade?.();
  };

  if (isLimitReached) {
    const title = creditLabel
      ? `You’ve used all your ${plural} this month${planSuffix(planType)}.`
      : planType === "FREE"
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
        ctaContext={ctaContext}
        onUpgrade={onUpgrade ? handleUpgrade : undefined}
      />
    );
  }

  const noun = normalizedRemaining === 1 ? singular : plural;
  const message = `You have ${normalizedRemaining} ${noun} left this month${planSuffix(planType)}.`;

  return (
    <div
      role="status"
      className={`rounded-xl border border-amber-500/30 bg-amber-500/10 p-4 text-sm text-foreground/85 ${className ?? ""}`}
    >
      <p>{message}</p>
    </div>
  );
}
