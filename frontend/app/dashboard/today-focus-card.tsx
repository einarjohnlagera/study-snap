import Link from "next/link";
import { Compass } from "lucide-react";
import { ResponsiveActionButton, ResponsiveActionLink } from "@/components/ui/action-button";
import { Card } from "@/components/ui/card";
import type { TodayFocusResponse } from "@/lib/api";

type TodayFocusCardProps = {
  focus: TodayFocusResponse;
  onUnlockAdaptivePractice: () => void;
};

function resolveActionHref(focus: TodayFocusResponse) {
  if (
    (focus.type === "RESUME_REVIEW" || focus.type === "RETRY_REVIEW" || focus.type === "REVIEW_PACK")
    && focus.noteId
  ) {
    return `/notes/${focus.noteId}/quick-review`;
  }
  if (focus.type === "PRACTICE_WEAK_CONCEPT" && focus.noteId) {
    return `/notes/${focus.noteId}/adaptive-practice`;
  }
  if (focus.type === "STUDY_SUGGESTION") {
    return "/study";
  }
  return "/library";
}

export function TodayFocusCard({ focus, onUnlockAdaptivePractice }: Readonly<TodayFocusCardProps>) {
  const actionHref = resolveActionHref(focus);
  const message = focus.type === "PRACTICE_WEAK_CONCEPT"
    ? "Practice weak concepts from your recent review."
    : focus.message;
  const dueConcepts = focus.concepts ?? [];
  const isDueConceptFocus = focus.type === "DUE_CONCEPTS_REVIEW";

  return (
    <Card className="space-y-4 p-4 sm:p-6">
      <div className="space-y-2">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
          TODAY&apos;S FOCUS
        </p>
        <h2 className="flex items-center gap-2 text-lg font-semibold sm:text-xl">
          <Compass className="h-5 w-5 text-blue-600 dark:text-blue-400" aria-hidden="true" />
          {focus.title}
        </h2>
        <p className="text-sm leading-relaxed text-foreground/80">{message}</p>
      </div>

      {isDueConceptFocus && dueConcepts.length > 0 ? (
        <ul className="space-y-2 rounded-lg border border-border bg-muted/30 p-3 text-sm">
          {dueConcepts.map((concept) => (
            <li key={`${concept.noteId ?? "unavailable"}-${concept.concept}`} className="flex flex-wrap items-center justify-between gap-2">
              <span className="font-medium text-foreground">{concept.concept}</span>
              {concept.noteId ? (
                <Link href={`/notes/${concept.noteId}`} className="text-foreground/70 hover:text-blue-700 hover:underline dark:hover:text-blue-300">
                  {concept.noteTitle ?? "Source note"}
                </Link>
              ) : (
                <span className="text-foreground/60">{concept.noteTitle ?? "Source note unavailable"}</span>
              )}
            </li>
          ))}
        </ul>
      ) : null}

      {isDueConceptFocus ? (
        focus.adaptivePracticeAvailable && focus.noteId ? (
          <ResponsiveActionLink
            href={`/notes/${focus.noteId}/adaptive-practice`}
            action="adaptivePractice"
            label={focus.actionLabel}
            className="w-full sm:w-auto"
          />
        ) : focus.noteId ? (
          <ResponsiveActionLink
            href={`/notes/${focus.noteId}`}
            action="library"
            label="Revisit Note"
            className="w-full sm:w-auto"
          />
        ) : (
          <ResponsiveActionButton
            type="button"
            variant="outline"
            onClick={onUnlockAdaptivePractice}
            action="adaptivePractice"
            label="Unlock Adaptive Practice"
            className="w-full sm:w-auto"
          />
        )
      ) : (
        <ResponsiveActionLink href={actionHref} action="quickReview" label={focus.actionLabel} className="w-full sm:w-auto" />
      )}
    </Card>
  );
}
