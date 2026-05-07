"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Check, Sparkles } from "lucide-react";
import { usePathname, useRouter } from "next/navigation";
import { VerifyEmailRequiredModal } from "@/components/auth/verify-email-required-modal";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import {
  createPremiumCheckoutSession,
  isEmailNotVerifiedError,
  trackAnalyticsEvent,
  type PlanType,
} from "@/lib/api";
import { getAuthUser, getCurrentPathWithQuery, getSafeRedirectPath } from "@/lib/auth";
import { formatBillingAmount, getBillingCyclePriceLabel } from "@/lib/billing-pricing";
import { redirectToCheckoutUrl } from "@/lib/checkout-redirect";
import {
  type PaywallContext,
  type PaywallModalVariant,
  resolvePaywallContextTypeFromVariant,
  resolvePaywallPresentation,
} from "@/lib/paywall-content";
import {
  savePendingPaywallUpgradeContext,
  type PendingPaywallUpgradeContext,
} from "@/lib/paywall-upgrade-context";
import { pricingConfig, resolvePricingDisplayRegion } from "@/lib/pricing-config";
import { useBillingPricing } from "@/hooks/use-billing-pricing";
import { PLANS } from "@/src/config/plans";

type PaywallModalProps = {
  isOpen: boolean;
  context?: PaywallContext;
  variant?: PaywallModalVariant;
  onClose: () => void;
  source: string;
  returnUrl?: string | null;
  resolveReturnUrl?: () => Promise<string | null> | string | null;
  resolveContext?: () => Promise<Partial<PaywallContext> | null> | Partial<PaywallContext> | null;
};

const PLAN_CARD_ORDER: Array<"PLUS" | "PRO"> = ["PLUS", "PRO"];
const PLAN_CARD_SUBTEXT = {
  PLUS: "Train on weak areas (limited sessions)",
  PRO: "Train on weak areas until you master them",
} as const;
const PAYWALL_REASSURANCE = "Access activates immediately after payment";
const PAYWALL_RENEWAL_NOTE = "No automatic charges. You control renewals.";

function buildFallbackMonthlyLabel(planType: "PLUS" | "PRO", region: ReturnType<typeof resolvePricingDisplayRegion>) {
  const regionPricing = pricingConfig.price[region];
  const introPricing = pricingConfig.intro[region];
  const regularAmount = planType === "PLUS" ? regionPricing.plus.monthly : regionPricing.pro.monthly;
  const introAmount = planType === "PLUS" ? introPricing.plus.monthly : introPricing.pro.monthly;
  if (introAmount === null) {
    return `${formatBillingAmount(regularAmount, regionPricing.currency)}/month`;
  }
  return `${formatBillingAmount(introAmount, regionPricing.currency)} first month, then ${formatBillingAmount(regularAmount, regionPricing.currency)}/month`;
}

function resolvePaywallContext(
  context: PaywallContext | undefined,
  variant: PaywallModalVariant | undefined,
): PaywallContext {
  if (context) {
    return context;
  }
  return {
    type: resolvePaywallContextTypeFromVariant(variant ?? "adaptive-practice"),
  };
}

function PlanCard({
  planType,
  currentPlan,
  monthlyLabel,
  selected,
  onClick,
}: Readonly<{
  planType: "PLUS" | "PRO";
  currentPlan: PlanType;
  monthlyLabel: string;
  selected?: boolean;
  onClick?: () => void;
}>) {
  const plan = PLANS[planType];
  const isCurrentPlan = currentPlan === planType;
  const isInteractive = !isCurrentPlan && !!onClick;
  const highlight = planType === "PRO";
  const bgClass = highlight ? "bg-blue-50/45 dark:bg-blue-950/22" : "bg-muted/20";
  const cardClass = selected && isInteractive
    ? `border-blue-500 ring-2 ring-blue-500 dark:border-blue-400 dark:ring-blue-400 ${bgClass}`
    : highlight
      ? "border-blue-400 bg-blue-50/45 dark:border-blue-700 dark:bg-blue-950/22"
      : "border-border bg-muted/20";

  return (
    <div
      role={isInteractive ? "button" : undefined}
      tabIndex={isInteractive ? 0 : undefined}
      onClick={isInteractive ? onClick : undefined}
      onKeyDown={isInteractive ? (e) => { if (e.key === "Enter" || e.key === " ") onClick?.(); } : undefined}
      className={`rounded-2xl border p-4 transition-colors ${isInteractive ? "cursor-pointer " : ""}${cardClass}`}
    >
      <div className="space-y-2">
        <div className="flex items-center justify-between gap-3">
          <p className="text-sm font-semibold text-foreground">{plan.name}</p>
          {isCurrentPlan ? (
            <span className="rounded-full border border-blue-500/20 bg-blue-500/10 px-2.5 py-0.5 text-[11px] font-semibold text-blue-700 dark:text-blue-300">
              Current plan
            </span>
          ) : null}
          {!isCurrentPlan && plan.eyebrow ? (
            <span className="inline-flex items-center gap-1 rounded-full border border-blue-500/20 bg-blue-500/10 px-2.5 py-0.5 text-[11px] font-semibold text-blue-700 dark:text-blue-300">
              <Sparkles className="h-3 w-3" aria-hidden="true" />
              {plan.eyebrow}
            </span>
          ) : null}
        </div>
        <p className="text-lg font-semibold text-foreground">{monthlyLabel}</p>
        <p className="text-sm text-foreground/70">{plan.title}</p>
      </div>
      <ul className="mt-4 space-y-2 text-sm text-foreground/80">
        {plan.features.map((feature) => (
          <li key={feature.label} className="flex items-start gap-2">
            <Check className="mt-0.5 h-4 w-4 shrink-0 text-blue-600 dark:text-blue-400" aria-hidden="true" />
            <span>
              {feature.label}
              {feature.helper ? <span className="block text-xs text-foreground/55">{feature.helper}</span> : null}
            </span>
          </li>
        ))}
      </ul>
      <p className="mt-4 text-xs text-foreground/55">{PLAN_CARD_SUBTEXT[planType]}</p>
    </div>
  );
}

export function PaywallModal({
  isOpen,
  context,
  variant,
  onClose,
  source,
  returnUrl = null,
  resolveReturnUrl,
  resolveContext,
}: Readonly<PaywallModalProps>) {
  const router = useRouter();
  const pathname = usePathname();
  const hasTrackedOpenRef = useRef(false);
  const [verifyEmailModalOpen, setVerifyEmailModalOpen] = useState(false);
  const [startingCheckoutPlan, setStartingCheckoutPlan] = useState<"PLUS" | "PRO" | null>(null);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [selectedPlan, setSelectedPlan] = useState<"PLUS" | "PRO">("PRO");
  const authUser = getAuthUser();
  const currentPlan = authUser?.planType ?? "FREE";
  const normalizedContext = useMemo(
    () => resolvePaywallContext(context, variant),
    [context, variant],
  );
  const presentation = useMemo(
    () => resolvePaywallPresentation(normalizedContext.type, currentPlan, authUser?.profileType),
    [authUser?.profileType, currentPlan, normalizedContext.type],
  );
  const { billingPricing } = useBillingPricing(true);
  const displayRegion = resolvePricingDisplayRegion(billingPricing?.region);
  const plusMonthlyLabel = billingPricing
    ? getBillingCyclePriceLabel(billingPricing, "PLUS", "MONTHLY")
    : buildFallbackMonthlyLabel("PLUS", displayRegion);
  const proMonthlyLabel = billingPricing
    ? getBillingCyclePriceLabel(billingPricing, "PRO", "MONTHLY")
    : buildFallbackMonthlyLabel("PRO", displayRegion);

  useEffect(() => {
    if (!isOpen) {
      hasTrackedOpenRef.current = false;
      setVerifyEmailModalOpen(false);
      setStartingCheckoutPlan(null);
      setCheckoutError(null);
      setSelectedPlan("PRO");
      return;
    }
    if (hasTrackedOpenRef.current) {
      return;
    }
    hasTrackedOpenRef.current = true;
    void trackAnalyticsEvent({
      eventType: "PAYWALL_VIEWED",
      metadata: {
        source,
        feature: presentation.feature,
        path: pathname,
        paywallType: normalizedContext.type,
        currentPlan,
        remaining: normalizedContext.remaining ?? null,
      },
    });
  }, [currentPlan, isOpen, normalizedContext.remaining, normalizedContext.type, pathname, presentation.feature, source]);

  const handleDismiss = () => {
    void trackAnalyticsEvent({
      eventType: "PAYWALL_DISMISSED",
      metadata: {
        source,
        feature: presentation.feature,
        path: pathname,
        paywallType: normalizedContext.type,
        currentPlan,
      },
    });
    onClose();
  };

  const startCheckout = async (planType: "PLUS" | "PRO") => {
    if (authUser && !authUser.emailVerifiedAt) {
      setVerifyEmailModalOpen(true);
      return;
    }
    if (!authUser) {
      onClose();
      router.push("/signup");
      return;
    }

    setStartingCheckoutPlan(planType);
    setCheckoutError(null);
    try {
      const contextPatch = (await resolveContext?.()) ?? null;
      const resolvedReturnPath = getSafeRedirectPath(
        contextPatch?.returnPath
        ?? (await resolveReturnUrl?.())
        ?? normalizedContext.returnPath
        ?? returnUrl
        ?? getCurrentPathWithQuery(),
      );
      const pendingContext: PendingPaywallUpgradeContext = {
        type: contextPatch?.type ?? normalizedContext.type,
        lastAction: presentation.lastAction,
        noteId: contextPatch?.noteId ?? normalizedContext.noteId ?? null,
        returnPath: resolvedReturnPath,
        source,
        createdAtMs: Date.now(),
      };
      savePendingPaywallUpgradeContext(authUser.id, pendingContext);

      void trackAnalyticsEvent({
        eventType: "UPGRADE_CLICKED",
        metadata: {
          source,
          feature: presentation.feature,
          path: pathname,
          paywallType: normalizedContext.type,
          currentPlan,
          selectedPlan: planType,
          target: "xendit_checkout",
        },
      });

      const response = await createPremiumCheckoutSession({
        planType,
        returnUrl: resolvedReturnPath,
      });
      onClose();
      redirectToCheckoutUrl(response.checkoutUrl);
    } catch (error) {
      if (isEmailNotVerifiedError(error)) {
        setVerifyEmailModalOpen(true);
      } else {
        setCheckoutError(error instanceof Error ? error.message : "Could not start checkout. Please try again.");
      }
    } finally {
      setStartingCheckoutPlan(null);
    }
  };

  const isProUser = currentPlan === "PRO";

  return (
    <>
      <AppModal
        isOpen={isOpen}
        title={presentation.headline}
        description={presentation.body}
        onClose={handleDismiss}
        panelClassName="max-w-[760px]"
        actions={(
          <div className="space-y-3">
            <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
              <Button type="button" variant="ghost" onClick={handleDismiss}>
                {isProUser ? "Close" : "Maybe Later"}
              </Button>
              {isProUser ? (
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => { onClose(); router.push("/settings?section=plans"); }}
                >
                  View My Plan
                </Button>
              ) : (
                <Button
                  type="button"
                  onClick={() => void startCheckout(selectedPlan)}
                  loading={!!startingCheckoutPlan}
                  loadingText="Redirecting..."
                  disabled={!!startingCheckoutPlan}
                >
                  Continue with {PLANS[selectedPlan].name}
                </Button>
              )}
            </div>
            {!isProUser ? (
              <div className="space-y-1 text-center text-xs text-foreground/60 sm:text-right">
                <p>{PAYWALL_REASSURANCE}</p>
                <p>{PAYWALL_RENEWAL_NOTE}</p>
              </div>
            ) : null}
          </div>
        )}
      >
        <div className="space-y-4">
          {isProUser ? (
            <div className="rounded-lg border border-border bg-muted/20 p-4 text-sm text-foreground/75">
              <p>You&apos;re already on Pro — the top plan. For billing or account questions, visit <strong>Settings → Plans</strong>.</p>
            </div>
          ) : (
            <div className="grid gap-3 lg:grid-cols-2">
              {PLAN_CARD_ORDER.map((planType) => (
                <PlanCard
                  key={planType}
                  planType={planType}
                  currentPlan={currentPlan}
                  monthlyLabel={planType === "PLUS" ? plusMonthlyLabel : proMonthlyLabel}
                  selected={selectedPlan === planType}
                  onClick={currentPlan !== planType ? () => setSelectedPlan(planType) : undefined}
                />
              ))}
            </div>
          )}
          {checkoutError ? (
            <div className="rounded-lg border border-red-500/30 bg-red-50/70 p-3 text-sm text-red-700 dark:bg-red-950/20 dark:text-red-300">
              {checkoutError}
            </div>
          ) : null}
        </div>
      </AppModal>
      <VerifyEmailRequiredModal
        isOpen={verifyEmailModalOpen}
        onClose={() => setVerifyEmailModalOpen(false)}
      />
    </>
  );
}

export type { PaywallContext, PaywallModalVariant } from "@/lib/paywall-content";
