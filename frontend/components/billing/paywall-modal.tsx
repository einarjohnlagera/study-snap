"use client";

import { useEffect, useRef } from "react";
import { usePathname } from "next/navigation";
import { Button } from "@/components/ui/button";
import { AppModal } from "@/components/ui/app-modal";
import { useBillingPricing } from "@/hooks/use-billing-pricing";
import { trackAnalyticsEvent } from "@/lib/api";
import { getBillingCyclePriceLabel } from "@/lib/billing-pricing";

export type PaywallModalVariant =
  | "challenge-quiz"
  | "adaptive-practice"
  | "study-pack-limit";

type PaywallModalProps = {
  isOpen: boolean;
  variant: PaywallModalVariant;
  onClose: () => void;
  onUpgrade: () => void;
};

const PREMIUM_FEATURE_BENEFITS = [
  "100 Study Packs per month",
  "Challenge Quiz (Exam Mode)",
  "Adaptive Practice (focus on weak topics)",
  "Priority AI generation",
];

function getModalCopy(variant: PaywallModalVariant) {
  if (variant === "study-pack-limit") {
    return {
      title: "You've reached your monthly limit",
      intro:
        "Free plan includes 5 Study Pack generations per month. Upgrade to Premium to continue generating Study Packs and unlock Challenge Quiz and Adaptive Practice.",
      dismissLabel: "OK",
    };
  }

  if (variant === "adaptive-practice") {
    return {
      title: "Focus on Your Weak Topics",
      intro:
        "Adaptive Practice creates quizzes based on the topics you got wrong so you can improve faster and focus on weak areas.",
      dismissLabel: "Maybe Later",
    };
  }

  return {
    title: "Unlock Exam Mode",
    intro:
      "Challenge Quiz simulates a real exam and helps you test your knowledge without seeing answers immediately. Perfect for exam preparation.",
    dismissLabel: "Maybe Later",
  };
}

export function PaywallModal({
  isOpen,
  variant,
  onClose,
  onUpgrade,
}: PaywallModalProps) {
  const pathname = usePathname();
  const hasTrackedOpenRef = useRef(false);
  const copy = getModalCopy(variant);
  const { billingPricing } = useBillingPricing(isOpen);
  const monthlyLabel = getBillingCyclePriceLabel(billingPricing, "MONTHLY");
  const yearlyLabel = getBillingCyclePriceLabel(billingPricing, "YEARLY");

  useEffect(() => {
    if (!isOpen) {
      hasTrackedOpenRef.current = false;
      return;
    }
    if (hasTrackedOpenRef.current) {
      return;
    }
    hasTrackedOpenRef.current = true;
    void trackAnalyticsEvent({
      eventType: "PAYWALL_VIEWED",
      metadata: {
        variant,
        path: pathname,
      },
    });
  }, [isOpen, pathname, variant]);

  const handleUpgrade = () => {
    void trackAnalyticsEvent({
      eventType: "UPGRADE_CLICKED",
      metadata: {
        source: "paywall_modal",
        variant,
        path: pathname,
      },
    });
    onUpgrade();
  };

  return (
    <AppModal
      isOpen={isOpen}
      title={copy.title}
      onClose={onClose}
      panelClassName="max-w-[460px]"
      actions={(
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={onClose}>
            {copy.dismissLabel}
          </Button>
          <Button type="button" onClick={handleUpgrade}>
            Upgrade to Premium
          </Button>
        </div>
      )}
    >
      <div className="space-y-4 text-sm leading-relaxed text-foreground/85">
        <p>{copy.intro}</p>
        <div className="rounded-lg border border-border bg-muted/30 p-3">
          <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">
            With Premium, you get:
          </p>
          <ul className="mt-2 list-disc space-y-1 pl-5">
            {PREMIUM_FEATURE_BENEFITS.map((benefit) => (
              <li key={benefit}>{benefit}</li>
            ))}
          </ul>
        </div>
        {billingPricing ? (
          <div className="rounded-lg border border-blue-500/20 bg-blue-500/10 p-3 text-sm text-foreground/85">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
              Pricing in your region
            </p>
            <p className="mt-2 font-medium text-foreground">{monthlyLabel}</p>
            <p className="text-xs text-foreground/70">{yearlyLabel}</p>
          </div>
        ) : null}
      </div>
    </AppModal>
  );
}
