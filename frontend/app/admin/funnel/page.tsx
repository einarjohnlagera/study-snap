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
        </>
      ) : null}
    </div>
  );
}
