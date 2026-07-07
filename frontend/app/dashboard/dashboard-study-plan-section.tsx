"use client";

import { useEffect, useMemo, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import {
  adoptGoal,
  adoptStudyPlan,
  listCollections,
  listPublicStudyPlans,
  type NoteCollectionSummary,
  type ProfileType,
} from "@/lib/api";
import { getCollectionLabels } from "@/lib/collection-labels";
import { normalizeCourseProgram } from "@/lib/learning-profile";
import { setStudyPlanSkippedNotice } from "@/lib/study-plan-skipped-notice";

export { getStudyPlanSkippedNotice } from "@/lib/study-plan-skipped-notice";

type DashboardStudyPlanSectionProps = {
  courseProgram: string | null;
  profileType: ProfileType | null;
  primaryCollectionId?: string | null;
  viewAllHref?: string;
  browseWhenEmpty?: boolean;
};

export function DashboardStudyPlanSection({
  courseProgram,
  profileType,
  primaryCollectionId,
  viewAllHref,
  browseWhenEmpty = false,
}: Readonly<DashboardStudyPlanSectionProps>) {
  const router = useRouter();
  const labels = useMemo(() => getCollectionLabels(profileType), [profileType]);
  const normalizedCourseProgram = useMemo(() => normalizeCourseProgram(courseProgram), [courseProgram]);
  const [plan, setPlan] = useState<NoteCollectionSummary | null>(null);
  const [adoptedPlan, setAdoptedPlan] = useState<NoteCollectionSummary | null>(null);
  const [matchCount, setMatchCount] = useState(0);
  const [loadedCourseProgram, setLoadedCourseProgram] = useState<string | null>(null);
  const [adopting, setAdopting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [primaryMatch, setPrimaryMatch] = useState<NoteCollectionSummary | null>(null);
  const [primaryLoaded, setPrimaryLoaded] = useState(false);

  // The Primary Review Set is an explicit, already-owned choice — it takes precedence over the
  // course/program-matched recommendation below and skips the adopt flow entirely (see the
  // ownsSource render path further down). If it comes back not-found (a defensive fallback; the
  // backend invariant should keep this reference valid), rendering falls through to the
  // course/program-driven effect below, same as if no primary were set.
  useEffect(() => {
    if (!primaryCollectionId) {
      setPrimaryMatch(null);
      setPrimaryLoaded(true);
      return;
    }

    let cancelled = false;
    setPrimaryLoaded(false);
    void listCollections()
      .then((collections) => {
        if (cancelled) {
          return;
        }
        setPrimaryMatch(collections.find((collection) => collection.id === primaryCollectionId) ?? null);
        setPrimaryLoaded(true);
      })
      .catch(() => {
        if (!cancelled) {
          setPrimaryMatch(null);
          setPrimaryLoaded(true);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [primaryCollectionId]);

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
        setMatchCount(publicPlans.length);
        setAdoptedPlan(
          matchingPlan
            ? personalCollections.find(
                (collection) => collection.id === matchingPlan.id || collection.sourcePlanId === matchingPlan.id,
              ) ?? null
            : null,
        );
        setLoadedCourseProgram(normalizedCourseProgram);
      })
      .catch(() => {
        if (!cancelled) {
          setPlan(null);
          setMatchCount(0);
          setAdoptedPlan(null);
          setLoadedCourseProgram(normalizedCourseProgram);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [normalizedCourseProgram]);

  const primaryPending = Boolean(primaryCollectionId) && !primaryLoaded;
  const usingPrimary = Boolean(primaryCollectionId) && primaryLoaded && primaryMatch !== null;
  const courseProgramPending = !usingPrimary && (!normalizedCourseProgram || loadedCourseProgram !== normalizedCourseProgram);

  if (primaryPending || courseProgramPending) {
    return null;
  }

  const displayPlan = usingPrimary ? primaryMatch : plan;

  if (!displayPlan) {
    if (!browseWhenEmpty) {
      return null;
    }
    return (
      <section className="space-y-3 sm:space-y-4">
        <div className="flex flex-col gap-0.5 sm:flex-row sm:items-center sm:justify-between">
          <h2 className="text-lg font-semibold sm:text-xl">Recommended {labels.singular}</h2>
          <p className="text-xs text-foreground/65">{normalizedCourseProgram}</p>
        </div>
        <Card className="space-y-1.5 border-dashed p-4 sm:p-6">
          <CardTitle>No curated {labels.plural.toLowerCase()} for {normalizedCourseProgram} yet</CardTitle>
          <CardDescription>
            We add curated {labels.plural.toLowerCase()} per track. Check back soon, or build your own above.
          </CardDescription>
        </Card>
      </section>
    );
  }

  const continuePlan = usingPrimary ? primaryMatch : (adoptedPlan ?? null);
  const ownsSource = continuePlan?.id === displayPlan.id;
  const isGoal = displayPlan.childCount > 0;
  const ctaLabel = continuePlan
    ? (ownsSource ? "Open this plan" : (isGoal ? "Continue this Goal" : "Continue this plan"))
    : (isGoal ? "Start this Goal" : "Start this plan");
  const subjectPlanLabel = `${displayPlan.childCount} ${displayPlan.childCount === 1 ? "Subject plan" : "Subject plans"}`;
  const noteLabel = `${displayPlan.itemCount} ${displayPlan.itemCount === 1 ? "note" : "notes"}`;
  // A Goal never holds notes directly (only its child Subject plans do), so plan.itemCount
  // is always 0 here — showing "0 notes" alongside "N Subject plans" reads as broken/empty.
  // Omit the note count for Goals rather than show a truthful-but-misleading zero.
  const descriptionFallback = isGoal ? subjectPlanLabel : `${noteLabel} in saved order.`;
  const detailLine = isGoal ? subjectPlanLabel : `${noteLabel} curated for this track.`;

  const handleStart = async () => {
    if (continuePlan) {
      router.push(`/collections/${continuePlan.id}`);
      return;
    }
    setAdopting(true);
    setError(null);
    try {
      if (isGoal) {
        const result = await adoptGoal(displayPlan.id);
        setStudyPlanSkippedNotice(result.goalCollectionId, result.skippedSubjectCount);
        router.push(`/collections/${result.goalCollectionId}`);
        return;
      }
      const result = await adoptStudyPlan(displayPlan.id);
      setStudyPlanSkippedNotice(result.collectionId, result.skippedCount);
      router.push(`/collections/${result.collectionId}`);
    } catch (adoptError) {
      setError(adoptError instanceof Error ? adoptError.message : `Could not start this ${isGoal ? "Goal" : "plan"}.`);
    } finally {
      setAdopting(false);
    }
  };

  return (
    <section className="space-y-3 sm:space-y-4">
      <div className="flex flex-col gap-0.5 sm:flex-row sm:items-center sm:justify-between">
        <h2 className="text-lg font-semibold sm:text-xl">
          {usingPrimary ? labels.primarySingular : `Recommended ${labels.singular}`}
        </h2>
        {usingPrimary ? null : <p className="text-xs text-foreground/65">{normalizedCourseProgram}</p>}
      </div>
      <Card className="space-y-4 border-blue-500/20 bg-blue-500/5 p-4 sm:p-6">
        <div className="space-y-1.5">
          <div className="flex flex-wrap items-center gap-2">
            <CardTitle>{displayPlan.title}</CardTitle>
            {continuePlan ? (
              <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700 dark:bg-blue-950/40 dark:text-blue-300">
                In your library
              </span>
            ) : null}
          </div>
          <CardDescription>{displayPlan.description || descriptionFallback}</CardDescription>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-foreground/70">{detailLine}</p>
          <Button type="button" loading={adopting} loadingText="Starting..." onClick={handleStart}>
            {ctaLabel}
          </Button>
        </div>
        {error ? (
          <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950/40 dark:text-red-200">
            {error}
          </p>
        ) : null}
      </Card>
      {!usingPrimary && viewAllHref && matchCount > 1 ? (
        <Link
          href={viewAllHref}
          className="inline-flex w-fit text-sm font-medium text-blue-600 transition-colors hover:underline dark:text-blue-400"
        >
          See all {matchCount} {labels.plural.toLowerCase()}
        </Link>
      ) : null}
    </section>
  );
}
