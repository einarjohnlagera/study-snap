"use client";

import { Button } from "@/components/ui/button";
import { AppModal } from "@/components/ui/app-modal";
import { useBillingPricing } from "@/hooks/use-billing-pricing";
import { getBillingCyclePriceLabel } from "@/lib/billing-pricing";

export type PaywallModalVariant = "premium-feature" | "study-pack-limit";

type PaywallModalProps = {
  isOpen: boolean;
  variant: PaywallModalVariant;
  onClose: () => void;
  onUpgrade: () => void;
};

const PREMIUM_FEATURE_BENEFITS = [
  "100 Study Packs per month",
  "Challenge Quiz (exam-style)",
  "Adaptive Practice (focus on weak concepts)",
  "Higher monthly limits",
];

function getModalCopy(variant: PaywallModalVariant) {
  if (variant === "study-pack-limit") {
    return {
      title: "You've reached your monthly limit",
      intro:
        "Free plan includes 5 Study Pack generations per month. Upgrade to Premium to continue generating Study Packs and unlock Challenge Quiz and Adaptive Practice.",
    };
  }

  return {
    title: "Unlock Exam Mode",
    intro:
      "Challenge Quiz and Adaptive Practice are Premium features designed to help you focus on weak topics and prepare for exams faster.",
  };
}

export function PaywallModal({
  isOpen,
  variant,
  onClose,
  onUpgrade,
}: PaywallModalProps) {
  const copy = getModalCopy(variant);
  const { billingPricing } = useBillingPricing(isOpen);
  const monthlyLabel = getBillingCyclePriceLabel(billingPricing, "MONTHLY");
  const yearlyLabel = getBillingCyclePriceLabel(billingPricing, "YEARLY");

  return (
    <AppModal
      isOpen={isOpen}
      title={copy.title}
      onClose={onClose}
      panelClassName="max-w-[460px]"
      actions={(
        <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">
          <Button type="button" variant="outline" onClick={onClose}>
            OK
          </Button>
          <Button type="button" onClick={onUpgrade}>
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
