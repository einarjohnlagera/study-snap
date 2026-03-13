import Link from "next/link";
import { Compass } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import type { TodayFocusResponse } from "@/lib/api";

type TodayFocusCardProps = {
  focus: TodayFocusResponse;
};

function resolveActionHref(focus: TodayFocusResponse) {
  if (
    (focus.type === "RESUME_REVIEW" || focus.type === "RETRY_REVIEW" || focus.type === "REVIEW_PACK")
    && focus.studyPackId
  ) {
    return `/study-packs/${focus.studyPackId}/quick-review`;
  }
  if (focus.type === "PRACTICE_WEAK_CONCEPT" && focus.studyPackId) {
    return `/study-packs/${focus.studyPackId}/adaptive-practice`;
  }
  if (focus.type === "STUDY_SUGGESTION") {
    return "/study";
  }
  return "/library";
}

export function TodayFocusCard({ focus }: TodayFocusCardProps) {
  const actionHref = resolveActionHref(focus);

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
        <p className="text-sm leading-relaxed text-foreground/80">{focus.message}</p>
      </div>

      <Link href={actionHref} className="w-full sm:w-auto">
        <Button type="button" className="w-full sm:w-auto">{focus.actionLabel}</Button>
      </Link>
    </Card>
  );
}
