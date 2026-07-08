"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import { BackLink } from "@/components/ui/back-link";
import { PublicStudyPlanCard } from "@/components/study-plan/public-study-plan-card";
import { getAuthUser } from "@/lib/auth";
import { getCollectionLabels } from "@/lib/collection-labels";
import { normalizeCourseProgram } from "@/lib/learning-profile";
import { getMe, listCollections, listPublicStudyPlans, type NoteCollectionSummary } from "@/lib/api";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";
import { PROFILE_LEARNING_PROFILE_SECTION_ID } from "@/lib/profile-sections";

type LoadState = "loading" | "ready" | "error";

type PlanWithAdoption = {
  plan: NoteCollectionSummary;
  adoptedCollection: NoteCollectionSummary | null;
};

function PlanSkeletonGrid() {
  return (
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3" aria-label="Loading study plans">
      {Array.from({ length: 3 }, (_, index) => (
        <Card key={index} className="space-y-4 p-5">
          <div className="h-5 w-2/3 animate-pulse rounded bg-muted" />
          <div className="h-4 w-full animate-pulse rounded bg-muted" />
          <div className="h-9 w-full animate-pulse rounded bg-muted" />
        </Card>
      ))}
    </div>
  );
}

export function PublishedPlansPageClient() {
  const router = useRouter();
  const profileType = useMemo(() => getAuthUser()?.profileType ?? null, []);
  const labels = useMemo(() => getCollectionLabels(profileType), [profileType]);
  const [recommendedState, setRecommendedState] = useState<LoadState>("loading");
  const [browseAllState, setBrowseAllState] = useState<LoadState>("loading");
  const [courseProgram, setCourseProgram] = useState<string | null>(null);
  const [plans, setPlans] = useState<PlanWithAdoption[]>([]);
  const [browseAllPlans, setBrowseAllPlans] = useState<PlanWithAdoption[]>([]);
  const [recommendedError, setRecommendedError] = useState<string | null>(null);
  const [browseAllError, setBrowseAllError] = useState<string | null>(null);

  const joinAdoptedCollections = useCallback((
    publicPlans: NoteCollectionSummary[],
    personalCollections: NoteCollectionSummary[],
  ): PlanWithAdoption[] => publicPlans.map((plan) => ({
    plan,
    adoptedCollection:
      personalCollections.find((collection) => collection.sourcePlanId === plan.id) ?? null,
  })), []);

  const sortPlansByTitle = useCallback((publicPlans: NoteCollectionSummary[]): NoteCollectionSummary[] => (
    [...publicPlans].sort((first, second) => first.title.localeCompare(second.title, undefined, { sensitivity: "base" }))
  ), []);

  const loadPlans = useCallback(async () => {
    setRecommendedState("loading");
    setBrowseAllState("loading");
    setRecommendedError(null);
    setBrowseAllError(null);

    const personalCollectionsPromise = listCollections().catch(() => []);

    const loadRecommended = async () => {
      try {
        const me = await getMe();
        const normalized = normalizeCourseProgram(me.courseProgram);
        setCourseProgram(normalized);
        if (!normalized) {
          setPlans([]);
          setRecommendedState("ready");
          return;
        }
        const [publicPlans, personalCollections] = await Promise.all([
          listPublicStudyPlans({ courseProgram: normalized }),
          personalCollectionsPromise,
        ]);
        setPlans(joinAdoptedCollections(publicPlans, personalCollections));
        setRecommendedState("ready");
      } catch (error) {
        setRecommendedError(error instanceof Error ? error.message : "Could not load published plans.");
        setRecommendedState("error");
      }
    };

    const loadBrowseAll = async () => {
      try {
        const [publicPlans, personalCollections] = await Promise.all([
          listPublicStudyPlans({}),
          personalCollectionsPromise,
        ]);
        setBrowseAllPlans(joinAdoptedCollections(sortPlansByTitle(publicPlans), personalCollections));
        setBrowseAllState("ready");
      } catch (error) {
        setBrowseAllError(error instanceof Error ? error.message : "Could not load official review sets.");
        setBrowseAllState("error");
      }
    };

    await Promise.allSettled([loadRecommended(), loadBrowseAll()]);
  }, [joinAdoptedCollections, sortPlansByTitle]);

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    void Promise.resolve().then(loadPlans);
  }, [loadPlans, router]);

  return (
    <main className="mx-auto flex w-full max-w-6xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
      <BackLink href="/dashboard" label="Dashboard" />

      <PageHeader
        eyebrow="DISCOVER"
        title={`Recommended ${labels.plural}`}
        description={
          courseProgram
            ? `Published ${labels.plural.toLowerCase()} for ${courseProgram}. Adopt any of them to start studying with a curated set of notes.`
            : `Published ${labels.plural.toLowerCase()} matched to your course or program.`
        }
      />

      {recommendedState === "loading" ? <PlanSkeletonGrid /> : null}

      {recommendedState === "error" ? (
        <Card className="space-y-4 p-6">
          <CardTitle>Could not load {labels.plural.toLowerCase()}</CardTitle>
          <CardDescription>{recommendedError ?? "Please try again."}</CardDescription>
          <Button type="button" variant="outline" onClick={() => void loadPlans()}>
            Retry
          </Button>
        </Card>
      ) : null}

      {recommendedState === "ready" && !courseProgram ? (
        <Card className="space-y-4 p-6 text-center">
          <CardTitle>Set your course or program first</CardTitle>
          <CardDescription>
            Recommended {labels.plural.toLowerCase()} are matched to your course or program. Add yours to see plans
            curated for your track.
          </CardDescription>
          <div>
            <Link
              href={`/profile?from=published#${PROFILE_LEARNING_PROFILE_SECTION_ID}`}
              className="inline-flex w-fit text-sm font-medium text-blue-600 transition-colors hover:underline dark:text-blue-400"
            >
              Set course or program
            </Link>
          </div>
        </Card>
      ) : null}

      {recommendedState === "ready" && courseProgram && plans.length === 0 ? (
        <Card className="space-y-4 p-6 text-center">
          <CardTitle>No published {labels.plural.toLowerCase()} yet</CardTitle>
          <CardDescription>
            There are no published {labels.plural.toLowerCase()} for {courseProgram} right now. Check back later or
            create your own.
          </CardDescription>
          <div>
            <Link
              href="/collections"
              className="inline-flex w-fit text-sm font-medium text-blue-600 transition-colors hover:underline dark:text-blue-400"
            >
              Go to your {labels.plural.toLowerCase()}
            </Link>
          </div>
        </Card>
      ) : null}

      {recommendedState === "ready" && courseProgram && plans.length > 0 ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {plans.map(({ plan, adoptedCollection }) => (
            <PublicStudyPlanCard key={plan.id} plan={plan} adoptedCollection={adoptedCollection} profileType={profileType} />
          ))}
        </div>
      ) : null}

      <section id="browse-all" className="space-y-3 sm:space-y-4">
        <h2 className="text-lg font-semibold sm:text-xl">Browse All Official {labels.plural}</h2>

        {browseAllState === "loading" ? <PlanSkeletonGrid /> : null}

        {browseAllState === "error" ? (
          <Card className="space-y-4 p-6">
            <CardTitle>Could not load official {labels.plural.toLowerCase()}</CardTitle>
            <CardDescription>{browseAllError ?? "Please try again."}</CardDescription>
            <Button type="button" variant="outline" onClick={() => void loadPlans()}>
              Retry
            </Button>
          </Card>
        ) : null}

        {browseAllState === "ready" && browseAllPlans.length === 0 ? (
          <Card className="space-y-4 p-6 text-center">
            <CardTitle>No Official {labels.plural} have been published yet</CardTitle>
            <CardDescription>
              Check back later for curated sets from the NoteLib team.
            </CardDescription>
          </Card>
        ) : null}

        {browseAllState === "ready" && browseAllPlans.length > 0 ? (
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {browseAllPlans.map(({ plan, adoptedCollection }) => (
              <PublicStudyPlanCard key={plan.id} plan={plan} adoptedCollection={adoptedCollection} profileType={profileType} />
            ))}
          </div>
        ) : null}
      </section>
    </main>
  );
}
