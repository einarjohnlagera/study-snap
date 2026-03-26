import { Card } from "@/components/ui/card";
import type { DashboardWeeklyActivityResponse } from "@/lib/api";

type DashboardWeeklyActivityCardProps = {
  activity: DashboardWeeklyActivityResponse | null;
};

export function DashboardWeeklyActivityCard({
  activity,
}: DashboardWeeklyActivityCardProps) {
  const stats = [
    { label: "Study Packs Created", value: activity?.studyPacksCreated ?? 0 },
    { label: "Quizzes Taken", value: activity?.quizzesTaken ?? 0 },
    { label: "Adaptive Sessions", value: activity?.adaptiveSessions ?? 0 },
    { label: "Study Days", value: activity?.studyDays ?? 0 },
  ];

  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold sm:text-xl">This Week</h2>
      <Card className="grid gap-3 p-4 sm:grid-cols-2 sm:p-6 lg:grid-cols-4">
        {stats.map((stat) => (
          <div key={stat.label} className="rounded-lg border border-border bg-background p-3">
            <p className="text-xs uppercase tracking-wide text-foreground/60">{stat.label}</p>
            <p className="mt-2 text-xl font-semibold text-foreground">{stat.value}</p>
          </div>
        ))}
      </Card>
    </section>
  );
}
