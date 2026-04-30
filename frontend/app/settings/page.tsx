"use client";

import { useCallback, useEffect, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
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
  updateStudyReminders,
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
import { formatBillingAmount as formatPricingAmount, getBillingCyclePriceLabel, resolveCyclePricing } from "@/lib/billing-pricing";
import { redirectToCheckoutUrl } from "@/lib/checkout-redirect";
import { redirectToLoginWithCurrentDestination } from "@/lib/route-guards";
import {
  PLAN_BILLING_SECTION_ID,
  getPlanDisplayName,
  getUsageProgressPercent,
  isPaidPlanType,
} from "@/lib/plans";

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
  showUpgradeCta,
  upgradeLabel,
  onUpgradeClick,
}: Readonly<{
  label: string;
  used: number;
  limit: number;
  resetDateLabel: string;
  showUpgradeCta: boolean;
  upgradeLabel: string;
  onUpgradeClick: () => void;
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
        <div className="space-y-2">
          <p className="text-xs text-foreground/70">
            You&apos;ve reached your limit for this cycle. Limits reset on: {resetDateLabel}
          </p>
          {showUpgradeCta ? (
            <Button type="button" variant="outline" size="sm" className="w-full sm:w-auto" onClick={onUpgradeClick}>
              {upgradeLabel}
            </Button>
          ) : null}
        </div>
      ) : null}
    </div>
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
  const [savingStudyReminders, setSavingStudyReminders] = useState(false);
  const [studyRemindersMessage, setStudyRemindersMessage] = useState<string | null>(null);
  const [isCancellationModalOpen, setIsCancellationModalOpen] = useState(false);
  const [selectedCancellationReason, setSelectedCancellationReason] = useState<SubscriptionCancellationReason | null>(null);
  const [cancellationFeedback, setCancellationFeedback] = useState("");
  const [cancellingSubscription, setCancellingSubscription] = useState(false);
  const [startingCheckoutKey, setStartingCheckoutKey] = useState<string | null>(null);
  const [checkoutError, setCheckoutError] = useState<string | null>(null);
  const [verifyEmailModalOpen, setVerifyEmailModalOpen] = useState(false);

  const scrollToPlanBillingSection = useCallback(() => {
    if (globalThis.window === undefined) {
      return;
    }
    if (globalThis.location.hash !== `#${PLAN_BILLING_SECTION_ID}`) {
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
    setStudyRemindersMessage(null);
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
      scrollToPlanBillingSection();
    }, 0);
    return () => {
      globalThis.clearTimeout(timeoutId);
    };
  }, [loading, scrollToPlanBillingSection]);

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
      setEngagementModeMessage("Learning style updated.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not update learning style.";
      setEngagementModeMessage(message);
    } finally {
      setSavingEngagementMode(false);
    }
  };

  const handleSaveStudyReminders = async () => {
    setSavingStudyReminders(true);
    setStudyRemindersMessage(null);
    try {
      const updated = await updateStudyReminders({
        inactivityRemindersEnabled,
        weakConceptRemindersEnabled,
      });
      setProfile(updated);
      setSelectedEngagementMode(updated.engagementMode);
      setInactivityRemindersEnabled(updated.inactivityRemindersEnabled);
      setWeakConceptRemindersEnabled(updated.weakConceptRemindersEnabled);
      setStudyRemindersMessage("Study reminders updated.");
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not update study reminders.";
      setStudyRemindersMessage(message);
    } finally {
      setSavingStudyReminders(false);
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
    } finally {
      setCancellingSubscription(false);
    }
  };

  const currentPlan = usageSummary?.plan ?? profile?.planType ?? "FREE";
  const isPaidPlan = isPaidPlanType(currentPlan);
  const isProPlan = currentPlan === "PRO";
  const upgradePlanType = currentPlan === "PLUS" ? "PRO" : "PLUS";
  const upgradePlanLabel = currentPlan === "PLUS" ? "Upgrade to Pro" : "Upgrade to Plus";
  const isCancellationScheduled = Boolean(
    billingHistory?.cancelAtPeriodEnd ?? (profile?.subscription.cancelAtPeriodEnd && profile.subscription.premiumEndsAt),
  );
  const studyPacksUsed = usageSummary?.usage.studyPacksUsed ?? 0;
  const studyPacksLimit = usageSummary?.limits.studyPacksPerMonth ?? 0;
  const challengeQuizUsed = usageSummary?.usage.challengeQuizzesUsed ?? 0;
  const challengeQuizLimit = usageSummary?.limits.challengeQuizzesPerMonth ?? 0;
  const adaptivePracticeUsed = usageSummary?.usage.adaptivePracticeUsed ?? 0;
  const adaptivePracticeLimit = usageSummary?.limits.adaptivePracticePerMonth ?? 0;
  const exportsUsed = usageSummary?.usage.exportsUsed ?? 0;
  const exportsLimit = usageSummary?.limits.exportsPerMonth ?? null;
  const difficultySelectionAvailable = usageSummary?.features.difficultySelectionAvailable ?? false;
  const plusMonthlyPriceLabel = getBillingCyclePriceLabel(billingPricing, "PLUS", "MONTHLY");
  const plusAnnualPriceLabel = getBillingCyclePriceLabel(billingPricing, "PLUS", "YEARLY");
  const proMonthlyPriceLabel = getBillingCyclePriceLabel(billingPricing, "PRO", "MONTHLY");
  const proAnnualPriceLabel = getBillingCyclePriceLabel(billingPricing, "PRO", "YEARLY");
  const plusMonthlyPricing = resolveCyclePricing(billingPricing, "PLUS", "MONTHLY");
  const plusAnnualPricing = resolveCyclePricing(billingPricing, "PLUS", "YEARLY");
  const proMonthlyPricing = resolveCyclePricing(billingPricing, "PRO", "MONTHLY");
  const proAnnualPricing = resolveCyclePricing(billingPricing, "PRO", "YEARLY");
  const billingTransactions = billingHistory?.transactions ?? [];

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
    ? `${billingHistory?.billingType === "YEARLY" ? "Yearly" : "Monthly"} · Manual renewal`
    : "—";
  const usageResetDateLabel = formatUsageResetDate(usageSummary?.usageCycle?.endsAt);

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
              Keep NoteLib aligned with the way you study. Theme, Learning Style, and reminder preferences can all be
              adjusted here anytime.
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
            <div className="space-y-4 rounded-md border border-border bg-background p-4">
              <div className="space-y-1">
                <p className="text-sm font-medium">Study Reminders</p>
                <p className="text-xs text-foreground/60">
                  Control the types of reminders you want. Future reminder timing will follow your Learning Style.
                </p>
              </div>
              <label className="flex items-start justify-between gap-4 rounded-md border border-border p-3">
                <span className="space-y-1">
                  <span className="block text-sm font-medium">Inactivity reminders</span>
                  <span className="block text-xs text-foreground/60">
                    Get reminded to come back when you have not studied for a while.
                  </span>
                </span>
                <input
                  type="checkbox"
                  checked={inactivityRemindersEnabled}
                  onChange={(event) => setInactivityRemindersEnabled(event.target.checked)}
                  disabled={savingStudyReminders}
                />
              </label>
              <label className="flex items-start justify-between gap-4 rounded-md border border-border p-3">
                <span className="space-y-1">
                  <span className="block text-sm font-medium">Weak concept reminders</span>
                  <span className="block text-xs text-foreground/60">
                    Get reminded to review topics you struggled with.
                  </span>
                </span>
                <input
                  type="checkbox"
                  checked={weakConceptRemindersEnabled}
                  onChange={(event) => setWeakConceptRemindersEnabled(event.target.checked)}
                  disabled={savingStudyReminders}
                />
              </label>
              <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                <ResponsiveActionButton
                  type="button"
                  className="w-full sm:w-auto"
                  onClick={() => void handleSaveStudyReminders()}
                  loading={savingStudyReminders}
                  loadingText="Saving..."
                  action="save"
                  label="Save Study Reminders"
                />
                {studyRemindersMessage ? (
                  <p className="text-xs text-foreground/60">{studyRemindersMessage}</p>
                ) : null}
              </div>
            </div>
          </Card>

          <Card id={PLAN_BILLING_SECTION_ID} className="scroll-mt-24 space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Plan &amp; Billing</h2>
            <div className="space-y-4 rounded-md border border-border bg-background p-4">
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Current Plan</p>
                <p className="mt-2 inline-flex rounded-full border border-border px-3 py-1 text-sm font-semibold">
                  {getPlanDisplayName(currentPlan)}
                </p>
              </div>
              <div className="space-y-2">
                <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Monthly Usage</p>
                <p className="text-xs text-foreground/60">Usage resets on: {usageResetDateLabel}</p>
                <div className="space-y-3">
                  <UsageMetric
                    label="Study Packs"
                    used={studyPacksUsed}
                    limit={studyPacksLimit}
                    resetDateLabel={usageResetDateLabel}
                    showUpgradeCta={!isProPlan}
                    upgradeLabel={upgradePlanLabel}
                    onUpgradeClick={() => void handleStartCheckout(upgradePlanType)}
                  />
                  <UsageMetric
                    label="Quiz"
                    used={challengeQuizUsed}
                    limit={challengeQuizLimit}
                    resetDateLabel={usageResetDateLabel}
                    showUpgradeCta={!isProPlan}
                    upgradeLabel={upgradePlanLabel}
                    onUpgradeClick={() => void handleStartCheckout(upgradePlanType)}
                  />
                  {exportsLimit !== null && exportsLimit !== undefined ? (
                    <UsageMetric
                      label="Exports"
                      used={exportsUsed}
                      limit={exportsLimit}
                      resetDateLabel={usageResetDateLabel}
                      showUpgradeCta={!isProPlan}
                      upgradeLabel={upgradePlanLabel}
                      onUpgradeClick={() => void handleStartCheckout(upgradePlanType)}
                    />
                  ) : null}
                  {adaptivePracticeLimit > 0 ? (
                    <UsageMetric
                      label="Adaptive Practice"
                      used={adaptivePracticeUsed}
                      limit={adaptivePracticeLimit}
                      resetDateLabel={usageResetDateLabel}
                      showUpgradeCta={false}
                      upgradeLabel="Upgrade"
                      onUpgradeClick={() => {}}
                    />
                  ) : null}
                </div>
              </div>
              {isPaidPlan ? (
                <div className="space-y-2">
                  <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Features</p>
                  <ul className="list-disc space-y-1 pl-5 text-sm text-foreground/80">
                    <li>Weak concepts visible</li>
                    <li>Higher monthly limits</li>
                    <li>{exportsLimit === null ? "Unlimited exports" : `${exportsLimit} exports per month`}</li>
                    <li>{isProPlan ? "Adaptive Practice available" : "Upgrade to Pro for Adaptive Practice"}</li>
                    {adaptivePracticeLimit > 0 ? <li>{adaptivePracticeLimit} Adaptive Practice sessions per month</li> : null}
                    <li>{difficultySelectionAvailable ? "Difficulty selection enabled" : "Upgrade to Pro for difficulty selection"}</li>
                    <li>{isProPlan ? "Board Exam Mode included" : "Upgrade to Pro for Board Exam Mode"}</li>
                    <li>{isProPlan ? "Highest note-generation and OCR limits" : "Expanded note-generation and OCR limits"}</li>
                    <li>Higher limits</li>
                    <li>Priority AI enabled</li>
                  </ul>
                </div>
              ) : (
                <div className="space-y-4 rounded-md border border-border bg-background p-4">
                  <div className="space-y-2">
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Free Features</p>
                    <ul className="list-disc space-y-1 pl-5 text-sm text-foreground/80">
                      <li>Save unlimited notes</li>
                      <li>{studyPacksLimit} Study Packs per month</li>
                      <li>Quick Review</li>
                      <li>{challengeQuizLimit} Quizzes per month</li>
                      <li>{exportsLimit ?? 0} exports per month</li>
                      <li>Weak concepts visible</li>
                      <li>File uploads available</li>
                      <li>Image to Text (OCR) - Limited</li>
                      <li>Public Library access</li>
                    </ul>
                  </div>
                  <div className="space-y-2">
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Paid Plan Highlights</p>
                    <ul className="list-disc space-y-1 pl-5 text-sm text-foreground/80">
                      <li>Plus: higher monthly limits and more exports</li>
                      <li>Pro: highest limits for serious exam prep</li>
                      <li>Adaptive Practice and Difficulty Selection on Pro</li>
                      <li>Board Exam Mode on Pro</li>
                      <li>Higher OCR and note-generation limits</li>
                    </ul>
                  </div>
                  <div className="space-y-2">
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Checkout Options</p>
                    {billingPricing ? (
                      <p className="text-xs text-foreground/60">
                        Regional pricing: {billingPricing.region} · {billingPricing.currency}
                      </p>
                    ) : null}
                    <div className="rounded-md border border-border bg-background p-3 text-sm">
                      <p className="font-medium text-foreground">Hosted checkout via Xendit</p>
                      <p className="mt-1 text-xs text-foreground/60">
                        Paid access activates after Xendit confirms payment by webhook.
                      </p>
                      <p className="mt-2 text-xs text-foreground/60">
                        Choose the plan that fits your study pace. Renewal stays manual for now.
                      </p>
                    </div>
                  </div>
                  <div className="grid gap-3 lg:grid-cols-2">
                    <div className="space-y-3 rounded-md border border-border bg-background p-4">
                      <div className="space-y-1">
                        <p className="text-sm font-medium text-foreground">Plus Monthly</p>
                        <p className="text-xs text-foreground/60">{plusMonthlyPriceLabel}</p>
                        {plusMonthlyPricing?.introEligible && plusMonthlyPricing.introAmount !== null ? (
                          <p className="text-xs text-foreground/60">
                            Intro offer: {formatPricingAmount(plusMonthlyPricing.introAmount, billingPricing?.currency ?? "PHP")} first month
                          </p>
                        ) : null}
                      </div>
                      <ResponsiveActionButton
                        type="button"
                        className="w-full"
                        onClick={() => void handleStartCheckout("PLUS", "MONTHLY")}
                        loading={startingCheckoutKey === "PLUS-MONTHLY"}
                        loadingText="Redirecting..."
                        action="studyPack"
                        label="Choose Plus"
                      />
                    </div>
                    <div className="space-y-3 rounded-md border border-border bg-background p-4">
                      <div className="space-y-1">
                        <p className="text-sm font-medium text-foreground">Pro Monthly</p>
                        <p className="text-xs text-foreground/60">{proMonthlyPriceLabel}</p>
                        {proMonthlyPricing?.introEligible && proMonthlyPricing.introAmount !== null ? (
                          <p className="text-xs text-foreground/60">
                            Intro offer: {formatPricingAmount(proMonthlyPricing.introAmount, billingPricing?.currency ?? "PHP")} first month
                          </p>
                        ) : null}
                      </div>
                      <ResponsiveActionButton
                        type="button"
                        className="w-full"
                        onClick={() => void handleStartCheckout("PRO", "MONTHLY")}
                        loading={startingCheckoutKey === "PRO-MONTHLY"}
                        loadingText="Redirecting..."
                        action="studyPack"
                        label="Choose Pro"
                      />
                    </div>
                  </div>
                  {plusAnnualPricing?.available || proAnnualPricing?.available ? (
                    <div className="grid gap-3 lg:grid-cols-2">
                      {plusAnnualPricing?.available ? (
                        <div className="space-y-3 rounded-md border border-border bg-background p-4">
                          <div className="space-y-1">
                            <p className="text-sm font-medium text-foreground">Plus Annual</p>
                            <p className="text-xs text-foreground/60">{plusAnnualPriceLabel}</p>
                          </div>
                          <ResponsiveActionButton
                            type="button"
                            className="w-full"
                            onClick={() => void handleStartCheckout("PLUS", "YEARLY")}
                            loading={startingCheckoutKey === "PLUS-YEARLY"}
                            loadingText="Redirecting..."
                            action="studyPack"
                            label="Choose Plus Annual"
                          />
                        </div>
                      ) : null}
                      {proAnnualPricing?.available ? (
                        <div className="space-y-3 rounded-md border border-border bg-background p-4">
                          <div className="space-y-1">
                            <p className="text-sm font-medium text-foreground">Pro Annual</p>
                            <p className="text-xs text-foreground/60">{proAnnualPriceLabel}</p>
                          </div>
                          <ResponsiveActionButton
                            type="button"
                            className="w-full"
                            onClick={() => void handleStartCheckout("PRO", "YEARLY")}
                            loading={startingCheckoutKey === "PRO-YEARLY"}
                            loadingText="Redirecting..."
                            action="studyPack"
                            label="Choose Pro Annual"
                          />
                        </div>
                      ) : null}
                    </div>
                  ) : null}
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                    {checkoutError ? (
                      <p className="text-xs text-red-600 dark:text-red-300">{checkoutError}</p>
                    ) : null}
                  </div>
                </div>
              )}
              {null}
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
        description={`Your ${getPlanDisplayName(currentPlan)} access will remain active until the end of your current billing period. After that, your account will return to the Free plan. Your notes and Study Packs will remain in your library.`}
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
        </div>
      </AppModal>
      <VerifyEmailRequiredModal
        isOpen={verifyEmailModalOpen}
        onClose={() => setVerifyEmailModalOpen(false)}
      />
    </main>
  );
}
