"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import {
  adoptStudyPlan,
  listCollections,
  listPublicStudyPlans,
  type NoteCollectionSummary,
  type ProfileType,
} from "@/lib/api";
import { getCollectionLabels } from "@/lib/collection-labels";
import { normalizeCourseProgram } from "@/lib/learning-profile";

type DashboardStudyPlanSectionProps = {
  courseProgram: string | null;
  profileType: ProfileType | null;
};

const SKIPPED_NOTICE_PREFIX = "notelib-study-plan-skipped-";

function getSkippedNoticeKey(collectionId: string): string {
  return `${SKIPPED_NOTICE_PREFIX}${collectionId}`;
}

export function getStudyPlanSkippedNotice(collectionId: string): number | null {
  try {
    const key = getSkippedNoticeKey(collectionId);
    const raw = globalThis.sessionStorage?.getItem(key);
    if (!raw) {
      return null;
    }
    globalThis.sessionStorage?.removeItem(key);
    const parsed = Number.parseInt(raw, 10);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  } catch {
    return null;
  }
}

export function DashboardStudyPlanSection({
  courseProgram,
  profileType,
}: Readonly<DashboardStudyPlanSectionProps>) {
  const router = useRouter();
  const labels = useMemo(() => getCollectionLabels(profileType), [profileType]);
  const normalizedCourseProgram = useMemo(() => normalizeCourseProgram(courseProgram), [courseProgram]);
  const [plan, setPlan] = useState<NoteCollectionSummary | null>(null);
  const [adoptedPlan, setAdoptedPlan] = useState<NoteCollectionSummary | null>(null);
  const [loadedCourseProgram, setLoadedCourseProgram] = useState<string | null>(null);
  const [adopting, setAdopting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!normalizedCourseProgram) {
      setPlan(null);
      setAdoptedPlan(null);
      setLoadedCourseProgram(null);
      return;
    }

    let cancelled = false;
    setError(null);
    void Promise.all([
      listPublicStudyPlans({ courseProgram: normalizedCourseProgram }),
      listCollections(),
    ])
      .then(([publicPlans, personalCollections]) => {
        if (cancelled) {
          return;
        }
        const matchingPlan = publicPlans[0] ?? null;
        setPlan(matchingPlan);
        setAdoptedPlan(
          matchingPlan
            ? personalCollections.find((collection) => collection.sourcePlanId === matchingPlan.id) ?? null
            : null,
        );
        setLoadedCourseProgram(normalizedCourseProgram);
      })
      .catch(() => {
        if (!cancelled) {
          setPlan(null);
          setAdoptedPlan(null);
          setLoadedCourseProgram(normalizedCourseProgram);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [normalizedCourseProgram]);

  if (!normalizedCourseProgram || loadedCourseProgram !== normalizedCourseProgram || !plan) {
    return null;
  }

  const continuePlan = adoptedPlan ?? null;

  const handleStart = async () => {
    if (continuePlan) {
      router.push(`/collections/${continuePlan.id}`);
      return;
    }
    setAdopting(true);
    setError(null);
    try {
      const result = await adoptStudyPlan(plan.id);
      if (result.skippedCount > 0) {
        globalThis.sessionStorage?.setItem(getSkippedNoticeKey(result.collectionId), String(result.skippedCount));
      }
      router.push(`/collections/${result.collectionId}`);
    } catch (adoptError) {
      setError(adoptError instanceof Error ? adoptError.message : "Could not start this plan.");
    } finally {
      setAdopting(false);
    }
  };

  return (
    <section className="space-y-3 sm:space-y-4">
      <div className="flex flex-col gap-0.5 sm:flex-row sm:items-center sm:justify-between">
        <h2 className="text-lg font-semibold sm:text-xl">Recommended {labels.singular}</h2>
        <p className="text-xs text-foreground/65">{normalizedCourseProgram}</p>
      </div>
      <Card className="space-y-4 border-blue-500/20 bg-blue-500/5 p-4 sm:p-6">
        <div className="space-y-1.5">
          <CardTitle>{plan.title}</CardTitle>
          <CardDescription>{plan.description || `${plan.itemCount} notes in saved order.`}</CardDescription>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-foreground/70">
            {plan.itemCount} {plan.itemCount === 1 ? "note" : "notes"} curated for this track.
          </p>
          <Button type="button" loading={adopting} loadingText="Starting..." onClick={handleStart}>
            {continuePlan ? "Continue this plan" : "Start this plan"}
          </Button>
        </div>
        {error ? (
          <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-200">
            {error}
          </p>
        ) : null}
      </Card>
    </section>
  );
}
