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
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [courseProgram, setCourseProgram] = useState<string | null>(null);
  const [plans, setPlans] = useState<PlanWithAdoption[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);

  const loadPlans = useCallback(async () => {
    setLoadState("loading");
    setLoadError(null);
    try {
      const me = await getMe();
      const normalized = normalizeCourseProgram(me.courseProgram);
      setCourseProgram(normalized);
      if (!normalized) {
        setPlans([]);
        setLoadState("ready");
        return;
      }
      const [publicPlans, personalCollections] = await Promise.all([
        listPublicStudyPlans({ courseProgram: normalized }),
        listCollections(),
      ]);
      setPlans(
        publicPlans.map((plan) => ({
          plan,
          adoptedCollection:
            personalCollections.find((collection) => collection.sourcePlanId === plan.id) ?? null,
        })),
      );
      setLoadState("ready");
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : "Could not load published plans.");
      setLoadState("error");
    }
  }, []);

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

      {loadState === "loading" ? <PlanSkeletonGrid /> : null}

      {loadState === "error" ? (
        <Card className="space-y-4 p-6">
          <CardTitle>Could not load {labels.plural.toLowerCase()}</CardTitle>
          <CardDescription>{loadError ?? "Please try again."}</CardDescription>
          <Button type="button" variant="outline" onClick={() => void loadPlans()}>
            Retry
          </Button>
        </Card>
      ) : null}

      {loadState === "ready" && !courseProgram ? (
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

      {loadState === "ready" && courseProgram && plans.length === 0 ? (
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

      {loadState === "ready" && courseProgram && plans.length > 0 ? (
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
          {plans.map(({ plan, adoptedCollection }) => (
            <PublicStudyPlanCard key={plan.id} plan={plan} adoptedCollection={adoptedCollection} profileType={profileType} />
          ))}
        </div>
      ) : null}
    </main>
  );
}
