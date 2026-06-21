"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { adoptStudyPlan, type NoteCollectionSummary } from "@/lib/api";
import { setStudyPlanSkippedNotice } from "@/lib/study-plan-skipped-notice";

type PublicStudyPlanCardProps = {
  plan: NoteCollectionSummary;
  adoptedCollection: NoteCollectionSummary | null;
};

export function PublicStudyPlanCard({ plan, adoptedCollection }: Readonly<PublicStudyPlanCardProps>) {
  const router = useRouter();
  const [adopting, setAdopting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleStart = async () => {
    if (adoptedCollection) {
      router.push(`/collections/${adoptedCollection.id}`);
      return;
    }
    setAdopting(true);
    setError(null);
    try {
      const result = await adoptStudyPlan(plan.id);
      setStudyPlanSkippedNotice(result.collectionId, result.skippedCount);
      router.push(`/collections/${result.collectionId}`);
    } catch (adoptError) {
      setError(adoptError instanceof Error ? adoptError.message : "Could not start this plan.");
    } finally {
      setAdopting(false);
    }
  };

  return (
    <Card className="flex min-h-44 flex-col justify-between gap-4 border-blue-500/20 bg-blue-500/5 p-5">
      <div className="space-y-1.5">
        <CardTitle className="line-clamp-2">{plan.title}</CardTitle>
        <CardDescription className="line-clamp-2 text-sm">
          {plan.description || `${plan.itemCount} notes in saved order.`}
        </CardDescription>
      </div>
      <div className="space-y-3">
        <p className="text-sm text-foreground/70">
          {plan.itemCount} {plan.itemCount === 1 ? "note" : "notes"} curated for this track.
        </p>
        <Button
          type="button"
          className="w-full"
          loading={adopting}
          loadingText="Starting..."
          onClick={handleStart}
        >
          {adoptedCollection ? "Continue this plan" : "Start this plan"}
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
