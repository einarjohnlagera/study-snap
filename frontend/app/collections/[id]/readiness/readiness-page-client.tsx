"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { BackLink } from "@/components/ui/back-link";
import { Button } from "@/components/ui/button";
import { Card, CardDescription, CardTitle } from "@/components/ui/card";
import { PageHeader } from "@/components/page-header";
import { ReadinessSummary } from "@/components/readiness/readiness-summary";
import { getAuthUser, type AuthUser } from "@/lib/auth";
import { getCollectionLabels } from "@/lib/collection-labels";
import {
  ApiRequestError,
  getPlanReadiness,
  trackAnalyticsEvent,
  type PlanReadinessResponse,
} from "@/lib/api";
import { requireAuthenticatedOnboardedUser } from "@/lib/route-guards";

type LoadState = "loading" | "ready" | "error" | "not-found";

export function CollectionReadinessPageClient({ collectionId }: Readonly<{ collectionId: string }>) {
  const router = useRouter();
  const [authUser] = useState<AuthUser | null>(() => getAuthUser());
  const [loadState, setLoadState] = useState<LoadState>("loading");
  const [readiness, setReadiness] = useState<PlanReadinessResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const trackedViewRef = useRef(false);
  const labels = useMemo(() => getCollectionLabels(authUser?.profileType), [authUser?.profileType]);

  const loadReadiness = useCallback(async () => {
    setLoadState("loading");
    setLoadError(null);
    try {
      const payload = await getPlanReadiness(collectionId);
      setReadiness(payload);
      setLoadState("ready");
      if (!trackedViewRef.current) {
        trackedViewRef.current = true;
        void trackAnalyticsEvent({
          eventType: "PLAN_READINESS_VIEWED",
          entityId: payload.collectionId,
          metadata: {
            totalNotes: payload.totalNotes,
            notesWithStudyPack: payload.notesWithStudyPack,
            totalConcepts: payload.totalConcepts,
            overallReadinessPercentage: payload.overallReadinessPercentage,
          },
        });
      }
    } catch (error) {
      if (error instanceof ApiRequestError && error.status === 404) {
        setLoadState("not-found");
        return;
      }
      setLoadError(error instanceof Error ? error.message : "Could not load readiness.");
      setLoadState("error");
    }
  }, [collectionId]);

  useEffect(() => {
    if (!requireAuthenticatedOnboardedUser(router)) {
      return;
    }
    void Promise.resolve().then(loadReadiness);
  }, [loadReadiness, router]);

  if (loadState === "loading") {
    return (
      <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
        <BackLink href={`/collections/${collectionId}`} label={labels.singular} />
        <PageHeader
          eyebrow={labels.singular.toUpperCase()}
          title={`${labels.singular} readiness`}
          description="Loading readiness for this saved set of notes."
        />
        <Card className="space-y-4 p-6">
          <div className="h-5 w-1/2 animate-pulse rounded bg-muted" />
          <div className="h-24 w-full animate-pulse rounded bg-muted" />
        </Card>
      </main>
    );
  }

  if (loadState === "not-found") {
    return (
      <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
        <BackLink href="/collections" label={labels.plural} />
        <Card className="space-y-4 p-6">
          <CardTitle>{labels.singular} not found</CardTitle>
          <CardDescription>This saved set may have been deleted or may not belong to your account.</CardDescription>
          <Link className="inline-flex text-sm font-medium text-blue-600 hover:underline dark:text-blue-400" href="/collections">
            {labels.plural}
          </Link>
        </Card>
      </main>
    );
  }

  if (loadState === "error" || !readiness) {
    return (
      <main className="mx-auto flex w-full max-w-3xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
        <BackLink href={`/collections/${collectionId}`} label={labels.singular} />
        <Card className="space-y-4 p-6">
          <CardTitle>Could not load readiness</CardTitle>
          <CardDescription>{loadError ?? "Please try again."}</CardDescription>
          <Button type="button" variant="outline" onClick={() => void loadReadiness()}>Retry</Button>
        </Card>
      </main>
    );
  }

  return (
    <main className="mx-auto flex w-full max-w-5xl flex-col gap-6 px-4 py-6 sm:px-6 lg:px-8">
      <BackLink href={`/collections/${collectionId}`} label={labels.singular} />
      <PageHeader
        eyebrow={labels.singular.toUpperCase()}
        title={`${labels.singular} readiness`}
        description="Overall and subject readiness from your recent practice in this saved set."
      />

      <Card className="flex flex-col gap-3 p-4 text-sm text-foreground/75 sm:flex-row sm:items-center sm:justify-between sm:p-5">
        <p>
          {readiness.notesWithStudyPack} of {readiness.totalNotes} notes have Study Packs.
        </p>
        <Link href="/progress" className="font-medium text-blue-700 hover:underline dark:text-blue-300">
          View full Progress &rarr;
        </Link>
      </Card>

      <ReadinessSummary
        overallReadinessPercentage={readiness.overallReadinessPercentage}
        totalConcepts={readiness.totalConcepts}
        masteredConcepts={readiness.masteredConcepts}
        dueConcepts={readiness.dueConcepts}
        notPracticedConcepts={readiness.notPracticedConcepts}
        subjects={readiness.subjects}
        emptyTitle="No readiness yet"
        emptyDescription="Generate Study Packs and practice to see readiness."
      />
    </main>
  );
}
