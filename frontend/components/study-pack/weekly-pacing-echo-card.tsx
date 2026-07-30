"use client";

import { Card } from "@/components/ui/card";
import { buildWeeklyPacingEchoLine } from "@/lib/weekly-pacing";

export function WeeklyPacingEchoCard({
  weeksRemaining,
  goalLabel,
  contained = false,
}: Readonly<{ weeksRemaining: number | null; goalLabel: string; contained?: boolean }>) {
  const line = buildWeeklyPacingEchoLine(weeksRemaining, goalLabel);
  if (!line) {
    return null;
  }
  const content = (
    <>
      <p className="text-sm text-foreground/75">{line}</p>
    </>
  );

  return contained ? (
    <section className="space-y-1 p-4" data-result-guidance-item="weekly-pacing">
      {content}
    </section>
  ) : (
    <Card className="space-y-1 border-blue-500/20 bg-blue-500/5 p-4">
      {content}
    </Card>
  );
}
