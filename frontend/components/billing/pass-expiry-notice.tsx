"use client";

import { useMemo, useState } from "react";
import { ResponsiveActionButton } from "@/components/ui/action-button";
import type { PlanType } from "@/lib/api";
import { PASS_NO_AUTO_CHARGE_FOOTER, getUpgradeCtas, type AppPlanType } from "@/src/config/plans";

const DAY_IN_MILLISECONDS = 24 * 60 * 60 * 1000;
const SEVEN_DAY_WINDOW_START_DAYS = 6;
const SEVEN_DAY_WINDOW_END_DAYS = 8;
const DISMISSAL_STORAGE_PREFIX = "notelib-pass-expiry-notice-dismissed";

export type PassExpiryNoticeStage = "SEVEN_DAY" | "ONE_DAY";

type PassExpiryNoticeProps = {
  userId: string;
  planType: PlanType;
  premiumEndsAt: string | null;
  onRenew: (planType: "PLUS" | "PRO") => void;
  renewalLoading?: boolean;
};

function isPaidPlan(planType: PlanType): planType is "PLUS" | "PRO" {
  return planType === "PLUS" || planType === "PRO";
}

export function getPassExpiryNoticeStage(
  premiumEndsAt: string | null,
  now: number = Date.now(),
): PassExpiryNoticeStage | null {
  if (!premiumEndsAt) {
    return null;
  }
  const expiryTime = new Date(premiumEndsAt).getTime();
  if (Number.isNaN(expiryTime)) {
    return null;
  }

  const remainingMilliseconds = expiryTime - now;
  if (remainingMilliseconds <= 0) {
    return null;
  }
  if (remainingMilliseconds <= DAY_IN_MILLISECONDS) {
    return "ONE_DAY";
  }
  if (
    remainingMilliseconds >= SEVEN_DAY_WINDOW_START_DAYS * DAY_IN_MILLISECONDS
    && remainingMilliseconds <= SEVEN_DAY_WINDOW_END_DAYS * DAY_IN_MILLISECONDS
  ) {
    return "SEVEN_DAY";
  }
  return null;
}

function getDismissalKey(userId: string, stage: PassExpiryNoticeStage, premiumEndsAt: string) {
  return `${DISMISSAL_STORAGE_PREFIX}:${userId}:${stage}:${premiumEndsAt}`;
}

function hasStoredDismissal(dismissalKey: string | null) {
  if (!dismissalKey || typeof globalThis.window === "undefined") {
    return false;
  }
  try {
    return globalThis.localStorage.getItem(dismissalKey) === "1";
  } catch {
    return false;
  }
}

function formatExpiryDate(premiumEndsAt: string) {
  return new Date(premiumEndsAt).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

export function PassExpiryNotice({
  userId,
  planType,
  premiumEndsAt,
  onRenew,
  renewalLoading = false,
}: Readonly<PassExpiryNoticeProps>) {
  const stage = getPassExpiryNoticeStage(premiumEndsAt);
  const dismissalKey = stage && premiumEndsAt ? getDismissalKey(userId, stage, premiumEndsAt) : null;
  const [dismissedKey, setDismissedKey] = useState<string | null>(null);
  const dismissed = dismissedKey === dismissalKey || hasStoredDismissal(dismissalKey);

  const renewalCta = useMemo(
    () => (isPaidPlan(planType) ? getUpgradeCtas(planType as AppPlanType, "pass-renewal").primary : null),
    [planType],
  );

  if (!stage || !premiumEndsAt || !isPaidPlan(planType) || !renewalCta || !dismissalKey || dismissed) {
    return null;
  }

  const expiryDate = formatExpiryDate(premiumEndsAt);
  const message = stage === "ONE_DAY"
    ? `Your ${planType === "PLUS" ? "Plus" : "Pro"} pass ends tomorrow (${expiryDate}) — get another pass to keep your limits.`
    : `Your ${planType === "PLUS" ? "Plus" : "Pro"} pass ends on ${expiryDate} — get another pass to keep your limits.`;

  const dismiss = () => {
    try {
      globalThis.localStorage.setItem(dismissalKey, "1");
    } catch {
      // Dismissal is best effort; the renewal path remains available.
    }
    setDismissedKey(dismissalKey);
  };

  return (
    <section className="rounded-xl border border-sky-500/30 bg-sky-500/10 p-4" aria-label="Pass expiry notice">
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-3">
          <div className="space-y-1">
            <p className="text-sm font-semibold text-foreground">Keep your study pass active</p>
            <p className="text-sm leading-relaxed text-foreground/80">{message}</p>
          </div>
          <div className="space-y-2">
            <ResponsiveActionButton
              type="button"
              onClick={() => onRenew(renewalCta.targetPlan)}
              loading={renewalLoading}
              loadingText="Redirecting..."
              action="studyPack"
              label={renewalCta.label}
              className="w-full sm:w-auto"
            />
            <p className="text-xs text-foreground/65">{PASS_NO_AUTO_CHARGE_FOOTER}</p>
          </div>
        </div>
        <button
          type="button"
          onClick={dismiss}
          className="shrink-0 text-xs font-medium text-foreground/60 transition-colors hover:text-foreground"
        >
          Dismiss
        </button>
      </div>
    </section>
  );
}
