"use client";

import { useParams } from "next/navigation";
import { useEffect, useState } from "react";
import { BackLink } from "@/components/ui/back-link";
import { PageHeader } from "@/components/page-header";
import { Card } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import {
  getLinkedLearnerProgress,
  type LinkedLearnerProgressResponse,
} from "@/lib/api";

function formatScore(score: number | null): string {
  return score === null ? "No quiz results yet" : `${Math.round(score)}%`;
}

export default function LinkedLearnerProgressPage() {
  const params = useParams<{ relationshipId: string }>();
  const [progress, setProgress] = useState<LinkedLearnerProgressResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Loading is DERIVED, not set. Calling setState synchronously inside the effect trips the
  // cascading-render lint rule, and tracking which id has finished loading also keeps a param
  // change from briefly rendering the previous learner's numbers under the new learner's page.
  const [loadedRelationshipId, setLoadedRelationshipId] = useState<string | null>(null);
  const loading = loadedRelationshipId !== params.relationshipId;

  useEffect(() => {
    let active = true;
    getLinkedLearnerProgress(params.relationshipId)
      .then((response) => {
        if (active) {
          setProgress(response);
          setError(null);
        }
      })
      .catch((loadError: unknown) => {
        if (active) {
          // Clear the previous learner's numbers. The error card and the stat grid are independent
          // conditionals, so without this a failed load for learner B renders "unavailable" ABOVE
          // learner A's still-mounted stats, under a header naming B.
          setProgress(null);
          setError(loadError instanceof Error && loadError.message
            ? loadError.message
            : "This learner's progress is not available.");
        }
      })
      .finally(() => {
        if (active) setLoadedRelationshipId(params.relationshipId);
      });
    return () => {
      active = false;
    };
  }, [params.relationshipId]);

  return (
    <main className="mx-auto w-full max-w-5xl space-y-6 px-4 py-6 sm:px-6">
      <BackLink href="/linked-learners" label="Learning connections" />
      <PageHeader
        eyebrow="Learning support"
        title={progress ? `${progress.learnerDisplayName}'s progress` : "Learner progress"}
        description="A privacy-safe view of readiness, plan progress and quiz performance. Personal notes and study material are never shown."
      />

      {loading ? (
        <div className="grid gap-4 sm:grid-cols-2">
          <Card className="space-y-3 p-5"><Skeleton className="h-5 w-32" /><Skeleton className="h-16 w-full" /></Card>
          <Card className="space-y-3 p-5"><Skeleton className="h-5 w-32" /><Skeleton className="h-16 w-full" /></Card>
        </div>
      ) : null}

      {!loading && error ? (
        <Card className="space-y-4 p-5">
          <h2 className="text-lg font-semibold">Progress unavailable</h2>
          <p role="alert" className="text-sm text-foreground/70">{error}</p>
        </Card>
      ) : null}

      {!loading && progress && !progress.hasActivity ? (
        <Card className="space-y-3 p-5 sm:p-6">
          <h2 className="text-lg font-semibold">No learning activity yet</h2>
          <p className="text-sm text-foreground/70">
            {progress.learnerDisplayName} has not completed enough study activity to show progress here yet.
          </p>
        </Card>
      ) : null}

      {!loading && progress && progress.hasActivity ? (
        <div className="grid gap-4 sm:grid-cols-2">
          <Card className="space-y-3 p-5">
            <h2 className="text-lg font-semibold">Readiness</h2>
            <p className="text-3xl font-semibold">{progress.readiness.readinessPercentage}% ready</p>
            <p className="text-sm text-foreground/70">
              {progress.readiness.masteredConcepts} mastered · {progress.readiness.dueConcepts} due · {progress.readiness.notStartedConcepts} not started
            </p>
          </Card>
          <Card className="space-y-3 p-5">
            <h2 className="text-lg font-semibold">Quiz performance</h2>
            <p className="text-3xl font-semibold">{formatScore(progress.quizPerformance.averageRecentScore)}</p>
            <p className="text-sm text-foreground/70">
              Best recent score: {formatScore(progress.quizPerformance.bestRecentScore)} · {progress.quizPerformance.studyPacksReviewed} Study Packs reviewed
            </p>
          </Card>
          <Card className="space-y-3 p-5">
            <h2 className="text-lg font-semibold">Plan progress</h2>
            <p className="text-3xl font-semibold">{progress.collectionProgress.practicedItems}/{progress.collectionProgress.totalItems} practiced</p>
            <p className="text-sm text-foreground/70">
              {progress.collectionProgress.readyItems} ready items across {progress.collectionProgress.collectionCount} plans
            </p>
          </Card>
        </div>
      ) : null}
    </main>
  );
}
