import { Card } from "@/components/ui/card";
import type { DashboardPerformanceSummaryResponse } from "@/lib/api";

type DashboardPerformanceSummaryCardProps = {
  summary: DashboardPerformanceSummaryResponse | null;
};

function formatPercent(value: number | null) {
  if (value === null) {
    return "No data yet";
  }
  if (Number.isInteger(value)) {
    return `${value}%`;
  }
  return `${value.toFixed(2).replace(/\.?0+$/, "")}%`;
}

export function DashboardPerformanceSummaryCard({
  summary,
}: DashboardPerformanceSummaryCardProps) {
  const cards = [
    { label: "Average Quiz Score", value: formatPercent(summary?.averageQuizScore ?? null) },
    { label: "Total Quizzes Taken", value: String(summary?.totalQuizzesTaken ?? 0) },
    { label: "Study Packs Created", value: String(summary?.studyPacksCreated ?? 0) },
  ];

  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold sm:text-xl">Performance Summary</h2>
      <Card className="space-y-4 p-4 sm:p-6">
        <div className="grid gap-3 sm:grid-cols-3">
          {cards.map((card) => (
            <div key={card.label} className="rounded-lg border border-border bg-background p-3">
              <p className="text-xs uppercase tracking-wide text-foreground/60">{card.label}</p>
              <p className="mt-2 text-lg font-semibold text-foreground">{card.value}</p>
            </div>
          ))}
        </div>

        <div className="grid gap-3 sm:grid-cols-2">
          <div className="rounded-lg border border-emerald-500/20 bg-emerald-500/5 p-3">
            <p className="text-xs uppercase tracking-wide text-foreground/60">Strongest Concept</p>
            <p className="mt-2 text-base font-semibold text-foreground">
              {summary?.strongestConcept?.conceptName ?? "Not enough concept data yet"}
            </p>
            {summary?.strongestConcept ? (
              <p className="mt-1 text-sm text-foreground/75">
                {formatPercent(summary.strongestConcept.accuracyPercentage)}
              </p>
            ) : (
              <p className="mt-1 text-sm text-foreground/75">
                Complete a few quizzes to start tracking concept accuracy.
              </p>
            )}
          </div>

          <div className="rounded-lg border border-rose-500/20 bg-rose-500/5 p-3">
            <p className="text-xs uppercase tracking-wide text-foreground/60">Weakest Concept</p>
            <p className="mt-2 text-base font-semibold text-foreground">
              {summary?.weakestConcept?.conceptName ?? "Not enough concept data yet"}
            </p>
            {summary?.weakestConcept ? (
              <p className="mt-1 text-sm text-foreground/75">
                {formatPercent(summary.weakestConcept.accuracyPercentage)}
              </p>
            ) : (
              <p className="mt-1 text-sm text-foreground/75">
                Challenge Quiz results will surface the concepts you should revisit.
              </p>
            )}
          </div>
        </div>
      </Card>
    </section>
  );
}
