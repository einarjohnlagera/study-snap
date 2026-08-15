"use client";

import { useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import {
  adoptGoal,
  adoptStudyPlan,
  listCollections,
  listPublicStudyPlans,
  trackAnalyticsEvent,
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
  discoveryPresentation?: "full" | "pointer" | "recommendation";
  // The no-primary pointer card duplicates DashboardEmpty's own "ready-made {plan} instead"
  // link on the zero-note Dashboard — set true there so this section only renders when a
  // Primary Review Set actually exists to continue.
  suppressPointerWhenNoPrimary?: boolean;
  /**
   * An already-resolved, already-qualified plan. When supplied this component does NOT re-fetch.
   *
   * Onboarding gates the practice-first screen on `itemCount > 0 && readyCount > 0`, but this component's
   * own effect renders `publicPlans[0]` with no such predicate -- so the gate and the render could
   * disagree, and a second fetch could return a different or newly-unqualified plan between the two.
   * Passing the resolved plan down removes the disagreement by construction rather than duplicating the
   * predicate in two places that can drift apart.
   */
  resolvedPlan?: NoteCollectionSummary | null;
  onPlanStarted?: (context: StudyPlanStartContext) => Promise<void>;
};

function ExplorePointerCard({
  courseProgram,
  singular,
  plural,
}: Readonly<{
  courseProgram: string | null;
  singular: string;
  plural: string;
}>) {
  const analyticsMetadata = useMemo(() => ({
    surface: "dashboard",
    recommendationType: "generic-pointer",
    courseProgram,
  }), [courseProgram]);
  const impressionSignature = useMemo(
    () => `dashboard:generic-pointer:${courseProgram ?? "none"}`,
    [courseProgram],
  );
  const impressedSignatureRef = useRef<string | null>(null);

  useEffect(() => {
    if (impressedSignatureRef.current === impressionSignature) {
      return;
    }
    impressedSignatureRef.current = impressionSignature;
    trackAnalyticsSafely({
      eventType: "STUDY_PLAN_RECOMMENDATION_IMPRESSION",
      metadata: analyticsMetadata,
    });
  }, [analyticsMetadata, impressionSignature]);

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
          onClick={() => trackAnalyticsSafely({
            eventType: "STUDY_PLAN_RECOMMENDATION_CLICKED",
            metadata: analyticsMetadata,
          })}
          className="inline-flex w-fit text-sm font-medium text-blue-600 transition-colors hover:underline dark:text-blue-400"
        >
          Browse in Explore
        </Link>
      </Card>
    </section>
  );
}

function trackAnalyticsSafely(payload: Parameters<typeof trackAnalyticsEvent>[0]): void {
  try {
    void Promise.resolve(trackAnalyticsEvent(payload)).catch(() => undefined);
  } catch {
    // Analytics must never interrupt recommendation rendering or navigation.
  }
}

function RecommendationImpression({
  courseProgram,
  planId,
  children,
}: Readonly<{
  courseProgram: string;
  planId: string;
  children: ReactNode;
}>) {
  const analyticsMetadata = useMemo(() => ({
    surface: "dashboard",
    recommendationType: "named-plan",
    courseProgram,
  }), [courseProgram]);
  const impressionSignature = useMemo(
    () => `dashboard:named-plan:${courseProgram}:${planId}`,
    [courseProgram, planId],
  );
  const impressedSignatureRef = useRef<string | null>(null);

  useEffect(() => {
    if (impressedSignatureRef.current === impressionSignature) {
      return;
    }
    impressedSignatureRef.current = impressionSignature;
    trackAnalyticsSafely({
      eventType: "STUDY_PLAN_RECOMMENDATION_IMPRESSION",
      entityId: planId,
      metadata: analyticsMetadata,
    });
  }, [analyticsMetadata, impressionSignature, planId]);

  return children;
}

export function DashboardStudyPlanSection({
  courseProgram,
  profileType,
  context = "default",
  primaryCollectionId,
  discoveryPresentation = "full",
  suppressPointerWhenNoPrimary = false,
  resolvedPlan,
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

  // Deliberately NOT `primaryLoaded` / `primaryMatch` as separate effect deps. `setPrimaryLoaded(true)`
  // fires even when no primary is configured, so those deps flip false -> true after the fetch below
  // has already started, cancelling and re-running it — every no-primary learner paid two
  // `listPublicStudyPlans` + two `listCollections` calls, and because this effect is shared, so did
  // onboarding's `full` mode. This value is constant whenever `primaryCollectionId` is null, so the
  // common paths resolve in a single fetch; only the genuinely stale primary flips it, and that case
  // has to fetch anyway.
  const primaryResolvedToNothing = Boolean(primaryCollectionId) && primaryLoaded && primaryMatch === null;

  useEffect(() => {
    // Caller already resolved and qualified the plan -- trust it and do not re-fetch.
    if (resolvedPlan !== undefined) {
      setPlan(resolvedPlan);
      setAdoptedPlan(null);
      setLoadedCourseProgram(normalizedCourseProgram);
      return;
    }

    if (discoveryPresentation === "pointer") {
      setPlan(null);
      setAdoptedPlan(null);
      setLoadedCourseProgram(null);
      return;
    }

    // A learner who already has a Primary Review Set renders the continue card and never sees a
    // recommendation, so fetching public plans for them is two network calls with no reachable
    // output. This is the call that `v0.67.0` removed from the Dashboard; it comes back only for
    // the learners the recommendation can actually reach.
    if (discoveryPresentation === "recommendation" && primaryCollectionId && !primaryResolvedToNothing) {
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
  }, [discoveryPresentation, normalizedCourseProgram, resolvedPlan, primaryCollectionId, primaryResolvedToNothing]);

  const primaryPending = Boolean(primaryCollectionId) && !primaryLoaded;
  const usingPrimary = Boolean(primaryCollectionId) && primaryLoaded && primaryMatch !== null;

  if (primaryPending) {
    return null;
  }

  if (discoveryPresentation === "pointer" && !usingPrimary) {
    if (suppressPointerWhenNoPrimary) {
      return null;
    }
    return <ExplorePointerCard courseProgram={normalizedCourseProgram} singular={labels.singular} plural={labels.plural} />;
  }

  const courseProgramPending = !usingPrimary && (!normalizedCourseProgram || loadedCourseProgram !== normalizedCourseProgram);

  if (courseProgramPending && normalizedCourseProgram !== null) {
    return null;
  }

  if (!usingPrimary && normalizedCourseProgram === null) {
    if (discoveryPresentation === "recommendation") {
      if (suppressPointerWhenNoPrimary) {
        return null;
      }
      return <ExplorePointerCard courseProgram={null} singular={labels.singular} plural={labels.plural} />;
    }
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
    if (discoveryPresentation === "recommendation" && !suppressPointerWhenNoPrimary) {
      return <ExplorePointerCard courseProgram={normalizedCourseProgram} singular={labels.singular} plural={labels.plural} />;
    }
    return null;
  }

  // Already adopted: there is nothing to recommend, but returning null would blank the slot for a
  // learner who HAS engaged with plans — before this presentation existed they saw the Explore
  // pointer here. Fall through to it rather than removing their only discovery entry point.
  if (discoveryPresentation === "recommendation" && !usingPrimary && adoptedPlan) {
    if (suppressPointerWhenNoPrimary) {
      return null;
    }
    return <ExplorePointerCard courseProgram={normalizedCourseProgram} singular={labels.singular} plural={labels.plural} />;
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
  // `!usingPrimary` matters: the Primary Review Set continue card is documented as rendering
  // byte-for-byte unchanged, and readyCount is non-null on every owned summary, so omitting this
  // silently rewrote that card's detail line for every learner who has a primary.
  const detailLine = (context === "practice-first" || (discoveryPresentation === "recommendation" && !usingPrimary))
    && typeof displayPlan.readyCount === "number"
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
    if (discoveryPresentation === "recommendation" && !usingPrimary) {
      trackAnalyticsSafely({
        eventType: "STUDY_PLAN_RECOMMENDATION_CLICKED",
        entityId: displayPlan.id,
        metadata: {
          surface: "dashboard",
          recommendationType: "named-plan",
          courseProgram: normalizedCourseProgram,
        },
      });
    }
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
      // A zero-copy adopt yields an EMPTY collection. Routing to it as if it succeeded strands the
      // learner on a plan with nothing in it at the most abandonment-sensitive moment -- and this is the
      // first thing they chose to do. skippedCount was already surfaced; copiedCount was not checked.
      if (result.copiedCount <= 0) {
        setError(
          `This ${isGoal ? labels.goalSingular : labels.singular} has no notes ready to study yet. `
          + "Try another one, or start from your own notes.",
        );
        return;
      }
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

  const section = (
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

  if (discoveryPresentation === "recommendation" && !usingPrimary && normalizedCourseProgram) {
    return (
      <RecommendationImpression courseProgram={normalizedCourseProgram} planId={displayPlan.id}>
        {section}
      </RecommendationImpression>
    );
  }
  return section;
}
