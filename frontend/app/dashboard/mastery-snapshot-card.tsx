import { Card } from "@/components/ui/card";
import type { MasterySnapshotResponse } from "@/lib/api";

type MasterySnapshotCardProps = {
  snapshot: MasterySnapshotResponse | null;
};

function formatScore(value: number) {
  if (Number.isInteger(value)) {
    return `${value}%`;
  }
  return `${value.toFixed(2).replace(/\.?0+$/, "")}%`;
}

export function MasterySnapshotCard({ snapshot }: MasterySnapshotCardProps) {
  const hasCompletedReviews = Boolean(
    snapshot && snapshot.averageRecentScore !== null && snapshot.bestRecentScore !== null,
  );

  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="space-y-1">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          MASTERY SNAPSHOT
        </p>
        {hasCompletedReviews && snapshot ? (
          <div className="space-y-2 text-sm text-foreground/80">
            <p>
              <span className="font-medium text-foreground">Average recent score:</span>{" "}
              {formatScore(snapshot.averageRecentScore!)}
            </p>
            <p>
              <span className="font-medium text-foreground">Best recent score:</span>{" "}
              {formatScore(snapshot.bestRecentScore!)}
            </p>
            <p>
              <span className="font-medium text-foreground">Study packs reviewed:</span>{" "}
              {snapshot.studyPacksReviewed}
            </p>
          </div>
        ) : (
          <p className="text-sm text-foreground/75">
            Complete your first Quick Review to start tracking your progress.
          </p>
        )}
      </div>
    </Card>
  );
}
