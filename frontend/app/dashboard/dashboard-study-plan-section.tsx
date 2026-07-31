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
import { buildExploreUrl } from "@/lib/explore-url";
import { normalizeCourseProgram } from "@/lib/learning-profile";
import { setJustAdoptedNotice } from "@/lib/just-adopted-notice";
import { setStudyPlanSkippedNotice } from "@/lib/study-plan-skipped-notice";

export { getStudyPlanSkippedNotice } from "@/lib/study-plan-skipped-notice";

export type StudyPlanStartContext = {
  collectionId: string;
  isGoal: boolean;
  plan: NoteCollectionSummary;
};

export async function listCourseProgramStudyPlans(courseProgram: string | null): Promise<NoteCollectionSummary[]> {
  const normalizedCourseProgram = normalizeCourseProgram(courseProgram);
  if (!normalizedCourseProgram) {
    return [];
  }
  return listPublicStudyPlans({ courseProgram: normalizedCourseProgram });
}

type DashboardStudyPlanSectionProps = {
  courseProgram: string | null;
  profileType: ProfileType | null;
  context?: "default" | "onboarding" | "practice-first";
  primaryCollectionId?: string | null;
  discoveryPresentation?: "full" | "pointer";
  // The no-primary pointer card duplicates DashboardEmpty's own "ready-made {plan} instead"
  // link on the zero-note Dashboard — set true there so this section only renders when a
  // Primary Review Set actually exists to continue.
  suppressPointerWhenNoPrimary?: boolean;
  onPlanStarted?: (context: StudyPlanStartContext) => Promise<void>;
};

function ExplorePointerCard({
  singular,
  plural,
}: Readonly<{
  singular: string;
  plural: string;
}>) {
  return (
    <section className="space-y-3 sm:space-y-4">
      <h2 className="text-lg font-semibold sm:text-xl">Find your next {singular}</h2>
      <Card className="space-y-3 border-dashed p-4 sm:p-6">
        <CardTitle>Explore official {plural}</CardTitle>
        <CardDescription>
          Browse curated {plural.toLowerCase()} and choose one when you&apos;re ready to start.
        </CardDescription>
        <Link
          href={buildExploreUrl({ source: "dashboard" })}
          className="inline-flex w-fit text-sm font-medium text-blue-600 transition-colors hover:underline dark:text-blue-400"
        >
          Browse in Explore
        </Link>
      </Card>
    </section>
  );
}

export function DashboardStudyPlanSection({
  courseProgram,
  profileType,
  context = "default",
  primaryCollectionId,
  discoveryPresentation = "full",
  suppressPointerWhenNoPrimary = false,
  onPlanStarted,
}: Readonly<DashboardStudyPlanSectionProps>) {
  const router = useRouter();
  const labels = useMemo(() => getCollectionLabels(profileType), [profileType]);
  const normalizedCourseProgram = useMemo(() => normalizeCourseProgram(courseProgram), [courseProgram]);
  const [plan, setPlan] = useState<NoteCollectionSummary | null>(null);
  const [adoptedPlan, setAdoptedPlan] = useState<NoteCollectionSummary | null>(null);
  const [loadedCourseProgram, setLoadedCourseProgram] = useState<string | null>(null);
  const [adopting, setAdopting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [startedPlan, setStartedPlan] = useState<StudyPlanStartContext | null>(null);

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
    if (discoveryPresentation === "pointer") {
      setPlan(null);
      setAdoptedPlan(null);
      setLoadedCourseProgram(null);
      return;
    }

    if (!normalizedCourseProgram) {
      setPlan(null);
      setAdoptedPlan(null);
      setLoadedCourseProgram(null);
      return;
    }

    let cancelled = false;
    setError(null);
    void Promise.all([
      listCourseProgramStudyPlans(normalizedCourseProgram),
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
          setAdoptedPlan(null);
          setLoadedCourseProgram(normalizedCourseProgram);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [discoveryPresentation, normalizedCourseProgram]);

  const primaryPending = Boolean(primaryCollectionId) && !primaryLoaded;
  const usingPrimary = Boolean(primaryCollectionId) && primaryLoaded && primaryMatch !== null;

  if (primaryPending) {
    return null;
  }

  if (discoveryPresentation === "pointer" && !usingPrimary) {
    if (suppressPointerWhenNoPrimary) {
      return null;
    }
    return <ExplorePointerCard singular={labels.singular} plural={labels.plural} />;
  }

  const courseProgramPending = !usingPrimary && (!normalizedCourseProgram || loadedCourseProgram !== normalizedCourseProgram);

  if (courseProgramPending && normalizedCourseProgram !== null) {
    return null;
  }

  if (!usingPrimary && normalizedCourseProgram === null) {
    return (
      <section className="space-y-3 sm:space-y-4">
        <h2 className="text-lg font-semibold sm:text-xl">Recommended {labels.singular}</h2>
        <Card className="space-y-3 border-dashed p-4 sm:p-6">
          <CardTitle>Set your course or program to find official {labels.plural.toLowerCase()}</CardTitle>
          <CardDescription>
            We use your course or program to match curated plans to your current track.
          </CardDescription>
          <Link href="/profile#learning-profile" className="inline-flex w-fit text-sm font-medium text-blue-600 transition-colors hover:underline dark:text-blue-400">
            Set course or program
          </Link>
        </Card>
      </section>
    );
  }

  const displayPlan = usingPrimary ? primaryMatch : plan;

  if (!displayPlan) {
    return null;
  }

  const continuePlan = usingPrimary ? primaryMatch : (adoptedPlan ?? null);
  const ownsSource = continuePlan?.id === displayPlan.id;
  const isGoal = displayPlan.childCount > 0;
  const ctaLabel = continuePlan
    ? (ownsSource ? "Open this plan" : (isGoal ? `Continue this ${labels.goalSingular}` : `Continue this ${labels.singular}`))
    : (isGoal ? `Start this ${labels.goalSingular}` : `Start this ${labels.singular}`);
  const subjectPlanLabel = `${displayPlan.childCount} ${labels.subjectSingular}${displayPlan.childCount === 1 ? "" : "s"}`;
  const noteLabel = `${displayPlan.itemCount} ${displayPlan.itemCount === 1 ? "note" : "notes"}`;
  const descriptionFallback = isGoal ? `${subjectPlanLabel} · ${noteLabel}` : `${noteLabel} in saved order.`;
  const detailLine = context === "practice-first" && typeof displayPlan.readyCount === "number"
    ? `${displayPlan.readyCount} of ${displayPlan.itemCount} notes practice-ready`
    : (isGoal ? `${subjectPlanLabel} · ${noteLabel}` : `${noteLabel} curated for this track.`);

  const finishStart = async (startContext: StudyPlanStartContext) => {
    if (onPlanStarted) {
      await onPlanStarted(startContext);
      return;
    }
    router.push(`/collections/${startContext.collectionId}`);
  };

  const handleStart = async () => {
    if (adopting) {
      return;
    }
    setAdopting(true);
    setError(null);
    try {
      if (startedPlan) {
        await finishStart(startedPlan);
        return;
      }
      if (continuePlan) {
        await finishStart({
          collectionId: continuePlan.id,
          isGoal,
          plan: displayPlan,
        });
        return;
      }
      if (isGoal) {
        const result = await adoptGoal(displayPlan.id);
        setStudyPlanSkippedNotice(result.goalCollectionId, result.skippedSubjectCount);
        setJustAdoptedNotice(result.goalCollectionId);
        const startContext = {
          collectionId: result.goalCollectionId,
          isGoal,
          plan: displayPlan,
        };
        setStartedPlan(startContext);
        await finishStart(startContext);
        return;
      }
      const result = await adoptStudyPlan(displayPlan.id);
      setStudyPlanSkippedNotice(result.collectionId, result.skippedCount);
      const startContext = {
        collectionId: result.collectionId,
        isGoal,
        plan: displayPlan,
      };
      setStartedPlan(startContext);
      await finishStart(startContext);
    } catch (adoptError) {
      setError(adoptError instanceof Error ? adoptError.message : `Could not start this ${isGoal ? labels.goalSingular : labels.singular}.`);
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
        {context === "onboarding" ? (
          <p className="text-sm text-foreground/70">
            Optional: explore an official {labels.singular.toLowerCase()} alongside the Study Pack you just created.
          </p>
        ) : null}
        <div className="space-y-1.5">
          <div className="flex flex-wrap items-center gap-2">
            <CardTitle>{displayPlan.title}</CardTitle>
            {continuePlan ? (
              <span className="rounded-full border border-border bg-muted/60 px-2 py-0.5 text-xs font-medium text-foreground/65">
                Adopted
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
    </section>
  );
}
