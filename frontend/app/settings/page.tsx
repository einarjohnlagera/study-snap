"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { AppModal } from "@/components/ui/app-modal";
import { PageHeader } from "@/components/page-header";
import {
  cancelPremiumSubscription,
  createPremiumCheckoutSession,
  getBillingPricing,
  getBillingHistory,
  getBillingUsageSummary,
  getMe,
  isEmailNotVerifiedError,
  logout,
  updateEngagementMode,
  updateStudyReminders,
  type BillingCycle,
  type BillingHistoryResponse,
  type BillingHistoryItemResponse,
  type BillingPricingResponse,
  type BillingUsageSummaryResponse,
  type CancelPremiumSubscriptionRequest,
  type EngagementMode,
  type MeResponse,
  type SubscriptionCancellationReason,
} from "@/lib/api";
import { getAuthUser } from "@/lib/auth";
import { getBillingCyclePriceLabel } from "@/lib/billing-pricing";
import { redirectToLoginWithCurrentDestination } from "@/lib/route-guards";
import {
  PLAN_BILLING_SECTION_ID,
  getUsageProgressPercent,
} from "@/lib/plans";

function SettingsLoading() {
  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="h-6 w-48 animate-pulse rounded bg-foreground/10" />
      <div className="h-4 w-64 animate-pulse rounded bg-foreground/10" />
      <div className="h-10 w-full animate-pulse rounded bg-foreground/10" />
    </Card>
  );
}

function UsageMetric({
  label,
  used,
  limit,
}: {
  label: string;
  used: number;
  limit: number;
}) {
  const progressPercent = getUsageProgressPercent(used, limit);
  return (
    <div className="space-y-2">
      <p className="text-sm text-foreground/80">
        {label}: {used} / {limit}
      </p>
      <div className="h-2 overflow-hidden rounded-full bg-muted">
        <div
          className="h-full rounded-full bg-blue-600 transition-all dark:bg-blue-400"
          style={{ width: `${progressPercent}%` }}
        />
      </div>
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
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [profile, setProfile] = useState<MeResponse | null>(null);
  const [usageSummary, setUsageSummary] = useState<BillingUsageSummaryResponse | null>(null);
  const [billingHistory, setBillingHistory] = useState<BillingHistoryResponse | null>(null);
  const [billingPricing, setBillingPricing] = useState<BillingPricingResponse | null>(null);
  const [signingOut, setSigningOut] = useState(false);
  const [startingCheckout, setStartingCheckout] = useState(false);
  const [selectedBillingCycle, setSelectedBillingCycle] = useState<BillingCycle>("MONTHLY");
  const [planMessage, setPlanMessage] = useState<string | null>(null);
  const [checkoutReturnState, setCheckoutReturnState] = useState<"none" | "success" | "processing" | "cancel" | "failed">("none");
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
        getBillingUsageSummary(),
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
    const params = new URLSearchParams(window.location.search);
    const checkoutStatus = params.get("checkout");
    if (checkoutStatus === "success") {
      setCheckoutReturnState("success");
      setPlanMessage("Payment submitted. Premium will be activated once PayMongo confirms your subscription.");
      return;
    }
    if (checkoutStatus === "processing") {
      setCheckoutReturnState("processing");
      setPlanMessage("Subscription is processing. We will refresh your plan once payment is confirmed.");
      return;
    }
    if (checkoutStatus === "cancel") {
      setCheckoutReturnState("cancel");
      setPlanMessage("Checkout canceled. Your plan remains unchanged.");
      return;
    }
    if (checkoutStatus === "failed") {
      setCheckoutReturnState("failed");
      setPlanMessage("Payment was not completed. Your plan remains unchanged.");
      return;
    }
    setCheckoutReturnState("none");
    setPlanMessage(null);
  }, []);

  useEffect(() => {
    if ((checkoutReturnState !== "success" && checkoutReturnState !== "processing") || profile?.planType === "PREMIUM") {
      return;
    }

    let cancelled = false;
    let attempts = 0;
    const maxAttempts = 8;

    const pollProfileUntilPremium = async () => {
      while (!cancelled && attempts < maxAttempts) {
        attempts += 1;
        try {
          const [me, usage] = await Promise.all([getMe(), getBillingUsageSummary()]);
          if (cancelled) {
            return;
          }
          setProfile(me);
          setUsageSummary(usage);
          if (me.planType === "PREMIUM") {
            setPlanMessage("Premium is now active.");
            return;
          }
        } catch {
          // Keep existing UI state and continue polling.
        }
        await new Promise((resolve) => setTimeout(resolve, 2500));
      }
    };

    void pollProfileUntilPremium();
    return () => {
      cancelled = true;
    };
  }, [checkoutReturnState, profile?.planType]);

  const isEmailVerified = Boolean(profile?.emailVerifiedAt);

  const handleUpgradeClick = async () => {
    if (!isEmailVerified) {
      setPlanMessage("Verify your email before purchasing Premium.");
      return;
    }
    setStartingCheckout(true);
    setPlanMessage(null);
    try {
      const payload = await createPremiumCheckoutSession(selectedBillingCycle);
      window.location.assign(payload.checkoutUrl);
    } catch (err) {
      const message = isEmailNotVerifiedError(err)
        ? "Verify your email before purchasing Premium."
        : err instanceof Error
          ? err.message
          : "Could not start checkout. Please try again.";
      setPlanMessage(message);
      setStartingCheckout(false);
    }
  };

  const handleSignOut = async () => {
    setSigningOut(true);
    try {
      await logout();
      router.push("/auth");
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
    setPlanMessage(null);
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
      setPlanMessage("Cancellation scheduled. Premium remains active until the end of your current billing period.");
      setIsCancellationModalOpen(false);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not schedule Premium cancellation.";
      setPlanMessage(message);
    } finally {
      setCancellingSubscription(false);
    }
  };

  const isPremiumPlan = profile?.planType === "PREMIUM";
  const isCancellationScheduled = Boolean(
    billingHistory?.cancelAtPeriodEnd ?? (profile?.subscription.cancelAtPeriodEnd && profile.subscription.premiumEndsAt),
  );
  const studyPacksUsed = usageSummary?.studyPacksUsed ?? 0;
  const studyPacksLimit = usageSummary?.studyPacksLimit ?? (isPremiumPlan ? 100 : 5);
  const challengeQuizUsed = usageSummary?.challengeQuizUsed ?? 0;
  const challengeQuizLimit = usageSummary?.challengeQuizLimit ?? 50;
  const adaptivePracticeUsed = usageSummary?.adaptivePracticeUsed ?? 0;
  const adaptivePracticeLimit = usageSummary?.adaptivePracticeLimit ?? 50;
  const hasReachedMonthlyLimit = studyPacksUsed >= studyPacksLimit && studyPacksLimit > 0;
  const monthlyPriceLabel = getBillingCyclePriceLabel(billingPricing, "MONTHLY");
  const yearlyPriceLabel = getBillingCyclePriceLabel(billingPricing, "YEARLY");
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

  const subscriptionSummaryPlan = billingHistory?.currentPlan ?? profile?.planType ?? "FREE";
  const subscriptionSummaryStatus = (() => {
    if (subscriptionSummaryPlan !== "PREMIUM") {
      return "Free plan";
    }
    if (billingHistory?.cancelAtPeriodEnd) {
      return "Cancels at period end";
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
    return "Premium";
  })();
  const subscriptionSummaryDate = billingHistory?.cancelAtPeriodEnd
    ? billingHistory.cancellationEffectiveAt ?? billingHistory.currentPeriodEnd
    : billingHistory?.currentPeriodEnd;
  const subscriptionSummaryDateLabel =
    subscriptionSummaryPlan === "PREMIUM" ? (billingHistory?.cancelAtPeriodEnd ? "Ends on" : "Renews on") : "Status";
  const subscriptionBillingCycleLabel = billingHistory?.billingType
    ? billingHistory.billingType === "YEARLY"
      ? "Yearly"
      : "Monthly"
    : "—";

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-4 py-6 sm:px-6 sm:py-10">
      {loading ? (
        <SettingsLoading />
      ) : error ? (
        <Card className="space-y-4 p-4 sm:p-6">
          <h1 className="text-2xl font-semibold">Could not load settings</h1>
          <p className="text-sm text-foreground/75">{error}</p>
          <Button type="button" className="w-full sm:w-auto" onClick={() => void loadProfile()}>
            Retry
          </Button>
        </Card>
      ) : profile ? (
        <div className="space-y-6">
          <PageHeader
            eyebrow="SETTINGS"
            title="Configuration"
            description="Manage account settings, plan and billing details, and learning preferences."
          />

          <Card className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Preferences</h2>
            <p className="text-sm text-foreground/70">
              Keep NoteLib aligned with the way you study. Learning Style guides future reminder cadence, and you can
              turn reminder types on or off anytime.
            </p>
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
                <Button
                  type="button"
                  className="w-full sm:w-auto"
                  onClick={() => void handleSaveEngagementMode()}
                  disabled={savingEngagementMode}
                >
                  {savingEngagementMode ? "Saving..." : "Save Learning Style"}
                </Button>
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
                <Button
                  type="button"
                  className="w-full sm:w-auto"
                  onClick={() => void handleSaveStudyReminders()}
                  disabled={savingStudyReminders}
                >
                  {savingStudyReminders ? "Saving..." : "Save Study Reminders"}
                </Button>
                {studyRemindersMessage ? (
                  <p className="text-xs text-foreground/60">{studyRemindersMessage}</p>
                ) : null}
              </div>
            </div>
          </Card>

          <Card id={PLAN_BILLING_SECTION_ID} className="space-y-4 p-4 sm:p-6">
            <h2 className="text-lg font-semibold sm:text-xl">Plan &amp; Billing</h2>
            <div className="space-y-4 rounded-md border border-border bg-background p-4">
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Current Plan</p>
                <p className="mt-2 inline-flex rounded-full border border-border px-3 py-1 text-sm font-semibold">
                  {profile.planType}
                </p>
              </div>
              <div className="space-y-2">
                <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Monthly Usage</p>
                <UsageMetric label="Study Packs" used={studyPacksUsed} limit={studyPacksLimit} />
                {isPremiumPlan ? (
                  <>
                    <UsageMetric label="Challenge Quiz" used={challengeQuizUsed} limit={challengeQuizLimit} />
                    <UsageMetric label="Adaptive Practice" used={adaptivePracticeUsed} limit={adaptivePracticeLimit} />
                  </>
                ) : null}
                {hasReachedMonthlyLimit ? (
                  <p className="text-sm text-foreground/80">You have reached your monthly Study Pack limit.</p>
                ) : null}
              </div>
              {isPremiumPlan ? (
                <div className="space-y-2">
                  <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Features</p>
                  <ul className="list-disc space-y-1 pl-5 text-sm text-foreground/80">
                    <li>Weak Concept Detection</li>
                    <li>Higher limits</li>
                    <li>Premium features enabled</li>
                  </ul>
                </div>
              ) : (
                <div className="space-y-4 rounded-md border border-border bg-background p-4">
                  <div className="space-y-2">
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Free Features</p>
                    <ul className="list-disc space-y-1 pl-5 text-sm text-foreground/80">
                      <li>Save unlimited notes</li>
                      <li>Quick Review</li>
                      <li>Public Library access</li>
                    </ul>
                  </div>
                  <div className="space-y-2">
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Premium Features</p>
                    <ul className="list-disc space-y-1 pl-5 text-sm text-foreground/80">
                      <li>Challenge Quiz</li>
                      <li>Adaptive Practice</li>
                      <li>Weak Concept Detection</li>
                      <li>Higher Study Pack limits</li>
                    </ul>
                  </div>
                  <div className="space-y-2">
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Billing Cycle</p>
                    {billingPricing ? (
                      <p className="text-xs text-foreground/60">
                        Regional pricing: {billingPricing.region} · {billingPricing.currency}
                      </p>
                    ) : null}
                    <div className="grid gap-2 sm:grid-cols-2">
                      <button
                        type="button"
                        onClick={() => setSelectedBillingCycle("MONTHLY")}
                        className={`rounded-md border p-3 text-left text-sm transition ${
                          selectedBillingCycle === "MONTHLY"
                            ? "border-blue-500 bg-blue-500/5"
                            : "border-border bg-background"
                        }`}
                        disabled={startingCheckout}
                      >
                        <p className="font-medium">Monthly</p>
                        <p className="text-xs text-foreground/60">{monthlyPriceLabel}</p>
                      </button>
                      <button
                        type="button"
                        onClick={() => setSelectedBillingCycle("YEARLY")}
                        className={`rounded-md border p-3 text-left text-sm transition ${
                          selectedBillingCycle === "YEARLY"
                            ? "border-blue-500 bg-blue-500/5"
                            : "border-border bg-background"
                        }`}
                        disabled={startingCheckout}
                      >
                        <p className="font-medium">Yearly</p>
                        <p className="text-xs text-foreground/60">{yearlyPriceLabel}</p>
                      </button>
                    </div>
                  </div>
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
                    <Button
                      type="button"
                      className="w-full sm:w-auto"
                      onClick={() => void handleUpgradeClick()}
                      disabled={startingCheckout}
                    >
                      {startingCheckout ? "Redirecting..." : "Upgrade to Premium"}
                    </Button>
                    {planMessage ? (
                      <p className="text-xs text-foreground/60">{planMessage}</p>
                    ) : null}
                  </div>
                </div>
              )}
              {isPremiumPlan && !isCancellationScheduled ? (
                <div className="rounded-md border border-border bg-background p-4">
                  <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div className="space-y-1">
                      <p className="text-sm font-medium">Cancel Subscription</p>
                      <p className="text-xs text-foreground/70">
                        Premium stays active until the end of your current billing period.
                      </p>
                    </div>
                    <Button
                      type="button"
                      variant="outline"
                      className="w-full sm:w-auto"
                      onClick={handleOpenCancellationModal}
                    >
                      Cancel Subscription
                    </Button>
                  </div>
                </div>
              ) : null}
              {isPremiumPlan && planMessage ? (
                <p className="text-xs text-foreground/60">{planMessage}</p>
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
                      {subscriptionSummaryPlan === "PREMIUM" ? "Premium" : "Free"}
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
                      {subscriptionSummaryPlan === "PREMIUM"
                        ? formatBillingDate(subscriptionSummaryDate ?? null)
                        : "No active subscription"}
                    </p>
                  </div>
                  <div className="space-y-1">
                    <p className="text-xs font-semibold uppercase tracking-wide text-foreground/60">Billing cycle</p>
                    <p className="text-sm font-medium text-foreground">{subscriptionBillingCycleLabel}</p>
                  </div>
                </div>

                {billingHistory?.cancelAtPeriodEnd ? (
                  <div className="rounded-md border border-amber-500/30 bg-amber-500/10 p-4">
                    <p className="text-sm font-medium text-foreground">
                      Your Premium plan will end on {formatBillingDate(subscriptionSummaryDate ?? null)} and will not renew.
                    </p>
                    <p className="mt-1 text-xs text-foreground/70">
                      Your notes and Study Packs will remain in your library.
                    </p>
                  </div>
                ) : subscriptionSummaryPlan === "PREMIUM" ? (
                  <p className="text-sm text-foreground/75">
                    Your Premium plan renews on {formatBillingDate(subscriptionSummaryDate ?? null)}.
                  </p>
                ) : (
                  <p className="text-sm text-foreground/75">
                    Your payment history will appear here once you subscribe to Premium.
                  </p>
                )}
              </div>

              <div className="space-y-3">
                <h3 className="text-base font-semibold text-foreground">Payment History</h3>
                {billingTransactions.length === 0 ? (
                  <div className="rounded-md border border-dashed border-border p-4 text-sm">
                    <p className="font-medium text-foreground">No billing history yet</p>
                    <p className="mt-1 text-foreground/70">
                      Your payment history will appear here once you subscribe to Premium.
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
              <Button type="button" className="w-full sm:w-auto" onClick={() => void handleSignOut()} disabled={signingOut}>
                {signingOut ? "Signing out..." : "Sign Out"}
              </Button>
              <Button type="button" variant="outline" className="w-full sm:w-auto" disabled>
                Delete Account (Coming Soon)
              </Button>
            </div>
          </Card>
        </div>
      ) : null}
      <AppModal
        isOpen={isCancellationModalOpen}
        title="Cancel Premium?"
        description="Your Premium access will remain active until the end of your current billing period. After that, your account will return to the Free plan. Your notes and Study Packs will remain in your library."
        onClose={handleCloseCancellationModal}
        panelClassName="max-w-[560px]"
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              className="w-full sm:w-auto"
              onClick={handleCloseCancellationModal}
              disabled={cancellingSubscription}
            >
              Keep Premium
            </Button>
            <Button
              type="button"
              className="w-full sm:w-auto"
              onClick={() => void handleConfirmCancellation()}
              disabled={cancellingSubscription}
            >
              {cancellingSubscription ? "Confirming..." : "Confirm Cancellation"}
            </Button>
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
    </main>
  );
}
