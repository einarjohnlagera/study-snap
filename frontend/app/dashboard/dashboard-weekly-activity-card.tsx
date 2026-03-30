import { Card } from "@/components/ui/card";
import type { DashboardWeeklyActivityResponse } from "@/lib/api";

type DashboardWeeklyActivityCardProps = {
  activity: DashboardWeeklyActivityResponse | null;
  title?: string;
  labels?: {
    studyPacksCreated?: string;
    quizzesTaken?: string;
    adaptiveSessions?: string;
    studyDays?: string;
  };
};

export function DashboardWeeklyActivityCard({
  activity,
  title = "This Week",
  labels,
}: Readonly<DashboardWeeklyActivityCardProps>) {
  const stats = [
    { label: labels?.studyPacksCreated ?? "Study Packs Created", value: activity?.studyPacksCreated ?? 0 },
    { label: labels?.quizzesTaken ?? "Quizzes Taken", value: activity?.quizzesTaken ?? 0 },
    { label: labels?.adaptiveSessions ?? "Adaptive Sessions", value: activity?.adaptiveSessions ?? 0 },
    { label: labels?.studyDays ?? "Study Days", value: activity?.studyDays ?? 0 },
  ];

  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold sm:text-xl">{title}</h2>
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
