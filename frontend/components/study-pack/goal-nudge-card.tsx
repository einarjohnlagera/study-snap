"use client";

import Link from "next/link";
import { useEffect } from "react";
import { Card } from "@/components/ui/card";
import { trackAnalyticsEvent, type PostSessionNextStepResponse } from "@/lib/api";

type GoalNudge = NonNullable<PostSessionNextStepResponse["goalNudge"]>;

type GoalNudgeCardProps = {
  goalNudge: GoalNudge;
  noteId: string | null;
  contained?: boolean;
};

export function GoalNudgeCard({ goalNudge, noteId, contained = false }: Readonly<GoalNudgeCardProps>) {
  useEffect(() => {
    void trackAnalyticsEvent({
      eventType: "GOAL_NUDGE_SHOWN",
      metadata: { studyGoal: goalNudge.studyGoal, noteId },
    });
  }, [goalNudge.studyGoal, noteId]);

  const dueCopy = goalNudge.dueConcepts === 0
    ? "You've reviewed all current goal concepts."
    : `${goalNudge.dueConcepts} ${goalNudge.dueConcepts === 1 ? "concept" : "concepts"} due`;

  const content = (
    <>
      <div className="flex items-start justify-between gap-4">
        <div className="space-y-1">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
            {goalNudge.goalName} Goal
          </p>
          <h2 className="text-base font-semibold text-foreground">{goalNudge.goalLabel}</h2>
        </div>
        <p className="shrink-0 text-right text-lg font-semibold text-foreground">
          {goalNudge.masteryPercentage}% <span className="text-xs font-medium text-foreground/60">mastered</span>
        </p>
      </div>
      <p className="text-sm text-foreground/75">{dueCopy}</p>
      <Link
        href="/progress"
        className="inline-flex text-sm font-medium text-blue-700 hover:underline dark:text-blue-300"
        onClick={() => {
          void trackAnalyticsEvent({
            eventType: "GOAL_NUDGE_CTA_CLICKED",
            metadata: { studyGoal: goalNudge.studyGoal, noteId },
          });
        }}
      >
        View {goalNudge.goalName} progress &rarr;
      </Link>
    </>
  );

  return contained ? (
    <section className="space-y-4 p-4" data-result-guidance-item="goal-nudge">
      {content}
    </section>
  ) : (
    <Card className="space-y-4 border-blue-500/20 bg-blue-500/5 p-4">
      {content}
    </Card>
  );
}
