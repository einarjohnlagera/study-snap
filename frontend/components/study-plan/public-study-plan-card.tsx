"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { adoptGoal, adoptStudyPlan, type NoteCollectionSummary, type ProfileType } from "@/lib/api";
import { getCollectionLabels } from "@/lib/collection-labels";
import { setJustAdoptedNotice } from "@/lib/just-adopted-notice";
import { setStudyPlanSkippedNotice } from "@/lib/study-plan-skipped-notice";

type PublicStudyPlanCardProps = {
  plan: NoteCollectionSummary;
  adoptedCollection: NoteCollectionSummary | null;
  profileType?: ProfileType | null;
};

export function PublicStudyPlanCard({ plan, adoptedCollection, profileType = null }: Readonly<PublicStudyPlanCardProps>) {
  const router = useRouter();
  const labels = useMemo(() => getCollectionLabels(profileType), [profileType]);
  const [adopting, setAdopting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const isGoal = plan.childCount > 0;
  const subjectPlanLabel = `${plan.childCount} ${labels.subjectSingular}${plan.childCount === 1 ? "" : "s"}`;
  const noteLabel = `${plan.itemCount} ${plan.itemCount === 1 ? "note" : "notes"}`;
  const practiceReadyLine = plan.readyCount == null
    ? null
    : `${plan.readyCount} of ${plan.itemCount} notes practice-ready`;
  const descriptionFallback = isGoal
    ? `${subjectPlanLabel} · ${noteLabel}`
    : `${noteLabel} in saved order.`;
  const detailLine = isGoal
    ? `${subjectPlanLabel} · ${noteLabel}`
    : `${noteLabel} curated for this track.`;
  const buttonLabel = adoptedCollection
    ? (isGoal ? `Continue this ${labels.goalSingular}` : `Continue this ${labels.singular}`)
    : (isGoal ? `Start this ${labels.goalSingular}` : `Start this ${labels.singular}`);

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
        setJustAdoptedNotice(result.goalCollectionId);
        router.push(`/collections/${result.goalCollectionId}`);
        return;
      }
      const result = await adoptStudyPlan(plan.id);
      setStudyPlanSkippedNotice(result.collectionId, result.skippedCount);
      router.push(`/collections/${result.collectionId}`);
    } catch (adoptError) {
      setError(adoptError instanceof Error ? adoptError.message : `Could not start this ${isGoal ? labels.goalSingular : labels.singular}.`);
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
        {practiceReadyLine ? <p className="text-sm text-foreground/70">{practiceReadyLine}</p> : null}
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
