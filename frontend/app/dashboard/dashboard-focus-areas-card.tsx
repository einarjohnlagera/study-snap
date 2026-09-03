import Link from "next/link";
import { Card } from "@/components/ui/card";
import { ResponsiveActionButton, ResponsiveActionLink } from "@/components/ui/action-button";
import type { DashboardFocusAreasResponse } from "@/lib/api";
import {
  ADAPTIVE_PRACTICE_DASHBOARD_FOCUS_AREAS_ENTRY,
  buildAdaptivePracticeHref,
} from "@/lib/adaptive-practice-entry";

type DashboardFocusAreasCardProps = {
  focusAreas: DashboardFocusAreasResponse | null;
  onUnlockAdaptivePractice: () => void;
  title?: string;
  emptyStateText?: string;
  primaryActionLabel?: string;
  lockedActionLabel?: string;
  showAction?: boolean;
  onPracticeAcrossPlan?: (collectionId: string) => void;
  planActionLabel?: string;
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
  onPracticeAcrossPlan,
  planActionLabel = "Practice Across This Plan",
}: Readonly<DashboardFocusAreasCardProps>) {
  const concepts = focusAreas?.concepts ?? [];
  const hasConcepts = concepts.length > 0;

  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-lg font-semibold sm:text-xl">{title}</h2>
        <Link href="/progress" className="shrink-0 text-sm font-medium text-blue-600 hover:underline dark:text-blue-400">
          View progress report &rarr;
        </Link>
      </div>
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
            <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
              <ResponsiveActionLink
                href={buildAdaptivePracticeHref(focusAreas.practiceNoteId, {
                  entry: ADAPTIVE_PRACTICE_DASHBOARD_FOCUS_AREAS_ENTRY,
                })}
                action="adaptivePractice"
                label={primaryActionLabel}
                className="w-full sm:w-auto"
              />
              {/* Plan-scoped sibling. It appears only when the server resolved a plan for the
                  weakest note -- the client never picks one, so this cannot disagree with the
                  session the server would actually start. */}
              {onPracticeAcrossPlan && focusAreas.practiceCollectionId ? (
                <ResponsiveActionButton
                  type="button"
                  variant="outline"
                  action="adaptivePractice"
                  label={planActionLabel}
                  className="w-full sm:w-auto"
                  onClick={() => onPracticeAcrossPlan(focusAreas.practiceCollectionId as string)}
                />
              ) : null}
            </div>
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
      </Card>
    </section>
  );
}
