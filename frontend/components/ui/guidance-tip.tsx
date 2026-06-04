"use client";

import { useEffect, useState } from "react";
import { X } from "lucide-react";
import { hasSeenTip, markTipSeen } from "@/lib/guidance";

type GuidanceTipProps = Readonly<{
  tipId: string;
  message: string;
  className?: string;
  action?: { label: string; onClick: () => void };
}>;

/**
 * A lightweight one-time dismissible tip strip.
 * Once dismissed, it never appears again (tracked via localStorage).
 * Fades in on first render, fades out on dismiss.
 */
export function GuidanceTip({ tipId, message, className, action }: GuidanceTipProps) {
  const [visible, setVisible] = useState(() => !hasSeenTip(tipId));
  const [fading, setFading] = useState(false);

  useEffect(() => {
    if (!hasSeenTip(tipId)) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setVisible(true);
    } else {
      setVisible(false);
    }
  }, [tipId]);

  if (!visible) {
    return null;
  }

  const handleDismiss = () => {
    setFading(true);
    markTipSeen(tipId);
    setTimeout(() => setVisible(false), 200);
  };

  return (
    <div
      role="status"
      aria-live="polite"
      className={[
        "flex items-start justify-between gap-3 rounded-xl border border-blue-200 bg-blue-50 px-3 py-2.5 text-xs text-blue-800 transition-opacity duration-200 dark:border-blue-900/50 dark:bg-blue-950/30 dark:text-blue-300",
        fading ? "opacity-0" : "opacity-100",
        className ?? "",
      ]
        .join(" ")
        .trim()}
    >
      <span>{message}</span>
      <div className="flex shrink-0 items-center gap-2">
        {action ? (
          <button
            type="button"
            onClick={() => { action.onClick(); handleDismiss(); }}
            className="rounded text-xs font-medium text-blue-700 underline-offset-2 hover:underline dark:text-blue-300"
          >
            {action.label}
          </button>
        ) : null}
        <button
          type="button"
          onClick={handleDismiss}
          aria-label="Dismiss tip"
          className="mt-px rounded text-blue-500 transition-colors hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-200"
        >
          <X className="h-3.5 w-3.5" aria-hidden="true" />
        </button>
      </div>
    </div>
  );
}
