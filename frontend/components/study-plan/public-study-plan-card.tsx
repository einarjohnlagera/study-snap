"use client";

import { useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import {
  adoptGoal,
  adoptStudyPlan,
  getPublicStudyPlanDetail,
  type NoteCollectionDetail,
  type NoteCollectionSummary,
  type ProfileType,
} from "@/lib/api";
import { getCollectionLabels } from "@/lib/collection-labels";
import { setJustAdoptedNotice } from "@/lib/just-adopted-notice";
import { setStudyPlanSkippedNotice } from "@/lib/study-plan-skipped-notice";

type PublicStudyPlanCardProps = {
  plan: NoteCollectionSummary;
  adoptedCollection: NoteCollectionSummary | null;
  profileType?: ProfileType | null;
  canAdopt?: boolean;
};

export function PublicStudyPlanCard({
  plan,
  adoptedCollection,
  profileType = null,
  canAdopt = true,
}: Readonly<PublicStudyPlanCardProps>) {
  const router = useRouter();
  const labels = useMemo(() => getCollectionLabels(profileType), [profileType]);
  const [adopting, setAdopting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);
  const [preview, setPreview] = useState<NoteCollectionDetail | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [loadingPreview, setLoadingPreview] = useState(false);
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
    : !canAdopt
      ? "Sign in to adopt"
    : (isGoal ? `Start this ${labels.goalSingular}` : `Start this ${labels.singular}`);

  const loadPreview = async () => {
    setLoadingPreview(true);
    setPreviewError(null);
    try {
      setPreview(await getPublicStudyPlanDetail(plan.id));
    } catch {
      setPreviewError("Couldn’t load this plan preview. Please try again.");
    } finally {
      setLoadingPreview(false);
    }
  };

  const handlePreview = () => {
    if (previewOpen) {
      setPreviewOpen(false);
      return;
    }
    setPreviewOpen(true);
    if (!preview && !loadingPreview) {
      void loadPreview();
    }
  };

  const previewReadyLine = preview?.readyCount == null
    ? null
    : `${preview.readyCount} of ${preview.items.length} notes practice-ready`;

  const handleStart = async () => {
    if (!canAdopt) {
      router.push("/auth");
      return;
    }
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
        <Button type="button" variant="outline" className="w-full" onClick={handlePreview} aria-expanded={previewOpen}>
          {previewOpen ? "Hide plan preview" : "Preview this plan"}
        </Button>
        {previewOpen ? (
          <section aria-label="Plan preview" className="space-y-3 rounded-xl border border-border bg-background/70 p-3">
            {loadingPreview ? <p className="text-sm text-foreground/70">Loading plan preview…</p> : null}
            {previewError ? (
              <div className="space-y-2">
                <p className="text-sm text-red-700 dark:text-red-200">{previewError}</p>
                <Button type="button" variant="outline" size="sm" onClick={() => void loadPreview()}>
                  Try again
                </Button>
              </div>
            ) : null}
            {preview ? (
              <>
                <div className="space-y-1 text-sm text-foreground/70">
                  {preview.courseProgram ? <p>Course / Program: {preview.courseProgram}</p> : null}
                  <p>
                    {preview.estimatedStudyHours == null
                      ? "Estimated study time is not set."
                      : `Estimated study time: ${preview.estimatedStudyHours} ${preview.estimatedStudyHours === 1 ? "hour" : "hours"}`}
                  </p>
                  {previewReadyLine ? <p>{previewReadyLine}</p> : null}
                </div>
                {preview.items.length === 0 ? (
                  <p className="text-sm text-foreground/70">This plan does not have any available notes yet.</p>
                ) : (
                  <ol className="space-y-2" aria-label="Notes in this plan">
                    {preview.items.map((item) => (
                      <li key={item.noteId} className="rounded-lg border border-border px-3 py-2 text-sm">
                        <p className="font-medium text-foreground">{item.title || "Untitled note"}</p>
                        <p className="text-foreground/70">
                          {[item.subject, item.label ? `Section: ${item.label}` : null].filter(Boolean).join(" · ") || "No subject or section"}
                        </p>
                      </li>
                    ))}
                  </ol>
                )}
              </>
            ) : null}
          </section>
        ) : null}
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
