import { Card } from "@/components/ui/card";
import type { StudyEngagementResponse } from "@/lib/api";

type StudyConsistencyCardProps = {
  engagement: StudyEngagementResponse;
};

function formatStudyDaysThisWeek(studyDaysThisWeek: number) {
  const dayLabel = studyDaysThisWeek === 1 ? "day" : "days";
  return `${studyDaysThisWeek} study ${dayLabel} this week`;
}

export function StudyConsistencyCard({ engagement }: StudyConsistencyCardProps) {
  if (engagement.engagementMode === "FOCUSED") {
    return null;
  }

  if (engagement.engagementMode === "CONSISTENCY") {
    return (
      <Card className="space-y-4 p-4 sm:p-6">
        <div className="space-y-1">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
            Study Consistency
          </p>
          <h2 className="text-lg font-semibold sm:text-xl">
            {formatStudyDaysThisWeek(engagement.studyDaysThisWeek)}
          </h2>
          <p className="text-sm text-foreground/75">
            {engagement.studyDaysThisWeek > 0
              ? "Nice consistency! Reviewing a little at a time helps strengthen memory."
              : "A short review today can help you build a steady study rhythm."}
          </p>
        </div>
      </Card>
    );
  }

  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          Study Streak
        </p>
        <h2 className="text-lg font-semibold sm:text-xl">
          {engagement.currentStreak > 0 ? `🔥 ${engagement.currentStreak} day streak` : "Start your study streak"}
        </h2>
        <p className="text-sm text-foreground/75">
          {engagement.currentStreak > 0
            ? "Nice momentum! A short review today keeps it going."
            : "Begin with a short review today to start your streak."}
        </p>
        <p className="text-xs text-foreground/60">Best streak: {engagement.longestStreak} days</p>
      </div>
    </Card>
  );
}
