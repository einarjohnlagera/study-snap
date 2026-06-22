"use client";

import { useCallback, useEffect, useState } from "react";
import { Check, Sparkles } from "lucide-react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { VerifyEmailRequiredModal } from "@/components/auth/verify-email-required-modal";
import { useRouteProgress } from "@/components/navigation/route-progress-provider";
import { ThemeSelector } from "@/components/theme-selector";
import { Button } from "@/components/ui/button";
import { ResponsiveActionButton } from "@/components/ui/action-button";
import { Card } from "@/components/ui/card";
import { AppModal } from "@/components/ui/app-modal";
import { PageHeader } from "@/components/page-header";
import { Skeleton } from "@/components/ui/skeleton";
import {
  cancelPremiumSubscription,
  createPremiumCheckoutSession,
  getBillingPricing,
  getBillingHistory,
  getMyPlan,
  getMe,
  isEmailNotVerifiedError,
  logout,
  trackAnalyticsEvent,
  updateEngagementMode,
  updateEmailPreferences,
  type BillingHistoryResponse,
  type BillingHistoryItemResponse,
  type BillingPricingResponse,
  type BillingCycle,
  type CancelPremiumSubscriptionRequest,
  type EngagementMode,
  type MePlanResponse,
  type MeResponse,
  type SubscriptionCancellationReason,
} from "@/lib/api";
import { buildLoginPath, getAuthUser, getCurrentPathWithQuery, getSafeRedirectPath, LOGIN_REASON_LOGGED_OUT } from "@/lib/auth";
import { formatBillingAmount as formatPricingAmount, getBillingCyclePriceLabel, getExamCyclePriceLabel, resolveCyclePricing } from "@/lib/billing-pricing";
import { pricingConfig, resolvePricingDisplayRegion } from "@/lib/pricing-config";
import { redirectToCheckoutUrl } from "@/lib/checkout-redirect";
import { redirectToLoginWithCurrentDestination } from "@/lib/route-guards";
import {
  PLAN_BILLING_SECTION_ID,
  getPlanDisplayName,
  getUsageProgressPercent,
  isPaidPlanType,
} from "@/lib/plans";
import { PLANS, TEACHER_PLUS_EXPORT_CALLOUT, getPaidPlanCtaLabel } from "@/src/config/plans";

function SettingsLoading() {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <Skeleton className="h-6 w-48" />
      <Skeleton className="h-4 w-64" />
      <Skeleton className="h-10 w-full" />
    </Card>
  );
}

function getUsageBarClasses(used: number, limit: number) {
  const progressPercent = getUsageProgressPercent(used, limit);
  if (progressPercent >= 85) {
    return "bg-red-500 dark:bg-red-400";
  }
  if (progressPercent >= 60) {
    return "bg-amber-500 dark:bg-amber-400";
  }
  return "bg-blue-600 dark:bg-blue-400";
}

function UsageMetric({
  label,
  used,
  limit,
  resetDateLabel,
}: Readonly<{
  label: string;
  used: number;
  limit: number;
  resetDateLabel: string;
}>) {
  const progressPercent = getUsageProgressPercent(used, limit);
  const hasReachedLimit = limit > 0 && used >= limit;
  const metricTestId = `usage-metric-${label.toLowerCase().replaceAll(/\s+/g, "-")}`;
  return (
    <div data-testid={metricTestId} className="space-y-3 rounded-md border border-border bg-background p-4">
      <div className="space-y-1">
        <p className="text-sm font-medium text-foreground">{label}</p>
        <p className="text-sm text-foreground/70">
          {used} / {limit}
        </p>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-muted">
        <div
          className={`h-full rounded-full transition-all ${getUsageBarClasses(used, limit)}`}
          style={{ width: `${progressPercent}%` }}
        />
      </div>
      {hasReachedLimit ? (
        <p className="text-xs text-foreground/70">
          You&apos;ve reached your limit for this cycle. Limits reset on: {resetDateLabel}
        </p>
      ) : null}
    </div>
  );
}

function ExportUsageMetric({
  label,
  used,
  limit,
  remaining,
  resetDateLabel,
}: Readonly<{
  label: string;
  used: number;
  limit: number | null;
  remaining: number | null;
  resetDateLabel: string;
}>) {
  if (limit === null) {
    return (
      <div data-testid={`usage-metric-${label.toLowerCase().replaceAll(/\s+/g, "-")}`} className="space-y-1 rounded-md border border-border bg-background p-4">
        <p className="text-sm font-medium text-foreground">{label}</p>
        <p className="text-sm text-foreground/70">{used} used / Unlimited</p>
        <p className="text-xs text-foreground/60">Unlimited remaining this cycle.</p>
      </div>
    );
  }

  return (
    <div className="space-y-2">
      <UsageMetric label={label} used={used} limit={limit} resetDateLabel={resetDateLabel} />
      <p className="px-1 text-xs text-foreground/60">{remaining ?? 0} remaining this cycle.</p>
    </div>
  );
}

function PlanFeatureList({
  features,
}: Readonly<{
  features: ReadonlyArray<{ label: string; helper?: string }>;
}>) {
  return (
    <ul className="mb-5 flex-1 space-y-2 text-sm text-foreground/80">
      {features.map((feature) => (
        <li key={feature.label} className="flex items-start gap-2">
          <Check className="mt-0.5 h-4 w-4 shrink-0 text-blue-600 dark:text-blue-400" aria-hidden="true" />
          <span>
            {feature.label}
            {feature.helper ? <span className="block text-xs text-foreground/50">{feature.helper}</span> : null}
          </span>
        </li>
      ))}
    </ul>
  );
}

const CANCELLATION_REASONS: Array<{
  value: SubscriptionCancellationReason;
  label: string;
}> = [
  { value: "TOO_EXPENSIVE", label: "Too expensive" },
  { value: "NOT_USING_ENOUGH", label: "Not using it enough" },
  { value: "MISSING_FEATURES", label: "Missing features I need" },
  { value: "TECHNICAL_ISSUES", label: "Technical issues" },
  { value: "FOUND_ANOTHER_TOOL", label: "Found another tool" },
  { value: "JUST_TRYING_IT_OUT", label: "Just trying it out" },
  { value: "OTHER", label: "Other" },
];

export default function SettingsPage() {
  const pathname = usePathname();
  const router = useRouter();
  const searchParams = useSearchParams();
  const sectionParam = searchParams.get("section");
  const highlightPlansSection = sectionParam === "plans";
  const startRouteProgress = useRouteProgress();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [profile, setProfile] = useState<MeResponse | null>(null);
  const [usageSummary, setUsageSummary] = useState<MePlanResponse | null>(null);
  const [billingHistory, setBillingHistory] = useState<BillingHistoryResponse | null>(null);
  const [billingPricing, setBillingPricing] = useState<BillingPricingResponse | null>(null);
  const [signingOut, setSigningOut] = useState(false);
  const [selectedEngagementMode, setSelectedEngagementMode] = useState<EngagementMode>("FOCUSED");
  const [savingEngagementMode, setSavingEngagementMode] = useState(false);
  const [engagementModeMessage, setEngagementModeMessage] = useState<string | null>(null);
  const [inactivityRemindersEnabled, setInactivityRemindersEnabled] = useState(false);
  const [weakConceptRemindersEnabled, setWeakConceptRemindersEnabled] = useState(false);
  const [weeklySummaryRemindersEnabled, setWeeklySummaryRemindersEnabled] = useState(false);
  const [marketingEmailsEnabled, setMarketingEmailsEnabled] = useState(false);
  const [savingEmailPreferences, setSavingEmailPreferences] = useState(false);
  const [emailPreferencesMessage, setEmailPreferencesMessage] = useState<string | null>(null);
  const [isCancellationModalOpen, setIsCancellationModalOpen] = useState(false);
  const [selectedCancellationReason, setSelectedCancellationReason] = useState<SubscriptionCancellationReason | null>(null);
  const [cancellationFeedback, setCancellationFeedback] = useState("");
  const [cancellationError, setCancellationError] = useState<string | null>(null);
  const [cancellingSubscription, setCancellingSubscription] = useState(false);
  const [startingCheckoutKey, setStartingCheckoutKey] = useState<string | null>(null);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [selectedCycle, setSelectedCycle] = useState<BillingCycle>("MONTHLY");
  const [verifyEmailModalOpen, setVerifyEmailModalOpen] = useState(false);

  const scrollToPlanBillingSection = useCallback((force = false) => {
    if (globalThis.window === undefined) {
      return;
    }
    const hashRequested = globalThis.location.hash === `#${PLAN_BILLING_SECTION_ID}`;
    if (!force && !hashRequested) {
      return;
    }
    const section = globalThis.document.getElementById(PLAN_BILLING_SECTION_ID);
    if (!section) {
      return;
    }
    globalThis.requestAnimationFrame(() => {
      section.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  }, []);

  const loadProfile = useCallback(async () => {
    const authUser = getAuthUser();
    if (!authUser) {
      redirectToLoginWithCurrentDestination(router);
      return;
    }

    setLoading(true);
    setError(null);
    setEngagementModeMessage(null);
    setEmailPreferencesMessage(null);
    try {
      const [me, usage, history, pricing] = await Promise.all([
        getMe(),
        getMyPlan(),
        getBillingHistory(),
        getBillingPricing().catch(() => null),
      ]);
      setProfile(me);
      setUsageSummary(usage);
      setBillingHistory(history);
      setBillingPricing(pricing);
      setSelectedEngagementMode(me.engagementMode);
      setInactivityRemindersEnabled(me.inactivityRemindersEnabled);
      setWeakConceptRemindersEnabled(me.weakConceptRemindersEnabled);
      setWeeklySummaryRemindersEnabled(me.weeklySummaryRemindersEnabled);
      setMarketingEmailsEnabled(me.marketingEmailsEnabled);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load settings.";
      setError(message);
      setProfile(null);
      setUsageSummary(null);
      setBillingHistory(null);
      setBillingPricing(null);
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    void loadProfile();
  }, [loadProfile]);

  useEffect(() => {
    if (loading) {
      return;
    }
    const timeoutId = globalThis.setTimeout(() => {
      scrollToPlanBillingSection(highlightPlansSection);
    }, 0);
    return () => {
      globalThis.clearTimeout(timeoutId);
    };
  }, [highlightPlansSection, loading, scrollToPlanBillingSection]);

  useEffect(() => {
    const handleHashChange = () => {
      scrollToPlanBillingSection();
    };
    globalThis.addEventListener("hashchange", handleHashChange);
    return () => {
      globalThis.removeEventListener("hashchange", handleHashChange);
    };
  }, [scrollToPlanBillingSection]);

  const handleSignOut = async () => {
    setSigningOut(true);
    try {
      await logout();
      startRouteProgress();
      router.push(
        buildLoginPath({
          reason: LOGIN_REASON_LOGGED_OUT,
        }),
      );
      router.refresh();
    } finally {
      setSigningOut(false);
    }
  };

  const handleSaveEngagementMode = async () => {
    setSavingEngagementMode(true);
    setEngagementModeMessage(null);
    try {
      const updated = await updateEngagementMode({ engagementMode: selectedEngagementMode });
      setProfile(updated);
      setSelectedEngagementMode(updated.engagementMode);
      setInactivityRemindersEnabled(updated.inactivityRemindersEnabled);
      setWeakConceptRemindersEnabled(updated.weakConceptRemindersEnabled);
      setWeeklySummaryRemindersEnabled(updated.weeklySummaryRemindersEnabled);
      setMarketingEmailsEnabled(updated.marketingEmailsEnabled);
      setEngagementModeMessage("Learning style updated.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not update learning style.";
      setEngagementModeMessage(message);
    } finally {
      setSavingEngagementMode(false);
    }
  };

  const handleSaveEmailPreferences = async () => {
    setSavingEmailPreferences(true);
    setEmailPreferencesMessage(null);
    try {
      const updated = await updateEmailPreferences({
        inactivityRemindersEnabled,
        weakConceptRemindersEnabled,
        weeklySummaryRemindersEnabled,
        marketingEmailsEnabled,
      });
      setProfile(updated);
      setSelectedEngagementMode(updated.engagementMode);
      setInactivityRemindersEnabled(updated.inactivityRemindersEnabled);
      setWeakConceptRemindersEnabled(updated.weakConceptRemindersEnabled);
      setWeeklySummaryRemindersEnabled(updated.weeklySummaryRemindersEnabled);
      setMarketingEmailsEnabled(updated.marketingEmailsEnabled);
      setEmailPreferencesMessage("Email preferences updated.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not update email preferences.";
      setEmailPreferencesMessage(message);
    } finally {
      setSavingEmailPreferences(false);
    }
  };

  const handleStartCheckout = async (
    planType: "PLUS" | "PRO",
    billingCycle: BillingCycle = "MONTHLY",
  ) => {
    const authUser = getAuthUser();
    if (authUser && !authUser.emailVerifiedAt) {
      setVerifyEmailModalOpen(true);
      return;
    }

    const checkoutKey = `${planType}-${billingCycle}`;
    setStartingCheckoutKey(checkoutKey);
    setCheckoutError(null);
    try {
      void trackAnalyticsEvent({
        eventType: "UPGRADE_CLICKED",
        metadata: {
          source: "settings_plan_billing",
          feature: `${planType.toLowerCase()}_checkout_${billingCycle.toLowerCase()}`,
          path: pathname,
          target: "xendit_checkout",
        },
      });
      const response = await createPremiumCheckoutSession({
        planType,
        billingCycle,
        returnUrl: getSafeRedirectPath(getCurrentPathWithQuery()),
      });
      redirectToCheckoutUrl(response.checkoutUrl);
    } catch (error) {
      if (isEmailNotVerifiedError(error)) {
        setVerifyEmailModalOpen(true);
      } else {
        setCheckoutError(error instanceof Error ? error.message : "Could not start checkout. Please try again.");
      }
    } finally {
      setStartingCheckoutKey(null);
    }
  };

  const handleOpenCancellationModal = () => {
    setSelectedCancellationReason(null);
    setCancellationFeedback("");
    setCancellationError(null);
    setIsCancellationModalOpen(true);
  };

  const handleCloseCancellationModal = () => {
    if (cancellingSubscription) {
      return;
    }
    setIsCancellationModalOpen(false);
  };

  const handleConfirmCancellation = async () => {
    setCancellingSubscription(true);
    setCancellationError(null);
    try {
      const request: CancelPremiumSubscriptionRequest = {
        reason: selectedCancellationReason,
        feedback: cancellationFeedback.trim() || null,
      };
      const updatedProfile = await cancelPremiumSubscription(request);
      setProfile((currentProfile) => {
        const baseProfile = updatedProfile ?? currentProfile;
        if (!baseProfile) {
          return currentProfile;
        }
        return {
          ...baseProfile,
          subscription: {
            ...baseProfile.subscription,
            cancelAtPeriodEnd: true,
            premiumEndsAt: baseProfile.subscription.premiumEndsAt ?? currentProfile?.subscription.premiumEndsAt ?? null,
            cancelledAt: baseProfile.subscription.cancelledAt ?? currentProfile?.subscription.cancelledAt ?? new Date().toISOString(),
          },
        };
      });
      setBillingHistory((currentHistory) => {
        if (!currentHistory) {
          return currentHistory;
        }
        return {
          ...currentHistory,
          cancelAtPeriodEnd: true,
          cancellationEffectiveAt:
            updatedProfile?.subscription.premiumEndsAt ?? currentHistory.cancellationEffectiveAt ?? currentHistory.currentPeriodEnd,
          currentPeriodEnd: updatedProfile?.subscription.premiumEndsAt ?? currentHistory.currentPeriodEnd,
        };
      });
      setIsCancellationModalOpen(false);
    } catch {
      setCancellationError("Something went wrong. Please try again or contact support@mail.notelib.app.");
    } finally {
      setCancellingSubscription(false);
    }
  };

  const currentPlan = usageSummary?.plan ?? profile?.planType ?? "FREE";
  const isCancellationScheduled = Boolean(
    billingHistory?.cancelAtPeriodEnd ?? (profile?.subscription.cancelAtPeriodEnd && profile.subscription.premiumEndsAt),
  );
  const studyPacksUsed = usageSummary?.usage.studyPacksUsed ?? 0;
  const studyPacksLimit = usageSummary?.limits.studyPacksPerMonth ?? 0;
  const challengeQuizUsed = usageSummary?.usage.challengeQuizzesUsed ?? 0;
  const challengeQuizLimit = usageSummary?.limits.challengeQuizzesPerMonth ?? 0;
  const adaptivePracticeUsed = usageSummary?.usage.adaptivePracticeUsed ?? 0;
  const adaptivePracticeLimit = usageSummary?.limits.adaptivePracticePerMonth ?? 0;
  const interviewPracticeUsed = usageSummary?.usage.interviewPracticeUsed ?? 0;
  const interviewPracticeLimit = usageSummary?.limits.interviewPracticePerMonth ?? 0;
  const longExamUsed = usageSummary?.usage.longExamUsed ?? 0;
  const longExamLimit = usageSummary?.limits.longExamPerMonth ?? 0;
  const boardExamUsed = usageSummary?.usage.boardExamUsed ?? 0;
  const boardExamLimit = usageSummary?.limits.boardExamPerMonth ?? 0;
  const docxExportsUsed = usageSummary?.usage.docxExportsUsed ?? 0;
  const docxExportsLimit = usageSummary?.limits.docxExportsPerMonth ?? null;
  const docxExportsRemaining = usageSummary?.remaining.docxExportsRemaining ?? null;
  const pdfExportsUsed = usageSummary?.usage.pdfExportsUsed ?? 0;
  const pdfExportsLimit = usageSummary?.limits.pdfExportsPerMonth ?? null;
  const pdfExportsRemaining = usageSummary?.remaining.pdfExportsRemaining ?? null;
  const plusMonthlyPriceLabel = getBillingCyclePriceLabel(billingPricing, "PLUS", "MONTHLY");
  const proMonthlyPriceLabel = getBillingCyclePriceLabel(billingPricing, "PRO", "MONTHLY");
  const proAnnualPriceLabel = getBillingCyclePriceLabel(billingPricing, "PRO", "YEARLY");
  const plusAnnualPricing = resolveCyclePricing(billingPricing, "PLUS", "YEARLY");
  const proAnnualPricing = resolveCyclePricing(billingPricing, "PRO", "YEARLY");
  const proExamCyclePricing = resolveCyclePricing(billingPricing, "PRO", "EXAM_CYCLE");
  const proExamCyclePriceLabel = proExamCyclePricing?.available
    ? getExamCyclePriceLabel(billingPricing, "PRO")
    : null;
  const billingTransactions = billingHistory?.transactions ?? [];

  const displayRegion = resolvePricingDisplayRegion(billingPricing?.region);
  const annualAvailableForPro = proAnnualPricing?.available ?? (pricingConfig.price[displayRegion].pro.yearly !== null);
  const annualAvailableForPlus = plusAnnualPricing?.available ?? (pricingConfig.price[displayRegion].plus.yearly !== null);
  const hasAnnualOption = annualAvailableForPro || annualAvailableForPlus;
  const freePlanConfig = PLANS.FREE;
  const plusPlanConfig = PLANS.PLUS;
  const proPlanConfig = PLANS.PRO;
  const proYearlySavingsPct = (() => {
    const monthly = billingPricing?.pro.monthly.amount ?? pricingConfig.price[displayRegion].pro.monthly;
    const yearly = billingPricing?.pro.yearly.amount ?? pricingConfig.price[displayRegion].pro.yearly;
    if (!yearly || !monthly) return null;
    return Math.round((1 - yearly / (monthly * 12)) * 100);
  })();
  const proAnnualLabelForDisplay = (() => {
    if (proAnnualPricing?.available) return proAnnualPriceLabel;
    const yearly = pricingConfig.price[displayRegion].pro.yearly;
    if (!yearly) return null;
    return `${formatPricingAmount(yearly, pricingConfig.price[displayRegion].currency)}/year`;
  })();
  const effectivePlusCycle: BillingCycle = "MONTHLY";
  const effectiveProCycle: BillingCycle = selectedCycle === "YEARLY" && annualAvailableForPro ? "YEARLY" : "MONTHLY";

  const formatBillingDate = (rawDate: string | null) => {
    if (!rawDate) {
      return "the end of your current billing period";
    }
    const value = new Date(rawDate);
    if (Number.isNaN(value.getTime())) {
      return rawDate;
    }
    return value.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
  };

  const formatUsageResetDate = (rawDate: string | null | undefined) => {
    if (!rawDate) {
      return "—";
    }
    const value = new Date(rawDate);
    if (Number.isNaN(value.getTime())) {
      return rawDate;
    }
    return value.toLocaleDateString(undefined, { month: "long", day: "numeric" });
  };

  const formatBillingAmount = (amount: number, currency: string) => {
    try {
      return new Intl.NumberFormat(undefined, {
        style: "currency",
        currency,
        minimumFractionDigits: 2,
        maximumFractionDigits: 2,
      }).format(amount);
    } catch {
      return `${Number(amount).toFixed(2)} ${currency}`;
    }
  };

  const formatSubscriptionBillingCycle = (billingType: BillingCycle | null | undefined) => {
    if (billingType === "EXAM_CYCLE") {
      return "90-Day Exam Pass";
    }
    if (billingType === "YEARLY") {
      return "Yearly";
    }
    return "Monthly";
  };

  const getTransactionStatusLabel = (status: BillingHistoryItemResponse["status"]) => {
    switch (status) {
      case "SUCCESS":
        return "Paid";
      case "FAILED":
        return "Failed";
      case "REFUNDED":
        return "Refunded";
      default:
        return "Pending";
    }
  };

  const getTransactionStatusClasses = (status: BillingHistoryItemResponse["status"]) => {
    switch (status) {
      case "SUCCESS":
        return "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300";
      case "FAILED":
        return "border-red-500/30 bg-red-500/10 text-red-700 dark:text-red-300";
      case "REFUNDED":
        return "border-border bg-muted text-foreground/75";
      default:
        return "border-border bg-background text-foreground/75";
    }
  };

  const subscriptionSummaryPlan = billingHistory?.currentPlan ?? currentPlan;
  const subscriptionSummaryStatus = (() => {
    if (!isPaidPlanType(subscriptionSummaryPlan)) {
      return "Free plan";
    }
    if (billingHistory?.subscriptionStatus === "ACTIVE") {
      return "Active";
    }
    if (billingHistory?.subscriptionStatus === "EXPIRED") {
      return "Expired";
    }
    if (billingHistory?.subscriptionStatus === "CANCELED") {
      return "Canceled";
    }
    return `${getPlanDisplayName(subscriptionSummaryPlan)} plan`;
  })();
  const subscriptionSummaryDate = billingHistory?.currentPeriodEnd;
  const subscriptionSummaryDateLabel =
    isPaidPlanType(subscriptionSummaryPlan)
      ? "Valid until"
      : "Status";
  const subscriptionBillingCycleLabel = isPaidPlanType(subscriptionSummaryPlan)
    ? `${formatSubscriptionBillingCycle(billingHistory?.billingType)} · Manual renewal`
    : "—";
  const usageResetDateLabel = formatUsageResetDate(usageSummary?.usageCycle?.endsAt);
  const cancellationAccessEndsAt = billingHistory?.cancellationEffectiveAt ?? billingHistory?.currentPeriodEnd ?? null;
  const cancellationScheduledLabel = cancellationAccessEndsAt
    ? `Access ends ${formatBillingDate(cancellationAccessEndsAt)}`
    : "Cancellation scheduled";

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      {loading ? (
        <SettingsLoading />
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">Could not load settings</h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <ResponsiveActionButton type="button" className="w-full sm:w-auto" onClick={() => void loadProfile()} action="retry" label="Retry" />
        </Card>
      ) : profile ? (
        <div className="space-y-6">
          <PageHeader
            eyebrow="SETTINGS"
            title="Configuration"
            description="Manage account settings, plan and billing details, and learning preferences."
            actions={
              <a
                href="/help"
                className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400"
              >
                Help Center
              </a>
            }
          />

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Preferences</h2>
            <p className="text-sm text-foreground/70">
              Keep NoteLib aligned with the way you study. Theme and Learning Style can be adjusted here anytime.
            </p>
            <div className="space-y-3 rounded-md border border-border bg-background p-4">
              <div className="space-y-1">
                <p className="text-sm font-medium">Theme</p>
                <p className="text-xs text-foreground/60">
                  Choose Light, Dark, or System. System follows your device setting automatically.
                </p>
              </div>
              <ThemeSelector />
            </div>
            <div className="space-y-3 rounded-md border border-border bg-background p-4">
              <div className="space-y-1">
                <p className="text-sm font-medium">Learning Style</p>
                <p className="text-xs text-foreground/60">
                  Choose how much study encouragement you want from NoteLib.
                </p>
              </div>
              <div className="space-y-3">
                <label className="flex cursor-pointer items-start gap-3 rounded-md border border-border p-3">
                  <input
                    type="radio"
                    name="engagementMode"
                    value="FOCUSED"
                    checked={selectedEngagementMode === "FOCUSED"}
                    onChange={() => setSelectedEngagementMode("FOCUSED")}
                  />
                  <span className="space-y-1">
                    <span className="block text-sm font-medium">Focused</span>
                    <span className="block text-xs text-foreground/60">
                      Use NoteLib when you need it. No streaks or pressure.
                    </span>
                  </span>
                </label>
                <label className="flex cursor-pointer items-start gap-3 rounded-md border border-border p-3">
                  <input
                    type="radio"
                    name="engagementMode"
                    value="CONSISTENCY"
                    checked={selectedEngagementMode === "CONSISTENCY"}
                    onChange={() => setSelectedEngagementMode("CONSISTENCY")}
                  />
                  <span className="space-y-1">
                    <span className="block text-sm font-medium">Consistency</span>
                    <span className="block text-xs text-foreground/60">
                      Light encouragement to study regularly.
                    </span>
                  </span>
                </label>
                <label className="flex cursor-pointer items-start gap-3 rounded-md border border-border p-3">
                  <input
                    type="radio"
                    name="engagementMode"
                    value="STREAK"
                    checked={selectedEngagementMode === "STREAK"}
                    onChange={() => setSelectedEngagementMode("STREAK")}
                  />
                  <span className="space-y-1">
                    <span className="block text-sm font-medium">Streak</span>
                    <span className="block text-xs text-foreground/60">
                      Track consecutive study days.
                    </span>
                  </span>
                </label>
              </div>
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                <ResponsiveActionButton
                  type="button"
                  className="w-full sm:w-auto"
                  onClick={() => void handleSaveEngagementMode()}
                  loading={savingEngagementMode}
                  loadingText="Saving..."
                  action="save"
                  label="Save Learning Style"
                />
                {engagementModeMessage ? (
                  <p className="text-xs text-foreground/60">{engagementModeMessage}</p>
                ) : null}
              </div>
            </div>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Email Preferences</h2>
            <p className="text-sm text-foreground/70">
              Choose which optional emails you get. Account and billing emails are always sent so you stay secure and informed.
            </p>
            <div className="space-y-3 rounded-md border border-border bg-background p-4">
              <label className="flex items-start justify-between gap-4 rounded-md border border-border p-3">
                <span className="space-y-1">
                  <span className="block text-sm font-medium">Study reminders</span>
                  <span className="block text-xs text-foreground/60">
                    Nudges when you&apos;ve been inactive or left a note unfinished.
                  </span>
                </span>
                <input
                  type="checkbox"
                  checked={inactivityRemindersEnabled}
                  onChange={(event) => setInactivityRemindersEnabled(event.target.checked)}
                  disabled={savingEmailPreferences}
                />
              </label>
              <label className="flex items-start justify-between gap-4 rounded-md border border-border p-3">
                <span className="space-y-1">
                  <span className="block text-sm font-medium">Weak-concept nudges</span>
                  <span className="block text-xs text-foreground/60">
                    A reminder to revisit concepts you missed on a quiz.
                  </span>
                </span>
                <input
                  type="checkbox"
                  checked={weakConceptRemindersEnabled}
                  onChange={(event) => setWeakConceptRemindersEnabled(event.target.checked)}
                  disabled={savingEmailPreferences}
                />
              </label>
              <label className="flex items-start justify-between gap-4 rounded-md border border-border p-3">
                <span className="space-y-1">
                  <span className="block text-sm font-medium">Weekly summary</span>
                  <span className="block text-xs text-foreground/60">
                    A Sunday recap of your week&apos;s study progress.
                  </span>
                </span>
                <input
                  type="checkbox"
                  checked={weeklySummaryRemindersEnabled}
                  onChange={(event) => setWeeklySummaryRemindersEnabled(event.target.checked)}
                  disabled={savingEmailPreferences}
                />
              </label>
              <label className="flex items-start justify-between gap-4 rounded-md border border-border p-3">
                <span className="space-y-1">
                  <span className="block text-sm font-medium">Product news &amp; tips</span>
                  <span className="block text-xs text-foreground/60">
                    Occasional updates, study tips, and new features.
                  </span>
                </span>
                <input
                  type="checkbox"
                  checked={marketingEmailsEnabled}
                  onChange={(event) => setMarketingEmailsEnabled(event.target.checked)}
                  disabled={savingEmailPreferences}
                />
              </label>
            </div>
            <div className="space-y-2 rounded-md border border-border bg-background p-4">
              <p className="text-sm font-medium">Always sent</p>
              <ul className="space-y-1 text-xs text-foreground/60">
                <li>Account &amp; security — sign-in verification, password resets</li>
                <li>Billing — payment receipts, plan-expiry reminders, refunds</li>
              </ul>
            </div>
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <ResponsiveActionButton
                type="button"
                className="w-full sm:w-auto"
                onClick={() => void handleSaveEmailPreferences()}
                loading={savingEmailPreferences}
                loadingText="Saving..."
                action="save"
                label="Save email preferences"
              />
              {emailPreferencesMessage ? (
                <p className="text-xs text-foreground/60">{emailPreferencesMessage}</p>
              ) : null}
            </div>
          </Card>

          <Card
            id={PLAN_BILLING_SECTION_ID}
            className={`scroll-mt-24 space-y-4 p-4 sm:p-6 ${
              highlightPlansSection ? "ring-2 ring-primary/40 ring-offset-2 ring-offset-background" : ""
            }`}
          >
            <h2 className="text-lg font-semibold sm:text-xl">Plan &amp; Billing</h2>

            {/* Monthly Usage */}
            <div className="space-y-2 rounded-md border border-border bg-background p-4">
              <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Monthly Usage</p>
              <p className="text-xs text-foreground/60">Usage resets on: {usageResetDateLabel}</p>
              <div className="space-y-3">
                <UsageMetric
                  label="Study Packs"
                  used={studyPacksUsed}
                  limit={studyPacksLimit}
                  resetDateLabel={usageResetDateLabel}
                />
                <UsageMetric
                  label="Quiz"
                  used={challengeQuizUsed}
                  limit={challengeQuizLimit}
                  resetDateLabel={usageResetDateLabel}
                />
                <ExportUsageMetric
                  label="DOCX Exports"
                  used={docxExportsUsed}
                  limit={docxExportsLimit}
                  remaining={docxExportsRemaining}
                  resetDateLabel={usageResetDateLabel}
                />
                <ExportUsageMetric
                  label="PDF Exports"
                  used={pdfExportsUsed}
                  limit={pdfExportsLimit}
                  remaining={pdfExportsRemaining}
                  resetDateLabel={usageResetDateLabel}
                />
                {adaptivePracticeLimit > 0 ? (
                  <UsageMetric
                    label="Adaptive Practice"
                    used={adaptivePracticeUsed}
                    limit={adaptivePracticeLimit}
                    resetDateLabel={usageResetDateLabel}
                  />
                ) : null}
                {currentPlan === "PRO" || interviewPracticeUsed > 0 ? (
                  <UsageMetric
                    label="Interview Practice"
                    used={interviewPracticeUsed}
                    limit={interviewPracticeLimit}
                    resetDateLabel={usageResetDateLabel}
                  />
                ) : null}
                {currentPlan === "PRO" || longExamUsed > 0 ? (
                  <UsageMetric
                    label="Long Exam"
                    used={longExamUsed}
                    limit={longExamLimit}
                    resetDateLabel={usageResetDateLabel}
                  />
                ) : null}
                {currentPlan === "PRO" || boardExamUsed > 0 ? (
                  <UsageMetric
                    label="Board Exam"
                    used={boardExamUsed}
                    limit={boardExamLimit}
                    resetDateLabel={usageResetDateLabel}
                  />
                ) : null}
              </div>
            </div>

            {/* Plan Cards */}
            <div className="space-y-4 rounded-md border border-border bg-background p-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Choose Your Plan</p>
                {hasAnnualOption ? (
                  <div className="flex items-center gap-1 rounded-lg border border-border bg-muted/30 p-1">
                    <button
                      type="button"
                      onClick={() => setSelectedCycle("MONTHLY")}
                      className={`rounded-md px-3 py-1 text-xs font-medium transition-colors ${
                        selectedCycle === "MONTHLY"
                          ? "bg-blue-600 text-white shadow-sm dark:bg-blue-500 dark:text-slate-950"
                          : "text-foreground/60 hover:text-foreground"
                      }`}
                    >
                      Monthly
                    </button>
                    <button
                      type="button"
                      onClick={() => setSelectedCycle("YEARLY")}
                      className={`flex items-center gap-1.5 rounded-md px-3 py-1 text-xs font-medium transition-colors ${
                        selectedCycle === "YEARLY"
                          ? "bg-blue-600 text-white shadow-sm dark:bg-blue-500 dark:text-slate-950"
                          : "text-foreground/60 hover:text-foreground"
                      }`}
                    >
                      Annual
                      {proYearlySavingsPct ? (
                        <span className="rounded-full bg-emerald-500/15 px-1.5 py-0.5 text-[10px] font-semibold text-emerald-700 dark:text-emerald-300">
                          Save {proYearlySavingsPct}%
                        </span>
                      ) : null}
                    </button>
                  </div>
                ) : null}
              </div>

              <div className="grid gap-4 lg:grid-cols-3">
                {/* Free card */}
                <div className={`flex flex-col rounded-lg border p-4 ${currentPlan === "FREE" ? "border-blue-300 bg-blue-50/20 dark:border-blue-700 dark:bg-blue-950/10" : "border-border bg-muted/20"}`}>
                  <div className="mb-4 space-y-2">
                    {currentPlan === "FREE" ? (
                      <span className="inline-flex rounded-full border border-blue-500/20 bg-blue-500/10 px-2.5 py-0.5 text-xs font-semibold text-blue-700 dark:text-blue-300">
                        Current plan
                      </span>
                    ) : null}
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">{freePlanConfig.name}</p>
                    <p className="text-2xl font-bold">{freePlanConfig.title}</p>
                    <p className="text-sm leading-snug text-foreground/70">{freePlanConfig.description}</p>
                  </div>
                  <PlanFeatureList features={freePlanConfig.features} />
                  {currentPlan === "FREE" ? (
                    <Button variant="outline" className="w-full" disabled>Current Plan</Button>
                  ) : null}
                </div>

                {/* Plus card */}
                <div className={`flex flex-col rounded-lg border p-4 ${currentPlan === "PLUS" ? "border-blue-300 bg-blue-50/30 dark:border-blue-700 dark:bg-blue-950/15" : "border-border bg-background"}`}>
                  <div className="mb-4 space-y-2">
                    {currentPlan === "PLUS" ? (
                      <span className="inline-flex rounded-full border border-blue-500/20 bg-blue-500/10 px-2.5 py-0.5 text-xs font-semibold text-blue-700 dark:text-blue-300">
                        Current plan
                      </span>
                    ) : null}
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">{plusPlanConfig.name}</p>
                    <p className="text-lg font-semibold text-foreground">{plusPlanConfig.title}</p>
                    <p className="text-sm leading-snug text-foreground/70">{plusPlanConfig.description}</p>
                    <p className="text-xl font-semibold">{plusMonthlyPriceLabel}</p>
                    {selectedCycle === "YEARLY" && !annualAvailableForPlus ? (
                      <p className="text-xs text-foreground/50">Monthly billing only</p>
                    ) : null}
                  </div>
                  <PlanFeatureList features={plusPlanConfig.features} />
                  {profile?.profileType === "TEACHER" ? (
                    <p className="mb-3 rounded-md border border-blue-500/20 bg-blue-500/10 px-3 py-2 text-xs font-medium text-blue-700 dark:text-blue-300">
                      {TEACHER_PLUS_EXPORT_CALLOUT}
                    </p>
                  ) : null}
                  <p className="mb-5 text-xs text-foreground/55">{plusPlanConfig.adaptivePracticeMessage}</p>
                  <div className="space-y-2">
                    {currentPlan === "FREE" ? (
                      <ResponsiveActionButton
                        type="button"
                        className="w-full"
                        onClick={() => void handleStartCheckout("PLUS", effectivePlusCycle)}
                        loading={startingCheckoutKey === `PLUS-${effectivePlusCycle}`}
                        loadingText="Redirecting..."
                        action="studyPack"
                        label={getPaidPlanCtaLabel("PLUS")}
                      />
                    ) : currentPlan === "PLUS" ? (
                      <>
                        <Button variant="outline" className="w-full" disabled>Current Plan</Button>
                        {!isCancellationScheduled ? (
                          <button
                            type="button"
                            className="w-full text-center text-xs text-foreground/50 transition-colors hover:text-foreground/70"
                            onClick={handleOpenCancellationModal}
                          >
                            Cancel plan
                          </button>
                        ) : (
                          <p className="text-center text-xs text-amber-600 dark:text-amber-400">{cancellationScheduledLabel}</p>
                        )}
                      </>
                    ) : null}
                  </div>
                </div>

                {/* Pro card */}
                <div className={`flex flex-col rounded-lg border p-4 ${currentPlan === "PRO" ? "border-blue-400 bg-blue-50/40 dark:border-blue-600 dark:bg-blue-950/20" : "border-blue-200 bg-blue-50/20 dark:border-blue-800/60 dark:bg-blue-950/10"}`}>
                  <div className="mb-4 space-y-2">
                    {currentPlan === "PRO" ? (
                      <span className="inline-flex rounded-full border border-blue-500/20 bg-blue-500/10 px-2.5 py-0.5 text-xs font-semibold text-blue-700 dark:text-blue-300">
                        Current plan
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1.5 rounded-full border border-blue-500/20 bg-blue-500/10 px-2.5 py-0.5 text-xs font-semibold text-blue-700 dark:text-blue-300">
                        <Sparkles className="h-3 w-3" aria-hidden="true" />
                        {proPlanConfig.eyebrow}
                      </span>
                    )}
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">{proPlanConfig.name}</p>
                    <p className="text-lg font-semibold text-foreground">{proPlanConfig.title}</p>
                    <p className="text-sm leading-snug text-foreground/70">{proPlanConfig.description}</p>
                    <p className="text-xl font-semibold">
                      {selectedCycle === "YEARLY" && annualAvailableForPro
                        ? (proAnnualLabelForDisplay ?? proMonthlyPriceLabel)
                        : proMonthlyPriceLabel}
                    </p>
                    {proExamCyclePriceLabel ? (
                      <p className="text-sm font-medium text-blue-700 dark:text-blue-300">
                        90-Day Exam Pass: {proExamCyclePriceLabel}
                      </p>
                    ) : null}
                  </div>
                  <PlanFeatureList features={proPlanConfig.features} />
                  <p className="mb-5 text-xs text-foreground/55">{proPlanConfig.adaptivePracticeMessage}</p>
                  <div className="space-y-2">
                    {currentPlan !== "PRO" ? (
                      <>
                        <ResponsiveActionButton
                          type="button"
                          className="w-full"
                          onClick={() => void handleStartCheckout("PRO", effectiveProCycle)}
                          loading={startingCheckoutKey === `PRO-${effectiveProCycle}`}
                          loadingText="Redirecting..."
                          action="studyPack"
                          label={getPaidPlanCtaLabel("PRO")}
                        />
                        {proExamCyclePriceLabel ? (
                          <ResponsiveActionButton
                            type="button"
                            variant="outline"
                            className="w-full"
                            onClick={() => void handleStartCheckout("PRO", "EXAM_CYCLE")}
                            loading={startingCheckoutKey === "PRO-EXAM_CYCLE"}
                            loadingText="Redirecting..."
                            action="studyPack"
                            label="Go Pro — 90-Day Exam Pass"
                          />
                        ) : null}
                      </>
                    ) : (
                      <>
                        <Button variant="outline" className="w-full" disabled>Current Plan</Button>
                        {!isCancellationScheduled ? (
                          <button
                            type="button"
                            className="w-full text-center text-xs text-foreground/50 transition-colors hover:text-foreground/70"
                            onClick={handleOpenCancellationModal}
                          >
                            Cancel plan
                          </button>
                        ) : (
                          <p className="text-center text-xs text-amber-600 dark:text-amber-400">{cancellationScheduledLabel}</p>
                        )}
                      </>
                    )}
                  </div>
                </div>
              </div>

              <div className="rounded-md border border-border bg-muted/30 p-3 text-xs text-foreground/60">
                Hosted checkout via Xendit. Access activates after payment confirmation. Manual renewal. ·{" "}
                <Link href="/refund" className="underline underline-offset-2">
                  Refund Policy
                </Link>
                {billingPricing ? <span> · {billingPricing.region} · {billingPricing.currency}</span> : null}
              </div>
              {checkoutError ? (
                <p className="text-xs text-red-600 dark:text-red-300">{checkoutError}</p>
              ) : null}
            </div>

            <div className="space-y-4 rounded-md border border-border bg-background p-4">
              <div className="space-y-1">
                <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Billing History</p>
                <h3 className="text-base font-semibold text-foreground">Subscription Summary</h3>
              </div>

              <div className="space-y-4 rounded-md border border-border bg-muted/30 p-4">
                <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
                  <div className="space-y-1">
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Current plan</p>
                    <p className="text-sm font-medium text-foreground">
                      {getPlanDisplayName(subscriptionSummaryPlan)}
                    </p>
                  </div>
                  <div className="space-y-1">
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Status</p>
                    <p className="text-sm font-medium text-foreground">{subscriptionSummaryStatus}</p>
                  </div>
                  <div className="space-y-1">
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">
                      {subscriptionSummaryDateLabel}
                    </p>
                    <p className="text-sm font-medium text-foreground">
                      {isPaidPlanType(subscriptionSummaryPlan)
                        ? (subscriptionSummaryDate ? formatBillingDate(subscriptionSummaryDate) : "Manual renewal")
                        : "No active subscription"}
                    </p>
                  </div>
                  <div className="space-y-1">
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Billing cycle</p>
                    <p className="text-sm font-medium text-foreground">{subscriptionBillingCycleLabel}</p>
                  </div>
                </div>

                {isPaidPlanType(subscriptionSummaryPlan) ? (
                  <p className="text-sm text-foreground/75">
                    {subscriptionSummaryDate
                      ? `Your ${getPlanDisplayName(subscriptionSummaryPlan)} access is active until ${formatBillingDate(subscriptionSummaryDate)}. Renew manually whenever you're ready.`
                      : `Your ${getPlanDisplayName(subscriptionSummaryPlan)} access is active. Renewal is manual in the current billing model.`}
                  </p>
                ) : (
                  <p className="text-sm text-foreground/75">
                    Your payment history will appear here once you subscribe to a paid plan.
                  </p>
                )}
              </div>

              <div className="space-y-3">
                <h3 className="text-base font-semibold text-foreground">Payment History</h3>
                {billingTransactions.length === 0 ? (
                  <div className="rounded-md border border-dashed border-border p-4 text-sm">
                    <p className="font-medium text-foreground">No billing history yet</p>
                    <p className="mt-1 text-foreground/70">
                      Your payment history will appear here once you subscribe to Plus or Pro.
                    </p>
                  </div>
                ) : (
                  <>
                    <div className="hidden overflow-x-auto rounded-md border border-border md:block">
                      <table className="min-w-full divide-y divide-border text-sm">
                        <thead className="bg-muted/50">
                          <tr className="text-left text-xs uppercase tracking-wide text-foreground/60">
                            <th className="px-4 py-3 font-semibold">Date</th>
                            <th className="px-4 py-3 font-semibold">Description</th>
                            <th className="px-4 py-3 font-semibold">Amount</th>
                            <th className="px-4 py-3 font-semibold">Status</th>
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-border bg-background">
                          {billingTransactions.map((item) => (
                            <tr key={item.id}>
                              <td className="px-4 py-3 text-foreground/75">{formatBillingDate(item.date)}</td>
                              <td className="px-4 py-3">
                                <p className="font-medium text-foreground">{item.description}</p>
                                <p className="mt-1 text-xs text-foreground/60">
                                  {item.provider} • <span className="font-mono">{item.providerReferenceId}</span>
                                </p>
                              </td>
                              <td className="px-4 py-3 text-foreground/75">
                                {formatBillingAmount(item.amount, item.currency)}
                              </td>
                              <td className="px-4 py-3">
                                <span
                                  className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-medium ${getTransactionStatusClasses(item.status)}`}
                                >
                                  {getTransactionStatusLabel(item.status)}
                                </span>
                              </td>
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>

                    <div className="space-y-3 md:hidden">
                      {billingTransactions.map((item) => (
                        <div key={item.id} className="rounded-md border border-border p-4 text-sm">
                          <div className="flex items-start justify-between gap-3">
                            <div className="space-y-1">
                              <p className="font-medium text-foreground">{item.description}</p>
                              <p className="text-xs text-foreground/60">{formatBillingDate(item.date)}</p>
                            </div>
                            <span
                              className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-medium ${getTransactionStatusClasses(item.status)}`}
                            >
                              {getTransactionStatusLabel(item.status)}
                            </span>
                          </div>
                          <div className="mt-3 space-y-1 text-xs text-foreground/70">
                            <p>{formatBillingAmount(item.amount, item.currency)}</p>
                            <p>{item.provider}</p>
                            <p className="font-mono">{item.providerReferenceId}</p>
                          </div>
                        </div>
                      ))}
                    </div>
                  </>
                )}
              </div>
            </div>
          </Card>

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Account</h2>
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <ResponsiveActionButton
                type="button"
                className="w-full sm:w-auto"
                onClick={() => void handleSignOut()}
                loading={signingOut}
                loadingText="Signing out..."
                action="signOut"
                label="Sign Out"
              />
              <ResponsiveActionButton type="button" variant="outline" className="w-full sm:w-auto" disabled action="delete" label="Delete Account (Coming Soon)" />
            </div>
          </Card>
        </div>
      ) : null}
      <AppModal
        isOpen={isCancellationModalOpen}
        title={`Cancel ${getPlanDisplayName(currentPlan)}?`}
        description={`Your ${getPlanDisplayName(currentPlan)} access will remain active until ${formatBillingDate(billingHistory?.currentPeriodEnd ?? null)}. After that, your account returns to the Free plan. Your notes and Study Packs will remain in your library.`}
        onClose={handleCloseCancellationModal}
        panelClassName="max-w-[560px]"
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <ResponsiveActionButton
              type="button"
              variant="outline"
              className="w-full sm:w-auto"
              onClick={handleCloseCancellationModal}
              disabled={cancellingSubscription}
              action="back"
              label={`Keep ${getPlanDisplayName(currentPlan)}`}
              showTextOnMobile
            />
            <ResponsiveActionButton
              type="button"
              className="w-full sm:w-auto"
              onClick={() => void handleConfirmCancellation()}
              loading={cancellingSubscription}
              loadingText="Confirming..."
              action="delete"
              label="Confirm Cancellation"
            />
          </div>
        )}
      >
        <div className="space-y-4">
          <fieldset className="space-y-2">
            <legend className="text-sm font-medium text-foreground">Why are you cancelling? (Optional)</legend>
            <div className="space-y-2">
              {CANCELLATION_REASONS.map((reason) => (
                <label
                  key={reason.value}
                  className="flex cursor-pointer items-start gap-3 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground"
                >
                  <input
                    type="radio"
                    name="cancellationReason"
                    value={reason.value}
                    checked={selectedCancellationReason === reason.value}
                    onChange={() => setSelectedCancellationReason(reason.value)}
                  />
                  <span>{reason.label}</span>
                </label>
              ))}
            </div>
          </fieldset>
          <label className="block space-y-2">
            <span className="text-sm font-medium text-foreground">Anything we can improve?</span>
            <textarea
              value={cancellationFeedback}
              onChange={(event) => setCancellationFeedback(event.target.value)}
              rows={4}
              maxLength={1000}
              className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
              placeholder="Optional feedback"
            />
          </label>
          {cancellationError ? (
            <p className="text-sm text-red-600 dark:text-red-400">{cancellationError}</p>
          ) : null}
        </div>
      </AppModal>
      <VerifyEmailRequiredModal
        isOpen={verifyEmailModalOpen}
        onClose={() => setVerifyEmailModalOpen(false)}
      />
    </main>
  );
}
