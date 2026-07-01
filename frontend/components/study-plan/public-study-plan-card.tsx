"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { adoptGoal, adoptStudyPlan, type NoteCollectionSummary } from "@/lib/api";
import { setStudyPlanSkippedNotice } from "@/lib/study-plan-skipped-notice";

type PublicStudyPlanCardProps = {
  plan: NoteCollectionSummary;
  adoptedCollection: NoteCollectionSummary | null;
};

export function PublicStudyPlanCard({ plan, adoptedCollection }: Readonly<PublicStudyPlanCardProps>) {
  const router = useRouter();
  const [adopting, setAdopting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isGoal = plan.childCount > 0;
  const subjectPlanLabel = `${plan.childCount} ${plan.childCount === 1 ? "Subject plan" : "Subject plans"}`;
  const noteLabel = `${plan.itemCount} ${plan.itemCount === 1 ? "note" : "notes"}`;
  const descriptionFallback = isGoal
    ? `${subjectPlanLabel} · ${noteLabel}`
    : `${noteLabel} in saved order.`;
  const detailLine = isGoal
    ? `${subjectPlanLabel} · ${noteLabel}`
    : `${noteLabel} curated for this track.`;
  const buttonLabel = adoptedCollection
    ? (isGoal ? "Continue this Goal" : "Continue this plan")
    : (isGoal ? "Start this Goal" : "Start this plan");

  const handleStart = async () => {
    if (adoptedCollection) {
      router.push(`/collections/${adoptedCollection.id}`);
      return;
    }
    setAdopting(true);
    setError(null);
    try {
      if (isGoal) {
        const result = await adoptGoal(plan.id);
        setStudyPlanSkippedNotice(result.goalCollectionId, result.skippedSubjectCount);
        router.push(`/collections/${result.goalCollectionId}`);
        return;
      }
      const result = await adoptStudyPlan(plan.id);
      setStudyPlanSkippedNotice(result.collectionId, result.skippedCount);
      router.push(`/collections/${result.collectionId}`);
    } catch (adoptError) {
      setError(adoptError instanceof Error ? adoptError.message : `Could not start this ${isGoal ? "Goal" : "plan"}.`);
    } finally {
      setAdopting(false);
    }
  };

  return (
    <Card className="flex min-h-44 flex-col justify-between gap-4 border-blue-500/20 bg-blue-500/5 p-5">
      <div className="space-y-1.5">
        <CardTitle className="line-clamp-2">{plan.title}</CardTitle>
        <CardDescription className="line-clamp-2 text-sm">
          {plan.description || descriptionFallback}
        </CardDescription>
      </div>
      <div className="space-y-3">
        <p className="text-sm text-foreground/70">{detailLine}</p>
        <Button
          type="button"
          className="w-full"
          loading={adopting}
          loadingText="Starting..."
          onClick={handleStart}
        >
          {buttonLabel}
        </Button>
        {error ? (
          <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-200">
            {error}
          </p>
        ) : null}
      </div>
    </Card>
  );
}
