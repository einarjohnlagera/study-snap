"use client";

import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { AppModal } from "@/components/ui/app-modal";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { ToastMessage } from "@/components/ui/toast-message";
import Link from "next/link";
import {
  ApiRequestError,
  getAdminDashboardRecentEvents,
  getAdminDashboardSummary,
  getAdminDashboardTopContent,
  getAdminFeedbackImage,
  getAdminRegenerationStatus,
  issueAdminRefund,
  regenerateAdminSummaries,
  type AdminRecentUpgradeItemResponse,
  type AdminRecentFeedbackItemResponse,
  type AdminDashboardRecentEventsResponse,
  type AdminDashboardSummaryResponse,
  type AdminDashboardTopContentResponse,
  type AdminRegenerationStatusResponse,
} from "@/lib/api";
import { requireAdminUser } from "@/lib/route-guards";

function formatMetric(value: number): string {
  return new Intl.NumberFormat("en-US").format(value);
}

function formatAmount(value: number): string {
  return new Intl.NumberFormat("en-US", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(value);
}

function formatDate(value: string): string {
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
  }).format(new Date(value));
}

function formatDateTime(value: string): string {
  return new Intl.DateTimeFormat("en-US", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

function truncateCell(value: string, maxLength = 80): string {
  if (value.length <= maxLength) {
    return value;
  }
  return `${value.slice(0, maxLength - 1).trimEnd()}…`;
}

function formatFeedbackStatus(value: "NEW" | "REVIEWED" | "CLOSED"): string {
  switch (value) {
    case "REVIEWED":
      return "Reviewed";
    case "CLOSED":
      return "Closed";
    default:
      return "New";
  }
}

type MetricCardProps = {
  label: string;
  value: string;
  detail?: string;
};

function MetricCard({ label, value, detail }: Readonly<MetricCardProps>) {
  return (
    <Card className="space-y-2 p-4 sm:p-5">
      <p className="text-xs font-semibold uppercase tracking-wide text-foreground/55">{label}</p>
      <p className="text-2xl font-semibold text-foreground">{value}</p>
      {detail ? <p className="text-sm text-foreground/65">{detail}</p> : null}
    </Card>
  );
}

type SimpleTableProps = {
  title: string;
  columns: string[];
  emptyMessage: string;
  rows: ReactNode[][];
};

function SimpleTable({ title, columns, emptyMessage, rows }: Readonly<SimpleTableProps>) {
  return (
    <Card className="overflow-hidden">
      <div className="border-b border-border/70 px-4 py-3 sm:px-5">
        <h2 className="text-base font-semibold text-foreground">{title}</h2>
      </div>
      {rows.length === 0 ? (
        <div className="px-4 py-6 text-sm text-foreground/65 sm:px-5">{emptyMessage}</div>
      ) : (
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead className="bg-muted/40 text-left text-foreground/60">
              <tr>
                {columns.map((column) => (
                  <th key={column} className="px-4 py-3 font-medium sm:px-5">
                    {column}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((row, index) => (
                <tr key={`${title}-${index}`} className="border-t border-border/60">
                  {row.map((cell, cellIndex) => (
                    <td key={`${title}-${index}-${cellIndex}`} className="px-4 py-3 text-foreground/80 sm:px-5">
                      {cell}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}

export default function AdminPage() {
  const router = useRouter();
  const [summary, setSummary] = useState<AdminDashboardSummaryResponse | null>(null);
  const [topContent, setTopContent] = useState<AdminDashboardTopContentResponse | null>(null);
  const [recentEvents, setRecentEvents] = useState<AdminDashboardRecentEventsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refundTarget, setRefundTarget] = useState<AdminRecentUpgradeItemResponse | null>(null);
  const [feedbackDetailTarget, setFeedbackDetailTarget] = useState<AdminRecentFeedbackItemResponse | null>(null);
  const [feedbackImageUrl, setFeedbackImageUrl] = useState<string | null>(null);
  const feedbackImageUrlRef = useRef<string | null>(null);
  const feedbackImageRequestRef = useRef(0);
  const [refundError, setRefundError] = useState<string | null>(null);
  const [refundSuccessMessage, setRefundSuccessMessage] = useState<string | null>(null);
  const [issuingRefund, setIssuingRefund] = useState(false);
  const [regeneratingSummaries, setRegeneratingSummaries] = useState(false);
  const [regenerateSummariesError, setRegenerateSummariesError] = useState<string | null>(null);
  const [regenerateSummariesMessage, setRegenerateSummariesMessage] = useState<string | null>(null);
  const [regenerationProgress, setRegenerationProgress] = useState<AdminRegenerationStatusResponse | null>(null);

  const loadDashboard = useCallback(async () => {
    if (!requireAdminUser(router)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const [summaryResponse, topContentResponse, recentEventsResponse] = await Promise.all([
        getAdminDashboardSummary(),
        getAdminDashboardTopContent(),
        getAdminDashboardRecentEvents(),
      ]);
      setSummary(summaryResponse);
      setTopContent(topContentResponse);
      setRecentEvents(recentEventsResponse);
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 403) {
        router.replace("/dashboard");
        return;
      }
      setError(err instanceof Error ? err.message : "Could not load admin dashboard.");
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    void loadDashboard();
  }, [loadDashboard]);

  useEffect(() => {
    if (!refundSuccessMessage) {
      return;
    }
    const timeoutId = globalThis.setTimeout(() => setRefundSuccessMessage(null), 4000);
    return () => {
      globalThis.clearTimeout(timeoutId);
    };
  }, [refundSuccessMessage]);

  useEffect(() => {
    if (!regenerateSummariesMessage) {
      return;
    }
    const timeoutId = globalThis.setTimeout(() => setRegenerateSummariesMessage(null), 4000);
    return () => {
      globalThis.clearTimeout(timeoutId);
    };
  }, [regenerateSummariesMessage]);

  const handleOpenRefundModal = (item: AdminRecentUpgradeItemResponse) => {
    setRefundTarget(item);
    setRefundError(null);
  };

  const handleCloseRefundModal = () => {
    if (issuingRefund) {
      return;
    }
    setRefundTarget(null);
    setRefundError(null);
  };

  const handleOpenFeedbackDetail = (item: AdminRecentFeedbackItemResponse) => {
    const requestId = ++feedbackImageRequestRef.current;
    replaceFeedbackImageUrl(null);
    setFeedbackDetailTarget(item);
    if (!item.hasImage) {
      return;
    }
    void getAdminFeedbackImage(item.feedbackId)
      .then((image) => {
        if (requestId !== feedbackImageRequestRef.current || !image) {
          return;
        }
        replaceFeedbackImageUrl(URL.createObjectURL(image));
      })
      .catch(() => {
        if (requestId === feedbackImageRequestRef.current) {
          replaceFeedbackImageUrl(null);
        }
      });
  };

  const handleCloseFeedbackDetail = () => {
    feedbackImageRequestRef.current += 1;
    replaceFeedbackImageUrl(null);
    setFeedbackDetailTarget(null);
  };

  const replaceFeedbackImageUrl = (nextUrl: string | null) => {
    if (feedbackImageUrlRef.current) {
      URL.revokeObjectURL(feedbackImageUrlRef.current);
    }
    feedbackImageUrlRef.current = nextUrl;
    setFeedbackImageUrl(nextUrl);
  };

  useEffect(() => () => {
    feedbackImageRequestRef.current += 1;
    if (feedbackImageUrlRef.current) {
      URL.revokeObjectURL(feedbackImageUrlRef.current);
    }
  }, []);

  const handleConfirmRefund = async () => {
    if (!refundTarget?.transactionId) {
      return;
    }
    setIssuingRefund(true);
    setRefundError(null);
    try {
      const response = await issueAdminRefund(refundTarget.transactionId);
      setRefundSuccessMessage(`Refund issued for ${response.userEmail}`);
      setRecentEvents((current) => {
        if (!current) {
          return current;
        }
        return {
          ...current,
          recentPremiumUpgrades: current.recentPremiumUpgrades.map((item) => (
            item.transactionId === response.transactionId
              ? { ...item, transactionId: null }
              : item
          )),
        };
      });
      setRefundTarget(null);
    } catch (err) {
      setRefundError(err instanceof Error ? err.message : "Could not issue refund.");
    } finally {
      setIssuingRefund(false);
    }
  };

  const handleRegenerateSummaries = async () => {
    setRegeneratingSummaries(true);
    setRegenerateSummariesError(null);
    setRegenerationProgress(null);
    try {
      const response = await regenerateAdminSummaries();
      if (response.queued === 0) {
        setRegenerateSummariesMessage(`No summaries to regenerate (${response.skipped} already enriched)`);
        setRegeneratingSummaries(false);
        return;
      }
      const pollInterval = globalThis.setInterval(async () => {
        try {
          const status = await getAdminRegenerationStatus();
          setRegenerationProgress(status);
          if (status.done) {
            globalThis.clearInterval(pollInterval);
            setRegeneratingSummaries(false);
            setRegenerateSummariesMessage(
              `Done: ${status.processed} regenerated, ${status.failed} failed (${response.skipped} already enriched)`,
            );
            setRegenerationProgress(null);
          }
        } catch {
          globalThis.clearInterval(pollInterval);
          setRegeneratingSummaries(false);
        }
      }, 2500);
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 403) {
        router.replace("/dashboard");
        return;
      }
      setRegenerateSummariesError("Could not queue regeneration. Try again.");
      setRegeneratingSummaries(false);
    }
  };

  const overviewCards = useMemo(() => {
    if (!summary) {
      return [] as MetricCardProps[];
    }
    return [
      { label: "Total Users", value: formatMetric(summary.overview.totalUsers) },
      { label: "Verified Users", value: formatMetric(summary.overview.verifiedUsers) },
      { label: "Paid Users", value: formatMetric(summary.overview.premiumUsers) },
      { label: "Legacy Upgrade Waitlist", value: formatMetric(summary.overview.premiumWaitlistCount) },
      { label: "Total Notes", value: formatMetric(summary.overview.totalNotes) },
      { label: "Study Packs", value: formatMetric(summary.overview.totalStudyPacksGenerated) },
      { label: "Public Notes", value: formatMetric(summary.overview.totalPublicNotes) },
      { label: "Public Views", value: formatMetric(summary.overview.totalPublicNoteViews) },
      { label: "Public Copies", value: formatMetric(summary.overview.totalPublicNoteCopies) },
      { label: "Upgrades", value: formatMetric(summary.overview.totalUpgrades) },
    ] satisfies MetricCardProps[];
  }, [summary]);

  const billingCards = useMemo(() => {
    if (!summary) {
      return [] as MetricCardProps[];
    }
    return [
      { label: "Active Paid", value: formatMetric(summary.billing.activePremiumSubscriptions) },
      { label: "Monthly Subs", value: formatMetric(summary.billing.monthlySubscriptions) },
      { label: "Yearly Subs", value: formatMetric(summary.billing.yearlySubscriptions) },
      { label: "Cancel at Period End", value: formatMetric(summary.billing.cancelAtPeriodEndSubscriptions) },
      { label: "Failed Payments", value: formatMetric(summary.billing.failedPayments) },
      {
        label: "Estimated MRR",
        value: formatAmount(summary.billing.estimatedMrr),
        detail: "Based on latest successful recurring payments.",
      },
      {
        label: "Estimated ARR",
        value: formatAmount(summary.billing.estimatedArr),
        detail: "Mixed currencies may affect this estimate.",
      },
    ] satisfies MetricCardProps[];
  }, [summary]);

  const engagementCards = useMemo(() => {
    if (!summary) {
      return [] as MetricCardProps[];
    }
    return [
      { label: "Study Packs This Week", value: formatMetric(summary.engagement.studyPacksGeneratedThisWeek) },
      { label: "Quick Reviews", value: formatMetric(summary.engagement.quickReviewsStarted) },
      { label: "Challenge Quizzes", value: formatMetric(summary.engagement.challengeQuizzesStarted) },
      { label: "Adaptive Practice", value: formatMetric(summary.engagement.adaptivePracticeStarted) },
      { label: "Long Exams", value: formatMetric(summary.engagement.longExamsStarted) },
      { label: "Interview Practice", value: formatMetric(summary.engagement.interviewPracticeStarted) },
      { label: "Paywall Views", value: formatMetric(summary.engagement.paywallViews) },
      { label: "Upgrade Clicks", value: formatMetric(summary.engagement.upgradeClicks) },
      { label: "Signups", value: formatMetric(summary.engagement.signups) },
      { label: "Verified Accounts", value: formatMetric(summary.engagement.verifiedAccounts) },
    ] satisfies MetricCardProps[];
  }, [summary]);

  const refundAmountLabel = refundTarget?.currency && refundTarget.amount !== null
    ? `${refundTarget.currency} ${formatAmount(refundTarget.amount)}`
    : "the selected payment";

  return (
    <div className="mx-auto w-full max-w-7xl space-y-8 px-4 py-6 sm:px-6 sm:py-10">
      {refundSuccessMessage ? <ToastMessage message={refundSuccessMessage} tone="success" /> : null}
      {regenerateSummariesMessage ? <ToastMessage message={regenerateSummariesMessage} tone="success" /> : null}
      <header className="space-y-2">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex flex-wrap items-center gap-4">
            <h1 className="text-3xl font-semibold text-foreground">Admin Dashboard</h1>
            <Link href="/admin/campaigns" className="text-sm text-foreground/55 hover:text-foreground/80">
              Campaigns →
            </Link>
            <Link href="/admin/funnel" className="text-sm text-foreground/55 hover:text-foreground/80">
              Funnel →
            </Link>
          </div>
          <div className="flex flex-col items-start gap-2 sm:items-end">
            <Button
              type="button"
              size="sm"
              variant="outline"
              onClick={() => void handleRegenerateSummaries()}
              disabled={regeneratingSummaries}
            >
              {regeneratingSummaries ? "Processing..." : "Regenerate Summaries"}
            </Button>
            {regenerationProgress ? (
              <p className="text-sm text-foreground/65">
                Processing {regenerationProgress.processed + regenerationProgress.failed}/{regenerationProgress.total}...
              </p>
            ) : null}
            {regenerateSummariesError ? (
              <p className="text-sm text-red-600 dark:text-red-400">{regenerateSummariesError}</p>
            ) : null}
          </div>
        </div>
        <p className="max-w-3xl text-sm leading-relaxed text-foreground/70">
          Internal view of product usage, billing health, upgrade activity, and Public Library growth.
        </p>
      </header>

      {loading ? (
        <Card className="p-6 text-sm text-foreground/70">Loading admin metrics...</Card>
      ) : error ? (
        <Card className="border-red-500/40 bg-red-50/70 p-6 text-sm text-red-700 dark:bg-red-950/20 dark:text-red-300">
          {error}
        </Card>
      ) : summary && topContent && recentEvents ? (
        <>
          <section className="space-y-3">
            <h2 className="text-lg font-semibold text-foreground">Overview</h2>
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {overviewCards.map((card) => (
                <MetricCard key={card.label} label={card.label} value={card.value} detail={card.detail} />
              ))}
            </div>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold text-foreground">Billing Summary</h2>
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {billingCards.map((card) => (
                <MetricCard key={card.label} label={card.label} value={card.value} detail={card.detail} />
              ))}
            </div>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold text-foreground">Engagement</h2>
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {engagementCards.map((card) => (
                <MetricCard key={card.label} label={card.label} value={card.value} detail={card.detail} />
              ))}
            </div>
          </section>

          <section className="grid gap-6 xl:grid-cols-3">
            <SimpleTable
              title="Most Viewed Public Notes"
              columns={["Title", "Subject", "Views"]}
              emptyMessage="No public note views yet."
              rows={topContent.mostViewedPublicNotes.map((item) => [
                item.title ?? "Untitled note",
                item.subject ?? "Uncategorized",
                formatMetric(item.totalCount),
              ])}
            />
            <SimpleTable
              title="Most Copied Public Notes"
              columns={["Title", "Subject", "Copies"]}
              emptyMessage="No public note copies yet."
              rows={topContent.mostCopiedPublicNotes.map((item) => [
                item.title ?? "Untitled note",
                item.subject ?? "Uncategorized",
                formatMetric(item.totalCount),
              ])}
            />
            <SimpleTable
              title="Top Subjects by Study Pack Generation"
              columns={["Subject", "Study Packs"]}
              emptyMessage="No Study Packs generated yet."
              rows={topContent.topSubjectsByStudyPackGeneration.map((item) => [
                item.subject ?? "Uncategorized",
                formatMetric(item.studyPackCount),
              ])}
            />
          </section>

          <section className="grid gap-6 xl:grid-cols-2">
            <SimpleTable
              title="Recent Paid Upgrades"
              columns={["User", "Cycle", "Amount", "Provider", "Started", "Action"]}
              emptyMessage="No paid upgrades recorded yet."
              rows={recentEvents.recentPremiumUpgrades.map((item) => [
                item.userEmail,
                item.billingCycle === "YEARLY" ? "Yearly" : "Monthly",
                item.currency && item.amount !== null ? `${item.currency} ${formatAmount(item.amount)}` : "Not linked",
                item.provider,
                formatDate(item.startedAt),
                item.provider === "XENDIT" && item.transactionId ? (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    onClick={() => handleOpenRefundModal(item)}
                  >
                    Refund
                  </Button>
                ) : (
                  <span className="text-foreground/50">—</span>
                ),
              ])}
            />
            <SimpleTable
              title="Recent Failed Payments"
              columns={["User", "Amount", "Provider", "Date"]}
              emptyMessage="No failed payments recorded."
              rows={recentEvents.recentFailedPayments.map((item) => [
                item.userEmail,
                `${item.currency} ${formatAmount(item.amount)}`,
                item.provider,
                formatDate(item.createdAt),
              ])}
            />
          </section>

          <section>
            <SimpleTable
              title="Recent Feedback"
              columns={["Date", "User", "Message", "Page URL", "Status", "Action"]}
              emptyMessage="No feedback submitted yet."
              rows={recentEvents.recentFeedback.map((item) => [
                formatDate(item.createdAt),
                item.userEmail,
                truncateCell(item.message, 96),
                item.pageUrl ? truncateCell(item.pageUrl, 60) : "Not provided",
                formatFeedbackStatus(item.status),
                <Button
                  key={`feedback-view-${item.feedbackId}`}
                  type="button"
                  size="sm"
                  variant="outline"
                  onClick={() => handleOpenFeedbackDetail(item)}
                >
                  View
                </Button>,
              ])}
            />
          </section>
        </>
      ) : null}
      <AppModal
        isOpen={Boolean(refundTarget)}
        title="Issue Refund"
        description={refundTarget ? `This will submit a refund of ${refundAmountLabel} to Xendit for ${refundTarget.userEmail}. The user will receive a confirmation email. This cannot be undone.` : undefined}
        onClose={handleCloseRefundModal}
        panelClassName="max-w-[520px]"
        actions={(
          <div className="flex flex-col gap-2 sm:flex-row sm:justify-end">
            <Button
              type="button"
              variant="outline"
              className="w-full sm:w-auto"
              onClick={handleCloseRefundModal}
              disabled={issuingRefund}
            >
              Cancel
            </Button>
            <Button
              type="button"
              className="w-full bg-red-600 text-white hover:bg-red-700 active:bg-red-800 sm:w-auto"
              onClick={() => void handleConfirmRefund()}
              loading={issuingRefund}
              loadingText="Issuing..."
            >
              Confirm Refund
            </Button>
          </div>
        )}
      >
        {refundError ? (
          <p className="rounded-md border border-red-500/30 bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/20 dark:text-red-300">
            {refundError}
          </p>
        ) : null}
      </AppModal>
      <AppModal
        isOpen={Boolean(feedbackDetailTarget)}
        title="Feedback Details"
        onClose={handleCloseFeedbackDetail}
        panelClassName="max-w-[520px]"
        actions={(
          <div className="flex justify-end">
            <Button type="button" variant="outline" onClick={handleCloseFeedbackDetail}>
              Close
            </Button>
          </div>
        )}
      >
        {feedbackDetailTarget ? (
          <div className="space-y-5">
            <section className="space-y-1.5">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Message</h3>
              <p className="whitespace-pre-line break-words text-sm leading-relaxed text-foreground">
                {feedbackDetailTarget.message}
              </p>
            </section>
            <section className="space-y-1.5">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Page URL</h3>
              {feedbackDetailTarget.pageUrl ? (
                <a
                  href={feedbackDetailTarget.pageUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="block break-all text-sm text-primary underline-offset-4 hover:underline"
                >
                  {feedbackDetailTarget.pageUrl}
                </a>
              ) : (
                <p className="text-sm text-foreground/65">Not provided</p>
              )}
            </section>
            <div className="grid gap-4 sm:grid-cols-2">
              <section className="space-y-1.5">
                <h3 className="text-xs font-semibold uppercase tracking-wide text-foreground/55">User email</h3>
                <p className="break-all text-sm text-foreground">{feedbackDetailTarget.userEmail}</p>
              </section>
              <section className="space-y-1.5">
                <h3 className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Submitted</h3>
                <p className="text-sm text-foreground">{formatDateTime(feedbackDetailTarget.createdAt)}</p>
              </section>
            </div>
            <section className="space-y-1.5">
              <h3 className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Status</h3>
              <span className="inline-flex rounded-full border border-border bg-muted/50 px-2.5 py-1 text-xs font-medium text-foreground/75">
                {formatFeedbackStatus(feedbackDetailTarget.status)}
              </span>
            </section>
            {feedbackImageUrl ? (
              <section className="space-y-1.5">
                <h3 className="text-xs font-semibold uppercase tracking-wide text-foreground/55">Screenshot</h3>
                {/* eslint-disable-next-line @next/next/no-img-element -- authenticated blob URL */}
                <img
                  src={feedbackImageUrl}
                  alt="Feedback screenshot"
                  className="max-h-[420px] w-full rounded-xl border border-border bg-muted/20 object-contain"
                />
              </section>
            ) : null}
          </div>
        ) : null}
      </AppModal>
    </div>
  );
}
