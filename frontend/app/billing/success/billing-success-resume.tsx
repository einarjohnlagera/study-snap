"use client";

import { useEffect } from "react";
import { getMyPlan, type PaidPlanType, type PlanType } from "@/lib/api";
import { getAuthUser, patchAuthUser } from "@/lib/auth";
import {
  clearPendingPaywallUpgradeContext,
  loadPendingPaywallUpgradeContext,
  resolvePaywallSuccessPath,
} from "@/lib/paywall-upgrade-context";

type BillingSuccessResumeProps = {
  fallbackReturnUrl: string | null;
  shouldPreferDashboard: boolean;
  selectedPlan: PaidPlanType;
};

const PLAN_POLL_INTERVAL_MS = 1200;
const PLAN_POLL_ATTEMPTS = 8;
const REDIRECT_DELAY_MS = 900;

function resolvePlanRank(plan: PlanType): number {
  switch (plan) {
    case "PRO":
      return 2;
    case "PLUS":
      return 1;
    default:
      return 0;
  }
}

function hasReachedExpectedPlan(currentPlan: PlanType, expectedPlan: PaidPlanType): boolean {
  return resolvePlanRank(currentPlan) >= resolvePlanRank(expectedPlan);
}

function delay(ms: number) {
  return new Promise((resolve) => {
    globalThis.setTimeout(resolve, ms);
  });
}

export function BillingSuccessResume({
  fallbackReturnUrl,
  shouldPreferDashboard,
  selectedPlan,
}: Readonly<BillingSuccessResumeProps>) {
  useEffect(() => {
    const authUser = getAuthUser();
    if (!authUser) {
      return;
    }

    const pendingContext = loadPendingPaywallUpgradeContext(authUser.id);
    const destination = shouldPreferDashboard
      ? "/dashboard"
      : resolvePaywallSuccessPath(pendingContext, fallbackReturnUrl);
    let cancelled = false;
    let redirectTimeoutId: ReturnType<typeof globalThis.setTimeout> | null = null;

    const redirectToDestination = () => {
      if (cancelled) {
        return;
      }
      clearPendingPaywallUpgradeContext(authUser.id);
      redirectTimeoutId = globalThis.setTimeout(() => {
        if (!cancelled) {
          globalThis.location.assign(destination);
        }
      }, REDIRECT_DELAY_MS);
    };

    const confirmPlanAndResume = async () => {
      if (shouldPreferDashboard) {
        redirectToDestination();
        return;
      }

      for (let attempt = 0; attempt < PLAN_POLL_ATTEMPTS; attempt += 1) {
        if (cancelled) {
          return;
        }

        try {
          const latestPlan = await getMyPlan();
          if (hasReachedExpectedPlan(latestPlan.plan, selectedPlan)) {
            patchAuthUser({
              planType: latestPlan.plan,
              planSummary: latestPlan,
            });
            redirectToDestination();
            return;
          }
        } catch {
          // Keep polling. Checkout success can arrive before the webhook finishes.
        }

        await delay(PLAN_POLL_INTERVAL_MS);
      }

      redirectToDestination();
    };

    void confirmPlanAndResume();

    return () => {
      cancelled = true;
      if (redirectTimeoutId !== null) {
        globalThis.clearTimeout(redirectTimeoutId);
      }
    };
  }, [fallbackReturnUrl, selectedPlan, shouldPreferDashboard]);

  return null;
}
