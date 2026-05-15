import { Card } from "@/components/ui/card";
import type { MePlanResponse } from "@/lib/me-plan";
import { getUsageProgressPercent } from "@/lib/plans";

type DashboardMonthlyUsageCardProps = {
  usageSummary: MePlanResponse | null;
  title?: string;
};

function getBarTone(used: number, limit: number) {
  const percent = getUsageProgressPercent(used, limit);
  if (percent >= 85) {
    return "bg-rose-500";
  }
  if (percent >= 60) {
    return "bg-amber-500";
  }
  return "bg-blue-600 dark:bg-blue-400";
}

export function DashboardMonthlyUsageCard({
  usageSummary,
  title = "This Month",
}: DashboardMonthlyUsageCardProps) {
  if (!usageSummary) {
    return null;
  }

  const items = [
    {
      label: "Study Packs",
      used: usageSummary.usage.studyPacksUsed,
      limit: usageSummary.limits.studyPacksPerMonth,
    },
    {
      label: "Challenge Quiz",
      used: usageSummary.usage.challengeQuizzesUsed,
      limit: usageSummary.limits.challengeQuizzesPerMonth,
    },
    ...(usageSummary.plan === "PRO"
      ? [{
        label: "Adaptive Practice",
        used: usageSummary.usage.adaptivePracticeUsed,
        limit: usageSummary.limits.adaptivePracticePerMonth,
      }, {
        label: "Interview Practice",
        used: usageSummary.usage.interviewPracticeUsed ?? 0,
        limit: usageSummary.limits.interviewPracticePerMonth ?? 0,
      }]
      : []),
  ];

  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold sm:text-xl">{title}</h2>
      <Card className="space-y-4 p-4 sm:p-6">
        {items.map((item) => {
          const width = getUsageProgressPercent(item.used, item.limit);
          return (
            <div key={item.label} className="space-y-2">
              <div className="flex items-center justify-between gap-3">
                <p className="text-sm font-medium text-foreground">{item.label}</p>
                <p className="text-sm text-foreground/70">
                  {item.used} / {item.limit}
                </p>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-muted">
                <div
                  className={`h-full rounded-full transition-all ${getBarTone(item.used, item.limit)}`}
                  style={{ width: `${Math.max(width, item.used > 0 ? 6 : 0)}%` }}
                />
              </div>
            </div>
          );
        })}
      </Card>
    </section>
  );
}
