"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import {
  ApiRequestError,
  getAdminFunnelMetrics,
  type AdminFunnelMetricsResponse,
} from "@/lib/api";
import { requireAdminUser } from "@/lib/route-guards";

function formatMetric(value: number): string {
  return new Intl.NumberFormat("en-US").format(value);
}

function formatPercent(value: number): string {
  return `${value.toFixed(1)}%`;
}

function formatDays(value: number | null): string {
  if (value === null) {
    return "—";
  }
  return `${new Intl.NumberFormat("en-US", {
    maximumFractionDigits: 1,
  }).format(value)} days`;
}

function formatWeek(value: string): string {
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    day: "numeric",
    year: "numeric",
    timeZone: "UTC",
  }).format(new Date(`${value}T00:00:00Z`));
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

export default function AdminFunnelPage() {
  const router = useRouter();
  const [metrics, setMetrics] = useState<AdminFunnelMetricsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadMetrics = useCallback(async () => {
    if (!requireAdminUser(router)) {
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const result = await getAdminFunnelMetrics();
      setMetrics(result);
    } catch (err) {
      if (err instanceof ApiRequestError && err.status === 403) {
        router.replace("/dashboard");
        return;
      }
      setError("Could not load funnel metrics.");
    } finally {
      setLoading(false);
    }
  }, [router]);

  useEffect(() => {
    void loadMetrics();
  }, [loadMetrics]);

  const activationCards = useMemo(() => {
    if (!metrics) {
      return [] as MetricCardProps[];
    }
    return [
      {
        label: "Activation Rate",
        value: formatPercent(metrics.activation.activationRatePercent),
        detail: `${formatMetric(metrics.activation.activatedUsers)} of ${formatMetric(metrics.activation.totalVerifiedUsers)} verified users`,
      },
      {
        label: "Median Days to First Study Pack",
        value: formatDays(metrics.activation.medianDaysToFirstPack),
      },
      {
        label: "Stuck Before Generation",
        value: `${formatMetric(metrics.stuckUsers.stuckUsersCount)} users`,
        detail: "Created notes 7+ days ago, never generated",
      },
    ] satisfies MetricCardProps[];
  }, [metrics]);

  const paywallCards = useMemo(() => {
    if (!metrics) {
      return [] as MetricCardProps[];
    }
    return [
      {
        label: "Free Quota Hit Rate",
        value: formatPercent(metrics.quotaHit.ratePercent),
        detail: `${formatMetric(metrics.quotaHit.freeUsersHitQuota)} of ${formatMetric(metrics.quotaHit.totalFreeUsers)} free users hit the monthly limit`,
      },
      {
        label: "Paywall Conversion",
        value: formatPercent(metrics.paywallConversion.ratePercent),
        detail: `${formatMetric(metrics.paywallConversion.usersUpgradedAfterPaywall)} of ${formatMetric(metrics.paywallConversion.usersSeenPaywall)} who saw the paywall upgraded`,
      },
      {
        label: "Value Loop Closure",
        value: formatPercent(metrics.valueLoop.ratePercent),
        detail: `${formatMetric(metrics.valueLoop.usersStartedQuizWithin7Days)} of ${formatMetric(metrics.valueLoop.usersGeneratedPack)} who generated a pack started a quiz within 7 days`,
      },
    ] satisfies MetricCardProps[];
  }, [metrics]);

  const checkoutCards = useMemo(() => {
    if (!metrics) {
      return [] as MetricCardProps[];
    }
    return [
      {
        label: "Upgrade Clicks",
        value: formatMetric(metrics.checkoutConversion.usersClickedUpgrade),
      },
      {
        label: "Checkout Initiated",
        value: formatMetric(metrics.checkoutConversion.usersInitiatedCheckout),
      },
      {
        label: "Paid Conversions",
        value: formatMetric(metrics.checkoutConversion.usersSubscribed),
      },
      {
        label: "Click to Checkout",
        value: formatPercent(metrics.checkoutConversion.clickToCheckoutRatePercent),
      },
      {
        label: "Checkout to Paid",
        value: formatPercent(metrics.checkoutConversion.checkoutToPaidRatePercent),
      },
      {
        label: "Click to Paid",
        value: formatPercent(metrics.checkoutConversion.clickToPaidRatePercent),
      },
    ] satisfies MetricCardProps[];
  }, [metrics]);

  return (
    <div className="mx-auto w-full max-w-7xl space-y-8 px-4 py-6 sm:px-6 sm:py-10">
      <header className="space-y-2">
        <div className="flex items-center gap-3">
          <Link href="/admin" className="text-sm text-foreground/55 hover:text-foreground/80">
            ← Admin
          </Link>
        </div>
        <h1 className="text-3xl font-semibold text-foreground">Conversion Funnel</h1>
        <p className="max-w-3xl text-sm leading-relaxed text-foreground/70">
          Snapshot metrics for the signup → generate → practice → upgrade loop. All-time unless noted.
        </p>
      </header>

      {loading ? (
        <Card className="p-6 text-sm text-foreground/70">Loading funnel metrics...</Card>
      ) : error ? (
        <Card className="space-y-4 border-red-500/40 bg-red-50/70 p-6 text-sm text-red-700 dark:bg-red-950/20 dark:text-red-300">
          <p>{error}</p>
          <Button type="button" variant="outline" onClick={() => void loadMetrics()}>
            Retry
          </Button>
        </Card>
      ) : metrics ? (
        <>
          <section className="space-y-3">
            <h2 className="text-lg font-semibold text-foreground">Activation</h2>
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {activationCards.map((card) => (
                <MetricCard key={card.label} label={card.label} value={card.value} detail={card.detail} />
              ))}
            </div>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold text-foreground">Paywall &amp; Value Loop</h2>
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {paywallCards.map((card) => (
                <MetricCard key={card.label} label={card.label} value={card.value} detail={card.detail} />
              ))}
            </div>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold text-foreground">Checkout conversion</h2>
            <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
              {checkoutCards.map((card) => (
                <MetricCard key={card.label} label={card.label} value={card.value} detail={card.detail} />
              ))}
            </div>
          </section>

          <section className="space-y-3">
            <h2 className="text-lg font-semibold text-foreground">W1→W2 retention</h2>
            <div className="grid gap-4 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.5fr)]">
              <MetricCard
                label="Returned in Week 2"
                value={formatPercent(metrics.retentionCohort.ratePercent)}
                detail={`${formatMetric(metrics.retentionCohort.returnedWeek2Users)} of ${formatMetric(metrics.retentionCohort.eligibleActivatedUsers)} eligible activated users`}
              />
              <Card className="overflow-hidden">
                {metrics.retentionCohort.weeklyCohorts.length > 0 ? (
                  <div className="overflow-x-auto">
                    <table className="w-full min-w-[520px] text-left text-sm">
                      <thead className="border-b border-border bg-muted/40 text-xs uppercase tracking-wide text-foreground/55">
                        <tr>
                          <th className="px-4 py-3 font-semibold">Week</th>
                          <th className="px-4 py-3 font-semibold">Size</th>
                          <th className="px-4 py-3 font-semibold">Returned</th>
                          <th className="px-4 py-3 font-semibold">Rate</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-border">
                        {metrics.retentionCohort.weeklyCohorts.map((cohort) => (
                          <tr key={cohort.weekStart}>
                            <td className="px-4 py-3 font-medium text-foreground">{formatWeek(cohort.weekStart)}</td>
                            <td className="px-4 py-3 text-foreground/75">{formatMetric(cohort.cohortSize)}</td>
                            <td className="px-4 py-3 text-foreground/75">{formatMetric(cohort.returnedCount)}</td>
                            <td className="px-4 py-3 text-foreground/75">{formatPercent(cohort.ratePercent)}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <p className="p-5 text-sm text-foreground/65">No eligible retention cohorts yet.</p>
                )}
              </Card>
            </div>
          </section>
        </>
      ) : null}
    </div>
  );
}
