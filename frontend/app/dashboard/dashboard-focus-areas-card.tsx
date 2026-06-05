import Link from "next/link";
import { Card } from "@/components/ui/card";
import { ResponsiveActionButton, ResponsiveActionLink } from "@/components/ui/action-button";
import type { DashboardFocusAreasResponse } from "@/lib/api";

type DashboardFocusAreasCardProps = {
  focusAreas: DashboardFocusAreasResponse | null;
  onUnlockAdaptivePractice: () => void;
  title?: string;
  emptyStateText?: string;
  primaryActionLabel?: string;
  lockedActionLabel?: string;
  showAction?: boolean;
};

function getBarTone(accuracyPercentage: number) {
  if (accuracyPercentage < 40) {
    return "bg-rose-500";
  }
  if (accuracyPercentage < 60) {
    return "bg-amber-500";
  }
  return "bg-blue-600 dark:bg-blue-400";
}

function formatPercent(value: number) {
  if (Number.isInteger(value)) {
    return `${value}%`;
  }
  return `${value.toFixed(2).replace(/\.?0+$/, "")}%`;
}

export function DashboardFocusAreasCard({
  focusAreas,
  onUnlockAdaptivePractice,
  title = "Focus Areas",
  emptyStateText = "Finish a few Challenge Quizzes to surface the concepts that need extra attention.",
  primaryActionLabel = "Practice Weak Concepts",
  lockedActionLabel = "Unlock Adaptive Practice",
  showAction = true,
}: Readonly<DashboardFocusAreasCardProps>) {
  const concepts = focusAreas?.concepts ?? [];
  const hasConcepts = concepts.length > 0;

  return (
    <section className="space-y-3">
      <h2 className="text-lg font-semibold sm:text-xl">{title}</h2>
      <Card className="space-y-4 p-4 sm:p-6">
        {hasConcepts ? (
          <div className="space-y-4">
            {concepts.map((concept) => {
              const width = Math.max(6, Math.min(100, Math.round(concept.accuracyPercentage)));
              return (
                <div key={concept.conceptName} className="space-y-2">
                  <div className="flex items-center justify-between gap-3">
                    <p className="text-sm font-medium text-foreground">{concept.conceptName}</p>
                    <p className="text-sm text-foreground/70">{formatPercent(concept.accuracyPercentage)}</p>
                  </div>
                  <div className="h-2 overflow-hidden rounded-full bg-muted">
                    <div
                      className={`h-full rounded-full transition-all ${getBarTone(concept.accuracyPercentage)}`}
                      style={{ width: `${width}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <p className="text-sm leading-relaxed text-foreground/75">
            {emptyStateText}
          </p>
        )}

        {hasConcepts && showAction ? (
          focusAreas?.adaptivePracticeAvailable && focusAreas.practiceNoteId ? (
            <ResponsiveActionLink
              href={`/notes/${focusAreas.practiceNoteId}/adaptive-practice`}
              action="adaptivePractice"
              label={primaryActionLabel}
              className="w-full sm:w-auto"
            />
          ) : focusAreas?.practiceNoteId ? (
            <ResponsiveActionLink
              href={`/notes/${focusAreas.practiceNoteId}`}
              action="library"
              label="Revisit Note"
              className="w-full sm:w-auto"
            />
          ) : (
            <ResponsiveActionButton type="button" variant="outline" onClick={onUnlockAdaptivePractice} action="adaptivePractice" label={lockedActionLabel} />
          )
        ) : null}
        <Link href="/progress" className="block text-sm text-foreground/60 hover:text-foreground/80">
          View full progress report &rarr;
        </Link>
      </Card>
    </section>
  );
}
