"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { PracticeQuizCard } from "@/components/study-pack/practice-quiz-card";
import { getAuthUser } from "@/lib/auth";
import { getMyStudyPack, type StudyPackResponse } from "@/lib/api";

function StudyPackDetailLoading() {
  return (
    <div className="space-y-6">
      <Card className="space-y-3">
        <div className="h-4 w-28 animate-pulse rounded bg-foreground/10" />
        <div className="h-8 w-2/3 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-1/3 animate-pulse rounded bg-foreground/10" />
      </Card>
      <Card className="space-y-3">
        <div className="h-6 w-28 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-5/6 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-2/3 animate-pulse rounded bg-foreground/10" />
      </Card>
      <Card className="space-y-3">
        <div className="h-6 w-36 animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-full animate-pulse rounded bg-foreground/10" />
        <div className="h-4 w-3/4 animate-pulse rounded bg-foreground/10" />
      </Card>
      <Card className="space-y-4">
        <div className="h-6 w-24 animate-pulse rounded bg-foreground/10" />
        {Array.from({ length: 3 }).map((_, index) => (
          <div key={`quiz-skeleton-${index}`} className="space-y-2">
            <div className="h-4 w-3/4 animate-pulse rounded bg-foreground/10" />
            <div className="h-4 w-1/2 animate-pulse rounded bg-foreground/10" />
            <div className="h-4 w-2/3 animate-pulse rounded bg-foreground/10" />
          </div>
        ))}
      </Card>
    </div>
  );
}

export default function StudyPackDetailPage() {
  const router = useRouter();
  const params = useParams<{ id: string }>();
  const [studyPack, setStudyPack] = useState<StudyPackResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const studyPackId = useMemo(() => {
    if (!params?.id) {
      return "";
    }
    return Array.isArray(params.id) ? params.id[0] : params.id;
  }, [params]);

  const loadStudyPack = useCallback(async () => {
    if (!studyPackId) {
      setError("Study Pack not found.");
      setLoading(false);
      return;
    }

    const authUser = getAuthUser();
    if (!authUser) {
      router.replace("/login");
      return;
    }
    if (!authUser.emailVerifiedAt) {
      router.replace("/verify-email");
      return;
    }
    if (!authUser.profileType) {
      router.replace("/onboarding");
      return;
    }

    setLoading(true);
    setError(null);
    try {
      const detail = await getMyStudyPack(studyPackId);
      setStudyPack(detail);
    } catch (err) {
      const message = err instanceof Error ? err.message : "Could not load this Study Pack.";
      setError(message);
      setStudyPack(null);
    } finally {
      setLoading(false);
    }
  }, [router, studyPackId]);

  useEffect(() => {
    void loadStudyPack();
  }, [loadStudyPack]);

  const isNotFound = error?.toLowerCase().includes("not found") ?? false;

  return (
    <main className="mx-auto w-full max-w-4xl space-y-6 px-6 py-10">
      <div className="flex items-center justify-between gap-3">
        <Link href="/dashboard" className="text-sm font-medium text-blue-600 hover:underline dark:text-blue-400">
          Back to Dashboard
        </Link>
      </div>

      {loading ? (
        <StudyPackDetailLoading />
      ) : error ? (
        <Card className="space-y-4">
          <h1 className="text-2xl font-semibold">
            {isNotFound ? "Study Pack not found" : "Could not load this Study Pack"}
          </h1>
          <p className="text-sm text-foreground/75">
            {isNotFound
              ? "This Study Pack is unavailable or does not belong to your account."
              : error}
          </p>
          <div className="flex flex-wrap gap-2">
            {!isNotFound ? (
              <Button type="button" onClick={() => void loadStudyPack()}>
                Retry
              </Button>
            ) : null}
            <Link href="/dashboard">
              <Button type="button" variant="outline">
                Back to Dashboard
              </Button>
            </Link>
          </div>
        </Card>
      ) : studyPack ? (
        <div className="space-y-6">
          <Card className="space-y-3">
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-600 dark:text-blue-400">
              Study Pack
            </p>
            <h1 className="text-3xl font-semibold tracking-tight">{studyPack.title}</h1>
            <div className="flex flex-wrap gap-3 text-xs text-foreground/70">
              <span>{new Date(studyPack.createdAt).toLocaleString()}</span>
              <span>{studyPack.quiz.length} quiz questions</span>
            </div>
            {studyPack.tags.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {studyPack.tags.map((tag) => (
                  <span
                    key={tag}
                    className="rounded-full border border-border bg-background px-2 py-1 text-xs text-foreground/75"
                  >
                    {tag}
                  </span>
                ))}
              </div>
            ) : null}
            <div>
              <Link href={`/study-packs/${studyPack.id}/quick-review`}>
                <Button type="button" variant="outline">
                  Start Quick Review
                </Button>
              </Link>
            </div>
          </Card>

          <Card className="space-y-3">
            <h2 className="text-xl font-semibold">Summary</h2>
            <p className="whitespace-pre-wrap text-sm leading-relaxed text-foreground/85">{studyPack.summary}</p>
          </Card>

          <Card className="space-y-3">
            <h2 className="text-xl font-semibold">Key Concepts</h2>
            <ul className="list-disc space-y-2 pl-5 text-sm leading-relaxed text-foreground/85">
              {studyPack.keyConcepts.map((concept, index) => (
                <li key={`${studyPack.id}-concept-${index}`}>{concept}</li>
              ))}
            </ul>
          </Card>

          <PracticeQuizCard quiz={studyPack.quiz} />
        </div>
      ) : null}
    </main>
  );
}
