"use client";
import { useState } from "react";
import Link from "next/link";
import { X } from "lucide-react";
import { trackAnalyticsEvent } from "@/lib/api";
import { getAuthUser } from "@/lib/auth";

export type PostSuccessUpgradeNudgeTrigger = "quick-review" | "challenge-quiz";

const NUDGE_COPY: Record<PostSuccessUpgradeNudgeTrigger, { message: string; cta: string }> = {
  "quick-review": {
    message: "Ready to improve your weak areas?",
    cta: "Go Pro",
  },
  "challenge-quiz": {
    message: "You're building momentum. Keep going without limits.",
    cta: "Go Pro",
  },
};

type PostSuccessUpgradeNudgeProps = {
  trigger: PostSuccessUpgradeNudgeTrigger;
};

function getSessionKey(trigger: PostSuccessUpgradeNudgeTrigger, userId: string): string {
  return `notelib-post-success-nudge:${trigger}:${userId}`;
}

export function PostSuccessUpgradeNudge({ trigger }: PostSuccessUpgradeNudgeProps) {
  const [dismissed, setDismissed] = useState(() => {
    if (typeof globalThis.sessionStorage === "undefined") return false;
    const userId = getAuthUser()?.id ?? "anon";
    return globalThis.sessionStorage.getItem(getSessionKey(trigger, userId)) === "1";
  });

  if (dismissed) return null;

  const handleDismiss = () => {
    if (typeof globalThis.sessionStorage !== "undefined") {
      const userId = getAuthUser()?.id ?? "anon";
      globalThis.sessionStorage.setItem(getSessionKey(trigger, userId), "1");
    }
    setDismissed(true);
  };

  const { message, cta } = NUDGE_COPY[trigger];

  return (
    <div className="flex items-center justify-between gap-3 rounded-md border border-primary/20 bg-primary/5 px-3 py-2.5 text-sm">
      <div className="flex min-w-0 flex-wrap items-center gap-x-2 gap-y-1">
        <span className="text-foreground/80">{message}</span>
        <Link
          href="/pricing"
          className="shrink-0 font-medium text-primary hover:underline"
          onClick={() => {
            void trackAnalyticsEvent({
              eventType: "UPGRADE_CLICKED",
              metadata: {
                source: `post_success_nudge_${trigger}`,
                path: typeof globalThis.location !== "undefined" ? globalThis.location.pathname : "",
              },
            });
          }}
        >
          {cta} →
        </Link>
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
