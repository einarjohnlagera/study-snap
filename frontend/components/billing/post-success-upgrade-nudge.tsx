"use client";
import { useState } from "react";
import Link from "next/link";
import { X } from "lucide-react";
import { trackAnalyticsEvent } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { getUpgradeCtas, type AppPlanType } from "@/src/config/plans";

export type PostSuccessUpgradeNudgeTrigger = "quick-review" | "challenge-quiz";

const UPGRADE_PATH = "/settings?section=plans";

const NUDGE_MESSAGE: Record<PostSuccessUpgradeNudgeTrigger, string> = {
  "quick-review": "Ready to improve your weak areas?",
  "challenge-quiz": "You're building momentum. Keep going without limits.",
};

type PostSuccessUpgradeNudgeProps = {
  trigger: PostSuccessUpgradeNudgeTrigger;
};

function getSessionKey(trigger: PostSuccessUpgradeNudgeTrigger, userId: string): string {
  return `notelib-post-success-nudge:${trigger}:${userId}`;
}

function resolveCurrentPlan(): AppPlanType {
  const planType = getAuthUser()?.planType;
  if (planType === "PLUS" || planType === "PRO") {
    return planType;
  }
  return "FREE";
}

export function PostSuccessUpgradeNudge({ trigger }: Readonly<PostSuccessUpgradeNudgeProps>) {
  const [dismissed, setDismissed] = useState(() => {
    if (globalThis.sessionStorage === undefined) return false;
    const userId = getAuthUser()?.id ?? "anon";
    return globalThis.sessionStorage.getItem(getSessionKey(trigger, userId)) === "1";
  });

  if (dismissed) return null;

  const currentPlan = resolveCurrentPlan();
  const ctas = getUpgradeCtas(currentPlan);
  if (!ctas.primary) return null;

  const handleDismiss = () => {
    if (globalThis.sessionStorage !== undefined) {
      const userId = getAuthUser()?.id ?? "anon";
      globalThis.sessionStorage.setItem(getSessionKey(trigger, userId), "1");
    }
    setDismissed(true);
  };

  const message = NUDGE_MESSAGE[trigger];

  return (
    <div className="flex items-center justify-between gap-3 rounded-md border border-primary/20 bg-primary/5 px-3 py-2.5 text-sm">
      <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
        <span className="text-foreground/80">{message}</span>
        <Link
          href={UPGRADE_PATH}
          className="shrink-0 font-medium text-primary hover:underline"
          onClick={() => {
            void trackAnalyticsEvent({
              eventType: "UPGRADE_CLICKED",
              metadata: {
                source: `post_success_nudge_${trigger}`,
                targetPlan: ctas.primary?.targetPlan,
                path: typeof globalThis.location !== "undefined" ? globalThis.location.pathname : "",
              },
            });
          }}
        >
          {ctas.primary.label} →
        </Link>
        {ctas.secondary ? (
          <Link
            href={UPGRADE_PATH}
            className="shrink-0 text-foreground/65 hover:underline"
            onClick={() => {
              void trackAnalyticsEvent({
                eventType: "UPGRADE_CLICKED",
                metadata: {
                  source: `post_success_nudge_${trigger}_secondary`,
                  targetPlan: ctas.secondary?.targetPlan,
                  path: typeof globalThis.location !== "undefined" ? globalThis.location.pathname : "",
                },
              });
            }}
          >
            or {ctas.secondary.label}
          </Link>
        ) : null}
      </div>
      <button
        type="button"
        aria-label="Dismiss upgrade suggestion"
        className="shrink-0 text-foreground/40 hover:text-foreground/70"
        onClick={handleDismiss}
      >
        <X className="h-3.5 w-3.5" aria-hidden="true" />
      </button>
    </div>
  );
}
