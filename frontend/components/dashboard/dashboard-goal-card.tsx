"use client";

import Link from "next/link";
import { useEffect } from "react";
import { Card } from "@/components/ui/card";
import { buttonVariants } from "@/components/ui/button";
import { trackAnalyticsEvent, type GoalNudgeResponse } from "@/lib/api";

type DashboardGoalCardProps = {
  goalSummary: GoalNudgeResponse;
};

export function DashboardGoalCard({ goalSummary }: Readonly<DashboardGoalCardProps>) {
  const dueCopy = goalSummary.dueConcepts === 0
    ? "All caught up — keep practicing!"
    : `${goalSummary.dueConcepts} ${goalSummary.dueConcepts === 1 ? "concept" : "concepts"} due`;

  useEffect(() => {
    void trackAnalyticsEvent({
      eventType: "DASHBOARD_GOAL_CARD_VIEWED",
      metadata: { studyGoal: goalSummary.studyGoal },
    });
  }, [goalSummary.studyGoal]);

  return (
    <Card className="overflow-hidden border-blue-500/25 bg-linear-to-br from-blue-500/10 via-background to-emerald-500/10 p-4 sm:p-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-2">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">
            {goalSummary.goalName} Goal
          </p>
          <div className="space-y-1">
            <h2 className="text-lg font-semibold tracking-tight sm:text-xl">{goalSummary.goalLabel}</h2>
            <p className="text-sm text-foreground/70">{dueCopy}</p>
            {goalSummary.weakestGoalSubject ? (
              <p className="text-sm text-foreground/70">
                Focus: <span className="font-medium text-foreground">{goalSummary.weakestGoalSubject}</span>
              </p>
            ) : null}
          </div>
        </div>
        <div className="text-left sm:text-right">
          <p className="text-4xl font-semibold tracking-tight text-foreground">{goalSummary.masteryPercentage}%</p>
          <p className="text-xs font-medium uppercase tracking-wide text-foreground/55">mastered</p>
        </div>
      </div>
      <div className="mt-4">
        <Link
          href="/progress"
          className={buttonVariants({ variant: "outline", size: "sm" })}
          onClick={() => {
            void trackAnalyticsEvent({
              eventType: "DASHBOARD_GOAL_CARD_CTA_CLICKED",
              metadata: { studyGoal: goalSummary.studyGoal },
            });
          }}
        >
          View goal progress
        </Link>
      </div>
    </Card>
  );
}
