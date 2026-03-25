"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Card } from "@/components/ui/card";
import {
  ApiRequestError,
  getAdminDashboardRecentEvents,
  getAdminDashboardSummary,
  getAdminDashboardTopContent,
  type AdminDashboardRecentEventsResponse,
  type AdminDashboardSummaryResponse,
  type AdminDashboardTopContentResponse,
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

function MetricCard({ label, value, detail }: MetricCardProps) {
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
  rows: string[][];
};

function SimpleTable({ title, columns, emptyMessage, rows }: SimpleTableProps) {
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

  const overviewCards = useMemo(() => {
    if (!summary) {
      return [] as MetricCardProps[];
    }
    return [
      { label: "Total Users", value: formatMetric(summary.overview.totalUsers) },
      { label: "Verified Users", value: formatMetric(summary.overview.verifiedUsers) },
      { label: "Premium Users", value: formatMetric(summary.overview.premiumUsers) },
      { label: "Premium Waitlist", value: formatMetric(summary.overview.premiumWaitlistCount) },
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
      { label: "Active Premium", value: formatMetric(summary.billing.activePremiumSubscriptions) },
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
      { label: "Paywall Views", value: formatMetric(summary.engagement.paywallViews) },
      { label: "Upgrade Clicks", value: formatMetric(summary.engagement.upgradeClicks) },
      { label: "Signups", value: formatMetric(summary.engagement.signups) },
      { label: "Verified Accounts", value: formatMetric(summary.engagement.verifiedAccounts) },
    ] satisfies MetricCardProps[];
  }, [summary]);

  return (
    <div className="mx-auto w-full max-w-7xl space-y-8 px-4 py-6 sm:px-6 sm:py-10">
      <header className="space-y-2">
        <h1 className="text-3xl font-semibold text-foreground">Admin Dashboard</h1>
        <p className="max-w-3xl text-sm leading-relaxed text-foreground/70">
          Internal read-only view of product usage, billing health, upgrade activity, and Public Library growth.
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
              title="Recent Premium Upgrades"
              columns={["User", "Cycle", "Provider", "Started"]}
              emptyMessage="No Premium upgrades recorded yet."
              rows={recentEvents.recentPremiumUpgrades.map((item) => [
                item.userEmail,
                item.billingCycle === "YEARLY" ? "Yearly" : "Monthly",
                item.provider,
                formatDate(item.startedAt),
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
              columns={["Date", "User", "Message", "Page URL", "Status"]}
              emptyMessage="No feedback submitted yet."
              rows={recentEvents.recentFeedback.map((item) => [
                formatDate(item.createdAt),
                item.userEmail,
                truncateCell(item.message, 96),
                item.pageUrl ? truncateCell(item.pageUrl, 60) : "Not provided",
                formatFeedbackStatus(item.status),
              ])}
            />
          </section>
        </>
      ) : null}
    </div>
  );
}
