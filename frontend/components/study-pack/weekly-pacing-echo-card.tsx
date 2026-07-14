"use client";

import { Card } from "@/components/ui/card";
import { buildWeeklyPacingEchoLine } from "@/lib/weekly-pacing";

export function WeeklyPacingEchoCard({
  weeksRemaining,
  goalLabel,
}: Readonly<{ weeksRemaining: number | null; goalLabel: string }>) {
  const line = buildWeeklyPacingEchoLine(weeksRemaining, goalLabel);
  if (!line) {
    return null;
  }
  return (
    <Card className="space-y-1 border-blue-500/20 bg-blue-500/5 p-4">
      <p className="text-sm text-foreground/75">{line}</p>
    </Card>
  );
}
